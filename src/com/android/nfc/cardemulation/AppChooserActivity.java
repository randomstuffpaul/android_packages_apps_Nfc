package com.android.nfc.cardemulation;

import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.drawable.Drawable;
import android.nfc.NfcAdapter;
import android.nfc.cardemulation.ApduServiceInfo;
import android.nfc.cardemulation.CardEmulation;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import com.android.internal.app.AlertActivity;
import com.android.internal.app.AlertController.AlertParams;
import com.android.nfc.C0027R;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class AppChooserActivity extends AlertActivity implements OnItemClickListener {
    public static final String EXTRA_APDU_SERVICES = "services";
    public static final String EXTRA_CATEGORY = "category";
    public static final String EXTRA_FAILED_COMPONENT = "failed_component";
    static final String TAG = "AppChooserActivity";
    private CardEmulation mCardEmuManager;
    private String mCategory;
    private int mIconSize;
    private ListAdapter mListAdapter;
    private ListView mListView;
    final BroadcastReceiver mReceiver;

    /* renamed from: com.android.nfc.cardemulation.AppChooserActivity.1 */
    class C00291 extends BroadcastReceiver {
        C00291() {
        }

        public void onReceive(Context context, Intent intent) {
            AppChooserActivity.this.finish();
        }
    }

    final class DisplayAppInfo {
        Drawable displayBanner;
        Drawable displayIcon;
        CharSequence displayLabel;
        ApduServiceInfo serviceInfo;

        public DisplayAppInfo(ApduServiceInfo serviceInfo, CharSequence label, Drawable icon, Drawable banner) {
            this.serviceInfo = serviceInfo;
            this.displayIcon = icon;
            this.displayLabel = label;
            this.displayBanner = banner;
        }
    }

    final class ListAdapter extends BaseAdapter {
        private final LayoutInflater mInflater;
        private final boolean mIsPayment;
        private List<DisplayAppInfo> mList;

        public ListAdapter(Context context, ArrayList<ApduServiceInfo> services) {
            this.mInflater = (LayoutInflater) context.getSystemService("layout_inflater");
            PackageManager pm = AppChooserActivity.this.getPackageManager();
            this.mList = new ArrayList();
            this.mIsPayment = "payment".equals(AppChooserActivity.this.mCategory);
            Iterator i$ = services.iterator();
            while (i$.hasNext()) {
                ApduServiceInfo service = (ApduServiceInfo) i$.next();
                CharSequence label = service.getDescription();
                if (label == null) {
                    label = service.loadLabel(pm);
                }
                Drawable icon = service.loadIcon(pm);
                Drawable banner = null;
                if (this.mIsPayment) {
                    banner = service.loadBanner(pm);
                    if (banner == null) {
                        Log.e(AppChooserActivity.TAG, "Not showing " + label + " because no banner specified.");
                    }
                }
                this.mList.add(new DisplayAppInfo(service, label, icon, banner));
            }
        }

        public int getCount() {
            return this.mList.size();
        }

        public Object getItem(int position) {
            return this.mList.get(position);
        }

        public long getItemId(int position) {
            return (long) position;
        }

        public View getView(int position, View convertView, ViewGroup parent) {
            View view;
            if (convertView == null) {
                if (this.mIsPayment) {
                    view = this.mInflater.inflate(C0027R.layout.cardemu_payment_item, parent, false);
                } else {
                    view = this.mInflater.inflate(C0027R.layout.cardemu_item, parent, false);
                }
                view.setTag(new ViewHolder(view));
            } else {
                view = convertView;
            }
            ViewHolder holder = (ViewHolder) view.getTag();
            DisplayAppInfo appInfo = (DisplayAppInfo) this.mList.get(position);
            if (this.mIsPayment) {
                holder.banner.setImageDrawable(appInfo.displayBanner);
            } else {
                LayoutParams lp = holder.icon.getLayoutParams();
                int access$100 = AppChooserActivity.this.mIconSize;
                lp.height = access$100;
                lp.width = access$100;
                holder.icon.setImageDrawable(appInfo.displayIcon);
                holder.text.setText(appInfo.displayLabel);
            }
            return view;
        }
    }

    static class ViewHolder {
        public ImageView banner;
        public ImageView icon;
        public TextView text;

        public ViewHolder(View view) {
            this.text = (TextView) view.findViewById(C0027R.id.applabel);
            this.icon = (ImageView) view.findViewById(C0027R.id.appicon);
            this.banner = (ImageView) view.findViewById(C0027R.id.banner);
        }
    }

    public AppChooserActivity() {
        this.mReceiver = new C00291();
    }

    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(this.mReceiver);
    }

    protected void onCreate(Bundle savedInstanceState, String category, ArrayList<ApduServiceInfo> options, ComponentName failedComponent) {
        super.onCreate(savedInstanceState);
        setTheme(16974663);
        IntentFilter filter = new IntentFilter("android.intent.action.SCREEN_OFF");
        registerReceiver(this.mReceiver, filter);
        if ((options == null || options.size() == 0) && failedComponent == null) {
            Log.e(TAG, "No components passed in.");
            finish();
            return;
        }
        this.mCategory = category;
        boolean isPayment = "payment".equals(this.mCategory);
        this.mCardEmuManager = CardEmulation.getInstance(NfcAdapter.getDefaultAdapter(this));
        AlertParams ap = this.mAlertParams;
        this.mIconSize = ((ActivityManager) getSystemService("activity")).getLauncherLargeIconSize();
        PackageManager pm = getPackageManager();
        CharSequence applicationLabel = "unknown";
        if (failedComponent != null) {
            try {
                applicationLabel = pm.getApplicationInfo(failedComponent.getPackageName(), 0).loadLabel(pm);
            } catch (NameNotFoundException e) {
            }
        }
        if (options.size() != 0 || failedComponent == null) {
            this.mListAdapter = new ListAdapter(this, options);
            if (failedComponent != null) {
                ap.mTitle = String.format(getString(C0027R.string.could_not_use_app), new Object[]{applicationLabel});
                ap.mNegativeButtonText = getString(17039360);
            } else if ("payment".equals(category)) {
                ap.mTitle = getString(C0027R.string.pay_with);
            } else {
                ap.mTitle = getString(C0027R.string.complete_with);
            }
            ap.mView = getLayoutInflater().inflate(C0027R.layout.cardemu_resolver, null);
            this.mListView = (ListView) ap.mView.findViewById(C0027R.id.resolver_list);
            if (isPayment) {
                this.mListView.setDivider(getResources().getDrawable(17170445));
                this.mListView.setDividerHeight((int) (getResources().getDisplayMetrics().density * 16.0f));
            } else {
                this.mListView.setPadding(0, 0, 0, 0);
            }
            this.mListView.setAdapter(this.mListAdapter);
            this.mListView.setOnItemClickListener(this);
            setupAlert();
        } else {
            String formatString = getString(C0027R.string.transaction_failure);
            ap.mTitle = "";
            ap.mMessage = String.format(formatString, new Object[]{applicationLabel});
            ap.mPositiveButtonText = getString(17039370);
            setupAlert();
        }
        getWindow().addFlags(4194304);
    }

    protected void onCreate(Bundle savedInstanceState) {
        Intent intent = getIntent();
        onCreate(savedInstanceState, intent.getStringExtra(EXTRA_CATEGORY), intent.getParcelableArrayListExtra(EXTRA_APDU_SERVICES), (ComponentName) intent.getParcelableExtra(EXTRA_FAILED_COMPONENT));
    }

    public void onItemClick(AdapterView<?> adapterView, View view, int position, long id) {
        DisplayAppInfo info = (DisplayAppInfo) this.mListAdapter.getItem(position);
        this.mCardEmuManager.setDefaultForNextTap(info.serviceInfo.getComponent());
        Intent dialogIntent = new Intent(this, TapAgainDialog.class);
        dialogIntent.putExtra(EXTRA_CATEGORY, this.mCategory);
        dialogIntent.putExtra(TapAgainDialog.EXTRA_APDU_SERVICE, info.serviceInfo);
        startActivity(dialogIntent);
        finish();
    }
}
