package in.textcall.lab;

import java.util.Locale;

/**
 * Conservative offline rules for Samsung's unversioned accessibility chat UI.
 * NEVER consider unlabeled chat text a caller message. Prevents echo loops.
 */
public final class CallTurnGuard {
    private CallTurnGuard() { }
    public static boolean isIncoming(String viewId, String description) {
        String d = normalize(description);
        String id = normalize(viewId);
        boolean incomingLabel = d.startsWith("caller:") || d.startsWith("caller says:")
                || d.startsWith("incoming message:") || d.startsWith("other party:")
                || d.startsWith("from caller:") || d.startsWith("caller message:")
                || d.startsWith("incoming message,") || d.startsWith("other party says:");
        boolean incomingId = id.endsWith("/incoming_message_text")
                || id.endsWith("/received_message_text")
                || id.endsWith("/caller_transcript_text")
                || id.endsWith("/incoming_transcription_text");
        return incomingLabel || incomingId;
    }
    public static boolean isOutgoing(String viewId, String description) {
        String d = normalize(description);
        String id = normalize(viewId);
        return d.startsWith("you:") || d.startsWith("outgoing message:")
                || d.startsWith("my message:") || d.startsWith("assistant:")
                || id.endsWith("/outgoing_message_text")
                || id.endsWith("/sent_message_text");
    }
    public static String normalizedCallerText(String text, String description) {
        String raw = text == null ? "" : text.trim();
        if (raw.isEmpty() && description != null) {
            int cut = description.indexOf(':');
            if (cut > 0 && cut < 25) raw = description.substring(cut + 1).trim();
        }
        if (raw.length() < 2 || raw.length() > 400) return "";
        return raw;
    }
    public static boolean safeSend(String viewId, String label) {
        String id = normalize(viewId);
        String s = normalize(label);
        // Strictly require Samsung in-call UI's native send button, never a generic click.
        boolean knownButton = id.startsWith("com.samsung.android.incallui:id/")
                && (id.endsWith("/send_button") || id.endsWith("/btn_send")
                || id.endsWith("/send_message_button") || id.endsWith("/message_send_button"));
        return knownButton && (s.isEmpty() || "send".equals(s) || "send message".equals(s));
    }
    public static String category(String text) {
        String t = normalize(text);
        if (any(t,"emergency","ambulance","accident","urgent","जरूरी","इमरजेंसी")) return "Urgent";
        if (any(t,"meeting","appointment","booking","मिलना","समय","अपॉइंटमेंट")) return "Meeting";
        if (any(t,"price","rate","cost","charge","fees","कितना","कीमत")) return "Pricing";
        if (any(t,"callback","call back","wapas","वापस","फिर कॉल")) return "Callback";
        if (any(t,"services","business","service","work","काम","सेवा")) return "Service enquiry";
        return "General enquiry";
    }
    public static String followUp(String category) {
        if ("Urgent".equals(category)) return "Review promptly and contact the caller yourself.";
        if ("Meeting".equals(category)) return "Confirm the requested meeting details personally.";
        if ("Pricing".equals(category)) return "Check a verified quote before replying.";
        if ("Callback".equals(category)) return "Decide whether to call back; no callback was promised.";
        if ("Service enquiry".equals(category)) return "Review the service enquiry and respond.";
        return "Review the call and decide whether follow-up is needed.";
    }
    private static String normalize(String s) { return s == null ? "" : s.trim().toLowerCase(Locale.ROOT); }
    private static boolean any(String t,String... keys) {
        for (String key: keys) if (t.contains(key)) return true;
        return false;
    }
}