package dev.pgr4port.android;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.ResultReceiver;
import android.content.ComponentName;
import android.content.ServiceConnection;

/**
 * Runs Vulkan driver probes in an isolated ":probe" process so a bad custom
 * Turnip .so cannot crash the main launcher UI.
 */
public final class ProbeService extends Service {
    public static final String EXTRA_DRIVER_DIR = "driver_dir";
    public static final String EXTRA_DRIVER_NAME = "driver_name";
    public static final String EXTRA_RECEIVER = "receiver";
    public static final String KEY_RAW = "raw";

    private final IBinder binder = new Binder();

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            stopSelf(startId);
            return START_NOT_STICKY;
        }
        final String driverDir = intent.getStringExtra(EXTRA_DRIVER_DIR);
        final String driverName = intent.getStringExtra(EXTRA_DRIVER_NAME);
        final ResultReceiver receiver = intent.getParcelableExtra(EXTRA_RECEIVER);

        new Thread(() -> {
            GpuProbe.GpuInfo info = GpuProbe.probeNow(getApplicationContext(), driverDir, driverName);
            if (receiver != null) {
                Bundle data = new Bundle();
                data.putString(KEY_RAW, (info.ok ? "1" : "0") + "|" + info.vendorId + "|"
                        + info.deviceName + "|" + info.apiVersion + "|" + info.driverVersion + "|"
                        + (info.bdaSupported ? "1" : "0") + "|" + info.error);
                receiver.send(info.ok ? 1 : 0, data);
            }
            stopSelf(startId);
        }, "pgr4-probe-worker").start();

        return START_NOT_STICKY;
    }

    interface Callback {
        void onResult(GpuProbe.GpuInfo info);
    }

    static void probeIsolated(Context context, String driverDir, String driverName, Callback callback) {
        final Handler mainHandler = new Handler(Looper.getMainLooper());
        final boolean[] finished = {false};

        final ServiceConnection[] connHolder = new ServiceConnection[1];
        Runnable timeoutTask = () -> {
            if (!finished[0]) {
                finished[0] = true;
                tryUnbind(context, connHolder[0]);
                callback.onResult(new GpuProbe.GpuInfo(
                        false, "", "", "", "", false, "Driver probe timed out or crashed"));
            }
        };

        ResultReceiver receiver = new ResultReceiver(mainHandler) {
            @Override
            protected void onReceiveResult(int resultCode, Bundle resultData) {
                if (finished[0]) return;
                finished[0] = true;
                mainHandler.removeCallbacks(timeoutTask);
                tryUnbind(context, connHolder[0]);
                String raw = resultData != null ? resultData.getString(KEY_RAW, "") : "";
                callback.onResult(GpuProbe.GpuInfo.parse(raw));
            }
        };

        connHolder[0] = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                Intent startIntent = new Intent(context, ProbeService.class);
                startIntent.putExtra(EXTRA_DRIVER_DIR, driverDir);
                startIntent.putExtra(EXTRA_DRIVER_NAME, driverName);
                startIntent.putExtra(EXTRA_RECEIVER, receiver);
                context.startService(startIntent);
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                if (!finished[0]) {
                    finished[0] = true;
                    mainHandler.removeCallbacks(timeoutTask);
                    tryUnbind(context, this);
                    callback.onResult(new GpuProbe.GpuInfo(
                            false, "", "", "", "", false, "Driver crashed during Vulkan init"));
                }
            }
        };

        mainHandler.postDelayed(timeoutTask, 4000);
        Intent bindIntent = new Intent(context, ProbeService.class);
        context.bindService(bindIntent, connHolder[0], Context.BIND_AUTO_CREATE);
    }

    private static void tryUnbind(Context context, ServiceConnection conn) {
        if (conn == null) return;
        try {
            context.unbindService(conn);
        } catch (IllegalArgumentException ignored) {
        }
    }
}
