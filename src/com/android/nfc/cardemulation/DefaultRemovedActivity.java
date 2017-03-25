package com.android.nfc.cardemulation;

import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.os.Bundle;
import com.android.internal.app.AlertActivity;
import com.android.internal.app.AlertController.AlertParams;
import com.android.nfc.C0027R;

public class DefaultRemovedActivity extends AlertActivity implements OnClickListener {
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(16974663);
        super.onCreate(savedInstanceState);
        AlertParams ap = this.mAlertParams;
        ap.mTitle = getString(C0027R.string.default_pay_app_removed_title);
        ap.mMessage = getString(C0027R.string.default_pay_app_removed);
        ap.mNegativeButtonText = getString(17039369);
        ap.mPositiveButtonText = getString(17039379);
        ap.mPositiveButtonListener = this;
        setupAlert();
    }

    public void onClick(DialogInterface dialog, int which) {
        startActivity(new Intent("android.settings.NFC_PAYMENT_SETTINGS"));
    }
}
