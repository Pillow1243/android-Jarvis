package ai.nexus.memory;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import ai.nexus.core.*;
import java.util.*;
import java.time.LocalDate;
import org.json.*;

/** Private, bounded SQLite journal. Audit metadata and explicit memory are separate domains. */
public final class Store extends SQLiteOpenHelper {
    public final SharedPreferences prefs;
    public Store(Context c){super(c,"nexus.db",null,1);prefs=c.getSharedPreferences("controls",0);setWriteAheadLoggingEnabled(true);}
    @Override public void onConfigure(SQLiteDatabase d){d.setForeignKeyConstraintsEnabled(true);}
    @Override public void onCreate(SQLiteDatabase d){
        d.execSQL("CREATE TABLE tasks(id INTEGER PRIMARY KEY, stamp INTEGER NOT NULL, title TEXT NOT NULL, state TEXT NOT NULL)");
        d.execSQL("CREATE TABLE steps(id INTEGER PRIMARY KEY, task INTEGER REFERENCES tasks(id) ON DELETE CASCADE, stamp INTEGER, tool TEXT, state TEXT, code TEXT)");
        d.execSQL("CREATE INDEX step_task ON steps(task,id)");
        d.execSQL("CREATE TABLE memory(id INTEGER PRIMARY KEY, kind TEXT, name TEXT, value TEXT, stamp INTEGER, UNIQUE(kind,name))");
        d.execSQL("CREATE TABLE usage(day TEXT PRIMARY KEY, requests INTEGER, tokens INTEGER)");
        d.execSQL("CREATE TABLE reminders(id INTEGER PRIMARY KEY AUTOINCREMENT, stamp INTEGER NOT NULL, text TEXT NOT NULL)");
    }
    @Override public void onUpgrade(SQLiteDatabase d,int old,int next){throw new IllegalStateException("Unknown schema version");}
    private ContentValues values(Object... kv){ContentValues v=new ContentValues();for(int i=0;i<kv.length;i+=2){Object x=kv[i+1];String k=(String)kv[i];if(x instanceof Number)v.put(k,((Number)x).longValue());else v.put(k,String.valueOf(x));}return v;}
    public List<Map<String,String>> rows(String sql,String... args){List<Map<String,String>> r=new ArrayList<>();try(Cursor c=getReadableDatabase().rawQuery(sql,args)){while(c.moveToNext()){Map<String,String> m=new LinkedHashMap<>();for(int i=0;i<c.getColumnCount();i++)m.put(c.getColumnName(i),c.getString(i));r.add(m);}}return r;}
    public long scalar(String sql,String...args){try(Cursor c=getReadableDatabase().rawQuery(sql,args)){return c.moveToFirst()?c.getLong(0):0;}}
    public boolean memoryOn(){return prefs.getBoolean("memory",true);}
    public boolean historyOn(){return prefs.getBoolean("history",true);}
    public void interruptOpen(){getWritableDatabase().execSQL("UPDATE tasks SET state='interrupted' WHERE state='running'");getWritableDatabase().execSQL("UPDATE steps SET state='interrupted', code='not_replayed' WHERE state IN ('requested','attempted','executed')");}
    public long task(String title){if(!historyOn())return -1;SQLiteDatabase d=getWritableDatabase();d.execSQL("DELETE FROM tasks WHERE id NOT IN (SELECT id FROM tasks ORDER BY id DESC LIMIT 149)");return d.insertOrThrow("tasks",null,values("stamp",System.currentTimeMillis(),"title",title,"state","running"));}
    public void title(long id,String t){getWritableDatabase().update("tasks",values("title",t),"id=?",new String[]{""+id});}
    public void end(long id,String state){getWritableDatabase().update("tasks",values("state",state),"id=?",new String[]{""+id});}
    public long step(long id,String tool){return id<0?-1:getWritableDatabase().insertOrThrow("steps",null,values("task",id,"tool",tool,"stamp",System.currentTimeMillis(),"state","requested","code","pending"));}
    public void updateStep(long id,String state,String code){if(id>=0)getWritableDatabase().update("steps",values("state",state,"code",code),"id=?",new String[]{""+id});}
    public void clearHistory(){getWritableDatabase().delete("tasks",null,null);}
    public long save(long id,String kind,String name,String value){
        if(!memoryOn())throw new IllegalStateException("Memory is paused");name=name.trim();value=value.trim();
        if(!Arrays.asList("note","shortcut","app_alias","contact_alias").contains(kind)||name.isEmpty()||name.length()>90||value.isEmpty()||value.length()>1600||Text.secret(name+" "+value))throw new IllegalArgumentException("Use a short title and non-secret value");
        if(id<0&&scalar("SELECT count(*) FROM memory")>=250)throw new IllegalArgumentException("Memory limit: 250 entries");
        SQLiteDatabase d=getWritableDatabase();ContentValues v=values("kind",kind,"name",name,"value",value,"stamp",System.currentTimeMillis());
        if(id<0)return d.insertOrThrow("memory",null,v);if(d.update("memory",v,"id=?",new String[]{""+id})!=1)throw new IllegalStateException("Entry not found");return id;
    }
    public String lookup(String kind,String name){if(!memoryOn())return "";for(Map<String,String> r:rows("SELECT name,value FROM memory WHERE kind=?",kind))if(Text.norm(r.get("name")).equals(Text.norm(name)))return r.get("value");return "";}
    public void forget(long id){getWritableDatabase().delete("memory","id=?",new String[]{""+id});}
    public void clearMemory(){getWritableDatabase().delete("memory",null,null);}
    public String recentContext(){if(!memoryOn()||!historyOn())return "";List<Map<String,String>> r=rows("SELECT tool,state FROM steps WHERE state IN ('verified','handed_off') ORDER BY id DESC LIMIT 3");return new JSONArray(r).toString();}
    public synchronized boolean reserve(){String day=LocalDate.now().toString();SQLiteDatabase d=getWritableDatabase();d.beginTransaction();try{d.execSQL("INSERT OR IGNORE INTO usage VALUES(?,0,0)",new Object[]{day});if(scalar("SELECT requests FROM usage WHERE day=?",day)>=prefs.getInt("limit",30))return false;d.execSQL("UPDATE usage SET requests=requests+1 WHERE day=?",new Object[]{day});d.setTransactionSuccessful();return true;}finally{d.endTransaction();}}
    public void tokens(String day,int n){if(n>0)getWritableDatabase().execSQL("UPDATE usage SET tokens=tokens+? WHERE day=?",new Object[]{n,day});}
    public JSONObject exportData(){return J.obj("schema",1,"entries",new JSONArray(rows("SELECT kind,name,value FROM memory ORDER BY id")),"tasks",new JSONArray(rows("SELECT * FROM tasks ORDER BY id")),"steps",new JSONArray(rows("SELECT * FROM steps ORDER BY id")),"usage",new JSONArray(rows("SELECT * FROM usage ORDER BY day")));}
    public long reminder(long at,String text){return getWritableDatabase().insertOrThrow("reminders",null,values("stamp",at,"text",text));}
    public void removeReminder(long id){getWritableDatabase().delete("reminders","id=?",new String[]{""+id});}
}
