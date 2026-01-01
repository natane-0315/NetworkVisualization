package com.example;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class AnomalyDetector {
    // 重要なポートのベースラインを格納する Map<ポート, ベースライン>
    private static final Map<Integer, Baseline> BASELINES = new ConcurrentHashMap<>();
    private static final double THRESHOLD = 3.0; // 異常と見なす閾値 (3σルール)

    public AnomalyDetector() {
        // ベースラインの定義 (例: 443, 80)
        BASELINES.put(80, new Baseline(3.0, 1.0));
        BASELINES.put(443, new Baseline(5.0, 1.5));
    }

    // 脈拍データを受け取り、体温を計算して警告を出す
    public void checkAnomaly(int port, double currentRate) {
        if (!BASELINES.containsKey(port)) {
            return;
        }
        
        Baseline base = BASELINES.get(port);
        double mu = base.getAverage();
        double sigma = base.getStdDev();

        // 体温 (A) の計算: Zスコア A_p(t) = |R_p(t) - μ_p| / σ_p
        double anomalyScore = Math.abs(currentRate - mu) / sigma;
        
        if (anomalyScore > THRESHOLD) {
            System.err.println("🔥 警告 (体温): Port " + port + "が異常 (" + String.format("%.2f", anomalyScore) + ")");
        }
    }
    
    // ベースラインデータを保持するための内部クラス
    private static class Baseline {
        private final double average;
        private final double stdDev;
        public Baseline(double average, double stdDev) {
            this.average = average;
            this.stdDev = stdDev;
        }
        public double getAverage() { return average; }
        public double getStdDev() { return stdDev; }
    }
}