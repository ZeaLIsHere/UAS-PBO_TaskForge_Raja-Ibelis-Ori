package com.taskforge.service;

import com.taskforge.dto.request.UpdateProfileRequest;
import com.taskforge.dto.response.UserResponse;
import com.taskforge.exception.ResourceNotFoundException;
import com.taskforge.exception.ValidationException;
import com.taskforge.model.User;
import com.taskforge.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    @Value("${file.photo-dir}")
    private String photoDir;

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(".jpg", ".jpeg", ".png");
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/png");

    public UserResponse getProfile(String email) {
        return UserResponse.from(findByEmail(email));
    }

    @Transactional
    public UserResponse updateProfile(String email, UpdateProfileRequest request) {
        User user = findByEmail(email);
        user.setName(request.getName().trim());
        user.setNim(request.getNim() != null && !request.getNim().isBlank()
                ? request.getNim().trim() : null);
        User saved = userRepository.save(user);
        log.info("Profil user {} diperbarui", saved.getEmail());
        return UserResponse.from(saved);
    }

    @Transactional
    public UserResponse uploadPhoto(String email, MultipartFile file) throws IOException {
        validateImageFile(file);

        User user = findByEmail(email);

        Path dir = Paths.get(photoDir).toAbsolutePath().normalize();
        Files.createDirectories(dir);

        String ext = extractExtension(file.getOriginalFilename());
        String fileName = "user-" + user.getId() + ext;
        Path target = dir.resolve(fileName).normalize();

        // Cegah path traversal
        if (!target.startsWith(dir)) {
            throw new ValidationException("Nama file tidak valid");
        }

        Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);

        // Simpan nama file saja — bukan absolute path
        user.setPhotoPath(fileName);
        User saved = userRepository.save(user);
        log.info("Foto profil user {} diperbarui", saved.getEmail());
        return UserResponse.from(saved);
    }

    public record PhotoFile(byte[] data, MediaType mediaType) {}

    public PhotoFile getPhoto(String email) throws IOException {
        User user = findByEmail(email);
        String fileName = user.getPhotoPath();
        if (fileName == null || fileName.isBlank()) {
            throw new ResourceNotFoundException("Foto tidak ditemukan");
        }
        Path dir = Paths.get(photoDir).toAbsolutePath().normalize();
        Path photoFile = dir.resolve(fileName).normalize();

        // Cegah path traversal dari data yang tersimpan di DB
        if (!photoFile.startsWith(dir) || !Files.exists(photoFile)) {
            throw new ResourceNotFoundException("Foto tidak ditemukan");
        }

        MediaType mediaType = fileName.toLowerCase().endsWith(".png")
                ? MediaType.IMAGE_PNG : MediaType.IMAGE_JPEG;
        return new PhotoFile(Files.readAllBytes(photoFile), mediaType);
    }

    private void validateImageFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ValidationException("File tidak boleh kosong");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase())) {
            throw new ValidationException("Tipe file tidak didukung. Gunakan JPG atau PNG");
        }
        extractExtension(file.getOriginalFilename());
    }

    private String extractExtension(String originalName) {
        if (originalName == null || !originalName.contains(".")) return ".jpg";
        String ext = originalName.substring(originalName.lastIndexOf('.')).toLowerCase();
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw new ValidationException("Ekstensi file tidak didukung. Gunakan .jpg, .jpeg, atau .png");
        }
        return ext;
    }

    private User findByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User tidak ditemukan"));
    }
}
