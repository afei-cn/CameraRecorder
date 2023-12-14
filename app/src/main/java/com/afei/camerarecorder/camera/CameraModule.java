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
import android.media.MediaCodec;
import android.media.MediaRecorder;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Size;
import android.view.Surface;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.concurrent.Executor;
import java.util.concurrent.locks.ReentrantLock;

public class CameraModule {

    private final String TAG = "CameraModule";
    /* 相机状态 */
    private final int CAMERA_STATE_OPENING = 0x001;
    private final int CAMERA_STATE_OPENED = 0x002;
    private final int CAMERA_STATE_CLOSE = 0x003;
    private final int CAMERA_STATE_ERROR = 0x004;
    private final int CAMERA_STATE_PREVIEW = 0x005;

    /* 相机通用*/
    private Activity mActivity;
    private CameraManager mCameraManager; // 相机管理者
    private CameraCharacteristics mCameraCharacteristics; // 相机属性
    private CameraDevice mCameraDevice; // 相机对象
    private CameraCaptureSession mCameraSession; // 相机事务
    private Surface mPreviewSurface;  // 预览的Surface
    private CaptureRequest.Builder mRequestBuilder;
    private Handler mCameraHandler;
    private HandlerThread mCameraThread;
    private ReentrantLock mCameraStateLock = new ReentrantLock();
    private int mCameraState = CAMERA_STATE_CLOSE;
    private int mDisplayRotation; // 原始画面顺时针旋转该角度(0, 90, 180, 270)后画面朝上，用于设置视频方向

    /* 录制相关*/
    private Surface mRecordSurface;
    private MediaRecorder mMediaRecorder;
    private boolean mIsRecording;  // 是否正在录制

    private CameraConfig mCameraConfig;
    private Size mPreviewSize;

    private CameraDevice.StateCallback mCameraOpenCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            mCameraState = CAMERA_STATE_OPENED;
            mCameraDevice = camera;
            createVideoSession();  // 相机打开后开始创建session
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
        if (mPreviewSurface == null) {
            Log.e(TAG, "Open camera failed! No PreviewSurface");
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

    private void startBackgroundThread() {
        if (mCameraThread == null || mCameraHandler == null) {
            Log.v(TAG, "startBackgroundThread");
            mCameraThread = new HandlerThread("CameraBackground");
            mCameraThread.start();
            mCameraHandler = new Handler(mCameraThread.getLooper());
        }
    }

    private void initDisplayRotation(CameraCharacteristics cameraCharacteristics) {
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

    private void createVideoSession() {
        Log.v(TAG, "createVideoSession start...");
        try {
            // video surface
            mRecordSurface = MediaCodec.createPersistentInputSurface();
            mMediaRecorder = createRecorder();
            ArrayList<Surface> sessionSurfaces = new ArrayList<>();
            sessionSurfaces.add(mPreviewSurface);
            sessionSurfaces.add(mRecordSurface);
            createPreviewRequest();
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                ArrayList<OutputConfiguration> outputConfigurations = new ArrayList<>();
                for (Surface surface : sessionSurfaces) {
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
                mCameraDevice.createCaptureSession(sessionSurfaces, mSessionCreateCallback, mCameraHandler);
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void createPreviewRequest() {
        CaptureRequest.Builder builder;
        try {
            builder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
        } catch (CameraAccessException e) {
            e.printStackTrace();
            Log.e(TAG, "setUpPreviewRequest, Camera access failed");
            return;
        }
        builder.addTarget(mPreviewSurface);
        builder.addTarget(mRecordSurface);
        applyCommonSettings(builder);
        mRequestBuilder = builder;
    }

    private void applyCommonSettings(CaptureRequest.Builder builder) {
        if (builder == null) {
            return;
        }
        builder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);
        builder.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_VIDEO);  // 设置聚焦
        builder.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO); // 设置白平衡
        builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH); // 设置曝光
        // applyExposure(builder);
        // applyIso(builder);
    }

    private CameraCaptureSession.StateCallback mSessionCreateCallback = new CameraCaptureSession.StateCallback() {
        @Override
        public void onConfigured(@NonNull CameraCaptureSession session) {
            mCameraSession = session;
            startPreview();  // 开始预览
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

    private MediaRecorder createRecorder() {
        MediaRecorder mediaRecorder = new MediaRecorder();
        try {
            File tmpFile = configRecorder(mediaRecorder);
            if (tmpFile != null) {
                // 创建后有个空的临时文件删除一下，虽然也可以在点击开始录制时依旧保存到这个文件中，但这样文件创建的时间信息可能会有较大差异
                tmpFile.delete();
            }
        } catch (IOException e) {
            e.printStackTrace();
            Log.e(TAG, "createRecorder error: " + e.getMessage());
        }
        return mediaRecorder;
    }

    private File configRecorder(@NonNull MediaRecorder mediaRecorder) throws IOException {
        if (mediaRecorder == null) {
            Log.e(TAG, "configRecorder failed! mediaRecorder is null");
            return null;
        }
        mediaRecorder.reset();
        mediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE); // 设置视频来源
        mediaRecorder.setVideoEncodingBitRate(mPreviewSize.getWidth() * mPreviewSize.getHeight() * 8); // 设置比特率
        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC); // 设置音频来源
        mediaRecorder.setAudioEncodingBitRate(96000);  // 设置音频编码比特率（一般音乐和语音录制）
        mediaRecorder.setAudioSamplingRate(44100);  // 设置音频采样率（CD音质）
        mediaRecorder.setCaptureRate(30); // 捕获率
        mediaRecorder.setOrientationHint(mDisplayRotation);  // 设置录制视频时的预期方向，取值为 0、90、180 或 270

        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4); // 设置输出格式
        mediaRecorder.setVideoSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());  // 设置视频宽高
        mediaRecorder.setVideoFrameRate(30); // 设置帧数
        mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264); // 设置视频编码格式
        mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC); // 设置音频编码格式

        mediaRecorder.setInputSurface(mRecordSurface);
        File outputFile = getOutputFile();
        mediaRecorder.setOutputFile(outputFile);
        mediaRecorder.prepare();
        mIsRecording = false;
        return outputFile;
    }

    private File getOutputFile() {
        File saveDirectory = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
                "CameraRecorder");
        saveDirectory.mkdirs();
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyyMMdd_HHmmss");
        String fileName = simpleDateFormat.format(new Date(System.currentTimeMillis())) + ".mp4";
        File outputFile = new File(saveDirectory, fileName);
        return outputFile;
    }

    public void startRecorder() {
        if (mCameraState != CAMERA_STATE_PREVIEW || mMediaRecorder == null) {
            Log.e(TAG, "Start Recorder failed!");
            return;
        }
        try {
            configRecorder(mMediaRecorder);
            mMediaRecorder.start();
            mIsRecording = true;
            Log.i(TAG, "startRecorder...");
        } catch (IOException e) {
            Log.e(TAG, "startRecorder failed! " + e.getMessage());
        }
    }

    public void stopRecorder() {
        if (mMediaRecorder != null && mIsRecording) {
            Log.i(TAG, "stopRecorder...");
            mMediaRecorder.stop();
            mIsRecording = false;
        }
    }

    private void releaseRecorder() {
        if (mMediaRecorder == null) {
            return;
        }
        stopRecorder();
        mMediaRecorder.reset();
        mMediaRecorder.release();
        mMediaRecorder = null;
    }

    public void releaseCamera() {
        if (mCameraState == CAMERA_STATE_CLOSE) {
            Log.w(TAG, "camera is closed");
            return;
        }
        mCameraStateLock.lock();
        Log.v(TAG, "releaseCamera");
        releaseRecorder();  // 如果正在录制，则先停止录制
        stopPreview();
        closeCameraSession();
        closeCameraDevice();
        stopBackgroundThread(); // 对应 `startBackgroundThread()`
        mCameraState = CAMERA_STATE_CLOSE;
        mCameraStateLock.unlock();
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

    private void closeCameraSession() {
        if (mCameraSession != null) {
            mCameraSession.close();
            mCameraSession = null;
        }
    }

    private void closeCameraDevice() {
        if (mCameraDevice != null) {
            mCameraDevice.close();
            mCameraDevice = null;
        }
    }

    private void stopBackgroundThread() {
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

    private class HandlerExecutor implements Executor {
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
