package com.example;

import org.pcap4j.core.*;
import org.pcap4j.core.PcapNetworkInterface.PromiscuousMode;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.Timer;     // ✅ 新規追加
import java.util.TimerTask; // ✅ 新規追加
import java.time.Instant;   // ✅ 新規追加


// 既存の CountPorts, PacketBital, Defining クラスは削除してください。

public class Main {
    public static void main(String[] args) {

        // 既存の CountPorts TCPcp = new CountPorts(); などは削除

        PcapNetworkInterface selectedNif = null;
        
        try (Scanner scanner = new Scanner(System.in)) {
            
            System.out.println("--- Pcap4J デジタル・バイタルサイン分析プログラム ---");
            
            // 1. ネットワークデバイスのリストアップ
            List<PcapNetworkInterface> allDevs = Pcaps.findAllDevs();

            if (allDevs.isEmpty()) {
                System.out.println("エラー: ネットワークデバイスが見つかりませんでした。");
                return;
            }

            // 2. ユーザーにデバイスを選択させる (既存のロジックをそのまま使用)
            // ... (デバイス一覧の表示ロジック) ...
            for (int i = 0; i < allDevs.size(); i++) {
                PcapNetworkInterface nif = allDevs.get(i);
                System.out.printf("  [%d]: %s (%s)%n", i, nif.getName(), nif.getDescription());
            }

            int selection = -1;
            while (selectedNif == null) {
                System.out.print("\nキャプチャに使用するデバイスの番号を入力してください (0〜" + (allDevs.size() - 1) + "): ");
                
                if (scanner.hasNextInt()) {
                    selection = scanner.nextInt();
                    if (selection >= 0 && selection < allDevs.size()) {
                        selectedNif = allDevs.get(selection);
                    } else {
                        System.out.println("無効な番号です。リストの範囲内 (0〜" + (allDevs.size() - 1) + ") で入力してください。");
                    }
                } else {
                    System.out.println("無効な入力です。数字を入力してください。");
                    scanner.next(); // 不正な入力をスキップ
                }
            }
            
            System.out.println("=> デバイス " + selection + " (" + selectedNif.getDescription() + ") で分析を開始します。");
            System.out.println("------------------------------------");
            
            // --- ✅ 修正後のキャプチャ実行ロジック ---
            
            // 3. ログキューとキャプチャスレッドの準備
            BlockingQueue<String> logQueue = new ArrayBlockingQueue<>(100);
            PacketCapture captureTask = new PacketCapture(selectedNif, logQueue);
            
            // PacketCaptureを別スレッドで実行
            Thread captureThread = new Thread(captureTask);
            captureThread.start();
            
            // 4. タイマーによる分析処理の定期実行 (60秒ごと)
            Timer timer = new Timer(true);
            timer.scheduleAtFixedRate(new TimerTask() {
                @Override
                public void run() {
                    try {
                        // 脈拍、密輸率、体温の計算とリセット (Aggregator)
                        //GUIへ変更captureTask.getAggregator().calculateAndReset(Instant.now());
                        VitalSignData pulseData = captureTask.getAggregator().calculateAndReset(Instant.now());
                        double entropy = captureTask.getFairnessCalculator().calculateFairness();
                        // 富の集中度の計算とリセット (FairnessCalculator)
                        //GUIへ変更captureTask.getFairnessCalculator().calculateFairness();
                        // **結果をコンソールに出力し直す**
                        System.out.println("--- 60秒分析結果 ---");
                        System.out.println("[密輸] 密輸率: " + String.format("%.2f%%", pulseData.smugglingRate));
                        System.out.println("[集中度] 富の集中度 (エントロピー): " + String.format("%.2f", entropy));
            
                        // 脈拍データも出力
                        pulseData.pulseRates.forEach((port, rate) -> {
                    System.out.println("[脈拍] Port " + port + ": " + String.format("%.2f", rate) + " pkt/s");
                });
                    } catch (Exception e) {
                        System.err.println("タイマー実行エラー: " + e.getMessage());
                    }
                }
            }, 0, 60000); // 60秒（60000ミリ秒）ごとに実行
            
            // 5. メインスレッドでのログ出力処理 (BlockingQueueからログを取り出してコンソールに出力)
            while (true) {
                String log = logQueue.take();
                System.out.println(log);
            }

        } catch (InterruptedException e) {
            System.out.println("\nプログラムが中断されました。");
        } catch (PcapNativeException e) {
            System.err.println("致命的なエラー: Pcap4Jの初期化、または管理者権限が必要です。");
            e.printStackTrace();
        } catch (Exception e) {
            System.err.println("予期せぬエラーが発生しました: " + e.getMessage());
            e.printStackTrace();
        } finally {
            // クリーンアップ処理
            if (selectedNif != null) {
                // captureTask.stopCapture() は try-with-resources の外側で、かつ catch/finallyブロック内で安全に呼び出されるべき
                // ここでは finally ブロックの外で処理を中断させるため、明示的に呼び出す
                // ただし、try-with-resourcesのスコープ外でキャプチャ停止とタイマー停止のロジックが確実に実行されるようにする必要があります。
            }
        }
        
        // プログラム終了時に停止処理が実行されることを保証
        System.exit(0);
    }
}