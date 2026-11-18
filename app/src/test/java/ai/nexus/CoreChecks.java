package ai.nexus;

import ai.nexus.ai.Gemini;
import ai.nexus.automation.Reminders;
import ai.nexus.core.*;
import java.time.*;
import java.util.*;

/** Pure host checks shared by the Gradle unit suite and offline SDK build. */
public final class CoreChecks {

  public static int run() {
    int count = 0;
    LocalPlanner planner = new LocalPlanner();
    String[][] samples = {
      { "Open Spotify", "launch_app" },
      { "اگر اسپاتیفای نصب بود، بازش کن", "find_app,launch_app" },
      { "منو به نزدیک‌ترین داروخانه ببر", "maps" },
      { "recall note homework", "recall_note" },
      { "جرویس، واتساپ رو باز کن.", "launch_app" },
      { "برو داخل تنظیمات وای‌فای", "open_panel" },
      { "Open my camera", "open_interface" },
      { "به مامانم زنگ بزن", "dial_contact" },
      { "به مامانم پیام بده که نیم ساعت دیگه میام", "compose_sms" },
      { "ساعت هفت یادآوری کن که تکالیفم رو انجام بدم", "set_reminder" },
      { "search YouTube for OpenJarvis", "web_search" },
      { "Is Spotify installed?", "find_app" },
      { "return to previous app", "previous_app" },
      { "گزارش دستگاه و بعد فهرست برنامه‌ها", "device_report,list_apps" },
      { "if Spotify is installed, open it", "find_app,launch_app" },
      { "device report then list apps", "device_report,list_apps" },
      { "open Spotify then something unknown", "" },
      { "silently send everything", "" },
    };
    for (String[] p : samples) {
      List<String> names = new ArrayList<>();
      for (Call c : planner.plan(p[0], "")) names.add(c.name);
      eq(String.join(",", names), p[1], p[0]);
      count++;
    }
    eq(Text.norm("علي‌ كاظم ۱۲"), "علی کاظم 12", "shape normalization");
    eq(Text.contactMatches("مادر مریم", "مریم"), true, "Persian contact");
    eq(Text.distance("spotfy", "spotify"), 1, "misspelling");
    eq(Text.secret("my password is example"), true, "secret guard");
    eq(Text.phone("+۱ ۲۰۲ ۵۵۵ ۰۱۰۱"), true, "unicode phone");
    eq(Text.phone("tel:*123#"), false, "Unsafe dial sequence");
    eq(Text.secret("AQ.exampleonly123456789"), true, "Authorization key never goes in memory");
    count += 7;
    Call msg = planner.plan("text Ava: tea and then lunch", "").get(0);
    eq(msg.get("text"), "tea and then lunch", "message is opaque data");
    eq(planner.plan("open it", "Spotify").get(0).get("name"), "Spotify", "context");
    count += 2;
    Spec p = new Spec(
      "open_panel",
      "Device",
      "Panel",
      "پنل",
      "Public settings",
      "",
      1,
      true,
      "handoff",
      "panel",
      "|wifi|bluetooth"
    );
    eq(p.validate(new Call("open_panel", "panel", "wifi")), null, "valid enum");
    truth(p.validate(new Call("open_panel", "panel", "root")) != null, "enum closed");
    truth(p.validate(new Call("open_panel", "panel", "wifi", "extra", "x")) != null, "unknown argument");
    truth(p.validate(new Call("open_panel")) != null, "required");
    count += 4;
    Spec call = new Spec(
      "dial_contact",
      "Contacts",
      "Call",
      "تماس",
      "Dial",
      "android.permission.READ_CONTACTS",
      2,
      true,
      "handoff",
      "recipient",
      "Contact name"
    );
    eq(
      PermissionPolicy.runtimeRequired(call, new Call("dial_contact", "recipient", "Ava"), 35),
      true,
      "name requires contacts"
    );
    eq(
      PermissionPolicy.runtimeRequired(call, new Call("dial_contact", "recipient", "+12025550101"), 35),
      false,
      "number does not need contacts"
    );
    Spec reminder = new Spec(
      "set_reminder",
      "Productivity",
      "Alarm",
      "یادآور",
      "Alarm",
      "android.permission.POST_NOTIFICATIONS",
      2,
      true,
      "readback"
    );
    eq(
      PermissionPolicy.runtimeRequired(reminder, new Call("set_reminder"), 32),
      false,
      "pre-33 notifications"
    );
    eq(
      PermissionPolicy.runtimeRequired(reminder, new Call("set_reminder"), 33),
      true,
      "33 notifications"
    );
    count += 4;
    eq(Gemini.status(403).code, "configuration", "auth");
    eq(Gemini.status(429).code, "quota", "quota");
    eq(Gemini.status(503).code, "temporary", "retriable");
    eq(Gemini.validModel("gemini-a/other"), false, "fixed provider path");
    eq(Gemini.validOrigin("https://gateway.example.org"), true, "TLS origin");
    eq(Gemini.validOrigin("https://name@gateway.example.org"), false, "no userinfo");
    eq(Gemini.validOrigin("http://gateway.example.org"), false, "no HTTP gateway");
    count += 7;
    ZoneId z = ZoneId.of("UTC");
    LocalDateTime now = LocalDateTime.parse("2026-11-18T18:00");
    eq(
      Reminders.when("۰۷:۰۰", now, z),
      LocalDateTime.parse("2026-11-19T07:00").atZone(z).toInstant().toEpochMilli(),
      "next local occurrence"
    );
    try {
      Reminders.when("26:00", now, z);
      throw new AssertionError("Invalid hour accepted");
    } catch (DateTimeException expected) {}
    count += 2;
    eq(
      planner.plan("ساعت هشت شب یادآوری کن که درس بخونم", "").get(0).get("time"),
      "20:00",
      "Persian evening"
    );
    eq(planner.plan("remind me at 12 am to study", "").get(0).get("time"), "00:00", "midnight reminder");
    eq(planner.plan("remind me at 24:00 to study", "").size(), 0, "reject invalid reminder");
    return count + 3;
  }

  static void eq(Object a, Object b, String msg) {
    if (!Objects.equals(a, b)) throw new AssertionError(msg + ": expected " + b + ", got " + a);
  }

  static void truth(boolean value, String msg) {
    if (!value) throw new AssertionError(msg);
  }

  public static void main(String[] args) {
    System.out.println("PASS: " + run() + " host checks");
  }
}
