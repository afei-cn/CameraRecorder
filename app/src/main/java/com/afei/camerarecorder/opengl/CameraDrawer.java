package com.afei.camerarecorder.opengl;

import android.opengl.GLES11Ext;
import android.opengl.GLES30;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

public class CameraDrawer {

    private static final String TAG = "CameraDrawer";

    private static final String CAMERA_PREVIEW_VSH = "#version 300 es\n" +
            "\n" +
            "layout(location = 0) in vec4 v_Position;\n" +
            "layout(location = 1) in vec2 v_TextureCoord;\n" +
            "\n" +
            "out vec2 texture_coord;\n" +
            "\n" +
            "void main() {\n" +
            "    gl_Position = v_Position;\n" +
            "    texture_coord = v_TextureCoord;\n" +
            "}";

    private static final String CAMERA_PREVIEW_FSH = "#version 300 es\n" +
            "\n" +
            "#extension GL_OES_EGL_image_external_essl3 : require\n" +
            "\n" +
            "precision mediump float;\n" +
            "\n" +
            "uniform samplerExternalOES camera_texture;\n" +
            "in vec2 texture_coord;\n" +
            "out vec4 out_color;\n" +
            "\n" +
            "void main() {\n" +
            "    out_color = vec4(1.0) - texture(camera_texture, texture_coord);\n" +
            "}";

    private FloatBuffer mVertexBuffer;
    private FloatBuffer mBackTextureBuffer;
    private FloatBuffer mFrontTextureBuffer;
    private ByteBuffer mDrawListBuffer;
    private int mProgram;
    private int mOESTextureId = -1;

    private static final float VERTEXES[] = {
            -1.0f, 1.0f,
            -1.0f, -1.0f,
            1.0f, -1.0f,
            1.0f, 1.0f,
    };

    // 后置摄像头使用的纹理坐标
    private static final float TEXTURE_BACK[] = {
            0.0f, 1.0f,
            1.0f, 1.0f,
            1.0f, 0.0f,
            0.0f, 0.0f,
    };

    // 前置摄像头使用的纹理坐标
    private static final float TEXTURE_FRONT[] = {
            1.0f, 1.0f,
            0.0f, 1.0f,
            0.0f, 0.0f,
            1.0f, 0.0f,
    };

    private static final byte VERTEX_ORDER[] = {0, 1, 2, 3}; // order to draw vertices

    private final int VERTEX_SIZE = 2;
    private final int VERTEX_STRIDE = VERTEX_SIZE * 4;

    public CameraDrawer() {
        // init float buffer for vertex coordinates
        mVertexBuffer = ByteBuffer.allocateDirect(VERTEXES.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        mVertexBuffer.put(VERTEXES).position(0);

        // init float buffer for texture coordinates
        mBackTextureBuffer = ByteBuffer.allocateDirect(TEXTURE_BACK.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        mBackTextureBuffer.put(TEXTURE_BACK).position(0);
        mFrontTextureBuffer =
                ByteBuffer.allocateDirect(TEXTURE_FRONT.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        mFrontTextureBuffer.put(TEXTURE_FRONT).position(0);

        // init byte buffer for draw list
        mDrawListBuffer = ByteBuffer.allocateDirect(VERTEX_ORDER.length).order(ByteOrder.nativeOrder());
        mDrawListBuffer.put(VERTEX_ORDER).position(0);

        mProgram = OpenGLUtils.createProgram(CAMERA_PREVIEW_VSH, CAMERA_PREVIEW_FSH);
        mOESTextureId = OpenGLUtils.getExternalOESTextureID();
    }

    public int getOESTextureId() {
        return mOESTextureId;
    }


    public void drawPreview(boolean isFrontCamera) {
        GLES30.glUseProgram(mProgram);
        GLES30.glEnable(GLES30.GL_CULL_FACE);
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0);
        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, mOESTextureId); // bind external camera texture
        GLES30.glEnableVertexAttribArray(0);
        GLES30.glVertexAttribPointer(0, VERTEX_SIZE, GLES30.GL_FLOAT, false, VERTEX_STRIDE, mVertexBuffer);
        GLES30.glEnableVertexAttribArray(1);
        GLES30.glVertexAttribPointer(1, VERTEX_SIZE, GLES30.GL_FLOAT, false, VERTEX_STRIDE,
                isFrontCamera ? mFrontTextureBuffer : mBackTextureBuffer);
        GLES30.glDrawElements(GLES30.GL_TRIANGLE_FAN, VERTEX_ORDER.length, GLES30.GL_UNSIGNED_BYTE, mDrawListBuffer);
        GLES30.glDisableVertexAttribArray(0);
        GLES30.glDisableVertexAttribArray(1);
    }

}
