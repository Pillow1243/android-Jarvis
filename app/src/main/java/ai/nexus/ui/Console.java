package ai.nexus.ui;

import ai.nexus.MainActivity;
import ai.nexus.accessibility.WindowReceiptService;
import ai.nexus.agent.Agent;
import ai.nexus.core.*;
import ai.nexus.memory.Store;
import ai.nexus.tools.*;
import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.os.Build;
import android.view.*;
import android.widget.*;
import java.time.*;
import java.util.*;

public final class Console {

  final MainActivity a;
  final Kit k;
  final Store db;
  String filter = "All",
    memoryFilter = "all";
  SignalField fieldView;
  TextView transcript;

  public Console(MainActivity a) {
    this.a = a;
    k = a.k;
    db = a.db;
  }

  String t(String s, String f) {
    return a.tr(s, f);
  }

  public void draw(LinearLayout v, String tab) {
    fieldView = null;
    transcript = null;
    switch (tab) {
      case "agent":
        agent(v);
        break;
      case "activity":
        activity(v);
        break;
      case "tools":
        tools(v);
        break;
      case "memory":
        memory(v);
        break;
      case "settings":
        a.controls.draw(v);
        break;
      default:
        home(v);
    }
  }

  void heading(LinearLayout v, String meta, String title, String desc) {
    LinearLayout m = k.row();
    View blip = new View(a);
    k.box(blip, Kit.LIME, 2, 0);
    k.add(m, blip, 13, 3);
    k.gap(m, 7);
    k.add(m, k.text(meta, 10, k.muted, 2), -2, -2);
    k.add(v, m, -1, -2);
    k.gap(v, 7);
    TextView h = k.text(title, 32, k.ink, 1);
    h.setLetterSpacing(a.fa ? 0 : -.035f);
    k.add(v, h, -1, -2);
    k.gap(v, 5);
    k.add(v, k.text(desc, 14, k.sub, 0), -1, -2);
    k.gap(v, 16);
  }

  public void audio(float rms, String text) {
    if (fieldView != null) fieldView.level(rms);
    if (transcript != null && !text.isEmpty()) transcript.setText(Text.clipped(text, 60));
  }

  String state(String s) {
    switch (s) {
      case "idle":
        return t("Standby", "آماده");
      case "listening":
        return t("Listening", "در حال شنیدن");
      case "speaking":
        return t("Speaking", "در حال گفتار");
      case "transcribing":
        return t("Transcribing", "رونویسی");
      case "thinking":
        return t("Thinking", "در حال تحلیل");
      case "planning":
        return t("Planning", "برنامه‌ریزی");
      case "executing":
        return t("Executing", "در حال اجرا");
      case "verifying":
        return t("Verifying", "در حال بررسی");
      case "completed":
        return t("Verified", "تأییدشده");
      case "verified":
        return t("Verified", "تأییدشده");
      case "handoff":
      case "handed_off":
        return t("Handed off", "واگذارشده");
      case "failed":
        return t("Failed", "ناموفق");
      case "permission":
        return t("Permission needed", "نیاز به مجوز");
      case "approval":
        return t("Needs approval", "نیاز به تأیید");
      case "clarification":
      case "clarifying":
        return t("Choose a match", "انتخاب نتیجه");
      case "waiting_return":
        return t("Return to continue", "بازگشت برای ادامه");
      case "answered":
        return t("Reply ready", "پاسخ آماده");
      case "declined":
        return t("Declined", "ردشده");
      case "cancelled":
        return t("Cancelled", "لغوشده");
      case "interrupted":
        return t("Interrupted", "متوقف‌شده");
      case "requested":
        return t("Pending", "در انتظار");
      case "executed":
        return t("Executed", "اجراشده");
      case "attempted":
        return t("Attempted", "تلاش‌شده");
      case "skipped":
        return t("Skipped", "اجرانشده");
      case "running":
        return t("In progress", "در حال اجرا");
      case "partial":
        return t("Review results", "بررسی نتایج");
      default:
        return s.replace('_', ' ');
    }
  }

  int color(String s) {
    return s.matches("failed|declined|interrupted")
      ? Kit.RED
      : s.matches("handed_off|handoff|approval|permission|partial|limited")
        ? Kit.AMBER
        : s.matches("thinking|planning|clarifying")
          ? Kit.LILAC
          : s.matches("verified|completed|idle")
            ? Kit.LIME
            : Kit.TEAL;
  }

  String icon(String id) {
    switch (id) {
      case "device_report":
        return "activity";
      case "list_apps":
      case "find_app":
      case "launch_app":
        return "layout_grid";
      case "previous_app":
        return "rotate_ccw";
      case "open_panel":
        return "sliders_horizontal";
      case "open_interface":
        return "smartphone";
      case "maps":
        return "compass";
      case "open_url":
        return "globe";
      case "web_search":
        return "search";
      case "find_contact":
        return "users";
      case "dial_contact":
        return "phone";
      case "compose_sms":
        return "message_circle";
      case "compose_email":
        return "mail";
      case "share_text":
        return "share_2";
      case "save_note":
        return "notebook_pen";
      case "set_reminder":
        return "bell_ring";
      default:
        return "scan_line";
    }
  }

  String titleFor(String id) {
    Spec s = a.tools.specs.get(id);
    return s == null ? id : t(s.title, s.fa);
  }

  private void home(LinearLayout v) {
    int hour = LocalTime.now().getHour();
    String greet = hour < 12
      ? t("Good morning", "صبح بخیر")
      : hour < 18
        ? t("Good afternoon", "روز بخیر")
        : t("Good evening", "عصر بخیر");
    String name = db.prefs.getString("name", t("operator", "کاربر"));
    heading(
      v,
      "WORKSPACE   /   01",
      t("Command center", "مرکز فرمان"),
      greet + t(", ", "، ") + name + t(". What’s the mission?", ". چه کاری انجام بدهم؟")
    );
    FrameLayout hero = new FrameLayout(a);
    k.box(hero, 0xff102219, 20, 0xff39543d);
    hero.setClipToOutline(true);
    fieldView = new SignalField(a);
    String st = a.voiceState.equals("idle") ? a.agent.stage : a.voiceState;
    fieldView.update(st, a.motion());
    hero.addView(fieldView, new FrameLayout.LayoutParams(-1, -1));
    LinearLayout top = k.row();
    k.pad(top, 14, 12, 14, 0);
    LinearLayout tag = k.row();
    k.add(tag, k.icon("radio", Kit.LIME), 14, 14);
    k.gap(tag, 5);
    k.add(tag, k.text("NEURAL LINK", 9, 0xffc2d6c4, 2), -2, -2);
    k.weight(top, tag, -2);
    k.add(top, k.badge("• " + state(st).toUpperCase(Locale.ROOT), color(st)), -2, -2);
    hero.addView(top, new FrameLayout.LayoutParams(-1, -2, Gravity.TOP));
    LinearLayout bottom = k.row();
    k.pad(bottom, 16, 0, 14, 13);
    LinearLayout label = k.col();
    k.add(label, k.text("NEXUS CORE   /   01", 8, 0xffa3bda5, 2), -1, -2);
    k.gap(label, 4);
    transcript = k.text(
      a.agent.running
        ? t("Tap to interrupt", "برای توقف لمس کنید")
        : a.voice.listening
          ? t("Listening…", "در حال شنیدن…")
          : a.voice.speaking
            ? t("Tap to interrupt", "برای توقف لمس کنید")
            : t("Tap to speak", "برای صحبت لمس کنید"),
      17,
      k.ink,
      1
    );
    k.single(transcript);
    k.add(label, transcript, -1, -2);
    k.weight(bottom, label, -2);
    FrameLayout mic = new FrameLayout(a);
    k.box(mic, Kit.LIME, 24, 0);
    mic.addView(
      k.icon(a.agent.running || a.voice.listening || a.voice.speaking ? "square" : "mic", 0xff15220e),
      new FrameLayout.LayoutParams(k.d(21), k.d(21), Gravity.CENTER)
    );
    k.add(bottom, mic, 43, 43);
    hero.addView(bottom, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));
    k.tap(hero, a::microphone, t("Voice control", "کنترل صوتی"));
    k.add(v, hero, -1, 199);
    k.gap(v, 10);
    LinearLayout input = k.row();
    k.pad(input, 14, 0, 9, 0);
    k.box(input, k.card, 15, k.line);
    k.add(input, k.icon("terminal", k.muted), 19, 19);
    k.gap(input, 10);
    k.weight(input, k.text(t("Type a command…", "یک فرمان بنویسید…"), 15, k.sub, 0), -2);
    k.add(input, k.glyph("arrow_up", Kit.LIME, 30, 16), 30, 30);
    k.tap(input, () -> a.command(""), "command_input");
    k.add(v, input, -1, 52);
    if (!a.notice.isEmpty()) {
      k.gap(v, 10);
      k.item(v, "circle_alert", Kit.AMBER, t("Voice notice", "اعلان صوتی"), a.notice, () ->
        a.info(t("Voice input", "ورودی صوتی"), a.notice)
      );
    }
    if (a.agent.running || !a.agent.report.isEmpty()) {
      k.gap(v, 10);
      k.item(
        v,
        a.agent.running ? "workflow" : "list_checks",
        color(a.agent.stage),
        a.agent.running ? t("Active task", "وظیفهٔ جاری") : state(a.agent.stage),
        a.agent.running ? a.agent.title : Text.clipped(a.agent.report, 120),
        () -> a.page("agent")
      );
    }
    k.section(v, t("Quick actions", "دسترسی سریع"), "04  /  LOCAL");
    LinearLayout q = k.row();
    quick(
      q,
      "activity",
      Kit.LIME,
      t("Device report", "گزارش دستگاه"),
      t("Read diagnostics", "بررسی دستگاه"),
      () -> a.run(new Call("device_report"))
    );
    k.gap(q, 9);
    quick(
      q,
      "layout_grid",
      Kit.BLUE,
      t("Launch an app", "اجرای برنامه"),
      t("Browse registry", "فهرست برنامه‌ها"),
      () -> a.controls.appPicker()
    );
    k.add(v, q, -1, -2);
    k.gap(v, 9);
    q = k.row();
    quick(
      q,
      "notebook_pen",
      Kit.LILAC,
      t("Save a note", "ثبت یادداشت"),
      t("Local memory", "حافظهٔ محلی"),
      () -> a.toolForm(a.tools.specs.get("save_note"))
    );
    k.gap(q, 9);
    quick(
      q,
      "bell_ring",
      Kit.AMBER,
      t("Set reminder", "یادآور"),
      t("Schedule a nudge", "تنظیم زمان"),
      () -> a.toolForm(a.tools.specs.get("set_reminder"))
    );
    k.add(v, q, -1, -2);
    if (db.memoryOn()) {
      List<Map<String, String>> pinned = db.rows(
        "SELECT name,value FROM memory WHERE kind='shortcut' AND pinned=1 ORDER BY stamp DESC LIMIT 8"
      );
      if (!pinned.isEmpty()) {
        k.section(v, t("Favorite commands", "فرمان‌های محبوب"), "PINNED");
        LinearLayout pins = chips(v);
        for (Map<String, String> m : pinned)
          chip(pins, "☆  " + m.get("name"), false, () -> a.submit(m.get("name"), false));
      }
    }
    k.section(
      v,
      t("Recent activity", "فعالیت اخیر"),
      db.memoryOn() ? t("MEMORY ON", "حافظه روشن") : t("MEMORY OFF", "حافظه خاموش")
    );
    List<Map<String, String>> r = db.rows("SELECT * FROM tasks ORDER BY id DESC LIMIT 2");
    if (r.isEmpty()) empty(
      v,
      "history",
      t("Your timeline starts here", "آغاز تاریخچهٔ شما"),
      t("Run a command to see its device receipts.", "یک فرمان اجرا کنید تا رسیدهایش را ببینید.")
    );
    else for (Map<String, String> row : r) taskRow(v, row);
    if (!db.prefs.getBoolean("intro", false)) k.item(
      v,
      "sparkles",
      Kit.TEAL,
      t("First flight guide", "راهنمای شروع"),
      t("Local commands, cloud planning and consent.", "فرمان محلی، برنامه‌ریزی ابری و مجوزها."),
      () -> a.controls.guide()
    );
  }

  private void quick(
    LinearLayout row,
    String icon,
    int color,
    String title,
    String detail,
    Runnable click
  ) {
    LinearLayout c = k.col();
    k.box(c, k.card, 15, k.line);
    k.pad(c, 13, 10, 12, 11);
    LinearLayout h = k.row();
    k.add(h, k.glyph(icon, color, 28, 16), 28, 28);
    k.weight(h, new View(a), 1);
    k.add(h, k.icon("arrow_up_right", k.muted), 13, 13);
    k.add(c, h, -1, -2);
    k.gap(c, 8);
    TextView name = k.text(title, 15, k.ink, 1);
    k.single(name);
    k.add(c, name, -1, -2);
    k.gap(c, 3);
    TextView sub = k.text(detail, 11, k.sub, 0);
    k.single(sub);
    k.add(c, sub, -1, -2);
    k.tap(c, click, title);
    k.weight(row, c, -2);
  }

  private void empty(LinearLayout v, String icon, String title, String detail) {
    LinearLayout c = k.card(),
      r = k.row();
    k.add(r, k.glyph(icon, k.muted, 36, 18), 36, 36);
    k.gap(r, 10);
    LinearLayout words = k.col();
    k.add(words, k.text(title, 14, k.ink, 1), -1, -2);
    k.gap(words, 4);
    k.add(words, k.text(detail, 12, k.sub, 0), -1, -2);
    k.weight(r, words, -2);
    k.add(c, r, -1, -2);
    k.add(v, c, -1, -2);
    k.gap(v, 6);
  }

  private long n(String sql, String... args) {
    return db.scalar(sql, args);
  }

  private String stamp(String s) {
    return java.text.DateFormat.getDateTimeInstance(
      java.text.DateFormat.SHORT,
      java.text.DateFormat.SHORT
    ).format(new Date(Long.parseLong(s)));
  }

  private void agent(LinearLayout v) {
    heading(
      v,
      "INTELLIGENCE   /   02",
      t("Agent telemetry", "پایش عامل"),
      t("Execution, evidence and resource usage.", "اجرا، شواهد و مصرف منابع.")
    );
    LinearLayout top = k.card(),
      r = k.row();
    k.add(r, k.icon("orbit", Kit.LIME), 20, 20);
    k.gap(r, 8);
    k.weight(r, k.text(t("Execution engine", "موتور اجرا"), 17, k.ink, 1), -2);
    k.add(r, k.badge(state(a.agent.stage), color(a.agent.stage)), -2, -2);
    k.add(top, r, -1, -2);
    k.gap(top, 13);
    k.add(
      top,
      k.text(
        a.agent.title.isEmpty()
          ? t("Ready for an instruction", "آماده برای دریافت فرمان")
          : a.agent.title,
        19,
        k.ink,
        1
      ),
      -1,
      -2
    );
    k.gap(top, 6);
    k.add(
      top,
      k.text(
        a.agent.running
          ? (a.agent.progress.isEmpty() ? state(a.agent.stage) : a.agent.progress)
          : a.agent.report.isEmpty()
            ? t(
              "Local tools are available without an API key.",
              "ابزارهای محلی بدون کلید هم کار می‌کنند."
            )
            : Text.clipped(a.agent.report, 1200),
        14,
        k.sub,
        0
      ),
      -1,
      -2
    );
    if (a.agent.running) {
      k.gap(top, 12);
      k.add(top, k.button(t("Cancel task", "لغو وظیفه"), false, a.agent::cancel), -1, -2);
    }
    k.add(v, top, -1, -2);
    if (!a.agent.steps.isEmpty()) {
      k.section(v, t("Execution timeline", "خط زمانی اجرا"), "TRACE / " + a.agent.steps.size());
      int i = 0;
      for (Agent.Step st : a.agent.steps) trace(v, ++i, titleFor(st.call.name), st.state, st.detail);
    }
    k.section(v, t("Usage today", "مصرف امروز"), LocalDate.now().toString());
    String day = LocalDate.now().toString();
    LinearLayout stats = k.row();
    stat(
      stats,
      "network",
      Kit.TEAL,
      "" + n("SELECT requests FROM usage WHERE day=?", day),
      t("Requests", "درخواست")
    );
    k.gap(stats, 8);
    stat(
      stats,
      "zap",
      Kit.AMBER,
      "" + n("SELECT tokens FROM usage WHERE day=?", day),
      t("Tokens", "توکن")
    );
    k.gap(stats, 8);
    stat(
      stats,
      "workflow",
      Kit.LILAC,
      "" +
        n(
          "SELECT count(*) FROM steps WHERE stamp>=? AND state NOT IN ('requested','skipped')",
          "" + LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        ),
      t("Tool receipts", "رسید ابزار")
    );
    k.add(v, stats, -1, -2);
    k.section(v, t("Task volume", "حجم وظایف"), "07 DAYS");
    LinearLayout chart = k.card();
    LinearLayout bars = k.row();
    long[] vals = new long[7];
    long max = 1;
    for (int i = 0; i < 7; i++) {
      LocalDate ld = LocalDate.now().minusDays(6 - i);
      long from = ld.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
        to = ld.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
      vals[i] = n("SELECT count(*) FROM tasks WHERE stamp>=? AND stamp<?", "" + from, "" + to);
      max = Math.max(max, vals[i]);
    }
    for (int i = 0; i < 7; i++) {
      LinearLayout col = k.col();
      col.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
      k.add(col, k.text("" + vals[i], 10, k.sub, 2), -2, -2);
      k.gap(col, 4);
      View bar = new View(a);
      k.box(bar, i == 6 ? Kit.LIME : k.muted, 3, 0);
      k.add(col, bar, 16, vals[i] == 0 ? 1 : 4 + (int) ((vals[i] * 42) / max));
      k.gap(col, 6);
      k.add(col, k.text("" + LocalDate.now().minusDays(6 - i).getDayOfMonth(), 9, k.muted, 2), -2, -2);
      k.weight(bars, col, 82);
    }
    k.add(chart, bars, -1, -2);
    k.add(v, chart, -1, -2);
    k.section(v, t("Subsystems", "زیرسامانه‌ها"), "LIVE");
    k.item(v, "brain_circuit", Kit.TEAL, "Gemini", a.controls.cloudStatus(), a.controls::provider);
    k.item(
      v,
      "layout_grid",
      Kit.BLUE,
      t("Application registry", "فهرست برنامه‌ها"),
      a.indexed
        ? a.apps.size() + t(" launchable activities", " فعالیت قابل اجرا")
        : t("Indexing…", "نمایه‌سازی…"),
      a.controls::appPicker
    );
    k.item(
      v,
      "database",
      Kit.LILAC,
      t("Memory store", "حافظهٔ محلی"),
      n("SELECT count(*) FROM memory") + " / 250",
      () -> a.page("memory")
    );
    k.item(
      v,
      "shield_check",
      Kit.LIME,
      t("Device permissions", "مجوزهای دستگاه"),
      t("Inspect access and controls", "بررسی دسترسی‌ها"),
      a.controls::permissions
    );
  }

  private void stat(LinearLayout r, String icon, int tint, String n, String label) {
    LinearLayout c = k.card();
    k.pad(c, 10, 12, 10, 11);
    k.add(c, k.icon(icon, tint), 18, 18);
    k.gap(c, 8);
    k.add(c, k.text(n, 21, k.ink, 1), -1, -2);
    k.gap(c, 3);
    k.add(c, k.text(label, 11, k.sub, 0), -1, -2);
    k.weight(r, c, -2);
  }

  private void trace(LinearLayout v, int idx, String title, String st, String detail) {
    LinearLayout r = k.row();
    k.pad(r, 3, 6, 3, 6);
    k.add(r, k.badge(String.format(Locale.ROOT, "%02d", idx), color(st)), -2, -2);
    k.gap(r, 9);
    LinearLayout words = k.col();
    k.add(words, k.text(title, 15, k.ink, 1), -1, -2);
    if (!detail.isEmpty()) {
      k.gap(words, 4);
      k.add(words, k.text(Text.clipped(detail, 350), 12, k.sub, 0), -1, -2);
    }
    k.weight(r, words, -2);
    k.gap(r, 5);
    k.add(r, k.text(state(st), 11, color(st), 1), -2, -2);
    k.add(v, r, -1, -2);
    k.line(v);
  }

  private void activity(LinearLayout v) {
    heading(
      v,
      "FLIGHT RECORDER   /   03",
      t("Activity log", "دفتر فعالیت"),
      t("Local receipts, not model predictions.", "رسیدهای محلی، نه پیش‌بینی‌های مدل.")
    );
    List<Map<String, String>> rows = db.rows("SELECT * FROM tasks ORDER BY id DESC LIMIT 150");
    if (!db.historyOn()) empty(
      v,
      "pause",
      t("History is paused", "تاریخچه متوقف است"),
      t("New actions are not retained.", "اقدام‌های جدید نگهداری نمی‌شوند.")
    );
    if (rows.isEmpty()) {
      empty(
        v,
        "list_tree",
        t("No recorded tasks", "هنوز وظیفه‌ای ثبت نشده"),
        t(
          "Run a two-step system check to record your first trace.",
          "یک بررسی دومرحله‌ای اجرا کنید تا اولین رسیدها ثبت شوند."
        )
      );
      k.add(
        v,
        k.button(t("Run system check", "اجرای بررسی دستگاه"), false, () -> {
          a.page("agent");
          a.submit(t("device report then list apps", "گزارش دستگاه و بعد فهرست برنامه‌ها"), false);
        }),
        -1,
        -2
      );
    }
    for (Map<String, String> row : rows) taskRow(v, row);
  }

  private void taskRow(LinearLayout v, Map<String, String> row) {
    String st = row.get("state");
    k.item(
      v,
      st.equals("completed") ? "check_check" : "history",
      color(st),
      row.get("title"),
      state(st) + "   ·   " + stamp(row.get("stamp")),
      () -> {
        Kit.Sheet s = k.new Sheet(row.get("title"));
        s.note(state(st) + "   /   " + stamp(row.get("stamp")));
        int i = 0;
        for (Map<String, String> step : db.rows(
          "SELECT * FROM steps WHERE task=? ORDER BY id",
          row.get("id")
        )) {
          trace(
            s.body,
            ++i,
            titleFor(step.get("tool")),
            step.get("state"),
            step.get("code").replace('_', ' ')
          );
          List<String> phases = new ArrayList<>();
          for (Map<String, String> e : db.rows(
            "SELECT state FROM events WHERE step=? ORDER BY id",
            step.get("id")
          ))
            phases.add(state(e.get("state")));
          if (!phases.isEmpty()) {
            k.gap(s.body, 5);
            k.add(s.body, k.text(String.join("  ›  ", phases), 10, k.muted, 1), -1, -2);
            k.gap(s.body, 8);
          }
        }
        if (i == 0) s.note(
          t("No device tool executed for this task.", "هیچ ابزار دستگاهی برای این وظیفه اجرا نشده است.")
        );
        s.action(t("Close", "بستن"), false, s::close);
        s.show();
      }
    );
  }

  public String field(String key) {
    String[] keys = {
        "name",
        "panel",
        "surface",
        "engine",
        "query",
        "mode",
        "place",
        "recipient",
        "text",
        "time",
        "to",
        "subject",
        "url",
      },
      en = {
        "Name",
        "Settings page",
        "Interface",
        "Search provider",
        "Search phrase",
        "Mode",
        "Destination",
        "Recipient",
        "Exact text",
        "Local time",
        "To",
        "Subject",
        "HTTPS address",
      },
      fa = {
        "نام",
        "صفحهٔ تنظیمات",
        "رابط",
        "موتور جست‌وجو",
        "عبارت جست‌وجو",
        "حالت",
        "مقصد",
        "گیرنده",
        "متن دقیق",
        "زمان محلی",
        "به",
        "موضوع",
        "نشانی HTTPS",
      };
    int i = Arrays.asList(keys).indexOf(key);
    return i < 0 ? key : t(en[i], fa[i]);
  }

  public String description(Spec s) {
    if (!a.fa) return s.description;
    switch (s.id) {
      case "device_report":
        return "خواندن باتری، مدل دستگاه و نسخهٔ اندروید.";
      case "find_app":
        return "بررسی وجود برنامه با جست‌وجوی واقعی در فهرست دستگاه.";
      case "previous_app":
        return "بردن نکسوس به پس‌زمینه؛ مقصد را اندروید انتخاب می‌کند.";
      case "list_apps":
        return "نمایش برنامه‌های قابل اجرای دستگاه.";
      case "launch_app":
        return "یافتن و باز کردن برنامه با نام، بسته یا نام مستعار.";
      case "open_panel":
        return "باز کردن تنظیمات اندروید بدون تغییر آن‌ها.";
      case "open_interface":
        return "دوربین، گالری، مرورگر، مخاطبان، شماره‌گیر یا فایل‌ها.";
      case "open_url":
        return "بررسی نشانی HTTPS و باز کردن در مرورگر.";
      case "web_search":
        return "پیش‌نمایش عبارت و جست‌وجو در وب یا یوتیوب.";
      case "maps":
        return "نمایش جست‌وجو یا پیش‌نمایش مسیر در نقشه.";
      case "find_contact":
        return "جست‌وجوی نام و شماره فقط روی دستگاه.";
      case "dial_contact":
        return "انتخاب مخاطب و تأیید شماره برای باز کردن شماره‌گیر.";
      case "compose_sms":
        return "پیش‌نمایش گیرنده و متن؛ ارسال نهایی با شماست.";
      case "compose_email":
        return "بررسی و باز کردن پیش‌نویس ایمیل.";
      case "share_text":
        return "پیش‌نمایش متن و انتخاب مقصد اشتراک‌گذاری.";
      case "recall_note":
        return "جست‌وجوی یادداشت‌های ذخیره‌شده روی دستگاه بدون ارسال متن به مدل.";
      case "save_note":
        return "ذخیرهٔ یادداشت غیرمحرمانه در حافظهٔ دستگاه.";
      case "set_reminder":
        return "ثبت اعلان محلی در زمان تقریبی.";
      default:
        return "باز کردن تنظیمات نمایشگر و پیمایش یک‌باره با اجازهٔ شما.";
    }
  }

  private String category(String id) {
    String[] en = {
        "All",
        "Device",
        "Applications",
        "Contacts",
        "Communication",
        "Browser",
        "Productivity",
        "Automation",
        "Memory",
        "Files",
        "Voice",
      },
      f = {
        "همه",
        "دستگاه",
        "برنامه‌ها",
        "مخاطبان",
        "ارتباطات",
        "مرورگر",
        "بهره‌وری",
        "خودکارسازی",
        "حافظه",
        "فایل‌ها",
        "صدا",
      };
    int i = Arrays.asList(en).indexOf(id);
    return i < 0 ? id : t(id, f[i]);
  }

  private LinearLayout chips(LinearLayout v) {
    HorizontalScrollView sc = new HorizontalScrollView(a);
    sc.setHorizontalScrollBarEnabled(false);
    LinearLayout r = k.row();
    sc.addView(r);
    k.add(v, sc, -1, -2);
    k.gap(v, 13);
    return r;
  }

  private void chip(LinearLayout r, String label, boolean selected, Runnable cb) {
    TextView x = k.text(label, 12, selected ? Kit.LIME : k.sub, 1);
    k.box(
      x,
      selected ? Kit.shade(Kit.LIME, 21) : k.card,
      18,
      selected ? Kit.shade(Kit.LIME, 78) : k.line
    );
    k.pad(x, 12, 8, 12, 8);
    k.tap(x, cb, label);
    k.add(r, x, -2, -2);
    k.gap(r, 6);
  }

  private void tools(LinearLayout v) {
    heading(
      v,
      "CAPABILITY INDEX   /   04",
      t("Tool center", "مرکز ابزار"),
      a.tools.specs.size() +
        t(" typed tools · permission-aware by design.", " ابزار با بررسی مجوز و ورودی.")
    );
    LinearLayout r = chips(v);
    for (String cat : Arrays.asList(
      "All",
      "Device",
      "Applications",
      "Contacts",
      "Communication",
      "Browser",
      "Productivity",
      "Automation",
      "Memory",
      "Files",
      "Voice"
    ))
      chip(r, category(cat), cat.equals(filter), () -> {
        filter = cat;
        a.render(false);
      });
    if (filter.equals("Voice")) {
      k.item(
        v,
        "mic",
        Kit.TEAL,
        t("Push to talk", "فشار برای صحبت"),
        t("Foreground speech service", "سرویس گفتار در پیش‌زمینه"),
        a::microphone
      );
      return;
    }
    if (filter.equals("Files")) {
      k.item(
        v,
        "folder_open",
        Kit.BLUE,
        t("Browse documents", "مرور فایل‌ها"),
        t("System picker · no broad storage access", "انتخابگر سیستم · بدون دسترسی کلی"),
        () -> a.run(new Call("open_interface", "surface", "files"))
      );
      return;
    }
    for (Spec s : a.tools.specs.values()) {
      if (!filter.equals("All") && !filter.equals(s.category)) continue;
      boolean permitted =
        s.permission.isEmpty() ||
        (s.permission.equals("accessibility")
            ? WindowReceiptService.connected()
            : (s.permission.equals(Manifest.permission.POST_NOTIFICATIONS) &&
              Build.VERSION.SDK_INT < 33) ||
            a.checkSelfPermission(s.permission) == PackageManager.PERMISSION_GRANTED);
      int tint = s.risk == 1 ? Kit.TEAL : Kit.AMBER;
      long count = n(
        "SELECT count(*) FROM steps WHERE tool=? AND state NOT IN ('requested','skipped')",
        s.id
      );
      long last = n(
        "SELECT max(stamp) FROM steps WHERE tool=? AND state NOT IN ('requested','skipped')",
        s.id
      );
      LinearLayout c = k.card(),
        head = k.row();
      k.add(head, k.glyph(icon(s.id), tint, 34, 18), 34, 34);
      k.gap(head, 10);
      k.weight(head, k.text(t(s.title, s.fa), 16, k.ink, 1), -2);
      k.add(head, k.badge("L" + s.risk, tint), -2, -2);
      k.add(c, head, -1, -2);
      k.gap(c, 9);
      k.add(c, k.text(description(s), 13, k.sub, 0), -1, -2);
      k.gap(c, 10);
      k.add(
        c,
        k.text(
          (permitted ? t("READY", "آماده") : t("ACCESS NEEDED", "نیاز به مجوز")) +
            "   ·   " +
            count +
            t(" RUNS", " اجرا") +
            (last > 0 ? "   ·   " + stamp("" + last) : ""),
          10,
          k.muted,
          2
        ),
        -1,
        -2
      );
      k.tap(c, () -> a.toolForm(s), t(s.title, s.fa));
      k.add(v, c, -1, -2);
      k.gap(v, 8);
    }
  }

  private void memory(LinearLayout v) {
    heading(
      v,
      "LOCAL VAULT   /   05",
      t("Memory bank", "بانک حافظه"),
      t("What Nexus keeps is up to you.", "شما تعیین می‌کنید چه چیزی بماند.")
    );
    LinearLayout vault = k.card(),
      vh = k.row();
    k.add(vh, k.icon("database", Kit.LILAC), 19, 19);
    k.gap(vh, 8);
    k.weight(vh, k.text(t("Local storage", "ذخیرهٔ محلی"), 15, k.ink, 1), -2);
    k.add(
      vh,
      k.badge(
        db.memoryOn() ? t("ACTIVE", "فعال") : t("PAUSED", "متوقف"),
        db.memoryOn() ? Kit.LILAC : k.muted
      ),
      -2,
      -2
    );
    k.add(vault, vh, -1, -2);
    k.gap(vault, 13);
    long count = n("SELECT count(*) FROM memory");
    LinearLayout gauge = k.row();
    for (int i = 0; i < 24; i++) {
      View seg = new View(a);
      k.box(seg, i < Math.ceil((count * 24.0) / 250) ? Kit.LILAC : k.line, 2, 0);
      k.weight(gauge, seg, 6);
      if (i != 23) k.gap(gauge, 3);
    }
    k.add(vault, gauge, -1, -2);
    k.gap(vault, 7);
    k.add(
      vault,
      k.text(count + " / 250   ·   " + t("EXPLICIT ENTRIES", "ورودی‌های ذخیره‌شده"), 10, k.muted, 2),
      -1,
      -2
    );
    k.add(v, vault, -1, -2);
    k.gap(v, 10);
    LinearLayout actions = k.row();
    k.weight(
      actions,
      k.button(t("＋ New entry", "＋ ورودی جدید"), true, () -> a.controls.entry(null)),
      -2
    );
    k.gap(actions, 8);
    k.weight(
      actions,
      k.button(t("Privacy controls", "کنترل حریم خصوصی"), false, a.controls::privacy),
      -2
    );
    k.add(v, actions, -1, -2);
    k.gap(v, 13);
    if (!db.memoryOn()) {
      empty(
        v,
        "lock_keyhole",
        t("Memory paused", "حافظه متوقف است"),
        t(
          "Entries are not used or displayed until re-enabled.",
          "تا فعال‌سازی مجدد از داده‌ها استفاده نمی‌شود."
        )
      );
    } else {
      LinearLayout r = chips(v);
      for (String kind : Arrays.asList("all", "note", "shortcut", "app_alias", "contact_alias"))
        chip(r, a.controls.kind(kind), memoryFilter.equals(kind), () -> {
          memoryFilter = kind;
          a.render(false);
        });
      List<Map<String, String>> entries = memoryFilter.equals("all")
        ? db.rows("SELECT * FROM memory ORDER BY stamp DESC")
        : db.rows("SELECT * FROM memory WHERE kind=? ORDER BY stamp DESC", memoryFilter);
      if (entries.isEmpty()) empty(
        v,
        "notebook_pen",
        t("A clean slate", "هنوز ورودی‌ای نیست"),
        t(
          "Save a note, give an app an alias, or create a reusable command.",
          "یادداشت، نام مستعار یا فرمان تکرارشونده بسازید."
        )
      );
      for (Map<String, String> m : entries) {
        String kind = m.get("kind");
        k.item(
          v,
          kind.equals("shortcut") ? "workflow" : "book_open",
          Kit.LILAC,
          m.get("name"),
          a.controls.kind(kind) + "  ·  " + Text.clipped(m.get("value"), 90),
          () -> a.controls.entry(m)
        );
      }
    }
    k.section(v, t("Scheduled reminders", "یادآورهای زمان‌بندی‌شده"), "LOCAL");
    List<Map<String, String>> pending = db.rows("SELECT * FROM reminders ORDER BY stamp");
    if (pending.isEmpty()) empty(
      v,
      "calendar_clock",
      t("No reminders scheduled", "یادآوری ثبت نشده"),
      t("Choose Set reminder from Home.", "از خانه، تنظیم یادآور را انتخاب کنید.")
    );
    for (Map<String, String> m : pending)
      k.item(v, "bell_ring", Kit.AMBER, m.get("text"), stamp(m.get("stamp")), () ->
        a.approval(t("Remove reminder?", "حذف یادآور؟"), m.get("text"), ok -> {
          if (ok) {
            ai.nexus.automation.Reminders.cancel(a, db, Long.parseLong(m.get("id")));
            a.changed();
          }
        })
      );
  }
}
