#include <stdio.h>
#include <stdlib.h>
#include <vulkan/vulkan.h>

static uint32_t graphics_queue(VkPhysicalDevice device) {
    uint32_t count = 0;
    vkGetPhysicalDeviceQueueFamilyProperties(device, &count, NULL);
    VkQueueFamilyProperties *families = calloc(count, sizeof(*families));
    if (!families) {
        return UINT32_MAX;
    }
    vkGetPhysicalDeviceQueueFamilyProperties(device, &count, families);
    uint32_t index = 0;
    while (index < count && !(families[index].queueFlags & VK_QUEUE_GRAPHICS_BIT)) {
        ++index;
    }
    free(families);
    return index == count ? UINT32_MAX : index;
}

static int check_device(VkPhysicalDevice physical) {
    VkPhysicalDeviceProperties props;
    vkGetPhysicalDeviceProperties(physical, &props);
    printf("%s | Vulkan %u.%u.%u | driver 0x%x\n", props.deviceName,
           VK_VERSION_MAJOR(props.apiVersion), VK_VERSION_MINOR(props.apiVersion),
           VK_VERSION_PATCH(props.apiVersion), props.driverVersion);

    uint32_t family = graphics_queue(physical);
    if (family == UINT32_MAX || props.apiVersion < VK_API_VERSION_1_1) {
        return EXIT_FAILURE;
    }

    const float priority = 1.0f;
    const VkDeviceQueueCreateInfo queue = {
        .sType = VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO,
        .queueFamilyIndex = family,
        .queueCount = 1,
        .pQueuePriorities = &priority,
    };
    const VkDeviceCreateInfo info = {
        .sType = VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO,
        .queueCreateInfoCount = 1,
        .pQueueCreateInfos = &queue,
    };
    VkDevice logical;
    VkResult result = vkCreateDevice(physical, &info, NULL, &logical);
    if (result != VK_SUCCESS) {
        fprintf(stderr, "vkCreateDevice failed: %d\n", result);
        return EXIT_FAILURE;
    }
    vkDestroyDevice(logical, NULL);
    return EXIT_SUCCESS;
}

int main(void) {
    const VkApplicationInfo app = {
        .sType = VK_STRUCTURE_TYPE_APPLICATION_INFO,
        .pApplicationName = "PGR4 Android Vulkan probe",
        .apiVersion = VK_API_VERSION_1_1,
    };
    const VkInstanceCreateInfo info = {
        .sType = VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO,
        .pApplicationInfo = &app,
    };
    VkInstance instance;
    VkResult result = vkCreateInstance(&info, NULL, &instance);
    if (result != VK_SUCCESS) {
        fprintf(stderr, "vkCreateInstance failed: %d\n", result);
        return EXIT_FAILURE;
    }
    uint32_t count = 0;
    result = vkEnumeratePhysicalDevices(instance, &count, NULL);
    int status = EXIT_FAILURE;
    if (result == VK_SUCCESS && count > 0) {
        VkPhysicalDevice *devices = calloc(count, sizeof(*devices));
        if (devices && vkEnumeratePhysicalDevices(instance, &count, devices) == VK_SUCCESS) {
            for (uint32_t i = 0; i < count; ++i) {
                if (check_device(devices[i]) == EXIT_SUCCESS) {
                    status = EXIT_SUCCESS;
                }
            }
        }
        free(devices);
    }
    vkDestroyInstance(instance, NULL);
    puts(status == EXIT_SUCCESS ? "PASS: Vulkan graphics device created"
                                : "FAIL: no usable Vulkan graphics device");
    return status;
}
