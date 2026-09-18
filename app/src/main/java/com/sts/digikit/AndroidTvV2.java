package com.sts.digikit;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Log;

import com.google.polo.wire.protobuf.PoloProto;
import remote.Remotemessage;

import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPublicKey;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509ExtendedKeyManager;
import javax.net.ssl.X509TrustManager;

public class AndroidTvV2 {
    private static final String TAG="STS-AndroidTV";
    private static final String KEY_ALIAS="sts_digikit_android_tv_remote";
    private static final int PAIR_PORT=6467;
    private static final int REMOTE_PORT=6466;
    private static final int REQUESTED_FEATURES=1|2|32|64|512;

    private final Context context;

    private SSLSocket pairingSocket;
    private InputStream pairingIn;
    private OutputStream pairingOut;
    private X509Certificate pairingServerCert;
    private String pairingIp;

    private SSLSocket remoteSocket;
    private InputStream remoteIn;
    private OutputStream remoteOut;
    private final Object remoteWriteLock=new Object();
    private volatile boolean remoteConnected=false;
    private volatile int activeFeatures=REQUESTED_FEATURES;
    private Thread remoteReader;
    private CountDownLatch remoteStarted;

    public AndroidTvV2(Context context){
        this.context=context.getApplicationContext();
    }

    private static class TrustAll implements X509TrustManager {
        @Override public void checkClientTrusted(X509Certificate[] chain,String authType){}
        @Override public void checkServerTrusted(X509Certificate[] chain,String authType){}
        @Override public X509Certificate[] getAcceptedIssuers(){return new X509Certificate[0];}
    }

    private static class SingleKeyManager extends X509ExtendedKeyManager {
        private final String alias;
        private final PrivateKey key;
        private final X509Certificate cert;

        SingleKeyManager(String alias,PrivateKey key,X509Certificate cert){
            this.alias=alias; this.key=key; this.cert=cert;
        }

        @Override public String[] getClientAliases(String keyType,Principal[] issuers){return new String[]{alias};}
        @Override public String chooseClientAlias(String[] keyType,Principal[] issuers,Socket socket){return alias;}
        @Override public String[] getServerAliases(String keyType,Principal[] issuers){return null;}
        @Override public String chooseServerAlias(String keyType,Principal[] issuers,Socket socket){return null;}
        @Override public X509Certificate[] getCertificateChain(String alias){return this.alias.equals(alias)?new X509Certificate[]{cert}:null;}
        @Override public PrivateKey getPrivateKey(String alias){return this.alias.equals(alias)?key:null;}
        @Override public String chooseEngineClientAlias(String[] keyType,Principal[] issuers,SSLEngine engine){return alias;}
        @Override public String chooseEngineServerAlias(String keyType,Principal[] issuers,SSLEngine engine){return null;}
    }

    private synchronized void ensureIdentity() throws Exception{
        KeyStore ks=KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        if(!ks.containsAlias(KEY_ALIAS)){
            KeyPairGenerator gen=KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA,"AndroidKeyStore");
            Date start=new Date(System.currentTimeMillis()-86400000L);
            Date end=new Date(System.currentTimeMillis()+10L*365L*86400000L);
            KeyGenParameterSpec spec=new KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_SIGN|KeyProperties.PURPOSE_VERIFY)
                    .setKeySize(2048)
                    .setDigests(KeyProperties.DIGEST_SHA256,KeyProperties.DIGEST_SHA512)
                    .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
                    .setCertificateSubject(new javax.security.auth.x500.X500Principal("CN=STS DigiKit"))
                    .setCertificateSerialNumber(new BigInteger(64,new SecureRandom()).abs().add(BigInteger.ONE))
                    .setCertificateNotBefore(start)
                    .setCertificateNotAfter(end)
                    .build();
            gen.initialize(spec);
            gen.generateKeyPair();
        }
    }

    private KeyStore.PrivateKeyEntry identity() throws Exception{
        ensureIdentity();
        KeyStore ks=KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        KeyStore.Entry e=ks.getEntry(KEY_ALIAS,null);
        if(!(e instanceof KeyStore.PrivateKeyEntry)) throw new IllegalStateException("TV identity unavailable");
        return (KeyStore.PrivateKeyEntry)e;
    }

    private SSLContext sslContext() throws Exception{
        KeyStore.PrivateKeyEntry e=identity();
        X509Certificate cert=(X509Certificate)e.getCertificate();
        KeyManager km=new SingleKeyManager(KEY_ALIAS,e.getPrivateKey(),cert);
        SSLContext ctx=SSLContext.getInstance("TLS");
        ctx.init(new KeyManager[]{km},new TrustManager[]{new TrustAll()},new SecureRandom());
        return ctx;
    }

    private SSLSocket openTls(String ip,int port,int timeout) throws Exception{
        Socket plain=new Socket();
        plain.connect(new InetSocketAddress(ip,port),timeout);
        SSLSocketFactory factory=sslContext().getSocketFactory();
        SSLSocket ssl=(SSLSocket)factory.createSocket(plain,ip,port,true);
        ssl.setUseClientMode(true);
        ssl.setSoTimeout(timeout);
        ssl.startHandshake();
        return ssl;
    }

    private PoloProto.OuterMessage basePolo(){
        return PoloProto.OuterMessage.newBuilder()
                .setProtocolVersion(2)
                .setStatus(PoloProto.OuterMessage.Status.STATUS_OK)
                .build();
    }

    private PoloProto.OuterMessage.Builder basePoloBuilder(){
        return PoloProto.OuterMessage.newBuilder()
                .setProtocolVersion(2)
                .setStatus(PoloProto.OuterMessage.Status.STATUS_OK);
    }

    private void validatePolo(PoloProto.OuterMessage msg) throws Exception{
        if(msg==null) throw new java.io.EOFException("TV closed pairing connection");
        if(msg.getStatus()!=PoloProto.OuterMessage.Status.STATUS_OK){
            throw new SecurityException("TV pairing status: "+msg.getStatus());
        }
    }

    public synchronized boolean startPairing(String ip) throws Exception{
        closePairing();
        pairingIp=ip;

        pairingSocket=openTls(ip,PAIR_PORT,10000);
        X509Certificate[] peer=(X509Certificate[])pairingSocket.getSession().getPeerCertificates();
        if(peer.length==0) throw new SecurityException("TV certificate missing");
        pairingServerCert=peer[0];
        pairingIn=pairingSocket.getInputStream();
        pairingOut=pairingSocket.getOutputStream();

        PoloProto.PairingRequest request=PoloProto.PairingRequest.newBuilder()
                .setServiceName("atvremote")
                .setClientName("STS DigiKit")
                .build();
        PoloProto.OuterMessage m=basePoloBuilder().setPairingRequest(request).build();
        m.writeDelimitedTo(pairingOut);
        pairingOut.flush();

        PoloProto.OuterMessage ack=PoloProto.OuterMessage.parseDelimitedFrom(pairingIn);
        validatePolo(ack);
        if(!ack.hasPairingRequestAck()) throw new SecurityException("Unexpected TV pairing response");

        PoloProto.Options.Encoding enc=PoloProto.Options.Encoding.newBuilder()
                .setType(PoloProto.Options.Encoding.EncodingType.ENCODING_TYPE_HEXADECIMAL)
                .setSymbolLength(6)
                .build();
        PoloProto.Options opts=PoloProto.Options.newBuilder()
                .setPreferredRole(PoloProto.Options.RoleType.ROLE_TYPE_INPUT)
                .addInputEncodings(enc)
                .build();

        basePoloBuilder().setOptions(opts).build().writeDelimitedTo(pairingOut);
        pairingOut.flush();

        PoloProto.OuterMessage serverOptions=PoloProto.OuterMessage.parseDelimitedFrom(pairingIn);
        validatePolo(serverOptions);
        if(!serverOptions.hasOptions()) throw new SecurityException("TV did not offer pairing options");

        PoloProto.Configuration cfg=PoloProto.Configuration.newBuilder()
                .setEncoding(enc)
                .setClientRole(PoloProto.Options.RoleType.ROLE_TYPE_INPUT)
                .build();
        basePoloBuilder().setConfiguration(cfg).build().writeDelimitedTo(pairingOut);
        pairingOut.flush();

        PoloProto.OuterMessage cfgAck=PoloProto.OuterMessage.parseDelimitedFrom(pairingIn);
        validatePolo(cfgAck);
        if(!cfgAck.hasConfigurationAck()) throw new SecurityException("TV did not confirm pairing configuration");

        return true;
    }

    private byte[] hexBytes(BigInteger value){
        String hex=value.toString(16).toUpperCase(Locale.US);
        if((hex.length()&1)==1) hex="0"+hex;
        byte[] out=new byte[hex.length()/2];
        for(int i=0;i<out.length;i++){
            out[i]=(byte)Integer.parseInt(hex.substring(i*2,i*2+2),16);
        }
        return out;
    }

    private byte[] hexStringBytes(String hex){
        if((hex.length()&1)==1) hex="0"+hex;
        byte[] out=new byte[hex.length()/2];
        for(int i=0;i<out.length;i++) out[i]=(byte)Integer.parseInt(hex.substring(i*2,i*2+2),16);
        return out;
    }

    public synchronized boolean finishPairing(String code) throws Exception{
        if(pairingSocket==null || pairingIn==null || pairingOut==null || pairingServerCert==null || pairingIp==null){
            throw new IllegalStateException("No active TV pairing session");
        }
        String pin=code==null?"":code.trim().toUpperCase(Locale.US);
        if(pin.length()!=6 || !pin.matches("[0-9A-F]{6}")){
            throw new IllegalArgumentException("TV code must be 6 hexadecimal characters");
        }

        X509Certificate clientCert=(X509Certificate)identity().getCertificate();
        if(!(clientCert.getPublicKey() instanceof RSAPublicKey) || !(pairingServerCert.getPublicKey() instanceof RSAPublicKey)){
            throw new SecurityException("TV pairing requires RSA certificates");
        }

        RSAPublicKey client=(RSAPublicKey)clientCert.getPublicKey();
        RSAPublicKey server=(RSAPublicKey)pairingServerCert.getPublicKey();

        MessageDigest sha=MessageDigest.getInstance("SHA-256");
        sha.update(hexBytes(client.getModulus()));
        sha.update(hexBytes(client.getPublicExponent()));
        sha.update(hexBytes(server.getModulus()));
        sha.update(hexBytes(server.getPublicExponent()));
        sha.update(hexStringBytes(pin.substring(2)));
        byte[] digest=sha.digest();

        int expected=Integer.parseInt(pin.substring(0,2),16);
        if((digest[0]&0xff)!=expected) throw new SecurityException("Incorrect TV pairing code");

        PoloProto.Secret secret=PoloProto.Secret.newBuilder()
                .setSecret(com.google.protobuf.ByteString.copyFrom(digest))
                .build();
        basePoloBuilder().setSecret(secret).build().writeDelimitedTo(pairingOut);
        pairingOut.flush();

        PoloProto.OuterMessage ack=PoloProto.OuterMessage.parseDelimitedFrom(pairingIn);
        validatePolo(ack);
        if(!ack.hasSecretAck()) throw new SecurityException("TV rejected pairing code");

        String ip=pairingIp;
        closePairing();
        return connect(ip);
    }

    public synchronized void closePairing(){
        try{if(pairingSocket!=null)pairingSocket.close();}catch(Exception ignored){}
        pairingSocket=null;
        pairingIn=null;
        pairingOut=null;
        pairingServerCert=null;
        pairingIp=null;
    }

    public synchronized boolean connect(String ip) throws Exception{
        closeRemote();
        remoteSocket=openTls(ip,REMOTE_PORT,7000);
        remoteSocket.setSoTimeout(0);
        remoteIn=remoteSocket.getInputStream();
        remoteOut=remoteSocket.getOutputStream();
        remoteConnected=true;
        activeFeatures=REQUESTED_FEATURES;
        remoteStarted=new CountDownLatch(1);

        remoteReader=new Thread(()->remoteReadLoop(),"STS-TV-Remote");
        remoteReader.setDaemon(true);
        remoteReader.start();

        remoteStarted.await(4, TimeUnit.SECONDS);
        return remoteConnected;
    }

    private void sendRemote(Remotemessage.RemoteMessage msg) throws Exception{
        synchronized(remoteWriteLock){
            if(!remoteConnected || remoteOut==null) throw new java.io.IOException("TV remote is not connected");
            msg.writeDelimitedTo(remoteOut);
            remoteOut.flush();
        }
    }

    private void remoteReadLoop(){
        try{
            while(remoteConnected && remoteIn!=null){
                Remotemessage.RemoteMessage msg=Remotemessage.RemoteMessage.parseDelimitedFrom(remoteIn);
                if(msg==null) break;

                if(msg.hasRemoteConfigure()){
                    int supported=msg.getRemoteConfigure().getCode1();
                    activeFeatures=REQUESTED_FEATURES & supported;
                    Remotemessage.RemoteDeviceInfo info=Remotemessage.RemoteDeviceInfo.newBuilder()
                            .setUnknown1(1)
                            .setUnknown2("1")
                            .setPackageName("com.sts.digikit")
                            .setAppVersion("1.0")
                            .build();
                    Remotemessage.RemoteConfigure cfg=Remotemessage.RemoteConfigure.newBuilder()
                            .setCode1(activeFeatures)
                            .setDeviceInfo(info)
                            .build();
                    sendRemote(Remotemessage.RemoteMessage.newBuilder().setRemoteConfigure(cfg).build());
                }else if(msg.hasRemoteSetActive()){
                    Remotemessage.RemoteSetActive active=Remotemessage.RemoteSetActive.newBuilder()
                            .setActive(activeFeatures)
                            .build();
                    sendRemote(Remotemessage.RemoteMessage.newBuilder().setRemoteSetActive(active).build());
                }else if(msg.hasRemotePingRequest()){
                    Remotemessage.RemotePingResponse pong=Remotemessage.RemotePingResponse.newBuilder()
                            .setVal1(msg.getRemotePingRequest().getVal1())
                            .build();
                    sendRemote(Remotemessage.RemoteMessage.newBuilder().setRemotePingResponse(pong).build());
                }else if(msg.hasRemoteStart()){
                    if(remoteStarted!=null) remoteStarted.countDown();
                }
            }
        }catch(Exception e){
            Log.w(TAG,"TV remote connection closed: "+e.getMessage());
        }finally{
            remoteConnected=false;
            if(remoteStarted!=null) remoteStarted.countDown();
        }
    }

    public boolean isConnected(){
        return remoteConnected && remoteSocket!=null && remoteSocket.isConnected() && !remoteSocket.isClosed();
    }

    public boolean sendKey(int keyCode){
        try{
            if(!isConnected()) return false;
            Remotemessage.RemoteKeyInject inject=Remotemessage.RemoteKeyInject.newBuilder()
                    .setKeyCodeValue(keyCode)
                    .setDirection(Remotemessage.RemoteDirection.SHORT)
                    .build();
            sendRemote(Remotemessage.RemoteMessage.newBuilder().setRemoteKeyInject(inject).build());
            return true;
        }catch(Exception e){
            Log.w(TAG,"sendKey failed: "+e.getMessage());
            return false;
        }
    }

    public synchronized void closeRemote(){
        remoteConnected=false;
        try{if(remoteSocket!=null)remoteSocket.close();}catch(Exception ignored){}
        remoteSocket=null;
        remoteIn=null;
        remoteOut=null;
        if(remoteStarted!=null) remoteStarted.countDown();
    }

    public void close(){
        closePairing();
        closeRemote();
    }
}
