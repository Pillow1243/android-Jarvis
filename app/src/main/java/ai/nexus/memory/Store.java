package ai.nexus.memory;

import ai.nexus.core.*;
import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import java.time.LocalDate;
import java.util.*;
import org.json.*;

/** Private, bounded SQLite journal. Audit metadata and explicit memory are separate domains. */
public final class Store extends SQLiteOpenHelper {

  public final SharedPreferences prefs;

  public Store(Context c) {
    super(c, "nexus.db", null, 3);
    prefs = c.getSharedPreferences("controls", 0);
    setWriteAheadLoggingEnabled(true);
  }

  @Override
  public void onConfigure(SQLiteDatabase d) {
    d.setForeignKeyConstraintsEnabled(true);
  }

  @Override
  public void onCreate(SQLiteDatabase d) {
    d.execSQL(
      "CREATE TABLE tasks(id INTEGER PRIMARY KEY, stamp INTEGER NOT NULL, title TEXT NOT NULL, state TEXT NOT NULL)"
    );
    d.execSQL(
      "CREATE TABLE steps(id INTEGER PRIMARY KEY, task INTEGER REFERENCES tasks(id) ON DELETE CASCADE, stamp INTEGER, tool TEXT, state TEXT, code TEXT)"
    );
    d.execSQL("CREATE INDEX step_task ON steps(task,id)");
    createEvents(d);
    d.execSQL(
      "CREATE TABLE memory(id INTEGER PRIMARY KEY, kind TEXT, name TEXT, value TEXT, stamp INTEGER, pinned INTEGER NOT NULL DEFAULT 0, UNIQUE(kind,name))"
    );
    d.execSQL("CREATE TABLE usage(day TEXT PRIMARY KEY, requests INTEGER, tokens INTEGER)");
    d.execSQL(
      "CREATE TABLE reminders(id INTEGER PRIMARY KEY AUTOINCREMENT, stamp INTEGER NOT NULL, text TEXT NOT NULL)"
    );
  }

  private void createEvents(SQLiteDatabase d) {
    d.execSQL(
      "CREATE TABLE events(id INTEGER PRIMARY KEY, step INTEGER NOT NULL REFERENCES steps(id) ON DELETE CASCADE, stamp INTEGER NOT NULL,state TEXT NOT NULL,code TEXT NOT NULL)"
    );
    d.execSQL("CREATE INDEX events_by_step ON events(step,id)");
  }

  @Override
  public void onUpgrade(SQLiteDatabase d, int old, int next) {
    if (old < 2) d.execSQL("ALTER TABLE memory ADD COLUMN pinned INTEGER NOT NULL DEFAULT 0");
    if (old < 3) createEvents(d);
  }

  private ContentValues values(Object... kv) {
    ContentValues v = new ContentValues();
    for (int i = 0; i < kv.length; i += 2) {
      Object x = kv[i + 1];
      String k = (String) kv[i];
      if (x instanceof Number) v.put(k, ((Number) x).longValue());
      else v.put(k, String.valueOf(x));
    }
    return v;
  }

  public List<Map<String, String>> rows(String sql, String... args) {
    List<Map<String, String>> r = new ArrayList<>();
    try (Cursor c = getReadableDatabase().rawQuery(sql, args)) {
      while (c.moveToNext()) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i < c.getColumnCount(); i++) m.put(c.getColumnName(i), c.getString(i));
        r.add(m);
      }
    }
    return r;
  }

  public long scalar(String sql, String... args) {
    try (Cursor c = getReadableDatabase().rawQuery(sql, args)) {
      return c.moveToFirst() ? c.getLong(0) : 0;
    }
  }

  public boolean memoryOn() {
    return prefs.getBoolean("memory", true);
  }

  public boolean historyOn() {
    return prefs.getBoolean("history", true);
  }

  public void interruptOpen() {
    SQLiteDatabase d = getWritableDatabase();
    d.beginTransaction();
    try {
      d.execSQL(
        "INSERT INTO events(step,stamp,state,code) SELECT id,?,'interrupted','not_replayed' FROM steps WHERE state IN ('requested','attempted','executed')",
        new Object[] { System.currentTimeMillis() }
      );
      d.execSQL("UPDATE tasks SET state='interrupted' WHERE state='running'");
      d.execSQL(
        "UPDATE steps SET state='interrupted', code='not_replayed' WHERE state IN ('requested','attempted','executed')"
      );
      d.setTransactionSuccessful();
    } finally {
      d.endTransaction();
    }
  }

  public long task(String title) {
    if (!historyOn()) return -1;
    SQLiteDatabase d = getWritableDatabase();
    d.execSQL("DELETE FROM tasks WHERE id NOT IN (SELECT id FROM tasks ORDER BY id DESC LIMIT 149)");
    return d.insertOrThrow(
      "tasks",
      null,
      values("stamp", System.currentTimeMillis(), "title", title, "state", "running")
    );
  }

  public void title(long id, String t) {
    getWritableDatabase().update("tasks", values("title", t), "id=?", new String[] { "" + id });
  }

  public void end(long id, String state) {
    getWritableDatabase().update("tasks", values("state", state), "id=?", new String[] { "" + id });
  }

  public long step(long id, String tool) {
    if (id < 0) return -1;
    SQLiteDatabase d = getWritableDatabase();
    d.beginTransaction();
    try {
      long now = System.currentTimeMillis();
      long row = d.insertOrThrow(
        "steps",
        null,
        values("task", id, "tool", tool, "stamp", now, "state", "requested", "code", "pending")
      );
      d.insertOrThrow(
        "events",
        null,
        values("step", row, "stamp", now, "state", "requested", "code", "pending")
      );
      d.setTransactionSuccessful();
      return row;
    } finally {
      d.endTransaction();
    }
  }

  public void updateStep(long id, String state, String code) {
    if (id < 0) return;
    SQLiteDatabase d = getWritableDatabase();
    d.beginTransaction();
    try {
      if (
        d.update("steps", values("state", state, "code", code), "id=?", new String[] { "" + id }) == 1
      ) d.insertOrThrow(
        "events",
        null,
        values("step", id, "stamp", System.currentTimeMillis(), "state", state, "code", code)
      );
      d.setTransactionSuccessful();
    } finally {
      d.endTransaction();
    }
  }

  public void clearHistory() {
    getWritableDatabase().delete("tasks", null, null);
  }

  public long save(long id, String kind, String name, String value) {
    if (!memoryOn()) throw new IllegalStateException("Memory is paused");
    name = name.trim();
    value = value.trim();
    if (
      !Arrays.asList("note", "shortcut", "app_alias", "contact_alias").contains(kind) ||
      name.isEmpty() ||
      name.length() > 90 ||
      value.isEmpty() ||
      value.length() > 1600 ||
      Text.secret(name + " " + value)
    ) throw new IllegalArgumentException("Use a short title and non-secret value");
    if (id < 0 && scalar("SELECT count(*) FROM memory") >= 250) throw new IllegalArgumentException(
      "Memory limit: 250 entries"
    );
    SQLiteDatabase d = getWritableDatabase();
    ContentValues v = values(
      "kind",
      kind,
      "name",
      name,
      "value",
      value,
      "stamp",
      System.currentTimeMillis()
    );
    if (!kind.equals("shortcut")) v.put("pinned", 0);
    if (id < 0) return d.insertOrThrow("memory", null, v);
    if (d.update("memory", v, "id=?", new String[] { "" + id }) != 1) throw new IllegalStateException(
      "Entry not found"
    );
    return id;
  }

  public String lookup(String kind, String name) {
    if (!memoryOn()) return "";
    for (Map<String, String> r : rows("SELECT name,value FROM memory WHERE kind=?", kind))
      if (Text.norm(r.get("name")).equals(Text.norm(name))) return r.get("value");
    return "";
  }

  public void pin(long id, boolean on) {
    if (!memoryOn()) throw new IllegalStateException("Memory paused");
    if (on && scalar("SELECT count(*) FROM memory WHERE pinned=1") >= 8) throw new IllegalStateException(
      "Pin limit: 8"
    );
    getWritableDatabase().update(
      "memory",
      values("pinned", on ? 1 : 0),
      "id=? AND kind='shortcut'",
      new String[] { "" + id }
    );
  }

  public List<Map<String, String>> searchNotes(String query) {
    List<Map<String, String>> hits = new ArrayList<>();
    if (!memoryOn()) return hits;
    String q = Text.norm(query);
    for (Map<String, String> r : rows(
      "SELECT name,value FROM memory WHERE kind='note' ORDER BY stamp DESC"
    )) {
      if (Text.norm(r.get("name") + " " + r.get("value")).contains(q)) {
        hits.add(r);
        if (hits.size() == 8) break;
      }
    }
    return hits;
  }

  public void forget(long id) {
    getWritableDatabase().delete("memory", "id=?", new String[] { "" + id });
  }

  public void clearMemory() {
    getWritableDatabase().delete("memory", null, null);
  }

  public String recentContext() {
    if (!memoryOn() || !historyOn()) return "";
    List<Map<String, String>> r = rows(
      "SELECT tool,state FROM steps WHERE state IN ('verified','handed_off') ORDER BY id DESC LIMIT 3"
    );
    return new JSONArray(r).toString();
  }

  public synchronized boolean reserve() {
    String day = LocalDate.now().toString();
    SQLiteDatabase d = getWritableDatabase();
    d.beginTransaction();
    try {
      d.execSQL("INSERT OR IGNORE INTO usage VALUES(?,0,0)", new Object[] { day });
      if (
        scalar("SELECT requests FROM usage WHERE day=?", day) >= prefs.getInt("limit", 30)
      ) return false;
      d.execSQL("UPDATE usage SET requests=requests+1 WHERE day=?", new Object[] { day });
      d.setTransactionSuccessful();
      return true;
    } finally {
      d.endTransaction();
    }
  }

  public void tokens(String day, int n) {
    if (n > 0) getWritableDatabase().execSQL(
      "UPDATE usage SET tokens=tokens+? WHERE day=?",
      new Object[] { n, day }
    );
  }

  public JSONObject exportData() {
    return J.obj(
      "schema",
      1,
      "entries",
      new JSONArray(rows("SELECT kind,name,value,pinned FROM memory ORDER BY id")),
      "tasks",
      new JSONArray(rows("SELECT * FROM tasks ORDER BY id")),
      "steps",
      new JSONArray(rows("SELECT * FROM steps ORDER BY id")),
      "events",
      new JSONArray(rows("SELECT * FROM events ORDER BY id")),
      "usage",
      new JSONArray(rows("SELECT * FROM usage ORDER BY day"))
    );
  }

  public long reminder(long at, String text) {
    if (Text.secret(text)) throw new IllegalArgumentException("Do not store secrets in reminders");
    return getWritableDatabase().insertOrThrow("reminders", null, values("stamp", at, "text", text));
  }

  public void removeReminder(long id) {
    getWritableDatabase().delete("reminders", "id=?", new String[] { "" + id });
  }
}
