package com.propertly.service;

import com.propertly.db.Database;
import com.propertly.model.Property;

import java.sql.*;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

public class PropertyService {

    public List<Property> findAll(String agencyId) throws SQLException {
        List<Property> props = new ArrayList<>();
        String sql = "SELECT id, agency_id, address, barrio, moneda, precio, mes_inicio, " +
                "ajuste_meses, indice_ajuste, tenant_name, notes, created_at " +
                "FROM properties WHERE agency_id = ?::uuid ORDER BY barrio, address";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, agencyId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    props.add(mapAndCalculate(rs));
                }
            }
        }
        return props;
    }

    public List<Property> findUpcoming(String agencyId, int daysAhead) throws SQLException {
        List<Property> all = findAll(agencyId);
        List<Property> upcoming = new ArrayList<>();
        for (Property p : all) {
            if ("ARS".equals(p.moneda) && p.daysUntilAdjustment >= 0 && p.daysUntilAdjustment <= daysAhead) {
                upcoming.add(p);
            }
        }
        upcoming.sort((a, b) -> Integer.compare(a.daysUntilAdjustment, b.daysUntilAdjustment));
        return upcoming;
    }

    public Property findById(String id) throws SQLException {
        String sql = "SELECT id, agency_id, address, barrio, moneda, precio, mes_inicio, " +
                "ajuste_meses, indice_ajuste, tenant_name, notes, created_at " +
                "FROM properties WHERE id = ?::uuid";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapAndCalculate(rs);
            }
        }
        return null;
    }

    public Property create(Property prop) throws SQLException {
        String sql = "INSERT INTO properties (agency_id, address, barrio, moneda, precio, mes_inicio, " +
                "ajuste_meses, indice_ajuste, tenant_name, notes) " +
                "VALUES (?::uuid, ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id, created_at";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, prop.agencyId);
            ps.setString(2, prop.address);
            ps.setString(3, prop.barrio);
            ps.setString(4, prop.moneda);
            ps.setBigDecimal(5, prop.precio);
            ps.setDate(6, Date.valueOf(prop.mesInicio));
            ps.setInt(7, prop.ajusteMeses);
            ps.setString(8, prop.indiceAjuste);
            ps.setString(9, prop.tenantName);
            ps.setString(10, prop.notes);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    prop.id = rs.getString("id");
                    prop.createdAt = rs.getTimestamp("created_at").toLocalDateTime();
                }
            }
        }
        return findById(prop.id);
    }

    public Property update(String id, Property prop) throws SQLException {
        String sql = "UPDATE properties SET address = ?, barrio = ?, moneda = ?, precio = ?, " +
                "mes_inicio = ?, ajuste_meses = ?, indice_ajuste = ?, tenant_name = ?, notes = ? " +
                "WHERE id = ?::uuid";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, prop.address);
            ps.setString(2, prop.barrio);
            ps.setString(3, prop.moneda);
            ps.setBigDecimal(4, prop.precio);
            ps.setDate(5, Date.valueOf(prop.mesInicio));
            ps.setInt(6, prop.ajusteMeses);
            ps.setString(7, prop.indiceAjuste);
            ps.setString(8, prop.tenantName);
            ps.setString(9, prop.notes);
            ps.setString(10, id);
            ps.executeUpdate();
        }
        return findById(id);
    }

    public void delete(String id) throws SQLException {
        String sql = "DELETE FROM properties WHERE id = ?::uuid";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            ps.executeUpdate();
        }
    }

    private Property mapAndCalculate(ResultSet rs) throws SQLException {
        Property p = new Property();
        p.id = rs.getString("id");
        p.agencyId = rs.getString("agency_id");
        p.address = rs.getString("address");
        p.barrio = rs.getString("barrio");
        p.moneda = rs.getString("moneda");
        p.precio = rs.getBigDecimal("precio");
        p.mesInicio = rs.getDate("mes_inicio").toLocalDate();
        p.ajusteMeses = rs.getInt("ajuste_meses");
        p.indiceAjuste = rs.getString("indice_ajuste");
        p.tenantName = rs.getString("tenant_name");
        p.notes = rs.getString("notes");
        p.createdAt = rs.getTimestamp("created_at").toLocalDateTime();

        if ("ARS".equals(p.moneda)) {
            p.nextAdjustmentDate = calculateNextAdjustment(p.mesInicio, p.ajusteMeses);
            p.daysUntilAdjustment = (int) ChronoUnit.DAYS.between(LocalDate.now(), p.nextAdjustmentDate);
            p.adjustmentDue = p.daysUntilAdjustment <= 0;
        }

        return p;
    }

    private LocalDate calculateNextAdjustment(LocalDate mesInicio, int ajusteMeses) {
        LocalDate today = LocalDate.now();
        LocalDate candidate = mesInicio.withDayOfMonth(1);

        while (!candidate.isAfter(today)) {
            candidate = candidate.plusMonths(ajusteMeses);
        }
        return candidate;
    }
}
