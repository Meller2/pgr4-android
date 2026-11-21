#include <dlfcn.h>
#include <jni.h>
#include <rex/ui/vulkan/android_gpu_driver.h>
#include <vulkan/vulkan.h>

#include <cstdio>
#include <cstring>
#include <string>
#include <vector>

namespace {

std::string SanitizeField(const char* text) {
  if (!text) return "";
  std::string out(text);
  for (char& ch : out) {
    if (ch == '|' || ch == '\n' || ch == '\r') {
      ch = ' ';
    }
  }
  return out;
}

std::string MakeResult(bool ok, uint32_t vendor_id, const char* device_name,
                       uint32_t api_version, uint32_t driver_version,
                       bool bda_supported, const char* error_msg) {
  char buf[512];
  std::snprintf(
      buf, sizeof(buf), "%d|0x%04X|%s|%u.%u.%u|0x%08X|%d|%s", ok ? 1 : 0,
      vendor_id, SanitizeField(device_name).c_str(),
      VK_VERSION_MAJOR(api_version), VK_VERSION_MINOR(api_version),
      VK_VERSION_PATCH(api_version), driver_version, bda_supported ? 1 : 0,
      SanitizeField(error_msg).c_str());
  return std::string(buf);
}

std::string ProbeVulkanDriver(const char* hooks_dir, const char* tmp_dir,
                              const char* driver_dir, const char* driver_name) {
  const bool is_custom = driver_name && *driver_name;
  void* handle = rex_android_open_vulkan(
      hooks_dir ? hooks_dir : "", tmp_dir ? tmp_dir : "",
      driver_dir ? driver_dir : "", driver_name ? driver_name : "");
  if (!handle) {
    return MakeResult(false, 0, "", 0, 0, false,
                      is_custom ? "Failed to open custom Vulkan driver"
                                : "Failed to open system libvulkan.so");
  }

  auto get_instance_proc = reinterpret_cast<PFN_vkGetInstanceProcAddr>(
      dlsym(handle, "vkGetInstanceProcAddr"));
  if (!get_instance_proc) {
    return MakeResult(false, 0, "", 0, 0, false,
                      "vkGetInstanceProcAddr missing in driver");
  }

  auto create_instance = reinterpret_cast<PFN_vkCreateInstance>(
      get_instance_proc(VK_NULL_HANDLE, "vkCreateInstance"));
  if (!create_instance) {
    return MakeResult(false, 0, "", 0, 0, false,
                      "vkCreateInstance missing in driver");
  }

  VkApplicationInfo app_info{};
  app_info.sType = VK_STRUCTURE_TYPE_APPLICATION_INFO;
  app_info.pApplicationName = "PGR4 Launcher Probe";
  app_info.apiVersion = VK_API_VERSION_1_2;

  VkInstanceCreateInfo inst_info{};
  inst_info.sType = VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO;
  inst_info.pApplicationInfo = &app_info;

  VkInstance instance = VK_NULL_HANDLE;
  VkResult res = create_instance(&inst_info, nullptr, &instance);
  if (res != VK_SUCCESS) {
    app_info.apiVersion = VK_API_VERSION_1_1;
    res = create_instance(&inst_info, nullptr, &instance);
  }
  if (res != VK_SUCCESS || instance == VK_NULL_HANDLE) {
    char err[64];
    std::snprintf(err, sizeof(err), "vkCreateInstance failed (%d)", res);
    return MakeResult(false, 0, "", 0, 0, false, err);
  }

  auto destroy_instance = reinterpret_cast<PFN_vkDestroyInstance>(
      get_instance_proc(instance, "vkDestroyInstance"));
  auto enum_phys = reinterpret_cast<PFN_vkEnumeratePhysicalDevices>(
      get_instance_proc(instance, "vkEnumeratePhysicalDevices"));
  auto get_props = reinterpret_cast<PFN_vkGetPhysicalDeviceProperties>(
      get_instance_proc(instance, "vkGetPhysicalDeviceProperties"));
  auto get_queues = reinterpret_cast<PFN_vkGetPhysicalDeviceQueueFamilyProperties>(
      get_instance_proc(instance, "vkGetPhysicalDeviceQueueFamilyProperties"));
  auto create_device = reinterpret_cast<PFN_vkCreateDevice>(
      get_instance_proc(instance, "vkCreateDevice"));
  auto destroy_device = reinterpret_cast<PFN_vkDestroyDevice>(
      get_instance_proc(instance, "vkDestroyDevice"));
  auto enum_ext = reinterpret_cast<PFN_vkEnumerateDeviceExtensionProperties>(
      get_instance_proc(instance, "vkEnumerateDeviceExtensionProperties"));
  auto get_features2 = reinterpret_cast<PFN_vkGetPhysicalDeviceFeatures2>(
      get_instance_proc(instance, "vkGetPhysicalDeviceFeatures2"));

  if (!enum_phys || !get_props || !get_queues || !create_device ||
      !destroy_device) {
    if (destroy_instance) destroy_instance(instance, nullptr);
    return MakeResult(false, 0, "", 0, 0, false,
                      "Required Vulkan instance functions missing");
  }

  uint32_t dev_count = 0;
  res = enum_phys(instance, &dev_count, nullptr);
  if (res != VK_SUCCESS || dev_count == 0) {
    if (destroy_instance) destroy_instance(instance, nullptr);
    return MakeResult(false, 0, "", 0, 0, false,
                      "No Vulkan physical devices found");
  }

  std::vector<VkPhysicalDevice> devices(dev_count);
  res = enum_phys(instance, &dev_count, devices.data());
  if (res != VK_SUCCESS) {
    if (destroy_instance) destroy_instance(instance, nullptr);
    return MakeResult(false, 0, "", 0, 0, false,
                      "vkEnumeratePhysicalDevices failed");
  }

  std::string final_result;
  for (VkPhysicalDevice phys : devices) {
    VkPhysicalDeviceProperties props{};
    get_props(phys, &props);

    uint32_t q_count = 0;
    get_queues(phys, &q_count, nullptr);
    std::vector<VkQueueFamilyProperties> q_props(q_count);
    get_queues(phys, &q_count, q_props.data());

    uint32_t gfx_family = UINT32_MAX;
    for (uint32_t i = 0; i < q_count; ++i) {
      if (q_props[i].queueFlags & VK_QUEUE_GRAPHICS_BIT) {
        gfx_family = i;
        break;
      }
    }
    if (gfx_family == UINT32_MAX || props.apiVersion < VK_API_VERSION_1_1) {
      continue;
    }

    float priority = 1.0f;
    VkDeviceQueueCreateInfo queue_info{};
    queue_info.sType = VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO;
    queue_info.queueFamilyIndex = gfx_family;
    queue_info.queueCount = 1;
    queue_info.pQueuePriorities = &priority;

    VkDeviceCreateInfo dev_info{};
    dev_info.sType = VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO;
    dev_info.queueCreateInfoCount = 1;
    dev_info.pQueueCreateInfos = &queue_info;

    VkDevice device = VK_NULL_HANDLE;
    VkResult dev_res = create_device(phys, &dev_info, nullptr, &device);
    if (dev_res != VK_SUCCESS) {
      continue;
    }
    destroy_device(device, nullptr);

    bool bda_supported = false;
    if (enum_ext) {
      uint32_t ext_count = 0;
      if (enum_ext(phys, nullptr, &ext_count, nullptr) == VK_SUCCESS &&
          ext_count > 0) {
        std::vector<VkExtensionProperties> exts(ext_count);
        if (enum_ext(phys, nullptr, &ext_count, exts.data()) == VK_SUCCESS) {
          for (const auto& ext : exts) {
            if (std::strcmp(ext.extensionName,
                            VK_KHR_BUFFER_DEVICE_ADDRESS_EXTENSION_NAME) == 0) {
              bda_supported = true;
              break;
            }
          }
        }
      }
    }
    if (!bda_supported && get_features2 &&
        props.apiVersion >= VK_API_VERSION_1_2) {
      VkPhysicalDeviceVulkan12Features v12{};
      v12.sType = VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_VULKAN_1_2_FEATURES;
      VkPhysicalDeviceFeatures2 f2{};
      f2.sType = VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_FEATURES_2;
      f2.pNext = &v12;
      get_features2(phys, &f2);
      bda_supported = (v12.bufferDeviceAddress == VK_TRUE);
    }

    final_result = MakeResult(true, props.vendorID, props.deviceName,
                              props.apiVersion, props.driverVersion,
                              bda_supported, "");
    break;
  }

  if (destroy_instance) destroy_instance(instance, nullptr);

  if (final_result.empty()) {
    return MakeResult(false, 0, "", 0, 0, false,
                      "No compatible Vulkan 1.1+ graphics device");
  }
  return final_result;
}

}  // namespace

extern "C" JNIEXPORT jstring JNICALL
Java_dev_pgr4port_android_GpuProbe_nativeProbeVulkan(
    JNIEnv* env, jclass, jstring j_hooks_dir, jstring j_tmp_dir,
    jstring j_driver_dir, jstring j_driver_name) {
  const char* hooks =
      j_hooks_dir ? env->GetStringUTFChars(j_hooks_dir, nullptr) : nullptr;
  const char* tmp =
      j_tmp_dir ? env->GetStringUTFChars(j_tmp_dir, nullptr) : nullptr;
  const char* dir =
      j_driver_dir ? env->GetStringUTFChars(j_driver_dir, nullptr) : nullptr;
  const char* lib =
      j_driver_name ? env->GetStringUTFChars(j_driver_name, nullptr) : nullptr;

  std::string result = ProbeVulkanDriver(hooks, tmp, dir, lib);

  if (hooks) env->ReleaseStringUTFChars(j_hooks_dir, hooks);
  if (tmp) env->ReleaseStringUTFChars(j_tmp_dir, tmp);
  if (dir) env->ReleaseStringUTFChars(j_driver_dir, dir);
  if (lib) env->ReleaseStringUTFChars(j_driver_name, lib);

  return env->NewStringUTF(result.c_str());
}
