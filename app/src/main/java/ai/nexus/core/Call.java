package ai.nexus.core;
import org.json.*;
import java.util.*;
/** Immutable input snapshot shared by direct commands and provider calls. */
public final class Call {
    public final String name; public final Map<String,String> args;
    public Call(String name,String... kv){this.name=name; Map<String,String> m=new TreeMap<>(); for(int i=0;i<kv.length;i+=2)m.put(kv[i],kv[i+1]); args=Collections.unmodifiableMap(m);}
    public static Call from(String name,JSONObject obj)throws JSONException{ArrayList<String> a=new ArrayList<>();Iterator<String> it=obj.keys();while(it.hasNext()){String k=it.next();Object v=obj.get(k);if(!(v instanceof String))throw new JSONException("String expected");a.add(k);a.add((String)v);}return new Call(name,a.toArray(new String[0]));}
    public String get(String k){String s=args.get(k);return s==null?"":s;}
    public String fingerprint(){JSONArray a=J.arr(name);for(Map.Entry<String,String> e:args.entrySet()){a.put(e.getKey());a.put(e.getValue());}return a.toString();}
}
