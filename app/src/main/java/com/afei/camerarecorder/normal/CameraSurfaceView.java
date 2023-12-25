package com.afei.camerarecorder.normal;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Size;
import android.view.SurfaceHolder;

import com.afei.camerarecorder.camera.CameraModule;

public class CameraSurfaceView extends AutoFitSurfaceView {

    private static final String TAG = "CameraSurfaceView";

    private CameraModule mCameraModule;
    private Size mPreviewSize;

    public CameraSurfaceView(Context context) {
        this(context, null);
    }

    public CameraSurfaceView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        getHolder().addCallback(mSurfaceHolderCallback);
        setKeepScreenOn(true);
    }

    public void setCameraModule(CameraModule cameraModule) {
        if (cameraModule != null) {
            mCameraModule = cameraModule;
            mPreviewSize = mCameraModule.getPreviewSize();
        }
    }

    public CameraModule getCameraModule() {
        return mCameraModule;
    }

    private SurfaceHolder.Callback mSurfaceHolderCallback = new SurfaceHolder.Callback() {
        @Override
        public void surfaceCreated(SurfaceHolder holder) {
            if (mPreviewSize != null) {
                holder.setFixedSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
            }
        }

        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            Log.d(TAG, "surfaceChanged: width: " + width + ", height: " + height);
            if (mCameraModule == null || mPreviewSize == null) {
                Log.e(TAG, "mCameraModule or mPreviewSize is null!");
                return;
            }
            setAspectRatio(mPreviewSize.getHeight(), mPreviewSize.getWidth()); //横向显示
            float ratio;
            if (width > height) {
                ratio = height * 1.0f / width;
            } else {
                ratio = width * 1.0f / height;
            }
            if (ratio * mPreviewSize.getWidth() == mPreviewSize.getHeight()) {
                mCameraModule.setPreviewSurface(holder.getSurface()); // 等view的大小固定后在设置surface
                mCameraModule.openCamera();
            }
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
            Log.v(TAG, "surfaceDestroyed");
            if (mCameraModule != null) {
                mCameraModule.releaseCamera();
            }
        }
    };

}
