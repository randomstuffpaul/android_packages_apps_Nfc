package com.samsung.android.sdk.cover;

import android.content.ComponentName;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.util.Log;
import android.util.Slog;
import android.view.Window;
import android.view.WindowManager.LayoutParams;
import com.samsung.android.cover.CoverState;
import com.samsung.android.cover.ICoverManager;
import com.samsung.android.cover.ICoverManagerCallback.Stub;
import java.util.Iterator;
import java.util.concurrent.CopyOnWriteArrayList;

public class ScoverManager {
    public static final int COVER_MODE_HIDE_SVIEW_ONCE = 2;
    public static final int COVER_MODE_NONE = 0;
    public static final int COVER_MODE_SVIEW = 1;
    private static final String FEATURE_COVER_FLIP = "com.sec.feature.cover.flip";
    private static final String FEATURE_COVER_SVIEW = "com.sec.feature.cover.sview";
    static final int SDK_VERSION = 16777216;
    private static final String TAG = "CoverManager";
    private static boolean sIsFilpCoverSystemFeatureEnabled;
    private static boolean sIsSViewCoverSystemFeatureEnabled;
    private static boolean sIsSystemFeatureQueried;
    private Context mContext;
    private final CopyOnWriteArrayList<CoverListenerDelegate> mListenerDelegates;
    private ICoverManager mService;

    public interface StateListener {
        void onCoverStateChanged(ScoverState scoverState);
    }

    private class CoverListenerDelegate extends Stub {
        private Handler mHandler;
        private final StateListener mListener;

        /* renamed from: com.samsung.android.sdk.cover.ScoverManager.CoverListenerDelegate.1 */
        class C00491 extends Handler {
            final /* synthetic */ ScoverManager val$this$0;

            C00491(Looper x0, ScoverManager scoverManager) {
                this.val$this$0 = scoverManager;
                super(x0);
            }

            public void handleMessage(Message msg) {
                if (CoverListenerDelegate.this.mListener != null) {
                    CoverState coverState = msg.obj;
                    if (coverState != null) {
                        CoverListenerDelegate.this.mListener.onCoverStateChanged(new ScoverState(coverState.switchState, coverState.type, coverState.color, coverState.widthPixel, coverState.heightPixel));
                        return;
                    }
                    Log.e(ScoverManager.TAG, "coverState : null");
                }
            }
        }

        CoverListenerDelegate(StateListener listener, Handler handler) {
            this.mListener = listener;
            this.mHandler = new C00491(handler == null ? ScoverManager.this.mContext.getMainLooper() : handler.getLooper(), ScoverManager.this);
        }

        public StateListener getListener() {
            return this.mListener;
        }

        public void coverCallback(CoverState state) throws RemoteException {
            Message msg = Message.obtain();
            msg.what = ScoverManager.COVER_MODE_NONE;
            msg.obj = state;
            this.mHandler.sendMessage(msg);
        }

        public String getListenerInfo() throws RemoteException {
            return this.mListener.toString();
        }
    }

    @Deprecated
    public interface ScoverStateListener {
        void onCoverStateChanged(ScoverState scoverState);
    }

    static {
        sIsSystemFeatureQueried = false;
        sIsFilpCoverSystemFeatureEnabled = false;
        sIsSViewCoverSystemFeatureEnabled = false;
    }

    public ScoverManager(Context context) {
        this.mListenerDelegates = new CopyOnWriteArrayList();
        this.mContext = context;
        initSystemFeature();
    }

    private void initSystemFeature() {
        if (!sIsSystemFeatureQueried) {
            sIsFilpCoverSystemFeatureEnabled = this.mContext.getPackageManager().hasSystemFeature(FEATURE_COVER_FLIP);
            sIsSViewCoverSystemFeatureEnabled = this.mContext.getPackageManager().hasSystemFeature(FEATURE_COVER_SVIEW);
            sIsSystemFeatureQueried = true;
        }
    }

    boolean isSupportCover() {
        return sIsFilpCoverSystemFeatureEnabled || sIsSViewCoverSystemFeatureEnabled;
    }

    boolean isSupportSViewCover() {
        return sIsSViewCoverSystemFeatureEnabled;
    }

    boolean isSupportTypeOfCover(int type) {
        switch (type) {
            case COVER_MODE_NONE /*0*/:
                return sIsFilpCoverSystemFeatureEnabled;
            case COVER_MODE_SVIEW /*1*/:
            case ScoverState.TYPE_SVIEW_CHARGER_COVER /*3*/:
                return sIsSViewCoverSystemFeatureEnabled;
            default:
                return false;
        }
    }

    private synchronized ICoverManager getService() {
        if (this.mService == null) {
            this.mService = ICoverManager.Stub.asInterface(ServiceManager.getService("cover"));
            if (this.mService == null) {
                Slog.w(TAG, "warning: no COVER_MANAGER_SERVICE");
            }
        }
        return this.mService;
    }

    public void setCoverModeToWindow(Window window, int coverMode) {
        if (isSupportSViewCover()) {
            LayoutParams wlp = window.getAttributes();
            if (wlp != null) {
                wlp.coverMode = coverMode;
                window.setAttributes(wlp);
                return;
            }
            return;
        }
        Log.w(TAG, "setSViewCoverModeToWindow : This device is not supported s view cover");
    }

    @Deprecated
    public void registerListener(ScoverStateListener listener) {
        Log.d(TAG, "registerListener : Use deprecated API!! Change ScoverStateListener to StateListener");
    }

    public void registerListener(StateListener listener) {
        CoverListenerDelegate coverListener;
        Throwable th;
        Log.d(TAG, "registerListener");
        if (!isSupportCover()) {
            Log.w(TAG, "registerListener : This device is not supported cover");
        } else if (listener == null) {
            Log.w(TAG, "registerListener : listener is null");
        } else {
            synchronized (this.mListenerDelegates) {
                try {
                    CoverListenerDelegate coverListener2;
                    Iterator<CoverListenerDelegate> i = this.mListenerDelegates.iterator();
                    while (i.hasNext()) {
                        CoverListenerDelegate delegate = (CoverListenerDelegate) i.next();
                        if (delegate.getListener().equals(listener)) {
                            coverListener = delegate;
                            break;
                        }
                    }
                    coverListener = null;
                    if (coverListener == null) {
                        try {
                            coverListener2 = new CoverListenerDelegate(listener, null);
                            this.mListenerDelegates.add(coverListener2);
                        } catch (Throwable th2) {
                            th = th2;
                            coverListener2 = coverListener;
                            throw th;
                        }
                    }
                    coverListener2 = coverListener;
                    ICoverManager svc = getService();
                    if (svc != null) {
                        ComponentName cm = new ComponentName(this.mContext.getPackageName(), getClass().getCanonicalName());
                        if (!(coverListener2 == null || cm == null)) {
                            svc.registerCallback(coverListener2, cm);
                        }
                    }
                } catch (RemoteException e) {
                    Log.e(TAG, "RemoteException in registerListener: ", e);
                } catch (Throwable th3) {
                    th = th3;
                    throw th;
                }
            }
        }
    }

    @Deprecated
    public void unregisterListener(ScoverStateListener listener) {
        Log.d(TAG, "unregisterListener : Use deprecated API!! Change ScoverStateListener to StateListener");
    }

    public void unregisterListener(StateListener listener) {
        Log.d(TAG, "unregisterListener");
        if (!isSupportCover()) {
            Log.w(TAG, "unregisterListener : This device is not supported cover");
        } else if (listener == null) {
            Log.w(TAG, "unregisterListener : listener is null");
        } else {
            synchronized (this.mListenerDelegates) {
                CoverListenerDelegate coverListener = null;
                Iterator<CoverListenerDelegate> i = this.mListenerDelegates.iterator();
                while (i.hasNext()) {
                    CoverListenerDelegate delegate = (CoverListenerDelegate) i.next();
                    if (delegate.getListener().equals(listener)) {
                        coverListener = delegate;
                        break;
                    }
                }
                if (coverListener == null) {
                    return;
                }
                try {
                    ICoverManager svc = getService();
                    if (svc != null && svc.unregisterCallback(coverListener)) {
                        this.mListenerDelegates.remove(coverListener);
                    }
                } catch (RemoteException e) {
                    Log.e(TAG, "RemoteException in unregisterListener: ", e);
                }
            }
        }
    }

    public ScoverState getCoverState() {
        if (isSupportCover()) {
            try {
                ICoverManager svc = getService();
                if (svc != null) {
                    CoverState coverState = svc.getCoverState();
                    if (coverState != null) {
                        return new ScoverState(coverState.switchState, coverState.type, coverState.color, coverState.widthPixel, coverState.heightPixel);
                    }
                    Log.e(TAG, "getCoverState : coverState is null");
                }
            } catch (RemoteException e) {
                Log.e(TAG, "RemoteException in getCoverState: ", e);
            }
            return null;
        }
        Log.w(TAG, "getCoverState : This device is not supported cover");
        return null;
    }
}
