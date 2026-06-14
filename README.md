# Software Requirements Specification (SRS)
## TaskForge — Project Hub untuk Mahasiswa

---

| | |
|---|---|
| **LINK YOUTUBE** | 1.0 |
| **Nama Kelompok** | [Raja Iblis Ori] |
| **Anggota Kelompok** | [Tariq Rahmadari  (241401021)], [], [Blessly Victory Deo Silaban (241401060)],  [Blessly Victory Deo Silaban (241401060)],  [Muhammad Al Farel Azhar (241401009))],  [Gregorian Goclio Sinaga (241401087)], [Deofajar Jhonropinus Situmorang (241401102)]|
| **Mata Kuliah** | Pemrograman Berbasis Objek (PBO) |
| **Institusi** | [UNIVERSITAS SUMATERA UTARA] |
| **ASLAB** | [BANG ANDRE] |


---

## Daftar Isi

1. Pendahuluan
2. Deskripsi Umum Sistem
3. Kebutuhan Fungsional
4. Kebutuhan Non-Fungsional
5. Arsitektur Sistem
6. Desain Database
7. Implementasi 4 Pilar OOP
8. Kebutuhan Antarmuka
9. Kebutuhan Keamanan
10. Kebutuhan Validasi
11. Batasan & Asumsi Teknis

---

## 1. Pendahuluan

### 1.1 Tujuan Dokumen

Dokumen SRS ini mendefinisikan secara teknis seluruh kebutuhan perangkat lunak TaskForge — aplikasi manajemen proyek kelompok mahasiswa. Dokumen ini menjadi acuan utama dalam pengembangan, pengujian, dan evaluasi proyek UAS mata kuliah Pemrograman Berbasis Objek.

### 1.2 Ruang Lingkup

TaskForge adalah aplikasi desktop yang terdiri dari dua komponen utama:

1. **Frontend:** Aplikasi desktop JavaFX yang menjadi antarmuka pengguna
2. **Backend:** REST API berbasis Spring Boot yang mengelola logika bisnis dan persistensi data

Kedua komponen berkomunikasi melalui HTTP/REST dengan format data JSON dan mekanisme autentikasi berbasis JWT (JSON Web Token).

### 1.3 Definisi & Singkatan

| Istilah | Definisi |
|---|---|
| JWT | JSON Web Token — token untuk autentikasi stateless |
| JPA | Java Persistence API — standar ORM di Java |
| ORM | Object-Relational Mapping — pemetaan objek Java ke tabel database |
| MVC | Model-View-Controller — pola arsitektur perangkat lunak |
| DTO | Data Transfer Object — objek untuk transfer data antar layer |
| REST | Representational State Transfer — gaya arsitektur API |
| CRUD | Create, Read, Update, Delete — operasi dasar database |
| H2 | Database in-memory berbasis Java |
| Kanban | Sistem manajemen alur kerja visual dengan kolom status |
| Ketua | Pengguna dengan role ROLE_KETUA |
| Anggota | Pengguna dengan role ROLE_ANGGOTA |

---

## 2. Deskripsi Umum Sistem

### 2.1 Perspektif Sistem

TaskForge berjalan sebagai aplikasi lokal (localhost). Backend Spring Boot berjalan di port 8080 dan menyediakan REST API. Frontend JavaFX mengonsumsi API tersebut melalui HTTP client. Database H2 berjalan secara embedded di dalam proses Spring Boot.

```
[JavaFX Desktop App]
        |
        | HTTP/REST (JSON)
        |
[Spring Boot Backend :8080]
        |
        | JPA/Hibernate
        |
[H2 In-Memory Database]
```

### 2.2 Fungsi Utama Sistem

- Autentikasi dan otorisasi pengguna berbasis JWT dan role
- Manajemen proyek kelompok (CRUD)
- Manajemen task dengan tampilan Kanban
- Penyimpanan file terpusat (upload fisik + link eksternal)
- Penghitungan contribution score otomatis
- Pencatatan activity log
- Generasi laporan proyek

### 2.3 Karakteristik Pengguna

Sistem memiliki dua role pengguna:

**ROLE_KETUA:** Memiliki akses penuh — buat dan hapus proyek, assign task, approve perubahan, generate laporan, dan hapus file siapapun.

**ROLE_ANGGOTA:** Akses terbatas — update task yang di-assign kepadanya, upload file, tambah link, dan lihat data proyek.

---

## 3. Kebutuhan Fungsional

### 3.1 Modul Autentikasi

#### REQ-AUTH-01: Registrasi Pengguna
- **Deskripsi:** Sistem menyediakan endpoint untuk registrasi akun baru
- **Input:** `name` (String), `email` (String), `password` (String)
- **Proses:** Validasi input → hash password dengan BCrypt → simpan ke database → return respons sukses
- **Output:** HTTP 201 Created dengan data user (tanpa password)
- **Validasi:** Email harus format valid dan belum terdaftar; password minimal 8 karakter
- **Error:** HTTP 400 jika validasi gagal; HTTP 409 jika email sudah terdaftar

#### REQ-AUTH-02: Login
- **Deskripsi:** Sistem memverifikasi kredensial dan menerbitkan JWT
- **Input:** `email` (String), `password` (String)
- **Proses:** Cari user berdasarkan email → verifikasi password dengan BCrypt → generate JWT token
- **Output:** HTTP 200 dengan `{ token: "...", expiresIn: 86400, user: {...} }`
- **Error:** HTTP 401 jika kredensial salah

#### REQ-AUTH-03: Validasi Token
- **Deskripsi:** Setiap request ke endpoint yang dilindungi harus menyertakan JWT Bearer token di header
- **Header:** `Authorization: Bearer <token>`
- **Proses:** Filter `JwtAuthFilter` memvalidasi token sebelum request masuk ke Controller
- **Error:** HTTP 401 jika token tidak ada, tidak valid, atau kadaluarsa

### 3.2 Modul Proyek

#### REQ-PROJ-01: Buat Proyek
- **Endpoint:** `POST /api/projects`
- **Role:** KETUA
- **Input:** `title`, `description`, `deadline` (LocalDateTime)
- **Proses:** Buat entitas Project → set owner = user yang login → simpan ke database
- **Output:** HTTP 201 dengan data project lengkap

#### REQ-PROJ-02: Lihat Daftar Proyek
- **Endpoint:** `GET /api/projects`
- **Role:** KETUA, ANGGOTA
- **Proses:** Ambil semua proyek di mana user adalah owner atau member
- **Output:** HTTP 200 dengan list project (ringkasan)

#### REQ-PROJ-03: Lihat Detail Proyek
- **Endpoint:** `GET /api/projects/{projectId}`
- **Role:** KETUA, ANGGOTA (hanya anggota proyek tersebut)
- **Output:** HTTP 200 dengan data proyek + daftar member + statistik task

#### REQ-PROJ-04: Update Proyek
- **Endpoint:** `PUT /api/projects/{projectId}`
- **Role:** KETUA (hanya owner proyek)
- **Input:** Field yang ingin diubah (title, description, deadline)
- **Output:** HTTP 200 dengan data proyek terbaru

#### REQ-PROJ-05: Hapus Proyek
- **Endpoint:** `DELETE /api/projects/{projectId}`
- **Role:** KETUA (hanya owner proyek)
- **Proses:** Cascade delete semua task, file, dan log yang terkait
- **Output:** HTTP 204 No Content

#### REQ-PROJ-06: Tambah Anggota
- **Endpoint:** `POST /api/projects/{projectId}/members`
- **Role:** KETUA
- **Input:** `email` (email anggota yang akan diundang)
- **Proses:** Cari user berdasarkan email → tambahkan sebagai member proyek
- **Output:** HTTP 200 dengan data member baru
- **Error:** HTTP 404 jika email tidak ditemukan; HTTP 409 jika sudah menjadi member

### 3.3 Modul Task

#### REQ-TASK-01: Buat Task
- **Endpoint:** `POST /api/projects/{projectId}/tasks`
- **Role:** KETUA
- **Input:** `title`, `description`, `priority` (LOW/MEDIUM/HIGH), `deadline`, `assigneeId`, `taskType` (SIMPLE/MILESTONE)
- **Proses:** Buat entitas task (SimpleTask atau MilestoneTask berdasarkan taskType) → simpan → catat di activity log
- **Output:** HTTP 201 dengan data task lengkap

#### REQ-TASK-02: Lihat Semua Task dalam Proyek
- **Endpoint:** `GET /api/projects/{projectId}/tasks`
- **Role:** KETUA, ANGGOTA
- **Output:** HTTP 200 dengan list task dikelompokkan berdasarkan status

#### REQ-TASK-03: Update Status Task
- **Endpoint:** `PUT /api/tasks/{taskId}/status`
- **Role:** KETUA (semua task); ANGGOTA (hanya task yang di-assign padanya)
- **Input:** `status` (TODO/IN_PROGRESS/REVIEW/DONE)
- **Proses:** Update status → jika DONE, hitung ulang contribution score → catat activity log
- **Output:** HTTP 200 dengan data task terbaru

#### REQ-TASK-04: Assign Task
- **Endpoint:** `PUT /api/tasks/{taskId}/assign`
- **Role:** KETUA
- **Input:** `assigneeId` (Long)
- **Proses:** Update assignee → catat activity log
- **Output:** HTTP 200 dengan data task terbaru

#### REQ-TASK-05: Hapus Task
- **Endpoint:** `DELETE /api/tasks/{taskId}`
- **Role:** KETUA
- **Proses:** Cascade delete semua file terkait task → hapus task → catat activity log
- **Output:** HTTP 204 No Content

#### REQ-TASK-06: Deteksi Task Overdue
- **Deskripsi:** Sistem secara otomatis menandai task sebagai overdue jika deadline telah lewat dan status belum DONE
- **Proses:** Scheduler berjalan setiap menit (atau dicek saat load) → update field `isOverdue = true` jika `deadline < now && status != DONE`
- **Output:** Field `isOverdue` di response task

### 3.4 Modul File Hub

#### REQ-FILE-01: Upload File Fisik
- **Endpoint:** `POST /api/tasks/{taskId}/files/upload`
- **Role:** KETUA, ANGGOTA
- **Input:** `file` (MultipartFile), `description` (opsional)
- **Proses:** Validasi tipe dan ukuran file → simpan ke folder upload lokal → buat entitas UploadedFile → simpan ke database
- **Output:** HTTP 201 dengan metadata file (id, name, size, type, uploadedAt, uploadedBy)
- **Validasi:** Ukuran maks 10 MB; tipe file: pdf, doc, docx, ppt, pptx, jpg, jpeg, png, gif, txt, java, py, js, zip

#### REQ-FILE-02: Tambah Link Eksternal
- **Endpoint:** `POST /api/tasks/{taskId}/files/link`
- **Role:** KETUA, ANGGOTA
- **Input:** `name` (String), `url` (String), `description` (opsional)
- **Proses:** Validasi URL → buat entitas LinkedFile → simpan ke database
- **Output:** HTTP 201 dengan data link

#### REQ-FILE-03: Lihat File per Task
- **Endpoint:** `GET /api/tasks/{taskId}/files`
- **Role:** KETUA, ANGGOTA
- **Output:** HTTP 200 dengan list file dan link, terurut dari terbaru

#### REQ-FILE-04: Download File
- **Endpoint:** `GET /api/files/{fileId}/download`
- **Role:** KETUA, ANGGOTA (anggota proyek terkait)
- **Proses:** Baca file dari storage path → stream sebagai response
- **Output:** File binary dengan header Content-Disposition

#### REQ-FILE-05: Hapus File
- **Endpoint:** `DELETE /api/files/{fileId}`
- **Role:** KETUA; ANGGOTA (hanya file yang diuploadnya)
- **Proses:** Cek otorisasi → hapus file fisik dari disk (jika UploadedFile) → hapus entitas dari database
- **Output:** HTTP 204 No Content

### 3.5 Modul Kontribusi & Laporan

#### REQ-SCORE-01: Hitung Contribution Score
- **Deskripsi:** Sistem menghitung skor kontribusi tiap anggota secara otomatis
- **Trigger:** Setiap kali task berstatus DONE
- **Formula:**
  ```
  Score = (task_selesai × bobot_prioritas) + (ontime_bonus)
  
  Bobot prioritas: LOW = 1, MEDIUM = 2, HIGH = 3
  Ontime bonus: +1 jika diselesaikan sebelum deadline, 0 jika tidak
  
  Persentase = (score_user / total_score_semua_anggota) × 100
  ```
- **Output:** Score absolut dan persentase kontribusi per anggota

#### REQ-SCORE-02: Lihat Contribution Score
- **Endpoint:** `GET /api/projects/{projectId}/scores`
- **Role:** KETUA, ANGGOTA
- **Output:** HTTP 200 dengan list skor semua anggota, diurutkan dari tertinggi

#### REQ-LOG-01: Activity Log
- **Endpoint:** `GET /api/projects/{projectId}/activity`
- **Role:** KETUA
- **Output:** HTTP 200 dengan list log aktivitas, diurutkan dari terbaru
- **Log yang dicatat:** Pembuatan task, perubahan status, upload file, tambah link, perubahan assignee, tambah member

#### REQ-REPORT-01: Generate Laporan
- **Endpoint:** `GET /api/projects/{projectId}/report`
- **Role:** KETUA
- **Output:** HTTP 200 dengan data laporan terstruktur: ringkasan proyek, daftar task per status, contribution score, daftar file

---

## 4. Kebutuhan Non-Fungsional

### 4.1 Performa
- Waktu respons API untuk operasi CRUD biasa: < 500ms
- Waktu upload file 5 MB: < 3 detik pada koneksi localhost
- Waktu load Kanban board dengan 20 task: < 1 detik

### 4.2 Keandalan
- Aplikasi harus bisa dijalankan ulang tanpa kehilangan data (H2 file-based mode untuk persistensi)
- Tidak ada crash fatal saat validasi input gagal — semua error ditangani dengan pesan yang jelas

### 4.3 Kegunaan
- Semua form memiliki label dan placeholder yang jelas
- Pesan error ditampilkan di dekat field yang bermasalah, bukan hanya di console
- Status loading ditampilkan saat menunggu respons API

### 4.4 Keamanan
- Password di-hash dengan BCrypt sebelum disimpan (strength: 10)
- JWT token tidak menyimpan informasi sensitif selain userId dan role
- Endpoint yang dilindungi tidak dapat diakses tanpa token yang valid
- Pengguna tidak dapat mengakses data proyek yang bukan miliknya

### 4.5 Maintainability
- Pemisahan layer yang bersih: tidak ada logika bisnis di Controller atau Repository
- Setiap class memiliki tanggung jawab tunggal (Single Responsibility Principle)
- Kode mengikuti konvensi penamaan Java standar

---

## 5. Arsitektur Sistem

### 5.1 Arsitektur Keseluruhan

```
┌─────────────────────────────────────┐
│         FRONTEND (JavaFX)           │
│                                     │
│  LoginView ──── DashboardView       │
│       │              │              │
│  KanbanView    FileHubView          │
│       └──────── ApiClient ─────────►│
└────────────────────────│────────────┘
                         │ HTTP/JSON
┌────────────────────────▼────────────┐
│         BACKEND (Spring Boot)       │
│                                     │
│  Controller → Service → Repository  │
│                   │                 │
│           JPA/Hibernate             │
│                   │                 │
│           H2 Database               │
└─────────────────────────────────────┘
```

### 5.2 Layer Backend

**Controller Layer**
Menerima HTTP request, memanggil Service, mengembalikan HTTP response. Tidak boleh mengandung logika bisnis.

**Service Layer**
Mengelola logika bisnis, validasi lanjutan, dan orkestrasi antar repository. Dipanggil oleh Controller.

**Repository Layer**
Antarmuka ke database menggunakan Spring Data JPA. Hanya berisi query — tidak ada logika bisnis.

**Model Layer**
Entitas JPA yang merepresentasikan tabel database. Mengimplementasikan hierarki OOP.

### 5.3 Pola Komunikasi Frontend-Backend

```
JavaFX Controller
    │
    ├── ApiClient.get("/api/projects")
    │       │
    │       └── HttpClient.send(request)
    │               │
    │               └── [parse JSON → DTO object]
    │
    └── Update JavaFX UI (di JavaFX Application Thread)
```

Semua HTTP call dari JavaFX harus dilakukan di thread terpisah menggunakan `Task<T>` untuk menghindari UI freeze.

---

## 6. Desain Database

### 6.1 Entitas dan Relasi

```
users
  ├── id (PK, BIGINT, AUTO_INCREMENT)
  ├── name (VARCHAR 100, NOT NULL)
  ├── email (VARCHAR 100, UNIQUE, NOT NULL)
  ├── password (VARCHAR 255, NOT NULL)  ← BCrypt hash
  └── role (ENUM: KETUA, ANGGOTA)

projects
  ├── id (PK)
  ├── title (VARCHAR 200, NOT NULL)
  ├── description (TEXT)
  ├── deadline (DATETIME)
  ├── created_at (DATETIME)
  └── owner_id (FK → users.id)

project_members              ← join table Many-to-Many
  ├── project_id (FK → projects.id)
  └── user_id (FK → users.id)

tasks
  ├── id (PK)
  ├── title (VARCHAR 200, NOT NULL)
  ├── description (TEXT)
  ├── status (ENUM: TODO, IN_PROGRESS, REVIEW, DONE)
  ├── priority (ENUM: LOW, MEDIUM, HIGH)
  ├── task_type (ENUM: SIMPLE, MILESTONE)  ← discriminator
  ├── deadline (DATETIME)
  ├── is_overdue (BOOLEAN, DEFAULT false)
  ├── created_at (DATETIME)
  ├── completed_at (DATETIME, nullable)
  ├── project_id (FK → projects.id)
  └── assignee_id (FK → users.id, nullable)

milestone_subtasks           ← hanya untuk MilestoneTask
  ├── id (PK)
  ├── title (VARCHAR 200)
  ├── is_done (BOOLEAN)
  └── milestone_task_id (FK → tasks.id)

project_files
  ├── id (PK)
  ├── name (VARCHAR 255, NOT NULL)
  ├── file_type (ENUM: UPLOAD, LINK)  ← discriminator
  ├── storage_path (VARCHAR 500, nullable)  ← untuk UPLOAD
  ├── external_url (VARCHAR 1000, nullable) ← untuk LINK
  ├── file_size (BIGINT, nullable)
  ├── mime_type (VARCHAR 100, nullable)
  ├── description (TEXT)
  ├── uploaded_at (DATETIME)
  ├── task_id (FK → tasks.id)
  └── uploaded_by (FK → users.id)

activity_logs
  ├── id (PK)
  ├── action (VARCHAR 500, NOT NULL)
  ├── action_type (ENUM: TASK_CREATED, STATUS_CHANGED, FILE_UPLOADED, LINK_ADDED, ASSIGNEE_CHANGED, MEMBER_ADDED)
  ├── timestamp (DATETIME)
  ├── actor_id (FK → users.id)
  └── project_id (FK → projects.id)

contribution_scores
  ├── id (PK)
  ├── score (DOUBLE)
  ├── tasks_completed (INT)
  ├── tasks_ontime (INT)
  ├── percentage (DOUBLE)
  ├── last_updated (DATETIME)
  ├── user_id (FK → users.id)
  └── project_id (FK → projects.id)
  UNIQUE (user_id, project_id)
```

### 6.2 Konfigurasi JPA

```java
// Relasi utama di Java

// Project.java
@OneToMany(mappedBy = "project", cascade = CascadeType.ALL, orphanRemoval = true)
private List<BaseTask> tasks;

@ManyToMany
@JoinTable(name = "project_members",
    joinColumns = @JoinColumn(name = "project_id"),
    inverseJoinColumns = @JoinColumn(name = "user_id"))
private Set<User> members;

// BaseTask.java (abstract)
@OneToMany(mappedBy = "task", cascade = CascadeType.ALL, orphanRemoval = true)
private List<ProjectFile> files;

@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "assignee_id")
private User assignee;
```

---

## 7. Implementasi 4 Pilar OOP

### 7.1 Encapsulation

**Class `BaseTask`:**
```java
public abstract class BaseTask {
    private TaskStatus status;          // private — tidak bisa diubah langsung
    private double score;               // private — dihitung internal
    private User assignee;             // private — harus lewat reassign()

    // Public interface yang terkontrol
    public void complete() {
        this.status = TaskStatus.DONE;
        this.completedAt = LocalDateTime.now();
        this.score = calculateScore();  // polymorphism
    }

    public void reassign(User newAssignee) {
        this.assignee = newAssignee;
        // bisa tambah validasi di sini
    }

    public void markOverdue() {
        if (this.deadline.isBefore(LocalDateTime.now()) && this.status != TaskStatus.DONE) {
            this.isOverdue = true;
        }
    }
}
```

**Class `ProjectFile`:**
```java
public abstract class ProjectFile {
    private String storagePath;      // private — detail implementasi tersembunyi
    private String externalUrl;      // private

    public abstract String getAccessUrl();  // satu-satunya cara akses URL
}
```

### 7.2 Inheritance

```
BaseTask (abstract)
    ├── SimpleTask
    │       └── Override: calculateScore() → bobot standar
    └── MilestoneTask
            ├── Tambahan field: List<SubTask> subTasks
            └── Override: calculateScore() → bobot berlipat berdasarkan sub-task selesai

ProjectFile (abstract)
    ├── UploadedFile
    │       ├── Field: storagePath, fileSize, mimeType
    │       └── Override: getAccessUrl() → return path lokal
    └── LinkedFile
            ├── Field: externalUrl
            └── Override: getAccessUrl() → return URL eksternal
```

### 7.3 Polymorphism

**Interface Scorable:**
```java
public interface Scorable {
    double calculateScore();
}

// SimpleTask: skor berdasarkan prioritas saja
@Override
public double calculateScore() {
    double base = priority.getWeight();  // LOW=1, MEDIUM=2, HIGH=3
    boolean onTime = completedAt != null && completedAt.isBefore(deadline);
    return onTime ? base + 1 : base;
}

// MilestoneTask: skor berlipat berdasarkan sub-task selesai
@Override
public double calculateScore() {
    long done = subTasks.stream().filter(SubTask::isDone).count();
    double completionRatio = (double) done / subTasks.size();
    return priority.getWeight() * 2 * completionRatio;
}
```

**Interface Accessible:**
```java
public interface Accessible {
    String getAccessUrl();
    String getDisplayName();
}

// UploadedFile
@Override
public String getAccessUrl() {
    return "/api/files/" + this.id + "/download";
}

// LinkedFile
@Override
public String getAccessUrl() {
    return this.externalUrl;
}
```

**Interface Reportable:**
```java
public interface Reportable {
    ReportData generate(Long projectId);
}

// ContributionReport: fokus pada skor dan statistik anggota
// FileActivityReport: fokus pada file yang diupload per task
```

### 7.4 Abstraction

```java
// Abstract class menyembunyikan detail implementasi
public abstract class BaseTask implements Scorable {
    // Detail bagaimana score dihitung disembunyikan dari layer lain
    // Controller dan Service hanya panggil: task.complete()
    // Mereka tidak perlu tahu formula perhitungan score

    public abstract double calculateScore(); // Definisi kontrak
}

public abstract class ProjectFile implements Accessible {
    // Detail storage (lokal vs URL) disembunyikan
    // FileService hanya panggil: file.getAccessUrl()
    // Tidak perlu tahu apakah itu file fisik atau link
}

public abstract class ReportGenerator implements Reportable {
    // Template method pattern
    public final ReportData generate(Long projectId) {
        ProjectData data = collectData(projectId);  // sama untuk semua
        return format(data);                         // berbeda tiap subclass
    }

    protected abstract ReportData format(ProjectData data);
}
```

---

## 8. Kebutuhan Antarmuka

### 8.1 Halaman Login

**Komponen UI:**
- Field email dengan placeholder "Masukkan email kamu"
- Field password (masked) dengan placeholder "Masukkan password"
- Tombol "Masuk"
- Link "Belum punya akun? Daftar"
- Label error yang muncul jika login gagal

**Behavior:**
- Tombol Masuk disabled selama proses login berlangsung
- Tampilkan spinner/loading indicator saat request dikirim
- Redirect ke Dashboard setelah login berhasil
- Simpan JWT token di memori aplikasi (bukan di disk)

### 8.2 Halaman Dashboard

**Komponen UI:**
- Header dengan nama pengguna dan tombol logout
- Daftar proyek yang diikuti (card per proyek)
- Setiap card menampilkan: nama proyek, deadline, jumlah task, progress bar
- Tombol "Buat Proyek Baru" (hanya terlihat jika role = KETUA)

### 8.3 Kanban Board

**Komponen UI:**
- 4 kolom: To-Do | In Progress | Review | Done
- Setiap task ditampilkan sebagai card dengan: judul, nama assignee, badge prioritas (merah/kuning/hijau), sisa hari atau label "OVERDUE" berwarna merah
- Tombol "Tambah Task" di header kolom To-Do (hanya Ketua)
- Klik task → panel detail task muncul di samping

**Behavior:**
- Ketua: bisa drag-and-drop task ke kolom manapun
- Anggota: hanya bisa memindahkan task miliknya
- Task overdue: latar card berwarna merah muda

### 8.4 File Hub

**Komponen UI:**
- Dropdown selector: pilih task yang ingin dilihat filenya
- Daftar file dengan ikon tipe (PDF, Word, gambar, link eksternal)
- Setiap item menampilkan: nama, ukuran (untuk upload), tanggal, nama pengunggah
- Tombol "Upload File" dan "Tambah Link"
- Tombol hapus di setiap item (hanya untuk uploader atau ketua)

### 8.5 Halaman Laporan & Skor

**Komponen UI:**
- Tabel contribution score: nama anggota, task selesai, persentase, bar visual
- Warna bar: hijau (≥ rata-rata), kuning (50-80% rata-rata), merah (< 50% rata-rata)
- Tombol "Export Laporan" (format teks/tampilan yang bisa di-screenshot)

---

## 9. Kebutuhan Keamanan

### 9.1 Autentikasi

```java
// Konfigurasi Spring Security
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())  // disabled karena REST + JWT
            .sessionManagement(sess -> sess
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/**", "/h2-console/**").permitAll()
                .anyRequest().authenticated())
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
```

### 9.2 Otorisasi Berbasis Role

```java
// Contoh penerapan @PreAuthorize
@PreAuthorize("hasRole('KETUA')")
public ResponseEntity<?> createTask(...) { ... }

@PreAuthorize("hasRole('KETUA') or @taskService.isAssignee(#taskId, authentication.name)")
public ResponseEntity<?> updateTaskStatus(@PathVariable Long taskId, ...) { ... }

@PreAuthorize("hasRole('KETUA') or @fileService.isUploader(#fileId, authentication.name)")
public ResponseEntity<?> deleteFile(@PathVariable Long fileId, ...) { ... }
```

### 9.3 JWT Configuration

```java
// Konfigurasi JWT
jwt.secret=[GANTI_DENGAN_SECRET_KEY_MINIMAL_256_BIT]
jwt.expiration=86400000  // 24 jam dalam milidetik

// Payload JWT (tidak menyimpan data sensitif)
{
  "sub": "user@email.com",
  "userId": 1,
  "role": "ROLE_KETUA",
  "iat": 1234567890,
  "exp": 1234654290
}
```

---

## 10. Kebutuhan Validasi

### 10.1 Validasi Input di DTO

```java
// TaskRequestDTO.java
public class TaskRequestDTO {

    @NotBlank(message = "Judul task tidak boleh kosong")
    @Size(max = 200, message = "Judul maksimal 200 karakter")
    private String title;

    @NotNull(message = "Deadline wajib diisi")
    @Future(message = "Deadline harus di masa depan")
    private LocalDateTime deadline;

    @NotNull(message = "Prioritas wajib dipilih")
    private Priority priority;

    @NotNull(message = "Assignee wajib ditentukan")
    private Long assigneeId;
}

// LinkedFileRequestDTO.java
public class LinkedFileRequestDTO {

    @NotBlank(message = "Nama link tidak boleh kosong")
    private String name;

    @NotBlank(message = "URL tidak boleh kosong")
    @org.hibernate.validator.constraints.URL(message = "Format URL tidak valid")
    private String url;
}

// RegisterRequestDTO.java
public class RegisterRequestDTO {

    @NotBlank(message = "Nama tidak boleh kosong")
    private String name;

    @NotBlank(message = "Email tidak boleh kosong")
    @Email(message = "Format email tidak valid")
    private String email;

    @NotBlank(message = "Password tidak boleh kosong")
    @Size(min = 8, message = "Password minimal 8 karakter")
    private String password;
}
```

### 10.2 Validasi File Upload

```java
// Di FileService.java
public void validateUploadedFile(MultipartFile file) {
    // Validasi ukuran
    if (file.getSize() > 10_000_000) {
        throw new ValidationException("Ukuran file maksimal 10 MB");
    }

    // Validasi tipe file
    String originalName = file.getOriginalFilename();
    String extension = getExtension(originalName).toLowerCase();
    List<String> allowed = List.of(
        "pdf", "doc", "docx", "ppt", "pptx",
        "jpg", "jpeg", "png", "gif",
        "txt", "java", "py", "js", "ts", "html", "css",
        "zip", "rar"
    );
    if (!allowed.contains(extension)) {
        throw new ValidationException("Tipe file tidak didukung: " + extension);
    }
}
```

### 10.3 Global Exception Handler

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleValidation(
            MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors()
            .forEach(err -> errors.put(err.getField(), err.getDefaultMessage()));
        return ResponseEntity.badRequest()
            .body(ApiResponse.error("Validasi gagal", errors));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(ResourceNotFoundException ex) {
        return ResponseEntity.status(404)
            .body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(403)
            .body(ApiResponse.error("Kamu tidak punya izin untuk melakukan aksi ini"));
    }
}
```

---

## 11. Batasan & Asumsi Teknis

### 11.1 Batasan Teknis

- H2 in-memory: data tidak persisten setelah aplikasi dimatikan, kecuali dikonfigurasi sebagai file-based (`jdbc:h2:file:./taskforgedb`)
- Tidak ada fitur real-time: perubahan oleh user lain hanya terlihat setelah refresh halaman
- File storage bersifat lokal: file disimpan di direktori yang dikonfigurasi di `application.properties`
- Satu instance backend: tidak dirancang untuk multi-instance/clustering

### 11.2 Asumsi Teknis

- Java 17+ tersedia di mesin penguji
- Maven tersedia di PATH sistem
- Port 8080 tidak digunakan oleh aplikasi lain saat demo
- Direktori upload sudah dibuat sebelum aplikasi dijalankan (atau dibuat otomatis oleh `FileService`)
- JavaFX SDK sudah dikonfigurasi di Maven (`pom.xml`) atau tersedia di sistem

### 11.3 Konfigurasi Environment Wajib

Sebelum menjalankan aplikasi, pastikan nilai berikut sudah diisi di `application.properties`:

| Property | Nilai Default | Keterangan |
|---|---|---|
| `spring.datasource.username` | `[GANTI]` | Username H2, contoh: `sa` |
| `spring.datasource.password` | `[GANTI]` | Password H2, boleh kosong |
| `jwt.secret` | `[GANTI]` | String acak minimal 32 karakter |
| `file.upload-dir` | `[GANTI]` | Path absolut folder upload |
| `server.port` | `8080` | Port backend, ubah jika konflik |

---

*Dokumen ini adalah bagian dari proyek UAS mata kuliah Pemrograman Berbasis Objek.*  
*Referensi: PRD TaskForge v1.0 | CLAUDE.md TaskForge v1.0*
