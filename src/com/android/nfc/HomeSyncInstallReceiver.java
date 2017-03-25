package com.android.nfc;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.util.Log;

public class HomeSyncInstallReceiver extends BroadcastReceiver {
    static final boolean DBG;
    static final String HOMESYNC_ACTIVITY_NAME = "com.sec.android.homesync.RemoteLinkActivity";
    static final String HOMESYNC_PACKAGE_NAME = "com.sec.android.homesync";
    private static String TAG;

    static {
        TAG = "HomeSyncInstallReceiver";
        DBG = NfcService.DBG;
    }

    public void onReceive(Context context, Intent intent) {
        String package_name = "Nothing";
        try {
            package_name = intent.getData().getSchemeSpecificPart();
            SharedPreferences pref;
            String btaddr;
            if (package_name != null && HOMESYNC_PACKAGE_NAME.equals(package_name)) {
                Log.i(TAG, "Package Added : " + package_name);
                pref = context.getSharedPreferences("homesync", 0);
                Editor editor = pref.edit();
                btaddr = pref.getString("bt_addr", "Nothing");
                editor.remove("bt_addr");
                editor.commit();
                if (btaddr.length() > 15) {
                    Log.i(TAG, "send homesync_intent with btaddr : " + btaddr);
                    Intent homesync_intent = context.getPackageManager().getLaunchIntentForPackage(HOMESYNC_PACKAGE_NAME);
                    homesync_intent.putExtra("bt_addr", btaddr);
                    context.startActivity(homesync_intent);
                }
            } else if (package_name != null) {
                pref = context.getSharedPreferences("bt_addr_list", 0);
                Editor ed = pref.edit();
                btaddr = pref.getString(package_name, "");
                if (btaddr.length() > 15) {
                    Log.i(TAG, "send" + package_name + "_intent with bt addr : ");
                    Intent launch_intent = context.getPackageManager().getLaunchIntentForPackage(package_name);
                    launch_intent.putExtra("bt_addr", btaddr);
                    context.startActivity(launch_intent);
                    ed.remove(package_name);
                    ed.commit();
                    return;
                }
                Log.i(TAG, "This pkg do not need BT addr. Do nothing");
            }
        } catch (NullPointerException e) {
            e.printStackTrace();
        }
    }
}
