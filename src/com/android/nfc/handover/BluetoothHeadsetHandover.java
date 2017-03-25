package com.android.nfc.handover;

import android.bluetooth.BluetoothA2dp;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHeadset;
import android.bluetooth.BluetoothProfile.ServiceListener;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.IAudioService;
import android.media.IAudioService.Stub;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.util.Log;
import android.view.KeyEvent;
import android.widget.Toast;
import com.android.nfc.C0027R;

public class BluetoothHeadsetHandover implements ServiceListener {
    static final String ACTION_ALLOW_CONNECT = "com.android.nfc.handover.action.ALLOW_CONNECT";
    static final int ACTION_CONNECT = 2;
    static final String ACTION_DENY_CONNECT = "com.android.nfc.handover.action.DENY_CONNECT";
    static final int ACTION_DISCONNECT = 1;
    static final int ACTION_INIT = 0;
    static final boolean DBG;
    static final int MSG_NEXT_STEP = 2;
    static final int MSG_TIMEOUT = 1;
    static final int RESULT_CONNECTED = 1;
    static final int RESULT_DISCONNECTED = 2;
    static final int RESULT_PENDING = 0;
    static final int STATE_BONDING = 4;
    static final int STATE_COMPLETE = 7;
    static final int STATE_CONNECTING = 5;
    static final int STATE_DISCONNECTING = 6;
    static final int STATE_INIT = 0;
    static final int STATE_INIT_COMPLETE = 2;
    static final int STATE_WAITING_FOR_BOND_CONFIRMATION = 3;
    static final int STATE_WAITING_FOR_PROXIES = 1;
    static final String TAG = "BluetoothHeadsetHandover";
    static final int TIMEOUT_MS = 20000;
    BluetoothA2dp mA2dp;
    int mA2dpResult;
    int mAction;
    final BluetoothAdapter mBluetoothAdapter;
    final int mBtClass;
    final Callback mCallback;
    final Context mContext;
    final BluetoothDevice mDevice;
    final Handler mHandler;
    BluetoothHeadset mHeadset;
    int mHfpResult;
    final Object mLock;
    final String mName;
    final BroadcastReceiver mReceiver;
    int mState;

    /* renamed from: com.android.nfc.handover.BluetoothHeadsetHandover.1 */
    class C00341 extends Handler {
        C00341() {
        }

        public void handleMessage(Message msg) {
            switch (msg.what) {
                case BluetoothHeadsetHandover.STATE_WAITING_FOR_PROXIES /*1*/:
                    if (BluetoothHeadsetHandover.this.mState != BluetoothHeadsetHandover.STATE_COMPLETE) {
                        Log.i(BluetoothHeadsetHandover.TAG, "Timeout completing BT handover");
                        BluetoothHeadsetHandover.this.complete(BluetoothHeadsetHandover.DBG);
                    }
                case BluetoothHeadsetHandover.STATE_INIT_COMPLETE /*2*/:
                    BluetoothHeadsetHandover.this.nextStep();
                default:
            }
        }
    }

    /* renamed from: com.android.nfc.handover.BluetoothHeadsetHandover.2 */
    class C00352 extends BroadcastReceiver {
        C00352() {
        }

        public void onReceive(Context context, Intent intent) {
            BluetoothHeadsetHandover.this.handleIntent(intent);
        }
    }

    public interface Callback {
        void onBluetoothHeadsetHandoverComplete(boolean z);
    }

    static {
        DBG = HandoverManager.DBG;
    }

    public BluetoothHeadsetHandover(Context context, BluetoothDevice device, String name, int cod, Callback callback) {
        this.mLock = new Object();
        this.mHandler = new C00341();
        this.mReceiver = new C00352();
        checkMainThread();
        this.mContext = context;
        this.mDevice = device;
        this.mName = name;
        this.mBtClass = cod;
        this.mCallback = callback;
        this.mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        this.mState = STATE_INIT;
    }

    public boolean hasStarted() {
        return this.mState != 0 ? true : DBG;
    }

    public void start() {
        checkMainThread();
        if (this.mState == 0 && this.mBluetoothAdapter != null) {
            if (DBG) {
                Log.d(TAG, "start : state : " + this.mState);
            }
            IntentFilter filter = new IntentFilter();
            filter.addAction("android.bluetooth.adapter.action.STATE_CHANGED");
            filter.addAction("android.bluetooth.device.action.BOND_STATE_CHANGED");
            filter.addAction("android.bluetooth.a2dp.profile.action.CONNECTION_STATE_CHANGED");
            filter.addAction("android.bluetooth.headset.profile.action.CONNECTION_STATE_CHANGED");
            filter.addAction(ACTION_ALLOW_CONNECT);
            filter.addAction(ACTION_DENY_CONNECT);
            this.mContext.registerReceiver(this.mReceiver, filter);
            this.mHandler.sendMessageDelayed(this.mHandler.obtainMessage(STATE_WAITING_FOR_PROXIES), 20000);
            this.mAction = STATE_INIT;
            nextStep();
        }
    }

    void nextStep() {
        if (this.mAction == 0) {
            nextStepInit();
        } else if (this.mAction == STATE_INIT_COMPLETE) {
            nextStepConnect();
        } else {
            nextStepDisconnect();
        }
    }

    void nextStepInit() {
        switch (this.mState) {
            case STATE_INIT /*0*/:
                if (this.mA2dp == null || this.mHeadset == null) {
                    this.mState = STATE_WAITING_FOR_PROXIES;
                    if (!getProfileProxys()) {
                        complete(DBG);
                        return;
                    }
                    return;
                }
            case STATE_WAITING_FOR_PROXIES /*1*/:
                break;
            default:
                return;
        }
        this.mState = STATE_INIT_COMPLETE;
        synchronized (this.mLock) {
            if (this.mA2dp.getConnectedDevices().contains(this.mDevice) || this.mHeadset.getConnectedDevices().contains(this.mDevice)) {
                Log.i(TAG, "ACTION_DISCONNECT addr=" + this.mDevice + " name=" + this.mName);
                this.mAction = STATE_WAITING_FOR_PROXIES;
            } else {
                Log.i(TAG, "ACTION_CONNECT addr=" + this.mDevice + " name=" + this.mName);
                this.mAction = STATE_INIT_COMPLETE;
            }
        }
        nextStep();
    }

    void nextStepDisconnect() {
        if (DBG) {
            Log.d(TAG, "nextStepDisconnect()");
        }
        if (DBG) {
            Log.d(TAG, "state : " + this.mState);
        }
        switch (this.mState) {
            case STATE_INIT_COMPLETE /*2*/:
                this.mState = STATE_DISCONNECTING;
                synchronized (this.mLock) {
                    if (this.mHeadset.getConnectionState(this.mDevice) == 0) {
                        this.mHfpResult = STATE_INIT_COMPLETE;
                        break;
                    } else {
                        this.mHfpResult = STATE_INIT;
                        this.mHeadset.disconnect(this.mDevice);
                    }
                    if (this.mA2dp.getConnectionState(this.mDevice) != 0) {
                        this.mA2dpResult = STATE_INIT;
                        this.mA2dp.disconnect(this.mDevice);
                    } else {
                        this.mA2dpResult = STATE_INIT_COMPLETE;
                    }
                    if (this.mA2dpResult != 0 && this.mHfpResult != 0) {
                        break;
                    }
                    toast(this.mContext.getString(C0027R.string.disconnecting_headset) + " " + this.mName + "...");
                    return;
                    break;
                }
                break;
            case STATE_DISCONNECTING /*6*/:
                break;
            default:
                return;
        }
        if (this.mA2dpResult != 0 && this.mHfpResult != 0) {
            if (this.mA2dpResult == STATE_INIT_COMPLETE && this.mHfpResult == STATE_INIT_COMPLETE) {
                Context context = this.mContext;
                Object[] objArr = new Object[STATE_WAITING_FOR_PROXIES];
                objArr[STATE_INIT] = this.mName;
                toast(context.getString(C0027R.string.disconnected_headset, objArr));
            }
            complete(DBG);
        }
    }

    boolean getProfileProxys() {
        if (this.mBluetoothAdapter.getProfileProxy(this.mContext, this, STATE_WAITING_FOR_PROXIES) && this.mBluetoothAdapter.getProfileProxy(this.mContext, this, STATE_INIT_COMPLETE)) {
            return true;
        }
        return DBG;
    }

    /* JADX WARNING: inconsistent code. */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    void nextStepConnect() {
        /*
        r8 = this;
        r7 = 12;
        r4 = 2;
        r6 = 0;
        r5 = 1;
        r1 = DBG;
        if (r1 == 0) goto L_0x0010;
    L_0x0009:
        r1 = "BluetoothHeadsetHandover";
        r2 = "nextStepConnect()";
        android.util.Log.d(r1, r2);
    L_0x0010:
        r1 = DBG;
        if (r1 == 0) goto L_0x002e;
    L_0x0014:
        r1 = "BluetoothHeadsetHandover";
        r2 = new java.lang.StringBuilder;
        r2.<init>();
        r3 = "state : ";
        r2 = r2.append(r3);
        r3 = r8.mState;
        r2 = r2.append(r3);
        r2 = r2.toString();
        android.util.Log.d(r1, r2);
    L_0x002e:
        r1 = r8.mState;
        switch(r1) {
            case 2: goto L_0x0034;
            case 3: goto L_0x0043;
            case 4: goto L_0x004f;
            case 5: goto L_0x0113;
            default: goto L_0x0033;
        };
    L_0x0033:
        return;
    L_0x0034:
        r1 = r8.mDevice;
        r1 = r1.getBondState();
        if (r1 == r7) goto L_0x0043;
    L_0x003c:
        r8.requestPairConfirmation();
        r1 = 3;
        r8.mState = r1;
        goto L_0x0033;
    L_0x0043:
        r1 = r8.mDevice;
        r1 = r1.getBondState();
        if (r1 == r7) goto L_0x004f;
    L_0x004b:
        r8.startBonding();
        goto L_0x0033;
    L_0x004f:
        r1 = r8.mDevice;
        r0 = r1.getUuids();
        if (r0 == 0) goto L_0x0066;
    L_0x0057:
        r1 = android.bluetooth.BluetoothUuid.HSP;
        r1 = android.bluetooth.BluetoothUuid.isUuidPresent(r0, r1);
        if (r1 == 0) goto L_0x00db;
    L_0x005f:
        r1 = "BluetoothHeadsetHandover";
        r2 = "Check HSP devices";
        android.util.Log.d(r1, r2);
    L_0x0066:
        r1 = 5;
        r8.mState = r1;
        r2 = r8.mLock;
        monitor-enter(r2);
        r1 = r8.mHeadset;	 Catch:{ all -> 0x00d8 }
        r3 = r8.mDevice;	 Catch:{ all -> 0x00d8 }
        r1 = r1.getConnectionState(r3);	 Catch:{ all -> 0x00d8 }
        if (r1 == r4) goto L_0x0109;
    L_0x0076:
        r1 = 0;
        r8.mHfpResult = r1;	 Catch:{ all -> 0x00d8 }
        r1 = r8.mDevice;	 Catch:{ all -> 0x00d8 }
        r3 = r8.mBtClass;	 Catch:{ all -> 0x00d8 }
        r1.setBluetoothClass(r3);	 Catch:{ all -> 0x00d8 }
        r1 = r8.mHeadset;	 Catch:{ all -> 0x00d8 }
        r3 = r8.mDevice;	 Catch:{ all -> 0x00d8 }
        r1.connect(r3);	 Catch:{ all -> 0x00d8 }
    L_0x0087:
        r1 = r8.mA2dp;	 Catch:{ all -> 0x00d8 }
        r3 = r8.mDevice;	 Catch:{ all -> 0x00d8 }
        r1 = r1.getConnectionState(r3);	 Catch:{ all -> 0x00d8 }
        if (r1 == r4) goto L_0x010e;
    L_0x0091:
        r1 = 0;
        r8.mA2dpResult = r1;	 Catch:{ all -> 0x00d8 }
        r1 = r8.mDevice;	 Catch:{ all -> 0x00d8 }
        r3 = r8.mBtClass;	 Catch:{ all -> 0x00d8 }
        r1.setBluetoothClass(r3);	 Catch:{ all -> 0x00d8 }
        r1 = r8.mA2dp;	 Catch:{ all -> 0x00d8 }
        r3 = r8.mDevice;	 Catch:{ all -> 0x00d8 }
        r1.connect(r3);	 Catch:{ all -> 0x00d8 }
    L_0x00a2:
        r1 = r8.mA2dpResult;	 Catch:{ all -> 0x00d8 }
        if (r1 == 0) goto L_0x00aa;
    L_0x00a6:
        r1 = r8.mHfpResult;	 Catch:{ all -> 0x00d8 }
        if (r1 != 0) goto L_0x0112;
    L_0x00aa:
        r1 = new java.lang.StringBuilder;	 Catch:{ all -> 0x00d8 }
        r1.<init>();	 Catch:{ all -> 0x00d8 }
        r3 = r8.mContext;	 Catch:{ all -> 0x00d8 }
        r4 = 2131165201; // 0x7f070011 float:1.7944612E38 double:1.0529355114E-314;
        r3 = r3.getString(r4);	 Catch:{ all -> 0x00d8 }
        r1 = r1.append(r3);	 Catch:{ all -> 0x00d8 }
        r3 = " ";
        r1 = r1.append(r3);	 Catch:{ all -> 0x00d8 }
        r3 = r8.mName;	 Catch:{ all -> 0x00d8 }
        r1 = r1.append(r3);	 Catch:{ all -> 0x00d8 }
        r3 = "...";
        r1 = r1.append(r3);	 Catch:{ all -> 0x00d8 }
        r1 = r1.toString();	 Catch:{ all -> 0x00d8 }
        r8.toast(r1);	 Catch:{ all -> 0x00d8 }
        monitor-exit(r2);	 Catch:{ all -> 0x00d8 }
        goto L_0x0033;
    L_0x00d8:
        r1 = move-exception;
        monitor-exit(r2);	 Catch:{ all -> 0x00d8 }
        throw r1;
    L_0x00db:
        r1 = android.bluetooth.BluetoothUuid.Handsfree;
        r1 = android.bluetooth.BluetoothUuid.isUuidPresent(r0, r1);
        if (r1 == 0) goto L_0x00ec;
    L_0x00e3:
        r1 = "BluetoothHeadsetHandover";
        r2 = "Check Handsfree devices";
        android.util.Log.d(r1, r2);
        goto L_0x0066;
    L_0x00ec:
        r1 = android.bluetooth.BluetoothUuid.AudioSink;
        r1 = android.bluetooth.BluetoothUuid.isUuidPresent(r0, r1);
        if (r1 == 0) goto L_0x00fd;
    L_0x00f4:
        r1 = "BluetoothHeadsetHandover";
        r2 = "Check AudioSink devices";
        android.util.Log.d(r1, r2);
        goto L_0x0066;
    L_0x00fd:
        r1 = "BluetoothHeadsetHandover";
        r2 = "Not headset/audio devices";
        android.util.Log.e(r1, r2);
        r8.complete(r5);
        goto L_0x0033;
    L_0x0109:
        r1 = 1;
        r8.mHfpResult = r1;	 Catch:{ all -> 0x00d8 }
        goto L_0x0087;
    L_0x010e:
        r1 = 1;
        r8.mA2dpResult = r1;	 Catch:{ all -> 0x00d8 }
        goto L_0x00a2;
    L_0x0112:
        monitor-exit(r2);	 Catch:{ all -> 0x00d8 }
    L_0x0113:
        r1 = r8.mA2dpResult;
        if (r1 == 0) goto L_0x0033;
    L_0x0117:
        r1 = r8.mHfpResult;
        if (r1 == 0) goto L_0x0033;
    L_0x011b:
        r1 = r8.mA2dpResult;
        if (r1 == r5) goto L_0x0123;
    L_0x011f:
        r1 = r8.mHfpResult;
        if (r1 != r5) goto L_0x013a;
    L_0x0123:
        r1 = r8.mContext;
        r2 = 2131165202; // 0x7f070012 float:1.7944614E38 double:1.052935512E-314;
        r3 = new java.lang.Object[r5];
        r4 = r8.mName;
        r3[r6] = r4;
        r1 = r1.getString(r2, r3);
        r8.toast(r1);
        r8.complete(r5);
        goto L_0x0033;
    L_0x013a:
        r1 = r8.mContext;
        r2 = 2131165203; // 0x7f070013 float:1.7944616E38 double:1.0529355124E-314;
        r3 = new java.lang.Object[r5];
        r4 = r8.mName;
        r3[r6] = r4;
        r1 = r1.getString(r2, r3);
        r8.toast(r1);
        r8.complete(r6);
        goto L_0x0033;
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.nfc.handover.BluetoothHeadsetHandover.nextStepConnect():void");
    }

    void startBonding() {
        this.mState = STATE_BONDING;
        toast(this.mContext.getString(C0027R.string.pairing_headset) + " " + this.mName + "...");
        if (!this.mDevice.createBond()) {
            Context context = this.mContext;
            Object[] objArr = new Object[STATE_WAITING_FOR_PROXIES];
            objArr[STATE_INIT] = this.mName;
            toast(context.getString(C0027R.string.pairing_headset_failed, objArr));
            complete(DBG);
        }
    }

    void handleIntent(Intent intent) {
        String action = intent.getAction();
        if (!this.mDevice.equals((BluetoothDevice) intent.getParcelableExtra("android.bluetooth.device.extra.DEVICE"))) {
            return;
        }
        if (ACTION_ALLOW_CONNECT.equals(action)) {
            nextStepConnect();
        } else if (ACTION_DENY_CONNECT.equals(action)) {
            complete(DBG);
        } else if ("android.bluetooth.device.action.BOND_STATE_CHANGED".equals(action) && this.mState == STATE_BONDING) {
            int bond = intent.getIntExtra("android.bluetooth.device.extra.BOND_STATE", Integer.MIN_VALUE);
            if (bond == 12) {
                nextStepConnect();
            } else if (bond == 10) {
                Context context = this.mContext;
                Object[] objArr = new Object[STATE_WAITING_FOR_PROXIES];
                objArr[STATE_INIT] = this.mName;
                toast(context.getString(C0027R.string.pairing_headset_failed, objArr));
                complete(DBG);
            }
        } else if ("android.bluetooth.headset.profile.action.CONNECTION_STATE_CHANGED".equals(action) && (this.mState == STATE_CONNECTING || this.mState == STATE_DISCONNECTING)) {
            state = intent.getIntExtra("android.bluetooth.profile.extra.STATE", Integer.MIN_VALUE);
            if (state == STATE_INIT_COMPLETE) {
                this.mHfpResult = STATE_WAITING_FOR_PROXIES;
                nextStep();
            } else if (state == 0) {
                this.mHfpResult = STATE_INIT_COMPLETE;
                nextStep();
            }
        } else if (!"android.bluetooth.a2dp.profile.action.CONNECTION_STATE_CHANGED".equals(action)) {
        } else {
            if (this.mState == STATE_CONNECTING || this.mState == STATE_DISCONNECTING) {
                state = intent.getIntExtra("android.bluetooth.profile.extra.STATE", Integer.MIN_VALUE);
                if (state == STATE_INIT_COMPLETE) {
                    this.mA2dpResult = STATE_WAITING_FOR_PROXIES;
                    nextStep();
                } else if (state == 0) {
                    this.mA2dpResult = STATE_INIT_COMPLETE;
                    nextStep();
                }
            }
        }
    }

    void complete(boolean connected) {
        Callback callback;
        if (DBG) {
            Log.d(TAG, "complete()");
        }
        this.mState = STATE_COMPLETE;
        try {
            if (DBG) {
                Log.d(TAG, "unregisterReceiver()");
            }
            this.mContext.unregisterReceiver(this.mReceiver);
            this.mHandler.removeMessages(STATE_WAITING_FOR_PROXIES);
            synchronized (this.mLock) {
                if (this.mA2dp != null) {
                    this.mBluetoothAdapter.closeProfileProxy(STATE_INIT_COMPLETE, this.mA2dp);
                }
                if (this.mHeadset != null) {
                    this.mBluetoothAdapter.closeProfileProxy(STATE_WAITING_FOR_PROXIES, this.mHeadset);
                }
                this.mA2dp = null;
                this.mHeadset = null;
            }
            callback = this.mCallback;
        } catch (IllegalArgumentException e) {
            Log.w(TAG, "catch IllegalArgumentException and ignore it");
            e.printStackTrace();
            this.mHandler.removeMessages(STATE_WAITING_FOR_PROXIES);
            synchronized (this.mLock) {
            }
            if (this.mA2dp != null) {
                this.mBluetoothAdapter.closeProfileProxy(STATE_INIT_COMPLETE, this.mA2dp);
            }
            if (this.mHeadset != null) {
                this.mBluetoothAdapter.closeProfileProxy(STATE_WAITING_FOR_PROXIES, this.mHeadset);
            }
            this.mA2dp = null;
            this.mHeadset = null;
            callback = this.mCallback;
        } catch (Throwable th) {
            this.mHandler.removeMessages(STATE_WAITING_FOR_PROXIES);
            synchronized (this.mLock) {
            }
            if (this.mA2dp != null) {
                this.mBluetoothAdapter.closeProfileProxy(STATE_INIT_COMPLETE, this.mA2dp);
            }
            if (this.mHeadset != null) {
                this.mBluetoothAdapter.closeProfileProxy(STATE_WAITING_FOR_PROXIES, this.mHeadset);
            }
            this.mA2dp = null;
            this.mHeadset = null;
            this.mCallback.onBluetoothHeadsetHandoverComplete(connected);
        }
        callback.onBluetoothHeadsetHandoverComplete(connected);
    }

    void toast(CharSequence text) {
        Toast.makeText(this.mContext, text, STATE_INIT).show();
    }

    void startTheMusic() {
        IAudioService audioService = Stub.asInterface(ServiceManager.checkService("audio"));
        if (audioService != null) {
            try {
                audioService.dispatchMediaKeyEvent(new KeyEvent(STATE_INIT, 126));
                audioService.dispatchMediaKeyEvent(new KeyEvent(STATE_WAITING_FOR_PROXIES, 126));
                return;
            } catch (RemoteException e) {
                Log.e(TAG, "dispatchMediaKeyEvent threw exception " + e);
                return;
            }
        }
        Log.w(TAG, "Unable to find IAudioService for media key event");
    }

    void requestPairConfirmation() {
        Intent dialogIntent = new Intent(this.mContext, ConfirmConnectActivity.class);
        dialogIntent.setFlags(268435456);
        dialogIntent.putExtra("android.bluetooth.device.extra.DEVICE", this.mDevice);
        this.mContext.startActivity(dialogIntent);
    }

    static void checkMainThread() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            throw new IllegalThreadStateException("must be called on main thread");
        }
    }

    /* JADX WARNING: inconsistent code. */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public void onServiceConnected(int r4, android.bluetooth.BluetoothProfile r5) {
        /*
        r3 = this;
        r1 = r3.mLock;
        monitor-enter(r1);
        switch(r4) {
            case 1: goto L_0x0008;
            case 2: goto L_0x001a;
            default: goto L_0x0006;
        };
    L_0x0006:
        monitor-exit(r1);	 Catch:{ all -> 0x0017 }
        return;
    L_0x0008:
        r5 = (android.bluetooth.BluetoothHeadset) r5;	 Catch:{ all -> 0x0017 }
        r3.mHeadset = r5;	 Catch:{ all -> 0x0017 }
        r0 = r3.mA2dp;	 Catch:{ all -> 0x0017 }
        if (r0 == 0) goto L_0x0006;
    L_0x0010:
        r0 = r3.mHandler;	 Catch:{ all -> 0x0017 }
        r2 = 2;
        r0.sendEmptyMessage(r2);	 Catch:{ all -> 0x0017 }
        goto L_0x0006;
    L_0x0017:
        r0 = move-exception;
        monitor-exit(r1);	 Catch:{ all -> 0x0017 }
        throw r0;
    L_0x001a:
        r5 = (android.bluetooth.BluetoothA2dp) r5;	 Catch:{ all -> 0x0017 }
        r3.mA2dp = r5;	 Catch:{ all -> 0x0017 }
        r0 = r3.mHeadset;	 Catch:{ all -> 0x0017 }
        if (r0 == 0) goto L_0x0006;
    L_0x0022:
        r0 = r3.mHandler;	 Catch:{ all -> 0x0017 }
        r2 = 2;
        r0.sendEmptyMessage(r2);	 Catch:{ all -> 0x0017 }
        goto L_0x0006;
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.nfc.handover.BluetoothHeadsetHandover.onServiceConnected(int, android.bluetooth.BluetoothProfile):void");
    }

    public void onServiceDisconnected(int profile) {
    }
}
