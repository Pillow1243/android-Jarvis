package ai.nexus;

import ai.nexus.memory.Store;
import android.app.Application;

public final class NexusApp extends Application {

  public Store store;

  @Override
  public void onCreate() {
    super.onCreate();
    store = new Store(this);
    store.interruptOpen();
  }
}
