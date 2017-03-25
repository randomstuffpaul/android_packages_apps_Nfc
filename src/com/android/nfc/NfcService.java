package com.android.nfc;

import android.app.ActivityManager;
import android.app.Application;
import android.app.KeyguardManager;
import android.app.PendingIntent;
import android.app.StatusBarManager;
import android.app.admin.DevicePolicyManager;
import android.app.enterprise.EnterpriseDeviceManager;
import android.app.enterprise.devicesettings.DeviceSettingsPolicy;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources.NotFoundException;
import android.media.SoundPool;
import android.net.Uri;
import android.nfc.FormatException;
import android.nfc.IAppCallback;
import android.nfc.INfcAdapter;
import android.nfc.INfcAdapterExtras;
import android.nfc.INfcCardEmulation;
import android.nfc.INfcSecureElement;
import android.nfc.INfcSetting;
import android.nfc.INfcSetting.Stub;
import android.nfc.INfcTag;
import android.nfc.INfcUtility;
import android.nfc.INfcUtilityCallback;
import android.nfc.NdefMessage;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.TechListParcel;
import android.nfc.TransceiveResult;
import android.nfc.cardemulation.ApduServiceInfo;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.DeadObjectException;
import android.os.Debug;
import android.os.FactoryTest;
import android.os.Handler;
import android.os.IBinder;
import android.os.IBinder.DeathRecipient;
import android.os.Message;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.Process;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.os.Vibrator;
import android.provider.Settings.Global;
import android.provider.Settings.Secure;
import android.provider.Settings.System;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.util.Pair;
import android.widget.Toast;
import com.android.nfc.DeviceHost.DeviceHostListener;
import com.android.nfc.DeviceHost.LlcpConnectionlessSocket;
import com.android.nfc.DeviceHost.LlcpServerSocket;
import com.android.nfc.DeviceHost.LlcpSocket;
import com.android.nfc.DeviceHost.NfcDepEndpoint;
import com.android.nfc.DeviceHost.TagEndpoint;
import com.android.nfc.broadcom.NativeNfcBrcmPowerMode;
import com.android.nfc.broadcom.NfcFactory;
import com.android.nfc.cardemulation.AidRoutingManager;
import com.android.nfc.cardemulation.CardEmulationRoutingManager;
import com.android.nfc.cardemulation.HostEmulationManager;
import com.android.nfc.cardemulation.RegisteredAidCache;
import com.android.nfc.cardemulation.TapAgainDialog;
import com.android.nfc.dhimpl.NativeNfcManager;
import com.android.nfc.dhimpl.NativeNfcSecureElement;
import com.android.nfc.handover.HandoverManager;
import com.android.nfc.handover.HandoverService.Device;
import com.android.nfc.sony.NativeNfcSetting;
import com.android.nfc.sony.NativeNfcUtility;
import com.gsma.services.nfc.ICallbacks;
import com.gsma.services.nfc.INfcController;
import com.gsma.services.nfc.ISEController;
import com.samsung.android.sdk.SsdkUnsupportedException;
import com.samsung.android.sdk.cover.Scover;
import com.samsung.android.sdk.cover.ScoverManager;
import com.samsung.android.sdk.cover.ScoverManager.StateListener;
import com.samsung.android.sdk.cover.ScoverState;
import com.sec.android.app.CscFeature;
import com.sony.jp.android.nfccontrol.INfcControlService;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutionException;

public class NfcService implements DeviceHostListener {
    public static final String ACTION_AID_SELECTED = "com.android.nfc_extras.action.AID_SELECTED";
    public static final String ACTION_APDU_RECEIVED = "com.android.nfc_extras.action.APDU_RECEIVED";
    public static final String ACTION_CLEAR_COVER_OPEN = "com.samsung.cover.NFC.OPEN";
    public static final String ACTION_CONNECTIVITY_EVENT_DETECTED = "com.nxp.action.CONNECTIVITY_EVENT_DETECTED";
    public static final String ACTION_EMV_CARD_REMOVAL = "com.android.nfc_extras.action.EMV_CARD_REMOVAL";
    public static final String ACTION_ISIS_TRANSACTION_DETECTED = "com.paywithisis.action.TRANSACTION_DETECTED";
    public static final String ACTION_LLCP_DOWN = "com.android.nfc.action.LLCP_DOWN";
    public static final String ACTION_LLCP_UP = "com.android.nfc.action.LLCP_UP";
    private static final String ACTION_MASTER_CLEAR_NOTIFICATION = "android.intent.action.MASTER_CLEAR_NOTIFICATION";
    public static final String ACTION_MIFARE_ACCESS_DETECTED = "com.android.nfc_extras.action.MIFARE_ACCESS_DETECTED";
    private static final String ACTION_NFC_DEVICE_POLICY_MANAGER_STATE_CHANGED = "com.android.nfc.action.DEVICE_POLICY_MANAGER_STATE_CHANGED";
    private static final String ACTION_NFC_UART_ABNORMAL = "com.android.nfc.UART_ABNORMAL";
    public static final String ACTION_RF_FIELD_OFF_DETECTED = "com.android.nfc_extras.action.RF_FIELD_OFF_DETECTED";
    public static final String ACTION_RF_FIELD_ON_DETECTED = "com.android.nfc_extras.action.RF_FIELD_ON_DETECTED";
    public static final String ACTION_SE_LISTEN_ACTIVATED = "com.android.nfc_extras.action.SE_LISTEN_ACTIVATED";
    public static final String ACTION_SE_LISTEN_DEACTIVATED = "com.android.nfc_extras.action.SE_LISTEN_DEACTIVATED";
    public static final String ACTION_TRANSACTION_DETECTED = "com.nxp.action.TRANSACTION_DETECTED";
    private static final String ADMIN_PERM = "android.permission.WRITE_SECURE_SETTINGS";
    private static final String ADMIN_PERM_ERROR = "WRITE_SECURE_SETTINGS permission required";
    public static final int ALL_SE_ID_TYPE = 3;
    private static final int CEN_HIGH = 1;
    private static final int CEN_LOW = 0;
    public static final String CHECK_SEC_NFC_ESE = "com.sec.android.app.nfctest.NFC_CHECK_ESE";
    public static final String CHECK_SEC_NFC_ESE_RESP = "com.sec.android.app.nfctest.NFC_CHECK_ESE_RESPONSE";
    public static final String CHECK_SEC_NFC_SIM = "com.sec.android.app.nfctest.NFC_CHECK_SIM";
    public static final String CHECK_SEC_NFC_SIM_RESP = "com.sec.android.app.nfctest.NFC_CHECK_SIM_RESPONSE";
    public static final String CHECK_SIM_DATA = "SIM_DATA";
    static final boolean DBG;
    static final int DEFAULT_PRESENCE_CHECK_DELAY = 125;
    public static final String DISABLE_SEC_NFC_DISCOVERY = "com.sec.android.app.nfctest.NFC_DISCOVERY_DISABLE";
    public static final String EEPROM_SET = "com.sec.android.app.nfctest.EEPROM_SET";
    static final int EE_ERROR_ALREADY_OPEN = -2;
    static final int EE_ERROR_EXT_FIELD = -5;
    static final int EE_ERROR_INIT = -3;
    static final int EE_ERROR_IO = -1;
    static final int EE_ERROR_LISTEN_MODE = -4;
    static final int EE_ERROR_NFC_DISABLED = -6;
    public static final String ENABLE_SEC_NFC_DISCOVERY = "com.sec.android.app.nfctest.NFC_DISCOVERY_ENABLE";
    public static final String END_SEC_NFC_TEST_CMD = "com.sec.android.app.nfctest.NFC_TEST_END";
    public static final String ESE_TYPE = "ESE_TYPE";
    public static final String EXTRA_AID = "com.android.nfc_extras.extra.AID";
    public static final String EXTRA_APDU_BYTES = "com.android.nfc_extras.extra.APDU_BYTES";
    public static final String EXTRA_DATA = "com.android.nfc_extras.extra.DATA";
    public static final String EXTRA_ISIS_AID = "paywithisis.nfc.extra.AID";
    public static final String EXTRA_ISIS_DATA = "paywithisis.nfc.extra.DATA";
    public static final String EXTRA_MIFARE_BLOCK = "com.android.nfc_extras.extra.MIFARE_BLOCK";
    public static final String FILEPATH_FELICA_CEN = "/dev/snfc_cen";
    public static final String FW_VERSION = "FW_VERSION";
    public static final String GET_ESE_TYPE = "com.sec.android.app.nfctest.GET_ESE_TYPE";
    public static final String GET_ESE_TYPE_RESPONSE = "com.sec.android.app.nfctest.GET_ESE_TYPE_RESPONSE";
    public static final String GET_FW_VERSION = "com.sec.android.app.nfctest.GET_FW_VERSION";
    public static final String GET_FW_VERSION_RESPONSE = "com.sec.android.app.nfctest.GET_FW_VERSION_RESPONSE";
    public static final String HCE_DEVICE_HOST_NAME = "DH";
    public static final int HCE_DH_ID = 0;
    static final int INIT_WATCHDOG_MS = 90000;
    private static final String INTERNAL_LOCKSTATUS_CHANGED_ACTION = "com.samsung.felica.action.LOCKSTATUS_CHANGED";
    static final int MSG_ACTIVE_NFC_ICON = 103;
    static final int MSG_CARD_EMULATION = 1;
    static final int MSG_CARD_EMULATION_AID_SELECTED = 19;
    static final int MSG_CHN_ENABLE_CANCEL = 203;
    static final int MSG_CHN_ENABLE_DIRECT = 202;
    static final int MSG_CHN_ENABLE_POPUP = 201;
    static final int MSG_CLEAR_ROUTING = 150;
    static final int MSG_COMMIT_ROUTING = 18;
    static final int MSG_CONNECTIVITY_EVENT = 40;
    static final int MSG_ENABLE_ROUTE_HOST = 111;
    static final int MSG_HCI_EVT_TRANSACTION = 20;
    static final int MSG_INACTIVE_NFC_ICON = 104;
    static final int MSG_LLCP_LINK_ACTIVATION = 2;
    static final int MSG_LLCP_LINK_DEACTIVATED = 3;
    static final int MSG_LLCP_LINK_FIRST_PACKET = 15;
    static final int MSG_MOCK_NDEF = 7;
    static final int MSG_NDEF_TAG = 0;
    static final int MSG_PPSE_ROUTED = 112;
    static final int MSG_ROUTE_AID = 16;
    static final int MSG_SET_SCREEN_STATE = 151;
    static final int MSG_SE_APDU_RECEIVED = 10;
    static final int MSG_SE_EMV_CARD_REMOVAL = 11;
    static final int MSG_SE_FIELD_ACTIVATED = 8;
    static final int MSG_SE_FIELD_DEACTIVATED = 9;
    static final int MSG_SE_LISTEN_ACTIVATED = 13;
    static final int MSG_SE_LISTEN_DEACTIVATED = 14;
    static final int MSG_SE_MIFARE_ACCESS = 12;
    static final int MSG_TARGET_DESELECTED = 4;
    static final int MSG_UNROUTE_AID = 17;
    static final int MSG_UPDATE_SE = 110;
    static final boolean NDEF_PUSH_ON_DEFAULT = true;
    public static final String NFCCONTROLLER_SERVICE_NAME = "nfccontroller";
    public static final String NFC_DISCOVER_INIT_ACTION = "com.felicanetworks.mfm.action.NFC_DISCOVER_INIT";
    public static final String NFC_DISCOVER_START_ACTION = "com.felicanetworks.mfm.action.NFC_DISCOVER_START";
    public static final String NFC_DISCOVER_STOP_ACTION = "com.felicanetworks.mfm.action.NFC_DISCOVER_STOP";
    static boolean NFC_ON_DEFAULT = false;
    private static final boolean NFC_ON_READER_DEFAULT = false;
    private static final String NFC_PERM = "android.permission.NFC";
    private static final String NFC_PERM_ERROR = "NFC permission required";
    private static int NFC_READER_BLANK = 0;
    private static int NFC_READER_OFF = 0;
    private static int NFC_READER_ON = 0;
    public static final String NO_DISCOVERY_SEC_NFC_ON = "com.sec.android.app.nfctest.NFC_ON_NO_DISCOVERY";
    private static String NfcStateChangeKey = null;
    private static String NfcStateULockKey = null;
    public static final int PN65T_ID = 2;
    static final int POLLING_MODE = 3;
    public static final String PRBS_TEST_OFF = "com.sec.android.app.nfctest.PRBS_TEST_OFF";
    public static final String PRBS_TEST_ON = "com.sec.android.app.nfctest.PRBS_TEST_ON";
    public static final String PREF = "NfcServicePrefs";
    public static final String PREFS_DEFAULT_ROUTE_SETTING = "defaultRouteDest";
    public static final String PREFS_ROUTE_TO_DEFAULT = "routeToDefault";
    static final String PREF_AIRPLANE_OVERRIDE = "airplane_override";
    private static final String PREF_DEFAULT_ROUTE_ID = "default_route_id";
    static final String PREF_FIRST_BEAM = "first_beam";
    static final String PREF_FIRST_BOOT = "first_boot";
    static final String PREF_NDEF_PUSH_ON = "ndef_push_on";
    static final String PREF_NFC_ON = "nfc_on";
    private static final String PREF_NFC_READER_ON = "nfc_reader_on";
    private static final String PREF_SECURE_ELEMENT_ID = "secure_element_id";
    private static final String PREF_SECURE_ELEMENT_ON = "secure_element_on";
    public static final int ROUTE_OFF = 1;
    public static final int ROUTE_ON_ALWAYS = 5;
    public static final int ROUTE_ON_WHEN_POWER_ON = 4;
    public static final int ROUTE_ON_WHEN_SCREEN_ON = 2;
    public static final int ROUTE_ON_WHEN_SCREEN_UNLOCK = 3;
    static final int ROUTING_WATCHDOG_MS = 10000;
    static final int SCREEN_STATE_OFF = 1;
    static final int SCREEN_STATE_ON_LOCKED = 2;
    static final int SCREEN_STATE_ON_UNLOCKED = 3;
    static final int SCREEN_STATE_UNKNOWN = 0;
    public static final String SECONTROLLER_SERVICE_NAME = "secontroller";
    public static int SECURE_ELEMENT_ESE_ID = 0;
    public static final String SECURE_ELEMENT_ESE_NAME = "ESE";
    public static int SECURE_ELEMENT_UICC_ID = 0;
    public static final String SECURE_ELEMENT_UICC_NAME = "UICC";
    public static final String SERVICE_NAME = "nfc";
    public static final String SET_ESE_TYPE = "com.sec.android.app.nfctest.SET_ESE_TYPE";
    static final boolean SE_BROADCASTS_WITH_HCE = true;
    public static int SMART_MX_ID_TYPE = 0;
    public static final int SOUND_END = 1;
    public static final int SOUND_ERROR = 2;
    public static final int SOUND_START = 0;
    public static final String START_SEC_NFC_TEST_CMD = "com.sec.android.app.nfctest.NFC_TEST_START";
    private static final int STATE_ACTIVATE = 2;
    private static final int STATE_IDLE = 1;
    private static final int STATE_ROUTING_CHANGE = 3;
    static final String TAG = "NfcService";
    static final int TASK_BOOT = 3;
    static final int TASK_CHN_ENABLE_CANCEL = 102;
    static final int TASK_CHN_ENABLE_DIRECT = 101;
    static final int TASK_DISABLE = 2;
    static final int TASK_EE_WIPE = 4;
    static final int TASK_ENABLE = 1;
    static final int TASK_READER_DISABLE = 6;
    static final int TASK_READER_ENABLE = 5;
    static final int TASK_ROUTING_CHANGED = 121;
    public static final String UICC_IDLE_TIME = "com.sec.android.app.nfctest.UICC_IDLE_TIME";
    public static int UICC_ID_TYPE = 0;
    public static final int VEN_CFG_NFC_OFF_POWER_OFF = 2;
    public static final int VEN_CFG_NFC_ON_POWER_ON = 3;
    static final int WAIT_FOR_NFCEE_OPERATIONS_MS = 5000;
    static final int WAIT_FOR_NFCEE_POLL_MS = 100;
    static int handle;
    public static boolean isVzw;
    private static boolean mEnableSwpProactiveCommand;
    private static String mHideTerminal;
    public static boolean mIsSecNdefEnabled;
    public static String mProductName;
    private static String mSecureEventType;
    private static boolean mStopDiscoveryDuringCall;
    static boolean menu_split;
    private static NfcService sService;
    private int DEFAULT_ROUTE_ID_DEFAULT;
    private int SECURE_ELEMENT_ID_DEFAULT;
    private boolean SECURE_ELEMENT_ON_DEFAULT;
    private boolean beforeFeliCaLockState;
    private int ibeforeFeliCaLockState;
    private boolean isClosed;
    private boolean isFirstInit;
    private boolean isGsmaApiSupported;
    private boolean isOpened;
    private RegisteredAidCache mAidCache;
    private AidRoutingManager mAidRoutingManager;
    private NfcFactory mBrcmFactory;
    private NativeNfcBrcmPowerMode mBrcmPowerMode;
    private INfcUtilityCallback mCallback;
    private CardEmulationRoutingManager mCardEmulationRoutingManager;
    CardEmulationService mCardEmulationService;
    boolean mChnEnablePopupExist;
    boolean mChnEnablePopupFromAirplaneOn;
    private ContentResolver mContentResolver;
    Context mContext;
    private ScoverManager mCoverManager;
    private DeviceHost mDeviceHost;
    private int mEeRoutingState;
    private WakeLock mEeWakeLock;
    int mEndSound;
    int mErrorSound;
    private int mEseRoutingMode;
    NfcAdapterExtrasService mExtrasService;
    private INfcControlService mFeliCa;
    private final BroadcastReceiver mFeliCaLockReceiver;
    private NfcServiceHandler mHandler;
    private HandoverManager mHandoverManager;
    private int mHceRoutingState;
    HciEventControl mHciEventControl;
    private HostEmulationManager mHostEmulationManager;
    boolean mHostRouteEnabled;
    boolean mInProvisionMode;
    List<PackageInfo> mInstalledPackages;
    boolean mIsAirplaneSensitive;
    boolean mIsAirplaneToggleable;
    boolean mIsDebugBuild;
    private boolean mIsDefaultApkForHost;
    boolean mIsHceCapable;
    private boolean mIsLock;
    boolean mIsNdefPushEnabled;
    public boolean mIsRouteForced;
    boolean mIsRoutingTableDirty;
    private KeyguardManager mKeyguard;
    int mLastScreenState;
    private NativeNfcSetting mNativeNfcSetting;
    NfcAdapterService mNfcAdapter;
    private NfcAdapter mNfcAdapterDev;
    NfcControllerService mNfcControllerService;
    private NfcDispatcher mNfcDispatcher;
    private boolean[] mNfcEventsPermissionResults;
    private long mNfcEventsResultCacheTime;
    boolean mNfcHceRouteEnabled;
    boolean mNfcPollingEnabled;
    private boolean mNfcSecureElementState;
    private final Stub mNfcSetting;
    TagService mNfcTagService;
    NativeNfcUtility mNfcUtility;
    NfceeAccessControl mNfceeAccessControl;
    boolean mNfceeRouteEnabled;
    private boolean mNoDiscoveryNfcOn;
    final HashMap<Integer, Object> mObjectMap;
    private OpenSecureElement mOpenEe;
    final HashMap<Integer, OpenSecureElement> mOpenEeMap;
    private boolean mOpenSmxPending;
    private final BroadcastReceiver mOwnerReceiver;
    P2pLinkManager mP2pLinkManager;
    boolean mP2pStarted;
    private String[] mPackagesWithNfcPermission;
    private boolean mPollingLoopStarted;
    private PowerManager mPowerManager;
    boolean mPowerShutDown;
    private SharedPreferences mPrefs;
    private Editor mPrefsEditor;
    private final ReaderModeDeathRecipient mReaderModeDeathRecipient;
    boolean mReaderModeEnabled;
    ReaderModeParams mReaderModeParams;
    private final BroadcastReceiver mReceiver;
    private WakeLock mRoutingWakeLock;
    SEControllerService mSEControllerService;
    private SamsungPreference mSamsungPref;
    private Scover mScover;
    private ScoverState mScoverState;
    int mScreenState;
    private int mSeActivationState;
    HashSet<String> mSePackages;
    private NativeNfcSecureElement mSecureElement;
    private int mSecureElementHandle;
    NfcSecureElementService mSecureElementService;
    private int mSelectedSeId;
    SoundPool mSoundPool;
    int mStartSound;
    int mState;
    private StateListener mStateListener;
    private boolean mTestMode;
    private final BroadcastReceiver mTestReceiver;
    private Timer mTimerOpenSmx;
    ToastHandler mToastHandler;
    private int mUiccRoutingMode;
    private ServiceConnection nfcControlServiceConnection;

    /* renamed from: com.android.nfc.NfcService.1 */
    class C00151 implements StateListener {
        C00151() {
        }

        public void onCoverStateChanged(ScoverState state) {
            NfcService.this.mScoverState = state;
            if (NfcService.this.mScoverState.getSwitchState() == NfcService.SE_BROADCASTS_WITH_HCE) {
                Log.i(NfcService.TAG, "Hello Cover  : onCoverStateChanged - State Open");
                Intent intent = new Intent(NfcService.ACTION_CLEAR_COVER_OPEN);
                intent.putExtra("coverOpen", NfcService.SE_BROADCASTS_WITH_HCE);
                NfcService.this.mContext.sendBroadcast(intent);
                return;
            }
            Log.i(NfcService.TAG, "Hello Cover  : onCoverStateChanged - State Close");
            intent = new Intent(NfcService.ACTION_CLEAR_COVER_OPEN);
            intent.putExtra("coverOpen", NfcService.NFC_ON_READER_DEFAULT);
            NfcService.this.mContext.sendBroadcast(intent);
        }
    }

    /* renamed from: com.android.nfc.NfcService.2 */
    class C00162 extends BroadcastReceiver {
        C00162() {
        }

        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals("android.intent.action.PACKAGE_REMOVED") || action.equals("android.intent.action.PACKAGE_ADDED") || action.equals("android.intent.action.EXTERNAL_APPLICATIONS_AVAILABLE") || action.equals("android.intent.action.EXTERNAL_APPLICATIONS_UNAVAILABLE")) {
                NfcService.this.updatePackageCache();
                if (action.equals("android.intent.action.PACKAGE_REMOVED")) {
                    NfcService.this.mNfceeAccessControl.invalidateCache();
                    if (intent.getBooleanExtra("android.intent.extra.DATA_REMOVED", NfcService.NFC_ON_READER_DEFAULT)) {
                        Uri data = intent.getData();
                        if (data != null) {
                            String packageName = data.getSchemeSpecificPart();
                            synchronized (NfcService.this) {
                                if (NfcService.this.mSePackages.contains(packageName)) {
                                    AsyncTask enableDisableTask = new EnableDisableTask();
                                    Integer[] numArr = new Integer[NfcService.TASK_ENABLE];
                                    numArr[NfcService.SOUND_START] = Integer.valueOf(NfcService.TASK_EE_WIPE);
                                    enableDisableTask.execute(numArr);
                                    NfcService.this.mSePackages.remove(packageName);
                                }
                            }
                        }
                    }
                }
            } else if (action.equals(NfcService.ACTION_MASTER_CLEAR_NOTIFICATION)) {
                EnableDisableTask eeWipeTask = new EnableDisableTask();
                Integer[] numArr2 = new Integer[NfcService.TASK_ENABLE];
                numArr2[NfcService.SOUND_START] = Integer.valueOf(NfcService.TASK_EE_WIPE);
                eeWipeTask.execute(numArr2);
                try {
                    eeWipeTask.get();
                } catch (ExecutionException e) {
                    Log.w(NfcService.TAG, "failed to wipe NFC-EE");
                } catch (InterruptedException e2) {
                    Log.w(NfcService.TAG, "failed to wipe NFC-EE");
                }
            } else if (action.equals("android.intent.action.SIM_STATE_CHANGED")) {
                String simState = intent.getStringExtra("ss");
                Log.d(NfcService.TAG, "SIM_STATE_CHANGED[state]: " + simState);
                if (!NfcService.this.isNfcEnabled()) {
                    return;
                }
                if ("LOCKED".equals(simState) || "READY".equals(simState)) {
                    Log.d(NfcService.TAG, "Exception handling, doAbort");
                    NfcService.this.mDeviceHost.doAbort();
                }
            }
        }
    }

    /* renamed from: com.android.nfc.NfcService.3 */
    class C00173 extends BroadcastReceiver {
        C00173() {
        }

        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            String iconType = CscFeature.getInstance().getString("CscFeature_NFC_StatusBarIconType");
            if (action.equals("android.intent.action.ACTION_SHUTDOWN")) {
                if ("BCM2079x".equals("NXP_PN544C3")) {
                    Log.i(NfcService.TAG, "action : " + action + " will be ignored");
                } else if ("NXP_PN547C2".equals("NXP_PN544C3") || "S3FNRN3".equals("NXP_PN544C3") || "S3FWRN5".equals("NXP_PN544C3")) {
                    NfcService.this.mPowerShutDown = NfcService.SE_BROADCASTS_WITH_HCE;
                    Log.d(NfcService.TAG, "Device is shutting down.");
                }
            }
            if (action.equals(NativeNfcManager.INTERNAL_TARGET_DESELECTED_ACTION)) {
                new ApplyRoutingTask().execute(new Integer[NfcService.SOUND_START]);
            } else if ("android.intent.action.SETTINGS_SOFT_RESET".equals(action)) {
                r8 = new EnableDisableTask();
                r9 = new Integer[NfcService.TASK_ENABLE];
                r9[NfcService.SOUND_START] = Integer.valueOf(NfcService.VEN_CFG_NFC_OFF_POWER_OFF);
                r8.execute(r9);
                NfcService.this.mPrefsEditor.clear();
                if (NfcService.this.mPrefsEditor.commit()) {
                    Log.d(NfcService.TAG, "NFC SOFT RESET");
                    NfcService.this.mIsNdefPushEnabled = NfcService.SE_BROADCASTS_WITH_HCE;
                    r8 = new EnableDisableTask();
                    r9 = new Integer[NfcService.TASK_ENABLE];
                    r9[NfcService.SOUND_START] = Integer.valueOf(NfcService.VEN_CFG_NFC_ON_POWER_ON);
                    r8.execute(r9);
                }
            } else if (action.equals("android.intent.action.SCREEN_ON") || action.equals("android.intent.action.SCREEN_OFF") || action.equals("android.intent.action.USER_PRESENT") || action.equals(NfcService.ACTION_CLEAR_COVER_OPEN) || action.equals("android.intent.action.CONFIGURATION_CHANGED")) {
                int screenState = NfcService.TASK_ENABLE;
                if (action.equals("android.intent.action.SCREEN_OFF")) {
                    screenState = NfcService.TASK_ENABLE;
                } else if (action.equals("android.intent.action.SCREEN_ON")) {
                    NfcService.this.mScoverState = NfcService.this.mCoverManager.getCoverState();
                    if (NfcService.this.mScoverState == null) {
                        Log.i(NfcService.TAG, "NFC: not supported Device");
                        screenState = NfcService.this.mKeyguard.isKeyguardLocked() ? NfcService.VEN_CFG_NFC_OFF_POWER_OFF : NfcService.VEN_CFG_NFC_ON_POWER_ON;
                    } else {
                        if (NfcService.this.mScoverState.getSwitchState() == NfcService.SE_BROADCASTS_WITH_HCE ? NfcService.SE_BROADCASTS_WITH_HCE : NfcService.NFC_ON_READER_DEFAULT) {
                            Log.i(NfcService.TAG, "NFC: Screen On & Cover Open");
                            screenState = NfcService.this.mKeyguard.isKeyguardLocked() ? NfcService.VEN_CFG_NFC_OFF_POWER_OFF : NfcService.VEN_CFG_NFC_ON_POWER_ON;
                        } else {
                            Log.i(NfcService.TAG, "NFC: Screen On & Cover Close");
                            screenState = NfcService.TASK_ENABLE;
                        }
                    }
                } else if (action.equals(NfcService.ACTION_CLEAR_COVER_OPEN)) {
                    if (intent.getBooleanExtra("coverOpen", NfcService.NFC_ON_READER_DEFAULT)) {
                        Log.i(NfcService.TAG, "NFC: You opened S View Cover.");
                        screenState = NfcService.this.mKeyguard.isKeyguardLocked() ? NfcService.VEN_CFG_NFC_OFF_POWER_OFF : NfcService.VEN_CFG_NFC_ON_POWER_ON;
                        action = "android.intent.action.SCREEN_ON";
                    } else {
                        Log.i(NfcService.TAG, "NFC: You closed S View Cover!!");
                        screenState = NfcService.TASK_ENABLE;
                        action = "android.intent.action.SCREEN_OFF";
                    }
                } else if (action.equals("android.intent.action.USER_PRESENT")) {
                    if (NfcService.this.checkScreenState() == NfcService.TASK_ENABLE) {
                        Log.i(NfcService.TAG, "NFC: Screen Off!! But receive ACTION_USER_PRESENT");
                        return;
                    }
                    screenState = NfcService.VEN_CFG_NFC_ON_POWER_ON;
                }
                if ("NXP_PN544C3".equals("NXP_PN544C3")) {
                    NfcService.this.mHostEmulationManager.setScreenState(screenState);
                }
                if ("BCM2079x".equals("NXP_PN544C3")) {
                    switch (screenState) {
                        case NfcService.TASK_ENABLE /*1*/:
                            NfcService.this.mBrcmPowerMode.setPowerMode(NfcService.VEN_CFG_NFC_OFF_POWER_OFF);
                            break;
                        case NfcService.VEN_CFG_NFC_OFF_POWER_OFF /*2*/:
                            NfcService.this.mBrcmPowerMode.setPowerMode(NfcService.VEN_CFG_NFC_ON_POWER_ON);
                            break;
                        case NfcService.VEN_CFG_NFC_ON_POWER_ON /*3*/:
                            NfcService.this.mBrcmPowerMode.setPowerMode(NfcService.TASK_EE_WIPE);
                            break;
                    }
                }
                if ("Vzw".equalsIgnoreCase(iconType)) {
                    if (screenState != NfcService.VEN_CFG_NFC_ON_POWER_ON) {
                        NfcService.this.setIcon(NfcService.NFC_ON_READER_DEFAULT);
                    } else if (NfcService.this.mState == NfcService.VEN_CFG_NFC_ON_POWER_ON) {
                        NfcService.this.setIcon(NfcService.SE_BROADCASTS_WITH_HCE);
                    } else if (NfcService.this.mState == NfcService.TASK_ENABLE) {
                        NfcService.this.setIcon(NfcService.NFC_ON_READER_DEFAULT);
                    }
                }
                if (screenState == NfcService.VEN_CFG_NFC_OFF_POWER_OFF && Global.getInt(NfcService.this.mContext.getContentResolver(), "device_provisioned", NfcService.SOUND_START) == 0) {
                    if (NfcService.DBG) {
                        Log.d(NfcService.TAG, "Return unlock screen state for LocalFota");
                    }
                    screenState = NfcService.VEN_CFG_NFC_ON_POWER_ON;
                }
                if (!NfcService.mStopDiscoveryDuringCall || screenState == NfcService.TASK_ENABLE || TelephonyManager.getDefault().getCallState() == 0) {
                    r8 = new ApplyRoutingTask();
                    r9 = new Integer[NfcService.TASK_ENABLE];
                    r9[NfcService.SOUND_START] = Integer.valueOf(screenState);
                    r8.execute(r9);
                }
            } else if (action.equals("android.intent.action.AIRPLANE_MODE")) {
                boolean isAirplaneModeOn = intent.getBooleanExtra("state", NfcService.NFC_ON_READER_DEFAULT);
                if (isAirplaneModeOn == NfcService.this.isAirplaneModeOn() && NfcService.this.mIsAirplaneSensitive) {
                    NfcService.this.mPrefsEditor.putBoolean(NfcService.PREF_AIRPLANE_OVERRIDE, NfcService.NFC_ON_READER_DEFAULT);
                    NfcService.this.mPrefsEditor.apply();
                    if (isAirplaneModeOn) {
                        r8 = new EnableDisableTask();
                        r9 = new Integer[NfcService.TASK_ENABLE];
                        r9[NfcService.SOUND_START] = Integer.valueOf(NfcService.VEN_CFG_NFC_OFF_POWER_OFF);
                        r8.execute(r9);
                    } else if (!isAirplaneModeOn && NfcService.this.mPrefs.getBoolean(NfcService.PREF_NFC_ON, NfcService.NFC_ON_DEFAULT)) {
                        NfcService.this.mChnEnablePopupFromAirplaneOn = NfcService.SE_BROADCASTS_WITH_HCE;
                        r8 = new EnableDisableTask();
                        r9 = new Integer[NfcService.TASK_ENABLE];
                        r9[NfcService.SOUND_START] = Integer.valueOf(NfcService.TASK_ENABLE);
                        r8.execute(r9);
                    }
                }
            } else if (action.equals("android.intent.action.USER_SWITCHED")) {
                int userId = intent.getIntExtra("android.intent.extra.user_handle", NfcService.SOUND_START);
                if (userId >= NfcService.WAIT_FOR_NFCEE_POLL_MS) {
                    if (NfcService.DBG) {
                        Log.i(NfcService.TAG, "It's Knox Mode");
                    }
                    r8 = new EnableDisableTask();
                    r9 = new Integer[NfcService.TASK_ENABLE];
                    r9[NfcService.SOUND_START] = Integer.valueOf(NfcService.VEN_CFG_NFC_OFF_POWER_OFF);
                    r8.execute(r9);
                } else if (userId != 0 || intent.getIntExtra("old_user_id", NfcService.SOUND_START) < NfcService.WAIT_FOR_NFCEE_POLL_MS) {
                    if (NfcService.DBG) {
                        Log.i(NfcService.TAG, "It's AOSP Mode");
                    }
                    NfcService.this.mP2pLinkManager.onUserSwitched();
                    if (NfcService.this.mIsHceCapable) {
                        NfcService.this.mAidCache.invalidateCache(intent.getIntExtra("android.intent.extra.user_handle", NfcService.SOUND_START));
                    }
                } else {
                    if (NfcService.DBG) {
                        Log.i(NfcService.TAG, "It's From Knox to OWNER Mode");
                    }
                    if (NfcService.this.mPrefs.getBoolean(NfcService.PREF_NFC_ON, NfcService.NFC_ON_DEFAULT)) {
                        r8 = new EnableDisableTask();
                        r9 = new Integer[NfcService.TASK_ENABLE];
                        r9[NfcService.SOUND_START] = Integer.valueOf(NfcService.TASK_ENABLE);
                        r8.execute(r9);
                        return;
                    }
                    r8 = new EnableDisableTask();
                    r9 = new Integer[NfcService.TASK_ENABLE];
                    r9[NfcService.SOUND_START] = Integer.valueOf(NfcService.VEN_CFG_NFC_OFF_POWER_OFF);
                    r8.execute(r9);
                }
            } else if (intent.getAction().equals("android.intent.action.PHONE_STATE")) {
                if (!NfcService.mStopDiscoveryDuringCall || !NfcService.this.mPowerManager.isScreenOn()) {
                    return;
                }
                if (TelephonyManager.getDefault().getCallState() == 0) {
                    Log.i(NfcService.TAG, "Start Discovery after call");
                    NfcService.this.mScreenState = NfcService.this.checkScreenState();
                    new ApplyRoutingTask().execute(new Integer[NfcService.SOUND_START]);
                    return;
                }
                Log.i(NfcService.TAG, "Stop Discovery during call");
                NfcService.this.mScreenState = NfcService.SOUND_START;
                new ApplyRoutingTask().execute(new Integer[NfcService.SOUND_START]);
            } else if (action.equals("android.app.action.DEVICE_POLICY_MANAGER_STATE_CHANGED") && !NfcService.this.isAllowWifiByDevicePolicy(context)) {
                Log.d(NfcService.TAG, "mDPMReceiver : apply eas policy");
                NfcService.this.mContext.sendBroadcast(new Intent(NfcService.ACTION_NFC_DEVICE_POLICY_MANAGER_STATE_CHANGED));
            }
        }
    }

    /* renamed from: com.android.nfc.NfcService.4 */
    class C00184 extends BroadcastReceiver {
        C00184() {
        }

        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(NfcService.START_SEC_NFC_TEST_CMD)) {
                Log.d(NfcService.TAG, "Start Factory Test");
                NfcService.this.mTestMode = NfcService.SE_BROADCASTS_WITH_HCE;
                NfcService.this.mNoDiscoveryNfcOn = NfcService.NFC_ON_READER_DEFAULT;
            } else if (action.equals(NfcService.END_SEC_NFC_TEST_CMD)) {
                Log.d(NfcService.TAG, "END Factory Test");
                NfcService.this.mTestMode = NfcService.NFC_ON_READER_DEFAULT;
                NfcService.this.mNoDiscoveryNfcOn = NfcService.NFC_ON_READER_DEFAULT;
                if ("NXP_PN547C2".equals("NXP_PN544C3")) {
                    Log.d(NfcService.TAG, "NFC No Action");
                } else {
                    new ApplyRoutingTask().execute(new Integer[NfcService.SOUND_START]);
                }
            } else if (action.equals(NfcService.ENABLE_SEC_NFC_DISCOVERY)) {
                Log.d(NfcService.TAG, "Enable Discovery");
                NfcService.this.mScreenState = NfcService.VEN_CFG_NFC_ON_POWER_ON;
                NfcService.this.mDeviceHost.enableDiscovery();
            } else if (action.equals(NfcService.DISABLE_SEC_NFC_DISCOVERY)) {
                Log.d(NfcService.TAG, "Disable Discovery");
                if (!"NXP_PN544C3".equals("NXP_PN544C3") || NfcService.this.isNfcEnabled()) {
                    NfcService.this.mDeviceHost.disableDiscovery();
                } else {
                    Log.d(NfcService.TAG, "Ignore Disable-Discovery event if nfc is off");
                }
            } else if (action.equals(NfcService.NO_DISCOVERY_SEC_NFC_ON)) {
                Log.d(NfcService.TAG, "Disable Discovery When NFC ON");
                NfcService.this.mNoDiscoveryNfcOn = NfcService.SE_BROADCASTS_WITH_HCE;
            } else if (action.equals(NfcService.CHECK_SEC_NFC_SIM)) {
                Log.d(NfcService.TAG, "check SIM I/O");
                status = NfcService.this.mDeviceHost.SWPSelfTest(NfcService.SOUND_START);
                Log.d(NfcService.TAG, "SWP TEST Result:" + status);
                respIntent = new Intent(NfcService.CHECK_SEC_NFC_SIM_RESP);
                respIntent.putExtra(NfcService.CHECK_SIM_DATA, status);
                NfcService.this.mContext.sendBroadcast(respIntent);
            } else if (action.equals(NfcService.CHECK_SEC_NFC_ESE)) {
                Log.d(NfcService.TAG, "check eSE I/O");
                status = NfcService.this.mDeviceHost.SWPSelfTest(NfcService.TASK_ENABLE);
                Log.d(NfcService.TAG, "ESE TEST Result:" + status);
                respIntent = new Intent(NfcService.CHECK_SEC_NFC_ESE_RESP);
                respIntent.putExtra(NfcService.CHECK_SIM_DATA, status);
                NfcService.this.mContext.sendBroadcast(respIntent);
            } else if (action.equals(NfcService.PRBS_TEST_ON)) {
                Log.d(NfcService.TAG, "PRBS_ON");
                disableTask = new EnableDisableTask();
                r8 = new Integer[NfcService.TASK_ENABLE];
                r8[NfcService.SOUND_START] = Integer.valueOf(NfcService.VEN_CFG_NFC_OFF_POWER_OFF);
                disableTask.execute(r8);
                try {
                    disableTask.get();
                    if ("NXP_PN547C2".equals("NXP_PN544C3")) {
                        NfcService.this.mDeviceHost.initialize(NfcService.SE_BROADCASTS_WITH_HCE);
                    }
                    NfcService.this.mDeviceHost.doPrbsOn(intent.getIntExtra("TECH", NfcService.SOUND_START), intent.getIntExtra("RATE", NfcService.SOUND_START));
                } catch (Exception e) {
                    Log.d(NfcService.TAG, "failed to prbsOff");
                }
            } else if (action.equals(NfcService.PRBS_TEST_OFF)) {
                Log.d(NfcService.TAG, "PRBS_OFF");
                NfcService.this.mDeviceHost.doPrbsOff();
                enableTask = new EnableDisableTask();
                r8 = new Integer[NfcService.TASK_ENABLE];
                r8[NfcService.SOUND_START] = Integer.valueOf(NfcService.TASK_ENABLE);
                enableTask.execute(r8);
                try {
                    enableTask.get();
                } catch (Exception e2) {
                    Log.d(NfcService.TAG, "failed to prbsOff");
                }
            } else if (action.equals(NfcService.GET_FW_VERSION)) {
                Log.d(NfcService.TAG, "GET_FW_VERSION");
                int version = NfcService.this.mDeviceHost.getFWVersion();
                respIntent = new Intent(NfcService.GET_FW_VERSION_RESPONSE);
                respIntent.putExtra(NfcService.FW_VERSION, version);
                NfcService.this.mContext.sendBroadcast(respIntent);
            } else if (action.equals(NfcService.EEPROM_SET)) {
                Log.d(NfcService.TAG, "EEPROM_SET");
            } else if (action.equals(NfcService.GET_ESE_TYPE)) {
                int ese_type = NfcService.this.mDeviceHost.doGetSecureElementTechList();
                Log.d(NfcService.TAG, "GET_ESE_TYPE : " + ese_type);
                respIntent = new Intent(NfcService.GET_ESE_TYPE_RESPONSE);
                respIntent.putExtra(NfcService.ESE_TYPE, ese_type);
                NfcService.this.mContext.sendBroadcast(respIntent);
            } else if (action.equals(NfcService.SET_ESE_TYPE)) {
                Log.d(NfcService.TAG, "SET_ESE_TYPE");
                NfcService.this.mDeviceHost.doSetSecureElementListenTechMask(intent.getIntExtra(NfcService.ESE_TYPE, NfcService.SOUND_START));
            } else if (action.equals(NfcService.UICC_IDLE_TIME) && "BCM2079x".equals("NXP_PN544C3")) {
                Log.d(NfcService.TAG, "UICC_IDLE_TIME");
                Log.d(NfcService.TAG, "--------------before disable ---------------");
                disableTask = new EnableDisableTask();
                r8 = new Integer[NfcService.TASK_ENABLE];
                r8[NfcService.SOUND_START] = Integer.valueOf(NfcService.VEN_CFG_NFC_OFF_POWER_OFF);
                disableTask.execute(r8);
                try {
                    disableTask.get();
                } catch (Exception e3) {
                }
                Log.d(NfcService.TAG, "--------------after disable ---------------");
                Log.d(NfcService.TAG, "--------------before enable ---------------");
                enableTask = new EnableDisableTask();
                r8 = new Integer[NfcService.TASK_ENABLE];
                r8[NfcService.SOUND_START] = Integer.valueOf(NfcService.TASK_ENABLE);
                enableTask.execute(r8);
                try {
                    enableTask.get();
                } catch (Exception e4) {
                }
                Log.d(NfcService.TAG, "--------------after enable ---------------");
                NfcService.this.mDeviceHost.setUiccIdleTime(2147483646);
            }
        }
    }

    /* renamed from: com.android.nfc.NfcService.5 */
    class C00195 extends BroadcastReceiver {
        C00195() {
        }

        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if ("NXP_PN544C3".equals("CXD2235BGG")) {
                if (NfcService.DBG) {
                    Log.d(NfcService.TAG, "mFeliCalockReceiver Intent receive");
                }
                if (action.equals(NfcService.NFC_DISCOVER_START_ACTION)) {
                    if (NfcService.DBG) {
                        Log.d(NfcService.TAG, "mFeliCaLockReceiver NFC_DISCOVER_START");
                    }
                    NfcService.this.mScreenState = NfcService.this.checkScreenState();
                    AsyncTask enableDisableTask = new EnableDisableTask();
                    Integer[] numArr = new Integer[NfcService.TASK_ENABLE];
                    numArr[NfcService.SOUND_START] = Integer.valueOf(NfcService.TASK_ENABLE);
                    enableDisableTask.execute(numArr);
                } else if (action.equals(NfcService.NFC_DISCOVER_STOP_ACTION)) {
                    if (NfcService.DBG) {
                        Log.d(NfcService.TAG, "mFeliCaLockReceiver NFC_DISCOVER_STOP");
                    }
                    NfcService.this.mScreenState = NfcService.this.checkScreenState();
                    EnableDisableTask lockdisableTask = new EnableDisableTask();
                    Integer[] numArr2 = new Integer[NfcService.TASK_ENABLE];
                    numArr2[NfcService.SOUND_START] = Integer.valueOf(NfcService.VEN_CFG_NFC_OFF_POWER_OFF);
                    lockdisableTask.execute(numArr2);
                    try {
                        lockdisableTask.get();
                    } catch (Exception e) {
                        Log.d(NfcService.TAG, "mFeliCaLockReceiver: NFC disable failed");
                    }
                } else if (action.equals(NfcService.NFC_DISCOVER_INIT_ACTION)) {
                    boolean currNfcState = NfcService.this.isNfcEnabled();
                    if (NfcService.this.ibeforeFeliCaLockState == NfcService.EE_ERROR_IO) {
                        NfcService.this.beforeFeliCaLockState = currNfcState;
                        NfcService.this.ibeforeFeliCaLockState = NfcService.TASK_ENABLE;
                    }
                } else if (action.equals(NfcService.INTERNAL_LOCKSTATUS_CHANGED_ACTION)) {
                    NfcService.this.mNfcAdapterDev = NfcAdapter.getDefaultAdapter(context);
                    Log.e(NfcService.TAG, "LOCKSTATUS_CHANGED_ACTION!!!");
                    if (NfcService.this.getLockStatefromDevice() == NfcService.TASK_ENABLE) {
                        NfcService.this.mNfcUtility.waitSimBoot(NfcService.this.mCallback, NfcService.this.mIsLock);
                        boolean booltmp = NfcService.this.mDeviceHost.initialize(NfcService.SE_BROADCASTS_WITH_HCE);
                        NfcService.this.nfcAdapterEnableDisable(context, NfcService.this.readNFcPrefsULock(context));
                    }
                }
            }
        }
    }

    /* renamed from: com.android.nfc.NfcService.6 */
    class C00206 implements ServiceConnection {
        C00206() {
        }

        public void onServiceConnected(ComponentName name, IBinder service) {
            if (NfcService.DBG) {
                Log.d(NfcService.TAG, "onServiceConnected()");
            }
            NfcService.this.mFeliCa = INfcControlService.Stub.asInterface(service);
        }

        public void onServiceDisconnected(ComponentName name) {
            if (NfcService.DBG) {
                Log.d(NfcService.TAG, "onServiceDisconnected()");
            }
            if (NfcService.this.mFeliCa != null) {
                NfcService.this.mFeliCa = null;
            }
        }
    }

    /* renamed from: com.android.nfc.NfcService.7 */
    class C00217 extends Stub {
        C00217() {
        }

        public boolean setParameter(int index, int value) {
            if (NfcService.DBG) {
                Log.d("INfcSetting", "setParameter : index = " + index + ", value = " + value);
            }
            if (NfcService.this.mNativeNfcSetting == null) {
                return NfcService.NFC_ON_READER_DEFAULT;
            }
            return NfcService.this.mNativeNfcSetting.setParameter(index, value);
        }

        public boolean changeParameter(int target) {
            if (NfcService.this.mNativeNfcSetting == null) {
                return NfcService.NFC_ON_READER_DEFAULT;
            }
            return NfcService.this.mNativeNfcSetting.changeParameter(target);
        }
    }

    class ApplyRoutingTask extends AsyncTask<Integer, Void, Void> {
        ICallbacks callback;

        public ApplyRoutingTask() {
            this.callback = null;
        }

        public ApplyRoutingTask(ICallbacks cb) {
            this.callback = null;
            if (NfcService.this.isGsmaApiSupported) {
                this.callback = cb;
            }
        }

        protected Void doInBackground(Integer... params) {
            synchronized (NfcService.this) {
                if (NfcService.this.isGsmaApiSupported && this.callback != null) {
                    if (NfcService.DBG) {
                        Log.e(NfcService.TAG, "GSMA mNfcSecureElementState: " + NfcService.this.mNfcSecureElementState);
                    }
                    if (NfcService.DBG) {
                        Log.e(NfcService.TAG, "GSMA mEeRoutingState: " + NfcService.this.mEeRoutingState);
                    }
                    if (NfcService.DBG) {
                        Log.e(NfcService.TAG, "GSMA mSelectedSeId: " + NfcService.this.mSelectedSeId);
                    }
                    if (NfcService.DBG) {
                        Log.e(NfcService.TAG, "GSMA mNfceeRouteEnabled: " + NfcService.this.mNfceeRouteEnabled);
                    }
                    if (NfcService.this.mEeRoutingState == NfcService.TASK_ENABLE) {
                        if (!NfcService.this.mNfcSecureElementState) {
                            NfcService.this.mNfceeRouteEnabled = NfcService.NFC_ON_READER_DEFAULT;
                            NfcService.this.mDeviceHost.doDeselectSecureElement(NfcService.this.mSelectedSeId);
                        }
                    } else if (NfcService.this.mNfcSecureElementState) {
                        NfcService.this.mNfceeRouteEnabled = NfcService.SE_BROADCASTS_WITH_HCE;
                        NfcService.this.mDeviceHost.doSelectSecureElement(NfcService.this.mSelectedSeId);
                    }
                } else if (params == null || params.length != NfcService.TASK_ENABLE) {
                    if (NfcService.DBG) {
                        Log.d(NfcService.TAG, "applyRouting #8");
                    }
                    NfcService.this.applyRouting(NfcService.SE_BROADCASTS_WITH_HCE);
                } else {
                    NfcService.this.mLastScreenState = NfcService.this.mScreenState;
                    NfcService.this.mScreenState = params[NfcService.SOUND_START].intValue();
                    NfcService.this.mRoutingWakeLock.acquire();
                    try {
                        if (NfcService.DBG) {
                            Log.d(NfcService.TAG, "applyRouting #9");
                        }
                        NfcService.this.applyRouting(NfcService.NFC_ON_READER_DEFAULT);
                    } finally {
                        NfcService.this.mRoutingWakeLock.release();
                    }
                }
            }
            return null;
        }

        protected void onPostExecute(Void v) {
            if (!NfcService.this.isGsmaApiSupported || this.callback == null) {
                Log.e(NfcService.TAG, "callback == null");
                return;
            }
            try {
                if (NfcService.this.mEeRoutingState >= NfcService.TASK_ENABLE && NfcService.this.mNfcSecureElementState) {
                    this.callback.onCardEmulationMode(NfcService.TASK_ENABLE);
                } else if (NfcService.this.mEeRoutingState != NfcService.TASK_ENABLE || NfcService.this.mNfcSecureElementState) {
                    this.callback.onCardEmulationMode(NfcService.WAIT_FOR_NFCEE_POLL_MS);
                } else {
                    this.callback.onCardEmulationMode(NfcService.SOUND_START);
                }
            } catch (RemoteException e) {
                if (e instanceof DeadObjectException) {
                    Log.e(NfcService.TAG, "Parent of onCardEmulationMode is dead.");
                } else {
                    Log.e(NfcService.TAG, "Can't execute onCardEmulationMode callback.");
                }
            }
        }
    }

    final class CardEmulationService extends INfcCardEmulation.Stub {
        CardEmulationService() {
        }

        public boolean isDefaultServiceForCategory(int userId, ComponentName service, String category) throws RemoteException {
            if (!NfcService.this.mIsHceCapable) {
                return NfcService.NFC_ON_READER_DEFAULT;
            }
            NfcService.this.mContext.enforceCallingOrSelfPermission(NfcService.NFC_PERM, NfcService.NFC_PERM_ERROR);
            NfcService.validateUserId(userId);
            return NfcService.this.mAidCache.isDefaultServiceForCategory(userId, category, service);
        }

        public boolean isDefaultServiceForAid(int userId, ComponentName service, String aid) throws RemoteException {
            if (!NfcService.this.mIsHceCapable) {
                return NfcService.NFC_ON_READER_DEFAULT;
            }
            NfcService.validateUserId(userId);
            NfcService.this.mContext.enforceCallingOrSelfPermission(NfcService.NFC_PERM, NfcService.NFC_PERM_ERROR);
            return NfcService.this.mAidCache.isDefaultServiceForAid(userId, service, aid);
        }

        public boolean setDefaultServiceForCategory(int userId, ComponentName service, String category) throws RemoteException {
            if (!NfcService.this.mIsHceCapable) {
                return NfcService.NFC_ON_READER_DEFAULT;
            }
            NfcService.validateUserId(userId);
            NfcService.enforceAdminPerm(NfcService.this.mContext);
            return NfcService.this.mAidCache.setDefaultServiceForCategory(userId, service, category);
        }

        public boolean setDefaultForNextTap(int userId, ComponentName service) throws RemoteException {
            if (!NfcService.this.mIsHceCapable) {
                return NfcService.NFC_ON_READER_DEFAULT;
            }
            NfcService.validateUserId(userId);
            NfcService.enforceAdminPerm(NfcService.this.mContext);
            NfcService.this.mHostEmulationManager.setDefaultForNextTap(service);
            return NfcService.this.mAidCache.setDefaultForNextTap(userId, service);
        }

        public List<ApduServiceInfo> getServices(int userId, String category) throws RemoteException {
            if (!NfcService.this.mIsHceCapable) {
                return null;
            }
            NfcService.validateUserId(userId);
            NfcService.enforceAdminPerm(NfcService.this.mContext);
            return NfcService.this.mAidCache.getServicesForCategory(userId, category);
        }
    }

    class EnableDisableTask extends AsyncTask<Integer, Void, Void> {
        ICallbacks callback;

        public EnableDisableTask() {
            this.callback = null;
        }

        public EnableDisableTask(ICallbacks cb) {
            this.callback = cb;
        }

        protected Void doInBackground(Integer... params) {
            switch (NfcService.this.mState) {
                case NfcService.VEN_CFG_NFC_OFF_POWER_OFF /*2*/:
                case NfcService.TASK_EE_WIPE /*4*/:
                    Log.e(NfcService.TAG, "Processing EnableDisable task " + params[NfcService.SOUND_START] + " from bad state " + NfcService.this.mState);
                    break;
                default:
                    Process.setThreadPriority(NfcService.SOUND_START);
                    switch (params[NfcService.SOUND_START].intValue()) {
                        case NfcService.TASK_ENABLE /*1*/:
                            if (NfcService.this.checkEnablePopupForChinaNalSecurity() == NfcService.SE_BROADCASTS_WITH_HCE) {
                                Log.e(NfcService.TAG, "sendChnEnablePopup mState - " + NfcService.this.mState);
                                if (NfcService.this.mState != NfcService.VEN_CFG_NFC_ON_POWER_ON) {
                                    if (!NfcService.this.mChnEnablePopupExist) {
                                        NfcService.this.sendChnEnablePopup();
                                        NfcService.this.mChnEnablePopupExist = NfcService.SE_BROADCASTS_WITH_HCE;
                                        break;
                                    }
                                    Log.e(NfcService.TAG, "sendChnEnablePopup mChnEnablePopupExist !!");
                                    break;
                                }
                            }
                            if (this.callback != null) {
                                if (NfcService.this.isGsmaApiSupported) {
                                    try {
                                        this.callback.onNfcController(enableInternal());
                                        break;
                                    } catch (RemoteException e) {
                                        if (!(e instanceof DeadObjectException)) {
                                            if (NfcService.DBG) {
                                                Log.e(NfcService.TAG, "Can't execute onNfcController callback.");
                                                break;
                                            }
                                        } else if (NfcService.DBG) {
                                            Log.e(NfcService.TAG, "Parent of onNfcController is dead.");
                                            break;
                                        }
                                    }
                                }
                            }
                            enableInternal();
                            break;
                            break;
                        case NfcService.VEN_CFG_NFC_OFF_POWER_OFF /*2*/:
                            disableInternal();
                            break;
                        case NfcService.VEN_CFG_NFC_ON_POWER_ON /*3*/:
                            Log.d(NfcService.TAG, "checking on firmware download");
                            if (CscFeature.getInstance().getEnableStatus("CscFeature_NFC_SetOnAsDefault")) {
                                NfcService.NFC_ON_DEFAULT = NfcService.SE_BROADCASTS_WITH_HCE;
                            }
                            if ("ON".equalsIgnoreCase(CscFeature.getInstance().getString("CscFeature_NFC_DefStatus"))) {
                                NfcService.NFC_ON_DEFAULT = NfcService.SE_BROADCASTS_WITH_HCE;
                            } else if ("OFF".equalsIgnoreCase(CscFeature.getInstance().getString("CscFeature_NFC_DefStatus"))) {
                                NfcService.NFC_ON_DEFAULT = NfcService.NFC_ON_READER_DEFAULT;
                            } else if ("OFF".equals("ON")) {
                                NfcService.NFC_ON_DEFAULT = NfcService.SE_BROADCASTS_WITH_HCE;
                            }
                            Log.d(NfcService.TAG, " NFC_ON_DEFAULT : " + NfcService.NFC_ON_DEFAULT);
                            boolean airplaneOverride = NfcService.this.mPrefs.getBoolean(NfcService.PREF_AIRPLANE_OVERRIDE, NfcService.NFC_ON_READER_DEFAULT);
                            if (!"NXP_PN544C3".equals("NXP_PN544C3")) {
                                if ("NONE".equalsIgnoreCase(NfcService.this.mSamsungPref.getString(NfcService.PREFS_DEFAULT_ROUTE_SETTING, "NONE"))) {
                                    String defaultIsoDepRoute = NfcService.this.mCardEmulationRoutingManager.getDefaultRoute(NfcService.TASK_ENABLE);
                                    Log.d(NfcService.TAG, "defaultIsoDepRoute: " + defaultIsoDepRoute);
                                    NfcService.this.mSamsungPref.putString(NfcService.PREFS_DEFAULT_ROUTE_SETTING, defaultIsoDepRoute);
                                }
                                if (Secure.getInt(NfcService.this.mContext.getContentResolver(), "device_provisioned", NfcService.SOUND_START) == 0 && NfcService.this.mPrefs.contains(NfcService.PREF_NFC_ON)) {
                                    Log.d(NfcService.TAG, "In SetupWizard during TASK_BOOT, set nfc state to  NFC_ON_DEFAULT. Ignore PREF_NFC_ON");
                                    NfcService.this.saveNfcOnSetting(NfcService.NFC_ON_DEFAULT);
                                }
                            }
                            if (!NfcService.this.mPrefs.getBoolean(NfcService.PREF_NFC_ON, NfcService.NFC_ON_DEFAULT) || (NfcService.this.mIsAirplaneSensitive && NfcService.this.isAirplaneModeOn() && !airplaneOverride)) {
                                Log.d(NfcService.TAG, "NFC is off.  Checking firmware version");
                                NfcService.this.mDeviceHost.checkFirmware();
                                if (NfcService.this.mIsHceCapable) {
                                    NfcService.this.mAidCache.invalidateCache(ActivityManager.getCurrentUser());
                                }
                                updateState(NfcService.TASK_ENABLE);
                            } else {
                                Log.d(NfcService.TAG, "NFC is on. Doing normal stuff");
                                EnterpriseDeviceManager edm = (EnterpriseDeviceManager) NfcService.this.mContext.getSystemService("enterprise_policy");
                                if (edm == null || edm.getRestrictionPolicy().isNFCEnabled()) {
                                    enableInternal();
                                } else {
                                    Log.e(NfcService.TAG, "EDM : nfc policy disabled. can't enable it ");
                                }
                            }
                            if (NfcService.this.mPrefs.getBoolean(NfcService.PREF_FIRST_BOOT, NfcService.SE_BROADCASTS_WITH_HCE)) {
                                Log.i(NfcService.TAG, "First Boot");
                                NfcService.this.mPrefsEditor.putBoolean(NfcService.PREF_FIRST_BOOT, NfcService.NFC_ON_READER_DEFAULT);
                                NfcService.this.mPrefsEditor.apply();
                                if (!"NXP_PN544C3".equals("CXD2235BGG")) {
                                    executeEeWipe();
                                    break;
                                }
                            }
                            break;
                        case NfcService.TASK_EE_WIPE /*4*/:
                            if (!"NXP_PN544C3".equals("CXD2235BGG")) {
                                executeEeWipe();
                                break;
                            }
                            break;
                        case NfcService.TASK_READER_ENABLE /*5*/:
                            if (NfcService.this.mState != NfcService.TASK_READER_ENABLE) {
                                Log.e(NfcService.TAG, "TASK_READER_ENABLE - wrong state transition mState =" + NfcService.this.mState);
                                break;
                            }
                            updateState(NfcService.VEN_CFG_NFC_ON_POWER_ON);
                            if (NfcService.DBG) {
                                Log.d(NfcService.TAG, "applyRouting #1");
                            }
                            NfcService.this.applyRouting(NfcService.SE_BROADCASTS_WITH_HCE);
                            break;
                        case NfcService.TASK_READER_DISABLE /*6*/:
                            if (NfcService.DBG) {
                                Log.d(NfcService.TAG, "NFC-C polling OFF");
                            }
                            if (NfcService.this.mState != NfcService.VEN_CFG_NFC_ON_POWER_ON) {
                                Log.e(NfcService.TAG, "TASK_READER_DISABLE - wrong state transition mState =" + NfcService.this.mState);
                                break;
                            }
                            NfcService.this.mDeviceHost.disableDiscovery();
                            updateState(NfcService.TASK_READER_ENABLE);
                            if (!"NXP_PN544C3".equals("NXP_PN544C3")) {
                                NfcService.this.applyRouting(NfcService.SE_BROADCASTS_WITH_HCE);
                                break;
                            }
                            break;
                        case NfcService.TASK_CHN_ENABLE_DIRECT /*101*/:
                            Log.e(NfcService.TAG, "TASK_CHN_ENABLE_DIRECT");
                            NfcService.this.mChnEnablePopupExist = NfcService.NFC_ON_READER_DEFAULT;
                            enableInternal();
                            break;
                        case NfcService.TASK_CHN_ENABLE_CANCEL /*102*/:
                            Log.e(NfcService.TAG, "TASK_CHN_ENABLE_CANCEL");
                            NfcService.this.mChnEnablePopupExist = NfcService.NFC_ON_READER_DEFAULT;
                            NfcService.this.saveNfcOnSetting(NfcService.NFC_ON_READER_DEFAULT);
                            disableInternal();
                            updateState(NfcService.TASK_ENABLE);
                            Intent intent = new Intent("com.android.settings.action.SBEAM_STATE_UPDATED");
                            intent.putExtra("turn_on", NfcService.NFC_ON_READER_DEFAULT);
                            NfcService.this.mContext.sendBroadcast(intent);
                            break;
                        case NfcService.TASK_ROUTING_CHANGED /*121*/:
                            Log.e(NfcService.TAG, "Routing Changed");
                            onRoutingChaned();
                            break;
                    }
                    Process.setThreadPriority(NfcService.MSG_SE_APDU_RECEIVED);
                    break;
            }
            return null;
        }

        protected void onPostExecute(Void result) {
            if (NfcService.DBG) {
                Log.d(NfcService.TAG, "EnableDisableTask onPostExecute()");
            }
            if (!"NXP_PN544C3".equals("CXD2235BGG")) {
                if (NfcService.this.isFirstInit && Secure.getInt(NfcService.this.mContext.getContentResolver(), "device_provisioned", NfcService.SOUND_START) == 0) {
                    NfcService.this.isFirstInit = NfcService.NFC_ON_READER_DEFAULT;
                    if (NfcService.DBG) {
                        Log.d(NfcService.TAG, "Broadcast nfc init Intent");
                    }
                    Intent initIntent = new Intent("com.sec.android.nfc.NFCSERVICE_STARTED");
                    initIntent.putExtra("nfc_state", NfcService.NFC_ON_DEFAULT);
                    NfcService.this.mContext.sendBroadcast(initIntent);
                }
                if ((NfcService.this.mState == NfcService.VEN_CFG_NFC_ON_POWER_ON || NfcService.this.mState == NfcService.TASK_READER_ENABLE) && "BCM2079x".equals("NXP_PN544C3")) {
                    synchronized (NfcService.this) {
                        NfcService.this.setDefaultTechnologyRoutingInfo();
                    }
                }
            } else if (NfcService.this.mCallback != null) {
                NfcService.this.mNfcUtility.waitSimBoot(NfcService.this.mCallback, NfcService.this.mIsLock);
                NfcService.this.mCallback = null;
            }
        }

        void checkSecureElementConfuration() {
            int[] seList = NfcService.this.mDeviceHost.doGetSecureElementList();
            if ("NXP_PN547C2".equals("NXP_PN544C3") && seList != null) {
                for (int i = NfcService.SOUND_START; i < seList.length; i += NfcService.TASK_ENABLE) {
                    Log.d(NfcService.TAG, "deSelect SE");
                    NfcService.this.mDeviceHost.doDeselectSecureElement(seList[i]);
                    NfcService.this.mDeviceHost.doSelectSecureElement(seList[i]);
                }
            }
        }

        /* JADX WARNING: inconsistent code. */
        /* Code decompiled incorrectly, please refer to instructions dump. */
        boolean enableInternal() {
            /*
            r12 = this;
            r8 = com.android.nfc.NfcService.this;
            r8 = r8.mDeviceHost;
            r9 = 3;
            r8.doSetVenConfigValue(r9);
            r8 = com.android.nfc.NfcService.this;
            r8 = r8.mState;
            r9 = 3;
            if (r8 != r9) goto L_0x0013;
        L_0x0011:
            r8 = 1;
        L_0x0012:
            return r8;
        L_0x0013:
            r8 = com.android.nfc.NfcService.menu_split;
            r9 = 1;
            if (r8 != r9) goto L_0x0021;
        L_0x0018:
            r8 = com.android.nfc.NfcService.this;
            r8 = r8.mState;
            r9 = 5;
            if (r8 != r9) goto L_0x0021;
        L_0x001f:
            r8 = 1;
            goto L_0x0012;
        L_0x0021:
            r8 = "NfcService";
            r9 = "Enabling NFC";
            android.util.Log.i(r8, r9);
            r8 = 2;
            r12.updateState(r8);
            r8 = "NXP_PN544C3";
            r9 = "NXP_PN544C3";
            r8 = r8.equals(r9);
            if (r8 == 0) goto L_0x00b9;
        L_0x0036:
            r8 = com.android.nfc.NfcService.menu_split;
            r9 = 1;
            if (r8 != r9) goto L_0x0142;
        L_0x003b:
            r8 = com.android.nfc.NfcService.UICC_ID_TYPE;
            r9 = com.android.nfc.NfcService.this;
            r9 = r9.mPrefs;
            r10 = "default_route_id";
            r11 = 100;
            r9 = r9.getInt(r10, r11);
            if (r8 == r9) goto L_0x005d;
        L_0x004d:
            r8 = com.android.nfc.NfcService.this;
            r8 = r8.mPrefs;
            r9 = "default_route_id";
            r10 = 100;
            r8 = r8.getInt(r9, r10);
            if (r8 != 0) goto L_0x0142;
        L_0x005d:
            r8 = "NfcService";
            r9 = "####USE Preference for defualt route ID";
            android.util.Log.d(r8, r9);
        L_0x0064:
            r8 = "ENABLE";
            r9 = com.sec.android.app.CscFeature.getInstance();
            r10 = "CscFeature_NFC_ConfigAdvancedSettings";
            r11 = "ENABLE";
            r9 = r9.getString(r10, r11);
            r9 = r9.toUpperCase();
            r8 = r8.equalsIgnoreCase(r9);
            if (r8 == 0) goto L_0x00a1;
        L_0x007c:
            r0 = "DH";
            r6 = 0;
            r8 = com.android.nfc.NfcService.this;
            r8 = r8.mPrefs;
            r9 = "default_route_id";
            r10 = 100;
            r6 = r8.getInt(r9, r10);
            r8 = 2;
            if (r6 != r8) goto L_0x01c9;
        L_0x0090:
            r0 = "UICC";
        L_0x0092:
            r8 = com.android.nfc.NfcService.this;
            r8 = r8.mSamsungPref;
            r9 = "defaultRouteDest";
            r10 = r0.toUpperCase();
            r8.putString(r9, r10);
        L_0x00a1:
            r4 = 0;
            r8 = com.android.nfc.NfcService.this;
            r8 = r8.mScreenState;
            r9 = 2;
            if (r8 < r9) goto L_0x00b2;
        L_0x00a9:
            r8 = com.android.nfc.NfcService.this;
            r8 = r8.mEeRoutingState;
            r9 = 2;
            if (r8 == r9) goto L_0x00b9;
        L_0x00b2:
            r8 = com.android.nfc.NfcService.this;
            r8 = r8.mScreenState;
            r9 = 3;
            if (r8 < r9) goto L_0x00b9;
        L_0x00b9:
            r7 = new com.android.nfc.NfcService$WatchDogThread;
            r8 = com.android.nfc.NfcService.this;
            r9 = "enableInternal";
            r10 = 90000; // 0x15f90 float:1.26117E-40 double:4.4466E-319;
            r7.<init>(r9, r10);
            r7.start();
            r8 = com.android.nfc.NfcService.this;	 Catch:{ all -> 0x01ed }
            r8 = r8.mRoutingWakeLock;	 Catch:{ all -> 0x01ed }
            r8.acquire();	 Catch:{ all -> 0x01ed }
            r4 = 0;
            r8 = com.android.nfc.NfcService.this;	 Catch:{ all -> 0x01e2 }
            r9 = com.android.nfc.NfcService.this;	 Catch:{ all -> 0x01e2 }
            r9 = r9.checkScreenState();	 Catch:{ all -> 0x01e2 }
            r8.mScreenState = r9;	 Catch:{ all -> 0x01e2 }
            r8 = com.android.nfc.NfcService.this;	 Catch:{ all -> 0x01e2 }
            r9 = com.android.nfc.NfcService.this;	 Catch:{ all -> 0x01e2 }
            r9 = r9.mScreenState;	 Catch:{ all -> 0x01e2 }
            r8.mLastScreenState = r9;	 Catch:{ all -> 0x01e2 }
            r8 = "NfcService";
            r9 = new java.lang.StringBuilder;	 Catch:{ all -> 0x01e2 }
            r9.<init>();	 Catch:{ all -> 0x01e2 }
            r10 = "enableInternal(), screenState = ";
            r9 = r9.append(r10);	 Catch:{ all -> 0x01e2 }
            r10 = com.android.nfc.NfcService.this;	 Catch:{ all -> 0x01e2 }
            r10 = r10.mScreenState;	 Catch:{ all -> 0x01e2 }
            r9 = r9.append(r10);	 Catch:{ all -> 0x01e2 }
            r9 = r9.toString();	 Catch:{ all -> 0x01e2 }
            android.util.Log.i(r8, r9);	 Catch:{ all -> 0x01e2 }
            r8 = "BCM2079x";
            r9 = "NXP_PN544C3";
            r8 = r8.equals(r9);	 Catch:{ all -> 0x01e2 }
            if (r8 == 0) goto L_0x0111;
        L_0x010a:
            r8 = com.android.nfc.NfcService.this;	 Catch:{ all -> 0x01e2 }
            r8 = r8.mScreenState;	 Catch:{ all -> 0x01e2 }
            switch(r8) {
                case 1: goto L_0x01d6;
                case 2: goto L_0x01f2;
                case 3: goto L_0x01fe;
                default: goto L_0x0111;
            };	 Catch:{ all -> 0x01e2 }
        L_0x0111:
            r8 = "NXP_PN544C3";
            r9 = "NXP_PN544C3";
            r8 = r8.equals(r9);	 Catch:{ all -> 0x01e2 }
            if (r8 != 0) goto L_0x011c;
        L_0x011b:
            r4 = 1;
        L_0x011c:
            r8 = com.android.nfc.NfcService.this;	 Catch:{ all -> 0x01e2 }
            r8 = r8.mDeviceHost;	 Catch:{ all -> 0x01e2 }
            r8 = r8.initialize(r4);	 Catch:{ all -> 0x01e2 }
            if (r8 != 0) goto L_0x020a;
        L_0x0128:
            r8 = "NfcService";
            r9 = "Error enabling NFC";
            android.util.Log.w(r8, r9);	 Catch:{ all -> 0x01e2 }
            r8 = 1;
            r12.updateState(r8);	 Catch:{ all -> 0x01e2 }
            r8 = 0;
            r9 = com.android.nfc.NfcService.this;	 Catch:{ all -> 0x01ed }
            r9 = r9.mRoutingWakeLock;	 Catch:{ all -> 0x01ed }
            r9.release();	 Catch:{ all -> 0x01ed }
            r7.cancel();
            goto L_0x0012;
        L_0x0142:
            r8 = "DH";
            r9 = com.sec.android.app.CscFeature.getInstance();
            r10 = "CscFeature_NFC_DefaultCardModeConfig";
            r9 = r9.getString(r10);
            r9 = r9.toUpperCase();
            r8 = r8.equalsIgnoreCase(r9);
            if (r8 == 0) goto L_0x0176;
        L_0x0158:
            r8 = "NfcService";
            r9 = "####HCE_DH_ID";
            android.util.Log.d(r8, r9);
            r8 = com.android.nfc.NfcService.this;
            r8 = r8.mPrefsEditor;
            r9 = "default_route_id";
            r10 = 0;
            r8.putInt(r9, r10);
            r8 = com.android.nfc.NfcService.this;
            r8 = r8.mPrefsEditor;
            r8.apply();
            goto L_0x0064;
        L_0x0176:
            r8 = "UICC";
            r9 = com.sec.android.app.CscFeature.getInstance();
            r10 = "CscFeature_NFC_DefaultCardModeConfig";
            r9 = r9.getString(r10);
            r9 = r9.toUpperCase();
            r8 = r8.equalsIgnoreCase(r9);
            if (r8 == 0) goto L_0x01ab;
        L_0x018c:
            r8 = "NfcService";
            r9 = "####UICC_ID_TYPE";
            android.util.Log.d(r8, r9);
            r8 = com.android.nfc.NfcService.this;
            r8 = r8.mPrefsEditor;
            r9 = "default_route_id";
            r10 = com.android.nfc.NfcService.UICC_ID_TYPE;
            r8.putInt(r9, r10);
            r8 = com.android.nfc.NfcService.this;
            r8 = r8.mPrefsEditor;
            r8.apply();
            goto L_0x0064;
        L_0x01ab:
            r8 = "NfcService";
            r9 = "####HCE_DH_ID";
            android.util.Log.d(r8, r9);
            r8 = com.android.nfc.NfcService.this;
            r8 = r8.mPrefsEditor;
            r9 = "default_route_id";
            r10 = 0;
            r8.putInt(r9, r10);
            r8 = com.android.nfc.NfcService.this;
            r8 = r8.mPrefsEditor;
            r8.apply();
            goto L_0x0064;
        L_0x01c9:
            if (r6 != 0) goto L_0x01cf;
        L_0x01cb:
            r0 = "DH";
            goto L_0x0092;
        L_0x01cf:
            r8 = 1;
            if (r6 != r8) goto L_0x0092;
        L_0x01d2:
            r0 = "UICC";
            goto L_0x0092;
        L_0x01d6:
            r8 = com.android.nfc.NfcService.this;	 Catch:{ all -> 0x01e2 }
            r8 = r8.mBrcmPowerMode;	 Catch:{ all -> 0x01e2 }
            r9 = 2;
            r8.setPowerMode(r9);	 Catch:{ all -> 0x01e2 }
            goto L_0x0111;
        L_0x01e2:
            r8 = move-exception;
            r9 = com.android.nfc.NfcService.this;	 Catch:{ all -> 0x01ed }
            r9 = r9.mRoutingWakeLock;	 Catch:{ all -> 0x01ed }
            r9.release();	 Catch:{ all -> 0x01ed }
            throw r8;	 Catch:{ all -> 0x01ed }
        L_0x01ed:
            r8 = move-exception;
            r7.cancel();
            throw r8;
        L_0x01f2:
            r8 = com.android.nfc.NfcService.this;	 Catch:{ all -> 0x01e2 }
            r8 = r8.mBrcmPowerMode;	 Catch:{ all -> 0x01e2 }
            r9 = 3;
            r8.setPowerMode(r9);	 Catch:{ all -> 0x01e2 }
            goto L_0x0111;
        L_0x01fe:
            r8 = com.android.nfc.NfcService.this;	 Catch:{ all -> 0x01e2 }
            r8 = r8.mBrcmPowerMode;	 Catch:{ all -> 0x01e2 }
            r9 = 4;
            r8.setPowerMode(r9);	 Catch:{ all -> 0x01e2 }
            goto L_0x0111;
        L_0x020a:
            r8 = com.android.nfc.NfcService.this;	 Catch:{ all -> 0x01ed }
            r8 = r8.mRoutingWakeLock;	 Catch:{ all -> 0x01ed }
            r8.release();	 Catch:{ all -> 0x01ed }
            r7.cancel();
            r8 = "NXP_PN544C3";
            r9 = "NXP_PN544C3";
            r8 = r8.equals(r9);
            if (r8 != 0) goto L_0x0223;
        L_0x0220:
            r12.checkSecureElementConfuration();
        L_0x0223:
            r8 = "NXP_PN544C3";
            r9 = "NXP_PN544C3";
            r8 = r8.equals(r9);
            if (r8 == 0) goto L_0x0232;
        L_0x022d:
            r8 = com.android.nfc.NfcService.this;
            r9 = 1;
            r8.mIsRouteForced = r9;
        L_0x0232:
            r8 = com.android.nfc.NfcService.this;
            r8 = r8.mIsHceCapable;
            if (r8 == 0) goto L_0x02c2;
        L_0x0238:
            r8 = "BCM2079x";
            r9 = "NXP_PN544C3";
            r8 = r8.equals(r9);
            if (r8 == 0) goto L_0x0254;
        L_0x0242:
            r8 = com.android.nfc.NfcService.this;
            r8 = r8.mAidRoutingManager;
            r8.getDefaultRouteDestination();
            r8 = com.android.nfc.NfcService.this;
            r8 = r8.mAidRoutingManager;
            r8.getDefaultOffHostRouteDestination();
        L_0x0254:
            r8 = "NXP_PN544C3";
            r9 = "NXP_PN544C3";
            r8 = r8.equals(r9);
            if (r8 != 0) goto L_0x02b9;
        L_0x025e:
            r8 = com.android.nfc.NfcService.this;
            r8 = r8.mSamsungPref;
            r9 = "defaultRouteDest";
            r10 = "DH";
            r2 = r8.getString(r9, r10);
            r8 = com.android.nfc.NfcService.DBG;
            if (r8 == 0) goto L_0x0288;
        L_0x0270:
            r8 = "NfcService";
            r9 = new java.lang.StringBuilder;
            r9.<init>();
            r10 = "set default IsoRoute: ";
            r9 = r9.append(r10);
            r9 = r9.append(r2);
            r9 = r9.toString();
            android.util.Log.d(r8, r9);
        L_0x0288:
            r8 = com.android.nfc.NfcService.this;
            r8 = r8.mCardEmulationRoutingManager;
            r9 = 1;
            r8.setDefaultRoute(r9, r2);
            r8 = com.android.nfc.NfcService.this;
            r8 = r8.mCardEmulationRoutingManager;
            r5 = r8.getRouteDestination(r2);
            r8 = com.android.nfc.NfcService.this;
            r8 = r8.mAidRoutingManager;
            r9 = 0;
            r8.setDefaultRoute(r5, r9);
            r8 = com.android.nfc.NfcService.this;
            r8 = r8.mAidRoutingManager;
            r9 = com.android.nfc.NfcService.this;
            r9 = r9.mDeviceHost;
            r9 = r9.getAidTableSize();
            r8.setTableSize(r9);
        L_0x02b9:
            r8 = com.android.nfc.NfcService.this;
            r8 = r8.mAidCache;
            r8.onNfcEnabled();
        L_0x02c2:
            r8 = "NXP_PN544C3";
            r9 = "NXP_PN544C3";
            r8 = r8.equals(r9);
            if (r8 == 0) goto L_0x02d1;
        L_0x02cc:
            r8 = com.android.nfc.NfcService.this;
            r9 = 0;
            r8.mIsRouteForced = r9;
        L_0x02d1:
            r9 = com.android.nfc.NfcService.this;
            monitor-enter(r9);
            r8 = com.android.nfc.NfcService.this;	 Catch:{ all -> 0x0390 }
            r8 = r8.mObjectMap;	 Catch:{ all -> 0x0390 }
            r8.clear();	 Catch:{ all -> 0x0390 }
            r8 = com.android.nfc.NfcService.this;	 Catch:{ all -> 0x0390 }
            r8 = r8.mP2pLinkManager;	 Catch:{ all -> 0x0390 }
            r10 = com.android.nfc.NfcService.this;	 Catch:{ all -> 0x0390 }
            r10 = r10.mIsNdefPushEnabled;	 Catch:{ all -> 0x0390 }
            r11 = 1;
            r8.enableDisable(r10, r11);	 Catch:{ all -> 0x0390 }
            r8 = com.android.nfc.NfcService.menu_split;	 Catch:{ all -> 0x0390 }
            r10 = 1;
            if (r8 != r10) goto L_0x0393;
        L_0x02ec:
            r8 = com.android.nfc.NfcService.this;	 Catch:{ all -> 0x0390 }
            r8 = r8.mPrefs;	 Catch:{ all -> 0x0390 }
            r10 = "nfc_reader_on";
            r11 = 0;
            r8 = r8.getBoolean(r10, r11);	 Catch:{ all -> 0x0390 }
            if (r8 == 0) goto L_0x0364;
        L_0x02fb:
            r8 = 3;
            r12.updateState(r8);	 Catch:{ all -> 0x0390 }
        L_0x02ff:
            monitor-exit(r9);	 Catch:{ all -> 0x0390 }
            r8 = com.android.nfc.NfcService.this;
            r8.initSoundPool();
            r1 = r12.getChipName();
            r8 = 2;
            if (r1 != r8) goto L_0x0325;
        L_0x030c:
            r8 = "NfcService";
            r9 = "Wait for UICC init 3sec";
            android.util.Log.d(r8, r9);	 Catch:{ Exception -> 0x0399 }
            r8 = 1500; // 0x5dc float:2.102E-42 double:7.41E-321;
            java.lang.Thread.sleep(r8);	 Catch:{ InterruptedException -> 0x03c5 }
        L_0x0318:
            r8 = "NXP_PN544C3";
            r9 = "NXP_PN65T_JCOP_DNLD";
            r8 = r8.equals(r9);
            if (r8 == 0) goto L_0x0325;
        L_0x0322:
            r12.jcopOsdwnld();
        L_0x0325:
            r8 = com.android.nfc.NfcService.this;
            r8 = r8.mNoDiscoveryNfcOn;
            if (r8 == 0) goto L_0x039f;
        L_0x032d:
            r8 = com.android.nfc.NfcService.this;
            r9 = 0;
            r8.mNoDiscoveryNfcOn = r9;
            r8 = "S3FNRN3";
            r9 = "NXP_PN544C3";
            r8 = r8.equals(r9);
            if (r8 != 0) goto L_0x0347;
        L_0x033d:
            r8 = "S3FWRN5";
            r9 = "NXP_PN544C3";
            r8 = r8.equals(r9);
            if (r8 == 0) goto L_0x0361;
        L_0x0347:
            r8 = com.android.nfc.NfcService.DBG;
            if (r8 == 0) goto L_0x0352;
        L_0x034b:
            r8 = "NfcService";
            r9 = "doSelectSecureElement() for FactoryTest";
            android.util.Log.i(r8, r9);
        L_0x0352:
            r8 = com.android.nfc.NfcService.this;
            r8 = r8.mDeviceHost;
            r9 = com.android.nfc.NfcService.this;
            r9 = r9.mSelectedSeId;
            r8.doSelectSecureElement(r9);
        L_0x0361:
            r8 = 1;
            goto L_0x0012;
        L_0x0364:
            r8 = "NXP_PN544C3";
            r10 = "NXP_PN544C3";
            r8 = r8.equals(r10);	 Catch:{ all -> 0x0390 }
            if (r8 != 0) goto L_0x0378;
        L_0x036e:
            r8 = com.android.nfc.NfcService.this;	 Catch:{ all -> 0x0390 }
            r8 = r8.mCardEmulationRoutingManager;	 Catch:{ all -> 0x0390 }
            r10 = 1;
            r8.upateRouting(r10);	 Catch:{ all -> 0x0390 }
        L_0x0378:
            r8 = com.android.nfc.NfcService.this;	 Catch:{ all -> 0x0390 }
            r8 = r8.mDeviceHost;	 Catch:{ all -> 0x0390 }
            r8.enableDiscovery();	 Catch:{ all -> 0x0390 }
            r8 = com.android.nfc.NfcService.this;	 Catch:{ all -> 0x0390 }
            r8 = r8.mDeviceHost;	 Catch:{ all -> 0x0390 }
            r8.disableDiscovery();	 Catch:{ all -> 0x0390 }
            r8 = 5;
            r12.updateState(r8);	 Catch:{ all -> 0x0390 }
            goto L_0x02ff;
        L_0x0390:
            r8 = move-exception;
            monitor-exit(r9);	 Catch:{ all -> 0x0390 }
            throw r8;
        L_0x0393:
            r8 = 3;
            r12.updateState(r8);	 Catch:{ all -> 0x0390 }
            goto L_0x02ff;
        L_0x0399:
            r3 = move-exception;
            r3.printStackTrace();
            goto L_0x0318;
        L_0x039f:
            r8 = com.android.nfc.NfcService.this;
            r9 = com.android.nfc.NfcService.this;
            r9 = r9.mScreenState;
            r8.mLastScreenState = r9;
            r8 = com.android.nfc.NfcService.this;
            r9 = com.android.nfc.NfcService.this;
            r9 = r9.checkScreenState();
            r8.mScreenState = r9;
            r8 = com.android.nfc.NfcService.DBG;
            if (r8 == 0) goto L_0x03bc;
        L_0x03b5:
            r8 = "NfcService";
            r9 = "applyRouting #2";
            android.util.Log.d(r8, r9);
        L_0x03bc:
            r8 = com.android.nfc.NfcService.this;
            r9 = 1;
            r8.applyRouting(r9);
            r8 = 1;
            goto L_0x0012;
        L_0x03c5:
            r8 = move-exception;
            goto L_0x0318;
            */
            throw new UnsupportedOperationException("Method not decompiled: com.android.nfc.NfcService.EnableDisableTask.enableInternal():boolean");
        }

        void jcopOsdwnld() {
            NfcService.this.mToastHandler.showToast("JcopOS download Started", NfcService.SOUND_START);
            Log.i(NfcService.TAG, "Starting JCOS download");
            int status = NfcService.this.mDeviceHost.JCOSDownload();
            if (status == 0) {
                NfcService.this.mToastHandler.showToast("JcopOS download Success", NfcService.SOUND_START);
                Log.i(NfcService.TAG, "JCOS download success");
            } else if (status == NfcService.TASK_ENABLE) {
                NfcService.this.mToastHandler.showToast("JcopOS is upto date", NfcService.SOUND_START);
                Log.i(NfcService.TAG, "JCOS is already upto date - No update required");
            } else if (status == NfcService.MSG_LLCP_LINK_FIRST_PACKET) {
                NfcService.this.mToastHandler.showToast("JcopOS download Feature is not available", NfcService.SOUND_START);
                Log.i(NfcService.TAG, "JCOS download Failed");
            } else {
                NfcService.this.mToastHandler.showToast("JcopOS download Failed", NfcService.SOUND_START);
                Log.i(NfcService.TAG, "JCOS download Failed");
            }
        }

        int getChipName() {
            Log.i(NfcService.TAG, "Starting getChipName");
            return NfcService.this.mDeviceHost.getChipVer();
        }

        boolean disableInternal() {
            if (NfcService.this.mState == NfcService.TASK_ENABLE) {
                return NfcService.SE_BROADCASTS_WITH_HCE;
            }
            Log.i(NfcService.TAG, "Disabling NFC");
            updateState(NfcService.TASK_EE_WIPE);
            WatchDogThread watchDog = new WatchDogThread("disableInternal", NfcService.ROUTING_WATCHDOG_MS);
            watchDog.start();
            if (NfcService.this.mPowerShutDown == NfcService.SE_BROADCASTS_WITH_HCE) {
                Log.i(NfcService.TAG, "Power off : Disabling NFC Disabling ESE/UICC");
                NfcService.this.mPowerShutDown = NfcService.NFC_ON_READER_DEFAULT;
                NfcService.this.mDeviceHost.doSetVenConfigValue(NfcService.VEN_CFG_NFC_ON_POWER_ON);
            } else {
                Log.i(NfcService.TAG, "Disabling NFC Disabling ESE/UICC");
                NfcService.this.mDeviceHost.doSetVenConfigValue(NfcService.VEN_CFG_NFC_OFF_POWER_OFF);
            }
            if (NfcService.this.mIsHceCapable) {
                NfcService.this.mAidCache.onNfcDisabled();
            }
            Log.i(NfcService.TAG, "disableInternal/ -- enableDisable");
            NfcService.this.mP2pLinkManager.enableDisable(NfcService.NFC_ON_READER_DEFAULT, NfcService.NFC_ON_READER_DEFAULT);
            Log.i(NfcService.TAG, "disableInternal/ ++ enableDisable");
            Long startTime = Long.valueOf(SystemClock.elapsedRealtime());
            do {
                synchronized (NfcService.this) {
                    if (NfcService.this.mOpenEe == null) {
                        break;
                    }
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                    }
                }
            } while (SystemClock.elapsedRealtime() - startTime.longValue() < 5000);
            synchronized (NfcService.this) {
                if ("NXP_PN544C3".equals("NXP_PN544C3")) {
                    if (NfcService.this.mOpenEe != null) {
                        try {
                            NfcService.this._nfcEeClose(NfcService.EE_ERROR_IO, NfcService.this.mOpenEe.binder);
                        } catch (IOException e2) {
                        }
                    }
                } else if (NfcService.this.mOpenEeMap.size() != 0) {
                    try {
                        HashMap<Integer, OpenSecureElement> mOpenEeMapclone = new HashMap();
                        mOpenEeMapclone.putAll(NfcService.this.mOpenEeMap);
                        for (Integer key_pid : mOpenEeMapclone.keySet()) {
                            NfcService.this.mOpenEe = (OpenSecureElement) NfcService.this.mOpenEeMap.get(key_pid);
                            NfcService.this._nfcEeClose(key_pid.intValue(), NfcService.this.mOpenEe.binder);
                        }
                        NfcService.this.mOpenEeMap.clear();
                        mOpenEeMapclone.clear();
                        NfcService.this.mOpenEe = null;
                    } catch (IOException e3) {
                    }
                }
            }
            NfcService.this.maybeDisconnectTarget();
            NfcService.this.mNfcDispatcher.setForegroundDispatch(null, null, (String[][]) null);
            boolean result = NfcService.this.mDeviceHost.deinitialize();
            if (NfcService.DBG) {
                Log.d(NfcService.TAG, "mDeviceHost.deinitialize() = " + result);
            }
            watchDog.cancel();
            updateState(NfcService.TASK_ENABLE);
            NfcService.this.releaseSoundPool();
            return result;
        }

        void executeEeWipe() {
            if (NfcService.DBG) {
                Log.w(NfcService.TAG, "This Model is not support ESE");
            }
        }

        void updateState(int newState) {
            synchronized (NfcService.this) {
                if ("NXP_PN544C3".equals("NXP_PN544C3") && newState == NfcService.this.mState) {
                    return;
                }
                NfcService.this.mState = newState;
                String EXTRA_PREF_ADAPTER_STATE = "android.nfc.extra.PREF_ADAPTER_STATE";
                Intent intent = new Intent("android.nfc.action.ADAPTER_STATE_CHANGED");
                intent.setFlags(67108864);
                intent.putExtra("android.nfc.extra.ADAPTER_STATE", NfcService.this.mState);
                intent.putExtra("android.nfc.extra.PREF_ADAPTER_STATE", NfcService.this.mPrefs.getBoolean(NfcService.PREF_NFC_ON, NfcService.NFC_ON_DEFAULT));
                if (!"NXP_PN544C3".equals("CXD2235BGG")) {
                    NfcService.this.mContext.sendBroadcastAsUser(intent, UserHandle.CURRENT);
                } else if (NfcService.this.getLockStatefromDevice() != 0) {
                    NfcService.this.mContext.sendBroadcastAsUser(intent, UserHandle.CURRENT);
                }
                if (newState == NfcService.VEN_CFG_NFC_ON_POWER_ON) {
                    NfcService.this.sendMessage(NfcService.MSG_ACTIVE_NFC_ICON, null);
                } else if (newState == NfcService.TASK_ENABLE) {
                    NfcService.this.sendMessage(NfcService.MSG_INACTIVE_NFC_ICON, null);
                }
                if (NfcService.menu_split == NfcService.SE_BROADCASTS_WITH_HCE) {
                    Intent intent2 = new Intent("android.nfc.action.ADAPTER_STATE_CHANGE_READER");
                    intent2.setFlags(67108864);
                    intent2.putExtra("android.nfc.extra.ADAPTER_STATE", NfcService.this.mState);
                    if (!"NXP_PN544C3".equals("CXD2235BGG")) {
                        NfcService.this.mContext.sendBroadcastAsUser(intent2, UserHandle.CURRENT);
                    } else if (NfcService.this.getLockStatefromDevice() != 0) {
                        NfcService.this.mContext.sendBroadcastAsUser(intent2, UserHandle.CURRENT);
                    }
                }
            }
        }

        void onRoutingChaned() {
            if ("NXP_PN544C3".equals("NXP_PN544C3")) {
                if (NfcService.this.mState != NfcService.VEN_CFG_NFC_ON_POWER_ON) {
                    Log.d(NfcService.TAG, "nfc does not enabled");
                } else if (NfcService.this.mIsHceCapable) {
                    NfcService.this.mIsRouteForced = NfcService.SE_BROADCASTS_WITH_HCE;
                    Log.d(NfcService.TAG, "--clear aid table pn544c3");
                    NfcService.this.mDeviceHost.clearRouting();
                    Log.d(NfcService.TAG, "++ clear aid table pn544c3");
                    NfcService.this.mAidCache.onNfcRoutingChanged(NfcService.SOUND_START);
                    NfcService.this.mIsRouteForced = NfcService.NFC_ON_READER_DEFAULT;
                }
            } else if (NfcService.this.mState != NfcService.VEN_CFG_NFC_ON_POWER_ON && NfcService.this.mState != NfcService.TASK_READER_ENABLE) {
                Log.d(NfcService.TAG, "nfc does not enabled");
            } else if (NfcService.this.mIsHceCapable) {
                String defaultIsoRoute = NfcService.this.mSamsungPref.getString(NfcService.PREFS_DEFAULT_ROUTE_SETTING, NfcService.HCE_DEVICE_HOST_NAME);
                int route = NfcService.this.mCardEmulationRoutingManager.getRouteDestination(defaultIsoRoute);
                if (NfcService.this.mAidRoutingManager.getDefaultRoute() != route) {
                    Log.d(NfcService.TAG, "--clear aid table");
                    NfcService.this.mDeviceHost.clearAidTable();
                    Log.d(NfcService.TAG, "++ clear aid table");
                    NfcService.this.mCardEmulationRoutingManager.setDefaultRoute(NfcService.TASK_ENABLE, defaultIsoRoute);
                    NfcService.this.mAidCache.onNfcRoutingChanged(route);
                    return;
                }
                Log.d(NfcService.TAG, "same: ");
            }
        }
    }

    final class NfcAdapterExtrasService extends INfcAdapterExtras.Stub {
        NfcAdapterExtrasService() {
        }

        private Bundle writeNoException() {
            Bundle p = new Bundle();
            p.putInt("e", NfcService.SOUND_START);
            return p;
        }

        private Bundle writeEeException(int exceptionType, String message) {
            Bundle p = new Bundle();
            p.putInt("e", exceptionType);
            p.putString("m", message);
            return p;
        }

        public Bundle open(String pkg, IBinder b) throws RemoteException {
            NfcService.this.enforceNfceeAdminPerm(pkg);
            int handle = _open(b);
            if (handle < 0) {
                return writeEeException(handle, "NFCEE open exception.");
            }
            return writeNoException();
        }

        private int _open(IBinder b) {
            int i = NfcService.EE_ERROR_ALREADY_OPEN;
            synchronized (NfcService.this) {
                if (!NfcService.this.isNfcEnabled()) {
                    i = NfcService.EE_ERROR_NFC_DISABLED;
                } else if (NfcService.this.mInProvisionMode) {
                    i = NfcService.EE_ERROR_IO;
                } else if (NfcService.this.mDeviceHost.enablePN544Quirks() && NfcService.this.mP2pLinkManager.isLlcpActive()) {
                    i = NfcService.EE_ERROR_EXT_FIELD;
                } else if (NfcService.this.mOpenEeMap.size() > 0 && "GOOGLE".equals(NfcService.mSecureEventType)) {
                } else if (NfcService.this.mOpenEeMap.get(Integer.valueOf(getCallingPid())) != null) {
                } else {
                    if (NfcService.DBG) {
                        Log.d(NfcService.TAG, "_open :: mopenEe size is " + NfcService.this.mOpenEeMap.size());
                    }
                    boolean restorePolling = NfcService.NFC_ON_READER_DEFAULT;
                    if (NfcService.this.mDeviceHost.enablePN544Quirks() && NfcService.this.mNfcPollingEnabled) {
                        NfcService.this.mDeviceHost.disableDiscovery();
                        NfcService.this.mNfcPollingEnabled = NfcService.NFC_ON_READER_DEFAULT;
                        restorePolling = NfcService.SE_BROADCASTS_WITH_HCE;
                    }
                    if (NfcService.this.mOpenEeMap.size() == 0) {
                        Log.d(NfcService.TAG, "_open :: doOpenSecureElementConnection");
                        NfcService.handle = NfcService.this.doOpenSecureElementConnection();
                    }
                    if (NfcService.handle < 0) {
                        if (restorePolling) {
                            NfcService.this.mDeviceHost.enableDiscovery();
                            NfcService.this.mNfcPollingEnabled = NfcService.SE_BROADCASTS_WITH_HCE;
                        }
                        i = NfcService.handle;
                    } else {
                        NfcService.this.mDeviceHost.setTimeout(NfcService.VEN_CFG_NFC_ON_POWER_ON, 30000);
                        NfcService.this.mOpenEe = new OpenSecureElement(getCallingPid(), NfcService.handle, b);
                        if (NfcService.DBG) {
                            Log.d(NfcService.TAG, "_open :: SE handle value is " + NfcService.handle);
                        }
                        NfcService.this.mOpenEeMap.put(Integer.valueOf(getCallingPid()), NfcService.this.mOpenEe);
                        if (NfcService.DBG) {
                            Log.d(NfcService.TAG, "_open :: mOpenEeMap.put(getCallingPid(), mOpenEe) , getCallingPid() : " + getCallingPid());
                        }
                        try {
                            b.linkToDeath(NfcService.this.mOpenEe, NfcService.SOUND_START);
                        } catch (RemoteException e) {
                            NfcService.this.mOpenEe.binderDied();
                        }
                        String[] arr$ = NfcService.this.mContext.getPackageManager().getPackagesForUid(getCallingUid());
                        int len$ = arr$.length;
                        for (int i$ = NfcService.SOUND_START; i$ < len$; i$ += NfcService.TASK_ENABLE) {
                            NfcService.this.mSePackages.add(arr$[i$]);
                        }
                        i = NfcService.handle;
                    }
                }
            }
            return i;
        }

        public Bundle close(String pkg, IBinder binder) throws RemoteException {
            NfcService.this.enforceNfceeAdminPerm(pkg);
            try {
                NfcService.this._nfcEeClose(getCallingPid(), binder);
                Bundle result = writeNoException();
                if (!NfcService.DBG) {
                    return result;
                }
                Log.d(NfcService.TAG, "close :: Remain mopenEe size is " + NfcService.this.mOpenEeMap.size());
                return result;
            } catch (IOException e) {
                return writeEeException(NfcService.EE_ERROR_IO, e.getMessage());
            }
        }

        public Bundle transceive(String pkg, byte[] in) throws RemoteException {
            NfcService.this.enforceNfceeAdminPerm(pkg);
            try {
                byte[] out = _transceive(in);
                Bundle result = writeNoException();
                result.putByteArray("out", out);
                return result;
            } catch (IOException e) {
                return writeEeException(NfcService.EE_ERROR_IO, e.getMessage());
            }
        }

        private byte[] _transceive(byte[] data) throws IOException {
            synchronized (NfcService.this) {
                if (NfcService.this.isNfcEnabled()) {
                    NfcService.this.mOpenEe = (OpenSecureElement) NfcService.this.mOpenEeMap.get(Integer.valueOf(getCallingPid()));
                    if (NfcService.DBG) {
                        Log.d(NfcService.TAG, "_transceive :: mOpenEe = (OpenSecureElement)(mOpenEeMap.get(getCallingPid())) , getCallingPid() : " + getCallingPid());
                    }
                    if (NfcService.this.mOpenEe == null) {
                        throw new IOException("NFC EE is not open");
                    } else if (getCallingPid() != NfcService.this.mOpenEe.pid) {
                        throw new SecurityException("Wrong PID");
                    }
                } else {
                    throw new IOException("NFC is not enabled");
                }
            }
            return NfcService.this.doTransceive(NfcService.this.mOpenEe.handle, data);
        }

        public int getCardEmulationRoute(String pkg) throws RemoteException {
            NfcService.this.enforceNfceeAdminPerm(pkg);
            return NfcService.this.mCardEmulationRoutingManager.getRoutingState(NfcService.SECURE_ELEMENT_ESE_NAME);
        }

        public void setCardEmulationRoute(String pkg, int route) throws RemoteException {
            NfcService.this.enforceNfceeAdminPerm(pkg);
            if ("NXP_PN544C3".equals("NXP_PN544C3")) {
                NfcService.this.mEeRoutingState = route;
            }
            NfcService.this.mCardEmulationRoutingManager.setRoutingState(NfcService.SECURE_ELEMENT_ESE_NAME, route);
            ApplyRoutingTask applyRoutingTask = new ApplyRoutingTask();
            applyRoutingTask.execute(new Integer[NfcService.SOUND_START]);
            try {
                applyRoutingTask.get();
            } catch (ExecutionException e) {
                Log.e(NfcService.TAG, "failed to set card emulation mode");
            } catch (InterruptedException e2) {
                Log.e(NfcService.TAG, "failed to set card emulation mode");
            }
        }

        public void authenticate(String pkg, byte[] token) throws RemoteException {
            NfcService.this.enforceNfceeAdminPerm(pkg);
        }

        public String getDriverName(String pkg) throws RemoteException {
            NfcService.this.enforceNfceeAdminPerm(pkg);
            return NfcService.this.mDeviceHost.getName();
        }
    }

    final class NfcAdapterService extends INfcAdapter.Stub {
        NfcAdapterService() {
        }

        public boolean enable() throws RemoteException {
            NfcService.enforceAdminPerm(NfcService.this.mContext);
            EnterpriseDeviceManager edm = (EnterpriseDeviceManager) NfcService.this.mContext.getSystemService("enterprise_policy");
            int val = NfcService.this.mDeviceHost.GetDefaultSE();
            if (NfcService.DBG) {
                Log.i(NfcService.TAG, "getDefaultSE " + val);
            }
            if (edm != null && !edm.getRestrictionPolicy().isNFCEnabled()) {
                Log.e(NfcService.TAG, "EDM : nfc policy disabled. can't enable it ");
                return NfcService.NFC_ON_READER_DEFAULT;
            } else if (!DeviceSettingsPolicy.getInstance(NfcService.this.mContext).isNFCStateChangeAllowed()) {
                return NfcService.NFC_ON_READER_DEFAULT;
            } else {
                NfcService.this.saveNfcOnSetting(NfcService.SE_BROADCASTS_WITH_HCE);
                if (NfcService.this.mIsAirplaneSensitive && NfcService.this.isAirplaneModeOn()) {
                    if (NfcService.this.mIsAirplaneToggleable) {
                        NfcService.this.mPrefsEditor.putBoolean(NfcService.PREF_AIRPLANE_OVERRIDE, NfcService.SE_BROADCASTS_WITH_HCE);
                        NfcService.this.mPrefsEditor.apply();
                    } else {
                        Log.i(NfcService.TAG, "denying enable() request (airplane mode)");
                        return NfcService.NFC_ON_READER_DEFAULT;
                    }
                }
                AsyncTask enableDisableTask = new EnableDisableTask();
                Integer[] numArr = new Integer[NfcService.TASK_ENABLE];
                numArr[NfcService.SOUND_START] = Integer.valueOf(NfcService.TASK_ENABLE);
                enableDisableTask.execute(numArr);
                return NfcService.SE_BROADCASTS_WITH_HCE;
            }
        }

        public boolean disable(boolean saveState) throws RemoteException {
            NfcService.enforceAdminPerm(NfcService.this.mContext);
            if (!DeviceSettingsPolicy.getInstance(NfcService.this.mContext).isNFCStateChangeAllowed()) {
                return NfcService.NFC_ON_READER_DEFAULT;
            }
            String shutdown = SystemProperties.get("sys.deviceOffReq", "0");
            String reason = SystemProperties.get("sys.shutdown.requested", "0");
            if (NfcService.DBG) {
                Log.i(NfcService.TAG, "shutdown : " + shutdown + "reason : " + reason);
            }
            if (!NfcService.this.mPowerShutDown) {
                if (!"NXP_PN544C3".equals("NXP_PN544C3")) {
                    NfcService.this.mDeviceHost.doDeselectSecureElement(NfcService.UICC_ID_TYPE);
                    NfcService.this.mDeviceHost.doDeselectSecureElement(NfcService.SMART_MX_ID_TYPE);
                } else if (!"ROUTE_ON_ALWAYS".equalsIgnoreCase(CscFeature.getInstance().getString("CscFeature_NFC_CardModeRoutingTypeForUicc").toUpperCase())) {
                    NfcService.this.deSelectSecureElement();
                }
            }
            if (saveState) {
                NfcService.this.saveNfcOnSetting(NfcService.NFC_ON_READER_DEFAULT);
            }
            if ("BCM2079x".equals("NXP_PN544C3")) {
                if ("1".equals(shutdown) && ("0".equals(reason) || "0no power".equals(reason))) {
                    Log.e(NfcService.TAG, "setPowerMode is called while shutdown is on going - for brcm");
                    NfcService.this.mBrcmPowerMode.setPowerMode(NfcService.TASK_ENABLE);
                    try {
                        Thread.sleep(1600);
                    } catch (InterruptedException e) {
                    }
                }
            } else if (("S3FNRN3".equals("NXP_PN544C3") || "S3FWRN5".equals("NXP_PN544C3")) && "1".equals(shutdown) && ("0".equals(reason) || "0no power".equals(reason))) {
                try {
                    Log.d(NfcService.TAG, "S LSI PowerOff mode - HCE off");
                    NfcService.this.mAidRoutingManager.disableAidsRoutedToHost();
                    NfcService.this.enableRouteToHost(NfcService.NFC_ON_READER_DEFAULT);
                    Thread.sleep(1600);
                } catch (InterruptedException e2) {
                }
            }
            AsyncTask enableDisableTask = new EnableDisableTask();
            Integer[] numArr = new Integer[NfcService.TASK_ENABLE];
            numArr[NfcService.SOUND_START] = Integer.valueOf(NfcService.VEN_CFG_NFC_OFF_POWER_OFF);
            enableDisableTask.execute(numArr);
            return NfcService.SE_BROADCASTS_WITH_HCE;
        }

        public boolean isNdefPushEnabled() throws RemoteException {
            boolean z;
            synchronized (NfcService.this) {
                z = (NfcService.this.mState == NfcService.VEN_CFG_NFC_ON_POWER_ON && NfcService.this.mIsNdefPushEnabled) ? NfcService.SE_BROADCASTS_WITH_HCE : NfcService.NFC_ON_READER_DEFAULT;
            }
            return z;
        }

        public boolean enableNdefPush() throws RemoteException {
            NfcService.enforceAdminPerm(NfcService.this.mContext);
            synchronized (NfcService.this) {
                if (NfcService.this.mIsNdefPushEnabled) {
                } else {
                    Log.i(NfcService.TAG, "enabling NDEF Push");
                    NfcService.this.mPrefsEditor.putBoolean(NfcService.PREF_NDEF_PUSH_ON, NfcService.SE_BROADCASTS_WITH_HCE);
                    NfcService.this.mPrefsEditor.apply();
                    NfcService.this.mIsNdefPushEnabled = NfcService.SE_BROADCASTS_WITH_HCE;
                    if (NfcService.this.isNfcEnabled()) {
                        NfcService.this.mP2pLinkManager.enableDisable(NfcService.SE_BROADCASTS_WITH_HCE, NfcService.SE_BROADCASTS_WITH_HCE);
                    }
                    NfcService.this.mP2pLinkManager.sendBeamChangeIntent(NfcService.SE_BROADCASTS_WITH_HCE);
                }
            }
            return NfcService.SE_BROADCASTS_WITH_HCE;
        }

        public boolean disableNdefPush() throws RemoteException {
            NfcService.enforceAdminPerm(NfcService.this.mContext);
            synchronized (NfcService.this) {
                if (NfcService.this.mIsNdefPushEnabled) {
                    Log.i(NfcService.TAG, "disabling NDEF Push");
                    NfcService.this.mPrefsEditor.putBoolean(NfcService.PREF_NDEF_PUSH_ON, NfcService.NFC_ON_READER_DEFAULT);
                    NfcService.this.mPrefsEditor.apply();
                    NfcService.this.mIsNdefPushEnabled = NfcService.NFC_ON_READER_DEFAULT;
                    if (NfcService.this.isNfcEnabled()) {
                        NfcService.this.mP2pLinkManager.enableDisable(NfcService.NFC_ON_READER_DEFAULT, NfcService.SE_BROADCASTS_WITH_HCE);
                    }
                    NfcService.this.mP2pLinkManager.sendBeamChangeIntent(NfcService.NFC_ON_READER_DEFAULT);
                }
            }
            return NfcService.SE_BROADCASTS_WITH_HCE;
        }

        public void setForegroundDispatch(PendingIntent intent, IntentFilter[] filters, TechListParcel techListsParcel) {
            NfcService.this.mContext.enforceCallingOrSelfPermission(NfcService.NFC_PERM, NfcService.NFC_PERM_ERROR);
            if (intent == null && filters == null && techListsParcel == null) {
                NfcService.this.mNfcDispatcher.setForegroundDispatch(null, null, (String[][]) null);
                return;
            }
            if (filters != null) {
                if (filters.length == 0) {
                    filters = null;
                } else {
                    IntentFilter[] arr$ = filters;
                    int len$ = arr$.length;
                    for (int i$ = NfcService.SOUND_START; i$ < len$; i$ += NfcService.TASK_ENABLE) {
                        if (arr$[i$] == null) {
                            throw new IllegalArgumentException("null IntentFilter");
                        }
                    }
                }
            }
            String[][] techLists = null;
            if (techListsParcel != null) {
                techLists = techListsParcel.getTechLists();
            }
            NfcService.this.mNfcDispatcher.setForegroundDispatch(intent, filters, techLists);
        }

        public void setAppCallback(IAppCallback callback) {
            NfcService.this.mContext.enforceCallingOrSelfPermission(NfcService.NFC_PERM, NfcService.NFC_PERM_ERROR);
            NfcService.this.mP2pLinkManager.setNdefCallback(callback, Binder.getCallingUid());
        }

        public INfcTag getNfcTagInterface() throws RemoteException {
            return NfcService.this.mNfcTagService;
        }

        public INfcAdapterExtras getNfcAdapterExtrasInterface(String pkg) {
            NfcService.this.enforceNfceeAdminPerm(pkg);
            return NfcService.this.mExtrasService;
        }

        public INfcCardEmulation getNfcCardEmulationInterface() {
            return NfcService.this.mCardEmulationService;
        }

        public int getState() throws RemoteException {
            int i;
            synchronized (NfcService.this) {
                i = NfcService.this.mState;
            }
            return i;
        }

        protected void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
            NfcService.this.dump(fd, pw, args);
        }

        public int deselectSecureElement() throws RemoteException {
            NfcService.this.mContext.enforceCallingOrSelfPermission(NfcService.ADMIN_PERM, NfcService.ADMIN_PERM_ERROR);
            if (!NfcService.this.isNfcEnabled()) {
                return -17;
            }
            if (NfcService.this.mSelectedSeId == 0) {
                return -20;
            }
            if (NfcService.this.mSelectedSeId != NfcService.VEN_CFG_NFC_ON_POWER_ON) {
                NfcService.this.mDeviceHost.doDeselectSecureElement(NfcService.this.mSelectedSeId);
            } else {
                int[] Se_list = NfcService.this.mDeviceHost.doGetSecureElementList();
                for (int i = NfcService.SOUND_START; i < Se_list.length; i += NfcService.TASK_ENABLE) {
                    NfcService.this.mDeviceHost.doDeselectSecureElement(Se_list[i]);
                }
            }
            NfcService.this.mNfcSecureElementState = NfcService.NFC_ON_READER_DEFAULT;
            NfcService.this.mSelectedSeId = NfcService.SOUND_START;
            NfcService.this.mPrefsEditor.putBoolean(NfcService.PREF_SECURE_ELEMENT_ON, NfcService.NFC_ON_READER_DEFAULT);
            NfcService.this.mPrefsEditor.putInt(NfcService.PREF_SECURE_ELEMENT_ID, NfcService.SOUND_START);
            NfcService.this.mPrefsEditor.apply();
            return NfcService.SOUND_START;
        }

        public int[] getSecureElementList() throws RemoteException {
            NfcService.this.mContext.enforceCallingOrSelfPermission(NfcService.ADMIN_PERM, NfcService.ADMIN_PERM_ERROR);
            if (NfcService.this.isNfcEnabled()) {
                return NfcService.this.mDeviceHost.doGetSecureElementList();
            }
            return null;
        }

        public int getSelectedSecureElement() throws RemoteException {
            NfcService.this.mContext.enforceCallingOrSelfPermission(NfcService.ADMIN_PERM, NfcService.ADMIN_PERM_ERROR);
            return NfcService.this.mSelectedSeId;
        }

        public INfcSecureElement getNfcSecureElementInterface() {
            if (!"NXP_PN544C3".equals("NXP_PN544C3")) {
                return NfcService.this.mSecureElementService;
            }
            NfcService.this.mContext.enforceCallingOrSelfPermission(NfcService.ADMIN_PERM, NfcService.ADMIN_PERM_ERROR);
            if (NfcService.this.mSecureElementService == null) {
                NfcService.this.mSecureElementService = new NfcSecureElementService();
            }
            return NfcService.this.mSecureElementService;
        }

        public void storeSePreference(int seId) {
            NfcService.this.mContext.enforceCallingOrSelfPermission(NfcService.ADMIN_PERM, NfcService.ADMIN_PERM_ERROR);
            Log.d(NfcService.TAG, "SE Preference stored");
            NfcService.this.mPrefsEditor.putBoolean(NfcService.PREF_SECURE_ELEMENT_ON, NfcService.SE_BROADCASTS_WITH_HCE);
            NfcService.this.mPrefsEditor.putInt(NfcService.PREF_SECURE_ELEMENT_ID, seId);
            NfcService.this.mPrefsEditor.apply();
        }

        public int selectSecureElement(int seId) throws RemoteException {
            NfcService.this.mContext.enforceCallingOrSelfPermission(NfcService.ADMIN_PERM, NfcService.ADMIN_PERM_ERROR);
            if (!NfcService.this.isNfcEnabled()) {
                return -17;
            }
            if (NfcService.this.mSelectedSeId == seId) {
                return -18;
            }
            if (NfcService.this.mSelectedSeId != 0) {
                return -19;
            }
            int[] Se_list = NfcService.this.mDeviceHost.doGetSecureElementList();
            NfcService.this.mSelectedSeId = seId;
            if (seId != NfcService.VEN_CFG_NFC_ON_POWER_ON) {
                NfcService.this.mDeviceHost.doSelectSecureElement(NfcService.this.mSelectedSeId);
            } else if (Se_list.length > NfcService.TASK_ENABLE) {
                for (int i = NfcService.SOUND_START; i < Se_list.length; i += NfcService.TASK_ENABLE) {
                    NfcService.this.mDeviceHost.doSelectSecureElement(Se_list[i]);
                    try {
                        Thread.sleep(200);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
            NfcService.this.mPrefsEditor.putBoolean(NfcService.PREF_SECURE_ELEMENT_ON, NfcService.SE_BROADCASTS_WITH_HCE);
            NfcService.this.mPrefsEditor.putInt(NfcService.PREF_SECURE_ELEMENT_ID, NfcService.this.mSelectedSeId);
            NfcService.this.mPrefsEditor.apply();
            NfcService.this.mNfcSecureElementState = NfcService.SE_BROADCASTS_WITH_HCE;
            return NfcService.SOUND_START;
        }

        public void dispatch(Tag tag) throws RemoteException {
            NfcService.enforceAdminPerm(NfcService.this.mContext);
            NfcService.this.mNfcDispatcher.dispatchTag(tag);
        }

        public void setP2pModes(int initiatorModes, int targetModes) throws RemoteException {
            NfcService.enforceAdminPerm(NfcService.this.mContext);
            NfcService.this.mDeviceHost.setP2pInitiatorModes(initiatorModes);
            NfcService.this.mDeviceHost.setP2pTargetModes(targetModes);
            NfcService.this.mDeviceHost.disableDiscovery();
            NfcService.this.mDeviceHost.enableDiscovery();
        }

        public INfcUtility getNfcUtilityInterface() {
            return new NfcUtilityService();
        }

        public INfcSetting getNfcSettingInterface() {
            NfcService.this.mContext.enforceCallingOrSelfPermission(NfcService.NFC_PERM, NfcService.NFC_PERM_ERROR);
            return NfcService.this.mNfcSetting;
        }

        public void setReaderMode(IBinder binder, IAppCallback callback, int flags, Bundle extras) throws RemoteException {
            int i = NfcService.DEFAULT_PRESENCE_CHECK_DELAY;
            NfcService.this.mContext.enforceCallingOrSelfPermission(NfcService.NFC_PERM, NfcService.NFC_PERM_ERROR);
            synchronized (NfcService.this) {
                if (flags != 0) {
                    try {
                        NfcService.this.mReaderModeParams = new ReaderModeParams();
                        NfcService.this.mReaderModeParams.callback = callback;
                        NfcService.this.mReaderModeParams.flags = flags;
                        ReaderModeParams readerModeParams = NfcService.this.mReaderModeParams;
                        if (extras != null) {
                            i = extras.getInt("presence", NfcService.DEFAULT_PRESENCE_CHECK_DELAY);
                        }
                        readerModeParams.presenceCheckDelay = i;
                        binder.linkToDeath(NfcService.this.mReaderModeDeathRecipient, NfcService.SOUND_START);
                    } catch (RemoteException e) {
                        Log.e(NfcService.TAG, "Remote binder has already died.");
                        return;
                    }
                }
                try {
                    NfcService.this.mReaderModeParams = null;
                    binder.unlinkToDeath(NfcService.this.mReaderModeDeathRecipient, NfcService.SOUND_START);
                } catch (NoSuchElementException e2) {
                    Log.e(NfcService.TAG, "Reader mode Binder was never registered.");
                }
                if (NfcService.DBG) {
                    Log.d(NfcService.TAG, "applyRouting #3");
                }
                NfcService.this.applyRouting(NfcService.NFC_ON_READER_DEFAULT);
            }
        }

        public boolean readerEnable() throws RemoteException {
            NfcService.enforceAdminPerm(NfcService.this.mContext);
            NfcService.this.saveNfcReaderOnSetting(NfcService.SE_BROADCASTS_WITH_HCE);
            if (NfcService.this.mIsAirplaneSensitive && NfcService.this.isAirplaneModeOn() && !NfcService.this.mIsAirplaneToggleable) {
                Log.i(NfcService.TAG, "denying readerEnable() request (airplane mode)");
                return NfcService.NFC_ON_READER_DEFAULT;
            } else if (NfcService.this.mChnEnablePopupExist == NfcService.SE_BROADCASTS_WITH_HCE) {
                Log.i(NfcService.TAG, "readerEnable() mChnEnablePopupExist!!");
                return NfcService.NFC_ON_READER_DEFAULT;
            } else {
                AsyncTask enableDisableTask = new EnableDisableTask();
                Integer[] numArr = new Integer[NfcService.TASK_ENABLE];
                numArr[NfcService.SOUND_START] = Integer.valueOf(NfcService.TASK_READER_ENABLE);
                enableDisableTask.execute(numArr);
                return NfcService.SE_BROADCASTS_WITH_HCE;
            }
        }

        public boolean readerDisable() throws RemoteException {
            NfcService.enforceAdminPerm(NfcService.this.mContext);
            NfcService.this.saveNfcReaderOnSetting(NfcService.NFC_ON_READER_DEFAULT);
            AsyncTask enableDisableTask = new EnableDisableTask();
            Integer[] numArr = new Integer[NfcService.TASK_ENABLE];
            numArr[NfcService.SOUND_START] = Integer.valueOf(NfcService.TASK_READER_DISABLE);
            enableDisableTask.execute(numArr);
            return NfcService.SE_BROADCASTS_WITH_HCE;
        }

        public boolean enableSecNdef(boolean enable) {
            NfcService.mIsSecNdefEnabled = enable;
            return NfcService.mIsSecNdefEnabled;
        }

        public boolean isSecNdefEnabled() {
            return NfcService.mIsSecNdefEnabled;
        }

        public int createSecNdefService(String serviceName, int SSAP, String pkgName, byte[] type, byte[] id) throws RemoteException {
            int i = NfcService.EE_ERROR_IO;
            if (isSecNdefEnabled() && (NfcService.this.mState == NfcService.VEN_CFG_NFC_ON_POWER_ON || NfcService.this.mState == NfcService.TASK_READER_ENABLE)) {
                i = NfcService.SOUND_START;
                try {
                    i = NfcService.this.mP2pLinkManager.createSecNdefService(serviceName, SSAP, pkgName, type, id);
                } catch (Exception e) {
                }
            }
            return i;
        }

        public int closeSecNdefService(int secNdefServiceId) throws RemoteException {
            if (isSecNdefEnabled() && NfcService.this.isNfcEnabled()) {
                return NfcService.this.mP2pLinkManager.closeSecNdefService(secNdefServiceId);
            }
            return NfcService.EE_ERROR_IO;
        }

        public int sendSecNdefMsg(int secNdefServiceId, NdefMessage msg) throws RemoteException {
            if (isSecNdefEnabled() && NfcService.this.isNfcEnabled()) {
                return NfcService.this.mP2pLinkManager.secSendNdefMsg(secNdefServiceId, msg);
            }
            return NfcService.EE_ERROR_IO;
        }

        public int sendSecDefaultNdefMsg(NdefMessage msg) throws RemoteException {
            if (isSecNdefEnabled() && NfcService.this.isNfcEnabled()) {
                return NfcService.this.mP2pLinkManager.secSendAbeamNdefMsg(msg);
            }
            return NfcService.EE_ERROR_IO;
        }

        public void enableDisableSeTestMode(String SE, boolean enable) throws RemoteException {
            if ("NXP_PN544C3".equals("NXP_PN544C3")) {
                int seID;
                if (SE.equals(NfcService.SECURE_ELEMENT_UICC_NAME)) {
                    seID = NfcService.VEN_CFG_NFC_OFF_POWER_OFF;
                } else if (SE.equals(NfcService.HCE_DEVICE_HOST_NAME)) {
                    seID = NfcService.SOUND_START;
                } else {
                    seID = NfcService.TASK_ENABLE;
                }
                Log.d(NfcService.TAG, "PN544C3 setDefaultRoutingDestination SE string " + SE);
                NfcService.this.mSamsungPref.putString(NfcService.PREFS_DEFAULT_ROUTE_SETTING, SE.toUpperCase());
                NfcService.this.mPrefsEditor.putInt(NfcService.PREF_DEFAULT_ROUTE_ID, seID);
                NfcService.this.mPrefsEditor.apply();
                AsyncTask enableDisableTask = new EnableDisableTask();
                Integer[] numArr = new Integer[NfcService.TASK_ENABLE];
                numArr[NfcService.SOUND_START] = Integer.valueOf(NfcService.TASK_ROUTING_CHANGED);
                enableDisableTask.execute(numArr);
            } else if (enable) {
                if (NfcService.DBG) {
                    Log.d(NfcService.TAG, "enable test mode");
                }
                NfcService.this.mSamsungPref.putString(NfcService.PREFS_ROUTE_TO_DEFAULT, SE.toUpperCase());
                NfcService.this.mCardEmulationRoutingManager.enableTestMode(SE, enable);
                NfcService.this.mCardEmulationRoutingManager.upateRouting(NfcService.SE_BROADCASTS_WITH_HCE);
            } else {
                if (NfcService.DBG) {
                    Log.d(NfcService.TAG, "disable test mode");
                }
                NfcService.this.mSamsungPref.remove(NfcService.PREFS_ROUTE_TO_DEFAULT);
                NfcService.this.mCardEmulationRoutingManager.enableTestMode(SE, enable);
            }
        }

        public void setDefaultRoutingDestination(String SE) throws RemoteException {
            int seID = NfcService.SOUND_START;
            if ("NXP_PN544C3".equals("NXP_PN544C3")) {
                if (SE.equals(NfcService.SECURE_ELEMENT_UICC_NAME)) {
                    seID = NfcService.VEN_CFG_NFC_OFF_POWER_OFF;
                } else if (SE.equals(NfcService.HCE_DEVICE_HOST_NAME)) {
                    seID = NfcService.SOUND_START;
                } else {
                    seID = NfcService.TASK_ENABLE;
                }
                Log.d(NfcService.TAG, "setDefaultRoutingDestination SE string " + SE);
            }
            if (!CscFeature.getInstance().getString("CscFeature_NFC_ConfigAdvancedSettings", "ENABLE").toUpperCase().equalsIgnoreCase("DISABLE")) {
                NfcService.this.mSamsungPref.putString(NfcService.PREFS_DEFAULT_ROUTE_SETTING, SE.toUpperCase());
                if ("NXP_PN544C3".equals("NXP_PN544C3")) {
                    NfcService.this.mPrefsEditor.putInt(NfcService.PREF_DEFAULT_ROUTE_ID, seID);
                    NfcService.this.mPrefsEditor.apply();
                }
                AsyncTask enableDisableTask = new EnableDisableTask();
                Integer[] numArr = new Integer[NfcService.TASK_ENABLE];
                numArr[NfcService.SOUND_START] = Integer.valueOf(NfcService.TASK_ROUTING_CHANGED);
                enableDisableTask.execute(numArr);
            } else if (NfcService.DBG) {
                Log.d(NfcService.TAG, "do not support advanced setting menu");
            }
        }

        public String getDefaultRoutingDestination() throws RemoteException {
            return NfcService.this.mSamsungPref.getString(NfcService.PREFS_DEFAULT_ROUTE_SETTING, NfcService.HCE_DEVICE_HOST_NAME);
        }

        public boolean changeDefaultRoute(int defaultRoute) throws RemoteException {
            if (NfcService.this.getDefaultRoute() == defaultRoute) {
                return NfcService.NFC_ON_READER_DEFAULT;
            }
            NfcService.this.mPrefsEditor.putInt(NfcService.PREF_DEFAULT_ROUTE_ID, defaultRoute);
            NfcService.this.mPrefsEditor.apply();
            boolean result = NfcService.this.mDeviceHost.clearAidTable();
            NfcService.this.mAidCache.onNfcEnabled();
            return result;
        }

        public boolean setFilterList(byte[] filterList) throws RemoteException {
            NfcService.enforceAdminPerm(NfcService.this.mContext);
            return NfcService.NFC_ON_READER_DEFAULT;
        }

        public boolean enableFilterCondition(byte filterConditionTag) throws RemoteException {
            NfcService.enforceAdminPerm(NfcService.this.mContext);
            return NfcService.NFC_ON_READER_DEFAULT;
        }

        public boolean disableFilterCondition(byte filterConditionTag) throws RemoteException {
            NfcService.enforceAdminPerm(NfcService.this.mContext);
            return NfcService.NFC_ON_READER_DEFAULT;
        }
    }

    final class NfcControllerService extends INfcController.Stub {
        NfcControllerService() {
        }

        public boolean isEnabled() throws RemoteException {
            if (NfcService.this.isGsmaApiSupported) {
                return NfcService.this.isNfcEnabled();
            }
            if (NfcService.DBG) {
                Log.e(NfcService.TAG, "Gsma Apis are not Supported in this project");
            }
            return NfcService.NFC_ON_READER_DEFAULT;
        }

        public int enableNfcController(ICallbacks cb) throws RemoteException {
            if (NfcService.this.isGsmaApiSupported) {
                if (NfcService.DBG) {
                    Log.i(NfcService.TAG, "enableNfcController");
                }
                if (NfcService.this.mSEControllerService == null) {
                    return NfcService.EE_ERROR_INIT;
                }
                try {
                    if (!NfcService.this.mHciEventControl.isAllowedForGsma("SIM")) {
                        return NfcService.EE_ERROR_INIT;
                    }
                    AsyncTask enableDisableTask = new EnableDisableTask(cb);
                    Integer[] numArr = new Integer[NfcService.TASK_ENABLE];
                    numArr[NfcService.SOUND_START] = Integer.valueOf(NfcService.TASK_ENABLE);
                    enableDisableTask.execute(numArr);
                    return NfcService.SOUND_START;
                } catch (RemoteException e) {
                    if (!NfcService.DBG) {
                        return NfcService.EE_ERROR_INIT;
                    }
                    Log.e(NfcService.TAG, "Checking CDF failed.");
                    return NfcService.EE_ERROR_INIT;
                } catch (NullPointerException e2) {
                    if (!NfcService.DBG) {
                        return NfcService.EE_ERROR_INIT;
                    }
                    Log.e(NfcService.TAG, "mHciEventControl is null");
                    return NfcService.EE_ERROR_INIT;
                }
            }
            if (NfcService.DBG) {
                Log.e(NfcService.TAG, "Gsma Apis are not Supported in this project");
            }
            return NfcService.EE_ERROR_IO;
        }

        public boolean isCardEmulationEnabled() throws RemoteException {
            if (NfcService.this.isGsmaApiSupported) {
                if (NfcService.DBG) {
                    Log.i(NfcService.TAG, "isCardEmulationEnabled");
                }
                if (!NfcService.this.isNfcEnabled() || NfcService.this.mEeRoutingState < NfcService.VEN_CFG_NFC_OFF_POWER_OFF) {
                    return NfcService.NFC_ON_READER_DEFAULT;
                }
                return NfcService.this.mNfcSecureElementState;
            } else if (!NfcService.DBG) {
                return NfcService.NFC_ON_READER_DEFAULT;
            } else {
                Log.e(NfcService.TAG, "Gsma Apis are not Supported in this project");
                return NfcService.NFC_ON_READER_DEFAULT;
            }
        }

        public int enableCardEmulationMode(ICallbacks cb) throws RemoteException {
            if (NfcService.this.isGsmaApiSupported) {
                if (NfcService.DBG) {
                    Log.i(NfcService.TAG, "enableCardEmulationMode");
                }
                if (NfcService.this.mSEControllerService == null) {
                    return NfcService.EE_ERROR_INIT;
                }
                if (!NfcService.this.isNfcEnabled()) {
                    return NfcService.EE_ERROR_ALREADY_OPEN;
                }
                try {
                    if (!NfcService.this.mHciEventControl.isAllowedForGsma(NfcService.this.mSEControllerService.getActiveSecureElement())) {
                        return NfcService.EE_ERROR_INIT;
                    }
                    if (!NfcService.this.isNfcEnabled()) {
                        return NfcService.EE_ERROR_ALREADY_OPEN;
                    }
                    NfcService nfcService = NfcService.this;
                    int access$3700 = NfcService.this.mSelectedSeId == NfcService.SECURE_ELEMENT_UICC_ID ? NfcService.this.mUiccRoutingMode : NfcService.this.mSelectedSeId == NfcService.SECURE_ELEMENT_ESE_ID ? NfcService.this.mEseRoutingMode : NfcService.TASK_ENABLE;
                    nfcService.mEeRoutingState = access$3700;
                    NfcService.this.mNfcSecureElementState = NfcService.SE_BROADCASTS_WITH_HCE;
                    new ApplyRoutingTask(cb).execute(new Integer[NfcService.SOUND_START]);
                    return NfcService.SOUND_START;
                } catch (RemoteException e) {
                    if (!NfcService.DBG) {
                        return NfcService.EE_ERROR_INIT;
                    }
                    Log.e(NfcService.TAG, "Checking CDF failed.");
                    return NfcService.EE_ERROR_INIT;
                } catch (NullPointerException e2) {
                    if (!NfcService.DBG) {
                        return NfcService.EE_ERROR_INIT;
                    }
                    Log.e(NfcService.TAG, "mHciEventControl is null");
                    return NfcService.EE_ERROR_INIT;
                }
            }
            if (NfcService.DBG) {
                Log.e(NfcService.TAG, "Gsma Apis are not Supported in this project");
            }
            return NfcService.EE_ERROR_IO;
        }

        public int disableCardEmulationMode(ICallbacks cb) throws RemoteException {
            if (NfcService.this.isGsmaApiSupported) {
                if (NfcService.DBG) {
                    Log.i(NfcService.TAG, "disableCardEmulationMode");
                }
                if (NfcService.this.mSEControllerService == null) {
                    return NfcService.EE_ERROR_INIT;
                }
                try {
                    if (!NfcService.this.mHciEventControl.isAllowedForGsma(NfcService.this.mSEControllerService.getActiveSecureElement())) {
                        return NfcService.EE_ERROR_INIT;
                    }
                    NfcService.this.mEeRoutingState = NfcService.TASK_ENABLE;
                    NfcService.this.mNfcSecureElementState = NfcService.NFC_ON_READER_DEFAULT;
                    new ApplyRoutingTask(cb).execute(new Integer[NfcService.SOUND_START]);
                    return NfcService.SOUND_START;
                } catch (RemoteException e) {
                    if (!NfcService.DBG) {
                        return NfcService.EE_ERROR_INIT;
                    }
                    Log.e(NfcService.TAG, "Checking CDF failed.");
                    return NfcService.EE_ERROR_INIT;
                } catch (NullPointerException e2) {
                    if (!NfcService.DBG) {
                        return NfcService.EE_ERROR_INIT;
                    }
                    Log.e(NfcService.TAG, "mHciEventControl is null");
                    return NfcService.EE_ERROR_INIT;
                }
            }
            if (NfcService.DBG) {
                Log.e(NfcService.TAG, "Gsma Apis are not Supported in this project");
            }
            return NfcService.EE_ERROR_IO;
        }
    }

    final class NfcSecureElementService extends INfcSecureElement.Stub {
        NfcSecureElementService() {
        }

        public int openSecureElementConnection() throws RemoteException {
            NfcService.this.mContext.enforceCallingOrSelfPermission(NfcService.ADMIN_PERM, NfcService.ADMIN_PERM_ERROR);
            Log.d(NfcService.TAG, "openSecureElementConnection");
            if (!"NXP_PN544C3".equals("NXP_PN544C3")) {
                return NfcService.SOUND_START;
            }
            if (!NfcService.this.isNfcEnabled()) {
                return NfcService.SOUND_START;
            }
            if (NfcService.this.mOpenSmxPending) {
                return NfcService.SOUND_START;
            }
            int handle = NfcService.this.mSecureElement.doOpenSecureElementConnection();
            if (handle == 0) {
                NfcService.this.mOpenSmxPending = NfcService.NFC_ON_READER_DEFAULT;
                return handle;
            }
            NfcService.this.mSecureElementHandle = handle;
            NfcService.this.mTimerOpenSmx = new Timer();
            NfcService.this.mTimerOpenSmx.schedule(new TimerOpenSecureElement(), 30000);
            NfcService.this.isOpened = NfcService.SE_BROADCASTS_WITH_HCE;
            NfcService.this.isClosed = NfcService.NFC_ON_READER_DEFAULT;
            NfcService.this.mOpenSmxPending = NfcService.SE_BROADCASTS_WITH_HCE;
            return handle;
        }

        public int closeSecureElementConnection(int nativeHandle) throws RemoteException {
            NfcService.this.mContext.enforceCallingOrSelfPermission(NfcService.ADMIN_PERM, NfcService.ADMIN_PERM_ERROR);
            if (!"NXP_PN544C3".equals("NXP_PN544C3")) {
                return NfcService.SOUND_START;
            }
            if (!NfcService.this.isNfcEnabled()) {
                return -17;
            }
            if (NfcService.this.isClosed || !NfcService.this.isOpened) {
                return NfcService.EE_ERROR_IO;
            }
            NfcService.this.applyRouting(NfcService.SE_BROADCASTS_WITH_HCE);
            if (NfcService.this.mSecureElement.doDisconnect(nativeHandle)) {
                NfcService.this.mTimerOpenSmx.cancel();
                NfcService.this.isOpened = NfcService.NFC_ON_READER_DEFAULT;
                NfcService.this.isClosed = NfcService.SE_BROADCASTS_WITH_HCE;
                NfcService.this.mOpenSmxPending = NfcService.NFC_ON_READER_DEFAULT;
                if (NfcService.this.mPollingLoopStarted) {
                    Log.d(NfcService.TAG, "Start Polling Loop");
                    NfcService.this.maybeEnableDiscovery();
                } else {
                    Log.d(NfcService.TAG, "Stop Polling Loop");
                    NfcService.this.maybeDisableDiscovery();
                }
                return NfcService.SOUND_START;
            }
            NfcService.this.mTimerOpenSmx.cancel();
            NfcService.this.isOpened = NfcService.NFC_ON_READER_DEFAULT;
            NfcService.this.isClosed = NfcService.SE_BROADCASTS_WITH_HCE;
            NfcService.this.mOpenSmxPending = NfcService.NFC_ON_READER_DEFAULT;
            if (NfcService.this.mPollingLoopStarted) {
                Log.d(NfcService.TAG, "Start Polling Loop");
                NfcService.this.maybeEnableDiscovery();
            } else {
                Log.d(NfcService.TAG, "Stop Polling Loop");
                NfcService.this.maybeDisableDiscovery();
            }
            return NfcService.EE_ERROR_EXT_FIELD;
        }

        public int[] getSecureElementTechList(int nativeHandle) throws RemoteException {
            NfcService.this.mContext.enforceCallingOrSelfPermission(NfcService.ADMIN_PERM, NfcService.ADMIN_PERM_ERROR);
            if (!"NXP_PN544C3".equals("NXP_PN544C3") || !NfcService.this.isNfcEnabled() || NfcService.this.isClosed || !NfcService.this.isOpened) {
                return null;
            }
            int[] techList = NfcService.this.mSecureElement.doGetTechList(nativeHandle);
            NfcService.this.mTimerOpenSmx.cancel();
            NfcService.this.mTimerOpenSmx = new Timer();
            NfcService.this.mTimerOpenSmx.schedule(new TimerOpenSecureElement(), 30000);
            return techList;
        }

        public byte[] getSecureElementUid(int nativeHandle) throws RemoteException {
            NfcService.this.mContext.enforceCallingOrSelfPermission(NfcService.ADMIN_PERM, NfcService.ADMIN_PERM_ERROR);
            if (!"NXP_PN544C3".equals("NXP_PN544C3") || !NfcService.this.isNfcEnabled() || NfcService.this.isClosed || !NfcService.this.isOpened) {
                return null;
            }
            byte[] uid = NfcService.this.mSecureElement.doGetUid(nativeHandle);
            NfcService.this.mTimerOpenSmx.cancel();
            NfcService.this.mTimerOpenSmx = new Timer();
            NfcService.this.mTimerOpenSmx.schedule(new TimerOpenSecureElement(), 30000);
            return uid;
        }

        public byte[] exchangeAPDU(int nativeHandle, byte[] data) throws RemoteException {
            NfcService.this.mContext.enforceCallingOrSelfPermission(NfcService.ADMIN_PERM, NfcService.ADMIN_PERM_ERROR);
            if (!"NXP_PN544C3".equals("NXP_PN544C3") || !NfcService.this.isNfcEnabled() || NfcService.this.isClosed || !NfcService.this.isOpened) {
                return null;
            }
            byte[] response = NfcService.this.mSecureElement.doTransceive(nativeHandle, data);
            NfcService.this.mTimerOpenSmx.cancel();
            NfcService.this.mTimerOpenSmx = new Timer();
            NfcService.this.mTimerOpenSmx.schedule(new TimerOpenSecureElement(), 30000);
            return response;
        }
    }

    final class NfcServiceHandler extends Handler {
        NfcServiceHandler() {
        }

        /* JADX WARNING: inconsistent code. */
        /* Code decompiled incorrectly, please refer to instructions dump. */
        public void handleMessage(android.os.Message r56) {
            /*
            r55 = this;
            r0 = r56;
            r3 = r0.what;
            switch(r3) {
                case 0: goto L_0x01b7;
                case 1: goto L_0x02ff;
                case 2: goto L_0x0561;
                case 3: goto L_0x0586;
                case 4: goto L_0x0610;
                case 7: goto L_0x0132;
                case 8: goto L_0x0641;
                case 9: goto L_0x0661;
                case 10: goto L_0x04af;
                case 11: goto L_0x0484;
                case 12: goto L_0x04f4;
                case 13: goto L_0x0681;
                case 14: goto L_0x06a1;
                case 15: goto L_0x0605;
                case 16: goto L_0x000f;
                case 17: goto L_0x0094;
                case 18: goto L_0x00df;
                case 19: goto L_0x02c7;
                case 20: goto L_0x07c0;
                case 40: goto L_0x0453;
                case 103: goto L_0x06c1;
                case 104: goto L_0x06cb;
                case 110: goto L_0x06d5;
                case 111: goto L_0x0724;
                case 112: goto L_0x0810;
                case 150: goto L_0x011b;
                case 151: goto L_0x00c0;
                case 201: goto L_0x0769;
                case 202: goto L_0x079b;
                case 203: goto L_0x07eb;
                default: goto L_0x0007;
            };
        L_0x0007:
            r3 = "NfcService";
            r4 = "Unknown message received";
            android.util.Log.e(r3, r4);
        L_0x000e:
            return;
        L_0x000f:
            r0 = r56;
            r8 = r0.arg1;
            r0 = r56;
            r0 = r0.arg2;
            r44 = r0;
            r0 = r56;
            r15 = r0.obj;
            r15 = (java.lang.String) r15;
            r3 = "BCM2079x";
            r4 = "NXP_PN544C3";
            r3 = r3.equals(r4);
            if (r3 == 0) goto L_0x0076;
        L_0x0029:
            r0 = r55;
            r3 = com.android.nfc.NfcService.this;
            r3 = r3.mCardEmulationRoutingManager;
            r13 = r3.getSeName(r8);
            r0 = r55;
            r3 = com.android.nfc.NfcService.this;
            r3 = r3.mCardEmulationRoutingManager;
            r48 = r3.getScreenState(r13);
            r0 = r55;
            r3 = com.android.nfc.NfcService.this;
            r3 = r3.mCardEmulationRoutingManager;
            r45 = r3.getPowerState(r13);
            r3 = r48 & 8;
            if (r3 == 0) goto L_0x0088;
        L_0x0051:
            r5 = 1;
        L_0x0052:
            r3 = r48 & 2;
            if (r3 == 0) goto L_0x008a;
        L_0x0056:
            r6 = 1;
        L_0x0057:
            r3 = r48 & 4;
            if (r3 == 0) goto L_0x008c;
        L_0x005b:
            r7 = 1;
        L_0x005c:
            r3 = r45 & 1;
            if (r3 == 0) goto L_0x008e;
        L_0x0060:
            r9 = 1;
        L_0x0061:
            r3 = r45 & 2;
            if (r3 == 0) goto L_0x0090;
        L_0x0065:
            r10 = 1;
        L_0x0066:
            r3 = r45 & 4;
            if (r3 == 0) goto L_0x0092;
        L_0x006a:
            r11 = 1;
        L_0x006b:
            r0 = r55;
            r3 = com.android.nfc.NfcService.this;
            r4 = com.android.nfc.NfcService.hexStringToBytes(r15);
            r3.setHceOffHostAidRoute(r4, r5, r6, r7, r8, r9, r10, r11);
        L_0x0076:
            r0 = r55;
            r3 = com.android.nfc.NfcService.this;
            r3 = r3.mDeviceHost;
            r4 = com.android.nfc.NfcService.hexStringToBytes(r15);
            r0 = r44;
            r3.routeAid(r4, r8, r0);
            goto L_0x000e;
        L_0x0088:
            r5 = 0;
            goto L_0x0052;
        L_0x008a:
            r6 = 0;
            goto L_0x0057;
        L_0x008c:
            r7 = 0;
            goto L_0x005c;
        L_0x008e:
            r9 = 0;
            goto L_0x0061;
        L_0x0090:
            r10 = 0;
            goto L_0x0066;
        L_0x0092:
            r11 = 0;
            goto L_0x006b;
        L_0x0094:
            r0 = r56;
            r15 = r0.obj;
            r15 = (java.lang.String) r15;
            r3 = "BCM2079x";
            r4 = "NXP_PN544C3";
            r3 = r3.equals(r4);
            if (r3 == 0) goto L_0x00af;
        L_0x00a4:
            r0 = r55;
            r3 = com.android.nfc.NfcService.this;
            r4 = com.android.nfc.NfcService.hexStringToBytes(r15);
            r3.removeHceOffHostAidRoute(r4);
        L_0x00af:
            r0 = r55;
            r3 = com.android.nfc.NfcService.this;
            r3 = r3.mDeviceHost;
            r4 = com.android.nfc.NfcService.hexStringToBytes(r15);
            r3.unrouteAid(r4);
            goto L_0x000e;
        L_0x00c0:
            r3 = "NXP_PN544C3";
            r4 = "NXP_PN544C3";
            r3 = r3.equals(r4);
            if (r3 == 0) goto L_0x00df;
        L_0x00ca:
            r0 = r56;
            r0 = r0.arg1;
            r27 = r0;
            r0 = r55;
            r3 = com.android.nfc.NfcService.this;
            r3 = r3.mDeviceHost;
            r0 = r27;
            r3.SetFilterTag(r0);
            goto L_0x000e;
        L_0x00df:
            r3 = "NXP_PN544C3";
            r4 = "NXP_PN544C3";
            r3 = r3.equals(r4);
            if (r3 == 0) goto L_0x0101;
        L_0x00e9:
            r0 = r55;
            r3 = com.android.nfc.NfcService.this;
            r4 = 1;
            r3.mIsRoutingTableDirty = r4;
            r3 = "NfcService";
            r4 = "MSG_COMMIT_ROUTING";
            android.util.Log.d(r3, r4);
            r0 = r55;
            r3 = com.android.nfc.NfcService.this;
            r4 = 1;
            r3.applyRouting(r4);
            goto L_0x000e;
        L_0x0101:
            r0 = r55;
            r3 = com.android.nfc.NfcService.this;
            r4 = 3;
            r3.mSeActivationState = r4;
            r0 = r55;
            r3 = com.android.nfc.NfcService.this;
            r4 = 1;
            r3.applyRouting(r4);
            r0 = r55;
            r3 = com.android.nfc.NfcService.this;
            r4 = 1;
            r3.mSeActivationState = r4;
            goto L_0x000e;
        L_0x011b:
            r3 = "NXP_PN544C3";
            r4 = "NXP_PN544C3";
            r3 = r3.equals(r4);
            if (r3 == 0) goto L_0x0132;
        L_0x0125:
            r0 = r55;
            r3 = com.android.nfc.NfcService.this;
            r3 = r3.mDeviceHost;
            r3.clearRouting();
            goto L_0x000e;
        L_0x0132:
            r0 = r56;
            r0 = r0.obj;
            r40 = r0;
            r40 = (android.nfc.NdefMessage) r40;
            r32 = new android.os.Bundle;
            r32.<init>();
            r3 = "ndefmsg";
            r0 = r32;
            r1 = r40;
            r0.putParcelable(r3, r1);
            r3 = "ndefmaxlength";
            r4 = 0;
            r0 = r32;
            r0.putInt(r3, r4);
            r3 = "ndefcardstate";
            r4 = 1;
            r0 = r32;
            r0.putInt(r3, r4);
            r3 = "ndeftype";
            r4 = -1;
            r0 = r32;
            r0.putInt(r3, r4);
            r3 = 1;
            r3 = new byte[r3];
            r4 = 0;
            r53 = 0;
            r3[r4] = r53;
            r4 = 1;
            r4 = new int[r4];
            r53 = 0;
            r54 = 6;
            r4[r53] = r54;
            r53 = 1;
            r0 = r53;
            r0 = new android.os.Bundle[r0];
            r53 = r0;
            r54 = 0;
            r53[r54] = r32;
            r0 = r53;
            r50 = android.nfc.Tag.createMockTag(r3, r4, r0);
            r3 = "NfcService";
            r4 = "mock NDEF tag, starting corresponding activity";
            android.util.Log.d(r3, r4);
            r3 = "NfcService";
            r4 = r50.toString();
            android.util.Log.d(r3, r4);
            r0 = r55;
            r3 = com.android.nfc.NfcService.this;
            r3 = r3.mNfcDispatcher;
            r0 = r50;
            r23 = r3.dispatchTag(r0);
            if (r23 == 0) goto L_0x01ad;
        L_0x01a3:
            r0 = r55;
            r3 = com.android.nfc.NfcService.this;
            r4 = 1;
            r3.playSound(r4);
            goto L_0x000e;
        L_0x01ad:
            r0 = r55;
            r3 = com.android.nfc.NfcService.this;
            r4 = 2;
            r3.playSound(r4);
            goto L_0x000e;
        L_0x01b7:
            r3 = com.android.nfc.NfcService.DBG;
            if (r3 == 0) goto L_0x01c2;
        L_0x01bb:
            r3 = "NfcService";
            r4 = "Tag detected, notifying applications";
            android.util.Log.d(r3, r4);
        L_0x01c2:
            r0 = r56;
            r0 = r0.obj;
            r50 = r0;
            r50 = (com.android.nfc.DeviceHost.TagEndpoint) r50;
            r47 = 0;
            r46 = 125; // 0x7d float:1.75E-43 double:6.2E-322;
            r0 = r55;
            r4 = com.android.nfc.NfcService.this;
            monitor-enter(r4);
            r0 = r55;
            r3 = com.android.nfc.NfcService.this;	 Catch:{ all -> 0x0209 }
            r0 = r3.mReaderModeParams;	 Catch:{ all -> 0x0209 }
            r47 = r0;
            monitor-exit(r4);	 Catch:{ all -> 0x0209 }
            if (r47 == 0) goto L_0x020c;
        L_0x01de:
            r0 = r47;
            r0 = r0.presenceCheckDelay;
            r46 = r0;
            r0 = r47;
            r3 = r0.flags;
            r3 = r3 & 128;
            if (r3 == 0) goto L_0x020c;
        L_0x01ec:
            r3 = com.android.nfc.NfcService.DBG;
            if (r3 == 0) goto L_0x01f7;
        L_0x01f0:
            r3 = "NfcService";
            r4 = "Skipping NDEF detection in reader mode";
            android.util.Log.d(r3, r4);
        L_0x01f7:
            r0 = r50;
            r1 = r46;
            r0.startPresenceChecking(r1);
            r0 = r55;
            r1 = r50;
            r2 = r47;
            r0.dispatchTagEndpoint(r1, r2);
            goto L_0x000e;
        L_0x0209:
            r3 = move-exception;
            monitor-exit(r4);	 Catch:{ all -> 0x0209 }
            throw r3;
        L_0x020c:
            if (r47 == 0) goto L_0x0216;
        L_0x020e:
            r0 = r47;
            r3 = r0.flags;
            r3 = r3 & 256;
            if (r3 != 0) goto L_0x021e;
        L_0x0216:
            r0 = r55;
            r3 = com.android.nfc.NfcService.this;
            r4 = 0;
            r3.playSound(r4);
        L_0x021e:
            r3 = "NXP_PN544C3";
            r4 = "NXP_PN544C3";
            r3 = r3.equals(r4);
            if (r3 == 0) goto L_0x0249;
        L_0x0228:
            r3 = r50.getTechList();
            r4 = 0;
            r3 = r3[r4];
            r4 = 10;
            if (r3 != r4) goto L_0x026e;
        L_0x0233:
            r3 = com.android.nfc.NfcService.DBG;
            if (r3 == 0) goto L_0x023e;
        L_0x0237:
            r3 = "NfcService";
            r4 = "Skipping NDEF detection for NFC Barcode";
            android.util.Log.d(r3, r4);
        L_0x023e:
            r0 = r55;
            r1 = r50;
            r2 = r47;
            r0.dispatchTagEndpoint(r1, r2);
            goto L_0x000e;
        L_0x0249:
            r3 = r50.getConnectedTechnology();
            r4 = 10;
            if (r3 != r4) goto L_0x026e;
        L_0x0251:
            r3 = com.android.nfc.NfcService.DBG;
            if (r3 == 0) goto L_0x025c;
        L_0x0255:
            r3 = "NfcService";
            r4 = "Skipping NDEF detection for NFC Barcode";
            android.util.Log.d(r3, r4);
        L_0x025c:
            r0 = r50;
            r1 = r46;
            r0.startPresenceChecking(r1);
            r0 = r55;
            r1 = r50;
            r2 = r47;
            r0.dispatchTagEndpoint(r1, r2);
            goto L_0x000e;
        L_0x026e:
            r40 = r50.findAndReadNdef();
            r0 = r55;
            r3 = com.android.nfc.NfcService.this;
            r3 = r3.mTestMode;
            if (r3 == 0) goto L_0x028e;
        L_0x027c:
            r0 = r55;
            r3 = com.android.nfc.NfcService.this;
            r3 = r3.mContext;
            r4 = new android.content.Intent;
            r53 = "android.nfc.action.TAG_DISCOVERED";
            r0 = r53;
            r4.<init>(r0);
            r3.sendBroadcast(r4);
        L_0x028e:
            if (r40 == 0) goto L_0x02a2;
        L_0x0290:
            r0 = r50;
            r1 = r46;
            r0.startPresenceChecking(r1);
            r0 = r55;
            r1 = r50;
            r2 = r47;
            r0.dispatchTagEndpoint(r1, r2);
            goto L_0x000e;
        L_0x02a2:
            r3 = r50.reconnect();
            if (r3 == 0) goto L_0x02ba;
        L_0x02a8:
            r0 = r50;
            r1 = r46;
            r0.startPresenceChecking(r1);
            r0 = r55;
            r1 = r50;
            r2 = r47;
            r0.dispatchTagEndpoint(r1, r2);
            goto L_0x000e;
        L_0x02ba:
            r50.disconnect();
            r0 = r55;
            r3 = com.android.nfc.NfcService.this;
            r4 = 2;
            r3.playSound(r4);
            goto L_0x000e;
        L_0x02c7:
            r3 = com.android.nfc.NfcService.DBG;
            if (r3 == 0) goto L_0x02d2;
        L_0x02cb:
            r3 = "NfcService";
            r4 = "Card Emulation AID_SELECTED message";
            android.util.Log.d(r3, r4);
        L_0x02d2:
            r0 = r56;
            r3 = r0.obj;
            r3 = (byte[]) r3;
            r15 = r3;
            r15 = (byte[]) r15;
            r16 = new android.content.Intent;
            r3 = "com.android.nfc_extras.action.AID_SELECTED";
            r0 = r16;
            r0.<init>(r3);
            r3 = "com.android.nfc_extras.extra.AID";
            r0 = r16;
            r0.putExtra(r3, r15);
            r3 = com.android.nfc.NfcService.DBG;
            if (r3 == 0) goto L_0x02f6;
        L_0x02ef:
            r3 = "NfcService";
            r4 = "Broadcasting com.android.nfc_extras.action.AID_SELECTED";
            android.util.Log.d(r3, r4);
        L_0x02f6:
            r0 = r55;
            r1 = r16;
            r0.sendSeBroadcast(r1);
            goto L_0x000e;
        L_0x02ff:
            r3 = com.android.nfc.NfcService.DBG;
            if (r3 == 0) goto L_0x030a;
        L_0x0303:
            r3 = "NfcService";
            r4 = "Card Emulation message";
            android.util.Log.d(r3, r4);
        L_0x030a:
            r0 = r55;
            r3 = com.android.nfc.NfcService.this;
            r3 = r3.mHostEmulationManager;
            if (r3 == 0) goto L_0x031f;
        L_0x0314:
            r0 = r55;
            r3 = com.android.nfc.NfcService.this;
            r3 = r3.mHostEmulationManager;
            r3.notifyOffHostAidSelected();
        L_0x031f:
            r3 = "NXP_PN544C3";
            r4 = "NXP_PN544C3";
            r3 = r3.equals(r4);
            if (r3 == 0) goto L_0x036b;
        L_0x0329:
            r0 = r56;
            r0 = r0.obj;
            r52 = r0;
            r52 = (android.util.Pair) r52;
            r16 = new android.content.Intent;
            r16.<init>();
            r3 = "com.android.nfc_extras.action.AID_SELECTED";
            r0 = r16;
            r0.setAction(r3);
            r4 = "com.android.nfc_extras.extra.AID";
            r0 = r52;
            r3 = r0.first;
            r3 = (byte[]) r3;
            r0 = r16;
            r0.putExtra(r4, r3);
            r4 = "com.android.nfc_extras.extra.DATA";
            r0 = r52;
            r3 = r0.second;
            r3 = (byte[]) r3;
            r0 = r16;
            r0.putExtra(r4, r3);
            r3 = com.android.nfc.NfcService.DBG;
            if (r3 == 0) goto L_0x0362;
        L_0x035b:
            r3 = "NfcService";
            r4 = "Broadcasting NXP-PN544C3 com.android.nfc_extras.action.AID_SELECTED";
            android.util.Log.d(r3, r4);
        L_0x0362:
            r0 = r55;
            r1 = r16;
            r0.sendSeBroadcast(r1);
            goto L_0x000e;
        L_0x036b:
            r0 = r56;
            r0 = r0.obj;
            r51 = r0;
            r51 = (android.util.Pair) r51;
            r0 = r51;
            r0 = r0.second;
            r21 = r0;
            r21 = (android.util.Pair) r21;
            r0 = r51;
            r15 = r0.first;
            r15 = (byte[]) r15;
            r0 = r21;
            r0 = r0.first;
            r43 = r0;
            r43 = (byte[]) r43;
            r0 = r55;
            r1 = r43;
            r16 = r0.makeAidIntent(r15, r1);
            r0 = r21;
            r3 = r0.second;
            r3 = (java.lang.Integer) r3;
            r3 = r3.intValue();
            r4 = com.android.nfc.NfcService.SECURE_ELEMENT_UICC_ID;
            if (r3 != r4) goto L_0x03e7;
        L_0x039f:
            r3 = "GOOGLE";
            r4 = com.android.nfc.NfcService.mSecureEventType;
            r3 = r3.equals(r4);
            if (r3 != 0) goto L_0x03b7;
        L_0x03ab:
            r3 = "ISIS";
            r4 = com.android.nfc.NfcService.mSecureEventType;
            r3 = r3.equals(r4);
            if (r3 == 0) goto L_0x03cb;
        L_0x03b7:
            r3 = com.android.nfc.NfcService.DBG;
            if (r3 == 0) goto L_0x03c2;
        L_0x03bb:
            r3 = "NfcService";
            r4 = "Broadcasting EVENT";
            android.util.Log.d(r3, r4);
        L_0x03c2:
            r0 = r55;
            r1 = r16;
            r0.sendSeBroadcast(r1);
            goto L_0x000e;
        L_0x03cb:
            r0 = r55;
            r3 = com.android.nfc.NfcService.this;	 Catch:{ NullPointerException -> 0x03d9 }
            r3 = r3.mHciEventControl;	 Catch:{ NullPointerException -> 0x03d9 }
            r4 = 0;
            r0 = r43;
            r3.checkAndSendIntent(r4, r15, r0);	 Catch:{ NullPointerException -> 0x03d9 }
            goto L_0x000e;
        L_0x03d9:
            r26 = move-exception;
            r3 = com.android.nfc.NfcService.DBG;
            if (r3 == 0) goto L_0x000e;
        L_0x03de:
            r3 = "NfcService";
            r4 = "mHciEventControl is null";
            android.util.Log.e(r3, r4);
            goto L_0x000e;
        L_0x03e7:
            r0 = r21;
            r3 = r0.second;
            r3 = (java.lang.Integer) r3;
            r3 = r3.intValue();
            r4 = com.android.nfc.NfcService.SECURE_ELEMENT_ESE_ID;
            if (r3 != r4) goto L_0x0423;
        L_0x03f5:
            r3 = com.android.nfc.NfcService.DBG;
            if (r3 == 0) goto L_0x0400;
        L_0x03f9:
            r3 = "NfcService";
            r4 = "Broadcasting EVENT";
            android.util.Log.d(r3, r4);
        L_0x0400:
            r0 = r55;
            r1 = r16;
            r0.sendSeBroadcast(r1);
            r0 = r55;
            r3 = com.android.nfc.NfcService.this;	 Catch:{ NullPointerException -> 0x0415 }
            r3 = r3.mHciEventControl;	 Catch:{ NullPointerException -> 0x0415 }
            r4 = 1;
            r0 = r43;
            r3.checkAndSendIntent(r4, r15, r0);	 Catch:{ NullPointerException -> 0x0415 }
            goto L_0x000e;
        L_0x0415:
            r26 = move-exception;
            r3 = com.android.nfc.NfcService.DBG;
            if (r3 == 0) goto L_0x000e;
        L_0x041a:
            r3 = "NfcService";
            r4 = "mHciEventControl is null";
            android.util.Log.e(r3, r4);
            goto L_0x000e;
        L_0x0423:
            r3 = com.android.nfc.NfcService.DBG;
            if (r3 == 0) goto L_0x000e;
        L_0x0427:
            r4 = "NfcService";
            r3 = new java.lang.StringBuilder;
            r3.<init>();
            r53 = "unexpected src=0x";
            r0 = r53;
            r53 = r3.append(r0);
            r0 = r21;
            r3 = r0.second;
            r3 = (java.lang.Integer) r3;
            r3 = r3.intValue();
            r3 = java.lang.Integer.toHexString(r3);
            r0 = r53;
            r3 = r0.append(r3);
            r3 = r3.toString();
            android.util.Log.d(r4, r3);
            goto L_0x000e;
        L_0x0453:
            r3 = com.android.nfc.NfcService.DBG;
            if (r3 == 0) goto L_0x045e;
        L_0x0457:
            r3 = "NfcService";
            r4 = "SE EVENT CONNECTIVITY";
            android.util.Log.d(r3, r4);
        L_0x045e:
            r29 = new android.content.Intent;
            r29.<init>();
            r3 = "com.nxp.action.CONNECTIVITY_EVENT_DETECTED";
            r0 = r29;
            r0.setAction(r3);
            r3 = com.android.nfc.NfcService.DBG;
            if (r3 == 0) goto L_0x0475;
        L_0x046e:
            r3 = "NfcService";
            r4 = "Broadcasting Intent";
            android.util.Log.d(r3, r4);
        L_0x0475:
            r0 = r55;
            r3 = com.android.nfc.NfcService.this;
            r3 = r3.mContext;
            r4 = "android.permission.NFC";
            r0 = r29;
            r3.sendBroadcast(r0, r4);
            goto L_0x000e;
        L_0x0484:
            r3 = com.android.nfc.NfcService.DBG;
            if (r3 == 0) goto L_0x048f;
        L_0x0488:
            r3 = "NfcService";
            r4 = "Card Removal message";
            android.util.Log.d(r3, r4);
        L_0x048f:
            r19 = new android.content.Intent;
            r19.<init>();
            r3 = "com.android.nfc_extras.action.EMV_CARD_REMOVAL";
            r0 = r19;
            r0.setAction(r3);
            r3 = com.android.nfc.NfcService.DBG;
            if (r3 == 0) goto L_0x04a6;
        L_0x049f:
            r3 = "NfcService";
            r4 = "Broadcasting com.android.nfc_extras.action.EMV_CARD_REMOVAL";
            android.util.Log.d(r3, r4);
        L_0x04a6:
            r0 = r55;
            r1 = r19;
            r0.sendSeBroadcast(r1);
            goto L_0x000e;
        L_0x04af:
            r3 = com.android.nfc.NfcService.DBG;
            if (r3 == 0) goto L_0x04ba;
        L_0x04b3:
            r3 = "NfcService";
            r4 = "APDU Received message";
            android.util.Log.d(r3, r4);
        L_0x04ba:
            r0 = r56;
            r3 = r0.obj;
            r3 = (byte[]) r3;
            r17 = r3;
            r17 = (byte[]) r17;
            r18 = new android.content.Intent;
            r18.<init>();
            r3 = "com.android.nfc_extras.action.APDU_RECEIVED";
            r0 = r18;
            r0.setAction(r3);
            if (r17 == 0) goto L_0x04e0;
        L_0x04d2:
            r0 = r17;
            r3 = r0.length;
            if (r3 <= 0) goto L_0x04e0;
        L_0x04d7:
            r3 = "com.android.nfc_extras.extra.APDU_BYTES";
            r0 = r18;
            r1 = r17;
            r0.putExtra(r3, r1);
        L_0x04e0:
            r3 = com.android.nfc.NfcService.DBG;
            if (r3 == 0) goto L_0x04eb;
        L_0x04e4:
            r3 = "NfcService";
            r4 = "Broadcasting com.android.nfc_extras.action.APDU_RECEIVED";
            android.util.Log.d(r3, r4);
        L_0x04eb:
            r0 = r55;
            r1 = r18;
            r0.sendSeBroadcast(r1);
            goto L_0x000e;
        L_0x04f4:
            r3 = com.android.nfc.NfcService.DBG;
            if (r3 == 0) goto L_0x04ff;
        L_0x04f8:
            r3 = "NfcService";
            r4 = "MIFARE access message";
            android.util.Log.d(r3, r4);
        L_0x04ff:
            r0 = r56;
            r3 = r0.obj;
            r3 = (byte[]) r3;
            r39 = r3;
            r39 = (byte[]) r39;
            r37 = new android.content.Intent;
            r37.<init>();
            r3 = "com.android.nfc_extras.action.MIFARE_ACCESS_DETECTED";
            r0 = r37;
            r0.setAction(r3);
            if (r39 == 0) goto L_0x054d;
        L_0x0517:
            r0 = r39;
            r3 = r0.length;
            r4 = 1;
            if (r3 <= r4) goto L_0x054d;
        L_0x051d:
            r3 = 1;
            r3 = r39[r3];
            r0 = r3 & 255;
            r38 = r0;
            r3 = com.android.nfc.NfcService.DBG;
            if (r3 == 0) goto L_0x0544;
        L_0x0528:
            r3 = "NfcService";
            r4 = new java.lang.StringBuilder;
            r4.<init>();
            r53 = "Mifare Block=";
            r0 = r53;
            r4 = r4.append(r0);
            r0 = r38;
            r4 = r4.append(r0);
            r4 = r4.toString();
            android.util.Log.d(r3, r4);
        L_0x0544:
            r3 = "com.android.nfc_extras.extra.MIFARE_BLOCK";
            r0 = r37;
            r1 = r38;
            r0.putExtra(r3, r1);
        L_0x054d:
            r3 = com.android.nfc.NfcService.DBG;
            if (r3 == 0) goto L_0x0558;
        L_0x0551:
            r3 = "NfcService";
            r4 = "Broadcasting com.android.nfc_extras.action.MIFARE_ACCESS_DETECTED";
            android.util.Log.d(r3, r4);
        L_0x0558:
            r0 = r55;
            r1 = r37;
            r0.sendSeBroadcast(r1);
            goto L_0x000e;
        L_0x0561:
            r0 = r55;
            r3 = com.android.nfc.NfcService.this;
            r3 = r3.mIsDebugBuild;
            if (r3 == 0) goto L_0x0579;
        L_0x0569:
            r14 = new android.content.Intent;
            r3 = "com.android.nfc.action.LLCP_UP";
            r14.<init>(r3);
            r0 = r55;
            r3 = com.android.nfc.NfcService.this;
            r3 = r3.mContext;
            r3.sendBroadcast(r14);
        L_0x0579:
            r0 = r56;
            r3 = r0.obj;
            r3 = (com.android.nfc.DeviceHost.NfcDepEndpoint) r3;
            r0 = r55;
            r0.llcpActivated(r3);
            goto L_0x000e;
        L_0x0586:
            r0 = r55;
            r3 = com.android.nfc.NfcService.this;
            r3 = r3.mIsDebugBuild;
            if (r3 == 0) goto L_0x05a2;
        L_0x058e:
            r22 = new android.content.Intent;
            r3 = "com.android.nfc.action.LLCP_DOWN";
            r0 = r22;
            r0.<init>(r3);
            r0 = r55;
            r3 = com.android.nfc.NfcService.this;
            r3 = r3.mContext;
            r0 = r22;
            r3.sendBroadcast(r0);
        L_0x05a2:
            r0 = r56;
            r0 = r0.obj;
            r24 = r0;
            r24 = (com.android.nfc.DeviceHost.NfcDepEndpoint) r24;
            r41 = 0;
            r3 = "NfcService";
            r4 = "LLCP Link Deactivated message. Restart polling loop.";
            android.util.Log.d(r3, r4);
            r0 = r55;
            r4 = com.android.nfc.NfcService.this;
            monitor-enter(r4);
            r0 = r55;
            r3 = com.android.nfc.NfcService.this;	 Catch:{ all -> 0x0602 }
            r3 = r3.mObjectMap;	 Catch:{ all -> 0x0602 }
            r53 = r24.getHandle();	 Catch:{ all -> 0x0602 }
            r53 = java.lang.Integer.valueOf(r53);	 Catch:{ all -> 0x0602 }
            r0 = r53;
            r3 = r3.remove(r0);	 Catch:{ all -> 0x0602 }
            if (r3 == 0) goto L_0x05e3;
        L_0x05ce:
            r3 = r24.getMode();	 Catch:{ all -> 0x0602 }
            if (r3 != 0) goto L_0x05f4;
        L_0x05d4:
            r3 = com.android.nfc.NfcService.DBG;	 Catch:{ all -> 0x0602 }
            if (r3 == 0) goto L_0x05e1;
        L_0x05d8:
            r3 = "NfcService";
            r53 = "disconnecting from target";
            r0 = r53;
            android.util.Log.d(r3, r0);	 Catch:{ all -> 0x0602 }
        L_0x05e1:
            r41 = 1;
        L_0x05e3:
            monitor-exit(r4);	 Catch:{ all -> 0x0602 }
            if (r41 == 0) goto L_0x05e9;
        L_0x05e6:
            r24.disconnect();
        L_0x05e9:
            r0 = r55;
            r3 = com.android.nfc.NfcService.this;
            r3 = r3.mP2pLinkManager;
            r3.onLlcpDeactivated();
            goto L_0x000e;
        L_0x05f4:
            r3 = com.android.nfc.NfcService.DBG;	 Catch:{ all -> 0x0602 }
            if (r3 == 0) goto L_0x05e3;
        L_0x05f8:
            r3 = "NfcService";
            r53 = "not disconnecting from initiator";
            r0 = r53;
            android.util.Log.d(r3, r0);	 Catch:{ all -> 0x0602 }
            goto L_0x05e3;
        L_0x0602:
            r3 = move-exception;
            monitor-exit(r4);	 Catch:{ all -> 0x0602 }
            throw r3;
        L_0x0605:
            r0 = r55;
            r3 = com.android.nfc.NfcService.this;
            r3 = r3.mP2pLinkManager;
            r3.onLlcpFirstPacketReceived();
            goto L_0x000e;
        L_0x0610:
            r3 = com.android.nfc.NfcService.DBG;
            if (r3 == 0) goto L_0x061b;
        L_0x0614:
            r3 = "NfcService";
            r4 = "Target Deselected";
            android.util.Log.d(r3, r4);
        L_0x061b:
            r33 = new android.content.Intent;
            r33.<init>();
            r3 = "com.android.nfc.action.INTERNAL_TARGET_DESELECTED";
            r0 = r33;
            r0.setAction(r3);
            r3 = com.android.nfc.NfcService.DBG;
            if (r3 == 0) goto L_0x0632;
        L_0x062b:
            r3 = "NfcService";
            r4 = "Broadcasting Intent";
            android.util.Log.d(r3, r4);
        L_0x0632:
            r0 = r55;
            r3 = com.android.nfc.NfcService.this;
            r3 = r3.mContext;
            r4 = "android.permission.NFC";
            r0 = r33;
            r3.sendOrderedBroadcast(r0, r4);
            goto L_0x000e;
        L_0x0641:
            r3 = com.android.nfc.NfcService.DBG;
            if (r3 == 0) goto L_0x064c;
        L_0x0645:
            r3 = "NfcService";
            r4 = "SE FIELD ACTIVATED";
            android.util.Log.d(r3, r4);
        L_0x064c:
            r31 = new android.content.Intent;
            r31.<init>();
            r3 = "com.android.nfc_extras.action.RF_FIELD_ON_DETECTED";
            r0 = r31;
            r0.setAction(r3);
            r0 = r55;
            r1 = r31;
            r0.sendSeBroadcast(r1);
            goto L_0x000e;
        L_0x0661:
            r3 = com.android.nfc.NfcService.DBG;
            if (r3 == 0) goto L_0x066c;
        L_0x0665:
            r3 = "NfcService";
            r4 = "SE FIELD DEACTIVATED";
            android.util.Log.d(r3, r4);
        L_0x066c:
            r30 = new android.content.Intent;
            r30.<init>();
            r3 = "com.android.nfc_extras.action.RF_FIELD_OFF_DETECTED";
            r0 = r30;
            r0.setAction(r3);
            r0 = r55;
            r1 = r30;
            r0.sendSeBroadcast(r1);
            goto L_0x000e;
        L_0x0681:
            r3 = com.android.nfc.NfcService.DBG;
            if (r3 == 0) goto L_0x068c;
        L_0x0685:
            r3 = "NfcService";
            r4 = "SE LISTEN MODE ACTIVATED";
            android.util.Log.d(r3, r4);
        L_0x068c:
            r34 = new android.content.Intent;
            r34.<init>();
            r3 = "com.android.nfc_extras.action.SE_LISTEN_ACTIVATED";
            r0 = r34;
            r0.setAction(r3);
            r0 = r55;
            r1 = r34;
            r0.sendSeBroadcast(r1);
            goto L_0x000e;
        L_0x06a1:
            r3 = com.android.nfc.NfcService.DBG;
            if (r3 == 0) goto L_0x06ac;
        L_0x06a5:
            r3 = "NfcService";
            r4 = "SE LISTEN MODE DEACTIVATED";
            android.util.Log.d(r3, r4);
        L_0x06ac:
            r35 = new android.content.Intent;
            r35.<init>();
            r3 = "com.android.nfc_extras.action.SE_LISTEN_DEACTIVATED";
            r0 = r35;
            r0.setAction(r3);
            r0 = r55;
            r1 = r35;
            r0.sendSeBroadcast(r1);
            goto L_0x000e;
        L_0x06c1:
            r0 = r55;
            r3 = com.android.nfc.NfcService.this;
            r4 = 1;
            r3.setIcon(r4);
            goto L_0x000e;
        L_0x06cb:
            r0 = r55;
            r3 = com.android.nfc.NfcService.this;
            r4 = 0;
            r3.setIcon(r4);
            goto L_0x000e;
        L_0x06d5:
            r0 = r56;
            r0 = r0.arg1;
            r27 = r0;
            r0 = r56;
            r0 = r0.arg2;
            r49 = r0;
            r3 = com.android.nfc.NfcService.DBG;
            if (r3 == 0) goto L_0x0701;
        L_0x06e5:
            r3 = "NfcService";
            r4 = new java.lang.StringBuilder;
            r4.<init>();
            r53 = "UPDATE SE";
            r0 = r53;
            r4 = r4.append(r0);
            r0 = r49;
            r4 = r4.append(r0);
            r4 = r4.toString();
            android.util.Log.d(r3, r4);
        L_0x0701:
            r3 = 1;
            r0 = r27;
            if (r0 != r3) goto L_0x0715;
        L_0x0706:
            r0 = r55;
            r3 = com.android.nfc.NfcService.this;
            r3 = r3.mDeviceHost;
            r0 = r49;
            r3.doSelectSecureElement(r0);
            goto L_0x000e;
        L_0x0715:
            r0 = r55;
            r3 = com.android.nfc.NfcService.this;
            r3 = r3.mDeviceHost;
            r0 = r49;
            r3.doDeselectSecureElement(r0);
            goto L_0x000e;
        L_0x0724:
            r0 = r56;
            r0 = r0.arg1;
            r27 = r0;
            r3 = com.android.nfc.NfcService.DBG;
            if (r3 == 0) goto L_0x074a;
        L_0x072e:
            r3 = "NfcService";
            r4 = new java.lang.StringBuilder;
            r4.<init>();
            r53 = "enable routing to Host";
            r0 = r53;
            r4 = r4.append(r0);
            r0 = r27;
            r4 = r4.append(r0);
            r4 = r4.toString();
            android.util.Log.d(r3, r4);
        L_0x074a:
            r3 = 1;
            r0 = r27;
            if (r0 != r3) goto L_0x075c;
        L_0x074f:
            r0 = r55;
            r3 = com.android.nfc.NfcService.this;
            r3 = r3.mDeviceHost;
            r3.enableRoutingToHost();
            goto L_0x000e;
        L_0x075c:
            r0 = r55;
            r3 = com.android.nfc.NfcService.this;
            r3 = r3.mDeviceHost;
            r3.disableRoutingToHost();
            goto L_0x000e;
        L_0x0769:
            r3 = "NfcService";
            r4 = "NfcServiceHandler - MSG_CHN_ENABLE_POPUP";
            android.util.Log.d(r3, r4);
            r0 = r55;
            r3 = com.android.nfc.NfcService.this;
            r3 = r3.mContext;
            r4 = "statusbar";
            r36 = r3.getSystemService(r4);
            r36 = (android.app.StatusBarManager) r36;
            if (r36 == 0) goto L_0x0783;
        L_0x0780:
            r36.collapsePanels();
        L_0x0783:
            r12 = new android.content.Intent;
            r3 = "com.android.nfc.NfcChnEnablePopup";
            r12.<init>(r3);
            r3 = 268468224; // 0x10008000 float:2.5342157E-29 double:1.326409265E-315;
            r12.setFlags(r3);
            r0 = r55;
            r3 = com.android.nfc.NfcService.this;
            r3 = r3.mContext;
            r3.startActivity(r12);
            goto L_0x000e;
        L_0x079b:
            r3 = "NfcService";
            r4 = "NfcServiceHandler - MSG_CHN_ENABLE_DIRECT";
            android.util.Log.d(r3, r4);
            r28 = new com.android.nfc.NfcService$EnableDisableTask;
            r0 = r55;
            r3 = com.android.nfc.NfcService.this;
            r0 = r28;
            r0.<init>();
            r3 = 1;
            r3 = new java.lang.Integer[r3];
            r4 = 0;
            r53 = 101; // 0x65 float:1.42E-43 double:5.0E-322;
            r53 = java.lang.Integer.valueOf(r53);
            r3[r4] = r53;
            r0 = r28;
            r0.execute(r3);
            goto L_0x000e;
        L_0x07c0:
            r3 = com.android.nfc.NfcService.DBG;
            if (r3 == 0) goto L_0x07cb;
        L_0x07c4:
            r3 = "NfcService";
            r4 = "MSG_HCI_EVT_TRANSACTION";
            android.util.Log.d(r3, r4);
        L_0x07cb:
            r0 = r56;
            r3 = r0.obj;
            r3 = (byte[][]) r3;
            r20 = r3;
            r20 = (byte[][]) r20;
            r0 = r55;
            r3 = com.android.nfc.NfcService.this;
            r3 = r3.mNfcDispatcher;
            r4 = 0;
            r4 = r20[r4];
            r53 = 1;
            r53 = r20[r53];
            r0 = r53;
            r3.dispatchConnectivity(r4, r0);
            goto L_0x000e;
        L_0x07eb:
            r3 = "NfcService";
            r4 = "NfcServiceHandler - MSG_CHN_ENABLE_CANCEL";
            android.util.Log.d(r3, r4);
            r25 = new com.android.nfc.NfcService$EnableDisableTask;
            r0 = r55;
            r3 = com.android.nfc.NfcService.this;
            r0 = r25;
            r0.<init>();
            r3 = 1;
            r3 = new java.lang.Integer[r3];
            r4 = 0;
            r53 = 102; // 0x66 float:1.43E-43 double:5.04E-322;
            r53 = java.lang.Integer.valueOf(r53);
            r3[r4] = r53;
            r0 = r25;
            r0.execute(r3);
            goto L_0x000e;
        L_0x0810:
            r0 = r56;
            r3 = r0.arg1;
            r4 = 1;
            if (r3 != r4) goto L_0x082c;
        L_0x0817:
            r42 = 1;
        L_0x0819:
            r0 = r56;
            r8 = r0.arg2;
            r0 = r55;
            r3 = com.android.nfc.NfcService.this;
            r3 = r3.mDeviceHost;
            r0 = r42;
            r3.onPpseRouted(r0, r8);
            goto L_0x000e;
        L_0x082c:
            r42 = 0;
            goto L_0x0819;
            */
            throw new UnsupportedOperationException("Method not decompiled: com.android.nfc.NfcService.NfcServiceHandler.handleMessage(android.os.Message):void");
        }

        private Intent makeAidIntent(byte[] aid, byte[] param) {
            Intent aidIntent = new Intent();
            if ("ISIS".equals(NfcService.mSecureEventType)) {
                if (NfcService.DBG) {
                    Log.d(NfcService.TAG, "MAKE : com.paywithisis.action.TRANSACTION_DETECTED");
                }
                aidIntent.setAction(NfcService.ACTION_ISIS_TRANSACTION_DETECTED);
                aidIntent.putExtra(NfcService.EXTRA_ISIS_AID, aid);
                aidIntent.putExtra(NfcService.EXTRA_ISIS_DATA, param);
            } else {
                if (NfcService.DBG) {
                    Log.d(NfcService.TAG, "MAKE : com.android.nfc_extras.action.AID_SELECTED");
                }
                aidIntent.setAction(NfcService.ACTION_AID_SELECTED);
                aidIntent.putExtra(NfcService.EXTRA_AID, aid);
                aidIntent.putExtra(NfcService.EXTRA_DATA, param);
            }
            return aidIntent;
        }

        private void sendSeBroadcast(Intent intent) {
            intent.addFlags(32);
            NfcService.this.mNfcDispatcher.resumeAppSwitches();
            synchronized (this) {
                if (NfcService.isVzw && (intent.getAction().equals(NfcService.ACTION_RF_FIELD_ON_DETECTED) || intent.getAction().equals(NfcService.ACTION_RF_FIELD_OFF_DETECTED))) {
                    if (System.currentTimeMillis() - NfcService.this.mNfcEventsResultCacheTime >= 5000) {
                        NfcService.this.mNfcEventsPermissionResults = null;
                    }
                    if (NfcService.this.mNfcEventsPermissionResults == null) {
                        NfcService.this.mNfcEventsPermissionResults = NfcService.this.mHciEventControl.isSeFieldEvtAllowed("SIM - UICC", intent, NfcService.this.mPackagesWithNfcPermission);
                    }
                    NfcService.this.mNfcEventsResultCacheTime = System.currentTimeMillis();
                    if (NfcService.this.mNfcEventsPermissionResults != null && NfcService.this.mNfcEventsPermissionResults.length == NfcService.this.mPackagesWithNfcPermission.length) {
                        for (int i = NfcService.SOUND_START; i < NfcService.this.mNfcEventsPermissionResults.length; i += NfcService.TASK_ENABLE) {
                            if (NfcService.this.mNfcEventsPermissionResults[i]) {
                                intent.setPackage(NfcService.this.mPackagesWithNfcPermission[i]);
                                NfcService.this.mContext.sendBroadcast(intent);
                            }
                        }
                        if (NfcService.DBG) {
                            Log.d(NfcService.TAG, "Broadcasted RF Field On/Off Intent using GPAC Rule");
                        }
                        return;
                    }
                }
                for (PackageInfo pkg : NfcService.this.mInstalledPackages) {
                    if (!(pkg == null || pkg.applicationInfo == null || !NfcService.this.mNfceeAccessControl.check(pkg.applicationInfo))) {
                        intent.setPackage(pkg.packageName);
                        if ("ISIS".equalsIgnoreCase(NfcService.mSecureEventType) || NfcService.isVzw) {
                            NfcService.this.mContext.sendBroadcast(intent, NfcService.NFC_PERM);
                        } else {
                            NfcService.this.mContext.sendBroadcast(intent);
                        }
                    }
                }
            }
        }

        private boolean llcpActivated(NfcDepEndpoint device) {
            Log.d(NfcService.TAG, "LLCP Activation message");
            if (device.getMode() == 0) {
                if (NfcService.DBG) {
                    Log.d(NfcService.TAG, "NativeP2pDevice.MODE_P2P_TARGET");
                }
                if (device.connect()) {
                    if (!NfcService.this.mDeviceHost.doCheckLlcp()) {
                        if (NfcService.DBG) {
                            Log.d(NfcService.TAG, "Remote Target does not support LLCP. Disconnect.");
                        }
                        device.disconnect();
                    } else if (NfcService.this.mDeviceHost.doActivateLlcp()) {
                        if (NfcService.DBG) {
                            Log.d(NfcService.TAG, "Initiator Activate LLCP OK");
                        }
                        synchronized (NfcService.this) {
                            NfcService.this.mObjectMap.put(Integer.valueOf(device.getHandle()), device);
                        }
                        NfcService.this.mP2pLinkManager.onLlcpActivated();
                        return NfcService.SE_BROADCASTS_WITH_HCE;
                    } else {
                        Log.w(NfcService.TAG, "Initiator LLCP activation failed. Disconnect.");
                        device.disconnect();
                    }
                } else if (NfcService.DBG) {
                    Log.d(NfcService.TAG, "Cannot connect remote Target. Polling loop restarted.");
                }
            } else if (device.getMode() == NfcService.TASK_ENABLE) {
                if (NfcService.DBG) {
                    Log.d(NfcService.TAG, "NativeP2pDevice.MODE_P2P_INITIATOR");
                }
                if (!NfcService.this.mDeviceHost.doCheckLlcp()) {
                    Log.w(NfcService.TAG, "checkLlcp failed");
                } else if (NfcService.this.mDeviceHost.doActivateLlcp()) {
                    if (NfcService.DBG) {
                        Log.d(NfcService.TAG, "Target Activate LLCP OK");
                    }
                    synchronized (NfcService.this) {
                        NfcService.this.mObjectMap.put(Integer.valueOf(device.getHandle()), device);
                    }
                    NfcService.this.mP2pLinkManager.onLlcpActivated();
                    return NfcService.SE_BROADCASTS_WITH_HCE;
                }
            }
            return NfcService.NFC_ON_READER_DEFAULT;
        }

        private void dispatchTagEndpoint(TagEndpoint tagEndpoint, ReaderModeParams readerParams) {
            if (NfcService.this.mTestMode) {
                NfcService.this.playSound(NfcService.TASK_ENABLE);
                ((Vibrator) NfcService.this.mContext.getSystemService("vibrator")).vibrate(500);
                return;
            }
            Tag tag = new Tag(tagEndpoint.getUid(), tagEndpoint.getTechList(), tagEndpoint.getTechExtras(), tagEndpoint.getHandle(), NfcService.this.mNfcTagService);
            NfcService.this.registerTagObject(tagEndpoint);
            if (readerParams != null) {
                try {
                    if ((readerParams.flags & Device.COMPUTER_UNCATEGORIZED) == 0) {
                        NfcService.this.playSound(NfcService.TASK_ENABLE);
                    }
                    if (readerParams.callback != null) {
                        readerParams.callback.onTagDiscovered(tag);
                        return;
                    }
                } catch (RemoteException e) {
                    Log.e(NfcService.TAG, "Reader mode remote has died, falling back.");
                } catch (Exception e2) {
                    Log.e(NfcService.TAG, "App exception, not dispatching.");
                    return;
                }
            }
            if (NfcService.this.mNfcDispatcher.dispatchTag(tag)) {
                NfcService.this.playSound(NfcService.TASK_ENABLE);
                return;
            }
            NfcService.this.unregisterObject(tagEndpoint.getHandle());
            NfcService.this.playSound(NfcService.VEN_CFG_NFC_OFF_POWER_OFF);
        }
    }

    final class NfcUtilityService extends INfcUtility.Stub {
        NfcUtilityService() {
        }

        public boolean waitSimBootCallback(INfcUtilityCallback callback, boolean isLock) {
            if (NfcService.DBG) {
                Log.d(NfcService.TAG, "waitSimBoot(INfcUtilityCallback)");
            }
            synchronized (NfcService.this) {
                if (NfcService.this.mState != NfcService.TASK_ENABLE) {
                    Log.w(NfcService.TAG, "waitSimBoot(INfcUtilityCallback) state error");
                    NfcService.this.mCallback = callback;
                    NfcService.this.mIsLock = isLock;
                    return NfcService.SE_BROADCASTS_WITH_HCE;
                }
                return NfcService.this.mNfcUtility.waitSimBoot(callback, isLock);
            }
        }
    }

    private class OpenSecureElement implements DeathRecipient {
        public IBinder binder;
        public int handle;
        public int pid;

        public OpenSecureElement(int pid, int handle, IBinder binder) {
            this.pid = pid;
            this.handle = handle;
            this.binder = binder;
        }

        public void binderDied() {
            synchronized (NfcService.this) {
                Log.i(NfcService.TAG, "Tracked app " + this.pid + " died");
                NfcService.this.mOpenEe = (OpenSecureElement) NfcService.this.mOpenEeMap.get(Integer.valueOf(this.pid));
                try {
                    NfcService.this._nfcEeClose(this.pid, NfcService.this.mOpenEe.binder);
                } catch (IOException e) {
                }
            }
        }

        public String toString() {
            return Integer.toHexString(hashCode()) + "[pid=" + this.pid + " handle=" + this.handle + "]";
        }
    }

    final class ReaderModeDeathRecipient implements DeathRecipient {
        ReaderModeDeathRecipient() {
        }

        public void binderDied() {
            synchronized (NfcService.this) {
                if (NfcService.this.mReaderModeParams != null) {
                    NfcService.this.mReaderModeParams = null;
                    if (NfcService.DBG) {
                        Log.d(NfcService.TAG, "applyRouting #4");
                    }
                    NfcService.this.applyRouting(NfcService.NFC_ON_READER_DEFAULT);
                }
            }
        }
    }

    final class ReaderModeParams {
        public IAppCallback callback;
        public int flags;
        public int presenceCheckDelay;

        ReaderModeParams() {
        }
    }

    final class SEControllerService extends ISEController.Stub {
        SEControllerService() {
        }

        public String getActiveSecureElement() throws RemoteException {
            if (NfcService.this.isGsmaApiSupported) {
                if (NfcService.DBG) {
                    Log.i(NfcService.TAG, "getActiveSecureElement");
                }
                if (NfcService.this.isNfcEnabled()) {
                    if (NfcService.this.mSelectedSeId == NfcService.SECURE_ELEMENT_UICC_ID) {
                        return "SIM";
                    }
                    if (NfcService.this.mSelectedSeId == NfcService.SECURE_ELEMENT_ESE_ID) {
                        return "eSE";
                    }
                    return "None";
                } else if (!NfcService.DBG) {
                    return null;
                } else {
                    Log.d(NfcService.TAG, "Nfc was not enabled");
                    return null;
                }
            } else if (!NfcService.DBG) {
                return null;
            } else {
                Log.e(NfcService.TAG, "Gsma Apis are not Supported in this project");
                return null;
            }
        }

        public int setActiveSecureElement(String SEName) throws RemoteException {
            if (NfcService.this.isGsmaApiSupported) {
                if (NfcService.mHideTerminal.contains(NfcService.SECURE_ELEMENT_ESE_NAME) && "eSE".equalsIgnoreCase(SEName)) {
                    if (NfcService.DBG) {
                        Log.d(NfcService.TAG, "eSE is not available on this device");
                    }
                    return NfcService.EE_ERROR_INIT;
                }
                if (NfcService.DBG) {
                    Log.i(NfcService.TAG, "setActiveSecureElement " + SEName);
                }
                try {
                    if (!NfcService.this.mHciEventControl.isAllowedForGsma()) {
                        return NfcService.EE_ERROR_INIT;
                    }
                    if (!NfcService.DBG) {
                        return NfcService.EE_ERROR_IO;
                    }
                    Log.d(NfcService.TAG, "API not supported.");
                    return NfcService.EE_ERROR_IO;
                } catch (RemoteException e) {
                    if (NfcService.DBG) {
                        Log.e(NfcService.TAG, "Checking CDF failed.");
                    }
                    return NfcService.EE_ERROR_INIT;
                } catch (NullPointerException e2) {
                    if (NfcService.DBG) {
                        Log.e(NfcService.TAG, "Checking CDF failed.");
                    }
                    return NfcService.EE_ERROR_INIT;
                }
            } else if (!NfcService.DBG) {
                return NfcService.EE_ERROR_IO;
            } else {
                Log.e(NfcService.TAG, "Gsma Apis are not Supported in this project");
                return NfcService.EE_ERROR_IO;
            }
        }

        public int setForegroundDispatch(PendingIntent intent, IntentFilter[] filters) throws RemoteException {
            if (NfcService.this.isGsmaApiSupported) {
                Log.i(NfcService.TAG, "setForegroundDispatch");
                if (NfcService.this.mSEControllerService == null) {
                    return NfcService.EE_ERROR_INIT;
                }
                try {
                    if (!NfcService.this.mHciEventControl.isAllowedForGsma(getActiveSecureElement())) {
                        return NfcService.EE_ERROR_INIT;
                    }
                    if (intent == null && filters == null) {
                        NfcService.this.mHciEventControl.setForegroundDispatch(null, null);
                        return NfcService.SOUND_START;
                    }
                    if (filters != null) {
                        if (filters.length == 0) {
                            filters = null;
                        } else {
                            IntentFilter[] arr$ = filters;
                            int len$ = arr$.length;
                            for (int i$ = NfcService.SOUND_START; i$ < len$; i$ += NfcService.TASK_ENABLE) {
                                if (arr$[i$] == null) {
                                    return NfcService.EE_ERROR_LISTEN_MODE;
                                }
                            }
                        }
                    }
                    NfcService.this.mHciEventControl.setForegroundDispatch(intent, filters);
                    return NfcService.SOUND_START;
                } catch (RemoteException e) {
                    if (!NfcService.DBG) {
                        return NfcService.EE_ERROR_INIT;
                    }
                    Log.e(NfcService.TAG, "Checking CDF failed.");
                    return NfcService.EE_ERROR_INIT;
                } catch (NullPointerException e2) {
                    if (!NfcService.DBG) {
                        return NfcService.EE_ERROR_INIT;
                    }
                    Log.e(NfcService.TAG, "mHciEventControl is null");
                    return NfcService.EE_ERROR_INIT;
                }
            }
            if (NfcService.DBG) {
                Log.e(NfcService.TAG, "Gsma Apis are not Supported in this project");
            }
            return NfcService.EE_ERROR_IO;
        }

        public int enableMultiEvt_transactionReception(String SEName, boolean enable) throws RemoteException {
            if (!NfcService.this.isGsmaApiSupported) {
                if (NfcService.DBG) {
                    Log.e(NfcService.TAG, "Gsma Apis are not Supported in this project");
                }
                return NfcService.EE_ERROR_IO;
            } else if (!NfcService.mHideTerminal.contains(NfcService.SECURE_ELEMENT_ESE_NAME) || !"eSE".equalsIgnoreCase(SEName)) {
                if (NfcService.DBG) {
                    Log.i(NfcService.TAG, "enableMultiEvt_transactionReception");
                }
                try {
                    if (!NfcService.this.mHciEventControl.isAllowedForGsma(SEName)) {
                        return NfcService.EE_ERROR_INIT;
                    }
                    NfcService.this.mHciEventControl.enableMultiEvt_transactionReception(SEName, enable);
                    return NfcService.SOUND_START;
                } catch (RemoteException e) {
                    if (!NfcService.DBG) {
                        return NfcService.EE_ERROR_INIT;
                    }
                    Log.e(NfcService.TAG, "Checking CDF failed.");
                    return NfcService.EE_ERROR_INIT;
                } catch (NullPointerException e2) {
                    if (!NfcService.DBG) {
                        return NfcService.EE_ERROR_INIT;
                    }
                    Log.e(NfcService.TAG, "mHciEventControl is null");
                    return NfcService.EE_ERROR_INIT;
                }
            } else if (!NfcService.DBG) {
                return NfcService.EE_ERROR_INIT;
            } else {
                Log.d(NfcService.TAG, "eSE is not available on this device");
                return NfcService.EE_ERROR_INIT;
            }
        }
    }

    class SamsungPreference {
        public static final String PREFS = "SamsungNfcPrefs";
        private SharedPreferences prefs;
        private Editor prefsEditor;

        public SamsungPreference() {
            this.prefs = NfcService.this.mContext.getSharedPreferences(PREFS, NfcService.SOUND_START);
            this.prefsEditor = this.prefs.edit();
        }

        public void remove(String key) {
            this.prefsEditor.remove(key);
            this.prefsEditor.commit();
        }

        public void putString(String key, String value) {
            this.prefsEditor.putString(key, value);
            this.prefsEditor.commit();
        }

        public void putInt(String key, int value) {
            this.prefsEditor.putInt(key, value);
            this.prefsEditor.commit();
        }

        public String getString(String key, String value) {
            return this.prefs.getString(key, value);
        }

        public int getInt(String key, int defValue) {
            return this.prefs.getInt(key, defValue);
        }
    }

    final class TagService extends INfcTag.Stub {
        TagService() {
        }

        public int close(int nativeHandle) throws RemoteException {
            NfcService.this.mContext.enforceCallingOrSelfPermission(NfcService.NFC_PERM, NfcService.NFC_PERM_ERROR);
            if (!NfcService.this.isNfcEnabled()) {
                return -17;
            }
            TagEndpoint tag = (TagEndpoint) NfcService.this.findObject(nativeHandle);
            if (tag != null) {
                NfcService.this.unregisterObject(nativeHandle);
                tag.disconnect();
                return NfcService.SOUND_START;
            }
            if (NfcService.DBG) {
                Log.d(NfcService.TAG, "applyRouting #5");
            }
            NfcService.this.applyRouting(NfcService.SE_BROADCASTS_WITH_HCE);
            return NfcService.EE_ERROR_EXT_FIELD;
        }

        public int connect(int nativeHandle, int technology) throws RemoteException {
            NfcService.this.mContext.enforceCallingOrSelfPermission(NfcService.NFC_PERM, NfcService.NFC_PERM_ERROR);
            if (!NfcService.this.isNfcEnabled()) {
                return -17;
            }
            TagEndpoint tag = (TagEndpoint) NfcService.this.findObject(nativeHandle);
            if (tag != null && tag.isPresent() && tag.connect(technology)) {
                return NfcService.SOUND_START;
            }
            return NfcService.EE_ERROR_EXT_FIELD;
        }

        public int reconnect(int nativeHandle) throws RemoteException {
            NfcService.this.mContext.enforceCallingOrSelfPermission(NfcService.NFC_PERM, NfcService.NFC_PERM_ERROR);
            if (!NfcService.this.isNfcEnabled()) {
                return -17;
            }
            TagEndpoint tag = (TagEndpoint) NfcService.this.findObject(nativeHandle);
            if (tag == null || !tag.reconnect()) {
                return NfcService.EE_ERROR_EXT_FIELD;
            }
            return NfcService.SOUND_START;
        }

        public int[] getTechList(int nativeHandle) throws RemoteException {
            NfcService.this.mContext.enforceCallingOrSelfPermission(NfcService.NFC_PERM, NfcService.NFC_PERM_ERROR);
            if (!NfcService.this.isNfcEnabled()) {
                return null;
            }
            TagEndpoint tag = (TagEndpoint) NfcService.this.findObject(nativeHandle);
            if (tag != null) {
                return tag.getTechList();
            }
            return null;
        }

        public boolean isPresent(int nativeHandle) throws RemoteException {
            if (!NfcService.this.isNfcEnabled()) {
                return NfcService.NFC_ON_READER_DEFAULT;
            }
            TagEndpoint tag = (TagEndpoint) NfcService.this.findObject(nativeHandle);
            if (tag != null) {
                return tag.isPresent();
            }
            return NfcService.NFC_ON_READER_DEFAULT;
        }

        public boolean isNdef(int nativeHandle) throws RemoteException {
            NfcService.this.mContext.enforceCallingOrSelfPermission(NfcService.NFC_PERM, NfcService.NFC_PERM_ERROR);
            if (!NfcService.this.isNfcEnabled()) {
                return NfcService.NFC_ON_READER_DEFAULT;
            }
            TagEndpoint tag = (TagEndpoint) NfcService.this.findObject(nativeHandle);
            int[] ndefInfo = new int[NfcService.VEN_CFG_NFC_OFF_POWER_OFF];
            if (tag != null) {
                return tag.checkNdef(ndefInfo);
            }
            return NfcService.NFC_ON_READER_DEFAULT;
        }

        public TransceiveResult transceive(int nativeHandle, byte[] data, boolean raw) throws RemoteException {
            NfcService.this.mContext.enforceCallingOrSelfPermission(NfcService.NFC_PERM, NfcService.NFC_PERM_ERROR);
            if (!NfcService.this.isNfcEnabled()) {
                return null;
            }
            TagEndpoint tag = (TagEndpoint) NfcService.this.findObject(nativeHandle);
            if (tag == null) {
                return null;
            }
            if (data.length > getMaxTransceiveLength(tag.getConnectedTechnology())) {
                return new TransceiveResult(NfcService.VEN_CFG_NFC_ON_POWER_ON, null);
            }
            int result;
            int[] targetLost = new int[NfcService.TASK_ENABLE];
            byte[] response = tag.transceive(data, raw, targetLost);
            if (response != null) {
                result = NfcService.SOUND_START;
            } else if (targetLost[NfcService.SOUND_START] == NfcService.TASK_ENABLE) {
                result = NfcService.VEN_CFG_NFC_OFF_POWER_OFF;
            } else {
                result = NfcService.TASK_ENABLE;
            }
            return new TransceiveResult(result, response);
        }

        public NdefMessage ndefRead(int nativeHandle) throws RemoteException {
            NfcService.this.mContext.enforceCallingOrSelfPermission(NfcService.NFC_PERM, NfcService.NFC_PERM_ERROR);
            if (!NfcService.this.isNfcEnabled()) {
                return null;
            }
            TagEndpoint tag = (TagEndpoint) NfcService.this.findObject(nativeHandle);
            if (tag == null) {
                return null;
            }
            byte[] buf = tag.readNdef();
            if (buf == null) {
                return null;
            }
            try {
                return new NdefMessage(buf);
            } catch (FormatException e) {
                return null;
            }
        }

        public int ndefWrite(int nativeHandle, NdefMessage msg) throws RemoteException {
            NfcService.this.mContext.enforceCallingOrSelfPermission(NfcService.NFC_PERM, NfcService.NFC_PERM_ERROR);
            if (!NfcService.this.isNfcEnabled()) {
                return -17;
            }
            TagEndpoint tag = (TagEndpoint) NfcService.this.findObject(nativeHandle);
            if (tag == null) {
                return NfcService.EE_ERROR_IO;
            }
            if (msg == null) {
                return -8;
            }
            if (tag.writeNdef(msg.toByteArray())) {
                return NfcService.SOUND_START;
            }
            return NfcService.EE_ERROR_IO;
        }

        public boolean ndefIsWritable(int nativeHandle) throws RemoteException {
            throw new UnsupportedOperationException();
        }

        public int ndefMakeReadOnly(int nativeHandle) throws RemoteException {
            NfcService.this.mContext.enforceCallingOrSelfPermission(NfcService.NFC_PERM, NfcService.NFC_PERM_ERROR);
            if (!NfcService.this.isNfcEnabled()) {
                return -17;
            }
            TagEndpoint tag = (TagEndpoint) NfcService.this.findObject(nativeHandle);
            if (tag == null || !tag.makeReadOnly()) {
                return NfcService.EE_ERROR_IO;
            }
            return NfcService.SOUND_START;
        }

        public int formatNdef(int nativeHandle, byte[] key) throws RemoteException {
            NfcService.this.mContext.enforceCallingOrSelfPermission(NfcService.NFC_PERM, NfcService.NFC_PERM_ERROR);
            if (!NfcService.this.isNfcEnabled()) {
                return -17;
            }
            TagEndpoint tag = (TagEndpoint) NfcService.this.findObject(nativeHandle);
            if (tag == null || !tag.formatNdef(key)) {
                return NfcService.EE_ERROR_IO;
            }
            return NfcService.SOUND_START;
        }

        public Tag rediscover(int nativeHandle) throws RemoteException {
            NfcService.this.mContext.enforceCallingOrSelfPermission(NfcService.NFC_PERM, NfcService.NFC_PERM_ERROR);
            if (!NfcService.this.isNfcEnabled()) {
                return null;
            }
            TagEndpoint tag = (TagEndpoint) NfcService.this.findObject(nativeHandle);
            if (tag == null) {
                return null;
            }
            tag.removeTechnology(NfcService.TASK_READER_DISABLE);
            tag.removeTechnology(NfcService.MSG_MOCK_NDEF);
            tag.findAndReadNdef();
            return new Tag(tag.getUid(), tag.getTechList(), tag.getTechExtras(), tag.getHandle(), this);
        }

        public int setTimeout(int tech, int timeout) throws RemoteException {
            NfcService.this.mContext.enforceCallingOrSelfPermission(NfcService.NFC_PERM, NfcService.NFC_PERM_ERROR);
            if (NfcService.this.mDeviceHost.setTimeout(tech, timeout)) {
                return NfcService.SOUND_START;
            }
            return -8;
        }

        public int getTimeout(int tech) throws RemoteException {
            NfcService.this.mContext.enforceCallingOrSelfPermission(NfcService.NFC_PERM, NfcService.NFC_PERM_ERROR);
            return NfcService.this.mDeviceHost.getTimeout(tech);
        }

        public void resetTimeouts() throws RemoteException {
            NfcService.this.mContext.enforceCallingOrSelfPermission(NfcService.NFC_PERM, NfcService.NFC_PERM_ERROR);
            NfcService.this.mDeviceHost.resetTimeouts();
        }

        public boolean canMakeReadOnly(int ndefType) throws RemoteException {
            return NfcService.this.mDeviceHost.canMakeReadOnly(ndefType);
        }

        public int getMaxTransceiveLength(int tech) throws RemoteException {
            return NfcService.this.mDeviceHost.getMaxTransceiveLength(tech);
        }

        public boolean getExtendedLengthApdusSupported() throws RemoteException {
            return NfcService.this.mDeviceHost.getExtendedLengthApdusSupported();
        }
    }

    final class TimerOpenSecureElement extends TimerTask {
        TimerOpenSecureElement() {
        }

        public void run() {
            if ("NXP_PN544C3".equals("NXP_PN544C3") && NfcService.this.mSecureElementHandle != 0) {
                if (NfcService.DBG) {
                    Log.d(NfcService.TAG, "Open SMX timer expired");
                }
                try {
                    NfcService.this.mSecureElementService.closeSecureElementConnection(NfcService.this.mSecureElementHandle);
                } catch (RemoteException e) {
                }
            }
        }
    }

    public class ToastHandler {
        private Context mContext;
        private Handler mHandler;

        /* renamed from: com.android.nfc.NfcService.ToastHandler.1 */
        class C00221 extends Thread {
            final /* synthetic */ Runnable val$_runnable;

            C00221(Runnable runnable) {
                this.val$_runnable = runnable;
            }

            public void run() {
                ToastHandler.this.mHandler.post(this.val$_runnable);
            }
        }

        /* renamed from: com.android.nfc.NfcService.ToastHandler.2 */
        class C00232 implements Runnable {
            final /* synthetic */ int val$_duration;
            final /* synthetic */ CharSequence val$_text;

            C00232(CharSequence charSequence, int i) {
                this.val$_text = charSequence;
                this.val$_duration = i;
            }

            public void run() {
                Toast.makeText(ToastHandler.this.mContext, this.val$_text, this.val$_duration).show();
            }
        }

        public ToastHandler(Context _context) {
            this.mContext = _context;
            this.mHandler = new Handler();
        }

        private void runRunnable(Runnable _runnable) {
            Thread thread = new C00221(_runnable);
            thread.start();
            thread.interrupt();
        }

        public void showToast(CharSequence _text, int _duration) {
            runRunnable(new C00232(_text, _duration));
        }
    }

    class WatchDogThread extends Thread {
        final Object mCancelWaiter;
        boolean mCanceled;
        final int mTimeout;

        public WatchDogThread(String threadName, int timeout) {
            super(threadName);
            this.mCancelWaiter = new Object();
            this.mCanceled = NfcService.NFC_ON_READER_DEFAULT;
            this.mTimeout = timeout;
        }

        public void run() {
            try {
                synchronized (this.mCancelWaiter) {
                    this.mCancelWaiter.wait((long) this.mTimeout);
                    if (this.mCanceled) {
                        return;
                    }
                    Log.e(NfcService.TAG, getName() + " Watchdog triggered, aborting.");
                    NfcService.this.mDeviceHost.doAbort();
                }
            } catch (InterruptedException e) {
                Log.w(NfcService.TAG, "Watchdog thread interruped.");
                interrupt();
            }
        }

        public synchronized void cancel() {
            synchronized (this.mCancelWaiter) {
                this.mCanceled = NfcService.SE_BROADCASTS_WITH_HCE;
                this.mCancelWaiter.notify();
            }
        }
    }

    static {
        boolean z;
        boolean z2 = SE_BROADCASTS_WITH_HCE;
        if (Debug.isProductShip() == TASK_ENABLE) {
            z = NFC_ON_READER_DEFAULT;
        } else {
            z = SE_BROADCASTS_WITH_HCE;
        }
        DBG = z;
        NfcStateULockKey = "NfcReaderULock";
        NfcStateChangeKey = "NfcStateChangeKey";
        NFC_READER_ON = TASK_ENABLE;
        NFC_READER_OFF = SOUND_START;
        NFC_READER_BLANK = EE_ERROR_IO;
        NFC_ON_DEFAULT = NFC_ON_READER_DEFAULT;
        menu_split = NFC_ON_READER_DEFAULT;
        SMART_MX_ID_TYPE = TASK_ENABLE;
        UICC_ID_TYPE = VEN_CFG_NFC_OFF_POWER_OFF;
        SECURE_ELEMENT_ESE_ID = TASK_ENABLE;
        SECURE_ELEMENT_UICC_ID = VEN_CFG_NFC_OFF_POWER_OFF;
        mStopDiscoveryDuringCall = NFC_ON_READER_DEFAULT;
        mSecureEventType = null;
        mEnableSwpProactiveCommand = NFC_ON_READER_DEFAULT;
        mHideTerminal = null;
        mIsSecNdefEnabled = SE_BROADCASTS_WITH_HCE;
        mProductName = SystemProperties.get("ro.product.name");
        if (mProductName == null || !mProductName.endsWith("vzw")) {
            z2 = NFC_ON_READER_DEFAULT;
        }
        isVzw = z2;
    }

    public static void enforceAdminPerm(Context context) {
        context.enforceCallingOrSelfPermission(ADMIN_PERM, ADMIN_PERM_ERROR);
    }

    public static void validateUserId(int userId) {
        if (userId != UserHandle.getCallingUserId()) {
            throw new SecurityException("userId passed in it not the calling user.");
        }
    }

    public void enforceNfceeAdminPerm(String pkg) {
    }

    public static NfcService getInstance() {
        return sService;
    }

    public void onRemoteEndpointDiscovered(TagEndpoint tag) {
        sendMessage(SOUND_START, tag);
    }

    public void onCardEmulationDeselected() {
        if (this.mIsHceCapable) {
            sendMessage(TASK_EE_WIPE, null);
        } else {
            sendMessage(TASK_EE_WIPE, null);
        }
    }

    public void onCardEmulationAidSelected4Google(byte[] aid) {
        sendMessage(MSG_CARD_EMULATION_AID_SELECTED, aid);
    }

    public void onCardEmulationAidSelected(byte[] aid, byte[] data, int evtSrc) {
        Pair<byte[], Pair> transactionInfo;
        if (this.mIsHceCapable) {
            transactionInfo = new Pair(aid, new Pair(data, Integer.valueOf(evtSrc)));
            Log.d(TAG, "onCardEmulationAidSelected : Source" + evtSrc);
            sendMessage(TASK_ENABLE, transactionInfo);
        } else {
            transactionInfo = new Pair(aid, new Pair(data, Integer.valueOf(evtSrc)));
            Log.d(TAG, "onCardEmulationAidSelected : Source" + evtSrc);
            sendMessage(TASK_ENABLE, transactionInfo);
        }
    }

    public void onCardEmulationAidSelected(byte[] aid, byte[] data) {
        if ("NXP_PN544C3".equals("NXP_PN544C3")) {
            if (this.mIsHceCapable) {
                sendMessage(TASK_ENABLE, new Pair(aid, data));
            } else {
                sendMessage(TASK_ENABLE, new Pair(aid, data));
            }
        }
    }

    public void onConnectivityEvent(int evtSrc) {
        Log.d(TAG, "onConnectivityEvent : Source" + evtSrc);
        sendMessage(MSG_CONNECTIVITY_EVENT, Integer.valueOf(evtSrc));
    }

    public void onConnectivityEvent() {
        if ("NXP_PN544C3".equals("NXP_PN544C3")) {
            sendMessage(MSG_CONNECTIVITY_EVENT, null);
        }
    }

    public void onHostCardEmulationActivated() {
        if (this.mHostEmulationManager != null) {
            this.mHostEmulationManager.notifyHostEmulationActivated();
        }
    }

    public void onAidRoutingTableFull() {
        Log.d(TAG, "NxpNci: onAidRoutingTableFull: AID Routing Table is FULL!");
    }

    public void onHostCardEmulationData(byte[] data) {
        if (this.mHostEmulationManager != null) {
            this.mHostEmulationManager.notifyHostEmulationData(data);
        }
    }

    public void onHostCardEmulationDeactivated() {
        if (this.mHostEmulationManager != null) {
            this.mHostEmulationManager.notifyNostEmulationDeactivated();
        }
    }

    public void onLlcpLinkActivated(NfcDepEndpoint device) {
        sendMessage(VEN_CFG_NFC_OFF_POWER_OFF, device);
    }

    public void onLlcpLinkDeactivated(NfcDepEndpoint device) {
        sendMessage(VEN_CFG_NFC_ON_POWER_ON, device);
    }

    public void onLlcpFirstPacketReceived(NfcDepEndpoint device) {
        sendMessage(MSG_LLCP_LINK_FIRST_PACKET, device);
    }

    public void onRemoteFieldActivated() {
        if (this.mIsHceCapable) {
            sendMessage(MSG_SE_FIELD_ACTIVATED, null);
        } else {
            sendMessage(MSG_SE_FIELD_ACTIVATED, null);
        }
    }

    public void onRemoteFieldDeactivated() {
        if (this.mIsHceCapable) {
            sendMessage(MSG_SE_FIELD_DEACTIVATED, null);
        } else {
            sendMessage(MSG_SE_FIELD_DEACTIVATED, null);
        }
    }

    public void onSeListenActivated() {
        if (this.mIsHceCapable) {
            sendMessage(MSG_SE_LISTEN_ACTIVATED, null);
        } else {
            sendMessage(MSG_SE_LISTEN_ACTIVATED, null);
        }
        if (!this.mIsHceCapable) {
            return;
        }
        if (this.mAidCache.isNextTapOnHost()) {
            Log.d(TAG, "onSeListenActivated - Skip this one, user select the onHost apk temporarily");
            return;
        }
        if (DBG) {
            Log.d(TAG, "state : " + this.mSeActivationState);
        }
        if (this.mSeActivationState != VEN_CFG_NFC_ON_POWER_ON) {
            this.mSeActivationState = VEN_CFG_NFC_OFF_POWER_OFF;
            if (this.mAidCache.isNextTapOverriden()) {
                if (DBG) {
                    Log.d(TAG, "It means that, user select temporarily app");
                }
                Intent intent = new Intent(TapAgainDialog.ACTION_CLOSE);
                intent.setPackage("com.android.nfc");
                this.mContext.sendBroadcastAsUser(intent, UserHandle.ALL);
            }
        } else if (DBG) {
            Log.d(TAG, "onSeListenActivated - ignore this one during routing changed");
        }
    }

    public void onSeListenDeactivated() {
        if ("NXP_PN544C3".equals("NXP_PN544C3")) {
            if (this.mIsHceCapable) {
                sendMessage(MSG_SE_LISTEN_DEACTIVATED, null);
            } else {
                sendMessage(MSG_SE_LISTEN_DEACTIVATED, null);
            }
            return;
        }
        if (this.mIsHceCapable) {
            sendMessage(MSG_SE_LISTEN_DEACTIVATED, null);
        } else {
            sendMessage(MSG_SE_LISTEN_DEACTIVATED, null);
        }
        if (!this.mIsHceCapable) {
            return;
        }
        if (this.mAidCache == null || !this.mAidCache.isNextTapOnHost()) {
            if (DBG) {
                Log.d(TAG, "state : " + this.mSeActivationState);
            }
            if (this.mSeActivationState == VEN_CFG_NFC_OFF_POWER_OFF) {
                this.mSeActivationState = TASK_ENABLE;
                if (this.mAidCache != null && this.mAidCache.isNextTapOverriden()) {
                    this.mAidCache.setDefaultForNextTap(ActivityManager.getCurrentUser(), null);
                    return;
                }
                return;
            } else if (DBG) {
                Log.d(TAG, "onSeListenDeactivated - ignore this one during routing changed");
                return;
            } else {
                return;
            }
        }
        Log.d(TAG, "onSeListenDeactivated - Skip this one, user select the onHost apk temporarily");
    }

    public void onSeApduReceived(byte[] apdu) {
        if (this.mIsHceCapable) {
            sendMessage(MSG_SE_APDU_RECEIVED, apdu);
        } else {
            sendMessage(MSG_SE_APDU_RECEIVED, apdu);
        }
    }

    public void onSeEmvCardRemoval() {
        if (this.mIsHceCapable) {
            sendMessage(MSG_SE_EMV_CARD_REMOVAL, null);
        } else {
            sendMessage(MSG_SE_EMV_CARD_REMOVAL, null);
        }
    }

    public void onSeMifareAccess(byte[] block) {
        if (this.mIsHceCapable) {
            sendMessage(MSG_SE_MIFARE_ACCESS, block);
        } else {
            sendMessage(MSG_SE_MIFARE_ACCESS, block);
        }
    }

    public void onHciEvtTransaction(byte[][] data) {
        sendMessage(MSG_HCI_EVT_TRANSACTION, data);
    }

    public NfcService(Application nfcApplication) {
        this.mNfcAdapterDev = null;
        this.isGsmaApiSupported = NFC_ON_READER_DEFAULT;
        this.SECURE_ELEMENT_ON_DEFAULT = NFC_ON_READER_DEFAULT;
        this.SECURE_ELEMENT_ID_DEFAULT = SOUND_START;
        this.DEFAULT_ROUTE_ID_DEFAULT = AidRoutingManager.DEFAULT_ROUTE;
        this.mSelectedSeId = SOUND_START;
        this.mUiccRoutingMode = TASK_READER_ENABLE;
        this.mEseRoutingMode = TASK_EE_WIPE;
        this.mTestMode = NFC_ON_READER_DEFAULT;
        this.mNoDiscoveryNfcOn = NFC_ON_READER_DEFAULT;
        this.mOpenSmxPending = NFC_ON_READER_DEFAULT;
        this.isClosed = NFC_ON_READER_DEFAULT;
        this.isOpened = NFC_ON_READER_DEFAULT;
        this.mPollingLoopStarted = SE_BROADCASTS_WITH_HCE;
        this.mReaderModeDeathRecipient = new ReaderModeDeathRecipient();
        this.mP2pStarted = NFC_ON_READER_DEFAULT;
        this.mObjectMap = new HashMap();
        this.mOpenEeMap = new HashMap();
        this.mSePackages = new HashSet();
        this.mPowerShutDown = NFC_ON_READER_DEFAULT;
        this.mSEControllerService = null;
        this.mNfcControllerService = null;
        this.mFeliCa = null;
        this.mCallback = null;
        this.mIsLock = NFC_ON_READER_DEFAULT;
        this.beforeFeliCaLockState = NFC_ON_READER_DEFAULT;
        this.ibeforeFeliCaLockState = EE_ERROR_IO;
        this.isFirstInit = SE_BROADCASTS_WITH_HCE;
        this.mPackagesWithNfcPermission = null;
        this.mNfcEventsPermissionResults = null;
        this.mNfcEventsResultCacheTime = 0;
        this.mHandler = new NfcServiceHandler();
        this.mOwnerReceiver = new C00162();
        this.mReceiver = new C00173();
        this.mTestReceiver = new C00184();
        this.mFeliCaLockReceiver = new C00195();
        this.nfcControlServiceConnection = new C00206();
        this.mNfcSetting = new C00217();
        this.mNfcTagService = new TagService();
        this.mNfcAdapter = new NfcAdapterService();
        this.mExtrasService = new NfcAdapterExtrasService();
        this.mCardEmulationService = new CardEmulationService();
        this.mNfcUtility = new NativeNfcUtility();
        this.mNativeNfcSetting = new NativeNfcSetting();
        Log.i(TAG, "Starting NFC service");
        sService = this;
        this.mContext = nfcApplication;
        this.mContentResolver = this.mContext.getContentResolver();
        this.mDeviceHost = new NativeNfcManager(this.mContext, this);
        this.mScover = new Scover();
        try {
            this.mScover.initialize(this.mContext);
        } catch (IllegalArgumentException e) {
        } catch (SsdkUnsupportedException e2) {
        }
        this.mCoverManager = new ScoverManager(this.mContext);
        this.mStateListener = new C00151();
        this.mCoverManager.registerListener(this.mStateListener);
        this.mHandoverManager = new HandoverManager(this.mContext);
        boolean isNfcProvisioningEnabled = NFC_ON_READER_DEFAULT;
        try {
            isNfcProvisioningEnabled = this.mContext.getResources().getBoolean(C0027R.bool.enable_nfc_provisioning);
        } catch (NotFoundException e3) {
        }
        if (isNfcProvisioningEnabled) {
            this.mInProvisionMode = Secure.getInt(this.mContentResolver, "device_provisioned", SOUND_START) == 0 ? SE_BROADCASTS_WITH_HCE : NFC_ON_READER_DEFAULT;
        } else {
            this.mInProvisionMode = NFC_ON_READER_DEFAULT;
        }
        this.mHandoverManager.setEnabled(!this.mInProvisionMode ? SE_BROADCASTS_WITH_HCE : NFC_ON_READER_DEFAULT);
        this.mNfcDispatcher = new NfcDispatcher(this.mContext, this.mHandoverManager, this.mInProvisionMode);
        this.mP2pLinkManager = new P2pLinkManager(this.mContext, this.mHandoverManager, this.mDeviceHost.getDefaultLlcpMiu(), this.mDeviceHost.getDefaultLlcpRwSize());
        this.mSecureElement = new NativeNfcSecureElement(this.mContext);
        if ("NXP_PN544C3".equals("NXP_PN544C3")) {
            this.mHceRoutingState = VEN_CFG_NFC_OFF_POWER_OFF;
            if ("ROUTE_ON_ALWAYS".equalsIgnoreCase(CscFeature.getInstance().getString("CscFeature_NFC_CardModeRoutingTypeForUicc").toUpperCase())) {
                this.mEeRoutingState = TASK_READER_ENABLE;
                Log.d(TAG, "####mEe ROUTE_ON_ALWAYS");
            } else if ("ROUTE_ON_WHEN_SCREEN_UNLOCK".equalsIgnoreCase(CscFeature.getInstance().getString("CscFeature_NFC_CardModeRoutingTypeForUicc").toUpperCase())) {
                this.mEeRoutingState = VEN_CFG_NFC_ON_POWER_ON;
                Log.d(TAG, "####mEe ROUTE_ON_WHEN_SCREEN_UNLOCK");
            } else {
                this.mEeRoutingState = TASK_ENABLE;
                Log.d(TAG, "####mEe ROUTE_OFF");
            }
        } else {
            this.mEeRoutingState = TASK_ENABLE;
            Log.d(TAG, "####mEe ROUTE_OFF");
        }
        this.mToastHandler = new ToastHandler(this.mContext);
        this.mNfceeAccessControl = new NfceeAccessControl(this.mContext);
        this.mPrefs = this.mContext.getSharedPreferences(PREF, SOUND_START);
        this.mPrefsEditor = this.mPrefs.edit();
        this.mState = TASK_ENABLE;
        this.mIsNdefPushEnabled = this.mPrefs.getBoolean(PREF_NDEF_PUSH_ON, SE_BROADCASTS_WITH_HCE);
        boolean z = ("userdebug".equals(Build.TYPE) || "eng".equals(Build.TYPE)) ? SE_BROADCASTS_WITH_HCE : NFC_ON_READER_DEFAULT;
        this.mIsDebugBuild = z;
        this.mPowerManager = (PowerManager) this.mContext.getSystemService("power");
        this.mRoutingWakeLock = this.mPowerManager.newWakeLock(TASK_ENABLE, "NfcService:mRoutingWakeLock");
        this.mEeWakeLock = this.mPowerManager.newWakeLock(TASK_ENABLE, "NfcService:mEeWakeLock");
        this.mKeyguard = (KeyguardManager) this.mContext.getSystemService("keyguard");
        this.mScreenState = checkScreenState();
        this.mLastScreenState = this.mScreenState;
        ServiceManager.addService(SERVICE_NAME, this.mNfcAdapter);
        if ("BCM2079x".equals("NXP_PN544C3")) {
            this.mBrcmFactory = new NfcFactory(this);
            NfcFactory nfcFactory = this.mBrcmFactory;
            ServiceManager.addService(NfcFactory.SERVICE_NAME, this.mBrcmFactory);
            this.mBrcmPowerMode = new NativeNfcBrcmPowerMode();
        }
        IntentFilter filter = new IntentFilter(NativeNfcManager.INTERNAL_TARGET_DESELECTED_ACTION);
        filter.addAction("android.intent.action.SCREEN_OFF");
        filter.addAction("android.intent.action.SCREEN_ON");
        filter.addAction("android.intent.action.USER_PRESENT");
        filter.addAction(ACTION_CLEAR_COVER_OPEN);
        filter.addAction("android.intent.action.SETTINGS_SOFT_RESET");
        filter.addAction("android.intent.action.USER_SWITCHED");
        filter.addAction("android.intent.action.ACTION_SHUTDOWN");
        filter.addAction("android.intent.action.SIM_STATE_CHANGED");
        filter.addAction("android.intent.action.PHONE_STATE");
        filter.addAction("android.app.action.DEVICE_POLICY_MANAGER_STATE_CHANGED");
        registerForAirplaneMode(filter);
        this.mContext.registerReceiverAsUser(this.mReceiver, UserHandle.ALL, filter, null, null);
        this.mHciEventControl = new HciEventControl(this.mContext);
        filter = new IntentFilter();
        registerForTestMode(filter);
        this.mContext.registerReceiverAsUser(this.mTestReceiver, UserHandle.ALL, filter, null, null);
        this.mTestMode = NFC_ON_READER_DEFAULT;
        this.mNoDiscoveryNfcOn = NFC_ON_READER_DEFAULT;
        mStopDiscoveryDuringCall = CscFeature.getInstance().getEnableStatus("CscFeature_NFC_DisableDiscoveryDuringCall");
        mSecureEventType = CscFeature.getInstance().getString("CscFeature_NFC_SetSecureEventType", "GSMA").toUpperCase();
        mEnableSwpProactiveCommand = CscFeature.getInstance().getEnableStatus("CscFeature_NFC_EnableSwpProactiveCommand", NFC_ON_READER_DEFAULT);
        mHideTerminal = CscFeature.getInstance().getString("CscFeature_SmartcardSvc_HideTerminalCapability", "NONE").toUpperCase();
        if (!("S3FNRN3".equals("NXP_PN544C3") || "S3FWRN5".equals("NXP_PN544C3"))) {
            String v = "3.1.0";
            this.isGsmaApiSupported = Integer.parseInt(v.substring(SOUND_START, v.indexOf("."))) >= VEN_CFG_NFC_ON_POWER_ON ? SE_BROADCASTS_WITH_HCE : NFC_ON_READER_DEFAULT;
            if (this.isGsmaApiSupported) {
                this.mSEControllerService = new SEControllerService();
                this.mNfcControllerService = new NfcControllerService();
                try {
                    ServiceManager.addService(SECONTROLLER_SERVICE_NAME, this.mSEControllerService);
                    ServiceManager.addService(NFCCONTROLLER_SERVICE_NAME, this.mNfcControllerService);
                } catch (Exception e4) {
                    Log.d(TAG, "SRIB-B ");
                }
            }
        }
        this.mSamsungPref = new SamsungPreference();
        if ("BCM2079x".equals("NXP_PN544C3")) {
            Log.d(TAG, "Configure BCM2079x seID: 0xF3 0xF4");
            SECURE_ELEMENT_UICC_ID = 243;
            SECURE_ELEMENT_ESE_ID = 244;
        } else if ("NXP_PN544C3".equals("NXP_PN544")) {
            Log.d(TAG, "Configure NXP_PN544");
            SECURE_ELEMENT_UICC_ID = 11259376;
            SECURE_ELEMENT_ESE_ID = 11259375;
        } else if ("S3FNRN3".equals("NXP_PN544C3") || "S3FWRN5".equals("NXP_PN544C3")) {
            Log.d(TAG, "Configure System LSI seID: 0x03 0x02");
            SECURE_ELEMENT_UICC_ID = VEN_CFG_NFC_ON_POWER_ON;
            SECURE_ELEMENT_ESE_ID = VEN_CFG_NFC_OFF_POWER_OFF;
        }
        if ("NXP_PN544C3".equals("CXD2235BGG")) {
            filter = new IntentFilter();
            filter.addAction(NFC_DISCOVER_START_ACTION);
            filter.addAction(NFC_DISCOVER_STOP_ACTION);
            filter.addAction(NFC_DISCOVER_INIT_ACTION);
            filter.addAction(INTERNAL_LOCKSTATUS_CHANGED_ACTION);
            this.mContext.registerReceiverAsUser(this.mFeliCaLockReceiver, UserHandle.ALL, filter, null, null);
        }
        this.mIsHceCapable = this.mContext.getPackageManager().hasSystemFeature("android.hardware.nfc.hce");
        if (this.mIsHceCapable) {
            this.mIsDefaultApkForHost = NFC_ON_READER_DEFAULT;
            this.mAidRoutingManager = new AidRoutingManager();
            this.mAidCache = new RegisteredAidCache(this.mContext, this.mAidRoutingManager);
            this.mHostEmulationManager = new HostEmulationManager(this.mContext, this.mAidCache);
        }
        if (!this.mIsHceCapable) {
            this.mUiccRoutingMode = getRouteMode(CscFeature.getInstance().getString("CscFeature_NFC_CardModeRoutingTypeForUicc", "ROUTE_ON_ALWAYS"));
            this.mEseRoutingMode = getRouteMode(CscFeature.getInstance().getString("CscFeature_NFC_CardModeRoutingTypeForEse", "ROUTE_ON_WHEN_POWER_ON"));
        }
        if (!"NXP_PN544C3".equals("NXP_PN544C3")) {
            this.mCardEmulationRoutingManager = new CardEmulationRoutingManager(this.mContext, this.mAidRoutingManager);
        }
        IntentFilter ownerFilter;
        if (this.mIsHceCapable) {
            ownerFilter = new IntentFilter(NativeNfcManager.INTERNAL_TARGET_DESELECTED_ACTION);
            ownerFilter.addAction("android.intent.action.EXTERNAL_APPLICATIONS_AVAILABLE");
            ownerFilter.addAction("android.intent.action.EXTERNAL_APPLICATIONS_UNAVAILABLE");
            ownerFilter.addAction(ACTION_MASTER_CLEAR_NOTIFICATION);
            this.mContext.registerReceiver(this.mOwnerReceiver, ownerFilter);
            ownerFilter = new IntentFilter();
            ownerFilter.addAction("android.intent.action.PACKAGE_ADDED");
            ownerFilter.addAction("android.intent.action.PACKAGE_REMOVED");
            ownerFilter.addDataScheme("package");
            this.mContext.registerReceiver(this.mOwnerReceiver, ownerFilter);
            updatePackageCache();
            this.mSeActivationState = TASK_ENABLE;
            this.mIsRoutingTableDirty = SE_BROADCASTS_WITH_HCE;
        } else {
            ownerFilter = new IntentFilter(NativeNfcManager.INTERNAL_TARGET_DESELECTED_ACTION);
            ownerFilter.addAction("android.intent.action.EXTERNAL_APPLICATIONS_AVAILABLE");
            ownerFilter.addAction("android.intent.action.EXTERNAL_APPLICATIONS_UNAVAILABLE");
            ownerFilter.addAction(ACTION_MASTER_CLEAR_NOTIFICATION);
            this.mContext.registerReceiver(this.mOwnerReceiver, ownerFilter);
            ownerFilter = new IntentFilter();
            ownerFilter.addAction("android.intent.action.PACKAGE_ADDED");
            ownerFilter.addAction("android.intent.action.PACKAGE_REMOVED");
            ownerFilter.addDataScheme("package");
            this.mContext.registerReceiver(this.mOwnerReceiver, ownerFilter);
            updatePackageCache();
            this.mSeActivationState = TASK_ENABLE;
            this.mIsRoutingTableDirty = SE_BROADCASTS_WITH_HCE;
        }
        if ("NXP_PN544C3".equals("CXD2235BGG")) {
            try {
                this.mContext.bindService(new Intent(INfcControlService.class.getName()), this.nfcControlServiceConnection, TASK_ENABLE);
            } catch (Exception e5) {
                Log.e(TAG, "BindService Exception: " + e5.getMessage());
            }
        }
        EnableDisableTask enableDisableTask = new EnableDisableTask();
        Integer[] numArr = new Integer[TASK_ENABLE];
        numArr[SOUND_START] = Integer.valueOf(VEN_CFG_NFC_ON_POWER_ON);
        enableDisableTask.execute(numArr);
    }

    private void setDefaultTechnologyRoutingInfo() {
        int offHostHandle = SOUND_START;
        String isoDefault = this.mCardEmulationRoutingManager.getDefaultRoute(TASK_ENABLE);
        String techDefault = this.mCardEmulationRoutingManager.getDefaultRoute(VEN_CFG_NFC_OFF_POWER_OFF);
        int defaultIsoHandle = this.mCardEmulationRoutingManager.getRouteDestination(isoDefault);
        int techHandle = this.mCardEmulationRoutingManager.getRouteDestination(techDefault);
        if (this.mIsHceCapable) {
            if (isoDefault.equalsIgnoreCase(SECURE_ELEMENT_UICC_NAME)) {
                offHostHandle = this.mCardEmulationRoutingManager.isMultiSeSupport() ? SECURE_ELEMENT_ESE_ID : SOUND_START;
            } else {
                if (isoDefault.equalsIgnoreCase(SECURE_ELEMENT_ESE_NAME)) {
                    offHostHandle = this.mCardEmulationRoutingManager.isMultiSeSupport() ? SECURE_ELEMENT_UICC_ID : SOUND_START;
                } else {
                    if (isoDefault.equalsIgnoreCase(HCE_DEVICE_HOST_NAME)) {
                        offHostHandle = techHandle;
                    }
                }
            }
        }
        int screenState = this.mCardEmulationRoutingManager.getScreenState(techDefault);
        int powerState = this.mCardEmulationRoutingManager.getPowerState(techDefault);
        boolean screenOn = (screenState & MSG_SE_FIELD_ACTIVATED) != 0 ? SE_BROADCASTS_WITH_HCE : NFC_ON_READER_DEFAULT;
        boolean screenOff = (screenState & VEN_CFG_NFC_OFF_POWER_OFF) != 0 ? SE_BROADCASTS_WITH_HCE : NFC_ON_READER_DEFAULT;
        boolean screenLock = (screenState & TASK_EE_WIPE) != 0 ? SE_BROADCASTS_WITH_HCE : NFC_ON_READER_DEFAULT;
        boolean switchOn = (powerState & TASK_ENABLE) != 0 ? SE_BROADCASTS_WITH_HCE : NFC_ON_READER_DEFAULT;
        boolean switchOff = (powerState & VEN_CFG_NFC_OFF_POWER_OFF) != 0 ? SE_BROADCASTS_WITH_HCE : NFC_ON_READER_DEFAULT;
        boolean batteryOff = (powerState & TASK_EE_WIPE) != 0 ? SE_BROADCASTS_WITH_HCE : NFC_ON_READER_DEFAULT;
        if (DBG) {
            Log.i(TAG, "setDefaultTechnologyRoutingInfo : defaultHandle =" + Integer.toHexString(defaultIsoHandle) + ", techHandle=" + Integer.toHexString(techHandle) + ", offHostHandle=" + Integer.toHexString(offHostHandle) + ", screenOn=" + screenOn + ", screenOff=" + screenOff + ", screenLock=" + screenLock + ", switchOn=" + switchOn + ", switchOff=" + switchOff + ", batteryOff=" + batteryOff);
        }
        if (this.mIsHceCapable) {
            setDefaultRouteDestinations(defaultIsoHandle, offHostHandle);
        } else {
            setStaticRouteByProto(MSG_SE_FIELD_ACTIVATED, screenOn, screenOff, screenLock, defaultIsoHandle, switchOn, switchOff, batteryOff);
        }
        setStaticRouteByTech(TASK_ENABLE, screenOn, screenOff, screenLock, techHandle, switchOn, switchOff, batteryOff);
        setStaticRouteByTech(VEN_CFG_NFC_OFF_POWER_OFF, screenOn, screenOff, screenLock, techHandle, switchOn, switchOff, batteryOff);
        setStaticRouteByTech(TASK_EE_WIPE, screenOn, screenOff, screenLock, techHandle, switchOn, switchOff, batteryOff);
        if (this.mIsHceCapable) {
            this.mAidRoutingManager.reRouteAllAids();
        } else {
            this.mDeviceHost.routToSecureElement(defaultIsoHandle);
        }
    }

    void initSoundPool() {
        synchronized (this) {
            if (this.mSoundPool == null) {
                this.mSoundPool = new SoundPool(TASK_ENABLE, TASK_READER_ENABLE, SOUND_START);
                Log.e(TAG, "initsound SEC_PRODUCT_FEATURE_AUDIO_SOUNDBOOSTER");
                this.mStartSound = this.mSoundPool.load(this.mContext, C0027R.raw.start, TASK_ENABLE);
                this.mEndSound = this.mSoundPool.load(this.mContext, C0027R.raw.end, TASK_ENABLE);
                this.mErrorSound = this.mSoundPool.load(this.mContext, C0027R.raw.error, TASK_ENABLE);
            }
        }
    }

    void releaseSoundPool() {
        synchronized (this) {
            if (this.mSoundPool != null) {
                this.mSoundPool.release();
                this.mSoundPool = null;
            }
        }
    }

    void registerForAirplaneMode(IntentFilter filter) {
        String airplaneModeRadios = System.getString(this.mContentResolver, "airplane_mode_radios");
        String toggleableRadios = System.getString(this.mContentResolver, "airplane_mode_toggleable_radios");
        this.mIsAirplaneSensitive = airplaneModeRadios == null ? SE_BROADCASTS_WITH_HCE : airplaneModeRadios.contains(SERVICE_NAME);
        this.mIsAirplaneToggleable = toggleableRadios == null ? NFC_ON_READER_DEFAULT : toggleableRadios.contains(SERVICE_NAME);
        if (this.mIsAirplaneSensitive) {
            filter.addAction("android.intent.action.AIRPLANE_MODE");
        }
    }

    void registerForTestMode(IntentFilter filter) {
        filter.addAction(START_SEC_NFC_TEST_CMD);
        filter.addAction(END_SEC_NFC_TEST_CMD);
        filter.addAction(ENABLE_SEC_NFC_DISCOVERY);
        filter.addAction(DISABLE_SEC_NFC_DISCOVERY);
        filter.addAction(NO_DISCOVERY_SEC_NFC_ON);
        filter.addAction(CHECK_SEC_NFC_SIM);
        filter.addAction(CHECK_SEC_NFC_ESE);
        filter.addAction(PRBS_TEST_ON);
        filter.addAction(PRBS_TEST_OFF);
        filter.addAction(GET_FW_VERSION);
        filter.addAction(EEPROM_SET);
        filter.addAction(GET_ESE_TYPE);
        filter.addAction(SET_ESE_TYPE);
        filter.addAction(UICC_IDLE_TIME);
    }

    void updatePackageCache() {
        PackageManager pm = this.mContext.getPackageManager();
        List<PackageInfo> packages = pm.getInstalledPackages(SOUND_START, SOUND_START);
        synchronized (this) {
            this.mInstalledPackages = packages;
            this.mPackagesWithNfcPermission = null;
            List<String> packageList = new ArrayList();
            for (PackageInfo packageInfo : packages) {
                String packageName = packageInfo.packageName;
                if (packageInfo.applicationInfo != null && pm.checkPermission(NFC_PERM, packageName) == 0) {
                    packageList.add(packageName);
                }
                this.mPackagesWithNfcPermission = (String[]) packageList.toArray(new String[packageList.size()]);
                this.mNfcEventsPermissionResults = null;
                this.mNfcEventsResultCacheTime = 0;
            }
        }
    }

    public int checkScreenState() {
        if (!this.mPowerManager.isScreenOn()) {
            return TASK_ENABLE;
        }
        if (!this.mKeyguard.isKeyguardLocked()) {
            return VEN_CFG_NFC_ON_POWER_ON;
        }
        if (Global.getInt(this.mContext.getContentResolver(), "device_provisioned", SOUND_START) != 0) {
            return VEN_CFG_NFC_OFF_POWER_OFF;
        }
        if (!DBG) {
            return VEN_CFG_NFC_ON_POWER_ON;
        }
        Log.d(TAG, "Return unlock screen state for LocalFota");
        return VEN_CFG_NFC_ON_POWER_ON;
    }

    int doOpenSecureElementConnection() {
        this.mEeWakeLock.acquire();
        try {
            if ("NXP_PN544C3".equals("CXD2235BGG")) {
                return SOUND_START;
            }
            int doOpenSecureElementConnection = this.mSecureElement.doOpenSecureElementConnection();
            this.mEeWakeLock.release();
            return doOpenSecureElementConnection;
        } finally {
            this.mEeWakeLock.release();
        }
    }

    byte[] doTransceive(int handle, byte[] cmd) {
        byte[] doTransceiveNoLock;
        synchronized (this) {
            this.mEeWakeLock.acquire();
            try {
                doTransceiveNoLock = doTransceiveNoLock(handle, cmd);
            } finally {
                this.mEeWakeLock.release();
            }
        }
        return doTransceiveNoLock;
    }

    byte[] doTransceiveNoLock(int handle, byte[] cmd) {
        return this.mSecureElement.doTransceive(handle, cmd);
    }

    void doDisconnect(int handle) {
        this.mEeWakeLock.acquire();
        try {
            this.mSecureElement.doDisconnect(handle);
        } finally {
            this.mEeWakeLock.release();
        }
    }

    void saveNfcOnSetting(boolean on) {
        synchronized (this) {
            this.mPrefsEditor.putBoolean(PREF_NFC_ON, on);
            this.mPrefsEditor.apply();
        }
    }

    void saveNfcReaderOnSetting(boolean on) {
        synchronized (this) {
            this.mPrefsEditor.putBoolean(PREF_NFC_READER_ON, on);
            this.mPrefsEditor.apply();
        }
    }

    /* JADX WARNING: inconsistent code. */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public void playSound(int r8) {
        /*
        r7 = this;
        monitor-enter(r7);
        r0 = r7.mSoundPool;	 Catch:{ all -> 0x0013 }
        if (r0 != 0) goto L_0x000e;
    L_0x0005:
        r0 = "NfcService";
        r1 = "Not playing sound when NFC is disabled";
        android.util.Log.w(r0, r1);	 Catch:{ all -> 0x0013 }
        monitor-exit(r7);	 Catch:{ all -> 0x0013 }
    L_0x000d:
        return;
    L_0x000e:
        switch(r8) {
            case 0: goto L_0x0016;
            case 1: goto L_0x0026;
            case 2: goto L_0x0036;
            default: goto L_0x0011;
        };	 Catch:{ all -> 0x0013 }
    L_0x0011:
        monitor-exit(r7);	 Catch:{ all -> 0x0013 }
        goto L_0x000d;
    L_0x0013:
        r0 = move-exception;
        monitor-exit(r7);	 Catch:{ all -> 0x0013 }
        throw r0;
    L_0x0016:
        r0 = r7.mSoundPool;	 Catch:{ all -> 0x0013 }
        r1 = r7.mStartSound;	 Catch:{ all -> 0x0013 }
        r2 = 1065353216; // 0x3f800000 float:1.0 double:5.263544247E-315;
        r3 = 1065353216; // 0x3f800000 float:1.0 double:5.263544247E-315;
        r4 = 0;
        r5 = 0;
        r6 = 1065353216; // 0x3f800000 float:1.0 double:5.263544247E-315;
        r0.play(r1, r2, r3, r4, r5, r6);	 Catch:{ all -> 0x0013 }
        goto L_0x0011;
    L_0x0026:
        r0 = r7.mSoundPool;	 Catch:{ all -> 0x0013 }
        r1 = r7.mEndSound;	 Catch:{ all -> 0x0013 }
        r2 = 1065353216; // 0x3f800000 float:1.0 double:5.263544247E-315;
        r3 = 1065353216; // 0x3f800000 float:1.0 double:5.263544247E-315;
        r4 = 0;
        r5 = 0;
        r6 = 1065353216; // 0x3f800000 float:1.0 double:5.263544247E-315;
        r0.play(r1, r2, r3, r4, r5, r6);	 Catch:{ all -> 0x0013 }
        goto L_0x0011;
    L_0x0036:
        r0 = r7.mSoundPool;	 Catch:{ all -> 0x0013 }
        r1 = r7.mErrorSound;	 Catch:{ all -> 0x0013 }
        r2 = 1065353216; // 0x3f800000 float:1.0 double:5.263544247E-315;
        r3 = 1065353216; // 0x3f800000 float:1.0 double:5.263544247E-315;
        r4 = 0;
        r5 = 0;
        r6 = 1065353216; // 0x3f800000 float:1.0 double:5.263544247E-315;
        r0.play(r1, r2, r3, r4, r5, r6);	 Catch:{ all -> 0x0013 }
        goto L_0x0011;
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.nfc.NfcService.playSound(int):void");
    }

    boolean checkEnablePopupForChinaNalSecurity() {
        synchronized (this) {
            boolean ret = NFC_ON_READER_DEFAULT;
            if (CscFeature.getInstance().getString("CscFeature_Common_ConfigLocalSecurityPolicy").equals("ChinaNalSecurity") == SE_BROADCASTS_WITH_HCE) {
                Log.e(TAG, "ChinaNalSecurity enable");
                ret = SE_BROADCASTS_WITH_HCE;
            }
            if (Global.getInt(this.mContext.getContentResolver(), "device_provisioned", SOUND_START) == 0) {
                Log.e(TAG, "ChinaNalSecurity skip - Settings.Global.DEVICE_PROVISIONED false");
                return NFC_ON_READER_DEFAULT;
            } else if (FactoryTest.isFactoryMode()) {
                Log.e(TAG, "ChinaNalSecurity skip - FactoryTest.isFactoryMode true");
                return NFC_ON_READER_DEFAULT;
            } else if (this.mChnEnablePopupFromAirplaneOn == SE_BROADCASTS_WITH_HCE) {
                Log.e(TAG, "ChinaNalSecurity skip - FromAirplaneOn true");
                this.mChnEnablePopupFromAirplaneOn = NFC_ON_READER_DEFAULT;
                return NFC_ON_READER_DEFAULT;
            } else {
                return ret;
            }
        }
    }

    public static String toHex(byte[] digest) {
        String digits = "0123456789abcdef";
        StringBuilder sb = new StringBuilder(digest.length * VEN_CFG_NFC_OFF_POWER_OFF);
        byte[] arr$ = digest;
        int len$ = arr$.length;
        for (int i$ = SOUND_START; i$ < len$; i$ += TASK_ENABLE) {
            int bi = arr$[i$] & 255;
            sb.append(digits.charAt(bi >> TASK_EE_WIPE));
            sb.append(digits.charAt(bi & MSG_LLCP_LINK_FIRST_PACKET));
        }
        return sb.toString();
    }

    void _nfcEeClose(int callingPid, IBinder binder) throws IOException {
        synchronized (this) {
            if (!isNfcEnabledOrShuttingDown()) {
                throw new IOException("NFC adapter is disabled");
            } else if ("NXP_PN544C3".equals("NXP_PN544C3") && this.mOpenEe == null) {
                throw new IOException("NFC EE closed");
            } else {
                if (DBG) {
                    Log.d(TAG, "_nfcEeClose :: mopenEeMap size is " + this.mOpenEeMap.size());
                }
                if (this.mOpenEeMap.size() == 0) {
                    if (DBG) {
                        Log.d(TAG, "applyRouting #6");
                    }
                    applyRouting(SE_BROADCASTS_WITH_HCE);
                    throw new IOException("NFC EE closed");
                } else if (this.mOpenEeMap.get(Integer.valueOf(callingPid)) == null) {
                    throw new IOException("invalid PID access");
                } else {
                    this.mOpenEe = (OpenSecureElement) this.mOpenEeMap.get(Integer.valueOf(callingPid));
                    if (DBG) {
                        Log.d(TAG, "_nfcEeClose :: mOpenEe = (OpenSecureElement)(mOpenEeMap.get(callingPid)), callingPid : " + callingPid);
                    }
                    if (callingPid != EE_ERROR_IO && callingPid != this.mOpenEe.pid) {
                        throw new SecurityException("Wrong PID");
                    } else if (this.mOpenEe.binder != binder) {
                        throw new SecurityException("Wrong binder handle");
                    } else {
                        if (DBG) {
                            Log.d(TAG, " _nfcEeClose");
                        }
                        binder.unlinkToDeath(this.mOpenEe, SOUND_START);
                        this.mOpenEeMap.remove(Integer.valueOf(callingPid));
                        if (this.mOpenEeMap.size() == 0) {
                            this.mDeviceHost.resetTimeouts();
                            doDisconnect(this.mOpenEe.handle);
                            this.mOpenEe = null;
                        }
                        if ("NXP_PN544C3".equals("NXP_PN544C3")) {
                            binder.unlinkToDeath(this.mOpenEe, SOUND_START);
                            this.mDeviceHost.resetTimeouts();
                            doDisconnect(this.mOpenEe.handle);
                            this.mOpenEe = null;
                            applyRouting(SE_BROADCASTS_WITH_HCE);
                        }
                    }
                }
            }
        }
    }

    private synchronized void maybeEnableDiscovery() {
        if ("NXP_PN544C3".equals("NXP_PN544C3")) {
            if (this.mScreenState < VEN_CFG_NFC_ON_POWER_ON || !isNfcEnabled()) {
                if (DBG) {
                    Log.d(TAG, "mScreenState = " + this.mScreenState);
                }
            } else if (this.mOpenSmxPending) {
                this.mPollingLoopStarted = SE_BROADCASTS_WITH_HCE;
            } else {
                if (DBG) {
                    Log.d(TAG, "maybeEnableDiscovery inside");
                }
                this.mDeviceHost.enableDiscovery();
            }
        }
    }

    private synchronized void maybeDisableDiscovery() {
        if ("NXP_PN544C3".equals("NXP_PN544C3") && isNfcEnabled()) {
            if (this.mOpenSmxPending) {
                this.mPollingLoopStarted = NFC_ON_READER_DEFAULT;
            } else {
                this.mDeviceHost.disableDiscovery();
            }
        }
    }

    boolean isNfcEnabledOrShuttingDown() {
        boolean z;
        synchronized (this) {
            z = (this.mState == VEN_CFG_NFC_ON_POWER_ON || this.mState == TASK_EE_WIPE) ? SE_BROADCASTS_WITH_HCE : NFC_ON_READER_DEFAULT;
        }
        return z;
    }

    boolean isNfcEnabled() {
        boolean z;
        synchronized (this) {
            z = this.mState == VEN_CFG_NFC_ON_POWER_ON ? SE_BROADCASTS_WITH_HCE : NFC_ON_READER_DEFAULT;
        }
        return z;
    }

    static byte[] hexStringToBytes(String s) {
        if (s == null || s.length() == 0) {
            return null;
        }
        int len = s.length();
        if (len % VEN_CFG_NFC_OFF_POWER_OFF != 0) {
            s = '0' + s;
            len += TASK_ENABLE;
        }
        byte[] data = new byte[(len / VEN_CFG_NFC_OFF_POWER_OFF)];
        for (int i = SOUND_START; i < len; i += VEN_CFG_NFC_OFF_POWER_OFF) {
            data[i / VEN_CFG_NFC_OFF_POWER_OFF] = (byte) ((Character.digit(s.charAt(i), MSG_ROUTE_AID) << TASK_EE_WIPE) + Character.digit(s.charAt(i + TASK_ENABLE), MSG_ROUTE_AID));
        }
        return data;
    }

    void applyRouting(boolean force) {
        boolean z = NFC_ON_READER_DEFAULT;
        synchronized (this) {
            if (!(isNfcEnabledOrShuttingDown() && this.mOpenEe == null)) {
                if (menu_split != SE_BROADCASTS_WITH_HCE || this.mState != TASK_READER_ENABLE) {
                    Log.i(TAG, "applyRouting return - 2 ");
                    return;
                } else if ("NXP_PN544C3".equals("NXP_PN544C3")) {
                    Log.i(TAG, "NXP_PN544C3 go through");
                } else {
                    this.mCardEmulationRoutingManager.upateRouting(force);
                    if ("NXP_PN547C2".equals("NXP_PN544C3")) {
                        Log.d(TAG, "KOR Screen state: mScreenState = " + this.mScreenState + ", mLastScreenState = " + this.mLastScreenState);
                        this.mDeviceHost.doSetScreenState(this.mScreenState);
                    } else if ("S3FNRN3".equals("NXP_PN544C3") || "S3FWRN5".equals("NXP_PN544C3")) {
                        Log.d(TAG, "KOR Screen state: mScreenState = " + this.mScreenState + ", mLastScreenState = " + this.mLastScreenState);
                        this.mDeviceHost.doSetScreenState(this.mScreenState);
                    }
                    Log.i(TAG, "applyRouting return - 1 ");
                    return;
                }
            }
            WatchDogThread watchDog = new WatchDogThread("applyRouting", ROUTING_WATCHDOG_MS);
            if (this.mInProvisionMode) {
                boolean z2;
                if (Secure.getInt(this.mContentResolver, "device_provisioned", SOUND_START) == 0) {
                    z2 = SE_BROADCASTS_WITH_HCE;
                } else {
                    z2 = NFC_ON_READER_DEFAULT;
                }
                this.mInProvisionMode = z2;
                if (!this.mInProvisionMode) {
                    this.mNfcDispatcher.disableProvisioningMode();
                    this.mHandoverManager.setEnabled(SE_BROADCASTS_WITH_HCE);
                }
            }
            try {
                watchDog.start();
                if (this.mDeviceHost.enablePN544Quirks() && this.mScreenState == TASK_ENABLE) {
                    if (force || this.mNfcPollingEnabled) {
                        Log.d(TAG, "NFC-C OFF, disconnect");
                        this.mNfcPollingEnabled = NFC_ON_READER_DEFAULT;
                        this.mDeviceHost.disableDiscovery();
                        maybeDisconnectTarget();
                    }
                    if ("NXP_PN544C3".equals("NXP_PN544C3")) {
                        if (this.mEeRoutingState == VEN_CFG_NFC_ON_POWER_ON && (force || this.mNfceeRouteEnabled)) {
                            Log.d(TAG, "####NFC-EE OFF");
                            this.mNfceeRouteEnabled = NFC_ON_READER_DEFAULT;
                            deSelectSecureElement();
                        }
                        if (this.mHceRoutingState == VEN_CFG_NFC_OFF_POWER_OFF && (force || this.mNfcHceRouteEnabled)) {
                            Log.d(TAG, "####NFC-HCE OFF");
                            this.mNfcHceRouteEnabled = NFC_ON_READER_DEFAULT;
                            this.mDeviceHost.disableRoutingToHost();
                        }
                    } else {
                        this.mCardEmulationRoutingManager.upateRouting(force);
                    }
                    return;
                }
                Log.d(TAG, "####apply routing : screen state off not return");
                if ("NXP_PN544C3".equals("NXP_PN544C3")) {
                    if (this.mEeRoutingState == VEN_CFG_NFC_ON_POWER_ON && this.mScreenState == VEN_CFG_NFC_ON_POWER_ON) {
                        if (force || !this.mNfceeRouteEnabled) {
                            Log.d(TAG, "####NFC-EE ON");
                            this.mNfceeRouteEnabled = SE_BROADCASTS_WITH_HCE;
                            SelectSecureElement();
                        }
                    } else if (this.mEeRoutingState != TASK_READER_ENABLE || this.mScreenState < VEN_CFG_NFC_OFF_POWER_OFF) {
                        if (force || this.mNfceeRouteEnabled) {
                            Log.d(TAG, "####NFC-EE OFF");
                            this.mNfceeRouteEnabled = NFC_ON_READER_DEFAULT;
                            deSelectSecureElement();
                        }
                    } else if (force || !this.mNfceeRouteEnabled) {
                        Log.d(TAG, "####NFC-EE ON");
                        this.mNfceeRouteEnabled = SE_BROADCASTS_WITH_HCE;
                        SelectSecureElement();
                    }
                    Log.d(TAG, "####force " + force);
                    Log.d(TAG, "####mNfcHceRouteEnabled " + this.mNfcHceRouteEnabled);
                    if (this.mScreenState < VEN_CFG_NFC_OFF_POWER_OFF || this.mHceRoutingState != VEN_CFG_NFC_OFF_POWER_OFF) {
                        if (force || this.mNfcHceRouteEnabled) {
                            Log.d(TAG, "####NFC-HCE OFF");
                            this.mNfcHceRouteEnabled = NFC_ON_READER_DEFAULT;
                            this.mDeviceHost.disableRoutingToHost();
                        }
                    } else if (force || !this.mNfcHceRouteEnabled) {
                        Log.d(TAG, "####NFC-HCE ON");
                        this.mNfcHceRouteEnabled = SE_BROADCASTS_WITH_HCE;
                        this.mDeviceHost.enableRoutingToHost();
                    }
                    if (this.mIsRoutingTableDirty) {
                        int defaultRoute = getDefaultRoute();
                        Log.d(TAG, "set default route " + defaultRoute);
                        this.mDeviceHost.setDefaultAidRoute(defaultRoute);
                        this.mIsRoutingTableDirty = NFC_ON_READER_DEFAULT;
                    }
                }
                if (!"NXP_PN544C3".equals("NXP_PN544C3")) {
                    if (this.mIsDefaultApkForHost) {
                        DeviceHost deviceHost = this.mDeviceHost;
                        if (TASK_ENABLE != this.mScreenState) {
                            z = SE_BROADCASTS_WITH_HCE;
                        }
                        deviceHost.enableTech_A(z);
                    }
                    this.mCardEmulationRoutingManager.upateRouting(force);
                    Log.d(TAG, "we have to wait for ee mode change");
                }
                if (this.mScreenState >= VEN_CFG_NFC_ON_POWER_ON) {
                    if (force || !this.mNfcPollingEnabled) {
                        if (!"NXP_PN544C3".equals("CXD2235BGG")) {
                            Log.d(TAG, "NFC-C ON");
                            this.mNfcPollingEnabled = SE_BROADCASTS_WITH_HCE;
                            this.mDeviceHost.enableDiscovery();
                        } else if (getLockStatefromDevice() == TASK_ENABLE) {
                            Log.d(TAG, "NFC-C ON");
                            this.mNfcPollingEnabled = SE_BROADCASTS_WITH_HCE;
                            this.mDeviceHost.enableDiscovery();
                        } else {
                            Log.d(TAG, "switch NFC-C ON failed.");
                        }
                    }
                    if (!(this.mReaderModeParams == null || this.mReaderModeEnabled)) {
                        this.mReaderModeEnabled = SE_BROADCASTS_WITH_HCE;
                        this.mDeviceHost.enableReaderMode(this.mReaderModeParams.flags);
                    }
                    if (this.mReaderModeParams == null && this.mReaderModeEnabled) {
                        this.mReaderModeEnabled = NFC_ON_READER_DEFAULT;
                        this.mDeviceHost.disableReaderMode();
                    }
                } else if (this.mScreenState >= VEN_CFG_NFC_OFF_POWER_OFF) {
                    if (this.mInProvisionMode && !this.mNfcPollingEnabled) {
                        Log.d(TAG, "NFC-C ON");
                        this.mNfcPollingEnabled = SE_BROADCASTS_WITH_HCE;
                        this.mDeviceHost.enableDiscovery();
                    }
                } else if (force || this.mNfcPollingEnabled) {
                    Log.d(TAG, "NFC-C OFF");
                    if (this.mReaderModeEnabled) {
                        this.mReaderModeEnabled = NFC_ON_READER_DEFAULT;
                        this.mDeviceHost.disableReaderMode();
                    }
                    this.mNfcPollingEnabled = NFC_ON_READER_DEFAULT;
                    this.mDeviceHost.disableDiscovery();
                }
                if ("NXP_PN547C2".equals("NXP_PN544C3") && !force && this.mScreenState <= VEN_CFG_NFC_OFF_POWER_OFF && this.mLastScreenState != VEN_CFG_NFC_ON_POWER_ON) {
                    Log.d(TAG, "Screen state: mScreenState = " + this.mScreenState + ", mLastScreenState = " + this.mLastScreenState);
                    this.mDeviceHost.doSetScreenState(this.mScreenState);
                }
                watchDog.cancel();
            } finally {
                watchDog.cancel();
            }
        }
    }

    void maybeDisconnectTarget() {
        if (isNfcEnabledOrShuttingDown()) {
            Object[] objectsToDisconnect;
            synchronized (this) {
                Object[] objectValues = this.mObjectMap.values().toArray();
                objectsToDisconnect = Arrays.copyOf(objectValues, objectValues.length);
                this.mObjectMap.clear();
            }
            Object[] arr$ = objectsToDisconnect;
            int len$ = arr$.length;
            for (int i$ = SOUND_START; i$ < len$; i$ += TASK_ENABLE) {
                TagEndpoint o = arr$[i$];
                if (DBG) {
                    Log.d(TAG, "disconnecting " + o.getClass().getName());
                }
                if (o instanceof TagEndpoint) {
                    o.disconnect();
                } else if (o instanceof NfcDepEndpoint) {
                    NfcDepEndpoint device = (NfcDepEndpoint) o;
                    if (device.getMode() == 0) {
                        device.disconnect();
                    }
                }
            }
        }
    }

    Object findObject(int key) {
        Object device;
        synchronized (this) {
            device = this.mObjectMap.get(Integer.valueOf(key));
            if (device == null) {
                Log.w(TAG, "Handle not found");
            }
        }
        return device;
    }

    void registerTagObject(TagEndpoint tag) {
        synchronized (this) {
            this.mObjectMap.put(Integer.valueOf(tag.getHandle()), tag);
        }
    }

    void unregisterObject(int handle) {
        synchronized (this) {
            this.mObjectMap.remove(Integer.valueOf(handle));
        }
    }

    public LlcpSocket createLlcpSocket(int sap, int miu, int rw, int linearBufferLength) throws LlcpException {
        return this.mDeviceHost.createLlcpSocket(sap, miu, rw, linearBufferLength);
    }

    public LlcpConnectionlessSocket createLlcpConnectionLessSocket(int sap, String sn) throws LlcpException {
        return this.mDeviceHost.createLlcpConnectionlessSocket(sap, sn);
    }

    public LlcpServerSocket createLlcpServerSocket(int sap, String sn, int miu, int rw, int linearBufferLength) throws LlcpException {
        return this.mDeviceHost.createLlcpServerSocket(sap, sn, miu, rw, linearBufferLength);
    }

    public void sendMockNdefTag(NdefMessage msg) {
        sendMessage(MSG_MOCK_NDEF, msg);
    }

    public void sendChnEnablePopup() {
        sendMessage(MSG_CHN_ENABLE_POPUP, null);
    }

    public void sendChnEnableDirect() {
        sendMessage(MSG_CHN_ENABLE_DIRECT, null);
    }

    public void sendChnEnableCancel() {
        sendMessage(MSG_CHN_ENABLE_CANCEL, null);
    }

    public void routeAids(String aid, int route, int powerState) {
        Message msg = this.mHandler.obtainMessage();
        msg.what = MSG_ROUTE_AID;
        msg.arg1 = route;
        if (!"NXP_PN544C3".equals("NXP_PN544C3")) {
            String SE = "";
            if (route == 0) {
                SE = HCE_DEVICE_HOST_NAME;
            } else if (route == SECURE_ELEMENT_UICC_ID) {
                SE = SECURE_ELEMENT_UICC_NAME;
            } else if (route == SECURE_ELEMENT_ESE_ID) {
                SE = SECURE_ELEMENT_ESE_NAME;
            }
            msg.arg2 = this.mCardEmulationRoutingManager.getPowerState(SE);
        }
        msg.obj = aid;
        this.mHandler.sendMessage(msg);
    }

    public void unrouteAids(String aid) {
        sendMessage(MSG_UNROUTE_AID, aid);
    }

    public void commitRouting() {
        this.mHandler.sendEmptyMessage(MSG_COMMIT_ROUTING);
    }

    public boolean setDefaultRoute(int routeLoc) {
        if (!"NXP_PN544C3".equals("NXP_PN544C3")) {
            return SE_BROADCASTS_WITH_HCE;
        }
        Log.d(TAG, "setDefaultRoute  ------PN544C3");
        return this.mDeviceHost.setDefaultAidRoute(routeLoc);
    }

    public void setDefaultApkType(boolean bOnHost) {
        this.mIsDefaultApkForHost = bOnHost;
    }

    public void reRouteAid(String aid, int route, boolean isStopDiscovery, boolean isStartDiscovery) {
        if (!"S3FNRN3".equals("NXP_PN544C3") && !"S3FWRN5".equals("NXP_PN544C3")) {
            this.mDeviceHost.reRouteAid(hexStringToBytes(aid), route, isStopDiscovery, isStartDiscovery);
        } else if (isStopDiscovery) {
            this.mDeviceHost.unrouteAid(hexStringToBytes(aid));
        } else {
            String SE = "";
            if (route == 0) {
                SE = HCE_DEVICE_HOST_NAME;
            } else if (route == SECURE_ELEMENT_UICC_ID) {
                SE = SECURE_ELEMENT_UICC_NAME;
            } else if (route == SECURE_ELEMENT_ESE_ID) {
                SE = SECURE_ELEMENT_ESE_NAME;
            }
            this.mDeviceHost.routeAid(hexStringToBytes(aid), route, this.mCardEmulationRoutingManager.getPowerState(SE));
        }
    }

    public boolean onPpseRouted(boolean onHost, int route) {
        Message msg = this.mHandler.obtainMessage();
        msg.what = MSG_PPSE_ROUTED;
        msg.arg1 = onHost ? TASK_ENABLE : SOUND_START;
        msg.arg2 = route;
        this.mHandler.sendMessage(msg);
        return SE_BROADCASTS_WITH_HCE;
    }

    public void adjustDefaultRoutes(int defaultIsoDepRoute, int defaultOffHostRoute) {
        this.mDeviceHost.setDefaultRouteDestinations(defaultIsoDepRoute, defaultOffHostRoute);
    }

    public void clearRouting() {
        if ("NXP_PN544C3".equals("NXP_PN544C3")) {
            this.mHandler.sendEmptyMessage(MSG_CLEAR_ROUTING);
        }
    }

    public boolean sendData(byte[] data) {
        return this.mDeviceHost.sendRawFrame(data);
    }

    public int getDefaultSecureElement() {
        int[] seList = this.mDeviceHost.doGetSecureElementList();
        if (seList == null || seList.length != TASK_ENABLE) {
            return EE_ERROR_IO;
        }
        return seList[SOUND_START];
    }

    public void SelectSecureElement() {
        int[] seList = this.mDeviceHost.doGetSecureElementList();
        Log.d(TAG, "selectSecureElement seList.length " + seList.length);
        for (int i = SOUND_START; i < seList.length; i += TASK_ENABLE) {
            this.mDeviceHost.doSelectSecureElement(seList[i]);
        }
    }

    public void deSelectSecureElement() {
        int[] seList = this.mDeviceHost.doGetSecureElementList();
        Log.d(TAG, "selectSecureElement seList.length " + seList.length);
        for (int i = SOUND_START; i < seList.length; i += TASK_ENABLE) {
            this.mDeviceHost.doDeselectSecureElement(seList[i]);
        }
    }

    void sendMessage(int what, Object obj) {
        Message msg = this.mHandler.obtainMessage();
        msg.what = what;
        msg.obj = obj;
        this.mHandler.sendMessage(msg);
    }

    public boolean readNFcPrefsChange(Context context) {
        boolean readState;
        if (System.getInt(this.mContext.getContentResolver(), "nfc_rw_p2p_switch", SOUND_START) == TASK_ENABLE) {
            readState = SE_BROADCASTS_WITH_HCE;
        } else {
            readState = NFC_ON_READER_DEFAULT;
        }
        Log.d(TAG, "changePrefs : readState =" + readState);
        return readState;
    }

    public int readNFcPrefsULock(Context context) {
        int readState = EE_ERROR_IO;
        try {
            SharedPreferences prefsrw = context.createPackageContext("com.android.settings", VEN_CFG_NFC_OFF_POWER_OFF).getSharedPreferences(NfcStateULockKey, TASK_EE_WIPE);
            if (prefsrw == null) {
                Log.d(TAG, "readNFcPrefsULock : null");
                return EE_ERROR_IO;
            }
            readState = prefsrw.getInt(NfcStateULockKey, EE_ERROR_IO);
            Log.d(TAG, "prefsrw : readState =" + readState);
            return readState;
        } catch (NameNotFoundException e) {
            Log.d(TAG, "readNFcPrefsULock : NameNotFoundException");
        }
    }

    public void nfcAdapterEnableDisable(Context context, int beforeState) {
        boolean beforeNfcSitchState = readNFcPrefsChange(context);
        boolean isCenLocked = getLockStatefromDevice() == 0 ? SE_BROADCASTS_WITH_HCE : NFC_ON_READER_DEFAULT;
        Log.d(TAG, "nfcAdapterEnableDisable : S");
        if (!isCenLocked) {
            if (beforeNfcSitchState) {
                Log.d(TAG, "nfcAdapterEnableDisable : NFC_READER_ON");
                try {
                    Thread.sleep(200);
                } catch (InterruptedException e) {
                }
                Log.d(TAG, "GLOBALCONFIG_COMMON_OPERATOR = ");
                if (!"".equals("KDI")) {
                    Log.d(TAG, "mNfcAdapterDev Ena ON -> OFF");
                    this.mNfcAdapterDev.enable();
                    this.mNfcAdapterDev.disable();
                }
                this.mNfcAdapterDev.enable();
            } else {
                Log.d(TAG, "nfcAdapterEnableDisable : else");
                try {
                    Thread.sleep(200);
                } catch (InterruptedException e2) {
                }
                Log.d(TAG, "GLOBALCONFIG_COMMON_OPERATOR = ");
                if (!"".equals("KDI")) {
                    Log.d(TAG, "mNfcAdapterDev Dis ON -> OFF");
                    this.mNfcAdapterDev.enable();
                    this.mNfcAdapterDev.disable();
                }
            }
        }
        Log.d(TAG, "nfcAdapterEnableDisable : E");
    }

    private int getLockStatefromDevice() {
        int ret;
        Throwable th;
        if (!"NXP_PN544C3".equals("CXD2235BGG")) {
            return TASK_ENABLE;
        }
        Log.d(TAG, "[S]getLockStatefromDevice");
        FileInputStream fileInputStream = null;
        try {
            FileInputStream fileInputStream2 = new FileInputStream(FILEPATH_FELICA_CEN);
            try {
                ret = fileInputStream2.read();
                if (fileInputStream2 != null) {
                    try {
                        fileInputStream2.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                        fileInputStream = fileInputStream2;
                    }
                }
                fileInputStream = fileInputStream2;
            } catch (FileNotFoundException e2) {
                fileInputStream = fileInputStream2;
                ret = EE_ERROR_IO;
                try {
                    Log.e(TAG, "[Ex]FileNotFoundException");
                    if (fileInputStream != null) {
                        try {
                            fileInputStream.close();
                        } catch (IOException e3) {
                            e3.printStackTrace();
                        }
                    }
                    Log.d(TAG, "[E]getLockStatefromDevice (" + ret + ")");
                    return ret;
                } catch (Throwable th2) {
                    th = th2;
                    if (fileInputStream != null) {
                        try {
                            fileInputStream.close();
                        } catch (IOException e32) {
                            e32.printStackTrace();
                        }
                    }
                    throw th;
                }
            } catch (IOException e4) {
                fileInputStream = fileInputStream2;
                ret = EE_ERROR_IO;
                Log.e(TAG, "[Ex]IOException");
                if (fileInputStream != null) {
                    try {
                        fileInputStream.close();
                    } catch (IOException e322) {
                        e322.printStackTrace();
                    }
                }
                Log.d(TAG, "[E]getLockStatefromDevice (" + ret + ")");
                return ret;
            } catch (Throwable th3) {
                th = th3;
                fileInputStream = fileInputStream2;
                if (fileInputStream != null) {
                    fileInputStream.close();
                }
                throw th;
            }
        } catch (FileNotFoundException e5) {
            ret = EE_ERROR_IO;
            Log.e(TAG, "[Ex]FileNotFoundException");
            if (fileInputStream != null) {
                fileInputStream.close();
            }
            Log.d(TAG, "[E]getLockStatefromDevice (" + ret + ")");
            return ret;
        } catch (IOException e6) {
            ret = EE_ERROR_IO;
            Log.e(TAG, "[Ex]IOException");
            if (fileInputStream != null) {
                fileInputStream.close();
            }
            Log.d(TAG, "[E]getLockStatefromDevice (" + ret + ")");
            return ret;
        }
        Log.d(TAG, "[E]getLockStatefromDevice (" + ret + ")");
        return ret;
    }

    boolean isAirplaneModeOn() {
        return System.getInt(this.mContentResolver, "airplane_mode_on", SOUND_START) == TASK_ENABLE ? SE_BROADCASTS_WITH_HCE : NFC_ON_READER_DEFAULT;
    }

    static String stateToString(int state) {
        switch (state) {
            case TASK_ENABLE /*1*/:
                return "off";
            case VEN_CFG_NFC_OFF_POWER_OFF /*2*/:
                return "turning on";
            case VEN_CFG_NFC_ON_POWER_ON /*3*/:
                return "on";
            case TASK_EE_WIPE /*4*/:
                return "turning off";
            default:
                return "<error>";
        }
    }

    static String screenStateToString(int screenState) {
        switch (screenState) {
            case TASK_ENABLE /*1*/:
                return "OFF";
            case VEN_CFG_NFC_OFF_POWER_OFF /*2*/:
                return "ON_LOCKED";
            case VEN_CFG_NFC_ON_POWER_ON /*3*/:
                return "ON_UNLOCKED";
            default:
                return "UNKNOWN";
        }
    }

    void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        if (this.mContext.checkCallingOrSelfPermission("android.permission.DUMP") != 0) {
            pw.println("Permission Denial: can't dump nfc from from pid=" + Binder.getCallingPid() + ", uid=" + Binder.getCallingUid() + " without permission " + "android.permission.DUMP");
            return;
        }
        synchronized (this) {
            pw.println("mState=" + stateToString(this.mState));
            pw.println("mIsZeroClickRequested=" + this.mIsNdefPushEnabled);
            pw.println("mScreenState=" + screenStateToString(this.mScreenState));
            pw.println("mNfcPollingEnabled=" + this.mNfcPollingEnabled);
            pw.println("mNfceeRouteEnabled=" + this.mNfceeRouteEnabled);
            pw.println("mIsAirplaneSensitive=" + this.mIsAirplaneSensitive);
            pw.println("mIsAirplaneToggleable=" + this.mIsAirplaneToggleable);
            pw.println("mOpenEe=" + this.mOpenEe);
            this.mP2pLinkManager.dump(fd, pw, args);
            if (this.mIsHceCapable) {
                this.mAidCache.dump(fd, pw, args);
            }
            this.mNfceeAccessControl.dump(fd, pw, args);
            this.mNfcDispatcher.dump(fd, pw, args);
            pw.println(this.mDeviceHost.dump());
        }
    }

    public void setDefaultRouteDestinations(int defaultIsoDepRoute, int defaultOffHostRoute) {
        if (this.mIsHceCapable) {
            this.mAidRoutingManager.adjustDefaultRoutes(defaultIsoDepRoute, defaultOffHostRoute);
            if ("NXP_PN544C3".equals("NXP_PN544C3")) {
                Log.e(TAG, " >> setDefaultRouteDestinations : defaultIsoDepRoute %x" + defaultIsoDepRoute);
                if (defaultIsoDepRoute == 0) {
                    setStaticRouteByProto(MSG_SE_FIELD_ACTIVATED, SE_BROADCASTS_WITH_HCE, NFC_ON_READER_DEFAULT, SE_BROADCASTS_WITH_HCE, defaultIsoDepRoute, SE_BROADCASTS_WITH_HCE, NFC_ON_READER_DEFAULT, NFC_ON_READER_DEFAULT);
                } else {
                    setStaticRouteByProto(MSG_SE_FIELD_ACTIVATED, SE_BROADCASTS_WITH_HCE, SE_BROADCASTS_WITH_HCE, SE_BROADCASTS_WITH_HCE, defaultIsoDepRoute, SE_BROADCASTS_WITH_HCE, SE_BROADCASTS_WITH_HCE, SE_BROADCASTS_WITH_HCE);
                }
            } else {
                String isoDefault = this.mCardEmulationRoutingManager.getDefaultRoute(TASK_ENABLE);
                int screenState = this.mCardEmulationRoutingManager.getScreenState(isoDefault);
                int powerState = this.mCardEmulationRoutingManager.getPowerState(isoDefault);
                boolean screenOn = (screenState & MSG_SE_FIELD_ACTIVATED) != 0 ? SE_BROADCASTS_WITH_HCE : NFC_ON_READER_DEFAULT;
                boolean screenOff = (screenState & VEN_CFG_NFC_OFF_POWER_OFF) != 0 ? SE_BROADCASTS_WITH_HCE : NFC_ON_READER_DEFAULT;
                boolean screenLock = (screenState & TASK_EE_WIPE) != 0 ? SE_BROADCASTS_WITH_HCE : NFC_ON_READER_DEFAULT;
                boolean switchOn = (powerState & TASK_ENABLE) != 0 ? SE_BROADCASTS_WITH_HCE : NFC_ON_READER_DEFAULT;
                boolean switchOff = (powerState & VEN_CFG_NFC_OFF_POWER_OFF) != 0 ? SE_BROADCASTS_WITH_HCE : NFC_ON_READER_DEFAULT;
                boolean batteryOff = (powerState & TASK_EE_WIPE) != 0 ? SE_BROADCASTS_WITH_HCE : NFC_ON_READER_DEFAULT;
                Log.e(TAG, " >> setDefaultRouteDestinations : defaultIsoDepRoute %x" + defaultIsoDepRoute);
                if (DBG) {
                    Log.i(TAG, "setDefaultRouteDestinations : screenOn=" + screenOn + ",screenOff=" + screenOff + ", screenLock=" + screenLock + ", switchOn=" + switchOn + ", switchOff=" + switchOff + ", batteryOff=" + batteryOff);
                }
                setStaticRouteByProto(MSG_SE_FIELD_ACTIVATED, screenOn, screenOff, screenLock, defaultIsoDepRoute, switchOn, switchOff, batteryOff);
            }
            this.mAidCache.onNfcDisabled();
            this.mAidCache.onNfcEnabled();
            this.mAidRoutingManager.reRouteAllAids();
        }
    }

    public void setStaticRouteByTech(int technology, boolean screenOn, boolean screenOff, boolean screenLock, int route, boolean switchOn, boolean switchOff, boolean batteryOff) {
        this.mDeviceHost.setStaticRouteByTech(technology, screenOn, screenOff, screenLock, route, switchOn, switchOff, batteryOff);
    }

    public void setStaticRouteByProto(int protocol, boolean screenOn, boolean screenOff, boolean screenLock, int route, boolean switchOn, boolean switchOff, boolean batteryOff) {
        this.mDeviceHost.setStaticRouteByProto(protocol, screenOn, screenOff, screenLock, route, switchOn, switchOff, batteryOff);
    }

    public void setHceOffHostAidRoute(byte[] aid, boolean screenOn, boolean screenOff, boolean screenLock, int route, boolean switchOn, boolean switchOff, boolean batteryOff) {
        String output = new String();
        byte[] arr$ = aid;
        int len$ = arr$.length;
        for (int i$ = SOUND_START; i$ < len$; i$ += TASK_ENABLE) {
            Object[] objArr = new Object[TASK_ENABLE];
            objArr[SOUND_START] = Byte.valueOf(arr$[i$]);
            output = output + String.format("%02X:", objArr);
        }
        this.mDeviceHost.setHceOffHostAidRoute(output.substring(SOUND_START, output.length() + EE_ERROR_IO).getBytes(), screenOn, screenOff, screenLock, route, switchOn, switchOff, batteryOff);
    }

    public void removeHceOffHostAidRoute(byte[] aid) {
        String output = new String();
        byte[] arr$ = aid;
        int len$ = arr$.length;
        for (int i$ = SOUND_START; i$ < len$; i$ += TASK_ENABLE) {
            Object[] objArr = new Object[TASK_ENABLE];
            objArr[SOUND_START] = Byte.valueOf(arr$[i$]);
            output = output + String.format("%02X:", objArr);
        }
        this.mDeviceHost.removeHceOffHostAidRoute(output.substring(SOUND_START, output.length() + EE_ERROR_IO).getBytes());
    }

    private void setIcon(boolean on) {
        String iconType = CscFeature.getInstance().getString("CscFeature_NFC_StatusBarIconType");
        StatusBarManager sb = (StatusBarManager) this.mContext.getSystemService("statusbar");
        if (iconType != null && iconType.length() != 0) {
            if (on && "Vzw".equalsIgnoreCase(iconType) && checkScreenState() == VEN_CFG_NFC_OFF_POWER_OFF) {
                Log.i(TAG, "In VZW, NFC icon should not be displayed on lock screen. ");
            } else if (!on) {
                sb.removeIcon(PREF_NFC_ON);
            } else if ("Default".equalsIgnoreCase(iconType)) {
                sb.setIcon(PREF_NFC_ON, C0027R.drawable.stat_sys_nfc_on, SOUND_START, null);
            } else if ("Cityzi".equalsIgnoreCase(iconType)) {
                sb.setIcon(PREF_NFC_ON, C0027R.drawable.stat_sys_nfc_on_nrj, SOUND_START, null);
            } else if ("Att".equalsIgnoreCase(iconType)) {
                sb.setIcon(PREF_NFC_ON, C0027R.drawable.stat_sys_nfc_on_att, SOUND_START, null);
            } else if ("Vzw".equalsIgnoreCase(iconType)) {
                sb.setIcon(PREF_NFC_ON, C0027R.drawable.stat_sys_nfc_on_att, SOUND_START, null);
            } else if ("Tmo".equalsIgnoreCase(iconType)) {
                sb.setIcon(PREF_NFC_ON, C0027R.drawable.stat_sys_nfc_on_att, SOUND_START, null);
            }
        }
    }

    public void updateSecureElement(int seID, boolean enable) {
        if ("BCM2079x".equals("NXP_PN544C3")) {
            this.mDeviceHost.routToSecureElement(seID);
        } else if (enable) {
            this.mDeviceHost.doSelectSecureElement(seID);
        } else {
            this.mDeviceHost.doDeselectSecureElement(seID);
        }
    }

    public void enableRouteToHost(boolean enable) {
        if ("NXP_PN547C2".equals("NXP_PN544C3")) {
            if (DBG) {
                Log.d(TAG, "temporarily disable with PN547C2");
            }
        } else if (enable) {
            this.mDeviceHost.enableRoutingToHost();
        } else {
            this.mDeviceHost.disableRoutingToHost();
        }
    }

    public void setRouteTable(int route, int tech, int proto) {
        if (DBG) {
            Log.d(TAG, "change routing table: [route: " + route + ", tech: " + tech + ", proto: " + proto + "]");
        }
        this.mDeviceHost.setDefaultTechRoute(route, tech, tech);
        this.mDeviceHost.setDefaultProtoRoute(route, proto, proto);
    }

    public void setDefaultRoute(int switch_on, int switch_off, int battery_off) {
        this.mIsRoutingTableDirty = NFC_ON_READER_DEFAULT;
        if (DBG) {
            Log.d(TAG, "setDefaultRoute - NXP ");
        }
        this.mDeviceHost.setDefaultRoute(switch_on, switch_off, battery_off);
    }

    public int getDefaultRoute() {
        if (DBG) {
            Log.d(TAG, "getDefaultRoute - NXP ");
        }
        return this.mPrefs.getInt(PREF_DEFAULT_ROUTE_ID, this.DEFAULT_ROUTE_ID_DEFAULT);
    }

    public void setRouteByTech(int route, int powerState, int screenState, int tech) {
        boolean switchedOff;
        boolean screenOn;
        if ((screenState & MSG_SE_FIELD_ACTIVATED) != 0) {
            screenOn = SE_BROADCASTS_WITH_HCE;
        } else {
            screenOn = NFC_ON_READER_DEFAULT;
        }
        if ((screenState & VEN_CFG_NFC_OFF_POWER_OFF) != 0) {
            boolean screenOff = SE_BROADCASTS_WITH_HCE;
        } else {
            Object obj = SOUND_START;
        }
        if ((screenState & TASK_EE_WIPE) != 0) {
            boolean screenLocked = SE_BROADCASTS_WITH_HCE;
        } else {
            Object obj2 = SOUND_START;
        }
        if ((powerState & TASK_ENABLE) != 0) {
            boolean switchedOn = SE_BROADCASTS_WITH_HCE;
        } else {
            Object obj3 = SOUND_START;
        }
        if ((powerState & VEN_CFG_NFC_OFF_POWER_OFF) != 0) {
            switchedOff = SE_BROADCASTS_WITH_HCE;
        } else {
            switchedOff = NFC_ON_READER_DEFAULT;
        }
        if ((powerState & TASK_EE_WIPE) == 0) {
            Object obj4 = SOUND_START;
        }
        if ("NXP_PN547C2".equals("NXP_PN544C3")) {
            int techToSwtichOff;
            int techToSwticOn = tech;
            if (switchedOff) {
                techToSwtichOff = tech;
            } else {
                techToSwtichOff = SOUND_START;
            }
            this.mDeviceHost.setDefaultTechRoute(route, techToSwticOn, techToSwtichOff);
        }
    }

    private boolean isAllowWifiByDevicePolicy(Context context) {
        DevicePolicyManager dpm = (DevicePolicyManager) context.getApplicationContext().getSystemService("device_policy");
        if (dpm == null || dpm.getAllowWifi(null)) {
            return SE_BROADCASTS_WITH_HCE;
        }
        return NFC_ON_READER_DEFAULT;
    }

    public void onUartAbnormal() {
        if (DBG) {
            Log.d(TAG, "onUartAbnormal() -> FeliCaCenLowHigh()");
        }
        if (this.mFeliCa != null) {
            try {
                this.mFeliCa.FeliCaCenLowHigh();
            } catch (Exception e) {
                Log.e(TAG, "Exception:", e);
            }
        }
    }

    private int getRouteMode(String type) {
        int routingMode = TASK_ENABLE;
        if ("ROUTE_OFF".equalsIgnoreCase(type)) {
            routingMode = TASK_ENABLE;
        } else if ("ROUTE_ON_WHEN_SCREEN_ON".equalsIgnoreCase(type)) {
            routingMode = VEN_CFG_NFC_OFF_POWER_OFF;
        } else if ("ROUTE_ON_WHEN_POWER_ON".equalsIgnoreCase(type)) {
            routingMode = TASK_EE_WIPE;
        } else if ("ROUTE_ON_WHEN_SCREEN_UNLOCK".equalsIgnoreCase(type)) {
            routingMode = VEN_CFG_NFC_ON_POWER_ON;
        } else if ("ROUTE_ON_ALWAYS".equalsIgnoreCase(type)) {
            routingMode = TASK_READER_ENABLE;
        }
        Log.e(TAG, "Routing Mode : " + routingMode);
        return routingMode;
    }
}
