package cn.dormnative.app;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.net.*;
import java.io.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import javax.net.ssl.HttpsURLConnection;
import javax.crypto.*;
import javax.crypto.spec.*;
import org.json.*;

public class ParallelApiTests {
    static int checks;
    static JSONObject seed,row;
    static volatile String denial="";
    static volatile CountDownLatch barrier;
    static AtomicInteger calls=new AtomicInteger(),peak=new AtomicInteger(),active=new AtomicInteger();
    static void check(boolean b){checks++;if(!b)throw new AssertionError("parallel "+checks);}
    static final class FakeConnection extends HttpsURLConnection{
        ByteArrayOutputStream request=new ByteArrayOutputStream();
        FakeConnection(URL u){super(u);}
        public void disconnect(){}public boolean usingProxy(){return false;}public void connect(){}
        public String getCipherSuite(){return "fixture";}
        public java.security.cert.Certificate[] getLocalCertificates(){return null;}
        public java.security.cert.Certificate[] getServerCertificates(){return null;}
        public OutputStream getOutputStream(){return request;}
        public int getResponseCode(){return 200;}
        public InputStream getInputStream()throws IOException{
            int n=active.incrementAndGet();peak.accumulateAndGet(n,Math::max);calls.incrementAndGet();
            try{
                barrier.countDown();if(!barrier.await(2,TimeUnit.SECONDS))throw new IOException("requests did not run concurrently");
                JSONObject wrapped=new JSONObject(new String(request.toByteArray(),StandardCharsets.UTF_8));
                Cipher cipher=Cipher.getInstance("AES/CBC/PKCS5Padding");
                cipher.init(Cipher.DECRYPT_MODE,new SecretKeySpec("1234567890abcdef".getBytes(StandardCharsets.UTF_8),"AES"),new IvParameterSpec("abcdef1234567890".getBytes(StandardCharsets.UTF_8)));
                JSONObject body=new JSONObject(new String(cipher.doFinal(java.util.Base64.getDecoder().decode(wrapped.getString("data"))),StandardCharsets.UTF_8));
                String route=body.optString("url",url.getPath());JSONObject response=new JSONObject();
                if(route.equals("/staff/getStaff")){
                    JSONObject p=new JSONObject().put("staffID",seed.getString("staffID")).put("uniqueID",denial.equals("person")?"999":seed.getString("personId"));
                    response.put("staff",new JSONArray().put(p));
                }else if(route.equals("/1075/getStaffRole")){
                    response.put("data",new JSONArray().put(new JSONObject().put("roleType",denial.equals("role")?1:0)));
                }else if(route.equals("/wxProgram/getCompanyListEncryption")){
                    response.put("data",denial.equals("org")?new JSONArray():new JSONArray().put(new JSONObject().put("companyID",seed.getString("companyID"))));
                }else if(route.equals("/1081/getAccess")){
                    JSONObject r=new JSONObject(row.toString()).put("effectiveTime","").put("expiryTime","");
                    if(denial.equals("room"))r.put("eStatus",1);
                    response.put("data",new JSONArray().put(r));
                }else throw new AssertionError("Unexpected endpoint");
                return new ByteArrayInputStream(new JSONObject().put("code",200).put("body",response).toString().getBytes(StandardCharsets.UTF_8));
            }catch(Exception e){throw new IOException("Fixture response failed",e);}
            finally{active.decrementAndGet();}
        }
    }
    static void setup(String mode){denial=mode;barrier=new CountDownLatch(4);calls.set(0);peak.set(0);}
    public static void main(String[] args)throws Exception{
        URL.setURLStreamHandlerFactory(protocol->new URLStreamHandler(){protected URLConnection openConnection(URL u){
            if(!"https".equals(u.getProtocol())||!(u.getHost().equals("gy.bostex.net")||u.getHost().equals("applets.bostex.net")))throw new AssertionError("Unexpected host");
            return new FakeConnection(u);
        }});
        JSONObject envelope=new JSONObject(new String(Files.readAllBytes(Paths.get(args[0])),StandardCharsets.UTF_8));
        seed=MigrationTrust.verify(envelope);
        row=new JSONObject(new String(Files.readAllBytes(Paths.get(args[1])),StandardCharsets.UTF_8)).getJSONObject("room");
        setup("");check(new NativeApi(envelope).grants().size()==1);check(calls.get()==4);check(peak.get()==4);
        for(String mode:new String[]{"person","role","org","room"}){
            setup(mode);boolean rejected=false;try{new NativeApi(envelope).grants();}catch(Exception e){rejected=true;}check(rejected);check(calls.get()==4);
        }
        CountDownLatch order=new CountDownLatch(1);
        List<Callable<Integer>> tasks=Arrays.asList(()->{order.await();return 10;},()->{order.countDown();return 20;});
        check(ParallelQueries.run(tasks,1000).equals(Arrays.asList(10,20)));
        CountDownLatch started=new CountDownLatch(1),interrupted=new CountDownLatch(1);
        List<Callable<Integer>> failed=Arrays.asList(()->{started.countDown();try{new CountDownLatch(1).await();}catch(InterruptedException e){interrupted.countDown();throw e;}return 1;},()->{started.await();throw new Exception("fixture rejected");});
        boolean rejected=false;try{ParallelQueries.run(failed,1000);}catch(Exception e){rejected=true;}check(rejected);check(interrupted.await(1,TimeUnit.SECONDS));
        CountDownLatch expired=new CountDownLatch(1);
        rejected=false;try{ParallelQueries.run(Collections.singletonList(()->{try{new CountDownLatch(1).await();}catch(InterruptedException e){expired.countDown();throw e;}return 1;}),50);}catch(Exception e){rejected=true;}
        check(rejected);check(expired.await(1,TimeUnit.SECONDS));
        Thread.currentThread().interrupt();rejected=false;try{ParallelQueries.run(Collections.singletonList(()->1),1000);}catch(InterruptedException e){rejected=true;}finally{Thread.interrupted();}check(rejected);
        System.out.println("{\"ok\":true,\"assertions\":"+checks+",\"networkUsed\":false,\"maxConcurrentFixtureRequests\":4,\"scope\":\"production NativeApi through in-memory HTTPS fixtures plus batch cancellation/deadline/order\"}");
    }
}

