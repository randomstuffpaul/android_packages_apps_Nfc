package com.android.nfc.cardemulation;

import android.os.Debug;
import android.util.Log;
import android.util.SparseArray;
import com.android.nfc.NfcService;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map.Entry;
import java.util.Set;

public class AidRoutingManager {
    static final boolean DBG;
    static int DEFAULT_OFFHOST_ROUTE = 0;
    public static int DEFAULT_ROUTE = 0;
    static final String PPSE = "325041592E5359532E4444463031";
    static final String TAG = "AidRoutingManager";
    final SparseArray<Set<String>> mAidRoutingTable;
    int mDefaultSeId;
    boolean mDirty;
    int mLmrTableSize;
    final Object mLock;
    final HashMap<String, Integer> mPowerForAid;
    final HashMap<String, Integer> mRouteForAid;
    boolean mRoutingChanged;
    final AidRoutingCache mRoutnigCache;

    private native int doGetDefaultOffHostRouteDestination();

    private native int doGetDefaultRouteDestination();

    private native int doGetPaymentAidBlockingMode();

    static {
        DBG = Debug.isProductShip() == 1 ? DBG : true;
        DEFAULT_ROUTE = 0;
        DEFAULT_OFFHOST_ROUTE = 1;
    }

    public AidRoutingManager() {
        this.mLock = new Object();
        this.mAidRoutingTable = new SparseArray();
        this.mRouteForAid = new HashMap();
        this.mPowerForAid = new HashMap();
        this.mLmrTableSize = 500;
        this.mRoutingChanged = DBG;
        this.mDefaultSeId = -1;
        this.mRoutnigCache = new AidRoutingCache();
    }

    public boolean aidsRoutedToHost() {
        boolean z = DBG;
        synchronized (this.mLock) {
            Set<String> aidsToHost = (Set) this.mAidRoutingTable.get(0);
            if (aidsToHost != null && aidsToHost.size() > 0) {
                z = true;
            }
        }
        return z;
    }

    public Set<String> getRoutedAids() {
        Set<String> routedAids = new HashSet();
        synchronized (this.mLock) {
            for (Entry<String, Integer> aidEntry : this.mRouteForAid.entrySet()) {
                routedAids.add(aidEntry.getKey());
            }
        }
        return routedAids;
    }

    public boolean setRouteForAid(String aid, boolean onHost, int route, int power) {
        boolean z = true;
        boolean hceEnabled = aidsRoutedToHost();
        synchronized (this.mLock) {
            int currentRoute = getRouteForAidLocked(aid);
            if (DBG) {
                Log.d(TAG, "Set route for AID: " + aid + ", host: " + onHost + " , route: " + route + ", power: " + power + ", current: 0x" + Integer.toHexString(currentRoute));
            }
            Set<String> aids;
            if ("BCM2079x".equals("NXP_PN544C3")) {
                if (onHost) {
                    route = 0;
                } else if (route == 1) {
                    route = 244;
                } else {
                    route = 243;
                }
                if (route == currentRoute) {
                } else {
                    if (currentRoute != -1) {
                        removeAid(aid);
                    }
                    if (DEFAULT_ROUTE == 0 && DEFAULT_OFFHOST_ROUTE == 0) {
                        Log.e(TAG, "setRouteForAid; deny off-host route, only hce support");
                        z = DBG;
                    } else {
                        if (aid.equals(PPSE)) {
                            Log.d(TAG, "AID matches the PPSE");
                            this.mDirty = NfcService.getInstance().onPpseRouted(onHost, route);
                        }
                        aids = (Set) this.mAidRoutingTable.get(route);
                        if (DBG) {
                            Log.d(TAG, "setRouteForAid(): aids:" + aids);
                        }
                        if (aids == null) {
                            aids = new HashSet();
                            this.mAidRoutingTable.put(route, aids);
                        }
                        aids.add(aid);
                        this.mRouteForAid.put(aid, Integer.valueOf(route));
                        if (route != DEFAULT_ROUTE) {
                            if (DBG) {
                                Log.d(TAG, "routeAids(): aid:" + aid + ", route=" + route);
                            }
                            NfcService.getInstance().routeAids(aid, route, power);
                            this.mDirty = true;
                        }
                    }
                }
            } else {
                if ("S3FNRN3".equals("NXP_PN544C3") || "S3FWRN5".equals("NXP_PN544C3")) {
                    if (route == 1) {
                        route = 2;
                    } else if (route == 2) {
                        route = 3;
                    }
                }
                if (onHost) {
                    route = 0;
                }
                if (!onHost && route == -1) {
                    if (this.mDefaultSeId == -1) {
                        int seId = NfcService.getInstance().getDefaultSecureElement();
                        if (seId == -1) {
                            seId = DEFAULT_OFFHOST_ROUTE;
                        }
                        this.mDefaultSeId = seId;
                    }
                    if (DBG) {
                        Log.d(TAG, "default SE ID: " + this.mDefaultSeId);
                    }
                    route = this.mDefaultSeId;
                }
                if (!this.mRoutingChanged && route == currentRoute) {
                } else {
                    if (currentRoute != route) {
                        removeAid(aid);
                    }
                    aids = (Set) this.mAidRoutingTable.get(route);
                    if (DBG) {
                        Log.d(TAG, "setRouteForAid(): aids:" + aids);
                    }
                    if (aids == null) {
                        aids = new HashSet();
                        this.mAidRoutingTable.put(route, aids);
                    }
                    aids.add(aid);
                    this.mRouteForAid.put(aid, Integer.valueOf(route));
                    if (route != DEFAULT_ROUTE) {
                        if (DBG) {
                            Log.d(TAG, "routeAids(): aid:" + aid + ", route=" + route);
                        }
                        NfcService.getInstance().routeAids(aid, route, power);
                        this.mDirty = true;
                    }
                }
            }
        }
        return z;
    }

    public boolean setRouteForAid(String aid, boolean onHost, int route, int power, boolean isDefaultApp) {
        boolean hceEnabled = aidsRoutedToHost();
        synchronized (this.mLock) {
            int currentRoute = getRouteForAidLocked(aid);
            boolean currentDefault = this.mRoutnigCache.isDefault(aid);
            if (DBG) {
                Log.d(TAG, "Set route for AID: " + aid + ", host: " + onHost + " , route: " + route + ", power: " + power + ", current: 0x" + Integer.toHexString(currentRoute) + " , isDefaultApp: " + isDefaultApp);
            }
            int defaultRoute = NfcService.getInstance().getDefaultRoute();
            if (onHost) {
                route = DEFAULT_ROUTE;
            }
            if (!onHost && route == -1) {
                if (this.mDefaultSeId == -1) {
                    int seId = NfcService.getInstance().getDefaultSecureElement();
                    if (seId == -1) {
                        seId = defaultRoute;
                    }
                    this.mDefaultSeId = seId;
                }
                if (DBG) {
                    Log.d(TAG, "default SE ID: " + this.mDefaultSeId);
                }
                route = this.mDefaultSeId;
            }
            if (route == currentRoute && isDefaultApp == currentDefault) {
                return true;
            }
            if (route != currentRoute || (defaultRoute == route && currentDefault != isDefaultApp)) {
                removeAid(aid);
            }
            Set<String> aids = (Set) this.mAidRoutingTable.get(route);
            if (DBG) {
                Log.d(TAG, "setRouteForAid(): aids:" + aids);
            }
            if (aids == null) {
                aids = new HashSet();
                this.mAidRoutingTable.put(route, aids);
            }
            aids.add(aid);
            this.mRouteForAid.put(aid, Integer.valueOf(route));
            if (!hceEnabled && onHost) {
                this.mDirty = true;
            }
            if (route != defaultRoute || isDefaultApp) {
                if (DBG) {
                    Log.d(TAG, "routeAids(): aid:" + aid + ", route=" + route);
                }
                this.mRoutnigCache.addAid(aid, isDefaultApp, route, power);
                this.mDirty = true;
            }
            return true;
        }
    }

    public void onNfccRoutingTableCleared() {
        synchronized (this.mLock) {
            this.mAidRoutingTable.clear();
            this.mRouteForAid.clear();
            if ("NXP_PN544C3".equals("NXP_PN544C3")) {
                this.mRoutnigCache.clear();
            }
        }
    }

    public boolean removeAid(String aid) {
        if (DBG) {
            Log.d(TAG, "removeAid(String aid): aid:" + aid);
        }
        boolean hceEnabled = aidsRoutedToHost();
        synchronized (this.mLock) {
            int currentRoute = getRouteForAidLocked(aid);
            if (currentRoute == -1) {
                if (DBG) {
                    Log.d(TAG, "removeAid(): No existing route for " + aid);
                }
                return DBG;
            }
            Set<String> aids = (Set) this.mAidRoutingTable.get(currentRoute);
            if (aids == null) {
                return DBG;
            }
            aids.remove(aid);
            this.mRouteForAid.remove(aid);
            if ("BCM2079x".equals("NXP_PN544C3") && aid.equals(PPSE) && DBG) {
                Log.d(TAG, "PPSE AID is removed");
            }
            if ("NXP_PN544C3".equals("NXP_PN544C3")) {
                if (hceEnabled && !aidsRoutedToHost()) {
                    this.mDirty = true;
                }
                if (DBG) {
                    Log.d(TAG, "removeAid(): aid:" + aid + ", currentRoute=" + currentRoute);
                }
                if (this.mRoutnigCache.removeAid(aid)) {
                    this.mDirty = true;
                }
            }
            if (!("NXP_PN544C3".equals("NXP_PN544C3") || currentRoute == DEFAULT_ROUTE)) {
                if (DBG) {
                    Log.d(TAG, "unrouteAids(): aid:" + aid + ", currentRoute=" + currentRoute);
                }
                NfcService.getInstance().unrouteAids(aid);
                this.mDirty = true;
            }
            return true;
        }
    }

    public void commitRouting() {
        synchronized (this.mLock) {
            Log.d(TAG, "commitRouting");
            if ("NXP_PN544C3".equals("NXP_PN544C3")) {
                Log.d(TAG, "commitRouting+++ mDirty =" + this.mDirty);
                if (this.mDirty || true == NfcService.getInstance().mIsRouteForced) {
                    this.mRoutnigCache.commit();
                    NfcService.getInstance().commitRouting();
                    this.mDirty = DBG;
                    this.mRoutingChanged = DBG;
                } else if (DBG) {
                    Log.d(TAG, "Not committing routing because table not dirty.");
                }
            } else if (this.mDirty || this.mRoutingChanged) {
                NfcService.getInstance().commitRouting();
                this.mDirty = DBG;
                this.mRoutingChanged = DBG;
            } else if (DBG) {
                Log.d(TAG, "Not committing routing because table not dirty.");
            }
        }
    }

    int getRouteForAidLocked(String aid) {
        Integer route = (Integer) this.mRouteForAid.get(aid);
        return route == null ? -1 : route.intValue();
    }

    public int getDefaultRoute() {
        return DEFAULT_ROUTE;
    }

    public void setDefaultRoute(int route, boolean changed) {
        if (changed && DEFAULT_ROUTE != route) {
            this.mRoutingChanged = true;
        }
        DEFAULT_ROUTE = route;
    }

    public int getMaxTableSize() {
        return this.mLmrTableSize;
    }

    public void setTableSize(int size) {
        if (DBG) {
            Log.d(TAG, "setTable " + size);
        }
        this.mLmrTableSize = size;
    }

    public void getDefaultRouteDestination() {
        synchronized (this.mLock) {
            DEFAULT_ROUTE = doGetDefaultRouteDestination();
            if (DBG) {
                Log.d(TAG, "DEFAULT_ROUTE=0x" + Integer.toHexString(DEFAULT_ROUTE));
            }
        }
    }

    public void getDefaultOffHostRouteDestination() {
        synchronized (this.mLock) {
            DEFAULT_OFFHOST_ROUTE = doGetDefaultOffHostRouteDestination();
            if (DBG) {
                Log.d(TAG, "DEFAULT_OFFHOST_ROUTE=0x" + Integer.toHexString(DEFAULT_OFFHOST_ROUTE));
            }
        }
    }

    public void reRouteAllAids() {
        synchronized (this.mLock) {
            NfcService.getInstance().reRouteAid("00", 0, true, DBG);
            for (Entry<String, Integer> aidEntry : this.mRouteForAid.entrySet()) {
                int route = ((Integer) aidEntry.getValue()).intValue();
                String aid = (String) aidEntry.getKey();
                Log.d(TAG, "reRouteAllAids; sec elem id=0x" + Integer.toHexString(route) + " aid=" + aid);
                NfcService.getInstance().reRouteAid(aid, route, DBG, DBG);
            }
            NfcService.getInstance().reRouteAid("00", 0, DBG, true);
        }
    }

    public void adjustDefaultRoutes(int defaultIsoDepRoute, int defaultOffHostRoute) {
        synchronized (this.mLock) {
            DEFAULT_ROUTE = defaultIsoDepRoute;
            DEFAULT_OFFHOST_ROUTE = defaultOffHostRoute;
            NfcService.getInstance().adjustDefaultRoutes(defaultIsoDepRoute, defaultOffHostRoute);
        }
    }

    public void enableAidsRoutedToHost() {
        synchronized (this.mLock) {
            if (DEFAULT_ROUTE == 0) {
                Log.d(TAG, "default is host, don't need to change");
                return;
            }
            Log.d(TAG, "call --- enableAidsRoutedToHost ");
            Set<String> aidsToHost = (Set) this.mAidRoutingTable.get(0);
            if (aidsToHost != null) {
                for (String aid : aidsToHost) {
                    NfcService.getInstance().reRouteAid(aid, 0, DBG, DBG);
                }
            }
        }
    }

    public void disableAidsRoutedToHost() {
        synchronized (this.mLock) {
            if (DEFAULT_ROUTE == 0) {
                Log.d(TAG, "default is host, don't need to change");
                return;
            }
            Log.d(TAG, "call --- disableAidsRoutedToHost ");
            Set<String> aidsToHost = (Set) this.mAidRoutingTable.get(0);
            if (aidsToHost != null) {
                for (String aid : aidsToHost) {
                    NfcService.getInstance().reRouteAid(aid, 0, true, true);
                }
            }
        }
    }

    public boolean isDefaultRouteToDH() {
        return DEFAULT_ROUTE == 0 ? true : DBG;
    }

    public int getPaymentAidBlockingMode() {
        int mode = 0;
        if ("BCM2079x".equals("NXP_PN544C3")) {
            synchronized (this.mLock) {
                mode = doGetPaymentAidBlockingMode();
            }
        }
        return mode;
    }
}
