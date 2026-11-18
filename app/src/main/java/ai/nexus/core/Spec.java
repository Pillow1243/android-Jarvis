package ai.nexus.core;

import java.util.*;
import org.json.*;

/** A closed, flat schema: extra fields, blank values, wrong types and unknown enums fail closed. */
public final class Spec {

  public final String id, category, title, fa, description, permission, verification;
  public final int risk, timeoutMs;
  public final boolean cloud;
  public final Map<String, String> fields;
  public final List<String> errorCodes;

  public Spec(
    String id,
    String cat,
    String en,
    String fa,
    String desc,
    String permission,
    int risk,
    boolean cloud,
    String verify,
    String... fields
  ) {
    this.id = id;
    category = cat;
    title = en;
    this.fa = fa;
    description = desc;
    this.permission = permission;
    this.risk = risk;
    this.cloud = cloud;
    verification = verify;
    timeoutMs = 120000;
    Map<String, String> schema = new LinkedHashMap<>();
    for (int i = 0; i < fields.length; i += 2) schema.put(fields[i], fields[i + 1]);
    this.fields = Collections.unmodifiableMap(schema);
    List<String> errors = new ArrayList<>(
      Arrays.asList(
        "invalid_arguments",
        "duplicate_blocked",
        "execution_error",
        "permission_error",
        "timeout_unknown",
        "prerequisite_failed"
      )
    );
    if (risk >= 2 || !permission.isEmpty() || id.equals("launch_app")) errors.add("not_authorized");
    if (
      Arrays.asList(
        "open_panel",
        "open_interface",
        "open_url",
        "web_search",
        "maps",
        "launch_app",
        "dial_contact",
        "compose_sms",
        "compose_email",
        "share_text",
        "display_walkthrough"
      ).contains(id)
    ) errors.add("no_handler");
    switch (id) {
      case "previous_app":
        errors.add("back_unavailable");
        break;
      case "find_app":
      case "launch_app":
        errors.add("app_not_found");
        break;
      case "dial_contact":
      case "compose_sms":
        errors.add("invalid_number");
      case "find_contact":
        errors.add("contact_not_found");
        break;
      case "open_url":
        errors.add("invalid_url");
        break;
      case "compose_email":
        errors.add("invalid_email");
        break;
      case "recall_note":
        errors.add("memory_paused");
        break;
      case "set_reminder":
        errors.addAll(Arrays.asList("invalid_time", "notification_blocked"));
      case "save_note":
        errors.add("verify_failed");
        break;
      case "display_walkthrough":
        errors.addAll(Arrays.asList("access_disabled", "access_unavailable", "scroll_unverified"));
        break;
      default:
        break;
    }
    errorCodes = Collections.unmodifiableList(errors);
  }

  public String validate(Call c) {
    if (
      !id.equals(c.name) || !fields.keySet().equals(c.args.keySet())
    ) return "Unexpected or missing fields";
    for (String k : fields.keySet()) {
      String v = c.get(k);
      int cap = k.equals("text") ? 1200 : 240;
      if (
        v.trim().isEmpty() ||
        v.length() > cap ||
        v.matches("(?s).*[\\x00-\\x08\\x0B-\\x1F\\x7F\\u202A-\\u202E\\u2066-\\u2069].*")
      ) return "Invalid field: " + k;
      String f = fields.get(k);
      if (f.startsWith("|") && !Arrays.asList(f.substring(1).split("\\|")).contains(v)) return (
        "Unknown option: " + k
      );
    }
    return null;
  }

  public JSONObject declaration() {
    JSONObject props = J.obj();
    JSONArray req = J.arr();
    for (String k : fields.keySet()) {
      String f = fields.get(k);
      JSONObject p = J.obj("type", "STRING");
      if (f.startsWith("|")) J.put(p, "enum", new JSONArray(Arrays.asList(f.substring(1).split("\\|"))));
      else J.put(p, "description", f);
      J.put(props, k, p);
      req.put(k);
    }
    JSONObject d = J.obj("name", id, "description", description);
    if (!fields.isEmpty()) J.put(
      d,
      "parameters",
      J.obj("type", "OBJECT", "properties", props, "required", req)
    );
    return d;
  }

  public JSONObject outputSchema() {
    return J.obj(
      "type",
      "object",
      "required",
      J.arr("state", "evidence", "metrics"),
      "properties",
      J.obj(
        "state",
        J.obj("enum", J.arr("verified", "handed_off", "failed", "declined", "skipped")),
        "evidence",
        J.obj("type", "string"),
        "metrics",
        J.obj("type", "object")
      )
    );
  }
}
