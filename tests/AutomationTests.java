package cn.dormnative.app;

public final class AutomationTests {
    private static int passed;
    private static void check(boolean value,String name){if(!value)throw new AssertionError(name);passed++;}
    private static ProximityGate gate(){return new ProximityGate(-60,-80);}
    private static void far(ProximityGate g,long from){for(int i=0;i<4;i++)check(!g.sample(from+i*4000,-85),"departure alone must not open");}
    public static void main(String[] args){
        ProximityGate g=gate();for(int i=0;i<50;i++)check(!g.sample(i*1000,-45),"startup while near");check(!g.armed(),"near startup disarmed");
        g=gate();check(!g.sample(1,-85),"one weak sample");check(!g.sample(600000,-40),"silence is not departure");check(!g.armed(),"silence cannot arm");
        g=gate();for(int i=0;i<8;i++)check(!g.sample(i*9000,-90),"sparse weak samples");check(!g.armed(),"gaps break departure streak");
        g=gate();far(g,1);check(g.armed(),"continuous departure arms");check(!g.sample(14001,-55),"first near");check(!g.sample(15001,-55),"second near");check(g.sample(16001,-55),"third sustained near opens once");
        for(int i=1;i<110;i++)check(!g.sample(16001+i*1000,-40),"no duplicate or timer-only rearm");check(!g.armed(),"remaining near does not rearm");
        check(!g.sample(150000,-40),"cooldown expiry alone not rearm");far(g,160000);check(g.armed(),"new departure after cooldown");check(!g.sample(174000,-55),"return1");check(!g.sample(175000,-55),"return2");check(g.sample(176000,-55),"new departure return opens");
        g=gate();far(g,1);check(!g.sample(14001,-55),"jitter near1");check(!g.sample(15001,-65),"intermediate breaks near");check(!g.sample(16001,-55),"near restarts");check(!g.sample(17001,-55),"not enough after jitter");check(g.sample(18001,-55),"sustained after jitter");
        g=gate();far(g,1);check(!g.sample(14001,-55),"duplicate first");for(int i=0;i<9;i++)check(!g.sample(14001,-40),"duplicate timestamp rejected");check(!g.sample(15001,-55),"only two actual samples");check(g.sample(16001,-55),"third actual sample");
        g=gate();far(g,1);g.consume(13000);check(!g.armed(),"manual operation consumes auto opportunity");check(!g.sample(14000,-40),"manual cooldown");
        g=gate();far(g,1);g=gate();check(!g.sample(14001,-40),"service restart cannot restore armed state");
        boolean rejected=false;try{new ProximityGate(-60,-65);}catch(IllegalArgumentException e){rejected=true;}check(rejected,"hysteresis minimum");
        check(SignalCalibration.median(new int[]{-51,-55,-49,-56,-54})==-54,"median robust ordering");
        rejected=false;try{SignalCalibration.median(new int[]{-50,-55});}catch(IllegalArgumentException e){rejected=true;}check(rejected,"too few samples");
        rejected=false;try{SignalCalibration.thresholds(-60,-70);}catch(IllegalArgumentException e){rejected=true;}check(rejected,"overlapping near far calibration");
        int[] t=SignalCalibration.thresholds(-55,-85);check(t[0]==-58&&t[1]==-82,"calibrated thresholds");
        Object a=OperationGuard.acquire();check(a!=null,"first lease");check(OperationGuard.acquire()==null,"mutual exclusion");OperationGuard.release(new Object());check(OperationGuard.busy(),"foreign release ignored");OperationGuard.release(a);Object b=OperationGuard.acquire();OperationGuard.release(a);check(OperationGuard.busy(),"stale release ignored");OperationGuard.release(b);check(!OperationGuard.busy(),"released");
        ScanSession session=new ScanSession();long old=session.begin(10000);
        check(!session.accept(old,10010,9999,-50),"pre-registration radio data rejected");
        check(session.accept(old,10020,10010,-50),"new radio sample accepted");
        check(!session.accept(old,10030,10010,-40),"duplicate radio sample rejected");
        check(!session.accept(old,20000,13000,-50),"delayed callback rejected");
        check(!session.accept(old,20000,21000,-50),"future sample rejected");
        check(!session.accept(old,20000,19000,127),"unknown RSSI rejected");
        check(session.accept(old,20000,19000,-50),"bad sample did not advance valid cursor");
        session.end();check(!session.accept(old,22000,22000,-50),"callbacks after stop rejected");
        long fresh=session.begin(23000);check(!session.accept(old,24000,24000,-50),"old registration cannot act in new session");
        check(!session.accept(fresh,24000,22000,-50),"old hardware data cannot act in new session");
        check(session.accept(fresh,24000,24000,-50),"new session works");
        int[] nearData={-55,-56,-54,-56,-55,-54,-55,-56};int[] farData={-85,-86,-84,-85,-85,-84,-86,-85};
        SignalCalibration.Profile np=SignalCalibration.profile(nearData,12000),fp=SignalCalibration.profile(farData,12000);
        t=SignalCalibration.thresholds(np,fp);check(t[0]==-58&&t[1]==-82,"stable complete pair accepted");
        rejected=false;try{SignalCalibration.profile(nearData,9000);}catch(IllegalArgumentException e){rejected=true;}check(rejected,"short radio burst cannot calibrate");
        rejected=false;try{SignalCalibration.profile(new int[]{-90,-85,-80,-65,-60,-50,-45,-40},12000);}catch(IllegalArgumentException e){rejected=true;}check(rejected,"high variance rejected");
        np=SignalCalibration.profile(new int[]{-68,-68,-65,-55,-54,-53,-52,-52,-50,-49},12000);
        fp=SignalCalibration.profile(new int[]{-85,-84,-83,-82,-80,-75,-73,-71,-70,-69},12000);
        rejected=false;try{SignalCalibration.thresholds(np,fp);}catch(IllegalArgumentException e){rejected=true;}check(rejected,"overlapping distributions rejected despite separated medians");
        np=SignalCalibration.profile(new int[]{-110,-56,-55,-55,-55,-54,-54,-53},12000);check(np.median==-55,"single outlier does not dominate profile");
        MonitorPolicy observer=new MonitorPolicy(true,gate());
        for(int i=0;i<4;i++)check(observer.sample(1+i*4000,-85)==MonitorPolicy.Decision.NONE,"observation departure alone");
        check(observer.sample(14001,-50)==MonitorPolicy.Decision.NONE,"observation approaching1");check(observer.sample(15001,-50)==MonitorPolicy.Decision.NONE,"observation approaching2");
        check(observer.sample(16001,-50)==MonitorPolicy.Decision.OBSERVE,"observer emits preview only");
        for(int i=1;i<=5;i++)check(observer.sample(16001+i*1000,-40)!=MonitorPolicy.Decision.UNLOCK,"observer never upgrades mode");
        MonitorPolicy automatic=new MonitorPolicy(false,gate());
        check(automatic.sample(20000,-40)==MonitorPolicy.Decision.NONE,"new actual session does not inherit observer departure");
        for(int i=0;i<4;i++)check(automatic.sample(30000+i*4000,-85)==MonitorPolicy.Decision.NONE,"auto new departure");
        check(automatic.sample(44000,-50)==MonitorPolicy.Decision.NONE,"auto new near1");check(automatic.sample(45000,-50)==MonitorPolicy.Decision.NONE,"auto new near2");check(automatic.sample(46000,-50)==MonitorPolicy.Decision.UNLOCK,"only explicit auto mode emits unlock");
        check(automatic.sample(47000,-50)==MonitorPolicy.Decision.NONE,"one attempt per approach");
        ArrivalGate arrival=new ArrivalGate(1000,-55);
        check(!arrival.sample(1000,-40),"arrival grace blocks near start");check(!arrival.sample(15999,-40),"arrival grace cannot open");
        check(!arrival.sample(16000,-40),"arrival near first");check(!arrival.sample(16000,-40),"duplicate near sample rejected");
        check(!arrival.sample(17000,-40),"arrival near second");check(arrival.sample(18000,-40),"explicit one-shot opens without far departure");
        for(int i=0;i<100;i++)check(!arrival.sample(19000+i*1000,-35),"arrival one shot never re-arms");
        check(!arrival.active(18000),"one-shot consumed");check(arrival.withinWindow(18000),"in-flight condition retains time window");
        ArrivalGate silence=new ArrivalGate(0,-55);check(!silence.sample(15000,-50),"first near");check(!silence.sample(19000,-50),"gap resets near");check(!silence.sample(20000,-50),"second after gap");check(silence.sample(21000,-50),"fresh sequence after gap");
        ArrivalGate jitter=new ArrivalGate(0,-55);jitter.sample(15000,-50);jitter.sample(16000,-80);check(!jitter.sample(17000,-50),"weak signal resets streak");check(!jitter.sample(18000,-50),"still second");check(jitter.sample(19000,-50),"third after weakness");
        ArrivalGate deadline=new ArrivalGate(0,-55);deadline.sample(598000,-40);deadline.sample(599000,-40);check(!deadline.sample(600000,-40),"deadline prohibits final sample");check(!deadline.withinWindow(600000),"in-flight operation also expires");
        check(!deadline.sample(-1,-40),"negative monotonic time rejected");
        ArrivalGate cancel=new ArrivalGate(0,-55);cancel.sample(15000,-40);cancel.cancel();check(!cancel.sample(17000,-40),"cancel blocks trigger");
        ArrivalGate invalid=new ArrivalGate(0,-55);check(!invalid.sample(15000,127),"unknown signal rejected");check(!invalid.sample(16000,-40),"invalid sample not counted");check(!invalid.sample(17000,-40),"two valid only");check(invalid.sample(18000,-40),"third valid accepted");
        ArrivalGate measuredDoor=new ArrivalGate(0,-65);
        check(!measuredDoor.sample(15000,-60),"measured door first sample");
        check(!measuredDoor.sample(16000,-60),"measured door second sample");
        check(measuredDoor.sample(17000,-60),"measured minus60 triggers at relaxed threshold");
        check(!measuredDoor.sample(18000,-60),"measured door remains one shot");
        ArrivalGate weakerDoor=new ArrivalGate(0,-65);
        for(int i=0;i<5;i++)check(!weakerDoor.sample(15000+i*1000,-66),"below relaxed threshold does not trigger");
        ConnectionRetry retry=new ConnectionRetry();check(retry.shouldAttempt(0),"first preparation starts immediately");check(!retry.shouldAttempt(500),"no parallel retry");check(retry.shouldAttempt(2000),"second preparation after two seconds");check(!retry.shouldAttempt(5900),"second retry spacing");check(retry.shouldAttempt(6000),"retry can resume");retry.connected();check(retry.shouldAttempt(6100),"disconnect reconnects promptly");
        ScanBudget budget=new ScanBudget();for(int i=0;i<4;i++)check(budget.allow(i*1000),"bounded scan start");check(!budget.allow(5000),"fifth scan skipped");check(!budget.allow(29999),"sliding window held");check(budget.allow(30000),"old scan expires");check(!budget.allow(30001),"remaining scans still count");check(budget.allow(31000),"next slot restored");
        System.out.println("{\"ok\":true,\"assertions\":"+passed+",\"scope\":\"departure, silence, jitter, cooldown, lease, scan generations, fresh timestamps, calibration distributions, immutable observation mode\",\"screenOffRuntime\":\"NOT RUN\",\"realDoorUnlock\":\"NOT RUN\"}");
    }
}
