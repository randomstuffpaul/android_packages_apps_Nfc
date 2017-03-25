package com.android.nfc.cardemulation;

/* compiled from: AidRoutingCache */
class AidElement implements Comparable {
    private String mAid;
    private boolean mIsDefault;
    private int mPowerState;
    private int mRouteLocation;

    public AidElement(String aid, boolean isDefault, int route, int power) {
        this.mAid = aid;
        this.mIsDefault = isDefault;
        this.mRouteLocation = route;
        this.mPowerState = power;
    }

    public boolean isDefault() {
        return this.mIsDefault;
    }

    public String getAid() {
        return this.mAid;
    }

    public int getRouteLocation() {
        return this.mRouteLocation;
    }

    public int getPowerState() {
        return this.mPowerState;
    }

    public int compareTo(Object o) {
        AidElement elem = (AidElement) o;
        if (this.mIsDefault && !elem.isDefault()) {
            return -1;
        }
        if (this.mIsDefault || !elem.isDefault()) {
            return elem.getAid().length() - this.mAid.length();
        }
        return 1;
    }

    public String toString() {
        return "aid: " + this.mAid + ", location: " + this.mRouteLocation + ", power: " + this.mPowerState + ",isDefault: " + this.mIsDefault;
    }
}
