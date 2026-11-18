package ai.nexus.tools;

import ai.nexus.accessibility.WindowReceiptService;
import ai.nexus.automation.Reminders;
import ai.nexus.core.*;
import ai.nexus.device.*;
import ai.nexus.memory.Store;
import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.net.Uri;
import android.os.*;
import android.provider.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import org.json.*;

/** There is no generic intent, URI scheme, shell, UI-click or arbitrary-component tool. */
public final class ToolBox {

  public interface Invocation {
    Activity activity();
    Store store();
    String tr(String en, String fa);
    void choose(String title, List<String> options, Consumer<Integer> selected);
    void commit(String exactPreview, Runnable operation); // engine enforces risk + one-shot + foreground
    <T> void query(Callable<T> op, Consumer<T> result);
    void finish(Receipt receipt);
  }

  public interface Handler {
    void run(Call c, Invocation h);
  }

  private final Map<String, Spec> registry = new LinkedHashMap<>();
  public final Map<String, Spec> specs = Collections.unmodifiableMap(registry);
  private final Map<String, Handler> handlers = new HashMap<>();

  private void add(Spec s, Handler h) {
    if (registry.put(s.id, s) != null) throw new IllegalStateException("Duplicate tool: " + s.id);
    handlers.put(s.id, h);
  }

  public void execute(Call c, Invocation h) {
    Spec s = specs.get(c.name);
    if (s == null || s.validate(c) != null) {
      h.finish(Receipt.failed("Invalid tool or arguments", "invalid_arguments"));
      return;
    }
    handlers.get(c.name).run(c, h);
  }

  public JSONArray declarations() {
    JSONArray a = new JSONArray();
    for (Spec s : specs.values()) if (s.cloud) a.put(s.declaration());
    return a;
  }

  public ToolBox() {
    add(
      new Spec(
        "device_report",
        "Device",
        "Device report",
        "گزارش دستگاه",
        "Read actual battery level, Android release and device model.",
        "",
        1,
        true,
        "BatteryManager / Build"
      ),
      (c, h) ->
        h.commit("", () -> {
          int battery = h
            .activity()
            .getSystemService(BatteryManager.class)
            .getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
          String b = battery < 0 || battery > 100 ? "—" : battery + "%";
          h.finish(
            Receipt.verified(
              Build.MODEL +
                " · Android " +
                Build.VERSION.RELEASE +
                "\n" +
                h.tr("Battery", "باتری") +
                ": " +
                b,
              "device_read",
              J.obj("android_sdk", Build.VERSION.SDK_INT, "battery", b)
            )
          );
        })
    );
    add(
      new Spec(
        "list_apps",
        "Applications",
        "App registry",
        "فهرست برنامه‌ها",
        "Count launchable activities and show their names locally.",
        "",
        1,
        true,
        "PackageManager query"
      ),
      (c, h) ->
        h.commit("", () ->
          h.query(
            () -> AppIndex.scan(h.activity()),
            apps -> {
              StringBuilder names = new StringBuilder();
              for (int i = 0; i < Math.min(12, apps.size()); i++) {
                if (i > 0) names.append(" · ");
                names.append(apps.get(i).name);
              }
              h.finish(
                Receipt.verified(
                  apps.size() + h.tr(" launchable activities\n", " فعالیت قابل اجرا\n") + names,
                  "registry_read",
                  J.obj("count", apps.size())
                )
              );
            }
          )
        )
    );
    add(
      new Spec(
        "find_app",
        "Applications",
        "Check app availability",
        "بررسی وجود برنامه",
        "Check whether a matching launchable app exists. Return the real match count for conditional planning.",
        "",
        1,
        true,
        "Filtered PackageManager query",
        "name",
        "Application name or alias"
      ),
      (c, h) ->
        h.commit("", () -> {
          String alias = h.store().lookup("app_alias", c.get("name"));
          h.query(
            () -> AppIndex.find(AppIndex.scan(h.activity()), alias.isEmpty() ? c.get("name") : alias),
            list -> {
              if (list.isEmpty()) h.finish(
                Receipt.failed(
                  h.tr(
                    "No matching launchable application is installed.",
                    "برنامهٔ قابل اجرا پیدا نشد."
                  ),
                  "app_not_found"
                )
              );
              else h.finish(
                Receipt.verified(
                  list.size() + h.tr(" installed match(es): ", " نتیجهٔ نصب‌شده: ") + list.get(0).name,
                  "installed_match",
                  J.obj("count", list.size())
                )
              );
            }
          );
        })
    );
    add(
      new Spec(
        "previous_app",
        "Applications",
        "Return to previous task",
        "بازگشت به وظیفهٔ قبلی",
        "Move Nexus behind the previous Android task. Android chooses the visible destination.",
        "",
        1,
        true,
        "ActivityManager moveTaskToBack boolean"
      ),
      (c, h) ->
        h.commit("", () ->
          h.finish(
            h.activity().moveTaskToBack(true)
              ? Receipt.handoff(
                h.tr(
                  "Android moved Nexus to the background. The new foreground app was not verified.",
                  "اندروید نکسوس را به پس‌زمینه برد. برنامهٔ مقصد تأیید نشده است."
                )
              )
              : Receipt.failed(
                h.tr("Android did not move this task.", "اندروید این وظیفه را جابه‌جا نکرد."),
                "back_unavailable"
              )
          )
        )
    );
    add(
      new Spec(
        "launch_app",
        "Applications",
        "Launch an app",
        "اجرای برنامه",
        "Resolve a launchable application by English/Persian name, alias or package and open it.",
        "",
        1,
        true,
        "Android dispatch / optional window event",
        "name",
        "App name or package"
      ),
      (c, h) -> {
        String alias = h.store().lookup("app_alias", c.get("name"));
        String q = alias.isEmpty() ? c.get("name") : alias;
        h.query(
          () -> AppIndex.find(AppIndex.scan(h.activity()), q),
          apps -> {
            if (apps.isEmpty()) {
              h.finish(
                Receipt.failed(
                  h.tr(
                    "No installed launchable app matched that name.",
                    "برنامهٔ قابل اجرایی با این نام پیدا نشد."
                  ),
                  "app_not_found"
                )
              );
              return;
            }
            Consumer<Integer> select = i -> {
              AppIndex.App a = apps.get(i);
              h.commit("", () ->
                launch(
                  h,
                  Intent.makeMainActivity(new ComponentName(a.pkg, a.cls)).addFlags(
                    Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                  ),
                  a.name,
                  false
                )
              );
            };
            if (
              apps.size() == 1 &&
              (Text.norm(q).equals(Text.norm(apps.get(0).name)) || apps.get(0).pkg.equals(q))
            ) select.accept(0);
            else {
              List<String> labels = new ArrayList<>();
              for (AppIndex.App a : apps) labels.add(a.display());
              h.choose(h.tr("Select application", "انتخاب برنامه"), labels, select);
            }
          }
        );
      }
    );
    add(
      new Spec(
        "open_panel",
        "Device",
        "System panel",
        "تنظیمات سیستم",
        "Open a public settings page. Does not change any setting.",
        "",
        1,
        true,
        "Activity handoff",
        "panel",
        "|wifi|bluetooth|location|display|sound|notifications|battery|apps|dnd|internet|volume"
      ),
      (c, h) -> h.commit("", () -> launch(h, panelIntent(h, c.get("panel")), c.get("panel"), false))
    );
    add(
      new Spec(
        "open_interface",
        "Device",
        "System interface",
        "رابط دستگاه",
        "Open a supported system interface, without reading user files.",
        "",
        1,
        true,
        "Activity handoff",
        "surface",
        "|camera|gallery|browser|contacts|dialer|files"
      ),
      (c, h) -> h.commit("", () -> launch(h, surface(c.get("surface")), c.get("surface"), false))
    );
    add(
      new Spec(
        "open_url",
        "Browser",
        "Open a website",
        "باز کردن وب‌سایت",
        "Preview and open a public HTTPS website.",
        "",
        2,
        true,
        "HTTPS activity handoff",
        "url",
        "Full https:// URL"
      ),
      (c, h) -> {
        String url = c.get("url");
        try {
          java.net.URI u = new java.net.URI(url);
          if (
            !"https".equals(u.getScheme()) ||
            u.getHost() == null ||
            u.getUserInfo() != null ||
            u.getPort() != -1
          ) throw new Exception();
        } catch (Exception e) {
          h.finish(
            Receipt.failed(
              h.tr(
                "Use an HTTPS address without credentials or a custom port.",
                "نشانی HTTPS بدون اطلاعات ورود یا درگاه سفارشی وارد کنید."
              ),
              "invalid_url"
            )
          );
          return;
        }
        h.commit(url, () ->
          launch(
            h,
            new Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE),
            h.tr("Browser", "مرورگر"),
            false
          )
        );
      }
    );
    add(
      new Spec(
        "web_search",
        "Browser",
        "Search the web",
        "جست‌وجوی وب",
        "Preview a query and open results in a browser or YouTube.",
        "",
        2,
        true,
        "Search URL handed off",
        "engine",
        "|web|youtube",
        "query",
        "Search phrase"
      ),
      (c, h) -> {
        String base = c.get("engine").equals("web")
          ? "https://www.google.com/search?q="
          : "https://www.youtube.com/results?search_query=";
        h.commit(c.get("engine") + "\n" + c.get("query"), () ->
          launch(
            h,
            new Intent(Intent.ACTION_VIEW, Uri.parse(base + Uri.encode(c.get("query")))),
            h.tr("Search results", "نتایج جست‌وجو"),
            false
          )
        );
      }
    );
    add(
      new Spec(
        "maps",
        "Browser",
        "Maps & directions",
        "نقشه و مسیریابی",
        "Open a Maps search or directions preview. Location and final place selection belong to Maps.",
        "",
        2,
        true,
        "Map request handoff",
        "mode",
        "|search|route",
        "place",
        "Destination or place query"
      ),
      (c, h) -> {
        String base = c.get("mode").equals("route")
          ? "https://www.google.com/maps/dir/?api=1&destination="
          : "https://www.google.com/maps/search/?api=1&query=";
        h.commit("Maps · " + c.get("mode") + "\n" + c.get("place"), () ->
          launch(
            h,
            new Intent(Intent.ACTION_VIEW, Uri.parse(base + Uri.encode(c.get("place")))),
            "Maps",
            false
          )
        );
      }
    );
    add(
      new Spec(
        "find_contact",
        "Contacts",
        "Find a contact",
        "یافتن مخاطب",
        "Find matching phone contacts on-device. Only the match count is returned to the model.",
        Manifest.permission.READ_CONTACTS,
        1,
        true,
        "ContactsProvider rows",
        "name",
        "Contact name"
      ),
      (c, h) ->
        h.commit("", () ->
          people(h, c.get("name"), list -> {
            StringBuilder result = new StringBuilder();
            for (People.Person p : list) result.append(p.display()).append("\n");
            h.finish(
              Receipt.verified(result.toString().trim(), "contacts_read", J.obj("count", list.size()))
            );
          })
        )
    );
    add(
      new Spec(
        "dial_contact",
        "Communication",
        "Prepare a call",
        "آماده‌سازی تماس",
        "Resolve a recipient locally, confirm the exact name and number, then open the dialer; never place a call.",
        Manifest.permission.READ_CONTACTS,
        2,
        true,
        "Dialer handoff",
        "recipient",
        "Contact name or phone number"
      ),
      this::compose
    );
    add(
      new Spec(
        "compose_sms",
        "Communication",
        "Compose SMS",
        "نوشتن پیامک",
        "Resolve a recipient locally. Preview exact recipient and body. User sends in messaging app.",
        Manifest.permission.READ_CONTACTS,
        2,
        true,
        "SMS composer handoff",
        "recipient",
        "Contact name or number",
        "text",
        "Exact SMS text"
      ),
      this::compose
    );
    add(
      new Spec(
        "compose_email",
        "Communication",
        "Compose email",
        "نوشتن ایمیل",
        "Open a confirmed email draft. Does not send email.",
        "",
        2,
        true,
        "Email composer handoff",
        "to",
        "One email address",
        "subject",
        "Email subject",
        "text",
        "Exact body"
      ),
      (c, h) -> {
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(c.get("to")).matches()) {
          h.finish(Receipt.failed("Invalid email address", "invalid_email"));
          return;
        }
        Uri uri = Uri.parse(
          "mailto:" +
            Uri.encode(c.get("to"), "@") +
            "?subject=" +
            Uri.encode(c.get("subject")) +
            "&body=" +
            Uri.encode(c.get("text"))
        );
        h.commit(c.get("to") + "\n" + c.get("subject") + "\n\n" + c.get("text"), () ->
          launch(h, new Intent(Intent.ACTION_SENDTO, uri), h.tr("Email draft", "پیش‌نویس ایمیل"), false)
        );
      }
    );
    add(
      new Spec(
        "share_text",
        "Communication",
        "Share text",
        "اشتراک متن",
        "Preview exact text, then open the Android chooser. User selects a destination.",
        "",
        2,
        true,
        "System sharesheet handoff",
        "text",
        "Text to share"
      ),
      (c, h) ->
        h.commit(c.get("text"), () ->
          launch(
            h,
            Intent.createChooser(
              new Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, c.get("text")),
              null
            ),
            h.tr("Sharesheet", "اشتراک‌گذاری"),
            false
          )
        )
    );
    add(
      new Spec(
        "recall_note",
        "Memory",
        "Recall a note",
        "بازیابی یادداشت",
        "Search explicitly saved local notes. Return the match count, never the note contents, to the planner.",
        "",
        1,
        true,
        "Local SQLite matches",
        "query",
        "A word from the title or note"
      ),
      (c, h) ->
        h.commit("", () -> {
          if (!h.store().memoryOn()) {
            h.finish(Receipt.failed(h.tr("Memory is paused.", "حافظه متوقف است."), "memory_paused"));
            return;
          }
          List<Map<String, String>> hits = h.store().searchNotes(c.get("query"));
          StringBuilder content = new StringBuilder();
          for (Map<String, String> r : hits)
            content.append(r.get("name")).append(" · ").append(r.get("value")).append("\n");
          h.finish(
            Receipt.verified(
              hits.isEmpty()
                ? h.tr("No saved note matched.", "یادداشت منطبقی پیدا نشد.")
                : Text.clipped(content.toString().trim(), 1400),
              "memory_read",
              J.obj("count", hits.size())
            )
          );
        })
    );
    add(
      new Spec(
        "save_note",
        "Memory",
        "Save a note",
        "ذخیرهٔ یادداشت",
        "With consent store a non-secret note locally. Not a password manager.",
        "",
        2,
        true,
        "Read back saved row",
        "text",
        "Non-secret information"
      ),
      (c, h) ->
        h.commit(c.get("text"), () -> {
          String t = c.get("text");
          long id = h.store().save(-1, "note", Text.clipped(t.replace('\n', ' '), 48), t);
          boolean ok =
            h.store().scalar("SELECT count(*) FROM memory WHERE id=? AND value=?", "" + id, t.trim()) ==
            1;
          h.finish(
            ok
              ? Receipt.verified(
                h.tr(
                  "Note saved and read back from local memory.",
                  "یادداشت ذخیره و از حافظهٔ محلی بازخوانی شد."
                ),
                "note_read_back",
                J.obj()
              )
              : Receipt.failed("Read-back failed", "verify_failed")
          );
        })
    );
    add(
      new Spec(
        "set_reminder",
        "Productivity",
        "Set a reminder",
        "تنظیم یادآور",
        "Schedule an inexact local notification. Time is HH:mm (next occurrence) or ISO local date-time.",
        Manifest.permission.POST_NOTIFICATIONS,
        2,
        true,
        "Stored reminder + alarm accepted",
        "time",
        "HH:mm or YYYY-MM-DDTHH:mm device local time",
        "text",
        "Reminder message"
      ),
      (c, h) -> {
        long at;
        try {
          at = Reminders.when(c.get("time"), LocalDateTime.now(), ZoneId.systemDefault());
        } catch (Exception e) {
          h.finish(
            Receipt.failed(
              h.tr(
                "Use a future local date/time or HH:mm.",
                "زمان آینده یا ساعت به صورت HH:mm وارد کنید."
              ),
              "invalid_time"
            )
          );
          return;
        }
        String when = java.text.DateFormat.getDateTimeInstance().format(new Date(at));
        h.commit(when + "\n" + c.get("text"), () -> {
          long id = Reminders.schedule(h.activity(), h.store(), at, c.get("text"));
          h.finish(
            h.store().scalar("SELECT count(*) FROM reminders WHERE id=?", "" + id) == 1
              ? Receipt.verified(
                h.tr("Reminder scheduled for approximately ", "یادآور برای حدود ") + when,
                "alarm_registered",
                J.obj()
              )
              : Receipt.failed("Reminder record missing", "verify_failed")
          );
        });
      }
    );
    add(
      new Spec(
        "display_walkthrough",
        "Automation",
        "Guided display scroll",
        "پیمایش هدایت‌شدهٔ نمایشگر",
        "Manual only: open Android Display settings and attempt one accessible scroll. No text is read.",
        "accessibility",
        2,
        false,
        "Package-scoped TYPE_VIEW_SCROLLED"
      ),
      (c, h) ->
        h.commit(
          h.tr(
            "Open Display settings and scroll once. No settings will change.",
            "تنظیمات نمایشگر باز و یک‌بار پیمایش می‌شود؛ هیچ تنظیمی تغییر نمی‌کند."
          ),
          () -> launch(h, new Intent(Settings.ACTION_DISPLAY_SETTINGS), "Display", true)
        )
    );
  }

  private static Intent panelIntent(Invocation h, String p) {
    if (p.equals("notifications") && Build.VERSION.SDK_INT < 30) return new Intent(
      Settings.ACTION_APP_NOTIFICATION_SETTINGS
    ).putExtra(Settings.EXTRA_APP_PACKAGE, h.activity().getPackageName());
    return new Intent(panel(p));
  }

  private static String panel(String p) {
    switch (p) {
      case "wifi":
        return Settings.ACTION_WIFI_SETTINGS;
      case "bluetooth":
        return Settings.ACTION_BLUETOOTH_SETTINGS;
      case "location":
        return Settings.ACTION_LOCATION_SOURCE_SETTINGS;
      case "display":
        return Settings.ACTION_DISPLAY_SETTINGS;
      case "sound":
        return Settings.ACTION_SOUND_SETTINGS;
      case "notifications":
        return Settings.ACTION_ALL_APPS_NOTIFICATION_SETTINGS;
      case "battery":
        return Intent.ACTION_POWER_USAGE_SUMMARY;
      case "apps":
        return Settings.ACTION_APPLICATION_SETTINGS;
      case "dnd":
        return Settings.ACTION_ZEN_MODE_PRIORITY_SETTINGS;
      case "volume":
        return Build.VERSION.SDK_INT >= 29
          ? Settings.Panel.ACTION_VOLUME
          : Settings.ACTION_SOUND_SETTINGS;
      default:
        return Build.VERSION.SDK_INT >= 29
          ? Settings.Panel.ACTION_INTERNET_CONNECTIVITY
          : Settings.ACTION_WIFI_SETTINGS;
    }
  }

  private static Intent surface(String p) {
    switch (p) {
      case "camera":
        return new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA);
      case "gallery":
        return new Intent(Intent.ACTION_VIEW, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
      case "contacts":
        return new Intent(Intent.ACTION_VIEW, ContactsContract.Contacts.CONTENT_URI);
      case "dialer":
        return new Intent(Intent.ACTION_DIAL);
      case "files":
        return new Intent(Intent.ACTION_OPEN_DOCUMENT)
          .addCategory(Intent.CATEGORY_OPENABLE)
          .setType("*/*");
      default:
        return Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_BROWSER);
    }
  }

  private void people(Invocation h, String q, Consumer<List<People.Person>> done) {
    String alias = h.store().lookup("contact_alias", q);
    String name = alias.isEmpty() ? (Text.norm(q).equals("مامانم") ? "مامان" : q) : alias;
    h.query(
      () -> People.find(h.activity(), name),
      list -> {
        if (list.isEmpty()) h.finish(
          Receipt.failed(
            h.tr(
              "No matching phone contact. Try a shorter name or enter a number.",
              "مخاطب پیدا نشد. نام کوتاه‌تر یا شماره وارد کنید."
            ),
            "contact_not_found"
          )
        );
        else done.accept(list);
      }
    );
  }

  private void compose(Call c, Invocation h) {
    Consumer<People.Person> use = p -> {
      String digits = Text.digits(p.number).replaceAll("[ ()-]", "");
      if (!Text.phone(digits)) {
        h.finish(Receipt.failed("Invalid number", "invalid_number"));
        return;
      }
      boolean sms = c.name.equals("compose_sms");
      String preview = p.display() + (sms ? "\n\n" + c.get("text") : "");
      Intent i = sms
        ? new Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", digits, null)).putExtra(
          "sms_body",
          c.get("text")
        )
        : new Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", digits, null));
      h.commit(preview, () ->
        launch(h, i, sms ? h.tr("SMS draft", "پیش‌نویس پیامک") : h.tr("Dialer", "شماره‌گیر"), false)
      );
    };
    String r = c.get("recipient");
    if (Text.phone(r)) {
      use.accept(new People.Person(h.tr("Number", "شماره"), r));
      return;
    }
    people(h, r, list -> {
      if (list.size() == 1) {
        use.accept(list.get(0));
        return;
      }
      List<String> choices = new ArrayList<>();
      for (People.Person p : list) choices.add(p.display());
      h.choose(h.tr("Which contact?", "کدام مخاطب؟"), choices, i -> use.accept(list.get(i)));
    });
  }

  private static void launch(Invocation h, Intent intent, String label, boolean scroll) {
    Activity a = h.activity();
    ResolveInfo info = a.getPackageManager().resolveActivity(intent, 0);
    String pkg = intent.getComponent() != null
      ? intent.getComponent().getPackageName()
      : info != null && info.activityInfo != null
        ? info.activityInfo.packageName
        : null;
    String handed =
      h.tr("Android accepted: ", "اندروید پذیرفت: ") +
      label +
      h.tr(
        ". The result inside the other app is not verified.",
        ". نتیجهٔ داخل برنامهٔ دیگر تأیید نشده است."
      );
    boolean watching = WindowReceiptService.arm(
      "android".equals(pkg) || Intent.ACTION_CHOOSER.equals(intent.getAction()) ? null : pkg,
      scroll,
      status -> {
        if (status.equals("window_visible") || status.equals("display_scrolled")) h.finish(
          Receipt.verified(
            label +
              (scroll
                  ? h.tr(": scroll event observed.", ": پیمایش مشاهده شد.")
                  : h.tr(
                    ": destination window observed; no further action verified.",
                    ": پنجرهٔ مقصد مشاهده شد؛ اقدام دیگری تأیید نشده است."
                  )),
            status,
            J.obj()
          )
        );
        else if (scroll) h.finish(
          Receipt.failed(
            h.tr(
              "Display opened; scrolling could not be verified.",
              "نمایشگر باز شد؛ پیمایش تأیید نشد."
            ),
            "scroll_unverified"
          )
        );
        else h.finish(Receipt.handoff(handed));
      }
    );
    if (scroll && !watching) {
      h.finish(
        Receipt.failed(
          h.tr("No supported, opted-in Display window service.", "سرویس اختیاری نمایشگر در دسترس نیست."),
          "access_unavailable"
        )
      );
      return;
    }
    try {
      a.startActivity(intent);
      if (!watching) h.finish(Receipt.handoff(handed));
    } catch (ActivityNotFoundException | SecurityException ex) {
      WindowReceiptService.disarm();
      h.finish(
        Receipt.failed(
          h.tr(
            "No permitted application can open this interface.",
            "برنامهٔ مجازی برای این رابط پیدا نشد."
          ),
          "no_handler"
        )
      );
    }
  }
}
