package com.taskforge.controller;

import com.taskforge.common.ApiResponse;
import com.taskforge.dto.request.MemberRequest;
import com.taskforge.dto.request.ProjectRequest;
import com.taskforge.dto.response.ActivityLogResponse;
import com.taskforge.dto.response.ProjectResponse;
import com.taskforge.dto.response.UserResponse;
import com.taskforge.repository.ActivityLogRepository;
import com.taskforge.service.ProjectService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;
    private final ActivityLogRepository activityLogRepository;

    @GetMapping
    public ResponseEntity<ApiResponse<List<ProjectResponse>>> getMyProjects(Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(projectService.getMyProjects(auth.getName())));
    }

    @GetMapping("/available")
    public ResponseEntity<ApiResponse<List<ProjectResponse>>> getAvailableProjects(Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(projectService.getAvailableProjects(auth.getName())));
    }

    @PostMapping
    @PreAuthorize("hasRole('KETUA') or hasRole('DOSEN')")
    public ResponseEntity<ApiResponse<ProjectResponse>> createProject(
            @Valid @RequestBody ProjectRequest request, Authentication auth) {
        ProjectResponse response = projectService.createProject(auth.getName(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Proyek berhasil dibuat", response));
    }

    @GetMapping("/{projectId}")
    @PreAuthorize("@projectService.isMember(#projectId, authentication.name)")
    public ResponseEntity<ApiResponse<ProjectResponse>> getProject(@PathVariable Long projectId, Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(projectService.getProjectById(projectId, auth.getName())));
    }

    @PutMapping("/{projectId}")
    @PreAuthorize("hasRole('KETUA') and @projectService.isOwner(#projectId, authentication.name)")
    public ResponseEntity<ApiResponse<ProjectResponse>> updateProject(
            @PathVariable Long projectId,
            @Valid @RequestBody ProjectRequest request,
            Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success("Proyek diperbarui",
                projectService.updateProject(projectId, auth.getName(), request)));
    }

    @DeleteMapping("/{projectId}")
    @PreAuthorize("hasRole('KETUA') and @projectService.isOwner(#projectId, authentication.name)")
    public ResponseEntity<ApiResponse<Void>> deleteProject(@PathVariable Long projectId, Authentication auth) {
        projectService.deleteProject(projectId, auth.getName());
        return ResponseEntity.status(HttpStatus.NO_CONTENT).body(ApiResponse.success(null));
    }

    @PostMapping("/{projectId}/members")
    @PreAuthorize("hasRole('KETUA') and @projectService.isOwner(#projectId, authentication.name)")
    public ResponseEntity<ApiResponse<UserResponse>> addMember(
            @PathVariable Long projectId,
            @Valid @RequestBody MemberRequest request,
            Authentication auth) {
        UserResponse member = projectService.addMember(projectId, auth.getName(), request);
        return ResponseEntity.ok(ApiResponse.success("Anggota berhasil ditambahkan", member));
    }

    @PostMapping(value = "/{projectId}/cover", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@projectService.isOwner(#projectId, authentication.name)")
    public ResponseEntity<ApiResponse<Void>> uploadCover(
            @PathVariable Long projectId,
            @RequestParam("file") MultipartFile file,
            Authentication auth) throws IOException {
        projectService.uploadCover(projectId, auth.getName(), file);
        return ResponseEntity.ok(ApiResponse.success("Foto sampul berhasil diupload", null));
    }

    @GetMapping("/{projectId}/cover")
    @PreAuthorize("@projectService.isMember(#projectId, authentication.name)")
    public ResponseEntity<byte[]> getCover(@PathVariable Long projectId) throws IOException {
        ProjectService.CoverFile cover = projectService.getCover(projectId);
        return ResponseEntity.ok().contentType(cover.mediaType()).body(cover.data());
    }

    @GetMapping("/{projectId}/activity")
    @PreAuthorize("hasRole('KETUA') and @projectService.isMember(#projectId, authentication.name)")
    public ResponseEntity<ApiResponse<List<ActivityLogResponse>>> getActivity(@PathVariable Long projectId) {
        List<ActivityLogResponse> logs = activityLogRepository
                .findByProjectIdOrderByTimestampDesc(projectId).stream()
                .map(ActivityLogResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.success(logs));
    }
}
