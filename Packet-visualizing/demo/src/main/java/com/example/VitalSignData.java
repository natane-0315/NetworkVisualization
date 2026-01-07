package com.example;

import java.util.Map;
import java.util.Collections;
import java.util.HashMap;

/**
 * TimeAndPortAggregatorの結果（脈拍と密輸率）をGUIに渡すためのデータクラス
 */
public class VitalSignData {
    public final Map<Integer, Double> pulseRates;
    public final double smugglingRate;
    public final double mbps;

    public VitalSignData(Map<Integer, Double> pulseRates, double smugglingRate , double mbps) {
        // マップは変更不可にして安全性を高めます
        this.pulseRates = Collections.unmodifiableMap(new HashMap<>(pulseRates));
        this.smugglingRate = smugglingRate;
        this.mbps = mbps;
    }
}