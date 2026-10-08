package in.textcall.lab;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;

/**
 * Samsung-only, opt-in live Text Call automation experiment.
 * Controls only unique, exact/whitelisted UI elements, never captures SIM audio.
 * The One UI A52s layout has NOT been validated; unmapped layouts fail closed.
 * No raw caller texts/numbers are logged, saved or sent over a network.
 */
public final class BixbyAccessibilityService extends AccessibilityService {
    private static final String SAMSUNG = "com.samsung.android.incallui";
    private static final String FLOATING_BUTTON = SAMSUNG + ":id/ai_call_floating_button_container";
    private static final long POLL_MS = 160L;
    private static final long COOLDOWN_MS = 3600L;
    private static final int MAX_REPLIES = 12;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private WindowManager manager;
    private LinearLayout panel;
    private boolean dismissed;
    private long nextScan;
    private long nextStatus;
    private long lastCallEvent;
    private long autoWindowEnd;
    private long confirmUntil;
    private long lastAnswerAttempt;
    private long lastReplySentAt;
    private String lastInboundFingerprint = "";
    private String lastSentFingerprint = "";
    private int callerTurns;
    private int replies;
    private String intent = "General enquiry";
    private ConversationEngine conversation;
    private boolean inTextCall;
    private boolean briefCommitted;

    private final Runnable poll = new Runnable() {
        @Override public void run() {
            if (SystemClock.elapsedRealtime() > autoWindowEnd) return;
            if (!Prefs.get(BixbyAccessibilityService.this).getBoolean(Prefs.ENABLED, false)) return;
            inspect(SystemClock.elapsedRealtime());
            handler.postDelayed(this, POLL_MS);
        }
    };

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getPackageName() == null
                || !SAMSUNG.contentEquals(event.getPackageName())) return;
        SharedPreferences p = Prefs.get(this);
        if (!p.getBoolean(Prefs.ENABLED, false)) {
            removePanel();
            return;
        }
        long now = SystemClock.elapsedRealtime();
        lastCallEvent = now;
        if (now < nextScan) return;
        nextScan = now + 220L;
        inspect(now);
    }

    private AccessibilityNodeInfo samsungRoot() {
        // The accessibility overlay may be the ACTIVE window; inspect Samsung's
        // genuine underlying window instead of accidentally reading ourselves.
        java.util.List<AccessibilityWindowInfo> windows = getWindows();
        if (windows != null) {
            for (AccessibilityWindowInfo window : windows) {
                AccessibilityNodeInfo root = window.getRoot();
                if (root != null && root.getPackageName() != null
                        && SAMSUNG.contentEquals(root.getPackageName())) return root;
            }
        }
        AccessibilityNodeInfo active = getRootInActiveWindow();
        return active != null && active.getPackageName() != null
                && SAMSUNG.contentEquals(active.getPackageName()) ? active : null;
    }

    private void inspect(long now) {
        AccessibilityNodeInfo root = samsungRoot();
        if (root == null) return;

        Snapshot snapshot = new Snapshot();
        scan(root, snapshot, 0, false);
        if (!snapshot.relevant()) return;

        if (snapshot.inCall && snapshot.editables == 1) {
            inTextCall = true;
            briefCommitted = false;
            if (conversation == null) {
                SharedPreferences settings = Prefs.get(this);
                conversation = new ConversationEngine(
                    settings.getString(Prefs.PROFILE_NAME, "Owner"),
                    settings.getString(Prefs.PROFILE_INFO, ""),
                    settings.getString(Prefs.PROFILE_RULES, ""));
            }
        }

        SharedPreferences p = Prefs.get(this);
        if (p.getBoolean(Prefs.FLOATING, false) && !dismissed) showPanel();
        else if (!p.getBoolean(Prefs.FLOATING, false)) removePanel();

        if (now > nextStatus) {
            String state = "Samsung call UI • Text mode: " + inTextCall
                    + " • Incoming role-labeled bubbles: " + snapshot.inbound
                    + " • Editable: " + snapshot.editables
                    + " • Exact send buttons: " + snapshot.sendButtons
                    + " • Replies attempted: " + replies;
            String diag = "Samsung in-call UI only\n"
                    + "Text Call control detected: " + (snapshot.textCallButton != null)
                    + "\nText Call confirmation detected: " + (snapshot.textCallConfirm != null)
                    + "\nCaller speaker-marker detected: " + (snapshot.inbound > 0)
                    + "\nReply field detected: " + (snapshot.editables == 1)
                    + "\nWhitelisted send button detected: " + (snapshot.sendButtons == 1)
                    + "\nAI reply opt-in: " + p.getBoolean(Prefs.LIVE_REPLY, false)
                    + "\nAutomatic answer opt-in: " + p.getBoolean(Prefs.AUTO_ATTEND, false)
                    + "\nRaw transcript/number: NOT saved\n"
                    + "Unknown controls are never clicked.";
            Prefs.status(this, state, diag);
            nextStatus = now + 1400L;
        }

        if (p.getBoolean(Prefs.AUTO_ATTEND, false) || now < autoWindowEnd) {
            tryBeginTextCall(snapshot, now);
        }
        if (inTextCall && (p.getBoolean(Prefs.LIVE_REPLY, false)
                || p.getBoolean(Prefs.SAVE_BRIEF, false))) {
            observeTurn(snapshot, now, p.getBoolean(Prefs.LIVE_REPLY, false));
        }
        handler.removeCallbacks(expire);
        handler.postDelayed(expire, 45000L);
    }

    private void tryBeginTextCall(Snapshot s, long now) {
        if (inTextCall) return;
        if (confirmUntil > now) {
            if (s.textCallConfirm != null) {
                if (click(s.textCallConfirm)) {
                    confirmUntil = 0;
                    autoWindowEnd = 0;
                    Prefs.status(this, "Text Call accept action attempted — verify Samsung screen",
                            "No caller text, phone number or audio saved.");
                }
            }
            return;
        }
        // For automatic answering, require clear incoming-call evidence.
        // A deliberate tap on the overlay's "Try AI Attend" may attempt the
        // exact Samsung Text Call button without this additional screen label.
        if (s.textCallButton == null || (!s.incomingScreen && now > autoWindowEnd)
                || now - lastAnswerAttempt < 5000L) return;
        lastAnswerAttempt = now;
        if (click(s.textCallButton)) {
            confirmUntil = now + 5500L;
            autoWindowEnd = now + 5700L;
            handler.removeCallbacks(poll);
            handler.postDelayed(poll, POLL_MS);
        }
    }

    private void observeTurn(Snapshot s, long now, boolean autoReply) {
        // Do not learn from ambiguous unlabeled chat bubbles or reply input.
        if (!s.inCall || s.inbound == 0 || s.callerText.isEmpty() || conversation == null) return;
        String hash = fingerprint(s.callerText);
        if (hash.equals(lastInboundFingerprint) || hash.equals(lastSentFingerprint)) return;
        lastInboundFingerprint = hash;
        callerTurns++;
        String category = CallTurnGuard.category(s.callerText);
        if ("Urgent".equals(category) || "General enquiry".equals(intent)) intent = category;

        if (!autoReply) return;
        if (s.editables != 1 || s.sendButtons != 1 || s.editor == null || s.sender == null
                || replies >= MAX_REPLIES || now - lastReplySentAt < COOLDOWN_MS) return;
        String answer = conversation.respond(s.callerText);
        if (answer.isEmpty()) return;
        Bundle text = new Bundle();
        text.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, answer);
        boolean typed = s.editor.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, text);
        if (!typed) {
            Prefs.status(this, "Samsung reply field rejected local reply", "No caller text stored.");
            return;
        }
        boolean sent = click(s.sender);
        if (sent) {
            lastSentFingerprint = fingerprint(answer);
            replies++;
            lastReplySentAt = now;
            Prefs.status(this, "Samsung reply click attempted (" + replies + ")", "Topic: " + intent
                    + ". Verify that the caller actually heard the voice.");
        } else {
            Prefs.status(this, "Send click failed; Bixby input may contain unsent text",
                    "No raw caller content saved.");
        }
    }

    private void scan(AccessibilityNodeInfo n, Snapshot s, int depth, boolean incomingScope) {
        if (n == null || depth > 20 || s.nodes >= 180 || n.isPassword()) return;
        s.nodes++;
        String id = n.getViewIdResourceName();
        String label = label(n);
        String text = n.getText() == null ? "" : n.getText().toString();
        String description = n.getContentDescription() == null ? "" : n.getContentDescription().toString();
        String l = label.toLowerCase(Locale.ROOT);
        boolean inbound = CallTurnGuard.isIncoming(id, description);
        boolean outgoing = CallTurnGuard.isOutgoing(id, description);
        if (!outgoing && inbound) {
            s.inbound++;
            String v = CallTurnGuard.normalizedCallerText(text, description);
            if (!v.isEmpty()) s.callerText = v;
        } else if (!outgoing && incomingScope && !n.isEditable() && !text.isEmpty()) {
            String v = CallTurnGuard.normalizedCallerText(text, "");
            if (!v.isEmpty()) { s.callerText = v; s.inbound++; }
        }

        if (id != null && FLOATING_BUTTON.equals(id) && n.isVisibleToUser()) {
            s.textCallButton = n;
        }
        // Accept confirmation may have no ID on some Samsung releases.
        if (s.textCallConfirm == null && n.isClickable()
                && ((l.contains("text call") && l.contains("answer"))
                || (l.contains("text call") && l.contains("swipe to answer")))) {
            s.textCallConfirm = n;
        }
        if (l.equals("incoming call") || l.equals("answer call") || l.equals("decline call")
                || l.equals("answer") || l.equals("decline") || l.equals("reject")
                || l.contains("incoming call") || l.contains("swipe to answer")) {
            s.incomingScreen = true;
        }
        if (l.contains("end call") || l.contains("switch to voice call")
                || l.contains("voice call")) s.inCall = true;
        if (n.isEditable() && n.isVisibleToUser()) {
            s.editables++;
            s.editor = n;
        }
        if (n.isClickable() && n.isVisibleToUser() && CallTurnGuard.safeSend(id, label)) {
            s.sendButtons++;
            s.sender = n;
        }
        for (int i = 0; i < n.getChildCount() && s.nodes < 180; i++) {
            scan(n.getChild(i), s, depth + 1, inbound || (incomingScope && !outgoing));
        }
    }

    private static String label(AccessibilityNodeInfo n) {
        CharSequence description = n.getContentDescription();
        if (description != null && description.length() > 0) return description.toString().trim();
        CharSequence text = n.getText();
        return text == null ? "" : text.toString().trim();
    }
    private static boolean click(AccessibilityNodeInfo node) {
        if (node == null || !node.isVisibleToUser()) return false;
        if (node.isClickable()) return node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        AccessibilityNodeInfo parent = node.getParent();
        if (parent != null && parent.isClickable() && parent.isVisibleToUser()) {
            return parent.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        }
        return false;
    }

    private static String fingerprint(String text) {
        try {
            byte[] v = MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder();
            for (int i=0;i<12;i++) out.append(String.format(Locale.ROOT,"%02x",v[i] & 0xff));
            return out.toString();
        } catch (Exception e) { return Integer.toHexString(text.hashCode()); }
    }

    private final Runnable expire = new Runnable() {
        @Override public void run() {
            if (SystemClock.elapsedRealtime() - lastCallEvent < 30000L) return;
            finalizeSession();
            removePanel();
        }
    };

    private void finalizeSession() {
        if (briefCommitted) return;
        briefCommitted = true;
        SharedPreferences p = Prefs.get(this);
        if (p.getBoolean(Prefs.SAVE_BRIEF, false) && callerTurns > 0) {
            boolean ok = new PrivateBriefStore(this).save(intent,
                    CallTurnGuard.followUp(intent), callerTurns);
            Prefs.status(this, ok ? "Encrypted call brief saved" : "Could not encrypt call brief",
                    "Summary contains only category, follow-up and turn count; no raw transcript.");
        }
        conversation = null;
        inTextCall = false;
        callerTurns = 0;
        replies = 0;
        intent = "General enquiry";
        lastInboundFingerprint = "";
        lastSentFingerprint = "";
        autoWindowEnd = 0;
        confirmUntil = 0;
        lastAnswerAttempt = 0;
    }

    private GradientDrawable background(int color) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(18 * getResources().getDisplayMetrics().density);
        return d;
    }
    private int dp(int n) { return (int)(n * getResources().getDisplayMetrics().density + .5f); }

    private void showPanel() {
        if (panel != null) return;
        try {
            manager = (WindowManager)getSystemService(WINDOW_SERVICE);
            if (manager == null) return;
            panel = new LinearLayout(this);
            panel.setOrientation(LinearLayout.VERTICAL);
            panel.setPadding(dp(13),dp(11),dp(13),dp(11));
            panel.setBackground(background(Color.rgb(16,31,60)));
            TextView heading = new TextView(this);
            heading.setText("◉  CALLCOMPANION");
            heading.setTextColor(Color.WHITE);
            heading.setTextSize(14);
            panel.addView(heading);
            TextView note = new TextView(this);
            note.setText("Samsung Text Call • experimental");
            note.setTextColor(Color.rgb(197,214,235));
            note.setTextSize(11);
            panel.addView(note);

            Button attend = new Button(this);
            attend.setText("Try AI Attend (Bixby)");
            attend.setAllCaps(false);
            attend.setOnClickListener(v -> {
                autoWindowEnd = SystemClock.elapsedRealtime() + 7000L;
                handler.removeCallbacks(poll);
                handler.post(poll);
            });
            panel.addView(attend);

            Button open = new Button(this);
            open.setText("App / call brief");
            open.setAllCaps(false);
            open.setOnClickListener(v -> {
                removePanel();
                Intent intent = new Intent(this, MainActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
            });
            panel.addView(open);
            Button dismiss = new Button(this);
            dismiss.setText("Dismiss");
            dismiss.setAllCaps(false);
            dismiss.setOnClickListener(v -> { dismissed = true; removePanel(); });
            panel.addView(dismiss);

            WindowManager.LayoutParams p = new WindowManager.LayoutParams(
                    dp(240), WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                    PixelFormat.TRANSLUCENT);
            p.gravity = Gravity.TOP | Gravity.END;
            p.x = dp(14); p.y = dp(130);
            manager.addView(panel, p);
        } catch (Exception e) { removePanel(); }
    }
    private void removePanel() {
        if (panel != null && manager != null) {
            try { manager.removeView(panel); } catch (Exception ignored) { }
        }
        panel = null;
    }
    @Override public void onInterrupt() {
        removePanel();
        handler.removeCallbacks(poll);
    }
    @Override public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        finalizeSession();
        removePanel();
        super.onDestroy();
    }
    private static final class Snapshot {
        int nodes;
        int editables;
        int sendButtons;
        int inbound;
        boolean inCall;
        boolean incomingScreen;
        AccessibilityNodeInfo textCallButton;
        AccessibilityNodeInfo textCallConfirm;
        AccessibilityNodeInfo editor;
        AccessibilityNodeInfo sender;
        String callerText = "";
        boolean relevant() {
            return inCall || incomingScreen || textCallButton != null || textCallConfirm != null
                    || editables > 0;
        }
    }
}