package com.taskforge.ui.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.taskforge.ui.model.ApiResponse;
import com.taskforge.ui.model.ProjectModel;
import com.taskforge.ui.model.ScoreModel;
import com.taskforge.ui.service.ApiClient;
import com.taskforge.ui.util.Dialogs;
import com.taskforge.ui.util.SceneNavigator;
import com.taskforge.ui.util.SidebarProfileBinder;
import com.taskforge.ui.session.SessionManager;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.util.List;

public class ReportScoreController {

    @FXML private Label projectTitleLabel;
    @FXML private Label statusLabel;
    @FXML private VBox scoresPane;
    @FXML private VBox reportPane;
    @FXML private Button generateReportButton;

    // Sidebar profil (mengikuti dashboard)
    @FXML private Label avatarInitials;
    @FXML private Label userNameLabel;
    @FXML private Label userNimLabel;
    @FXML private Label userRoleLabel;

    private ProjectModel currentProject;
    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    public void initWithProject(ProjectModel project) {
        this.currentProject = project;
        projectTitleLabel.setText(project.getTitle());
        SidebarProfileBinder.refresh(userNameLabel, userNimLabel, userRoleLabel, avatarInitials);

        boolean isKetua = SessionManager.getInstance().isKetua();
        generateReportButton.setVisible(isKetua);
        generateReportButton.setManaged(isKetua);

        loadScores();
    }

    private void loadScores() {
        scoresPane.getChildren().clear();
        statusLabel.setText("Memuat data kontribusi...");

        Task<List<ScoreModel>> fetchTask = new Task<>() {
            @Override
            protected List<ScoreModel> call() throws Exception {
                ApiResponse<Object> raw = ApiClient.get(
                        "/api/projects/" + currentProject.getId() + "/scores", Object.class);
                if (!raw.isSuccess()) throw new Exception(raw.getMessage());
                String json = MAPPER.writeValueAsString(raw.getData());
                return MAPPER.readValue(json, new TypeReference<>() {});
            }
        };

        fetchTask.setOnSucceeded(e -> {
            statusLabel.setText("");
            renderScores(fetchTask.getValue());
        });

        fetchTask.setOnFailed(e -> Platform.runLater(
                () -> statusLabel.setText("Gagal memuat skor: " + fetchTask.getException().getMessage())));

        Thread t = new Thread(fetchTask);
        t.setDaemon(true);
        t.start();
    }

    private void renderScores(List<ScoreModel> scores) {
        scoresPane.getChildren().clear();

        if (scores.isEmpty()) {
            Label empty = new Label("Belum ada task yang selesai — skor akan muncul setelah task di-mark Done.");
            empty.setStyle("-fx-text-fill: #6B7280; -fx-font-size: 13px; -fx-wrap-text: true;");
            scoresPane.getChildren().add(empty);
            return;
        }

        for (ScoreModel score : scores) {
            scoresPane.getChildren().add(buildScoreRow(score));
        }
    }

    private VBox buildScoreRow(ScoreModel score) {
        VBox row = new VBox(6);
        row.setPadding(new Insets(14, 18, 14, 18));
        row.setMaxWidth(Double.MAX_VALUE);

        String borderColor = switch (score.getScoreLevel() != null ? score.getScoreLevel() : "") {
            case "HIGH"   -> "#10B981";
            case "MEDIUM" -> "#F59E0B";
            default       -> "#EF4444";
        };
        row.setStyle("-fx-background-color: white; -fx-background-radius: 8px; " +
                "-fx-border-color: " + borderColor + "; -fx-border-width: 0 0 0 4px; " +
                "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.07), 4, 0, 0, 1);");

        // Name + score
        HBox header = new HBox();
        header.setAlignment(Pos.CENTER_LEFT);
        Label nameLbl = new Label(score.getUserName());
        nameLbl.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #1F2937;");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Label scoreLbl = new Label(String.format("%.1f poin  •  %.1f%%", score.getScore(), score.getPercentage()));
        scoreLbl.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: " + borderColor + ";");
        header.getChildren().addAll(nameLbl, spacer, scoreLbl);

        // Stats
        Label stats = new Label(score.getTasksCompleted() + " task selesai  •  " +
                score.getTasksOntime() + " on-time  •  " + score.getUserEmail());
        stats.setStyle("-fx-font-size: 11px; -fx-text-fill: #6B7280;");

        // Progress bar
        ProgressBar bar = new ProgressBar(score.getPercentage() / 100.0);
        bar.setMaxWidth(Double.MAX_VALUE);
        bar.getStyleClass().add(score.getBarStyleClass());

        row.getChildren().addAll(header, stats, bar);
        return row;
    }

    @FXML
    public void handleGenerateReport() {
        statusLabel.setText("Generating laporan...");

        Task<String> reportTask = new Task<>() {
            @Override
            protected String call() throws Exception {
                ApiResponse<Object> raw = ApiClient.get(
                        "/api/projects/" + currentProject.getId() + "/report", Object.class);
                return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(raw.getData());
            }
        };

        reportTask.setOnSucceeded(e -> Platform.runLater(() -> {
            statusLabel.setText("");
            showReportDialog(reportTask.getValue());
        }));

        reportTask.setOnFailed(e -> Platform.runLater(
                () -> statusLabel.setText("Gagal: " + reportTask.getException().getMessage())));

        Thread t = new Thread(reportTask);
        t.setDaemon(true);
        t.start();
    }

    private void showReportDialog(String json) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Laporan Proyek — " + currentProject.getTitle());
        dialog.setHeaderText("Laporan dapat di-screenshot untuk laporan ke dosen");
        dialog.setResizable(true);

        TextArea area = new TextArea(json);
        area.setEditable(false);
        area.setPrefSize(700, 500);
        area.setStyle("-fx-font-family: monospace; -fx-font-size: 12px;");

        dialog.getDialogPane().setContent(area);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        Dialogs.style(dialog);
        dialog.showAndWait();
    }

    @FXML
    public void handleRefresh() {
        loadScores();
    }

    @FXML
    public void handleBack() {
        try {
            Stage stage = (Stage) projectTitleLabel.getScene().getWindow();
            KanbanController ctrl = SceneNavigator.navigate(
                    stage, "/fxml/kanban.fxml", "TaskForge — " + currentProject.getTitle(), 1200, 700);
            ctrl.initWithProject(currentProject);
        } catch (Exception e) {
            statusLabel.setText("Gagal kembali: " + e.getMessage());
        }
    }

    @FXML
    public void handleFileHub() {
        try {
            Stage stage = (Stage) projectTitleLabel.getScene().getWindow();
            FileHubController ctrl = SceneNavigator.navigate(
                    stage, "/fxml/filehub.fxml", "TaskForge — File Hub", 1200, 700);
            ctrl.initWithProject(currentProject);
        } catch (Exception e) {
            statusLabel.setText("Gagal membuka File Hub: " + e.getMessage());
        }
    }

    // ─── Sidebar navigation (mengikuti dashboard) ────────────────────────────

    @FXML
    public void handleDashboard() { navigate("/fxml/dashboard.fxml", "TaskForge — Dashboard"); }

    @FXML
    public void handleProyek() { navigate("/fxml/proyek.fxml", "TaskForge — Proyek"); }

    @FXML
    public void handleNotifikasi() { navigate("/fxml/notifikasi.fxml", "TaskForge — Notifikasi"); }

    @FXML
    public void handleProfil() { navigate("/fxml/profil.fxml", "TaskForge — Profil"); }

    @FXML
    public void handleLogout() { SidebarProfileBinder.logout(userNameLabel); }

    private void navigate(String fxml, String title) {
        try {
            Stage stage = (Stage) projectTitleLabel.getScene().getWindow();
            SceneNavigator.navigate(stage, fxml, title, 1100, 700);
        } catch (Exception e) {
            statusLabel.setText("Gagal navigasi: " + e.getMessage());
        }
    }
}
