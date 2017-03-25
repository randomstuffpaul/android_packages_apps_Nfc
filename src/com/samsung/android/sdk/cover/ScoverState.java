package com.samsung.android.sdk.cover;

public class ScoverState {
    @Deprecated
    public static final int COLOR_BLACK = 1;
    public static final int COLOR_BLUSH_PINK = 3;
    public static final int COLOR_CARBON_METAL = 9;
    @Deprecated
    public static final int COLOR_CHARCOAL_GRAY = 10;
    public static final int COLOR_CLASSIC_WHITE = 2;
    public static final int COLOR_DEFAULT = 0;
    @Deprecated
    public static final int COLOR_GRAYISH_BLUE = 8;
    public static final int COLOR_INDIGO_BLUE = 6;
    public static final int COLOR_JET_BLACK = 1;
    @Deprecated
    public static final int COLOR_MAGENTA = 7;
    public static final int COLOR_MINT_BLUE = 8;
    @Deprecated
    public static final int COLOR_MUSTARD_YELLOW = 9;
    @Deprecated
    public static final int COLOR_OATMEAL = 5;
    public static final int COLOR_OATMEAL_BEIGE = 5;
    @Deprecated
    public static final int COLOR_ORANGE = 4;
    public static final int COLOR_PEAKCOCK_GREEN = 10;
    public static final int COLOR_PEARL_WHITE = 13;
    public static final int COLOR_PINK = 12;
    public static final int COLOR_PLUM_RED = 7;
    public static final int COLOR_ROSE_GOLD = 11;
    @Deprecated
    public static final int COLOR_SOFT_PINK = 3;
    @Deprecated
    public static final int COLOR_WHITE = 2;
    public static final int COLOR_WILD_ORANGE = 4;
    public static final boolean SWITCH_STATE_COVER_CLOSE = false;
    public static final boolean SWITCH_STATE_COVER_OPEN = true;
    private static final String TAG = "ScoverState";
    public static final int TYPE_FLIP_COVER = 0;
    public static final int TYPE_HEALTH_COVER = 4;
    public static final int TYPE_NONE = 2;
    public static final int TYPE_SVIEW_CHARGER_COVER = 3;
    public static final int TYPE_SVIEW_COVER = 1;
    public static final int TYPE_S_CHARGER_COVER = 5;
    public int color;
    private int heightPixel;
    private boolean switchState;
    public int type;
    private int widthPixel;

    public ScoverState() {
        this.switchState = SWITCH_STATE_COVER_OPEN;
        this.type = TYPE_NONE;
        this.color = TYPE_FLIP_COVER;
        this.widthPixel = TYPE_FLIP_COVER;
        this.heightPixel = TYPE_FLIP_COVER;
    }

    public ScoverState(boolean switchState, int type, int color, int widthPixel, int heightPixel) {
        this.switchState = switchState;
        this.type = type;
        this.color = color;
        this.widthPixel = widthPixel;
        this.heightPixel = heightPixel;
    }

    public boolean getSwitchState() {
        return this.switchState;
    }

    public int getType() {
        return this.type;
    }

    public int getColor() {
        return this.color;
    }

    public int getWindowWidth() {
        return this.widthPixel;
    }

    public int getWindowHeight() {
        return this.heightPixel;
    }

    public String toString() {
        Object[] objArr = new Object[TYPE_S_CHARGER_COVER];
        objArr[TYPE_FLIP_COVER] = Boolean.valueOf(this.switchState);
        objArr[TYPE_SVIEW_COVER] = Integer.valueOf(this.type);
        objArr[TYPE_NONE] = Integer.valueOf(this.color);
        objArr[TYPE_SVIEW_CHARGER_COVER] = Integer.valueOf(this.widthPixel);
        objArr[TYPE_HEALTH_COVER] = Integer.valueOf(this.heightPixel);
        return String.format("ScoverState(switchState=%b type=%d color=%d widthPixel=%d heightPixel=%d)", objArr);
    }
}
