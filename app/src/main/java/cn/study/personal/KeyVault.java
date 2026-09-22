package cn.study.personal;

import android.content.Context;
import android.security.keystore.*;
import android.util.Base64;
import java.security.KeyStore;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;

final class KeyVault {
    private static final String ALIAS="study_personal_deepseek_v1";
    private final Context context;
    KeyVault(Context c){context=c.getApplicationContext();}
    private SecretKey key() throws Exception {
        KeyStore ks=KeyStore.getInstance("AndroidKeyStore");ks.load(null);
        if(!ks.containsAlias(ALIAS)){
            KeyGenerator g=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
            g.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());g.generateKey();
        }return ((KeyStore.SecretKeyEntry)ks.getEntry(ALIAS,null)).getSecretKey();
    }
    boolean hasKey(){return context.getSharedPreferences("vault",0).contains("cipher");}
    void save(String value) throws Exception {
        Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,key());
        String encrypted=Base64.encodeToString(c.doFinal(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)),Base64.NO_WRAP);
        boolean ok=context.getSharedPreferences("vault",0).edit().putString("cipher",encrypted).putString("iv",Base64.encodeToString(c.getIV(),Base64.NO_WRAP)).commit();
        if(!ok)throw new Exception("密钥保存失败");
    }
    String get() throws Exception {
        android.content.SharedPreferences p=context.getSharedPreferences("vault",0);if(!hasKey())return "";
        Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Base64.decode(p.getString("iv",""),Base64.NO_WRAP)));
        return new String(c.doFinal(Base64.decode(p.getString("cipher",""),Base64.NO_WRAP)),java.nio.charset.StandardCharsets.UTF_8);
    }
    void clear(){context.getSharedPreferences("vault",0).edit().clear().commit();try{KeyStore ks=KeyStore.getInstance("AndroidKeyStore");ks.load(null);ks.deleteEntry(ALIAS);}catch(Exception ignored){}}
}
