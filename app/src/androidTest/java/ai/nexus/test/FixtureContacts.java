package ai.nexus.test;

import android.content.*;
import android.database.Cursor;
import android.provider.ContactsContract.*;
import android.provider.ContactsContract.CommonDataKinds.*;
import java.util.ArrayList;

/** Ordered, shell-only TEST receiver. Setup succeeds only after a provider read-back of both fixtures. */
public final class FixtureContacts extends BroadcastReceiver {

  @Override
  public void onReceive(Context c, Intent i) {
    String stage = "cleanup";
    try {
      c
        .getContentResolver()
        .delete(
          RawContacts.CONTENT_URI,
          RawContacts.SOURCE_ID + " IN (?,?)",
          new String[] { "nexus.qa.one", "nexus.qa.two" }
        );
      if (i.getBooleanExtra("clear", false)) {
        setResult(0, "cleared", null);
        return;
      }
      stage = "insert_one";
      add(c, "one", "مادر نکسوس یک", "+12025550101");
      stage = "insert_two";
      add(c, "two", "مادر نکسوس دو", "+12025550102");
      stage = "read_back";
      int matches = 0;
      try (
        Cursor cur = c
          .getContentResolver()
          .query(Phone.CONTENT_URI, new String[] { Phone.DISPLAY_NAME, Phone.NUMBER }, null, null, null)
      ) {
        while (cur != null && cur.moveToNext()) if (
          cur.getString(0) != null &&
          cur.getString(0).startsWith("مادر نکسوس") &&
          cur.getString(1).startsWith("+1202555010")
        ) matches++;
      }
      setResult(matches == 2 ? 42 : 1, "provider_rows=" + matches, null);
    } catch (Exception e) {
      boolean unlocked = c.getSystemService(android.os.UserManager.class).isUserUnlocked();
      String message = String.valueOf(e.getMessage()).replaceAll("[\r\n]", " ");
      setResult(
        2,
        "stage=" +
          stage +
          " exception=" +
          e.getClass().getSimpleName() +
          " unlocked=" +
          unlocked +
          " read=" +
          c.checkSelfPermission(android.Manifest.permission.READ_CONTACTS) +
          " write=" +
          c.checkSelfPermission(android.Manifest.permission.WRITE_CONTACTS) +
          " detail=" +
          message.substring(0, Math.min(message.length(), 220)),
        null
      );
    }
  }

  private void add(Context context, String id, String name, String number) throws Exception {
    ArrayList<ContentProviderOperation> ops = new ArrayList<>();
    ops.add(
      ContentProviderOperation.newInsert(RawContacts.CONTENT_URI)
        .withValue(RawContacts.ACCOUNT_TYPE, null)
        .withValue(RawContacts.ACCOUNT_NAME, null)
        .withValue(RawContacts.SOURCE_ID, "nexus.qa." + id)
        .build()
    );
    ops.add(
      ContentProviderOperation.newInsert(Data.CONTENT_URI)
        .withValueBackReference(Data.RAW_CONTACT_ID, 0)
        .withValue(Data.MIMETYPE, StructuredName.CONTENT_ITEM_TYPE)
        .withValue(StructuredName.DISPLAY_NAME, name)
        .build()
    );
    ops.add(
      ContentProviderOperation.newInsert(Data.CONTENT_URI)
        .withValueBackReference(Data.RAW_CONTACT_ID, 0)
        .withValue(Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE)
        .withValue(Phone.NUMBER, number)
        .withValue(Phone.TYPE, Phone.TYPE_MOBILE)
        .build()
    );
    context.getContentResolver().applyBatch("com.android.contacts", ops);
  }
}
