package com.benhero.glstudio.l6;

import android.content.Context;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.util.Log;

import com.benhero.glstudio.R;
import com.benhero.glstudio.util.LoggerConfig;
import com.benhero.glstudio.util.ShaderHelper;
import com.benhero.glstudio.util.TextureHelper;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * FrameBuffer的使用：屏幕外渲染
 *
 * @author Benhero
 */
public class FBORenderer6 implements GLSurfaceView.Renderer {
    private static final String VERTEX_SHADER = "" +
            "uniform mat4 u_Matrix;\n" +
            "attribute vec4 a_Position;\n" +
            // 纹理坐标：2个分量，S和T坐标
            "attribute vec2 a_texCoord;\n" +
            "varying vec2 v_texCoord;\n" +
            // a_Color：从外部传递进来的每个顶点的颜色值
            "attribute vec4 a_Color;\n" +
            // v_Color：将每个顶点的颜色值传递给片段着色器
            "varying vec4 v_Color;\n" +
            "void main()\n" +
            "{\n" +
            "    v_Color = a_Color;\n" +
            "    v_texCoord = a_texCoord;\n" +
            "    gl_Position = u_Matrix * a_Position;\n" +
            "}";
    private static final String FRAGMENT_SHADER = "" +
            "precision mediump float;\n" +
            "varying vec2 v_texCoord;\n" +
            "varying vec4 v_Color;\n" +
            // sampler2D：二维纹理数据的数组
            "uniform sampler2D u_TextureUnit;\n" +
            "void main()\n" +
            "{\n" +
            "    gl_FragColor = v_Color * texture2D(u_TextureUnit, v_texCoord);\n" +
            "}";
    private static final String A_POSITION = "a_Position";
    private static final String U_MATRIX = "u_Matrix";
    private static final String U_TEXTURE_UNIT = "u_TextureUnit";
    private static final String A_TEX_COORD = "a_texCoord";
    private static final String A_COLOR = "a_Color";

    private final Context mContext;
    private int mProgram;
    private final FloatBuffer mVertexData;
    private int aPositionLocation;
    private int uMatrixLocation;
    private int aColorLocation;

    /**
     * 纹理坐标索引
     */
    private int aTexCoordLocation;
    private int uTextureUnitLocation;
    /**
     * 颜色占用的向量个数
     */
    private static final int COLOR_COMPONENT_COUNT = 4;

    private static final int POSITION_COMPONENT_COUNT = 2;
    private static final int BYTES_PER_FLOAT = 4;

    private static final int STRIDE =
            (POSITION_COMPONENT_COUNT + COLOR_COMPONENT_COUNT) * BYTES_PER_FLOAT;

    private static final float[] POINT_DATA = {
            -1f, -1f, 1.0f, 1.0f, 1.0f, 0.9f,
            -1f, 1f, 1.0f, 1.0f, 1.0f, 0.9f,
            1f, 1f, 1.0f, 1.0f, 1.0f, 0.9f,
            1f, -1f, 1.0f, 1.0f, 1.0f, 0.9f,
    };

    private final float[] projectionMatrix = new float[]{
            1, 0, 0, 0,
            0, 1, 0, 0,
            0, 0, 1, 0,
            0, 0, 0, 1,
    };

    /**
     * 纹理坐标
     */
    private static final float[] TEX_VERTEX = {
            0, 1,
            0, 0,
            1, 0,
            1, 1,
    };
    /**
     * 纹理坐标中每个点占的向量个数
     */
    private static final int TEX_VERTEX_COMPONENT_COUNT = 2;
    private final FloatBuffer mTexVertexBuffer;
    /**
     * 纹理数据
     */
    private TextureHelper.TextureBean mTextureBean;

    private int[] mFrameBuffer = new int[1];
    private int[] mRenderBuffer = new int[1];
    private int[] mTexture = new int[1];

    private ByteBuffer mBuffer;

    private Callback mCallback;
    private int mWidth;
    private int mHeight;

    public interface Callback {
        void onCall(ByteBuffer data);
    }

    public void setCallback(Callback callback) {
        this.mCallback = callback;
    }

    public FBORenderer6(Context context) {
        mContext = context;

        mVertexData = ByteBuffer
                .allocateDirect(POINT_DATA.length * BYTES_PER_FLOAT)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .put(POINT_DATA);

        mTexVertexBuffer = ByteBuffer.allocateDirect(TEX_VERTEX.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .put(TEX_VERTEX);

    }

    @Override
    public void onSurfaceCreated(GL10 glUnused, EGLConfig config) {
        GLES20.glClearColor(1f, 1f, 1f, 0f);
        int vertexShader = ShaderHelper.compileVertexShader(VERTEX_SHADER);
        int fragmentShader = ShaderHelper.compileFragmentShader(FRAGMENT_SHADER);

        mProgram = ShaderHelper.linkProgram(vertexShader, fragmentShader);

        if (LoggerConfig.ON) {
            ShaderHelper.validateProgram(mProgram);
        }

        GLES20.glUseProgram(mProgram);


        // 开启纹理透明混合，这样才能绘制透明图片
//        GLES20.glEnable(GL10.GL_BLEND);
//        GLES20.glBlendFunc(GL10.GL_ONE, GL10.GL_ONE_MINUS_SRC_ALPHA);

        // 纹理数据
        mTextureBean = TextureHelper.loadTexture(mContext, R.drawable.tuzki);

        aPositionLocation = GLES20.glGetAttribLocation(mProgram, A_POSITION);
        uMatrixLocation = GLES20.glGetUniformLocation(mProgram, U_MATRIX);

        aColorLocation = GLES20.glGetAttribLocation(mProgram, A_COLOR);

        // 纹理索引
        aTexCoordLocation = GLES20.glGetAttribLocation(mProgram, A_TEX_COORD);
        uTextureUnitLocation = GLES20.glGetUniformLocation(mProgram, U_TEXTURE_UNIT);

        mVertexData.position(0);
        GLES20.glVertexAttribPointer(aPositionLocation, POSITION_COMPONENT_COUNT,
                GLES20.GL_FLOAT, false, STRIDE, mVertexData);
        GLES20.glEnableVertexAttribArray(aPositionLocation);

        // COLOR_COMPONENT_COUNT：从数组中每次读取3个向量
        // STRIDE：每次读取间隔是 (2个位置 + 4个颜色值) * Float占的Byte位
        mVertexData.position(POSITION_COMPONENT_COUNT);
        GLES20.glVertexAttribPointer(aColorLocation, COLOR_COMPONENT_COUNT,
                GLES20.GL_FLOAT, false, STRIDE, mVertexData);
        GLES20.glEnableVertexAttribArray(aColorLocation);

        // 加载纹理坐标
        mTexVertexBuffer.position(0);
        GLES20.glVertexAttribPointer(aTexCoordLocation, TEX_VERTEX_COMPONENT_COUNT, GLES20.GL_FLOAT, false, 0, mTexVertexBuffer);
        GLES20.glEnableVertexAttribArray(aTexCoordLocation);
    }

    @Override
    public void onSurfaceChanged(GL10 glUnused, int width, int height) {
        mWidth = width;
        mHeight = height;
        GLES20.glViewport(0, 0, mWidth, mHeight);
        final float aspectRatio = width > height ?
                (float) width / (float) height :
                (float) height / (float) width;
        if (width > height) {
            Matrix.orthoM(projectionMatrix, 0, -aspectRatio, aspectRatio, -1f, 1f, -1f, 1f);
        } else {
            Matrix.orthoM(projectionMatrix, 0, -1f, 1f, -aspectRatio, aspectRatio, -1f, 1f);
        }
        // 水平翻转
        Matrix.scaleM(projectionMatrix, 0, 1, -1, 1);
    }

    @Override
    public void onDrawFrame(GL10 glUnused) {
        if (!mIsRender) {
            return;
        }
        mIsRender = false;
        Log.e("JKL", "onDrawFrame: ");
        GLES20.glClear(GL10.GL_COLOR_BUFFER_BIT | GL10.GL_DEPTH_BUFFER_BIT);

        createEnvi();

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mFrameBuffer[0]);
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0,
                GLES20.GL_TEXTURE_2D, mTexture[0], 0);
        GLES20.glFramebufferRenderbuffer(GLES20.GL_FRAMEBUFFER, GLES20.GL_DEPTH_ATTACHMENT,
                GLES20.GL_RENDERBUFFER, mRenderBuffer[0]);

//        GLES20.glViewport((mWidth - mTextureBean.getWidth()) / 2, (mHeight - mTextureBean.getHeight()) / 2,
//                mTextureBean.getWidth(), mTextureBean.getHeight());
        GLES20.glViewport(0, 0, mWidth, mHeight);
        drawTexture();

        Log.i("JKL", "onDrawFrame: " + mWidth + " : " + mHeight + " : "
                + mTextureBean.getWidth() + " : " + mTextureBean.getHeight());

        mBuffer = ByteBuffer.allocate(mWidth * mHeight * 4);
        GLES20.glReadPixels(0,
                0,
                mWidth,
                mHeight,
                GLES20.GL_RGBA,
                GLES20.GL_UNSIGNED_BYTE, mBuffer);

        if (mCallback != null) {
            mCallback.onCall(mBuffer);
        }

        GLES20.glDeleteTextures(1, mTexture, 0);
        GLES20.glDeleteRenderbuffers(1, mRenderBuffer, 0);
        GLES20.glDeleteFramebuffers(1, mFrameBuffer, 0);
    }

    public void createEnvi() {
        // TODO: 18/1/22 添加注释
        GLES20.glGenFramebuffers(1, mFrameBuffer, 0);
        GLES20.glGenRenderbuffers(1, mRenderBuffer, 0);
        GLES20.glBindRenderbuffer(GLES20.GL_RENDERBUFFER, mRenderBuffer[0]);
        GLES20.glRenderbufferStorage(GLES20.GL_RENDERBUFFER, GLES20.GL_DEPTH_COMPONENT16,
                mWidth, mHeight);
        GLES20.glFramebufferRenderbuffer(GLES20.GL_FRAMEBUFFER, GLES20.GL_DEPTH_ATTACHMENT,
                GLES20.GL_RENDERBUFFER, mRenderBuffer[0]);
        GLES20.glBindRenderbuffer(GLES20.GL_RENDERBUFFER, 0);
        GLES20.glGenTextures(1, mTexture, 0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mTexture[0]);

        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, mWidth, mHeight,
                0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null);
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
    }

    private boolean mIsRender;

    public void startRenderer() {
        mIsRender = true;
    }

    private void drawTexture() {
        GLES20.glUniformMatrix4fv(uMatrixLocation, 1, false, projectionMatrix, 0);

        // 纹理单元：在OpenGL中，纹理不是直接绘制到片段着色器上，而是通过纹理单元去保存纹理

        // 设置当前活动的纹理单元为纹理单元0
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);

        // 将纹理ID绑定到当前活动的纹理单元上
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mTextureBean.getTextureId());

        // 将纹理单元传递片段着色器的u_TextureUnit
        GLES20.glUniform1i(uTextureUnitLocation, 0);

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_FAN, 0, 4);

    }
}
