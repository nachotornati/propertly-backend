package com.propertly.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.propertly.db.Database;
import com.propertly.model.AjusteInfo;
import com.propertly.model.AjusteRecord;
import com.propertly.model.Property;

import java.sql.*;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class PropertyService {

    private static final String SELECT_COLS = "id, agency_id, address, provincia, barrio, moneda, precio, mes_inicio, " +
            "ajuste_meses, duracion_meses, indice_ajuste, tenant_name, tenant_phone, tenant_email, tenant_factura, " +
            "tenant_persona_juridica, tenant_documento, unidad_funcional, notes, created_at, tenant_token, " +
            "precio_base_override, mes_base_override, historial_snapshot";

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    public List<Property> findAll(String agencyId) throws SQLException {
        List<Property> props = new ArrayList<>();
        String sql = "SELECT " + SELECT_COLS + " FROM properties WHERE agency_id = ?::uuid ORDER BY address, barrio";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, agencyId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    props.add(mapAndCalculate(rs));
                }
            }
        }
        // Auto-advance mes_base_override for ARS properties when new index data allows it.
        // Only advances forward, and never overwrites a manual price correction (precioBaseOverride).
        for (Property p : props) {
            if ("ARS".equals(p.moneda) && p.indiceAjuste != null
                    && p.precioBaseOverride == null
                    && p.nextAdjustmentDate != null
                    && p.historialAjustes != null && !p.historialAjustes.isEmpty()) {
                LocalDate expectedBase = p.nextAdjustmentDate.minusMonths(p.ajusteMeses);
                boolean needsAdvance = p.mesBaseOverride == null
                        || expectedBase.isAfter(p.mesBaseOverride);
                if (needsAdvance) {
                    try {
                        String upd = "UPDATE properties SET mes_base_override = ? WHERE id = ?::uuid";
                        try (Connection c2 = Database.getConnection();
                             PreparedStatement ps2 = c2.prepareStatement(upd)) {
                            ps2.setDate(1, Date.valueOf(expectedBase));
                            ps2.setString(2, p.id);
                            ps2.executeUpdate();
                        }
                        p.mesBaseOverride = expectedBase; // keep in-memory consistent
                    } catch (Exception ignored) {}
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
        String sql = "SELECT " + SELECT_COLS + " FROM properties WHERE id = ?::uuid";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapAndCalculate(rs);
            }
        }
        return null;
    }

    public Property findByToken(String token) throws SQLException {
        String sql = "SELECT " + SELECT_COLS + " FROM properties WHERE tenant_token = ?::uuid";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, token);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapAndCalculate(rs);
            }
        }
        return null;
    }

    public Property create(Property prop) throws SQLException {
        String sql = "INSERT INTO properties (agency_id, address, provincia, barrio, moneda, precio, mes_inicio, " +
                "ajuste_meses, duracion_meses, indice_ajuste, tenant_name, tenant_phone, tenant_email, " +
                "tenant_factura, tenant_persona_juridica, tenant_documento, unidad_funcional, notes" +
                ") VALUES (?::uuid, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id, created_at";
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
            if (prop.duracionMeses != null) ps.setInt(9, prop.duracionMeses);
            else ps.setNull(9, java.sql.Types.INTEGER);
            ps.setString(10, prop.indiceAjuste);
            ps.setString(11, prop.tenantName);
            ps.setString(12, prop.tenantPhone);
            ps.setString(13, prop.tenantEmail);
            if (prop.tenantFactura != null) ps.setBoolean(14, prop.tenantFactura);
            else ps.setNull(14, java.sql.Types.BOOLEAN);
            if (prop.tenantPersonaJuridica != null) ps.setBoolean(15, prop.tenantPersonaJuridica);
            else ps.setNull(15, java.sql.Types.BOOLEAN);
            ps.setString(16, prop.tenantDocumento);
            ps.setString(17, prop.unidadFuncional);
            ps.setString(18, prop.notes);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    prop.id = rs.getString("id");
                    prop.createdAt = rs.getTimestamp("created_at").toLocalDateTime();
                }
            }
        }

        // If the user indicates the next adjustment month, store mesBaseOverride so the
        // adjustment cycle is anchored correctly. Price is auto-calculated (no precioBaseOverride).
        if ("ARS".equals(prop.moneda) && prop.proximoMesAjusteInput != null) {
            YearMonth proximoMes = YearMonth.parse(prop.proximoMesAjusteInput);
            LocalDate mesBase = proximoMes.minusMonths(prop.ajusteMeses).atDay(1);
            String updateSql = "UPDATE properties SET mes_base_override = ? WHERE id = ?::uuid";
            try (Connection conn2 = Database.getConnection();
                 PreparedStatement ps2 = conn2.prepareStatement(updateSql)) {
                ps2.setDate(1, Date.valueOf(mesBase));
                ps2.setString(2, prop.id);
                ps2.executeUpdate();
            }
        }

        return findById(prop.id);
    }

    public Property update(String id, Property prop) throws SQLException {
        Property current = findById(id);

        // Detect if adjustment settings changed
        boolean settingsChanged = current != null && "ARS".equals(current.moneda)
                && current.indiceAjuste != null
                && (prop.ajusteMeses != current.ajusteMeses
                    || !Objects.equals(prop.indiceAjuste, current.indiceAjuste));

        // moneda, precio, mes_inicio are NOT updated — locked after creation
        // When adjustment settings change, clear overrides so history recalculates from scratch
        String sql = "UPDATE properties SET address = ?, provincia = ?, barrio = ?, " +
                "ajuste_meses = ?, duracion_meses = ?, indice_ajuste = ?, tenant_name = ?, tenant_phone = ?, " +
                "tenant_email = ?, tenant_factura = ?, tenant_persona_juridica = ?, tenant_documento = ?, " +
                "unidad_funcional = ?, notes = ?" +
                (settingsChanged ? ", precio_base_override = NULL, mes_base_override = NULL, historial_snapshot = NULL" : "") +
                " WHERE id = ?::uuid";

        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, prop.address);
            ps.setString(2, prop.provincia);
            ps.setString(3, prop.barrio);
            ps.setInt(4, prop.ajusteMeses);
            if (prop.duracionMeses != null) ps.setInt(5, prop.duracionMeses);
            else ps.setNull(5, java.sql.Types.INTEGER);
            ps.setString(6, prop.indiceAjuste);
            ps.setString(7, prop.tenantName);
            ps.setString(8, prop.tenantPhone);
            ps.setString(9, prop.tenantEmail);
            if (prop.tenantFactura != null) ps.setBoolean(10, prop.tenantFactura);
            else ps.setNull(10, java.sql.Types.BOOLEAN);
            if (prop.tenantPersonaJuridica != null) ps.setBoolean(11, prop.tenantPersonaJuridica);
            else ps.setNull(11, java.sql.Types.BOOLEAN);
            ps.setString(12, prop.tenantDocumento);
            ps.setString(13, prop.unidadFuncional);
            ps.setString(14, prop.notes);
            ps.setString(15, id);
            ps.executeUpdate();
        }

        // If user provides a corrected current price, override from the current cycle start.
        // This runs AFTER settingsChanged may have cleared overrides, so it always wins.
        // Use current.moneda (not prop.moneda) because moneda is locked and disabled fields may be null.
        if (current != null && "ARS".equals(current.moneda) && prop.precioActualInput != null
                && current.nextAdjustmentDate != null) {
            LocalDate mesBase = current.nextAdjustmentDate.minusMonths(current.ajusteMeses);
            String overrideSql = "UPDATE properties SET precio_base_override = ?, mes_base_override = ? WHERE id = ?::uuid";
            try (Connection conn2 = Database.getConnection();
                 PreparedStatement ps2 = conn2.prepareStatement(overrideSql)) {
                ps2.setBigDecimal(1, prop.precioActualInput);
                ps2.setDate(2, Date.valueOf(mesBase));
                ps2.setString(3, id);
                ps2.executeUpdate();
            }
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
        int dm = rs.getInt("duracion_meses");
        p.duracionMeses = rs.wasNull() ? null : dm;
        p.indiceAjuste = rs.getString("indice_ajuste");
        p.tenantName = rs.getString("tenant_name");
        p.tenantPhone = rs.getString("tenant_phone");
        p.tenantEmail = rs.getString("tenant_email");
        boolean tf = rs.getBoolean("tenant_factura"); p.tenantFactura = rs.wasNull() ? null : tf;
        boolean tpj = rs.getBoolean("tenant_persona_juridica"); p.tenantPersonaJuridica = rs.wasNull() ? null : tpj;
        p.tenantDocumento = rs.getString("tenant_documento");
        p.unidadFuncional = rs.getString("unidad_funcional");
        p.notes = rs.getString("notes");
        p.createdAt = rs.getTimestamp("created_at").toLocalDateTime();
        p.tenantToken = rs.getString("tenant_token");

        // Read override columns
        java.math.BigDecimal precioBaseOverride = rs.getBigDecimal("precio_base_override");
        if (!rs.wasNull()) p.precioBaseOverride = precioBaseOverride;
        Date mesBaseOverrideDate = rs.getDate("mes_base_override");
        if (mesBaseOverrideDate != null) p.mesBaseOverride = mesBaseOverrideDate.toLocalDate();
        p.historialSnapshotJson = rs.getString("historial_snapshot");

        if ("ARS".equals(p.moneda) && p.indiceAjuste != null) {
            // hasOverride only needs mesBaseOverride; precioBaseOverride is optional (manual correction)
            boolean hasOverride = p.mesBaseOverride != null;
            LocalDate baseDate = hasOverride ? p.mesBaseOverride : p.mesInicio;

            p.nextAdjustmentDate = calculateNextAdjustment(baseDate, p.ajusteMeses);
            p.daysUntilAdjustment = (int) ChronoUnit.DAYS.between(LocalDate.now(), p.nextAdjustmentDate);
            p.adjustmentDue = p.daysUntilAdjustment <= 0;

            List<AjusteRecord> historial = new ArrayList<>();
            java.math.BigDecimal precioActual;

            if (hasOverride) {
                // Auto-calculate history from mesInicio up through mesBaseOverride.
                // When no manual price override exists, INCLUDE the mesBaseOverride date so
                // the adjustment that anchors the cycle is captured (e.g. the last auto-advanced date).
                // When a manual override exists, exclude it — the base price comes from precioBaseOverride.
                java.math.BigDecimal historicalPrice = p.precio;
                for (LocalDate adjDate : getPastAdjustmentDates(p.mesInicio, p.ajusteMeses)) {
                    boolean pastBase = p.precioBaseOverride != null
                            ? !adjDate.isBefore(p.mesBaseOverride)   // manual override: exclude base date
                            : adjDate.isAfter(p.mesBaseOverride);    // auto-calculated: include base date
                    if (pastBase) break;
                    YearMonth hasta = YearMonth.from(adjDate);
                    YearMonth desde = hasta.minusMonths(p.ajusteMeses);
                    java.util.Optional<AjusteInfo> infoOpt =
                            calcularAjusteConFallback(p.indiceAjuste, desde, hasta, historicalPrice);
                    if (infoOpt.isPresent()) {
                        AjusteInfo info = infoOpt.get();
                        if (!info.estimado) {
                            historial.add(new AjusteRecord(adjDate, historicalPrice, info.nuevoPrecio, info.coeficiente));
                        }
                        historicalPrice = info.nuevoPrecio;
                    } else {
                        break;
                    }
                }
                // Use manual override price if provided; otherwise use the auto-calculated price
                precioActual = p.precioBaseOverride != null ? p.precioBaseOverride : historicalPrice;
            } else {
                precioActual = p.precio;
            }

            // Calculate adjustments from baseDate onwards with current settings
            for (LocalDate adjDate : getPastAdjustmentDates(baseDate, p.ajusteMeses)) {
                YearMonth hasta = YearMonth.from(adjDate);
                YearMonth desde = hasta.minusMonths(p.ajusteMeses);
                java.util.Optional<AjusteInfo> infoOpt =
                        calcularAjusteConFallback(p.indiceAjuste, desde, hasta, precioActual);
                if (infoOpt.isPresent()) {
                    AjusteInfo info = infoOpt.get();
                    if (!info.estimado) {
                        historial.add(new AjusteRecord(adjDate, precioActual, info.nuevoPrecio, info.coeficiente));
                    }
                    precioActual = info.nuevoPrecio;
                } else {
                    break;
                }
            }

            p.precioActual = precioActual;
            p.historialAjustes = historial;

            YearMonth hastaNext = YearMonth.from(p.nextAdjustmentDate);
            YearMonth desdeNext = hastaNext.minusMonths(p.ajusteMeses);
            p.ajusteInfo = calcularAjusteConFallback(p.indiceAjuste, desdeNext, hastaNext, precioActual)
                    .orElse(null);
        }

        return p;
    }

    private List<LocalDate> getPastAdjustmentDates(LocalDate baseDate, int ajusteMeses) {
        List<LocalDate> past = new ArrayList<>();
        LocalDate today = LocalDate.now();
        LocalDate candidate = baseDate.withDayOfMonth(1).plusMonths(ajusteMeses);
        while (!candidate.isAfter(today)) {
            past.add(candidate);
            candidate = candidate.plusMonths(ajusteMeses);
        }
        return past;
    }

    private LocalDate calculateNextAdjustment(LocalDate baseDate, int ajusteMeses) {
        LocalDate today = LocalDate.now();
        LocalDate candidate = baseDate.withDayOfMonth(1);
        while (!candidate.isAfter(today)) {
            candidate = candidate.plusMonths(ajusteMeses);
        }
        return candidate;
    }

    /**
     * If the requested window has estimated (unpublished) months, fall back to the
     * previous window (shifted back 1 month) which uses only real published data.
     */
    private java.util.Optional<AjusteInfo> calcularAjusteConFallback(
            String indice, YearMonth desde, YearMonth hasta, java.math.BigDecimal precio) {
        java.util.Optional<AjusteInfo> result = IndiceService.getInstance().calcularAjuste(indice, desde, hasta, precio);
        if (result.isPresent() && result.get().estimado) {
            java.util.Optional<AjusteInfo> fallback = IndiceService.getInstance()
                    .calcularAjuste(indice, desde.minusMonths(1), hasta.minusMonths(1), precio);
            if (fallback.isPresent() && !fallback.get().estimado) {
                return fallback;
            }
        }
        return result;
    }
}
