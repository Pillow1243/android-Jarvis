package ai.nexus.test;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

/** Shell-launched test-only foreground owner for the ContactsProvider setup; no production component. */
public final class FixtureWindow extends Activity {

  @Override
  protected void onCreate(Bundle saved) {
    super.onCreate(saved);
    TextView label = new TextView(this);
    label.setText("Nexus QA · setting up two synthetic contacts on this disposable emulator.");
    setContentView(label);
  }
}
