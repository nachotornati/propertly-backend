package com.propertly.controller;

import com.propertly.db.Database;
import com.propertly.model.Cobro;
import com.propertly.service.CobroService;
import com.propertly.service.WhatsAppService;
import io.javalin.Javalin;
import io.javalin.http.Context;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.Locale;
import java.util.Map;

public class CobroController {

    private final CobroService cobroService = new CobroService();

    public void register(Javalin app) {
        app.get("/api/agencies/{agencyId}/cobros-mes-actual", this::listMesActual);
        app.get("/api/agencies/{agencyId}/cobros-vencidos-anteriores", this::listVencidosAnteriores);
        app.get("/api/properties/{propertyId}/cobros", this::list);
        app.post("/api/properties/{propertyId}/cobros", this::create);
        app.put("/api/cobros/{id}", this::update);
        app.delete("/api/cobros/{id}", this::delete);
        app.post("/api/cobros/{id}/notificar", this::notificar);
    }

    private void listMesActual(Context ctx) {
        try {
            ctx.json(cobroService.findCurrentMesByAgency(ctx.pathParam("agencyId")));
        } catch (Exception e) {
            ctx.status(500).json(Map.of("error", e.getMessage()));
        }
    }

    private void listVencidosAnteriores(Context ctx) {
        try {
            ctx.json(cobroService.findVencidosAnteriores(ctx.pathParam("agencyId")));
        } catch (Exception e) {
            ctx.status(500).json(Map.of("error", e.getMessage()));
        }
    }

    private void list(Context ctx) {
        try {
            ctx.json(cobroService.findByProperty(ctx.pathParam("propertyId")));
        } catch (Exception e) {
            ctx.status(500).json(Map.of("error", e.getMessage()));
        }
    }

    private void create(Context ctx) {
        try {
            Cobro cobro = ctx.bodyAsClass(Cobro.class);
            cobro.propertyId = ctx.pathParam("propertyId");
            ctx.status(201).json(cobroService.create(cobro));
        } catch (Exception e) {
            ctx.status(500).json(Map.of("error", e.getMessage()));
        }
    }

    private void update(Context ctx) {
        try {
            Cobro cobro = ctx.bodyAsClass(Cobro.class);
            Cobro updated = cobroService.update(ctx.pathParam("id"), cobro);
            if (updated == null) ctx.status(404).json(Map.of("error", "Not found"));
            else ctx.json(updated);
        } catch (Exception e) {
            ctx.status(500).json(Map.of("error", e.getMessage()));
        }
    }

    private void delete(Context ctx) {
        try {
            cobroService.delete(ctx.pathParam("id"));
            ctx.status(204);
        } catch (Exception e) {
            ctx.status(500).json(Map.of("error", e.getMessage()));
        }
    }

    private void notificar(Context ctx) {
        try {
            String id = ctx.pathParam("id");
            String sql = """
                SELECT c.monto_total, c.mes,
                       COALESCE(p.address, p.barrio) AS address,
                       p.tenant_name, p.tenant_phone
                FROM cobros c JOIN properties p ON c.property_id = p.id
                WHERE c.id = ?::uuid""";

            String phone, tenantName, address, mesStr;
            java.math.BigDecimal montoTotal;
            try (Connection conn = Database.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        ctx.status(404).json(Map.of("error", "Cobro no encontrado"));
                        return;
                    }
                    phone       = rs.getString("tenant_phone");
                    tenantName  = rs.getString("tenant_name");
                    address     = rs.getString("address");
                    montoTotal  = rs.getBigDecimal("monto_total");
                    LocalDate mes = rs.getDate("mes").toLocalDate();
                    mesStr = mes.getMonth().getDisplayName(TextStyle.FULL, new Locale("es"))
                             + " " + mes.getYear();
                }
            }

            if (phone == null || phone.isBlank()) {
                ctx.status(400).json(Map.of("error", "El inquilino no tiene teléfono registrado"));
                return;
            }

            String greeting = (tenantName != null && !tenantName.isBlank())
                    ? "Hola " + tenantName + "! 👋"
                    : "Hola! 👋";
            long monto = montoTotal.longValue();
            String montoFmt = "$ " + String.format(Locale.ENGLISH, "%,d", monto).replace(",", ".");
            String message = greeting + " Te recordamos que tu alquiler de *" + address
                    + "* correspondiente a " + mesStr
                    + " está pendiente de pago.\n\n💰 Monto: *" + montoFmt + "*\n\nCualquier consulta, estamos a disposición. 🏠";

            new WhatsAppService().send(phone, message);
            ctx.json(Map.of("ok", true));
        } catch (Exception e) {
            ctx.status(500).json(Map.of("error", e.getMessage()));
        }
    }
}
