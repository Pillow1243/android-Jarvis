package ai.nexus.test;

import ai.nexus.*;
import ai.nexus.agent.Agent;
import ai.nexus.ai.*;
import ai.nexus.core.*;
import ai.nexus.device.*;
import ai.nexus.memory.*;
import ai.nexus.tools.ToolBox;
import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.*;
import android.view.accessibility.AccessibilityNodeInfo;
import java.io.*;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.function.*;
import org.json.*;

/** Runs inside the actual installed application; use adb shell am instrument -w. No test hooks in production. */
public final class NexusInstrumentation extends Instrumentation {

  private MainActivity a;
  private UiAutomation auto;
  private File evidence;
  private final List<String> results = new ArrayList<>();
  private int checks, failures;

  private interface Scenario {
    void run() throws Exception;
  }

  private void scenario(String name, Scenario task) throws Exception {
    try {
      task.run();
    } catch (Throwable error) {
      failures++;
      StringWriter trace = new StringWriter();
      error.printStackTrace(new PrintWriter(trace));
      results.add("FAIL  " + name + ": " + trace);
      write();
      try {
        shot("failed-" + name);
        returnToNexus();
        action(() -> {
          a.agent.cancel();
          a.dismissGate();
          a.page("home");
        });
      } catch (Throwable recovery) {
        results.add("RECOVERY  " + recovery.getClass().getSimpleName());
      }
    }
  }

  @Override
  public void onCreate(Bundle args) {
    super.onCreate(args);
    start();
  }

  private void ok(boolean truth, String message) {
    if (!truth) throw new AssertionError(message);
    checks++;
  }

  private <T> T ui(Callable<T> op) {
    java.util.concurrent.FutureTask<T> f = new java.util.concurrent.FutureTask<>(op);
    runOnMainSync(f);
    try {
      return f.get();
    } catch (Exception e) {
      throw new AssertionError(e);
    }
  }

  private void action(Runnable r) {
    ui(() -> {
      r.run();
      return null;
    });
  }

  private void waitFor(Callable<Boolean> test, String label) throws Exception {
    long end = SystemClock.uptimeMillis() + 13000;
    do {
      if (test.call()) return;
      SystemClock.sleep(100);
    } while (SystemClock.uptimeMillis() < end);
    throw new AssertionError("Timed out: " + label);
  }

  private void stage(String expect) throws Exception {
    waitFor(() -> ui(() -> !a.agent.running), "task finish");
    ok(
      ui(() -> a.agent.stage.equals(expect)),
      "Expected " + expect + ", got " + ui(() -> a.agent.stage) + ": " + ui(() -> a.agent.report)
    );
  }

  private AccessibilityNodeInfo find(AccessibilityNodeInfo n, String term) {
    if (n == null) return null;
    if (
      term.contentEquals(n.getContentDescription() == null ? "" : n.getContentDescription()) ||
      term.contentEquals(n.getText() == null ? "" : n.getText())
    ) return n;
    for (int i = 0; i < n.getChildCount(); i++) {
      AccessibilityNodeInfo f = find(n.getChild(i), term);
      if (f != null) return f;
    }
    return null;
  }

  private AccessibilityNodeInfo node(String term) throws Exception {
    final AccessibilityNodeInfo[] found = { null };
    waitFor(
      () -> (found[0] = find(auto.getRootInActiveWindow(), term)) != null,
      "visible node: " + term
    );
    return found[0];
  }

  private void click(String label) throws Exception {
    AccessibilityNodeInfo n = node(label);
    while (n != null && !n.isClickable()) n = n.getParent();
    ok(n != null && n.performAction(AccessibilityNodeInfo.ACTION_CLICK), "Couldn't click " + label);
    SystemClock.sleep(180);
  }

  private void type(String label, String text) throws Exception {
    Bundle b = new Bundle();
    b.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text);
    ok(node(label).performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, b), "Couldn't enter " + label);
  }

  private String pkg() {
    AccessibilityNodeInfo n = auto.getRootInActiveWindow();
    return n == null ? "" : String.valueOf(n.getPackageName());
  }

  private String shell(String command) throws Exception {
    try (
      ParcelFileDescriptor fd = auto.executeShellCommand(command);
      InputStream in = new ParcelFileDescriptor.AutoCloseInputStream(fd);
      ByteArrayOutputStream out = new ByteArrayOutputStream()
    ) {
      byte[] buf = new byte[4096];
      int n;
      while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
      return out.toString("UTF-8");
    }
  }

  private void shot(String name) throws Exception {
    SystemClock.sleep(350);
    Bitmap b = auto.takeScreenshot();
    ok(b != null, "Screenshot " + name);
    try (FileOutputStream out = new FileOutputStream(new File(evidence, name + ".png"))) {
      b.compress(Bitmap.CompressFormat.PNG, 100, out);
    }
    b.recycle();
  }

  private void record(String msg) throws Exception {
    results.add("PASS  " + msg);
    write();
  }

  private void write() throws Exception {
    try (PrintWriter w = new PrintWriter(new File(evidence, "instrumentation.txt"), "UTF-8")) {
      for (String line : results) w.println(line);
      w.println("Assertions: " + checks);
    }
  }

  @Override
  public void onStart() {
    Bundle result = new Bundle();
    try {
      auto = getUiAutomation();
      auto.setRotation(UiAutomation.ROTATION_FREEZE_0);
      evidence = new File(getTargetContext().getFilesDir(), "evidence");
      evidence.mkdirs();
      Intent launch = new Intent(getTargetContext(), MainActivity.class).addFlags(
        Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK
      );
      a = (MainActivity) startActivitySync(launch);
      waitFor(() -> ui(() -> a.indexed && a.active), "foreground application index");
      programTests();
      navigation();
      scenario("sequence", this::sequence);
      scenario("memory", this::memory);
      scenario("contacts", this::contacts);
      scenario("platform", this::platform);
      scenario("cloud", this::cloudContract);
      scenario("lifecycle", this::lifecycle);
      if (failures != 0) throw new AssertionError(
        failures + " device scenario(s) failed; see instrumentation.txt"
      );
      result.putString(
        "stream",
        "\nNEXUS_TESTS_PASSED\n" + String.join("\n", results) + "\nAssertions: " + checks + "\n"
      );
      finish(Activity.RESULT_OK, result);
    } catch (Throwable ex) {
      StringWriter sw = new StringWriter();
      ex.printStackTrace(new PrintWriter(sw));
      results.add("FAIL  " + sw);
      try {
        shot("failure");
        write();
      } catch (Throwable ignored) {}
      result.putString("stream", "\nNEXUS_TESTS_FAILED\n" + sw);
      finish(Activity.RESULT_CANCELED, result);
    }
  }

  private void programTests() throws Exception {
    LocalPlanner plan = new LocalPlanner();
    ok(plan.plan("جرویس، گزارش دستگاه و بعد فهرست برنامه‌ها", "").size() == 2, "Persian compound");
    ok(
      plan.plan("Open Spotify then invent something", "").isEmpty(),
      "No partial action from unknown clause"
    );
    ok(plan.plan("open it", "Spotify").get(0).get("name").equals("Spotify"), "app reference");
    ok(
      plan
        .plan("به مامانم پیام بده که نیم ساعت دیگه میام", "")
        .get(0)
        .get("text")
        .equals("نیم ساعت دیگه میام"),
      "Preserve Persian text"
    );
    ok(Text.contactMatches("مادر علي", "علی"), "Persian normalization");
    ok(!Text.phone("*123#") && Text.phone("+۱۲۰۲۵۵۵۰۱۰۱"), "No USSD / Persian numbers");
    ok(
      a.tools.specs.get("open_panel").validate(new Call("open_panel", "panel", "shell")) != null,
      "closed enum"
    );
    ok(
      a.tools.specs.get("device_report").validate(new Call("device_report", "junk", "x")) != null,
      "extra field rejected"
    );
    ok(
      !a.tools.declarations().toString().contains("display_walkthrough"),
      "No accessibility automation advertised to model"
    );
    for (Spec s : a.tools.specs.values())
      ok(
        s.timeoutMs > 0 && s.outputSchema().has("properties") && !s.verification.isEmpty(),
        "Complete tool descriptor"
      );
    ok(
      Gemini.status(429).code.equals("quota") && Gemini.status(404).code.equals("model_unavailable"),
      "HTTP error mapping"
    );
    ok(!Gemini.validModel("gemini-foo?key=evil"), "Model cannot inject request path");
    record("Planner, Farsi normalization, schemas, registry and error classification");
  }

  private void navigation() throws Exception {
    shot("01-home");
    for (String dest : Arrays.asList("agent", "activity", "tools", "memory")) {
      click("nav_" + dest);
      ok(ui(() -> a.tab.equals(dest)), "Navigate " + dest);
      shot(dest);
    }
    click("Settings");
    ok(ui(() -> a.tab.equals("settings")), "Settings navigation");
    shot("settings");
    click("Gemini provider");
    ok(node("model_id") != null, "Configurable provider");
    type("model_id", "invalid/id");
    click("Save configuration");
    ok(node("model_id") != null && !a.vault.present(), "Invalid provider config not saved");
    type("model_id", Gemini.DEFAULT_MODEL);
    type("api_key", "TEST_NEXUS_NOT_A_PROVIDER_KEY_01");
    click("Save configuration");
    ok(
      a.vault.present() && a.vault.read().equals("TEST_NEXUS_NOT_A_PROVIDER_KEY_01"),
      "Keystore round trip"
    );
    ok(
      !a.getSharedPreferences("vault", 0).getAll().toString().contains("TEST_NEXUS"),
      "No plaintext key in preferences"
    );
    a.vault.clear();
    record("Six native destinations; provider form validation, encrypted BYOK save/read/remove");
  }

  private void sequence() throws Exception {
    click("nav_home");
    click("command_input");
    type("command_text", "device report then list apps");
    click("Run command");
    stage("completed");
    ok(
      ui(
        () ->
          a.agent.steps.size() == 2 &&
          a.agent.steps.get(0).code.equals("device_read") &&
          a.agent.steps.get(1).code.equals("registry_read")
      ),
      "Real device and package observations"
    );
    click("nav_agent");
    shot("verified-sequence");
    ok(
      a.db.scalar("SELECT count(*) FROM steps WHERE state='verified'") >= 2,
      "SQLite receipts persisted"
    );
    ok(
      a.db.scalar(
          "SELECT count(*) FROM events WHERE step IN (SELECT id FROM steps WHERE task=?)",
          "" + a.agent.task
        ) ==
        8,
      "Four real transitions for each verified step"
    );
    action(() -> a.submit("device report then device report", false));
    stage("failed");
    ok(ui(() -> a.agent.steps.get(1).code.equals("duplicate_blocked")), "No duplicate dispatch");
    action(() -> a.run(new Call("launch_app", "name", "NEXUS_MISSING_38942")));
    stage("failed");
    ok(ui(() -> a.agent.steps.get(0).code.equals("app_not_found")), "Missing app failure");
    action(() -> a.submit("Could you explain the tides?", false));
    stage("failed");
    ok(ui(() -> a.agent.steps.isEmpty()), "No key freeform failure made no tool call");
    record(
      "Entered two-step instruction through UI; verified real OS reads, duplicate guard, absent app and missing key"
    );
  }

  private void memory() throws Exception {
    click("nav_memory");
    click("＋ New entry");
    type("memory_name", "Lab note");
    type("memory_value", "A non-secret local note");
    click("Save entry");
    Store reopened = new Store(a);
    try {
      ok(
        reopened.lookup("note", "Lab note").equals("A non-secret local note"),
        "New SQLite handle reads note"
      );
    } finally {
      reopened.close();
    }
    click("Lab note");
    type("memory_value", "Updated note");
    click("Save entry");
    ok(a.db.lookup("note", "Lab note").equals("Updated note"), "Edited note");
    long shortcut = a.db.save(-1, "shortcut", "briefing", "device report then list apps");
    a.db.pin(shortcut, true);
    ok(a.db.scalar("SELECT count(*) FROM memory WHERE pinned=1") == 1, "Pinned workflow");
    action(() -> a.run(new Call("recall_note", "query", "Updated")));
    stage("completed");
    ok(ui(() -> a.agent.report.contains("Updated note")), "On-device note retrieval");
    action(() -> a.submit("briefing", false));
    stage("completed");
    ok(ui(() -> a.agent.steps.size() == 2), "Shortcut uses real planner");
    a.db.prefs.edit().putBoolean("memory", false).commit();
    ok(a.db.lookup("note", "Lab note").isEmpty(), "Paused memory not retrieved");
    a.db.prefs.edit().putBoolean("memory", true).commit();
    try {
      a.db.save(-1, "note", "password", "example");
      throw new AssertionError("Accepted secret");
    } catch (IllegalArgumentException expected) {}
    click("Lab note");
    click("Delete entry");
    click("Approve action");
    ok(a.db.lookup("note", "Lab note").isEmpty(), "Individual entry deletion");
    record(
      "SQLite create/edit/read/delete, fresh handle persistence, paused memory, secret guard and runnable shortcut"
    );
  }

  private void contacts() throws Exception {
    action(() -> a.run(new Call("find_contact", "name", "مادر نکسوس")));
    click("Not now");
    stage("declined");
    ok(
      a.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED,
      "Denial respected"
    );
    action(() -> a.run(new Call("find_contact", "name", "مادر نکسوس")));
    click("Continue to Android");
    click("Allow");
    stage("completed");
    ok(
      ui(() -> a.agent.report.contains("+12025550101") && a.agent.report.contains("+12025550102")),
      "Contact provider returned both seeded contacts"
    );
    action(() -> a.run(new Call("dial_contact", "recipient", "مادر نکسوس")));
    click("مادر نکسوس یک\n+12025550101");
    ok(node("مادر نکسوس یک\n+12025550101") != null, "Exact recipient confirmation");
    click("Decline");
    stage("declined");
    ok(pkg().equals(a.getPackageName()), "No dialer before consent");
    action(() -> a.run(new Call("dial_contact", "recipient", "مادر نکسوس")));
    click("مادر نکسوس دو\n+12025550102");
    click("Approve action");
    stage("handoff");
    waitFor(
      () -> pkg().toLowerCase(Locale.ROOT).contains("dialer"),
      "actual dialer window after transition"
    );
    String dialer = pkg();
    ok(dialer.toLowerCase(Locale.ROOT).contains("dialer"), "Actual dialer package: " + dialer);
    returnToNexus();
    action(() ->
      a.run(new Call("compose_sms", "recipient", "+12025550101", "text", "Fixture only; do not send"))
    );
    ok(
      node("Number\n+12025550101\n\nFixture only; do not send") != null,
      "SMS exact body and number preview"
    );
    click("Decline");
    stage("declined");
    ok(
      !a.db.exportData().toString().contains("+1202555") &&
        !a.db.exportData().toString().contains("Fixture only"),
      "No numbers or bodies in journal/export"
    );
    record(
      "Real Android contacts grant/denial, Persian search, disambiguation, confirmed dialer handoff (" +
        dialer +
        "), SMS decline and redaction"
    );
  }

  private void returnToNexus() throws Exception {
    shell("am start -W --activity-reorder-to-front --activity-single-top -n ai.nexus/.MainActivity");
    waitFor(
      () -> ui(() -> a.active) && pkg().equals("ai.nexus"),
      "Nexus foreground after explicit user return"
    );
  }

  private void platform() throws Exception {
    String settings = a
      .getPackageManager()
      .resolveActivity(new Intent(android.provider.Settings.ACTION_SETTINGS), 0)
      .activityInfo.packageName;
    action(() -> a.run(new Call("launch_app", "name", settings)));
    stage("handoff");
    waitFor(() -> pkg().equals(settings), "Settings is actually the foreground package");
    returnToNexus();
    ok(!ai.nexus.accessibility.WindowReceiptService.connected(), "Window service off by default");
    action(() -> a.run(new Call("display_walkthrough")));
    stage("failed");
    action(() -> a.controls.access());
    ok(node("Open Android accessibility settings") != null, "Accessibility disclosure opt-in");
    click("Keep current setting");
    ok(!ai.nexus.accessibility.WindowReceiptService.connected(), "Disclosure did not enable service");
    action(() -> a.microphone());
    click("Not now");
    ok(
      a.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED &&
        !a.voice.listening,
      "No microphone without permission"
    );
    auto.grantRuntimePermission(a.getPackageName(), Manifest.permission.RECORD_AUDIO);
    boolean recognizer = android.speech.SpeechRecognizer.isRecognitionAvailable(a);
    action(() -> a.microphone());
    SystemClock.sleep(500);
    if (!recognizer) ok(ui(() -> !a.notice.isEmpty() && !a.voice.listening), "No-engine voice fallback");
    action(() -> a.voice.stop());
    if (Build.VERSION.SDK_INT >= 33) auto.grantRuntimePermission(
      a.getPackageName(),
      Manifest.permission.POST_NOTIFICATIONS
    );
    action(() -> a.run(new Call("set_reminder", "time", "23:48", "text", "Nexus test reminder")));
    click("Approve action");
    stage("completed");
    ok(
      a.db.scalar("SELECT count(*) FROM reminders WHERE text='Nexus test reminder'") == 1,
      "Alarm record"
    );
    ok(shell("dumpsys alarm").contains("ai.nexus.REMIND"), "Alarm actually registered with Android");
    long reminder = a.db.scalar("SELECT id FROM reminders LIMIT 1");
    ai.nexus.automation.Reminders.cancel(a, a.db, reminder);
    ok(a.db.scalar("SELECT count(*) FROM reminders") == 0, "Alarm cancellation");
    record(
      "Launchable app reached foreground: " +
        settings +
        "; accessibility opt-in, mic permission, voice engine available=" +
        recognizer +
        ", real AlarmManager registration/cancellation"
    );
  }

  private void cloudContract() throws Exception {
    String data =
      "data: {\"candidates\":[{\"content\":{\"parts\":[{\"functionCall\":{\"name\":\"device_report\",\"args\":{}},\"thoughtSignature\":\"opaque\"}]},\"finishReason\":\"STOP\"}],\"usageMetadata\":{\"totalTokenCount\":12}}\n";
    Gemini.StreamResult stream = Gemini.readStream(new BufferedReader(new StringReader(data)), q -> {});
    ok(
      stream.tokens == 12 && stream.content.toString().contains("opaque"),
      "SSE signature preservation"
    );
    try {
      Gemini.readStream(
        new BufferedReader(new StringReader(data.replace("STOP", "MAX_TOKENS"))),
        q -> {}
      );
      throw new AssertionError("incomplete accepted");
    } catch (Gemini.Failure expected) {}
    final int[] rounds = { 0 };
    Provider stub = new Provider() {
      public void cancel() {}

      public JSONObject turn(
        JSONArray history,
        JSONArray decl,
        String context,
        String lang,
        Consumer<String> partial
      ) throws Exception {
        rounds[0]++;
        if (rounds[0] == 1) return stream.content;
        if (rounds[0] == 2) {
          if (
            !history.toString().contains("device_read") || !history.toString().contains("opaque")
          ) throw new AssertionError("Missing actual functionResponse or signature");
          return J.obj(
            "role",
            "model",
            "parts",
            J.arr(J.obj("functionCall", J.obj("name", "list_apps", "args", J.obj(), "id", "fixture-id")))
          );
        }
        if (
          !history.toString().contains("registry_read") || !history.toString().contains("fixture-id")
        ) throw new AssertionError("Missing real package observation");
        return J.obj(
          "role",
          "model",
          "parts",
          J.arr(J.obj("text", "Fabricated success must not be displayed"))
        );
      }
    };
    Agent test = ui(() -> new Agent(a, a.db, new ToolBox(), stub));
    action(() -> test.submit("Use the conditional fixture", true));
    waitFor(() -> ui(() -> !test.running), "function loop");
    ok(
      rounds[0] == 3 &&
        test.steps.size() == 2 &&
        test.stage.equals("completed") &&
        !test.report.contains("Fabricated"),
      "Conditional Gemini-shaped function loop uses actual receipts"
    );
    action(test::close);
    int limit = a.db.prefs.getInt("limit", 30);
    a.db.prefs.edit().putInt("limit", 1).commit();
    ok(a.db.reserve() && !a.db.reserve(), "Atomic daily request budget");
    a.db.prefs.edit().putInt("limit", limit).commit();
    record(
      "Gemini wire-contract fixture (NOT live Gemini): partial-stream rejection, signatures, tool IDs, three planning rounds with real tool receipts, token parsing, quota guard"
    );
  }

  private void lifecycle() throws Exception {
    action(() -> a.run(new Call("share_text", "text", "No external effect")));
    node("Approve action");
    action(() -> a.agent.cancel());
    stage("cancelled");
    ok(pkg().equals("ai.nexus"), "Cancellation did not dispatch chooser");
    action(() -> a.page("home"));
    auto.setRotation(UiAutomation.ROTATION_FREEZE_90);
    SystemClock.sleep(600);
    ok(ui(() -> a.tab.equals("home") && a.active), "Rotation retains screen");
    shot("landscape");
    auto.setRotation(UiAutomation.ROTATION_FREEZE_0);
    action(() -> {
      a.db.prefs.edit().putString("language", "fa").apply();
      a.page("home");
    });
    waitFor(
      () ->
        ui(
          () ->
            a.getResources().getConfiguration().orientation ==
              android.content.res.Configuration.ORIENTATION_PORTRAIT &&
            a.fa &&
            a.getWindow().getDecorView().findViewById(android.R.id.content).getWidth() <
            a.getWindow().getDecorView().getHeight()
        ),
      "portrait Persian layout after rotation"
    );
    ok(node("مرکز فرمان") != null && node("خانه") != null, "Persian localized home and navigation");
    auto.waitForIdle(300, 4000);
    SystemClock.sleep(750);
    shot("persian-home");
    shell("input keyevent 3");
    waitFor(() -> ui(() -> !a.active), "app background");
    ok(!a.voice.listening, "No background listening");
    returnToNexus();
    action(() -> {
      a.db.prefs.edit().putString("language", "en").apply();
      a.page("home");
    });
    long test = a.db.task("Interrupted test");
    long step = a.db.step(test, "compose_sms");
    a.db.updateStep(step, "attempted", "dispatching");
    a.db.interruptOpen();
    ok(
      a.db.scalar("SELECT count(*) FROM tasks WHERE id=? AND state='interrupted'", "" + test) == 1 &&
        a.db.scalar("SELECT count(*) FROM steps WHERE id=? AND code='not_replayed'", "" + step) == 1,
      "Interrupted task persisted as not replayed"
    );
    record(
      "Cancellation, rotation, Persian RTL, foreground/background and interrupted journal recovery"
    );
  }
}
