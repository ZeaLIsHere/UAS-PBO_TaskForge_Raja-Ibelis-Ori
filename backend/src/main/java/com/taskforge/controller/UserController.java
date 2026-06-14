package com.taskforge.controller;

import com.taskforge.common.ApiResponse;
import com.taskforge.dto.request.UpdateProfileRequest;
import com.taskforge.dto.response.UserResponse;
import com.taskforge.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Slf4j
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> getMyProfile(Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success("Profil berhasil diambil",
                userService.getProfile(authentication.getName())));
    }

    @PutMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> updateProfile(
            @Valid @RequestBody UpdateProfileRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success("Profil berhasil diperbarui",
                userService.updateProfile(authentication.getName(), request)));
    }

    @PostMapping(value = "/me/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<UserResponse>> uploadPhoto(
            @RequestParam("file") MultipartFile file,
            Authentication authentication) throws IOException {
        return ResponseEntity.ok(ApiResponse.success("Foto berhasil diupload",
                userService.uploadPhoto(authentication.getName(), file)));
    }

    @GetMapping("/me/photo")
    public ResponseEntity<byte[]> getMyPhoto(Authentication authentication) throws IOException {
        UserService.PhotoFile photo = userService.getPhoto(authentication.getName());
        return ResponseEntity.ok().contentType(photo.mediaType()).body(photo.data());
    }
}
