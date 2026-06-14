package com.taskforge.ui.util;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.Stage;

import java.util.Optional;

/**
 * Pembuat dialog/alert dengan tampilan konsisten bertema TaskForge.
 * Semua notifikasi aplikasi sebaiknya dibuat lewat kelas ini agar
 * stylesheet & ikon judul ikut terpasang otomatis.
 */
public final class Dialogs {

    private static final String STYLESHEET = "/css/styles.css";
    private static final String ICON = "/images/taskforge-icon-48.png";

    private Dialogs() {}

    // ── Factory notifikasi ───────────────────────────────────────────────────

    public static void info(String title, String header, String content) {
        show(Alert.AlertType.INFORMATION, title, header, content);
    }

    public static void error(String title, String header, String content) {
        show(Alert.AlertType.ERROR, title, header, content);
    }

    public static void warning(String title, String header, String content) {
        show(Alert.AlertType.WARNING, title, header, content);
    }

    /** Dialog konfirmasi; mengembalikan true jika pengguna menekan OK. */
    public static boolean confirm(String title, String header, String content) {
        Alert alert = buildAlert(Alert.AlertType.CONFIRMATION, title, header, content);
        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == ButtonType.OK;
    }

    private static void show(Alert.AlertType type, String title, String header, String content) {
        buildAlert(type, title, header, content).showAndWait();
    }

    private static Alert buildAlert(Alert.AlertType type, String title, String header, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);
        style(alert.getDialogPane());
        return alert;
    }

    // ── Styling dialog kustom (Dialog<ButtonType>) ───────────────────────────

    /** Pasang stylesheet + ikon jendela pada dialog kustom apa pun. */
    public static void style(Dialog<?> dialog) {
        style(dialog.getDialogPane());
    }

    public static void style(DialogPane pane) {
        var css = Dialogs.class.getResource(STYLESHEET);
        if (css != null && !pane.getStylesheets().contains(css.toExternalForm())) {
            pane.getStylesheets().add(css.toExternalForm());
        }
        if (!pane.getStyleClass().contains("taskforge-dialog")) {
            pane.getStyleClass().add("taskforge-dialog");
        }
        // Pasang ikon aplikasi di title bar dialog
        var window = pane.getScene() != null ? pane.getScene().getWindow() : null;
        if (window instanceof Stage stage) {
            var icon = Dialogs.class.getResource(ICON);
            if (icon != null && stage.getIcons().isEmpty()) {
                stage.getIcons().add(new Image(icon.toExternalForm()));
            }
        }
    }

    /** Ikon kecil opsional untuk header dialog kustom. */
    public static ImageView appIcon(double size) {
        var url = Dialogs.class.getResource(ICON);
        if (url == null) return null;
        ImageView iv = new ImageView(new Image(url.toExternalForm()));
        iv.setFitWidth(size);
        iv.setFitHeight(size);
        iv.setPreserveRatio(true);
        iv.setSmooth(true);
        return iv;
    }
}
