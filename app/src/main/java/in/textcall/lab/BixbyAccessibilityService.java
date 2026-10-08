package in.textcall.lab;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Samsung in-call UI-only accessibility probe plus an optional user-operated panel.
 * Never reads or stores caller speech, never records, accepts, replies to or hangs up calls.
 * Floating panel is NOT an AI connected to the caller. It changes only local preferences.
 */
public final class BixbyAccessibilityService extends AccessibilityService {
    private static final String TARGET_PACKAGE = "com.samsung.android.incallui";
    private static final boolean LIVE_SEND_CERTIFIED = false;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private WindowManager windowManager;
    private LinearLayout panel;
    private long lastScanAt;
    private long lastStatusAt;
    private long panelExpiryAt;
    private boolean dismissed;

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getPackageName() == null) return;
        if (!TARGET_PACKAGE.contentEquals(event.getPackageName())) return;
        SharedPreferences prefs = Prefs.get(this);
        if (!prefs.getBoolean(Prefs.ENABLED, false)) {
            removePanel();
            return;
        }
        long now = SystemClock.elapsedRealtime();
        if (now - lastScanAt < 650) return;
        lastScanAt = now;

        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null || root.getPackageName() == null
                || !TARGET_PACKAGE.contentEquals(root.getPackageName())) return;

        Scan result = new Scan();
        scan(root, result, 0);
        if (now - lastStatusAt > 1300) {
            Prefs.status(this, "Samsung UI detected • screen probe only • no call control",
                    "Samsung package: " + TARGET_PACKAGE + "\nNodes: " + result.nodes
                    + "\nEditable fields: " + result.editable
                    + "\nClickable elements: " + result.clickable
                    + "\nRecognized call controls: " + result.callAnchor
                    + "\nRaw caller content: NEVER stored"
                    + "\nAuto-send: LOCKED");
            lastStatusAt = now;
        }
        if (result.callAnchor && prefs.getBoolean(Prefs.FLOATING, false) && !dismissed) {
            panelExpiryAt = now + 18000L;
            if (panel == null) showPanel();
            scheduleExpiry();
        } else if (!prefs.getBoolean(Prefs.FLOATING, false)) {
            removePanel();
        }
    }

    private void scan(AccessibilityNodeInfo node, Scan scan, int depth) {
        if (node == null || depth > 15 || scan.nodes > 130) return;
        scan.nodes++;
        if (node.isEditable()) scan.editable++;
        if (node.isClickable()) scan.clickable++;
        // UI control labels are inspected in memory, never persisted.
        CharSequence d = node.getContentDescription();
        String desc = d == null ? "" : d.toString().toLowerCase(java.util.Locale.ROOT);
        CharSequence t = node.getText();
        String label = t == null ? "" : t.toString().toLowerCase(java.util.Locale.ROOT);
        if (hasAnchor(desc) || hasAnchor(label)) scan.callAnchor = true;
        for (int i = 0; i < node.getChildCount() && scan.nodes <= 130; i++)
            scan(node.getChild(i), scan, depth + 1);
    }

    private boolean hasAnchor(String s) {
        return s.equals("answer") || s.equals("decline") || s.equals("end call")
                || s.contains("bixby text call") || s.contains("text call")
                || s.equals("reject") || s.contains("incoming call")
                || s.equals("answer call") || s.equals("decline call");
    }

    private GradientDrawable background(int color, int radius) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(radius * getResources().getDisplayMetrics().density);
        return bg;
    }
    private int dp(int d) {
        return (int) (d * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void showPanel() {
        try {
            windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
            if (windowManager == null) return;
            panel = new LinearLayout(this);
            panel.setOrientation(LinearLayout.VERTICAL);
            panel.setPadding(dp(14),dp(12),dp(14),dp(12));
            panel.setBackground(background(Color.rgb(17,28,53),20));
            TextView heading = new TextView(this);
            heading.setText("✦  CALLCOMPANION  ·  PREVIEW");
            heading.setTextColor(Color.WHITE);
            heading.setTextSize(12);
            panel.addView(heading);
            TextView disclosure = new TextView(this);
            disclosure.setText("Bixby Text Call must be started manually. This panel cannot answer or speak into your SIM call.");
            disclosure.setTextColor(Color.rgb(198,214,238));
            disclosure.setTextSize(11);
            disclosure.setPadding(0,dp(5),0,dp(7));
            panel.addView(disclosure);

            final String[] styles = {"Deep", "Natural", "Bright"};
            Button style = new Button(this);
            style.setAllCaps(false);
            style.setText("Voice preset: " + Prefs.get(this).getString(Prefs.STYLE, "Natural"));
            style.setOnClickListener(v -> {
                SharedPreferences p = Prefs.get(this);
                String current = p.getString(Prefs.STYLE, "Natural");
                int n = "Deep".equals(current) ? 1 : "Natural".equals(current) ? 2 : 0;
                p.edit().putString(Prefs.STYLE, styles[n]).apply();
                style.setText("Voice preset: " + styles[n]);
            });
            panel.addView(style);
            Button open = new Button(this);
            open.setAllCaps(false);
            open.setText("Open app · voices and privacy");
            open.setOnClickListener(v -> {
                removePanel();
                Intent i = new Intent(this, MainActivity.class);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(i);
            });
            panel.addView(open);
            Button dismiss = new Button(this);
            dismiss.setAllCaps(false);
            dismiss.setText("×  Dismiss shortcut");
            dismiss.setOnClickListener(v -> { dismissed = true; removePanel(); });
            panel.addView(dismiss);

            WindowManager.LayoutParams p = new WindowManager.LayoutParams(
                    dp(255), WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                    PixelFormat.TRANSLUCENT);
            p.gravity = Gravity.TOP | Gravity.END;
            p.x = dp(12);
            p.y = dp(180);
            windowManager.addView(panel, p);
        } catch (Exception ignored) { removePanel(); }
    }

    private void scheduleExpiry() {
        handler.removeCallbacksAndMessages(null);
        handler.postDelayed(() -> {
            if (SystemClock.elapsedRealtime() >= panelExpiryAt) {
                removePanel();
                dismissed = false;
            }
        }, 18500L);
    }

    private void removePanel() {
        if (panel != null && windowManager != null) {
            try { windowManager.removeView(panel); } catch (Exception ignored) { }
        }
        panel = null;
    }

    @Override public void onInterrupt() { removePanel(); }
    @Override public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        removePanel();
        super.onDestroy();
    }
    private static final class Scan {
        int nodes;
        int editable;
        int clickable;
        boolean callAnchor;
    }
}
