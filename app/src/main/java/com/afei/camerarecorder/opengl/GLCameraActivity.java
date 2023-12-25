package com.afei.camerarecorder.opengl;

import android.os.Bundle;
import android.util.Log;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import com.afei.camerarecorder.R;
import com.afei.camerarecorder.camera.CameraConfig;
import com.afei.camerarecorder.camera.CameraModule;
import com.afei.camerarecorder.databinding.ActivityGlCameraBinding;

public class GLCameraActivity extends AppCompatActivity implements View.OnClickListener {

    private final String TAG = getClass().getSimpleName();
    private ActivityGlCameraBinding mBinding;
    private CameraModule mCameraModule;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mBinding = ActivityGlCameraBinding.inflate(getLayoutInflater());
        setContentView(mBinding.getRoot());
        init();
    }

    private void init() {
        CameraConfig config = CameraConfig.sCameraConfig;
        mCameraModule = new CameraModule(this, config);
        mBinding.cameraView.setCameraModule(mCameraModule);
        mBinding.startRecorderIv.setOnClickListener(this::onClick);
        mBinding.stopRecorderIv.setOnClickListener(this::onClick);
        mBinding.messageTv.setText("Video save path: /sdcard/DCIM/CameraRecorder");
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.start_recorder_iv) {
            startRecorder();
        } else if (id == R.id.stop_recorder_iv) {
            stopRecorder();
        } else {
            Log.w(TAG, "unknown view id = " + id);
        }
    }

    private void startRecorder() {
        mCameraModule.startRecorder();
        mBinding.startRecorderIv.setVisibility(View.GONE);
        mBinding.stopRecorderIv.setVisibility(View.VISIBLE);
    }

    private void stopRecorder() {
        mCameraModule.stopRecorder();
        mBinding.startRecorderIv.setVisibility(View.VISIBLE);
        mBinding.stopRecorderIv.setVisibility(View.GONE);
    }
}