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
            answer = "Security ke liye OTP, PIN, password ya banking details mat batayein. Main yeh information collect nahi kar sakta.";
            topic = "security";
        } else if (any(t, "emergency", "ambulance", "accident", "heart attack",
                "medical emergency", "आकस्मिक", "इमरजेंसी")) {
            answer = "Yeh emergency lagti hai. Kripya turant local emergency service ko call karein; main emergency assistance dispatch nahi kar sakta.";
            topic = "emergency";
        } else if (any(t, "bye", "goodbye", "thank you", "shukriya", "धन्यवाद", "अलविदा")) {
            answer = "Samay dene ke liye dhanyavaad. Namaste.";
            topic = "closing";
        } else if (any(t, "price", "cost", "kitna", "rate", "charge", "fees", "pricing",
                "कीमत", "कितना", "कितने", "पैसे")) {
            topic = "price";
            answer = publicFacts.isEmpty()
                    ? "Kis service ya kaam ki price pooch rahe hain? Main bina verified rate ke amount nahi bataunga."
                    : "Owner ne yeh public details di hain: " + publicFacts + ". Aapko kis service ka exact quotation chahiye?";
        } else if (any(t, "appointment", "meeting", "milna", "booking", "schedule", "time slot",
                "मिलना", "अपॉइंटमेंट", "बुकिंग", "समय")) {
            topic = "meeting";
            answer = count % 2 == 0
                    ? "Meeting kis din aur kis samay rakhna chahenge? Main abhi ise confirm nahi kar sakta."
                    : "Zaroor. Aap date aur convenient time batayein. Final booking owner ki confirmation ke baad hogi.";
        } else if (any(t, "callback", "call back", "wapas call", "baad mein", "वापस फोन",
                "फिर कॉल")) {
            topic = "callback";
            answer = "Aapka callback request samajh gaya. Yeh demo kisi ko automatically notify nahi karta; kripya owner ko alag se message bhej dein.";
        } else if (any(t, "who are you", "kaun bol", "robot", "are you ai", "assistant",
                "कौन बोल", "आप कौन", "एआई")) {
            topic = "identity";
            answer = "Main " + owner + " ka automated voice assistant hoon, insaan nahi. Aap kis baare mein baat karna chahenge?";
        } else if (any(t, "work", "business", "services", "service", "kaam", "details",
                "जानकारी", "बिजनेस", "सेवा")) {
            topic = "business";
            answer = publicFacts.isEmpty()
                    ? "Aap kis tarah ki service ke baare mein jaana chahenge?"
                    : "Yeh verified public information owner ne di hai: " + publicFacts + ". Kya aur koi sawaal hai?";
        } else if (any(t, "yes", "haan", "ha", "ji", "ठीक", "हाँ", "हां") && "meeting".equals(topic)) {
            answer = "Achha. Aap exact date aur preferred time bata sakte hain? Main booking confirm nahi kar sakta.";
        } else if (any(t, "yes", "haan", "ha", "ji", "हाँ", "हां") && "price".equals(topic)) {
            answer = "Kis product ya service ki cost jaana chahte hain?";
        } else if (any(t, "hello", "hi ", "hey", "namaste", "good morning", "hallo",
                "नमस्ते", "हैलो")) {
            answer = count == 1
                    ? "Namaste. Main " + owner + " ka automated voice assistant hoon. Aapko kis baat mein help chahiye?"
                    : "Ji, main sun raha hoon. Aap apna sawaal batayein.";
            topic = "general";
        } else if ("meeting".equals(topic)) {
            answer = "Aapki meeting ki preference samajh raha hoon. Kripya date aur time dobara clear kar dein; booking confirm nahi hui hai.";
        } else if ("price".equals(topic)) {
            answer = "Theek hai. Main bina verified quotation ke amount nahi bataunga. Kis service ki baat ho rahi hai?";
        } else if (!specialInstruction.isEmpty() && any(t, "instruction", "rule",
                "guideline", "bataya", "निर्देश")) {
            answer = "Owner ne assistant ke liye kuch rules set kiye hain. Main sensitive ya private instructions caller ko disclose nahi karta.";
        } else {
            answer = count % 3 == 0
                    ? "Samajh raha hoon. Kya aap ise thoda aur specific kar sakte hain?"
                    : count % 3 == 1
                    ? "Ji, aapki baat suni. Aapka main sawaal ya requirement kya hai?"
                    : "Theek hai. Mujhe ek-do aur details de dijiye, taaki galat information na doon.";
        }
        add("Caller", spoken);
        add("Assistant", answer);
        return answer;
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
