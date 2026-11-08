package cn.dormnative.app;
public final class ProtocolTests {
 private static int passed;
 private static void check(boolean ok){if(!ok)throw new AssertionError("check "+passed);passed++;}
 public static void main(String[] args){
  if(args.length>0 && args[0].equals("--vectors")){
   StringBuilder result=new StringBuilder("[");
   for(long id:new long[]{1,0x01020304L,0x80000000L,0xffffffffL}){
    if(result.length()>1)result.append(',');StringBuilder hex=new StringBuilder();for(byte b:Protocol.unlock(id))hex.append(String.format("%02x",b&255));
    result.append("{\"id\":").append(id).append(",\"hex\":\"").append(hex).append("\"}");
   }System.out.println(result.append(']'));return;
  }
  byte[] c=Protocol.unlock(0x01020304L);check(c.length==15);check((c[0]&255)==0xaa);check(c[10]==1&&c[11]==2&&c[12]==3&&c[13]==4);
  int sum=0;for(int i=0;i<14;i++)sum+=c[i]&255;check((c[14]&255)==(sum&255));
  check((Protocol.unlock(0xffffffffL)[10]&255)==255);
  for(long bad:new long[]{0,-1,0x100000000L}){boolean rejected=false;try{Protocol.unlock(bad);}catch(IllegalArgumentException e){rejected=true;}check(rejected);}
  check(Protocol.validMac("AA:BB:CC:DD:EE:FF"));check(!Protocol.validMac("00:00:00:00:00:00"));check(!Protocol.validMac("AA:BB:CC"));check(!Protocol.validMac("AA:BB:CC:DD:EE:FG"));
  byte[] ack=new byte[11];ack[9]=1;check(!Protocol.successStatus(ack,false));check(Protocol.successStatus(ack,true));ack[10]=1;check(!Protocol.successStatus(ack,true));check(!Protocol.successStatus(new byte[10],true));check(!Protocol.successStatus(null,true));
  System.out.println("{\"ok\":true,\"passed\":"+passed+",\"scope\":\"static v53 packet port and status guards only\",\"deviceRuntime\":\"NOT RUN\",\"realDoorUnlock\":\"NOT RUN\"}");
 }
}
