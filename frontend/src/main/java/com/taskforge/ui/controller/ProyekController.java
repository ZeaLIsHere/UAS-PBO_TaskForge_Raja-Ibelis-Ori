package com.taskforge.ui.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.taskforge.ui.model.ApiResponse;
import com.taskforge.ui.model.ProjectModel;
import com.taskforge.ui.service.ApiClient;
import com.taskforge.ui.session.SessionManager;
import com.taskforge.ui.util.CoverLoader;
import com.taskforge.ui.util.Dialogs;
import com.taskforge.ui.util.SceneNavigator;
import com.taskforge.ui.util.SidebarProfileBinder;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.Separator;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;

import java.time.format.DateTimeFormatter;
import java.util.List;

public class ProyekController {

    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd MMM yyyy");

    @FXML private Label userNameLabel;
    @FXML private Label userNimLabel;
    @FXML private Label userRoleLabel;
    @FXML private Label avatarInitials;
    @FXML private Button newProjectButton;
    @FXML private Button tabMineButton;
    @FXML private Button tabAvailableButton;
    @FXML private FlowPane projectsPane;
    @FXML private ProgressIndicator loadingIndicator;
    @FXML private Label statusLabel;
    @FXML private VBox emptyStatePane;
    @FXML private Label emptyTitleLabel;
    @FXML private Label emptySubtitleLabel;

    private List<ProjectModel> myProjects = List.of();
    private List<ProjectModel> availableProjects = List.of();
    private boolean showingMine = true;

    @FXML
    public void initialize() {
        loadingIndicator.setVisible(false);
        statusLabel.setText("");
        refreshSidebarProfile();

        // KETUA membuat/claim proyek kelompok; DOSEN membuat proyek akademik
        boolean canCreate = SessionManager.getInstance().isKetua()
                || SessionManager.getInstance().isDosen();
        newProjectButton.setVisible(canCreate);
        newProjectButton.setManaged(canCreate);

        loadProjects();
    }

    private void refreshSidebarProfile() {
        SidebarProfileBinder.refresh(
                userNameLabel, userNimLabel, userRoleLabel, avatarInitials
        );
    }

    private void loadProjects() {
        loadingIndicator.setVisible(true);
        statusLabel.setText("");

        Task<Void> fetchTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                ApiResponse<Object> mineRaw = ApiClient.get("/api/projects", Object.class);
                if (!mineRaw.isSuccess()) throw new Exception(mineRaw.getMessage());
                myProjects = MAPPER.convertValue(mineRaw.getData(), new TypeReference<List<ProjectModel>>() {});

                ApiResponse<Object> availRaw = ApiClient.get("/api/projects/available", Object.class);
                if (!availRaw.isSuccess()) throw new Exception(availRaw.getMessage());
                availableProjects = MAPPER.convertValue(availRaw.getData(), new TypeReference<List<ProjectModel>>() {});
                return null;
            }
        };

        fetchTask.setOnSucceeded(e -> {
            loadingIndicator.setVisible(false);
            tabAvailableButton.setText("Tersedia (" + availableProjects.size() + ")");
            renderCurrentTab();
        });

        fetchTask.setOnFailed(e -> {
            loadingIndicator.setVisible(false);
            statusLabel.setText("Gagal memuat proyek: " + fetchTask.getException().getMessage());
        });

        Thread t = new Thread(fetchTask);
        t.setDaemon(true);
        t.start();
    }

    @FXML
    public void handleTabMine() {
        showingMine = true;
        applyTabStyles(true);
        renderCurrentTab();
    }

    @FXML
    public void handleTabAvailable() {
        showingMine = false;
        applyTabStyles(false);
        renderCurrentTab();
    }

    private void applyTabStyles(boolean mineActive) {
        tabMineButton.getStyleClass().removeAll("proyek-tab", "proyek-tab-active");
        tabAvailableButton.getStyleClass().removeAll("proyek-tab", "proyek-tab-active");
        tabMineButton.getStyleClass().add(mineActive ? "proyek-tab-active" : "proyek-tab");
        tabAvailableButton.getStyleClass().add(mineActive ? "proyek-tab" : "proyek-tab-active");
    }

    private void renderCurrentTab() {
        List<ProjectModel> projects = showingMine ? myProjects : availableProjects;
        projectsPane.getChildren().clear();

        if (projects.isEmpty()) {
            emptyStatePane.setVisible(true);
            emptyStatePane.setManaged(true);
            if (showingMine) {
                emptyTitleLabel.setText("Belum ada proyek");
                emptySubtitleLabel.setText("Proyek kamu akan muncul di sini");
            } else {
                emptyTitleLabel.setText("Tidak ada proyek tersedia");
                emptySubtitleLabel.setText("Proyek yang bisa kamu ikuti akan muncul di sini");
            }
            return;
        }

        emptyStatePane.setVisible(false);
        emptyStatePane.setManaged(false);
        for (ProjectModel project : projects) {
            projectsPane.getChildren().add(buildProjectCard(project, showingMine));
        }
    }

    private VBox buildProjectCard(ProjectModel project, boolean clickable) {
        VBox card = new VBox(0);
        card.setPrefWidth(320);
        card.setMaxWidth(320);
        card.setStyle(
                "-fx-background-color: white; -fx-background-radius: 12px; " +
                "-fx-border-color: #E2E8F0; -fx-border-radius: 12px; -fx-border-width: 1; " +
                "-fx-effect: dropshadow(gaussian, rgba(15,23,42,0.05), 8, 0, 0, 2);" +
                (clickable ? " -fx-cursor: hand;" : "")
        );

        VBox body = new VBox(10);
        body.setPadding(new Insets(18, 20, 16, 20));

        HBox titleRow = new HBox(10);
        titleRow.setAlignment(Pos.CENTER_LEFT);
        Label title = new Label(project.getTitle());
        title.setWrapText(true);
        title.setMaxWidth(210);
        title.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: #0F172A;");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Label activeBadge = new Label("ACTIVE");
        activeBadge.setStyle(
                "-fx-background-color: #10B981; -fx-text-fill: white; " +
                "-fx-font-size: 10px; -fx-font-weight: bold; -fx-background-radius: 20; " +
                "-fx-padding: 4 10 4 10;"
        );
        titleRow.getChildren().addAll(title, spacer, activeBadge);

        String desc = project.getDescription() != null && !project.getDescription().isBlank()
                ? project.getDescription()
                : "Tidak ada deskripsi";
        Label description = new Label(desc);
        description.setWrapText(true);
        description.setMaxWidth(280);
        description.setStyle("-fx-font-size: 13px; -fx-text-fill: #64748B;");

        Separator separator = new Separator();
        separator.setStyle("-fx-background-color: #F1F5F9;");

        HBox metaRow = new HBox(16);
        metaRow.setAlignment(Pos.CENTER_LEFT);

        String startDate = project.getCreatedAt() != null
                ? project.getCreatedAt().format(FMT)
                : "-";
        String endDate = project.getDeadline() != null
                ? project.getDeadline().format(FMT)
                : "Belum ada deadline";
        Label dateLabel = new Label("  " + startDate + " - " + endDate);
        dateLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #64748B;");

        int memberCount = project.getMemberCount();
        Label membersLabel = new Label("  " + memberCount + "/" + project.getMaxMembers());
        membersLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #64748B;");

        metaRow.getChildren().addAll(dateLabel, membersLabel);

        String leaderName = project.getOwner() != null ? project.getOwner().getName() : "-";
        Label leaderLabel = new Label("Ketua: " + leaderName);
        leaderLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #94A3B8;");

        body.getChildren().addAll(titleRow, description, separator, metaRow, leaderLabel);
        if (project.isHasCover()) {
            card.getChildren().add(CoverLoader.coverNode(project.getId(), 320, 140, 12));
        }
        card.getChildren().add(body);

        if (clickable) {
            card.setOnMouseClicked(e -> openProjectDetail(project));
            card.setOnMouseEntered(e -> card.setStyle(
                    "-fx-background-color: #F8FAFC; -fx-background-radius: 12px; " +
                    "-fx-border-color: #CBD5E1; -fx-border-radius: 12px; -fx-border-width: 1; " +
                    "-fx-effect: dropshadow(gaussian, rgba(15,23,42,0.10), 12, 0, 0, 3); -fx-cursor: hand;"
            ));
            card.setOnMouseExited(e -> card.setStyle(
                    "-fx-background-color: white; -fx-background-radius: 12px; " +
                    "-fx-border-color: #E2E8F0; -fx-border-radius: 12px; -fx-border-width: 1; " +
                    "-fx-effect: dropshadow(gaussian, rgba(15,23,42,0.05), 8, 0, 0, 2); -fx-cursor: hand;"
            ));
        }

        return card;
    }

    private void openProjectDetail(ProjectModel project) {
        try {
            Stage stage = (Stage) projectsPane.getScene().getWindow();
            ProjectDetailController controller = SceneNavigator.navigate(
                    stage, "/fxml/project-detail.fxml",
                    "TaskForge — " + project.getTitle(), 1100, 700);
            controller.initWithProject(project);
        } catch (Exception e) {
            statusLabel.setText("Gagal membuka detail proyek: " + e.getMessage());
        }
    }

    @FXML
    public void handleNewProject() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("TaskForge");
        dialog.setHeaderText("Buat Proyek Baru");

        TextField titleField = new TextField();
        titleField.setPromptText("Contoh: Tugas Akhir Sistem Perpustakaan");
        titleField.getStyleClass().add("text-field");

        TextArea descField = new TextArea();
        descField.setPromptText("Deskripsi singkat proyek (opsional)");
        descField.setPrefRowCount(3);
        descField.setWrapText(true);

        DatePicker deadlinePicker = new DatePicker();
        deadlinePicker.setPromptText("Pilih deadline (opsional)");
        deadlinePicker.setMaxWidth(Double.MAX_VALUE);

        Spinner<Integer> maxMembersSpinner = new Spinner<>(2, 20, 4);
        maxMembersSpinner.setEditable(true);
        maxMembersSpinner.setPrefWidth(90);
        Label spinnerHint = new Label("anggota (termasuk ketua)");
        spinnerHint.getStyleClass().add("dialog-form-hint");
        HBox memberRow = new HBox(10, maxMembersSpinner, spinnerHint);
        memberRow.setAlignment(Pos.CENTER_LEFT);

        // Pemilih foto sampul (opsional)
        final File[] coverHolder = {null};
        Button pickCover = new Button("Pilih Gambar...");
        pickCover.getStyleClass().add("secondary-button");
        Label coverName = new Label("Belum ada gambar dipilih");
        coverName.getStyleClass().add("dialog-form-hint");
        pickCover.setOnAction(ev -> {
            FileChooser fc = new FileChooser();
            fc.setTitle("Pilih Foto Sampul");
            fc.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter("Gambar (JPG/PNG)", "*.png", "*.jpg", "*.jpeg"));
            File f = fc.showOpenDialog(pickCover.getScene().getWindow());
            if (f != null) { coverHolder[0] = f; coverName.setText(f.getName()); }
        });
        HBox coverRow = new HBox(10, pickCover, coverName);
        coverRow.setAlignment(Pos.CENTER_LEFT);

        VBox content = new VBox(16,
                field("Judul Proyek", titleField),
                field("Deskripsi", descField),
                field("Deadline", deadlinePicker),
                field("Maksimal Anggota", memberRow),
                field("Foto Sampul (opsional)", coverRow));
        content.setPadding(new Insets(22, 26, 8, 26));
        content.setPrefWidth(440);

        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        ((Button) dialog.getDialogPane().lookupButton(ButtonType.OK)).setText("Buat Proyek");
        ((Button) dialog.getDialogPane().lookupButton(ButtonType.CANCEL)).setText("Batal");
        Dialogs.style(dialog);

        dialog.showAndWait().ifPresent(result -> {
            if (result == ButtonType.OK && !titleField.getText().isBlank()) {
                String deadlineStr = null;
                if (deadlinePicker.getValue() != null) {
                    deadlineStr = deadlinePicker.getValue().atTime(23, 59, 0).toString();
                }
                submitNewProject(
                        titleField.getText().trim(),
                        descField.getText().trim(),
                        deadlineStr,
                        maxMembersSpinner.getValue(),
                        coverHolder[0]
                );
            }
        });
    }

    /** Bangun satu baris form: label tebal di atas, input di bawahnya. */
    private VBox field(String labelText, Node input) {
        Label label = new Label(labelText);
        label.getStyleClass().add("dialog-form-label");
        if (input instanceof Region region) {
            region.setMaxWidth(Double.MAX_VALUE);
        }
        VBox box = new VBox(6, label, input);
        return box;
    }

    private void submitNewProject(String title, String description, String deadline,
                                  int maxMembers, File cover) {
        Task<Void> createTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                var body = new java.util.HashMap<String, Object>();
                body.put("title", title);
                body.put("description", description.isEmpty() ? null : description);
                if (deadline != null) body.put("deadline", deadline);
                body.put("maxMembers", maxMembers);
                ApiResponse<Object> raw = ApiClient.post("/api/projects", body, Object.class);
                if (!raw.isSuccess()) throw new Exception(raw.getMessage());

                // Upload foto sampul bila dipilih
                if (cover != null) {
                    ProjectModel created = MAPPER.convertValue(raw.getData(), ProjectModel.class);
                    if (created != null && created.getId() != null) {
                        ApiClient.postMultipart(
                                "/api/projects/" + created.getId() + "/cover", cover, Object.class);
                    }
                }
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
    public void handleDashboard() {
        navigate("/fxml/dashboard.fxml", "TaskForge — Dashboard");
    }

    @FXML
    public void handleProfil() {
        navigate("/fxml/profil.fxml", "TaskForge — Profil");
    }

    @FXML
    public void handleNotifikasi() {
        navigate("/fxml/notifikasi.fxml", "TaskForge — Notifikasi");
    }

    @FXML
    public void handleLogout() {
        SidebarProfileBinder.logout(userNameLabel);
    }

    private void navigate(String fxml, String title) {
        try {
            Stage stage = (Stage) userNameLabel.getScene().getWindow();
            SceneNavigator.navigate(stage, fxml, title, 1100, 700);
        } catch (Exception e) {
            statusLabel.setText("Gagal navigasi: " + e.getMessage());
        }
    }
}
