package com.afei.camerarecorder.camera;

import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.util.Size;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class CameraUtil {

    public static List<CameraConfig> getCameraInfo(Context context) {
        ArrayList<CameraConfig> cameraConfigs = new ArrayList<>();
        CameraManager cameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        try {
            String[] cameraIdList = cameraManager.getCameraIdList();
            for (int i = 0; i < cameraIdList.length; i++) {
                String cameraId = cameraIdList[i];
                CameraCharacteristics cameraCharacteristics = cameraManager.getCameraCharacteristics(cameraId);
                StreamConfigurationMap map = cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                Size[] previewSizes = map.getOutputSizes(SurfaceTexture.class);
                Size[] pictureSizes = map.getOutputSizes(ImageFormat.JPEG);
                Size previewSize = chooseOptimalSize(previewSizes, 1080, 0.75f);
                Size pictureSize = chooseOptimalSize(pictureSizes, 1080, 0.75f);
                CameraConfig cameraConfig =
                        new CameraConfig.Builder().setCameraId(cameraId).setPreviewSizes(Arrays.asList(previewSizes)).setPreviewSize(previewSize).setPictureSizes(Arrays.asList(pictureSizes)).setPictureSize(pictureSize).build();
                cameraConfigs.add(cameraConfig);
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        return cameraConfigs;
    }

    public static Size chooseOptimalSize(Size[] sizes, int dstSize, float aspectRatio) {
        if (sizes == null || sizes.length == 0) {
            return null;
        }
        int minDelta = Integer.MAX_VALUE; // 最小的差值，初始值应该设置大点保证之后的计算中会被重置
        int index = 0; // 最小的差值对应的索引坐标
        for (int i = 0; i < sizes.length; i++) {
            Size size = sizes[i];
            // 先判断比例是否相等
            if (size.getWidth() * aspectRatio == size.getHeight()) {
                int delta = Math.abs(dstSize - size.getHeight());
                if (delta == 0) {
                    return size;
                }
                if (minDelta > delta) {
                    minDelta = delta;
                    index = i;
                }
            }
        }
        return sizes[index];
    }
}
