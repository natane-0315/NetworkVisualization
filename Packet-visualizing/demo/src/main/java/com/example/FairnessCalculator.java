package com.example;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class FairnessCalculator {
    private final Map<String, Long> ipByteCounts = new ConcurrentHashMap<>();
    private static final double LOW_ENTROPY_THRESHOLD = 1.0; 

    // IPアドレス(匿名化済み)とバイト量を受け取る
    public void aggregate(String anonSrcIp, int byteSize) {
        ipByteCounts.merge(anonSrcIp, (long) byteSize, Long::sum);
    }

    // MainAppのタイマーから定期的に呼び出され、計算とリセットを行う
    public double calculateFairness() { //void → double
        if (ipByteCounts.size() < 2) {
            ipByteCounts.clear(); 
            return 0.0;
        }

        double totalBytes = ipByteCounts.values().stream().mapToLong(Long::longValue).sum();
        double entropy = 0.0;
        
        // 富の集中度 (E) の計算
        for (Long bytes : ipByteCounts.values()) {
            double Pi = (double) bytes / totalBytes; 
            
            if (Pi > 0) { 
                entropy -= Pi * (Math.log(Pi) / Math.log(2)); 
            }
        }
        
        //System.out.println("[集中度] 富の集中度 (エントロピー): " + String.format("%.2f", entropy));
        //GUIへの出力に変更
        if (entropy < LOW_ENTROPY_THRESHOLD) { 
            System.err.println("⚠️ 警告: 富が少数のIPに集中しています (エントロピー低)");
        }
        
        ipByteCounts.clear(); 

        return entropy;
    }
}