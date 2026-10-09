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
        boolean hindi = HindiLanguage.likelyHindi(spoken);
        if (any(t,"otp","password","pin number","cvv","ओटीपी","पासवर्ड","पिन बताओ")) {
            return hindi
                ? "कृपया ओटीपी, पिन या बैंक की निजी जानकारी साझा न करें।"
                : "Please do not share your OTP, PIN or private banking details.";
        }
        if (any(t,"emergency","ambulance","heart attack","accident","इमरजेंसी","एम्बुलेंस","दुर्घटना")) {
            return hindi
                ? "यह आपातकाल हो सकता है। कृपया तुरंत स्थानीय इमरजेंसी नंबर पर कॉल करें।"
                : "This may be an emergency. Please contact local emergency services immediately.";
        }
        // A short date/time continuation needs no repeated LLM prefill.
        // Do not imply that an appointment is actually booked.
        if (awaitingAppointmentTime(history) && hasDateOrTime(t)
                && !any(t,"price","charges","fees","कीमत","कितना शुल्क")) {
            return hindi
                ? "समय नोट किया, लेकिन अपॉइंटमेंट अभी कन्फर्म नहीं है। मालिक की पुष्टि ज़रूरी है।"
                : "I heard your preferred time, but the appointment is not confirmed. The owner must approve.";
        }
        if (any(t,"thank you","thanks","shukriya","dhanyavaad","धन्यवाद","शुक्रिया","bye","goodbye","अलविदा")) {
            return hindi ? "धन्यवाद। नमस्ते।" : "Thank you. Goodbye.";
        }
        if (any(t,"meeting","appointment","booking","milna","mulaqat","मुलाकात","मीटिंग","अपॉइंटमेंट","मिलना","मिलना है","समय बताइए")) {
            return hindi
                ? "किस दिन और कितने बजे मिलना चाहेंगे? अभी मैं बुकिंग कन्फर्म नहीं कर सकता।"
                : "What day and time would you prefer? I cannot confirm an appointment yet.";
        }
        if (any(t,"price","charges","rate kya","kitne paise","fees","कितने पैसे","कीमत","रेट क्या","कितना शुल्क")) {
            return hindi
                ? "किस सर्विस की कीमत पूछ रहे हैं? बिना पक्की जानकारी के रेट नहीं बता सकता।"
                : "Which service do you mean? I don't have a verified price yet.";
        }
        if (any(t,"callback","call back","wapas call","वापस कॉल","फिर फोन")) {
            return hindi
                ? "कृपया अपनी रिक्वेस्ट बता दें। कॉल-बैक का वादा मालिक की मंजूरी के बिना नहीं कर सकता।"
                : "Please tell me your request. I can't promise a callback without the owner's confirmation.";
        }
        if (any(t,"kaun bol","who are you","aap kaun","कौन बोल","आप कौन")) {
            String person = owner == null || owner.trim().isEmpty() ? "owner" : owner.trim();
            if (person.length() > 50) person=person.substring(0,50);
            return hindi
                ? "मैं एक ऑटोमेटेड एआई सेक्रेटरी हूँ। आप किस बारे में बात करना चाहते हैं?"
                : "I'm " + person + "'s automated AI secretary. How can I help?";
        }
        // Don't intercept complex greetings with meaningful additional requests.
        if (t.matches("^(namaste|hello|hi|hey|नमस्ते|हैलो)[!.,? ]*$")) {
            int prior = history == null ? 0 : history.size();
            return hindi
                ? (prior>0?"जी, मैं सुन रहा हूँ। बताइए।":"नमस्ते! मैं एआई सेक्रेटरी हूँ। कैसे मदद करूँ?")
                : (prior>0?"I'm listening. Please go ahead.":"Hello! I'm an automated AI secretary. How can I help?");
        }
        return "";
    }
    /** Conservative answer when LLM is too slow. Not a confirmation or task save. */
    public static String timeoutFallback(String spoken) {
        return HindiLanguage.likelyHindi(spoken)
                ? "माफ़ कीजिए, अभी सही जवाब नहीं दे पा रहा। कृपया अपना सवाल थोड़ा संक्षेप में दोहराएँ।"
                : "Sorry, I cannot answer reliably right now. Could you ask more briefly?";
    }
    private static boolean awaitingAppointmentTime(List<String> history) {
        if (history == null) return false;
        for (int i=history.size()-1; i>=0; i--) {
            String turn=history.get(i);
            if(turn==null || !turn.startsWith("Assistant:"))continue;
            String lower=turn.toLowerCase(Locale.ROOT);
            return any(lower,"किस दिन","कितने बजे","तारीख़ और समय",
                    "what day and time","which date and time","day and time would");
        }
        return false;
    }
    private static boolean hasDateOrTime(String t) {
        return any(t,"कल","आज","परसों","बजे","सुबह","शाम",
                   "सोमवार","मंगलवार","बुधवार","गुरुवार","शुक्रवार","शनिवार","रविवार")
                || t.matches(".*\\b(tomorrow|today|kal|parso|subah|shaam|monday|tuesday|wednesday|thursday|friday|saturday|sunday)\\b.*")
                || t.matches(".*\\b[0-9]{1,2}(:[0-9]{2})?\\s*(am|pm|baje|bje|o'clock)\\b.*");
    }
    private static boolean any(String t,String... choices) {
        for(String c:choices) if(t.contains(c)) return true;
        return false;
    }
}