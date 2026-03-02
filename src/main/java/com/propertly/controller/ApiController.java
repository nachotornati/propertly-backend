package com.propertly.controller;

import com.propertly.model.Agency;
import com.propertly.model.Property;
import com.propertly.service.AgencyService;
import com.propertly.service.PropertyService;
import io.javalin.Javalin;
import io.javalin.http.Context;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ApiController {

    private final AgencyService agencyService = new AgencyService();
    private final PropertyService propertyService = new PropertyService();
    private final CobroController cobroController = new CobroController();

    public void register(Javalin app) {
        cobroController.register(app);
        // Health check
        app.get("/health", ctx -> ctx.json(Map.of("status", "ok")));

        // Agencies
        app.get("/api/agencies", this::listAgencies);
        app.post("/api/agencies", this::createAgency);
        app.put("/api/agencies/{id}", this::updateAgency);
        app.delete("/api/agencies/{id}", this::deleteAgency);

        // Properties
        app.get("/api/agencies/{agencyId}/properties", this::listProperties);
        app.post("/api/agencies/{agencyId}/properties", this::createProperty);
        app.put("/api/properties/{id}", this::updateProperty);
        app.delete("/api/properties/{id}", this::deleteProperty);

        // Upcoming adjustments
        app.get("/api/agencies/{agencyId}/reminders", this::getReminders);
    }

    private void listAgencies(Context ctx) {
        try {
            ctx.json(agencyService.findAll());
        } catch (Exception e) {
            ctx.status(500).json(Map.of("error", e.getMessage()));
        }
    }

    private void createAgency(Context ctx) {
        try {
            Agency agency = ctx.bodyAsClass(Agency.class);
            ctx.status(201).json(agencyService.create(agency));
        } catch (Exception e) {
            ctx.status(500).json(Map.of("error", e.getMessage()));
        }
    }

    private void updateAgency(Context ctx) {
        try {
            Agency agency = ctx.bodyAsClass(Agency.class);
            Agency updated = agencyService.update(ctx.pathParam("id"), agency);
            if (updated == null) ctx.status(404).json(Map.of("error", "Not found"));
            else ctx.json(updated);
        } catch (Exception e) {
            ctx.status(500).json(Map.of("error", e.getMessage()));
        }
    }

    private void deleteAgency(Context ctx) {
        try {
            agencyService.delete(ctx.pathParam("id"));
            ctx.status(204);
        } catch (Exception e) {
            ctx.status(500).json(Map.of("error", e.getMessage()));
        }
    }

    private void listProperties(Context ctx) {
        try {
            ctx.json(propertyService.findAll(ctx.pathParam("agencyId")));
        } catch (Exception e) {
            ctx.status(500).json(Map.of("error", e.getMessage()));
        }
    }

    private void createProperty(Context ctx) {
        try {
            Property prop = ctx.bodyAsClass(Property.class);
            prop.agencyId = ctx.pathParam("agencyId");
            List<String> errors = validateProperty(prop, true);
            if (!errors.isEmpty()) { ctx.status(400).json(Map.of("error", String.join("; ", errors))); return; }
            ctx.status(201).json(propertyService.create(prop));
        } catch (Exception e) {
            ctx.status(500).json(Map.of("error", e.getMessage()));
        }
    }

    private void updateProperty(Context ctx) {
        try {
            Property prop = ctx.bodyAsClass(Property.class);
            List<String> errors = validateProperty(prop, false);
            if (!errors.isEmpty()) { ctx.status(400).json(Map.of("error", String.join("; ", errors))); return; }
            Property updated = propertyService.update(ctx.pathParam("id"), prop);
            if (updated == null) ctx.status(404).json(Map.of("error", "Not found"));
            else ctx.json(updated);
        } catch (Exception e) {
            ctx.status(500).json(Map.of("error", e.getMessage()));
        }
    }

    /** Returns a list of validation error messages. Empty list = valid. */
    private List<String> validateProperty(Property prop, boolean isCreate) {
        List<String> errors = new ArrayList<>();
        if (isCreate) {
            if (prop.mesInicio == null) {
                errors.add("Mes de inicio es requerido");
            } else if (prop.mesInicio.isAfter(YearMonth.now().atEndOfMonth())) {
                errors.add("El mes de inicio no puede ser futuro");
            }
            if (prop.precio == null || prop.precio.signum() <= 0) {
                errors.add("El precio debe ser mayor a 0");
            }
        }
        if (prop.ajusteMeses < 1) {
            errors.add("El período de ajuste debe ser al menos 1 mes");
        }
        if (prop.duracionMeses != null && prop.duracionMeses < 1) {
            errors.add("La duración del contrato debe ser al menos 1 mes");
        }
        return errors;
    }

    private void deleteProperty(Context ctx) {
        try {
            propertyService.delete(ctx.pathParam("id"));
            ctx.status(204);
        } catch (Exception e) {
            ctx.status(500).json(Map.of("error", e.getMessage()));
        }
    }

    private void getReminders(Context ctx) {
        try {
            int daysAhead = Integer.parseInt(ctx.queryParamAsClass("days", String.class).getOrDefault("30"));
            ctx.json(propertyService.findUpcoming(ctx.pathParam("agencyId"), daysAhead));
        } catch (Exception e) {
            ctx.status(500).json(Map.of("error", e.getMessage()));
        }
    }
}
