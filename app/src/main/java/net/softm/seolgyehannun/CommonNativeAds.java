package net.softm.seolgyehannun;

import android.app.Activity;
import android.widget.LinearLayout;

import softm.hannun.ads.HannunAdConfig;
import softm.hannun.ads.HannunAds;

final class CommonNativeAds {
    private final HannunAds delegate;

    CommonNativeAds(Activity activity, LinearLayout section) {
        delegate = new HannunAds(activity, section, new HannunAdConfig(
                activity.getString(R.string.admob_native_ad_unit_id), null,
                activity.getString(R.string.admob_interstitial_ad_unit_id),
                activity.getString(R.string.admob_exit_banner_ad_unit_id)));
    }

    void start() { delegate.start(); }
    void onResume() { delegate.onResume(); }
    void onPause() { delegate.onPause(); }
    void showExitConfirmation() { delegate.showExitConfirmation(); }
    void destroy() { delegate.destroy(); }
}

