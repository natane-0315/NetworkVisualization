package com.example;

import org.pcap4j.core.*;
import org.pcap4j.packet.*;
import org.pcap4j.packet.namednumber.TcpPort;
import org.pcap4j.packet.TcpPacket.TcpHeader; // 修正後のcheckRetransmissionで必要
import java.net.Inet4Address;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.time.Instant; // 追加

public class PacketCapture implements Runnable {

    private boolean openorclose = false ;
    private final PcapNetworkInterface nif;
    private final BlockingQueue<String> logQueue;
    private volatile boolean running = true;
    private PcapHandle handle;

    // TCP再送を検出するためのデータ構造 (TcpPortをIntegerに変更)
    private final Map<Integer, Map<Integer, Integer>> retransmissionTracker = new HashMap<>();

    // ✅ 新規追加: 分析クラスのインスタンス
    private final AnomalyDetector detector = new AnomalyDetector(); 
    private final TimeAndPortAggregator aggregator = new TimeAndPortAggregator(detector); 
    private final FairnessCalculator fairnessCalculator = new FairnessCalculator();
    private final RhythmAnalyzer rhythmAnalyzer = new RhythmAnalyzer();

    // ✅ 新規追加: Main.javaからアクセスするためのGetterメソッド
    public TimeAndPortAggregator getAggregator() { return aggregator; }
    public FairnessCalculator getFairnessCalculator() { return fairnessCalculator; }
    // (他のGetterも必要に応じて追加可能)


    public PacketCapture(PcapNetworkInterface nif, BlockingQueue<String> logQueue) {
        this.nif = nif;
        this.logQueue = logQueue;
    }

    public void stopCapture() {
        this.running = false;
        if (handle != null && handle.isOpen()) {
            try {
                handle.close();
            } catch (Exception e) {
                // クローズ時のエラーは無視
            }
        }
    }

    // ✅ 新規追加: logError メソッド (以前のエラーを解消)
    private void logError(String message) {
        try {
            logQueue.put(message);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void run() {
        try {
            handle = nif.openLive(65536, PcapNetworkInterface.PromiscuousMode.PROMISCUOUS, 10);
            handle.setFilter("ip", BpfProgram.BpfCompileMode.OPTIMIZE); // TCPだけでなくIPパケット全体を対象に

            logQueue.put("✅ キャプチャを開始しました: " + nif.getName());

            while (running) {
                Packet packet = handle.getNextPacket();
                if (packet != null) {
                    processPacket(packet);
                }
            }
        } catch (PcapNativeException e) {
            logError("致命的エラー: " + e.getMessage() + " (管理者権限で実行しているか確認してください)");
        } catch (NotOpenException e) {
            logError("キャプチャが停止されました。");
        } catch (Exception e) {
            logError("予期せぬエラー: " + e.getMessage());
        } finally {
            if (handle != null && handle.isOpen()) {
                handle.close();
            }
        }
    }

    private void processPacket(Packet packet) throws InterruptedException {
        IpV4Packet ipPacket = packet.get(IpV4Packet.class);
        
        if (ipPacket == null) return; 

        Inet4Address srcAddr = ipPacket.getHeader().getSrcAddr();
        
        // --- ✅ 新規追加: 分析に必要な基本データ ---
        Instant currentTime = Instant.now(); 
        int byteSize = packet.length();
        String anonSrcIp = String.valueOf(srcAddr.hashCode()); // 簡易匿名化

        // --- ✅ 新規追加: 分析ロジックの呼び出し ---
        aggregator.aggregate(currentTime, packet); 
        fairnessCalculator.aggregate(anonSrcIp, byteSize);
        rhythmAnalyzer.analyzeRhythm(currentTime);
        // ------------------------------------
        
        // 既存のログ出力
        String logMessage = ">> パケット: " + srcAddr.getHostAddress() + " -> " + ipPacket.getHeader().getDstAddr().getHostAddress();
        if(openorclose == true){
            logQueue.put(logMessage);
        }

        TcpPacket tcpPacket = packet.get(TcpPacket.class);
        if (tcpPacket != null) { 
            TcpHeader tcpHeader = tcpPacket.getHeader();

            // RSTフラグのチェック
            if (tcpHeader.getRst()) {
                logQueue.put("🚨 エラー: TCP RSTフラグ検出！(" + srcAddr.getHostAddress() + "が接続をリセット)");
            }

            // TCP再送のチェック
            checkRetransmission(tcpHeader, srcAddr.getHostAddress());
        }
    }

    // ✅ 修正後の checkRetransmission メソッド
    private synchronized void checkRetransmission(TcpHeader tcpHeader, String srcIp) throws InterruptedException {
        // TcpPortではなくIntegerを使用
        int srcPort = tcpHeader.getSrcPort().valueAsInt(); 
        int sequenceNumber = tcpHeader.getSequenceNumber();

        // Integerポートをキーにマップを取得
        retransmissionTracker.computeIfAbsent(srcPort, k -> new HashMap<>());

        Map<Integer, Integer> sequenceMap = retransmissionTracker.get(srcPort);
        int count = sequenceMap.getOrDefault(sequenceNumber, 0);

        if (count > 0) {
            logQueue.put("⚠️ 警告: TCP再送検出！ (Seq: " + sequenceNumber + " from " + srcIp + ")");
        }

        sequenceMap.put(sequenceNumber, count + 1);
    }
}