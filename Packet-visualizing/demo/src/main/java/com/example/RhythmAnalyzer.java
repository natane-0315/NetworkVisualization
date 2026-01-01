package com.example;

import java.time.Instant;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class RhythmAnalyzer {
    private Instant lastPacketTime = null;
    private final List<Long> iatList = new ArrayList<>(); 
    private final int MAX_SAMPLES = 100;
    private static final double STABLE_THRESHOLD = 30.0; // IAT標準偏差の閾値 (ms)

    // パケットごとに呼ばれ、リズムの計測と分析を行う
    public void analyzeRhythm(Instant currentTime) {
        if (lastPacketTime != null) {
            // 呼吸 (IAT) の計測
            long iatMillis = Duration.between(lastPacketTime, currentTime).toMillis();
            iatList.add(iatMillis);
        }
        lastPacketTime = currentTime;

        // 100個のサンプルが溜まったら分析を実行
        if (iatList.size() >= MAX_SAMPLES) {
            double averageIat = iatList.stream().mapToLong(Long::longValue).average().orElse(0.0);
            double stdDevIat = calculateStdDev(averageIat); 

            System.out.println("🎵 呼吸分析: 平均IAT=" + String.format("%.1f ms", averageIat) + 
                               ", 標準偏差=" + String.format("%.1f ms", stdDevIat));
            
            // 性質の判断（標準偏差が小さい＝リアルタイム通信）
            if (stdDevIat < STABLE_THRESHOLD) { 
                System.out.println("🟢 リズム安定: リアルタイム通信の可能性");
            }

            iatList.clear();
        }
    }

    private double calculateStdDev(double average) {
        double sumOfSquaredDifferences = iatList.stream()
            .mapToDouble(iat -> Math.pow(iat - average, 2))
            .sum();
        return Math.sqrt(sumOfSquaredDifferences / iatList.size());
    }
}