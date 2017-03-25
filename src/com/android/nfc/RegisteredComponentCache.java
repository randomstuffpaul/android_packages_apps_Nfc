package com.android.nfc;

import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.os.UserHandle;
import android.util.Log;
import com.android.nfc.handover.HandoverServer;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicReference;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

public class RegisteredComponentCache {
    static final boolean DBG;
    private static final String TAG = "RegisteredComponentCache";
    final String mAction;
    private ArrayList<ComponentInfo> mComponents;
    final Context mContext;
    final String mMetaDataName;
    final AtomicReference<BroadcastReceiver> mReceiver;

    /* renamed from: com.android.nfc.RegisteredComponentCache.1 */
    class C00281 extends BroadcastReceiver {
        C00281() {
        }

        public void onReceive(Context context1, Intent intent) {
            RegisteredComponentCache.this.generateComponentsList();
        }
    }

    public static class ComponentInfo {
        public final ResolveInfo resolveInfo;
        public final String[] techs;

        ComponentInfo(ResolveInfo resolveInfo, String[] techs) {
            this.resolveInfo = resolveInfo;
            this.techs = techs;
        }

        public String toString() {
            StringBuilder out = new StringBuilder("ComponentInfo: ");
            out.append(this.resolveInfo);
            out.append(", techs: ");
            for (String tech : this.techs) {
                out.append(tech);
                out.append(", ");
            }
            return out.toString();
        }
    }

    static {
        DBG = NfcService.DBG;
    }

    public RegisteredComponentCache(Context context, String action, String metaDataName) {
        this.mContext = context;
        this.mAction = action;
        this.mMetaDataName = metaDataName;
        generateComponentsList();
        BroadcastReceiver receiver = new C00281();
        this.mReceiver = new AtomicReference(receiver);
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction("android.intent.action.PACKAGE_ADDED");
        intentFilter.addAction("android.intent.action.PACKAGE_CHANGED");
        intentFilter.addAction("android.intent.action.PACKAGE_REMOVED");
        intentFilter.addDataScheme("package");
        this.mContext.registerReceiverAsUser(receiver, UserHandle.ALL, intentFilter, null, null);
        IntentFilter sdFilter = new IntentFilter();
        sdFilter.addAction("android.intent.action.EXTERNAL_APPLICATIONS_AVAILABLE");
        sdFilter.addAction("android.intent.action.EXTERNAL_APPLICATIONS_UNAVAILABLE");
        this.mContext.registerReceiverAsUser(receiver, UserHandle.ALL, sdFilter, null, null);
        IntentFilter userFilter = new IntentFilter();
        userFilter.addAction("android.intent.action.USER_SWITCHED");
        this.mContext.registerReceiverAsUser(receiver, UserHandle.ALL, userFilter, null, null);
    }

    public ArrayList<ComponentInfo> getComponents() {
        ArrayList<ComponentInfo> arrayList;
        synchronized (this) {
            arrayList = this.mComponents;
        }
        return arrayList;
    }

    public void close() {
        BroadcastReceiver receiver = (BroadcastReceiver) this.mReceiver.getAndSet(null);
        if (receiver != null) {
            this.mContext.unregisterReceiver(receiver);
        }
    }

    protected void finalize() throws Throwable {
        if (this.mReceiver.get() != null) {
            Log.e(TAG, "RegisteredServicesCache finalized without being closed");
        }
        close();
        super.finalize();
    }

    void dump(ArrayList<ComponentInfo> components) {
        Iterator i$ = components.iterator();
        while (i$.hasNext()) {
            Log.i(TAG, ((ComponentInfo) i$.next()).toString());
        }
    }

    void generateComponentsList() {
        try {
            PackageManager pm = this.mContext.createPackageContextAsUser("android", 0, new UserHandle(ActivityManager.getCurrentUser())).getPackageManager();
            ArrayList<ComponentInfo> components = new ArrayList();
            for (ResolveInfo resolveInfo : pm.queryIntentActivitiesAsUser(new Intent(this.mAction), HandoverServer.MIU, ActivityManager.getCurrentUser())) {
                try {
                    parseComponentInfo(pm, resolveInfo, components);
                } catch (XmlPullParserException e) {
                    Log.w(TAG, "Unable to load component info " + resolveInfo.toString(), e);
                } catch (IOException e2) {
                    Log.w(TAG, "Unable to load component info " + resolveInfo.toString(), e2);
                }
            }
            if (DBG) {
                dump(components);
            }
            synchronized (this) {
                this.mComponents = components;
            }
        } catch (NameNotFoundException e3) {
            Log.e(TAG, "Could not create user package context");
        }
    }

    void parseComponentInfo(PackageManager pm, ResolveInfo info, ArrayList<ComponentInfo> components) throws XmlPullParserException, IOException {
        ActivityInfo ai = info.activityInfo;
        XmlResourceParser parser = null;
        try {
            parser = ai.loadXmlMetaData(pm, this.mMetaDataName);
            if (parser == null) {
                throw new XmlPullParserException("No " + this.mMetaDataName + " meta-data");
            }
            parseTechLists(pm.getResourcesForApplication(ai.applicationInfo), ai.packageName, parser, info, components);
            if (parser != null) {
                parser.close();
            }
        } catch (NameNotFoundException e) {
            throw new XmlPullParserException("Unable to load resources for " + ai.packageName);
        } catch (Throwable th) {
            if (parser != null) {
                parser.close();
            }
        }
    }

    void parseTechLists(Resources res, String packageName, XmlPullParser parser, ResolveInfo resolveInfo, ArrayList<ComponentInfo> components) throws XmlPullParserException, IOException {
        int eventType = parser.getEventType();
        while (eventType != 2) {
            eventType = parser.next();
        }
        ArrayList<String> items = new ArrayList();
        eventType = parser.next();
        do {
            String tagName = parser.getName();
            if (eventType == 2 && "tech".equals(tagName)) {
                items.add(parser.nextText());
            } else if (eventType == 3 && "tech-list".equals(tagName)) {
                int size = items.size();
                if (size > 0) {
                    String[] techs = (String[]) items.toArray(new String[size]);
                    items.clear();
                    components.add(new ComponentInfo(resolveInfo, techs));
                }
            }
            eventType = parser.next();
        } while (eventType != 1);
    }
}
