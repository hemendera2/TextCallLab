import in.textcall.lab.CallTurnGuard;

public final class TestCallTurnGuard {
  private static void yes(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }
  public static void main(String[] args) {
    yes(CallTurnGuard.isIncoming("",
            "Caller: I need a meeting"), "Caller label missed");
    yes(CallTurnGuard.isIncoming("com.samsung.android.incallui:id/incoming_message_text", ""),
            "Known incoming ID missed");
    yes(!CallTurnGuard.isIncoming("", "You: Sent"), "Own speech falsely classified");
    yes(!CallTurnGuard.isIncoming("com.samsung.android.incallui:id/message_text", ""),
            "Ambiguous chat text falsely accepted");
    yes(CallTurnGuard.isOutgoing("", "Assistant: Hi"), "Own reply flag missing");
    yes(CallTurnGuard.safeSend("com.samsung.android.incallui:id/send_button", "Send"),
            "Whitelisted Samsung button rejected");
    yes(!CallTurnGuard.safeSend("com.some.unknown.app:id/send_button","Send"),
            "Foreign app action allowed");
    yes(!CallTurnGuard.safeSend("com.samsung.android.incallui:id/other_button","Send"),
            "Ambiguous Samsung button allowed");
    yes(!CallTurnGuard.safeSend("com.samsung.android.incallui:id/send_button","Delete"),
            "Dangerous label allowed");
    yes(CallTurnGuard.normalizedCallerText("hello","Caller: hello").equals("hello"),
            "Normalized caller failed");
    yes(CallTurnGuard.normalizedCallerText("", "Caller: Hello there").equals("Hello there"),
            "Description transcription missing");
    yes(CallTurnGuard.normalizedCallerText("x","").isEmpty(), "Very short text accepted");
    yes(CallTurnGuard.category("urgent emergency") .equals("Urgent"), "Urgency missed");
    yes(CallTurnGuard.category("meeting tomorrow").equals("Meeting"), "Meeting missed");
    yes(CallTurnGuard.followUp("Meeting").contains("Confirm"), "Next action missed");
    System.out.println("PASS: 15 conservative Samsung call-role/response safety tests");
  }
}