package in.textcall.lab;

import java.util.List;
import java.util.Locale;

/** Ultra-low-latency, deterministic first responder for clear routine intents.
 * Empty means the caller needs the real generative model; no fake AI replies.
 * No IO or network. Rules never execute payments/bookings/calls.
 */
public final class FastReply {
    private FastReply() {}
    public static String respond(String spoken, String owner, List<String> history) {
        if (spoken == null) return "";
        String t = spoken.toLowerCase(Locale.ROOT).trim();
        if (t.length() < 2) return "";
        boolean hindi = t.matches(".*[\\u0900-\\u097f].*");
        if (any(t,"otp","password","pin number","cvv","ओटीपी","पासवर्ड","पिन बताओ")) {
            return hindi
                ? "कृपया ओटीपी, पिन या बैंक की निजी जानकारी साझा न करें।"
                : "Kripya OTP, PIN ya bank ki private details share mat kijiye.";
        }
        if (any(t,"emergency","ambulance","heart attack","accident","इमरजेंसी","एम्बुलेंस","दुर्घटना")) {
            return hindi
                ? "यह आपातकाल हो सकता है। कृपया तुरंत स्थानीय इमरजेंसी नंबर पर कॉल करें।"
                : "Yeh emergency ho sakti hai. Kripya turant local emergency service ko call karein.";
        }
        if (any(t,"meeting","appointment","booking","milna","mulaqat","मुलाकात","मीटिंग","अपॉइंटमेंट")) {
            return hindi
                ? "किस दिन और कितने बजे मिलना चाहेंगे? अभी मैं बुकिंग कन्फर्म नहीं कर सकता।"
                : "Ji, kis din aur kitne baje milna chahenge? Main abhi booking confirm nahi kar sakta.";
        }
        if (any(t,"price","charges","rate kya","kitne paise","fees","कितने पैसे","कीमत","रेट क्या")) {
            return hindi
                ? "किस सर्विस की कीमत पूछ रहे हैं? बिना पक्की जानकारी के रेट नहीं बता सकता।"
                : "Kis service ka rate pooch rahe hain? Main bina verified price ke amount nahi bataunga.";
        }
        if (any(t,"callback","call back","wapas call","वापस कॉल","फिर फोन")) {
            return hindi
                ? "कृपया अपनी रिक्वेस्ट बता दें। कॉल-बैक का वादा मालिक की मंजूरी के बिना नहीं कर सकता।"
                : "Ji, apni request bataiye. Main bina owner confirmation callback promise nahi kar sakta.";
        }
        if (any(t,"kaun bol","who are you","aap kaun","कौन बोल","आप कौन")) {
            String person = owner == null || owner.trim().isEmpty() ? "owner" : owner.trim();
            if (person.length() > 50) person=person.substring(0,50);
            return hindi
                ? "मैं एक ऑटोमेटेड एआई सेक्रेटरी हूँ। आप किस बारे में बात करना चाहते हैं?"
                : "Main " + person + " ka automated AI secretary hoon. Batayiye kis kaam se call kiya?";
        }
        // Don't intercept complex greetings with meaningful additional requests.
        if (t.matches("^(namaste|hello|hi|hey|नमस्ते|हैलो)[!.,? ]*$")) {
            int prior = history == null ? 0 : history.size();
            return hindi
                ? (prior>0?"जी, मैं सुन रहा हूँ। बताइए।":"नमस्ते! मैं एआई सेक्रेटरी हूँ। कैसे मदद करूँ?")
                : (prior>0?"Ji, main sun raha hoon. Batayiye.":"Namaste! Main AI secretary hoon. Kaisi madad kar sakta hoon?");
        }
        return "";
    }
    private static boolean any(String t,String... choices) {
        for(String c:choices) if(t.contains(c)) return true;
        return false;
    }
}