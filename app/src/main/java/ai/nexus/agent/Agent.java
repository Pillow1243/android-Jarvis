package ai.nexus.agent;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.*;
import ai.nexus.ai.*;
import ai.nexus.accessibility.WindowReceiptService;
import ai.nexus.core.*;
import ai.nexus.memory.Store;
import ai.nexus.tools.ToolBox;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import org.json.*;

/** Main-thread state machine. A task/step generation owns each asynchronous callback and side effect. */
public final class Agent {
    public interface UI {
        Activity activity();String tr(String en,String fa);boolean foreground();void changed();void done(String text);void dismissGate();
        void approval(String title,String exact,Consumer<Boolean> cb);void pick(String title,List<String> items,Consumer<Integer> cb);void permission(String p,Consumer<Boolean> cb);
    }
    public static final class Step {public final Call call;public long row;public String state="requested",detail="",code="pending";Step(Call c){call=c;}}
    public final ToolBox box;public final Store db;public final List<Step> steps=new ArrayList<>();public String stage="idle",report="",source="",progress="",title="";public boolean running;
    public String lastApp="",lastCommand=""; public long task=-1;
    private final Provider ai;private final UI ui;private final Handler main=new Handler(Looper.getMainLooper());private final ExecutorService io=Executors.newFixedThreadPool(2);private Future<?> future;private Runnable resume,timeout;
    private String clarification="",lastSurface="";
    private final Set<String> ledger=new HashSet<>();private int generation,rounds,next,batchStart;private Step active;private boolean cloud,hasFailure;private JSONArray history,fc,results;private String language;
    public Agent(UI u,Store s,ToolBox t,Provider p){ui=u;db=s;box=t;ai=p;}
    private String t(String a,String b){return ui.tr(a,b);}
    public void submit(String raw,boolean hasKey){String q=raw.trim();if(running||q.isEmpty())return;if(q.length()>2000||Text.secret(q)){report=t("Do not enter secrets. Use a command under 2,000 characters.","اطلاعات محرمانه وارد نکنید. فرمان باید کوتاه‌تر از ۲۰۰۰ نویسه باشد.");ui.changed();return;}
        String normalized=Text.norm(q);
        if(!clarification.isEmpty()){String pending=clarification;clarification="";if(normalized.matches("cancel|stop|لغو|انصراف")){report=t("Clarification cancelled.","پرسش لغو شد.");ui.changed();return;}if(pending.equals("app")){direct(new Call("launch_app","name",q));return;}if(pending.equals("maps")){direct(new Call("maps","mode","search","place",q));return;}direct(new Call("web_search","engine",pending,"query",q));return;}
        if(lastApp.isEmpty()&&normalized.matches("open (it|that app)|اون برنامه رو باز کن|همون برنامه رو باز کن")){ask("app",t("Which application should I open?","کدام برنامه را باز کنم؟"));return;}
        if(normalized.matches("search (there|inside it)|حالا داخلش جست[ و‌]*جو کن|داخلش جست[ و‌]*جو کن")){
            String target=Text.norm(lastSurface);String dest=target.contains("youtube")||target.contains("یوتیوب")?"youtube":target.contains("maps")||target.contains("نقشه")?"maps":target.matches(".*(chrome|کروم|browser|مرورگر|web).*")?"web":"";
            if(!dest.isEmpty())ask(dest,t("What should I search for?","دنبال چه عبارتی بگردم؟"));else ask("",t("Which destination? Try “search YouTube for …” or “find on maps …”. I cannot search inside every app.","کجا جست‌وجو کنم؟ مثلاً «در یوتیوب جستجو کن …» یا «نقشه …». جست‌وجو داخل همهٔ برنامه‌ها ممکن نیست."));return;}
        if(Text.norm(q).matches("repeat that|do that again|همون کاری که گفتم رو انجام بده")){if(lastCommand.isEmpty()){report=t("Which command should I repeat?","کدام فرمان را تکرار کنم؟");ui.changed();return;}q=lastCommand;}
        String shortcut=db.lookup("shortcut",q);if(!shortcut.isEmpty())q=shortcut;language=Text.fa(q)?"Persian":"English";lastCommand=q;List<Call> local=new LocalPlanner().plan(q,lastApp);
        begin(local.isEmpty()?"Gemini":"Local",local.isEmpty()?t("Language task","فرمان زبانی"):label(local.get(0).name));if(!local.isEmpty()){enqueue(local);return;}if(!hasKey){finish("failed",t("This instruction needs Gemini. Connect a key in Settings, or try “device report then list apps”.","این فرمان به جمینای نیاز دارد. کلید را در تنظیمات وارد کنید یا «گزارش دستگاه و بعد فهرست برنامه‌ها» را امتحان کنید."));return;}
        cloud=true;history=J.arr(Gemini.user(q));think();}
    private void ask(String kind,String message){clarification=kind;begin("Local",t("Clarification","پرسش تکمیلی"));finish("clarification",message);}
    public void direct(Call c){if(running)return;clarification="";begin("Local",label(c.name));enqueue(Collections.singletonList(c));}
    private void begin(String src,String name){generation++;running=true;cloud=false;hasFailure=false;rounds=0;next=0;active=null;ledger.clear();steps.clear();report="";progress="";title=name;source=src;stage="planning";task=db.task(name);ui.changed();}
    private String label(String id){Spec s=box.specs.get(id);return s==null?"Tool":t(s.title,s.fa);}
    private void enqueue(List<Call> calls){if(steps.size()+calls.size()>10){finish("failed",t("Ten-step safety limit reached.","سقف ده مرحله رسید."));return;}batchStart=steps.size();for(Call c:calls){Step st=new Step(c);st.row=db.step(task,box.specs.containsKey(c.name)?c.name:"unknown_tool");steps.add(st);}if(steps.size()>0){title=label(steps.get(0).call.name)+(steps.size()>1?" +"+(steps.size()-1):"");db.title(task,title);}state("planning");later(()->advance());}
    private void later(Runnable r){int g=generation;main.post(()->{if(alive(g))r.run();});}
    private boolean alive(int g){return running&&generation==g;}
    public void foregrounded(){Runnable r=resume;resume=null;if(r!=null)r.run();}
    private void foreground(Runnable r){int g=generation;if(ui.foreground()){if(alive(g))r.run();}else{resume=()->{if(alive(g))r.run();};state("waiting_return");}}
    private void state(String s){stage=s;ui.changed();}
    private void think(){if(++rounds>6){finish("limited",t("Planning limit reached. Review the recorded steps.","سقف برنامه‌ریزی رسید؛ مراحل ثبت‌شده را ببینید."));return;}state("thinking");int g=generation;String context=db.recentContext()+(lastApp.isEmpty()?"":" Last user-requested app: "+Text.clipped(lastApp,80));deadline(55000,()->{ai.cancel();finish("failed",t("Planner timed out.","مهلت برنامه‌ریزی تمام شد."));});JSONArray saved=history;future=io.submit(()->{try{JSONObject content=ai.turn(saved,box.declarations(),context,language,part->main.post(()->{if(alive(g)){progress=t("Receiving model response…","در حال دریافت پاسخ مدل…");ui.changed();}}));main.post(()->{if(!alive(g))return;clearDeadline();try{onModel(content);}catch(Exception e){finish("failed",t("Malformed model response. No pending call was executed.","پاسخ نامعتبر مدل؛ اقدام معلقی اجرا نشد."));}});}catch(Exception e){main.post(()->{if(alive(g)){clearDeadline();String reason=e instanceof Gemini.Failure?e.getMessage():t("Gemini connection failed. Check Settings and your network.","اتصال جمینای ناموفق بود. شبکه و تنظیمات را بررسی کنید.");finish("failed",reason);}});}});}
    private void onModel(JSONObject content)throws Exception{JSONArray p=content.getJSONArray("parts");List<Call> calls=new ArrayList<>();fc=J.arr();results=J.arr();for(int i=0;i<p.length();i++){JSONObject f=p.getJSONObject(i).optJSONObject("functionCall");if(f!=null){fc.put(f);JSONObject a=f.optJSONObject("args");if(f.has("args")&&a==null)throw new JSONException("arguments");calls.add(Call.from(f.getString("name"),a==null?J.obj():a));}}history.put(content);progress="";if(calls.isEmpty()){if(steps.isEmpty()){String text=Gemini.visibleText(content);finish("answered",t("Gemini response · no device action\n","پاسخ جمینای · بدون اقدام دستگاه\n")+text);}else summarize();return;}if(calls.size()>5)throw new JSONException("too many calls");enqueue(calls);}
    private void advance(){if(!running)return;if(next==steps.size()){active=null;if(cloud){history.put(J.obj("role","user","parts",results));foreground(this::think);}else summarize();return;}
        foreground(()->{active=steps.get(next);Step st=active;Call c=st.call;Spec s=box.specs.get(c.name);int g=generation;
            if(s==null||(cloud&&!s.cloud)||s.validate(c)!=null){complete(st,Receipt.failed(t("Tool or arguments not allowed.","ابزار یا آرگومان مجاز نیست."),"invalid_arguments"));return;}
            if(!ledger.add(c.fingerprint())){complete(st,Receipt.failed(t("Duplicate action blocked; it was not repeated.","اقدام تکراری متوقف شد."),"duplicate_blocked"));return;}
            state("executing");deadline(s.timeoutMs,()->{if(alive(g)&&active==st){ui.dismissGate();WindowReceiptService.disarm();complete(st,Receipt.failed(t("Tool timed out. Its effect may be unknown; it will not be replayed.","مهلت ابزار تمام شد؛ اثر آن نامشخص است و تکرار نمی‌شود."),"timeout_unknown"));}});
            Runnable go=()->{if(!alive(g)||active!=st)return;try{box.execute(c,new Invocation(g,st,s));}catch(Exception e){complete(st,error(e));}};
            if(s.permission.equals("accessibility")){if(!WindowReceiptService.connected())complete(st,Receipt.failed(t("Enable window receipts explicitly in Settings first.","ابتدا سرویس رسید پنجره را در تنظیمات فعال کنید."),"access_disabled"));else go.run();return;}
            boolean need=PermissionPolicy.runtimeRequired(s,c,Build.VERSION.SDK_INT);
            if(need&&ui.activity().checkSelfPermission(s.permission)!=PackageManager.PERMISSION_GRANTED){state("permission");ui.permission(s.permission,ok->{if(alive(g)&&active==st){if(ok)foreground(go);else complete(st,Receipt.denied(t("Permission was not granted. No external action performed.","مجوز داده نشد؛ اقدام خارجی انجام نشد.")));}});}else go.run();});}
    private Receipt error(Exception e){return Receipt.failed(t("Operation unavailable. Check permissions, inputs, storage or the destination app.","عملیات در دسترس نیست؛ مجوز، ورودی، حافظه یا برنامهٔ مقصد را بررسی کنید."),e instanceof SecurityException?"permission_error":"execution_error");}
    private final class Invocation implements ToolBox.Invocation {
        final int g;final Step st;final Spec spec;boolean consumed=false;Invocation(int g,Step st,Spec s){this.g=g;this.st=st;spec=s;}
        boolean live(){return alive(g)&&active==st;}public Activity activity(){return ui.activity();}public Store store(){return db;}public String tr(String a,String b){return t(a,b);}
        public void choose(String title,List<String> opts,Consumer<Integer> cb){if(!live())return;foreground(()->{state("clarifying");ui.pick(title,opts,i->{if(!live())return;if(i<0||i>=opts.size())finish(Receipt.denied(t("No selection made.","انتخاب نشد.")));else try{cb.accept(i);}catch(Exception e){finish(error(e));}});});}
        public void commit(String exact,Runnable operation){if(!live()||consumed)return;consumed=true;foreground(()->{Consumer<Boolean> perform=ok->{if(!live())return;if(!ok){finish(Receipt.denied(t("Not approved. No action taken.","تأیید نشد؛ اقدامی انجام نشد.")));return;}foreground(()->{if(!live()||"attempted".equals(st.state))return;st.state="attempted";db.updateStep(st.row,st.state,"dispatching");state("executing");try{operation.run();}catch(Exception e){finish(error(e));}});};if(spec.risk>=2){state("approval");ui.approval(label(st.call.name),exact,perform);}else perform.accept(true);});}
        public <T> void query(Callable<T> op,Consumer<T> cb){if(!live())return;future=io.submit(()->{try{T result=op.call();main.post(()->{if(live())try{cb.accept(result);}catch(Exception e){finish(error(e));}});}catch(Exception e){main.post(()->{if(live())finish(error(e));});}});}
        public void finish(Receipt r){if(live())complete(st,r);}
    }
    private void complete(Step st,Receipt r){if(active!=st||!running)return;clearDeadline();if(r.ok()){db.updateStep(st.row,"executed","result_returned");state("verifying");}st.state=r.state;st.code=r.code;st.detail=r.detail;db.updateStep(st.row,r.state,r.code);if(r.ok()){switch(st.call.name){case "launch_app":lastSurface=lastApp=st.call.get("name");break;case "open_interface":lastSurface=st.call.get("surface");break;case "web_search":lastSurface=st.call.get("engine");break;case "open_url":lastSurface=st.call.get("url").contains("youtube.com")?"youtube":"browser";break;case "maps":lastSurface="maps";break;case "open_panel":lastSurface="settings";break;default:break;}}if(!r.ok())hasFailure=true;active=null;next++;if(cloud)results.put(Gemini.functionResult(fc.optJSONObject(next-1-batchStart),r));ui.changed();if(!r.ok()&&(r.state.equals("declined")||!cloud||r.code.equals("duplicate_blocked")||r.code.contains("timeout")||r.code.equals("scroll_unverified"))){finish(r.state.equals("declined")?"declined":"failed",r.detail);return;}later(this::advance);}
    private void summarize(){boolean hand=false;StringBuilder b=new StringBuilder();for(Step st:steps){hand|=st.state.equals("handed_off");if(b.length()>0)b.append("\n\n");b.append(st.detail);}finish(hasFailure?"partial":hand?"handoff":"completed",b.toString());}
    private void finish(String status,String text){if(!running)return;clearDeadline();resume=null;for(Step s:steps)if(s.state.equals("requested")||s.state.equals("attempted")){s.state=s.state.equals("attempted")?"interrupted":"skipped";db.updateStep(s.row,s.state,"not_replayed");}db.end(task,status);stage=status;report=text;progress="";running=false;active=null;history=null;fc=null;results=null;ledger.clear();ui.changed();ui.done(text);}
    private void deadline(int ms,Runnable r){clearDeadline();timeout=r;main.postDelayed(r,ms);}private void clearDeadline(){if(timeout!=null){main.removeCallbacks(timeout);timeout=null;}}
    public void cancel(){if(!running)return;generation++;ai.cancel();if(future!=null)future.cancel(true);WindowReceiptService.disarm();finish("cancelled",t("Stopped. Already handed-off actions were not reversed or retried.","متوقف شد. اقدام‌های واگذارشده لغو یا تکرار نشدند."));ui.dismissGate();}
    public void clearSession(){if(!running){steps.clear();report="";title="";task=-1;stage="idle";}lastApp="";lastSurface="";clarification="";lastCommand="";ui.changed();}
    public void close(){cancel();io.shutdownNow();}
}
