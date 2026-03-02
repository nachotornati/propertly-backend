package com.propertly.controller;

import com.propertly.model.Cobro;
import com.propertly.model.Property;
import com.propertly.service.CobroService;
import com.propertly.service.PropertyService;
import io.javalin.Javalin;
import io.javalin.http.Context;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class TenantController {

    private final PropertyService propertyService = new PropertyService();
    private final CobroService cobroService = new CobroService();

    public void register(Javalin app) {
        app.get("/api/public/tenant/{token}", this::getTenantView);
    }

    private void getTenantView(Context ctx) {
        try {
            String token = ctx.pathParam("token");
            Property prop = propertyService.findByToken(token);
            if (prop == null) {
                ctx.status(404).json(Map.of("error", "Propiedad no encontrada"));
                return;
            }
            List<Cobro> cobros = cobroService.findByProperty(prop.id);

            BigDecimal precioActual = prop.precioActual != null ? prop.precioActual : prop.precio;

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("address", prop.address != null ? prop.address : prop.barrio);
            response.put("barrio", prop.barrio);
            response.put("provincia", prop.provincia);
            response.put("tenantName", prop.tenantName);
            response.put("moneda", prop.moneda);
            response.put("precioActual", precioActual);
            response.put("mesInicio", prop.mesInicio);
            response.put("duracionMeses", prop.duracionMeses);
            response.put("nextAdjustmentDate", prop.nextAdjustmentDate);
            response.put("ajusteInfo", prop.ajusteInfo);
            response.put("historialAjustes", prop.historialAjustes);
            response.put("createdAt", prop.createdAt);
            response.put("cobros", cobros);

            ctx.json(response);
        } catch (Exception e) {
            ctx.status(500).json(Map.of("error", e.getMessage()));
        }
    }
}
