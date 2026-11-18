package ai.nexus.test;
import android.app.Activity;
import android.os.Bundle;
import android.content.*;
import android.provider.ContactsContract.*;
import android.provider.ContactsContract.CommonDataKinds.*;
import java.util.ArrayList;
/** Test APK only, restricted to adb shell. Never installed as part of Nexus. */
public final class SeedContactActivity extends Activity {
    @Override public void onCreate(Bundle b){super.onCreate(b);try{getContentResolver().delete(RawContacts.CONTENT_URI,RawContacts.SOURCE_ID+" IN (?,?)",new String[]{"nexus.qa.one","nexus.qa.two"});if(!getIntent().getBooleanExtra("clear",false)){add("one","مادر نکسوس یک","+12025550101");add("two","مادر نکسوس دو","+12025550102");}}catch(Exception e){throw new IllegalStateException("Test fixture setup failed",e);}finally{finish();}}
    private void add(String id,String name,String number)throws Exception{ArrayList<ContentProviderOperation> ops=new ArrayList<>();ops.add(ContentProviderOperation.newInsert(RawContacts.CONTENT_URI).withValue(RawContacts.ACCOUNT_TYPE,null).withValue(RawContacts.ACCOUNT_NAME,null).withValue(RawContacts.SOURCE_ID,"nexus.qa."+id).build());ops.add(ContentProviderOperation.newInsert(Data.CONTENT_URI).withValueBackReference(Data.RAW_CONTACT_ID,0).withValue(Data.MIMETYPE,StructuredName.CONTENT_ITEM_TYPE).withValue(StructuredName.DISPLAY_NAME,name).build());ops.add(ContentProviderOperation.newInsert(Data.CONTENT_URI).withValueBackReference(Data.RAW_CONTACT_ID,0).withValue(Data.MIMETYPE,Phone.CONTENT_ITEM_TYPE).withValue(Phone.NUMBER,number).withValue(Phone.TYPE,Phone.TYPE_MOBILE).build());getContentResolver().applyBatch("com.android.contacts",ops);}
}
