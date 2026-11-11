package com.yesus74.facturas;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.File;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@SuppressWarnings("unchecked")
public class DieselService {

    private static final Logger log = LoggerFactory.getLogger(DieselService.class);
    private static final double PRECIO_RESPALDO = 4.00;
    private static final File ARCHIVO = new File("diesel-cache.json");

    // 40 estados (sin los del norte con poco tráfico). Cada actualización = 40 llamadas.
    private static final List<String> ESTADOS = List.of(
            "AL","AZ","AR","CA","CO","CT","DE","FL","GA","IL","IN","IA",
            "KS","KY","LA","MD","MA","MI","MN","MS","MO","NE","NV","NJ",
            "NM","NY","NC","OH","OK","OR","PA","SC","TN","TX","UT",
            "VA","WA","WV","WI","WY");

    @Value("${oilprice.api.key}")
    private String oilPriceApiKey;

    private final WebClient webClient;
    private final ObjectMapper mapper = new ObjectMapper();
    private static final java.time.ZoneId ZONA = java.time.ZoneId.of("America/New_York");

    private final Map<String, Double> cache = new ConcurrentHashMap<>();
    private volatile LocalDateTime ultimaActualizacion;

    public DieselService(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.baseUrl("https://api.oilpriceapi.com/v1").build();
    }

    @PostConstruct
    public void cargaInicial() {
        cargarDeArchivo();
        if (hayQueActualizar()) {
            actualizarTodos();
        } else {
            log.info("Caché de diésel vigente ({}), no se llama a la API.", ultimaActualizacion);
        }
    }

    /** Todos los días a las 6:00 AM hora de Nueva York. */
    @Scheduled(cron = "0 0 6 * * *", zone = "America/New_York")
    public void actualizacionDiaria() {
        if (hayQueActualizar()) {
            actualizarTodos();
        }
    }

    private boolean hayQueActualizar() {
        return ultimaActualizacion == null
            || !ultimaActualizacion.toLocalDate().equals(java.time.LocalDate.now(ZONA));
}

    private void actualizarTodos() {
    // Se borran los extras: solo duran hasta esta actualización
    cache.keySet().removeIf(estado -> !ESTADOS.contains(estado));

    log.info("Actualizando precios de diésel de {} estados...", ESTADOS.size());

    Flux.fromIterable(ESTADOS)
            .flatMap(estado -> consultarApi(estado)
                    .doOnNext(precio -> cache.put(estado, precio))
                    .onErrorResume(e -> {
                        log.warn("No se pudo actualizar {}: {}", estado, e.getMessage());
                        return Mono.empty();
                    }), 3)
            .then()
            .doOnSuccess(v -> {
                ultimaActualizacion = LocalDateTime.now(ZONA);
                guardarEnArchivo();
                log.info("Precios actualizados. En caché: {}", cache.size());
            })
            .subscribe();
}

    /** Nunca llama a la API: lee de la caché o estima. */
    public Mono<Double> obtenerPrecioDiesel(String estado) {
        Double precio = cache.get(estado);
        if (precio != null) {
            return Mono.just(precio);
        }
        // Estado fuera de la lista: promedio de los estados en caché
        return Mono.just(promedioCache());
    }

    public LocalDateTime getUltimaActualizacion() {
        return ultimaActualizacion;
    }

    private double promedioCache() {
        return cache.values().stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(PRECIO_RESPALDO);
    }

    private Mono<Double> consultarApi(String estado) {
        return this.webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/prices/latest")
                        .queryParam("by_code", "DIESEL_RETAIL_STATE_" + estado + "_USD")
                        .build())
                .header("Authorization", "Token " + oilPriceApiKey)
                .retrieve()
                .bodyToMono(Map.class)
                .timeout(Duration.ofSeconds(10))
                .flatMap(response -> {
                    Map<String, Object> data = (Map<String, Object>) response.get("data");
                    if (data != null && data.get("price") instanceof Number n) {
                        return Mono.just(n.doubleValue());
                    }
                    return Mono.empty();
                });
    }

    // ---------- Persistencia en archivo (sobrevive a reinicios) ----------

    private void guardarEnArchivo() {
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("fecha", ultimaActualizacion.toString());
            data.put("precios", new HashMap<>(cache));
            mapper.writeValue(ARCHIVO, data);
        } catch (Exception e) {
            log.warn("No se pudo guardar la caché: {}", e.getMessage());
        }
    }

    private void cargarDeArchivo() {
        if (!ARCHIVO.exists()) return;
        try {
            Map<String, Object> data = mapper.readValue(ARCHIVO, Map.class);
            ultimaActualizacion = LocalDateTime.parse((String) data.get("fecha"));
            Map<String, Number> precios = (Map<String, Number>) data.get("precios");
            precios.forEach((k, v) -> cache.put(k, v.doubleValue()));
            log.info("Caché cargada del archivo: {} estados", cache.size());
        } catch (Exception e) {
            log.warn("No se pudo leer la caché: {}", e.getMessage());
        }
    }
}