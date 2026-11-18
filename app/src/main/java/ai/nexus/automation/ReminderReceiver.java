package ai.nexus.automation;

import ai.nexus.*;
import ai.nexus.memory.Store;
import android.app.*;
import android.content.*;
import java.util.*;

public final class ReminderReceiver extends BroadcastReceiver {

  @Override
  public void onReceive(Context c, Intent i) {
    Store s = ((NexusApp) c.getApplicationContext()).store;
    if (Intent.ACTION_BOOT_COMPLETED.equals(i.getAction())) {
      Reminders.restore(c, s);
      return;
    }
    if (!"ai.nexus.REMIND".equals(i.getAction())) return;
    long id = i.getLongExtra("id", -1);
    List<Map<String, String>> rows = s.rows("SELECT text FROM reminders WHERE id=?", "" + id);
    if (rows.isEmpty()) return;
    PendingIntent open = PendingIntent.getActivity(
      c,
      0,
      new Intent(c, MainActivity.class),
      PendingIntent.FLAG_IMMUTABLE
    );
    Notification n = new Notification.Builder(c, Reminders.CHANNEL)
      .setSmallIcon(android.R.drawable.ic_popup_reminder)
      .setContentTitle("JARVIS NEXUS")
      .setContentText(rows.get(0).get("text"))
      .setVisibility(Notification.VISIBILITY_PRIVATE)
      .setContentIntent(open)
      .setAutoCancel(true)
      .build();
    try {
      NotificationManager nm = c.getSystemService(NotificationManager.class);
      NotificationChannel chan = nm.getNotificationChannel(Reminders.CHANNEL);
      if (
        !nm.areNotificationsEnabled() ||
        chan == null ||
        chan.getImportance() == NotificationManager.IMPORTANCE_NONE
      ) throw new SecurityException();
      nm.notify((int) id, n);
    } catch (SecurityException denied) {
      // Permission/channel may change between scheduling and delivery. Leave a redacted failure, not a crash or a delivery claim.
      boolean fa =
        "fa".equals(s.prefs.getString("language", "auto")) ||
        ("auto".equals(s.prefs.getString("language", "auto")) &&
          !Locale.getDefault().getLanguage().equals("en"));
      long task = s.task(fa ? "اعلان یادآور مسدود شد" : "Reminder notification blocked");
      long step = s.step(task, "set_reminder");
      s.updateStep(step, "failed", "notification_blocked");
      s.end(task, "failed");
    } finally {
      s.removeReminder(id);
    }
  }
}
