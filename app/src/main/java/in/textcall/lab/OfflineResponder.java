package in.textcall.lab;

import java.util.Locale;

/**
 * Fully on-device, deterministic conversation MVP; NOT a generative AI model.
 * No server, API key, network, or hidden online dependency.
 */
public final class OfflineResponder {
    private OfflineResponder() { }

    public static String reply(String userText, String owner, String ownerInstructions) {
        if (userText == null) return "";
        String t = userText.trim().toLowerCase(Locale.ROOT);
        if (t.isEmpty()) return "";
        String identity = owner == null || owner.trim().isEmpty() ? "is number" : owner.trim();
        String context = ownerInstructions == null ? "" : ownerInstructions.trim();

        if (containsAny(t, "otp", "one time password", "password", "pin number", "upi pin", "पासवर्ड", "ओटीपी")) {
            return "Security ke liye OTP, PIN ya password share mat kijiye. Main inhe collect nahi karta.";
        }
        if (containsAny(t, "emergency", "ambulance", "accident", "hospital emergency", "urgent help", "इमरजेंसी")) {
            return "Agar emergency hai, turant local emergency service se sampark kijiye. Main emergency help dispatch nahi kar sakta.";
        }
        if (containsAny(t, "who are you", "are you ai", "robot", "assistant", "कौन बोल", "kaun bol")) {
            return "Main " + identity + " ka automated AI-style assistant bol raha hoon. Aap kya message dena chahenge?";
        }
        if (containsAny(t, "hello", "hi ", "hey", "namaste", "good morning", "नमस्ते", "हैलो")) {
            return "Namaste. Main " + identity + " ka automated assistant hoon. Batayiye, kis kaam se call kiya?";
        }
        if (containsAny(t, "appointment", "meeting", "schedule", "booking", "milna", "समय", "मिलना")) {
            return "Meeting ke liye apna naam, date aur convenient time bata dijiye. Main is call mein detail note kar raha hoon; booking confirm nahi kar sakta.";
        }
        if (containsAny(t, "price", "cost", "charge", "fees", "kitna", "rate", "कीमत", "कितने")) {
            return "Exact price mere paas verified nahi hai. Aap kis service ke baare mein pooch rahe hain?";
        }
        if (containsAny(t, "call back", "callback", "wapas call", "later", "baad mein", "वापस फोन")) {
            return "Theek hai. Aap apna naam aur call ka reason bata dijiye. Main callback ka vaada nahi kar sakta.";
        }
        if (containsAny(t, "bye", "goodbye", "thank you", "thanks", "shukriya", "धन्यवाद")) {
            return "Call karne ke liye shukriya. Namaste.";
        }
        if (!context.isEmpty() && containsAny(t, "business", "service", "work", "kaam", "details", "जानकारी")) {
            String contextShort = context.length() > 140 ? context.substring(0, 140) : context;
            return "Yeh owner ki di hui jaankari hai: " + contextShort + ". Aapko kya poochna hai?";
        }
        return "Ji, main sun raha hoon. Aap thoda aur detail mein bata sakte hain?";
    }

    private static boolean containsAny(String target, String... options) {
        for (String option : options) if (target.contains(option)) return true;
        return false;
    }
}
