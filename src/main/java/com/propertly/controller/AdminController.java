package com.propertly.controller;

import com.propertly.db.Database;
import io.javalin.Javalin;
import io.javalin.http.Context;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;

public class AdminController {

    public void register(Javalin app) {
        app.get("/admin/dashboard", this::dashboard);
    }

    private void dashboard(Context ctx) {
        String adminUser = System.getenv().getOrDefault("ADMIN_USER", "admin");
        String adminPass = System.getenv().getOrDefault("ADMIN_PASS", "propertly");

        String authHeader = ctx.header("Authorization");
        if (authHeader == null || !authHeader.startsWith("Basic ")) {
            ctx.header("WWW-Authenticate", "Basic realm=\"Propertly Admin\"");
            ctx.status(401).result("Unauthorized");
            return;
        }
        String decoded = new String(Base64.getDecoder().decode(authHeader.substring(6)));
        String[] parts = decoded.split(":", 2);
        if (parts.length != 2 || !parts[0].equals(adminUser) || !parts[1].equals(adminPass)) {
            ctx.header("WWW-Authenticate", "Basic realm=\"Propertly Admin\"");
            ctx.status(401).result("Unauthorized");
            return;
        }

        try {
            long totalUsers, totalAgencies, totalProperties, totalCobros;
            List<String> days = new ArrayList<>();
            List<Long> usersByDay = new ArrayList<>();
            List<Long> propertiesByDay = new ArrayList<>();
            List<Long> cobrosByDay = new ArrayList<>();
            List<Map<String, Object>> agencyRows = new ArrayList<>();

            try (Connection conn = Database.getConnection()) {
                totalUsers      = queryCount(conn, "SELECT COUNT(*) FROM users");
                totalAgencies   = queryCount(conn, "SELECT COUNT(*) FROM agencies");
                totalProperties = queryCount(conn, "SELECT COUNT(*) FROM properties");
                totalCobros     = queryCount(conn, "SELECT COUNT(*) FROM cobros");

                String usersPerDaySql = """
                    WITH d AS (
                        SELECT gs::date AS day FROM generate_series(
                            NOW() - INTERVAL '29 days', NOW(), INTERVAL '1 day') AS gs
                    )
                    SELECT d.day, COUNT(u.id) AS cnt
                    FROM d LEFT JOIN users u ON u.created_at::date = d.day
                    GROUP BY d.day ORDER BY d.day""";

                String propsPerDaySql = """
                    WITH d AS (
                        SELECT gs::date AS day FROM generate_series(
                            NOW() - INTERVAL '29 days', NOW(), INTERVAL '1 day') AS gs
                    )
                    SELECT d.day, COUNT(p.id) AS cnt
                    FROM d LEFT JOIN properties p ON p.created_at::date = d.day
                    GROUP BY d.day ORDER BY d.day""";

                String cobrosPerDaySql = """
                    WITH d AS (
                        SELECT gs::date AS day FROM generate_series(
                            NOW() - INTERVAL '29 days', NOW(), INTERVAL '1 day') AS gs
                    )
                    SELECT d.day, COUNT(c.id) AS cnt
                    FROM d LEFT JOIN cobros c ON c.created_at::date = d.day
                    GROUP BY d.day ORDER BY d.day""";

                try (PreparedStatement ps = conn.prepareStatement(usersPerDaySql);
                     ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        days.add(rs.getString("day").substring(5)); // MM-DD
                        usersByDay.add(rs.getLong("cnt"));
                    }
                }
                try (PreparedStatement ps = conn.prepareStatement(propsPerDaySql);
                     ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) propertiesByDay.add(rs.getLong("cnt"));
                }
                try (PreparedStatement ps = conn.prepareStatement(cobrosPerDaySql);
                     ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) cobrosByDay.add(rs.getLong("cnt"));
                }

                String agenciesSql = """
                    SELECT a.name, a.email, a.created_at,
                        COUNT(DISTINCT u.id) AS user_count,
                        COUNT(DISTINCT p.id) AS prop_count
                    FROM agencies a
                    LEFT JOIN users u ON u.agency_id = a.id
                    LEFT JOIN properties p ON p.agency_id = a.id
                    GROUP BY a.id, a.name, a.email, a.created_at
                    ORDER BY a.created_at DESC""";

                try (PreparedStatement ps = conn.prepareStatement(agenciesSql);
                     ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("name",       rs.getString("name"));
                        row.put("email",      rs.getString("email") != null ? rs.getString("email") : "—");
                        row.put("users",      rs.getLong("user_count"));
                        row.put("properties", rs.getLong("prop_count"));
                        row.put("createdAt",  rs.getTimestamp("created_at").toString().substring(0, 10));
                        agencyRows.add(row);
                    }
                }
            }

            ctx.contentType("text/html; charset=utf-8")
               .result(buildHtml(totalUsers, totalAgencies, totalProperties, totalCobros,
                                 days, usersByDay, propertiesByDay, cobrosByDay, agencyRows));
        } catch (Exception e) {
            ctx.status(500).result("Error: " + e.getMessage());
        }
    }

    private long queryCount(Connection conn, String sql) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getLong(1) : 0;
        }
    }

    private String buildHtml(long users, long agencies, long properties, long cobros,
                              List<String> days, List<Long> usersByDay, List<Long> propertiesByDay,
                              List<Long> cobrosByDay, List<Map<String, Object>> agencyRows) {
        StringBuilder rows = new StringBuilder();
        for (Map<String, Object> r : agencyRows) {
            rows.append("<tr>")
                .append("<td>").append(esc(r.get("name").toString())).append("</td>")
                .append("<td>").append(esc(r.get("email").toString())).append("</td>")
                .append("<td>").append(r.get("users")).append("</td>")
                .append("<td>").append(r.get("properties")).append("</td>")
                .append("<td>").append(r.get("createdAt")).append("</td>")
                .append("</tr>\n");
        }

        String now = java.time.LocalDateTime.now().toString().substring(0, 16).replace("T", " ");
        String today = java.time.LocalDate.now().toString();

        return """
            <!DOCTYPE html>
            <html lang="es">
            <head>
              <meta charset="UTF-8">
              <meta name="viewport" content="width=device-width,initial-scale=1">
              <title>Propertly Admin</title>
              <script src="https://cdn.jsdelivr.net/npm/chart.js@4/dist/chart.umd.min.js"></script>
              <style>
                *{box-sizing:border-box;margin:0;padding:0}
                body{font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',sans-serif;background:#f8fafc;color:#1e293b}
                .hdr{background:#1e293b;color:#fff;padding:18px 32px}
                .hdr h1{font-size:18px;font-weight:600}
                .hdr p{font-size:12px;color:#94a3b8;margin-top:3px}
                .main{max-width:1120px;margin:0 auto;padding:28px 20px}
                .cards{display:grid;grid-template-columns:repeat(4,1fr);gap:14px;margin-bottom:24px}
                .card{background:#fff;border-radius:10px;padding:18px 20px;border:1px solid #e2e8f0}
                .card .lbl{font-size:11px;font-weight:700;text-transform:uppercase;letter-spacing:.05em;color:#64748b;margin-bottom:6px}
                .card .val{font-size:30px;font-weight:700}
                .charts{display:grid;grid-template-columns:repeat(3,1fr);gap:14px;margin-bottom:24px}
                .cc{background:#fff;border-radius:10px;padding:18px 20px;border:1px solid #e2e8f0}
                .cc h2{font-size:11px;font-weight:700;text-transform:uppercase;letter-spacing:.05em;color:#64748b;margin-bottom:14px}
                .section{background:#fff;border-radius:10px;border:1px solid #e2e8f0;overflow:hidden}
                .section h2{font-size:11px;font-weight:700;text-transform:uppercase;letter-spacing:.05em;color:#64748b;padding:14px 20px;border-bottom:1px solid #f1f5f9}
                table{width:100%%;border-collapse:collapse}
                th{text-align:left;padding:9px 20px;font-size:11px;font-weight:700;color:#94a3b8;text-transform:uppercase;letter-spacing:.05em;background:#f8fafc}
                td{padding:11px 20px;font-size:13px;border-top:1px solid #f1f5f9}
                tr:hover td{background:#f8fafc}
                .ft{text-align:center;padding:20px;font-size:11px;color:#cbd5e1}
                @media(max-width:700px){.cards{grid-template-columns:repeat(2,1fr)}.charts{grid-template-columns:1fr}}
              </style>
            </head>
            <body>
              <div class="hdr"><h1>Propertly · Admin Dashboard</h1><p>Actualizado %s</p></div>
              <div class="main">
                <div class="cards">
                  <div class="card"><div class="lbl">Usuarios</div><div class="val">%d</div></div>
                  <div class="card"><div class="lbl">Agencias</div><div class="val">%d</div></div>
                  <div class="card"><div class="lbl">Propiedades</div><div class="val">%d</div></div>
                  <div class="card"><div class="lbl">Cobros</div><div class="val">%d</div></div>
                </div>
                <div class="charts">
                  <div class="cc"><h2>Usuarios · últimos 30 días</h2><div style="position:relative;height:180px"><canvas id="uc"></canvas></div></div>
                  <div class="cc"><h2>Propiedades · últimos 30 días</h2><div style="position:relative;height:180px"><canvas id="pc"></canvas></div></div>
                  <div class="cc"><h2>Cobros · últimos 30 días</h2><div style="position:relative;height:180px"><canvas id="cc"></canvas></div></div>
                </div>
                <div class="section">
                  <h2>Agencias</h2>
                  <table>
                    <thead><tr><th>Nombre</th><th>Email</th><th>Usuarios</th><th>Propiedades</th><th>Alta</th></tr></thead>
                    <tbody>%s</tbody>
                  </table>
                </div>
              </div>
              <div class="ft">Propertly Admin · %s</div>
              <script>
                const labels = %s;
                function bar(id, data, color) {
                  new Chart(document.getElementById(id), {
                    type: 'bar',
                    data: { labels, datasets: [{ data, backgroundColor: color, borderRadius: 3 }] },
                    options: {
                      responsive: true, maintainAspectRatio: false,
                      plugins: { legend: { display: false } },
                      scales: {
                        x: { ticks: { font: { size: 9 }, maxRotation: 45 }, grid: { display: false } },
                        y: { ticks: { font: { size: 10 }, precision: 0 }, beginAtZero: true }
                      }
                    }
                  });
                }
                bar('uc', %s, 'rgba(99,102,241,0.7)');
                bar('pc', %s, 'rgba(16,185,129,0.7)');
                bar('cc', %s, 'rgba(245,158,11,0.7)');
              </script>
            </body>
            </html>""".formatted(
                now,
                users, agencies, properties, cobros,
                rows.toString(),
                today,
                toJson(days), toJson(usersByDay), toJson(propertiesByDay), toJson(cobrosByDay)
            );
    }

    private String toJson(List<?> list) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            Object v = list.get(i);
            if (v instanceof String) sb.append('"').append(v).append('"');
            else sb.append(v);
        }
        return sb.append("]").toString();
    }

    private String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
