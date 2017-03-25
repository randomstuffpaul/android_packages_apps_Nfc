package com.android.nfc.cardemulation;

import android.content.Context;
import android.content.pm.PackageManager;
import android.util.Log;
import com.android.nfc.NfcService;
import com.sec.android.app.CscFeature;
import java.util.LinkedHashMap;
import java.util.StringTokenizer;

public class CardEmulationRoutingManager {
    static final boolean DBG;
    public static final int DEFAULT_ISO_DEP_ROUTE = 1;
    public static final int DEFAULT_TECH_ROUTE = 2;
    static final int DELEY_BETWEEN_SE_SELECTION = 200;
    public static final int POWER_BATTERY_OFF = 4;
    public static final int POWER_SWICHED_OFF = 2;
    public static final int POWER_SWICHED_ON = 1;
    public static final int SCREEN_OFF = 2;
    public static final int SCREEN_ON_LOCKED = 4;
    public static final int SCREEN_ON_UNLOCKED = 8;
    public static final int SCREEN_UNKNOWN = 1;
    static final String TAG = "CardEmulationRoutingManager";
    AidRoutingManager mAidRoutingManager;
    Context mContext;
    public String mDefaultSe;
    public boolean mEseSupport;
    public boolean mHceSupport;
    public String mIsoDefault;
    private int mLastScreenState;
    public int mNeedRoutingChange;
    LinkedHashMap<String, SeRoutigInfo> mRouteInfoTable;
    public String mTechDefault;
    private boolean mTestMode;
    private String mTestSe;
    public boolean mUiccSupport;

    class SeRoutigInfo {
        boolean enabled;
        int powerState;
        int route;
        int routingState;
        int screenState;

        public SeRoutigInfo(int route, int powerState, int screenState, int routingState, boolean enabled) {
            this.route = route;
            this.powerState = powerState;
            this.screenState = screenState;
            this.routingState = routingState;
            this.enabled = enabled;
        }

        public String toString() {
            String str = "" + "[HOST_NAME : ";
            if (this.route == 0) {
                str = str + NfcService.HCE_DEVICE_HOST_NAME;
            } else if (this.route == NfcService.SECURE_ELEMENT_UICC_ID) {
                str = str + NfcService.SECURE_ELEMENT_UICC_NAME;
            } else if (this.route == NfcService.SECURE_ELEMENT_ESE_ID) {
                str = str + NfcService.SECURE_ELEMENT_ESE_NAME;
            }
            str = str + "][POWER STATE: ";
            if ((this.powerState & CardEmulationRoutingManager.SCREEN_UNKNOWN) != 0) {
                str = str + "SWITCHED_ON";
            }
            if ((this.powerState & CardEmulationRoutingManager.SCREEN_OFF) != 0) {
                str = str + " | SWITCHED_OFF";
            }
            if ((this.powerState & CardEmulationRoutingManager.SCREEN_ON_LOCKED) != 0) {
                str = str + " | BATTERY_OFF";
            }
            str = str + "][SCREEN STATE: ";
            if ((this.screenState & CardEmulationRoutingManager.SCREEN_UNKNOWN) != 0) {
                str = str + " | SCREEN_UNKNOWN";
            }
            if ((this.screenState & CardEmulationRoutingManager.SCREEN_OFF) != 0) {
                str = str + " | SCREEN_OFF";
            }
            if ((this.screenState & CardEmulationRoutingManager.SCREEN_ON_LOCKED) != 0) {
                str = str + " | SCREEN_ON_LOCKED";
            }
            if ((this.screenState & CardEmulationRoutingManager.SCREEN_ON_UNLOCKED) != 0) {
                str = str + " | SCREEN_ON_UNLOCKED";
            }
            return str + "]";
        }
    }

    static {
        DBG = AidRoutingManager.DBG;
    }

    public CardEmulationRoutingManager(Context context, AidRoutingManager aidRoutingManager) {
        this.mUiccSupport = DBG;
        this.mEseSupport = DBG;
        this.mHceSupport = DBG;
        this.mTestMode = DBG;
        this.mTestSe = null;
        this.mNeedRoutingChange = 12;
        this.mRouteInfoTable = new LinkedHashMap();
        this.mContext = context;
        PackageManager pm = this.mContext.getPackageManager();
        String routingMode = "";
        this.mAidRoutingManager = aidRoutingManager;
        if (pm.hasSystemFeature("android.hardware.nfc.hce")) {
            routingMode = "ROUTE_ON_WHEN_SCREEN_ON";
            this.mRouteInfoTable.put(NfcService.HCE_DEVICE_HOST_NAME, new SeRoutigInfo(0, generatePowerState(routingMode), generateScreenState(routingMode), generateRoutingMode(routingMode), DBG));
            this.mHceSupport = true;
        }
        routingMode = CscFeature.getInstance().getString("CscFeature_NFC_CardModeRoutingTypeForUicc", "ROUTE_ON_ALWAYS");
        this.mRouteInfoTable.put(NfcService.SECURE_ELEMENT_UICC_NAME, new SeRoutigInfo(NfcService.SECURE_ELEMENT_UICC_ID, generatePowerState(routingMode), generateScreenState(routingMode), generateRoutingMode(routingMode), DBG));
        this.mUiccSupport = true;
        this.mLastScreenState = 0;
        this.mDefaultSe = CscFeature.getInstance().getString("CscFeature_NFC_DefaultCardModeConfig", NfcService.SECURE_ELEMENT_UICC_NAME);
        StringTokenizer st = new StringTokenizer(this.mDefaultSe, ":");
        this.mIsoDefault = st.nextToken();
        this.mTechDefault = st.hasMoreTokens() ? st.nextToken() : this.mIsoDefault;
    }

    public void adjustRouteTable(int[] seList) {
        for (String SE : this.mRouteInfoTable.keySet()) {
            SeRoutigInfo routingInfo = (SeRoutigInfo) this.mRouteInfoTable.get(SE);
            boolean removed = true;
            if (!SE.equalsIgnoreCase(NfcService.HCE_DEVICE_HOST_NAME)) {
                for (int i = 0; i < seList.length; i += SCREEN_UNKNOWN) {
                    if (seList[i] == routingInfo.route) {
                        removed = DBG;
                        break;
                    }
                }
                if (removed) {
                    if (DBG) {
                        Log.d(TAG, SE + " is set Feature, But we could not find the list");
                    }
                    this.mRouteInfoTable.remove(SE);
                }
            } else if (DBG) {
                Log.d(TAG, "we do not adjust on HCE");
            }
        }
    }

    private int generatePowerState(String routingMode) {
        int powerState = 0;
        if (!"ROUTE_OFF".equalsIgnoreCase(routingMode)) {
            powerState = SCREEN_UNKNOWN;
            if ("ROUTE_ON_ALWAYS".equalsIgnoreCase(routingMode)) {
                powerState = SCREEN_UNKNOWN | 6;
            }
        }
        if (DBG) {
            Log.d(TAG, "powerState: " + powerState);
        }
        return powerState;
    }

    private int generateScreenState(String routingMode) {
        int screenState = 0;
        if (!"ROUTE_OFF".equalsIgnoreCase(routingMode)) {
            if ("ROUTE_ON_WHEN_SCREEN_UNLOCK".equalsIgnoreCase(routingMode)) {
                screenState = 9;
            } else if ("ROUTE_ON_WHEN_SCREEN_ON".equalsIgnoreCase(routingMode)) {
                screenState = 13;
            } else if ("ROUTE_ON_WHEN_POWER_ON".equalsIgnoreCase(routingMode)) {
                screenState = 15;
            } else if ("ROUTE_ON_ALWAYS".equalsIgnoreCase(routingMode)) {
                screenState = 15;
            }
        }
        if (DBG) {
            Log.e(TAG, "screenState : " + screenState);
        }
        return screenState;
    }

    int generateRoutingMode(String routingMode) {
        int mode;
        if ("ROUTE_OFF".equalsIgnoreCase(routingMode)) {
            mode = SCREEN_UNKNOWN;
        } else {
            mode = SCREEN_OFF;
        }
        if (DBG) {
            Log.e(TAG, "Routing Mode : " + mode);
        }
        return mode;
    }

    public String getDefaultRoute(int type) {
        if (SCREEN_UNKNOWN == type) {
            return this.mIsoDefault;
        }
        if (SCREEN_OFF == type) {
            return this.mTechDefault;
        }
        return this.mIsoDefault;
    }

    public void setDefaultRoute(int type, String SE) {
        if (SCREEN_UNKNOWN == type) {
            this.mIsoDefault = SE;
        } else if (SCREEN_OFF == type) {
            this.mTechDefault = SE;
        }
    }

    public void upateRouting(boolean force) {
        if (this.mTestMode) {
            setRouteToSecureElement(this.mTestSe);
            return;
        }
        if ("NXP_PN547C2".equals("NXP_PN544C3")) {
            if (DBG) {
                Log.d(TAG, "NXP spcific function");
            }
            setRoute(force);
        } else if ("S3FNRN3".equals("NXP_PN544C3") || "S3FWRN5".equals("NXP_PN544C3")) {
            if (DBG) {
                Log.d(TAG, "System LSI Advanced Setting");
            }
            setRoute(force);
        }
        for (String updateRouting : this.mRouteInfoTable.keySet()) {
            if (updateRouting(updateRouting, force)) {
                try {
                    Thread.sleep(200);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        if ("BCM2079x".equals("NXP_PN544C3")) {
            if (DBG) {
                Log.d(TAG, "Brcm spcific function");
            }
            int screenState = NfcService.getInstance().checkScreenState();
            if (force || this.mLastScreenState != screenState) {
                this.mLastScreenState = screenState;
                if (this.mHceSupport) {
                    this.mAidRoutingManager.reRouteAllAids();
                    return;
                }
                NfcService.getInstance().updateSecureElement(((SeRoutigInfo) this.mRouteInfoTable.get(this.mIsoDefault)).route, true);
            }
        }
    }

    public void setRoute(boolean force) {
        int screenFlag = NfcService.getInstance().checkScreenState();
        if (DBG) {
            Log.d(TAG, "screenState : " + screenFlag + " , setRoute: " + force);
        }
        if (isNeedEnable(this.mNeedRoutingChange, screenFlag) && force) {
            SeRoutigInfo routeInfo = (SeRoutigInfo) this.mRouteInfoTable.get(this.mIsoDefault);
            if (routeInfo != null) {
                int switchOn;
                int switchOff;
                int batteryOff;
                int route = routeInfo.route;
                this.mAidRoutingManager.getDefaultRoute();
                if ((routeInfo.powerState & SCREEN_UNKNOWN) != 0) {
                    switchOn = route;
                } else {
                    switchOn = 0;
                }
                if ((routeInfo.powerState & SCREEN_OFF) != 0) {
                    switchOff = route;
                } else {
                    switchOff = 0;
                }
                if ((routeInfo.powerState & SCREEN_ON_LOCKED) != 0) {
                    batteryOff = route;
                } else {
                    batteryOff = 0;
                }
                if (DBG) {
                    Log.d(TAG, "CardEmulationRouting - route : " + routeInfo.route);
                    Log.d(TAG, "AidRoutingManager - route : " + route);
                    Log.d(TAG, "switchOn : " + switchOn);
                    Log.d(TAG, "switchOn : " + switchOff);
                    Log.d(TAG, "switchOn : " + batteryOff);
                }
                NfcService.getInstance().setDefaultRoute(switchOn, switchOff, batteryOff);
            }
            routeInfo = (SeRoutigInfo) this.mRouteInfoTable.get(this.mTechDefault);
            if (routeInfo != null) {
                if (DBG) {
                    Log.d(TAG, "call set route by tech");
                }
                NfcService.getInstance().setRouteByTech(routeInfo.route, routeInfo.powerState, routeInfo.screenState, routeInfo.route == 0 ? SCREEN_UNKNOWN : 7);
            }
        }
    }

    public boolean updateRouting(String SE, boolean force) {
        SeRoutigInfo routeInfo = (SeRoutigInfo) this.mRouteInfoTable.get(SE);
        boolean seModified = DBG;
        int screenFlag = NfcService.getInstance().checkScreenState();
        if (DBG) {
            Log.d(TAG, "update Routing - scrrenState : " + displayToString(screenFlag) + " SE : " + SE + "force : " + force);
        }
        if (routeInfo != null) {
            if (DBG) {
                Log.d(TAG, "routeInfo : " + routeInfo.toString());
            }
            if (SE.equalsIgnoreCase(NfcService.HCE_DEVICE_HOST_NAME)) {
                if ("S3FNRN3".equals("NXP_PN544C3") || "S3FWRN5".equals("NXP_PN544C3")) {
                    if (isNeedEnable(routeInfo.screenState, screenFlag)) {
                        if (force || !routeInfo.enabled) {
                            Log.d(TAG, "HCE ON");
                            if (!force) {
                                this.mAidRoutingManager.enableAidsRoutedToHost();
                            }
                            NfcService.getInstance().enableRouteToHost(true);
                            routeInfo.enabled = true;
                            seModified = true;
                        }
                    } else if (force || routeInfo.enabled) {
                        Log.d(TAG, "HCE OFF");
                        if (!force) {
                            this.mAidRoutingManager.disableAidsRoutedToHost();
                        }
                        NfcService.getInstance().enableRouteToHost(DBG);
                        routeInfo.enabled = DBG;
                        seModified = true;
                    }
                } else if (isNeedEnable(routeInfo.screenState, screenFlag) && this.mAidRoutingManager.aidsRoutedToHost()) {
                    if (force || !routeInfo.enabled) {
                        Log.d(TAG, "HCE ON");
                        NfcService.getInstance().enableRouteToHost(true);
                        routeInfo.enabled = true;
                        seModified = true;
                    }
                } else if (force || routeInfo.enabled) {
                    Log.d(TAG, "HCE OFF");
                    NfcService.getInstance().enableRouteToHost(DBG);
                    routeInfo.enabled = DBG;
                    seModified = true;
                }
            } else if (this.mHceSupport || SE.equalsIgnoreCase(this.mDefaultSe)) {
                if ("BCM2079x".equals("NXP_PN544C3")) {
                    return DBG;
                }
                if (!isNeedEnable(routeInfo.screenState, screenFlag) || routeInfo.routingState == SCREEN_UNKNOWN) {
                    if (force || routeInfo.enabled) {
                        Log.d(TAG, "NFC EE - OFF");
                        NfcService.getInstance().updateSecureElement(routeInfo.route, DBG);
                        routeInfo.enabled = DBG;
                        seModified = true;
                    }
                } else if (force || !routeInfo.enabled) {
                    Log.d(TAG, "NFC EE - ON");
                    NfcService.getInstance().updateSecureElement(routeInfo.route, true);
                    routeInfo.enabled = true;
                    seModified = true;
                }
            } else if (!DBG) {
                return DBG;
            } else {
                Log.d(TAG, "We don't any change non default SE" + SE);
                return DBG;
            }
        }
        return seModified;
    }

    public void setRouteToSecureElement(String SE) {
        if (DBG) {
            Log.d(TAG, "force route to " + SE);
        }
        String disableSE = SE.equalsIgnoreCase(NfcService.SECURE_ELEMENT_UICC_NAME) ? NfcService.SECURE_ELEMENT_ESE_NAME : NfcService.SECURE_ELEMENT_UICC_NAME;
        if ("BCM2079x".equals("NXP_PN544C3")) {
            if (DBG) {
                Log.d(TAG, "BRCM");
            }
            int isodep = 0;
            int offhost = 243;
            if (NfcService.SECURE_ELEMENT_UICC_NAME.equals(SE)) {
                isodep = 243;
                offhost = 244;
            } else if (NfcService.SECURE_ELEMENT_ESE_NAME.equals(SE)) {
                isodep = 244;
            }
            NfcService.getInstance().setStaticRouteByTech(SCREEN_UNKNOWN, true, true, true, isodep, true, true, true);
            NfcService.getInstance().setStaticRouteByTech(SCREEN_OFF, true, true, true, isodep, true, true, true);
            NfcService.getInstance().setDefaultRouteDestinations(isodep, offhost);
        } else if ("NXP_PN547C2".equals("NXP_PN544C3")) {
            if (DBG) {
                Log.d(TAG, "NXP-PN547C2");
            }
            routeInfo = (SeRoutigInfo) this.mRouteInfoTable.get(this.mTestSe);
            if (routeInfo != null) {
                route = routeInfo.route;
                int switchOn = (routeInfo.powerState & SCREEN_UNKNOWN) != 0 ? route : 0;
                int switchOff = (routeInfo.powerState & SCREEN_OFF) != 0 ? route : 0;
                int batteryOff = (routeInfo.powerState & SCREEN_ON_LOCKED) != 0 ? route : 0;
                if (DBG) {
                    Log.d(TAG, "route : " + route);
                    Log.d(TAG, "switchOn : " + switchOn);
                    Log.d(TAG, "switchOn : " + switchOff);
                    Log.d(TAG, "switchOn : " + batteryOff);
                }
                NfcService.getInstance().setDefaultRoute(switchOn, switchOff, batteryOff);
                return;
            }
            Log.d(TAG, this.mTestSe + " is not supported");
        } else {
            Log.d(TAG, "setRouteToSecureElement : Enter system LSI path");
            if (DBG) {
                Log.d(TAG, "S_LSI");
            }
            route = -1;
            Log.d(TAG, "Disable SE");
            routeInfo = (SeRoutigInfo) this.mRouteInfoTable.get(disableSE);
            if (routeInfo != null) {
                NfcService.getInstance().updateSecureElement(routeInfo.route, DBG);
                routeInfo.enabled = DBG;
                try {
                    Thread.sleep(200);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            Log.d(TAG, "Enable SE");
            routeInfo = (SeRoutigInfo) this.mRouteInfoTable.get(SE);
            if (routeInfo != null) {
                NfcService.getInstance().updateSecureElement(routeInfo.route, true);
                routeInfo.enabled = true;
                route = routeInfo.route;
                if (DBG) {
                    Log.d(TAG, "route = " + route);
                }
            }
            routeInfo = (SeRoutigInfo) this.mRouteInfoTable.get(NfcService.HCE_DEVICE_HOST_NAME);
            if (routeInfo != null) {
                NfcService.getInstance().enableRouteToHost(DBG);
                routeInfo.enabled = DBG;
            }
            if (route != -1) {
                NfcService.getInstance().setRouteTable(route, 3, SCREEN_ON_UNLOCKED);
            }
        }
    }

    public void selectSecureElement(String SE) {
        SeRoutigInfo routeInfo = (SeRoutigInfo) this.mRouteInfoTable.get(SE);
        if (routeInfo != null) {
            NfcService.getInstance().updateSecureElement(routeInfo.route, true);
            routeInfo.enabled = true;
        }
    }

    public void deSelectSecureElement(String SE) {
        SeRoutigInfo routeInfo = (SeRoutigInfo) this.mRouteInfoTable.get(SE);
        if (routeInfo != null) {
            NfcService.getInstance().updateSecureElement(routeInfo.route, DBG);
            routeInfo.enabled = DBG;
        }
    }

    public boolean isNeedEnable(int screenState, int flag) {
        if (DBG) {
            Log.d(TAG, "Screen Check : " + screenState);
        }
        if (((((byte) screenState) & (SCREEN_UNKNOWN << flag)) & 255) == 0) {
            return DBG;
        }
        if (!DBG) {
            return true;
        }
        Log.d(TAG, "need to Enable");
        return true;
    }

    String displayToString(int mode) {
        String str = "";
        if (mode == 0) {
            return "SCREEN_UNKNOWN";
        }
        if (mode == SCREEN_UNKNOWN) {
            return "SCREEN_OFF";
        }
        if (mode == SCREEN_OFF) {
            return "SCREEN_ON_LOCKED";
        }
        if (mode == 3) {
            return "SCREEN_ON_UNLOCKED";
        }
        return str;
    }

    public int getPowerState(String SE) {
        SeRoutigInfo routeInfo = (SeRoutigInfo) this.mRouteInfoTable.get(SE);
        return routeInfo != null ? routeInfo.powerState : 0;
    }

    public int getScreenState(String SE) {
        SeRoutigInfo routeInfo = (SeRoutigInfo) this.mRouteInfoTable.get(SE);
        return routeInfo != null ? routeInfo.screenState : 0;
    }

    public int getRoutingState(String SE) {
        SeRoutigInfo routeInfo = (SeRoutigInfo) this.mRouteInfoTable.get(SE);
        return routeInfo != null ? routeInfo.routingState : 0;
    }

    public String getSeName(int route) {
        for (String SE : this.mRouteInfoTable.keySet()) {
            if (route == ((SeRoutigInfo) this.mRouteInfoTable.get(SE)).route) {
                return SE;
            }
        }
        return null;
    }

    public int getRouteDestination(String SE) {
        SeRoutigInfo routeInfo = (SeRoutigInfo) this.mRouteInfoTable.get(SE);
        return routeInfo != null ? routeInfo.route : 0;
    }

    public void setRoutingState(String SE, int routingState) {
        SeRoutigInfo routeInfo = (SeRoutigInfo) this.mRouteInfoTable.get(SE);
        if (routeInfo != null) {
            routeInfo.routingState = routingState;
        }
    }

    public boolean isMultiSeSupport() {
        return (this.mUiccSupport && this.mEseSupport) ? true : DBG;
    }

    public boolean isSeSupport(String SE) {
        if (SE.equalsIgnoreCase(NfcService.SECURE_ELEMENT_UICC_NAME)) {
            return this.mUiccSupport;
        }
        if (SE.equalsIgnoreCase(NfcService.SECURE_ELEMENT_ESE_NAME)) {
            return this.mEseSupport;
        }
        return DBG;
    }

    public void enableTestMode(String se, boolean enable) {
        if (DBG) {
            Log.d(TAG, "testmode : " + enable + ", Target SE : " + se);
        }
        this.mTestMode = enable;
        if (!enable) {
            se = null;
        }
        this.mTestSe = se;
    }
}
