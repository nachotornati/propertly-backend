package com.propertly.service;

import com.propertly.db.Database;
import com.propertly.model.Agency;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class AgencyService {

    public List<Agency> findAll() throws SQLException {
        List<Agency> agencies = new ArrayList<>();
        String sql = "SELECT id, name, email, dias_antes_recordatorio, created_at FROM agencies ORDER BY name";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                agencies.add(map(rs));
            }
        }
        return agencies;
    }

    public Agency findById(String id) throws SQLException {
        String sql = "SELECT id, name, email, dias_antes_recordatorio, created_at FROM agencies WHERE id = ?::uuid";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return map(rs);
            }
        }
        return null;
    }

    public Agency create(Agency agency) throws SQLException {
        String sql = "INSERT INTO agencies (name, email, dias_antes_recordatorio) VALUES (?, ?, ?) RETURNING id, created_at";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, agency.name);
            ps.setString(2, agency.email);
            ps.setInt(3, agency.diasAntesRecordatorio);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    agency.id = rs.getString("id");
                    agency.createdAt = rs.getTimestamp("created_at").toLocalDateTime();
                }
            }
        }
        return agency;
    }

    public Agency update(String id, Agency agency) throws SQLException {
        String sql = "UPDATE agencies SET name = ?, email = ?, dias_antes_recordatorio = ? WHERE id = ?::uuid";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, agency.name);
            ps.setString(2, agency.email);
            ps.setInt(3, agency.diasAntesRecordatorio);
            ps.setString(4, id);
            ps.executeUpdate();
        }
        return findById(id);
    }

    public void delete(String id) throws SQLException {
        String sql = "DELETE FROM agencies WHERE id = ?::uuid";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            ps.executeUpdate();
        }
    }

    private Agency map(ResultSet rs) throws SQLException {
        return new Agency(
                rs.getString("id"),
                rs.getString("name"),
                rs.getString("email"),
                rs.getInt("dias_antes_recordatorio"),
                rs.getTimestamp("created_at").toLocalDateTime()
        );
    }
}
