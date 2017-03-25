package com.android.nfc.handover;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.net.Uri;
import android.nfc.FormatException;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.os.Bundle;
import android.os.Debug;
import android.os.Handler;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.os.UserHandle;
import android.util.Log;
import com.samsung.android.sdk.cover.ScoverState;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Random;

public class HandoverManager {
    static final String ACTION_WHITELIST_DEVICE = "android.btopp.intent.action.WHITELIST_DEVICE";
    static final int CARRIER_POWER_STATE_ACTIVATING = 2;
    static final int CARRIER_POWER_STATE_ACTIVE = 1;
    static final int CARRIER_POWER_STATE_INACTIVE = 0;
    static final int CARRIER_POWER_STATE_UNKNOWN = 3;
    static final boolean DBG;
    static final int MSG_HANDOVER_COMPLETE = 0;
    static final int MSG_HANDOVER_PRE_COMPLETE = 5;
    static final int MSG_HEADSET_CONNECTED = 1;
    static final int MSG_HEADSET_NOT_CONNECTED = 2;
    static final int MSG_INPUTDEVICE_CONNECTED = 3;
    static final int MSG_INPUTDEVICE_NOT_CONNECTED = 4;
    static final byte[] RTD_COLLISION_RESOLUTION;
    static final String TAG = "NfcHandover";
    static final byte[] TYPE_BT_OOB;
    static final byte[] TYPE_NOKIA;
    boolean mBinding;
    final BluetoothAdapter mBluetoothAdapter;
    boolean mBluetoothEnabledByNfc;
    boolean mBluetoothHeadsetConnected;
    boolean mBluetoothHeadsetPending;
    boolean mBluetoothInputDeviceConnected;
    boolean mBound;
    private ServiceConnection mConnection;
    final Context mContext;
    boolean mEnabled;
    int mHandoverTransferId;
    String mLocalBluetoothAddress;
    final Object mLock;
    final Messenger mMessenger;
    ArrayList<Message> mPendingServiceMessages;
    HashMap<Integer, PendingHandoverTransfer> mPendingTransfers;
    final BroadcastReceiver mReceiver;
    Messenger mService;

    /* renamed from: com.android.nfc.handover.HandoverManager.1 */
    class C00411 implements ServiceConnection {
        C00411() {
        }

        /* JADX WARNING: inconsistent code. */
        /* Code decompiled incorrectly, please refer to instructions dump. */
        public void onServiceConnected(android.content.ComponentName r8, android.os.IBinder r9) {
            /*
            r7 = this;
            r2 = 1;
            r3 = 0;
            r4 = com.android.nfc.handover.HandoverManager.this;
            r5 = r4.mLock;
            monitor-enter(r5);
            r4 = com.android.nfc.handover.HandoverManager.this;	 Catch:{ all -> 0x0064 }
            r6 = new android.os.Messenger;	 Catch:{ all -> 0x0064 }
            r6.<init>(r9);	 Catch:{ all -> 0x0064 }
            r4.mService = r6;	 Catch:{ all -> 0x0064 }
            r4 = com.android.nfc.handover.HandoverManager.this;	 Catch:{ all -> 0x0064 }
            r6 = 0;
            r4.mBinding = r6;	 Catch:{ all -> 0x0064 }
            r4 = com.android.nfc.handover.HandoverManager.this;	 Catch:{ all -> 0x0064 }
            r6 = 1;
            r4.mBound = r6;	 Catch:{ all -> 0x0064 }
            r4 = 0;
            r6 = 0;
            r1 = android.os.Message.obtain(r4, r6);	 Catch:{ all -> 0x0064 }
            r4 = com.android.nfc.handover.HandoverManager.this;	 Catch:{ all -> 0x0064 }
            r4 = r4.mBluetoothEnabledByNfc;	 Catch:{ all -> 0x0064 }
            if (r4 == 0) goto L_0x0067;
        L_0x0026:
            r4 = r2;
        L_0x0027:
            r1.arg1 = r4;	 Catch:{ all -> 0x0064 }
            r4 = com.android.nfc.handover.HandoverManager.this;	 Catch:{ all -> 0x0064 }
            r4 = r4.mBluetoothHeadsetConnected;	 Catch:{ all -> 0x0064 }
            if (r4 == 0) goto L_0x0069;
        L_0x002f:
            r1.arg2 = r2;	 Catch:{ all -> 0x0064 }
            r2 = com.android.nfc.handover.HandoverManager.this;	 Catch:{ all -> 0x0064 }
            r2 = r2.mMessenger;	 Catch:{ all -> 0x0064 }
            r1.replyTo = r2;	 Catch:{ all -> 0x0064 }
            r2 = com.android.nfc.handover.HandoverManager.this;	 Catch:{ RemoteException -> 0x006b }
            r2 = r2.mService;	 Catch:{ RemoteException -> 0x006b }
            r2.send(r1);	 Catch:{ RemoteException -> 0x006b }
        L_0x003e:
            r2 = com.android.nfc.handover.HandoverManager.this;	 Catch:{ all -> 0x0064 }
            r2 = r2.mPendingServiceMessages;	 Catch:{ all -> 0x0064 }
            r2 = r2.isEmpty();	 Catch:{ all -> 0x0064 }
            if (r2 != 0) goto L_0x0074;
        L_0x0048:
            r2 = com.android.nfc.handover.HandoverManager.this;	 Catch:{ all -> 0x0064 }
            r2 = r2.mPendingServiceMessages;	 Catch:{ all -> 0x0064 }
            r3 = 0;
            r1 = r2.remove(r3);	 Catch:{ all -> 0x0064 }
            r1 = (android.os.Message) r1;	 Catch:{ all -> 0x0064 }
            r2 = com.android.nfc.handover.HandoverManager.this;	 Catch:{ RemoteException -> 0x005b }
            r2 = r2.mService;	 Catch:{ RemoteException -> 0x005b }
            r2.send(r1);	 Catch:{ RemoteException -> 0x005b }
            goto L_0x003e;
        L_0x005b:
            r0 = move-exception;
            r2 = "NfcHandover";
            r3 = "Failed to send queued message to service";
            android.util.Log.e(r2, r3);	 Catch:{ all -> 0x0064 }
            goto L_0x003e;
        L_0x0064:
            r2 = move-exception;
            monitor-exit(r5);	 Catch:{ all -> 0x0064 }
            throw r2;
        L_0x0067:
            r4 = r3;
            goto L_0x0027;
        L_0x0069:
            r2 = r3;
            goto L_0x002f;
        L_0x006b:
            r0 = move-exception;
            r2 = "NfcHandover";
            r3 = "Failed to register client";
            android.util.Log.e(r2, r3);	 Catch:{ all -> 0x0064 }
            goto L_0x003e;
        L_0x0074:
            monitor-exit(r5);	 Catch:{ all -> 0x0064 }
            return;
            */
            throw new UnsupportedOperationException("Method not decompiled: com.android.nfc.handover.HandoverManager.1.onServiceConnected(android.content.ComponentName, android.os.IBinder):void");
        }

        public void onServiceDisconnected(ComponentName name) {
            synchronized (HandoverManager.this.mLock) {
                Log.d(HandoverManager.TAG, "Service disconnected");
                if (HandoverManager.this.mService != null) {
                    try {
                        Message msg = Message.obtain(null, HandoverManager.MSG_HEADSET_CONNECTED);
                        msg.replyTo = HandoverManager.this.mMessenger;
                        HandoverManager.this.mService.send(msg);
                    } catch (RemoteException e) {
                    }
                }
                HandoverManager.this.mService = null;
                HandoverManager.this.mBound = HandoverManager.DBG;
                HandoverManager.this.mBluetoothHeadsetPending = HandoverManager.DBG;
                HandoverManager.this.mPendingTransfers.clear();
                HandoverManager.this.mPendingServiceMessages.clear();
            }
        }
    }

    /* renamed from: com.android.nfc.handover.HandoverManager.2 */
    class C00422 extends BroadcastReceiver {
        C00422() {
        }

        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals("android.intent.action.USER_SWITCHED")) {
                HandoverManager.this.unbindServiceIfNeededLocked(true);
            }
        }
    }

    static class BluetoothHandoverData {
        public boolean carrierActivating;
        public int cod;
        public BluetoothDevice device;
        public String name;
        public boolean valid;

        BluetoothHandoverData() {
            this.valid = HandoverManager.DBG;
            this.carrierActivating = HandoverManager.DBG;
        }
    }

    class MessageHandler extends Handler {
        MessageHandler() {
        }

        /* JADX WARNING: inconsistent code. */
        /* Code decompiled incorrectly, please refer to instructions dump. */
        public void handleMessage(android.os.Message r8) {
            /*
            r7 = this;
            r2 = 1;
            r3 = 0;
            r4 = com.android.nfc.handover.HandoverManager.this;
            r4 = r4.mLock;
            monitor-enter(r4);
            r5 = r8.what;	 Catch:{ all -> 0x0024 }
            switch(r5) {
                case 0: goto L_0x0027;
                case 1: goto L_0x007c;
                case 2: goto L_0x0092;
                case 3: goto L_0x00a3;
                case 4: goto L_0x00aa;
                case 5: goto L_0x0014;
                default: goto L_0x000c;
            };	 Catch:{ all -> 0x0024 }
        L_0x000c:
            r2 = com.android.nfc.handover.HandoverManager.this;	 Catch:{ all -> 0x0024 }
            r3 = 0;
            r2.unbindServiceIfNeededLocked(r3);	 Catch:{ all -> 0x0024 }
            monitor-exit(r4);	 Catch:{ all -> 0x0024 }
            return;
        L_0x0014:
            r2 = 0;
            r3 = 0;
            r0 = android.os.Message.obtain(r2, r3);	 Catch:{ all -> 0x0024 }
            r2 = r8.arg1;	 Catch:{ all -> 0x0024 }
            r0.arg1 = r2;	 Catch:{ all -> 0x0024 }
            r2 = 1000; // 0x3e8 float:1.401E-42 double:4.94E-321;
            r7.sendMessageDelayed(r0, r2);	 Catch:{ all -> 0x0024 }
            goto L_0x000c;
        L_0x0024:
            r2 = move-exception;
            monitor-exit(r4);	 Catch:{ all -> 0x0024 }
            throw r2;
        L_0x0027:
            r1 = r8.arg1;	 Catch:{ all -> 0x0024 }
            r2 = "NfcHandover";
            r3 = new java.lang.StringBuilder;	 Catch:{ all -> 0x0024 }
            r3.<init>();	 Catch:{ all -> 0x0024 }
            r5 = "Completed transfer id: ";
            r3 = r3.append(r5);	 Catch:{ all -> 0x0024 }
            r5 = java.lang.Integer.toString(r1);	 Catch:{ all -> 0x0024 }
            r3 = r3.append(r5);	 Catch:{ all -> 0x0024 }
            r3 = r3.toString();	 Catch:{ all -> 0x0024 }
            android.util.Log.d(r2, r3);	 Catch:{ all -> 0x0024 }
            r2 = com.android.nfc.handover.HandoverManager.this;	 Catch:{ all -> 0x0024 }
            r2 = r2.mPendingTransfers;	 Catch:{ all -> 0x0024 }
            r3 = java.lang.Integer.valueOf(r1);	 Catch:{ all -> 0x0024 }
            r2 = r2.containsKey(r3);	 Catch:{ all -> 0x0024 }
            if (r2 == 0) goto L_0x005f;
        L_0x0053:
            r2 = com.android.nfc.handover.HandoverManager.this;	 Catch:{ all -> 0x0024 }
            r2 = r2.mPendingTransfers;	 Catch:{ all -> 0x0024 }
            r3 = java.lang.Integer.valueOf(r1);	 Catch:{ all -> 0x0024 }
            r2.remove(r3);	 Catch:{ all -> 0x0024 }
            goto L_0x000c;
        L_0x005f:
            r2 = "NfcHandover";
            r3 = new java.lang.StringBuilder;	 Catch:{ all -> 0x0024 }
            r3.<init>();	 Catch:{ all -> 0x0024 }
            r5 = "Could not find completed transfer id: ";
            r3 = r3.append(r5);	 Catch:{ all -> 0x0024 }
            r5 = java.lang.Integer.toString(r1);	 Catch:{ all -> 0x0024 }
            r3 = r3.append(r5);	 Catch:{ all -> 0x0024 }
            r3 = r3.toString();	 Catch:{ all -> 0x0024 }
            android.util.Log.e(r2, r3);	 Catch:{ all -> 0x0024 }
            goto L_0x000c;
        L_0x007c:
            r5 = com.android.nfc.handover.HandoverManager.this;	 Catch:{ all -> 0x0024 }
            r6 = r8.arg1;	 Catch:{ all -> 0x0024 }
            if (r6 == 0) goto L_0x0090;
        L_0x0082:
            r5.mBluetoothEnabledByNfc = r2;	 Catch:{ all -> 0x0024 }
            r2 = com.android.nfc.handover.HandoverManager.this;	 Catch:{ all -> 0x0024 }
            r3 = 1;
            r2.mBluetoothHeadsetConnected = r3;	 Catch:{ all -> 0x0024 }
            r2 = com.android.nfc.handover.HandoverManager.this;	 Catch:{ all -> 0x0024 }
            r3 = 0;
            r2.mBluetoothHeadsetPending = r3;	 Catch:{ all -> 0x0024 }
            goto L_0x000c;
        L_0x0090:
            r2 = r3;
            goto L_0x0082;
        L_0x0092:
            r2 = com.android.nfc.handover.HandoverManager.this;	 Catch:{ all -> 0x0024 }
            r3 = 0;
            r2.mBluetoothEnabledByNfc = r3;	 Catch:{ all -> 0x0024 }
            r2 = com.android.nfc.handover.HandoverManager.this;	 Catch:{ all -> 0x0024 }
            r3 = 0;
            r2.mBluetoothHeadsetConnected = r3;	 Catch:{ all -> 0x0024 }
            r2 = com.android.nfc.handover.HandoverManager.this;	 Catch:{ all -> 0x0024 }
            r3 = 0;
            r2.mBluetoothHeadsetPending = r3;	 Catch:{ all -> 0x0024 }
            goto L_0x000c;
        L_0x00a3:
            r2 = com.android.nfc.handover.HandoverManager.this;	 Catch:{ all -> 0x0024 }
            r3 = 1;
            r2.mBluetoothInputDeviceConnected = r3;	 Catch:{ all -> 0x0024 }
            goto L_0x000c;
        L_0x00aa:
            r2 = com.android.nfc.handover.HandoverManager.this;	 Catch:{ all -> 0x0024 }
            r3 = 0;
            r2.mBluetoothInputDeviceConnected = r3;	 Catch:{ all -> 0x0024 }
            goto L_0x000c;
            */
            throw new UnsupportedOperationException("Method not decompiled: com.android.nfc.handover.HandoverManager.MessageHandler.handleMessage(android.os.Message):void");
        }
    }

    static {
        boolean z = true;
        if (Debug.isProductShip() == MSG_HEADSET_CONNECTED) {
            z = DBG;
        }
        DBG = z;
        TYPE_NOKIA = "nokia.com:bt".getBytes(Charset.forName("US_ASCII"));
        TYPE_BT_OOB = "application/vnd.bluetooth.ep.oob".getBytes(Charset.forName("US_ASCII"));
        RTD_COLLISION_RESOLUTION = new byte[]{(byte) 99, (byte) 114};
    }

    public HandoverManager(Context context) {
        this.mMessenger = new Messenger(new MessageHandler());
        this.mLock = new Object();
        this.mService = null;
        this.mBinding = DBG;
        this.mConnection = new C00411();
        this.mReceiver = new C00422();
        this.mContext = context;
        this.mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        this.mPendingTransfers = new HashMap();
        this.mPendingServiceMessages = new ArrayList();
        this.mContext.registerReceiver(this.mReceiver, new IntentFilter("android.intent.action.USER_SWITCHED"), null, null);
        this.mEnabled = true;
        this.mBluetoothEnabledByNfc = DBG;
    }

    boolean bindServiceIfNeededLocked() {
        if (this.mBinding) {
            return true;
        }
        Log.d(TAG, "Binding to handover service");
        boolean bindSuccess = this.mContext.bindServiceAsUser(new Intent(this.mContext, HandoverService.class), this.mConnection, MSG_HEADSET_CONNECTED, UserHandle.CURRENT);
        this.mBinding = bindSuccess;
        return bindSuccess;
    }

    void unbindServiceIfNeededLocked(boolean force) {
        if (!this.mBound) {
            return;
        }
        if (force || (!this.mBluetoothHeadsetPending && this.mPendingTransfers.isEmpty())) {
            Log.d(TAG, "Unbinding from service.");
            this.mContext.unbindService(this.mConnection);
            this.mBound = DBG;
            this.mPendingServiceMessages.clear();
            this.mBluetoothHeadsetPending = DBG;
            this.mPendingTransfers.clear();
        }
    }

    static NdefRecord createCollisionRecord() {
        byte[] random = new byte[MSG_HEADSET_NOT_CONNECTED];
        new Random().nextBytes(random);
        return new NdefRecord((short) 1, RTD_COLLISION_RESOLUTION, null, random);
    }

    NdefRecord createBluetoothAlternateCarrierRecord(boolean activating) {
        byte[] payload = new byte[MSG_INPUTDEVICE_NOT_CONNECTED];
        payload[MSG_HANDOVER_COMPLETE] = (byte) (activating ? MSG_HEADSET_NOT_CONNECTED : MSG_HEADSET_CONNECTED);
        payload[MSG_HEADSET_CONNECTED] = (byte) 1;
        payload[MSG_HEADSET_NOT_CONNECTED] = (byte) 98;
        payload[MSG_INPUTDEVICE_CONNECTED] = (byte) 0;
        return new NdefRecord((short) 1, NdefRecord.RTD_ALTERNATIVE_CARRIER, null, payload);
    }

    NdefRecord createBluetoothOobDataRecord() {
        byte[] payload = new byte[8];
        payload[MSG_HANDOVER_COMPLETE] = (byte) (payload.length & 255);
        payload[MSG_HEADSET_CONNECTED] = (byte) ((payload.length >> 8) & 255);
        synchronized (this.mLock) {
            if (this.mLocalBluetoothAddress == null) {
                this.mLocalBluetoothAddress = this.mBluetoothAdapter.getAddress();
            }
            if (this.mLocalBluetoothAddress == null) {
                if (DBG) {
                    Log.d(TAG, "mLocalBluetoothAddress is null");
                }
                return null;
            }
            System.arraycopy(addressToReverseBytes(this.mLocalBluetoothAddress), MSG_HANDOVER_COMPLETE, payload, MSG_HEADSET_NOT_CONNECTED, 6);
            byte[] bArr = TYPE_BT_OOB;
            byte[] bArr2 = new byte[MSG_HEADSET_CONNECTED];
            bArr2[MSG_HANDOVER_COMPLETE] = (byte) 98;
            return new NdefRecord((short) 2, bArr, bArr2, payload);
        }
    }

    public void setEnabled(boolean enabled) {
        synchronized (this.mLock) {
            this.mEnabled = enabled;
        }
    }

    public boolean isHandoverSupported() {
        return this.mBluetoothAdapter != null ? true : DBG;
    }

    public NdefMessage createHandoverRequestMessage() {
        if (this.mBluetoothAdapter == null) {
            return null;
        }
        NdefRecord createHandoverRequestRecord = createHandoverRequestRecord();
        NdefRecord[] ndefRecordArr = new NdefRecord[MSG_HEADSET_CONNECTED];
        ndefRecordArr[MSG_HANDOVER_COMPLETE] = createBluetoothOobDataRecord();
        return new NdefMessage(createHandoverRequestRecord, ndefRecordArr);
    }

    NdefMessage createHandoverSelectMessage(boolean activating) {
        NdefRecord createHandoverSelectRecord = createHandoverSelectRecord(activating);
        NdefRecord[] ndefRecordArr = new NdefRecord[MSG_HEADSET_CONNECTED];
        ndefRecordArr[MSG_HANDOVER_COMPLETE] = createBluetoothOobDataRecord();
        return new NdefMessage(createHandoverSelectRecord, ndefRecordArr);
    }

    NdefRecord createHandoverSelectRecord(boolean activating) {
        byte[] nestedPayload = new NdefMessage(createBluetoothAlternateCarrierRecord(activating), new NdefRecord[MSG_HANDOVER_COMPLETE]).toByteArray();
        ByteBuffer payload = ByteBuffer.allocate(nestedPayload.length + MSG_HEADSET_CONNECTED);
        payload.put((byte) 18);
        payload.put(nestedPayload);
        byte[] payloadBytes = new byte[payload.position()];
        payload.position(MSG_HANDOVER_COMPLETE);
        payload.get(payloadBytes);
        return new NdefRecord((short) 1, NdefRecord.RTD_HANDOVER_SELECT, null, payloadBytes);
    }

    NdefRecord createHandoverRequestRecord() {
        NdefRecord createCollisionRecord = createCollisionRecord();
        NdefRecord[] ndefRecordArr = new NdefRecord[MSG_HEADSET_CONNECTED];
        ndefRecordArr[MSG_HANDOVER_COMPLETE] = createBluetoothAlternateCarrierRecord(DBG);
        NdefMessage nestedMessage = new NdefMessage(createCollisionRecord, ndefRecordArr);
        ByteBuffer payload = ByteBuffer.allocate(nestedMessage.toByteArray().length + MSG_HEADSET_CONNECTED);
        payload.put((byte) 18);
        payload.put(nestedMessage.toByteArray());
        byte[] payloadBytes = new byte[payload.position()];
        payload.position(MSG_HANDOVER_COMPLETE);
        payload.get(payloadBytes);
        return new NdefRecord((short) 1, NdefRecord.RTD_HANDOVER_REQUEST, null, payloadBytes);
    }

    public NdefMessage tryHandoverRequest(NdefMessage m) {
        if (m == null) {
            return null;
        }
        if (this.mBluetoothAdapter == null) {
            return null;
        }
        if (DBG) {
            Log.d(TAG, "tryHandoverRequest():" + m.toString());
        }
        NdefRecord r = m.getRecords()[MSG_HANDOVER_COMPLETE];
        if (r.getTnf() != (short) 1) {
            return null;
        }
        if (!Arrays.equals(r.getType(), NdefRecord.RTD_HANDOVER_REQUEST)) {
            return null;
        }
        BluetoothHandoverData bluetoothData = null;
        NdefRecord[] arr$ = m.getRecords();
        int len$ = arr$.length;
        for (int i$ = MSG_HANDOVER_COMPLETE; i$ < len$; i$ += MSG_HEADSET_CONNECTED) {
            NdefRecord oob = arr$[i$];
            if (oob.getTnf() == (short) 2 && Arrays.equals(oob.getType(), TYPE_BT_OOB)) {
                bluetoothData = parseBtOob(ByteBuffer.wrap(oob.getPayload()));
                break;
            }
        }
        if (bluetoothData == null) {
            return null;
        }
        boolean bluetoothActivating = !this.mBluetoothAdapter.isEnabled() ? true : DBG;
        synchronized (this.mLock) {
            if (this.mEnabled) {
                Message msg = Message.obtain(null, MSG_HEADSET_NOT_CONNECTED);
                PendingHandoverTransfer transfer = registerInTransferLocked(bluetoothData.device);
                Bundle transferData = new Bundle();
                transferData.putParcelable("transfer", transfer);
                msg.setData(transferData);
                if (sendOrQueueMessageLocked(msg)) {
                    whitelistOppDevice(bluetoothData.device);
                    NdefMessage selectMessage = createHandoverSelectMessage(bluetoothActivating);
                    if (!DBG) {
                        return selectMessage;
                    }
                    Log.d(TAG, "Waiting for incoming transfer, [" + bluetoothData.device.getAddress() + "]->[" + this.mLocalBluetoothAddress + "]");
                    return selectMessage;
                }
                removeTransferLocked(transfer.id);
                return null;
            }
            return null;
        }
    }

    public boolean sendOrQueueMessageLocked(Message msg) {
        if (this.mBound && this.mService != null) {
            try {
                this.mService.send(msg);
            } catch (RemoteException e) {
                Log.e(TAG, "Could not connect to handover service");
                return DBG;
            }
        } else if (bindServiceIfNeededLocked()) {
            this.mPendingServiceMessages.add(msg);
        } else {
            Log.e(TAG, "Could not start service");
            return DBG;
        }
        return true;
    }

    public boolean tryHandover(NdefMessage m) {
        if (m == null || this.mBluetoothAdapter == null) {
            return DBG;
        }
        if (DBG) {
            Log.d(TAG, "tryHandover(): " + m.toString());
        }
        BluetoothHandoverData handover = parse(m);
        if (handover == null) {
            return DBG;
        }
        if (!handover.valid) {
            return true;
        }
        synchronized (this.mLock) {
            if (!this.mEnabled) {
                return DBG;
            } else if (this.mBluetoothAdapter == null) {
                if (DBG) {
                    Log.d(TAG, "BT handover, but BT not available");
                }
                return true;
            } else {
                Bundle headsetData = new Bundle();
                headsetData.putParcelable("device", handover.device);
                headsetData.putString("name", handover.name);
                headsetData.putInt("cod", handover.cod);
                Message msg = Message.obtain(null, MSG_INPUTDEVICE_NOT_CONNECTED, MSG_HANDOVER_COMPLETE, MSG_HANDOVER_COMPLETE);
                msg.setData(headsetData);
                boolean sendOrQueueMessageLocked = sendOrQueueMessageLocked(msg);
                return sendOrQueueMessageLocked;
            }
        }
    }

    public void doHandoverUri(Uri[] uris, NdefMessage m) {
        if (this.mBluetoothAdapter != null) {
            BluetoothHandoverData data = parse(m);
            if (data != null && data.valid) {
                synchronized (this.mLock) {
                    Message msg = Message.obtain(null, MSG_INPUTDEVICE_CONNECTED, MSG_HANDOVER_COMPLETE, MSG_HANDOVER_COMPLETE);
                    PendingHandoverTransfer transfer = registerOutTransferLocked(data, uris);
                    Bundle transferData = new Bundle();
                    transferData.putParcelable("transfer", transfer);
                    msg.setData(transferData);
                    if (DBG) {
                        Log.d(TAG, "Initiating outgoing transfer, [" + this.mLocalBluetoothAddress + "]->[" + data.device.getAddress() + "]");
                    }
                    sendOrQueueMessageLocked(msg);
                }
            }
        }
    }

    PendingHandoverTransfer registerInTransferLocked(BluetoothDevice remoteDevice) {
        int i = this.mHandoverTransferId;
        this.mHandoverTransferId = i + MSG_HEADSET_CONNECTED;
        PendingHandoverTransfer transfer = new PendingHandoverTransfer(i, true, remoteDevice, DBG, null);
        this.mPendingTransfers.put(Integer.valueOf(transfer.id), transfer);
        return transfer;
    }

    PendingHandoverTransfer registerOutTransferLocked(BluetoothHandoverData data, Uri[] uris) {
        int i = this.mHandoverTransferId;
        this.mHandoverTransferId = i + MSG_HEADSET_CONNECTED;
        PendingHandoverTransfer transfer = new PendingHandoverTransfer(i, DBG, data.device, data.carrierActivating, uris);
        this.mPendingTransfers.put(Integer.valueOf(transfer.id), transfer);
        return transfer;
    }

    void removeTransferLocked(int id) {
        this.mPendingTransfers.remove(Integer.valueOf(id));
    }

    void whitelistOppDevice(BluetoothDevice device) {
        if (DBG) {
            Log.d(TAG, "Whitelisting " + device + " for BT OPP");
        }
        Intent intent = new Intent(ACTION_WHITELIST_DEVICE);
        intent.putExtra("android.bluetooth.device.extra.DEVICE", device);
        this.mContext.sendBroadcastAsUser(intent, UserHandle.CURRENT);
    }

    boolean isCarrierActivating(NdefRecord handoverRec, byte[] carrierId) {
        byte[] payload = handoverRec.getPayload();
        if (payload == null || payload.length <= MSG_HEADSET_CONNECTED) {
            return DBG;
        }
        byte[] payloadNdef = new byte[(payload.length - 1)];
        System.arraycopy(payload, MSG_HEADSET_CONNECTED, payloadNdef, MSG_HANDOVER_COMPLETE, payload.length - 1);
        try {
            NdefRecord[] arr$ = new NdefMessage(payloadNdef).getRecords();
            int len$ = arr$.length;
            for (int i$ = MSG_HANDOVER_COMPLETE; i$ < len$; i$ += MSG_HEADSET_CONNECTED) {
                byte[] acPayload = arr$[i$].getPayload();
                if (acPayload != null) {
                    ByteBuffer buf = ByteBuffer.wrap(acPayload);
                    int cps = buf.get() & MSG_INPUTDEVICE_CONNECTED;
                    int carrierRefLength = buf.get() & 255;
                    if (carrierRefLength != carrierId.length) {
                        return DBG;
                    }
                    byte[] carrierRefId = new byte[carrierRefLength];
                    buf.get(carrierRefId);
                    if (Arrays.equals(carrierRefId, carrierId)) {
                        return cps == MSG_HEADSET_NOT_CONNECTED ? true : DBG;
                    }
                }
            }
            return true;
        } catch (FormatException e) {
            return DBG;
        }
    }

    BluetoothHandoverData parseHandoverSelect(NdefMessage m) {
        NdefRecord[] arr$ = m.getRecords();
        int len$ = arr$.length;
        for (int i$ = MSG_HANDOVER_COMPLETE; i$ < len$; i$ += MSG_HEADSET_CONNECTED) {
            NdefRecord oob = arr$[i$];
            if (oob.getTnf() == (short) 2 && Arrays.equals(oob.getType(), TYPE_BT_OOB)) {
                BluetoothHandoverData data = parseBtOob(ByteBuffer.wrap(oob.getPayload()));
                if (data == null || !isCarrierActivating(m.getRecords()[MSG_HANDOVER_COMPLETE], oob.getId())) {
                    return data;
                }
                data.carrierActivating = true;
                return data;
            }
        }
        return null;
    }

    BluetoothHandoverData parse(NdefMessage m) {
        NdefRecord r = m.getRecords()[MSG_HANDOVER_COMPLETE];
        short tnf = r.getTnf();
        byte[] type = r.getType();
        if (r.getTnf() == (short) 2 && Arrays.equals(r.getType(), TYPE_BT_OOB)) {
            return parseBtOob(ByteBuffer.wrap(r.getPayload()));
        }
        if (tnf == (short) 1 && Arrays.equals(type, NdefRecord.RTD_HANDOVER_SELECT)) {
            return parseHandoverSelect(m);
        }
        if (tnf == (short) 4 && Arrays.equals(type, TYPE_NOKIA)) {
            return parseNokia(ByteBuffer.wrap(r.getPayload()));
        }
        return null;
    }

    BluetoothHandoverData parseNokia(ByteBuffer payload) {
        BluetoothHandoverData result = new BluetoothHandoverData();
        result.valid = DBG;
        try {
            payload.position(MSG_HEADSET_CONNECTED);
            byte[] address = new byte[6];
            payload.get(address);
            result.device = this.mBluetoothAdapter.getRemoteDevice(address);
            result.valid = true;
            payload.position(14);
            byte[] nameBytes = new byte[payload.get()];
            payload.get(nameBytes);
            result.name = new String(nameBytes, Charset.forName("UTF-8"));
        } catch (IllegalArgumentException e) {
            Log.i(TAG, "nokia: invalid BT address");
        } catch (BufferUnderflowException e2) {
            Log.i(TAG, "nokia: payload shorter than expected");
        }
        if (result.valid && result.name == null) {
            result.name = "";
        }
        return result;
    }

    BluetoothHandoverData parseBtOob(ByteBuffer payload) {
        BluetoothHandoverData result = new BluetoothHandoverData();
        result.valid = DBG;
        try {
            payload.position(MSG_HEADSET_NOT_CONNECTED);
            byte[] address = new byte[6];
            payload.get(address);
            for (int i = MSG_HANDOVER_COMPLETE; i < MSG_INPUTDEVICE_CONNECTED; i += MSG_HEADSET_CONNECTED) {
                byte temp = address[i];
                address[i] = address[5 - i];
                address[5 - i] = temp;
            }
            result.device = this.mBluetoothAdapter.getRemoteDevice(address);
            result.valid = true;
            while (payload.remaining() > 0) {
                int len = payload.get();
                byte[] nameBytes;
                switch (payload.get()) {
                    case ScoverState.COLOR_MINT_BLUE /*8*/:
                        nameBytes = new byte[(len - 1)];
                        payload.get(nameBytes);
                        result.name = new String(nameBytes, Charset.forName("UTF-8"));
                        break;
                    case ScoverState.COLOR_MUSTARD_YELLOW /*9*/:
                        if (result.name != null) {
                            break;
                        }
                        nameBytes = new byte[(len - 1)];
                        payload.get(nameBytes);
                        result.name = new String(nameBytes, Charset.forName("UTF-8"));
                        break;
                    case ScoverState.COLOR_PEARL_WHITE /*13*/:
                        int minor = payload.get();
                        int major = payload.get();
                        int service = payload.get();
                        result.cod = (((service << 16) + (major << 8)) + minor) & 16777215;
                        Log.d(TAG, "minor: " + minor + ", major: " + major + ", service: " + service + ", btclass: " + result.cod);
                        break;
                    default:
                        payload.position((payload.position() + len) - 1);
                        break;
                }
            }
        } catch (IllegalArgumentException e) {
            Log.i(TAG, "BT OOB: invalid BT address");
        } catch (BufferUnderflowException e2) {
            Log.i(TAG, "BT OOB: payload shorter than expected");
        }
        if (result.valid && result.name == null) {
            result.name = "";
        }
        Log.d(TAG, "name: " + result.name);
        return result;
    }

    static byte[] addressToReverseBytes(String address) {
        String[] split = address.split(":");
        byte[] result = new byte[split.length];
        for (int i = MSG_HANDOVER_COMPLETE; i < split.length; i += MSG_HEADSET_CONNECTED) {
            result[(split.length - 1) - i] = (byte) Integer.parseInt(split[i], 16);
        }
        return result;
    }
}
