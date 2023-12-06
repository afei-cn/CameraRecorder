package com.afei.camerarecorder;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.util.Size;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.afei.camerarecorder.camera.CameraConfig;
import com.afei.camerarecorder.camera.CameraUtil;
import com.afei.camerarecorder.databinding.ActivityMainBinding;

import java.util.List;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private final String TAG = getClass().getSimpleName();
    private static final int REQUEST_CODE = 200;
    private static final String[] PERMISSIONS = new String[]{
            android.Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA
    };

    private ActivityMainBinding mBinding;

    private List<CameraConfig> mCameraConfigList;
    private CameraConfig mCameraConfig;
    private String[] mCameraIds;
    private String[] mPreviewSizes;
    private String[] mPictureSizes;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mBinding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(mBinding.getRoot());
        init();
    }

    private void init() {
        checkPermission();
        mBinding.openCameraBtn.setOnClickListener(this::onClick);
        mBinding.cameraIdLayout.setOnClickListener(this::onClick);
        mBinding.previewSizeLayout.setOnClickListener(this::onClick);
        mBinding.pictureSizeLayout.setOnClickListener(this::onClick);
        mCameraConfigList = CameraUtil.getCameraInfo(this);
        if (mCameraConfigList == null || mCameraConfigList.size() == 0) {
            Log.w(TAG, "CameraUtil getCameraInfo is empty");
            return;
        }
        mCameraIds = new String[mCameraConfigList.size()];
        for (int i = 0; i < mCameraIds.length; i++) {
            mCameraIds[i] = mCameraConfigList.get(i).getCameraId();
        }
        updateUI(mCameraConfigList.get(0));
    }

    private void updateUI(CameraConfig config) {
        if (config != null) {
            mBinding.cameraIdTv.setText(config.getCameraId());
            mBinding.previewSizeTv.setText(getSizeString(config.getPreviewSize()));
            mBinding.pictureSizeTv.setText(getSizeString(config.getPictureSize()));
            // update size strings
            mPreviewSizes = getSizesString(config.getPreviewSizes());
            mPictureSizes = getSizesString(config.getPictureSizes());
        } else {
            mBinding.cameraIdTv.setText("-1");
            mBinding.previewSizeTv.setText("NULL");
            mBinding.pictureSizeTv.setText("NULL");
        }
        mCameraConfig = config;
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.camera_id_layout) {
            showChooseDialog(R.string.settings_camera_id, mCameraIds, (dialog, which) -> {
                updateUI(mCameraConfigList.get(which));
                dialog.dismiss();
            });
        } else if (v.getId() == R.id.preview_size_layout) {
            showChooseDialog(R.string.settings_preview_size, mPreviewSizes, (dialog, which) -> {
                mBinding.previewSizeTv.setText(mPreviewSizes[which]);
                mCameraConfig.setPreviewSize(mCameraConfig.getPreviewSizes().get(which));
                dialog.dismiss();
            });
        } else if (v.getId() == R.id.picture_size_layout) {
            showChooseDialog(R.string.settings_picture_size, mPictureSizes, (dialog, which) -> {
                mBinding.pictureSizeTv.setText(mPictureSizes[which]);
                mCameraConfig.setPictureSize(mCameraConfig.getPictureSizes().get(which));
                dialog.dismiss();
            });
        } else if (v.getId() == R.id.open_camera_btn) {
            startCameraActivity();
        } else {
            Log.w(TAG, "unknown view id = " + v.getId());
        }
    }

    private void showChooseDialog(@StringRes int titleId, String[] data, DialogInterface.OnClickListener listener) {
        if (data == null || data.length == 0) {
            Log.w(TAG, "showChooseDialog failed, input data is empty");
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(titleId)
                .setItems(data, listener)
                .setCancelable(true)
                .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                .show();
    }

    private void startCameraActivity() {
        if (checkPermission()) {
            CameraConfig.sCameraConfig = mCameraConfig;
            Intent intent = new Intent(this, CameraActivity.class);
            startActivity(intent);
        } else {
            Toast.makeText(this, "Missing permission!", Toast.LENGTH_SHORT).show();
        }
    }

    private boolean checkPermission() {
        for (String permission : PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, PERMISSIONS, REQUEST_CODE);
                return false;
            }
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE) {
            for (int i = 0; i < permissions.length; i++) {
                if (grantResults.length == 0 || grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "Please grant permission in Settings!", Toast.LENGTH_SHORT).show();
                    return;
                }
            }
        }
    }

    private String[] getSizesString(List<Size> sizes) {
        String[] sizesString = new String[sizes.size()];
        for (int i = 0; i < sizes.size(); i++) {
            sizesString[i] = getSizeString(sizes.get(i));
        }
        return sizesString;
    }

    private String getSizeString(Size size) {
        return size.getWidth() + " x " + size.getHeight();
    }

}