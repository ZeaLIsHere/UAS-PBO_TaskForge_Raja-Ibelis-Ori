package com.taskforge.ui.util;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public final class SceneNavigator {

    private static final String STYLESHEET = "/css/styles.css";

    private SceneNavigator() {}

    public static <T> T navigate(Stage stage, String fxmlPath, String title,
                                 double defaultWidth, double defaultHeight) throws Exception {
        return navigate(stage, fxmlPath, title, defaultWidth, defaultHeight, true);
    }

    // Parameter ukuran & preserveWindowState dipertahankan demi kompatibilitas
    // pemanggil lama. Semua halaman tampil maximized (full layar).
    public static <T> T navigate(Stage stage, String fxmlPath, String title,
                                 double defaultWidth, double defaultHeight,
                                 boolean preserveWindowState) throws Exception {
        FXMLLoader loader = new FXMLLoader(SceneNavigator.class.getResource(fxmlPath));
        Parent root = loader.load();

        // Tukar root pada scene yang sudah ada (bukan bikin Scene baru) agar
        // jendela tidak pernah resize — full layar tetap, tanpa kedip/delay.
        Scene scene = stage.getScene();
        if (scene == null) {
            scene = new Scene(root);
            applyStylesheet(scene);
            stage.setScene(scene);
        } else {
            applyStylesheet(scene);
            scene.setRoot(root);
        }

        stage.setTitle(title);
        stage.setResizable(true);
        if (!stage.isMaximized()) {
            stage.setMaximized(true);
        }

        return loader.getController();
    }

    public static void applyStylesheet(Scene scene) {
        var css = SceneNavigator.class.getResource(STYLESHEET);
        if (css != null && !scene.getStylesheets().contains(css.toExternalForm())) {
            scene.getStylesheets().add(css.toExternalForm());
        }
    }
}
