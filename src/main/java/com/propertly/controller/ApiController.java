package com.propertly.controller;

import com.propertly.model.Agency;
import com.propertly.model.Property;
import com.propertly.service.AgencyService;
import com.propertly.service.PropertyService;
import io.javalin.Javalin;
import io.javalin.http.Context;

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
            ctx.status(201).json(propertyService.create(prop));
        } catch (Exception e) {
            ctx.status(500).json(Map.of("error", e.getMessage()));
        }
    }

    private void updateProperty(Context ctx) {
        try {
            Property prop = ctx.bodyAsClass(Property.class);
            Property updated = propertyService.update(ctx.pathParam("id"), prop);
            if (updated == null) ctx.status(404).json(Map.of("error", "Not found"));
            else ctx.json(updated);
        } catch (Exception e) {
            ctx.status(500).json(Map.of("error", e.getMessage()));
        }
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
