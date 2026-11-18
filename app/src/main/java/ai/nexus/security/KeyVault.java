package ai.nexus.security;
import android.content.*;
import android.security.keystore.*;
import android.util.Base64;
import java.security.KeyStore;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import java.nio.charset.StandardCharsets;
/** AndroidKeyStore AES-GCM. No plaintext fallback, backup, URL parameter or logging. */
public final class KeyVault {
    private static final String ALIAS="nexus.gemini.v1"; private final SharedPreferences p;
    public KeyVault(Context c){p=c.getSharedPreferences("vault",0);}
    private SecretKey key(boolean create)throws Exception{KeyStore k=KeyStore.getInstance("AndroidKeyStore");k.load(null);if(k.containsAlias(ALIAS))return (SecretKey)k.getKey(ALIAS,null);if(!create)throw new IllegalStateException("Key unavailable");KeyGenerator gen=KeyGenerator.getInstance("AES","AndroidKeyStore");gen.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build());return gen.generateKey();}
    public synchronized void save(String s)throws Exception{if(s.isEmpty()){clear();return;}if(!s.matches("[A-Za-z0-9_-]{16,180}"))throw new IllegalArgumentException("Invalid key format");Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,key(true));String iv=Base64.encodeToString(c.getIV(),2),data=Base64.encodeToString(c.doFinal(s.getBytes(StandardCharsets.UTF_8)),2);if(!p.edit().putString("iv",iv).putString("cipher",data).commit())throw new IllegalStateException("Storage unavailable");}
    public synchronized String read()throws Exception{if(!present())return "";Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.DECRYPT_MODE,key(false),new GCMParameterSpec(128,Base64.decode(p.getString("iv",""),2)));return new String(c.doFinal(Base64.decode(p.getString("cipher",""),2)),StandardCharsets.UTF_8);}
    public boolean present(){return p.contains("cipher");}
    public synchronized void clear()throws Exception{p.edit().clear().commit();KeyStore k=KeyStore.getInstance("AndroidKeyStore");k.load(null);k.deleteEntry(ALIAS);}
}
