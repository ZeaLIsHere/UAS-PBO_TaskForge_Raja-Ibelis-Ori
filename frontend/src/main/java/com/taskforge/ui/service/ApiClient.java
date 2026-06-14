package com.taskforge.ui.service;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.taskforge.ui.model.ApiResponse;
import com.taskforge.ui.session.SessionManager;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class ApiClient {

    private static final String BASE_URL = "http://localhost:8080";
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    private ApiClient() {}

    public static <T> ApiResponse<T> post(String path, Object body, Class<T> responseType) throws Exception {
        String json = MAPPER.writeValueAsString(body);
        HttpRequest request = buildRequest(path)
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        return execute(request, responseType);
    }

    public static <T> ApiResponse<T> get(String path, Class<T> responseType) throws Exception {
        HttpRequest request = buildRequest(path)
                .GET()
                .build();
        return execute(request, responseType);
    }

    public static <T> ApiResponse<T> put(String path, Object body, Class<T> responseType) throws Exception {
        String json = MAPPER.writeValueAsString(body);
        HttpRequest request = buildRequest(path)
                .PUT(HttpRequest.BodyPublishers.ofString(json))
                .build();
        return execute(request, responseType);
    }

    public static <T> ApiResponse<T> delete(String path, Class<T> responseType) throws Exception {
        HttpRequest request = buildRequest(path)
                .DELETE()
                .build();
        return execute(request, responseType);
    }

    public static <T> ApiResponse<T> postMultipart(String path, java.io.File file, Class<T> responseType) throws Exception {
        String boundary = "----Boundary" + System.currentTimeMillis();
        byte[] fileBytes = java.nio.file.Files.readAllBytes(file.toPath());
        String fileName = file.getName();
        String contentType = fileName.toLowerCase().endsWith(".png") ? "image/png" : "image/jpeg";

        byte[] body = buildMultipartBody(boundary, "file", fileName, contentType, fileBytes);

        String token = SessionManager.getInstance().getToken();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + path))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .header("Accept", "application/json")
                .header("Authorization", token != null ? "Bearer " + token : "")
                .timeout(Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();

        HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        JavaType type = MAPPER.getTypeFactory().constructParametricType(ApiResponse.class, responseType);
        try {
            return MAPPER.readValue(response.body(), type);
        } catch (Exception e) {
            throw new Exception("Gagal upload foto (HTTP " + response.statusCode() + ")");
        }
    }

    /** Fetch raw bytes dari endpoint yang membutuhkan autentikasi (misalnya: foto profil). */
    public static byte[] getBytes(String path) throws Exception {
        String token = SessionManager.getInstance().getToken();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + path))
                .header("Accept", "image/*, application/octet-stream")
                .header("Authorization", token != null ? "Bearer " + token : "")
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();
        HttpResponse<byte[]> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() >= 400) {
            throw new Exception("HTTP " + response.statusCode() + " saat mengambil foto");
        }
        return response.body();
    }

    private static byte[] buildMultipartBody(String boundary, String fieldName,
                                              String fileName, String contentType, byte[] data) throws Exception {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        String header = "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"" + fieldName + "\"; filename=\"" + fileName + "\"\r\n"
                + "Content-Type: " + contentType + "\r\n\r\n";
        out.write(header.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        out.write(data);
        out.write(("\r\n--" + boundary + "--\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return out.toByteArray();
    }

    private static HttpRequest.Builder buildRequest(String path) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + path))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(30));

        String token = SessionManager.getInstance().getToken();
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }

        return builder;
    }

    private static <T> ApiResponse<T> execute(HttpRequest request, Class<T> responseType) throws Exception {
        HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        JavaType type = MAPPER.getTypeFactory()
                .constructParametricType(ApiResponse.class, responseType);
        try {
            return MAPPER.readValue(response.body(), type);
        } catch (Exception e) {
            String body = response.body();
            String preview = body != null ? body.substring(0, Math.min(200, body.length())) : "";
            throw new Exception("Respons tidak valid dari server (HTTP " + response.statusCode() + "): " + preview);
        }
    }
}
