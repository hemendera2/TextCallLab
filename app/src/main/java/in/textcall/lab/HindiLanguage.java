package in.textcall.lab;

import java.util.Locale;

/** Hindi-first routing for local speech/text (heuristic; does not certify ASR). */
public final class HindiLanguage {
    private HindiLanguage() { }
    private static final String[] HINGLISH = {
        "aap","apka","aapka","mujhe","mujhse","mera","meri","hum","ham","kya",
        "kaise","kab","kahan","kidhar","kaun","kaunsa","batao","bataiye","batana",
        "chahiye","chahie","milna","milega","kitna","kitne","kitni","paise",
        "kal","aaj","parso","karna","karo","karein","karoge","karni","haan",
        "nahi","nahin","thik","theek","theekhai","ji","bolna","boliye","suno",
        "shaam","subah","baat","ho","hai","hain","hoga","hainji","se","ko",
        "office","namaste"
    };
    public static boolean likelyHindi(String message) {
        if (message == null || message.isEmpty()) return false;
        for (int i=0;i<message.length();i++) {
            char ch=message.charAt(i);
            if (ch >= '\u0900' && ch <= '\u097F') return true;
        }
        String t=message.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z ]"," ").trim();
        if (t.isEmpty()) return false;
        String words=" "+t.replaceAll("\\s+"," ")+" ";
        int matches=0;
        for(String word:HINGLISH) {
            if (words.contains(" "+word+" ")) matches++;
        }
        if(words.matches(".* (namaste|bataiye|chahiye|mujhe|aapka|milega) .*")) return true;
        return matches>=2;
    }
    public static String replyStyle(String message) {
        return likelyHindi(message)
                ? "Reply in natural Hindi using Devanagari script; understand Hindi, Hinglish and local Indian speech. Keep common English proper nouns unchanged."
                : "If the caller uses English, reply in short simple English. Otherwise favor Hindi.";
    }
}
