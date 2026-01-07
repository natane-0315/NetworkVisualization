package com.example;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

import org.pcap4j.core.Pcaps;
import org.pcap4j.core.PcapNetworkInterface;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.time.Instant;

public class CaptureController {

    @FXML private ComboBox<String> nifComboBox;
    @FXML private Button startButton;
    @FXML private Button stopButton;
    @FXML private TextArea logArea;
    // UI要素をFXMLで定義したIDと一致させます
    @FXML private Label entropyLabel;
    @FXML private Label pulseRateLabel;
    @FXML private Label smugglingRateLabel;
    
    
    private List<PcapNetworkInterface> allDevs;
    private PacketCapture captureTask;
    private Thread captureThread;
    private ScheduledExecutorService scheduler; 
     // 体の画像表示用（将来使用予定）
    // LogQueue（PacketCaptureとログモニターの連携に使用）
    private final BlockingQueue<String> logQueue = new ArrayBlockingQueue<>(100);

    // FXMLロード後に自動で呼ばれる初期化メソッド
    public void initialize() {
        System.out.println("--- CaptureController: initialize() 実行開始 ---");
        // UI は常に表示できるように、デバイス列挙は非同期で行う
        stopButton.setDisable(true);
        startLogMonitor(); // ログキューを監視するスレッドを開始

        
        // ネットワークデバイスの検索はブロッキングになる可能性があるため別スレッドで実行
        CompletableFuture.runAsync(() -> {
            try {
                List<PcapNetworkInterface> devs = Pcaps.findAllDevs();
                if (devs == null || devs.isEmpty()) {
                    Platform.runLater(() -> logArea.appendText("警告: ネットワークデバイスが見つかりません。Npcap/WinPcap が必要な場合があります。\n"));
                } else {
                    allDevs = devs;
                    Platform.runLater(() -> {
                        nifComboBox.getItems().clear();
                        for (PcapNetworkInterface nif : allDevs) {
                            nifComboBox.getItems().add(nif.getName() + " (" + nif.getDescription() + ")");
                        }
                        nifComboBox.getSelectionModel().select(0);
                    });
                }
            } catch (Throwable e) {
                // UIスレッドをブロックせず、ログに例外を表示する
                e.printStackTrace();
                String msg = "エラー: ネットワークデバイスの取得に失敗しました: " + e.getMessage();
                Platform.runLater(() -> logArea.appendText(msg + "\n"));
            }
        });

        System.out.println("--- CaptureController: initialize() 実行完了 ---");
    }

    // ログキューの内容をTextAreaに反映させるスレッド
    private void startLogMonitor() {
        Thread logMonitor = new Thread(() -> {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    String log = logQueue.take();
                    // GUIの更新はJavaFXのメインスレッド（Platform.runLater）で行う
                    Platform.runLater(() -> logArea.appendText(log + "\n"));
                }
            } catch (InterruptedException e) {
                // スレッド中断
            }
        });
        logMonitor.setDaemon(true);
        logMonitor.start();
    }

    @FXML
    private void startCapture() {
        if (nifComboBox.getSelectionModel().isEmpty() || allDevs.isEmpty()) {
            logArea.appendText("エラー: デバイスを選択してください。\n");
            return;
        }

        int selectedIndex = nifComboBox.getSelectionModel().getSelectedIndex();
        PcapNetworkInterface selectedNif = allDevs.get(selectedIndex);

        // ログキューを渡してPacketCaptureインスタンスを生成
        captureTask = new PacketCapture(selectedNif, logQueue);
        captureThread = new Thread(captureTask);
        captureThread.start();
        
        logArea.appendText("✅ キャプチャを開始しました: " + selectedNif.getName() + "\n");

        startAnalysisScheduler(); // 定期分析タイマーを開始
        
        startButton.setDisable(true);
        stopButton.setDisable(false);
    }
    
    @FXML
    public void stopCapture() {
        if (captureTask != null) {
            captureTask.stopCapture();
            captureThread.interrupt();
            scheduler.shutdownNow(); // タイマーを停止
            logArea.appendText("キャプチャと分析を停止しました。\n");
            startButton.setDisable(false);
            stopButton.setDisable(true);
        }
    }

    // 60秒ごとに分析を実行し、GUIを更新するタイマー処理
    private void startAnalysisScheduler() {
        // PacketCaptureのGetterメソッド（getAggregator, getFairnessCalculator）が
        // 実装されている前提でコードを記述します。
        
        scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(() -> {
            try {
                // 1. 脈拍・密輸率の計算と取得
                VitalSignData pulseData = captureTask.getAggregator().calculateAndReset(Instant.now());
                
                // 2. 集中度の計算と取得
                double entropy = captureTask.getFairnessCalculator().calculateFairness();
                
                // TODO: 呼吸分析の結果取得もここに追加

                // GUIの更新は必ず Platform.runLater で行う
                Platform.runLater(() -> updateGUI(pulseData, entropy));

            } catch (Exception e) {
                // スケジューラー内でのエラーはログに出力
                Platform.runLater(() -> logArea.appendText("タイマー実行エラー: " + e.getMessage() + "\n"));
            }
        }, 0, 60, TimeUnit.SECONDS); // 60秒ごと
    }

    // GUIの各ラベルを更新するメソッド
    private void updateGUI(VitalSignData pulseData, double entropy) {
        // 集中度の表示
        entropyLabel.setText(String.format("%.2f", entropy));
        
        // 密輸率の表示
        smugglingRateLabel.setText(String.format("%.2f%%", pulseData.smugglingRate));
        
        // 脈拍の表示 (例: 最もパケット数が多いポートのレートを表示)
        pulseData.pulseRates.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .ifPresentOrElse(
                entry -> pulseRateLabel.setText(String.format("Port %d: %.2f pkt/s", entry.getKey(), entry.getValue())),
                () -> pulseRateLabel.setText("データなし")
            );
            
    }
    
    // PacketCapture.javaにこれらのメソッドがまだない場合、エラーを避けるために追加が必要
    /*
    public TimeAndPortAggregator getAggregator() { return aggregator; }
    public FairnessCalculator getFairnessCalculator() { return fairnessCalculator; }
    */
}