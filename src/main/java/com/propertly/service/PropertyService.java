package com.propertly.service;

import com.propertly.db.Database;
import com.propertly.model.AjusteInfo;
import com.propertly.model.AjusteRecord;
import com.propertly.model.Property;

import java.sql.*;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

public class PropertyService {

    public List<Property> findAll(String agencyId) throws SQLException {
        List<Property> props = new ArrayList<>();
        String sql = "SELECT id, agency_id, address, provincia, barrio, moneda, precio, mes_inicio, " +
                "ajuste_meses, indice_ajuste, tenant_name, tenant_phone, notes, created_at " +
                "FROM properties WHERE agency_id = ?::uuid ORDER BY address, barrio";
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
        String sql = "SELECT id, agency_id, address, provincia, barrio, moneda, precio, mes_inicio, " +
                "ajuste_meses, indice_ajuste, tenant_name, tenant_phone, notes, created_at " +
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
        String sql = "INSERT INTO properties (agency_id, address, provincia, barrio, moneda, precio, mes_inicio, " +
                "ajuste_meses, indice_ajuste, tenant_name, tenant_phone, notes) " +
                "VALUES (?::uuid, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id, created_at";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, prop.agencyId);
            ps.setString(2, prop.address);
            ps.setString(3, prop.provincia);
            ps.setString(4, prop.barrio);
            ps.setString(5, prop.moneda);
            ps.setBigDecimal(6, prop.precio);
            ps.setDate(7, Date.valueOf(prop.mesInicio));
            ps.setInt(8, prop.ajusteMeses);
            ps.setString(9, prop.indiceAjuste);
            ps.setString(10, prop.tenantName);
            ps.setString(11, prop.tenantPhone);
            ps.setString(12, prop.notes);
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
        String sql = "UPDATE properties SET address = ?, provincia = ?, barrio = ?, moneda = ?, precio = ?, " +
                "mes_inicio = ?, ajuste_meses = ?, indice_ajuste = ?, tenant_name = ?, tenant_phone = ?, notes = ? " +
                "WHERE id = ?::uuid";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, prop.address);
            ps.setString(2, prop.provincia);
            ps.setString(3, prop.barrio);
            ps.setString(4, prop.moneda);
            ps.setBigDecimal(5, prop.precio);
            ps.setDate(6, Date.valueOf(prop.mesInicio));
            ps.setInt(7, prop.ajusteMeses);
            ps.setString(8, prop.indiceAjuste);
            ps.setString(9, prop.tenantName);
            ps.setString(10, prop.tenantPhone);
            ps.setString(11, prop.notes);
            ps.setString(12, id);
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
        p.provincia = rs.getString("provincia");
        p.barrio = rs.getString("barrio");
        p.moneda = rs.getString("moneda");
        p.precio = rs.getBigDecimal("precio");
        p.mesInicio = rs.getDate("mes_inicio").toLocalDate();
        p.ajusteMeses = rs.getInt("ajuste_meses");
        p.indiceAjuste = rs.getString("indice_ajuste");
        p.tenantName = rs.getString("tenant_name");
        p.tenantPhone = rs.getString("tenant_phone");
        p.notes = rs.getString("notes");
        p.createdAt = rs.getTimestamp("created_at").toLocalDateTime();

        if ("ARS".equals(p.moneda) && p.indiceAjuste != null) {
            p.nextAdjustmentDate = calculateNextAdjustment(p.mesInicio, p.ajusteMeses);
            p.daysUntilAdjustment = (int) ChronoUnit.DAYS.between(LocalDate.now(), p.nextAdjustmentDate);
            p.adjustmentDue = p.daysUntilAdjustment <= 0;

            // Compute all past adjustments to derive the current accumulated price
            List<AjusteRecord> historial = new ArrayList<>();
            java.math.BigDecimal precioActual = p.precio;

            for (LocalDate adjDate : getPastAdjustmentDates(p.mesInicio, p.ajusteMeses)) {
                YearMonth hasta = YearMonth.from(adjDate);
                YearMonth desde = hasta.minusMonths(p.ajusteMeses);
                java.util.Optional<AjusteInfo> infoOpt =
                        IndiceService.getInstance().calcularAjuste(p.indiceAjuste, desde, hasta, precioActual);
                if (infoOpt.isPresent()) {
                    AjusteInfo info = infoOpt.get();
                    if (!info.estimado) {
                        // Only record confirmed (non-estimated) past adjustments in history
                        historial.add(new AjusteRecord(adjDate, precioActual, info.nuevoPrecio, info.coeficiente));
                    }
                    precioActual = info.nuevoPrecio;
                } else {
                    break; // can't continue without index data
                }
            }

            p.precioActual = precioActual;
            p.historialAjustes = historial;

            // Next adjustment estimate uses the accumulated current price as base
            YearMonth hastaNext = YearMonth.from(p.nextAdjustmentDate);
            YearMonth desdeNext = hastaNext.minusMonths(p.ajusteMeses);
            p.ajusteInfo = IndiceService.getInstance()
                    .calcularAjuste(p.indiceAjuste, desdeNext, hastaNext, precioActual)
                    .orElse(null);
        }

        return p;
    }

    /**
     * Returns all past adjustment dates (already passed, from first adjustment up to today).
     */
    private List<LocalDate> getPastAdjustmentDates(LocalDate mesInicio, int ajusteMeses) {
        List<LocalDate> past = new ArrayList<>();
        LocalDate today = LocalDate.now();
        LocalDate candidate = mesInicio.withDayOfMonth(1).plusMonths(ajusteMeses);
        while (!candidate.isAfter(today)) {
            past.add(candidate);
            candidate = candidate.plusMonths(ajusteMeses);
        }
        return past;
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
