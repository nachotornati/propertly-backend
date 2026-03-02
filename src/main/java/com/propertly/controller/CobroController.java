package com.propertly.controller;

import com.propertly.model.Cobro;
import com.propertly.service.CobroService;
import io.javalin.Javalin;
import io.javalin.http.Context;

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
}
