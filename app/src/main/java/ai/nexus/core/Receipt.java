package ai.nexus.core;

import org.json.JSONObject;

/** Display evidence stays on-device. Only the fixed code and explicitly safe metrics reach the model. */
public final class Receipt {

  public final String state, detail, code;
  public final JSONObject metrics;

  private Receipt(String s, String d, String c, JSONObject m) {
    state = s;
    detail = d;
    code = c;
    metrics = m;
  }

  public static Receipt verified(String detail, String code, JSONObject m) {
    return new Receipt("verified", detail, code, m);
  }

  public static Receipt handoff(String detail) {
    return new Receipt("handed_off", detail, "android_accepted", J.obj());
  }

  public static Receipt failed(String detail, String code) {
    return new Receipt("failed", detail, code, J.obj());
  }

  public static Receipt skipped(String detail) {
    return new Receipt("skipped", detail, "prerequisite_failed", J.obj());
  }

  public static Receipt denied(String detail) {
    return new Receipt("declined", detail, "not_authorized", J.obj());
  }

  public JSONObject forModel() {
    return J.obj("state", state, "evidence", code, "metrics", metrics);
  }

  public boolean ok() {
    return state.equals("verified") || state.equals("handed_off");
  }
}
