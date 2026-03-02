package com.propertly.service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

public class WhatsAppService {

    public void send(String to, String body) throws Exception {
        String instance = System.getenv("ULTRAMSG_INSTANCE");
        String token    = System.getenv("ULTRAMSG_TOKEN");

        if (instance == null || instance.isBlank() || token == null || token.isBlank()) {
            throw new RuntimeException("WhatsApp no configurado — definí las variables ULTRAMSG_INSTANCE y ULTRAMSG_TOKEN");
        }

        // Keep only digits (strips spaces, dashes, parens, leading +)
        String phone = to.replaceAll("[^0-9]", "");

        String form = "token=" + URLEncoder.encode(token, StandardCharsets.UTF_8)
                + "&to="    + URLEncoder.encode(phone, StandardCharsets.UTF_8)
                + "&body="  + URLEncoder.encode(body,  StandardCharsets.UTF_8);

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.ultramsg.com/" + instance + "/messages/chat"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new RuntimeException("UltraMsg error " + response.statusCode() + ": " + response.body());
        }
        // UltraMsg returns 200 even on failure; check "sent":"false" in body
        if (response.body().contains("\"sent\":\"false\"") || response.body().contains("\"sent\": \"false\"")) {
            throw new RuntimeException("UltraMsg no pudo enviar el mensaje: " + response.body());
        }
    }
}
