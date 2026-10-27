package com.voidstack.modkit;

import android.app.Activity;
import android.app.Application;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.lang.ref.WeakReference;

/* ============================================================================
 *  EXPIRY KIT  —  forced update dialog (non-dismissable)
 * ============================================================================
 *
 *  Dialog tab tak band nahi hota jab tak "Update Now" click na ho.
 *  Outside tap block, back button block, close button nahi hai.
 *
 *  INJECT (smali me ek line):
 *
 *    Application.onCreate()V:
 *      invoke-static {p0}, Lcom/voidstack/modkit/ModKit;->install(Landroid/app/Application;)V
 *
 *    Activity.onCreate()V:
 *      invoke-static {p0}, Lcom/voidstack/modkit/ModKit;->show(Landroid/app/Activity;)V
 *
 *    Confusion ho to (dono handle karta hai):
 *      invoke-static {p0}, Lcom/voidstack/modkit/ModKit;->attach(Landroid/content/Context;)V
 * ========================================================================== */

public final class ModKit {

    // ======================================================================
    //  CONFIG  —  sirf yahan edit karo
    // ======================================================================

    public static boolean ENABLED = true;
    public static int SHOW_DELAY_MS = 800;

    public static String TITLE = "UPDATE\nREQUIRED";
    public static String UPDATE_URL = "https://t.me/Voidstackresourcesbyhexy";
    public static String FOOTER_TEXT = "A new version ..xx \u2022  please update to continue \uD83D\uDE0A";

    /**
     * Plain-text URL jisme sirf latest versionCode ho (jaise "42").
     * Purana app: local < remote -> UPDATE REQUIRED dialog.
     * remote "0" ya "-1" -> dialog kabhi nahi (disable).
     * fetch fail -> dialog nahi (offline user block nahi).
     */
    public static String VERSION_URL = "https://raw.githubusercontent.com/officialsycronix/Expiry-Kit/main/latest.txt";

    public static String UPDATE_LABEL = "Update Now";

    public static String[] FEATURES = {
            "New version released",
            "Please update to continue",
            "Old version may not work properly"
    };

    public static String DESCRIPTION =
            "A new version of this app has been released.\n\n" +
            "Please update to the latest version to continue using all features.\n\n" +
            "Tap the button below to get the new version.";

    // ======================================================================
    //  ENTRY POINTS
    // ======================================================================

    public static void install(Application app) {
        if (app == null || sInstalled) return;
        sInstalled = true;
        try {
            app.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
                public void onActivityCreated(Activity a, Bundle b) { }
                public void onActivityStarted(Activity a) { }
                public void onActivityResumed(Activity a) { sLastActivity = new WeakReference<Activity>(a); maybeShow(a); }
                public void onActivityPaused(Activity a) { }
                public void onActivityStopped(Activity a) { }
                public void onActivitySaveInstanceState(Activity a, Bundle b) { }
                public void onActivityDestroyed(Activity a) { }
            });
        } catch (Throwable ignored) { }
    }

    public static void show(Activity a) { maybeShow(a); }

    public static void showAlways(Activity a) {
        if (a != null) display(a);
    }

    public static void attach(Context c) {
        if (c == null) return;
        try {
            Log.i("EXPIRYKIT", "attach from " + c.getClass().getName());
            Context app = c.getApplicationContext();
            if (app instanceof Application) install((Application) app);
            if (c instanceof Activity) show((Activity) c);
        } catch (Throwable ignored) { }
    }

    private static void maybeShow(Activity a) {
        if (a == null || sShownThisRun || !ENABLED) return;
        if (a != null) sLastActivity = new WeakReference<Activity>(a);
        Integer remote = sRemoteVersion;
        if (remote == null) {
            startVersionFetch();
            return; // fetch done hone pe decide hoga
        }
        if (remote <= 0) return;            // 0/-1 = disabled or fetch failed
        if (remote <= localVersionCode(a)) return; // already latest
        sShownThisRun = true;
        display(a);
    }

    private static void startVersionFetch() {
        if (sFetching) return;
        sFetching = true;
        new Thread(new Runnable() {
            public void run() {
                int result = -1;
                try {
                    HttpURLConnection c = (HttpURLConnection) new URL(VERSION_URL).openConnection();
                    c.setConnectTimeout(5000);
                    c.setReadTimeout(5000);
                    c.setRequestMethod("GET");
                    BufferedReader br = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) sb.append(line.trim());
                    br.close();
                    c.disconnect();
                    result = Integer.parseInt(sb.toString().trim());
                } catch (Throwable ignored) { result = -1; }
                sRemoteVersion = Integer.valueOf(result);
                sFetching = false;
                // fetch ke baad: activity live hai aur outdated hai to turant show
                if (result > 0) {
                    Activity act = sLastActivity != null ? sLastActivity.get() : null;
                    final Activity target = (act != null && !act.isFinishing()) ? act : null;
                    if (target != null && !sShownThisRun && ENABLED) {
                        if (result > localVersionCode(target)) {
                            target.runOnUiThread(new Runnable() {
                                public void run() {
                                    if (sShownThisRun || target.isFinishing()) return;
                                    sShownThisRun = true;
                                    display(target);
                                }
                            });
                        }
                    }
                }
            }
        }, "expirykit-version").start();
    }

    private static int localVersionCode(Context c) {
        try {
            PackageInfo pi = c.getPackageManager().getPackageInfo(c.getPackageName(), 0);
            return pi.versionCode;
        } catch (Throwable t) { return -1; }
    }

    private static void display(final Activity a) {
        if (a == null || a.isFinishing() || sShowing) return;
        try {
            a.getWindow().getDecorView().postDelayed(new Runnable() {
                private int tries;

                public void run() {
                    try {
                        if (a.isFinishing()) { sShowing = false; return; }
                        if (!a.getWindow().getDecorView().isShown()) {
                            if (++tries < 6) {
                                a.getWindow().getDecorView().postDelayed(this, 500);
                            } else {
                                sShowing = false;
                                sShownThisRun = false;
                            }
                            return;
                        }
                        sShowing = true;
                        Log.i("EXPIRYKIT", "showing on " + a.getClass().getName());
                        Ui.show(a);
                    } catch (Throwable t) {
                        sShowing = false;
                    }
                }
            }, Math.max(0, SHOW_DELAY_MS));
        } catch (Throwable ignored) { }
    }

    // ======================================================================
    //  STATE
    // ======================================================================

    private static boolean sInstalled;
    private static boolean sShownThisRun;
    private static boolean sShowing;

    private static volatile Integer sRemoteVersion; // null = not fetched yet
    private static volatile boolean sFetching;
    private static WeakReference<Activity> sLastActivity;

    static void onDialogClosed() { sShowing = false; }

    private ModKit() { }

    // ======================================================================
    //  UI  —  single screen, forced (non-dismissable)
    // ======================================================================

    private static final class Ui {

        private static final int CARD_BG      = 0xFF0B1424;
        private static final int CARD_STROKE  = 0xFF2E9BFF;
        private static final int INNER_BG     = 0xFF070E1A;
        private static final int INNER_STROKE = 0xFF1D4E86;
        private static final int TEXT_MAIN    = 0xFFFFFFFF;
        private static final int TEXT_SUB     = 0xFFA7B8CC;
        private static final int ACCENT_BLUE  = 0xFF3B9BFF;
        private static final int GRAD_A       = 0xFF8B2BE2;
        private static final int GRAD_B       = 0xFF2E6BFF;

        private final Activity act;
        private final float dens;
        private Dialog dialog;

        private Ui(Activity a) {
            act = a;
            dens = a.getResources().getDisplayMetrics().density;
        }

        static void show(Activity a) {
            if (a == null) return;
            try { new Ui(a).build(); } catch (Throwable t) { ModKit.onDialogClosed(); }
        }

        private void build() {
            dialog = new Dialog(act);
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

            // === FORCED: no outside tap, no back button ===
            dialog.setCancelable(false);
            dialog.setCanceledOnTouchOutside(false);

            Window w = dialog.getWindow();
            if (w != null) {
                w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
                w.setDimAmount(0.80f);
                w.setGravity(Gravity.CENTER);
            }

            FrameLayout root = new FrameLayout(act);
            int pad = dp(14);
            root.setPadding(pad, pad, pad, pad);

            LinearLayout card = new LinearLayout(act);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setBackground(cardBackground());

            FrameLayout body = new FrameLayout(act);
            card.addView(body, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            root.addView(card, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            body.addView(infoScreen(), new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            dialog.setContentView(root);
            dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
                public void onDismiss(DialogInterface d) { ModKit.onDialogClosed(); }
            });
            dialog.show();

            if (w != null) {
                int width = act.getResources().getDisplayMetrics().widthPixels - dp(24);
                int max = dp(420);
                if (width > max) width = max;
                w.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
            }
        }

        // ---- single screen ----

        private View infoScreen() {
            LinearLayout root = new LinearLayout(act);
            root.setOrientation(LinearLayout.VERTICAL);
            int p = dp(18);
            root.setPadding(p, p, p, dp(14));

            // header: icon + title
            LinearLayout head = new LinearLayout(act);
            head.setOrientation(LinearLayout.HORIZONTAL);
            head.setGravity(Gravity.CENTER_VERTICAL);
            root.addView(head, matchWrap());

            View icon = iconView(dp(52));
            LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(dp(52), dp(52));
            ilp.rightMargin = dp(12);
            head.addView(icon, ilp);

            TextView title = text(TITLE, 18f, TEXT_MAIN, true);
            title.setLineSpacing(dp(1), 1f);
            head.addView(title, new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            // feature box
            LinearLayout box = new LinearLayout(act);
            box.setOrientation(LinearLayout.VERTICAL);
            box.setBackground(roundRect(INNER_BG, dp(16), dp(1.5f), INNER_STROKE));
            int bp = dp(14);
            box.setPadding(bp, bp, bp, bp);
            LinearLayout.LayoutParams blp = matchWrap();
            blp.topMargin = dp(16);
            root.addView(box, blp);

            for (int i = 0; i < FEATURES.length; i++) {
                box.addView(bulletRow(FEATURES[i], i));
            }

            // description
            TextView desc = text(DESCRIPTION, 13.5f, TEXT_SUB, false);
            desc.setLineSpacing(dp(2), 1f);
            LinearLayout.LayoutParams dlp = matchWrap();
            dlp.topMargin = dp(14);
            root.addView(desc, dlp);

            // single Update Now button
            TextView update = gradientPill(UPDATE_LABEL, 16f);
            update.setGravity(Gravity.CENTER);
            update.setPadding(dp(20), dp(14), dp(20), dp(14));
            update.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { openUrl(UPDATE_URL); }
            });
            LinearLayout.LayoutParams ulp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(52));
            ulp.topMargin = dp(18);
            root.addView(update, ulp);

            // footer
            TextView ver = text(footerText(), 11f, TEXT_SUB, false);
            ver.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams vlp = matchWrap();
            vlp.topMargin = dp(12);
            root.addView(ver, vlp);

            return root;
        }

        // ---- pieces ----

        private View bulletRow(String s, int index) {
            LinearLayout r = new LinearLayout(act);
            r.setOrientation(LinearLayout.HORIZONTAL);
            r.setPadding(0, index == 0 ? 0 : dp(7), 0, 0);

            TextView dot = text("\u2022", 14f, ACCENT_BLUE, true);
            dot.setGravity(Gravity.TOP);
            r.addView(dot, new LinearLayout.LayoutParams(dp(16),
                    ViewGroup.LayoutParams.WRAP_CONTENT));

            TextView t = text(s, 14f, TEXT_MAIN, false);
            t.setLineSpacing(dp(2), 1f);
            r.addView(t, new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            return r;
        }

        private View iconView(int size) {
            Bitmap bmp = decodeAsset("modkit_logo.jpg");
            if (bmp == null) bmp = decodeAsset("modkit_logo.png");
            if (bmp != null) {
                RoundedIconView v = new RoundedIconView(act, bmp, dp(12));
                v.setMinimumWidth(size);
                v.setMinimumHeight(size);
                return v;
            }
            TextView t = new TextView(act);
            t.setText("!");
            t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f);
            t.setTextColor(TEXT_MAIN);
            t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            t.setGravity(Gravity.CENTER);
            t.setBackground(roundRect(0xFF111C2E, dp(12), dp(1), INNER_STROKE));
            return t;
        }

        private Bitmap decodeAsset(String name) {
            try {
                return BitmapFactory.decodeStream(act.getAssets().open(name));
            } catch (Throwable t) { return null; }
        }

        private LayerDrawable cardBackground() {
            GradientDrawable glow = new GradientDrawable();
            glow.setColor(Color.TRANSPARENT);
            glow.setCornerRadius(dp(30));
            glow.setStroke(dp(7), 0x332E9BFF);

            GradientDrawable solid = new GradientDrawable();
            solid.setColor(CARD_BG);
            solid.setCornerRadius(dp(26));
            solid.setStroke(dp(2), CARD_STROKE);

            LayerDrawable ld = new LayerDrawable(new Drawable[]{ glow, solid });
            ld.setLayerInset(1, dp(2), dp(2), dp(2), dp(2));
            return ld;
        }

        private GradientDrawable roundRect(int color, float radius, float strokeW, int strokeColor) {
            GradientDrawable d = new GradientDrawable();
            d.setColor(color);
            d.setCornerRadius(radius);
            if (strokeW > 0) d.setStroke((int) strokeW, strokeColor);
            return d;
        }

        private TextView gradientPill(String label, float sp) {
            TextView t = text(label, sp, TEXT_MAIN, true);
            t.setGravity(Gravity.CENTER);
            GradientDrawable d = new GradientDrawable(
                    GradientDrawable.Orientation.LEFT_RIGHT, new int[]{ GRAD_A, GRAD_B });
            d.setCornerRadius(dp(26));
            t.setBackground(d);
            return t;
        }

        private TextView text(String s, float sp, int color, boolean strong) {
            TextView t = new TextView(act);
            t.setText(s);
            t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
            t.setTextColor(color);
            t.setTypeface(Typeface.create(strong ? "sans-serif-medium" : "sans-serif",
                    Typeface.NORMAL));
            return t;
        }

        private String footerText() {
            return FOOTER_TEXT;
        }

        private void openUrl(String url) {
            if (url == null || url.length() == 0) return;
            try {
                Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                act.startActivity(i);
            } catch (ActivityNotFoundException ignored) {
            } catch (Throwable ignored) { }
        }

        private int dp(float v) {
            return (int) (v * dens + 0.5f);
        }

        private static LinearLayout.LayoutParams matchWrap() {
            return new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        private static final class RoundedIconView extends View {
            private final Bitmap bmp;
            private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
            private final Path clipPath = new Path();
            private final RectF dst = new RectF();
            private final float radius;

            RoundedIconView(Context c, Bitmap b, float radius) {
                super(c);
                this.bmp = b;
                this.radius = radius;
                setLayerType(LAYER_TYPE_SOFTWARE, null);
            }

            @Override
            protected void onDraw(Canvas canvas) {
                int w = getWidth();
                int h = getHeight();
                if (w <= 0 || h <= 0 || bmp == null) return;
                float side = Math.min(w, h);
                float l = (w - side) / 2f;
                float t = (h - side) / 2f;
                dst.set(l, t, l + side, t + side);
                clipPath.reset();
                clipPath.addRoundRect(dst, radius, radius, Path.Direction.CW);
                canvas.save();
                canvas.clipPath(clipPath);
                canvas.drawBitmap(bmp, null, dst, paint);
                canvas.restore();
            }
        }
    }
}
