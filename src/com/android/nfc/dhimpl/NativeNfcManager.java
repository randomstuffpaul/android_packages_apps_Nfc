package com.android.nfc.dhimpl;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.nfc.ErrorCodes;
import android.os.AsyncTask;
import android.util.Log;
import com.android.nfc.DeviceHost;
import com.android.nfc.DeviceHost.DeviceHostListener;
import com.android.nfc.DeviceHost.LlcpConnectionlessSocket;
import com.android.nfc.DeviceHost.LlcpServerSocket;
import com.android.nfc.DeviceHost.LlcpSocket;
import com.android.nfc.LlcpException;
import com.android.nfc.snep.SnepMessage;
import com.samsung.android.sdk.cover.ScoverState;
import java.util.Arrays;

public class NativeNfcManager implements DeviceHost {
    static final int DEFAULT_LLCP_MIU = 128;
    static final int DEFAULT_LLCP_RWSIZE = 1;
    static final String DRIVER_NAME = "nxp";
    private static final byte[][] EE_WIPE_APDUS;
    private static final long FIRMWARE_MODTIME_DEFAULT = -1;
    private static final int HCE_APPLET_NOT_SELECTED = 3;
    private static final int HCE_APPLET_SELECTED = 2;
    private static final int HCE_APPLET_SELECTING = 1;
    public static final String INTERNAL_TARGET_DESELECTED_ACTION = "com.android.nfc.action.INTERNAL_TARGET_DESELECTED";
    private static final String NFC_CONTROLLER_FIRMWARE_FILE_NAME = "/vendor/firmware/libpn544_fw.so";
    static final String PREF = "NxpDeviceHost";
    private static final String PREF_FIRMWARE_MODTIME = "firmware_modtime";
    private static final String TAG = "NativeNfcManager";
    private static final byte[] presentCheckCmd;
    private int mAppletSelectStatus;
    private final Context mContext;
    private final DeviceHostListener mListener;
    private int mNative;

    final class HceReceiverTask extends AsyncTask<Void, Void, Void> {
        HceReceiverTask() {
        }

        protected Void doInBackground(Void... v) {
            synchronized (NativeNfcManager.this) {
                Log.d(NativeNfcManager.TAG, "Waiting for an APDU...");
                byte[] data = NativeNfcManager.this.doReceiveData();
                Log.d(NativeNfcManager.TAG, "doReceiveData. reutrn..");
                if (data != null) {
                    Log.d(NativeNfcManager.TAG, "calling notifyHostEmuData...");
                    NativeNfcManager.this.notifyHostEmuData(data);
                }
            }
            return null;
        }
    }

    private native void PrbsOff();

    private native void PrbsOn(int i, int i2);

    private native NativeLlcpConnectionlessSocket doCreateLlcpConnectionlessSocket(int i, String str);

    private native NativeLlcpServiceSocket doCreateLlcpServiceSocket(int i, String str, int i2, int i3, int i4);

    private native NativeLlcpSocket doCreateLlcpSocket(int i, int i2, int i3, int i4);

    private native boolean doDeinitialize();

    private native void doDisableReaderMode();

    private native boolean doDownload();

    private native String doDump();

    private native void doEnableReaderMode(int i);

    private native int doGetTimeout(int i);

    private native boolean doInitialize();

    private native byte[] doReceiveData();

    private native void doResetTimeouts();

    private native boolean doSendRawFrame(byte[] bArr);

    private native void doSetFilterTag(int i);

    private native void doSetP2pInitiatorModes(int i);

    private native void doSetP2pTargetModes(int i);

    private native boolean doSetTimeout(int i, int i2);

    public native void clearRouting();

    public native void disableDiscovery();

    public native void disableRoutingToHost();

    public native void doAbort();

    public native boolean doActivateLlcp();

    public native boolean doCheckLlcp();

    public native void doDeselectSecureElement(int i);

    public native int doGetHWVersionInfo();

    public native int doGetLastError();

    public native int[] doGetSecureElementList();

    public native int doGetSecureElementTechList();

    public native int doSWPSelfTest();

    public native void doSelectSecureElement(int i);

    public native void doSetSecureElementListenTechMask(int i);

    public native void enableDiscovery();

    public native void enableRoutingToHost();

    public native boolean initializeNativeStructure();

    public native boolean routeAid(byte[] bArr, int i, int i2);

    public native boolean setDefaultAidRoute(int i);

    public native boolean setDefaultRoute(int i, int i2, int i3);

    static {
        presentCheckCmd = new byte[]{(byte) 0, (byte) -80, (byte) 0, (byte) 0, (byte) 1};
        EE_WIPE_APDUS = new byte[][]{new byte[]{(byte) 0, (byte) -92, (byte) 4, (byte) 0, (byte) 0}, new byte[]{(byte) 0, (byte) -92, (byte) 4, (byte) 0, (byte) 7, (byte) -96, (byte) 0, (byte) 0, (byte) 4, (byte) 118, (byte) 32, SnepMessage.VERSION, (byte) 0}, new byte[]{SnepMessage.RESPONSE_CONTINUE, (byte) -30, (byte) 1, (byte) 3, (byte) 0}, new byte[]{(byte) 0, (byte) -92, (byte) 4, (byte) 0, (byte) 0}, new byte[]{(byte) 0, (byte) -92, (byte) 4, (byte) 0, (byte) 7, (byte) -96, (byte) 0, (byte) 0, (byte) 4, (byte) 118, (byte) 48, (byte) 48, (byte) 0}, new byte[]{SnepMessage.RESPONSE_CONTINUE, (byte) -76, (byte) 0, (byte) 0, (byte) 0}, new byte[]{(byte) 0, (byte) -92, (byte) 4, (byte) 0, (byte) 0}};
        System.loadLibrary("nfc_jni");
    }

    public static boolean doSetPowerMode(int state) {
        return true;
    }

    public NativeNfcManager(Context context, DeviceHostListener listener) {
        this.mListener = listener;
        initializeNativeStructure();
        this.mContext = context;
        this.mAppletSelectStatus = HCE_APPLET_NOT_SELECTED;
    }

    public void checkFirmware() {
    }

    public boolean initialize(boolean nfcc_on) {
        SharedPreferences prefs = this.mContext.getSharedPreferences(PREF, 0);
        Editor editor = prefs.edit();
        if (prefs.getBoolean("se_wired", false)) {
            try {
                Thread.sleep(12000);
                editor.putBoolean("se_wired", false);
                editor.apply();
            } catch (InterruptedException e) {
            }
        }
        return doInitialize();
    }

    public boolean deinitialize() {
        Editor editor = this.mContext.getSharedPreferences(PREF, 0).edit();
        editor.putBoolean("se_wired", false);
        editor.apply();
        return doDeinitialize();
    }

    public String getName() {
        return DRIVER_NAME;
    }

    public boolean sendRawFrame(byte[] data) {
        Log.d(TAG, "mAppletSelectStatus=" + this.mAppletSelectStatus);
        if (this.mAppletSelectStatus == HCE_APPLET_SELECTING) {
            if (data.length > HCE_APPLET_SELECTED && data[data.length - 2] == -112 && data[data.length - 1] == null) {
                this.mAppletSelectStatus = HCE_APPLET_SELECTED;
            } else {
                this.mAppletSelectStatus = HCE_APPLET_NOT_SELECTED;
            }
        }
        boolean result = doSendRawFrame(data);
        if (result) {
            new HceReceiverTask().execute(new Void[0]);
        }
        return result;
    }

    public boolean reRouteAid(byte[] aid, int route, boolean isStopDiscovery, boolean isStartDiscovery) {
        return false;
    }

    public boolean onPpseRouted(boolean onHost, int route) {
        return false;
    }

    public void setDefaultRouteDestinations(int defaultIsoDepRoute, int defaultOffHostRoute) {
    }

    public void setStaticRouteByTech(int technology, boolean screenOn, boolean screenOff, boolean screenLock, int route, boolean switchOn, boolean switchOff, boolean batteryOff) {
    }

    public void setStaticRouteByProto(int protocol, boolean screenOn, boolean screenOff, boolean screenLock, int route, boolean switchOn, boolean switchOff, boolean batteryOff) {
    }

    public void setHceOffHostAidRoute(byte[] aid, boolean screenOn, boolean screenOff, boolean screenLock, int route, boolean switchOn, boolean switchOff, boolean batteryOff) {
    }

    public void removeHceOffHostAidRoute(byte[] aid) {
    }

    public boolean unrouteAid(byte[] aid) {
        return false;
    }

    public void doSetVenConfigValue(int venconfig) {
    }

    public void doSetScreenState(int state) {
    }

    public void setUiccIdleTime(int t) {
    }

    public void enableTech_A(boolean bOn) {
    }

    public LlcpConnectionlessSocket createLlcpConnectionlessSocket(int nSap, String sn) throws LlcpException {
        LlcpConnectionlessSocket socket = doCreateLlcpConnectionlessSocket(nSap, sn);
        if (socket != null) {
            return socket;
        }
        int error = doGetLastError();
        Log.d(TAG, "failed to create llcp socket: " + ErrorCodes.asString(error));
        switch (error) {
            case -12:
            case -9:
                throw new LlcpException(error);
            default:
                throw new LlcpException(-10);
        }
    }

    public LlcpServerSocket createLlcpServerSocket(int nSap, String sn, int miu, int rw, int linearBufferLength) throws LlcpException {
        LlcpServerSocket socket = doCreateLlcpServiceSocket(nSap, sn, miu, rw, linearBufferLength);
        if (socket != null) {
            return socket;
        }
        int error = doGetLastError();
        Log.d(TAG, "failed to create llcp socket: " + ErrorCodes.asString(error));
        switch (error) {
            case -12:
            case -9:
                throw new LlcpException(error);
            default:
                throw new LlcpException(-10);
        }
    }

    public LlcpSocket createLlcpSocket(int sap, int miu, int rw, int linearBufferLength) throws LlcpException {
        LlcpSocket socket = doCreateLlcpSocket(sap, miu, rw, linearBufferLength);
        if (socket != null) {
            return socket;
        }
        int error = doGetLastError();
        Log.d(TAG, "failed to create llcp socket: " + ErrorCodes.asString(error));
        switch (error) {
            case -12:
            case -9:
                throw new LlcpException(error);
            default:
                throw new LlcpException(-10);
        }
    }

    public void resetTimeouts() {
        doResetTimeouts();
    }

    public boolean setTimeout(int tech, int timeout) {
        return doSetTimeout(tech, timeout);
    }

    public int getTimeout(int tech) {
        return doGetTimeout(tech);
    }

    public boolean canMakeReadOnly(int ndefType) {
        return ndefType == HCE_APPLET_SELECTING || ndefType == HCE_APPLET_SELECTED || ndefType == 101;
    }

    public int getMaxTransceiveLength(int technology) {
        switch (technology) {
            case HCE_APPLET_SELECTING /*1*/:
            case HCE_APPLET_SELECTED /*2*/:
            case ScoverState.TYPE_S_CHARGER_COVER /*5*/:
            case ScoverState.COLOR_MINT_BLUE /*8*/:
            case ScoverState.COLOR_MUSTARD_YELLOW /*9*/:
                return 253;
            case HCE_APPLET_NOT_SELECTED /*3*/:
                return 65546;
            case ScoverState.TYPE_HEALTH_COVER /*4*/:
                return 252;
            default:
                return 0;
        }
    }

    public void setP2pInitiatorModes(int modes) {
        doSetP2pInitiatorModes(modes);
    }

    public void setP2pTargetModes(int modes) {
        doSetP2pTargetModes(modes);
    }

    public boolean enableReaderMode(int technologies) {
        doEnableReaderMode(technologies);
        return true;
    }

    public boolean disableReaderMode() {
        doDisableReaderMode();
        return true;
    }

    public boolean getExtendedLengthApdusSupported() {
        if (doGetHWVersionInfo() == 11) {
            return true;
        }
        return false;
    }

    public boolean SetFilterTag(int Enable) {
        doSetFilterTag(Enable);
        return true;
    }

    public boolean enablePN544Quirks() {
        return true;
    }

    public byte[][] getWipeApdus() {
        return EE_WIPE_APDUS;
    }

    public int getDefaultLlcpMiu() {
        return DEFAULT_LLCP_MIU;
    }

    public int getDefaultLlcpRwSize() {
        return HCE_APPLET_SELECTING;
    }

    public String dump() {
        return doDump();
    }

    public int getFWVersion() {
        return 0;
    }

    public int SWPSelfTest(int ch) {
        if (ch == 0) {
            return doSWPSelfTest();
        }
        return 0;
    }

    public void doPrbsOn(int technology, int bitrate) {
        PrbsOn(technology, bitrate);
    }

    public void doPrbsOff() {
        PrbsOff();
    }

    public void commitRouting() {
    }

    public int JCOSDownload() {
        return 0;
    }

    public int getChipVer() {
        return 0;
    }

    public void setDefaultProtoRoute(int seID, int proto_switchon, int proto_switchoff) {
    }

    public void setDefaultTechRoute(int seID, int tech_switchon, int tech_switchoff) {
    }

    public void doSetSEPowerOffState(int seID, boolean enable) {
    }

    public int GetDefaultSE() {
        return 0;
    }

    private void notifyNdefMessageListeners(NativeNfcTag tag) {
        this.mListener.onRemoteEndpointDiscovered(tag);
    }

    private void notifyTargetDeselected() {
        this.mListener.onCardEmulationDeselected();
    }

    private void notifyCardEmulationAidSelected(byte[] aid) {
        this.mListener.onCardEmulationAidSelected4Google(aid);
    }

    private void notifyTransactionListeners(byte[] aid, byte[] data) {
        Log.d(TAG, "NativeNfcManager-notifyTransactionListeners");
        this.mListener.onCardEmulationAidSelected(aid, data);
    }

    private void notifyConnectivityListeners() {
        this.mListener.onConnectivityEvent();
    }

    private void notifyLlcpLinkActivation(NativeP2pDevice device) {
        this.mListener.onLlcpLinkActivated(device);
    }

    private void notifyLlcpLinkDeactivated(NativeP2pDevice device) {
        this.mListener.onLlcpLinkDeactivated(device);
    }

    private void notifySeFieldActivated() {
        this.mListener.onRemoteFieldActivated();
    }

    private void notifySeFieldDeactivated() {
        this.mListener.onRemoteFieldDeactivated();
    }

    private void notifySeApduReceived(byte[] apdu) {
        this.mListener.onSeApduReceived(apdu);
    }

    private void notifySeEmvCardRemoval() {
        this.mListener.onSeEmvCardRemoval();
    }

    private void notifySeMifareAccess(byte[] block) {
        this.mListener.onSeMifareAccess(block);
    }

    private void notifyHostEmuActivated() {
        new HceReceiverTask().execute(new Void[0]);
        this.mListener.onHostCardEmulationActivated();
    }

    private void notifyHostEmuData(byte[] data) {
        if (!Arrays.equals(data, presentCheckCmd) || this.mAppletSelectStatus == HCE_APPLET_SELECTED) {
            if (data.length > HCE_APPLET_SELECTED && data[HCE_APPLET_SELECTING] == 164 && data[HCE_APPLET_SELECTED] == 4) {
                this.mAppletSelectStatus = HCE_APPLET_SELECTING;
            }
            Log.d(TAG, "calling onHostCardEmulationData...");
            this.mListener.onHostCardEmulationData(data);
            return;
        }
        byte[] errorRsp = new byte[]{(byte) 109, (byte) 0};
        Log.d(TAG, "send error rsp...");
        sendRawFrame(errorRsp);
    }

    private void notifyHostEmuDeactivated() {
        this.mAppletSelectStatus = HCE_APPLET_NOT_SELECTED;
        this.mListener.onHostCardEmulationDeactivated();
    }

    public void doSetEEPROM(byte[] val) {
    }

    public void routToSecureElement(int seId) {
    }

    public int getAidTableSize() {
        return 0;
    }

    public boolean clearAidTable() {
        return true;
    }
}
