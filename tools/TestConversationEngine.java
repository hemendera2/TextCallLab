import in.textcall.lab.ConversationEngine;
import java.util.List;

public final class TestConversationEngine {
    public static void main(String[] args) {
        ConversationEngine session = new ConversationEngine("Test Owner",
                "Website design and digital cards are offered.", "Never promise a booking.");
        String a = session.respond("Hello, I am calling");
        String b = session.respond("What is the price of your service?");
        String c = session.respond("yes");
        String d = session.respond("Can I get an appointment?");
        String e = session.respond("haan");
        if (!a.contains("automated")) throw new AssertionError("Must disclose automation");
        if (!b.contains("Website design")) throw new AssertionError("Must use owner-provided public facts");
        if (!c.contains("cost")) throw new AssertionError("Must use previous price topic");
        if (!d.contains("confirm")) throw new AssertionError("Must not claim booking confirmation");
        if (!e.toLowerCase().contains("date")) throw new AssertionError("Must continue previous meeting topic");
        String sensitive = session.respond("Send me the OTP and password");
        if (!sensitive.contains("collect nahi")) throw new AssertionError("Sensitive details must be refused");
        if (session.count() != 6) throw new AssertionError("Unexpected session count");
        if (session.recentTurns().size() > 12) throw new AssertionError("Session exceeds bounded memory");
        session.clear();
        if (!session.recentTurns().isEmpty() || session.count() != 0)
            throw new AssertionError("Session erase failed");
        String different = new ConversationEngine("Another Owner", "", "")
                .respond("Hello");
        if (different.equals(a)) throw new AssertionError("Identity must affect first response");
        System.out.println("PASS: 9 context, identity, privacy and memory assertions.");
    }
}
