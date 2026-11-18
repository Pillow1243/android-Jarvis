package ai.nexus.device;
import android.content.*;
import android.database.Cursor;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import ai.nexus.core.Text;
import java.util.*;
public final class People {
    public static final class Person {public final String name,number; public Person(String a,String b){name=a;number=b;}public String display(){return name+"\n"+number;}}
    public static List<Person> find(Context c,String query){List<Person> out=new ArrayList<>();Set<String> seen=new HashSet<>();try(Cursor cur=c.getContentResolver().query(Phone.CONTENT_URI,new String[]{Phone.DISPLAY_NAME,Phone.NUMBER},null,null,Phone.DISPLAY_NAME+" ASC")){if(cur==null)throw new IllegalStateException("Contacts provider unavailable");while(cur.moveToNext()&&out.size()<30){String name=cur.getString(0),num=cur.getString(1);if(name!=null&&num!=null&&Text.phone(num)&&Text.contactMatches(name,query)&&seen.add(name+"/"+num))out.add(new Person(name,num));}}return out;}
}
