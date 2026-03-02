package com.propertly.controller;

import com.propertly.service.AuthService;
import io.javalin.Javalin;
import io.javalin.http.Context;

import java.util.Map;

public class AuthController {

    private final AuthService authService = new AuthService();

    public void register(Javalin app) {
        app.post("/api/auth/register", this::register);
        app.post("/api/auth/login", this::login);
        app.post("/api/auth/logout", this::logout);
    }

    private void register(Context ctx) {
        try {
            Map<?, ?> body = ctx.bodyAsClass(Map.class);
            String username = (String) body.get("username");
            String password = (String) body.get("password");
            String agencyName = (String) body.get("agencyName");

            if (username == null || username.isBlank() || password == null || password.isBlank() || agencyName == null || agencyName.isBlank()) {
                ctx.status(400).json(Map.of("error", "Todos los campos son obligatorios"));
                return;
            }
            if (password.length() < 6) {
                ctx.status(400).json(Map.of("error", "La contraseña debe tener al menos 6 caracteres"));
                return;
            }

            ctx.status(201).json(authService.register(username.trim(), password, agencyName.trim()));
        } catch (IllegalArgumentException e) {
            ctx.status(409).json(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            ctx.status(500).json(Map.of("error", e.getMessage()));
        }
    }

    private void login(Context ctx) {
        try {
            Map<?, ?> body = ctx.bodyAsClass(Map.class);
            String username = (String) body.get("username");
            String password = (String) body.get("password");

            if (username == null || password == null) {
                ctx.status(400).json(Map.of("error", "Usuario y contraseña requeridos"));
                return;
            }

            ctx.json(authService.login(username.trim(), password));
        } catch (IllegalArgumentException e) {
            ctx.status(401).json(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            ctx.status(500).json(Map.of("error", e.getMessage()));
        }
    }

    private void logout(Context ctx) {
        try {
            String header = ctx.header("Authorization");
            if (header != null && header.startsWith("Bearer ")) {
                authService.logout(header.substring(7));
            }
            ctx.status(204);
        } catch (Exception e) {
            ctx.status(500).json(Map.of("error", e.getMessage()));
        }
    }
}
