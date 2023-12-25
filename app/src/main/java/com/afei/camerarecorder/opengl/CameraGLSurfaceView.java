package com.afei.camerarecorder.opengl;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.opengl.GLES30;
import android.opengl.GLSurfaceView;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.SurfaceHolder;

import com.afei.camerarecorder.camera.CameraModule;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class CameraGLSurfaceView extends AutoFitGLSurfaceView {

    private static final String TAG = "CameraGLSurfaceView";

    private CameraDrawer mCameraDrawer;
    private SurfaceTexture mSurfaceTexture;
    private CameraModule mCameraModule;
    private Size mPreviewSize;

    public CameraGLSurfaceView(Context context) {
        this(context, null);
    }

    public CameraGLSurfaceView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        setKeepScreenOn(true);
        setEGLContextClientVersion(3);
        setRenderer(mRenderer);
        setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
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

    private Renderer mRenderer = new Renderer() {

        @Override
        public void onSurfaceCreated(GL10 gl, EGLConfig config) {
            Log.d(TAG, "onSurfaceCreated");
            mCameraDrawer = new CameraDrawer();
            mSurfaceTexture = new SurfaceTexture(mCameraDrawer.getOESTextureId());
            mSurfaceTexture.setOnFrameAvailableListener(surfaceTexture -> {
                if (!surfaceTexture.isReleased()) {
                    requestRender();
                }
            });
            mSurfaceTexture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
            mCameraModule.setPreviewSurface(new Surface(mSurfaceTexture));
            mCameraModule.openCamera();
        }

        @Override
        public void onSurfaceChanged(GL10 gl, int width, int height) {
            Log.d(TAG, "surfaceChanged: width: " + width + ", height: " + height);
            setAspectRatio(mPreviewSize.getHeight(), mPreviewSize.getWidth());  // make display portrait
            GLES30.glViewport(0, 0, width, height);
        }

        @Override
        public void onDrawFrame(GL10 gl) {
            mCameraDrawer.drawPreview(mCameraModule.isFrontCamera());
            mSurfaceTexture.updateTexImage();
        }
    };

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        super.surfaceDestroyed(holder);
        mCameraModule.releaseCamera();
    }
}
