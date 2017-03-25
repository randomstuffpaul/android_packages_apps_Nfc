package com.android.nfc.handover;

import android.bluetooth.BluetoothDevice;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.Parcelable.Creator;

public class PendingHandoverTransfer implements Parcelable {
    public static final Creator<PendingHandoverTransfer> CREATOR;
    public int id;
    public boolean incoming;
    public boolean remoteActivating;
    public BluetoothDevice remoteDevice;
    public Uri[] uris;

    /* renamed from: com.android.nfc.handover.PendingHandoverTransfer.1 */
    static class C00451 implements Creator<PendingHandoverTransfer> {
        C00451() {
        }

        public PendingHandoverTransfer createFromParcel(Parcel in) {
            boolean incoming;
            boolean remoteActivating;
            int id = in.readInt();
            if (in.readInt() == 1) {
                incoming = true;
            } else {
                incoming = false;
            }
            BluetoothDevice remoteDevice = (BluetoothDevice) in.readParcelable(getClass().getClassLoader());
            if (in.readInt() == 1) {
                remoteActivating = true;
            } else {
                remoteActivating = false;
            }
            int numUris = in.readInt();
            Uri[] uris = null;
            if (numUris > 0) {
                uris = new Uri[numUris];
                in.readTypedArray(uris, Uri.CREATOR);
            }
            return new PendingHandoverTransfer(id, incoming, remoteDevice, remoteActivating, uris);
        }

        public PendingHandoverTransfer[] newArray(int size) {
            return new PendingHandoverTransfer[size];
        }
    }

    PendingHandoverTransfer(int id, boolean incoming, BluetoothDevice remoteDevice, boolean remoteActivating, Uri[] uris) {
        this.id = id;
        this.incoming = incoming;
        this.remoteDevice = remoteDevice;
        this.remoteActivating = remoteActivating;
        this.uris = uris;
    }

    static {
        CREATOR = new C00451();
    }

    public int describeContents() {
        return 0;
    }

    public void writeToParcel(Parcel dest, int flags) {
        int length;
        int i = 1;
        dest.writeInt(this.id);
        dest.writeInt(this.incoming ? 1 : 0);
        dest.writeParcelable(this.remoteDevice, 0);
        if (!this.remoteActivating) {
            i = 0;
        }
        dest.writeInt(i);
        if (this.uris != null) {
            length = this.uris.length;
        } else {
            length = 0;
        }
        dest.writeInt(length);
        if (this.uris != null && this.uris.length > 0) {
            dest.writeTypedArray(this.uris, 0);
        }
    }
}
