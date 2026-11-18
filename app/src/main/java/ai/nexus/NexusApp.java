package ai.nexus;
import android.app.Application;
import ai.nexus.memory.Store;
import ai.nexus.automation.Reminders;
public final class NexusApp extends Application {
    public Store store;
    @Override public void onCreate(){super.onCreate();store=new Store(this);store.interruptOpen();Reminders.channel(this);}
}
