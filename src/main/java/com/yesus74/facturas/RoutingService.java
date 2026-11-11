package com.yesus74.facturas;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@Service
public class RoutingService {

    @Value("${here.api.key}")
    private String hereApiKey;

    private final WebClient webClient;

    public RoutingService(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.baseUrl("https://router.hereapi.com/v8").build();
    }

   public Mono<Map> calcularRuta(String origenCoords, String destinoCoords) {
    return this.webClient.get()
            .uri(uriBuilder -> uriBuilder
                    .path("/routes")
                    .queryParam("apiKey", hereApiKey)
                    .queryParam("origin", origenCoords)
                    .queryParam("destination", destinoCoords)
                    .queryParam("transportMode", "truck")
                    .queryParam("return", "summary,tolls,polyline")
                    .queryParam("spans", "countryCode,stateCode,length")
                    .queryParam("currency", "USD")
                    .queryParam("vehicle[height]", "410")
                    .queryParam("vehicle[width]", "255")
                    .queryParam("vehicle[length]", "1600")
                    .queryParam("vehicle[grossWeight]", "36000")
                    .queryParam("vehicle[axleCount]", "5")
                    .queryParam("departureTime",  calcularSalida())
                    .build())
            .retrieve()
            .onStatus(status -> status.isError(), response ->
                    response.bodyToMono(String.class)
                            .map(body -> new RuntimeException("HERE respondió " + response.statusCode() + ": " + body)))
            .bodyToMono(Map.class)
            .timeout(java.time.Duration.ofSeconds(15));
}
    public Mono<String> geocodificar(String direccion) {
        WebClient geocodeClient = WebClient.builder()
                .baseUrl("https://geocode.search.hereapi.com/v1")
                .build();

        return geocodeClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/geocode")
                        .queryParam("apiKey", hereApiKey)
                        .queryParam("q", direccion)
                        .queryParam("limit", "1")
                        .build())
                .retrieve()
                .bodyToMono(Map.class)
                .map(response -> {
                    List<Map<String, Object>> items = (List<Map<String, Object>>) response.get("items");
                    if (items == null || items.isEmpty()) {
                        throw new RuntimeException("No se encontraron coordenadas para: " + direccion);
                    }
                    Map<String, Object> position = (Map<String, Object>) items.get(0).get("position");
                    return position.get("lat") + "," + position.get("lng");
                });
    }

    private String calcularSalida() {
    return java.time.LocalDateTime.now()
            .plusDays(1)
            .withHour(8).withMinute(0).withSecond(0).withNano(0)
            .format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME);
}
}