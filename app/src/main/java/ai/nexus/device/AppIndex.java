package ai.nexus.device;

import ai.nexus.core.Text;
import android.content.*;
import android.content.pm.*;
import java.util.*;

public final class AppIndex {

  public static final class App {

    public final String name, pkg, cls;

    App(String name, String pkg, String cls) {
      this.name = name;
      this.pkg = pkg;
      this.cls = cls;
    }

    public String display() {
      return name + "\n" + pkg;
    }
  }

  private static final String[][] ALIASES = {
    { "com.whatsapp", "واتساپ", "واتس اپ", "whats app" },
    { "org.telegram.messenger", "تلگرام", "telegram" },
    { "com.spotify.music", "اسپاتیفای", "spotify" },
    { "com.android.chrome", "کروم", "chrome" },
    { "com.google.android.youtube", "یوتیوب", "youtube" },
    { "com.google.android.apps.maps", "نقشه گوگل", "گوگل مپ", "google maps", "maps" },
    { "com.android.settings", "تنظیمات", "settings" },
    { "com.google.android.gm", "جیمیل", "gmail" },
  };

  public static List<App> scan(Context c) {
    PackageManager pm = c.getPackageManager();
    List<App> list = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    for (ResolveInfo r : pm.queryIntentActivities(
      new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
      0
    )) {
      ActivityInfo a = r.activityInfo;
      if (a == null || !a.exported || !a.enabled || !seen.add(a.packageName + "/" + a.name)) continue;
      list.add(new App(r.loadLabel(pm).toString(), a.packageName, a.name));
    }
    list.sort((a, b) -> a.name.compareToIgnoreCase(b.name));
    return list;
  }

  public static List<App> find(List<App> list, String query) {
    String q = Text.norm(query);
    for (String[] a : ALIASES) for (int i = 1; i < a.length; i++) if (q.equals(a[i])) q = a[0];
    List<App> out = new ArrayList<>();
    int best = 9;
    for (App a : list) {
      String n = Text.norm(a.name);
      int score = a.pkg.equals(q) || n.equals(q)
        ? 0
        : n.contains(q) && q.length() > 1
          ? 1
          : q.length() >= 4 && Text.distance(q, n) <= Math.min(2, q.length() / 4)
            ? 2
            : 9;
      if (score < best) {
        out.clear();
        best = score;
      }
      if (score == best && score < 9) out.add(a);
    }
    return out;
  }
}
