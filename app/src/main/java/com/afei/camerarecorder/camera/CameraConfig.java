package com.afei.camerarecorder.camera;

import android.util.Size;

import java.util.List;

public class CameraConfig {

    public static CameraConfig sCameraConfig;  // global for all Activity access

    private String mCameraId;
    private Size mPreviewSize;
    private Size mPictureSize;
    private float mExposure;
    private List<Size> mPreviewSizes;  // all support preview size
    private List<Size> mPictureSizes;  // all support picture size

    public static CameraConfig createDefault() {
        return new Builder().build();
    }

    public CameraConfig() {
    }

    private CameraConfig(Builder builder) {
        mCameraId = builder.cameraId;
        mPreviewSize = builder.previewSize;
        mPictureSize = builder.pictureSize;
        mExposure = builder.exposure;
        mPreviewSizes = builder.previewSizes;
        mPictureSizes = builder.pictureSizes;
    }

    public String getCameraId() {
        return mCameraId;
    }

    public void setCameraId(String cameraId) {
        mCameraId = cameraId;
    }

    public Size getPreviewSize() {
        return mPreviewSize;
    }

    public void setPreviewSize(Size previewSize) {
        mPreviewSize = previewSize;
    }

    public Size getPictureSize() {
        return mPictureSize;
    }

    public void setPictureSize(Size pictureSize) {
        mPictureSize = pictureSize;
    }

    public float getExposure() {
        return mExposure;
    }

    public void setExposure(float exposure) {
        mExposure = exposure;
    }

    public List<Size> getPreviewSizes() {
        return mPreviewSizes;
    }

    public void setPreviewSizes(List<Size> previewSizes) {
        this.mPreviewSizes = previewSizes;
    }

    public List<Size> getPictureSizes() {
        return mPictureSizes;
    }

    public void setPictureSizes(List<Size> pictureSizes) {
        mPictureSizes = pictureSizes;
    }

    public static class Builder {
        private String cameraId;
        private Size previewSize = null;
        private Size pictureSize = null;
        private float exposure = 0;
        private List<Size> previewSizes = null;
        private List<Size> pictureSizes = null;

        public Builder setCameraId(String cameraId) {
            this.cameraId = cameraId;
            return this;
        }

        public Builder setPreviewSize(Size previewSize) {
            if (previewSize != null) {
                this.previewSize = previewSize;
            }
            return this;
        }

        public Builder setPictureSize(Size pictureSize) {
            if (pictureSize != null) {
                this.pictureSize = pictureSize;
            }
            return this;
        }

        public Builder setExposure(float exposure) {
            this.exposure = exposure;
            return this;
        }

        public Builder setPreviewSizes(List<Size> previewSizes) {
            this.previewSizes = previewSizes;
            return this;
        }

        public Builder setPictureSizes(List<Size> pictureSizes) {
            this.pictureSizes = pictureSizes;
            return this;
        }

        public CameraConfig build() {
            return new CameraConfig(this);
        }
    }

}
