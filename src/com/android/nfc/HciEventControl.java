package com.android.nfc;

import android.app.ActivityManager;
import android.app.ActivityManager.RunningAppProcessInfo;
import android.app.ActivityManagerNative;
import android.app.IActivityManager;
import android.app.PendingIntent;
import android.app.PendingIntent.CanceledException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Binder;
import android.os.IBinder;
import android.os.PatternMatcher;
import android.os.RemoteException;
import android.util.Log;
import android.util.LogPrinter;
import com.android.nfc.handover.HandoverService.Device;
import com.gsma.services.nfc.NfcController;
import com.sec.android.app.CscFeature;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import org.simalliance.openmobileapi.service.ISmartcardService;
import org.simalliance.openmobileapi.service.ISmartcardService.Stub;
import org.simalliance.openmobileapi.service.ISmartcardServiceCallback;
import org.simalliance.openmobileapi.service.SmartcardError;

public class HciEventControl {
    private static final String ADMIN_PERM = "android.permission.WRITE_SECURE_SETTINGS";
    private static final String ADMIN_PERM_ERROR = "WRITE_SECURE_SETTINGS permission required";
    static final boolean DBG;
    public static final int READER_ESE = 1;
    public static final int READER_SIM = 0;
    static final String TAG = "NfcServiceHciEventControl";
    private String ACTION_TRANSACTION_DETECTED;
    private String EXTENDED_HCI_EVT_TRANSACTION_EXTRA_AID;
    private String EXTENDED_HCI_EVT_TRANSACTION_EXTRA_DATA;
    private String PERMISSION_TRANSACTION;
    private String READER_ESE_S;
    private String READER_SIM_S;
    private String SMARTCARDAPI_VERSION;
    private boolean isGsma_v40_HciSupported;
    private boolean mBindSmartcardServiceSuccess;
    private Context mContext;
    private final IActivityManager mIActivityManager;
    private IntentFilter[] mOverrideFilters;
    private PendingIntent mOverrideIntent;
    private PackageManager mPackageManager;
    private HashMap<String, Boolean> multEvt;
    private final ServiceConnection smartcardConnection;
    private volatile ISmartcardService smartcardSvc;

    /* renamed from: com.android.nfc.HciEventControl.1 */
    class C00031 implements ServiceConnection {
        C00031() {
        }

        public void onServiceConnected(ComponentName className, IBinder service) {
            if (HciEventControl.DBG) {
                Log.v(HciEventControl.TAG, "SmartcardService onServiceConnected");
            }
            HciEventControl.this.smartcardSvc = Stub.asInterface(service);
        }

        public void onServiceDisconnected(ComponentName className) {
            if (HciEventControl.DBG) {
                Log.v(HciEventControl.TAG, "SmartcardService onServiceDisconnected");
            }
        }
    }

    /* renamed from: com.android.nfc.HciEventControl.2 */
    class C00042 implements Comparator {
        C00042() {
        }

        public int compare(Object o1, Object o2) {
            int p1 = ((ResolveInfo) o1).priority;
            int p2 = ((ResolveInfo) o2).priority;
            long t1 = 0;
            long t2 = 0;
            try {
                t1 = HciEventControl.this.mPackageManager.getPackageInfo(((ResolveInfo) o1).activityInfo.packageName, HciEventControl.READER_SIM).firstInstallTime;
                t2 = HciEventControl.this.mPackageManager.getPackageInfo(((ResolveInfo) o2).activityInfo.packageName, HciEventControl.READER_SIM).firstInstallTime;
            } catch (NameNotFoundException e) {
                if (HciEventControl.DBG) {
                    Log.d(HciEventControl.TAG, "Cant't find Package name.");
                }
            }
            if (p1 > p2) {
                return -1;
            }
            if (p1 < p2) {
                return HciEventControl.READER_ESE;
            }
            if (t1 < t2) {
                return -1;
            }
            return t1 > t2 ? HciEventControl.READER_ESE : HciEventControl.READER_SIM;
        }
    }

    /* renamed from: com.android.nfc.HciEventControl.3 */
    class C00053 extends ISmartcardServiceCallback.Stub {
        C00053() {
        }
    }

    static {
        DBG = NfcService.DBG;
    }

    public HciEventControl(Context context) {
        this.PERMISSION_TRANSACTION = "android.permission.NFC_TRANSACTION";
        this.ACTION_TRANSACTION_DETECTED = "android.nfc.action.TRANSACTION_DETECTED";
        this.EXTENDED_HCI_EVT_TRANSACTION_EXTRA_AID = "android.nfc.extra.AID";
        this.EXTENDED_HCI_EVT_TRANSACTION_EXTRA_DATA = "android.nfc.extra.DATA";
        this.READER_SIM_S = "SIM: UICC";
        this.READER_ESE_S = "eSE: SmartMX";
        this.SMARTCARDAPI_VERSION = null;
        this.mBindSmartcardServiceSuccess = DBG;
        this.multEvt = new HashMap();
        this.isGsma_v40_HciSupported = DBG;
        this.smartcardConnection = new C00031();
        this.mContext = context;
        bindSmartcardService();
        this.mIActivityManager = ActivityManagerNative.getDefault();
        this.mPackageManager = context.getPackageManager();
        this.SMARTCARDAPI_VERSION = "3.1.0";
        if (Integer.parseInt(this.SMARTCARDAPI_VERSION.substring(READER_SIM, this.SMARTCARDAPI_VERSION.indexOf("."))) >= 3) {
            this.READER_SIM_S = "SIM - UICC";
            this.READER_ESE_S = "eSE - SmartMX";
        }
        this.isGsma_v40_HciSupported = CscFeature.getInstance().getString("CscFeature_NFC_SetSecureEventType", "GSMA").contains("GSMA_v40");
        if (this.isGsma_v40_HciSupported) {
            this.PERMISSION_TRANSACTION = NfcController.NFC_TRANSACTION_PERMISSION;
            this.ACTION_TRANSACTION_DETECTED = NfcController.ACTION_TRANSACTION_EVENT;
            this.EXTENDED_HCI_EVT_TRANSACTION_EXTRA_AID = NfcController.TRANSACTION_EXTRA_AID;
            this.EXTENDED_HCI_EVT_TRANSACTION_EXTRA_DATA = NfcController.TRANSACTION_EXTRA_DATA;
        }
        if (NfcService.isVzw) {
            this.multEvt.put(this.READER_SIM_S, Boolean.valueOf(true));
        }
    }

    public synchronized void setForegroundDispatch(PendingIntent intent, IntentFilter[] filters) {
        if (DBG) {
            Log.d(TAG, "Set SE Foreground Dispatch");
        }
        waitForSmartcardService();
        if (this.mBindSmartcardServiceSuccess) {
            this.mOverrideIntent = intent;
            this.mOverrideFilters = filters;
        }
    }

    public void enableMultiEvt_transactionReception(String SEName, boolean enable) {
        if (DBG) {
            Log.d(TAG, "enableMultiEvt_transactionReception for " + SEName);
        }
        waitForSmartcardService();
        if (!this.mBindSmartcardServiceSuccess) {
            return;
        }
        if (SEName.toLowerCase().startsWith("sim")) {
            this.multEvt.put(this.READER_SIM_S, Boolean.valueOf(enable));
        } else if (SEName.toLowerCase().startsWith("ese")) {
            this.multEvt.put(this.READER_ESE_S, Boolean.valueOf(enable));
        }
    }

    public void checkAndSendIntent(int reader, byte[] aid, byte[] param) {
        if (DBG) {
            Log.d(TAG, "checkAndSendIntent");
        }
        waitForSmartcardService();
        if (!this.mBindSmartcardServiceSuccess) {
            return;
        }
        if (reader == 0) {
            try {
                dispatchSecureEvent(this.READER_SIM_S, aid, param);
            } catch (Exception e) {
                if (DBG) {
                    Log.e(TAG, "exception occured");
                }
            }
        } else if (reader == READER_ESE) {
            dispatchSecureEvent(this.READER_ESE_S, aid, param);
        }
    }

    public boolean isAllowedForGsma(String SEName) throws RemoteException {
        waitForSmartcardService();
        if (!this.mBindSmartcardServiceSuccess) {
            return DBG;
        }
        try {
            this.mContext.enforceCallingOrSelfPermission(ADMIN_PERM, ADMIN_PERM_ERROR);
        } catch (SecurityException e) {
            try {
                checkCdfApproved(SEName);
            } catch (SecurityException e2) {
                return DBG;
            }
        }
        return true;
    }

    public boolean isAllowedForGsma() throws RemoteException {
        String[] list = new String[]{"SIM", "eSE"};
        boolean result = DBG;
        waitForSmartcardService();
        if (!this.mBindSmartcardServiceSuccess) {
            return DBG;
        }
        try {
            this.mContext.enforceCallingOrSelfPermission(ADMIN_PERM, ADMIN_PERM_ERROR);
            return DBG | READER_ESE;
        } catch (SecurityException e) {
            String[] arr$ = list;
            int len$ = arr$.length;
            for (int i$ = READER_SIM; i$ < len$; i$ += READER_ESE) {
                String se = arr$[i$];
                try {
                    if (DBG) {
                        Log.d(TAG, "check the permission on " + se);
                    }
                    checkCdfApproved(se);
                    result |= READER_ESE;
                } catch (SecurityException e2) {
                    if (DBG) {
                        Log.w(TAG, "SecurityException");
                    }
                } catch (RemoteException e3) {
                    if (DBG) {
                        Log.w(TAG, "RemoteException");
                    }
                } catch (NullPointerException e4) {
                    if (DBG) {
                        Log.w(TAG, "NullPointerException");
                    }
                }
            }
            return result;
        }
    }

    public boolean[] isSeFieldEvtAllowed(String SEName, Intent intent, String[] packageNames) {
        return isConnectionAllowed(SEName, intent, packageNames);
    }

    private void bindSmartcardService() {
        Intent intent = new Intent();
        if (DBG) {
            Log.d(TAG, "bindSmartcardService");
        }
        intent.setClassName("org.simalliance.openmobileapi.service", "org.simalliance.openmobileapi.service.SmartcardService");
        intent.setAction("org.simalliance.openmobileapi.service.ISmartcardService");
        if (this.mContext.getApplicationContext().bindService(intent, this.smartcardConnection, READER_ESE)) {
            if (DBG) {
                Log.d(TAG, "bindService success!");
            }
            this.mBindSmartcardServiceSuccess = true;
            return;
        }
        if (DBG) {
            Log.d(TAG, "bindService failed!");
        }
        this.mBindSmartcardServiceSuccess = DBG;
    }

    private String checkForException(SmartcardError error) {
        Exception exp = error.createException();
        if (exp != null) {
            return exp.getMessage();
        }
        return "";
    }

    private String byteArray2String(byte[] data, int start, int length, String prefix) {
        if (data == null) {
            return new String("");
        }
        if (length == -1) {
            length = data.length - start;
        }
        StringBuffer buffer = new StringBuffer();
        for (int ind = start; ind < start + length; ind += READER_ESE) {
            buffer.append(prefix + Integer.toHexString((data[ind] & 255) + Device.COMPUTER_UNCATEGORIZED).substring(READER_ESE));
        }
        return buffer.toString();
    }

    /* JADX WARNING: inconsistent code. */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    private void waitForSmartcardService() {
        /*
        r6 = this;
        r3 = r6.smartcardConnection;
        monitor-enter(r3);
        r2 = DBG;	 Catch:{ InterruptedException -> 0x004f }
        if (r2 == 0) goto L_0x0021;
    L_0x0007:
        r2 = "NfcServiceHciEventControl";
        r4 = new java.lang.StringBuilder;	 Catch:{ InterruptedException -> 0x004f }
        r4.<init>();	 Catch:{ InterruptedException -> 0x004f }
        r5 = "waitForSmartcardService : ";
        r4 = r4.append(r5);	 Catch:{ InterruptedException -> 0x004f }
        r5 = r6.smartcardSvc;	 Catch:{ InterruptedException -> 0x004f }
        r4 = r4.append(r5);	 Catch:{ InterruptedException -> 0x004f }
        r4 = r4.toString();	 Catch:{ InterruptedException -> 0x004f }
        android.util.Log.d(r2, r4);	 Catch:{ InterruptedException -> 0x004f }
    L_0x0021:
        r1 = 0;
    L_0x0022:
        r2 = 3;
        if (r1 >= r2) goto L_0x005b;
    L_0x0025:
        r2 = r6.smartcardSvc;	 Catch:{ InterruptedException -> 0x004f }
        if (r2 != 0) goto L_0x004c;
    L_0x0029:
        r2 = r6.mBindSmartcardServiceSuccess;	 Catch:{ InterruptedException -> 0x004f }
        if (r2 != 0) goto L_0x003a;
    L_0x002d:
        r2 = DBG;	 Catch:{ InterruptedException -> 0x004f }
        if (r2 == 0) goto L_0x0038;
    L_0x0031:
        r2 = "NfcServiceHciEventControl";
        r4 = "binding to access control service did not succeed";
        android.util.Log.e(r2, r4);	 Catch:{ InterruptedException -> 0x004f }
    L_0x0038:
        monitor-exit(r3);	 Catch:{ all -> 0x005d }
    L_0x0039:
        return;
    L_0x003a:
        r2 = DBG;	 Catch:{ InterruptedException -> 0x004f }
        if (r2 == 0) goto L_0x0045;
    L_0x003e:
        r2 = "NfcServiceHciEventControl";
        r4 = "waiting for connection to SmartcardService";
        android.util.Log.i(r2, r4);	 Catch:{ InterruptedException -> 0x004f }
    L_0x0045:
        r2 = r6.smartcardConnection;	 Catch:{ InterruptedException -> 0x004f }
        r4 = 1000; // 0x3e8 float:1.401E-42 double:4.94E-321;
        r2.wait(r4);	 Catch:{ InterruptedException -> 0x004f }
    L_0x004c:
        r1 = r1 + 1;
        goto L_0x0022;
    L_0x004f:
        r0 = move-exception;
        r2 = DBG;	 Catch:{ all -> 0x005d }
        if (r2 == 0) goto L_0x005b;
    L_0x0054:
        r2 = "NfcServiceHciEventControl";
        r4 = "InterruptedException";
        android.util.Log.e(r2, r4, r0);	 Catch:{ all -> 0x005d }
    L_0x005b:
        monitor-exit(r3);	 Catch:{ all -> 0x005d }
        goto L_0x0039;
    L_0x005d:
        r2 = move-exception;
        monitor-exit(r3);	 Catch:{ all -> 0x005d }
        throw r2;
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.nfc.HciEventControl.waitForSmartcardService():void");
    }

    private void dispatchSecureEvent(String reader, byte[] aid, byte[] param) {
        if (DBG) {
            Log.d(TAG, "Dispatching SE");
        }
        Intent intentTransaction = buildSeIntent(reader, aid, param);
        Intent partIntentTransaction = buildPartSeIntent(reader, aid, param);
        if (reader.startsWith(this.READER_ESE_S)) {
            intentTransaction = new Intent();
            intentTransaction.setAction(NfcService.ACTION_AID_SELECTED);
            intentTransaction.putExtra(NfcService.EXTRA_AID, aid);
            intentTransaction.putExtra(NfcService.EXTRA_DATA, param);
            this.mContext.sendBroadcast(intentTransaction);
        } else if (Boolean.TRUE.equals(this.multEvt.get(reader))) {
            ri = this.mPackageManager.queryBroadcastReceivers(intentTransaction, 65600);
            ri.addAll(this.mPackageManager.queryBroadcastReceivers(partIntentTransaction, 65600));
            try {
                sendSeBroadcast(reader, intentTransaction, aid, ri);
            } catch (Exception e) {
                if (DBG) {
                    Log.e(TAG, "exception occured");
                }
            }
        } else {
            IntentFilter[] overrideFilters;
            PendingIntent overrideIntent;
            synchronized (this) {
                overrideFilters = this.mOverrideFilters;
                overrideIntent = this.mOverrideIntent;
            }
            if (!isEnabledForegroundSeDispatch(reader, intentTransaction, overrideIntent, overrideFilters)) {
                resumeAppSwitches();
                ri = this.mPackageManager.queryIntentActivities(intentTransaction, 65600);
                ri.addAll(this.mPackageManager.queryIntentActivities(partIntentTransaction, 65600));
                Collections.sort(ri, new C00042());
                if (DBG) {
                    Log.d(TAG, "Looking for an activity for a SE.");
                }
                try {
                    if (findAndStartActivity(reader, intentTransaction, aid, ri)) {
                        if (DBG) {
                            Log.d(TAG, "Started activity for a SE.");
                            return;
                        }
                        return;
                    }
                } catch (Exception e2) {
                    if (DBG) {
                        Log.e(TAG, "exception occured");
                    }
                }
                if (DBG) {
                    Log.d(TAG, "There's no activity for a SE.");
                }
            }
        }
    }

    private boolean isEnabledForegroundSeDispatch(String reader, Intent intent, PendingIntent pendingIntent, IntentFilter[] filters) {
        if (pendingIntent != null) {
            String packageName = pendingIntent.getCreatorPackage();
            String[] strArr = new String[READER_ESE];
            strArr[READER_SIM] = packageName;
            boolean[] access = isConnectionAllowed(reader, intent, strArr);
            if (DBG) {
                Log.d(TAG, "Attempting to dispatch tag with override intent: ");
            }
            if (this.mPackageManager.checkPermission(this.PERMISSION_TRANSACTION, packageName) != 0 || this.mPackageManager.checkPermission(NfcController.NFC_CONTROLLER_PERMISSION, packageName) != 0 || !access[READER_SIM]) {
                if (DBG) {
                    Log.d(TAG, "Permission denied");
                }
                return DBG;
            } else if (filters == null) {
                try {
                    if (DBG) {
                        Log.d(TAG, "IntentFilter[] == null - not checking filter matching.");
                    }
                    pendingIntent.send(this.mContext, -1, intent);
                    Log.d(TAG, "Transaction intent was sent through pending intent.");
                    return true;
                } catch (CanceledException e) {
                    if (DBG) {
                        Log.d(TAG, "Cant't send an intent through pending intent.");
                    }
                    return DBG;
                }
            } else {
                IntentFilter[] arr$ = filters;
                int len$ = arr$.length;
                int i$ = READER_SIM;
                while (i$ < len$) {
                    IntentFilter filter = arr$[i$];
                    if (DBG) {
                        Log.d(TAG, "Found intent filter: ");
                        filter.dump(new LogPrinter(3, TAG), "");
                    }
                    if (isIntentFilterMatch(filter, intent)) {
                        try {
                            pendingIntent.send(this.mContext, -1, intent);
                            Log.d(TAG, "Transaction intent was sent through pending intent.");
                            return true;
                        } catch (CanceledException e2) {
                            if (DBG) {
                                Log.d(TAG, "Cant't send an intent through pending intent.");
                            }
                        }
                    } else {
                        i$ += READER_ESE;
                    }
                }
            }
        }
        return DBG;
    }

    private boolean findAndStartActivity(String reader, Intent intentTransaction, byte[] aid, List<ResolveInfo> ri) throws Exception {
        if (ri != null && ri.size() > 0) {
            int i;
            if (DBG) {
                Log.d(TAG, "Trying to start a normal activity.");
            }
            String[] packageNames = new String[ri.size()];
            for (i = READER_SIM; i < ri.size(); i += READER_ESE) {
                packageNames[i] = ((ResolveInfo) ri.get(i)).activityInfo.packageName;
            }
            boolean[] access = isConnectionAllowed(reader, intentTransaction, packageNames);
            i = READER_SIM;
            while (i < ri.size()) {
                String packageName = ((ResolveInfo) ri.get(i)).activityInfo.packageName;
                String activityName = ((ResolveInfo) ri.get(i)).activityInfo.name;
                if (DBG) {
                    try {
                        PackageInfo packageInfo = this.mContext.getPackageManager().getPackageInfo(packageName, 4096);
                        Log.i(TAG, "package=" + packageName);
                        Log.i(TAG, "activity=" + activityName);
                        Log.i(TAG, "requestedPermissions=" + Arrays.toString(packageInfo.requestedPermissions));
                    } catch (NameNotFoundException e) {
                        Log.e(TAG, e.getMessage(), e);
                    }
                    if (this.mPackageManager.checkPermission(this.PERMISSION_TRANSACTION, packageName) != 0) {
                        Log.w(TAG, this.PERMISSION_TRANSACTION + " doesn't exist");
                    }
                }
                if (this.mPackageManager.checkPermission(this.PERMISSION_TRANSACTION, packageName) == 0 && access[i]) {
                    if (startActivity(intentTransaction, packageName, activityName)) {
                        return true;
                    }
                } else if (DBG) {
                    Log.d(TAG, "Permission denied");
                }
                i += READER_ESE;
            }
            if (DBG) {
                Log.d(TAG, "There's no activity that is allowed to handle SE.");
            }
        }
        return DBG;
    }

    private Intent buildSeIntent(String reader, byte[] aid, byte[] param) {
        Intent transactionIntent = new Intent();
        transactionIntent.setAction(this.ACTION_TRANSACTION_DETECTED);
        transactionIntent.putExtra(this.EXTENDED_HCI_EVT_TRANSACTION_EXTRA_AID, aid);
        transactionIntent.putExtra(this.EXTENDED_HCI_EVT_TRANSACTION_EXTRA_DATA, param);
        if (this.isGsma_v40_HciSupported) {
            Uri uri;
            if (this.READER_SIM_S.equalsIgnoreCase(reader)) {
                uri = Uri.parse("nfc://secure:0/SIM/" + bytesToString(aid).toLowerCase());
            } else if (!this.READER_ESE_S.equalsIgnoreCase(reader)) {
                return null;
            } else {
                uri = Uri.parse("nfc://secure:0/eSE/" + bytesToString(aid).toLowerCase());
            }
            transactionIntent.setData(uri);
            return transactionIntent;
        } else if (this.READER_SIM_S.equalsIgnoreCase(reader)) {
            transactionIntent.setData(Uri.parse("nfc://secure:0/" + byteArray2String(aid, READER_SIM, -1, "").toLowerCase()));
            return transactionIntent;
        } else if (this.READER_ESE_S.equalsIgnoreCase(reader)) {
            return transactionIntent;
        } else {
            return null;
        }
    }

    private Intent buildPartSeIntent(String reader, byte[] aid, byte[] param) {
        Intent querryIntent = new Intent();
        if (this.isGsma_v40_HciSupported) {
            Uri uri;
            querryIntent.setAction(NfcController.ACTION_TRANSACTION_EVENT);
            querryIntent.putExtra(NfcController.TRANSACTION_EXTRA_AID, aid);
            querryIntent.putExtra(NfcController.TRANSACTION_EXTRA_DATA, param);
            if (this.READER_SIM_S.equalsIgnoreCase(reader)) {
                uri = Uri.parse("nfc://secure:0/SIM");
            } else if (this.READER_ESE_S.equalsIgnoreCase(reader)) {
                uri = Uri.parse("nfc://secure:0/eSE");
            } else {
                uri = Uri.parse("nfc://secure:0/");
            }
            querryIntent.setData(uri);
            return querryIntent;
        } else if (this.READER_SIM_S.equalsIgnoreCase(reader)) {
            querryIntent.setData(Uri.parse("nfc://secure:0/"));
            return querryIntent;
        } else if (this.READER_ESE_S.equalsIgnoreCase(reader)) {
            return querryIntent;
        } else {
            return null;
        }
    }

    private boolean isIntentFilterMatch(IntentFilter intentFilter, Intent intent) {
        if (intent == null || intentFilter == null) {
            return DBG;
        }
        String scheme = intent.getScheme();
        String host = intent.getData().getHost();
        int port = intent.getData().getPort();
        String fullPath = intent.getData().getPath();
        String partPath = "/" + ((String) intent.getData().getPathSegments().get(READER_SIM));
        boolean match = DBG;
        if (intentFilter.hasDataScheme(scheme)) {
            int numDataAuthorities = intentFilter.countDataAuthorities();
            int i = READER_SIM;
            while (i < numDataAuthorities) {
                if (intentFilter.getDataAuthority(i).getPort() == port && host.compareToIgnoreCase(intentFilter.getDataAuthority(i).getHost()) == 0) {
                    match = true;
                    break;
                }
                i += READER_ESE;
            }
            if (match) {
                int numDataPaths = intentFilter.countDataPaths();
                if (numDataPaths > 0) {
                    for (i = READER_SIM; i < numDataPaths; i += READER_ESE) {
                        PatternMatcher pm = intentFilter.getDataPath(i);
                        if (pm.getType() == 0) {
                            if (pm.match(fullPath) || pm.match(partPath)) {
                                if (DBG) {
                                    Log.d(TAG, "Found matching path.");
                                }
                                return true;
                            }
                        } else if (DBG) {
                            Log.d(TAG, "Only PATTERN_LITERAL paths are allowed.");
                        }
                    }
                }
                if (DBG) {
                    Log.d(TAG, "Intent filter does not match: no matching path in the intent filter.");
                }
                return DBG;
            }
            if (DBG) {
                Log.d(TAG, "Intent filter does not match: no matching host and port in the intent filter.");
            }
            return DBG;
        }
        if (DBG) {
            Log.d(TAG, "Intent filter does not match: no matching scheme in the intent filter.");
        }
        return DBG;
    }

    private boolean startActivity(Intent intentTransaction, String packageName, String activityName) {
        intentTransaction.setClassName(packageName, activityName);
        intentTransaction.addFlags(268435456);
        if (DBG) {
            Log.d(TAG, "Start Activity for Card Emulation event for package");
        }
        try {
            this.mContext.startActivity(intentTransaction);
            return true;
        } catch (Exception e) {
            if (DBG) {
                Log.e(TAG, "There were problems starting activity", e);
            }
            return DBG;
        }
    }

    private void sendSeBroadcast(String reader, Intent intent, byte[] aid, List<ResolveInfo> ri) throws Exception {
        if (DBG) {
            Log.d(TAG, "Secure event broadcast.");
        }
        resumeAppSwitches();
        if (ri != null && ri.size() > 0) {
            int i;
            String[] packageNames = new String[ri.size()];
            for (i = READER_SIM; i < ri.size(); i += READER_ESE) {
                packageNames[i] = ((ResolveInfo) ri.get(i)).activityInfo.packageName;
            }
            boolean[] access = isConnectionAllowed(reader, intent, packageNames);
            i = READER_SIM;
            while (i < ri.size()) {
                String packageName = ((ResolveInfo) ri.get(i)).activityInfo.packageName;
                String itemName = ((ResolveInfo) ri.get(i)).activityInfo.name;
                if (this.mPackageManager.checkPermission(this.PERMISSION_TRANSACTION, packageName) == 0 && this.mPackageManager.checkPermission(NfcController.NFC_CONTROLLER_PERMISSION, packageName) == 0 && access[i]) {
                    intent.setClassName(packageName, itemName);
                    this.mContext.sendBroadcast(intent);
                } else if (DBG) {
                    Log.d(TAG, "Permission denied");
                }
                i += READER_ESE;
            }
        }
    }

    private boolean[] isConnectionAllowed(String SEName, Intent intent, String[] packageNames) {
        if (SEName == null || intent == null) {
            if (DBG) {
                Log.d(TAG, "aid and/or packageName == null , returning false");
            }
            return new boolean[packageNames.length];
        }
        byte[] aid = intent.getByteArrayExtra(this.EXTENDED_HCI_EVT_TRANSACTION_EXTRA_AID);
        if (this.mBindSmartcardServiceSuccess) {
            try {
                ISmartcardServiceCallback callback = new C00053();
                SmartcardError error = new SmartcardError();
                boolean[] result = this.smartcardSvc.isNFCEventAllowed(SEName, aid, packageNames, callback, error);
                if (result != null) {
                    return result;
                }
                if (DBG) {
                    Log.d(TAG, "isNFCEventAllowed returned null: " + checkForException(error));
                }
                return new boolean[packageNames.length];
            } catch (RemoteException e) {
                if (DBG) {
                    Log.d(TAG, "There was problem during executing isNFCEventAllowed. Returning false.");
                }
                return new boolean[packageNames.length];
            }
        }
        if (DBG) {
            Log.d(TAG, "mBindSmartcardServiceSuccess == false , returning false");
        }
        return new boolean[packageNames.length];
    }

    private void resumeAppSwitches() {
        try {
            this.mIActivityManager.resumeAppSwitches();
        } catch (RemoteException e) {
        }
    }

    private static String bytesToString(byte[] bytes) {
        if (bytes == null) {
            return new String("");
        }
        StringBuffer sb = new StringBuffer();
        byte[] arr$ = bytes;
        int len$ = arr$.length;
        for (int i$ = READER_SIM; i$ < len$; i$ += READER_ESE) {
            Object[] objArr = new Object[READER_ESE];
            objArr[READER_SIM] = Integer.valueOf(arr$[i$] & 255);
            sb.append(String.format("%02x", objArr));
        }
        return sb.toString();
    }

    private void checkCdfApproved(String SEName) throws RemoteException {
        if (this.smartcardSvc == null || !this.mBindSmartcardServiceSuccess) {
            if (DBG) {
                Log.d(TAG, "SmartCardSrv not binded - cdf not approved");
            }
            throw new SecurityException("PKCS#15 CDF certificate authorization failed");
        }
        int pid = Binder.getCallingPid();
        for (RunningAppProcessInfo appInfo : ((ActivityManager) this.mContext.getSystemService("activity")).getRunningAppProcesses()) {
            if (appInfo.pid == pid) {
                if (DBG) {
                    Log.d(TAG, "found PID");
                }
                if (!this.smartcardSvc.isCdfAllowed(SEName, appInfo.processName)) {
                    Log.w(TAG, "PKCS#15 CDF authorization failed");
                    throw new SecurityException("PKCS#15 CDF certificate authorization failed");
                } else if (DBG) {
                    Log.d(TAG, "PKCS#15 CDF authorization passed");
                    return;
                } else {
                    return;
                }
            }
        }
        Log.w(TAG, "Caller package name cannot be determined");
        throw new SecurityException("PKCS#15 CDF certificate authorization failed");
    }
}
