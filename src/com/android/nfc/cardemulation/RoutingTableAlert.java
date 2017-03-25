package com.android.nfc.cardemulation;

import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.os.Bundle;
import com.android.internal.app.AlertActivity;
import com.android.internal.app.AlertController.AlertParams;
import com.android.nfc.C0027R;
import com.sec.android.app.CscFeature;

public class RoutingTableAlert extends AlertActivity implements OnClickListener {
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AlertParams ap = this.mAlertParams;
        ap.mTitle = getString(C0027R.string.route_table_title);
        ap.mMessage = getString(C0027R.string.route_table_alert_explain);
        ap.mNegativeButtonText = getString(C0027R.string.route_table_ignore);
        ap.mPositiveButtonText = getString(C0027R.string.route_table_advanced_settings);
        if ("Vzw".equalsIgnoreCase(CscFeature.getInstance().getString("CscFeature_NFC_StatusBarIconType"))) {
            ap.mTitle = getString(C0027R.string.route_table_title_vzw);
            ap.mMessage = getString(C0027R.string.route_table_alert_explain_vzw);
            ap.mNegativeButtonText = getString(C0027R.string.btn_ok);
            ap.mPositiveButtonText = getString(C0027R.string.route_table_advanced_settings_vzw);
        }
        ap.mPositiveButtonListener = this;
        setupAlert();
    }

    public void onClick(DialogInterface dialog, int which) {
        startActivity(new Intent("android.settings.SEC_NFC_ADVANCED_SETTING"));
    }
}
