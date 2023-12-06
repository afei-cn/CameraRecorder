package com.afei.camerarecorder;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.afei.camerarecorder.camera.CameraConfig;
import com.afei.camerarecorder.camera.CameraModule;
import com.afei.camerarecorder.databinding.ActivityCameraBinding;

public class CameraActivity extends AppCompatActivity {

    private final String TAG = getClass().getSimpleName();
    private ActivityCameraBinding mBinding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mBinding = ActivityCameraBinding.inflate(getLayoutInflater());
        setContentView(mBinding.getRoot());
        init();
    }

    private void init() {
        CameraConfig config = CameraConfig.sCameraConfig;
        CameraModule cameraModule = new CameraModule(this, config);
        mBinding.cameraView.setCameraModule(cameraModule);
    }
}