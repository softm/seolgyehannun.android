package net.softm.seolgyehannun;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.ads.AdLoader;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.interstitial.InterstitialAd;
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback;
import com.google.android.gms.ads.nativead.NativeAd;
import com.google.android.gms.ads.nativead.NativeAdOptions;
import com.google.android.gms.ads.nativead.NativeAdView;
import com.google.android.ump.ConsentInformation;
import com.google.android.ump.ConsentRequestParameters;
import com.google.android.ump.UserMessagingPlatform;

final class CommonNativeAds {
    private static final long INTERSTITIAL_FIRST_DELAY_MS = 75_000L;
    private static final long INTERSTITIAL_COOLDOWN_MS = 180_000L;
    private static final long EXIT_AD_WAIT_TIMEOUT_MS = 5_000L;
    private static final int INTERSTITIAL_MAX_PER_SESSION = 3;
    private final Activity activity;
    private final LinearLayout section;
    private final FrameLayout host;
    private final Button privacy;
    private ConsentInformation consent;
    private NativeAd nativeAd;
    private InterstitialAd interstitialAd;
    private AdView exitAd;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean initialized;
    private boolean interstitialLoading;
    private boolean interstitialScheduled;
    private boolean exitAdReady;
    private FrameLayout pendingExitSlot;
    private Runnable pendingExitResolved;
    private int interstitialShownCount;
    private long appStartedAt;
    private long lastInterstitialShownAt;

    CommonNativeAds(Activity activity, LinearLayout section) {
        this.activity = activity;
        this.section = section;
        section.setOrientation(LinearLayout.VERTICAL);
        section.setBackgroundColor(Color.WHITE);
        section.setVisibility(View.GONE);
        privacy = new Button(activity);
        privacy.setText("광고 개인정보 설정");
        privacy.setTextSize(11);
        privacy.setVisibility(View.GONE);
        privacy.setOnClickListener(view -> showPrivacyOptions());
        section.addView(privacy, new LinearLayout.LayoutParams(-2, -2));
        host = new FrameLayout(activity);
        section.addView(host, new LinearLayout.LayoutParams(-1, -2));
    }

    void start() {
        appStartedAt = System.currentTimeMillis();
        consent = UserMessagingPlatform.getConsentInformation(activity);
        consent.requestConsentInfoUpdate(activity, new ConsentRequestParameters.Builder().build(), () -> {
            updatePrivacy();
            UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity, error -> {
                updatePrivacy();
                maybeLoad();
            });
            maybeLoad();
        }, error -> {
            updatePrivacy();
            maybeLoad();
        });
    }

    private void maybeLoad() {
        if (initialized || consent == null || !consent.canRequestAds()) return;
        initialized = true;
        MobileAds.initialize(activity, status -> activity.runOnUiThread(() -> {
            loadNativeAd(); loadInterstitialAd(); loadExitAd(); scheduleInterstitial();
        }));
    }

    private void loadNativeAd() {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        new AdLoader.Builder(activity, activity.getString(R.string.admob_native_ad_unit_id))
            .forNativeAd(ad -> {
                if (activity.isFinishing() || activity.isDestroyed()) { ad.destroy(); return; }
                if (nativeAd != null) nativeAd.destroy();
                nativeAd = ad;
                host.removeAllViews();
                host.addView(render(ad), new FrameLayout.LayoutParams(-1, -2));
                updateVisibility(true);
            })
            .withAdListener(new com.google.android.gms.ads.AdListener() {
                @Override public void onAdFailedToLoad(LoadAdError error) {
                    host.removeAllViews();
                    updateVisibility(false);
                }
            })
            .withNativeAdOptions(new NativeAdOptions.Builder().build())
            .build().loadAd(new AdRequest.Builder().build());
    }

    private void loadInterstitialAd() {
        if (interstitialLoading || interstitialAd != null || !activityAlive()) return;
        interstitialLoading = true;
        InterstitialAd.load(activity, activity.getString(R.string.admob_interstitial_ad_unit_id), new AdRequest.Builder().build(), new InterstitialAdLoadCallback() {
            @Override public void onAdLoaded(InterstitialAd ad) {
                interstitialLoading = false; interstitialAd = ad;
                ad.setFullScreenContentCallback(new FullScreenContentCallback() {
                    @Override public void onAdDismissedFullScreenContent() { interstitialAd = null; lastInterstitialShownAt = System.currentTimeMillis(); loadInterstitialAd(); scheduleInterstitial(); }
                    @Override public void onAdFailedToShowFullScreenContent(AdError error) { interstitialAd = null; loadInterstitialAd(); scheduleInterstitial(); }
                });
                scheduleInterstitial();
            }
            @Override public void onAdFailedToLoad(LoadAdError error) { interstitialLoading = false; handler.postDelayed(CommonNativeAds.this::loadInterstitialAd, INTERSTITIAL_COOLDOWN_MS); }
        });
    }

    private void scheduleInterstitial() {
        if (interstitialScheduled || interstitialShownCount >= INTERSTITIAL_MAX_PER_SESSION) return;
        long now = System.currentTimeMillis();
        long first = Math.max(0L, appStartedAt + INTERSTITIAL_FIRST_DELAY_MS - now);
        long cooldown = lastInterstitialShownAt == 0L ? 0L : Math.max(0L, lastInterstitialShownAt + INTERSTITIAL_COOLDOWN_MS - now);
        interstitialScheduled = true;
        handler.postDelayed(() -> {
            interstitialScheduled = false;
            if (!activityAlive() || interstitialShownCount >= INTERSTITIAL_MAX_PER_SESSION) return;
            if (interstitialAd == null) { loadInterstitialAd(); return; }
            interstitialShownCount++; interstitialAd.show(activity);
        }, Math.max(first, cooldown));
    }

    private void loadExitAd() {
        if (!activityAlive() || exitAd != null) return;
        AdView ad = new AdView(activity); ad.setAdSize(AdSize.MEDIUM_RECTANGLE); ad.setAdUnitId(activity.getString(R.string.admob_exit_banner_ad_unit_id));
        exitAd = ad; exitAdReady = false;
        ad.setAdListener(new com.google.android.gms.ads.AdListener() {
            @Override public void onAdLoaded() {
                if (exitAd != ad || !activityAlive()) { ad.destroy(); return; }
                exitAdReady = true;
                attachPendingExitAd();
            }
            @Override public void onAdFailedToLoad(LoadAdError error) {
                if (exitAd == ad) { exitAd = null; exitAdReady = false; }
                ad.destroy();
                if (pendingExitSlot != null) { pendingExitSlot.removeAllViews(); pendingExitSlot.setVisibility(View.GONE); }
                resolvePendingExit();
                handler.postDelayed(CommonNativeAds.this::loadExitAd, INTERSTITIAL_COOLDOWN_MS);
            }
        });
        ad.loadAd(new AdRequest.Builder().build());
    }

    void showExitConfirmation() {
        if (!activityAlive()) return;
        FrameLayout slot = new FrameLayout(activity);
        LinearLayout loading = new LinearLayout(activity); loading.setOrientation(LinearLayout.VERTICAL); loading.setGravity(Gravity.CENTER);
        android.widget.ProgressBar progress = new android.widget.ProgressBar(activity); loading.addView(progress, new LinearLayout.LayoutParams(dp(44), dp(44)));
        TextView text = new TextView(activity); text.setText("광고를 불러오는 중입니다."); text.setPadding(0, dp(12), 0, 0); loading.addView(text, new LinearLayout.LayoutParams(-2, -2));
        slot.addView(loading, new FrameLayout.LayoutParams(-1, dp(250)));
        AlertDialog dialog = new AlertDialog.Builder(activity).setTitle("앱을 종료하시겠습니까?").setMessage("계속 이용하거나 앱을 종료할 수 있습니다.").setView(slot).setNegativeButton("계속 이용", null).setPositiveButton("종료", (d, which) -> activity.finish()).create();
        Runnable enableExit = () -> { if (dialog.isShowing()) dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true); };
        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
            if (!initialized) { slot.removeAllViews(); slot.setVisibility(View.GONE); enableExit.run(); }
            else { pendingExitSlot = slot; pendingExitResolved = enableExit; if (exitAdReady) attachPendingExitAd(); else loadExitAd(); }
            handler.postDelayed(enableExit, EXIT_AD_WAIT_TIMEOUT_MS);
        });
        dialog.setOnDismissListener(d -> { handler.removeCallbacks(enableExit); if (pendingExitSlot == slot) { pendingExitSlot = null; pendingExitResolved = null; } for (int i = 0; i < slot.getChildCount(); i++) if (slot.getChildAt(i) instanceof AdView) ((AdView) slot.getChildAt(i)).destroy(); slot.removeAllViews(); loadExitAd(); });
        dialog.show();
    }

    private void attachPendingExitAd() {
        if (pendingExitSlot == null || !exitAdReady || exitAd == null) return;
        FrameLayout slot = pendingExitSlot;
        AdView ad = exitAd;
        exitAd = null;
        exitAdReady = false;
        slot.removeAllViews();
        slot.addView(ad, new FrameLayout.LayoutParams(dp(300), dp(250), Gravity.CENTER));
        resolvePendingExit();
    }

    private void resolvePendingExit() {
        Runnable resolved = pendingExitResolved;
        pendingExitSlot = null;
        pendingExitResolved = null;
        if (resolved != null) resolved.run();
    }

    private boolean activityAlive() { return !activity.isFinishing() && !activity.isDestroyed(); }

    private NativeAdView render(NativeAd ad) {
        NativeAdView view = new NativeAdView(activity);
        LinearLayout row = new LinearLayout(activity);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(8), dp(14), dp(8));
        TextView title = new TextView(activity);
        title.setText("광고  " + ad.getHeadline());
        title.setTextColor(Color.rgb(27, 43, 41));
        title.setTextSize(14);
        title.setMaxLines(2);
        row.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        Button action = new Button(activity);
        if (ad.getCallToAction() == null) {
            action.setVisibility(View.GONE);
        } else {
            action.setText(ad.getCallToAction());
            view.setCallToActionView(action);
        }
        action.setTextSize(12);
        row.addView(action, new LinearLayout.LayoutParams(-2, dp(44)));
        view.addView(row, new FrameLayout.LayoutParams(-1, -2));
        view.setHeadlineView(title);
        view.setNativeAd(ad);
        return view;
    }

    private void updatePrivacy() {
        boolean required = consent != null && consent.getPrivacyOptionsRequirementStatus()
            == ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED;
        privacy.setVisibility(required ? View.VISIBLE : View.GONE);
        updateVisibility(host.getChildCount() > 0);
    }

    private void updateVisibility(boolean hasAd) {
        section.setVisibility(hasAd || privacy.getVisibility() == View.VISIBLE ? View.VISIBLE : View.GONE);
    }

    private void showPrivacyOptions() {
        UserMessagingPlatform.showPrivacyOptionsForm(activity, error -> {
            if (error != null) Toast.makeText(activity, "광고 개인정보 설정을 열지 못했습니다.", Toast.LENGTH_SHORT).show();
            updatePrivacy();
            maybeLoad();
        });
    }

    void destroy() {
        handler.removeCallbacksAndMessages(null);
        if (nativeAd != null) nativeAd.destroy();
        nativeAd = null;
        interstitialAd = null;
        if (exitAd != null) exitAd.destroy();
        exitAd = null;
        pendingExitSlot = null;
        pendingExitResolved = null;
        host.removeAllViews();
    }

    private int dp(int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
