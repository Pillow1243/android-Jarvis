package ai.nexus.core;

/** Pure decision table; actual permission grants are always queried from Android immediately before dispatch. */
public final class PermissionPolicy {

  private PermissionPolicy() {}

  public static boolean runtimeRequired(Spec spec, Call call, int sdk) {
    String p = spec.permission;
    return (
      !p.isEmpty() &&
      !p.equals("accessibility") &&
      !(p.equals("android.permission.POST_NOTIFICATIONS") && sdk < 33) &&
      !(p.equals("android.permission.READ_CONTACTS") &&
        call.args.containsKey("recipient") &&
        Text.phone(call.get("recipient")))
    );
  }
}
