package com.taskforge.ui.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.taskforge.ui.model.ApiResponse;
import com.taskforge.ui.model.ProjectModel;
import com.taskforge.ui.model.TaskModel;
import com.taskforge.ui.model.UserModel;
import com.taskforge.ui.service.ApiClient;
import com.taskforge.ui.util.Dialogs;
import com.taskforge.ui.util.SceneNavigator;
import com.taskforge.ui.util.SidebarProfileBinder;
import com.taskforge.ui.session.SessionManager;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class KanbanController {

    @FXML private Label projectTitleLabel;
    @FXML private Button addTaskButton;
    @FXML private Label statusLabel;
    @FXML private VBox todoColumn;
    @FXML private VBox inProgressColumn;
    @FXML private VBox reviewColumn;
    @FXML private VBox doneColumn;
    @FXML private Label todoCount;
    @FXML private Label inProgressCount;
    @FXML private Label reviewCount;
    @FXML private Label doneCount;

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
        addTaskButton.setVisible(isKetua);
        addTaskButton.setManaged(isKetua);

        setupDragAndDrop();
        loadTasks();
    }

    public void refreshData() {
        loadTasks();
    }

    private void loadTasks() {
        clearColumns();
        statusLabel.setText("Memuat tasks...");

        Task<List<TaskModel>> fetchTask = new Task<>() {
            @Override
            protected List<TaskModel> call() throws Exception {
                ApiResponse<Object> raw = ApiClient.get(
                        "/api/projects/" + currentProject.getId() + "/tasks", Object.class);
                if (!raw.isSuccess()) throw new Exception(raw.getMessage());
                String json = MAPPER.writeValueAsString(raw.getData());
                return MAPPER.readValue(json, new TypeReference<List<TaskModel>>() {});
            }
        };

        fetchTask.setOnSucceeded(e -> {
            statusLabel.setText("");
            renderKanban(fetchTask.getValue());
        });

        fetchTask.setOnFailed(e -> Platform.runLater(
                () -> statusLabel.setText("Gagal memuat tasks: " + fetchTask.getException().getMessage())));

        Thread t = new Thread(fetchTask);
        t.setDaemon(true);
        t.start();
    }

    private void clearColumns() {
        // Keep the column header (first child), remove task cards
        trimColumn(todoColumn);
        trimColumn(inProgressColumn);
        trimColumn(reviewColumn);
        trimColumn(doneColumn);
    }

    private void trimColumn(VBox col) {
        if (col.getChildren().size() > 1) {
            col.getChildren().subList(1, col.getChildren().size()).clear();
        }
    }

    private void renderKanban(List<TaskModel> tasks) {
        Map<String, List<TaskModel>> byStatus = tasks.stream()
                .collect(Collectors.groupingBy(TaskModel::getStatus));

        List<TaskModel> todo = byStatus.getOrDefault("TODO", List.of());
        List<TaskModel> inProgress = byStatus.getOrDefault("IN_PROGRESS", List.of());
        List<TaskModel> review = byStatus.getOrDefault("REVIEW", List.of());
        List<TaskModel> done = byStatus.getOrDefault("DONE", List.of());

        addTaskCards(todoColumn, todo);
        addTaskCards(inProgressColumn, inProgress);
        addTaskCards(reviewColumn, review);
        addTaskCards(doneColumn, done);

        todoCount.setText(String.valueOf(todo.size()));
        inProgressCount.setText(String.valueOf(inProgress.size()));
        reviewCount.setText(String.valueOf(review.size()));
        doneCount.setText(String.valueOf(done.size()));
    }

    private void addTaskCards(VBox column, List<TaskModel> tasks) {
        for (TaskModel task : tasks) {
            column.getChildren().add(buildTaskCard(task));
        }
    }

    private VBox buildTaskCard(TaskModel task) {
        boolean isDone = "DONE".equals(task.getStatus());
        boolean isOverdue = task.isOverdue();

        // Warna aksen atas kartu berdasarkan status
        String accent = switch (task.getStatus()) {
            case "IN_PROGRESS" -> "#4F46E5";
            case "REVIEW" -> "#F59E0B";
            case "DONE" -> "#10B981";
            default -> "#94A3B8";
        };

        VBox card = new VBox(10);
        card.setPadding(new Insets(14, 16, 14, 16));
        card.setMaxWidth(Double.MAX_VALUE);

        String base;
        if (isOverdue) {
            base = "-fx-background-color: #FFF5F5; -fx-background-radius: 12; " +
                   "-fx-border-color: #EF4444; -fx-border-radius: 12; -fx-border-width: 2; " +
                   "-fx-effect: dropshadow(gaussian, rgba(15,23,42,0.05), 8, 0, 0, 3);";
        } else {
            base = "-fx-background-color: white; -fx-background-radius: 12; " +
                   "-fx-border-color: " + accent + " #E2E8F0 #E2E8F0 #E2E8F0; " +
                   "-fx-border-radius: 12; -fx-border-width: 4 1 1 1; " +
                   "-fx-effect: dropshadow(gaussian, rgba(15,23,42,0.05), 8, 0, 0, 3);";
        }
        if (isDone) base += " -fx-opacity: 0.8;";
        card.setStyle(base);

        // Top row: badge tipe (kiri) + prioritas / centang done (kanan)
        HBox topRow = new HBox(6);
        topRow.setAlignment(Pos.CENTER_LEFT);
        Label typeBadge = new Label("MILESTONE".equals(task.getTaskType()) ? "MILESTONE" : "TASK");
        typeBadge.setStyle("MILESTONE".equals(task.getTaskType())
                ? "-fx-background-color: #F3E8FF; -fx-text-fill: #7E22CE; -fx-font-size: 9px; -fx-font-weight: bold; -fx-background-radius: 5; -fx-padding: 2 7 2 7;"
                : "-fx-background-color: #DBEAFE; -fx-text-fill: #1D4ED8; -fx-font-size: 9px; -fx-font-weight: bold; -fx-background-radius: 5; -fx-padding: 2 7 2 7;");
        Region topSpacer = new Region();
        HBox.setHgrow(topSpacer, Priority.ALWAYS);
        topRow.getChildren().addAll(typeBadge, topSpacer);
        if (isDone) {
            Label check = new Label("✓");
            check.setStyle("-fx-background-color: #D1FAE5; -fx-text-fill: #059669; -fx-font-size: 11px; "
                    + "-fx-font-weight: bold; -fx-background-radius: 20; -fx-padding: 1 7 1 7;");
            topRow.getChildren().add(check);
        } else {
            Label priorityBadge = new Label(task.getPriority());
            priorityBadge.getStyleClass().add(switch (task.getPriority()) {
                case "HIGH" -> "badge-high";
                case "MEDIUM" -> "badge-medium";
                default -> "badge-low";
            });
            topRow.getChildren().add(priorityBadge);
        }

        // Judul
        Label titleLabel = new Label(task.getTitle());
        titleLabel.setWrapText(true);
        titleLabel.setMaxWidth(250);
        titleLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #191C1E;"
                + (isDone ? " -fx-strikethrough: true; -fx-text-fill: #64748B;" : ""));

        // Deskripsi (maks 2 baris)
        String descText = task.getDescription() != null && !task.getDescription().isBlank()
                ? task.getDescription() : "Tidak ada deskripsi";
        Label descLabel = new Label(descText);
        descLabel.setWrapText(true);
        descLabel.setMaxWidth(250);
        descLabel.setMaxHeight(38);
        descLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #64748B;");

        // Footer: avatar assignee + deadline / status
        HBox footer = new HBox(8);
        footer.setAlignment(Pos.CENTER_LEFT);
        Label avatar = new Label(SidebarProfileBinder.getInitials(task.getAssigneeName()));
        avatar.setMinSize(26, 26);
        avatar.setPrefSize(26, 26);
        avatar.setAlignment(Pos.CENTER);
        avatar.setStyle("-fx-background-color: #E0E7FF; -fx-text-fill: #4338CA; -fx-font-size: 10px; "
                + "-fx-font-weight: bold; -fx-background-radius: 20;");
        Region fSpacer = new Region();
        HBox.setHgrow(fSpacer, Priority.ALWAYS);

        Label rightChip;
        if (isOverdue) {
            rightChip = new Label("⚠ " + task.getDeadlineLabel());
            rightChip.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #DC2626;");
        } else if (isDone) {
            rightChip = new Label("✓ Selesai");
            rightChip.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #059669;");
        } else {
            rightChip = new Label("📅 " + task.getDeadlineLabel());
            rightChip.setStyle("-fx-font-size: 11px; -fx-text-fill: #64748B;");
        }
        footer.getChildren().addAll(avatar, fSpacer, rightChip);

        card.getChildren().addAll(topRow, titleLabel, descLabel, footer);

        // Click to open Detail Modal
        card.setOnMouseClicked(e -> {
            if (e.getClickCount() == 1) {
                showTaskDetailModal(task);
            }
        });

        // Drag and Drop
        boolean isKetua = SessionManager.getInstance().isKetua();
        boolean isMyTask = isMyTask(task);
        boolean canMove = isKetua || isMyTask;

        if (canMove) {
            card.setOnDragDetected(e -> {
                Dragboard db = card.startDragAndDrop(TransferMode.MOVE);
                ClipboardContent content = new ClipboardContent();
                content.putString(task.getId().toString() + ":" + task.getStatus());
                db.setContent(content);
                e.consume();
            });
            card.setStyle(card.getStyle() + "; -fx-cursor: hand;");
        }

        return card;
    }

    private void setupDragAndDrop() {
        setupColumnDropTarget(todoColumn, "TODO");
        setupColumnDropTarget(inProgressColumn, "IN_PROGRESS");
        setupColumnDropTarget(reviewColumn, "REVIEW");
        setupColumnDropTarget(doneColumn, "DONE");
    }

    private void setupColumnDropTarget(VBox column, String status) {
        column.setOnDragOver(e -> {
            if (e.getGestureSource() != column && e.getDragboard().hasString()) {
                e.acceptTransferModes(TransferMode.MOVE);
            }
            e.consume();
        });
        column.setOnDragDropped(e -> {
            Dragboard db = e.getDragboard();
            boolean success = false;
            if (db.hasString()) {
                String[] parts = db.getString().split(":");
                Long taskId = Long.parseLong(parts[0]);
                String oldStatus = parts[1];
                if (!oldStatus.equals(status)) {
                    TaskModel t = new TaskModel();
                    t.setId(taskId);
                    moveTask(t, status);
                }
                success = true;
            }
            e.setDropCompleted(success);
            e.consume();
        });
    }

    private void showTaskDetailModal(TaskModel task) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/task-detail-modal.fxml"));
            javafx.scene.Parent root = loader.load();
            TaskDetailModalController controller = loader.getController();
            controller.initWithTask(task, this);

            Stage stage = new Stage();
            stage.setTitle("Detail Task - " + task.getTitle());
            stage.initModality(Modality.APPLICATION_MODAL);
            var icon = getClass().getResource("/images/taskforge-icon-128.png");
            if (icon != null) stage.getIcons().add(new javafx.scene.image.Image(icon.toExternalForm()));
            stage.setScene(new Scene(root, 600, 700));
            stage.show();
        } catch (Exception e) {
            statusLabel.setText("Gagal membuka detail task: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private boolean isMyTask(TaskModel task) {
        UserModel me = SessionManager.getInstance().getCurrentUser();
        return task.getAssignee() != null && task.getAssignee().getId().equals(me.getId());
    }

    private void moveTask(TaskModel task, String newStatus) {
        Task<Void> updateTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                Map<String, String> body = Map.of("status", newStatus);
                ApiResponse<Object> response = ApiClient.put(
                        "/api/tasks/" + task.getId() + "/status", body, Object.class);
                if (!response.isSuccess()) throw new Exception(response.getMessage());
                return null;
            }
        };

        updateTask.setOnSucceeded(e -> Platform.runLater(this::loadTasks));
        updateTask.setOnFailed(e -> Platform.runLater(() -> {
            statusLabel.setText("Gagal memindahkan: " + updateTask.getException().getMessage());
            // Show alert for the validation exception (e.g. missing file)
            Dialogs.error("TaskForge", "Gagal Memindahkan Task",
                    updateTask.getException().getMessage());
            loadTasks(); // refresh to snap back
        }));

        Thread t = new Thread(updateTask);
        t.setDaemon(true);
        t.start();
    }

    @FXML
    public void handleAddTask() {
        if (!SessionManager.getInstance().isKetua()) return;

        // Load project members for assignee dropdown
        Task<List<UserModel>> loadMembers = new Task<>() {
            @Override
            protected List<UserModel> call() throws Exception {
                ApiResponse<Object> raw = ApiClient.get(
                        "/api/projects/" + currentProject.getId(), Object.class);
                String json = MAPPER.writeValueAsString(raw.getData());
                ProjectModel detail = MAPPER.readValue(json, ProjectModel.class);
                // combine owner + members
                List<UserModel> all = new java.util.ArrayList<>();
                if (detail.getOwner() != null) all.add(detail.getOwner());
                if (detail.getMembers() != null) all.addAll(detail.getMembers());
                return all;
            }
        };

        loadMembers.setOnSucceeded(e -> Platform.runLater(
                () -> showCreateTaskDialog(loadMembers.getValue())));
        loadMembers.setOnFailed(e -> Platform.runLater(
                () -> showCreateTaskDialog(List.of())));

        Thread t = new Thread(loadMembers);
        t.setDaemon(true);
        t.start();
    }

    private void showCreateTaskDialog(List<UserModel> members) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Buat Task Baru");
        dialog.setHeaderText("Isi detail task");

        TextField titleField = new TextField();
        titleField.setPromptText("Judul task");

        ComboBox<String> priorityBox = new ComboBox<>();
        priorityBox.getItems().addAll("LOW", "MEDIUM", "HIGH");
        priorityBox.setValue("MEDIUM");

        ComboBox<String> typeBox = new ComboBox<>();
        typeBox.getItems().addAll("SIMPLE", "MILESTONE");
        typeBox.setValue("SIMPLE");

        DatePicker deadlinePicker = new DatePicker();
        deadlinePicker.setValue(java.time.LocalDate.now().plusDays(7));

        ComboBox<UserModel> assigneeBox = new ComboBox<>();
        assigneeBox.getItems().addAll(members);
        assigneeBox.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(UserModel u) { return u == null ? "" : u.getName() + " (" + u.getRole() + ")"; }
            @Override public UserModel fromString(String s) { return null; }
        });
        if (!members.isEmpty()) assigneeBox.setValue(members.get(0));

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(10));
        grid.addRow(0, new Label("Judul:"), titleField);
        grid.addRow(1, new Label("Prioritas:"), priorityBox);
        grid.addRow(2, new Label("Tipe:"), typeBox);
        grid.addRow(3, new Label("Deadline:"), deadlinePicker);
        grid.addRow(4, new Label("Assignee:"), assigneeBox);

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        Dialogs.style(dialog);

        dialog.showAndWait().ifPresent(result -> {
            if (result == ButtonType.OK
                    && !titleField.getText().isBlank()
                    && assigneeBox.getValue() != null
                    && deadlinePicker.getValue() != null) {

                Map<String, Object> body = new java.util.HashMap<>();
                body.put("title", titleField.getText().trim());
                body.put("priority", priorityBox.getValue());
                body.put("taskType", typeBox.getValue());
                body.put("assigneeId", assigneeBox.getValue().getId());
                body.put("deadline", deadlinePicker.getValue().atTime(23, 59, 0).toString());

                Task<Void> createTask = new Task<>() {
                    @Override
                    protected Void call() throws Exception {
                        ApiResponse<Object> response = ApiClient.post(
                                "/api/projects/" + currentProject.getId() + "/tasks", body, Object.class);
                        if (!response.isSuccess()) throw new Exception(response.getMessage());
                        return null;
                    }
                };
                createTask.setOnSucceeded(e -> Platform.runLater(this::loadTasks));
                createTask.setOnFailed(e -> Platform.runLater(
                        () -> statusLabel.setText("Gagal: " + createTask.getException().getMessage())));
                Thread t = new Thread(createTask);
                t.setDaemon(true);
                t.start();
            }
        });
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

    @FXML
    public void handleBack() {
        if (currentProject == null) {
            navigateToDashboard();
            return;
        }
        try {
            Stage stage = (Stage) projectTitleLabel.getScene().getWindow();
            ProjectDetailController controller = SceneNavigator.navigate(
                    stage, "/fxml/project-detail.fxml",
                    "TaskForge — " + currentProject.getTitle(), 1100, 700);
            controller.initWithProject(currentProject);
        } catch (Exception e) {
            statusLabel.setText("Gagal kembali ke detail proyek");
        }
    }

    private void navigateToDashboard() {
        navigate("/fxml/dashboard.fxml", "TaskForge — Dashboard");
    }

    // ─── Sidebar navigation (mengikuti dashboard) ────────────────────────────

    @FXML
    public void handleDashboard() {
        navigate("/fxml/dashboard.fxml", "TaskForge — Dashboard");
    }

    @FXML
    public void handleProyek() {
        navigate("/fxml/proyek.fxml", "TaskForge — Proyek");
    }

    @FXML
    public void handleNotifikasi() {
        navigate("/fxml/notifikasi.fxml", "TaskForge — Notifikasi");
    }

    @FXML
    public void handleProfil() {
        navigate("/fxml/profil.fxml", "TaskForge — Profil");
    }

    @FXML
    public void handleLogout() {
        SidebarProfileBinder.logout(userNameLabel);
    }

    private void navigate(String fxml, String title) {
        try {
            Stage stage = (Stage) projectTitleLabel.getScene().getWindow();
            SceneNavigator.navigate(stage, fxml, title, 1100, 700);
        } catch (Exception e) {
            statusLabel.setText("Gagal navigasi: " + e.getMessage());
        }
    }
}
