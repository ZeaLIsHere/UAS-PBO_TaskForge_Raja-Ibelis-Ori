package com.taskforge.ui.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.taskforge.ui.model.ApiResponse;
import com.taskforge.ui.model.ProfileUpdateModel;
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
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.stage.Stage;

public class ProfilController {

    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    @FXML private Label sidebarNameLabel;
    @FXML private Label sidebarNimLabel;
    @FXML private Label sidebarRoleLabel;
    @FXML private Label avatarInitials;
    @FXML private Label cardInitials;
    @FXML private Label cardNameLabel;
    @FXML private Label cardRoleLabel;
    @FXML private Label cardNimLabel;
    @FXML private TextField nameField;
    @FXML private TextField emailField;
    @FXML private Label statusLabel;
    @FXML private ProgressIndicator loadingIndicator;

    @FXML
    public void initialize() {
        loadingIndicator.setVisible(false);
        clearStatus();
        refreshSidebarProfile();
        loadProfile();
    }

    private void refreshSidebarProfile() {
        SidebarProfileBinder.refresh(
                sidebarNameLabel, sidebarNimLabel, sidebarRoleLabel, avatarInitials
        );
    }

    private void loadProfile() {
        loadingIndicator.setVisible(true);
        clearStatus();

        Task<UserModel> fetchTask = new Task<>() {
            @Override
            protected UserModel call() throws Exception {
                ApiResponse<Object> resp = ApiClient.get("/api/users/me", Object.class);
                if (!resp.isSuccess()) throw new Exception(resp.getMessage());
                return MAPPER.convertValue(resp.getData(), UserModel.class);
            }
        };

        fetchTask.setOnSucceeded(e -> {
            loadingIndicator.setVisible(false);
            applyUserToForm(fetchTask.getValue());
        });

        fetchTask.setOnFailed(e -> {
            loadingIndicator.setVisible(false);
            showError("Gagal memuat profil: " + fetchTask.getException().getMessage());
        });

        Thread t = new Thread(fetchTask);
        t.setDaemon(true);
        t.start();
    }

    private void applyUserToForm(UserModel user) {
        if (user == null) return;

        nameField.setText(user.getName() != null ? user.getName() : "");
        emailField.setText(user.getEmail() != null ? user.getEmail() : "");

        cardNameLabel.setText(user.getName());
        cardRoleLabel.setText(formatRole(user.getRole()));
        cardInitials.setText(SidebarProfileBinder.getInitials(user.getName()));

        if (user.getNim() != null && !user.getNim().isBlank()) {
            cardNimLabel.setText("NIM: " + user.getNim());
            cardNimLabel.setVisible(true);
            cardNimLabel.setManaged(true);
        } else {
            cardNimLabel.setText("");
            cardNimLabel.setVisible(false);
            cardNimLabel.setManaged(false);
        }
    }

    @FXML
    public void handleSaveProfile() {
        String name = nameField.getText().trim();
        String email = emailField.getText().trim();

        if (name.isBlank()) {
            showError("Nama lengkap tidak boleh kosong.");
            return;
        }
        if (email.isBlank()) {
            showError("Email tidak boleh kosong.");
            return;
        }
        if (!email.contains("@")) {
            showError("Format email tidak valid.");
            return;
        }

        loadingIndicator.setVisible(true);
        clearStatus();

        UserModel current = SessionManager.getInstance().getCurrentUser();

        Task<ProfileUpdateModel> saveTask = new Task<>() {
            @Override
            protected ProfileUpdateModel call() throws Exception {
                var body = new java.util.HashMap<String, Object>();
                body.put("name", name);
                body.put("email", email);
                body.put("nim", current.getNim());
                ApiResponse<Object> resp = ApiClient.put("/api/users/me", body, Object.class);
                if (!resp.isSuccess()) throw new Exception(resp.getMessage());
                return MAPPER.convertValue(resp.getData(), ProfileUpdateModel.class);
            }
        };

        saveTask.setOnSucceeded(e -> {
            loadingIndicator.setVisible(false);
            ProfileUpdateModel result = saveTask.getValue();
            UserModel updated = result.getUser();
            String token = result.getToken() != null
                    ? result.getToken()
                    : SessionManager.getInstance().getToken();
            SessionManager.getInstance().setSession(token, updated);
            refreshSidebarProfile();
            applyUserToForm(updated);
            showSuccess("Profil berhasil diperbarui.");
        });

        saveTask.setOnFailed(e -> Platform.runLater(() -> {
            loadingIndicator.setVisible(false);
            showError("Gagal menyimpan profil: " + saveTask.getException().getMessage());
        }));

        Thread t = new Thread(saveTask);
        t.setDaemon(true);
        t.start();
    }

    @FXML
    public void handleChangePassword() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Ubah Password");
        dialog.setHeaderText("Masukkan password lama dan password baru");

        PasswordField currentField = new PasswordField();
        currentField.setPromptText("Password lama");
        PasswordField newField = new PasswordField();
        newField.setPromptText("Password baru (min. 8 karakter)");
        PasswordField confirmField = new PasswordField();
        confirmField.setPromptText("Konfirmasi password baru");

        for (PasswordField field : new PasswordField[]{currentField, newField, confirmField}) {
            field.setStyle("-fx-pref-height: 36px; -fx-font-size: 13px;");
        }

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(12);
        grid.setPadding(new Insets(16));
        grid.setMinWidth(380);

        Label l1 = label("Password Lama:");
        Label l2 = label("Password Baru:");
        Label l3 = label("Konfirmasi:");
        GridPane.setHgrow(currentField, Priority.ALWAYS);
        GridPane.setHgrow(newField, Priority.ALWAYS);
        GridPane.setHgrow(confirmField, Priority.ALWAYS);
        grid.addRow(0, l1, currentField);
        grid.addRow(1, l2, newField);
        grid.addRow(2, l3, confirmField);

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.getDialogPane().setPrefWidth(440);
        Dialogs.style(dialog);

        dialog.showAndWait().ifPresent(result -> {
            if (result != ButtonType.OK) return;

            String current = currentField.getText();
            String newPass = newField.getText();
            String confirm = confirmField.getText();

            if (current.isBlank() || newPass.isBlank() || confirm.isBlank()) {
                showError("Semua field password wajib diisi.");
                return;
            }
            if (newPass.length() < 8) {
                showError("Password baru minimal 8 karakter.");
                return;
            }
            if (!newPass.equals(confirm)) {
                showError("Konfirmasi password tidak cocok.");
                return;
            }
            submitPasswordChange(current, newPass);
        });
    }

    private void submitPasswordChange(String currentPassword, String newPassword) {
        loadingIndicator.setVisible(true);
        clearStatus();

        Task<Void> changeTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                var body = new java.util.HashMap<String, String>();
                body.put("currentPassword", currentPassword);
                body.put("newPassword", newPassword);
                ApiResponse<Object> resp = ApiClient.put("/api/users/me/password", body, Object.class);
                if (!resp.isSuccess()) throw new Exception(resp.getMessage());
                return null;
            }
        };

        changeTask.setOnSucceeded(e -> Platform.runLater(() -> {
            loadingIndicator.setVisible(false);
            showSuccess("Password berhasil diperbarui.");
        }));

        changeTask.setOnFailed(e -> Platform.runLater(() -> {
            loadingIndicator.setVisible(false);
            showError("Gagal ubah password: " + changeTask.getException().getMessage());
        }));

        Thread t = new Thread(changeTask);
        t.setDaemon(true);
        t.start();
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
    public void handleNotifikasi() {
        navigate("/fxml/notifikasi.fxml", "TaskForge — Notifikasi");
    }

    @FXML
    public void handleLogout() {
        SidebarProfileBinder.logout(sidebarNameLabel);
    }

    private void navigate(String fxml, String title) {
        try {
            Stage stage = (Stage) sidebarNameLabel.getScene().getWindow();
            SceneNavigator.navigate(stage, fxml, title, 1100, 700);
        } catch (Exception e) {
            showError("Gagal navigasi: " + e.getMessage());
        }
    }

    private Label label(String text) {
        Label l = new Label(text);
        l.setStyle("-fx-font-weight: bold; -fx-font-size: 12px;");
        return l;
    }

    private String formatRole(String role) {
        if (role == null) return "ANGGOTA";
        return "KETUA".equalsIgnoreCase(role) ? "KETUA KELOMPOK" : role;
    }

    private void showError(String message) {
        statusLabel.setText(message);
        statusLabel.setStyle("-fx-padding: 0 28 20 28; -fx-font-size: 13px; -fx-text-fill: #EF4444;");
    }

    private void showSuccess(String message) {
        statusLabel.setText(message);
        statusLabel.setStyle("-fx-padding: 0 28 20 28; -fx-font-size: 13px; -fx-text-fill: #059669;");
    }

    private void clearStatus() {
        statusLabel.setText("");
        statusLabel.setStyle("-fx-padding: 0 28 20 28; -fx-font-size: 13px;");
    }
}
