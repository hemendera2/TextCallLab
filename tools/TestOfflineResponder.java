// Manual standard-library test of the actual Java responder (no Android SDK required).
import in.textcall.lab.OfflineResponder;
public class TestOfflineResponder {
  public static void main(String[] args) {
    for (String sample : new String[] {"hello", "who are you", "price kya hai", "OTP batao", "appointment chahiye", "bye"}) {
      String result = OfflineResponder.reply(sample, "ABC", "We sell websites");
      if (result == null || result.isEmpty()) throw new RuntimeException("Empty reply for " + sample);
      if (sample.equals("OTP batao") && !result.contains("share mat kijiye")) throw new RuntimeException("Safety rule failed");
      System.out.println(sample + " => " + result);
    }
  }
}
