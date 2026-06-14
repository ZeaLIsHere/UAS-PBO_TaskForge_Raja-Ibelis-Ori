package com.taskforge.ui.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.taskforge.ui.model.ApiResponse;
import com.taskforge.ui.model.FileModel;
import com.taskforge.ui.model.ProjectModel;
import com.taskforge.ui.model.TaskModel;
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
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.time.Duration;
import java.util.List;
import java.util.Map;

public class FileHubController {

    @FXML private Label projectTitleLabel;
    @FXML private Label statusLabel;
    @FXML private ComboBox<TaskModel> taskSelector;
    @FXML private VBox fileListPane;
    @FXML private Button uploadButton;
    @FXML private Button addLinkButton;

    // Sidebar profil (mengikuti dashboard)
    @FXML private Label avatarInitials;
    @FXML private Label userNameLabel;
    @FXML private Label userNimLabel;
    @FXML private Label userRoleLabel;

    private ProjectModel currentProject;
    private List<TaskModel> tasks;
    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    public void initWithProject(ProjectModel project) {
        this.currentProject = project;
        projectTitleLabel.setText(project.getTitle());
        SidebarProfileBinder.refresh(userNameLabel, userNimLabel, userRoleLabel, avatarInitials);
        loadTasks();
    }

    private void loadTasks() {
        Task<List<TaskModel>> fetchTask = new Task<>() {
            @Override
            protected List<TaskModel> call() throws Exception {
                ApiResponse<Object> raw = ApiClient.get(
                        "/api/projects/" + currentProject.getId() + "/tasks", Object.class);
                String json = MAPPER.writeValueAsString(raw.getData());
                return MAPPER.readValue(json, new TypeReference<>() {});
            }
        };

        fetchTask.setOnSucceeded(e -> {
            tasks = fetchTask.getValue();
            taskSelector.getItems().setAll(tasks);
            taskSelector.setConverter(new javafx.util.StringConverter<>() {
                @Override public String toString(TaskModel t) { return t == null ? "" : t.getTitle(); }
                @Override public TaskModel fromString(String s) { return null; }
            });
            if (!tasks.isEmpty()) {
                taskSelector.setValue(tasks.get(0));
                loadFilesForTask(tasks.get(0));
            }
        });

        fetchTask.setOnFailed(e -> statusLabel.setText("Gagal memuat tasks"));

        Thread t = new Thread(fetchTask);
        t.setDaemon(true);
        t.start();
    }

    @FXML
    public void handleTaskSelected() {
        TaskModel selected = taskSelector.getValue();
        if (selected != null) loadFilesForTask(selected);
    }

    private void loadFilesForTask(TaskModel task) {
        fileListPane.getChildren().clear();
        statusLabel.setText("Memuat file...");

        Task<List<FileModel>> fetchTask = new Task<>() {
            @Override
            protected List<FileModel> call() throws Exception {
                ApiResponse<Object> raw = ApiClient.get("/api/tasks/" + task.getId() + "/files", Object.class);
                String json = MAPPER.writeValueAsString(raw.getData());
                return MAPPER.readValue(json, new TypeReference<>() {});
            }
        };

        fetchTask.setOnSucceeded(e -> {
            statusLabel.setText("");
            List<FileModel> files = fetchTask.getValue();
            if (files.isEmpty()) {
                Label empty = new Label("Belum ada file di task ini.");
                empty.setStyle("-fx-text-fill: #9CA3AF; -fx-font-size: 13px;");
                fileListPane.getChildren().add(empty);
            } else {
                files.forEach(f -> fileListPane.getChildren().add(buildFileRow(f)));
            }
        });

        fetchTask.setOnFailed(e -> Platform.runLater(
                () -> statusLabel.setText("Gagal memuat file: " + fetchTask.getException().getMessage())));

        Thread t = new Thread(fetchTask);
        t.setDaemon(true);
        t.start();
    }

    private HBox buildFileRow(FileModel file) {
        HBox row = new HBox(12);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(10, 14, 10, 14));
        row.setMaxWidth(Double.MAX_VALUE);
        row.setStyle("-fx-background-color: white; -fx-background-radius: 8px; " +
                "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.07), 4, 0, 0, 1);");

        // Icon
        Label icon = new Label(file.getTypeIcon());
        icon.setStyle("-fx-font-size: 22px;");

        // Info
        VBox info = new VBox(3);
        Label nameLbl = new Label(file.getName());
        nameLbl.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #1F2937;");

        String meta = file.isLink()
                ? "🔗 Link eksternal  •  " + file.getUploadedAtLabel() + "  •  oleh " + file.getUploaderName()
                : file.getFileSizeLabel() + "  •  " + file.getUploadedAtLabel() + "  •  oleh " + file.getUploaderName();
        Label metaLbl = new Label(meta);
        metaLbl.setStyle("-fx-font-size: 11px; -fx-text-fill: #6B7280;");

        if (file.getDescription() != null && !file.getDescription().isBlank()) {
            Label descLbl = new Label(file.getDescription());
            descLbl.setStyle("-fx-font-size: 11px; -fx-text-fill: #9CA3AF; -fx-font-style: italic;");
            info.getChildren().addAll(nameLbl, metaLbl, descLbl);
        } else {
            info.getChildren().addAll(nameLbl, metaLbl);
        }
        HBox.setHgrow(info, Priority.ALWAYS);

        // Action buttons
        HBox actions = new HBox(6);
        actions.setAlignment(Pos.CENTER_RIGHT);

        if (file.isLink()) {
            Button openBtn = new Button("🌐 Buka");
            openBtn.setStyle("-fx-font-size: 11px; -fx-padding: 4 10 4 10; -fx-background-color: #EFF6FF; " +
                    "-fx-text-fill: #2563EB; -fx-background-radius: 5px; -fx-cursor: hand;");
            openBtn.setOnAction(e -> openUrl(file.getAccessUrl()));
            actions.getChildren().add(openBtn);
        } else {
            Button dlBtn = new Button("⬇ Download");
            dlBtn.setStyle("-fx-font-size: 11px; -fx-padding: 4 10 4 10; -fx-background-color: #ECFDF5; " +
                    "-fx-text-fill: #059669; -fx-background-radius: 5px; -fx-cursor: hand;");
            dlBtn.setOnAction(e -> downloadFile(file));
            actions.getChildren().add(dlBtn);
        }

        // Delete button — visible to uploader or KETUA
        boolean canDelete = SessionManager.getInstance().isKetua()
                || (file.getUploadedBy() != null
                && file.getUploadedBy().getId().equals(SessionManager.getInstance().getCurrentUser().getId()));
        if (canDelete) {
            Button delBtn = new Button("✕");
            delBtn.setStyle("-fx-font-size: 11px; -fx-padding: 4 8 4 8; -fx-background-color: #FEF2F2; " +
                    "-fx-text-fill: #DC2626; -fx-background-radius: 5px; -fx-cursor: hand;");
            delBtn.setOnAction(e -> deleteFile(file));
            actions.getChildren().add(delBtn);
        }

        row.getChildren().addAll(icon, info, actions);
        return row;
    }

    private void openUrl(String url) {
        try {
            java.awt.Desktop.getDesktop().browse(new URI(url));
        } catch (Exception e) {
            statusLabel.setText("Tidak bisa membuka URL: " + url);
        }
    }

    private void downloadFile(FileModel file) {
        FileChooser chooser = new FileChooser();
        chooser.setInitialFileName(file.getName());
        File dest = chooser.showSaveDialog(fileListPane.getScene().getWindow());
        if (dest == null) return;

        Task<Void> dlTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:8080" + file.getAccessUrl()))
                        .header("Authorization", "Bearer " + SessionManager.getInstance().getToken())
                        .timeout(Duration.ofSeconds(30))
                        .GET().build();
                HttpResponse<byte[]> resp = HttpClient.newHttpClient()
                        .send(req, HttpResponse.BodyHandlers.ofByteArray());
                Files.write(dest.toPath(), resp.body());
                return null;
            }
        };

        dlTask.setOnSucceeded(e -> Platform.runLater(
                () -> statusLabel.setText("File berhasil didownload ke " + dest.getName())));
        dlTask.setOnFailed(e -> Platform.runLater(
                () -> statusLabel.setText("Gagal download: " + dlTask.getException().getMessage())));

        Thread t = new Thread(dlTask);
        t.setDaemon(true);
        t.start();
    }

    private void deleteFile(FileModel file) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Hapus '" + file.getName() + "'? Tindakan ini tidak bisa dibatalkan.",
                ButtonType.YES, ButtonType.NO);
        confirm.setTitle("TaskForge");
        confirm.setHeaderText("Konfirmasi Hapus File");
        Dialogs.style(confirm.getDialogPane());
        confirm.showAndWait().ifPresent(result -> {
            if (result != ButtonType.YES) return;
            Task<Void> delTask = new Task<>() {
                @Override
                protected Void call() throws Exception {
                    ApiClient.delete("/api/files/" + file.getId(), Object.class);
                    return null;
                }
            };
            delTask.setOnSucceeded(e -> Platform.runLater(() -> {
                statusLabel.setText("File dihapus.");
                handleTaskSelected();
            }));
            delTask.setOnFailed(e -> Platform.runLater(
                    () -> statusLabel.setText("Gagal hapus: " + delTask.getException().getMessage())));
            Thread t = new Thread(delTask);
            t.setDaemon(true);
            t.start();
        });
    }

    @FXML
    public void handleUpload() {
        TaskModel task = taskSelector.getValue();
        if (task == null) { statusLabel.setText("Pilih task terlebih dahulu"); return; }

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Pilih File");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                "Semua file yang didukung",
                "*.pdf", "*.doc", "*.docx", "*.ppt", "*.pptx",
                "*.jpg", "*.jpeg", "*.png", "*.gif", "*.txt",
                "*.java", "*.py", "*.js", "*.ts", "*.html", "*.css",
                "*.zip", "*.rar"));
        File file = chooser.showOpenDialog(fileListPane.getScene().getWindow());
        if (file == null) return;

        if (file.length() > 10_000_000) {
            statusLabel.setText("File terlalu besar (maks 10 MB)");
            return;
        }

        statusLabel.setText("Mengupload " + file.getName() + "...");

        Task<Void> uploadTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                String boundary = "----JavaFormBoundary" + System.currentTimeMillis();
                byte[] fileBytes = Files.readAllBytes(file.toPath());
                String header = "--" + boundary + "\r\n"
                        + "Content-Disposition: form-data; name=\"file\"; filename=\"" + file.getName() + "\"\r\n"
                        + "Content-Type: application/octet-stream\r\n\r\n";
                String footer = "\r\n--" + boundary + "--\r\n";

                byte[] headerBytes = header.getBytes();
                byte[] footerBytes = footer.getBytes();
                byte[] body = new byte[headerBytes.length + fileBytes.length + footerBytes.length];
                System.arraycopy(headerBytes, 0, body, 0, headerBytes.length);
                System.arraycopy(fileBytes, 0, body, headerBytes.length, fileBytes.length);
                System.arraycopy(footerBytes, 0, body, headerBytes.length + fileBytes.length, footerBytes.length);

                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:8080/api/tasks/" + task.getId() + "/files/upload"))
                        .header("Authorization", "Bearer " + SessionManager.getInstance().getToken())
                        .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                        .timeout(Duration.ofSeconds(60))
                        .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                        .build();

                HttpClient.newHttpClient().send(req, HttpResponse.BodyHandlers.ofString());
                return null;
            }
        };

        uploadTask.setOnSucceeded(e -> Platform.runLater(() -> {
            statusLabel.setText("File berhasil diupload.");
            loadFilesForTask(task);
        }));
        uploadTask.setOnFailed(e -> Platform.runLater(
                () -> statusLabel.setText("Gagal upload: " + uploadTask.getException().getMessage())));

        Thread t = new Thread(uploadTask);
        t.setDaemon(true);
        t.start();
    }

    @FXML
    public void handleAddLink() {
        TaskModel task = taskSelector.getValue();
        if (task == null) { statusLabel.setText("Pilih task terlebih dahulu"); return; }

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("TaskForge");
        dialog.setHeaderText("Tambah Link Eksternal");
        TextField nameField = new TextField();
        nameField.setPromptText("Nama link (contoh: Google Drive Bab 2)");
        TextField urlField = new TextField();
        urlField.setPromptText("https://...");
        TextField descField = new TextField();
        descField.setPromptText("Deskripsi (opsional)");

        GridPane grid = new GridPane();
        grid.setHgap(10); grid.setVgap(10); grid.setPadding(new Insets(10));
        grid.addRow(0, new Label("Nama:"), nameField);
        grid.addRow(1, new Label("URL:"), urlField);
        grid.addRow(2, new Label("Deskripsi:"), descField);

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        Dialogs.style(dialog);

        dialog.showAndWait().ifPresent(result -> {
            if (result != ButtonType.OK || nameField.getText().isBlank() || urlField.getText().isBlank()) return;

            Task<Void> linkTask = new Task<>() {
                @Override
                protected Void call() throws Exception {
                    Map<String, String> body = Map.of(
                            "name", nameField.getText().trim(),
                            "url", urlField.getText().trim(),
                            "description", descField.getText().trim());
                    ApiClient.post("/api/tasks/" + task.getId() + "/files/link", body, Object.class);
                    return null;
                }
            };
            linkTask.setOnSucceeded(e -> Platform.runLater(() -> {
                statusLabel.setText("Link berhasil ditambahkan.");
                loadFilesForTask(task);
            }));
            linkTask.setOnFailed(e -> Platform.runLater(
                    () -> statusLabel.setText("Gagal: " + linkTask.getException().getMessage())));
            Thread t = new Thread(linkTask);
            t.setDaemon(true);
            t.start();
        });
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
    public void handleReport() {
        try {
            Stage stage = (Stage) projectTitleLabel.getScene().getWindow();
            ReportScoreController ctrl = SceneNavigator.navigate(
                    stage, "/fxml/report.fxml", "TaskForge — Kontribusi & Laporan", 1000, 680);
            ctrl.initWithProject(currentProject);
        } catch (Exception e) {
            statusLabel.setText("Gagal membuka Laporan: " + e.getMessage());
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
