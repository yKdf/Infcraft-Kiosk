package com.biel.kiosk;
import android.content.Context;
import java.io.*;
import java.security.*;
import javax.net.ssl.*;
import org.json.*;
class TlsConfig {
 static boolean ready(Context c){return new File(c.getFilesDir(),"admin-tls.p12").isFile();}
 static SSLContext context(byte[] bytes,String password)throws Exception {KeyStore store=KeyStore.getInstance("PKCS12");store.load(new ByteArrayInputStream(bytes),password.toCharArray());boolean key=false;java.util.Enumeration<String> aliases=store.aliases();while(aliases.hasMoreElements()){String alias=aliases.nextElement();if(store.isKeyEntry(alias)){((java.security.cert.X509Certificate)store.getCertificate(alias)).checkValidity();key=true;}}if(!key)throw new Exception("Certificado sem chave privada.");KeyManagerFactory factory=KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());factory.init(store,password.toCharArray());SSLContext ssl=SSLContext.getInstance("TLS");ssl.init(factory.getKeyManagers(),null,null);return ssl;}
 static void install(Context c,JSONObject body)throws Exception {byte[] data=android.util.Base64.decode(body.getString("pkcs12"),android.util.Base64.DEFAULT);String password=body.getString("password");context(data,password);File temp=new File(c.getFilesDir(),"admin-tls.tmp");try(FileOutputStream out=new FileOutputStream(temp)){out.write(data);out.getFD().sync();}if(!temp.renameTo(new File(c.getFilesDir(),"admin-tls.p12")))throw new IOException("Falha ao salvar certificado.");c.getSharedPreferences("tls",0).edit().putString("password",password).commit();}
 static SSLServerSocket listen(Context c)throws Exception {File f=new File(c.getFilesDir(),"admin-tls.p12");ByteArrayOutputStream out=new ByteArrayOutputStream();try(FileInputStream in=new FileInputStream(f)){byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}SSLServerSocket server=(SSLServerSocket)context(out.toByteArray(),c.getSharedPreferences("tls",0).getString("password","")).getServerSocketFactory().createServerSocket(8080);server.setEnabledProtocols(new String[]{"TLSv1.2"});return server;}
}
