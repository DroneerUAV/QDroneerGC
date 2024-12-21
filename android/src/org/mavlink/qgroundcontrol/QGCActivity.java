package org.mavlink.qgroundcontrol;

import java.util.List;
import java.lang.reflect.Method;

import android.content.Context;
import android.os.Bundle;
import android.os.PowerManager;
import android.net.wifi.WifiManager;
import android.util.Log;
import android.view.WindowManager;
import android.app.Activity;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;

import org.qtproject.qt.android.bindings.QtActivity;


import com.skydroid.rcsdk.*;
import com.skydroid.rcsdk.comm.CommListener;
import com.skydroid.rcsdk.common.Uart;
import com.skydroid.rcsdk.common.callback.*;
import com.skydroid.rcsdk.common.error.SkyException;
import com.skydroid.rcsdk.common.payload.*;
import com.skydroid.rcsdk.common.pipeline.Pipeline;
import com.skydroid.rcsdk.common.remotecontroller.ControlMode;
import com.skydroid.rcsdk.key.AirLinkKey;
import com.skydroid.rcsdk.key.RemoteControllerKey;
import com.skydroid.rcsdk.utils.RCSDKUtils;

public class QGCActivity extends QtActivity {
    private static final String TAG = QGCActivity.class.getSimpleName();
    private static final String SCREEN_BRIGHT_WAKE_LOCK_TAG = "QGroundControl";
    private static final String MULTICAST_LOCK_TAG = "QGroundControl";

    private static QGCActivity m_instance = null;

    private PowerManager.WakeLock m_wakeLock;
    private WifiManager.MulticastLock m_wifiMulticastLock;

    private Pipeline pipeline = null;


    public QGCActivity() {
        m_instance = this;
    }

    /**
     * Returns the singleton instance of QGCActivity.
     *
     * @return The current instance of QGCActivity.
     */
    public static QGCActivity getInstance() {
        return m_instance;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        nativeInit();
        acquireWakeLock();
        keepScreenOn();
        setupMulticastLock();

        QGCUsbSerialManager.initialize(this);

        RCSDKManager.INSTANCE.initSDK(this, new SDKManagerCallBack() {
            @Override
            public void onRcConnected() {
                        Pipeline pipeline = PipelineManager.INSTANCE.createPipeline(Uart.UART0);
                        pipeline.setOnCommListener(getCommListener(0, "SkyDroidLink"));
                        PipelineManager.INSTANCE.connectPipeline(pipeline);
                        m_instance.pipeline = pipeline;
            }

            @Override
            public void onRcConnectFail(SkyException e) {

            }

            @Override
            public void onRcDisconnect() {

            }
        });
        RCSDKManager.INSTANCE.connectToRC();
    }

    @Override
    protected void onDestroy() {
        try {
            releaseMulticastLock();
            releaseWakeLock();
            QGCUsbSerialManager.cleanup(this);
        } catch (final Exception e) {
            Log.e(TAG, "Exception onDestroy()", e);
        }
        RCSDKManager.INSTANCE.disconnectRC();
        Pipeline p = this.pipeline;
        if (p != null){
            PipelineManager.INSTANCE.disconnectPipeline(p);
        }
        super.onDestroy();
    }

    private CommListener getCommListener(int type, String tag) {
        return new CommListener() {
                    @Override
                    public void onConnectSuccess() {
                            qgcLogDebug(tag + " connected");
                    }

                    @Override
                    public void onConnectFail(SkyException e) {
                            qgcLogDebug(tag + " connect Failed" + e);
                    }

                    @Override
                    public void onDisconnect() {
                            qgcLogDebug(tag + " disconnected");
                    }

                    @Override
                    public void onReadData(byte[] bytes) {
                            if (type == 0) {
                                    jniFlightControlDataRecv(bytes,bytes.length);
                            }

                    }
        };
    }

    /**
     * Keeps the screen on by adding the appropriate window flag.
     */
    private void keepScreenOn() {
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    /**
     * Acquires a wake lock to keep the CPU running.
     */
    private void acquireWakeLock() {
        final PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        m_wakeLock = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK, SCREEN_BRIGHT_WAKE_LOCK_TAG);
        if (m_wakeLock != null) {
            m_wakeLock.acquire();
        } else {
            Log.w(TAG, "SCREEN_BRIGHT_WAKE_LOCK not acquired!");
        }
    }

    /**
     * Releases the wake lock if held.
     */
    private void releaseWakeLock() {
        if (m_wakeLock != null && m_wakeLock.isHeld()) {
            m_wakeLock.release();
        }
    }

    /**
     * Sets up a multicast lock to allow multicast packets.
     */
    private void setupMulticastLock() {
        if (m_wifiMulticastLock == null) {
            final WifiManager wifi = (WifiManager) getSystemService(Context.WIFI_SERVICE);
            m_wifiMulticastLock = wifi.createMulticastLock(MULTICAST_LOCK_TAG);
            m_wifiMulticastLock.setReferenceCounted(true);
        }

        m_wifiMulticastLock.acquire();
        Log.d(TAG, "Multicast lock: " + m_wifiMulticastLock.toString());
    }

    /**
     * Releases the multicast lock if held.
     */
    private void releaseMulticastLock() {
        if (m_wifiMulticastLock != null && m_wifiMulticastLock.isHeld()) {
            m_wifiMulticastLock.release();
            Log.d(TAG, "Multicast lock released.");
        }
    }

    public static boolean writeSkydroidData(byte[] data) {
            //m_instance.qgcLogDebug("==========writeSkydroidData:"+data.length);
            //Log.d(TAG, "==========writeSkydroidData:"+data.length);
            if( m_instance.pipeline != null)
                m_instance.pipeline.writeData(data);
            return m_instance.pipeline != null;
        }

    public static String getSDCardPath() {
        StorageManager storageManager = (StorageManager)m_instance.getSystemService(Activity.STORAGE_SERVICE);
        List<StorageVolume> volumes = storageManager.getStorageVolumes();
        Method mMethodGetPath;
        String path = "";
        for (StorageVolume vol : volumes) {
            try {
                mMethodGetPath = vol.getClass().getMethod("getPath");
            } catch (NoSuchMethodException e) {
                e.printStackTrace();
                continue;
            }
            try {
                path = (String) mMethodGetPath.invoke(vol);
            } catch (Exception e) {
                e.printStackTrace();
                continue;
            }

            if (vol.isRemovable() == true) {
                Log.i(TAG, "removable sd card mounted " + path);
                return path;
            } else {
                Log.i(TAG, "storage mounted " + path);
            }
        }
        return "";
    }

    // Native C++ functions
    public native boolean nativeInit();
    public native void qgcLogDebug(final String message);
    public native void qgcLogWarning(final String message);
    public native void jniFlightControlDataRecv(byte[] data, int len);
}
