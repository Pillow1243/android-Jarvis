package ai.nexus.automation;
import android.app.*;
import android.content.*;
import android.net.Uri;
import ai.nexus.memory.Store;
import ai.nexus.core.Text;
import java.time.*;
import java.util.Map;

public final class Reminders {
    public static final String CHANNEL="nexus_reminders";
    public static void channel(Context c){NotificationChannel ch=new NotificationChannel(CHANNEL,"Nexus reminders",NotificationManager.IMPORTANCE_DEFAULT);ch.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);c.getSystemService(NotificationManager.class).createNotificationChannel(ch);}
    public static long when(String time,LocalDateTime now,ZoneId zone){String t=Text.digits(time.trim());LocalDateTime dt;if(t.matches("[0-2]?[0-9]:[0-5][0-9]")){String[] p=t.split(":");dt=now.toLocalDate().atTime(Integer.parseInt(p[0]),Integer.parseInt(p[1]));if(!dt.isAfter(now))dt=dt.plusDays(1);}else dt=LocalDateTime.parse(t);if(!dt.isAfter(now)||dt.isAfter(now.plusYears(2)))throw new IllegalArgumentException("Choose a future time within two years");return dt.atZone(zone).toInstant().toEpochMilli();}
    private static PendingIntent intent(Context c,long id,int flag){Intent i=new Intent(c,ReminderReceiver.class).setAction("ai.nexus.REMIND").setData(Uri.parse("nexus:reminder/"+id)).putExtra("id",id);return PendingIntent.getBroadcast(c,0,i,flag|PendingIntent.FLAG_IMMUTABLE);}
    private static void arm(Context c,long id,long at){c.getSystemService(AlarmManager.class).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,at,intent(c,id,PendingIntent.FLAG_UPDATE_CURRENT));}
    public static long schedule(Context c,Store s,long at,String text){NotificationManager nm=c.getSystemService(NotificationManager.class);if(!nm.areNotificationsEnabled()||nm.getNotificationChannel(CHANNEL).getImportance()==NotificationManager.IMPORTANCE_NONE)throw new IllegalStateException("Reminder notifications are off");if(s.scalar("SELECT count(*) FROM reminders")>=100)throw new IllegalStateException("Reminder limit reached");long id=s.reminder(at,text);try{arm(c,id,at);return id;}catch(RuntimeException e){s.removeReminder(id);throw e;}}
    public static void cancel(Context c,Store s,long id){PendingIntent pi=intent(c,id,PendingIntent.FLAG_NO_CREATE);if(pi!=null){c.getSystemService(AlarmManager.class).cancel(pi);pi.cancel();}s.removeReminder(id);}
    public static void restore(Context c,Store s){for(Map<String,String> m:s.rows("SELECT id,stamp FROM reminders"))arm(c,Long.parseLong(m.get("id")),Math.max(System.currentTimeMillis()+1000,Long.parseLong(m.get("stamp"))));}
}
