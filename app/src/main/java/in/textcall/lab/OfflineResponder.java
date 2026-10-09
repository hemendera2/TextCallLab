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
            return "कृपया ओटीपी, पिन या पासवर्ड साझा न करें। मैं यह जानकारी नहीं लेता।";
        }
        if (containsAny(t, "emergency", "ambulance", "accident", "hospital emergency", "urgent help", "इमरजेंसी")) {
            return "अगर आपात स्थिति है, तो तुरंत स्थानीय आपातकालीन सेवा से संपर्क करें। मैं सहायता भेज नहीं सकता।";
        }
        if (containsAny(t, "who are you", "are you ai", "robot", "assistant", "कौन बोल", "kaun bol")) {
            return "मैं " + identity + " का ऑटोमेटेड एआई असिस्टेंट बोल रहा हूँ। आप क्या संदेश देना चाहेंगे?";
        }
        if (containsAny(t, "hello", "hi ", "hey", "namaste", "good morning", "नमस्ते", "हैलो")) {
            return "नमस्ते। मैं " + identity + " का ऑटोमेटेड असिस्टेंट हूँ। बताइए, किस काम से फ़ोन किया?";
        }
        if (containsAny(t, "appointment", "meeting", "schedule", "booking", "milna", "समय", "मिलना")) {
            return "बैठक के लिए अपना नाम, तारीख़ और सुविधाजनक समय बताएं। मैं अभी बुकिंग की पुष्टि नहीं कर सकता।";
        }
        if (containsAny(t, "price", "cost", "charge", "fees", "kitna", "rate", "कीमत", "कितने")) {
            return "मेरे पास अभी पुष्टि की हुई कीमत नहीं है। आप किस सेवा के बारे में पूछ रहे हैं?";
        }
        if (containsAny(t, "call back", "callback", "wapas call", "later", "baad mein", "वापस फोन")) {
            return "ठीक है। कृपया अपना नाम और कॉल करने का कारण बताएं। मैं कॉल-बैक का वादा नहीं कर सकता।";
        }
        if (containsAny(t, "bye", "goodbye", "thank you", "thanks", "shukriya", "धन्यवाद")) {
            return "फ़ोन करने के लिए धन्यवाद। नमस्ते।";
        }
        if (!context.isEmpty() && containsAny(t, "business", "service", "work", "kaam", "details", "जानकारी")) {
            String contextShort = context.length() > 140 ? context.substring(0, 140) : context;
            return "यह मालिक की दी हुई जानकारी है: " + contextShort + "। आप क्या पूछना चाहते हैं?";
        }
        return "जी, मैं सुन रहा हूँ। क्या आप अपनी बात थोड़ी और विस्तार से बता सकते हैं?";
    }

    private static boolean containsAny(String target, String... options) {
        for (String option : options) if (target.contains(option)) return true;
        return false;
    }
}
