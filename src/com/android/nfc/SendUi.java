package com.android.nfc;

import android.animation.Animator;
import android.animation.Animator.AnimatorListener;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.TimeAnimator;
import android.animation.TimeAnimator.TimeListener;
import android.animation.ValueAnimator;
import android.app.ActivityManager;
import android.app.StatusBarManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.os.AsyncTask;
import android.os.Binder;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.SurfaceControl;
import android.view.TextureView;
import android.view.TextureView.SurfaceTextureListener;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.WindowManager;
import android.view.WindowManager.LayoutParams;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import com.android.nfc.handover.HandoverService.Device;

public class SendUi implements AnimatorListener, TimeListener, SurfaceTextureListener, OnTouchListener {
    static final float[] BLACK_LAYER_ALPHA_DOWN_RANGE;
    static final float[] BLACK_LAYER_ALPHA_UP_RANGE;
    static final int FADE_IN_DURATION_MS = 250;
    static final int FADE_IN_START_DELAY_MS = 350;
    static final int FAST_SEND_DURATION_MS = 350;
    static final int FINISH_SCALE_UP = 0;
    static final int FINISH_SEND_SUCCESS = 1;
    static final float INTERMEDIATE_SCALE = 0.6f;
    static final int PRE_DURATION_MS = 350;
    static final float[] PRE_SCREENSHOT_SCALE;
    static final int SCALE_UP_DURATION_MS = 300;
    static final float[] SCALE_UP_SCREENSHOT_SCALE;
    static final float[] SEND_SCREENSHOT_SCALE;
    static final int SLIDE_OUT_DURATION_MS = 300;
    static final int SLOW_SEND_DURATION_MS = 8000;
    static final int STATE_COMPLETE = 7;
    static final int STATE_IDLE = 0;
    static final int STATE_SENDING = 6;
    static final int STATE_W4_CONFIRM = 5;
    static final int STATE_W4_PRESEND = 4;
    static final int STATE_W4_SCREENSHOT = 1;
    static final int STATE_W4_SCREENSHOT_PRESEND_REQUESTED = 2;
    static final int STATE_W4_SCREENSHOT_THEN_STOP = 3;
    static final String TAG = "SendUi";
    static final int TEXT_HINT_ALPHA_DURATION_MS = 500;
    static final float[] TEXT_HINT_ALPHA_RANGE;
    static final int TEXT_HINT_ALPHA_START_DELAY_MS = 300;
    final ObjectAnimator mAlphaDownAnimator;
    final ObjectAnimator mAlphaUpAnimator;
    final ImageView mBlackLayer;
    final Callback mCallback;
    final Context mContext;
    final Display mDisplay;
    final Matrix mDisplayMatrix;
    final DisplayMetrics mDisplayMetrics;
    final ObjectAnimator mFadeInAnimator;
    final ObjectAnimator mFastSendAnimator;
    final FireflyRenderer mFireflyRenderer;
    final TimeAnimator mFrameCounterAnimator;
    final boolean mHardwareAccelerated;
    final ObjectAnimator mHintAnimator;
    final LayoutInflater mLayoutInflater;
    final ObjectAnimator mPreAnimator;
    int mRenderedFrames;
    final ObjectAnimator mScaleUpAnimator;
    Bitmap mScreenshotBitmap;
    final View mScreenshotLayout;
    final ImageView mScreenshotView;
    final ObjectAnimator mSlowSendAnimator;
    int mState;
    final StatusBarManager mStatusBarManager;
    final AnimatorSet mSuccessAnimatorSet;
    SurfaceTexture mSurface;
    int mSurfaceHeight;
    int mSurfaceWidth;
    final TextView mTextHint;
    final TextView mTextRetry;
    final TextureView mTextureView;
    String mToastString;
    final LayoutParams mWindowLayoutParams;
    final WindowManager mWindowManager;

    interface Callback {
        void onSendConfirmed();
    }

    final class ScreenshotTask extends AsyncTask<Void, Void, Bitmap> {
        ScreenshotTask() {
        }

        protected Bitmap doInBackground(Void... params) {
            return SendUi.this.createScreenshot();
        }

        protected void onPostExecute(Bitmap result) {
            if (SendUi.this.mState == SendUi.STATE_W4_SCREENSHOT) {
                SendUi.this.mState = SendUi.STATE_W4_PRESEND;
            } else if (SendUi.this.mState == SendUi.STATE_W4_SCREENSHOT_THEN_STOP) {
                SendUi.this.mState = SendUi.STATE_IDLE;
            } else if (SendUi.this.mState != SendUi.STATE_W4_SCREENSHOT_PRESEND_REQUESTED) {
                Log.e(SendUi.TAG, "Invalid state on screenshot completion: " + Integer.toString(SendUi.this.mState));
            } else if (result != null) {
                SendUi.this.mScreenshotBitmap = result;
                SendUi.this.mState = SendUi.STATE_W4_PRESEND;
                SendUi.this.showPreSend();
            } else {
                Log.e(SendUi.TAG, "Failed to create screenshot");
                SendUi.this.mState = SendUi.STATE_IDLE;
            }
        }
    }

    static {
        PRE_SCREENSHOT_SCALE = new float[]{1.0f, INTERMEDIATE_SCALE};
        SEND_SCREENSHOT_SCALE = new float[]{INTERMEDIATE_SCALE, 0.2f};
        SCALE_UP_SCREENSHOT_SCALE = new float[]{INTERMEDIATE_SCALE, 1.0f};
        BLACK_LAYER_ALPHA_DOWN_RANGE = new float[]{0.9f, 0.0f};
        BLACK_LAYER_ALPHA_UP_RANGE = new float[]{0.0f, 0.9f};
        TEXT_HINT_ALPHA_RANGE = new float[]{0.0f, 1.0f};
    }

    public SendUi(Context context, Callback callback) {
        this.mContext = context;
        this.mCallback = callback;
        this.mDisplayMetrics = new DisplayMetrics();
        this.mDisplayMatrix = new Matrix();
        this.mWindowManager = (WindowManager) context.getSystemService("window");
        this.mStatusBarManager = (StatusBarManager) context.getSystemService("statusbar");
        this.mDisplay = this.mWindowManager.getDefaultDisplay();
        this.mLayoutInflater = (LayoutInflater) context.getSystemService("layout_inflater");
        this.mScreenshotLayout = this.mLayoutInflater.inflate(C0027R.layout.screenshot, null);
        this.mScreenshotView = (ImageView) this.mScreenshotLayout.findViewById(C0027R.id.screenshot);
        this.mScreenshotLayout.setFocusable(true);
        this.mTextHint = (TextView) this.mScreenshotLayout.findViewById(C0027R.id.calltoaction);
        this.mTextRetry = (TextView) this.mScreenshotLayout.findViewById(C0027R.id.retrytext);
        this.mBlackLayer = (ImageView) this.mScreenshotLayout.findViewById(C0027R.id.blacklayer);
        this.mTextureView = (TextureView) this.mScreenshotLayout.findViewById(C0027R.id.fireflies);
        this.mTextureView.setSurfaceTextureListener(this);
        this.mHardwareAccelerated = ActivityManager.isHighEndGfx();
        this.mWindowLayoutParams = new LayoutParams(-1, -1, STATE_IDLE, STATE_IDLE, 2003, ((this.mHardwareAccelerated ? 16777216 : STATE_IDLE) | Device.AUDIO_VIDEO_UNCATEGORIZED) | Device.COMPUTER_UNCATEGORIZED, -1);
        LayoutParams layoutParams = this.mWindowLayoutParams;
        layoutParams.privateFlags |= 16;
        this.mWindowLayoutParams.token = new Binder();
        this.mFrameCounterAnimator = new TimeAnimator();
        this.mFrameCounterAnimator.setTimeListener(this);
        PropertyValuesHolder preX = PropertyValuesHolder.ofFloat("scaleX", PRE_SCREENSHOT_SCALE);
        PropertyValuesHolder preY = PropertyValuesHolder.ofFloat("scaleY", PRE_SCREENSHOT_SCALE);
        ImageView imageView = this.mScreenshotView;
        PropertyValuesHolder[] propertyValuesHolderArr = new PropertyValuesHolder[STATE_W4_SCREENSHOT_PRESEND_REQUESTED];
        propertyValuesHolderArr[STATE_IDLE] = preX;
        propertyValuesHolderArr[STATE_W4_SCREENSHOT] = preY;
        this.mPreAnimator = ObjectAnimator.ofPropertyValuesHolder(imageView, propertyValuesHolderArr);
        this.mPreAnimator.setInterpolator(new DecelerateInterpolator());
        this.mPreAnimator.setDuration(350);
        this.mPreAnimator.addListener(this);
        PropertyValuesHolder postX = PropertyValuesHolder.ofFloat("scaleX", SEND_SCREENSHOT_SCALE);
        PropertyValuesHolder postY = PropertyValuesHolder.ofFloat("scaleY", SEND_SCREENSHOT_SCALE);
        PropertyValuesHolder alphaDown = PropertyValuesHolder.ofFloat("alpha", new float[]{1.0f, 0.0f});
        imageView = this.mScreenshotView;
        propertyValuesHolderArr = new PropertyValuesHolder[STATE_W4_SCREENSHOT_PRESEND_REQUESTED];
        propertyValuesHolderArr[STATE_IDLE] = postX;
        propertyValuesHolderArr[STATE_W4_SCREENSHOT] = postY;
        this.mSlowSendAnimator = ObjectAnimator.ofPropertyValuesHolder(imageView, propertyValuesHolderArr);
        this.mSlowSendAnimator.setInterpolator(new DecelerateInterpolator());
        this.mSlowSendAnimator.setDuration(8000);
        imageView = this.mScreenshotView;
        propertyValuesHolderArr = new PropertyValuesHolder[STATE_W4_SCREENSHOT_THEN_STOP];
        propertyValuesHolderArr[STATE_IDLE] = postX;
        propertyValuesHolderArr[STATE_W4_SCREENSHOT] = postY;
        propertyValuesHolderArr[STATE_W4_SCREENSHOT_PRESEND_REQUESTED] = alphaDown;
        this.mFastSendAnimator = ObjectAnimator.ofPropertyValuesHolder(imageView, propertyValuesHolderArr);
        this.mFastSendAnimator.setInterpolator(new DecelerateInterpolator());
        this.mFastSendAnimator.setDuration(350);
        this.mFastSendAnimator.addListener(this);
        PropertyValuesHolder scaleUpX = PropertyValuesHolder.ofFloat("scaleX", SCALE_UP_SCREENSHOT_SCALE);
        PropertyValuesHolder scaleUpY = PropertyValuesHolder.ofFloat("scaleY", SCALE_UP_SCREENSHOT_SCALE);
        imageView = this.mScreenshotView;
        propertyValuesHolderArr = new PropertyValuesHolder[STATE_W4_SCREENSHOT_PRESEND_REQUESTED];
        propertyValuesHolderArr[STATE_IDLE] = scaleUpX;
        propertyValuesHolderArr[STATE_W4_SCREENSHOT] = scaleUpY;
        this.mScaleUpAnimator = ObjectAnimator.ofPropertyValuesHolder(imageView, propertyValuesHolderArr);
        this.mScaleUpAnimator.setInterpolator(new DecelerateInterpolator());
        this.mScaleUpAnimator.setDuration(300);
        this.mScaleUpAnimator.addListener(this);
        float[] fArr = new float[STATE_W4_SCREENSHOT];
        fArr[STATE_IDLE] = 1.0f;
        PropertyValuesHolder fadeIn = PropertyValuesHolder.ofFloat("alpha", fArr);
        imageView = this.mScreenshotView;
        propertyValuesHolderArr = new PropertyValuesHolder[STATE_W4_SCREENSHOT];
        propertyValuesHolderArr[STATE_IDLE] = fadeIn;
        this.mFadeInAnimator = ObjectAnimator.ofPropertyValuesHolder(imageView, propertyValuesHolderArr);
        this.mFadeInAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
        this.mFadeInAnimator.setDuration(250);
        this.mFadeInAnimator.setStartDelay(350);
        this.mFadeInAnimator.addListener(this);
        PropertyValuesHolder alphaUp = PropertyValuesHolder.ofFloat("alpha", TEXT_HINT_ALPHA_RANGE);
        TextView textView = this.mTextHint;
        propertyValuesHolderArr = new PropertyValuesHolder[STATE_W4_SCREENSHOT];
        propertyValuesHolderArr[STATE_IDLE] = alphaUp;
        this.mHintAnimator = ObjectAnimator.ofPropertyValuesHolder(textView, propertyValuesHolderArr);
        this.mHintAnimator.setInterpolator(null);
        this.mHintAnimator.setDuration(500);
        this.mHintAnimator.setStartDelay(300);
        alphaDown = PropertyValuesHolder.ofFloat("alpha", BLACK_LAYER_ALPHA_DOWN_RANGE);
        imageView = this.mBlackLayer;
        propertyValuesHolderArr = new PropertyValuesHolder[STATE_W4_SCREENSHOT];
        propertyValuesHolderArr[STATE_IDLE] = alphaDown;
        this.mAlphaDownAnimator = ObjectAnimator.ofPropertyValuesHolder(imageView, propertyValuesHolderArr);
        this.mAlphaDownAnimator.setInterpolator(new DecelerateInterpolator());
        this.mAlphaDownAnimator.setDuration(400);
        alphaUp = PropertyValuesHolder.ofFloat("alpha", BLACK_LAYER_ALPHA_UP_RANGE);
        imageView = this.mBlackLayer;
        propertyValuesHolderArr = new PropertyValuesHolder[STATE_W4_SCREENSHOT];
        propertyValuesHolderArr[STATE_IDLE] = alphaUp;
        this.mAlphaUpAnimator = ObjectAnimator.ofPropertyValuesHolder(imageView, propertyValuesHolderArr);
        this.mAlphaUpAnimator.setInterpolator(new DecelerateInterpolator());
        this.mAlphaUpAnimator.setDuration(200);
        this.mSuccessAnimatorSet = new AnimatorSet();
        AnimatorSet animatorSet = this.mSuccessAnimatorSet;
        Animator[] animatorArr = new Animator[STATE_W4_SCREENSHOT_PRESEND_REQUESTED];
        animatorArr[STATE_IDLE] = this.mFastSendAnimator;
        animatorArr[STATE_W4_SCREENSHOT] = this.mFadeInAnimator;
        animatorSet.playSequentially(animatorArr);
        if (this.mHardwareAccelerated) {
            this.mFireflyRenderer = new FireflyRenderer(context);
        } else {
            this.mFireflyRenderer = null;
        }
        this.mState = STATE_IDLE;
    }

    public void takeScreenshot() {
        if (this.mState < STATE_W4_CONFIRM) {
            this.mState = STATE_W4_SCREENSHOT;
            new ScreenshotTask().execute(new Void[STATE_IDLE]);
        }
    }

    public void showPreSend() {
        switch (this.mState) {
            case STATE_IDLE /*0*/:
                Log.e(TAG, "Unexpected showPreSend() in STATE_IDLE");
            case STATE_W4_SCREENSHOT /*1*/:
                this.mState = STATE_W4_SCREENSHOT_PRESEND_REQUESTED;
            case STATE_W4_SCREENSHOT_PRESEND_REQUESTED /*2*/:
                Log.e(TAG, "Unexpected showPreSend() in STATE_W4_SCREENSHOT_PRESEND_REQUESTED");
            case STATE_W4_PRESEND /*4*/:
                this.mDisplay.getRealMetrics(this.mDisplayMetrics);
                int statusBarHeight = this.mContext.getResources().getDimensionPixelSize(17104908);
                this.mBlackLayer.setVisibility(8);
                this.mBlackLayer.setAlpha(0.0f);
                this.mScreenshotLayout.setOnTouchListener(this);
                this.mScreenshotView.setImageBitmap(this.mScreenshotBitmap);
                this.mScreenshotView.setTranslationX(0.0f);
                this.mScreenshotView.setAlpha(1.0f);
                this.mScreenshotView.setPadding(STATE_IDLE, statusBarHeight, STATE_IDLE, STATE_IDLE);
                this.mScreenshotLayout.requestFocus();
                this.mTextHint.setText(this.mContext.getResources().getString(C0027R.string.touch));
                this.mTextHint.setAlpha(0.0f);
                this.mTextHint.setVisibility(STATE_IDLE);
                this.mHintAnimator.start();
                switch (this.mContext.getResources().getConfiguration().orientation) {
                    case STATE_W4_SCREENSHOT /*1*/:
                        this.mWindowLayoutParams.screenOrientation = STATE_COMPLETE;
                        break;
                    case STATE_W4_SCREENSHOT_PRESEND_REQUESTED /*2*/:
                        this.mWindowLayoutParams.screenOrientation = STATE_SENDING;
                        break;
                    default:
                        this.mWindowLayoutParams.screenOrientation = STATE_COMPLETE;
                        break;
                }
                this.mWindowManager.addView(this.mScreenshotLayout, this.mWindowLayoutParams);
                this.mStatusBarManager.disable(65536);
                this.mToastString = null;
                if (!this.mHardwareAccelerated) {
                    this.mPreAnimator.start();
                }
                this.mState = STATE_W4_CONFIRM;
            default:
                Log.e(TAG, "Unexpected showPreSend() in state " + Integer.toString(this.mState));
        }
    }

    public void showStartSend() {
        if (this.mState >= STATE_SENDING) {
            this.mTextRetry.setVisibility(8);
            float currentScale = this.mScreenshotView.getScaleX();
            float[] fArr = new float[STATE_W4_SCREENSHOT_PRESEND_REQUESTED];
            fArr[STATE_IDLE] = currentScale;
            fArr[STATE_W4_SCREENSHOT] = 0.0f;
            PropertyValuesHolder postX = PropertyValuesHolder.ofFloat("scaleX", fArr);
            fArr = new float[STATE_W4_SCREENSHOT_PRESEND_REQUESTED];
            fArr[STATE_IDLE] = currentScale;
            fArr[STATE_W4_SCREENSHOT] = 0.0f;
            PropertyValuesHolder postY = PropertyValuesHolder.ofFloat("scaleY", fArr);
            ValueAnimator valueAnimator = this.mSlowSendAnimator;
            PropertyValuesHolder[] propertyValuesHolderArr = new PropertyValuesHolder[STATE_W4_SCREENSHOT_PRESEND_REQUESTED];
            propertyValuesHolderArr[STATE_IDLE] = postX;
            propertyValuesHolderArr[STATE_W4_SCREENSHOT] = postY;
            valueAnimator.setValues(propertyValuesHolderArr);
            float currentAlpha = this.mBlackLayer.getAlpha();
            if (this.mBlackLayer.isShown() && currentAlpha > 0.0f) {
                fArr = new float[STATE_W4_SCREENSHOT_PRESEND_REQUESTED];
                fArr[STATE_IDLE] = currentAlpha;
                fArr[STATE_W4_SCREENSHOT] = 0.0f;
                PropertyValuesHolder alphaDown = PropertyValuesHolder.ofFloat("alpha", fArr);
                valueAnimator = this.mAlphaDownAnimator;
                propertyValuesHolderArr = new PropertyValuesHolder[STATE_W4_SCREENSHOT];
                propertyValuesHolderArr[STATE_IDLE] = alphaDown;
                valueAnimator.setValues(propertyValuesHolderArr);
                this.mAlphaDownAnimator.start();
            }
            this.mSlowSendAnimator.start();
        }
    }

    public void finishAndToast(int finishMode, String toast) {
        this.mToastString = toast;
        finish(finishMode);
    }

    public void finish(int finishMode) {
        switch (this.mState) {
            case STATE_IDLE /*0*/:
            case STATE_W4_SCREENSHOT /*1*/:
            case STATE_W4_SCREENSHOT_PRESEND_REQUESTED /*2*/:
                this.mState = STATE_W4_SCREENSHOT_THEN_STOP;
            case STATE_W4_SCREENSHOT_THEN_STOP /*3*/:
                Log.e(TAG, "Unexpected call to finish() in STATE_W4_SCREENSHOT_THEN_STOP");
            case STATE_W4_PRESEND /*4*/:
                this.mScreenshotBitmap = null;
                this.mState = STATE_IDLE;
            default:
                if (this.mFireflyRenderer != null) {
                    this.mFireflyRenderer.stop();
                }
                this.mTextHint.setVisibility(8);
                this.mTextRetry.setVisibility(8);
                float currentScale = this.mScreenshotView.getScaleX();
                float currentAlpha = this.mScreenshotView.getAlpha();
                float[] fArr;
                ValueAnimator valueAnimator;
                PropertyValuesHolder[] propertyValuesHolderArr;
                if (finishMode == 0) {
                    this.mBlackLayer.setVisibility(8);
                    fArr = new float[STATE_W4_SCREENSHOT_PRESEND_REQUESTED];
                    fArr[STATE_IDLE] = currentScale;
                    fArr[STATE_W4_SCREENSHOT] = 1.0f;
                    PropertyValuesHolder scaleUpX = PropertyValuesHolder.ofFloat("scaleX", fArr);
                    fArr = new float[STATE_W4_SCREENSHOT_PRESEND_REQUESTED];
                    fArr[STATE_IDLE] = currentScale;
                    fArr[STATE_W4_SCREENSHOT] = 1.0f;
                    PropertyValuesHolder scaleUpY = PropertyValuesHolder.ofFloat("scaleY", fArr);
                    fArr = new float[STATE_W4_SCREENSHOT_PRESEND_REQUESTED];
                    fArr[STATE_IDLE] = currentAlpha;
                    fArr[STATE_W4_SCREENSHOT] = 1.0f;
                    PropertyValuesHolder scaleUpAlpha = PropertyValuesHolder.ofFloat("alpha", fArr);
                    valueAnimator = this.mScaleUpAnimator;
                    propertyValuesHolderArr = new PropertyValuesHolder[STATE_W4_SCREENSHOT_THEN_STOP];
                    propertyValuesHolderArr[STATE_IDLE] = scaleUpX;
                    propertyValuesHolderArr[STATE_W4_SCREENSHOT] = scaleUpY;
                    propertyValuesHolderArr[STATE_W4_SCREENSHOT_PRESEND_REQUESTED] = scaleUpAlpha;
                    valueAnimator.setValues(propertyValuesHolderArr);
                    this.mScaleUpAnimator.start();
                } else if (finishMode == STATE_W4_SCREENSHOT) {
                    fArr = new float[STATE_W4_SCREENSHOT_PRESEND_REQUESTED];
                    fArr[STATE_IDLE] = currentScale;
                    fArr[STATE_W4_SCREENSHOT] = 0.0f;
                    PropertyValuesHolder postX = PropertyValuesHolder.ofFloat("scaleX", fArr);
                    fArr = new float[STATE_W4_SCREENSHOT_PRESEND_REQUESTED];
                    fArr[STATE_IDLE] = currentScale;
                    fArr[STATE_W4_SCREENSHOT] = 0.0f;
                    PropertyValuesHolder postY = PropertyValuesHolder.ofFloat("scaleY", fArr);
                    fArr = new float[STATE_W4_SCREENSHOT_PRESEND_REQUESTED];
                    fArr[STATE_IDLE] = currentAlpha;
                    fArr[STATE_W4_SCREENSHOT] = 0.0f;
                    PropertyValuesHolder alpha = PropertyValuesHolder.ofFloat("alpha", fArr);
                    valueAnimator = this.mFastSendAnimator;
                    propertyValuesHolderArr = new PropertyValuesHolder[STATE_W4_SCREENSHOT_THEN_STOP];
                    propertyValuesHolderArr[STATE_IDLE] = postX;
                    propertyValuesHolderArr[STATE_W4_SCREENSHOT] = postY;
                    propertyValuesHolderArr[STATE_W4_SCREENSHOT_PRESEND_REQUESTED] = alpha;
                    valueAnimator.setValues(propertyValuesHolderArr);
                    PropertyValuesHolder fadeIn = PropertyValuesHolder.ofFloat("alpha", new float[]{0.0f, 1.0f});
                    valueAnimator = this.mFadeInAnimator;
                    propertyValuesHolderArr = new PropertyValuesHolder[STATE_W4_SCREENSHOT];
                    propertyValuesHolderArr[STATE_IDLE] = fadeIn;
                    valueAnimator.setValues(propertyValuesHolderArr);
                    this.mSlowSendAnimator.cancel();
                    this.mSuccessAnimatorSet.start();
                }
                this.mState = STATE_COMPLETE;
        }
    }

    void dismiss() {
        if (this.mState >= STATE_W4_CONFIRM) {
            this.mState = STATE_IDLE;
            this.mSurface = null;
            this.mFrameCounterAnimator.cancel();
            this.mPreAnimator.cancel();
            this.mSlowSendAnimator.cancel();
            this.mFastSendAnimator.cancel();
            this.mSuccessAnimatorSet.cancel();
            this.mScaleUpAnimator.cancel();
            this.mAlphaUpAnimator.cancel();
            this.mAlphaDownAnimator.cancel();
            this.mWindowManager.removeView(this.mScreenshotLayout);
            this.mStatusBarManager.disable(STATE_IDLE);
            this.mScreenshotBitmap = null;
            if (this.mToastString != null) {
                Toast.makeText(this.mContext, this.mToastString, STATE_W4_SCREENSHOT).show();
            }
            this.mToastString = null;
        }
    }

    static float getDegreesForRotation(int value) {
        switch (value) {
            case STATE_W4_SCREENSHOT /*1*/:
                return 90.0f;
            case STATE_W4_SCREENSHOT_PRESEND_REQUESTED /*2*/:
                return 180.0f;
            case STATE_W4_SCREENSHOT_THEN_STOP /*3*/:
                return 270.0f;
            default:
                return 0.0f;
        }
    }

    Bitmap createScreenshot() {
        this.mDisplay.getRealMetrics(this.mDisplayMetrics);
        boolean hasNavBar = this.mContext.getResources().getBoolean(17891403);
        float[] dims = new float[STATE_W4_SCREENSHOT_PRESEND_REQUESTED];
        dims[STATE_IDLE] = (float) this.mDisplayMetrics.widthPixels;
        dims[STATE_W4_SCREENSHOT] = (float) this.mDisplayMetrics.heightPixels;
        float degrees = getDegreesForRotation(this.mDisplay.getRotation());
        int statusBarHeight = this.mContext.getResources().getDimensionPixelSize(17104908);
        int navBarHeight = hasNavBar ? this.mContext.getResources().getDimensionPixelSize(17104909) : STATE_IDLE;
        int navBarHeightLandscape = hasNavBar ? this.mContext.getResources().getDimensionPixelSize(17104910) : STATE_IDLE;
        int navBarWidth = hasNavBar ? this.mContext.getResources().getDimensionPixelSize(17104911) : STATE_IDLE;
        boolean requiresRotation = degrees > 0.0f;
        if (requiresRotation) {
            this.mDisplayMatrix.reset();
            this.mDisplayMatrix.preRotate(-degrees);
            this.mDisplayMatrix.mapPoints(dims);
            dims[STATE_IDLE] = Math.abs(dims[STATE_IDLE]);
            dims[STATE_W4_SCREENSHOT] = Math.abs(dims[STATE_W4_SCREENSHOT]);
        }
        Bitmap bitmap = SurfaceControl.screenshot((int) dims[STATE_IDLE], (int) dims[STATE_W4_SCREENSHOT]);
        if (bitmap == null) {
            return null;
        }
        int newHeight;
        if (requiresRotation) {
            Bitmap ss = Bitmap.createBitmap(this.mDisplayMetrics.widthPixels, this.mDisplayMetrics.heightPixels, Config.ARGB_8888);
            Canvas c = new Canvas(ss);
            c.translate((float) (ss.getWidth() / STATE_W4_SCREENSHOT_PRESEND_REQUESTED), (float) (ss.getHeight() / STATE_W4_SCREENSHOT_PRESEND_REQUESTED));
            c.rotate(360.0f - degrees);
            c.translate((-dims[STATE_IDLE]) / 2.0f, (-dims[STATE_W4_SCREENSHOT]) / 2.0f);
            c.drawBitmap(bitmap, 0.0f, 0.0f, null);
            bitmap = ss;
        }
        int newTop = statusBarHeight;
        int newWidth = bitmap.getWidth();
        float smallestWidthDp = ((float) Math.min(newWidth, bitmap.getHeight())) / (((float) this.mDisplayMetrics.densityDpi) / 160.0f);
        if (bitmap.getWidth() < bitmap.getHeight()) {
            newHeight = (bitmap.getHeight() - statusBarHeight) - navBarHeight;
        } else if (smallestWidthDp > 599.0f) {
            newHeight = (bitmap.getHeight() - statusBarHeight) - navBarHeightLandscape;
        } else {
            newHeight = bitmap.getHeight() - statusBarHeight;
            newWidth = bitmap.getWidth() - navBarWidth;
        }
        return Bitmap.createBitmap(bitmap, STATE_IDLE, newTop, newWidth, newHeight);
    }

    public void onAnimationStart(Animator animation) {
    }

    public void onAnimationEnd(Animator animation) {
        if (animation == this.mScaleUpAnimator || animation == this.mSuccessAnimatorSet || animation == this.mFadeInAnimator) {
            dismiss();
        } else if (animation == this.mFastSendAnimator) {
            this.mScreenshotView.setScaleX(1.0f);
            this.mScreenshotView.setScaleY(1.0f);
        } else if (animation == this.mPreAnimator && this.mHardwareAccelerated && this.mState == STATE_W4_CONFIRM) {
            this.mFireflyRenderer.start(this.mSurface, this.mSurfaceWidth, this.mSurfaceHeight);
        }
    }

    public void onAnimationCancel(Animator animation) {
    }

    public void onAnimationRepeat(Animator animation) {
    }

    public void onTimeUpdate(TimeAnimator animation, long totalTime, long deltaTime) {
        int i = this.mRenderedFrames + STATE_W4_SCREENSHOT;
        this.mRenderedFrames = i;
        if (i < STATE_W4_PRESEND) {
            this.mScreenshotLayout.invalidate();
            return;
        }
        this.mFrameCounterAnimator.cancel();
        this.mPreAnimator.start();
    }

    public boolean onTouch(View v, MotionEvent event) {
        if (this.mState != STATE_W4_CONFIRM) {
            return false;
        }
        this.mState = STATE_SENDING;
        this.mScreenshotView.setOnTouchListener(null);
        this.mFrameCounterAnimator.cancel();
        this.mPreAnimator.cancel();
        this.mCallback.onSendConfirmed();
        return true;
    }

    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        if (this.mHardwareAccelerated && this.mState < STATE_COMPLETE) {
            this.mRenderedFrames = STATE_IDLE;
            this.mFrameCounterAnimator.start();
            this.mSurface = surface;
            this.mSurfaceWidth = width;
            this.mSurfaceHeight = height;
        }
    }

    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
    }

    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        this.mSurface = null;
        return true;
    }

    public void onSurfaceTextureUpdated(SurfaceTexture surface) {
    }

    public void showSendHint() {
        if (this.mAlphaDownAnimator.isRunning()) {
            this.mAlphaDownAnimator.cancel();
        }
        if (this.mSlowSendAnimator.isRunning()) {
            this.mSlowSendAnimator.cancel();
        }
        this.mBlackLayer.setScaleX(this.mScreenshotView.getScaleX());
        this.mBlackLayer.setScaleY(this.mScreenshotView.getScaleY());
        this.mBlackLayer.setVisibility(STATE_IDLE);
        this.mTextHint.setVisibility(8);
        this.mTextRetry.setText(this.mContext.getResources().getString(C0027R.string.beam_try_again));
        this.mTextRetry.setVisibility(STATE_IDLE);
        float[] fArr = new float[STATE_W4_SCREENSHOT_PRESEND_REQUESTED];
        fArr[STATE_IDLE] = this.mBlackLayer.getAlpha();
        fArr[STATE_W4_SCREENSHOT] = 0.9f;
        PropertyValuesHolder alphaUp = PropertyValuesHolder.ofFloat("alpha", fArr);
        ValueAnimator valueAnimator = this.mAlphaUpAnimator;
        PropertyValuesHolder[] propertyValuesHolderArr = new PropertyValuesHolder[STATE_W4_SCREENSHOT];
        propertyValuesHolderArr[STATE_IDLE] = alphaUp;
        valueAnimator.setValues(propertyValuesHolderArr);
        this.mAlphaUpAnimator.start();
    }
}
