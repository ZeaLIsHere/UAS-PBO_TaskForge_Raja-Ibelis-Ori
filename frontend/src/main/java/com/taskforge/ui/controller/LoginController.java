package com.taskforge.ui.controller;

import com.taskforge.ui.model.ApiResponse;
import com.taskforge.ui.model.AuthResponse;
import com.taskforge.ui.service.ApiClient;
import com.taskforge.ui.util.SceneNavigator;
import com.taskforge.ui.session.SessionManager;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;

import java.util.Map;

public class LoginController {

    @FXML private TextField emailField;
    @FXML private PasswordField passwordField;
    @FXML private Button loginButton;
    @FXML private Label errorLabel;
    @FXML private ProgressIndicator loadingIndicator;

    @FXML
    public void initialize() {
        errorLabel.setVisible(false);
        loadingIndicator.setVisible(false);
    }

    @FXML
    public void handleLogin() {
        String email = emailField.getText().trim();
        String password = passwordField.getText();

        if (email.isEmpty() || password.isEmpty()) {
            showError("Email dan password tidak boleh kosong");
            return;
        }

        setLoading(true);

        // All HTTP calls run off the UI thread via Task<T>
        Task<ApiResponse<AuthResponse>> loginTask = new Task<>() {
            @Override
            protected ApiResponse<AuthResponse> call() throws Exception {
                Map<String, String> body = Map.of("email", email, "password", password);
                return ApiClient.post("/api/auth/login", body, AuthResponse.class);
            }
        };

        loginTask.setOnSucceeded(event -> {
            setLoading(false);
            ApiResponse<AuthResponse> response = loginTask.getValue();
            if (response.isSuccess() && response.getData() != null) {
                AuthResponse auth = response.getData();
                SessionManager.getInstance().setSession(auth.getToken(), auth.getUser());
                navigateToDashboard();
            } else {
                showError(response.getMessage() != null ? response.getMessage() : "Login gagal");
            }
        });

        loginTask.setOnFailed(event -> {
            setLoading(false);
            showError("Tidak dapat terhubung ke server. Pastikan backend berjalan.");
        });

        Thread thread = new Thread(loginTask);
        thread.setDaemon(true);
        thread.start();
    }

    @FXML
    public void handleRegisterLink() {
        try {
            Stage stage = (Stage) loginButton.getScene().getWindow();
            SceneNavigator.navigate(stage, "/fxml/register.fxml", "TaskForge — Daftar Akun", 420, 520);
        } catch (Exception e) {
            showError("Gagal membuka halaman registrasi");
        }
    }

    private void navigateToDashboard() {
        try {
            Stage stage = (Stage) loginButton.getScene().getWindow();
            SceneNavigator.navigate(stage, "/fxml/dashboard.fxml", "TaskForge — Dashboard", 1100, 700, false);
        } catch (Exception e) {
            showError("Gagal membuka dashboard: " + e.getMessage());
        }
    }

    private void showError(String message) {
        Platform.runLater(() -> {
            errorLabel.setText(message);
            errorLabel.setVisible(true);
        });
    }

    private void setLoading(boolean loading) {
        Platform.runLater(() -> {
            loginButton.setDisable(loading);
            loadingIndicator.setVisible(loading);
            if (loading) errorLabel.setVisible(false);
        });
    }
}
