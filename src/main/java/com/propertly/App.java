package com.propertly;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.propertly.controller.ApiController;
import com.propertly.db.Database;
import io.javalin.Javalin;
import io.javalin.json.JavalinJackson;

public class App {

    public static void main(String[] args) {
        Database.init();

        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));

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

        new ApiController().register(app);

        System.out.println("Propertly backend running on port " + port);
    }
}
