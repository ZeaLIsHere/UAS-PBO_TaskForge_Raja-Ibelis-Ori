package com.taskforge.ui.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.taskforge.ui.model.ApiResponse;
import com.taskforge.ui.model.ProjectModel;
import com.taskforge.ui.service.ApiClient;
import com.taskforge.ui.session.SessionManager;
import com.taskforge.ui.util.SceneNavigator;
import com.taskforge.ui.util.SidebarProfileBinder;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

public class DashboardController {

    @FXML private Label userNameLabel;
    @FXML private Label userNimLabel;
    @FXML private Label userRoleLabel;
    @FXML private FlowPane projectsPane;
    @FXML private ProgressIndicator loadingIndicator;
    @FXML private Label statusLabel;
    @FXML private Label statTotalProyek;
    @FXML private Label statOverdue;
    @FXML private Label statSelesai;
    @FXML private TextField searchField;
    @FXML private Label avatarInitials;
    @FXML private Label pageTitle;
    @FXML private Label pageSubtitle;
    @FXML private Label sectionTitle;
    @FXML private Label sectionSubtitle;
    @FXML private Button primaryActionBtn;

    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd MMM yyyy");

    private List<ProjectModel> allProjects = List.of();

    @FXML
    public void initialize() {
        loadingIndicator.setVisible(false);
        statusLabel.setText("");
        refreshSidebarProfile();
        applyRoleView();
        loadProjects();
    }

    // ─── Role-adaptive view ──────────────────────────────────────────────────
    // Satu dashboard, tampilan berbeda sesuai role (DOSEN/ASDOS/KETUA/ANGGOTA).

    private void applyRoleView() {
        SessionManager session = SessionManager.getInstance();
        String name = session.getCurrentUser() != null
                ? session.getCurrentUser().getName().split("\\s+")[0] : "";

        if (session.isDosen()) {
            pageTitle.setText("Panel Dosen");
            pageSubtitle.setText("Pantau seluruh kelompok dan input nilai akhir mahasiswa");
            sectionTitle.setText("Semua Proyek Akademik");
            sectionSubtitle.setText("Klik proyek untuk mengawasi & menilai kelompok");
            showPrimaryAction("+ Buat Proyek");
        } else if (session.isAsdos()) {
            pageTitle.setText("Panel Asisten Dosen");
            pageSubtitle.setText("Pantau progres dan berkas semua kelompok bimbingan");
            sectionTitle.setText("Semua Kelompok");
            sectionSubtitle.setText("Klik proyek untuk meninjau progres & artefak");
            hidePrimaryAction();
        } else if (session.isKetua()) {
            pageTitle.setText("Dashboard Ketua");
            pageSubtitle.setText("Halo " + name + ", kelola proyek dan tim kelompokmu");
            sectionTitle.setText("Proyek Saya");
            sectionSubtitle.setText("Klik proyek untuk mengelola tugas & anggota");
            showPrimaryAction("+ Claim / Buat Proyek");
        } else { // ANGGOTA
            pageTitle.setText("Dashboard");
            pageSubtitle.setText("Halo " + name + ", berikut tugas dan proyek yang kamu ikuti");
            sectionTitle.setText("Proyek Saya");
            sectionSubtitle.setText("Klik proyek untuk melihat tugas yang ditugaskan padamu");
            hidePrimaryAction();
        }
    }

    private void showPrimaryAction(String text) {
        primaryActionBtn.setText(text);
        primaryActionBtn.setVisible(true);
        primaryActionBtn.setManaged(true);
    }

    private void hidePrimaryAction() {
        primaryActionBtn.setVisible(false);
        primaryActionBtn.setManaged(false);
    }

    @FXML
    public void handlePrimaryAction() {
        // DOSEN membuat proyek, KETUA claim/buat — keduanya menuju halaman Proyek
        navigate("/fxml/proyek.fxml", "TaskForge — Proyek");
    }

    // ─── Sidebar / Profile ───────────────────────────────────────────────────

    private void refreshSidebarProfile() {
        SidebarProfileBinder.refresh(
                userNameLabel, userNimLabel, userRoleLabel, avatarInitials
        );
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

    // ─── Projects ────────────────────────────────────────────────────────────

    private void loadProjects() {
        loadingIndicator.setVisible(true);
        statusLabel.setText("");

        Task<List<ProjectModel>> fetchTask = new Task<>() {
            @Override
            protected List<ProjectModel> call() throws Exception {
                ApiResponse<Object> raw = ApiClient.get("/api/projects", Object.class);
                if (!raw.isSuccess()) throw new Exception(raw.getMessage());
                return MAPPER.convertValue(raw.getData(), new TypeReference<List<ProjectModel>>() {});
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
        // Tentukan status proyek → warna & label badge
        int taskCount = project.getTaskCount();
        int completedCount = project.getCompletedTaskCount();
        boolean isOverdue = project.getOverdueTaskCount() > 0;
        boolean isDone = taskCount > 0 && completedCount == taskCount;

        String statusText, badgeBg, badgeFg, accent;
        if (isOverdue) {
            statusText = "Terlambat";   badgeBg = "#FEE2E2"; badgeFg = "#DC2626"; accent = "#EF4444";
        } else if (isDone) {
            statusText = "Selesai";     badgeBg = "#D1FAE5"; badgeFg = "#059669"; accent = "#10B981";
        } else {
            statusText = "Sedang Berjalan"; badgeBg = "#FEF3C7"; badgeFg = "#92400E"; accent = "#4F46E5";
        }

        String baseStyle =
            "-fx-background-color: white; -fx-background-radius: 16px; " +
            "-fx-border-color: #E2E8F0; -fx-border-radius: 16px; -fx-border-width: 1; " +
            "-fx-effect: dropshadow(gaussian, rgba(15,23,42,0.05), 12, 0, 0, 4); -fx-cursor: hand;";
        String hoverStyle =
            "-fx-background-color: white; -fx-background-radius: 16px; " +
            "-fx-border-color: " + accent + "; -fx-border-radius: 16px; -fx-border-width: 1; " +
            "-fx-effect: dropshadow(gaussian, rgba(79,70,229,0.16), 18, 0, 0, 6); -fx-cursor: hand;";

        VBox card = new VBox(12);
        card.setPrefWidth(360);
        card.setMaxWidth(360);
        card.setPadding(new Insets(20, 22, 18, 22));
        card.setStyle(baseStyle);

        // Status badge
        Label badge = new Label(statusText.toUpperCase());
        badge.setStyle(
            "-fx-background-color: " + badgeBg + "; -fx-text-fill: " + badgeFg + "; " +
            "-fx-font-size: 10px; -fx-font-weight: bold; -fx-background-radius: 20; -fx-padding: 4 10 4 10;");

        Label title = new Label(project.getTitle());
        title.setWrapText(true);
        title.setMaxWidth(316);
        title.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #191C1E;");

        String descText = project.getDescription() != null && !project.getDescription().isBlank()
                ? project.getDescription() : "Tidak ada deskripsi";
        Label desc = new Label(descText);
        desc.setWrapText(true);
        desc.setMaxWidth(316);
        desc.setMaxHeight(40);
        desc.setStyle("-fx-font-size: 13px; -fx-text-fill: #64748B;");

        // Progress
        double progressVal = taskCount == 0 ? 0 : (double) completedCount / taskCount;
        int progressPct = (int) Math.round(progressVal * 100);
        HBox progressHeader = new HBox();
        progressHeader.setAlignment(Pos.CENTER_LEFT);
        Label progLabel = new Label("Progress");
        progLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #94A3B8;");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Label pctLabel = new Label(progressPct + "%");
        pctLabel.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: " + accent + ";");
        progressHeader.getChildren().addAll(progLabel, spacer, pctLabel);

        ProgressBar progressBar = new ProgressBar(progressVal);
        progressBar.setMaxWidth(Double.MAX_VALUE);
        progressBar.setPrefHeight(8);
        progressBar.setStyle("-fx-accent: " + accent + ";");

        // Footer: anggota + deadline
        HBox footer = new HBox(10);
        footer.setAlignment(Pos.CENTER_LEFT);
        Label members = new Label("👥 " + project.getMemberCount() + " anggota");
        members.setStyle("-fx-font-size: 12px; -fx-text-fill: #64748B;");
        Region fSpacer = new Region();
        HBox.setHgrow(fSpacer, Priority.ALWAYS);
        String deadlineText = project.getDeadline() != null
                ? "📅 " + project.getDeadline().format(FMT) : "📅 Tanpa deadline";
        Label deadline = new Label(deadlineText);
        deadline.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: "
                + (isOverdue ? "#DC2626" : "#64748B") + ";");
        footer.getChildren().addAll(members, fSpacer, deadline);

        if (project.isHasCover()) {
            card.getChildren().add(com.taskforge.ui.util.CoverLoader.coverNode(project.getId(), 316, 120, 12));
        }
        card.getChildren().addAll(badge, title, desc, progressHeader, progressBar, footer);
        card.setOnMouseClicked(e -> openProjectDetail(project));
        card.setOnMouseEntered(e -> card.setStyle(hoverStyle));
        card.setOnMouseExited(e -> card.setStyle(baseStyle));

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
