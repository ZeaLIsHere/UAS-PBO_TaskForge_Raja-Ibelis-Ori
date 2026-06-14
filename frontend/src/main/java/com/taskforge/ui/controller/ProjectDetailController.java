package com.taskforge.ui.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.taskforge.ui.model.ApiResponse;
import com.taskforge.ui.model.ProjectModel;
import com.taskforge.ui.model.UserModel;
import com.taskforge.ui.service.ApiClient;
import com.taskforge.ui.session.SessionManager;
import com.taskforge.ui.util.Dialogs;
import com.taskforge.ui.util.SceneNavigator;
import com.taskforge.ui.util.SidebarProfileBinder;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class ProjectDetailController {

    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd MMM yyyy");

    @FXML private Label userNameLabel;
    @FXML private Label userNimLabel;
    @FXML private Label userRoleLabel;
    @FXML private Label avatarInitials;
    @FXML private Label projectTitleLabel;
    @FXML private Label projectDescLabel;
    @FXML private Label ketuaLabel;
    @FXML private Label periodeLabel;
    @FXML private Label anggotaCountLabel;
    @FXML private Button kanbanButton;
    @FXML private Button reportButton;
    @FXML private Button addMemberButton;
    @FXML private VBox membersPane;
    @FXML private VBox infoCard;
    @FXML private ProgressIndicator loadingIndicator;
    @FXML private Label statusLabel;

    private Long projectId;
    private ProjectModel currentProject;

    @FXML
    public void initialize() {
        loadingIndicator.setVisible(false);
        statusLabel.setText("");
        refreshSidebarProfile();
    }

    public void initWithProject(ProjectModel project) {
        this.projectId = project.getId();
        loadProjectDetail();
    }

    public void initWithProjectId(Long projectId) {
        this.projectId = projectId;
        loadProjectDetail();
    }

    private void refreshSidebarProfile() {
        SidebarProfileBinder.refresh(userNameLabel, userNimLabel, userRoleLabel, avatarInitials);
    }

    private void loadProjectDetail() {
        loadingIndicator.setVisible(true);
        statusLabel.setText("");

        Task<ProjectModel> fetchTask = new Task<>() {
            @Override
            protected ProjectModel call() throws Exception {
                ApiResponse<Object> raw = ApiClient.get("/api/projects/" + projectId, Object.class);
                if (!raw.isSuccess()) throw new Exception(raw.getMessage());
                return MAPPER.convertValue(raw.getData(), ProjectModel.class);
            }
        };

        fetchTask.setOnSucceeded(e -> {
            loadingIndicator.setVisible(false);
            currentProject = fetchTask.getValue();
            renderProjectDetail();
        });

        fetchTask.setOnFailed(e -> {
            loadingIndicator.setVisible(false);
            statusLabel.setText("Gagal memuat proyek: " + fetchTask.getException().getMessage());
        });

        Thread t = new Thread(fetchTask);
        t.setDaemon(true);
        t.start();
    }

    private void renderProjectDetail() {
        projectTitleLabel.setText(currentProject.getTitle());

        // Foto sampul (jika ada) di paling atas kartu info
        infoCard.getChildren().removeIf(n -> "cover".equals(n.getId()));
        if (currentProject.isHasCover()) {
            var cover = com.taskforge.ui.util.CoverLoader.coverNode(currentProject.getId(), 760, 200, 14);
            cover.setId("cover");
            infoCard.getChildren().add(0, cover);
        }

        String desc = currentProject.getDescription();
        projectDescLabel.setText(desc != null && !desc.isBlank() ? desc : "Tidak ada deskripsi");

        ketuaLabel.setText(currentProject.getOwner() != null ? currentProject.getOwner().getName() : "-");

        String startDate = currentProject.getCreatedAt() != null
                ? currentProject.getCreatedAt().format(FMT) : "-";
        String endDate = currentProject.getDeadline() != null
                ? currentProject.getDeadline().format(FMT) : "Belum ada deadline";
        periodeLabel.setText(startDate + " - " + endDate);

        anggotaCountLabel.setText(currentProject.getMemberCount() + " / " + currentProject.getMaxMembers());

        boolean isOwner = isCurrentUserOwner();
        addMemberButton.setVisible(isOwner);
        addMemberButton.setManaged(isOwner);

        renderMembers();
    }

    private void renderMembers() {
        membersPane.getChildren().clear();
        List<UserModel> allMembers = buildMemberList();

        if (allMembers.isEmpty()) {
            Label empty = new Label("Belum ada anggota tim.");
            empty.setStyle("-fx-text-fill: #94A3B8; -fx-font-size: 13px;");
            membersPane.getChildren().add(empty);
            return;
        }

        for (UserModel member : allMembers) {
            membersPane.getChildren().add(buildMemberRow(member));
        }
    }

    private List<UserModel> buildMemberList() {
        List<UserModel> allMembers = new ArrayList<>();
        if (currentProject.getOwner() != null) {
            allMembers.add(currentProject.getOwner());
        }
        if (currentProject.getMembers() != null) {
            allMembers.addAll(currentProject.getMembers());
        }
        return allMembers;
    }

    private HBox buildMemberRow(UserModel member) {
        HBox row = new HBox(14);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(12, 16, 12, 16));
        row.setStyle(
                "-fx-background-color: #F8FAFC; -fx-background-radius: 10; " +
                "-fx-border-color: #E2E8F0; -fx-border-radius: 10; -fx-border-width: 1;"
        );

        StackPane avatar = new StackPane();
        avatar.setPrefSize(40, 40);
        avatar.setStyle(
                "-fx-background-color: #E0E7FF; -fx-background-radius: 20; " +
                "-fx-min-width: 40; -fx-min-height: 40;"
        );
        String initials = getInitials(member.getName());
        Label avatarLabel = new Label(initials);
        avatarLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #4338CA;");
        avatar.getChildren().add(avatarLabel);

        VBox info = new VBox(2);
        Label nameLabel = new Label(member.getName());
        nameLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #0F172A;");

        String nim = member.getNim() != null && !member.getNim().isBlank() ? member.getNim() : "-";
        String roleLabel = "KETUA".equals(member.getRole()) ? "Ketua Kelompok" : "Anggota Kelompok";
        Label detailLabel = new Label("NIM: " + nim + "  •  " + roleLabel);
        detailLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #64748B;");
        info.getChildren().addAll(nameLabel, detailLabel);

        row.getChildren().addAll(avatar, info);
        return row;
    }

    private String getInitials(String name) {
        if (name == null || name.isBlank()) return "?";
        String[] parts = name.trim().split("\\s+");
        if (parts.length == 1) return parts[0].substring(0, 1).toUpperCase();
        return (parts[0].substring(0, 1) + parts[parts.length - 1].substring(0, 1)).toUpperCase();
    }

    private boolean isCurrentUserOwner() {
        UserModel current = SessionManager.getInstance().getCurrentUser();
        return currentProject.getOwner() != null
                && current != null
                && currentProject.getOwner().getId().equals(current.getId());
    }

    @FXML
    public void handleAddMember() {
        if (currentProject.getMemberCount() >= currentProject.getMaxMembers()) {
            statusLabel.setText("Proyek sudah penuh (" + currentProject.getMaxMembers() + " anggota termasuk ketua)");
            return;
        }

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Tambah Anggota");
        dialog.setHeaderText("Masukkan email anggota yang ingin ditambahkan");

        TextField emailField = new TextField();
        emailField.setPromptText("email@example.com");
        emailField.setPrefWidth(280);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(10));
        grid.addRow(0, new Label("Email:"), emailField);

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        Dialogs.style(dialog);

        dialog.showAndWait().ifPresent(result -> {
            if (result == ButtonType.OK && !emailField.getText().isBlank()) {
                submitAddMember(emailField.getText().trim());
            }
        });
    }

    private void submitAddMember(String email) {
        statusLabel.setText("");
        loadingIndicator.setVisible(true);

        Task<Void> addTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                var body = new java.util.HashMap<String, String>();
                body.put("email", email);
                ApiResponse<Object> raw = ApiClient.post(
                        "/api/projects/" + projectId + "/members", body, Object.class);
                if (!raw.isSuccess()) throw new Exception(raw.getMessage());
                return null;
            }
        };

        addTask.setOnSucceeded(e -> loadProjectDetail());
        addTask.setOnFailed(e -> Platform.runLater(() -> {
            loadingIndicator.setVisible(false);
            statusLabel.setText("Gagal menambah anggota: " + addTask.getException().getMessage());
        }));

        Thread t = new Thread(addTask);
        t.setDaemon(true);
        t.start();
    }

    @FXML
    public void handleKanban() {
        try {
            Stage stage = (Stage) projectTitleLabel.getScene().getWindow();
            KanbanController controller = SceneNavigator.navigate(
                    stage, "/fxml/kanban.fxml", "TaskForge — " + currentProject.getTitle(), 1200, 700);
            controller.initWithProject(currentProject);
        } catch (Exception e) {
            statusLabel.setText("Gagal membuka Kanban: " + e.getMessage());
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
            statusLabel.setText("Gagal membuka laporan: " + e.getMessage());
        }
    }

    @FXML
    public void handleBack() {
        navigate("/fxml/proyek.fxml", "TaskForge — Proyek");
    }

    @FXML
    public void handleDashboard() {
        navigate("/fxml/dashboard.fxml", "TaskForge — Dashboard");
    }

    @FXML
    public void handleProyek() {
        navigate("/fxml/proyek.fxml", "TaskForge — Proyek");
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
