package in.textcall.lab;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Memory-only, multi-turn dialogue prototype. This is deliberately NOT an LLM.
 * It uses different replies depending on current topic, previous question, and
 * owner-provided public information. Nothing is written to disk or transmitted.
 */
public final class ConversationEngine {
    private final List<String> turns = new ArrayList<>();
    private String topic = "";
    private int count = 0;
    private final String owner;
    private final String publicFacts;
    private final String specialInstruction;
    public ConversationEngine(String owner, String publicFacts, String specialInstruction) {
        this.owner = limit(owner, 50, "the owner");
        this.publicFacts = limit(publicFacts, 400, "");
        this.specialInstruction = limit(specialInstruction, 280, "");
    }

    public String respond(String utterance) {
        String spoken = limit(utterance, 800, "").trim();
        if (spoken.isEmpty()) return "";
        count++;
        String t = spoken.toLowerCase(Locale.ROOT);
        String answer;
        if (any(t, "otp", "one time password", "pin", "password", "cvv",
                "bank account", "aadhar", "aadhaar", "ओटीपी", "पासवर्ड", "पिन")) {
            answer = "कृपया ओटीपी, पिन, पासवर्ड या बैंक की निजी जानकारी साझा न करें। मैं यह जानकारी नहीं लेता।";
            topic = "security";
        } else if (any(t, "emergency", "ambulance", "accident", "heart attack",
                "medical emergency", "आकस्मिक", "इमरजेंसी")) {
            answer = "यह आपात स्थिति हो सकती है। कृपया तुरंत स्थानीय आपातकालीन सेवा को फ़ोन करें। मैं सहायता भेज नहीं सकता।";
            topic = "emergency";
        } else if (any(t, "bye", "goodbye", "thank you", "shukriya", "धन्यवाद", "अलविदा")) {
            answer = "समय देने के लिए धन्यवाद। नमस्ते।";
            topic = "closing";
        } else if (any(t, "price", "cost", "kitna", "rate", "charge", "fees", "pricing",
                "कीमत", "कितना", "कितने", "पैसे")) {
            topic = "price";
            answer = publicFacts.isEmpty()
                    ? "आप किस सेवा की कीमत पूछ रहे हैं? मैं बिना पुष्टि के राशि नहीं बता सकता।"
                    : "मालिक ने यह सार्वजनिक जानकारी दी है: " + publicFacts + "। आप किस सेवा का सही मूल्य जानना चाहते हैं?";
        } else if (any(t, "appointment", "meeting", "milna", "booking", "schedule", "time slot",
                "मिलना", "अपॉइंटमेंट", "बुकिंग", "समय")) {
            topic = "meeting";
            answer = count % 2 == 0
                    ? "आप किस दिन और कितने बजे मिलना चाहेंगे? मैं अभी बैठक की पुष्टि नहीं कर सकता।"
                    : "ज़रूर। आप तारीख़ और सुविधाजनक समय बताएं। अंतिम बुकिंग मालिक की पुष्टि के बाद होगी।";
        } else if (any(t, "callback", "call back", "wapas call", "baad mein", "वापस फोन",
                "फिर कॉल")) {
            topic = "callback";
            answer = "आपके कॉल-बैक अनुरोध को समझा। यह ऐप अभी अपने आप किसी को सूचना नहीं भेजता। कृपया मालिक को सीधे बता दें।";
        } else if (any(t, "who are you", "kaun bol", "robot", "are you ai", "assistant",
                "कौन बोल", "आप कौन", "एआई")) {
            topic = "identity";
            answer = "मैं " + owner + " का ऑटोमेटेड वॉइस असिस्टेंट हूँ, इंसान नहीं। आप किस बारे में बात करना चाहेंगे?";
        } else if (any(t, "work", "business", "services", "service", "kaam", "details",
                "जानकारी", "बिजनेस", "सेवा")) {
            topic = "business";
            answer = publicFacts.isEmpty()
                    ? "आप किस तरह की सेवा के बारे में जानना चाहेंगे?"
                    : "मालिक ने यह पुष्टि की हुई सार्वजनिक जानकारी दी है: " + publicFacts + "। क्या आपका कोई और सवाल है?";
        } else if (any(t, "yes", "haan", "ha", "ji", "ठीक", "हाँ", "हां") && "meeting".equals(topic)) {
            answer = "अच्छा। आप तारीख़ और समय बता सकते हैं? मैं बुकिंग की पुष्टि नहीं कर सकता।";
        } else if (any(t, "yes", "haan", "ha", "ji", "हाँ", "हां") && "price".equals(topic)) {
            answer = "आप किस उत्पाद या सेवा की कीमत जानना चाहते हैं?";
        } else if (any(t, "hello", "hi ", "hey", "namaste", "good morning", "hallo",
                "नमस्ते", "हैलो")) {
            answer = count == 1
                    ? "नमस्ते। मैं " + owner + " का ऑटोमेटेड वॉइस असिस्टेंट हूँ। आपको किस बात में मदद चाहिए?"
                    : "जी, मैं सुन रहा हूँ। आप अपना सवाल बताएं।";
            topic = "general";
        } else if ("meeting".equals(topic)) {
            answer = "आपकी बैठक का अनुरोध समझा। कृपया तारीख़ और समय दोबारा बताएं। बुकिंग अभी पक्की नहीं है।";
        } else if ("price".equals(topic)) {
            answer = "ठीक है। मैं बिना पुष्टि की कीमत नहीं बताऊँगा। आप किस सेवा के बारे में पूछ रहे हैं?";
        } else if (!specialInstruction.isEmpty() && any(t, "instruction", "rule",
                "guideline", "bataya", "निर्देश")) {
            answer = "मालिक ने असिस्टेंट के लिए कुछ नियम तय किए हैं। मैं निजी निर्देश किसी कॉलर को नहीं बताता।";
        } else {
            answer = count % 3 == 0
                    ? "समझ रहा हूँ। क्या आप अपनी बात थोड़ा और स्पष्ट कर सकते हैं?"
                    : count % 3 == 1
                    ? "जी, आपकी बात सुनी। आपका मुख्य सवाल क्या है?"
                    : "ठीक है। आप एक-दो और बातें बताएं, ताकि मैं गलत जानकारी न दूँ।";
        }
        add("Caller", spoken);
        add("Assistant", answer);
        return answer;
    }

    /** Store only bounded in-memory assistant exchanges after actual inference. */
    public void recordExchange(String caller, String assistant) {
        if (caller == null || assistant == null || caller.trim().isEmpty()
                || assistant.trim().isEmpty()) return;
        count++;
        add("Caller", limit(caller, 400, ""));
        add("Assistant", limit(assistant, 480, ""));
    }
    public List<String> recentTurns() { return new ArrayList<>(turns); }
    public int count() { return count; }
    public void clear() { turns.clear(); count = 0; topic = ""; }

    private void add(String role, String value) {
        turns.add(role + ": " + value);
        if (turns.size() > 12) turns.remove(0);
    }
    private static String limit(String input, int max, String fallback) {
        if (input == null) return fallback;
        String s = input.trim();
        if (s.isEmpty()) return fallback;
        return s.length() > max ? s.substring(0, max) : s;
    }
    private static boolean any(String s, String... terms) {
        for (String term : terms) if (s.contains(term)) return true;
        return false;
    }
}
