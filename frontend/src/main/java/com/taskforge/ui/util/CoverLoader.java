package com.taskforge.ui.util;

import com.taskforge.ui.service.ApiClient;
import javafx.concurrent.Task;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Rectangle;

import java.io.ByteArrayInputStream;

/**
 * Memuat foto sampul proyek dari backend secara asynchronous dan
 * menyajikannya sebagai node bersudut membulat untuk kartu/detail proyek.
 */
public final class CoverLoader {

    private CoverLoader() {}

    /** Node sampul berukuran tetap, sudut membulat, gambar dimuat di latar. */
    public static StackPane coverNode(long projectId, double width, double height, double arc) {
        ImageView iv = new ImageView();
        iv.setFitWidth(width);
        iv.setFitHeight(height);
        iv.setPreserveRatio(false);
        iv.setSmooth(true);

        StackPane pane = new StackPane(iv);
        pane.setPrefSize(width, height);
        pane.setMinSize(width, height);
        pane.setMaxSize(width, height);
        pane.setStyle("-fx-background-color: #EEF2FF;");

        Rectangle clip = new Rectangle(width, height);
        clip.setArcWidth(arc);
        clip.setArcHeight(arc);
        pane.setClip(clip);

        load(projectId, iv);
        return pane;
    }

    /** Muat gambar sampul ke ImageView yang sudah ada. */
    public static void load(long projectId, ImageView target) {
        Task<Image> task = new Task<>() {
            @Override
            protected Image call() throws Exception {
                byte[] bytes = ApiClient.getBytes("/api/projects/" + projectId + "/cover");
                return new Image(new ByteArrayInputStream(bytes));
            }
        };
        task.setOnSucceeded(e -> {
            Image img = task.getValue();
            if (img != null && !img.isError()) target.setImage(img);
        });
        Thread t = new Thread(task);
        t.setDaemon(true);
        t.start();
    }
}
