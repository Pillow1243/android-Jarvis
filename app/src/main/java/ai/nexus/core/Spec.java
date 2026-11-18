package ai.nexus.core;
import java.util.*;
import org.json.*;

/** A closed, flat schema: extra fields, blank values, wrong types and unknown enums fail closed. */
public final class Spec {
    public final String id,category,title,fa,description,permission,verification; public final int risk,timeoutMs; public final boolean cloud;
    public final Map<String,String> fields=new LinkedHashMap<>();
    public Spec(String id,String cat,String en,String fa,String desc,String permission,int risk,boolean cloud,String verify,String... fields){
        this.id=id;category=cat;title=en;this.fa=fa;description=desc;this.permission=permission;this.risk=risk;this.cloud=cloud;verification=verify;timeoutMs=120000;
        for(int i=0;i<fields.length;i+=2)this.fields.put(fields[i],fields[i+1]);
    }
    public String validate(Call c){
        if(!id.equals(c.name)||!fields.keySet().equals(c.args.keySet()))return "Unexpected or missing fields";
        for(String k:fields.keySet()){String v=c.get(k);int cap=k.equals("text")?1200:240;
            if(v.trim().isEmpty()||v.length()>cap||v.matches("(?s).*[\\x00-\\x08\\x0B-\\x1F\\x7F\\u202A-\\u202E\\u2066-\\u2069].*"))return "Invalid field: "+k;
            String f=fields.get(k);if(f.startsWith("|")&&!Arrays.asList(f.substring(1).split("\\|")).contains(v))return "Unknown option: "+k;
        }return null;
    }
    public JSONObject declaration(){JSONObject props=J.obj();JSONArray req=J.arr();for(String k:fields.keySet()){String f=fields.get(k);JSONObject p=J.obj("type","STRING");if(f.startsWith("|"))J.put(p,"enum",new JSONArray(Arrays.asList(f.substring(1).split("\\|"))));else J.put(p,"description",f);J.put(props,k,p);req.put(k);}JSONObject d=J.obj("name",id,"description",description);if(!fields.isEmpty())J.put(d,"parameters",J.obj("type","OBJECT","properties",props,"required",req));return d;}
    public JSONObject outputSchema(){return J.obj("type","object","required",J.arr("state","evidence","metrics"),"properties",J.obj("state",J.obj("enum",J.arr("verified","handed_off","failed","declined")),"evidence",J.obj("type","string"),"metrics",J.obj("type","object")));}
}
