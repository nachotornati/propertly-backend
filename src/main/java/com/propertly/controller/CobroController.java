package com.propertly.controller;

import com.propertly.db.Database;
import com.propertly.model.Cobro;
import com.propertly.model.Property;
import com.propertly.service.CobroService;
import com.propertly.service.PropertyService;
import com.propertly.service.WhatsAppService;
import io.javalin.Javalin;
import io.javalin.http.Context;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.*;
import java.util.stream.Collectors;

public class CobroController {

    private final CobroService   cobroService    = new CobroService();
    private final PropertyService propertyService = new PropertyService();

    public void register(Javalin app) {
        app.get("/api/agencies/{agencyId}/cobros-mes-actual", this::listMesActual);
        app.get("/api/agencies/{agencyId}/cobros-vencidos-anteriores", this::listVencidosAnteriores);
        app.post("/api/agencies/{agencyId}/cobros-bulk-create", this::bulkCreate);
        app.post("/api/agencies/{agencyId}/cobros-bulk-notificar", this::bulkNotificar);
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

    private void bulkCreate(Context ctx) {
        try {
            String agencyId = ctx.pathParam("agencyId");
            LocalDate mesActual = LocalDate.now().withDayOfMonth(1);

            List<Property> properties = propertyService.findAll(agencyId);
            List<Cobro> existing = cobroService.findCurrentMesByAgency(agencyId);
            Set<String> withCobro = existing.stream().map(c -> c.propertyId).collect(Collectors.toSet());

            int created = 0;
            int skipped = 0;
            for (Property p : properties) {
                if (withCobro.contains(p.id)) { skipped++; continue; }
                BigDecimal monto = p.precioActual != null ? p.precioActual : p.precio;
                Cobro c = new Cobro();
                c.propertyId = p.id;
                c.mes        = mesActual;
                c.montoBase  = monto;
                c.montoTotal = monto;
                c.pagado     = false;
                c.extras     = List.of();
                cobroService.create(c);
                created++;
            }
            ctx.json(Map.of("created", created, "skipped", skipped));
        } catch (Exception e) {
            ctx.status(500).json(Map.of("error", e.getMessage()));
        }
    }

    private void bulkNotificar(Context ctx) {
        try {
            String agencyId = ctx.pathParam("agencyId");
            LocalDate mesActual = LocalDate.now().withDayOfMonth(1);
            String frontendUrl = System.getenv().getOrDefault("FRONTEND_URL", "").stripTrailing();

            List<Property> properties = propertyService.findAll(agencyId);
            List<Cobro> existing = cobroService.findCurrentMesByAgency(agencyId);
            Map<String, Cobro> cobroByProp = existing.stream()
                    .collect(Collectors.toMap(c -> c.propertyId, c -> c));

            String mesStr = mesActual.getMonth().getDisplayName(TextStyle.FULL, new Locale("es"))
                    + " " + mesActual.getYear();

            int sent = 0, skipped = 0;
            List<String> errors = new ArrayList<>();

            for (Property p : properties) {
                Cobro cobro = cobroByProp.get(p.id);
                // skip if already paid
                if (cobro != null && cobro.pagado) { skipped++; continue; }
                // skip if no phone
                if (p.tenantPhone == null || p.tenantPhone.isBlank()) { skipped++; continue; }

                BigDecimal monto = cobro != null ? cobro.montoTotal
                        : (p.precioActual != null ? p.precioActual : p.precio);
                String montoFmt = "$ " + String.format(Locale.ENGLISH, "%,d", monto.longValue()).replace(",", ".");
                String address = (p.address != null && !p.address.isBlank()) ? p.address : p.barrio;
                String greeting = (p.tenantName != null && !p.tenantName.isBlank())
                        ? "Hola " + p.tenantName + "! 👋" : "Hola! 👋";

                String linkLine = "";
                if (p.tenantToken != null && !p.tenantToken.isBlank() && !frontendUrl.isBlank()) {
                    linkLine = "\n\n🔗 Podés ver el detalle de tu saldo del mes acá: " + frontendUrl + "/t/" + p.tenantToken;
                }

                String message = greeting + " Te recordamos que tu alquiler de *" + address
                        + "* correspondiente a " + mesStr
                        + " está pendiente de pago.\n\n💰 Monto: *" + montoFmt + "*"
                        + linkLine
                        + "\n\nCualquier consulta, estamos a disposición. 🏠";
                try {
                    new WhatsAppService().send(p.tenantPhone, message);
                    sent++;
                } catch (Exception e) {
                    errors.add(address + ": " + e.getMessage());
                }
            }
            ctx.json(Map.of("sent", sent, "skipped", skipped, "errors", errors));
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
                       p.tenant_name, p.tenant_phone, p.tenant_token
                FROM cobros c JOIN properties p ON c.property_id = p.id
                WHERE c.id = ?::uuid""";

            String phone, tenantName, address, mesStr, tenantToken;
            java.math.BigDecimal montoTotal;
            try (Connection conn = Database.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        ctx.status(404).json(Map.of("error", "Cobro no encontrado"));
                        return;
                    }
                    phone        = rs.getString("tenant_phone");
                    tenantName   = rs.getString("tenant_name");
                    address      = rs.getString("address");
                    montoTotal   = rs.getBigDecimal("monto_total");
                    tenantToken  = rs.getString("tenant_token");
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

            String linkLine = "";
            if (tenantToken != null && !tenantToken.isBlank()) {
                String frontendUrl = System.getenv().getOrDefault("FRONTEND_URL", "").stripTrailing();
                if (!frontendUrl.isBlank()) {
                    linkLine = "\n\n🔗 Podés ver el detalle de tu saldo del mes acá: " + frontendUrl + "/t/" + tenantToken;
                }
            }

            String message = greeting + " Te recordamos que tu alquiler de *" + address
                    + "* correspondiente a " + mesStr
                    + " está pendiente de pago.\n\n💰 Monto: *" + montoFmt + "*"
                    + linkLine
                    + "\n\nCualquier consulta, estamos a disposición. 🏠";

            new WhatsAppService().send(phone, message);
            ctx.json(Map.of("ok", true));
        } catch (Exception e) {
            ctx.status(500).json(Map.of("error", e.getMessage()));
        }
    }
}
