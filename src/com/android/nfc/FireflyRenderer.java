package com.android.nfc;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.SurfaceTexture;
import android.opengl.GLUtils;
import android.util.Log;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;
import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;
import javax.microedition.khronos.egl.EGLSurface;
import javax.microedition.khronos.opengles.GL10;

public class FireflyRenderer {
    static final float FAR_CLIPPING_PLANE = 100.0f;
    private static final String LOG_TAG = "NfcFireflyThread";
    static final float NEAR_CLIPPING_PLANE = 50.0f;
    static final int NUM_FIREFLIES = 200;
    static final short[] mIndices;
    static final float[] mTextCoords;
    static final float[] mVertices;
    static final int[] sEglConfig;
    final Context mContext;
    int mDisplayHeight;
    int mDisplayWidth;
    final Firefly[] mFireflies;
    FireflyRenderThread mFireflyRenderThread;
    final ShortBuffer mIndexBuffer;
    SurfaceTexture mSurface;
    final FloatBuffer mTextureBuffer;
    final FloatBuffer mVertexBuffer;

    private class Firefly {
        static final float SPEED = 0.5f;
        static final float TEXTURE_HEIGHT = 30.0f;
        float mAlpha;
        float mScale;
        float mT;
        float mX;
        float mY;
        float mZ;
        float mZ0;

        void reset() {
            this.mX = (((float) (Math.random() * ((double) FireflyRenderer.this.mDisplayWidth))) * 4.0f) - ((float) (FireflyRenderer.this.mDisplayWidth * 2));
            this.mY = (((float) (Math.random() * ((double) FireflyRenderer.this.mDisplayHeight))) * 4.0f) - ((float) (FireflyRenderer.this.mDisplayHeight * 2));
            float random = (((float) Math.random()) * 2.0f) - 1.0f;
            this.mZ = random;
            this.mZ0 = random;
            this.mT = 0.0f;
            this.mScale = 1.5f;
            this.mAlpha = 0.0f;
        }

        public void updatePositionAndScale(long timeElapsedMs) {
            this.mT += (float) timeElapsedMs;
            this.mZ = this.mZ0 + ((this.mT / 1000.0f) * SPEED);
            this.mAlpha = 1.0f - this.mZ;
            if (((double) this.mZ) > 1.0d) {
                reset();
            }
        }

        public void draw(GL10 gl) {
            gl.glLoadIdentity();
            gl.glFrontFace(2305);
            gl.glEnableClientState(32884);
            gl.glEnableClientState(32888);
            gl.glVertexPointer(3, 5126, 0, FireflyRenderer.this.mVertexBuffer);
            gl.glTexCoordPointer(2, 5126, 0, FireflyRenderer.this.mTextureBuffer);
            gl.glTranslatef(this.mX, this.mY, -50.0f - (this.mZ * FireflyRenderer.NEAR_CLIPPING_PLANE));
            gl.glColor4f(1.0f, 1.0f, 1.0f, this.mAlpha);
            gl.glTranslatef(15.0f, 15.0f, 0.0f);
            gl.glScalef(this.mScale, this.mScale, 0.0f);
            gl.glTranslatef(-15.0f, -15.0f, 0.0f);
            gl.glDrawElements(4, FireflyRenderer.mIndices.length, 5123, FireflyRenderer.this.mIndexBuffer);
            gl.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
            gl.glDisableClientState(32888);
            gl.glDisableClientState(32884);
        }
    }

    private class FireflyRenderThread extends Thread {
        EGL10 mEgl;
        EGLConfig mEglConfig;
        EGLContext mEglContext;
        EGLDisplay mEglDisplay;
        EGLSurface mEglSurface;
        volatile boolean mFinished;
        GL10 mGL;
        int mTextureId;

        private FireflyRenderThread() {
        }

        public void run() {
            if (initGL()) {
                loadStarTexture();
                this.mGL.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
                this.mGL.glViewport(0, 0, FireflyRenderer.this.mDisplayWidth, FireflyRenderer.this.mDisplayHeight);
                this.mGL.glMatrixMode(5889);
                this.mGL.glLoadIdentity();
                this.mGL.glFrustumf((float) (-FireflyRenderer.this.mDisplayWidth), (float) FireflyRenderer.this.mDisplayWidth, (float) FireflyRenderer.this.mDisplayHeight, (float) (-FireflyRenderer.this.mDisplayHeight), FireflyRenderer.NEAR_CLIPPING_PLANE, FireflyRenderer.FAR_CLIPPING_PLANE);
                this.mGL.glMatrixMode(5888);
                this.mGL.glLoadIdentity();
                this.mGL.glHint(3152, 4354);
                this.mGL.glDepthMask(true);
                for (Firefly firefly : FireflyRenderer.this.mFireflies) {
                    firefly.reset();
                }
                for (int i = 0; i < 3; i++) {
                    this.mGL.glClear(16384);
                    if (!this.mEgl.eglSwapBuffers(this.mEglDisplay, this.mEglSurface)) {
                        Log.e(FireflyRenderer.LOG_TAG, "Could not swap buffers");
                        this.mFinished = true;
                    }
                }
                long startTime = System.currentTimeMillis();
                while (!this.mFinished) {
                    long timeElapsedMs = System.currentTimeMillis() - startTime;
                    startTime = System.currentTimeMillis();
                    checkCurrent();
                    this.mGL.glClear(16384);
                    this.mGL.glLoadIdentity();
                    this.mGL.glEnable(3553);
                    this.mGL.glEnable(3042);
                    this.mGL.glBlendFunc(770, 1);
                    for (Firefly firefly2 : FireflyRenderer.this.mFireflies) {
                        firefly2.updatePositionAndScale(timeElapsedMs);
                        firefly2.draw(this.mGL);
                    }
                    if (!this.mEgl.eglSwapBuffers(this.mEglDisplay, this.mEglSurface)) {
                        Log.e(FireflyRenderer.LOG_TAG, "Could not swap buffers");
                        this.mFinished = true;
                    }
                    try {
                        Thread.sleep(Math.max(30 - (System.currentTimeMillis() - startTime), 0));
                    } catch (InterruptedException e) {
                    }
                }
                finishGL();
                return;
            }
            Log.e(FireflyRenderer.LOG_TAG, "Failed to initialize OpenGL.");
        }

        public void finish() {
            this.mFinished = true;
        }

        void loadStarTexture() {
            int[] textureIds = new int[1];
            this.mGL.glGenTextures(1, textureIds, 0);
            this.mTextureId = textureIds[0];
            InputStream in = null;
            try {
                in = FireflyRenderer.this.mContext.getAssets().open("star.png");
                Bitmap bitmap = BitmapFactory.decodeStream(in);
                this.mGL.glBindTexture(3553, this.mTextureId);
                this.mGL.glTexParameterx(3553, 10241, 9729);
                this.mGL.glTexParameterx(3553, 10240, 9729);
                GLUtils.texImage2D(3553, 0, bitmap, 0);
                bitmap.recycle();
                if (in != null) {
                    try {
                        in.close();
                    } catch (IOException e) {
                    }
                }
            } catch (IOException e2) {
                Log.e(FireflyRenderer.LOG_TAG, "IOException opening assets.");
                if (in != null) {
                    try {
                        in.close();
                    } catch (IOException e3) {
                    }
                }
            } catch (Throwable th) {
                if (in != null) {
                    try {
                        in.close();
                    } catch (IOException e4) {
                    }
                }
            }
        }

        private void checkCurrent() {
            if ((!this.mEglContext.equals(this.mEgl.eglGetCurrentContext()) || !this.mEglSurface.equals(this.mEgl.eglGetCurrentSurface(12377))) && !this.mEgl.eglMakeCurrent(this.mEglDisplay, this.mEglSurface, this.mEglSurface, this.mEglContext)) {
                throw new RuntimeException("eglMakeCurrent failed " + GLUtils.getEGLErrorString(this.mEgl.eglGetError()));
            }
        }

        boolean initGL() {
            this.mEgl = (EGL10) EGLContext.getEGL();
            this.mEglDisplay = this.mEgl.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY);
            if (this.mEglDisplay == EGL10.EGL_NO_DISPLAY) {
                Log.e(FireflyRenderer.LOG_TAG, "eglGetDisplay failed " + GLUtils.getEGLErrorString(this.mEgl.eglGetError()));
                return false;
            }
            if (this.mEgl.eglInitialize(this.mEglDisplay, new int[2])) {
                this.mEglConfig = chooseEglConfig();
                if (this.mEglConfig == null) {
                    Log.e(FireflyRenderer.LOG_TAG, "eglConfig not initialized.");
                    return false;
                }
                this.mEglContext = this.mEgl.eglCreateContext(this.mEglDisplay, this.mEglConfig, EGL10.EGL_NO_CONTEXT, null);
                this.mEglSurface = this.mEgl.eglCreateWindowSurface(this.mEglDisplay, this.mEglConfig, FireflyRenderer.this.mSurface, null);
                if (this.mEglSurface == null || this.mEglSurface == EGL10.EGL_NO_SURFACE) {
                    Log.e(FireflyRenderer.LOG_TAG, "createWindowSurface returned error " + Integer.toString(this.mEgl.eglGetError()));
                    return false;
                } else if (this.mEgl.eglMakeCurrent(this.mEglDisplay, this.mEglSurface, this.mEglSurface, this.mEglContext)) {
                    this.mGL = (GL10) this.mEglContext.getGL();
                    return true;
                } else {
                    Log.e(FireflyRenderer.LOG_TAG, "eglMakeCurrent failed " + GLUtils.getEGLErrorString(this.mEgl.eglGetError()));
                    return false;
                }
            }
            Log.e(FireflyRenderer.LOG_TAG, "eglInitialize failed " + GLUtils.getEGLErrorString(this.mEgl.eglGetError()));
            return false;
        }

        private void finishGL() {
            if (this.mEgl != null && this.mEglDisplay != null) {
                this.mEgl.eglMakeCurrent(this.mEglDisplay, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_CONTEXT);
                if (this.mEglSurface != null) {
                    this.mEgl.eglDestroySurface(this.mEglDisplay, this.mEglSurface);
                }
                if (this.mEglContext != null) {
                    this.mEgl.eglDestroyContext(this.mEglDisplay, this.mEglContext);
                }
            }
        }

        private EGLConfig chooseEglConfig() {
            int[] configsCount = new int[1];
            EGLConfig[] configs = new EGLConfig[1];
            if (!this.mEgl.eglChooseConfig(this.mEglDisplay, FireflyRenderer.sEglConfig, configs, 1, configsCount)) {
                throw new IllegalArgumentException("eglChooseConfig failed " + GLUtils.getEGLErrorString(this.mEgl.eglGetError()));
            } else if (configsCount[0] > 0) {
                return configs[0];
            } else {
                return null;
            }
        }
    }

    static {
        sEglConfig = new int[]{12324, 8, 12323, 8, 12322, 8, 12321, 0, 12325, 0, 12326, 0, 12344};
        mVertices = new float[]{0.0f, 0.0f, 0.0f, 0.0f, 32.0f, 0.0f, 32.0f, 32.0f, 0.0f, 32.0f, 0.0f, 0.0f};
        mTextCoords = new float[]{0.0f, 0.0f, 1.0f, 0.0f, 1.0f, 1.0f, 0.0f, 1.0f};
        mIndices = new short[]{(short) 0, (short) 1, (short) 2, (short) 0, (short) 2, (short) 3};
    }

    public FireflyRenderer(Context context) {
        this.mContext = context;
        ByteBuffer vbb = ByteBuffer.allocateDirect(mVertices.length * 4);
        vbb.order(ByteOrder.nativeOrder());
        this.mVertexBuffer = vbb.asFloatBuffer();
        this.mVertexBuffer.put(mVertices);
        this.mVertexBuffer.position(0);
        ByteBuffer ibb = ByteBuffer.allocateDirect(mIndices.length * 2);
        ibb.order(ByteOrder.nativeOrder());
        this.mIndexBuffer = ibb.asShortBuffer();
        this.mIndexBuffer.put(mIndices);
        this.mIndexBuffer.position(0);
        ByteBuffer tbb = ByteBuffer.allocateDirect(mTextCoords.length * 4);
        tbb.order(ByteOrder.nativeOrder());
        this.mTextureBuffer = tbb.asFloatBuffer();
        this.mTextureBuffer.put(mTextCoords);
        this.mTextureBuffer.position(0);
        this.mFireflies = new Firefly[NUM_FIREFLIES];
        for (int i = 0; i < NUM_FIREFLIES; i++) {
            this.mFireflies[i] = new Firefly();
        }
    }

    public void start(SurfaceTexture surface, int width, int height) {
        this.mSurface = surface;
        this.mDisplayWidth = width;
        this.mDisplayHeight = height;
        this.mFireflyRenderThread = new FireflyRenderThread();
        this.mFireflyRenderThread.start();
    }

    public void stop() {
        if (this.mFireflyRenderThread != null) {
            this.mFireflyRenderThread.finish();
            try {
                this.mFireflyRenderThread.join();
            } catch (InterruptedException e) {
                Log.e(LOG_TAG, "Couldn't wait for FireflyRenderThread.");
            }
            this.mFireflyRenderThread = null;
        }
    }
}
