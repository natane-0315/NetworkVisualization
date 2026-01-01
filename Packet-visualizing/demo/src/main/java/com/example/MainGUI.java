package com.example;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

public class MainGUI extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception {
        System.out.println("--- MainGUI: start() メソッド実行開始 ---");
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/capture_layout.fxml"));
            Parent root = loader.load();

            CaptureController controller = loader.getController();

            primaryStage.setTitle("デジタル・バイタルサイン分析システム");
            
            primaryStage.setScene(new Scene(root, 800, 650));
            primaryStage.show();
            

            System.out.println("--- MainGUI: primaryStage.show() 実行完了 --- Thread=" + Thread.currentThread().getName());

            // アプリケーションが閉じられたときの処理
            primaryStage.setOnCloseRequest(e -> {
                if (controller != null) {
                    controller.stopCapture();
                }
                System.exit(0);
            });

        } catch (Throwable t) {
            System.out.println("--- MainGUI: start() 例外発生: " + t);
            t.printStackTrace();
            throw t;
        }
    }

    public static void main(String[] args) {
        // JavaFXアプリケーションを起動
        launch(args);
    }
}