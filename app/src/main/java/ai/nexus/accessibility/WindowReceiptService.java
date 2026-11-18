package ai.nexus.accessibility;

import android.accessibilityservice.*;
import android.os.*;
import android.view.accessibility.*;
import java.util.function.Consumer;

/** A short-lived, package-scoped receipt. No text/description getters, gesture injection or screenshots. */
public final class WindowReceiptService extends AccessibilityService {

  private static WindowReceiptService live;
  private final Handler main = new Handler(Looper.getMainLooper());
  private String expected = "";
  private boolean scroll, attempted;
  private Consumer<String> callback;

  public static boolean connected() {
    return live != null;
  }

  @Override
  protected void onServiceConnected() {
    live = this;
    reset();
  }

  private void filter(String pkg) {
    AccessibilityServiceInfo info = getServiceInfo();
    if (info != null) {
      info.packageNames = new String[] { pkg };
      setServiceInfo(info);
    }
  }

  private void reset() {
    main.removeCallbacksAndMessages(null);
    expected = "";
    attempted = false;
    scroll = false;
    callback = null;
    filter(getPackageName());
  }

  public static void disarm() {
    if (live != null) live.reset();
  }

  public static boolean arm(String pkg, boolean scroll, Consumer<String> result) {
    if (
      live == null || pkg == null || pkg.isEmpty() || (scroll && !pkg.equals("com.android.settings"))
    ) return false;
    WindowReceiptService s = live;
    s.reset();
    s.expected = pkg;
    s.scroll = scroll;
    s.callback = result;
    s.filter(pkg);
    s.main.postDelayed(() -> s.finish("timeout"), 4500);
    return true;
  }

  private void finish(String state) {
    Consumer<String> cb = callback;
    reset();
    if (cb != null) cb.accept(state);
  }

  @Override
  public void onAccessibilityEvent(AccessibilityEvent e) {
    if (
      expected.isEmpty() || !expected.contentEquals(e.getPackageName() == null ? "" : e.getPackageName())
    ) return;
    if (!scroll && e.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
      finish("window_visible");
      return;
    }
    if (scroll && attempted && e.getEventType() == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
      finish("display_scrolled");
      return;
    }
    if (scroll && !attempted && e.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
      String cls = String.valueOf(e.getClassName());
      if (!cls.contains("DisplaySettings")) return;
      attempted = true;
      main.postDelayed(
        () -> {
          if (scroll && expected.equals("com.android.settings")) {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            try {
              if (
                root == null ||
                !expected.contentEquals(root.getPackageName()) ||
                !scrollOne(root, 0, new int[] { 100 })
              ) finish("no_scroll_control");
            } finally {
              if (root != null) root.recycle();
            }
          }
        },
        300
      );
    }
  }

  private boolean scrollOne(AccessibilityNodeInfo n, int depth, int[] budget) {
    if (
      depth > 12 ||
      --budget[0] < 0 ||
      n.isPassword() ||
      !n.isVisibleToUser() ||
      !expected.contentEquals(n.getPackageName() == null ? "" : n.getPackageName())
    ) return false;
    if (n.isScrollable() && n.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)) return true;
    for (int i = 0; i < n.getChildCount() && budget[0] > 0; i++) {
      AccessibilityNodeInfo child = n.getChild(i);
      if (child != null) try {
        if (scrollOne(child, depth + 1, budget)) return true;
      } finally {
        child.recycle();
      }
    }
    return false;
  }

  @Override
  public void onInterrupt() {
    finish("interrupted");
  }

  @Override
  public void onDestroy() {
    finish("disconnected");
    live = null;
    super.onDestroy();
  }
}
