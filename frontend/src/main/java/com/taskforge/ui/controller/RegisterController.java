package com.taskforge.ui.controller;

import com.taskforge.ui.model.ApiResponse;
import com.taskforge.ui.model.UserModel;
import com.taskforge.ui.service.ApiClient;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import com.taskforge.ui.util.Dialogs;
import com.taskforge.ui.util.SceneNavigator;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.HashMap;
import java.util.Map;

public class RegisterController {

    // Pilihan role: display name → nilai enum backend
    private static final Map<String, String> ROLE_MAP = Map.of(
            "Ketua Kelompok",  "KETUA",
            "Anggota Kelompok","ANGGOTA",
            "Dosen",           "DOSEN",
            "Asisten Dosen",   "ASDOS"
    );

    @FXML private TextField     nameField;
    @FXML private TextField     emailField;
    @FXML private PasswordField passwordField;
    @FXML private PasswordField confirmPasswordField;
    @FXML private ComboBox<String> roleBox;
    @FXML private Button        registerButton;
    @FXML private Label         errorLabel;
    @FXML private ProgressIndicator loadingIndicator;

    // Container & field NIM (mahasiswa)
    @FXML private VBox      nimBox;
    @FXML private TextField nimField;

    // Container & field NIPM (dosen)
    @FXML private VBox      nipmBox;
    @FXML private TextField nipmField;

    @FXML
    public void initialize() {
        roleBox.getItems().addAll(ROLE_MAP.keySet().stream().sorted((a, b) -> {
            // Urutan tetap: Ketua, Anggota, Asisten Dosen, Dosen
            String[] order = {"Ketua Kelompok", "Anggota Kelompok", "Asisten Dosen", "Dosen"};
            int ai = 99, bi = 99;
            for (int i = 0; i < order.length; i++) {
                if (order[i].equals(a)) ai = i;
                if (order[i].equals(b)) bi = i;
            }
            return Integer.compare(ai, bi);
        }).toList());

        roleBox.setValue("Anggota Kelompok");
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
        loadingIndicator.setVisible(false);
        loadingIndicator.setManaged(false);

        // Listener role: toggle NIM ↔ NIPM
        roleBox.valueProperty().addListener((obs, oldVal, newVal) -> updateIdField(newVal));
        updateIdField("Anggota Kelompok");
    }

    private void updateIdField(String displayRole) {
        boolean isDosen = "Dosen".equals(displayRole);
        nimBox.setVisible(!isDosen);
        nimBox.setManaged(!isDosen);
        nipmBox.setVisible(isDosen);
        nipmBox.setManaged(isDosen);
    }

    @FXML
    public void handleRegister() {
        String name    = nameField.getText().trim();
        String email   = emailField.getText().trim();
        String password = passwordField.getText();
        String confirm  = confirmPasswordField.getText();
        String displayRole = roleBox.getValue();

        if (name.isEmpty() || email.isEmpty() || password.isEmpty()) {
            showError("Semua field wajib diisi");
            return;
        }
        if (password.length() < 8) {
            showError("Password minimal 8 karakter");
            return;
        }
        if (!password.equals(confirm)) {
            showError("Password dan konfirmasi tidak cocok");
            return;
        }

        boolean isDosen = "Dosen".equals(displayRole);
        String idNumber = isDosen
                ? nipmField.getText().trim()
                : nimField.getText().trim();
        if (idNumber.isEmpty()) {
            showError(isDosen ? "NIPM wajib diisi" : "NIM wajib diisi");
            return;
        }

        String roleValue = ROLE_MAP.getOrDefault(displayRole, "ANGGOTA");

        setLoading(true);

        Map<String, String> body = new HashMap<>();
        body.put("name",     name);
        body.put("email",    email);
        body.put("password", password);
        body.put("role",     roleValue);
        if (isDosen) {
            body.put("nipm", idNumber);
        } else {
            body.put("nim", idNumber);
        }

        Task<ApiResponse<UserModel>> task = new Task<>() {
            @Override
            protected ApiResponse<UserModel> call() throws Exception {
                return ApiClient.post("/api/auth/register", body, UserModel.class);
            }
        };

        task.setOnSucceeded(e -> {
            setLoading(false);
            ApiResponse<UserModel> response = task.getValue();
            if (response.isSuccess()) {
                showSuccessAndGoLogin();
            } else {
                showError(response.getMessage() != null ? response.getMessage() : "Registrasi gagal");
            }
        });

        task.setOnFailed(e -> {
            setLoading(false);
            showError("Tidak dapat terhubung ke server. Pastikan backend berjalan.");
        });

        Thread t = new Thread(task);
        t.setDaemon(true);
        t.start();
    }

    private void showSuccessAndGoLogin() {
        Platform.runLater(() -> {
            Dialogs.info("TaskForge", "Registrasi Berhasil 🎉",
                    "Akun kamu berhasil dibuat! Silakan login untuk mulai menggunakan TaskForge.");
            goToLogin();
        });
    }

    @FXML
    public void handleBackToLogin() {
        goToLogin();
    }

    private void goToLogin() {
        try {
            Stage stage = (Stage) registerButton.getScene().getWindow();
            SceneNavigator.navigate(stage, "/fxml/login.fxml", "TaskForge — Login", 480, 660, false);
        } catch (Exception ex) {
            showError("Gagal kembali ke halaman login");
        }
    }

    private void showError(String msg) {
        Platform.runLater(() -> {
            errorLabel.setText(msg);
            errorLabel.setVisible(true);
            errorLabel.setManaged(true);
        });
    }

    private void setLoading(boolean loading) {
        Platform.runLater(() -> {
            registerButton.setDisable(loading);
            loadingIndicator.setVisible(loading);
            loadingIndicator.setManaged(loading);
            if (loading) {
                errorLabel.setVisible(false);
                errorLabel.setManaged(false);
            }
        });
    }
}
