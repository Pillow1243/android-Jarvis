package ai.nexus.automation;
import android.app.*;
import android.content.*;
import ai.nexus.*;
import ai.nexus.memory.Store;
import java.util.*;
public final class ReminderReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c,Intent i){Store s=((NexusApp)c.getApplicationContext()).store;if(Intent.ACTION_BOOT_COMPLETED.equals(i.getAction())){Reminders.restore(c,s);return;}if(!"ai.nexus.REMIND".equals(i.getAction()))return;long id=i.getLongExtra("id",-1);List<Map<String,String>> rows=s.rows("SELECT text FROM reminders WHERE id=?",""+id);if(rows.isEmpty())return;PendingIntent open=PendingIntent.getActivity(c,0,new Intent(c,MainActivity.class),PendingIntent.FLAG_IMMUTABLE);Notification n=new Notification.Builder(c,Reminders.CHANNEL).setSmallIcon(android.R.drawable.ic_popup_reminder).setContentTitle("JARVIS NEXUS").setContentText(rows.get(0).get("text")).setVisibility(Notification.VISIBILITY_PRIVATE).setContentIntent(open).setAutoCancel(true).build();try{c.getSystemService(NotificationManager.class).notify((int)id,n);}finally{s.removeReminder(id);}}
}
