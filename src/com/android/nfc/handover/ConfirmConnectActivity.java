package com.android.nfc.handover;

import android.app.Activity;
import android.app.AlertDialog.Builder;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.os.Bundle;
import android.provider.Settings.System;
import com.android.nfc.C0027R;

public class ConfirmConnectActivity extends Activity {
    BluetoothDevice mDevice;
    final BroadcastReceiver mReceiver;
    Context mcontext;

    /* renamed from: com.android.nfc.handover.ConfirmConnectActivity.1 */
    class C00381 implements OnClickListener {
        C00381() {
        }

        public void onClick(DialogInterface dialog, int id) {
            Intent denyIntent = new Intent("com.android.nfc.handover.action.DENY_CONNECT");
            denyIntent.putExtra("android.bluetooth.device.extra.DEVICE", ConfirmConnectActivity.this.mDevice);
            ConfirmConnectActivity.this.sendBroadcast(denyIntent);
            ConfirmConnectActivity.this.finish();
        }
    }

    /* renamed from: com.android.nfc.handover.ConfirmConnectActivity.2 */
    class C00392 implements OnClickListener {
        C00392() {
        }

        public void onClick(DialogInterface dialog, int id) {
            Intent allowIntent = new Intent("com.android.nfc.handover.action.ALLOW_CONNECT");
            allowIntent.putExtra("android.bluetooth.device.extra.DEVICE", ConfirmConnectActivity.this.mDevice);
            ConfirmConnectActivity.this.sendBroadcast(allowIntent);
            ConfirmConnectActivity.this.finish();
        }
    }

    /* renamed from: com.android.nfc.handover.ConfirmConnectActivity.3 */
    class C00403 extends BroadcastReceiver {
        C00403() {
        }

        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals("android.intent.action.AIRPLANE_MODE") && ConfirmConnectActivity.this.isAirplaneModeOn()) {
                ConfirmConnectActivity.this.finish();
            }
        }
    }

    public ConfirmConnectActivity() {
        this.mReceiver = new C00403();
    }

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.mcontext = getApplicationContext();
        this.mcontext.registerReceiver(this.mReceiver, new IntentFilter("android.intent.action.AIRPLANE_MODE"));
        Builder builder = new Builder(this, 2);
        this.mDevice = (BluetoothDevice) getIntent().getParcelableExtra("android.bluetooth.device.extra.DEVICE");
        if (this.mDevice == null) {
            finish();
        }
        Resources res = getResources();
        String deviceName = this.mDevice.getName() != null ? this.mDevice.getName() : "";
        builder.setMessage(String.format(res.getString(C0027R.string.confirm_pairing), new Object[]{deviceName})).setCancelable(false).setPositiveButton(res.getString(C0027R.string.pair_yes), new C00392()).setNegativeButton(res.getString(C0027R.string.pair_no), new C00381());
        builder.create().show();
    }

    boolean isAirplaneModeOn() {
        return System.getInt(this.mcontext.getContentResolver(), "airplane_mode_on", 0) == 1;
    }
}
