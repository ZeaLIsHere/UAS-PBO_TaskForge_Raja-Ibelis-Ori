package com.taskforge.seeder;

import com.taskforge.model.*;
import com.taskforge.model.file.LinkedFile;
import com.taskforge.model.file.UploadedFile;
import com.taskforge.model.task.BaseTask.Priority;
import com.taskforge.model.task.SimpleTask;
import com.taskforge.repository.*;
import com.taskforge.service.ContributionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;

/**
 * Seeds the demo scenario from CLAUDE.md §"Data Demo" on a fresh database.
 * Skips seeding if data already exists.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final ProjectRepository projectRepository;
    private final TaskRepository taskRepository;
    private final ProjectFileRepository fileRepository;
    private final ContributionService contributionService;
    private final PasswordEncoder passwordEncoder;

    @Value("${file.upload-dir}")
    private String uploadDir;

    @Override
    @Transactional
    public void run(String... args) {
        if (userRepository.count() > 0) {
            log.info("Database sudah berisi data — seeder dilewati");
            return;
        }

        log.info("Menyiapkan data demo...");

        // ── Users ──────────────────────────────────────────────────────────
        User ketua = createUser("Gregorian Sinaga", "ketua@demo.com", "Demo@1234", User.Role.KETUA);
        User rizki  = createUser("Rizki Maulana",   "rizki@demo.com",  "Demo@1234", User.Role.ANGGOTA);
        User dewi   = createUser("Dewi Rahayu",     "dewi@demo.com",   "Demo@1234", User.Role.ANGGOTA);
        User kevin  = createUser("Kevin Tanaka",    "kevin@demo.com",  "Demo@1234", User.Role.ANGGOTA);

        userRepository.save(ketua);
        userRepository.save(rizki);
        userRepository.save(dewi);
        userRepository.save(kevin);

        // ── Project ────────────────────────────────────────────────────────
        Project project = Project.builder()
                .title("Tugas Akhir Sistem Informasi Perpustakaan")
                .description("Pengembangan sistem informasi manajemen perpustakaan kampus berbasis web")
                .deadline(LocalDateTime.now().plusDays(30))
                .maxMembers(4)
                .owner(ketua)
                .build();
        project.getMembers().add(rizki);
        project.getMembers().add(dewi);
        project.getMembers().add(kevin);
        project = projectRepository.save(project);

        // ── Completed tasks (3) ────────────────────────────────────────────
        SimpleTask task1 = buildTask("Analisis Kebutuhan Sistem", Priority.HIGH,
                LocalDateTime.now().plusDays(60), project, rizki);
        task1.complete(); // score = HIGH(3) + ontime(1) = 4
        task1 = (SimpleTask) taskRepository.save(task1);
        contributionService.addScore(task1);

        SimpleTask task2 = buildTask("Desain ERD Database", Priority.MEDIUM,
                LocalDateTime.now().plusDays(60), project, dewi);
        task2.complete(); // score = MEDIUM(2) + ontime(1) = 3
        task2 = (SimpleTask) taskRepository.save(task2);
        contributionService.addScore(task2);

        SimpleTask task3 = buildTask("Implementasi Login Module", Priority.HIGH,
                LocalDateTime.now().plusDays(60), project, ketua);
        task3.complete(); // score = HIGH(3) + ontime(1) = 4
        task3 = (SimpleTask) taskRepository.save(task3);
        contributionService.addScore(task3);

        // ── Overdue task (1) — assigned to inactive member ─────────────────
        SimpleTask overdueTask = buildTask("Testing dan QA", Priority.HIGH,
                LocalDateTime.now().minusDays(5), project, kevin);  // deadline in the past
        overdueTask.markOverdue();
        taskRepository.save(overdueTask);

        // ── Files ──────────────────────────────────────────────────────────
        createUploadedFile("Laporan_BAB1.pdf", task1, rizki, "application/pdf");
        createUploadedFile("ERD_Database.pdf", task2, dewi, "application/pdf");

        LinkedFile driveLink = new LinkedFile();
        driveLink.setName("Google Drive — Referensi Jurnal");
        driveLink.setDescription("Kumpulan referensi jurnal dan artikel ilmiah");
        driveLink.setTask(task3);
        driveLink.setUploadedBy(ketua);
        driveLink.setExternalUrl("https://drive.google.com/drive/folders/taskforge-demo-reference");
        fileRepository.save(driveLink);

        // ── Activity log ───────────────────────────────────────────────────
        logActivity(project, ketua, "Gregorian Sinaga membuat proyek 'Tugas Akhir SI Perpustakaan'", ActivityLog.ActionType.TASK_CREATED);
        logActivity(project, ketua, "Gregorian Sinaga menambahkan Rizki Maulana ke proyek", ActivityLog.ActionType.MEMBER_ADDED);
        logActivity(project, ketua, "Gregorian Sinaga menambahkan Dewi Rahayu ke proyek", ActivityLog.ActionType.MEMBER_ADDED);
        logActivity(project, ketua, "Gregorian Sinaga menambahkan Kevin Tanaka ke proyek", ActivityLog.ActionType.MEMBER_ADDED);

        log.info("Data demo berhasil di-seed! Login dengan ketua@demo.com / Demo@1234");
    }

    private User createUser(String name, String email, String password, User.Role role) {
        return User.builder()
                .name(name)
                .email(email)
                .password(passwordEncoder.encode(password))
                .role(role)
                .build();
    }

    private SimpleTask buildTask(String title, Priority priority,
                                  LocalDateTime deadline, Project project, User assignee) {
        SimpleTask task = new SimpleTask();
        task.setTitle(title);
        task.setDescription("Task: " + title);
        task.setPriority(priority);
        task.setDeadline(deadline);
        task.setProject(project);
        task.reassign(assignee);
        return task;
    }

    private void createUploadedFile(String filename, SimpleTask task, User uploader, String mimeType) {
        Path dir = Paths.get(uploadDir);
        try {
            if (!Files.exists(dir)) Files.createDirectories(dir);
            Path file = dir.resolve(filename);
            if (!Files.exists(file)) {
                // Write a minimal PDF header so the file is non-empty
                Files.writeString(file, "%PDF-1.4\n%% Demo file: " + filename + "\n%% Generated by TaskForge DataSeeder\n");
            }

            UploadedFile entity = new UploadedFile();
            entity.setName(filename);
            entity.setDescription("Demo file untuk task: " + task.getTitle());
            entity.setTask(task);
            entity.setUploadedBy(uploader);
            entity.setStoragePath(file.toString());
            entity.setFileSize(Files.size(file));
            entity.setMimeType(mimeType);
            fileRepository.save(entity);
        } catch (IOException e) {
            log.warn("Gagal membuat file demo '{}': {}", filename, e.getMessage());
        }
    }

    private void logActivity(Project project, User actor, String action, ActivityLog.ActionType type) {
        ActivityLog log = ActivityLog.builder()
                .project(project)
                .actor(actor)
                .action(action)
                .actionType(type)
                .build();
        project.getActivityLogs().add(log);
        projectRepository.save(project);
    }
}
