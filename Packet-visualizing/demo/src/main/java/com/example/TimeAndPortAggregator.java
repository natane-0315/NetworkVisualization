package com.example;

import org.pcap4j.packet.Packet;
import org.pcap4j.packet.IpV4Packet;
import org.pcap4j.packet.TcpPacket;
import org.pcap4j.packet.UdpPacket;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.time.Instant;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

public class TimeAndPortAggregator {
    private final Map<Integer, Long> portPacketCounts = new ConcurrentHashMap<>();
    private long highPortBytes = 0;
    private long totalBytes = 0;
    private Instant lastLogTime = Instant.now();
    
    private final AnomalyDetector detector; // AnomalyDetectorへの参照

    public TimeAndPortAggregator(AnomalyDetector detector) {
        this.detector = detector;
    }

    // パケットごとに呼ばれ、集計を行う
    public void aggregate(Instant currentTime, Packet packet) {
        int byteSize = packet.length();
        int dstPort = getDstPort(packet);
        
        if (dstPort != 0) {
            portPacketCounts.merge(dstPort, 1L, Long::sum); 
        }
        
        totalBytes += byteSize;
        if (dstPort >= 49152 && dstPort <= 65535) {
            highPortBytes += byteSize;
        }
    }
    
    // MainAppのタイマーから定期的に呼び出され、計算とリセットを行う
    public VitalSignData calculateAndReset(Instant currentTime) {

        long elapsedSeconds = Duration.between(lastLogTime, currentTime).getSeconds();
        if (elapsedSeconds == 0) return new VitalSignData(new HashMap<>(), 0.0, 0.0); //GUI追加時にreturn後の内容追加

        Map<Integer, Double> currentPulseRates = new HashMap<>(); //GUI実装時に追加

        // 脈拍 (R) の計算と体温チェック
        for (Map.Entry<Integer, Long> entry : portPacketCounts.entrySet()) {
            int port = entry.getKey();
            long count = entry.getValue();
            double rate = (double) count / elapsedSeconds; // 脈拍 (pkt/s)
            
            //System.out.println("[脈拍] Port " + port + ": " + String.format("%.2f", rate) + " pkt/s");
            currentPulseRates.put(port, rate);//out.printの代わりにGUIへデータを送る
            detector.checkAnomaly(port, rate);
    }

    double smugglingRate = (totalBytes > 0) ? (double) highPortBytes / totalBytes * 100.0 : 0.0 ;

    double currentMbps = (totalBytes * 8.0) / (elapsedSeconds * 1_000_000.0) ;

    portPacketCounts.clear();
    highPortBytes = 0;
    totalBytes = 0;
    lastLogTime = currentTime;
    return new VitalSignData( currentPulseRates, smugglingRate,currentMbps);}

    private int getDstPort(Packet packet) {
        if (packet.contains(TcpPacket.class)) {
            return packet.get(TcpPacket.class).getHeader().getDstPort().valueAsInt();
        } else if (packet.contains(UdpPacket.class)) {
            return packet.get(UdpPacket.class).getHeader().getDstPort().valueAsInt();
        }
        return 0;
    }
}