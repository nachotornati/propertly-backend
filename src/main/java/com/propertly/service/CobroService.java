package com.propertly.service;

import com.propertly.db.Database;
import com.propertly.model.Cobro;
import com.propertly.model.CobroExtra;
import com.propertly.model.CobroVencidoAnterior;
import com.propertly.model.Property;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CobroService {

    public List<Cobro> findByProperty(String propertyId) throws SQLException {
        List<Cobro> cobros = new ArrayList<>();
        String sql = "SELECT id, property_id, mes, monto_base, monto_total, pagado, vencido, fecha_pago, notes, created_at " +
                "FROM cobros WHERE property_id = ?::uuid ORDER BY mes DESC";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, propertyId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) cobros.add(mapRow(rs));
            }
        }
        for (Cobro c : cobros) c.extras = findExtras(c.id);
        return cobros;
    }

    public List<Cobro> findCurrentMesByAgency(String agencyId) throws SQLException {
        Date mesActual = Date.valueOf(YearMonth.now().atDay(1));
        List<Cobro> cobros = new ArrayList<>();
        String sql = "SELECT c.id, c.property_id, c.mes, c.monto_base, c.monto_total, c.pagado, c.vencido, c.fecha_pago, c.notes, c.created_at " +
                "FROM cobros c JOIN properties p ON c.property_id = p.id " +
                "WHERE p.agency_id = ?::uuid AND c.mes = ?";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, agencyId);
            ps.setDate(2, mesActual);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) cobros.add(mapRow(rs));
            }
        }
        for (Cobro c : cobros) c.extras = findExtras(c.id);
        return cobros;
    }

    public Cobro findById(String id) throws SQLException {
        String sql = "SELECT id, property_id, mes, monto_base, monto_total, pagado, vencido, fecha_pago, notes, created_at " +
                "FROM cobros WHERE id = ?::uuid";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Cobro c = mapRow(rs);
                    c.extras = findExtras(c.id);
                    return c;
                }
            }
        }
        return null;
    }

    public Cobro create(Cobro cobro) throws SQLException {
        cobro.montoTotal = calcTotal(cobro);
        String sql = "INSERT INTO cobros (property_id, mes, monto_base, monto_total, pagado, fecha_pago, notes, vencido) " +
                "VALUES (?::uuid, ?, ?, ?, ?, ?, ?, ?) RETURNING id, created_at";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, cobro.propertyId);
            ps.setDate(2, Date.valueOf(cobro.mes));
            ps.setBigDecimal(3, cobro.montoBase);
            ps.setBigDecimal(4, cobro.montoTotal);
            ps.setBoolean(5, cobro.pagado);
            ps.setDate(6, cobro.fechaPago != null ? Date.valueOf(cobro.fechaPago) : null);
            ps.setString(7, cobro.notes);
            ps.setBoolean(8, cobro.vencido);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    cobro.id = rs.getString("id");
                    cobro.createdAt = rs.getTimestamp("created_at").toLocalDateTime();
                }
            }
        }
        saveExtras(cobro.id, cobro.extras);
        return findById(cobro.id);
    }

    public Cobro update(String id, Cobro cobro) throws SQLException {
        cobro.montoTotal = calcTotal(cobro);
        String sql = "UPDATE cobros SET mes = ?, monto_base = ?, monto_total = ?, pagado = ?, fecha_pago = ?, notes = ?, vencido = ? " +
                "WHERE id = ?::uuid";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(cobro.mes));
            ps.setBigDecimal(2, cobro.montoBase);
            ps.setBigDecimal(3, cobro.montoTotal);
            ps.setBoolean(4, cobro.pagado);
            ps.setDate(5, cobro.fechaPago != null ? Date.valueOf(cobro.fechaPago) : null);
            ps.setString(6, cobro.notes);
            ps.setBoolean(7, cobro.vencido);
            ps.setString(8, id);
            ps.executeUpdate();
        }
        deleteExtras(id);
        saveExtras(id, cobro.extras);
        return findById(id);
    }

    public void delete(String id) throws SQLException {
        String sql = "DELETE FROM cobros WHERE id = ?::uuid";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            ps.executeUpdate();
        }
    }

    private List<CobroExtra> findExtras(String cobroId) throws SQLException {
        List<CobroExtra> extras = new ArrayList<>();
        String sql = "SELECT id, cobro_id, descripcion, monto FROM cobro_extras WHERE cobro_id = ?::uuid ORDER BY id";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, cobroId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    CobroExtra e = new CobroExtra();
                    e.id = rs.getString("id");
                    e.cobroId = rs.getString("cobro_id");
                    e.descripcion = rs.getString("descripcion");
                    e.monto = rs.getBigDecimal("monto");
                    extras.add(e);
                }
            }
        }
        return extras;
    }

    private void saveExtras(String cobroId, List<CobroExtra> extras) throws SQLException {
        if (extras == null || extras.isEmpty()) return;
        String sql = "INSERT INTO cobro_extras (cobro_id, descripcion, monto) VALUES (?::uuid, ?, ?)";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (CobroExtra e : extras) {
                ps.setString(1, cobroId);
                ps.setString(2, e.descripcion);
                ps.setBigDecimal(3, e.monto);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private void deleteExtras(String cobroId) throws SQLException {
        String sql = "DELETE FROM cobro_extras WHERE cobro_id = ?::uuid";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, cobroId);
            ps.executeUpdate();
        }
    }

    private BigDecimal calcTotal(Cobro cobro) {
        BigDecimal total = cobro.montoBase != null ? cobro.montoBase : BigDecimal.ZERO;
        if (cobro.extras != null) {
            for (CobroExtra e : cobro.extras) {
                if (e.monto != null) total = total.add(e.monto);
            }
        }
        return total;
    }

    public List<CobroVencidoAnterior> findVencidosAnteriores(String agencyId) throws SQLException {
        LocalDate currentMonthStart = LocalDate.now().withDayOfMonth(1);
        LocalDate lookbackStart = currentMonthStart.minusMonths(3);

        List<Property> properties = new PropertyService().findAll(agencyId);

        // Fetch all cobros in lookback window for this agency
        Map<String, Map<LocalDate, Cobro>> cobroMap = new HashMap<>();
        String sql = "SELECT c.id, c.property_id, c.mes, c.monto_base, c.monto_total, c.pagado, c.vencido, c.fecha_pago, c.notes, c.created_at " +
                "FROM cobros c JOIN properties p ON c.property_id = p.id " +
                "WHERE p.agency_id = ?::uuid AND c.mes >= ? AND c.mes < ?";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, agencyId);
            ps.setDate(2, Date.valueOf(lookbackStart));
            ps.setDate(3, Date.valueOf(currentMonthStart));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Cobro c = mapRow(rs);
                    cobroMap.computeIfAbsent(c.propertyId, k -> new HashMap<>()).put(c.mes, c);
                }
            }
        }

        List<CobroVencidoAnterior> result = new ArrayList<>();
        for (Property prop : properties) {
            LocalDate rangeStart = prop.mesInicio.withDayOfMonth(1);
            // Never look back before the month the property was registered in the system
            LocalDate createdMonth = prop.createdAt.toLocalDate().withDayOfMonth(1);
            if (rangeStart.isBefore(createdMonth)) rangeStart = createdMonth;
            if (rangeStart.isBefore(lookbackStart)) rangeStart = lookbackStart;

            LocalDate month = rangeStart;
            while (month.isBefore(currentMonthStart)) {
                // Skip if contract has expired for this month
                if (prop.duracionMeses != null) {
                    YearMonth contractEnd = YearMonth.from(prop.mesInicio).plusMonths(prop.duracionMeses);
                    if (!contractEnd.isAfter(YearMonth.from(month))) {
                        month = month.plusMonths(1);
                        continue;
                    }
                }

                Map<LocalDate, Cobro> propCobros = cobroMap.getOrDefault(prop.id, new HashMap<>());
                Cobro cobro = propCobros.get(month);

                if (cobro == null) {
                    result.add(new CobroVencidoAnterior(prop, month, null));
                } else if (!cobro.pagado) {
                    result.add(new CobroVencidoAnterior(prop, month, cobro));
                }

                month = month.plusMonths(1);
            }
        }

        result.sort((a, b) -> {
            int c = b.mes.compareTo(a.mes);
            return c != 0 ? c : a.property.address.compareTo(b.property.address);
        });

        return result;
    }

    private Cobro mapRow(ResultSet rs) throws SQLException {
        Cobro c = new Cobro();
        c.id = rs.getString("id");
        c.propertyId = rs.getString("property_id");
        c.mes = rs.getDate("mes").toLocalDate();
        c.montoBase = rs.getBigDecimal("monto_base");
        c.montoTotal = rs.getBigDecimal("monto_total");
        c.pagado = rs.getBoolean("pagado");
        c.vencido = rs.getBoolean("vencido");
        Date fp = rs.getDate("fecha_pago");
        c.fechaPago = fp != null ? fp.toLocalDate() : null;
        c.notes = rs.getString("notes");
        c.createdAt = rs.getTimestamp("created_at").toLocalDateTime();
        return c;
    }
}
