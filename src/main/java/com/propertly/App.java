package com.propertly;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.propertly.controller.AdminController;
import com.propertly.controller.ApiController;
import com.propertly.controller.AuthController;
import com.propertly.controller.TenantController;
import com.propertly.db.Database;
import com.propertly.service.AuthService;
import io.javalin.Javalin;
import io.javalin.json.JavalinJackson;

import java.util.Map;

public class App {

    public static void main(String[] args) {
        Database.init();

        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
        AuthService authService = new AuthService();

        Javalin app = Javalin.create(config -> {
            config.jsonMapper(new JavalinJackson(mapper, true));
            config.bundledPlugins.enableCors(cors -> {
                cors.addRule(rule -> {
                    String allowedOrigin = System.getenv().getOrDefault("CORS_ORIGIN", "*");
                    if ("*".equals(allowedOrigin)) {
                        rule.anyHost();
                    } else {
                        rule.allowHost(allowedOrigin);
                    }
                });
            });
        }).start(port);

        // Auth middleware — protects all /api/* except /api/auth/* and /api/public/*
        app.before("/api/*", ctx -> {
            if (ctx.method().name().equals("OPTIONS")) return;
            if (ctx.path().startsWith("/api/auth/")) return;
            if (ctx.path().startsWith("/api/public/")) return;
            String header = ctx.header("Authorization");
            if (header == null || !header.startsWith("Bearer ")) {
                ctx.status(401).json(Map.of("error", "No autorizado"));
                ctx.skipRemainingHandlers();
                return;
            }
            String agencyId = authService.validateToken(header.substring(7));
            if (agencyId == null) {
                ctx.status(401).json(Map.of("error", "Token inválido o expirado"));
                ctx.skipRemainingHandlers();
                return;
            }
            ctx.attribute("agencyId", agencyId);
        });

        new AuthController().register(app);
        new TenantController().register(app);
        new ApiController().register(app);
        new AdminController().register(app);

        System.out.println("Propertly backend running on port " + port);
    }
}
