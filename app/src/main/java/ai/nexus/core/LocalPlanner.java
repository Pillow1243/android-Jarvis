package ai.nexus.core;

import java.util.*;
import java.util.regex.*;

/** All-or-nothing local grammar. Unrecognized clauses get sent to the planner, not silently dropped. */
public final class LocalPlanner {

  private static Matcher match(String p, String s) {
    Matcher m = Pattern.compile(p, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE).matcher(s);
    return m.matches() ? m : null;
  }

  private static final String[][] PANELS = {
    { "wifi", "wi-fi", "wifi", "وای فای", "وای‌فای" },
    { "bluetooth", "bluetooth", "بلوتوث" },
    { "location", "location", "مکان" },
    { "display", "display", "نمایشگر" },
    { "sound", "sound", "صدا" },
    { "notifications", "notification", "اعلان" },
    { "battery", "battery", "باتری" },
    { "apps", "installed apps", "برنامه ها" },
    { "dnd", "do not disturb", "مزاحم نشو" },
    { "internet", "internet panel", "پنل اینترنت" },
    { "volume", "volume panel", "پنل صدا" },
  };

  public List<Call> plan(String raw, String lastApp) {
    String s = raw.trim().replaceFirst("(?iu)^(?:jarvis|nexus|جرویس|جارویس|نکسوس)[،,:! ]+", "");
    // A message, note, query, or reminder body may itself contain conjunctions. Preserve it verbatim.
    Matcher guard = match("if (.+?) is installed,? (?:then )?open (?:it|that app)", s);
    if (guard != null) {
      String name = guard.group(1);
      return Arrays.asList(new Call("find_app", "name", name), new Call("launch_app", "name", name));
    }
    Call atomic = content(s);
    if (atomic != null) return Collections.singletonList(atomic);
    String[] parts = s.split(
      "(?iu)\\s*(?:[;؛]|,?\\s+and then\\s+|,?\\s+then\\s+|\\s+and\\s+(?=(?:open|list|show|check|search)\\b)|[،,]?\\s*(?:و بعد|سپس|بعدش)\\s+|[،,]\\s*(?=سایت|برنامه|تنظیمات|open )|\\s+و\\s+(?=عبارت|سایت|برنامه|تنظیمات))\\s*",
      -1
    );
    if (parts.length > 8) return Collections.emptyList();
    List<Call> result = new ArrayList<>();
    for (String part : parts) {
      Call c = one(part.trim(), lastApp);
      if (c == null) return Collections.emptyList();
      result.add(c);
      if (c.name.equals("launch_app")) lastApp = c.get("name");
    }
    return result;
  }

  private Call content(String s) {
    Matcher m;
    if (
      (m = match("(?:text|message|sms) (.+?)(?::| saying | that )\\s*(.+)", s)) != null
    ) return new Call("compose_sms", "recipient", m.group(1), "text", m.group(2));
    if ((m = match("به (.+?) (?:پیام|پیامک) بده (?:که )?(.+)", s)) != null) return new Call(
      "compose_sms",
      "recipient",
      m.group(1),
      "text",
      m.group(2)
    );
    if (
      (m = match("(?:remember|save note|یادت باشه|به خاطر بسپار)[: ]+(.+)", s)) != null
    ) return new Call("save_note", "text", m.group(1));
    if ((m = match("(?:remind me at|ساعت) (\\S+)(?: یادآوری کن)? (?:to |که )?(.+)", s)) != null) {
      String at = Text.digits(m.group(1));
      if (at.equals("هفت")) at = "7";
      if (at.matches("\\d{1,2}")) at += ":00";
      if (at.matches("\\d{1,2}:\\d{2}")) return new Call("set_reminder", "time", at, "text", m.group(2));
    }
    return null;
  }

  private Call one(String s, String lastApp) {
    String n = Text.norm(s).replaceFirst("[.!؟]+$", "");
    Matcher m;
    Call c = content(s);
    if (c != null) return c;
    s = s.replaceFirst("[.!؟]+$", "").trim();
    if (n.matches("(?:return to|open) (?:the )?previous app|برگرد به برنامه قبلی")) return new Call(
      "previous_app"
    );
    if ((m = match("(?:is (.+?) installed[?]?|check app (.+))", s)) != null) return new Call(
      "find_app",
      "name",
      m.group(1) == null ? m.group(2) : m.group(1)
    );
    if (
      n.matches(
        "(?:device (?:status|report)|(?:show |check )?(?:my )?(?:device|system) (?:status|report)|گزارش دستگاه|وضعیت (?:دستگاه|سیستم|گوشی))"
      )
    ) return new Call("device_report");
    if (
      n.matches(
        "(?:list|show)(?: (?:installed|my|launchable))? (?:apps|applications)|app registry|فهرست برنامه ها|لیست برنامه ها|برنامه ها(?: رو)? (?:نشان بده|نشون بده)"
      )
    ) return new Call("list_apps");
    if (
      n.matches("(?:open (?:it|that app)|اون برنامه رو باز کن|همون برنامه رو باز کن)")
    ) return lastApp.isEmpty() ? null : new Call("launch_app", "name", lastApp);
    if ((m = match("(?:recall note|find my note|یادداشت من درباره) (.+)", s)) != null) return new Call(
      "recall_note",
      "query",
      m.group(1)
    );
    if (
      (m = match("(?:call|dial) (.+)", s)) != null || (m = match("به (.+?) زنگ بزن", s)) != null
    ) return new Call("dial_contact", "recipient", m.group(1));
    if ((m = match("(?:find contact|مخاطب) (.+)", s)) != null) return new Call(
      "find_contact",
      "name",
      m.group(1)
    );
    if (
      (m = match("(?:search (?:youtube|یوتیوب) for|در یوتیوب جستجو کن) (.+)", s)) != null ||
      (m = match("عبارت (.+?) (?:رو |را )?جست[‌ ]?وجو کن", s)) != null
    ) return new Call("web_search", "engine", "youtube", "query", m.group(1));
    if ((m = match("(?:search (?:the web )?for|جستجو کن) (.+)", s)) != null) return new Call(
      "web_search",
      "engine",
      "web",
      "query",
      m.group(1)
    );
    if ((m = match("(?:route to|directions to|مسیر) (.+)", s)) != null) return new Call(
      "maps",
      "mode",
      "route",
      "place",
      m.group(1)
    );
    if ((m = match("(?:find on maps|نقشه) (.+)", s)) != null) return new Call(
      "maps",
      "mode",
      "search",
      "place",
      m.group(1)
    );
    for (String[] p : PANELS)
      for (int i = 1; i < p.length; i++) {
        String v = Pattern.quote(Text.norm(p[i]));
        if (
          n.matches(
            "(?:open )?" +
              v +
              "(?: settings)?|(?:برو داخل |باز کن )?(?:تنظیمات |پنل )?" +
              v +
              "(?: (?:رو |را )?باز کن)?"
          )
        ) return new Call("open_panel", "panel", p[0]);
      }
    if (
      (m = match("(?:open|launch|start) (?:my |the )?(.+)", s)) != null ||
      (m = match("(?:برنامه )?(.+?) (?:رو |را )?باز کن", s)) != null
    ) {
      String name = m.group(1).trim(),
        a = Text.norm(name);
      String[][] sys = {
        { "camera", "camera", "دوربین" },
        { "gallery", "gallery", "گالری" },
        { "contacts", "contacts", "مخاطبین", "مخاطبان" },
        { "dialer", "dialer", "شماره گیر" },
        { "browser", "browser", "مرورگر" },
        { "files", "files", "فایل ها" },
      };
      for (String[] line : sys)
        for (int i = 1; i < line.length; i++) if (a.equals(line[i])) return new Call(
          "open_interface",
          "surface",
          line[0]
        );
      if (a.equals("سایت یوتیوب") || a.equals("youtube.com")) return new Call(
        "open_url",
        "url",
        "https://www.youtube.com"
      );
      if (name.startsWith("https://")) return new Call("open_url", "url", name);
      return new Call("launch_app", "name", name);
    }
    return null;
  }
}
