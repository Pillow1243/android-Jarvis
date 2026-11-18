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

  private volatile MainActivity a;
  private UiAutomation auto;
  private File evidence;
  private final List<String> results = new ArrayList<>();
  private int checks, failures, launcherWaits;

  // Fresh software-rendered Pixel images can display a stock-launcher ANR over the target.
  // Acknowledge ONLY that exact foreign dialog via Android's normal Wait control. Never hide or
  // acknowledge an app-under-test ANR/crash, a permission prompt, or a security dialog here.
  private boolean waitForLauncher() throws Exception {
    if (launcherWaits >= 2) return false;
    AccessibilityNodeInfo root = auto.getRootInActiveWindow();
    if (
      root == null ||
      !"android".contentEquals(root.getPackageName() == null ? "" : root.getPackageName())
    ) return false;
    if (
      find(root, "Pixel Launcher isn't responding") == null &&
      find(root, "Pixel Launcher isn’t responding") == null
    ) return false;
    AccessibilityNodeInfo button = find(root, "Wait");
    if (button == null || !button.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return false;
    launcherWaits++;
    try (
      PrintWriter log = new PrintWriter(
        new FileOutputStream(new File(evidence, "platform-dialogs.txt"), true)
      )
    ) {
      log.println(
        "Pixel Launcher ANR over test app: chose system Wait (" +
          launcherWaits +
          "). No Nexus error suppressed."
      );
    }
    SystemClock.sleep(500);
    return true;
  }

  private interface Scenario {
    void run() throws Exception;
  }

  private void phase(String name) throws IOException {
    try (PrintWriter w = new PrintWriter(new File(evidence, "scenario-progress.txt"), "UTF-8")) {
      w.println(name);
    }
    Bundle b = new Bundle();
    b.putString("nexus_phase", name);
    sendStatus(1, b);
  }

  @Override
  public void callActivityOnResume(Activity activity) {
    super.callActivityOnResume(activity);
    if (activity instanceof MainActivity) a = (MainActivity) activity;
  }

  private void scenario(String name, Scenario task) throws Exception {
    phase(name);
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
    if (Looper.myLooper() == Looper.getMainLooper()) f.run();
    else new Handler(Looper.getMainLooper()).post(f);
    try {
      return f.get(10, java.util.concurrent.TimeUnit.SECONDS);
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
      if (waitForLauncher()) end += 2500;
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
      auto = getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
      auto.setRotation(UiAutomation.ROTATION_FREEZE_0);
      evidence = new File(getTargetContext().getFilesDir(), "evidence");
      evidence.mkdirs();
      Intent launch = new Intent(getTargetContext(), MainActivity.class).addFlags(
        Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK
      );
      phase("launch");
      getTargetContext().startActivity(launch);
      waitFor(() -> a != null && ui(() -> a.indexed && a.active), "foreground application index");
      phase("navigation");
      programTests();
      navigation();
      scenario("sequence", this::sequence);
      scenario("memory", this::memory);
      scenario("contacts", this::contacts);
      scenario("platform", this::platform);
      scenario("handoffs", this::handoffs);
      scenario("cloud", this::cloudContract);
      scenario("repair", this::agentRepair);
      scenario("lifecycle", this::lifecycle);
      scenario("network", this::testDeviceNetwork);
      scenario("accessibility", this::accessibilityFlow);
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
        s.timeoutMs > 0 &&
          s.outputSchema().has("properties") &&
          !s.verification.isEmpty() &&
          s.errorCodes.contains("timeout_unknown"),
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
    node("nav_agent");
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
    shell("am start --activity-reorder-to-front --activity-single-top -n ai.nexus/.MainActivity");
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
    String untrusted = "REFERENCE_SENTINEL";
    JSONArray first = J.arr(Gemini.user("actual task"), stream.content);
    JSONObject wire = Gemini.plannerRequest(first, a.tools.declarations(), untrusted, "English");
    ok(
      !wire.getJSONObject("systemInstruction").toString().contains(untrusted) &&
        wire
          .getJSONArray("contents")
          .getJSONObject(0)
          .getJSONArray("parts")
          .getJSONObject(0)
          .getString("text")
          .contains(untrusted),
      "Untrusted context remains out of system instructions"
    );
    ok(
      !first.getJSONObject(0).toString().contains(untrusted) &&
        wire.getJSONArray("contents").getJSONObject(1).toString().equals(stream.content.toString()),
      "History is immutable and signature content is preserved"
    );
    try {
      Gemini.readStream(
        new BufferedReader(new StringReader(new String(new char[270000]).replace('\0', 'x'))),
        q -> {}
      );
      throw new AssertionError("Unbounded line accepted");
    } catch (Gemini.Failure size) {
      ok(size.code.equals("oversize"), "Unterminated oversized stream fails closed");
    }
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

  private void agentRepair() throws Exception {
    int[] round = { 0 };
    Provider fixture = new Provider() {
      public void cancel() {}

      public JSONObject turn(
        JSONArray history,
        JSONArray decl,
        String ctx,
        String lang,
        Consumer<String> partial
      ) throws Exception {
        ++round[0];
        if (round[0] == 1) return J.obj(
          "role",
          "model",
          "parts",
          J.arr(
            J.obj(
              "functionCall",
              J.obj("name", "find_app", "args", J.obj("name", "NEXUS_MISSING_38942"), "id", "lookup")
            ),
            J.obj(
              "functionCall",
              J.obj("name", "open_panel", "args", J.obj("panel", "wifi"), "id", "would_launch")
            )
          )
        );
        if (round[0] == 2) {
          JSONArray p = history.getJSONObject(history.length() - 1).getJSONArray("parts");
          JSONObject skipped = p.getJSONObject(1).getJSONObject("functionResponse");
          if (
            !"would_launch".equals(skipped.getString("id")) ||
            !"skipped".equals(skipped.getJSONObject("response").getString("state"))
          ) throw new AssertionError("Dependent call not paired with skip receipt");
          return J.obj(
            "role",
            "model",
            "parts",
            J.arr(J.obj("functionCall", J.obj("name", "list_apps", "args", J.obj())))
          );
        }
        return J.obj("role", "model", "parts", J.arr(J.obj("text", "End fixture")));
      }
    };
    Agent test = ui(() -> new Agent(a, a.db, new ToolBox(), fixture));
    try {
      action(() -> test.submit("Use the repair fixture", true));
      waitFor(() -> ui(() -> !test.running), "observation-based repair");
      ok(
        round[0] == 3 &&
          test.stage.equals("partial") &&
          test.steps.get(1).state.equals("skipped") &&
          test.steps.get(2).code.equals("registry_read"),
        "Failed real lookup skips the entire batch tail; new turn can inspect registry"
      );
      ok(
        pkg().equals("ai.nexus") &&
          a.db.scalar(
            "SELECT count(*) FROM events WHERE step=? AND state='attempted'",
            Long.toString(test.steps.get(1).row)
          ) ==
          0,
        "Dependent system panel was never attempted"
      );
      record(
        "Repair fixture on device: missing app, skipped dependent OS launch with matched function ID, real registry lookup on next model turn"
      );
    } finally {
      action(test::close);
    }
  }

  private String external(String regex, String explanation) throws Exception {
    String[] seen = { "" };
    waitFor(() -> (seen[0] = pkg()).matches(regex), explanation);
    return seen[0];
  }

  private void handoffs() throws Exception {
    String router = "android|com\\.android\\.intentresolver";
    action(() -> a.submit("open Chrome then search YouTube for OpenJarvis", false));
    String first = external("com.android.chrome", "real launched Chrome foreground");
    returnToNexus();
    ok(node("youtube\nOpenJarvis") != null, "Exact query before external transfer");
    click("Approve action");
    stage("handoff");
    String search = external(
      router + "|com.android.chrome|com.google.android.youtube",
      "search destination or Android resolver"
    );
    returnToNexus();
    action(() -> a.submit("منو به نزدیک‌ترین داروخانه ببر", false));
    ok(node("Maps · route\nنزدیک‌ترین داروخانه") != null, "Exact Persian route query");
    click("Approve action");
    stage("handoff");
    String maps = external(
      router + "|com.android.chrome|com.google.android.apps.maps",
      "route intent destination"
    );
    returnToNexus();
    action(() -> a.run(new Call("compose_sms", "recipient", "+12025550101", "text", "Unsent fixture")));
    ok(node("Number\n+12025550101\n\nUnsent fixture") != null, "Native SMS exact preview");
    click("Approve action");
    stage("handoff");
    String sms = external(
      router + "|com.google.android.apps.messaging|com.android.mms|com.android.messaging",
      "SMS composer or system resolver"
    );
    returnToNexus();
    // Observe without intercepting: returning null lets the real Android startActivity run.
    java.util.concurrent.atomic.AtomicReference<Intent> sent =
      new java.util.concurrent.atomic.AtomicReference<>();
    Instrumentation.ActivityMonitor watch = new Instrumentation.ActivityMonitor() {
      @Override
      public ActivityResult onStartActivity(Intent intent) {
        if (
          Intent.ACTION_SENDTO.equals(intent.getAction()) && "mailto".equals(intent.getScheme())
        ) sent.set(new Intent(intent));
        return null;
      }
    };
    addMonitor(watch);
    action(() ->
      a.run(
        new Call(
          "compose_email",
          "to",
          "qa@example.invalid",
          "subject",
          "Nexus QA",
          "text",
          "Unsent fixture"
        )
      )
    );
    waitFor(
      () ->
        ui(
          () ->
            a.agent.stage.equals("approval") || a.agent.stage.equals("clarifying") || !a.agent.running
        ),
      "resolved mail applications"
    );
    Map<String, String> clients = new LinkedHashMap<>();
    PackageManager pm = a.getPackageManager();
    Set<String> smsApps = new HashSet<>(),
      emailApps = new HashSet<>();
    for (android.content.pm.ResolveInfo r : pm.queryIntentActivities(
      new Intent(Intent.ACTION_SENDTO, android.net.Uri.parse("smsto:")),
      PackageManager.MATCH_DEFAULT_ONLY
    ))
      smsApps.add(r.activityInfo.packageName);
    for (android.content.pm.ResolveInfo r : pm.queryIntentActivities(
      new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_EMAIL),
      0
    ))
      emailApps.add(r.activityInfo.packageName);
    for (android.content.pm.ResolveInfo r : pm.queryIntentActivities(
      new Intent(Intent.ACTION_SENDTO, android.net.Uri.parse("mailto:qa@example.invalid")),
      PackageManager.MATCH_DEFAULT_ONLY
    )) {
      String p = r.activityInfo.packageName;
      if (
        r.activityInfo.exported &&
        r.activityInfo.enabled &&
        (!smsApps.contains(p) || emailApps.contains(p))
      ) clients.put(p, r.activityInfo.applicationInfo.loadLabel(pm) + "\n" + p);
    }
    String email;
    if (!ui(() -> a.agent.running)) {
      ok(
        clients.isEmpty() && a.agent.steps.get(0).code.equals("no_handler") && sent.get() == null,
        "No eligible mail client: safe failure, no email intent or SMS fallback"
      );
      email = "NOT HANDED OFF · no eligible mail client";
    } else {
      if (ui(() -> a.agent.stage.equals("clarifying"))) {
        node("Choose an email application");
        String selected = null;
        for (Map.Entry<String, String> e : clients.entrySet()) {
          if (find(auto.getRootInActiveWindow(), e.getValue()) != null) {
            selected = e.getValue();
            if (e.getKey().equals("com.google.android.gm")) break;
          }
        }
        ok(
          selected != null,
          "The visible app choice is a registered email handler, not an SMS-only client"
        );
        click(selected);
      }
      node("Approve action");
      String reviewed = null;
      for (String p : clients.keySet()) {
        if (
          find(
            auto.getRootInActiveWindow(),
            "qa@example.invalid\nNexus QA\n\nUnsent fixture\n\nDestination app: " + p
          ) !=
          null
        ) {
          reviewed = p;
          break;
        }
      }
      ok(reviewed != null, "Exact email recipient, subject, body and eligible destination package");
      click("Approve action");
      stage("handoff");
      Intent actual = sent.get();
      ok(
        actual != null &&
          reviewed.equals(actual.getPackage()) &&
          "mailto:qa@example.invalid?subject=Nexus%20QA&body=Unsent%20fixture".equals(
            actual.getDataString()
          ),
        "Non-intercepted native email dispatch matches reviewed package and draft"
      );
      long until = SystemClock.uptimeMillis() + 4000;
      do {
        email = pkg();
        if (email.equals(reviewed)) break;
        SystemClock.sleep(150);
      } while (SystemClock.uptimeMillis() < until);
      email =
        "dispatched to " +
        reviewed +
        ", visible=" +
        (email.isEmpty() ? "not observed" : email) +
        " (mailbox not verified)";
      returnToNexus();
    }
    removeMonitor(watch);
    action(() -> a.result("device report", true));
    ok(
      node("command_text") != null && node("device report") != null && !a.agent.running,
      "Uncertain speech recognition result enters editable review, not dispatch"
    );
    click("Cancel");
    waitFor(
      () -> pkg().equals("ai.nexus") && find(auto.getRootInActiveWindow(), "command_text") == null,
      "review dismissed without an action"
    );
    record(
      "Real foreground handoffs: Chrome sequence=" +
        first +
        ", YouTube query=" +
        search +
        ", Persian pharmacy route=" +
        maps +
        ", SMS=" +
        sms +
        ", email=" +
        email +
        ". No send, account sign-in, route selection or search result claimed. Low-confidence transcript review tested via callback, not audio."
    );
  }

  private String serviceName() {
    return a.getString(ai.nexus.R.string.service_name);
  }

  private boolean enterServiceSettings() throws Exception {
    action(a.controls::access);
    click("Open Android accessibility settings");
    waitFor(() -> pkg().equals("com.android.settings"), "public accessibility settings");
    for (int i = 0; i < 6; i++) {
      if (find(auto.getRootInActiveWindow(), serviceName()) != null) {
        click(serviceName());
        return true;
      }
      if (find(auto.getRootInActiveWindow(), "Downloaded apps") != null && i == 0) {
        click("Downloaded apps");
        continue;
      }
      android.util.DisplayMetrics dm = a.getResources().getDisplayMetrics();
      int cx = dm.widthPixels / 2;
      shell(
        "input swipe " +
          cx +
          " " +
          (dm.heightPixels * 3) / 4 +
          " " +
          cx +
          " " +
          dm.heightPixels / 3 +
          " 300"
      );
      SystemClock.sleep(200);
    }
    return false;
  }

  private String toggleLabel() {
    for (String label : Arrays.asList("Use " + serviceName(), "Use service"))
      if (find(auto.getRootInActiveWindow(), label) != null) return label;
    return "";
  }

  private String waitToggle() {
    long until = SystemClock.uptimeMillis() + 4500;
    String label;
    do {
      label = toggleLabel();
      if (!label.isEmpty()) return label;
      SystemClock.sleep(150);
    } while (SystemClock.uptimeMillis() < until);
    return "";
  }

  private void accessibilityFlow() throws Exception {
    List<String> log = new ArrayList<>();
    boolean enabled = false;
    try {
      // No secure settings writes, automatic grants, or bypass of Android's restricted settings.
      if (!enterServiceSettings()) {
        log.add("NOT VERIFIED: service not offered by Android settings.");
        return;
      }
      String toggle = waitToggle();
      if (toggle.isEmpty()) {
        log.add("NOT VERIFIED: Android did not offer the service toggle.");
        return;
      }
      click(toggle);
      SystemClock.sleep(400);
      if (find(auto.getRootInActiveWindow(), "Allow") != null) click("Allow");
      if (!ui(() -> ai.nexus.accessibility.WindowReceiptService.connected())) SystemClock.sleep(900);
      enabled = ui(() -> ai.nexus.accessibility.WindowReceiptService.connected());
      if (!enabled) {
        log.add(
          "NOT VERIFIED: no OS service connection after the visible opt-in; restriction not bypassed."
        );
        return;
      }
      log.add("Verified: opt-in via in-app disclosure, Android settings switch and OS confirmation.");
      returnToNexus();
      action(() -> a.run(new Call("open_panel", "panel", "display")));
      waitFor(() -> ui(() -> !a.agent.running), "observed settings window");
      log.add("Open Display: " + ui(() -> a.agent.steps.get(0).code));
      returnToNexus();
      action(() -> a.run(new Call("display_walkthrough")));
      click("Approve action");
      waitFor(() -> ui(() -> !a.agent.running), "manual Display scroll result");
      log.add("Manual Display walkthrough: " + ui(() -> a.agent.steps.get(0).code));
      returnToNexus();
    } finally {
      try {
        if (enabled) {
          if (!pkg().equals("ai.nexus")) returnToNexus();
          ok(enterServiceSettings(), "Service available for opt-out");
          click(waitToggle());
          SystemClock.sleep(300);
          for (String label : Arrays.asList("Stop", "Turn off", "OK"))
            if (find(auto.getRootInActiveWindow(), label) != null) {
              click(label);
              break;
            }
          waitFor(
            () -> ui(() -> !ai.nexus.accessibility.WindowReceiptService.connected()),
            "user opt-out"
          );
          log.add("Verified: service disconnected after native opt-out.");
        }
      } finally {
        try (PrintWriter w = new PrintWriter(new File(evidence, "accessibility.txt"), "UTF-8")) {
          for (String l : log) w.println(l);
        }
        if (!pkg().equals("ai.nexus")) returnToNexus();
      }
    }
  }

  private void testDeviceNetwork() throws Exception {
    // Exercise the shipped Android TLS client, using a deliberately invalid synthetic credential.
    // Never provision a real API key to an emulator artifact, screenshot or instrumentation bundle.
    int uid = android.os.Process.myUid();
    long rx = android.net.TrafficStats.getUidRxBytes(uid),
      tx = android.net.TrafficStats.getUidTxBytes(uid),
      start = SystemClock.elapsedRealtime();
    String result;
    try {
      a.vault.save("INVALID_QA_FIXTURE_NOT_A_REAL_KEY");
      a.gemini.probe();
      throw new AssertionError("Invalid credential passed");
    } catch (Gemini.Failure e) {
      result = e.code;
      ok(
        Arrays.asList("configuration", "offline", "timeout", "temporary", "quota").contains(result),
        "Handled provider failure"
      );
    } finally {
      a.vault.clear();
    }
    long endRx = android.net.TrafficStats.getUidRxBytes(uid),
      endTx = android.net.TrafficStats.getUidTxBytes(uid);
    try (PrintWriter w = new PrintWriter(new File(evidence, "device-network.txt"), "UTF-8")) {
      w.println(
        "probe=invalid_credential\nclient=installed_android_https\nresult=" +
          result +
          "\nverified=false\nelapsed_ms=" +
          (SystemClock.elapsedRealtime() - start) +
          "\nuid_received_bytes_delta=" +
          (rx < 0 || endRx < 0 ? "unavailable" : endRx - rx) +
          "\nuid_transmitted_bytes_delta=" +
          (tx < 0 || endTx < 0 ? "unavailable" : endTx - tx)
      );
    }
    ok(
      !a.vault.present() && !a.db.prefs.getBoolean("verified", false),
      "No credential or false verified state after negative probe"
    );
    record(
      "Actual Android Gemini client with intentionally invalid key: " +
        result +
        "; not an authenticated model success"
    );
  }

  private boolean compactOrb(android.view.View v) {
    if (v instanceof ai.nexus.ui.SignalField) return v.getHeight() > 0 && v.getHeight() <= a.k.d(150);
    if (v instanceof android.view.ViewGroup) {
      android.view.ViewGroup group = (android.view.ViewGroup) v;
      for (int i = 0; i < group.getChildCount(); i++) if (compactOrb(group.getChildAt(i))) return true;
    }
    return false;
  }

  private void lifecycle() throws Exception {
    action(() -> a.run(new Call("share_text", "text", "No external effect")));
    node("Approve action");
    action(() -> a.agent.cancel());
    stage("cancelled");
    ok(pkg().equals("ai.nexus"), "Cancellation did not dispatch chooser");
    action(() -> a.page("home"));
    auto.setRotation(UiAutomation.ROTATION_FREEZE_90);
    waitFor(
      () ->
        ui(
          () ->
            a.getResources().getConfiguration().orientation ==
              android.content.res.Configuration.ORIENTATION_LANDSCAPE &&
            compactOrb(a.getWindow().getDecorView())
        ),
      "compact landscape after actual relayout"
    );
    auto.waitForIdle(300, 4000);
    SystemClock.sleep(450);
    ok(ui(() -> a.tab.equals("home") && a.active), "Rotation retains screen");
    shot("landscape");
    auto.setRotation(UiAutomation.ROTATION_FREEZE_0);
    action(() -> {
      a.db.prefs.edit().putString("language", "fa").apply();
      a.page("home");
      a.submit("گزارش دستگاه و بعد فهرست برنامه‌ها", false);
    });
    stage("completed");
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
