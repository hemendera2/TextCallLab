import in.textcall.lab.FastReply;
import java.util.Arrays;
import java.util.Collections;
public class TestFastReply {
    private static void ok(boolean yes, String why) {
        if(!yes) throw new AssertionError(why);
    }
    public static void main(String[] args) {
        ok(FastReply.respond("Namaste","Owner",Collections.emptyList()).contains("secretary"), "greeting");
        ok(FastReply.respond("Kal meeting chahiye","Owner",Collections.emptyList()).contains("confirm"), "meeting");
        ok(FastReply.respond("OTP share karu?","Owner",Collections.emptyList()).contains("OTP"), "OTP");
        ok(FastReply.respond("Accident emergency hai","Owner",Collections.emptyList()).contains("emergency"), "emergency");
        ok(FastReply.respond("Tell me a detailed investment strategy","Owner",Collections.emptyList()).isEmpty(), "no fabricated advice");
        ok(FastReply.respond("hi","Owner",Arrays.asList("Caller: Hello","Assistant: Welcome")).contains("sun"), "turn carryover");
        ok(FastReply.respond("appointment","Owner",Collections.emptyList()).contains("booking"), "unconfirmed booking");
        ok(FastReply.respond("लागत की कीमत?","Owner",Collections.emptyList()).contains("कीमत"),"hindi price");
        System.out.println("PASS: 8 instant caller intent safety and abstention checks");
    }
}