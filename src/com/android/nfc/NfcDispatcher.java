package com.android.nfc;

import android.app.ActivityManager;
import android.app.ActivityManagerNative;
import android.app.IActivityManager;
import android.app.PendingIntent;
import android.app.PendingIntent.CanceledException;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences.Editor;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ResolveInfo;
import android.content.res.Resources.NotFoundException;
import android.net.Uri;
import android.net.Uri.Builder;
import android.nfc.FormatException;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.Tag;
import android.nfc.tech.Ndef;
import android.nfc.tech.NfcA;
import android.os.PatternMatcher;
import android.os.RemoteException;
import android.os.UserHandle;
import android.util.Log;
import android.widget.Toast;
import com.android.nfc.RegisteredComponentCache.ComponentInfo;
import com.android.nfc.handover.HandoverManager;
import com.gsma.services.nfc.NfcController;
import com.sec.android.app.CscFeature;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.FileDescriptor;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

public class NfcDispatcher {
    static final boolean DBG;
    static final String HOMESYNC_PACKAGE_NAME = "com.sec.android.homesync";
    static final String REG_EXPRESS_PHONE_NUMBER = "^([0-9]|[*#(/)N,.;+-])+$";
    static final String TAG = "NfcDispatcher";
    static final String TYPE_NAME_SEC_OOB = "application/vnd.sec.oob.";
    static final String VALID_PHONE_NUMBER = "0123456789*#(/)N,.;+-";
    final String ACTION_TRANSACTION_DETECTED;
    final String EXTRA_DATA;
    final String NFC_PERM;
    final ContentResolver mContentResolver;
    final Context mContext;
    final HandoverManager mHandoverManager;
    final IActivityManager mIActivityManager;
    IntentFilter[] mOverrideFilters;
    PendingIntent mOverrideIntent;
    String[][] mOverrideTechLists;
    final String[] mProvisioningMimes;
    boolean mProvisioningOnly;
    final RegisteredComponentCache mTechListFilters;
    Toast mToast;

    static class DispatchInfo {
        final Context context;
        public final Intent intent;
        final String ndefMimeType;
        final Uri ndefUri;
        final PackageManager packageManager;
        final Intent rootIntent;

        public DispatchInfo(Context context, Tag tag, NdefMessage message) {
            this.intent = new Intent();
            this.intent.putExtra("android.nfc.extra.TAG", tag);
            this.intent.putExtra("android.nfc.extra.ID", tag.getId());
            if (message != null) {
                this.intent.putExtra("android.nfc.extra.NDEF_MESSAGES", new NdefMessage[]{message});
                this.ndefUri = message.getRecords()[0].toUri();
                this.ndefMimeType = message.getRecords()[0].toMimeType();
            } else {
                this.ndefUri = null;
                this.ndefMimeType = null;
            }
            this.rootIntent = new Intent(context, NfcRootActivity.class);
            this.rootIntent.putExtra("launchIntent", this.intent);
            this.rootIntent.setFlags(268468224);
            this.rootIntent.putExtra(NfcRootActivity.EXTRA_INVALID_TAG, NfcDispatcher.DBG);
            this.context = context;
            this.packageManager = context.getPackageManager();
        }

        public Intent setNdefIntent() {
            this.intent.setAction("android.nfc.action.NDEF_DISCOVERED");
            if (this.ndefUri != null) {
                this.intent.setData(this.ndefUri);
                return this.intent;
            } else if (this.ndefMimeType == null) {
                return null;
            } else {
                this.intent.setType(this.ndefMimeType);
                return this.intent;
            }
        }

        public Intent setTechIntent() {
            this.intent.setData(null);
            this.intent.setType(null);
            this.intent.setAction("android.nfc.action.TECH_DISCOVERED");
            return this.intent;
        }

        public Intent setTagIntent() {
            this.intent.setData(null);
            this.intent.setType(null);
            this.intent.setAction("android.nfc.action.TAG_DISCOVERED");
            return this.intent;
        }

        boolean tryStartActivity() {
            if (this.packageManager.queryIntentActivitiesAsUser(this.intent, 0, ActivityManager.getCurrentUser()).size() <= 0) {
                return NfcDispatcher.DBG;
            }
            Log.d(NfcDispatcher.TAG, "tryStartActivity. Send intent.");
            this.context.startActivityAsUser(this.rootIntent, UserHandle.CURRENT);
            return true;
        }

        boolean tryStartActivity(String packagename) {
            Intent startintetnt = new Intent("android.intent.action.MAIN");
            startintetnt.setPackage(packagename);
            ResolveInfo activities = this.packageManager.resolveActivityAsUser(startintetnt, 0, ActivityManager.getCurrentUser());
            if (activities == null) {
                return NfcDispatcher.DBG;
            }
            Intent intent = new Intent(startintetnt);
            intent.setClassName(activities.activityInfo.applicationInfo.packageName, activities.activityInfo.name);
            this.context.startActivityAsUser(intent, UserHandle.CURRENT);
            return true;
        }

        boolean tryStartActivity(Intent intentToStart) {
            if (this.packageManager.queryIntentActivitiesAsUser(intentToStart, 0, ActivityManager.getCurrentUser()).size() <= 0) {
                return NfcDispatcher.DBG;
            }
            this.rootIntent.putExtra("launchIntent", intentToStart);
            this.context.startActivityAsUser(this.rootIntent, UserHandle.CURRENT);
            return true;
        }
    }

    static {
        DBG = NfcService.DBG;
    }

    public NfcDispatcher(Context context, HandoverManager handoverManager, boolean provisionOnly) {
        this.ACTION_TRANSACTION_DETECTED = "com.sony.nfc.action.TRANSACTION_DETECTED";
        this.EXTRA_DATA = "com.sony.extra.DATA";
        this.NFC_PERM = NfcController.NFC_CONTROLLER_PERMISSION;
        this.mToast = null;
        this.mContext = context;
        this.mIActivityManager = ActivityManagerNative.getDefault();
        this.mTechListFilters = new RegisteredComponentCache(this.mContext, "android.nfc.action.TECH_DISCOVERED", "android.nfc.action.TECH_DISCOVERED");
        this.mContentResolver = context.getContentResolver();
        this.mHandoverManager = handoverManager;
        synchronized (this) {
            this.mProvisioningOnly = provisionOnly;
        }
        String[] provisionMimes = null;
        if (provisionOnly) {
            try {
                provisionMimes = context.getResources().getStringArray(C0027R.array.provisioning_mime_types);
            } catch (NotFoundException e) {
                provisionMimes = null;
            }
        }
        this.mProvisioningMimes = provisionMimes;
    }

    public synchronized void setForegroundDispatch(PendingIntent intent, IntentFilter[] filters, String[][] techLists) {
        if (DBG) {
            Log.d(TAG, "Set Foreground Dispatch");
        }
        this.mOverrideIntent = intent;
        this.mOverrideFilters = filters;
        this.mOverrideTechLists = techLists;
    }

    public synchronized void disableProvisioningMode() {
        this.mProvisioningOnly = DBG;
    }

    public boolean handleInputValidatioin(DispatchInfo dispatch, NdefMessage msg) {
        if (!CscFeature.getInstance().getEnableStatus("CscFeature_NFC_EnableInvalidTagPopup")) {
            return DBG;
        }
        if (msg == null) {
            return DBG;
        }
        short tnf = msg.getRecords()[0].getTnf();
        byte[] type = msg.getRecords()[0].getType();
        boolean checkValidation = DBG;
        int prefix = -1;
        if (tnf == (short) 1) {
            if (Arrays.equals(type, NdefRecord.RTD_URI)) {
                prefix = msg.getRecords()[0].getPayload()[0] & -1;
                checkValidation = true;
            } else if (Arrays.equals(type, NdefRecord.RTD_SMART_POSTER)) {
                try {
                    for (NdefRecord nestedRecord : new NdefMessage(msg.getRecords()[0].getPayload()).getRecords()) {
                        if (nestedRecord.getTnf() == (short) 1 && Arrays.equals(nestedRecord.getType(), NdefRecord.RTD_URI)) {
                            prefix = nestedRecord.getPayload()[0] & -1;
                            checkValidation = true;
                            break;
                        }
                    }
                } catch (FormatException e) {
                    checkValidation = DBG;
                } catch (NullPointerException e2) {
                    checkValidation = DBG;
                }
            }
        }
        if (checkValidation) {
            if (prefix < 0 || prefix > 35) {
                Log.d(TAG, "Invalid Uri Tag");
                dispatch.rootIntent.putExtra(NfcRootActivity.EXTRA_INVALID_TAG, true);
                return true;
            } else if (prefix == 5 && dispatch.ndefUri != null) {
                String telUri = dispatch.ndefUri.toString();
                String phoneNumber = telUri.substring(4, telUri.length());
                for (int i = 0; i < phoneNumber.length(); i++) {
                    if (VALID_PHONE_NUMBER.indexOf(phoneNumber.substring(i, i + 1)) == -1) {
                        Log.d(TAG, "Invalid phone number tag");
                        dispatch.rootIntent.putExtra(NfcRootActivity.EXTRA_INVALID_TAG, true);
                        this.mContext.startActivityAsUser(dispatch.rootIntent, UserHandle.CURRENT);
                        return true;
                    }
                }
            }
        }
        return DBG;
    }

    public boolean handleSecurityPopup(DispatchInfo dispatch, NdefMessage msg) {
        String popup = CscFeature.getInstance().getString("CscFeature_NFC_EnableSecurityPromptPopup");
        if (popup == null || popup.length() == 0) {
            return DBG;
        }
        if (msg == null) {
            return DBG;
        }
        if (popup.toLowerCase().contains("all") || popup.toLowerCase().contains("contact")) {
            NdefRecord record = msg.getRecords()[0];
            byte[] payload = record.getPayload();
            String type = new String(record.getType(), Charset.forName("UTF8"));
            if ("text/x-vcard".equalsIgnoreCase(type) || "text/vcard".equals(type)) {
                String endLine = "";
                try {
                    BufferedReader mReader = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(payload), Charset.forName("UTF8")));
                    String line = mReader.readLine();
                    if (line == null || !line.trim().equalsIgnoreCase("BEGIN:VCARD")) {
                        if (DBG) {
                            Log.d(TAG, "Not found BEGIN:VCARD at first line");
                        }
                        dispatch.rootIntent.putExtra(NfcRootActivity.EXTRA_INVALID_TAG, true);
                        this.mContext.startActivityAsUser(dispatch.rootIntent, UserHandle.CURRENT);
                        return true;
                    }
                    dispatch.intent.putExtra("mFNname", "");
                    for (line = mReader.readLine(); line != null; line = mReader.readLine()) {
                        String[] strArray = line.split(":", 2);
                        if (strArray[0].trim().contains("TEL")) {
                            dispatch.intent.putExtra("mFNname", strArray[1]);
                            if (DBG) {
                                Log.d(TAG, "phoneNumber : " + strArray[1]);
                            }
                        }
                        endLine = line;
                    }
                    if (!endLine.trim().equalsIgnoreCase("END:VCARD")) {
                        if (DBG) {
                            Log.d(TAG, "Not found END:VCARD at last line");
                        }
                        dispatch.rootIntent.putExtra(NfcRootActivity.EXTRA_INVALID_TAG, true);
                        this.mContext.startActivityAsUser(dispatch.rootIntent, UserHandle.CURRENT);
                        return true;
                    }
                } catch (IOException e) {
                }
            }
        }
        return DBG;
    }

    boolean isUnSupportedTag(DispatchInfo dispatch, Tag tag) {
        if (this.mContext.getPackageManager().hasSystemFeature("com.nxp.mifare")) {
            return DBG;
        }
        String popup = CscFeature.getInstance().getString("CscFeature_NFC_EnableSecurityPromptPopup", "mifareclassic");
        if (popup.toLowerCase().contains("all") || popup.toLowerCase().contains("mifareclassic")) {
            if (!tag.hasTech(3)) {
                short[] unSupportedTagTechnology = new short[]{(short) 1, (short) 8, (short) 9, (short) 16, (short) 17, (short) 24, (short) 40, (short) 56, (short) 136};
                NfcA a = NfcA.get(tag);
                if (a == null) {
                    return DBG;
                }
                short sak = a.getSak();
                try {
                    a.close();
                } catch (IOException e) {
                    Log.e(TAG, "Error closing");
                }
                for (short s : unSupportedTagTechnology) {
                    if (sak == s) {
                        if (this.mToast != null) {
                            this.mToast.cancel();
                            Log.d(TAG, "isUnSupportedTag : mToast cancel !!");
                        }
                        this.mToast = Toast.makeText(this.mContext, C0027R.string.unsupport_mifare_msg, 0);
                        this.mToast.show();
                        return true;
                    }
                }
                return DBG;
            } else if (!DBG) {
                return DBG;
            } else {
                Log.d(TAG, "isUnSupportedTag : This tag also supports ISO_DEP Tech");
                return DBG;
            }
        } else if (!DBG) {
            return DBG;
        } else {
            Log.d(TAG, "isUnsupportedTag : This feaure is not set");
            return DBG;
        }
    }

    public boolean dispatchTag(Tag tag) {
        NdefMessage message = null;
        Ndef ndef = Ndef.get(tag);
        if (ndef != null) {
            message = ndef.getCachedNdefMessage();
        }
        if (DBG) {
            Log.d(TAG, "dispatch tag: " + tag.toString() + " message: " + message);
        }
        DispatchInfo dispatch = new DispatchInfo(this.mContext, tag, message);
        synchronized (this) {
            IntentFilter[] overrideFilters = this.mOverrideFilters;
            PendingIntent overrideIntent = this.mOverrideIntent;
            String[][] overrideTechLists = this.mOverrideTechLists;
            boolean provisioningOnly = this.mProvisioningOnly;
        }
        resumeAppSwitches();
        isUnSupportedTag(dispatch, tag);
        if (handleInputValidatioin(dispatch, message)) {
            return DBG;
        }
        if (handleSecurityPopup(dispatch, message)) {
            return DBG;
        }
        if (tryOverrides(dispatch, tag, message, overrideIntent, overrideFilters, overrideTechLists)) {
            return true;
        }
        if (!provisioningOnly && this.mHandoverManager.tryHandover(message)) {
            if (DBG) {
                Log.i(TAG, "matched BT HANDOVER");
            }
            return true;
        } else if (tryNdef(dispatch, message, provisioningOnly)) {
            return true;
        } else {
            if (provisioningOnly) {
                return DBG;
            }
            if (tryTech(dispatch, tag)) {
                return true;
            }
            dispatch.setTagIntent();
            if (dispatch.tryStartActivity()) {
                if (DBG) {
                    Log.i(TAG, "matched TAG");
                }
                return true;
            }
            if (DBG) {
                Log.i(TAG, "no match");
            }
            return DBG;
        }
    }

    boolean tryOverrides(DispatchInfo dispatch, Tag tag, NdefMessage message, PendingIntent overrideIntent, IntentFilter[] overrideFilters, String[][] overrideTechLists) {
        if (overrideIntent == null) {
            return DBG;
        }
        Intent intent;
        if (message != null) {
            intent = dispatch.setNdefIntent();
            if (intent != null) {
                if (isFilterMatch(intent, overrideFilters, overrideTechLists != null ? true : DBG)) {
                    try {
                        overrideIntent.send(this.mContext, -1, intent);
                        if (DBG) {
                            Log.i(TAG, "matched NDEF override");
                        }
                        return true;
                    } catch (CanceledException e) {
                        return DBG;
                    }
                }
            }
        }
        intent = dispatch.setTechIntent();
        if (isTechMatch(tag, overrideTechLists)) {
            try {
                overrideIntent.send(this.mContext, -1, intent);
                if (DBG) {
                    Log.i(TAG, "matched TECH override");
                }
                return true;
            } catch (CanceledException e2) {
                return DBG;
            }
        }
        boolean z;
        intent = dispatch.setTagIntent();
        if (overrideTechLists != null) {
            z = true;
        } else {
            z = DBG;
        }
        if (!isFilterMatch(intent, overrideFilters, z)) {
            return DBG;
        }
        try {
            overrideIntent.send(this.mContext, -1, intent);
            if (DBG) {
                Log.i(TAG, "matched TAG override");
            }
            return true;
        } catch (CanceledException e3) {
            return DBG;
        }
    }

    boolean isFilterMatch(Intent intent, IntentFilter[] filters, boolean hasTechFilter) {
        if (filters != null) {
            for (IntentFilter filter : filters) {
                if (filter.match(this.mContentResolver, intent, DBG, TAG) >= 0) {
                    return true;
                }
            }
        } else if (!hasTechFilter) {
            return true;
        }
        return DBG;
    }

    boolean isTechMatch(Tag tag, String[][] techLists) {
        if (techLists == null) {
            return DBG;
        }
        String[] tagTechs = tag.getTechList();
        Arrays.sort(tagTechs);
        for (String[] filterTechs : techLists) {
            if (filterMatch(tagTechs, filterTechs)) {
                return true;
            }
        }
        return DBG;
    }

    /* JADX WARNING: inconsistent code. */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    boolean tryNdef(com.android.nfc.NfcDispatcher.DispatchInfo r25, android.nfc.NdefMessage r26, boolean r27) {
        /*
        r24 = this;
        if (r26 != 0) goto L_0x0005;
    L_0x0002:
        r21 = 0;
    L_0x0004:
        return r21;
    L_0x0005:
        r12 = r25.setNdefIntent();
        if (r12 != 0) goto L_0x000e;
    L_0x000b:
        r21 = 0;
        goto L_0x0004;
    L_0x000e:
        if (r27 == 0) goto L_0x0036;
    L_0x0010:
        r0 = r24;
        r0 = r0.mProvisioningMimes;
        r21 = r0;
        if (r21 == 0) goto L_0x002c;
    L_0x0018:
        r0 = r24;
        r0 = r0.mProvisioningMimes;
        r21 = r0;
        r21 = java.util.Arrays.asList(r21);
        r22 = r12.getType();
        r21 = r21.contains(r22);
        if (r21 != 0) goto L_0x0036;
    L_0x002c:
        r21 = "NfcDispatcher";
        r22 = "Dropping NFC intent in provisioning mode.";
        android.util.Log.e(r21, r22);
        r21 = 0;
        goto L_0x0004;
    L_0x0036:
        r6 = r26.getRecords();
        r13 = r6.length;
        r11 = 0;
    L_0x003c:
        if (r11 >= r13) goto L_0x007c;
    L_0x003e:
        r18 = r6[r11];
        r21 = r18.getTnf();
        r22 = 4;
        r0 = r21;
        r1 = r22;
        if (r0 != r1) goto L_0x0070;
    L_0x004c:
        r21 = r18.getType();
        r22 = "samsung.com:facebook";
        r22 = r22.getBytes();
        r21 = java.util.Arrays.equals(r21, r22);
        if (r21 == 0) goto L_0x0070;
    L_0x005c:
        r21 = r25.tryStartActivity();
        if (r21 == 0) goto L_0x0079;
    L_0x0062:
        r21 = DBG;
        if (r21 == 0) goto L_0x006d;
    L_0x0066:
        r21 = "NfcDispatcher";
        r22 = "found facebook record";
        android.util.Log.i(r21, r22);
    L_0x006d:
        r21 = 1;
        goto L_0x0004;
    L_0x0070:
        r21 = r18.getTnf();
        if (r21 != 0) goto L_0x0079;
    L_0x0076:
        r21 = 0;
        goto L_0x0004;
    L_0x0079:
        r11 = r11 + 1;
        goto L_0x003c;
    L_0x007c:
        r4 = extractAarPackages(r26);
        r11 = r4.iterator();
    L_0x0084:
        r21 = r11.hasNext();
        if (r21 == 0) goto L_0x00b0;
    L_0x008a:
        r15 = r11.next();
        r15 = (java.lang.String) r15;
        r0 = r25;
        r0 = r0.intent;
        r21 = r0;
        r0 = r21;
        r0.setPackage(r15);
        r21 = r25.tryStartActivity();
        if (r21 == 0) goto L_0x0084;
    L_0x00a1:
        r21 = DBG;
        if (r21 == 0) goto L_0x00ac;
    L_0x00a5:
        r21 = "NfcDispatcher";
        r22 = "matched AAR to NDEF";
        android.util.Log.i(r21, r22);
    L_0x00ac:
        r21 = 1;
        goto L_0x0004;
    L_0x00b0:
        r3 = extractSamsungPackages(r26);
        r11 = r3.iterator();
    L_0x00b8:
        r21 = r11.hasNext();
        if (r21 == 0) goto L_0x0123;
    L_0x00be:
        r15 = r11.next();
        r15 = (java.lang.String) r15;
        r21 = "com.sec.android.homesync";
        r0 = r21;
        r21 = r15.equals(r0);
        if (r21 != 0) goto L_0x00b8;
    L_0x00ce:
        r0 = r25;
        r0 = r0.intent;
        r21 = r0;
        r0 = r21;
        r0.setPackage(r15);
        r21 = r25.tryStartActivity();
        if (r21 == 0) goto L_0x00ea;
    L_0x00df:
        r21 = "NfcDispatcher";
        r22 = "String matched SAR to NDEF";
        android.util.Log.i(r21, r22);
        r21 = 1;
        goto L_0x0004;
    L_0x00ea:
        r21 = new java.lang.StringBuilder;
        r21.<init>();
        r0 = r21;
        r21 = r0.append(r15);
        r22 = "stub";
        r21 = r21.append(r22);
        r21 = r21.toString();
        r0 = r25;
        r1 = r21;
        r21 = r0.tryStartActivity(r1);
        if (r21 == 0) goto L_0x00b8;
    L_0x0109:
        r0 = r24;
        r1 = r26;
        r7 = r0.extractBtAddr(r1);
        if (r7 == 0) goto L_0x0118;
    L_0x0113:
        r0 = r24;
        r0.saveBtAddr(r15, r7);
    L_0x0118:
        r21 = "NfcDispatcher";
        r22 = "String matched SAR with stub to NDEF";
        android.util.Log.i(r21, r22);
        r21 = 1;
        goto L_0x0004;
    L_0x0123:
        r0 = r24;
        r1 = r26;
        r7 = r0.extractBtAddr(r1);
        if (r7 == 0) goto L_0x0144;
    L_0x012d:
        r21 = r4.size();
        if (r21 <= 0) goto L_0x01da;
    L_0x0133:
        r21 = 0;
        r0 = r21;
        r16 = r4.get(r0);
        r16 = (java.lang.String) r16;
        r0 = r24;
        r1 = r16;
        r0.saveBtAddr(r1, r7);
    L_0x0144:
        r21 = r3.size();
        if (r21 <= 0) goto L_0x023e;
    L_0x014a:
        r21 = 0;
        r0 = r21;
        r10 = r3.get(r0);
        r10 = (java.lang.String) r10;
        r20 = "";
        r8 = new android.os.UserHandle;	 Catch:{ NameNotFoundException -> 0x0200 }
        r21 = android.app.ActivityManager.getCurrentUser();	 Catch:{ NameNotFoundException -> 0x0200 }
        r0 = r21;
        r8.<init>(r0);	 Catch:{ NameNotFoundException -> 0x0200 }
        r0 = r24;
        r0 = r0.mContext;	 Catch:{ NameNotFoundException -> 0x0200 }
        r21 = r0;
        r22 = "android";
        r23 = 0;
        r0 = r21;
        r1 = r22;
        r2 = r23;
        r21 = r0.createPackageContextAsUser(r1, r2, r8);	 Catch:{ NameNotFoundException -> 0x0200 }
        r17 = r21.getPackageManager();	 Catch:{ NameNotFoundException -> 0x0200 }
        r21 = DBG;
        if (r21 == 0) goto L_0x0197;
    L_0x017d:
        r21 = "NfcDispatcher";
        r22 = new java.lang.StringBuilder;
        r22.<init>();
        r23 = "firstPackage Name : ";
        r22 = r22.append(r23);
        r0 = r22;
        r22 = r0.append(r10);
        r22 = r22.toString();
        android.util.Log.i(r21, r22);
    L_0x0197:
        r0 = r24;
        r1 = r26;
        r20 = r0.HaveHomesyncAPK(r10, r1);
        r0 = r17;
        r5 = r0.getLaunchIntentForPackage(r10);
        if (r5 == 0) goto L_0x020c;
    L_0x01a7:
        r21 = r20.length();
        r22 = 2;
        r0 = r21;
        r1 = r22;
        if (r0 <= r1) goto L_0x01c3;
    L_0x01b3:
        r21 = "NfcDispatcher";
        r22 = "appLaunchIntent.putExtra(bt_addr, strAddr);";
        android.util.Log.i(r21, r22);
        r21 = "bt_addr";
        r0 = r21;
        r1 = r20;
        r5.putExtra(r0, r1);
    L_0x01c3:
        r0 = r25;
        r21 = r0.tryStartActivity(r5);
        if (r21 == 0) goto L_0x020c;
    L_0x01cb:
        r21 = DBG;
        if (r21 == 0) goto L_0x01d6;
    L_0x01cf:
        r21 = "NfcDispatcher";
        r22 = "matched SamsungPackages to application launch";
        android.util.Log.i(r21, r22);
    L_0x01d6:
        r21 = 1;
        goto L_0x0004;
    L_0x01da:
        r21 = r3.size();
        if (r21 <= 0) goto L_0x01f3;
    L_0x01e0:
        r21 = 0;
        r0 = r21;
        r16 = r3.get(r0);
        r16 = (java.lang.String) r16;
        r0 = r24;
        r1 = r16;
        r0.saveBtAddr(r1, r7);
        goto L_0x0144;
    L_0x01f3:
        r21 = DBG;
        if (r21 == 0) goto L_0x0144;
    L_0x01f7:
        r21 = "NfcDispatcher";
        r22 = "no matched AAR or SAR";
        android.util.Log.i(r21, r22);
        goto L_0x0144;
    L_0x0200:
        r9 = move-exception;
        r21 = "NfcDispatcher";
        r22 = "Could not create user package context";
        android.util.Log.e(r21, r22);
        r21 = 0;
        goto L_0x0004;
    L_0x020c:
        r19 = getSamsungAppSearchIntent(r10);
        if (r19 == 0) goto L_0x023e;
    L_0x0212:
        r0 = r25;
        r1 = r19;
        r21 = r0.tryStartActivity(r1);
        if (r21 == 0) goto L_0x023e;
    L_0x021c:
        r21 = DBG;
        if (r21 == 0) goto L_0x0227;
    L_0x0220:
        r21 = "NfcDispatcher";
        r22 = "matched SamsungApps to market launch";
        android.util.Log.i(r21, r22);
    L_0x0227:
        r21 = r20.length();
        r22 = 2;
        r0 = r21;
        r1 = r22;
        if (r0 <= r1) goto L_0x023a;
    L_0x0233:
        r0 = r24;
        r1 = r20;
        r0.saveHomeSyncData(r1);
    L_0x023a:
        r21 = 1;
        goto L_0x0004;
    L_0x023e:
        r21 = r4.size();
        if (r21 <= 0) goto L_0x0309;
    L_0x0244:
        r21 = 0;
        r0 = r21;
        r10 = r4.get(r0);
        r10 = (java.lang.String) r10;
        r20 = "";
        r8 = new android.os.UserHandle;	 Catch:{ NameNotFoundException -> 0x02cd }
        r21 = android.app.ActivityManager.getCurrentUser();	 Catch:{ NameNotFoundException -> 0x02cd }
        r0 = r21;
        r8.<init>(r0);	 Catch:{ NameNotFoundException -> 0x02cd }
        r0 = r24;
        r0 = r0.mContext;	 Catch:{ NameNotFoundException -> 0x02cd }
        r21 = r0;
        r22 = "android";
        r23 = 0;
        r0 = r21;
        r1 = r22;
        r2 = r23;
        r21 = r0.createPackageContextAsUser(r1, r2, r8);	 Catch:{ NameNotFoundException -> 0x02cd }
        r17 = r21.getPackageManager();	 Catch:{ NameNotFoundException -> 0x02cd }
        r21 = DBG;
        if (r21 == 0) goto L_0x0291;
    L_0x0277:
        r21 = "NfcDispatcher";
        r22 = new java.lang.StringBuilder;
        r22.<init>();
        r23 = "firstPackage Name : ";
        r22 = r22.append(r23);
        r0 = r22;
        r22 = r0.append(r10);
        r22 = r22.toString();
        android.util.Log.i(r21, r22);
    L_0x0291:
        r0 = r24;
        r1 = r26;
        r20 = r0.HaveHomesyncAPK(r10, r1);
        r0 = r17;
        r5 = r0.getLaunchIntentForPackage(r10);
        if (r5 == 0) goto L_0x02d9;
    L_0x02a1:
        r21 = r20.length();
        r22 = 2;
        r0 = r21;
        r1 = r22;
        if (r0 <= r1) goto L_0x02b6;
    L_0x02ad:
        r21 = "bt_addr";
        r0 = r21;
        r1 = r20;
        r5.putExtra(r0, r1);
    L_0x02b6:
        r0 = r25;
        r21 = r0.tryStartActivity(r5);
        if (r21 == 0) goto L_0x02d9;
    L_0x02be:
        r21 = DBG;
        if (r21 == 0) goto L_0x02c9;
    L_0x02c2:
        r21 = "NfcDispatcher";
        r22 = "matched AAR to application launch";
        android.util.Log.i(r21, r22);
    L_0x02c9:
        r21 = 1;
        goto L_0x0004;
    L_0x02cd:
        r9 = move-exception;
        r21 = "NfcDispatcher";
        r22 = "Could not create user package context";
        android.util.Log.e(r21, r22);
        r21 = 0;
        goto L_0x0004;
    L_0x02d9:
        r14 = getAppSearchIntent(r10);
        if (r14 == 0) goto L_0x0309;
    L_0x02df:
        r0 = r25;
        r21 = r0.tryStartActivity(r14);
        if (r21 == 0) goto L_0x0309;
    L_0x02e7:
        r21 = DBG;
        if (r21 == 0) goto L_0x02f2;
    L_0x02eb:
        r21 = "NfcDispatcher";
        r22 = "matched AAR to market launch";
        android.util.Log.i(r21, r22);
    L_0x02f2:
        r21 = r20.length();
        r22 = 2;
        r0 = r21;
        r1 = r22;
        if (r0 <= r1) goto L_0x0305;
    L_0x02fe:
        r0 = r24;
        r1 = r20;
        r0.saveHomeSyncData(r1);
    L_0x0305:
        r21 = 1;
        goto L_0x0004;
    L_0x0309:
        r0 = r25;
        r0 = r0.intent;
        r21 = r0;
        r22 = 0;
        r21.setPackage(r22);
        r21 = r25.tryStartActivity();
        if (r21 == 0) goto L_0x0329;
    L_0x031a:
        r21 = DBG;
        if (r21 == 0) goto L_0x0325;
    L_0x031e:
        r21 = "NfcDispatcher";
        r22 = "matched NDEF";
        android.util.Log.i(r21, r22);
    L_0x0325:
        r21 = 1;
        goto L_0x0004;
    L_0x0329:
        r21 = 0;
        goto L_0x0004;
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.nfc.NfcDispatcher.tryNdef(com.android.nfc.NfcDispatcher$DispatchInfo, android.nfc.NdefMessage, boolean):boolean");
    }

    String extractBtAddr(NdefMessage message) {
        String btAddr = "";
        NdefRecord firstRecord = message.getRecords()[0];
        String rtn_cmp = new String(firstRecord.getType(), StandardCharsets.US_ASCII);
        if (firstRecord.getTnf() == (short) 2 && rtn_cmp.contains(TYPE_NAME_SEC_OOB)) {
            btAddr = new String(firstRecord.getPayload());
            Log.i(TAG, "extractBtAddr : Bluetooth addr : ");
            if (17 == btAddr.length()) {
                return btAddr;
            }
            Log.i(TAG, "extractBtAddr : invalid btAddr : ");
            return null;
        } else if (!DBG) {
            return null;
        } else {
            Log.i(TAG, "extractBtAddr : failed ");
            return null;
        }
    }

    void saveBtAddr(String pkg_name, String btAddr) {
        Editor ed = this.mContext.getSharedPreferences("bt_addr_list", 0).edit();
        ed.putString(pkg_name, btAddr);
        ed.commit();
    }

    static List<String> extractAarPackages(NdefMessage message) {
        List<String> aarPackages = new LinkedList();
        for (NdefRecord record : message.getRecords()) {
            String pkg = checkForAar(record);
            if (pkg != null) {
                aarPackages.add(pkg);
            }
        }
        return aarPackages;
    }

    static List<String> extractSamsungPackages(NdefMessage message) {
        List<String> SamsungPackages = new LinkedList();
        for (NdefRecord record : message.getRecords()) {
            String pkg = checkForSamsungPackage(record);
            if (pkg != null) {
                SamsungPackages.add(pkg);
            }
        }
        return SamsungPackages;
    }

    String HaveHomesyncAPK(String firstPackage, NdefMessage message) {
        String strAddr = "";
        if (!firstPackage.equals(HOMESYNC_PACKAGE_NAME)) {
            return strAddr;
        }
        strAddr = new String(message.getRecords()[0].getPayload());
        Log.i(TAG, "com.sec.android.homesyncBluetooth addr : " + strAddr);
        return strAddr;
    }

    void saveHomeSyncData(String strAddr) {
        Editor editor = this.mContext.getSharedPreferences("homesync", 0).edit();
        editor.putString("bt_addr", strAddr);
        editor.commit();
    }

    boolean tryTech(DispatchInfo dispatch, Tag tag) {
        dispatch.setTechIntent();
        String[] tagTechs = tag.getTechList();
        Arrays.sort(tagTechs);
        ArrayList<ResolveInfo> matches = new ArrayList();
        List<ComponentInfo> registered = this.mTechListFilters.getComponents();
        try {
            PackageManager pm = this.mContext.createPackageContextAsUser("android", 0, new UserHandle(ActivityManager.getCurrentUser())).getPackageManager();
            for (ComponentInfo info : registered) {
                if (filterMatch(tagTechs, info.techs) && isComponentEnabled(pm, info.resolveInfo) && !matches.contains(info.resolveInfo)) {
                    matches.add(info.resolveInfo);
                }
            }
            if (matches.size() == 1) {
                ResolveInfo info2 = (ResolveInfo) matches.get(0);
                dispatch.intent.setClassName(info2.activityInfo.packageName, info2.activityInfo.name);
                if (dispatch.tryStartActivity()) {
                    if (DBG) {
                        Log.i(TAG, "matched single TECH");
                    }
                    return true;
                }
                dispatch.intent.setComponent(null);
            } else if (matches.size() > 1) {
                Intent intent = new Intent(this.mContext, TechListChooserActivity.class);
                intent.putExtra("android.intent.extra.INTENT", dispatch.intent);
                intent.putParcelableArrayListExtra(TechListChooserActivity.EXTRA_RESOLVE_INFOS, matches);
                if (dispatch.tryStartActivity(intent)) {
                    if (DBG) {
                        Log.i(TAG, "matched multiple TECH");
                    }
                    return true;
                }
            }
            return DBG;
        } catch (NameNotFoundException e) {
            Log.e(TAG, "Could not create user package context");
            return DBG;
        }
    }

    void resumeAppSwitches() {
        try {
            this.mIActivityManager.resumeAppSwitches();
        } catch (RemoteException e) {
        }
    }

    boolean filterMatch(String[] tagTechs, String[] filterTechs) {
        if (filterTechs == null || filterTechs.length == 0) {
            return DBG;
        }
        for (String tech : filterTechs) {
            if (Arrays.binarySearch(tagTechs, tech) < 0) {
                return DBG;
            }
        }
        return true;
    }

    static String checkForAar(NdefRecord record) {
        if (record.getTnf() == (short) 4 && Arrays.equals(record.getType(), NdefRecord.RTD_ANDROID_APP)) {
            return new String(record.getPayload(), StandardCharsets.US_ASCII);
        }
        return null;
    }

    static String checkForSamsungPackage(NdefRecord record) {
        String SamsungPrefix = "samsungapps://ProductDetail/";
        String packageName = null;
        if (record.getTnf() != (short) 1 || !Arrays.equals(record.getType(), NdefRecord.RTD_URI)) {
            return null;
        }
        String payload = new String(record.getPayload());
        if (payload.regionMatches(1, "samsungapps://ProductDetail/", 0, "samsungapps://ProductDetail/".length())) {
            String[] ArStr = payload.split("/");
            if (ArStr.length > 0) {
                packageName = ArStr[ArStr.length - 1];
                Log.d(TAG, "SamsungApp find - name : " + packageName);
            }
        }
        return packageName;
    }

    static Intent getSamsungAppSearchIntent(String pkg) {
        Intent market = new Intent("android.intent.action.VIEW");
        market.setData(Uri.parse("samsungapps://ProductDetail/" + pkg));
        return market;
    }

    static Intent getAppSearchIntent(String pkg) {
        Intent market = new Intent("android.intent.action.VIEW");
        market.setData(Uri.parse("market://details?id=" + pkg));
        return market;
    }

    static boolean isComponentEnabled(PackageManager pm, ResolveInfo info) {
        boolean enabled = DBG;
        ComponentName compname = new ComponentName(info.activityInfo.packageName, info.activityInfo.name);
        try {
            if (pm.getActivityInfo(compname, 0) != null) {
                enabled = true;
            }
        } catch (NameNotFoundException e) {
            enabled = DBG;
        }
        if (!enabled) {
            Log.d(TAG, "Component not enabled: " + compname);
        }
        return enabled;
    }

    void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        synchronized (this) {
            pw.println("mOverrideIntent=" + this.mOverrideIntent);
            pw.println("mOverrideFilters=" + this.mOverrideFilters);
            pw.println("mOverrideTechLists=" + this.mOverrideTechLists);
        }
    }

    public void dispatchConnectivity(byte[] aid, byte[] parameter) {
        if (DBG) {
            Log.d(TAG, "dispatchConnectivity");
        }
        Intent targetIntent = new Intent();
        targetIntent.setAction("com.sony.nfc.action.TRANSACTION_DETECTED");
        Builder builder = new Builder();
        String path = "/" + byte2String(aid);
        builder.scheme(NfcService.SERVICE_NAME);
        builder.encodedAuthority("secure:0");
        builder.path(path);
        targetIntent.setData(builder.build());
        if (DBG) {
            Log.d(TAG, "Intent Uri = " + builder.build().toString());
        }
        targetIntent.putExtra("com.sony.extra.DATA", parameter);
        PackageManager pm = this.mContext.getPackageManager();
        List<ResolveInfo> registered = pm.queryIntentActivities(targetIntent, 65600);
        ArrayList<ResolveInfo> matches = new ArrayList();
        for (ResolveInfo info : registered) {
            if (hasMatchedDataPath(info, path)) {
                String packageName = info.activityInfo.packageName;
                if ((info.match & 5242880) >= 5242880 && pm.checkPermission(NfcController.NFC_CONTROLLER_PERMISSION, packageName) == 0 && !matches.contains(info)) {
                    matches.add(info);
                }
            }
        }
        if (matches.size() > 0) {
            Intent intent = new Intent(this.mContext, TechListChooserActivity.class);
            intent.addFlags(268435456);
            intent.putExtra("android.intent.extra.INTENT", targetIntent);
            intent.putParcelableArrayListExtra(TechListChooserActivity.EXTRA_RESOLVE_INFOS, matches);
            try {
                this.mContext.startActivity(intent);
            } catch (ActivityNotFoundException e) {
                if (DBG) {
                    Log.w(TAG, "No activities for Connectivity handling of " + intent);
                }
            }
        } else if (DBG) {
            Log.w(TAG, "No activities for Connectivity handling");
        }
    }

    private boolean hasMatchedDataPath(ResolveInfo info, String data) {
        if ((info.match & 268369920) < 5242880 || info.filter == null) {
            return DBG;
        }
        Iterator<PatternMatcher> paths = info.filter.pathsIterator();
        if (paths == null) {
            return DBG;
        }
        while (paths.hasNext()) {
            PatternMatcher pe = (PatternMatcher) paths.next();
            if (pe.getType() == 0 && pe.match(data)) {
                return true;
            }
        }
        return DBG;
    }

    private String byte2String(byte[] byteData) {
        if (byteData == null) {
            return null;
        }
        StringBuffer value = new StringBuffer();
        for (int i = 0; i < byteData.length; i++) {
            if ((byteData[i] & 255) < 16) {
                value.append("0" + Integer.toHexString(byteData[i] & 255));
            } else {
                value.append(Integer.toHexString(byteData[i] & 255));
            }
        }
        return value.toString();
    }
}
