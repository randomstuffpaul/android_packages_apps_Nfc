package com.android.nfc.handover;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.SoundPool;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.os.UserHandle;
import android.util.Log;
import android.util.Pair;
import com.android.nfc.C0027R;
import com.android.nfc.handover.BluetoothHeadsetHandover.Callback;
import java.io.File;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map.Entry;
import java.util.Queue;

public class HandoverService extends Service implements Callback, BluetoothInputDeviceHandover.Callback, Callback {
    static final String ACTION_BT_OPP_TRANSFER_DONE = "android.btopp.intent.action.BT_OPP_TRANSFER_DONE";
    static final String ACTION_BT_OPP_TRANSFER_PROGRESS = "android.btopp.intent.action.BT_OPP_TRANSFER_PROGRESS";
    static final String ACTION_CANCEL_HANDOVER_TRANSFER = "com.android.nfc.handover.action.CANCEL_HANDOVER_TRANSFER";
    static final String ACTION_HANDOVER_STARTED = "android.btopp.intent.action.BT_OPP_HANDOVER_STARTED";
    static final String BUNDLE_TRANSFER = "transfer";
    static final boolean DBG;
    static final int DIRECTION_BLUETOOTH_INCOMING = 0;
    static final int DIRECTION_BLUETOOTH_OUTGOING = 1;
    static final String EXTRA_BT_OPP_ADDRESS = "android.btopp.intent.extra.BT_OPP_ADDRESS";
    public static final String EXTRA_BT_OPP_OBJECT_COUNT = "android.btopp.intent.extra.BT_OPP_OBJECT_COUNT";
    static final String EXTRA_BT_OPP_TRANSFER_DIRECTION = "android.btopp.intent.extra.BT_OPP_TRANSFER_DIRECTION";
    static final String EXTRA_BT_OPP_TRANSFER_ID = "android.btopp.intent.extra.BT_OPP_TRANSFER_ID";
    static final String EXTRA_BT_OPP_TRANSFER_MIMETYPE = "android.btopp.intent.extra.BT_OPP_TRANSFER_MIMETYPE";
    static final String EXTRA_BT_OPP_TRANSFER_PROGRESS = "android.btopp.intent.extra.BT_OPP_TRANSFER_PROGRESS";
    static final String EXTRA_BT_OPP_TRANSFER_STATUS = "android.btopp.intent.extra.BT_OPP_TRANSFER_STATUS";
    static final String EXTRA_BT_OPP_TRANSFER_URI = "android.btopp.intent.extra.BT_OPP_TRANSFER_URI";
    static final String EXTRA_COD = "cod";
    static final String EXTRA_DEVICE = "device";
    static final String EXTRA_INCOMING = "com.android.nfc.handover.extra.INCOMING";
    static final String EXTRA_NAME = "name";
    static final String EXTRA_SOURCE_ADDRESS = "com.android.nfc.handover.extra.SOURCE_ADDRESS";
    static final String HANDOVER_STATUS_PERMISSION = "com.android.permission.HANDOVER_STATUS";
    static final int HANDOVER_TRANSFER_STATUS_FAILURE = 1;
    static final int HANDOVER_TRANSFER_STATUS_FAILURE_SDCARD_FULL = 2;
    static final int HANDOVER_TRANSFER_STATUS_SUCCESS = 0;
    static final int MSG_DEREGISTER_CLIENT = 1;
    static final int MSG_DEVICE_HANDOVER = 4;
    static final int MSG_REGISTER_CLIENT = 0;
    static final int MSG_START_INCOMING_TRANSFER = 2;
    static final int MSG_START_OUTGOING_TRANSFER = 3;
    public static final int PROFILE_A2DP = 1;
    public static final int PROFILE_HEADSET = 0;
    public static final int PROFILE_HID = 3;
    public static final int PROFILE_NAP = 5;
    public static final int PROFILE_OPP = 2;
    public static final int PROFILE_PANU = 4;
    static final String TAG = "HandoverService";
    int isCancelled;
    BluetoothAdapter mBluetoothAdapter;
    boolean mBluetoothEnabledByNfc;
    boolean mBluetoothHandoverOngoing;
    boolean mBluetoothHeadsetConnected;
    BluetoothHeadsetHandover mBluetoothHeadsetHandover;
    boolean mBluetoothInputDeviceConnected;
    BluetoothInputDeviceHandover mBluetoothInputDeviceHandover;
    int mBtClass;
    Messenger mClient;
    BluetoothDevice mDevice;
    Handler mHandler;
    final Messenger mMessenger;
    String mName;
    final Queue<BluetoothOppHandover> mPendingOutTransfers;
    final BroadcastReceiver mReceiver;
    SoundPool mSoundPool;
    int mSuccessSound;
    final HashMap<Pair<String, Boolean>, HandoverTransfer> mTransfers;

    /* renamed from: com.android.nfc.handover.HandoverService.1 */
    class C00441 extends BroadcastReceiver {
        C00441() {
        }

        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals("android.bluetooth.adapter.action.STATE_CHANGED")) {
                int state = intent.getIntExtra("android.bluetooth.adapter.extra.STATE", Integer.MIN_VALUE);
                if (state == 12) {
                    if (HandoverService.this.mBluetoothHeadsetHandover != null) {
                        if (!HandoverService.this.mBluetoothHeadsetHandover.hasStarted()) {
                            HandoverService.this.mBluetoothHeadsetHandover.start();
                        }
                    }
                    if (HandoverService.this.mBluetoothInputDeviceHandover != null) {
                        if (!HandoverService.this.mBluetoothInputDeviceHandover.hasStarted()) {
                            HandoverService.this.mBluetoothInputDeviceHandover.start();
                        }
                    }
                    HandoverService.this.startPendingTransfers();
                    return;
                } else if (state == 10) {
                    HandoverService.this.mBluetoothEnabledByNfc = HandoverService.DBG;
                    HandoverService.this.mBluetoothHeadsetConnected = HandoverService.DBG;
                    HandoverService.this.mBluetoothInputDeviceConnected = HandoverService.DBG;
                    return;
                } else {
                    return;
                }
            }
            HandoverTransfer transfer;
            if (action.equals(HandoverService.ACTION_CANCEL_HANDOVER_TRANSFER)) {
                transfer = HandoverService.this.findHandoverTransfer(intent.getStringExtra(HandoverService.EXTRA_SOURCE_ADDRESS), intent.getIntExtra(HandoverService.EXTRA_INCOMING, HandoverService.PROFILE_A2DP) == HandoverService.PROFILE_A2DP ? true : HandoverService.DBG);
                if (transfer != null) {
                    if (HandoverService.DBG) {
                        Log.d(HandoverService.TAG, "Cancelling transfer " + Integer.toString(transfer.mTransferId));
                    }
                    HandoverService.this.isCancelled = transfer.getTransferId();
                    transfer.cancel();
                    return;
                }
                return;
            }
            if (!action.equals(HandoverService.ACTION_BT_OPP_TRANSFER_PROGRESS)) {
                if (!action.equals(HandoverService.ACTION_BT_OPP_TRANSFER_DONE)) {
                    if (!action.equals(HandoverService.ACTION_HANDOVER_STARTED)) {
                        return;
                    }
                }
            }
            int direction = intent.getIntExtra(HandoverService.EXTRA_BT_OPP_TRANSFER_DIRECTION, -1);
            int id = intent.getIntExtra(HandoverService.EXTRA_BT_OPP_TRANSFER_ID, -1);
            if (action.equals(HandoverService.ACTION_HANDOVER_STARTED)) {
                direction = HandoverService.PROFILE_HEADSET;
            }
            String sourceAddress = intent.getStringExtra(HandoverService.EXTRA_BT_OPP_ADDRESS);
            if (direction != -1 && sourceAddress != null) {
                transfer = HandoverService.this.findHandoverTransfer(sourceAddress, direction == 0 ? true : HandoverService.DBG);
                if (transfer != null) {
                    if (action.equals(HandoverService.ACTION_BT_OPP_TRANSFER_DONE)) {
                        int handoverStatus = intent.getIntExtra(HandoverService.EXTRA_BT_OPP_TRANSFER_STATUS, HandoverService.PROFILE_A2DP);
                        if (handoverStatus == 0) {
                            String uriString = intent.getStringExtra(HandoverService.EXTRA_BT_OPP_TRANSFER_URI);
                            String mimeType = intent.getStringExtra(HandoverService.EXTRA_BT_OPP_TRANSFER_MIMETYPE);
                            Uri uri = Uri.parse(uriString);
                            if (HandoverService.DBG) {
                                Log.d(HandoverService.TAG, "uriSring: " + uriString + " MimeType : " + mimeType);
                                Log.d(HandoverService.TAG, "uri.toString() : " + uri.toString() + "uri.getPath() : " + uri.getPath());
                            }
                            if (uri.getScheme() == null) {
                                uri = Uri.fromFile(new File(uriString));
                            }
                            transfer.finishTransfer(true, uri, mimeType);
                            return;
                        } else if (handoverStatus == HandoverService.PROFILE_OPP) {
                            if (HandoverService.DBG) {
                                Log.d(HandoverService.TAG, "Displays popup because there're not enough memory in phone.");
                            }
                            transfer.finishTransfer(HandoverService.DBG, null, null);
                            Intent memoryPopupIntent = new Intent("com.android.nfc.AndroidBeamPopUp");
                            memoryPopupIntent.putExtra("POPUP_MODE", "disk_full");
                            context.startActivityAsUser(memoryPopupIntent, UserHandle.CURRENT);
                            return;
                        } else {
                            transfer.finishTransfer(HandoverService.DBG, null, null);
                            return;
                        }
                    }
                    if (action.equals(HandoverService.ACTION_BT_OPP_TRANSFER_PROGRESS)) {
                        transfer.updateFileProgress(intent.getFloatExtra(HandoverService.EXTRA_BT_OPP_TRANSFER_PROGRESS, 0.0f));
                        return;
                    }
                    if (action.equals(HandoverService.ACTION_HANDOVER_STARTED)) {
                        int count = intent.getIntExtra(HandoverService.EXTRA_BT_OPP_OBJECT_COUNT, HandoverService.PROFILE_HEADSET);
                        if (count > 0) {
                            transfer.setObjectCount(count);
                        }
                    }
                } else if (id != -1) {
                    if (HandoverService.DBG) {
                        Log.d(HandoverService.TAG, "Didn't find transfer, stopping");
                    }
                    Intent cancelIntent = new Intent("android.btopp.intent.action.STOP_HANDOVER_TRANSFER");
                    cancelIntent.putExtra(HandoverService.EXTRA_BT_OPP_TRANSFER_ID, id);
                    HandoverService.this.sendBroadcast(cancelIntent);
                    int i = HandoverService.this.isCancelled;
                    if (r0 != -1) {
                        HandoverService.this.notifyClientTransferComplete(HandoverService.this.isCancelled);
                        HandoverService.this.isCancelled = -1;
                    }
                }
            }
        }
    }

    public static class Device {
        public static final int AUDIO_VIDEO_CAMCORDER = 1076;
        public static final int AUDIO_VIDEO_CAR_AUDIO = 1056;
        public static final int AUDIO_VIDEO_HANDSFREE = 1032;
        public static final int AUDIO_VIDEO_HEADPHONES = 1048;
        public static final int AUDIO_VIDEO_HIFI_AUDIO = 1064;
        public static final int AUDIO_VIDEO_LOUDSPEAKER = 1044;
        public static final int AUDIO_VIDEO_MICROPHONE = 1040;
        public static final int AUDIO_VIDEO_PORTABLE_AUDIO = 1052;
        public static final int AUDIO_VIDEO_SET_TOP_BOX = 1060;
        public static final int AUDIO_VIDEO_UNCATEGORIZED = 1024;
        public static final int AUDIO_VIDEO_VCR = 1068;
        public static final int AUDIO_VIDEO_VIDEO_CAMERA = 1072;
        public static final int AUDIO_VIDEO_VIDEO_CONFERENCING = 1088;
        public static final int AUDIO_VIDEO_VIDEO_DISPLAY_AND_LOUDSPEAKER = 1084;
        public static final int AUDIO_VIDEO_VIDEO_GAMING_TOY = 1096;
        public static final int AUDIO_VIDEO_VIDEO_MONITOR = 1080;
        public static final int AUDIO_VIDEO_WEARABLE_HEADSET = 1028;
        private static final int BITMASK = 8188;
        private static final int BITMASK_PERIPHERAL = 1472;
        private static final int BITMASK_PERIPHERAL_SUBCLASS = 1340;
        public static final int COMPUTER_DESKTOP = 260;
        public static final int COMPUTER_HANDHELD_PC_PDA = 272;
        public static final int COMPUTER_LAPTOP = 268;
        public static final int COMPUTER_PALM_SIZE_PC_PDA = 276;
        public static final int COMPUTER_SERVER = 264;
        public static final int COMPUTER_UNCATEGORIZED = 256;
        public static final int COMPUTER_WEARABLE = 280;
        public static final int HEALTH_BLOOD_PRESSURE = 2308;
        public static final int HEALTH_DATA_DISPLAY = 2332;
        public static final int HEALTH_GLUCOSE = 2320;
        public static final int HEALTH_PULSE_OXIMETER = 2324;
        public static final int HEALTH_PULSE_RATE = 2328;
        public static final int HEALTH_THERMOMETER = 2312;
        public static final int HEALTH_UNCATEGORIZED = 2304;
        public static final int HEALTH_WEIGHING = 2316;
        public static final int IMAGING_CAMERA = 1568;
        public static final int PERIPHERAL_GAMEPAD = 1288;
        public static final int PERIPHERAL_JOYSTICK = 1284;
        public static final int PERIPHERAL_KEYBOARD = 1344;
        public static final int PERIPHERAL_KEYBOARD_POINTING = 1472;
        public static final int PERIPHERAL_NON_KEYBOARD_NON_POINTING = 1280;
        public static final int PERIPHERAL_POINTING = 1408;
        public static final int PERIPHERAL_REMOTE_CONTROL = 1292;
        public static final int PHONE_CELLULAR = 516;
        public static final int PHONE_CORDLESS = 520;
        public static final int PHONE_ISDN = 532;
        public static final int PHONE_MODEM_OR_GATEWAY = 528;
        public static final int PHONE_SMART = 524;
        public static final int PHONE_UNCATEGORIZED = 512;
        public static final int TOY_CONTROLLER = 2064;
        public static final int TOY_DOLL_ACTION_FIGURE = 2060;
        public static final int TOY_GAME = 2068;
        public static final int TOY_ROBOT = 2052;
        public static final int TOY_UNCATEGORIZED = 2048;
        public static final int TOY_VEHICLE = 2056;
        public static final int WEARABLE_GLASSES = 1812;
        public static final int WEARABLE_HELMET = 1808;
        public static final int WEARABLE_JACKET = 1804;
        public static final int WEARABLE_PAGER = 1800;
        public static final int WEARABLE_UNCATEGORIZED = 1792;
        public static final int WEARABLE_WRIST_WATCH = 1796;

        public static class Major {
            public static final int AUDIO_VIDEO = 1024;
            private static final int BITMASK = 7936;
            public static final int COMPUTER = 256;
            public static final int HEALTH = 2304;
            public static final int IMAGING = 1536;
            public static final int MISC = 0;
            public static final int NETWORKING = 768;
            public static final int PERIPHERAL = 1280;
            public static final int PHONE = 512;
            public static final int TOY = 2048;
            public static final int UNCATEGORIZED = 7936;
            public static final int WEARABLE = 1792;
        }
    }

    class MessageHandler extends Handler {
        MessageHandler() {
        }

        public void handleMessage(Message msg) {
            boolean z = true;
            switch (msg.what) {
                case HandoverService.PROFILE_HEADSET /*0*/:
                    boolean z2;
                    HandoverService.this.mClient = msg.replyTo;
                    HandoverService handoverService = HandoverService.this;
                    if (msg.arg1 != 0) {
                        z2 = true;
                    } else {
                        z2 = HandoverService.DBG;
                    }
                    handoverService.mBluetoothEnabledByNfc = z2;
                    HandoverService handoverService2 = HandoverService.this;
                    if (msg.arg2 == 0) {
                        z = HandoverService.DBG;
                    }
                    handoverService2.mBluetoothHeadsetConnected = z;
                case HandoverService.PROFILE_A2DP /*1*/:
                    HandoverService.this.mClient = null;
                case HandoverService.PROFILE_OPP /*2*/:
                    HandoverService.this.doIncomingTransfer(msg);
                case HandoverService.PROFILE_HID /*3*/:
                    HandoverService.this.doOutgoingTransfer(msg);
                case HandoverService.PROFILE_PANU /*4*/:
                    HandoverService.this.doCheckDevice(msg);
                default:
            }
        }
    }

    static {
        DBG = HandoverManager.DBG;
    }

    public HandoverService() {
        this.isCancelled = -1;
        this.mReceiver = new C00441();
        this.mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        this.mPendingOutTransfers = new LinkedList();
        this.mTransfers = new HashMap();
        this.mHandler = new MessageHandler();
        this.mMessenger = new Messenger(this.mHandler);
        this.mBluetoothHeadsetConnected = DBG;
        this.mBluetoothInputDeviceConnected = DBG;
        this.mBluetoothEnabledByNfc = DBG;
    }

    public void onCreate() {
        super.onCreate();
        this.mSoundPool = new SoundPool(PROFILE_A2DP, PROFILE_NAP, PROFILE_HEADSET);
        this.mSuccessSound = this.mSoundPool.load(this, C0027R.raw.end, PROFILE_A2DP);
        this.mBluetoothHandoverOngoing = DBG;
        IntentFilter filter = new IntentFilter(ACTION_BT_OPP_TRANSFER_DONE);
        filter.addAction(ACTION_BT_OPP_TRANSFER_PROGRESS);
        filter.addAction(ACTION_CANCEL_HANDOVER_TRANSFER);
        filter.addAction("android.bluetooth.adapter.action.STATE_CHANGED");
        filter.addAction(ACTION_HANDOVER_STARTED);
        registerReceiver(this.mReceiver, filter, HANDOVER_STATUS_PERMISSION, this.mHandler);
    }

    public void onDestroy() {
        super.onDestroy();
        if (this.mSoundPool != null) {
            this.mSoundPool.release();
        }
        unregisterReceiver(this.mReceiver);
    }

    void doOutgoingTransfer(Message msg) {
        Bundle msgData = msg.getData();
        msgData.setClassLoader(getClassLoader());
        PendingHandoverTransfer pendingTransfer = (PendingHandoverTransfer) msgData.getParcelable(BUNDLE_TRANSFER);
        createHandoverTransfer(pendingTransfer);
        BluetoothOppHandover handover = new BluetoothOppHandover(this, pendingTransfer.remoteDevice, pendingTransfer.uris, pendingTransfer.remoteActivating);
        if (this.mBluetoothAdapter.isEnabled()) {
            handover.start();
        } else if (enableBluetooth()) {
            if (DBG) {
                Log.d(TAG, "Queueing out transfer " + Integer.toString(pendingTransfer.id));
            }
            this.mPendingOutTransfers.add(handover);
        } else {
            Log.e(TAG, "Error enabling Bluetooth.");
            notifyClientTransferComplete(pendingTransfer.id);
        }
    }

    void doIncomingTransfer(Message msg) {
        Bundle msgData = msg.getData();
        msgData.setClassLoader(getClassLoader());
        PendingHandoverTransfer pendingTransfer = (PendingHandoverTransfer) msgData.getParcelable(BUNDLE_TRANSFER);
        if (this.mBluetoothAdapter.isEnabled() || enableBluetooth()) {
            createHandoverTransfer(pendingTransfer);
            return;
        }
        Log.e(TAG, "Error enabling Bluetooth.");
        notifyClientTransferComplete(pendingTransfer.id);
    }

    void doCheckDevice(Message msg) {
        Bundle msgData = msg.getData();
        this.mDevice = (BluetoothDevice) msgData.getParcelable(EXTRA_DEVICE);
        this.mName = msgData.getString(EXTRA_NAME);
        this.mBtClass = msgData.getInt(EXTRA_COD);
        if (this.mBluetoothHandoverOngoing) {
            Log.d(TAG, "Ignoring pairing request, existing handover in progress.");
            return;
        }
        this.mBluetoothHandoverOngoing = true;
        if (this.mDevice == null) {
            if (DBG) {
                Log.e(TAG, "device is null!!");
            } else if (DBG) {
                Log.e(TAG, "device is " + this.mDevice + "   name is " + this.mName + "  cod is  " + this.mBtClass);
            }
        }
        if (doesClassMatch(this.mBtClass, PROFILE_HEADSET) || doesClassMatch(this.mBtClass, PROFILE_A2DP)) {
            doHeadsetHandover(this.mDevice, this.mName, this.mBtClass);
        } else if (doesClassMatch(this.mBtClass, PROFILE_HID)) {
            doInputDeviceHandover(this.mDevice, this.mName, this.mBtClass);
        } else {
            Log.e(TAG, "The device is not a headset or a hid, but try to pair");
            doHeadsetHandover(this.mDevice, this.mName, this.mBtClass);
        }
    }

    void doHeadsetHandover(BluetoothDevice device, String name, int cod) {
        this.mBluetoothHeadsetHandover = new BluetoothHeadsetHandover(this, device, name, cod, this);
        if (this.mBluetoothAdapter.isEnabled()) {
            this.mBluetoothHeadsetHandover.start();
        } else if (!enableBluetooth()) {
            Log.e(TAG, "Error enabling Bluetooth.");
            this.mBluetoothHeadsetHandover = null;
            this.mBluetoothHandoverOngoing = DBG;
        }
    }

    void doInputDeviceHandover(BluetoothDevice device, String name, int cod) {
        this.mBluetoothInputDeviceHandover = new BluetoothInputDeviceHandover(this, device, name, cod, this);
        if (this.mBluetoothAdapter.isEnabled()) {
            this.mBluetoothInputDeviceHandover.start();
        } else if (!enableBluetooth()) {
            Log.e(TAG, "Error enabling Bluetooth.");
            this.mBluetoothInputDeviceHandover = null;
            this.mBluetoothHandoverOngoing = DBG;
        }
    }

    void startPendingTransfers() {
        while (!this.mPendingOutTransfers.isEmpty()) {
            ((BluetoothOppHandover) this.mPendingOutTransfers.remove()).start();
        }
    }

    boolean enableBluetooth() {
        if (this.mBluetoothAdapter.isEnabled()) {
            return true;
        }
        this.mBluetoothEnabledByNfc = true;
        return this.mBluetoothAdapter.enableNoAutoConnect();
    }

    void disableBluetoothIfNeeded() {
        if (this.mBluetoothEnabledByNfc) {
            Log.e(TAG, "mTransfers.size(): " + this.mTransfers.size() + ", mBluetoothHeadsetConnected: " + this.mBluetoothHeadsetConnected + ", mBluetoothInputDeviceConnected: " + this.mBluetoothInputDeviceConnected);
            if (this.mTransfers.size() == 0 && !this.mBluetoothHeadsetConnected && !this.mBluetoothInputDeviceConnected) {
                this.mBluetoothAdapter.disable();
                this.mBluetoothEnabledByNfc = DBG;
            }
        }
    }

    void createHandoverTransfer(PendingHandoverTransfer pendingTransfer) {
        Pair<String, Boolean> key = new Pair(pendingTransfer.remoteDevice.getAddress(), Boolean.valueOf(pendingTransfer.incoming));
        if (this.mTransfers.containsKey(key)) {
            if (((HandoverTransfer) this.mTransfers.get(key)).isRunning()) {
                notifyClientTransferComplete(pendingTransfer.id);
                return;
            }
            this.mTransfers.remove(key);
        }
        HandoverTransfer transfer = new HandoverTransfer(this, this, pendingTransfer);
        this.mTransfers.put(key, transfer);
        transfer.updateNotification();
    }

    HandoverTransfer findHandoverTransfer(String sourceAddress, boolean incoming) {
        Pair<String, Boolean> key = new Pair(sourceAddress, Boolean.valueOf(incoming));
        if (this.mTransfers.containsKey(key)) {
            HandoverTransfer transfer = (HandoverTransfer) this.mTransfers.get(key);
            if (transfer.isRunning()) {
                return transfer;
            }
        }
        return null;
    }

    public IBinder onBind(Intent intent) {
        return this.mMessenger.getBinder();
    }

    void notifyClientTransferComplete(int transferId) {
        if (this.mClient != null) {
            Message msg = Message.obtain(null, PROFILE_NAP);
            msg.arg1 = transferId;
            try {
                this.mClient.send(msg);
            } catch (RemoteException e) {
            }
        }
    }

    public boolean onUnbind(Intent intent) {
        this.mClient = null;
        return DBG;
    }

    public void onTransferComplete(HandoverTransfer transfer, boolean success) {
        Iterator it = this.mTransfers.entrySet().iterator();
        while (it.hasNext()) {
            if (((HandoverTransfer) ((Entry) it.next()).getValue()) == transfer) {
                it.remove();
            }
        }
        if (this.isCancelled == -1) {
            notifyClientTransferComplete(transfer.getTransferId());
        }
        if (success) {
            this.mSoundPool.play(this.mSuccessSound, 1.0f, 1.0f, PROFILE_HEADSET, PROFILE_HEADSET, 1.0f);
        } else if (DBG) {
            Log.d(TAG, "Transfer failed, final state: " + Integer.toString(transfer.mState));
        }
        disableBluetoothIfNeeded();
    }

    public void onBluetoothHeadsetHandoverComplete(boolean connected) {
        int i = PROFILE_A2DP;
        this.mBluetoothHeadsetHandover = null;
        this.mBluetoothHeadsetConnected = connected;
        if (this.mClient != null) {
            Message msg = Message.obtain(null, connected ? PROFILE_A2DP : PROFILE_OPP);
            if (!this.mBluetoothEnabledByNfc) {
                i = PROFILE_HEADSET;
            }
            msg.arg1 = i;
            try {
                this.mClient.send(msg);
            } catch (RemoteException e) {
            }
        }
        disableBluetoothIfNeeded();
        this.mBluetoothHandoverOngoing = DBG;
    }

    public void onBluetoothInputDeviceHandoverComplete(boolean connected) {
        this.mBluetoothInputDeviceHandover = null;
        this.mBluetoothInputDeviceConnected = connected;
        if (this.mClient != null) {
            try {
                this.mClient.send(Message.obtain(null, connected ? PROFILE_HID : PROFILE_PANU));
            } catch (RemoteException e) {
            }
        }
        disableBluetoothIfNeeded();
        this.mBluetoothHandoverOngoing = DBG;
    }

    private boolean doesClassMatch(int cod, int profile) {
        cod &= 8188;
        if (DBG) {
            Log.d(TAG, "doesClassMatch : cod is " + cod);
        }
        if (profile == PROFILE_A2DP) {
            switch (cod) {
                case Device.AUDIO_VIDEO_LOUDSPEAKER /*1044*/:
                case Device.AUDIO_VIDEO_HEADPHONES /*1048*/:
                case Device.AUDIO_VIDEO_CAR_AUDIO /*1056*/:
                case Device.AUDIO_VIDEO_HIFI_AUDIO /*1064*/:
                    return true;
                default:
                    return DBG;
            }
        } else if (profile == 0) {
            switch (cod) {
                case Device.AUDIO_VIDEO_WEARABLE_HEADSET /*1028*/:
                case Device.AUDIO_VIDEO_HANDSFREE /*1032*/:
                case Device.AUDIO_VIDEO_CAR_AUDIO /*1056*/:
                    return true;
                default:
                    return DBG;
            }
        } else if (profile == PROFILE_OPP) {
            switch (cod) {
                case Device.COMPUTER_UNCATEGORIZED /*256*/:
                case Device.COMPUTER_DESKTOP /*260*/:
                case Device.COMPUTER_SERVER /*264*/:
                case Device.COMPUTER_LAPTOP /*268*/:
                case Device.COMPUTER_HANDHELD_PC_PDA /*272*/:
                case Device.COMPUTER_PALM_SIZE_PC_PDA /*276*/:
                case Device.COMPUTER_WEARABLE /*280*/:
                case Device.PHONE_UNCATEGORIZED /*512*/:
                case Device.PHONE_CELLULAR /*516*/:
                case Device.PHONE_CORDLESS /*520*/:
                case Device.PHONE_SMART /*524*/:
                case Device.PHONE_MODEM_OR_GATEWAY /*528*/:
                case Device.PHONE_ISDN /*532*/:
                    return true;
                default:
                    return DBG;
            }
        } else if (profile == PROFILE_HID) {
            if ((cod & Device.PERIPHERAL_NON_KEYBOARD_NON_POINTING) != Device.PERIPHERAL_NON_KEYBOARD_NON_POINTING) {
                return DBG;
            }
            return true;
        } else if (profile != PROFILE_PANU && profile != PROFILE_NAP) {
            return DBG;
        } else {
            if ((cod & Major.NETWORKING) != Major.NETWORKING) {
                return DBG;
            }
            return true;
        }
    }
}
