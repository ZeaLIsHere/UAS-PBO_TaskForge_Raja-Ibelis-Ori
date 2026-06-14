package com.taskforge.ui;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

public class MainApp extends Application {

    /** Ukuran ikon yang tersedia di /images — OS memilih yang paling pas. */
    private static final String[] ICON_SIZES = {
            "/images/taskforge-icon-256.png",
            "/images/taskforge-icon-128.png",
            "/images/taskforge-icon-48.png"
    };

    @Override
    public void start(Stage primaryStage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/login.fxml"));
        Scene scene = new Scene(loader.load());
        scene.getStylesheets().add(getClass().getResource("/css/styles.css").toExternalForm());

        applyAppIcons(primaryStage);

        primaryStage.setTitle("TaskForge — Login");
        primaryStage.setScene(scene);
        primaryStage.setResizable(true);
        primaryStage.setMinWidth(480);
        primaryStage.setMinHeight(500);
        primaryStage.setMaximized(true);
        primaryStage.show();
    }

    /** Pasang ikon aplikasi pada title bar & taskbar. */
    private void applyAppIcons(Stage stage) {
        for (String path : ICON_SIZES) {
            var url = getClass().getResource(path);
            if (url != null) {
                stage.getIcons().add(new Image(url.toExternalForm()));
            }
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
