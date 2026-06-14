package com.taskforge.service;

import com.taskforge.dto.request.MemberRequest;
import com.taskforge.dto.request.ProjectRequest;
import com.taskforge.dto.response.ProjectResponse;
import com.taskforge.dto.response.UserResponse;
import com.taskforge.exception.DuplicateResourceException;
import com.taskforge.exception.ResourceNotFoundException;
import com.taskforge.exception.ValidationException;
import com.taskforge.model.ActivityLog.ActionType;
import com.taskforge.model.Project;
import com.taskforge.model.User;
import com.taskforge.repository.ProjectRepository;
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
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final ActivityLogService activityLogService;

    @Value("${file.cover-dir}")
    private String coverDir;

    private static final Set<String> ALLOWED_COVER_EXT = Set.of(".jpg", ".jpeg", ".png");
    private static final Set<String> ALLOWED_COVER_TYPES = Set.of("image/jpeg", "image/png");

    // ── SpEL helpers (called from @PreAuthorize) ──────────────────────────

    @Transactional(readOnly = true)
    public boolean isMember(Long projectId, String email) {
        // DOSEN & ASDOS adalah pengawas: punya hak "bypass" untuk melihat
        // proyek, task, dan laporan semua kelompok tanpa perlu diundang.
        if (isObserver(email)) {
            return projectRepository.existsById(projectId);
        }
        return projectRepository.findById(projectId)
                .map(p -> p.getOwner().getEmail().equals(email)
                        || p.getMembers().stream().anyMatch(m -> m.getEmail().equals(email)))
                .orElse(false);
    }

    /** True jika user adalah pengawas global (DOSEN atau ASDOS). */
    @Transactional(readOnly = true)
    public boolean isObserver(String email) {
        return userRepository.findByEmail(email)
                .map(u -> u.getRole() == User.Role.DOSEN || u.getRole() == User.Role.ASDOS)
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public boolean isOwner(Long projectId, String email) {
        return projectRepository.findById(projectId)
                .map(p -> p.getOwner().getEmail().equals(email))
                .orElse(false);
    }

    // ── CRUD ──────────────────────────────────────────────────────────────

    @Transactional
    public ProjectResponse createProject(String actorEmail, ProjectRequest request) {
        User owner = getUser(actorEmail);

        Project project = Project.builder()
                .title(request.getTitle())
                .description(request.getDescription())
                .deadline(request.getDeadline())
                .maxMembers(request.getMaxMembers())
                .owner(owner)
                .build();

        Project saved = projectRepository.save(project);
        log.info("Proyek '{}' dibuat oleh {}", saved.getTitle(), actorEmail);
        return ProjectResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<ProjectResponse> getMyProjects(String actorEmail) {
        User user = getUser(actorEmail);
        // Pengawas (DOSEN/ASDOS) melihat seluruh proyek kelompok di sistem.
        List<Project> projects = (user.getRole() == User.Role.DOSEN || user.getRole() == User.Role.ASDOS)
                ? projectRepository.findAll()
                : projectRepository.findAllByMemberOrOwner(user);
        return projects.stream()
                .map(ProjectResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ProjectResponse> getAvailableProjects(String actorEmail) {
        User user = getUser(actorEmail);
        return projectRepository.findAvailableFor(user).stream()
                .map(ProjectResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public ProjectResponse getProjectById(Long projectId, String actorEmail) {
        Project project = getProject(projectId);
        ensureMember(project, actorEmail);
        return ProjectResponse.from(project);
    }

    @Transactional
    public ProjectResponse updateProject(Long projectId, String actorEmail, ProjectRequest request) {
        Project project = getProject(projectId);
        ensureOwner(project, actorEmail);

        project.setTitle(request.getTitle());
        project.setDescription(request.getDescription());
        project.setDeadline(request.getDeadline());

        return ProjectResponse.from(projectRepository.save(project));
    }

    @Transactional
    public void deleteProject(Long projectId, String actorEmail) {
        Project project = getProject(projectId);
        ensureOwner(project, actorEmail);
        projectRepository.delete(project);
        log.info("Proyek id={} dihapus oleh {}", projectId, actorEmail);
    }

    @Transactional
    public UserResponse addMember(Long projectId, String actorEmail, MemberRequest request) {
        Project project = getProject(projectId);
        ensureOwner(project, actorEmail);

        User newMember = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User dengan email " + request.getEmail() + " tidak ditemukan"));

        if (project.getOwner().getId().equals(newMember.getId())
                || project.getMembers().contains(newMember)) {
            throw new DuplicateResourceException(
                    request.getEmail() + " sudah menjadi anggota proyek ini");
        }

        int currentCount = 1 + project.getMembers().size();
        int maxMembers = project.getMaxMembers() != null ? project.getMaxMembers() : 4;
        if (currentCount >= maxMembers) {
            throw new ValidationException(
                    "Proyek sudah penuh (" + maxMembers + " anggota termasuk ketua)");
        }

        project.getMembers().add(newMember);
        projectRepository.save(project);

        User actor = getUser(actorEmail);
        activityLogService.log(project, actor,
                actor.getName() + " menambahkan " + newMember.getName() + " ke proyek",
                ActionType.MEMBER_ADDED);

        log.info("Member {} ditambahkan ke proyek id={}", newMember.getEmail(), projectId);
        return UserResponse.from(newMember);
    }

    // ── Cover image ────────────────────────────────────────────────────────

    @Transactional
    public void uploadCover(Long projectId, String actorEmail, MultipartFile file) throws IOException {
        Project project = getProject(projectId);
        ensureOwner(project, actorEmail);
        validateCoverFile(file);

        Path dir = Paths.get(coverDir).toAbsolutePath().normalize();
        Files.createDirectories(dir);

        String ext = extractCoverExtension(file.getOriginalFilename());
        String fileName = "project-" + project.getId() + ext;
        Path target = dir.resolve(fileName).normalize();
        if (!target.startsWith(dir)) {
            throw new ValidationException("Nama file tidak valid");
        }

        Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        project.setCoverPath(fileName);
        projectRepository.save(project);
        log.info("Cover proyek id={} diperbarui oleh {}", projectId, actorEmail);
    }

    public record CoverFile(byte[] data, MediaType mediaType) {}

    @Transactional(readOnly = true)
    public CoverFile getCover(Long projectId) throws IOException {
        Project project = getProject(projectId);
        String fileName = project.getCoverPath();
        if (fileName == null || fileName.isBlank()) {
            throw new ResourceNotFoundException("Cover tidak ditemukan");
        }
        Path dir = Paths.get(coverDir).toAbsolutePath().normalize();
        Path coverFile = dir.resolve(fileName).normalize();
        if (!coverFile.startsWith(dir) || !Files.exists(coverFile)) {
            throw new ResourceNotFoundException("Cover tidak ditemukan");
        }
        MediaType mediaType = fileName.toLowerCase().endsWith(".png")
                ? MediaType.IMAGE_PNG : MediaType.IMAGE_JPEG;
        return new CoverFile(Files.readAllBytes(coverFile), mediaType);
    }

    private void validateCoverFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ValidationException("File tidak boleh kosong");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_COVER_TYPES.contains(contentType.toLowerCase())) {
            throw new ValidationException("Tipe file tidak didukung. Gunakan JPG atau PNG");
        }
        extractCoverExtension(file.getOriginalFilename());
    }

    private String extractCoverExtension(String originalName) {
        if (originalName == null || !originalName.contains(".")) return ".jpg";
        String ext = originalName.substring(originalName.lastIndexOf('.')).toLowerCase();
        if (!ALLOWED_COVER_EXT.contains(ext)) {
            throw new ValidationException("Ekstensi file tidak didukung. Gunakan .jpg, .jpeg, atau .png");
        }
        return ext;
    }

    // ── Internal helpers ──────────────────────────────────────────────────

    public User getUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User tidak ditemukan: " + email));
    }

    public Project getProject(Long projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", projectId));
    }

    private void ensureMember(Project project, String email) {
        boolean ok = isObserver(email)
                || project.getOwner().getEmail().equals(email)
                || project.getMembers().stream().anyMatch(m -> m.getEmail().equals(email));
        if (!ok) throw new ResourceNotFoundException("Project", project.getId());
    }

    private void ensureOwner(Project project, String email) {
        if (!project.getOwner().getEmail().equals(email)) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Hanya owner proyek yang bisa melakukan aksi ini");
        }
    }
}
