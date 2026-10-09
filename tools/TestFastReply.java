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
        ok(FastReply.respond("kal 5 baje","Owner",
                Arrays.asList("Caller: appointment chahiye",
                              "Assistant: किस दिन और कितने बजे मिलना चाहेंगे? अभी मैं बुकिंग कन्फर्म नहीं कर सकता।"))
                .contains("कन्फर्म नहीं"),"context date continuation");
        ok(FastReply.respond("tomorrow 5pm","Owner",
                Arrays.asList("Caller: appointment",
                              "Assistant: What day and time would you prefer? I cannot confirm an appointment yet."))
                .contains("not confirmed"),"English date continuation");
        ok(FastReply.respond("tomorrow 5pm","Owner",Collections.emptyList()).isEmpty(),
                "Never invent a previous appointment topic");
        ok(FastReply.timeoutFallback("Please help").contains("cannot answer"),"English timeout");
        ok(FastReply.timeoutFallback("mujhe help karo").contains("सही जवाब"),"Hindi timeout");
        ok(!FastReply.timeoutFallback("kal").contains("बुकिंग कन्फर्म"),"Never claim booked on timeout");
        System.out.println("PASS: 17 Hindi/Hinglish/English instant caller intent and fallback safety checks");
    }
}