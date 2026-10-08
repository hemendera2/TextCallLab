import in.textcall.lab.FastReply;
import java.util.Arrays;
import java.util.Collections;
public class TestFastReply {
    private static void ok(boolean yes, String why) {
        if(!yes) throw new AssertionError(why);
    }
    public static void main(String[] args) {
        ok(FastReply.respond("Namaste","Owner",Collections.emptyList()).contains("सेक्रेटरी"), "greeting");
        ok(FastReply.respond("Kal meeting chahiye","Owner",Collections.emptyList()).contains("कन्फर्म"), "meeting");
        ok(FastReply.respond("OTP share karu?","Owner",Collections.emptyList()).contains("ओटीपी"), "OTP");
        ok(FastReply.respond("Accident emergency hai","Owner",Collections.emptyList()).contains("आपातकाल"), "emergency");
        ok(FastReply.respond("Tell me a detailed investment strategy","Owner",Collections.emptyList()).isEmpty(), "no fabricated advice");
        ok(FastReply.respond("hi","Owner",Arrays.asList("Caller: Hello","Assistant: Welcome")).contains("listening"), "turn carryover");
        ok(FastReply.respond("appointment","Owner",Collections.emptyList()).contains("confirm"), "unconfirmed booking");
        ok(FastReply.respond("लागत की कीमत?","Owner",Collections.emptyList()).contains("कीमत"),"hindi price");
        ok(FastReply.respond("कल मिलने का समय बताइए","Owner",Collections.emptyList()).contains("किस दिन"),"native Hindi meeting");
        ok(FastReply.respond("Mujhe kal milna hai","Owner",Collections.emptyList()).contains("किस दिन"),"Hinglish meeting");
        ok(FastReply.respond("price please","Owner",Collections.emptyList()).contains("verified"),"English pricing");
        System.out.println("PASS: 11 Hindi/Hinglish/English instant caller intent safety checks");
    }
}