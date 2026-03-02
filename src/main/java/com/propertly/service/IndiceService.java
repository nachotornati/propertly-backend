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
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.time.*;
import javax.net.ssl.*;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class IndiceService {

    private static final IndiceService INSTANCE = new IndiceService();
    private static final String BCRA_ICL = "https://api.bcra.gob.ar/estadisticas/v4.0/Monetarias/40";
    private static final String INDEC_IPC = "https://apis.datos.gob.ar/series/api/series/?ids=148.3_INIVELNAL_DICI_M_26&format=json";
    private static final Duration CACHE_TTL = Duration.ofHours(6);
    private static final DateTimeFormatter ES_MONTH = DateTimeFormatter.ofPattern("MMMM yyyy", new Locale("es"));
    private static final DateTimeFormatter ES_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .sslContext(buildTrustAllSslContext())
            .build();

    private static SSLContext buildTrustAllSslContext() {
        try {
            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(null, new TrustManager[]{new X509TrustManager() {
                public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                public void checkClientTrusted(X509Certificate[] c, String a) {}
                public void checkServerTrusted(X509Certificate[] c, String a) {}
            }}, null);
            return ctx;
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            throw new RuntimeException(e);
        }
    }
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
     * Calculates adjustment by compounding monthly rates over the window [desdeMonth, hastaMonth).
     *
     * For a property starting in January adjusting every 3 months (next = April):
     *   desdeMonth = January, hastaMonth = April
     *   Window months: January, February, March
     *   Rate per month = index(month) / index(month - 1)
     *   Coefficient = rate_jan × rate_feb × rate_mar
     *
     * For months whose data isn't published yet, the last known monthly rate is repeated
     * and estimado=true is set with a disclaimer listing the estimated months.
     *
     * ICL: uses last published value of each *completed* calendar month.
     * IPC: uses the monthly index level published by INDEC.
     */
    public Optional<AjusteInfo> calcularAjuste(String indice, YearMonth desdeMonth, YearMonth hastaMonth, BigDecimal precio) {
        try {
            // Base month = month before the window; needed to compute the first monthly rate
            YearMonth baseMonth = desdeMonth.minusMonths(1);
            Optional<IndiceData> baseOpt = getIndexDataForMonth(indice, baseMonth);
            if (baseOpt.isEmpty()) return Optional.empty();

            IndiceData baseData = baseOpt.get();
            IndiceData prevData = baseData;
            IndiceData lastAvailableData = baseData;

            double compoundedCoef = 1.0;
            boolean estimado = false;
            boolean gapFound = false;
            List<YearMonth> estimatedMonths = new ArrayList<>();

            // Pre-seed a fallback rate from the month before the base, so that if the
            // very first window month has no data we can still estimate using the last
            // published monthly change (e.g. Pareja: window starts in March but March
            // ICL is not yet complete → use February's monthly rate as the fallback).
            double lastKnownRate = -1;
            Optional<IndiceData> prevBaseOpt = getIndexDataForMonth(indice, baseMonth.minusMonths(1));
            if (prevBaseOpt.isPresent()) {
                lastKnownRate = baseData.valor / prevBaseOpt.get().valor;
            }

            YearMonth current = desdeMonth;
            while (current.isBefore(hastaMonth)) {
                if (!gapFound) {
                    Optional<IndiceData> currentOpt = getIndexDataForMonth(indice, current);
                    if (currentOpt.isPresent()) {
                        double rate = currentOpt.get().valor / prevData.valor;
                        lastKnownRate = rate;
                        lastAvailableData = currentOpt.get();
                        compoundedCoef *= rate;
                        prevData = currentOpt.get();
                    } else {
                        gapFound = true;
                    }
                }
                if (gapFound) {
                    if (lastKnownRate < 0) return Optional.empty(); // no rate to repeat
                    compoundedCoef *= lastKnownRate;
                    estimado = true;
                    estimatedMonths.add(current);
                }
                current = current.plusMonths(1);
            }

            String disclaimer = null;
            if (estimado) {
                String lastAvailableMonthStr = YearMonth.from(lastAvailableData.fecha).format(ES_MONTH);
                String missingMonths = estimatedMonths.stream()
                        .map(m -> m.format(ES_MONTH))
                        .collect(Collectors.joining(", "));
                disclaimer = String.format(
                        "Calculado con el %s de %s (último disponible) para %s. Se actualizará cuando se publiquen los datos.",
                        indice, lastAvailableMonthStr, missingMonths);
            }

            BigDecimal coef = BigDecimal.valueOf(compoundedCoef).setScale(6, RoundingMode.HALF_UP);
            BigDecimal nuevoPrecio = precio.multiply(coef).setScale(0, RoundingMode.HALF_UP);

            return Optional.of(new AjusteInfo(coef, nuevoPrecio,
                    baseData.valor, baseData.fecha.toString(),
                    lastAvailableData.valor, lastAvailableData.fecha.toString(),
                    estimado, disclaimer));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /**
     * Returns the index level for a completed month (base for monthly rate calculation).
     * ICL: only complete months (last calendar day already passed) to avoid partial-month rates.
     * IPC: uses the published monthly level (available the following month).
     */
    private Optional<IndiceData> getIndexDataForMonth(String indice, YearMonth month) {
        if ("ICL".equals(indice)) {
            if (!month.atEndOfMonth().isBefore(LocalDate.now())) return Optional.empty();
            return getICLLastOfMonth(month);
        } else {
            return getIPCForMonth(month);
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
        if (desde.isAfter(today)) return Optional.empty();
        if (hasta.isAfter(today)) hasta = today;

        String url = BCRA_ICL + "?Desde=" + desde + "&Hasta=" + hasta + "&Limit=1";
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

    // IPC level for a specific month (published by INDEC the following month)
    private Optional<IndiceData> getIPCForMonth(YearMonth month) {
        String key = "IPC:" + month;
        CachedEntry cached = cache.get(key);
        if (cached != null && !cached.expired()) return Optional.of(cached.data);

        if (month.atDay(1).isAfter(LocalDate.now())) return Optional.empty();

        String date = month.atDay(1).toString();
        String url = INDEC_IPC + "&start_date=" + date + "&end_date=" + date + "&limit=1";
        return parseIPCResponse(url, key);
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
