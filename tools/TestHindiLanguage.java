import in.textcall.lab.HindiLanguage;
public final class TestHindiLanguage {
    private static void yes(boolean ok,String label) { if(!ok)throw new AssertionError(label); }
    public static void main(String[] args) {
        yes(HindiLanguage.likelyHindi("कल मीटिंग किस समय है?"),"Devanagari Hindi");
        yes(HindiLanguage.likelyHindi("mujhe kal meeting karni hai"),"Hinglish");
        yes(HindiLanguage.likelyHindi("kal 5 baje"),"Roman-Hindi appointment time");
        yes(HindiLanguage.likelyHindi("OTP share karu?"),"roman Hindi question");
        yes(HindiLanguage.likelyHindi("accident emergency hai"),"Hinglish emergency");
        yes(HindiLanguage.likelyHindi("namaste"),"roman greeting");
        yes(!HindiLanguage.likelyHindi("what is the appointment fee"),"English");
        yes(!HindiLanguage.likelyHindi("Hello, I need your help"),"English greeting");
        yes(!HindiLanguage.likelyHindi(null),"null");
        yes(HindiLanguage.replyStyle("kal milna hai").contains("Devanagari"),"Hindi TTS output");
        yes(HindiLanguage.replyStyle("Can you call me?").contains("English"),"English output");
        System.out.println("PASS: 11 Hindi/English detection and Hindi speech-style regressions");
    }
}
