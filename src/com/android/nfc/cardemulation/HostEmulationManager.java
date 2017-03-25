package com.android.nfc.cardemulation;

import android.app.ActivityManager;
import android.app.KeyguardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.database.ContentObserver;
import android.net.Uri;
import android.nfc.cardemulation.ApduServiceInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.os.UserHandle;
import android.provider.Settings.Secure;
import android.util.Log;
import com.android.nfc.NfcService;
import com.android.nfc.snep.SnepMessage;
import java.util.ArrayList;

public class HostEmulationManager {
    static final byte[] AID_NOT_FOUND;
    static final String ANDROID_HCE_AID = "A000000476416E64726F6964484345";
    static final byte[] ANDROID_HCE_RESPONSE;
    static final boolean DBG;
    static final byte INSTR_SELECT = (byte) -92;
    static final int MINIMUM_AID_LENGTH = 5;
    static final int SCREEN_STATE_OFF = 1;
    static final int SCREEN_STATE_ON_LOCKED = 2;
    static final int SCREEN_STATE_ON_UNLOCKED = 3;
    static final int SELECT_APDU_HDR_LENGTH = 5;
    static final int STATE_IDLE = 0;
    static final int STATE_W4_DEACTIVATE = 3;
    static final int STATE_W4_SELECT = 1;
    static final int STATE_W4_SERVICE = 2;
    static final int STATE_XFER = 4;
    static final String TAG = "HostEmulationManager";
    static final byte[] UNKNOWN_ERROR;
    Messenger mActiveService;
    ComponentName mActiveServiceName;
    final RegisteredAidCache mAidCache;
    public boolean mClearNextTapDefault;
    private ServiceConnection mConnection;
    final Context mContext;
    final Handler mHandler;
    final KeyguardManager mKeyguard;
    String mLastSelectedAid;
    final Object mLock;
    final Messenger mMessenger;
    private ServiceConnection mPaymentConnection;
    Messenger mPaymentService;
    boolean mPaymentServiceBound;
    ComponentName mPaymentServiceName;
    int mScreenState;
    byte[] mSelectApdu;
    Messenger mService;
    boolean mServiceBound;
    ComponentName mServiceName;
    int mState;

    /* renamed from: com.android.nfc.cardemulation.HostEmulationManager.1 */
    class C00301 implements ServiceConnection {
        C00301() {
        }

        public void onServiceConnected(ComponentName name, IBinder service) {
            synchronized (HostEmulationManager.this.mLock) {
                HostEmulationManager.this.mPaymentServiceName = name;
                HostEmulationManager.this.mPaymentService = new Messenger(service);
                HostEmulationManager.this.mPaymentServiceBound = true;
            }
        }

        public void onServiceDisconnected(ComponentName name) {
            synchronized (HostEmulationManager.this.mLock) {
                HostEmulationManager.this.mPaymentService = null;
                HostEmulationManager.this.mPaymentServiceBound = HostEmulationManager.DBG;
                HostEmulationManager.this.mPaymentServiceName = null;
            }
        }
    }

    /* renamed from: com.android.nfc.cardemulation.HostEmulationManager.2 */
    class C00312 implements ServiceConnection {
        C00312() {
        }

        public void onServiceConnected(ComponentName name, IBinder service) {
            synchronized (HostEmulationManager.this.mLock) {
                HostEmulationManager.this.mService = new Messenger(service);
                HostEmulationManager.this.mServiceBound = true;
                HostEmulationManager.this.mServiceName = name;
                Log.d(HostEmulationManager.TAG, "Service bound");
                HostEmulationManager.this.mState = HostEmulationManager.STATE_XFER;
                if (HostEmulationManager.this.mSelectApdu != null) {
                    HostEmulationManager.this.sendDataToServiceLocked(HostEmulationManager.this.mService, HostEmulationManager.this.mSelectApdu);
                    HostEmulationManager.this.mSelectApdu = null;
                }
            }
        }

        public void onServiceDisconnected(ComponentName name) {
            synchronized (HostEmulationManager.this.mLock) {
                Log.d(HostEmulationManager.TAG, "Service unbound");
                HostEmulationManager.this.mService = null;
                HostEmulationManager.this.mServiceBound = HostEmulationManager.DBG;
            }
        }
    }

    class MessageHandler extends Handler {
        MessageHandler() {
        }

        public void handleMessage(Message msg) {
            synchronized (HostEmulationManager.this.mLock) {
                if (HostEmulationManager.this.mActiveService == null) {
                    Log.d(HostEmulationManager.TAG, "Dropping service response message; service no longer active.");
                } else if (msg.replyTo.getBinder().equals(HostEmulationManager.this.mActiveService.getBinder())) {
                    if (msg.what == HostEmulationManager.STATE_W4_SELECT) {
                        Bundle dataBundle = msg.getData();
                        if (dataBundle != null) {
                            byte[] data = dataBundle.getByteArray("data");
                            if (data == null || data.length == 0) {
                                Log.e(HostEmulationManager.TAG, "Dropping empty R-APDU");
                                return;
                            }
                            int state;
                            synchronized (HostEmulationManager.this.mLock) {
                                state = HostEmulationManager.this.mState;
                            }
                            if (state == HostEmulationManager.STATE_XFER) {
                                Log.d(HostEmulationManager.TAG, "Sending data");
                                NfcService.getInstance().sendData(data);
                                return;
                            }
                            Log.d(HostEmulationManager.TAG, "Dropping data, wrong state " + Integer.toString(state));
                        }
                    } else if (msg.what == HostEmulationManager.STATE_W4_DEACTIVATE) {
                        synchronized (HostEmulationManager.this.mLock) {
                            AidResolveInfo resolveInfo = HostEmulationManager.this.mAidCache.resolveAidPrefix(HostEmulationManager.this.mLastSelectedAid);
                            String category = HostEmulationManager.this.mAidCache.getCategoryForAid(HostEmulationManager.this.mLastSelectedAid);
                            if (resolveInfo.services.size() > 0) {
                                ArrayList<ApduServiceInfo> services = new ArrayList();
                                for (ApduServiceInfo service : resolveInfo.services) {
                                    if (!service.getComponent().equals(HostEmulationManager.this.mActiveServiceName)) {
                                        services.add(service);
                                    }
                                }
                                HostEmulationManager.this.launchResolver(services, HostEmulationManager.this.mActiveServiceName, category);
                            }
                        }
                    }
                } else {
                    Log.d(HostEmulationManager.TAG, "Dropping service response message; service no longer bound.");
                }
            }
        }
    }

    private final class SettingsObserver extends ContentObserver {
        public SettingsObserver(Handler handler) {
            super(handler);
        }

        public void onChange(boolean selfChange, Uri uri) {
            super.onChange(selfChange, uri);
            synchronized (HostEmulationManager.this.mLock) {
                int userId = ActivityManager.getCurrentUser();
                ComponentName paymentApp = HostEmulationManager.this.mAidCache.getDefaultServiceForCategory(userId, "payment", true);
                if (paymentApp != null) {
                    HostEmulationManager.this.bindPaymentServiceLocked(userId, paymentApp);
                } else {
                    HostEmulationManager.this.unbindPaymentServiceLocked(userId);
                }
            }
        }
    }

    static {
        DBG = AidRoutingManager.DBG;
        ANDROID_HCE_RESPONSE = new byte[]{(byte) 20, SnepMessage.RESPONSE_SUCCESS, (byte) 0, (byte) 0, (byte) -112, (byte) 0};
        AID_NOT_FOUND = new byte[]{(byte) 106, (byte) -126};
        UNKNOWN_ERROR = new byte[]{(byte) 111, (byte) 0};
    }

    public HostEmulationManager(Context context, RegisteredAidCache aidCache) {
        this.mMessenger = new Messenger(new MessageHandler());
        this.mHandler = new Handler(Looper.getMainLooper());
        this.mPaymentConnection = new C00301();
        this.mConnection = new C00312();
        this.mContext = context;
        this.mLock = new Object();
        this.mAidCache = aidCache;
        this.mState = STATE_IDLE;
        this.mScreenState = STATE_W4_DEACTIVATE;
        this.mKeyguard = (KeyguardManager) context.getSystemService("keyguard");
        context.getContentResolver().registerContentObserver(Secure.getUriFor("nfc_payment_default_component"), true, new SettingsObserver(this.mHandler), -1);
        int userId = ActivityManager.getCurrentUser();
        String name = Secure.getStringForUser(this.mContext.getContentResolver(), "nfc_payment_default_component", userId);
        if (name != null) {
            bindPaymentServiceLocked(userId, ComponentName.unflattenFromString(name));
        }
    }

    public void setDefaultForNextTap(ComponentName service) {
        synchronized (this.mLock) {
            if (service != null) {
                bindServiceIfNeededLocked(service);
            } else {
                unbindServiceIfNeededLocked();
            }
        }
    }

    public void setScreenState(int state) {
        this.mScreenState = state;
    }

    public void notifyHostEmulationActivated() {
        Log.d(TAG, "notifyHostEmulationActivated");
        synchronized (this.mLock) {
            this.mClearNextTapDefault = this.mAidCache.isNextTapOverriden();
            Intent intent = new Intent(TapAgainDialog.ACTION_CLOSE);
            intent.setPackage("com.android.nfc");
            this.mContext.sendBroadcastAsUser(intent, UserHandle.ALL);
            if (this.mState != 0) {
                Log.e(TAG, "Got activation event in non-idle state");
            }
            this.mState = STATE_W4_SELECT;
        }
    }

    public void notifyHostEmulationData(byte[] data) {
        Log.d(TAG, "notifyHostEmulationData");
        String selectAid = findSelectAid(data);
        ComponentName resolvedService = null;
        boolean isPartialMatched = DBG;
        synchronized (this.mLock) {
            if (this.mState == 0) {
                Log.e(TAG, "Got data in idle state.");
            } else if (this.mState == STATE_W4_DEACTIVATE) {
                Log.e(TAG, "Dropping APDU in STATE_W4_DECTIVATE");
            } else if ("NXP_PN544C3".equals("NXP_PN544C3") && this.mScreenState == STATE_W4_SELECT) {
                NfcService.getInstance().sendData(AID_NOT_FOUND);
            } else {
                if (selectAid != null) {
                    if ("NXP_PN544C3".equals("NXP_PN544C3") || !selectAid.equals(ANDROID_HCE_AID)) {
                        String category;
                        AidResolveInfo resolveInfo = this.mAidCache.resolveAidPrefix(selectAid);
                        if (resolveInfo == null || resolveInfo.services.size() == 0) {
                            if ("NXP_PN544C3".equals("NXP_PN544C3")) {
                                resolveInfo = this.mAidCache.resolveAidPartialMatch(selectAid);
                                if (resolveInfo == null || resolveInfo.services.size() == 0) {
                                    if (DBG) {
                                        Log.d(TAG, "we don't handle this AID : " + selectAid);
                                    }
                                    NfcService.getInstance().sendData(AID_NOT_FOUND);
                                    return;
                                }
                                isPartialMatched = true;
                            } else {
                                if (DBG) {
                                    Log.d(TAG, "we don't handle this AID : " + selectAid);
                                }
                                NfcService.getInstance().sendData(AID_NOT_FOUND);
                                return;
                            }
                        }
                        this.mLastSelectedAid = resolveInfo.aid;
                        if (!"NXP_PN544C3".equals("NXP_PN544C3") || ("NXP_PN544C3".equals("NXP_PN544C3") && !isPartialMatched)) {
                            if (resolveInfo.defaultService == null) {
                                if (this.mActiveServiceName != null) {
                                    for (ApduServiceInfo service : resolveInfo.services) {
                                        if (this.mActiveServiceName.equals(service.getComponent())) {
                                            resolvedService = this.mActiveServiceName;
                                            break;
                                        }
                                    }
                                }
                            } else if (resolveInfo.defaultService.requiresUnlock() && this.mKeyguard.isKeyguardLocked() && this.mKeyguard.isKeyguardSecure()) {
                                category = this.mAidCache.getCategoryForAid(resolveInfo.aid);
                                this.mState = STATE_W4_DEACTIVATE;
                                launchTapAgain(resolveInfo.defaultService, category);
                                return;
                            } else if (resolveInfo.defaultService.isOnHost()) {
                                resolvedService = resolveInfo.defaultService.getComponent();
                            } else {
                                Log.e(TAG, "AID that was meant to go off-host was routed to host. Check routing table configuration.");
                                NfcService.getInstance().sendData(AID_NOT_FOUND);
                                return;
                            }
                        }
                        if (resolvedService == null) {
                            category = this.mAidCache.getCategoryForAid(resolveInfo.aid);
                            this.mState = STATE_W4_DEACTIVATE;
                            if ("NXP_PN544C3".equals("NXP_PN544C3")) {
                                ArrayList<ApduServiceInfo> services = resolveInfo.services;
                                if (isPartialMatched) {
                                    services = new ArrayList(resolveInfo.services);
                                    for (ApduServiceInfo service2 : resolveInfo.services) {
                                        if (resolveInfo.aid.length() > this.mLastSelectedAid.length()) {
                                            services.remove(service2);
                                        }
                                    }
                                }
                                launchResolver(services, null, category);
                            } else {
                                launchResolver((ArrayList) resolveInfo.services, null, category);
                            }
                            return;
                        }
                    }
                    NfcService.getInstance().sendData(ANDROID_HCE_RESPONSE);
                    return;
                }
                Messenger existingService;
                switch (this.mState) {
                    case STATE_W4_SELECT /*1*/:
                        if (selectAid == null) {
                            Log.d(TAG, "Dropping non-select APDU in STATE_W4_SELECT");
                            NfcService.getInstance().sendData(UNKNOWN_ERROR);
                            break;
                        }
                        existingService = bindServiceIfNeededLocked(resolvedService);
                        if (existingService == null) {
                            Log.d(TAG, "Waiting for new service.");
                            this.mSelectApdu = data;
                            this.mState = STATE_W4_SERVICE;
                            break;
                        }
                        Log.d(TAG, "Binding to existing service");
                        this.mState = STATE_XFER;
                        sendDataToServiceLocked(existingService, data);
                        break;
                    case STATE_W4_SERVICE /*2*/:
                        Log.d(TAG, "Unexpected APDU in STATE_W4_SERVICE");
                        break;
                    case STATE_XFER /*4*/:
                        if (selectAid == null) {
                            if (this.mActiveService == null) {
                                Log.d(TAG, "Service no longer bound, dropping APDU");
                                break;
                            } else {
                                sendDataToServiceLocked(this.mActiveService, data);
                                break;
                            }
                        }
                        existingService = bindServiceIfNeededLocked(resolvedService);
                        if (existingService == null) {
                            this.mSelectApdu = data;
                            this.mState = STATE_W4_SERVICE;
                            break;
                        }
                        sendDataToServiceLocked(existingService, data);
                        this.mState = STATE_XFER;
                        break;
                }
            }
        }
    }

    public void notifyNostEmulationDeactivated() {
        Log.d(TAG, "notifyHostEmulationDeactivated");
        synchronized (this.mLock) {
            if (this.mState == 0) {
                Log.e(TAG, "Got deactivation event while in idle state");
            }
            if (this.mClearNextTapDefault) {
                this.mAidCache.setDefaultForNextTap(ActivityManager.getCurrentUser(), null);
            }
            sendDeactivateToActiveServiceLocked(STATE_IDLE);
            this.mActiveService = null;
            this.mActiveServiceName = null;
            unbindServiceIfNeededLocked();
            this.mState = STATE_IDLE;
        }
    }

    public void notifyOffHostAidSelected() {
        Log.d(TAG, "notifyOffHostAidSelected");
        synchronized (this.mLock) {
            if (this.mState == STATE_XFER && this.mActiveService != null) {
                sendDeactivateToActiveServiceLocked(STATE_W4_SELECT);
            }
            this.mActiveService = null;
            this.mActiveServiceName = null;
            unbindServiceIfNeededLocked();
            this.mState = STATE_W4_SELECT;
        }
    }

    Messenger bindServiceIfNeededLocked(ComponentName service) {
        if (this.mPaymentServiceBound && this.mPaymentServiceName.equals(service)) {
            Log.d(TAG, "Service already bound as payment service.");
            return this.mPaymentService;
        } else if (this.mServiceBound && this.mServiceName.equals(service)) {
            Log.d(TAG, "Service already bound as regular service.");
            return this.mService;
        } else {
            Log.d(TAG, "Binding to service " + service);
            unbindServiceIfNeededLocked();
            Intent aidIntent = new Intent("android.nfc.cardemulation.action.HOST_APDU_SERVICE");
            aidIntent.setComponent(service);
            if (!this.mContext.bindServiceAsUser(aidIntent, this.mConnection, STATE_W4_SELECT, UserHandle.CURRENT)) {
                Log.e(TAG, "Could not bind service.");
            }
            return null;
        }
    }

    void sendDataToServiceLocked(Messenger service, byte[] data) {
        if (service != this.mActiveService) {
            sendDeactivateToActiveServiceLocked(STATE_W4_SELECT);
            this.mActiveService = service;
            if (service.equals(this.mPaymentService)) {
                this.mActiveServiceName = this.mPaymentServiceName;
            } else {
                this.mActiveServiceName = this.mServiceName;
            }
        }
        Message msg = Message.obtain(null, STATE_IDLE);
        Bundle dataBundle = new Bundle();
        dataBundle.putByteArray("data", data);
        msg.setData(dataBundle);
        msg.replyTo = this.mMessenger;
        try {
            this.mActiveService.send(msg);
        } catch (RemoteException e) {
            Log.e(TAG, "Remote service has died, dropping APDU");
        }
    }

    void sendDeactivateToActiveServiceLocked(int reason) {
        if (this.mActiveService != null) {
            Message msg = Message.obtain(null, STATE_W4_SERVICE);
            msg.arg1 = reason;
            try {
                this.mActiveService.send(msg);
            } catch (RemoteException e) {
            }
        }
    }

    void unbindPaymentServiceLocked(int userId) {
        if (this.mPaymentServiceBound) {
            this.mContext.unbindService(this.mPaymentConnection);
            this.mPaymentServiceBound = DBG;
            this.mPaymentService = null;
            this.mPaymentServiceName = null;
        }
    }

    void bindPaymentServiceLocked(int userId, ComponentName service) {
        unbindPaymentServiceLocked(userId);
        Intent intent = new Intent("android.nfc.cardemulation.action.HOST_APDU_SERVICE");
        intent.setComponent(service);
        if (!this.mContext.bindServiceAsUser(intent, this.mPaymentConnection, STATE_W4_SELECT, new UserHandle(userId))) {
            Log.e(TAG, "Could not bind (persistent) payment service.");
        }
    }

    void unbindServiceIfNeededLocked() {
        if (this.mServiceBound) {
            Log.d(TAG, "Unbinding from service " + this.mServiceName);
            this.mContext.unbindService(this.mConnection);
            this.mServiceBound = DBG;
            this.mService = null;
            this.mServiceName = null;
        }
    }

    void launchTapAgain(ApduServiceInfo service, String category) {
        Intent dialogIntent = new Intent(this.mContext, TapAgainDialog.class);
        dialogIntent.putExtra(TapAgainDialog.EXTRA_CATEGORY, category);
        dialogIntent.putExtra(TapAgainDialog.EXTRA_APDU_SERVICE, service);
        dialogIntent.setFlags(268468224);
        this.mContext.startActivityAsUser(dialogIntent, UserHandle.CURRENT);
    }

    void launchResolver(ArrayList<ApduServiceInfo> services, ComponentName failedComponent, String category) {
        Intent intent = new Intent(this.mContext, AppChooserActivity.class);
        intent.setFlags(268468224);
        intent.putParcelableArrayListExtra(AppChooserActivity.EXTRA_APDU_SERVICES, services);
        intent.putExtra(TapAgainDialog.EXTRA_CATEGORY, category);
        if (failedComponent != null) {
            intent.putExtra(AppChooserActivity.EXTRA_FAILED_COMPONENT, failedComponent);
        }
        this.mContext.startActivityAsUser(intent, UserHandle.CURRENT);
    }

    String findSelectAid(byte[] data) {
        Log.d(TAG, "call findSelectAid - 1");
        if (data == null || data.length < 10) {
            if (!DBG) {
                return null;
            }
            Log.d(TAG, "Data size too small for SELECT APDU");
            return null;
        } else if (data[STATE_IDLE] != null || data[STATE_W4_SELECT] != -92 || data[STATE_W4_SERVICE] != (byte) 4) {
            return null;
        } else {
            if (data[STATE_W4_DEACTIVATE] != null) {
                Log.d(TAG, "Selecting next, last or previous AID occurrence is not supported");
            }
            int aidLength = data[STATE_XFER];
            if (data.length >= aidLength + SELECT_APDU_HDR_LENGTH) {
                return bytesToString(data, SELECT_APDU_HDR_LENGTH, aidLength);
            }
            Log.d(TAG, "call findSelectAid - 2");
            return null;
        }
    }

    static String bytesToString(byte[] bytes, int offset, int length) {
        char[] hexChars = new char[]{'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'};
        char[] chars = new char[(length * STATE_W4_SERVICE)];
        for (int j = STATE_IDLE; j < length; j += STATE_W4_SELECT) {
            int byteValue = bytes[offset + j] & 255;
            chars[j * STATE_W4_SERVICE] = hexChars[byteValue >>> STATE_XFER];
            chars[(j * STATE_W4_SERVICE) + STATE_W4_SELECT] = hexChars[byteValue & 15];
        }
        return new String(chars);
    }
}
