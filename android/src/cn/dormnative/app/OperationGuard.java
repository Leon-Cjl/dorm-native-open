package cn.dormnative.app;

/** Shared by manual, automatic and diagnostic connections. Stale owners cannot release a new lease. */
final class OperationGuard {
    private static Object owner;
    static synchronized Object acquire() { if(owner!=null)return null; owner=new Object();return owner; }
    static synchronized void release(Object lease) { if(lease!=null && owner==lease)owner=null; }
    static synchronized boolean busy() { return owner!=null; }
}
