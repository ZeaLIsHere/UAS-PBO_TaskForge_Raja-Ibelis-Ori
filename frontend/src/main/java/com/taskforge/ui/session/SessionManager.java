package com.taskforge.ui.session;

import com.taskforge.ui.model.UserModel;

// JWT and current user live in memory — never written to disk
public class SessionManager {

    private static SessionManager instance;

    private String token;
    private UserModel currentUser;

    private SessionManager() {}

    public static SessionManager getInstance() {
        if (instance == null) {
            instance = new SessionManager();
        }
        return instance;
    }

    public void setSession(String token, UserModel user) {
        this.token = token;
        this.currentUser = user;
    }

    public void clearSession() {
        this.token = null;
        this.currentUser = null;
    }

    public String getToken() { return token; }
    public UserModel getCurrentUser() { return currentUser; }
    public boolean isLoggedIn() { return token != null; }

    // ─── Role helpers ──────────────────────────────────────────────────────────
    public String getRole() {
        return currentUser != null ? currentUser.getRole() : null;
    }

    private boolean roleIs(String role) {
        return currentUser != null && role.equals(currentUser.getRole());
    }

    public boolean isKetua()   { return roleIs("KETUA"); }
    public boolean isAnggota() { return roleIs("ANGGOTA"); }
    public boolean isDosen()   { return roleIs("DOSEN"); }
    public boolean isAsdos()   { return roleIs("ASDOS"); }

    /** DOSEN & ASDOS: pengawas yang bisa memantau semua kelompok. */
    public boolean isObserver() { return isDosen() || isAsdos(); }

    /** Mahasiswa (KETUA & ANGGOTA): peserta yang mengerjakan proyek. */
    public boolean isMahasiswa() { return isKetua() || isAnggota(); }
}
