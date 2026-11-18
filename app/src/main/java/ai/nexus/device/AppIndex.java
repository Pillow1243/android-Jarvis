package ai.nexus.device;

import ai.nexus.core.Text;
import android.content.*;
import android.content.pm.*;
import java.util.*;

public final class AppIndex {

  public static final class App {

    public final String name, pkg, cls;

    public App(String name, String pkg, String cls) {
      this.name = name;
      this.pkg = pkg;
      this.cls = cls;
    }

    public String display() {
      return name + "\n" + pkg;
    }
  }

  private static final String[][] ALIASES = {
    { "com.whatsapp", "واتساپ", "واتس اپ", "واتس آپ", "whatsapp", "whats app" },
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

  private static int rank(String q, String label) {
    if (q.equals(label)) return 0;
    if (q.length() > 1 && label.contains(q)) return 1;
    return q.length() >= 4 && Text.distance(q, label) <= Math.min(2, q.length() / 4) ? 2 : 9;
  }

  public static List<App> find(List<App> list, String query) {
    String q = Text.norm(query);
    List<App> out = new ArrayList<>();
    if (q.isEmpty()) return out;
    int best = 9;
    for (App a : list) {
      int score = a.pkg.equals(q) ? 0 : rank(q, Text.norm(a.name));
      for (String[] aliases : ALIASES)
        if (aliases[0].equals(a.pkg)) {
          for (int i = 1; i < aliases.length && score != 0; i++) score = Math.min(
            score,
            rank(q, Text.norm(aliases[i]))
          );
          break;
        }
      if (score < best) {
        out.clear();
        best = score;
      }
      if (score == best && score < 9) out.add(a);
    }
    return out;
  }
}
