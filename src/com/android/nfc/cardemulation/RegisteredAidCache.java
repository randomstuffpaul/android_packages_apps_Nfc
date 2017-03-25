package com.android.nfc.cardemulation;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.net.Uri;
import android.nfc.cardemulation.ApduServiceInfo;
import android.nfc.cardemulation.ApduServiceInfo.AidGroup;
import android.nfc.cardemulation.ApduServiceInfo.ESeInfo;
import android.os.Handler;
import android.os.Looper;
import android.os.UserHandle;
import android.provider.Settings.Secure;
import android.util.Log;
import com.android.nfc.NfcService;
import com.android.nfc.cardemulation.RegisteredServicesCache.Callback;
import com.google.android.collect.Maps;
import com.sec.android.app.CscFeature;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

public class RegisteredAidCache implements Callback {
    public static final String ACTION_CHANGE_DEFAULT_EXT = "com.android.nfc.cardemulation.action.ACTION_CHANGE_DEFAULT_EXT";
    static final boolean DBG;
    static final int PMT_AID_BLOCKING_ALL_PAYMENT = 2;
    static final int PMT_AID_BLOCKING_DEFAULT_ONLY = 1;
    static final int PMT_AID_BLOCKING_DISABLED = 0;
    static final String TAG = "RegisteredAidCache";
    private boolean isDefaultServiceOnly;
    final HashMap<String, AidResolveInfo> mAidCache;
    private ArrayList<String> mAidCheckBuffer;
    final TreeMap<String, ArrayList<ApduServiceInfo>> mAidToOffHostServices;
    final TreeMap<String, ArrayList<ApduServiceInfo>> mAidToServices;
    ArrayList<String> mBlockList;
    public final HashMap<String, Set<String>> mCategoryAids;
    final HashMap<String, ComponentName> mCategoryDefaults;
    final Context mContext;
    final Handler mHandler;
    final Object mLock;
    private boolean mNeedCheckBuffer;
    ComponentName mNextTapComponent;
    boolean mNfcEnabled;
    final AidRoutingManager mRoutingManager;
    HashMap<ComponentName, Integer> mRoutingTypes;
    final RegisteredServicesCache mServiceCache;
    List<ApduServiceInfo> mServicesCache;
    final SettingsObserver mSettingsObserver;

    final class AidResolveInfo {
        String aid;
        ApduServiceInfo defaultService;
        List<ApduServiceInfo> services;
        ApduServiceInfo unConditionalService;

        AidResolveInfo() {
        }
    }

    private final class SettingsObserver extends ContentObserver {
        public SettingsObserver(Handler handler) {
            super(handler);
        }

        public void onChange(boolean selfChange, Uri uri) {
            super.onChange(selfChange, uri);
            synchronized (RegisteredAidCache.this.mLock) {
                int currentUser = ActivityManager.getCurrentUser();
                if (RegisteredAidCache.this.updateFromSettingsLocked(currentUser)) {
                    if ("NXP_PN544C3".equals("NXP_PN544C3")) {
                        RegisteredAidCache.this.generateAidTreeLocked(currentUser, RegisteredAidCache.this.mServicesCache);
                        RegisteredAidCache.this.generateAidCategoriesLocked(RegisteredAidCache.this.mServicesCache);
                        RegisteredAidCache.this.generateAidCacheLocked();
                        RegisteredAidCache.this.updateRoutingLocked(currentUser);
                    } else {
                        RegisteredAidCache.this.updateBlockedList();
                        RegisteredAidCache.this.generateAidCacheLocked();
                        RegisteredAidCache.this.updateRoutingLocked();
                    }
                    RegisteredAidCache.this.defaultPaymentChangedFromSetting(true);
                } else if (RegisteredAidCache.DBG) {
                    Log.d(RegisteredAidCache.TAG, "Not updating aid cache + routing: nothing changed.");
                }
            }
        }
    }

    static {
        DBG = AidRoutingManager.DBG;
    }

    public RegisteredAidCache(Context context, AidRoutingManager routingManager) {
        this.mAidToServices = new TreeMap();
        this.mAidToOffHostServices = new TreeMap();
        this.mAidCache = Maps.newHashMap();
        this.mCategoryDefaults = Maps.newHashMap();
        this.mRoutingTypes = Maps.newHashMap();
        this.mCategoryAids = Maps.newHashMap();
        this.mHandler = new Handler(Looper.getMainLooper());
        this.mLock = new Object();
        this.mNextTapComponent = null;
        this.mNfcEnabled = DBG;
        this.mBlockList = null;
        this.mSettingsObserver = new SettingsObserver(this.mHandler);
        this.mContext = context;
        this.mServiceCache = new RegisteredServicesCache(context, this);
        this.mRoutingManager = routingManager;
        this.mContext.getContentResolver().registerContentObserver(Secure.getUriFor("nfc_payment_default_component"), true, this.mSettingsObserver, -1);
        updateFromSettingsLocked(ActivityManager.getCurrentUser());
        this.mAidCheckBuffer = new ArrayList();
        this.mNeedCheckBuffer = DBG;
        String configPaymentRoutingTbl = CscFeature.getInstance().getString("CscFeature_NFC_ConfigPaymentRoutingTbl", "defaultOnly");
        this.isDefaultServiceOnly = "defaultOnly".equalsIgnoreCase(configPaymentRoutingTbl);
        if (DBG) {
            Log.d(TAG, "Routing Table Config : " + configPaymentRoutingTbl);
        }
    }

    public boolean isNextTapOverriden() {
        boolean z;
        synchronized (this.mLock) {
            z = this.mNextTapComponent != null ? true : DBG;
        }
        return z;
    }

    public AidResolveInfo resolveAidPrefix(String aid) {
        AidResolveInfo resolveInfo = null;
        synchronized (this.mLock) {
            if (!"NXP_PN544C3".equals("NXP_PN544C3")) {
                SortedMap<String, ArrayList<ApduServiceInfo>> matches = this.mAidToServices.subMap(aid, aid.substring(PMT_AID_BLOCKING_DISABLED, aid.length() - 1) + ((char) (aid.charAt(aid.length() - 1) + PMT_AID_BLOCKING_DEFAULT_ONLY)));
                if (matches.isEmpty()) {
                } else {
                    resolveInfo = (AidResolveInfo) this.mAidCache.get(matches.firstKey());
                    resolveInfo.aid = (String) matches.firstKey();
                }
            } else if (this.mAidToServices.containsKey(aid)) {
                resolveInfo = (AidResolveInfo) this.mAidCache.get(aid);
                resolveInfo.aid = aid;
            }
        }
        return resolveInfo;
    }

    public AidResolveInfo resolveAidPrefixOffHost(String aid) {
        AidResolveInfo resolveInfo = null;
        if ("NXP_PN544C3".equals("NXP_PN544C3")) {
            synchronized (this.mLock) {
                if (this.mAidToOffHostServices.containsKey(aid)) {
                    resolveInfo = new AidResolveInfo();
                    resolveInfo.services = (List) this.mAidToOffHostServices.get(aid);
                    resolveInfo.aid = aid;
                }
            }
        }
        return resolveInfo;
    }

    public String getCategoryForAid(String aid) {
        String str;
        synchronized (this.mLock) {
            Set<String> paymentAids = (Set) this.mCategoryAids.get("payment");
            if (paymentAids == null || !paymentAids.contains(aid)) {
                str = "other";
            } else {
                str = "payment";
            }
        }
        return str;
    }

    public AidResolveInfo resolveAidPartialMatch(String selectAid) {
        if (!"NXP_PN544C3".equals("NXP_PN544C3")) {
            return null;
        }
        AidResolveInfo info = null;
        for (String nextAid = selectAid.substring(PMT_AID_BLOCKING_DISABLED, selectAid.length() - 1); nextAid.length() >= 10; nextAid = nextAid.substring(PMT_AID_BLOCKING_DISABLED, nextAid.length() - 1)) {
            info = resolveAidPrefixOffHost(nextAid);
            if (info != null) {
                return info;
            }
        }
        return info;
    }

    public boolean isDefaultServiceForAid(int userId, ComponentName service, String aid) {
        synchronized (this.mLock) {
            boolean serviceFound = this.mServiceCache.hasService(userId, service);
        }
        if (!serviceFound) {
            if (DBG) {
                Log.d(TAG, "Didn't find passed in service, invalidating cache.");
            }
            this.mServiceCache.invalidateCache(userId);
        }
        synchronized (this.mLock) {
            AidResolveInfo resolveInfo = (AidResolveInfo) this.mAidCache.get(aid);
        }
        if (resolveInfo == null) {
            return DBG;
        }
        if (resolveInfo.services == null || resolveInfo.services.size() == 0) {
            return DBG;
        }
        if (resolveInfo.defaultService != null) {
            return service.equals(resolveInfo.defaultService.getComponent());
        }
        return resolveInfo.services.size() == PMT_AID_BLOCKING_DEFAULT_ONLY ? service.equals(((ApduServiceInfo) resolveInfo.services.get(PMT_AID_BLOCKING_DISABLED)).getComponent()) : DBG;
    }

    public boolean setDefaultServiceForCategory(int userId, ComponentName service, String category) {
        if ("payment".equals(category)) {
            synchronized (this.mLock) {
                if (service != null) {
                    if (!this.mServiceCache.hasService(userId, service)) {
                        Log.e(TAG, "Could not find default service to make default: " + service);
                    }
                }
                Secure.putStringForUser(this.mContext.getContentResolver(), "nfc_payment_default_component", service != null ? service.flattenToString() : null, userId);
            }
            return true;
        }
        Log.e(TAG, "Not allowing defaults for category " + category);
        return DBG;
    }

    public boolean isDefaultServiceForCategory(int userId, String category, ComponentName service) {
        synchronized (this.mLock) {
            if (DBG) {
                Log.d(TAG, "Didn't find passed in service, invalidating cache.");
            }
            boolean serviceFound = this.mServiceCache.hasService(userId, service);
        }
        if (!serviceFound) {
            if (DBG) {
                Log.d(TAG, "Didn't find passed in service, invalidating cache.");
            }
            this.mServiceCache.invalidateCache(userId);
        }
        ComponentName defaultService = getDefaultServiceForCategory(userId, category, true);
        if (defaultService == null || !defaultService.equals(service)) {
            return DBG;
        }
        return true;
    }

    ComponentName getDefaultServiceForCategory(int userId, String category, boolean validateInstalled) {
        if ("payment".equals(category)) {
            synchronized (this.mLock) {
                String name = Secure.getStringForUser(this.mContext.getContentResolver(), "nfc_payment_default_component", userId);
                if (name != null) {
                    ComponentName service = ComponentName.unflattenFromString(name);
                    if (!validateInstalled || service == null) {
                        return service;
                    }
                    if (!this.mServiceCache.hasService(userId, service)) {
                        service = null;
                    }
                    return service;
                }
                return null;
            }
        }
        Log.e(TAG, "Not allowing defaults for category " + category);
        return null;
    }

    public List<ApduServiceInfo> getServicesForCategory(int userId, String category) {
        return this.mServiceCache.getServicesForCategory(userId, category);
    }

    public boolean setDefaultForNextTap(int userId, ComponentName service) {
        synchronized (this.mLock) {
            if (service != null) {
                this.mNextTapComponent = service;
            } else {
                this.mNextTapComponent = null;
            }
            if ("NXP_PN544C3".equals("NXP_PN544C3")) {
                generateAidTreeLocked(userId, this.mServicesCache);
                generateAidCategoriesLocked(this.mServicesCache);
                generateAidCacheLocked();
                updateRoutingLocked(userId);
            } else {
                updateBlockedList();
                generateAidCacheLocked();
                updateRoutingLocked();
            }
            defaultPaymentChangedFromSetting(DBG);
        }
        return true;
    }

    AidResolveInfo resolveAidLocked(List<ApduServiceInfo> resolvedServices, String aid) {
        if (resolvedServices == null || resolvedServices.size() == 0) {
            if (DBG) {
                Log.d(TAG, "Could not resolve AID " + aid + " to any service.");
            }
            return null;
        }
        AidResolveInfo resolveInfo = new AidResolveInfo();
        if (DBG) {
            Log.d(TAG, "resolveAidLocked: resolving AID " + aid);
        }
        resolveInfo.services = new ArrayList();
        resolveInfo.services.addAll(resolvedServices);
        resolveInfo.defaultService = null;
        ComponentName defaultComponent = this.mNextTapComponent;
        if (DBG) {
            Log.d(TAG, "resolveAidLocked: next tap component is " + defaultComponent);
        }
        Set<String> paymentAids = (Set) this.mCategoryAids.get("payment");
        Iterator i$;
        if (paymentAids == null || !(paymentAids.contains(aid) || isAidBlocked(aid))) {
            for (ApduServiceInfo service : resolvedServices) {
                if (service.getComponent().equals(defaultComponent)) {
                    if (DBG) {
                        Log.d(TAG, "resolveAidLocked: DECISION: cat OTHER AID, routing to (default) " + service.getComponent());
                    }
                    resolveInfo.defaultService = service;
                    if (resolveInfo.defaultService == null) {
                        return resolveInfo;
                    }
                    if (!"NXP_PN544C3".equals("NXP_PN544C3")) {
                        if (resolveInfo.services.size() == PMT_AID_BLOCKING_DEFAULT_ONLY) {
                            resolveInfo.defaultService = (ApduServiceInfo) resolveInfo.services.get(PMT_AID_BLOCKING_DISABLED);
                            if (DBG) {
                                return resolveInfo;
                            }
                            Log.d(TAG, "resolveAidLocked: DECISION: cat OTHER AID, routing to (default) " + resolveInfo.defaultService.getComponent());
                            return resolveInfo;
                        } else if (DBG) {
                            return resolveInfo;
                        } else {
                            Log.d(TAG, "resolveAidLocked: DECISION: cat OTHER AID, routing all");
                            return resolveInfo;
                        }
                    } else if (resolveInfo.services.size() != PMT_AID_BLOCKING_DEFAULT_ONLY && (!this.mAidToOffHostServices.containsKey(aid) || ((ArrayList) this.mAidToOffHostServices.get(aid)).size() == PMT_AID_BLOCKING_DEFAULT_ONLY)) {
                        resolveInfo.defaultService = (ApduServiceInfo) resolveInfo.services.get(PMT_AID_BLOCKING_DISABLED);
                        if (!DBG) {
                            return resolveInfo;
                        }
                        Log.d(TAG, "resolveAidLocked: DECISION: cat OTHER AID, routing to (default) " + resolveInfo.defaultService.getComponent());
                        return resolveInfo;
                    } else if (DBG) {
                        return resolveInfo;
                    } else {
                        Log.d(TAG, "resolveAidLocked: DECISION: cat OTHER AID, routing all");
                        return resolveInfo;
                    }
                }
            }
            if (resolveInfo.defaultService == null) {
                return resolveInfo;
            }
            if (!"NXP_PN544C3".equals("NXP_PN544C3")) {
                if (resolveInfo.services.size() != PMT_AID_BLOCKING_DEFAULT_ONLY) {
                }
                if (DBG) {
                    return resolveInfo;
                }
                Log.d(TAG, "resolveAidLocked: DECISION: cat OTHER AID, routing all");
                return resolveInfo;
            } else if (resolveInfo.services.size() == PMT_AID_BLOCKING_DEFAULT_ONLY) {
                resolveInfo.defaultService = (ApduServiceInfo) resolveInfo.services.get(PMT_AID_BLOCKING_DISABLED);
                if (DBG) {
                    return resolveInfo;
                }
                Log.d(TAG, "resolveAidLocked: DECISION: cat OTHER AID, routing to (default) " + resolveInfo.defaultService.getComponent());
                return resolveInfo;
            } else if (DBG) {
                return resolveInfo;
            } else {
                Log.d(TAG, "resolveAidLocked: DECISION: cat OTHER AID, routing all");
                return resolveInfo;
            }
        }
        if (DBG) {
            Log.d(TAG, "resolveAidLocked: AID " + aid + " is a payment AID");
        }
        if (defaultComponent == null) {
            defaultComponent = (ComponentName) this.mCategoryDefaults.get("payment");
        }
        if (DBG) {
            Log.d(TAG, "resolveAidLocked: default payment component is " + defaultComponent);
        }
        if (resolvedServices.size() == PMT_AID_BLOCKING_DEFAULT_ONLY) {
            ApduServiceInfo resolvedService = (ApduServiceInfo) resolvedServices.get(PMT_AID_BLOCKING_DISABLED);
            if (DBG) {
                Log.d(TAG, "resolveAidLocked: resolved single service " + resolvedService.getComponent());
            }
            if ("NXP_PN544C3".equals("NXP_PN544C3") && defaultComponent != null && defaultComponent.equals(resolvedService.getComponent())) {
                if (DBG) {
                    Log.d(TAG, "resolveAidLocked: DECISION: routing to (default) " + resolvedService.getComponent());
                }
                resolveInfo.defaultService = resolvedService;
            }
            if (!this.isDefaultServiceOnly) {
                boolean foundConflict = DBG;
                i$ = resolvedService.getAidGroups().iterator();
                while (i$.hasNext()) {
                    AidGroup aidGroup = (AidGroup) i$.next();
                    if (aidGroup.getCategory().equals("payment")) {
                        Iterator i$2 = aidGroup.getAids().iterator();
                        while (i$2.hasNext()) {
                            ArrayList<ApduServiceInfo> servicesForAid = (ArrayList) this.mAidToServices.get((String) i$2.next());
                            if (servicesForAid != null && servicesForAid.size() > PMT_AID_BLOCKING_DEFAULT_ONLY) {
                                foundConflict = true;
                            }
                        }
                    }
                }
                if (foundConflict) {
                    if (DBG) {
                        Log.d(TAG, " ---- conflict aid " + aid + " ---- need to host ---");
                    }
                    if (DBG) {
                        Log.d(TAG, "resolveAidLocked: DECISION: routing AID " + aid + " to " + resolvedService.getComponent() + ", but will ask confirmation because its AID group is contended.");
                    }
                } else {
                    if (DBG) {
                        Log.d(TAG, "resolveAidLocked: DECISION: routing to " + resolvedService.getComponent());
                    }
                    resolveInfo.defaultService = resolvedService;
                    resolveInfo.unConditionalService = resolvedService;
                }
                if (defaultComponent == null || !defaultComponent.equals(resolvedService.getComponent())) {
                    return resolveInfo;
                }
                if (DBG) {
                    Log.d(TAG, "resolveAidLocked: DECISION: routing to (default) " + resolvedService.getComponent());
                }
                resolveInfo.defaultService = resolvedService;
                return resolveInfo;
            } else if (defaultComponent == null || !defaultComponent.equals(resolvedService.getComponent())) {
                if (DBG) {
                    Log.d(TAG, "resolveAidLocked: DECISION: not routing because not default payment service.");
                }
                resolveInfo.services.clear();
                return resolveInfo;
            } else {
                if (DBG) {
                    Log.d(TAG, "resolveAidLocked: DECISION: routing to (default) " + resolvedService.getComponent());
                }
                resolveInfo.defaultService = resolvedService;
                return resolveInfo;
            }
        } else if (resolvedServices.size() <= PMT_AID_BLOCKING_DEFAULT_ONLY) {
            return resolveInfo;
        } else {
            if (DBG) {
                Log.d(TAG, "resolveAidLocked: multiple services matched.");
            }
            if (DBG) {
                Log.d(TAG, " ---- muliple aid " + aid + " ---- need to host ---");
            }
            if (defaultComponent == null) {
                return resolveInfo;
            }
            for (ApduServiceInfo service2 : resolvedServices) {
                if (service2.getComponent().equals(defaultComponent)) {
                    if (DBG) {
                        Log.d(TAG, "resolveAidLocked: DECISION: routing to (default) " + service2.getComponent());
                    }
                    resolveInfo.defaultService = service2;
                    if (resolveInfo.defaultService == null) {
                        return resolveInfo;
                    }
                    if (this.isDefaultServiceOnly) {
                        if (DBG) {
                            Log.d(TAG, "resolveAidLocked: DECISION: not routing because not default payment service.");
                        }
                        resolveInfo.services.clear();
                        return resolveInfo;
                    } else if (DBG) {
                        return resolveInfo;
                    } else {
                        Log.d(TAG, "resolveAidLocked: DECISION: routing to all services");
                        return resolveInfo;
                    }
                }
            }
            if (resolveInfo.defaultService == null) {
                return resolveInfo;
            }
            if (this.isDefaultServiceOnly) {
                if (DBG) {
                    Log.d(TAG, "resolveAidLocked: DECISION: not routing because not default payment service.");
                }
                resolveInfo.services.clear();
                return resolveInfo;
            } else if (DBG) {
                return resolveInfo;
            } else {
                Log.d(TAG, "resolveAidLocked: DECISION: routing to all services");
                return resolveInfo;
            }
        }
    }

    void generateAidTreeLocked(List<ApduServiceInfo> services) {
        this.mAidToServices.clear();
        for (ApduServiceInfo service : services) {
            if (DBG) {
                Log.d(TAG, "generateAidTree component: " + service.getComponent());
            }
            Iterator i$ = service.getAids().iterator();
            while (i$.hasNext()) {
                String aid = (String) i$.next();
                if (DBG) {
                    Log.d(TAG, "generateAidTree AID: " + aid);
                }
                if (this.mAidToServices.containsKey(aid)) {
                    ((ArrayList) this.mAidToServices.get(aid)).add(service);
                } else {
                    ArrayList<ApduServiceInfo> aidServices = new ArrayList();
                    aidServices.add(service);
                    this.mAidToServices.put(aid, aidServices);
                }
            }
        }
    }

    void generateAidCategoriesLocked(List<ApduServiceInfo> services) {
        this.mCategoryAids.clear();
        for (ApduServiceInfo service : services) {
            ArrayList<AidGroup> aidGroups = service.getAidGroups();
            if (aidGroups != null) {
                Iterator i$ = aidGroups.iterator();
                while (i$.hasNext()) {
                    AidGroup aidGroup = (AidGroup) i$.next();
                    String groupCategory = aidGroup.getCategory();
                    Set<String> categoryAids = (Set) this.mCategoryAids.get(groupCategory);
                    if (categoryAids == null) {
                        categoryAids = new HashSet();
                    }
                    categoryAids.addAll(aidGroup.getAids());
                    this.mCategoryAids.put(groupCategory, categoryAids);
                }
            }
        }
    }

    void generateAidTreeLocked(int userId, List<ApduServiceInfo> services) {
        this.mAidToServices.clear();
        this.mAidToOffHostServices.clear();
        ComponentName defaultPaymentComponent = getDefaultServiceForCategory(userId, "payment", DBG);
        for (ApduServiceInfo service : services) {
            if (DBG) {
                Log.d(TAG, "generateAidTree component: " + service.getComponent());
            }
            Iterator it = service.getAidGroups().iterator();
            while (it.hasNext()) {
                Iterator i$;
                String aid;
                ArrayList<ApduServiceInfo> aidServices;
                AidGroup group = (AidGroup) it.next();
                if (defaultPaymentComponent == null || !"payment".equals(group.getCategory()) || defaultPaymentComponent.equals(service.getComponent())) {
                    i$ = group.getAids().iterator();
                    while (i$.hasNext()) {
                        aid = (String) i$.next();
                        if (DBG) {
                            Log.d(TAG, "generateAidTree AID: " + aid);
                        }
                        if (this.mAidToServices.containsKey(aid)) {
                            ((ArrayList) this.mAidToServices.get(aid)).add(service);
                        } else {
                            aidServices = new ArrayList();
                            aidServices.add(service);
                            this.mAidToServices.put(aid, aidServices);
                        }
                        if (DBG) {
                            Log.d(TAG, "initialize partial match tree component: " + service.getComponent());
                        }
                        if (!service.isOnHost()) {
                            if (this.mAidToOffHostServices.containsKey(aid)) {
                                ((ArrayList) this.mAidToOffHostServices.get(aid)).add(service);
                            } else {
                                aidServices = new ArrayList();
                                aidServices.add(service);
                                this.mAidToOffHostServices.put(aid, aidServices);
                            }
                        }
                    }
                } else if (DBG) {
                    Log.d(TAG, "ignore all AIDs in none default payment group, !");
                }
            }
        }
        for (ApduServiceInfo service2 : services) {
            if (DBG) {
                Log.d(TAG, "generate partial match tree component: " + service2.getComponent());
            }
            if (!service2.isOnHost()) {
                it = service2.getAids().iterator();
                while (it.hasNext()) {
                    aid = (String) it.next();
                    if (this.mAidToOffHostServices.containsKey(aid)) {
                        for (Entry<String, ArrayList<ApduServiceInfo>> aidEntry : this.mAidToOffHostServices.entrySet()) {
                            String selectAid = (String) aidEntry.getKey();
                            if (selectAid.startsWith(aid) || aid.startsWith(selectAid)) {
                                if (DBG) {
                                    Log.d(TAG, "add AID: " + aid + " to service from AID: " + selectAid);
                                }
                                aidServices = (ArrayList) this.mAidToOffHostServices.get(selectAid);
                                if (!aidServices.contains(service2)) {
                                    aidServices.add(service2);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    boolean updateFromSettingsLocked(int userId) {
        String name = Secure.getStringForUser(this.mContext.getContentResolver(), "nfc_payment_default_component", userId);
        ComponentName newDefault = name != null ? ComponentName.unflattenFromString(name) : null;
        ComponentName oldDefault = (ComponentName) this.mCategoryDefaults.put("payment", newDefault);
        if (DBG) {
            Log.d(TAG, "Updating default component to: " + (name != null ? ComponentName.unflattenFromString(name) : "null"));
        }
        if (newDefault != oldDefault) {
            return true;
        }
        return DBG;
    }

    void generateAidCacheLocked() {
        this.mAidCache.clear();
        for (Entry<String, ArrayList<ApduServiceInfo>> aidEntry : this.mAidToServices.entrySet()) {
            String aid = (String) aidEntry.getKey();
            if (!this.mAidCache.containsKey(aid)) {
                this.mAidCache.put(aid, resolveAidLocked((List) aidEntry.getValue(), aid));
            }
        }
    }

    public void generateRoutingTypeLocked(List<ApduServiceInfo> services) {
        this.mRoutingTypes.clear();
        if (DBG) {
            Log.d(TAG, "generateRoutingTypeLocked");
        }
        for (ApduServiceInfo service : services) {
            if (DBG) {
                Log.d(TAG, "generateRoutingTypeLocked:  " + service.getComponent());
            }
            this.mRoutingTypes.put(service.getComponent(), Integer.valueOf(service.getSEInfo().getSeId()));
            if (DBG) {
                Log.d(TAG, "SeId : " + service.getSEInfo().getSeId());
            }
        }
    }

    void updateRoutingLocked() {
        if (DBG) {
            Log.d(TAG, "updateRoutingLocked");
        }
        if (this.mNfcEnabled) {
            String aid;
            Set<String> handledAids = new HashSet();
            for (Entry<String, AidResolveInfo> aidEntry : this.mAidCache.entrySet()) {
                aid = (String) aidEntry.getKey();
                AidResolveInfo resolveInfo = (AidResolveInfo) aidEntry.getValue();
                if (resolveInfo.services.size() == 0) {
                    this.mRoutingManager.removeAid(aid);
                } else if (resolveInfo.defaultService != null) {
                    NfcService.getInstance().setDefaultApkType(resolveInfo.defaultService.isOnHost());
                    this.mRoutingManager.setRouteForAid(aid, resolveInfo.defaultService.isOnHost(), resolveInfo.defaultService.getSEInfo().getSeId(), PMT_AID_BLOCKING_DEFAULT_ONLY);
                } else if (resolveInfo.services.size() == PMT_AID_BLOCKING_DEFAULT_ONLY) {
                    this.mRoutingManager.setRouteForAid(aid, true, PMT_AID_BLOCKING_DISABLED, PMT_AID_BLOCKING_DISABLED);
                } else if (resolveInfo.services.size() > PMT_AID_BLOCKING_DEFAULT_ONLY) {
                    this.mRoutingManager.setRouteForAid(aid, true, PMT_AID_BLOCKING_DISABLED, PMT_AID_BLOCKING_DISABLED);
                }
                handledAids.add(aid);
            }
            for (String aid2 : this.mRoutingManager.getRoutedAids()) {
                if (!handledAids.contains(aid2)) {
                    if (DBG) {
                        Log.d(TAG, "Removing routing for AID " + aid2 + ", because " + "there are no no interested services.");
                    }
                    this.mRoutingManager.removeAid(aid2);
                }
            }
            this.mRoutingManager.commitRouting();
            if (DBG) {
                Log.d(TAG, "updateRoutingLocked+++++++++++++++++-");
            }
        } else if (DBG) {
            Log.d(TAG, "Not updating routing table because NFC is off.");
        }
    }

    void updateRoutingLocked(int userId) {
        if (this.mNfcEnabled) {
            String aid;
            Set<String> handledAids = new HashSet();
            ComponentName defaultPaymentComponent = getDefaultServiceForCategory(userId, "payment", DBG);
            for (Entry<String, AidResolveInfo> aidEntry : this.mAidCache.entrySet()) {
                aid = (String) aidEntry.getKey();
                AidResolveInfo resolveInfo = (AidResolveInfo) aidEntry.getValue();
                if (resolveInfo.services.size() == 0) {
                    this.mRoutingManager.removeAid(aid);
                } else if (resolveInfo.defaultService != null) {
                    ESeInfo seInfo = resolveInfo.defaultService.getSEInfo();
                    boolean isDefaultPayment = DBG;
                    if ("payment".equals(getCategoryForAid(aid))) {
                        if (this.mNextTapComponent != null) {
                            isDefaultPayment = this.mNextTapComponent.equals(resolveInfo.defaultService.getComponent());
                        } else if (defaultPaymentComponent != null) {
                            isDefaultPayment = defaultPaymentComponent.equals(resolveInfo.defaultService.getComponent());
                        }
                    }
                    this.mRoutingManager.setRouteForAid(aid, resolveInfo.defaultService.isOnHost(), seInfo.getSeId(), PMT_AID_BLOCKING_DISABLED, isDefaultPayment);
                } else if (resolveInfo.services.size() == PMT_AID_BLOCKING_DEFAULT_ONLY) {
                    this.mRoutingManager.setRouteForAid(aid, true, PMT_AID_BLOCKING_DISABLED, PMT_AID_BLOCKING_DISABLED, DBG);
                } else if (resolveInfo.services.size() > PMT_AID_BLOCKING_DEFAULT_ONLY) {
                    this.mRoutingManager.setRouteForAid(aid, true, PMT_AID_BLOCKING_DISABLED, PMT_AID_BLOCKING_DISABLED, DBG);
                }
                handledAids.add(aid);
            }
            for (String aid2 : this.mRoutingManager.getRoutedAids()) {
                if (!handledAids.contains(aid2)) {
                    if (DBG) {
                        Log.d(TAG, "Removing routing for AID " + aid2 + ", because " + "there are no no interested services.");
                    }
                    this.mRoutingManager.removeAid(aid2);
                }
            }
            this.mRoutingManager.commitRouting();
        } else if (DBG) {
            Log.d(TAG, "Not updating routing table because NFC is off.");
        }
    }

    boolean isAidTableFull(List<ApduServiceInfo> services) {
        this.mAidCheckBuffer.clear();
        int max = -1;
        if (DBG) {
            Log.d(TAG, "Check AID full | Current ISO-DEP : " + this.mRoutingManager.getDefaultRoute());
        }
        for (ApduServiceInfo service : services) {
            int temp;
            if (this.isDefaultServiceOnly) {
                temp = getAidListWhenDefaultServiceOnly(service, max);
            } else {
                temp = getAidListWhenDefaultServiceIsLocked(service, max);
            }
            if (temp > max) {
                max = temp;
            }
        }
        if (DBG) {
            Log.d(TAG, "WorstCase - " + this.mAidCheckBuffer);
        }
        if (max < this.mRoutingManager.getMaxTableSize()) {
            return DBG;
        }
        if (DBG) {
            Log.d(TAG, "Buffer Full Msg");
        }
        return true;
    }

    int getAidListWhenDefaultServiceOnly(ApduServiceInfo defaultService, int max) {
        int cnt = PMT_AID_BLOCKING_DISABLED;
        ArrayList<String> aidList = new ArrayList();
        if (DBG) {
            Log.d(TAG, "suppose " + defaultService + " is default service");
        }
        aidList = defaultService.getAids();
        int route = getVendorSpecificSeId(defaultService.isOnHost(), defaultService.getSEInfo().getSeId());
        if (DBG) {
            Log.d(TAG, "route : " + route);
        }
        if (route == this.mRoutingManager.getDefaultRoute()) {
            return PMT_AID_BLOCKING_DISABLED;
        }
        for (int i = PMT_AID_BLOCKING_DISABLED; i < aidList.size(); i += PMT_AID_BLOCKING_DEFAULT_ONLY) {
            String aid = (String) aidList.get(i);
            cnt += (aid.length() / PMT_AID_BLOCKING_ALL_PAYMENT) + 4;
            if (DBG) {
                Log.d(TAG, "aid : " + aid);
            }
        }
        if (cnt > max) {
            if (DBG) {
                Log.d(TAG, "change worst case ");
            }
            this.mAidCheckBuffer.clear();
            this.mAidCheckBuffer.addAll(aidList);
        }
        return cnt;
    }

    int getAidListWhenDefaultServiceIsLocked(ApduServiceInfo defaultService, int max) {
        int cnt = PMT_AID_BLOCKING_DISABLED;
        ArrayList<String> aidList = new ArrayList();
        if (DBG) {
            Log.d(TAG, "suppose " + defaultService + " is default service");
        }
        for (Entry<String, AidResolveInfo> aidEntry : this.mAidCache.entrySet()) {
            String aid = (String) aidEntry.getKey();
            AidResolveInfo resolveInfo = (AidResolveInfo) this.mAidCache.get(aid);
            ApduServiceInfo backUp = resolveInfo.unConditionalService;
            if (DBG) {
                Log.d(TAG, "[aid : " + aid + "]");
            }
            if (defaultService.getAids().contains(aid)) {
                Log.d(TAG, defaultService + " contains [" + aid + "]");
                resolveInfo.unConditionalService = defaultService;
            }
            if (resolveInfo.unConditionalService == null) {
                if (DBG) {
                    Log.d(TAG, "conflict aid it need to be host");
                }
                if (this.mRoutingManager.getDefaultRoute() != 0) {
                    aidList.add(aid);
                    cnt += (aid.length() / PMT_AID_BLOCKING_ALL_PAYMENT) + 4;
                }
            } else {
                int route = getVendorSpecificSeId(resolveInfo.unConditionalService.isOnHost(), resolveInfo.unConditionalService.getSEInfo().getSeId());
                if (DBG) {
                    Log.d(TAG, "non-conflict aid it need to route " + resolveInfo.unConditionalService);
                    Log.d(TAG, "onHost " + resolveInfo.unConditionalService.isOnHost());
                    Log.d(TAG, "route : " + route);
                }
                if (this.mRoutingManager.getDefaultRoute() != route) {
                    if (DBG) {
                        Log.d(TAG, "add");
                    }
                    aidList.add(aid);
                    cnt += (aid.length() / PMT_AID_BLOCKING_ALL_PAYMENT) + 4;
                }
            }
            resolveInfo.unConditionalService = backUp;
        }
        if (DBG) {
            Log.d(TAG, "aidList - " + aidList);
        }
        if (cnt > max) {
            if (DBG) {
                Log.d(TAG, "change worst case ");
            }
            this.mAidCheckBuffer.clear();
            this.mAidCheckBuffer.addAll(aidList);
        }
        return cnt;
    }

    int getVendorSpecificSeId(boolean onHost, int seid) {
        if (onHost) {
            return PMT_AID_BLOCKING_DISABLED;
        }
        if (seid == PMT_AID_BLOCKING_DEFAULT_ONLY) {
            return NfcService.SECURE_ELEMENT_ESE_ID;
        }
        return NfcService.SECURE_ELEMENT_UICC_ID;
    }

    public void defaultPaymentChangedFromSetting(boolean isSetting) {
        ComponentName defaultService = null;
        Log.d(TAG, "defaultPaymentChangedFromSetting");
        String name = Secure.getStringForUser(this.mContext.getContentResolver(), "nfc_payment_default_component", ActivityManager.getCurrentUser());
        if (isSetting) {
            if (name != null) {
                defaultService = ComponentName.unflattenFromString(name);
            }
        } else if (this.mNextTapComponent == null) {
            if (name != null) {
                defaultService = ComponentName.unflattenFromString(name);
            }
            if (DBG) {
                Log.d(TAG, "recovery from temporarily setting ");
            }
        } else {
            defaultService = this.mNextTapComponent;
            if (DBG) {
                Log.d(TAG, "temporarily make " + this.mNextTapComponent + " to default");
            }
        }
        boolean sendToApp = DBG;
        boolean sendToWalletManager = DBG;
        if (defaultService != null) {
            if (((Integer) this.mRoutingTypes.get(defaultService)).intValue() != -1) {
                if (((Integer) this.mRoutingTypes.get(defaultService)).intValue() == PMT_AID_BLOCKING_DEFAULT_ONLY) {
                    if (DBG) {
                        Log.d(TAG, "offHost(eSE) " + defaultService.getPackageName());
                    }
                    sendToApp = true;
                    if (isSetting) {
                        sendToWalletManager = true;
                    }
                } else if (DBG) {
                    Log.d(TAG, "offHost(UICC) " + defaultService.getPackageName());
                }
            } else if (DBG) {
                Log.d(TAG, "onHost " + defaultService.getPackageName());
            }
        }
        if (sendToApp) {
            sendSettingBroadcast(ACTION_CHANGE_DEFAULT_EXT, defaultService.getPackageName());
        }
        if (sendToWalletManager) {
            sendSettingBroadcast(ACTION_CHANGE_DEFAULT_EXT, "com.sec.android.wallet");
        }
    }

    public void sendSettingBroadcast(String action, String packageName) {
        Intent intent = new Intent(action);
        intent.setPackage(packageName);
        this.mContext.sendBroadcast(intent);
        Log.d(TAG, "send " + action + " event to " + packageName + "because default app is changed");
    }

    void showWarningDialog() {
        Intent intent = new Intent(this.mContext, RoutingTableAlert.class);
        intent.addFlags(268435456);
        this.mContext.startActivityAsUser(intent, UserHandle.CURRENT);
        if (DBG) {
            Log.d(TAG, " we have to make dialog");
        }
    }

    void showDefaultRemovedDialog() {
        Intent intent = new Intent(this.mContext, DefaultRemovedActivity.class);
        intent.addFlags(268435456);
        this.mContext.startActivityAsUser(intent, UserHandle.CURRENT);
    }

    void onPaymentDefaultRemoved(int userId, List<ApduServiceInfo> services) {
        int numPaymentServices = PMT_AID_BLOCKING_DISABLED;
        ComponentName lastFoundPaymentService = null;
        for (ApduServiceInfo service : services) {
            if (service.hasCategory("payment")) {
                numPaymentServices += PMT_AID_BLOCKING_DEFAULT_ONLY;
                lastFoundPaymentService = service.getComponent();
            }
        }
        if (DBG) {
            Log.d(TAG, "Number of payment services is " + Integer.toString(numPaymentServices));
        }
        if (numPaymentServices == 0) {
            if (DBG) {
                Log.d(TAG, "Default removed, no services left.");
            }
            setDefaultServiceForCategory(userId, null, "payment");
        } else if (numPaymentServices == PMT_AID_BLOCKING_DEFAULT_ONLY) {
            if (DBG) {
                Log.d(TAG, "Default removed, making remaining service default.");
            }
            setDefaultServiceForCategory(userId, lastFoundPaymentService, "payment");
        } else if (numPaymentServices > PMT_AID_BLOCKING_DEFAULT_ONLY) {
            if (DBG) {
                Log.d(TAG, "Default removed, asking user to pick.");
            }
            setDefaultServiceForCategory(userId, null, "payment");
            showDefaultRemovedDialog();
        }
    }

    void setDefaultIfNeededLocked(int userId, List<ApduServiceInfo> services) {
        int numPaymentServices = PMT_AID_BLOCKING_DISABLED;
        ComponentName lastFoundPaymentService = null;
        for (ApduServiceInfo service : services) {
            if (service.hasCategory("payment")) {
                numPaymentServices += PMT_AID_BLOCKING_DEFAULT_ONLY;
                lastFoundPaymentService = service.getComponent();
            }
        }
        if (numPaymentServices > PMT_AID_BLOCKING_DEFAULT_ONLY) {
            if (DBG) {
                Log.d(TAG, "No default set, more than one service left.");
            }
        } else if (numPaymentServices == PMT_AID_BLOCKING_DEFAULT_ONLY) {
            if (DBG) {
                Log.d(TAG, "No default set, making single service default.");
            }
            setDefaultServiceForCategory(userId, lastFoundPaymentService, "payment");
        } else if (DBG) {
            Log.d(TAG, "No default set, last payment service removed.");
        }
    }

    void checkDefaultsLocked(int userId, List<ApduServiceInfo> services) {
        ComponentName defaultPaymentService = getDefaultServiceForCategory(userId, "payment", DBG);
        if (DBG) {
            Log.d(TAG, "Current default: " + defaultPaymentService);
        }
        if (defaultPaymentService != null) {
            ApduServiceInfo serviceInfo = this.mServiceCache.getService(userId, defaultPaymentService);
            if (serviceInfo == null) {
                Log.e(TAG, "Default payment service unexpectedly removed.");
                onPaymentDefaultRemoved(userId, services);
            } else if (!serviceInfo.hasCategory("payment")) {
                if (DBG) {
                    Log.d(TAG, "Default payment service had payment category removed");
                }
                onPaymentDefaultRemoved(userId, services);
            } else if (DBG) {
                Log.d(TAG, "Default payment service still ok.");
            }
        } else {
            setDefaultIfNeededLocked(userId, services);
        }
        updateFromSettingsLocked(userId);
    }

    public void onServicesUpdated(int userId, List<ApduServiceInfo> services) {
        synchronized (this.mLock) {
            if (ActivityManager.getCurrentUser() == userId) {
                if ("NXP_PN544C3".equals("NXP_PN544C3")) {
                    this.mServicesCache = services;
                }
                checkDefaultsLocked(userId, services);
                if ("NXP_PN544C3".equals("NXP_PN544C3")) {
                    generateAidTreeLocked(userId, services);
                } else {
                    generateAidTreeLocked(services);
                }
                generateRoutingTypeLocked(services);
                generateAidCategoriesLocked(services);
                updateBlockedList();
                generateAidCacheLocked();
                if (this.mNeedCheckBuffer) {
                    this.mNeedCheckBuffer = DBG;
                    if (isAidTableFull(services)) {
                        showWarningDialog();
                    }
                }
                if ("NXP_PN544C3".equals("NXP_PN544C3")) {
                    updateRoutingLocked(userId);
                } else {
                    updateRoutingLocked();
                }
            } else if (DBG) {
                Log.d(TAG, "Ignoring update because it's not for the current user.");
            }
        }
    }

    public void onServiceInstalled(boolean onHost, int seid) {
        if (CscFeature.getInstance().getString("CscFeature_NFC_ConfigAdvancedSettings", "ENABLE").toUpperCase().equalsIgnoreCase("DISABLE")) {
            if (DBG) {
                Log.d(TAG, "do not support advanced setting menu");
            }
            this.mNeedCheckBuffer = DBG;
            return;
        }
        this.mNeedCheckBuffer = getVendorSpecificSeId(onHost, seid) != this.mRoutingManager.getDefaultRoute() ? true : this.mNeedCheckBuffer;
    }

    public boolean isNextTapOnHost() {
        if (this.mNextTapComponent == null) {
            return true;
        }
        return ((Integer) this.mRoutingTypes.get(this.mNextTapComponent)).intValue() == -1 ? true : DBG;
    }

    public void onAidFilterUpdated() {
        if ("NXP_PN544C3".equals("NXP_PN544C3")) {
            int userID = ActivityManager.getCurrentUser();
            generateAidTreeLocked(userID, this.mServicesCache);
            generateAidCategoriesLocked(this.mServicesCache);
            generateAidCacheLocked();
            updateRoutingLocked(userID);
        }
    }

    public void invalidateCache(int currentUser) {
        this.mServiceCache.invalidateCache(currentUser);
    }

    public void onNfcDisabled() {
        synchronized (this.mLock) {
            this.mNfcEnabled = DBG;
        }
        this.mServiceCache.onNfcDisabled();
        this.mRoutingManager.onNfccRoutingTableCleared();
    }

    public void onNfcEnabled() {
        synchronized (this.mLock) {
            this.mNfcEnabled = true;
            updateFromSettingsLocked(ActivityManager.getCurrentUser());
        }
        this.mServiceCache.onNfcEnabled();
    }

    public void onNfcRoutingChanged(int route) {
        if (DBG) {
            Log.d(TAG, "routingChanged");
            Log.d(TAG, "before : " + this.mRoutingManager.getDefaultRoute() + " -> after  : " + route);
        }
        if ("NXP_PN544C3".equals("NXP_PN544C3")) {
            Log.d(TAG, "onNfccRoutingTableCleared");
            this.mRoutingManager.onNfccRoutingTableCleared();
            Log.d(TAG, "setDefaultRoute +++");
            Log.d(TAG, "onNfcRoutingChanged +++");
            this.mServiceCache.onNfcRoutingChanged();
        } else if (route != this.mRoutingManager.getDefaultRoute()) {
            this.mRoutingManager.onNfccRoutingTableCleared();
            this.mRoutingManager.setDefaultRoute(route, true);
            this.mServiceCache.onNfcRoutingChanged();
        }
    }

    private void updateBlockedList() {
        int mode = this.mRoutingManager.getPaymentAidBlockingMode();
        if (DBG) {
            Log.d(TAG, "updateBlockedList: mode: " + mode);
        }
        this.mBlockList = null;
        if (mode != 0) {
            int userId = ActivityManager.getCurrentUser();
            ComponentName defaultPaymentService = this.mNextTapComponent;
            if (DBG) {
                Log.d(TAG, "updateBlockedList: next tap component is " + defaultPaymentService);
            }
            if (defaultPaymentService == null) {
                defaultPaymentService = getDefaultServiceForCategory(userId, "payment", DBG);
                if (DBG) {
                    Log.d(TAG, "updateBlockedList: current default: " + defaultPaymentService);
                }
            }
            if (defaultPaymentService != null) {
                ApduServiceInfo serviceInfo = this.mServiceCache.getService(userId, defaultPaymentService);
                boolean bIsDefaultRouteToDH = this.mRoutingManager.isDefaultRouteToDH();
                if (DBG) {
                    Log.d(TAG, "updateBlockedList: isDefaultRouteToDH:" + bIsDefaultRouteToDH + " isOnHost:" + (serviceInfo != null ? Boolean.valueOf(serviceInfo.isOnHost()) : "Not Payment"));
                }
                if (serviceInfo != null && serviceInfo.hasCategory("payment") && (!(serviceInfo.isOnHost() || bIsDefaultRouteToDH) || (serviceInfo.isOnHost() && bIsDefaultRouteToDH))) {
                    switch (mode) {
                        case PMT_AID_BLOCKING_DEFAULT_ONLY /*1*/:
                            this.mBlockList = serviceInfo.getAids();
                            break;
                        case PMT_AID_BLOCKING_ALL_PAYMENT /*2*/:
                            this.mBlockList = getAllPaymentAids();
                            break;
                    }
                }
            }
        }
        if (!DBG) {
            return;
        }
        if (this.mBlockList != null) {
            Iterator i$ = this.mBlockList.iterator();
            while (i$.hasNext()) {
                Log.d(TAG, "updateBlockedList: Blocked list contains " + ((String) i$.next()));
            }
            return;
        }
        Log.d(TAG, "updateBlockedList: Blocked list has been cleared");
    }

    private ArrayList<String> getAllPaymentAids() {
        ArrayList<String> aids = null;
        Set<String> paymentAids = (Set) this.mCategoryAids.get("payment");
        if (paymentAids != null) {
            aids = new ArrayList();
            for (String aid : paymentAids) {
                aids.add(aid);
            }
        }
        return aids;
    }

    private boolean isAidBlocked(String aid) {
        if (this.mBlockList == null) {
            return DBG;
        }
        Iterator i$ = this.mBlockList.iterator();
        while (i$.hasNext()) {
            String blockedAid = (String) i$.next();
            if (aid.startsWith(blockedAid, PMT_AID_BLOCKING_DISABLED)) {
                if (DBG) {
                    Log.d(TAG, "isAidBlocked: " + aid + " has prefix of a blocked AID (" + blockedAid + ")");
                }
                return true;
            }
        }
        return DBG;
    }

    String dumpEntry(Entry<String, AidResolveInfo> entry) {
        StringBuilder sb = new StringBuilder();
        sb.append("    \"" + ((String) entry.getKey()) + "\"\n");
        ApduServiceInfo defaultService = ((AidResolveInfo) entry.getValue()).defaultService;
        ComponentName defaultComponent = defaultService != null ? defaultService.getComponent() : null;
        for (ApduServiceInfo service : ((AidResolveInfo) entry.getValue()).services) {
            sb.append("        ");
            if (service.getComponent().equals(defaultComponent)) {
                sb.append("*DEFAULT* ");
            }
            sb.append(service.getComponent() + " (Description: " + service.getDescription() + ")\n");
        }
        return sb.toString();
    }

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        this.mServiceCache.dump(fd, pw, args);
        pw.println("AID cache entries: ");
        for (Entry<String, AidResolveInfo> entry : this.mAidCache.entrySet()) {
            pw.println(dumpEntry(entry));
        }
        pw.println("Category defaults: ");
        for (Entry<String, ComponentName> entry2 : this.mCategoryDefaults.entrySet()) {
            pw.println("    " + ((String) entry2.getKey()) + "->" + entry2.getValue());
        }
        pw.println("");
    }
}
