package cn.dormnative.app;
import java.util.*;
import java.util.concurrent.*;

/** Bounded independent reads; result order is stable, any denial/timeout cancels the batch. */
final class ParallelQueries {
    private static final class Result<T>{final int index;final T value;Result(int i,T v){index=i;value=v;}}
    static <T> List<T> run(List<Callable<T>> requests,long timeoutMs)throws Exception{
        if(requests.isEmpty()||requests.size()>4||timeoutMs<=0)throw new IllegalArgumentException();
        ExecutorService pool=Executors.newFixedThreadPool(requests.size(),r->{Thread t=new Thread(r,"door-read");t.setDaemon(true);return t;});
        CompletionService<Result<T>> completion=new ExecutorCompletionService<Result<T>>(pool);
        List<Future<Result<T>>> tasks=new ArrayList<Future<Result<T>>>();
        long deadline=System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(timeoutMs);
        try{
            if(Thread.currentThread().isInterrupted())throw new InterruptedException();
            for(int i=0;i<requests.size();i++){final int index=i;tasks.add(completion.submit(()->new Result<T>(index,requests.get(index).call())));}
            List<T> values=new ArrayList<T>(Collections.nCopies(requests.size(),null));
            for(int i=0;i<requests.size();i++){
                long remaining=deadline-System.nanoTime();
                if(remaining<=0)throw new Exception("开门准备超时，请检查网络后重试");
                Future<Result<T>> future=completion.poll(remaining,TimeUnit.NANOSECONDS);
                if(future==null)throw new Exception("开门准备超时，请检查网络后重试");
                Result<T> result=future.get();values.set(result.index,result.value);
            }
            return values;
        }catch(ExecutionException e){Throwable cause=e.getCause();if(cause instanceof Exception)throw (Exception)cause;throw new Exception("开门准备失败",cause);}
        finally{for(Future<?> task:tasks)if(!task.isDone())task.cancel(true);pool.shutdownNow();}
    }
}
