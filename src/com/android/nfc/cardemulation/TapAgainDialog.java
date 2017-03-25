package com.android.nfc.cardemulation;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.nfc.NfcAdapter;
import android.nfc.cardemulation.ApduServiceInfo;
import android.nfc.cardemulation.CardEmulation;
import android.os.Bundle;
import android.widget.TextView;
import com.android.internal.app.AlertActivity;
import com.android.internal.app.AlertController.AlertParams;
import com.android.nfc.C0027R;

public class TapAgainDialog extends AlertActivity implements OnClickListener {
    public static final String ACTION_CLOSE = "com.android.nfc.cardmeulation.close_tap_dialog";
    public static final String EXTRA_APDU_SERVICE = "apdu_service";
    public static final String EXTRA_CATEGORY = "category";
    private CardEmulation mCardEmuManager;
    private boolean mClosedOnRequest;
    final BroadcastReceiver mReceiver;

    /* renamed from: com.android.nfc.cardemulation.TapAgainDialog.1 */
    class C00331 extends BroadcastReceiver {
        C00331() {
        }

        public void onReceive(Context context, Intent intent) {
            TapAgainDialog.this.mClosedOnRequest = true;
            TapAgainDialog.this.finish();
        }
    }

    public TapAgainDialog() {
        this.mClosedOnRequest = false;
        this.mReceiver = new C00331();
    }

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTheme(16974663);
        this.mCardEmuManager = CardEmulation.getInstance(NfcAdapter.getDefaultAdapter(this));
        Intent intent = getIntent();
        String category = intent.getStringExtra(EXTRA_CATEGORY);
        ApduServiceInfo serviceInfo = (ApduServiceInfo) intent.getParcelableExtra(EXTRA_APDU_SERVICE);
        IntentFilter filter = new IntentFilter(ACTION_CLOSE);
        filter.addAction("android.intent.action.SCREEN_OFF");
        registerReceiver(this.mReceiver, filter);
        AlertParams ap = this.mAlertParams;
        ap.mTitle = "";
        ap.mView = getLayoutInflater().inflate(C0027R.layout.tapagain, null);
        PackageManager pm = getPackageManager();
        TextView tv = (TextView) ap.mView.findViewById(C0027R.id.textview);
        String description = serviceInfo.getDescription();
        if (description == null) {
            CharSequence label = serviceInfo.loadLabel(pm);
            if (label == null) {
                finish();
            } else {
                description = label.toString();
            }
        }
        if ("payment".equals(category)) {
            tv.setText(String.format(getString(C0027R.string.tap_again_to_pay), new Object[]{description}));
        } else {
            tv.setText(String.format(getString(C0027R.string.tap_again_to_complete), new Object[]{description}));
        }
        ap.mNegativeButtonText = getString(17039360);
        setupAlert();
        getWindow().addFlags(4194304);
    }

    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(this.mReceiver);
    }

    protected void onStop() {
        super.onStop();
        if (!this.mClosedOnRequest) {
            this.mCardEmuManager.setDefaultForNextTap(null);
        }
    }

    public void onClick(DialogInterface dialog, int which) {
        finish();
    }
}
