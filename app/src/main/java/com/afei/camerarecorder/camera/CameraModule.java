package com.afei.camerarecorder.camera;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.OutputConfiguration;
import android.hardware.camera2.params.SessionConfiguration;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Size;
import android.view.Surface;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.locks.ReentrantLock;

public class CameraModule {

    private static final String TAG = "CameraModule";
    /* 相机状态 */
    public static final int CAMERA_STATE_OPENING = 0x001;
    public static final int CAMERA_STATE_OPENED = 0x002;
    public static final int CAMERA_STATE_CLOSE = 0x003;
    public static final int CAMERA_STATE_ERROR = 0x004;
    public static final int CAMERA_STATE_PREVIEW = 0x005;

    /* 相机通用*/
    protected Activity mActivity;
    protected CameraManager mCameraManager; // 相机管理者
    protected CameraCharacteristics mCameraCharacteristics; // 相机属性
    protected CameraDevice mCameraDevice; // 相机对象
    protected CameraCaptureSession mCameraSession; // 相机事务
    protected List<Surface> mOutputSurfaces;
    protected Surface mPreviewSurface;  // 预览的Surface
    protected CaptureRequest.Builder mRequestBuilder;
    protected Handler mCameraHandler;
    protected HandlerThread mCameraThread;
    protected ReentrantLock mCameraStateLock = new ReentrantLock();
    protected int mCameraState = CAMERA_STATE_CLOSE;
    protected int mDisplayRotation; // 原始Sensor画面顺时针旋转该角度后，画面朝上。(0, 90, 180, 270)

    protected CameraConfig mCameraConfig;
    protected Size mPreviewSize;

    protected CameraDevice.StateCallback mCameraOpenCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            mCameraState = CAMERA_STATE_OPENED;
            mCameraDevice = camera;
            createVideoSession();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            mCameraState = CAMERA_STATE_ERROR;
            releaseCamera();
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            Log.e(TAG, "Camera onError: " + error);
            mCameraState = CAMERA_STATE_ERROR;
            releaseCamera();
        }
    };

    public CameraModule(Activity activity, @NonNull CameraConfig config) {
        mActivity = activity;
        mCameraManager = (CameraManager) mActivity.getSystemService(Context.CAMERA_SERVICE);
        mCameraConfig = config;
        mPreviewSize = config.getPreviewSize();
    }


    public Size getPreviewSize() {
        return mPreviewSize;
    }

    public void setPreviewSurface(Surface previewSurface) {
        mPreviewSurface = previewSurface;
    }

    public void openCamera() {
        if (mCameraState != CAMERA_STATE_CLOSE) {
            Log.e(TAG, "only could open camera when closed");
            return;
        }
        if (ActivityCompat.checkSelfPermission(mActivity, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Open camera failed! No permission CAMERA.");
            return;
        }
        mCameraStateLock.lock();
        String cameraId = mCameraConfig.getCameraId();
        Log.i(TAG, "openCamera --> cameraId: " + cameraId);
        mCameraState = CAMERA_STATE_OPENING;
        startBackgroundThread();
        try {
            String[] cameraIdList = mCameraManager.getCameraIdList();
            boolean isValidCameraId = false;
            for (int i = 0; i < cameraIdList.length; i++) {
                if (cameraIdList[i].equals(cameraId)) {
                    isValidCameraId = true;
                }
            }
            if (isValidCameraId) {
                mCameraCharacteristics = mCameraManager.getCameraCharacteristics(cameraId);
                initDisplayRotation(mCameraCharacteristics);
                mCameraManager.openCamera(cameraId, mCameraOpenCallback, mCameraHandler);
            } else {
                Log.e(TAG, "openCamera failed! invalid camera id: " + cameraId);
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        } finally {
            mCameraStateLock.unlock();
        }
    }

    protected void startBackgroundThread() {
        if (mCameraThread == null || mCameraHandler == null) {
            Log.v(TAG, "startBackgroundThread");
            mCameraThread = new HandlerThread("CameraBackground");
            mCameraThread.start();
            mCameraHandler = new Handler(mCameraThread.getLooper());
        }
    }

    protected void initDisplayRotation(CameraCharacteristics cameraCharacteristics) {
        if (cameraCharacteristics == null || mActivity == null) {
            return;
        }
        int displayRotation = mActivity.getWindowManager().getDefaultDisplay().getRotation();
        switch (displayRotation) {
            case Surface.ROTATION_0:
                displayRotation = 90;
                break;
            case Surface.ROTATION_90:
                displayRotation = 0;
                break;
            case Surface.ROTATION_180:
                displayRotation = 270;
                break;
            case Surface.ROTATION_270:
                displayRotation = 180;
                break;
        }
        int sensorOrientation = cameraCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
        mDisplayRotation = (displayRotation + sensorOrientation + 270) % 360;
        Log.d(TAG, "mDisplayRotation: " + mDisplayRotation);
    }

    protected void createVideoSession() {
        Log.v(TAG, "createVideoSession start...");
        try {
            setUpSessionOutputs();
            createPreviewRequest();
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                ArrayList<OutputConfiguration> outputConfigurations = new ArrayList<>();
                for (Surface surface : mOutputSurfaces) {
                    if (surface != null) {
                        OutputConfiguration configuration = new OutputConfiguration(surface);
                        outputConfigurations.add(configuration);
                    }
                }
                SessionConfiguration sessionConfiguration = new SessionConfiguration(SessionConfiguration.SESSION_REGULAR,
                        outputConfigurations, new HandlerExecutor(mCameraHandler), mSessionCreateCallback);
                sessionConfiguration.setSessionParameters(mRequestBuilder.build());
//                try {
//                    boolean supported = mCameraDevice.isSessionConfigurationSupported(sessionConfiguration);
//                    Log.v(TAG, " createCaptureSessionWithSessionConfiguration result :" + supported);
//                } catch (CameraAccessException | IllegalArgumentException | NullPointerException e) {
//                    Log.w(TAG, " check isSessionConfigurationSupported sessionConfig error");
//                    e.printStackTrace();
//                }
                mCameraDevice.createCaptureSession(sessionConfiguration);
            } else {
                mCameraDevice.createCaptureSession(mOutputSurfaces, mSessionCreateCallback, mCameraHandler);
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    protected void setUpSessionOutputs() {
        mOutputSurfaces = new ArrayList<>();
        mOutputSurfaces.add(mPreviewSurface);
    }

    protected void createPreviewRequest() {
        CaptureRequest.Builder builder;
        try {
            builder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
        } catch (CameraAccessException e) {
            e.printStackTrace();
            Log.e(TAG, "setUpPreviewRequest, Camera access failed");
            return;
        }
        for (Surface output : mOutputSurfaces) {
            if (output != null) {
                builder.addTarget(output);
            }
        }
        applyCommonSettings(builder);
        mRequestBuilder = builder;
    }

    protected void applyCommonSettings(CaptureRequest.Builder builder) {
        if (builder == null) {
            return;
        }
        builder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);
        builder.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_AUTO);  // 设置自动聚焦
        builder.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO); // 设置自动白平衡
        builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH); // 设置自动曝光
        // applyExposure(builder);
        // applyIso(builder);
    }

    protected CameraCaptureSession.StateCallback mSessionCreateCallback = new CameraCaptureSession.StateCallback() {
        @Override
        public void onConfigured(@NonNull CameraCaptureSession session) {
            mCameraSession = session;
            startPreview();
        }

        @Override
        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
            releaseCamera();
        }
    };

    public void startPreview() {
        Log.v(TAG, "startPreview");
        if (mCameraSession == null || mRequestBuilder == null) {
            Log.w(TAG, "startPreview failed. mCaptureSession or mCurrentRequestBuilder is null");
            return;
        }
        try {
            // 开始预览，即一直发送预览的请求
            CaptureRequest captureRequest = mRequestBuilder.build();
            mCameraSession.setRepeatingRequest(captureRequest, null, mCameraHandler);
            mCameraState = CAMERA_STATE_PREVIEW;
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    public void releaseCamera() {
        if (mCameraState == CAMERA_STATE_CLOSE) {
            Log.w(TAG, "camera is closed");
            return;
        }
        mCameraStateLock.lock();
        Log.v(TAG, "releaseCamera");
        closeCameraSession();
        closeCameraDevice();
        stopBackgroundThread(); // 对应 openCamera() 方法中的 startBackgroundThread()
        mCameraState = CAMERA_STATE_CLOSE;
        mCameraStateLock.unlock();
    }

    protected void closeCameraSession() {
        if (mCameraSession != null) {
            stopPreview();
            mCameraSession.close();
            mCameraSession = null;
        }
    }

    public void stopPreview() {
        Log.v(TAG, "stopPreview");
        if (mCameraSession == null) {
            Log.w(TAG, "stopPreview: mCaptureSession is null");
            return;
        }
        try {
            mCameraSession.stopRepeating();
            mCameraState = CAMERA_STATE_OPENED;
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    protected void closeCameraDevice() {
        if (mCameraDevice != null) {
            mCameraDevice.close();
            mCameraDevice = null;
        }
    }

    protected void stopBackgroundThread() {
        Log.v(TAG, "stopBackgroundThread");
        if (mCameraThread != null) {
            mCameraThread.quitSafely();
            try {
                mCameraThread.join();
                mCameraThread = null;
                mCameraHandler = null;
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    protected class HandlerExecutor implements Executor {
        private final Handler ihandler;

        public HandlerExecutor(Handler handler) {
            ihandler = handler;
        }

        @Override
        public void execute(Runnable runCmd) {
            ihandler.post(runCmd);
        }
    }

}
