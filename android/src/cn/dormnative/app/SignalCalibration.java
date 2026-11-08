package cn.dormnative.app;

import java.util.Arrays;

final class SignalCalibration {
    static final int VERSION=2;
    static final class Profile {
        final int median,low,high,count;
        Profile(int median,int low,int high,int count){this.median=median;this.low=low;this.high=high;this.count=count;}
    }
    static Profile profile(int[] samples,long span){
        if(samples.length<8||span<10000)throw new IllegalArgumentException("有效广播需要至少 8 个样本并覆盖 10 秒；请唤醒门锁后重新采集");
        median(samples);int[] sorted=samples.clone();Arrays.sort(sorted);
        int low=sorted[(sorted.length-1)/5],high=sorted[(sorted.length-1)*4/5];
        if(high-low>16)throw new IllegalArgumentException("此处信号波动过大，请保持手机姿势和位置不变后重新采集");
        return new Profile(sorted[sorted.length/2],low,high,sorted.length);
    }
    static int[] thresholds(Profile near,Profile far){
        if(near.low-far.high<8)throw new IllegalArgumentException("两处信号分布重叠，请走到信号更弱、但仍有广播的位置重新采集远处信号");
        int[] result=thresholds(near.median,far.median);
        if(result[0]>near.median||result[1]<far.median)throw new IllegalArgumentException("信号超出可用范围，请重新选择采集位置");
        return result;
    }
    static int median(int[] samples){
        if(samples.length<5)throw new IllegalArgumentException("有效样本不足 5 个，请唤醒门锁后重试");
        int[] copy=samples.clone();for(int r:copy)if(r< -120||r>=0)throw new IllegalArgumentException("信号样本无效");
        Arrays.sort(copy);return copy[copy.length/2];
    }
    static int[] thresholds(int nearMedian,int farMedian){
        if(nearMedian-farMedian<14)throw new IllegalArgumentException("门边与远处信号差不足 14 dB，暂不能可靠区分；请换位置重新采样");
        int near=Math.max(-85,Math.min(-30,nearMedian-3));int far=Math.max(-110,farMedian+3);
        new ProximityGate(near,far);return new int[]{near,far};
    }
}
