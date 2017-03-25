package com.android.nfc;

import android.app.ActivityManager;
import android.app.ActivityManager.RunningTaskInfo;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.Uri;
import android.nfc.BeamShareData;
import android.nfc.IAppCallback;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Handler.Callback;
import android.os.Message;
import android.os.Process;
import android.os.UserHandle;
import android.sec.enterprise.auditlog.AuditLog;
import android.util.Log;
import com.android.nfc.echoserver.EchoServer;
import com.android.nfc.handover.HandoverClient;
import com.android.nfc.handover.HandoverManager;
import com.android.nfc.handover.HandoverServer;
import com.android.nfc.ndefpush.NdefPushClient;
import com.android.nfc.ndefpush.NdefPushServer;
import com.android.nfc.secSend.SecNdefService;
import com.android.nfc.snep.SnepClient;
import com.android.nfc.snep.SnepMessage;
import com.android.nfc.snep.SnepServer;
import java.io.FileDescriptor;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Hashtable;
import java.util.List;

public class P2pLinkManager implements Callback, P2pEventListener.Callback {
    static final boolean DBG;
    static final String DISABLE_BEAM_DEFAULT = "android.nfc.disable_beam_default";
    static final boolean ECHOSERVER_ENABLED = false;
    static final int HANDOVER_FAILURE = 1;
    static final int HANDOVER_SAP = 20;
    static final int HANDOVER_SUCCESS = 0;
    static final int HANDOVER_UNSUPPORTED = 2;
    static final int LINK_FIRST_PDU_LIMIT_MS = 200;
    static final int LINK_NOTHING_TO_SEND_DEBOUNCE_MS = 750;
    static final int LINK_SEND_COMPLETE_DEBOUNCE_MS = 250;
    static final int LINK_SEND_CONFIRMED_DEBOUNCE_MS = 5000;
    static final int LINK_SEND_PENDING_DEBOUNCE_MS = 3000;
    static final int LINK_STATE_DEBOUNCE = 4;
    static final int LINK_STATE_DOWN = 1;
    static final int LINK_STATE_UP = 3;
    static final int LINK_STATE_WAITING_PDU = 2;
    static final int MSG_DEBOUNCE_TIMEOUT = 1;
    static final int MSG_HANDOVER_NOT_SUPPORTED = 7;
    static final int MSG_RECEIVE_COMPLETE = 2;
    static final int MSG_RECEIVE_HANDOVER = 3;
    static final int MSG_SEC_SEND_MSG = 9;
    static final int MSG_SEND_COMPLETE = 4;
    static final int MSG_SHOW_CONFIRMATION_UI = 8;
    static final int MSG_START_ECHOSERVER = 5;
    static final int MSG_STOP_ECHOSERVER = 6;
    static final int NDEFPUSH_SAP = 16;
    static final int SEND_STATE_NEED_CONFIRMATION = 2;
    static final int SEND_STATE_NOTHING_TO_SEND = 1;
    static final int SEND_STATE_SENDING = 3;
    static final int SEND_STATE_SEND_COMPLETE = 4;
    static final int SNEP_FAILURE = 1;
    static final int SNEP_SUCCESS = 0;
    static final String TAG = "NfcP2pLinkManager";
    final String ACTION_ABEAM_STATE_CHANGED;
    final String EXTRA_ABEAM_STATE;
    final ActivityManager mActivityManager;
    Hashtable<String, String[]> mAllValidCallbackPackages;
    IAppCallback mCallbackNdef;
    Hashtable<String, IAppCallback> mCallbackNdefs;
    ConnectTask mConnectTask;
    final Context mContext;
    final int mDefaultMiu;
    final int mDefaultRwSize;
    final SnepServer.Callback mDefaultSnepCallback;
    final SnepServer mDefaultSnepServer;
    final EchoServer mEchoServer;
    final P2pEventListener mEventListener;
    boolean mFirstBeam;
    final Handler mHandler;
    final HandoverServer.Callback mHandoverCallback;
    HandoverClient mHandoverClient;
    final HandoverManager mHandoverManager;
    final HandoverServer mHandoverServer;
    boolean mIsReceiveEnabled;
    boolean mIsSendEnabled;
    long mLastLlcpActivationTime;
    int mLinkState;
    boolean mLlcpConnectDelayed;
    boolean mLlcpServicesConnected;
    NdefMessage mMessageToSend;
    NdefPushClient mNdefPushClient;
    final NdefPushServer mNdefPushServer;
    final NdefPushServer.Callback mNppCallback;
    PackageManager mPackageManager;
    SharedPreferences mPrefs;
    private SecNdefService mSecNdefService;
    int mSendFlags;
    int mSendState;
    SendTask mSendTask;
    SnepClient mSnepClient;
    Uri[] mUrisToSend;
    String[] mValidCallbackPackages;

    /* renamed from: com.android.nfc.P2pLinkManager.1 */
    class C00241 implements HandoverServer.Callback {
        C00241() {
        }

        public void onHandoverRequestReceived() {
            P2pLinkManager.this.onReceiveHandover();
        }
    }

    /* renamed from: com.android.nfc.P2pLinkManager.2 */
    class C00252 implements NdefPushServer.Callback {
        C00252() {
        }

        public void onMessageReceived(NdefMessage msg) {
            P2pLinkManager.this.onReceiveComplete(msg);
        }
    }

    /* renamed from: com.android.nfc.P2pLinkManager.3 */
    class C00263 implements SnepServer.Callback {
        C00263() {
        }

        public SnepMessage doPut(NdefMessage msg) {
            P2pLinkManager.this.onReceiveComplete(msg);
            return SnepMessage.getMessage(SnepMessage.RESPONSE_SUCCESS);
        }

        public SnepMessage doGet(int acceptableLength, NdefMessage msg) {
            NdefMessage response = P2pLinkManager.this.mHandoverManager.tryHandoverRequest(msg);
            if (response == null) {
                return SnepMessage.getMessage(SnepMessage.RESPONSE_NOT_IMPLEMENTED);
            }
            P2pLinkManager.this.onReceiveHandover();
            return SnepMessage.getSuccessResponse(response);
        }
    }

    final class ConnectTask extends AsyncTask<Void, Void, Boolean> {
        ConnectTask() {
        }

        protected void onPostExecute(Boolean result) {
            if (isCancelled()) {
                if (P2pLinkManager.DBG) {
                    Log.d(P2pLinkManager.TAG, "ConnectTask was cancelled");
                }
            } else if (result.booleanValue()) {
                P2pLinkManager.this.onLlcpServicesConnected();
            } else {
                Log.e(P2pLinkManager.TAG, "Could not connect required NFC transports");
            }
        }

        protected Boolean doInBackground(Void... params) {
            Boolean valueOf;
            boolean needsHandover = P2pLinkManager.ECHOSERVER_ENABLED;
            boolean needsNdef = P2pLinkManager.ECHOSERVER_ENABLED;
            boolean success = P2pLinkManager.ECHOSERVER_ENABLED;
            HandoverClient handoverClient = null;
            SnepClient snepClient = null;
            NdefPushClient nppClient = null;
            synchronized (P2pLinkManager.this) {
                if (P2pLinkManager.this.mUrisToSend != null) {
                    needsHandover = true;
                }
                if (P2pLinkManager.this.mMessageToSend != null) {
                    needsNdef = true;
                }
            }
            if (needsHandover) {
                handoverClient = new HandoverClient();
                try {
                    handoverClient.connect();
                    success = true;
                } catch (IOException e) {
                    handoverClient = null;
                }
            }
            if (needsNdef || (needsHandover && handoverClient == null)) {
                snepClient = new SnepClient();
                try {
                    snepClient.connect();
                    success = true;
                } catch (IOException e2) {
                    snepClient = null;
                }
                if (!success) {
                    nppClient = new NdefPushClient();
                    try {
                        nppClient.connect();
                        success = true;
                    } catch (IOException e3) {
                        nppClient = null;
                    }
                }
            }
            synchronized (P2pLinkManager.this) {
                if (isCancelled()) {
                    if (handoverClient != null) {
                        handoverClient.close();
                    }
                    if (snepClient != null) {
                        snepClient.close();
                    }
                    if (nppClient != null) {
                        nppClient.close();
                    }
                    valueOf = Boolean.valueOf(P2pLinkManager.ECHOSERVER_ENABLED);
                } else {
                    P2pLinkManager.this.mHandoverClient = handoverClient;
                    P2pLinkManager.this.mSnepClient = snepClient;
                    P2pLinkManager.this.mNdefPushClient = nppClient;
                    valueOf = Boolean.valueOf(success);
                }
            }
            return valueOf;
        }
    }

    final class SendTask extends AsyncTask<Void, Void, Void> {
        HandoverClient handoverClient;
        NdefPushClient nppClient;
        SnepClient snepClient;

        SendTask() {
        }

        int doHandover(Uri[] uris) throws IOException {
            NdefMessage response = null;
            NdefMessage request = P2pLinkManager.this.mHandoverManager.createHandoverRequestMessage();
            if (request == null) {
                return P2pLinkManager.SEND_STATE_NEED_CONFIRMATION;
            }
            if (this.handoverClient != null) {
                response = this.handoverClient.sendHandoverRequest(request);
            }
            if (response == null && this.snepClient != null) {
                response = this.snepClient.get(request).getNdefMessage();
            }
            if (response == null) {
                return P2pLinkManager.SEND_STATE_NEED_CONFIRMATION;
            }
            P2pLinkManager.this.mHandoverManager.doHandoverUri(uris, response);
            return P2pLinkManager.SNEP_SUCCESS;
        }

        int doSnepProtocol(NdefMessage msg) throws IOException {
            if (msg == null) {
                return P2pLinkManager.SNEP_FAILURE;
            }
            this.snepClient.put(msg);
            return P2pLinkManager.SNEP_SUCCESS;
        }

        /* JADX WARNING: inconsistent code. */
        /* Code decompiled incorrectly, please refer to instructions dump. */
        public java.lang.Void doInBackground(java.lang.Void... r13) {
            /*
            r12 = this;
            r11 = 0;
            r10 = 3;
            r3 = 0;
            r9 = com.android.nfc.P2pLinkManager.this;
            monitor-enter(r9);
            r8 = com.android.nfc.P2pLinkManager.this;	 Catch:{ all -> 0x00a5 }
            r8 = r8.mLinkState;	 Catch:{ all -> 0x00a5 }
            if (r8 != r10) goto L_0x0012;
        L_0x000c:
            r8 = com.android.nfc.P2pLinkManager.this;	 Catch:{ all -> 0x00a5 }
            r8 = r8.mSendState;	 Catch:{ all -> 0x00a5 }
            if (r8 == r10) goto L_0x0014;
        L_0x0012:
            monitor-exit(r9);	 Catch:{ all -> 0x00a5 }
        L_0x0013:
            return r11;
        L_0x0014:
            r8 = com.android.nfc.P2pLinkManager.this;	 Catch:{ all -> 0x00a5 }
            r2 = r8.mMessageToSend;	 Catch:{ all -> 0x00a5 }
            r8 = com.android.nfc.P2pLinkManager.this;	 Catch:{ all -> 0x00a5 }
            r7 = r8.mUrisToSend;	 Catch:{ all -> 0x00a5 }
            r8 = com.android.nfc.P2pLinkManager.this;	 Catch:{ all -> 0x00a5 }
            r8 = r8.mSnepClient;	 Catch:{ all -> 0x00a5 }
            r12.snepClient = r8;	 Catch:{ all -> 0x00a5 }
            r8 = com.android.nfc.P2pLinkManager.this;	 Catch:{ all -> 0x00a5 }
            r8 = r8.mHandoverClient;	 Catch:{ all -> 0x00a5 }
            r12.handoverClient = r8;	 Catch:{ all -> 0x00a5 }
            r8 = com.android.nfc.P2pLinkManager.this;	 Catch:{ all -> 0x00a5 }
            r8 = r8.mNdefPushClient;	 Catch:{ all -> 0x00a5 }
            r12.nppClient = r8;	 Catch:{ all -> 0x00a5 }
            monitor-exit(r9);	 Catch:{ all -> 0x00a5 }
            r5 = android.os.SystemClock.elapsedRealtime();
            if (r7 == 0) goto L_0x0047;
        L_0x0035:
            r8 = com.android.nfc.P2pLinkManager.DBG;
            if (r8 == 0) goto L_0x0040;
        L_0x0039:
            r8 = "NfcP2pLinkManager";
            r9 = "Trying handover request";
            android.util.Log.d(r8, r9);
        L_0x0040:
            r1 = r12.doHandover(r7);	 Catch:{ IOException -> 0x00b3 }
            switch(r1) {
                case 0: goto L_0x00a8;
                case 1: goto L_0x00aa;
                case 2: goto L_0x00ac;
                default: goto L_0x0047;
            };
        L_0x0047:
            if (r3 != 0) goto L_0x0062;
        L_0x0049:
            if (r2 == 0) goto L_0x0062;
        L_0x004b:
            r8 = r12.snepClient;
            if (r8 == 0) goto L_0x0062;
        L_0x004f:
            r8 = com.android.nfc.P2pLinkManager.DBG;
            if (r8 == 0) goto L_0x005a;
        L_0x0053:
            r8 = "NfcP2pLinkManager";
            r9 = "Sending ndef via SNEP";
            android.util.Log.d(r8, r9);
        L_0x005a:
            r4 = r12.doSnepProtocol(r2);	 Catch:{ IOException -> 0x00ba }
            switch(r4) {
                case 0: goto L_0x00b6;
                case 1: goto L_0x00b8;
                default: goto L_0x0061;
            };
        L_0x0061:
            r3 = 0;
        L_0x0062:
            if (r3 != 0) goto L_0x0070;
        L_0x0064:
            if (r2 == 0) goto L_0x0070;
        L_0x0066:
            r8 = r12.nppClient;
            if (r8 == 0) goto L_0x0070;
        L_0x006a:
            r8 = r12.nppClient;
            r3 = r8.push(r2);
        L_0x0070:
            r8 = android.os.SystemClock.elapsedRealtime();
            r5 = r8 - r5;
            r8 = com.android.nfc.P2pLinkManager.DBG;
            if (r8 == 0) goto L_0x009c;
        L_0x007a:
            r8 = "NfcP2pLinkManager";
            r9 = new java.lang.StringBuilder;
            r9.<init>();
            r10 = "SendTask result=";
            r9 = r9.append(r10);
            r9 = r9.append(r3);
            r10 = ", time ms=";
            r9 = r9.append(r10);
            r9 = r9.append(r5);
            r9 = r9.toString();
            android.util.Log.d(r8, r9);
        L_0x009c:
            if (r3 == 0) goto L_0x0013;
        L_0x009e:
            r8 = com.android.nfc.P2pLinkManager.this;
            r8.onSendComplete(r2, r5);
            goto L_0x0013;
        L_0x00a5:
            r8 = move-exception;
            monitor-exit(r9);	 Catch:{ all -> 0x00a5 }
            throw r8;
        L_0x00a8:
            r3 = 1;
            goto L_0x0047;
        L_0x00aa:
            r3 = 0;
            goto L_0x0047;
        L_0x00ac:
            r3 = 0;
            r8 = com.android.nfc.P2pLinkManager.this;	 Catch:{ IOException -> 0x00b3 }
            r8.onHandoverUnsupported();	 Catch:{ IOException -> 0x00b3 }
            goto L_0x0047;
        L_0x00b3:
            r0 = move-exception;
            r3 = 0;
            goto L_0x0047;
        L_0x00b6:
            r3 = 1;
            goto L_0x0062;
        L_0x00b8:
            r3 = 0;
            goto L_0x0062;
        L_0x00ba:
            r0 = move-exception;
            r3 = 0;
            goto L_0x0062;
            */
            throw new UnsupportedOperationException("Method not decompiled: com.android.nfc.P2pLinkManager.SendTask.doInBackground(java.lang.Void[]):java.lang.Void");
        }
    }

    static {
        DBG = NfcService.DBG;
    }

    public P2pLinkManager(Context context, HandoverManager handoverManager, int defaultMiu, int defaultRwSize) {
        this.ACTION_ABEAM_STATE_CHANGED = "android.nfc.action.ABEAM_STATE_CHANGED";
        this.EXTRA_ABEAM_STATE = "android.nfc.extra.ABEAM_STATE";
        this.mHandoverCallback = new C00241();
        this.mNppCallback = new C00252();
        this.mDefaultSnepCallback = new C00263();
        this.mNdefPushServer = new NdefPushServer(NDEFPUSH_SAP, this.mNppCallback);
        this.mDefaultSnepServer = new SnepServer(this.mDefaultSnepCallback, defaultMiu, defaultRwSize);
        this.mHandoverServer = new HandoverServer(HANDOVER_SAP, handoverManager, this.mHandoverCallback);
        this.mEchoServer = null;
        this.mActivityManager = (ActivityManager) context.getSystemService("activity");
        this.mPackageManager = context.getPackageManager();
        this.mContext = context;
        this.mEventListener = new P2pEventManager(context, this);
        this.mHandler = new Handler(this);
        this.mLinkState = SNEP_FAILURE;
        this.mSendState = SNEP_FAILURE;
        this.mIsSendEnabled = ECHOSERVER_ENABLED;
        this.mIsReceiveEnabled = ECHOSERVER_ENABLED;
        this.mPrefs = context.getSharedPreferences(NfcService.PREF, SNEP_SUCCESS);
        this.mFirstBeam = this.mPrefs.getBoolean("first_beam", true);
        this.mHandoverManager = handoverManager;
        this.mDefaultMiu = defaultMiu;
        this.mDefaultRwSize = defaultRwSize;
        this.mLlcpServicesConnected = ECHOSERVER_ENABLED;
        this.mSecNdefService = new SecNdefService(this.mContext, this.mIsSendEnabled, this.mIsReceiveEnabled);
        this.mCallbackNdefs = new Hashtable();
        this.mAllValidCallbackPackages = new Hashtable();
    }

    public void enableDisable(boolean sendEnable, boolean receiveEnable) {
        synchronized (this) {
            if (!this.mIsReceiveEnabled && receiveEnable) {
                this.mDefaultSnepServer.start();
                this.mNdefPushServer.start();
                this.mHandoverServer.start();
                if (this.mEchoServer != null) {
                    this.mHandler.sendEmptyMessage(MSG_START_ECHOSERVER);
                }
            } else if (this.mIsReceiveEnabled && !receiveEnable) {
                if (DBG) {
                    Log.d(TAG, "enableDisable: llcp deactivate");
                }
                onLlcpDeactivated();
                this.mDefaultSnepServer.stop();
                this.mNdefPushServer.stop();
                this.mHandoverServer.stop();
                if (this.mEchoServer != null) {
                    this.mHandler.sendEmptyMessage(MSG_STOP_ECHOSERVER);
                }
            }
            this.mIsSendEnabled = sendEnable;
            this.mIsReceiveEnabled = receiveEnable;
            this.mSecNdefService.enableDisable(sendEnable, receiveEnable);
        }
    }

    public boolean isLlcpActive() {
        boolean z = true;
        synchronized (this) {
            if (this.mLinkState == SNEP_FAILURE) {
                z = ECHOSERVER_ENABLED;
            }
        }
        return z;
    }

    public void sendBeamChangeIntent(boolean enable) {
        if (DBG) {
            Log.i(TAG, "send Android beam state change intent : " + enable);
        }
        Intent intent = new Intent("android.nfc.action.ABEAM_STATE_CHANGED");
        intent.putExtra("android.nfc.extra.ABEAM_STATE", enable);
        this.mContext.sendBroadcast(intent);
    }

    public void setNdefCallback(IAppCallback callbackNdef, int callingUid) {
        synchronized (this) {
            this.mCallbackNdef = callbackNdef;
            this.mValidCallbackPackages = this.mPackageManager.getPackagesForUid(callingUid);
            String runningPackage = null;
            List<RunningTaskInfo> tasks = this.mActivityManager.getRunningTasks(SNEP_FAILURE);
            if (tasks.size() > 0) {
                runningPackage = ((RunningTaskInfo) tasks.get(SNEP_SUCCESS)).topActivity.getPackageName();
            }
            boolean callbackValidCheck = ECHOSERVER_ENABLED;
            String[] arr$ = this.mValidCallbackPackages;
            int len$ = arr$.length;
            for (int i$ = SNEP_SUCCESS; i$ < len$; i$ += SNEP_FAILURE) {
                if (arr$[i$].equals(runningPackage)) {
                    callbackValidCheck = true;
                    break;
                }
            }
            if (callbackValidCheck) {
                this.mCallbackNdefs.put(runningPackage, this.mCallbackNdef);
                this.mAllValidCallbackPackages.put(runningPackage, this.mValidCallbackPackages);
                if (DBG) {
                    Log.i(TAG, "NFC runningPackage is " + runningPackage);
                }
                if (DBG) {
                    Log.i(TAG, "NFC mCallbackNdefs size" + this.mCallbackNdefs.size());
                }
                if (DBG) {
                    Log.i(TAG, "NFC mValidCallbackPackage[0] is " + this.mValidCallbackPackages[SNEP_SUCCESS]);
                }
            }
        }
    }

    /* JADX WARNING: inconsistent code. */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public void onLlcpActivated() {
        /*
        r3 = this;
        r2 = 3;
        r0 = "NfcP2pLinkManager";
        r1 = "LLCP activated";
        android.util.Log.i(r0, r1);
        monitor-enter(r3);
        r0 = r3.mEchoServer;	 Catch:{ all -> 0x0062 }
        if (r0 == 0) goto L_0x0012;
    L_0x000d:
        r0 = r3.mEchoServer;	 Catch:{ all -> 0x0062 }
        r0.onLlcpActivated();	 Catch:{ all -> 0x0062 }
    L_0x0012:
        r0 = android.os.SystemClock.elapsedRealtime();	 Catch:{ all -> 0x0062 }
        r3.mLastLlcpActivationTime = r0;	 Catch:{ all -> 0x0062 }
        r0 = 0;
        r3.mLlcpConnectDelayed = r0;	 Catch:{ all -> 0x0062 }
        r0 = r3.mLinkState;	 Catch:{ all -> 0x0062 }
        switch(r0) {
            case 1: goto L_0x0022;
            case 2: goto L_0x0079;
            case 3: goto L_0x0086;
            case 4: goto L_0x0093;
            default: goto L_0x0020;
        };	 Catch:{ all -> 0x0062 }
    L_0x0020:
        monitor-exit(r3);	 Catch:{ all -> 0x0062 }
    L_0x0021:
        return;
    L_0x0022:
        r0 = 2;
        r3.mLinkState = r0;	 Catch:{ all -> 0x0062 }
        r0 = 1;
        r3.mSendState = r0;	 Catch:{ all -> 0x0062 }
        r0 = DBG;	 Catch:{ all -> 0x0062 }
        if (r0 == 0) goto L_0x0033;
    L_0x002c:
        r0 = "NfcP2pLinkManager";
        r1 = "onP2pInRange()";
        android.util.Log.d(r0, r1);	 Catch:{ all -> 0x0062 }
    L_0x0033:
        r0 = com.android.nfc.NfcService.mIsSecNdefEnabled;	 Catch:{ all -> 0x0062 }
        if (r0 == 0) goto L_0x003c;
    L_0x0037:
        r0 = r3.mSecNdefService;	 Catch:{ all -> 0x0062 }
        r0.onP2pInRange();	 Catch:{ all -> 0x0062 }
    L_0x003c:
        r0 = r3.mEventListener;	 Catch:{ all -> 0x0062 }
        r0.onP2pInRange();	 Catch:{ all -> 0x0062 }
        r3.prepareMessageToSend();	 Catch:{ all -> 0x0062 }
        r0 = r3.mMessageToSend;	 Catch:{ all -> 0x0062 }
        if (r0 != 0) goto L_0x0054;
    L_0x0048:
        r0 = r3.mUrisToSend;	 Catch:{ all -> 0x0062 }
        if (r0 == 0) goto L_0x0020;
    L_0x004c:
        r0 = r3.mHandoverManager;	 Catch:{ all -> 0x0062 }
        r0 = r0.isHandoverSupported();	 Catch:{ all -> 0x0062 }
        if (r0 == 0) goto L_0x0020;
    L_0x0054:
        r0 = r3.mSendFlags;	 Catch:{ all -> 0x0062 }
        r0 = r0 & 1;
        if (r0 == 0) goto L_0x0065;
    L_0x005a:
        r0 = 3;
        r3.mSendState = r0;	 Catch:{ all -> 0x0062 }
        r0 = 0;
        r3.onP2pSendConfirmed(r0);	 Catch:{ all -> 0x0062 }
        goto L_0x0020;
    L_0x0062:
        r0 = move-exception;
        monitor-exit(r3);	 Catch:{ all -> 0x0062 }
        throw r0;
    L_0x0065:
        r0 = 2;
        r3.mSendState = r0;	 Catch:{ all -> 0x0062 }
        r0 = DBG;	 Catch:{ all -> 0x0062 }
        if (r0 == 0) goto L_0x0073;
    L_0x006c:
        r0 = "NfcP2pLinkManager";
        r1 = "onP2pSendConfirmationRequested()";
        android.util.Log.d(r0, r1);	 Catch:{ all -> 0x0062 }
    L_0x0073:
        r0 = r3.mEventListener;	 Catch:{ all -> 0x0062 }
        r0.onP2pSendConfirmationRequested();	 Catch:{ all -> 0x0062 }
        goto L_0x0020;
    L_0x0079:
        r0 = DBG;	 Catch:{ all -> 0x0062 }
        if (r0 == 0) goto L_0x0084;
    L_0x007d:
        r0 = "NfcP2pLinkManager";
        r1 = "Unexpected onLlcpActivated() in LINK_STATE_WAITING_PDU";
        android.util.Log.d(r0, r1);	 Catch:{ all -> 0x0062 }
    L_0x0084:
        monitor-exit(r3);	 Catch:{ all -> 0x0062 }
        goto L_0x0021;
    L_0x0086:
        r0 = DBG;	 Catch:{ all -> 0x0062 }
        if (r0 == 0) goto L_0x0091;
    L_0x008a:
        r0 = "NfcP2pLinkManager";
        r1 = "Duplicate onLlcpActivated()";
        android.util.Log.d(r0, r1);	 Catch:{ all -> 0x0062 }
    L_0x0091:
        monitor-exit(r3);	 Catch:{ all -> 0x0062 }
        goto L_0x0021;
    L_0x0093:
        r0 = r3.mSendState;	 Catch:{ all -> 0x0062 }
        if (r0 != r2) goto L_0x00a5;
    L_0x0097:
        r0 = 3;
        r3.mLinkState = r0;	 Catch:{ all -> 0x0062 }
        r3.connectLlcpServices();	 Catch:{ all -> 0x0062 }
    L_0x009d:
        r0 = r3.mHandler;	 Catch:{ all -> 0x0062 }
        r1 = 1;
        r0.removeMessages(r1);	 Catch:{ all -> 0x0062 }
        goto L_0x0020;
    L_0x00a5:
        r0 = 2;
        r3.mLinkState = r0;	 Catch:{ all -> 0x0062 }
        goto L_0x009d;
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.nfc.P2pLinkManager.onLlcpActivated():void");
    }

    /* JADX WARNING: inconsistent code. */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public void onLlcpFirstPacketReceived() {
        /*
        r8 = this;
        r7 = 3;
        r6 = 1;
        monitor-enter(r8);
        r2 = android.os.SystemClock.elapsedRealtime();	 Catch:{ all -> 0x0044 }
        r4 = r8.mLastLlcpActivationTime;	 Catch:{ all -> 0x0044 }
        r0 = r2 - r4;
        r2 = DBG;	 Catch:{ all -> 0x0044 }
        if (r2 == 0) goto L_0x0031;
    L_0x000f:
        r2 = "NfcP2pLinkManager";
        r3 = new java.lang.StringBuilder;	 Catch:{ all -> 0x0044 }
        r3.<init>();	 Catch:{ all -> 0x0044 }
        r4 = "Took ";
        r3 = r3.append(r4);	 Catch:{ all -> 0x0044 }
        r4 = java.lang.Long.toString(r0);	 Catch:{ all -> 0x0044 }
        r3 = r3.append(r4);	 Catch:{ all -> 0x0044 }
        r4 = " to get first LLCP PDU";
        r3 = r3.append(r4);	 Catch:{ all -> 0x0044 }
        r3 = r3.toString();	 Catch:{ all -> 0x0044 }
        android.util.Log.d(r2, r3);	 Catch:{ all -> 0x0044 }
    L_0x0031:
        r2 = r8.mLinkState;	 Catch:{ all -> 0x0044 }
        switch(r2) {
            case 1: goto L_0x0047;
            case 2: goto L_0x004f;
            case 3: goto L_0x0038;
            case 4: goto L_0x0047;
            default: goto L_0x0036;
        };	 Catch:{ all -> 0x0044 }
    L_0x0036:
        monitor-exit(r8);	 Catch:{ all -> 0x0044 }
        return;
    L_0x0038:
        r2 = DBG;	 Catch:{ all -> 0x0044 }
        if (r2 == 0) goto L_0x0036;
    L_0x003c:
        r2 = "NfcP2pLinkManager";
        r3 = "Dropping first LLCP packet received";
        android.util.Log.d(r2, r3);	 Catch:{ all -> 0x0044 }
        goto L_0x0036;
    L_0x0044:
        r2 = move-exception;
        monitor-exit(r8);	 Catch:{ all -> 0x0044 }
        throw r2;
    L_0x0047:
        r2 = "NfcP2pLinkManager";
        r3 = "Unexpected first LLCP packet received";
        android.util.Log.e(r2, r3);	 Catch:{ all -> 0x0044 }
        goto L_0x0036;
    L_0x004f:
        r2 = 3;
        r8.mLinkState = r2;	 Catch:{ all -> 0x0044 }
        r2 = r8.mSendState;	 Catch:{ all -> 0x0044 }
        if (r2 == r6) goto L_0x0036;
    L_0x0056:
        r2 = 200; // 0xc8 float:2.8E-43 double:9.9E-322;
        r2 = (r0 > r2 ? 1 : (r0 == r2 ? 0 : -1));
        if (r2 < 0) goto L_0x0060;
    L_0x005c:
        r2 = r8.mSendState;	 Catch:{ all -> 0x0044 }
        if (r2 != r7) goto L_0x0064;
    L_0x0060:
        r8.connectLlcpServices();	 Catch:{ all -> 0x0044 }
        goto L_0x0036;
    L_0x0064:
        r2 = 1;
        r8.mLlcpConnectDelayed = r2;	 Catch:{ all -> 0x0044 }
        goto L_0x0036;
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.nfc.P2pLinkManager.onLlcpFirstPacketReceived():void");
    }

    public void onUserSwitched() {
        synchronized (this) {
            try {
                this.mPackageManager = this.mContext.createPackageContextAsUser("android", SNEP_SUCCESS, new UserHandle(ActivityManager.getCurrentUser())).getPackageManager();
            } catch (NameNotFoundException e) {
                Log.e(TAG, "Failed to retrieve PackageManager for user");
            }
        }
    }

    void prepareMessageToSend() {
        synchronized (this) {
            if (this.mIsSendEnabled) {
                String runningPackage = null;
                List<RunningTaskInfo> tasks = this.mActivityManager.getRunningTasks(SNEP_FAILURE);
                if (tasks.size() > 0) {
                    runningPackage = ((RunningTaskInfo) tasks.get(SNEP_SUCCESS)).topActivity.getPackageName();
                }
                if (runningPackage == null) {
                    Log.e(TAG, "Could not determine running package.");
                    this.mMessageToSend = null;
                    this.mUrisToSend = null;
                    return;
                }
                if (DBG) {
                    Log.i(TAG, "NFC prepareMessageToSend app is " + runningPackage);
                }
                this.mCallbackNdef = (IAppCallback) this.mCallbackNdefs.get(runningPackage);
                this.mValidCallbackPackages = (String[]) this.mAllValidCallbackPackages.get(runningPackage);
                if (this.mCallbackNdef != null) {
                    boolean callbackValid = ECHOSERVER_ENABLED;
                    if (this.mValidCallbackPackages != null) {
                        String[] arr$ = this.mValidCallbackPackages;
                        int len$ = arr$.length;
                        for (int i$ = SNEP_SUCCESS; i$ < len$; i$ += SNEP_FAILURE) {
                            if (arr$[i$].equals(runningPackage)) {
                                callbackValid = true;
                                break;
                            }
                        }
                    }
                    if (callbackValid) {
                        try {
                            BeamShareData shareData = this.mCallbackNdef.createBeamShareData();
                            this.mMessageToSend = shareData.ndefMessage;
                            this.mUrisToSend = shareData.uris;
                            this.mSendFlags = shareData.flags;
                            return;
                        } catch (Exception e) {
                            Log.e(TAG, "Failed NDEF callback: " + e.getMessage());
                        }
                    } else if (DBG) {
                        Log.d(TAG, "Last registered callback is not running in the foreground.");
                    }
                }
                if (beamDefaultDisabled(runningPackage) || "android".equals(runningPackage)) {
                    Log.d(TAG, "Disabling default Beam behavior");
                    this.mMessageToSend = null;
                    this.mUrisToSend = null;
                } else {
                    this.mMessageToSend = createDefaultNdef(runningPackage);
                    this.mUrisToSend = null;
                }
                if (DBG) {
                    Log.d(TAG, "mMessageToSend = " + this.mMessageToSend);
                }
                if (DBG) {
                    Log.d(TAG, "mUrisToSend = " + this.mUrisToSend);
                }
                return;
            }
            this.mMessageToSend = null;
            this.mUrisToSend = null;
        }
    }

    boolean beamDefaultDisabled(String pkgName) {
        boolean z = ECHOSERVER_ENABLED;
        try {
            ApplicationInfo ai = this.mPackageManager.getApplicationInfo(pkgName, HandoverServer.MIU);
            if (!(ai == null || ai.metaData == null)) {
                z = ai.metaData.getBoolean(DISABLE_BEAM_DEFAULT);
            }
        } catch (NameNotFoundException e) {
        }
        return z;
    }

    NdefMessage createDefaultNdef(String pkgName) {
        NdefRecord appUri = NdefRecord.createUri(Uri.parse("http://play.google.com/store/apps/details?id=" + pkgName + "&feature=beam"));
        NdefRecord appRecord = NdefRecord.createApplicationRecord(pkgName);
        NdefRecord[] ndefRecordArr = new NdefRecord[SEND_STATE_NEED_CONFIRMATION];
        ndefRecordArr[SNEP_SUCCESS] = appUri;
        ndefRecordArr[SNEP_FAILURE] = appRecord;
        return new NdefMessage(ndefRecordArr);
    }

    void disconnectLlcpServices() {
        synchronized (this) {
            if (this.mConnectTask != null) {
                this.mConnectTask.cancel(true);
                this.mConnectTask = null;
            }
            if (this.mNdefPushClient != null) {
                this.mNdefPushClient.close();
                this.mNdefPushClient = null;
            }
            if (this.mSnepClient != null) {
                this.mSnepClient.close();
                this.mSnepClient = null;
            }
            if (this.mHandoverClient != null) {
                this.mHandoverClient.close();
                this.mHandoverClient = null;
            }
            this.mLlcpServicesConnected = ECHOSERVER_ENABLED;
        }
    }

    /* JADX WARNING: inconsistent code. */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public void onLlcpDeactivated() {
        /*
        r5 = this;
        r1 = "NfcP2pLinkManager";
        r2 = "LLCP deactivated.";
        android.util.Log.i(r1, r2);
        monitor-enter(r5);
        r1 = r5.mEchoServer;	 Catch:{ all -> 0x0020 }
        if (r1 == 0) goto L_0x0011;
    L_0x000c:
        r1 = r5.mEchoServer;	 Catch:{ all -> 0x0020 }
        r1.onLlcpDeactivated();	 Catch:{ all -> 0x0020 }
    L_0x0011:
        r1 = r5.mLinkState;	 Catch:{ all -> 0x0020 }
        switch(r1) {
            case 1: goto L_0x0018;
            case 2: goto L_0x0023;
            case 3: goto L_0x0023;
            case 4: goto L_0x0018;
            default: goto L_0x0016;
        };	 Catch:{ all -> 0x0020 }
    L_0x0016:
        monitor-exit(r5);	 Catch:{ all -> 0x0020 }
        return;
    L_0x0018:
        r1 = "NfcP2pLinkManager";
        r2 = "Duplicate onLlcpDectivated()";
        android.util.Log.i(r1, r2);	 Catch:{ all -> 0x0020 }
        goto L_0x0016;
    L_0x0020:
        r1 = move-exception;
        monitor-exit(r5);	 Catch:{ all -> 0x0020 }
        throw r1;
    L_0x0023:
        r1 = 4;
        r5.mLinkState = r1;	 Catch:{ all -> 0x0020 }
        r0 = 0;
        r1 = r5.mSendState;	 Catch:{ all -> 0x0020 }
        switch(r1) {
            case 1: goto L_0x004b;
            case 2: goto L_0x004d;
            case 3: goto L_0x0050;
            case 4: goto L_0x0053;
            default: goto L_0x002c;
        };	 Catch:{ all -> 0x0020 }
    L_0x002c:
        r1 = r5.mHandler;	 Catch:{ all -> 0x0020 }
        r2 = 1;
        r3 = (long) r0;	 Catch:{ all -> 0x0020 }
        r1.sendEmptyMessageDelayed(r2, r3);	 Catch:{ all -> 0x0020 }
        r1 = r5.mSendState;	 Catch:{ all -> 0x0020 }
        r2 = 3;
        if (r1 != r2) goto L_0x0044;
    L_0x0038:
        r1 = "NfcP2pLinkManager";
        r2 = "onP2pSendDebounce()";
        android.util.Log.e(r1, r2);	 Catch:{ all -> 0x0020 }
        r1 = r5.mEventListener;	 Catch:{ all -> 0x0020 }
        r1.onP2pSendDebounce();	 Catch:{ all -> 0x0020 }
    L_0x0044:
        r5.cancelSendNdefMessage();	 Catch:{ all -> 0x0020 }
        r5.disconnectLlcpServices();	 Catch:{ all -> 0x0020 }
        goto L_0x0016;
    L_0x004b:
        r0 = 0;
        goto L_0x002c;
    L_0x004d:
        r0 = 3000; // 0xbb8 float:4.204E-42 double:1.482E-320;
        goto L_0x002c;
    L_0x0050:
        r0 = 5000; // 0x1388 float:7.006E-42 double:2.4703E-320;
        goto L_0x002c;
    L_0x0053:
        r0 = 250; // 0xfa float:3.5E-43 double:1.235E-321;
        goto L_0x002c;
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.nfc.P2pLinkManager.onLlcpDeactivated():void");
    }

    void onHandoverUnsupported() {
        this.mHandler.sendEmptyMessage(MSG_HANDOVER_NOT_SUPPORTED);
    }

    void onSendComplete(NdefMessage msg, long elapsedRealtime) {
        if (this.mFirstBeam) {
            EventLogTags.writeNfcFirstShare();
            this.mPrefs.edit().putBoolean("first_beam", ECHOSERVER_ENABLED).apply();
            this.mFirstBeam = ECHOSERVER_ENABLED;
        }
        EventLogTags.writeNfcShare(getMessageSize(msg), getMessageTnf(msg), getMessageType(msg), getMessageAarPresent(msg), (int) elapsedRealtime);
        AuditLog.log(MSG_START_ECHOSERVER, MSG_START_ECHOSERVER, true, Process.myPid(), getClass().getSimpleName(), "Sending data through NFC succeeded");
        this.mHandler.sendEmptyMessage(SEND_STATE_SEND_COMPLETE);
    }

    void sendNdefMessage() {
        synchronized (this) {
            cancelSendNdefMessage();
            this.mSendTask = new SendTask();
            this.mSendTask.execute(new Void[SNEP_SUCCESS]);
        }
    }

    void cancelSendNdefMessage() {
        synchronized (this) {
            if (this.mSendTask != null) {
                this.mSendTask.cancel(true);
            }
        }
    }

    void connectLlcpServices() {
        synchronized (this) {
            if (this.mConnectTask != null) {
                Log.e(TAG, "Still had a reference to mConnectTask!");
            }
            this.mConnectTask = new ConnectTask();
            this.mConnectTask.execute(new Void[SNEP_SUCCESS]);
        }
    }

    void onLlcpServicesConnected() {
        if (DBG) {
            Log.d(TAG, "onLlcpServicesConnected");
        }
        synchronized (this) {
            if (this.mLinkState != SEND_STATE_SENDING) {
                return;
            }
            this.mLlcpServicesConnected = true;
            if (this.mSendState == SEND_STATE_SENDING) {
                this.mEventListener.onP2pResumeSend();
                sendNdefMessage();
            }
        }
    }

    void onReceiveHandover() {
        this.mHandler.obtainMessage(SEND_STATE_SENDING).sendToTarget();
    }

    void onReceiveComplete(NdefMessage msg) {
        EventLogTags.writeNfcNdefReceived(getMessageSize(msg), getMessageTnf(msg), getMessageType(msg), getMessageAarPresent(msg));
        AuditLog.log(MSG_START_ECHOSERVER, MSG_START_ECHOSERVER, true, Process.myPid(), getClass().getSimpleName(), "Receiving data through NFC succeeded");
        this.mHandler.obtainMessage(SEND_STATE_NEED_CONFIRMATION, msg).sendToTarget();
    }

    public boolean handleMessage(Message msg) {
        switch (msg.what) {
            case SNEP_FAILURE /*1*/:
                synchronized (this) {
                    if (this.mLinkState == SEND_STATE_SEND_COMPLETE) {
                        if (this.mSendState == SEND_STATE_SENDING) {
                            AuditLog.log(MSG_START_ECHOSERVER, MSG_START_ECHOSERVER, ECHOSERVER_ENABLED, Process.myPid(), getClass().getSimpleName(), "Sending data through NFC failed");
                            EventLogTags.writeNfcShareFail(getMessageSize(this.mMessageToSend), getMessageTnf(this.mMessageToSend), getMessageType(this.mMessageToSend), getMessageAarPresent(this.mMessageToSend));
                        }
                        if (DBG) {
                            Log.d(TAG, "Debounce timeout");
                        }
                        this.mLinkState = SNEP_FAILURE;
                        this.mSendState = SNEP_FAILURE;
                        this.mMessageToSend = null;
                        this.mUrisToSend = null;
                        if (DBG) {
                            Log.d(TAG, "onP2pOutOfRange()");
                        }
                        this.mEventListener.onP2pOutOfRange();
                        if (NfcService.mIsSecNdefEnabled) {
                            this.mSecNdefService.onP2pOutOfRange();
                        }
                        break;
                    }
                    break;
                    break;
                }
            case SEND_STATE_NEED_CONFIRMATION /*2*/:
                NdefMessage m = msg.obj;
                synchronized (this) {
                    if (this.mLinkState != SNEP_FAILURE) {
                        if (this.mSendState == SEND_STATE_SENDING) {
                            cancelSendNdefMessage();
                        }
                        this.mSendState = SNEP_FAILURE;
                        if (DBG) {
                            Log.d(TAG, "onP2pReceiveComplete()");
                        }
                        this.mEventListener.onP2pReceiveComplete(true);
                        NfcService.getInstance().sendMockNdefTag(m);
                        break;
                    }
                    break;
                    break;
                }
            case SEND_STATE_SENDING /*3*/:
                synchronized (this) {
                    if (this.mLinkState != SNEP_FAILURE) {
                        if (this.mSendState == SEND_STATE_SENDING) {
                            cancelSendNdefMessage();
                        }
                        this.mSendState = SNEP_FAILURE;
                        if (DBG) {
                            Log.d(TAG, "onP2pReceiveComplete()");
                        }
                        this.mEventListener.onP2pReceiveComplete(ECHOSERVER_ENABLED);
                        break;
                    }
                    break;
                    break;
                }
            case SEND_STATE_SEND_COMPLETE /*4*/:
                synchronized (this) {
                    this.mSendTask = null;
                    if (this.mLinkState != SNEP_FAILURE && this.mSendState == SEND_STATE_SENDING) {
                        this.mSendState = SEND_STATE_SEND_COMPLETE;
                        this.mHandler.removeMessages(SNEP_FAILURE);
                        if (DBG) {
                            Log.d(TAG, "onP2pSendComplete()");
                        }
                        this.mEventListener.onP2pSendComplete();
                        if (this.mCallbackNdef != null) {
                            try {
                                this.mCallbackNdef.onNdefPushComplete();
                                break;
                            } catch (Exception e) {
                                Log.e(TAG, "Failed NDEF completed callback: " + e.getMessage());
                            }
                        }
                        break;
                    }
                    if (DBG) {
                        Log.d(TAG, "Send complete succefully, but state is chagned");
                    }
                    String ACTION_P2P_SEND_COMPLETE = "android.nfc.action.P2P_SEND_COMPLETE";
                    Intent SbeamIntent = new Intent("android.nfc.action.P2P_SEND_COMPLETE");
                    SbeamIntent.setPackage("com.sec.android.directshare");
                    this.mContext.sendBroadcast(SbeamIntent);
                    if (DBG) {
                        Log.d(TAG, "send p2p send complete intent to directshare");
                    }
                    Intent ShareShotIntent = new Intent("android.nfc.action.P2P_SEND_COMPLETE");
                    ShareShotIntent.setPackage("com.sec.android.directconnect");
                    this.mContext.sendBroadcast(ShareShotIntent);
                    if (DBG) {
                        Log.d(TAG, "send p2p send complete intent to directconnect");
                    }
                    break;
                    break;
                }
            case MSG_START_ECHOSERVER /*5*/:
                synchronized (this) {
                    this.mEchoServer.start();
                    break;
                }
                break;
            case MSG_STOP_ECHOSERVER /*6*/:
                synchronized (this) {
                    this.mEchoServer.stop();
                    break;
                }
                break;
            case MSG_HANDOVER_NOT_SUPPORTED /*7*/:
                synchronized (this) {
                    this.mSendTask = null;
                    if (this.mLinkState != SNEP_FAILURE && this.mSendState == SEND_STATE_SENDING) {
                        this.mSendState = SNEP_FAILURE;
                        if (DBG) {
                            Log.d(TAG, "onP2pHandoverNotSupported()");
                        }
                        this.mEventListener.onP2pHandoverNotSupported();
                        break;
                    }
                    break;
                    break;
                }
            case MSG_SEC_SEND_MSG /*9*/:
                NdefMessage nm = msg.obj;
                synchronized (this) {
                    if (!this.mIsSendEnabled || this.mLinkState != SEND_STATE_NEED_CONFIRMATION || this.mSendState != SNEP_FAILURE) {
                        if (DBG) {
                            Log.d(TAG, "mLinkState=" + linkStateToString(this.mLinkState));
                        }
                        if (DBG) {
                            Log.d(TAG, "mSendState=" + sendStateToString(this.mSendState));
                        }
                        break;
                    }
                    this.mMessageToSend = nm;
                    this.mSendState = SEND_STATE_NEED_CONFIRMATION;
                    if (DBG) {
                        Log.d(TAG, "onP2pSendConfirmationRequested()");
                    }
                    this.mEventListener.onP2pSendConfirmationRequested();
                    break;
                }
                break;
        }
        return true;
    }

    int getMessageSize(NdefMessage msg) {
        if (msg != null) {
            return msg.toByteArray().length;
        }
        return SNEP_SUCCESS;
    }

    int getMessageTnf(NdefMessage msg) {
        if (msg == null) {
            return SNEP_SUCCESS;
        }
        NdefRecord[] records = msg.getRecords();
        if (records == null || records.length == 0) {
            return SNEP_SUCCESS;
        }
        return records[SNEP_SUCCESS].getTnf();
    }

    String getMessageType(NdefMessage msg) {
        if (msg == null) {
            return "null";
        }
        NdefRecord[] records = msg.getRecords();
        if (records == null || records.length == 0) {
            return "null";
        }
        NdefRecord record = records[SNEP_SUCCESS];
        switch (record.getTnf()) {
            case SNEP_FAILURE /*1*/:
            case SEND_STATE_NEED_CONFIRMATION /*2*/:
            case SEND_STATE_SEND_COMPLETE /*4*/:
                return new String(record.getType(), StandardCharsets.UTF_8);
            case SEND_STATE_SENDING /*3*/:
                return "uri";
            default:
                return "unknown";
        }
    }

    int getMessageAarPresent(NdefMessage msg) {
        if (msg == null) {
            return SNEP_SUCCESS;
        }
        NdefRecord[] records = msg.getRecords();
        if (records == null) {
            return SNEP_SUCCESS;
        }
        NdefRecord[] arr$ = records;
        int len$ = arr$.length;
        for (int i$ = SNEP_SUCCESS; i$ < len$; i$ += SNEP_FAILURE) {
            NdefRecord record = arr$[i$];
            if (record.getTnf() == (short) 4 && Arrays.equals(NdefRecord.RTD_ANDROID_APP, record.getType())) {
                return SNEP_FAILURE;
            }
        }
        return SNEP_SUCCESS;
    }

    public void onP2pSendConfirmed() {
        onP2pSendConfirmed(true);
    }

    private void onP2pSendConfirmed(boolean requireConfirmation) {
        if (DBG) {
            Log.d(TAG, "onP2pSendConfirmed()");
        }
        synchronized (this) {
            if (this.mLinkState == SNEP_FAILURE || (requireConfirmation && this.mSendState != SEND_STATE_NEED_CONFIRMATION)) {
                return;
            }
            this.mSendState = SEND_STATE_SENDING;
            if (this.mLinkState == SEND_STATE_NEED_CONFIRMATION) {
                this.mLinkState = SEND_STATE_SENDING;
                connectLlcpServices();
            } else if (this.mLinkState == SEND_STATE_SENDING && this.mLlcpServicesConnected) {
                sendNdefMessage();
            } else if (this.mLinkState == SEND_STATE_SENDING && this.mLlcpConnectDelayed) {
                connectLlcpServices();
            } else if (this.mLinkState == SEND_STATE_SEND_COMPLETE) {
                this.mHandler.removeMessages(SNEP_FAILURE);
                this.mHandler.sendEmptyMessageDelayed(SNEP_FAILURE, 5000);
                this.mEventListener.onP2pSendDebounce();
            }
        }
    }

    static String sendStateToString(int state) {
        switch (state) {
            case SNEP_FAILURE /*1*/:
                return "SEND_STATE_NOTHING_TO_SEND";
            case SEND_STATE_NEED_CONFIRMATION /*2*/:
                return "SEND_STATE_NEED_CONFIRMATION";
            case SEND_STATE_SENDING /*3*/:
                return "SEND_STATE_SENDING";
            default:
                return "<error>";
        }
    }

    static String linkStateToString(int state) {
        switch (state) {
            case SNEP_FAILURE /*1*/:
                return "LINK_STATE_DOWN";
            case SEND_STATE_NEED_CONFIRMATION /*2*/:
                return "LINK_STATE_WAITING_PDU";
            case SEND_STATE_SENDING /*3*/:
                return "LINK_STATE_UP";
            case SEND_STATE_SEND_COMPLETE /*4*/:
                return "LINK_STATE_DEBOUNCE";
            default:
                return "<error>";
        }
    }

    void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        synchronized (this) {
            pw.println("mIsSendEnabled=" + this.mIsSendEnabled);
            pw.println("mIsReceiveEnabled=" + this.mIsReceiveEnabled);
            pw.println("mLinkState=" + linkStateToString(this.mLinkState));
            pw.println("mSendState=" + sendStateToString(this.mSendState));
            pw.println("mCallbackNdef=" + this.mCallbackNdef);
            pw.println("mMessageToSend=" + this.mMessageToSend);
            pw.println("mUrisToSend=" + this.mUrisToSend);
        }
    }

    public int createSecNdefService(String serviceName, int serverSap, String pkgName, byte[] type, byte[] id) throws Exception {
        if (!NfcService.mIsSecNdefEnabled) {
            return -1;
        }
        try {
            return this.mSecNdefService.createSecNdefService(serviceName, serverSap, pkgName, type, id);
        } catch (Exception e) {
            throw e;
        }
    }

    public int secSendNdefMsg(int SAP, NdefMessage msg) {
        if (NfcService.mIsSecNdefEnabled) {
            return this.mSecNdefService.secSendNdefMsg(SAP, msg);
        }
        return -1;
    }

    public int closeSecNdefService(int SAP) {
        if (NfcService.mIsSecNdefEnabled && this.mSecNdefService != null && this.mSecNdefService.closeSecNdefService(SAP)) {
            return SNEP_SUCCESS;
        }
        return -1;
    }

    public int secSendAbeamNdefMsg(NdefMessage msg) {
        if (!NfcService.mIsSecNdefEnabled) {
            return -1;
        }
        this.mHandler.obtainMessage(MSG_SEC_SEND_MSG, msg).sendToTarget();
        return SNEP_SUCCESS;
    }
}
