package com.propertly.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.propertly.model.AjusteInfo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class IndiceService {

    private static final IndiceService INSTANCE = new IndiceService();
    private static final String BCRA_ICL = "https://api.bcra.gob.ar/estadisticas/v4.0/Monetarias/40";
    private static final String INDEC_IPC = "https://apis.datos.gob.ar/series/api/series/?ids=148.3_INIVELNAL_DICI_M_26&format=json";
    private static final Duration CACHE_TTL = Duration.ofHours(6);
    private static final DateTimeFormatter ES_MONTH = DateTimeFormatter.ofPattern("MMMM yyyy", new Locale("es"));
    private static final DateTimeFormatter ES_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<String, CachedEntry> cache = new ConcurrentHashMap<>();

    public static IndiceService getInstance() { return INSTANCE; }

    private static class CachedEntry {
        final IndiceData data;
        final Instant ts;
        CachedEntry(IndiceData data) { this.data = data; this.ts = Instant.now(); }
        boolean expired() { return Duration.between(ts, Instant.now()).compareTo(CACHE_TTL) > 0; }
    }

    public static class IndiceData {
        public final double valor;
        public final LocalDate fecha;
        IndiceData(double valor, LocalDate fecha) { this.valor = valor; this.fecha = fecha; }
    }

    /**
     * Calculates the adjustment coefficient and new price for a property.
     * hastaMonth = month of the next adjustment date
     * desdeMonth = hastaMonth minus ajusteMeses (previous adjustment month)
     * Uses last available data of each month; if hastaMonth data isn't published yet,
     * falls back to the most recent available value and sets estimado=true.
     */
    public Optional<AjusteInfo> calcularAjuste(String indice, YearMonth desdeMonth, YearMonth hastaMonth, BigDecimal precio) {
        try {
            Optional<IndiceData> desde = "ICL".equals(indice) ? getICLLastOfMonth(desdeMonth) : getIPCForMonth(desdeMonth);
            Optional<IndiceData> hasta = "ICL".equals(indice) ? getICLLastOfMonth(hastaMonth) : getIPCForMonth(hastaMonth);

            boolean estimado = false;
            String disclaimer = null;

            // If hasta data not yet published, use the last available
            if (hasta.isEmpty()) {
                hasta = "ICL".equals(indice) ? getICLLastAvailable() : getIPCLastAvailable();
                if (hasta.isEmpty() || desde.isEmpty()) return Optional.empty();
                estimado = true;
                LocalDate fechaReal = hasta.get().fecha;
                disclaimer = String.format(
                    "Calculado con el último dato disponible al %s. El valor se actualizará cuando se publiquen los datos de %s.",
                    fechaReal.format(ES_DATE),
                    hastaMonth.format(ES_MONTH)
                );
            }

            if (desde.isEmpty()) return Optional.empty();

            IndiceData d = desde.get();
            IndiceData h = hasta.get();

            BigDecimal coef = BigDecimal.valueOf(h.valor / d.valor).setScale(6, RoundingMode.HALF_UP);
            BigDecimal nuevoPrecio = precio.multiply(coef).setScale(0, RoundingMode.HALF_UP);

            return Optional.of(new AjusteInfo(coef, nuevoPrecio,
                    d.valor, d.fecha.toString(),
                    h.valor, h.fecha.toString(),
                    estimado, disclaimer));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    // Last ICL value published within a given month (or empty if no data yet)
    private Optional<IndiceData> getICLLastOfMonth(YearMonth month) {
        String key = "ICL:" + month;
        CachedEntry cached = cache.get(key);
        if (cached != null && !cached.expired()) return Optional.of(cached.data);

        LocalDate today = LocalDate.now();
        LocalDate hasta = month.atEndOfMonth();
        LocalDate desde = month.atDay(1);
        if (desde.isAfter(today)) return Optional.empty(); // fully future month
        if (hasta.isAfter(today)) hasta = today;

        String url = BCRA_ICL + "?Desde=" + desde + "&Hasta=" + hasta + "&Limit=1";
        return parseICLResponse(url, key);
    }

    // Most recent ICL value ever (fallback when target month not published)
    private Optional<IndiceData> getICLLastAvailable() {
        String key = "ICL:LAST";
        CachedEntry cached = cache.get(key);
        if (cached != null && !cached.expired()) return Optional.of(cached.data);

        String url = BCRA_ICL + "?Desde=2020-07-01&Hasta=" + LocalDate.now() + "&Limit=1";
        return parseICLResponse(url, key);
    }

    private Optional<IndiceData> parseICLResponse(String url, String cacheKey) {
        try {
            JsonNode root = mapper.readTree(httpGet(url));
            JsonNode detalle = root.path("results").get(0).path("detalle");
            if (detalle == null || detalle.isEmpty()) return Optional.empty();
            JsonNode first = detalle.get(0);
            IndiceData data = new IndiceData(first.get("valor").asDouble(), LocalDate.parse(first.get("fecha").asText()));
            cache.put(cacheKey, new CachedEntry(data));
            return Optional.of(data);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    // IPC value for a specific month
    private Optional<IndiceData> getIPCForMonth(YearMonth month) {
        String key = "IPC:" + month;
        CachedEntry cached = cache.get(key);
        if (cached != null && !cached.expired()) return Optional.of(cached.data);

        if (month.atDay(1).isAfter(LocalDate.now())) return Optional.empty();

        String date = month.atDay(1).toString();
        String url = INDEC_IPC + "&start_date=" + date + "&end_date=" + date + "&limit=1";
        return parseIPCResponse(url, key);
    }

    // Most recent IPC value published (fallback)
    private Optional<IndiceData> getIPCLastAvailable() {
        String key = "IPC:LAST";
        CachedEntry cached = cache.get(key);
        if (cached != null && !cached.expired()) return Optional.of(cached.data);

        // Get last 1 month of data sorted desc
        String url = INDEC_IPC + "&start_date=2024-01-01&end_date=" + LocalDate.now() + "&limit=100";
        try {
            JsonNode root = mapper.readTree(httpGet(url));
            JsonNode data = root.path("data");
            if (data == null || data.isEmpty()) return Optional.empty();
            // datos.gob.ar returns ascending; take last element
            JsonNode last = data.get(data.size() - 1);
            IndiceData indice = new IndiceData(last.get(1).asDouble(), LocalDate.parse(last.get(0).asText()));
            cache.put(key, new CachedEntry(indice));
            return Optional.of(indice);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private Optional<IndiceData> parseIPCResponse(String url, String cacheKey) {
        try {
            JsonNode root = mapper.readTree(httpGet(url));
            JsonNode data = root.path("data");
            if (data == null || data.isEmpty()) return Optional.empty();
            JsonNode entry = data.get(0);
            IndiceData indice = new IndiceData(entry.get(1).asDouble(), LocalDate.parse(entry.get(0).asText()));
            cache.put(cacheKey, new CachedEntry(indice));
            return Optional.of(indice);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private String httpGet(String url) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(8))
                .GET().build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        return resp.body();
    }
}
