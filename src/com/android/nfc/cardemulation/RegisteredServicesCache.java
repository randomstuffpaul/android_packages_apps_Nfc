package com.android.nfc.cardemulation;

import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.nfc.cardemulation.ApduServiceInfo;
import android.os.UserHandle;
import android.util.Log;
import android.util.SparseArray;
import com.android.nfc.handover.HandoverServer;
import com.google.android.collect.Maps;
import com.gsma.services.nfc.NfcController;
import java.io.FileDescriptor;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicReference;
import org.xmlpull.v1.XmlPullParserException;

public class RegisteredServicesCache {
    static final boolean DBG;
    static final String TAG = "RegisteredServicesCache";
    final Callback mCallback;
    final Context mContext;
    final Object mLock;
    final AtomicReference<BroadcastReceiver> mReceiver;
    final SparseArray<UserServices> mUserServices;
    String pkg_name;

    public interface Callback {
        void onServiceInstalled(boolean z, int i);

        void onServicesUpdated(int i, List<ApduServiceInfo> list);
    }

    /* renamed from: com.android.nfc.cardemulation.RegisteredServicesCache.1 */
    class C00321 extends BroadcastReceiver {
        C00321() {
        }

        public void onReceive(Context context, Intent intent) {
            boolean replaced = RegisteredServicesCache.DBG;
            int uid = intent.getIntExtra("android.intent.extra.UID", -1);
            String action = intent.getAction();
            if (RegisteredServicesCache.DBG) {
                Log.d(RegisteredServicesCache.TAG, "Intent action: " + action);
            }
            if (uid != -1) {
                if (intent.getBooleanExtra("android.intent.extra.REPLACING", RegisteredServicesCache.DBG) && ("android.intent.action.PACKAGE_ADDED".equals(action) || "android.intent.action.PACKAGE_REMOVED".equals(action))) {
                    replaced = true;
                }
                if (replaced) {
                    if (RegisteredServicesCache.DBG) {
                        Log.d(RegisteredServicesCache.TAG, "Ignoring package intent due to package being replaced.");
                    }
                } else if (ActivityManager.getCurrentUser() == UserHandle.getUserId(uid)) {
                    RegisteredServicesCache.this.invalidateCache(UserHandle.getUserId(uid), intent);
                }
            }
        }
    }

    private static class UserServices {
        public final HashMap<ComponentName, ApduServiceInfo> services;

        private UserServices() {
            this.services = Maps.newHashMap();
        }
    }

    static {
        DBG = AidRoutingManager.DBG;
    }

    private UserServices findOrCreateUserLocked(int userId) {
        UserServices services = (UserServices) this.mUserServices.get(userId);
        if (services != null) {
            return services;
        }
        services = new UserServices();
        this.mUserServices.put(userId, services);
        return services;
    }

    public RegisteredServicesCache(Context context, Callback callback) {
        this.mLock = new Object();
        this.mUserServices = new SparseArray();
        this.mContext = context;
        this.mCallback = callback;
        this.mReceiver = new AtomicReference(new C00321());
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction("android.intent.action.PACKAGE_ADDED");
        intentFilter.addAction("android.intent.action.PACKAGE_CHANGED");
        intentFilter.addAction("android.intent.action.PACKAGE_REMOVED");
        intentFilter.addAction("android.intent.action.PACKAGE_REPLACED");
        intentFilter.addAction("android.intent.action.PACKAGE_FIRST_LAUNCH");
        intentFilter.addAction("android.intent.action.PACKAGE_RESTARTED");
        intentFilter.addDataScheme("package");
        this.mContext.registerReceiverAsUser((BroadcastReceiver) this.mReceiver.get(), UserHandle.ALL, intentFilter, null, null);
        IntentFilter sdFilter = new IntentFilter();
        sdFilter.addAction("android.intent.action.EXTERNAL_APPLICATIONS_AVAILABLE");
        sdFilter.addAction("android.intent.action.EXTERNAL_APPLICATIONS_UNAVAILABLE");
        this.mContext.registerReceiverAsUser((BroadcastReceiver) this.mReceiver.get(), UserHandle.ALL, sdFilter, null, null);
    }

    void dump(ArrayList<ApduServiceInfo> services) {
        Iterator i$ = services.iterator();
        while (i$.hasNext()) {
            ApduServiceInfo service = (ApduServiceInfo) i$.next();
            if (DBG) {
                Log.d(TAG, service.toString());
            }
        }
    }

    boolean containsServiceLocked(ArrayList<ApduServiceInfo> services, ComponentName serviceName) {
        Iterator i$ = services.iterator();
        while (i$.hasNext()) {
            if (((ApduServiceInfo) i$.next()).getComponent().equals(serviceName)) {
                return true;
            }
        }
        return DBG;
    }

    public boolean hasService(int userId, ComponentName service) {
        return getService(userId, service) != null ? true : DBG;
    }

    public ApduServiceInfo getService(int userId, ComponentName service) {
        ApduServiceInfo apduServiceInfo;
        synchronized (this.mLock) {
            apduServiceInfo = (ApduServiceInfo) findOrCreateUserLocked(userId).services.get(service);
        }
        return apduServiceInfo;
    }

    public List<ApduServiceInfo> getServices(int userId) {
        ArrayList<ApduServiceInfo> services = new ArrayList();
        synchronized (this.mLock) {
            services.addAll(findOrCreateUserLocked(userId).services.values());
        }
        return services;
    }

    public List<ApduServiceInfo> getServicesForCategory(int userId, String category) {
        ArrayList<ApduServiceInfo> services = new ArrayList();
        synchronized (this.mLock) {
            for (ApduServiceInfo service : findOrCreateUserLocked(userId).services.values()) {
                if (service.hasCategory(category)) {
                    services.add(service);
                }
            }
        }
        return services;
    }

    public void onNfcDisabled() {
    }

    public void onNfcEnabled() {
        invalidateCache(ActivityManager.getCurrentUser());
    }

    public void onNfcRoutingChanged() {
        invalidateCache(ActivityManager.getCurrentUser());
    }

    public void invalidateCache(int userId, Intent intent) {
        if ("android.intent.action.PACKAGE_ADDED".equals(intent.getAction())) {
            this.pkg_name = intent.getData().getSchemeSpecificPart();
        }
        invalidateCache(userId);
        this.pkg_name = null;
    }

    public void invalidateCache(int userId) {
        try {
            Iterator i$;
            ApduServiceInfo service;
            PackageManager pm = this.mContext.createPackageContextAsUser("android", 0, new UserHandle(userId)).getPackageManager();
            ArrayList<ApduServiceInfo> validServices = new ArrayList();
            List<ResolveInfo> resolvedServices = pm.queryIntentServicesAsUser(new Intent("android.nfc.cardemulation.action.HOST_APDU_SERVICE"), HandoverServer.MIU, userId);
            List<ResolveInfo> resolvedOffHostServices = pm.queryIntentServicesAsUser(new Intent("android.nfc.cardemulation.action.OFF_HOST_APDU_SERVICE"), HandoverServer.MIU, userId);
            resolvedServices.addAll(resolvedOffHostServices);
            for (ResolveInfo resolvedService : resolvedServices) {
                try {
                    boolean onHost = !resolvedOffHostServices.contains(resolvedService) ? true : DBG;
                    ServiceInfo si = resolvedService.serviceInfo;
                    ComponentName componentName = new ComponentName(si.packageName, si.name);
                    if (pm.checkPermission(NfcController.NFC_CONTROLLER_PERMISSION, si.packageName) != 0) {
                        Log.e(TAG, "Skipping APDU service " + componentName + ": it does not require the permission " + NfcController.NFC_CONTROLLER_PERMISSION);
                    } else {
                        if ("android.permission.BIND_NFC_SERVICE".equals(si.permission)) {
                            service = new ApduServiceInfo(pm, resolvedService, onHost);
                            if (service != null) {
                                validServices.add(service);
                                if (si.packageName.equals(this.pkg_name)) {
                                    Log.d(TAG, "we check the aid full");
                                    this.mCallback.onServiceInstalled(onHost, service.getSEInfo().getSeId());
                                }
                            }
                        } else {
                            Log.e(TAG, "Skipping APDU service " + componentName + ": it does not require the permission " + "android.permission.BIND_NFC_SERVICE");
                        }
                    }
                } catch (XmlPullParserException e) {
                    Log.w(TAG, "Unable to load component info " + resolvedService.toString(), e);
                } catch (IOException e2) {
                    Log.w(TAG, "Unable to load component info " + resolvedService.toString(), e2);
                }
            }
            synchronized (this.mLock) {
                UserServices userServices = findOrCreateUserLocked(userId);
                Iterator<Entry<ComponentName, ApduServiceInfo>> it = userServices.services.entrySet().iterator();
                while (it.hasNext()) {
                    Entry<ComponentName, ApduServiceInfo> entry = (Entry) it.next();
                    if (!containsServiceLocked(validServices, (ComponentName) entry.getKey())) {
                        Log.d(TAG, "Service removed: " + entry.getKey());
                        it.remove();
                    }
                }
                i$ = validServices.iterator();
                while (i$.hasNext()) {
                    service = (ApduServiceInfo) i$.next();
                    if (DBG) {
                        Log.d(TAG, "Adding service: " + service.getComponent() + " AIDs: " + service.getAids());
                    }
                    userServices.services.put(service.getComponent(), service);
                }
            }
            this.mCallback.onServicesUpdated(userId, Collections.unmodifiableList(validServices));
            dump(validServices);
        } catch (NameNotFoundException e3) {
            Log.e(TAG, "Could not create user package context");
        }
    }

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("Registered HCE services for current user: ");
        for (ApduServiceInfo service : findOrCreateUserLocked(ActivityManager.getCurrentUser()).services.values()) {
            pw.println("    " + service.getComponent() + " (Description: " + service.getDescription() + ")");
        }
        pw.println("");
    }
}
