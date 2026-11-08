package cn.dormnative.app;

/** The mode is immutable for a monitoring session; observing never emits an unlock decision. */
final class MonitorPolicy {
    enum Decision { NONE, OBSERVE, UNLOCK }
    private final boolean observation;
    private final ProximityGate gate;
    MonitorPolicy(boolean observation,ProximityGate gate){this.observation=observation;this.gate=gate;}
    Decision sample(long now,int rssi){return !gate.sample(now,rssi)?Decision.NONE:observation?Decision.OBSERVE:Decision.UNLOCK;}
}
