package com.afei.camerarecorder.camera;

import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.params.MeteringRectangle;
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
                CameraConfig cameraConfig = new CameraConfig.Builder()
                        .setCameraId(cameraId)
                        .setPreviewSizes(Arrays.asList(previewSizes))
                        .setPreviewSize(previewSize)
                        .setPictureSizes(Arrays.asList(pictureSizes))
                        .setPictureSize(pictureSize)
                        .build();
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

    public static MeteringRectangle getAFAERegion(float x, float y, int viewWidth, int viewHeight, int rotation,
                                                  boolean isFrontCamera, float multiple, Rect cropRegion) {
        // do rotate and mirror
        RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
        Matrix matrix1 = new Matrix();
        matrix1.setRotate(rotation);
        matrix1.postScale(isFrontCamera ? -1 : 1, 1);
        matrix1.invert(matrix1);
        matrix1.mapRect(viewRect);
        // get scale and translate matrix
        Matrix matrix2 = new Matrix();
        RectF cropRect = new RectF(cropRegion);
        matrix2.setRectToRect(viewRect, cropRect, Matrix.ScaleToFit.CENTER);
        // get out region
        int side = (int) (Math.max(viewWidth, viewHeight) / 8 * multiple);
        RectF outRect = new RectF(x - side / 2, y - side / 2, x + side / 2, y + side / 2);
        matrix1.mapRect(outRect);
        matrix2.mapRect(outRect);

        Rect meteringRect = new Rect((int) outRect.left, (int) outRect.top, (int) outRect.right, (int) outRect.bottom);
        meteringRect.left = CameraUtil.clamp(meteringRect.left, cropRegion.left, cropRegion.right);
        meteringRect.top = CameraUtil.clamp(meteringRect.top, cropRegion.top, cropRegion.bottom);
        meteringRect.right = CameraUtil.clamp(meteringRect.right, cropRegion.left, cropRegion.right);
        meteringRect.bottom = CameraUtil.clamp(meteringRect.bottom, cropRegion.top, cropRegion.bottom);
        return new MeteringRectangle(meteringRect, 1000);
    }

    public static int clamp(int x, int min, int max) {
        if (x > max) return max;
        if (x < min) return min;
        return x;
    }

    public static float clamp(float x, float min, float max) {
        if (x > max) return max;
        if (x < min) return min;
        return x;
    }

    public static String millisecondToTimeString(long milliSeconds, boolean displayCentiSeconds) {
        long seconds = milliSeconds / 1000; // round down to compute seconds
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long remainderMinutes = minutes - (hours * 60);
        long remainderSeconds = seconds - (minutes * 60);
        StringBuilder timeStringBuilder = new StringBuilder();
        // Hours
        if (hours > 0) {
            if (hours < 10) {
                timeStringBuilder.append('0');
            }
            timeStringBuilder.append(hours);
            timeStringBuilder.append(':');
        }
        // Minutes
        if (remainderMinutes < 10) {
            timeStringBuilder.append('0');
        }
        timeStringBuilder.append(remainderMinutes);
        timeStringBuilder.append(':');
        // Seconds
        if (remainderSeconds < 10) {
            timeStringBuilder.append('0');
        }
        timeStringBuilder.append(remainderSeconds);
        // Centi seconds
        if (displayCentiSeconds) {
            timeStringBuilder.append('.');
            long remainderCentiSeconds = (milliSeconds - seconds * 1000) / 10;
            if (remainderCentiSeconds < 10) {
                timeStringBuilder.append('0');
            }
            timeStringBuilder.append(remainderCentiSeconds);
        }
        return timeStringBuilder.toString();
    }

}
