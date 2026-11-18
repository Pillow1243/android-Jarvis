package ai.nexus.core;
import org.json.*;
public final class J {
    private J(){}
    public static JSONObject obj(Object... kv){JSONObject o=new JSONObject();for(int i=0;i<kv.length;i+=2)put(o,(String)kv[i],kv[i+1]);return o;}
    public static JSONObject put(JSONObject o,String key,Object v){try{o.put(key,v);return o;}catch(JSONException e){throw new IllegalArgumentException("Invalid JSON field",e);}}
    public static JSONArray arr(Object... v){JSONArray a=new JSONArray();for(Object x:v)a.put(x);return a;}
}
