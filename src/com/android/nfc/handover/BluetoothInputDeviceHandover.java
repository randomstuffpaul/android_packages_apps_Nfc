package com.android.nfc.handover;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothInputDevice;
import android.bluetooth.BluetoothProfile;
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

public class BluetoothInputDeviceHandover implements ServiceListener {
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
    static final String TAG = "BluetoothInputDeviceHandover";
    static final int TIMEOUT_MS = 20000;
    int mAction;
    final BluetoothAdapter mBluetoothAdapter;
    final int mBtClass;
    final Callback mCallback;
    final Context mContext;
    final BluetoothDevice mDevice;
    final Handler mHandler;
    BluetoothInputDevice mInputDevice;
    int mInputDeviceResult;
    final Object mLock;
    final String mName;
    final BroadcastReceiver mReceiver;
    int mState;

    /* renamed from: com.android.nfc.handover.BluetoothInputDeviceHandover.1 */
    class C00361 extends Handler {
        C00361() {
        }

        public void handleMessage(Message msg) {
            switch (msg.what) {
                case BluetoothInputDeviceHandover.STATE_WAITING_FOR_PROXIES /*1*/:
                    if (BluetoothInputDeviceHandover.this.mState != BluetoothInputDeviceHandover.STATE_COMPLETE) {
                        Log.i(BluetoothInputDeviceHandover.TAG, "Timeout completing BT handover");
                        BluetoothInputDeviceHandover.this.complete(BluetoothInputDeviceHandover.DBG);
                    }
                case BluetoothInputDeviceHandover.STATE_INIT_COMPLETE /*2*/:
                    BluetoothInputDeviceHandover.this.nextStep();
                default:
            }
        }
    }

    /* renamed from: com.android.nfc.handover.BluetoothInputDeviceHandover.2 */
    class C00372 extends BroadcastReceiver {
        C00372() {
        }

        public void onReceive(Context context, Intent intent) {
            BluetoothInputDeviceHandover.this.handleIntent(intent);
        }
    }

    public interface Callback {
        void onBluetoothInputDeviceHandoverComplete(boolean z);
    }

    static {
        DBG = HandoverManager.DBG;
    }

    public BluetoothInputDeviceHandover(Context context, BluetoothDevice device, String name, int cod, Callback callback) {
        this.mLock = new Object();
        this.mHandler = new C00361();
        this.mReceiver = new C00372();
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
            filter.addAction("android.bluetooth.input.profile.action.CONNECTION_STATE_CHANGED");
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
                if (this.mInputDevice == null) {
                    this.mState = STATE_WAITING_FOR_PROXIES;
                    if (!getProfileProxys()) {
                        complete(DBG);
                        return;
                    }
                    return;
                }
                break;
            case STATE_WAITING_FOR_PROXIES /*1*/:
                break;
            default:
                return;
        }
        this.mState = STATE_INIT_COMPLETE;
        synchronized (this.mLock) {
            if (this.mInputDevice.getConnectedDevices().contains(this.mDevice)) {
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
                    if (this.mInputDevice.getConnectionState(this.mDevice) == 0) {
                        this.mInputDeviceResult = STATE_INIT_COMPLETE;
                        break;
                    } else {
                        this.mInputDeviceResult = STATE_INIT;
                        this.mInputDevice.disconnect(this.mDevice);
                    }
                    if (this.mInputDeviceResult != 0) {
                        break;
                    }
                    toast(this.mContext.getString(C0027R.string.disconnecting_headset) + " " + this.mName + "...");
                    return;
                }
            case STATE_DISCONNECTING /*6*/:
                break;
            default:
                return;
        }
        if (this.mInputDeviceResult != 0) {
            if (this.mInputDeviceResult == STATE_INIT_COMPLETE) {
                Context context = this.mContext;
                Object[] objArr = new Object[STATE_WAITING_FOR_PROXIES];
                objArr[STATE_INIT] = this.mName;
                toast(context.getString(C0027R.string.disconnected_headset, objArr));
            }
            complete(DBG);
        }
    }

    boolean getProfileProxys() {
        if (this.mBluetoothAdapter.getProfileProxy(this.mContext, this, STATE_BONDING)) {
            return true;
        }
        return DBG;
    }

    /* JADX WARNING: inconsistent code. */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    void nextStepConnect() {
        /*
        r6 = this;
        r3 = 12;
        r5 = 0;
        r4 = 1;
        r0 = DBG;
        if (r0 == 0) goto L_0x000f;
    L_0x0008:
        r0 = "BluetoothInputDeviceHandover";
        r1 = "nextStepConnect()";
        android.util.Log.d(r0, r1);
    L_0x000f:
        r0 = DBG;
        if (r0 == 0) goto L_0x002d;
    L_0x0013:
        r0 = "BluetoothInputDeviceHandover";
        r1 = new java.lang.StringBuilder;
        r1.<init>();
        r2 = "state : ";
        r1 = r1.append(r2);
        r2 = r6.mState;
        r1 = r1.append(r2);
        r1 = r1.toString();
        android.util.Log.d(r0, r1);
    L_0x002d:
        r0 = r6.mState;
        switch(r0) {
            case 2: goto L_0x0033;
            case 3: goto L_0x0042;
            case 4: goto L_0x004e;
            case 5: goto L_0x00a9;
            default: goto L_0x0032;
        };
    L_0x0032:
        return;
    L_0x0033:
        r0 = r6.mDevice;
        r0 = r0.getBondState();
        if (r0 == r3) goto L_0x0042;
    L_0x003b:
        r6.requestPairConfirmation();
        r0 = 3;
        r6.mState = r0;
        goto L_0x0032;
    L_0x0042:
        r0 = r6.mDevice;
        r0 = r0.getBondState();
        if (r0 == r3) goto L_0x004e;
    L_0x004a:
        r6.startBonding();
        goto L_0x0032;
    L_0x004e:
        r0 = 5;
        r6.mState = r0;
        r1 = r6.mLock;
        monitor-enter(r1);
        r0 = r6.mInputDevice;	 Catch:{ all -> 0x00a1 }
        r2 = r6.mDevice;	 Catch:{ all -> 0x00a1 }
        r0 = r0.getConnectionState(r2);	 Catch:{ all -> 0x00a1 }
        r2 = 2;
        if (r0 == r2) goto L_0x00a4;
    L_0x005f:
        r0 = 0;
        r6.mInputDeviceResult = r0;	 Catch:{ all -> 0x00a1 }
        r0 = r6.mDevice;	 Catch:{ all -> 0x00a1 }
        r2 = r6.mBtClass;	 Catch:{ all -> 0x00a1 }
        r0.setBluetoothClass(r2);	 Catch:{ all -> 0x00a1 }
        r0 = r6.mInputDevice;	 Catch:{ all -> 0x00a1 }
        r2 = r6.mDevice;	 Catch:{ all -> 0x00a1 }
        r0.connect(r2);	 Catch:{ all -> 0x00a1 }
    L_0x0070:
        r0 = r6.mInputDeviceResult;	 Catch:{ all -> 0x00a1 }
        if (r0 != 0) goto L_0x00a8;
    L_0x0074:
        r0 = new java.lang.StringBuilder;	 Catch:{ all -> 0x00a1 }
        r0.<init>();	 Catch:{ all -> 0x00a1 }
        r2 = r6.mContext;	 Catch:{ all -> 0x00a1 }
        r3 = 2131165201; // 0x7f070011 float:1.7944612E38 double:1.0529355114E-314;
        r2 = r2.getString(r3);	 Catch:{ all -> 0x00a1 }
        r0 = r0.append(r2);	 Catch:{ all -> 0x00a1 }
        r2 = " ";
        r0 = r0.append(r2);	 Catch:{ all -> 0x00a1 }
        r2 = r6.mName;	 Catch:{ all -> 0x00a1 }
        r0 = r0.append(r2);	 Catch:{ all -> 0x00a1 }
        r2 = "...";
        r0 = r0.append(r2);	 Catch:{ all -> 0x00a1 }
        r0 = r0.toString();	 Catch:{ all -> 0x00a1 }
        r6.toast(r0);	 Catch:{ all -> 0x00a1 }
        monitor-exit(r1);	 Catch:{ all -> 0x00a1 }
        goto L_0x0032;
    L_0x00a1:
        r0 = move-exception;
        monitor-exit(r1);	 Catch:{ all -> 0x00a1 }
        throw r0;
    L_0x00a4:
        r0 = 1;
        r6.mInputDeviceResult = r0;	 Catch:{ all -> 0x00a1 }
        goto L_0x0070;
    L_0x00a8:
        monitor-exit(r1);	 Catch:{ all -> 0x00a1 }
    L_0x00a9:
        r0 = r6.mInputDeviceResult;
        if (r0 == 0) goto L_0x0032;
    L_0x00ad:
        r0 = r6.mInputDeviceResult;
        if (r0 != r4) goto L_0x00c8;
    L_0x00b1:
        r0 = r6.mContext;
        r1 = 2131165202; // 0x7f070012 float:1.7944614E38 double:1.052935512E-314;
        r2 = new java.lang.Object[r4];
        r3 = r6.mName;
        r2[r5] = r3;
        r0 = r0.getString(r1, r2);
        r6.toast(r0);
        r6.complete(r4);
        goto L_0x0032;
    L_0x00c8:
        r0 = r6.mContext;
        r1 = 2131165203; // 0x7f070013 float:1.7944616E38 double:1.0529355124E-314;
        r2 = new java.lang.Object[r4];
        r3 = r6.mName;
        r2[r5] = r3;
        r0 = r0.getString(r1, r2);
        r6.toast(r0);
        r6.complete(r5);
        goto L_0x0032;
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.nfc.handover.BluetoothInputDeviceHandover.nextStepConnect():void");
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
        } else if (!"android.bluetooth.input.profile.action.CONNECTION_STATE_CHANGED".equals(action)) {
        } else {
            if (this.mState == STATE_CONNECTING || this.mState == STATE_DISCONNECTING) {
                int state = intent.getIntExtra("android.bluetooth.profile.extra.STATE", Integer.MIN_VALUE);
                if (state == STATE_INIT_COMPLETE) {
                    this.mInputDeviceResult = STATE_WAITING_FOR_PROXIES;
                    nextStep();
                } else if (state == 0) {
                    this.mInputDeviceResult = STATE_INIT_COMPLETE;
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
                if (this.mInputDevice != null) {
                    this.mBluetoothAdapter.closeProfileProxy(STATE_BONDING, this.mInputDevice);
                }
                this.mInputDevice = null;
            }
            callback = this.mCallback;
        } catch (IllegalArgumentException e) {
            Log.w(TAG, "catch IllegalArgumentException and ignore it");
            e.printStackTrace();
            this.mHandler.removeMessages(STATE_WAITING_FOR_PROXIES);
            synchronized (this.mLock) {
            }
            if (this.mInputDevice != null) {
                this.mBluetoothAdapter.closeProfileProxy(STATE_BONDING, this.mInputDevice);
            }
            this.mInputDevice = null;
            callback = this.mCallback;
        } catch (Throwable th) {
            this.mHandler.removeMessages(STATE_WAITING_FOR_PROXIES);
            synchronized (this.mLock) {
            }
            if (this.mInputDevice != null) {
                this.mBluetoothAdapter.closeProfileProxy(STATE_BONDING, this.mInputDevice);
            }
            this.mInputDevice = null;
            this.mCallback.onBluetoothInputDeviceHandoverComplete(connected);
        }
        callback.onBluetoothInputDeviceHandoverComplete(connected);
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

    public void onServiceConnected(int profile, BluetoothProfile proxy) {
        synchronized (this.mLock) {
            switch (profile) {
                case STATE_BONDING /*4*/:
                    this.mInputDevice = (BluetoothInputDevice) proxy;
                    this.mHandler.sendEmptyMessage(STATE_INIT_COMPLETE);
                    break;
            }
        }
    }

    public void onServiceDisconnected(int profile) {
    }
}
