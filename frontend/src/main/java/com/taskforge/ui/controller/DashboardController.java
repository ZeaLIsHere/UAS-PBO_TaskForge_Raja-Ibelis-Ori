package com.taskforge.ui.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.taskforge.ui.model.ApiResponse;
import com.taskforge.ui.model.ProjectModel;
import com.taskforge.ui.model.UserModel;
import com.taskforge.ui.service.ApiClient;
import com.taskforge.ui.session.SessionManager;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.shape.Circle;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import java.io.File;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

public class DashboardController {

    @FXML private Label userNameLabel;
    @FXML private Label userNimLabel;
    @FXML private Label userRoleLabel;
    @FXML private Button newProjectButton;
    @FXML private FlowPane projectsPane;
    @FXML private ProgressIndicator loadingIndicator;
    @FXML private Label statusLabel;
    @FXML private Label statTotalProyek;
    @FXML private Label statOverdue;
    @FXML private Label statSelesai;
    @FXML private TextField searchField;
    @FXML private Circle avatarCircle;
    @FXML private Label avatarInitials;
    @FXML private ImageView profilePhoto;

    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd MMM yyyy");

    private static final String[] ACCENT_COLORS = {
        "#4F46E5", "#10B981", "#F59E0B", "#EF4444", "#8B5CF6"
    };
    private static final String[] ACCENT_LIGHT = {
        "#EEF2FF", "#ECFDF5", "#FFFBEB", "#FEF2F2", "#F5F3FF"
    };

    private List<ProjectModel> allProjects = List.of();

    @FXML
    public void initialize() {
        loadingIndicator.setVisible(false);
        statusLabel.setText("");
        refreshSidebarProfile();

        boolean isKetua = SessionManager.getInstance().isKetua();
        newProjectButton.setVisible(isKetua);
        newProjectButton.setManaged(isKetua);

        loadProjects();
    }

    // ─── Sidebar / Profile ───────────────────────────────────────────────────

    private void refreshSidebarProfile() {
        UserModel user = SessionManager.getInstance().getCurrentUser();
        userNameLabel.setText(user.getName());
        userRoleLabel.setText(user.getRole());

        // NIM
        if (user.getNim() != null && !user.getNim().isBlank()) {
            userNimLabel.setText("NIM: " + user.getNim());
            userNimLabel.setVisible(true);
            userNimLabel.setManaged(true);
        } else {
            userNimLabel.setText("");
            userNimLabel.setVisible(false);
            userNimLabel.setManaged(false);
        }

        // Initials avatar
        String initials = getInitials(user.getName());
        avatarInitials.setText(initials);

        // Photo if available
        loadPhoto(user.getPhotoPath());
    }

    private void loadPhoto(String photoPath) {
        if (photoPath != null && !photoPath.isBlank()) {
            // Normalize path separators (backend may return backslashes)
            String normalizedPath = photoPath.replace("\\", "/");
            java.io.File photoFile = new java.io.File(normalizedPath);
            if (!photoFile.exists()) {
                // Try with original path separators
                photoFile = new java.io.File(photoPath);
            }
            if (photoFile.exists()) {
                try {
                    Image img = new Image(photoFile.toURI().toString(), 56, 56, false, true);
                    if (!img.isError()) {
                        profilePhoto.setImage(img);
                        profilePhoto.setVisible(true);
                        avatarCircle.setVisible(false);
                        avatarInitials.setVisible(false);
                        return;
                    }
                } catch (Exception ignored) {}
            }
        }
        // Fallback to initials
        profilePhoto.setVisible(false);
        avatarCircle.setVisible(true);
        avatarInitials.setVisible(true);
    }

    private String getInitials(String name) {
        if (name == null || name.isBlank()) return "?";
        String[] parts = name.trim().split("\\s+");
        if (parts.length == 1) return parts[0].substring(0, Math.min(2, parts[0].length())).toUpperCase();
        return ("" + parts[0].charAt(0) + parts[parts.length - 1].charAt(0)).toUpperCase();
    }

    @FXML
    public void handleEditProfile() {
        UserModel user = SessionManager.getInstance().getCurrentUser();

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Edit Profil");
        dialog.setHeaderText("Perbarui data profil kamu");

        // Form fields
        TextField nameField = new TextField(user.getName());
        nameField.setPromptText("Nama lengkap");
        nameField.setStyle("-fx-pref-height: 34px; -fx-font-size: 13px;");

        TextField nimField = new TextField(user.getNim() != null ? user.getNim() : "");
        nimField.setPromptText("Nomor Induk Mahasiswa");
        nimField.setStyle("-fx-pref-height: 34px; -fx-font-size: 13px;");

        // Photo picker
        Label photoStatusLabel = new Label(
            user.getPhotoPath() != null && !user.getPhotoPath().isBlank()
                ? "Foto sudah ada" : "Belum ada foto"
        );
        photoStatusLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #6B7280;");

        final File[] selectedPhoto = {null};
        Button choosePhotoBtn = new Button("Pilih Foto...");
        choosePhotoBtn.setStyle(
            "-fx-background-color: #E5E7EB; -fx-text-fill: #374151; " +
            "-fx-font-size: 12px; -fx-background-radius: 5; -fx-cursor: hand; -fx-padding: 5 12 5 12;"
        );
        choosePhotoBtn.setOnAction(e -> {
            FileChooser fc = new FileChooser();
            fc.setTitle("Pilih Foto Profil");
            fc.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Gambar", "*.jpg", "*.jpeg", "*.png")
            );
            File f = fc.showOpenDialog(choosePhotoBtn.getScene().getWindow());
            if (f != null) {
                selectedPhoto[0] = f;
                photoStatusLabel.setText("Foto dipilih: " + f.getName());
                photoStatusLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #059669;");
            }
        });

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(12);
        grid.setPadding(new Insets(16));
        grid.setMinWidth(360);

        Label lName = new Label("Nama:");
        lName.setStyle("-fx-font-weight: bold; -fx-font-size: 12px;");
        Label lNim = new Label("NIM:");
        lNim.setStyle("-fx-font-weight: bold; -fx-font-size: 12px;");
        Label lPhoto = new Label("Foto Profil:");
        lPhoto.setStyle("-fx-font-weight: bold; -fx-font-size: 12px;");

        GridPane.setHgrow(nameField, Priority.ALWAYS);
        GridPane.setHgrow(nimField, Priority.ALWAYS);

        grid.addRow(0, lName, nameField);
        grid.addRow(1, lNim, nimField);
        grid.addRow(2, lPhoto, choosePhotoBtn);
        grid.add(photoStatusLabel, 1, 3);

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.getDialogPane().setPrefWidth(420);

        dialog.showAndWait().ifPresent(result -> {
            if (result != ButtonType.OK) return;
            String newName = nameField.getText().trim();
            String newNim = nimField.getText().trim();
            if (newName.isBlank()) {
                statusLabel.setText("Nama tidak boleh kosong.");
                return;
            }
            submitProfileUpdate(newName, newNim, selectedPhoto[0]);
        });
    }

    private void submitProfileUpdate(String name, String nim, File photoFile) {
        Task<UserModel> updateTask = new Task<>() {
            @Override
            protected UserModel call() throws Exception {
                // 1. Update name + NIM
                var body = new java.util.HashMap<String, Object>();
                body.put("name", name);
                body.put("nim", nim.isEmpty() ? null : nim);
                ApiResponse<Object> resp = ApiClient.put("/api/users/me", body, Object.class);
                if (!resp.isSuccess()) throw new Exception(resp.getMessage());

                // 2. Upload photo if selected
                if (photoFile != null) {
                    ApiResponse<Object> photoResp = ApiClient.postMultipart(
                        "/api/users/me/photo", photoFile, Object.class);
                    if (!photoResp.isSuccess()) throw new Exception(photoResp.getMessage());
                }

                // 3. Re-fetch updated profile
                ApiResponse<Object> meResp = ApiClient.get("/api/users/me", Object.class);
                if (!meResp.isSuccess()) throw new Exception(meResp.getMessage());
                String json = MAPPER.writeValueAsString(meResp.getData());
                return MAPPER.readValue(json, UserModel.class);
            }
        };

        updateTask.setOnSucceeded(e -> {
            UserModel updated = updateTask.getValue();
            // Update session dengan data terbaru
            SessionManager.getInstance().setSession(
                SessionManager.getInstance().getToken(), updated
            );
            Platform.runLater(() -> {
                refreshSidebarProfile();
                statusLabel.setText("");
            });
        });

        updateTask.setOnFailed(e -> Platform.runLater(() ->
            statusLabel.setText("Gagal update profil: " + updateTask.getException().getMessage())
        ));

        Thread t = new Thread(updateTask);
        t.setDaemon(true);
        t.start();
    }

    // ─── Projects ────────────────────────────────────────────────────────────

    private void loadProjects() {
        loadingIndicator.setVisible(true);
        statusLabel.setText("");

        Task<List<ProjectModel>> fetchTask = new Task<>() {
            @Override
            protected List<ProjectModel> call() throws Exception {
                ApiResponse<Object> raw = ApiClient.get("/api/projects", Object.class);
                if (!raw.isSuccess()) throw new Exception(raw.getMessage());
                String json = MAPPER.writeValueAsString(raw.getData());
                return MAPPER.readValue(json, new TypeReference<List<ProjectModel>>() {});
            }
        };

        fetchTask.setOnSucceeded(e -> {
            loadingIndicator.setVisible(false);
            allProjects = fetchTask.getValue();
            updateStatCards(allProjects);
            renderProjects(allProjects);
        });

        fetchTask.setOnFailed(e -> {
            loadingIndicator.setVisible(false);
            statusLabel.setText("Gagal memuat proyek: " + fetchTask.getException().getMessage());
        });

        Thread t = new Thread(fetchTask);
        t.setDaemon(true);
        t.start();
    }

    private void updateStatCards(List<ProjectModel> projects) {
        int totalProyek = projects.size();
        int totalOverdue = projects.stream().mapToInt(ProjectModel::getOverdueTaskCount).sum();
        int totalSelesai = projects.stream().mapToInt(ProjectModel::getCompletedTaskCount).sum();
        statTotalProyek.setText(String.valueOf(totalProyek));
        statOverdue.setText(String.valueOf(totalOverdue));
        statSelesai.setText(String.valueOf(totalSelesai));
    }

    @FXML
    public void handleSearch() {
        String query = searchField.getText().trim().toLowerCase();
        if (query.isEmpty()) {
            renderProjects(allProjects);
        } else {
            List<ProjectModel> filtered = allProjects.stream()
                .filter(p -> p.getTitle().toLowerCase().contains(query))
                .collect(Collectors.toList());
            renderProjects(filtered);
        }
    }

    private void renderProjects(List<ProjectModel> projects) {
        projectsPane.getChildren().clear();
        if (projects.isEmpty()) {
            Label empty = new Label("Belum ada proyek yang ditemukan.");
            empty.setStyle("-fx-text-fill: #6B7280; -fx-font-size: 14px;");
            projectsPane.getChildren().add(empty);
            return;
        }
        for (int i = 0; i < projects.size(); i++) {
            projectsPane.getChildren().add(buildProjectCard(projects.get(i), i));
        }
    }

    private VBox buildProjectCard(ProjectModel project, int index) {
        String accentColor = ACCENT_COLORS[index % ACCENT_COLORS.length];
        String accentLight = ACCENT_LIGHT[index % ACCENT_LIGHT.length];

        VBox card = new VBox(0);
        card.setPrefWidth(270);
        card.setMaxWidth(270);
        card.setStyle(
            "-fx-background-color: white; -fx-background-radius: 12px; " +
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.10), 10, 0, 0, 3); -fx-cursor: hand;"
        );

        HBox accentBar = new HBox();
        accentBar.setPrefHeight(6);
        accentBar.setStyle("-fx-background-color: " + accentColor + "; -fx-background-radius: 12px 12px 0 0;");

        VBox body = new VBox(8);
        body.setPadding(new Insets(14, 16, 14, 16));

        Label title = new Label(project.getTitle());
        title.setWrapText(true);
        title.setMaxWidth(238);
        title.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #1E293B;");

        String deadlineText = project.getDeadline() != null
                ? "  " + project.getDeadline().format(FMT)
                : "  Belum ada deadline";
        Label deadline = new Label(deadlineText);
        deadline.setStyle("-fx-font-size: 12px; -fx-text-fill: #64748B;");

        Label members = new Label("  " + project.getMemberCount() + " anggota");
        members.setStyle("-fx-font-size: 12px; -fx-text-fill: #64748B;");

        int taskCount = project.getTaskCount();
        int completedCount = project.getCompletedTaskCount();
        double progressVal = taskCount == 0 ? 0 : (double) completedCount / taskCount;
        int progressPct = (int) Math.round(progressVal * 100);

        HBox progressHeader = new HBox();
        progressHeader.setAlignment(Pos.CENTER_LEFT);
        Label tasksLabel = new Label(completedCount + "/" + taskCount + " task selesai");
        tasksLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #64748B;");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Label pctLabel = new Label(progressPct + "%");
        pctLabel.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: " + accentColor + ";");
        progressHeader.getChildren().addAll(tasksLabel, spacer, pctLabel);

        ProgressBar progress = new ProgressBar(progressVal);
        progress.setMaxWidth(Double.MAX_VALUE);
        progress.setStyle("-fx-accent: " + accentColor + ";");
        progress.setPrefHeight(6);

        body.getChildren().addAll(title, deadline, members, progressHeader, progress);

        if (project.getOverdueTaskCount() > 0) {
            Label overdueLabel = new Label("  " + project.getOverdueTaskCount() + " task overdue");
            overdueLabel.setStyle(
                "-fx-text-fill: #DC2626; -fx-font-size: 11px; -fx-font-weight: bold; " +
                "-fx-background-color: #FEF2F2; -fx-background-radius: 4px; -fx-padding: 3 8 3 8;"
            );
            body.getChildren().add(overdueLabel);
        }

        card.getChildren().addAll(accentBar, body);
        card.setOnMouseClicked(e -> openKanban(project));

        card.setOnMouseEntered(e -> card.setStyle(
            "-fx-background-color: " + accentLight + "; -fx-background-radius: 12px; " +
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.18), 16, 0, 0, 5); -fx-cursor: hand;"
        ));
        card.setOnMouseExited(e -> card.setStyle(
            "-fx-background-color: white; -fx-background-radius: 12px; " +
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.10), 10, 0, 0, 3); -fx-cursor: hand;"
        ));

        return card;
    }

    private void openKanban(ProjectModel project) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/kanban.fxml"));
            Stage stage = (Stage) projectsPane.getScene().getWindow();
            Scene scene = new Scene(loader.load(), 1200, 700);
            scene.getStylesheets().add(getClass().getResource("/css/styles.css").toExternalForm());
            KanbanController controller = loader.getController();
            controller.initWithProject(project);
            stage.setScene(scene);
            stage.setTitle("TaskForge — " + project.getTitle());
        } catch (Exception e) {
            statusLabel.setText("Gagal membuka Kanban: " + e.getMessage());
        }
    }

    @FXML
    public void handleNewProject() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Buat Proyek Baru");
        dialog.setHeaderText("Isi detail proyek");

        TextField titleField = new TextField();
        titleField.setPromptText("Judul proyek");
        TextArea descField = new TextArea();
        descField.setPromptText("Deskripsi (opsional)");
        descField.setPrefRowCount(3);
        DatePicker deadlinePicker = new DatePicker();
        deadlinePicker.setPromptText("Pilih deadline (opsional)");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(10));
        grid.addRow(0, new Label("Judul:"), titleField);
        grid.addRow(1, new Label("Deskripsi:"), descField);
        grid.addRow(2, new Label("Deadline:"), deadlinePicker);

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.showAndWait().ifPresent(result -> {
            if (result == ButtonType.OK && !titleField.getText().isBlank()) {
                String deadlineStr = null;
                if (deadlinePicker.getValue() != null) {
                    deadlineStr = deadlinePicker.getValue().atTime(23, 59, 0).toString();
                }
                submitNewProject(titleField.getText().trim(), descField.getText().trim(), deadlineStr);
            }
        });
    }

    private void submitNewProject(String title, String description, String deadline) {
        Task<Void> createTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                var body = new java.util.HashMap<String, Object>();
                body.put("title", title);
                body.put("description", description.isEmpty() ? null : description);
                if (deadline != null) body.put("deadline", deadline);
                ApiClient.post("/api/projects", body, Object.class);
                return null;
            }
        };
        createTask.setOnSucceeded(e -> loadProjects());
        createTask.setOnFailed(e -> Platform.runLater(
                () -> statusLabel.setText("Gagal membuat proyek: " + createTask.getException().getMessage())));
        Thread t = new Thread(createTask);
        t.setDaemon(true);
        t.start();
    }

    @FXML
    public void handleLogout() {
        SessionManager.getInstance().clearSession();
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/login.fxml"));
            Stage stage = (Stage) userNameLabel.getScene().getWindow();
            Scene scene = new Scene(loader.load(), 420, 480);
            scene.getStylesheets().add(getClass().getResource("/css/styles.css").toExternalForm());
            stage.setScene(scene);
            stage.setTitle("TaskForge — Login");
        } catch (Exception e) {
            statusLabel.setText("Gagal logout");
        }
    }
}
