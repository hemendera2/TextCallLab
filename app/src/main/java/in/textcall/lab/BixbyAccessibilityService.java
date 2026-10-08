package in.textcall.lab;

import android.accessibilityservice.AccessibilityService;
import android.os.Bundle;
import android.os.SystemClock;
import android.content.SharedPreferences;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Samsung Bixby Text Call proof-of-concept.  It NEVER accepts/hangs up a call.
 * User must initiate Samsung Text Call.  Auto-send is explicitly opt-in.
 * Safety: no action unless exact caller AND send resource IDs are configured.
 * One UI screen layout not verified on the user's A52s.
 */
public final class BixbyAccessibilityService extends AccessibilityService {
    private static final String TARGET_PACKAGE = "com.samsung.android.incallui";
    private static final long MIN_SCAN_MS = 650L;
    // All real call actions disabled until tested and audited on a real device.
    private static final boolean LIVE_SEND_CERTIFIED = false;
    private long lastScanAt = 0;
    private String lastProcessedCallerDigest = "";
    private String lastGeneratedResponseDigest = "";
    private long lastStatusAt = 0;

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) return;
        SharedPreferences p = Prefs.get(this);
        if (!p.getBoolean(Prefs.ENABLED, false)) return;
        CharSequence pkg = event.getPackageName();
        if (pkg == null || !TARGET_PACKAGE.contentEquals(pkg)) return;

        long now = SystemClock.elapsedRealtime();
        if (now - lastScanAt < MIN_SCAN_MS) return;
        lastScanAt = now;
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null || root.getPackageName() == null
                || !TARGET_PACKAGE.contentEquals(root.getPackageName())) return;
        try { inspect(root, p, now); }
        catch (Exception e) {
            Prefs.status(this, "Inspection error: " + e.getClass().getSimpleName(),
                    "Please disable auto-send and report the error without personal caller data.");
        }
    }

    private void inspect(AccessibilityNodeInfo root, SharedPreferences p, long now) {
        String callerId = Prefs.value(p, Prefs.CALLER_ID, "").trim();
        String sendId = Prefs.value(p, Prefs.SEND_ID, "").trim();
        Snapshot snap = new Snapshot(callerId, sendId);
        collect(root, snap, 0);

        // Only Samsung's in-call UI is read, never accept calls automatically.
        // Also require an editable input AND visible Text Call / Voice Call indicator.
        boolean seemsTextCall = snap.input != null && snap.containsCallAnchor;
        String mode = "PRIVACY SAFE: DRY RUN ONLY";
        String state = "Mode: " + mode + " | Samsung text-call view: " + seemsTextCall
                + " | Editable: " + (snap.input != null)
                + " | Caller-ID match: " + (snap.lastCallerNode != null)
                + " | Send-ID match: " + (snap.send != null);
        if (now - lastStatusAt > 1500L) {
            String diag = snap.report();
            if (diag.length() > 6000) diag = diag.substring(0, 6000);
            Prefs.status(this, state, diag);
            lastStatusAt = now;
        }

        if (!seemsTextCall || callerId.isEmpty() || sendId.isEmpty()
                || snap.send == null || snap.lastCallerNode == null) return;
        String caller = text(snap.lastCallerNode);
        if (caller.isEmpty() || caller.length() < 2 || caller.length() > 600
                || fingerprint(caller).equals(lastProcessedCallerDigest)
                || fingerprint(caller).equals(lastGeneratedResponseDigest)) return;

        // Strict safety check: avoids replying to obvious outgoing automated messages.
        String low = caller.toLowerCase(Locale.ROOT);
        if (low.startsWith("main ") && low.contains("automated")) return;
        String answer = OfflineResponder.reply(caller,
                Prefs.value(p, Prefs.OWNER, ""), Prefs.value(p, Prefs.INSTRUCTIONS, ""));
        if (answer.isEmpty()) return;
        lastProcessedCallerDigest = fingerprint(caller);
        lastGeneratedResponseDigest = fingerprint(answer);

        if (!LIVE_SEND_CERTIFIED || !p.getBoolean(Prefs.AUTO_SEND, false)) {
            Prefs.status(this, "DRY RUN: local response generated; not saved or sent",
                    snap.report());
            return;
        }

        Bundle b = new Bundle();
        b.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, answer);
        boolean filled = snap.input.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, b);
        if (!filled) {
            Prefs.status(this, "Reply box did not accept text. Nothing sent.", snap.report());
            return;
        }
        boolean clicked = snap.send.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        Prefs.status(this, clicked ? "Reply click attempted (VERIFY on call)" : "Reply typed; send click failed",
                snap.report());
    }

    private void collect(AccessibilityNodeInfo node, Snapshot snap, int depth) {
        if (node == null || depth > 18 || snap.count >= 180 || node.isPassword()) return;
        snap.count++;
        String id = node.getViewIdResourceName();
        String desc = node.getContentDescription() == null ? "" : node.getContentDescription().toString();
        String content = text(node);
        String label = (desc + " " + content).toLowerCase(Locale.ROOT);
        if (label.contains("voice call") || label.contains("bixby text call")
                || label.contains("text call") || label.contains("end call")) {
            snap.containsCallAnchor = true;
        }
        if (node.isEditable() && snap.input == null) snap.input = node;
        if (!snap.callerHint.isEmpty() && idMatch(id, snap.callerHint) && !node.isEditable()
                && !content.isEmpty()) {
            // Most recent matching text node in the current accessibility traversal.
            snap.lastCallerNode = node;
        }
        if (!snap.sendHint.isEmpty() && idMatch(id, snap.sendHint) && node.isClickable()) {
            snap.send = node;
        }
        if (id != null || !content.isEmpty() || !desc.isEmpty() || node.isEditable()) {
            if (snap.lines.size() < 95) {
                snap.lines.add((node.isEditable() ? "[EDIT] " : "")
                        + (node.isClickable() ? "[CLICK] " : "")
                        + (id == null ? "-" : id) + " | "
                        + node.getClassName() + " | hasText=" + !content.isEmpty()
                        + " | hasDescription=" + !desc.isEmpty());
            }
        }
        for (int i = 0; i < node.getChildCount() && snap.count < 180; i++) {
            collect(node.getChild(i), snap, depth + 1);
        }
    }

    private static boolean idMatch(String viewId, String requestedId) {
        if (viewId == null || requestedId == null || requestedId.isEmpty()) return false;
        return viewId.equals(requestedId) || viewId.endsWith("/" + requestedId);
    }

    private static String text(AccessibilityNodeInfo node) {
        if (node == null || node.getText() == null) return "";
        return node.getText().toString().trim();
    }

    // Prevent holding raw caller transcripts in long-lived service fields.
    private static String fingerprint(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder();
            for (int i = 0; i < 8; i++) {
                int n = bytes[i] & 0xFF;
                out.append(Character.forDigit(n >>> 4, 16));
                out.append(Character.forDigit(n & 0x0F, 16));
            }
            return out.toString();
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString(value.hashCode()) + ":" + value.length();
        }
    }

    @Override public void onInterrupt() { }

    private static final class Snapshot {
        final String callerHint;
        final String sendHint;
        final List<String> lines = new ArrayList<>();
        int count = 0;
        boolean containsCallAnchor = false;
        AccessibilityNodeInfo input;
        AccessibilityNodeInfo lastCallerNode;
        AccessibilityNodeInfo send;

        Snapshot(String callerHint, String sendHint) {
            this.callerHint = callerHint;
            this.sendHint = sendHint;
        }
        String report() {
            StringBuilder s = new StringBuilder();
            s.append("Samsung accessibility screen probe. Nodes: ").append(count).append('\n');
            s.append("Find transcript text node's resource ID and send button's resource ID.\n");
            s.append("IDs may be the SAME for caller and bot bubbles; if so DO NOT enable Auto-send.\n\n");
            for (String line : lines) s.append(line).append('\n');
            return s.toString();
        }
    }
}
