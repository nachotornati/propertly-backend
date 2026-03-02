package com.propertly.service;

import com.propertly.db.Database;
import com.propertly.model.Agency;
import org.mindrot.jbcrypt.BCrypt;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

public class AuthService {

    private static final int SESSION_DAYS = 30;
    private final AgencyService agencyService = new AgencyService();

    public Map<String, String> register(String username, String password, String agencyName) throws Exception {
        // Check username not taken
        if (findUserByUsername(username) != null) {
            throw new IllegalArgumentException("El nombre de usuario ya está en uso");
        }

        // Create agency first
        Agency agency = new Agency();
        agency.name = agencyName;
        agency.diasAntesRecordatorio = 30;
        Agency created = agencyService.create(agency);

        // Hash password and create user
        String hash = BCrypt.hashpw(password, BCrypt.gensalt());
        String userId = createUser(username, hash, created.id);

        // Create session
        String token = createSession(userId, created.id);
        return Map.of("token", token, "agencyId", created.id);
    }

    public Map<String, String> login(String username, String password) throws Exception {
        String[] userRow = findUserByUsername(username);
        if (userRow == null || !BCrypt.checkpw(password, userRow[1])) {
            throw new IllegalArgumentException("Usuario o contraseña incorrectos");
        }
        String userId = userRow[0];
        String agencyId = userRow[2];

        String token = createSession(userId, agencyId);
        return Map.of("token", token, "agencyId", agencyId);
    }

    public String validateToken(String token) {
        String sql = "SELECT agency_id FROM sessions WHERE token = ? AND expires_at > NOW()";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, token);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("agency_id");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public void resetPassword(String username, String newPassword) throws Exception {
        String[] userRow = findUserByUsername(username);
        if (userRow == null) {
            throw new IllegalArgumentException("No existe un usuario con ese nombre");
        }
        String hash = BCrypt.hashpw(newPassword, BCrypt.gensalt());
        String sql = "UPDATE users SET password_hash = ? WHERE username = ?";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, hash);
            ps.setString(2, username);
            ps.executeUpdate();
        }
    }

    public void logout(String token) throws SQLException {
        String sql = "DELETE FROM sessions WHERE token = ?";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, token);
            ps.executeUpdate();
        }
    }

    // Returns [id, password_hash, agency_id] or null
    private String[] findUserByUsername(String username) throws SQLException {
        String sql = "SELECT id, password_hash, agency_id FROM users WHERE username = ?";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return new String[]{rs.getString("id"), rs.getString("password_hash"), rs.getString("agency_id")};
            }
        }
        return null;
    }

    private String createUser(String username, String passwordHash, String agencyId) throws SQLException {
        String sql = "INSERT INTO users (username, password_hash, agency_id) VALUES (?, ?, ?::uuid) RETURNING id";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.setString(2, passwordHash);
            ps.setString(3, agencyId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("id");
            }
        }
        throw new SQLException("Failed to create user");
    }

    private String createSession(String userId, String agencyId) throws SQLException {
        String token = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
        LocalDateTime expires = LocalDateTime.now().plusDays(SESSION_DAYS);
        String sql = "INSERT INTO sessions (token, user_id, agency_id, expires_at) VALUES (?, ?::uuid, ?::uuid, ?)";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, token);
            ps.setString(2, userId);
            ps.setString(3, agencyId);
            ps.setTimestamp(4, Timestamp.valueOf(expires));
            ps.executeUpdate();
        }
        return token;
    }
}
