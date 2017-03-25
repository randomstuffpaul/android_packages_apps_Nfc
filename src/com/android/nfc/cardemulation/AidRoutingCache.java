package com.android.nfc.cardemulation;

import com.android.nfc.NfcService;
import java.util.Collections;
import java.util.Hashtable;
import java.util.List;

public class AidRoutingCache {
    private static final int CAPACITY = 32;
    private static final boolean DBG = false;
    private static final String TAG = "AidRoutingCache";
    private static final Hashtable<String, AidElement> mRouteCache;

    static {
        mRouteCache = new Hashtable(CAPACITY);
    }

    AidRoutingCache() {
    }

    boolean addAid(String aid, boolean isDefault, int route, int power) {
        AidElement elem = new AidElement(aid, isDefault, route, power);
        if (mRouteCache.size() >= CAPACITY) {
            return DBG;
        }
        mRouteCache.put(aid.toUpperCase(), elem);
        return true;
    }

    boolean removeAid(String aid) {
        return mRouteCache.remove(aid) != null ? true : DBG;
    }

    boolean isDefault(String aid) {
        AidElement elem = (AidElement) mRouteCache.get(aid);
        return (elem == null || !elem.isDefault()) ? DBG : true;
    }

    void clear() {
        mRouteCache.clear();
    }

    void commit() {
        List<AidElement> list = Collections.list(mRouteCache.elements());
        Collections.sort(list);
        NfcService.getInstance().clearRouting();
        for (AidElement element : list) {
            NfcService.getInstance().routeAids(element.getAid(), element.getRouteLocation(), element.getPowerState());
        }
    }
}
