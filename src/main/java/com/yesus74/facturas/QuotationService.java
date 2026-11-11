package com.yesus74.facturas;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@SuppressWarnings("unchecked")
public class QuotationService {

    @Autowired
    private RoutingService routingService;

    @Autowired
    private DieselService dieselService;

    public Mono<Map<String, Object>> cotizarViaje(String origen, String destino) {
        return routingService.calcularRuta(origen, destino)
                .flatMap(hereResponse -> {
                    try {
                        if (hereResponse.containsKey("error")) {
                            throw new RuntimeException("Error de HERE: " + hereResponse.get("error"));
                        }

                        Map<String, Object> route = (Map<String, Object>) ((List<?>) hereResponse.get("routes")).get(0);
                        List<Map<String, Object>> sections = (List<Map<String, Object>>) route.get("sections");

                        double totalMillas = 0.0;
                        double costoPeajes = 0.0;
                        Map<String, String> estadosPorCodigo = new HashMap<>(); // Guarda código y país
                        Map<String, Double> millasPorEstado = new HashMap<>();
                        List<Map<String, Object>> detallePeajes = new ArrayList<>();
                        String polylineRuta = null;

                        for (Map<String, Object> section : sections) {
                            // 1. Millas totales
                            Map<String, Object> summary = (Map<String, Object>) section.get("summary");
                            if (summary != null) {
                                double lengthInMeters = ((Number) summary.get("length")).doubleValue();
                                totalMillas += lengthInMeters / 1609.34;
                            }

                            // 2. Millas por estado y país
                            List<Map<String, Object>> spans = (List<Map<String, Object>>) section.get("spans");
                            if (spans != null) {
                                for (Map<String, Object> span : spans) {
                                    String stateCode = (String) span.get("stateCode");
                                    String countryCode = (String) span.get("countryCode");
                                    double lengthInMeters = ((Number) span.get("length")).doubleValue();
                                    double lengthInMiles = lengthInMeters / 1609.34;
                                    
                                    if (stateCode != null && countryCode != null) {
                                        String claveUnica = countryCode + "-" + stateCode; // Ej: USA-CA, MEX-BCN
                                        millasPorEstado.merge(claveUnica, lengthInMiles, Double::sum);
                                        estadosPorCodigo.put(claveUnica, countryCode);
                                    }
                                }
                            }

                            // 3. Peajes
                            List<Map<String, Object>> tolls = (List<Map<String, Object>>) section.get("tolls");
                            if (tolls != null) {
                                for (Map<String, Object> toll : tolls) {
                                    List<Map<String, Object>> fares = (List<Map<String, Object>>) toll.get("fares");
                                    Map<String, Object> fareElegida = elegirTarifa(fares);
                                    if (fareElegida != null) {
                                        Map<String, Object> price = (Map<String, Object>) fareElegida.get("price");
                                        if (price != null && price.get("value") != null) {
                                            double valor = ((Number) price.get("value")).doubleValue();
                                            costoPeajes += valor;

                                            Map<String, Object> detalle = new HashMap<>();
                                            detalle.put("nombre", toll.get("tollSystem"));
                                            detalle.put("monto", valor);
                                            detalle.put("metodo", fareElegida.get("paymentMethods"));
                                            detallePeajes.add(detalle);
                                        }
                                    }
                                }
                            }

                            // 4. Polyline
                            if (section.containsKey("polyline")) {
                                polylineRuta = (String) section.get("polyline");
                            }
                        }

                        final double totalMillasFinal = totalMillas;
                        final double costoPeajesFinal = costoPeajes;
                        final List<Map<String, Object>> detallePeajesFinal = detallePeajes;
                        final String polylineFinal = polylineRuta;

                        // 5. Consultar precios de diésel
                        List<Mono<Map<String, Object>>> dieselMonos = new ArrayList<>();
                        for (Map.Entry<String, Double> entry : millasPorEstado.entrySet()) {
                            String claveUnica = entry.getKey(); // Ej: USA-CA, MEX-BCN
                            Double millas = entry.getValue();
                            Double galones = millas / 5.5;
                            
                            String[] partes = claveUnica.split("-");
                            String pais = partes[0];
                            String estado = partes[1];

                            // Si es México, usamos un precio fijo (ej. $4.50 USD) porque OilPriceAPI no maneja MXN/USD para México
                            if (pais.equals("MEX")) {
                                double precioFijoMexico = 4.50;
                                Map<String, Object> estadoData = new HashMap<>();
                                estadoData.put("codigo", estado);
                                estadoData.put("nombre", "México (" + estado + ")");
                                estadoData.put("millas", millas);
                                estadoData.put("galones", galones);
                                estadoData.put("precioGalon", precioFijoMexico);
                                estadoData.put("costoDiesel", galones * precioFijoMexico);
                                dieselMonos.add(Mono.just(estadoData));
                            } else {
                                // Si es USA, consultamos la API
                                dieselMonos.add(dieselService.obtenerPrecioDiesel(estado)
                                        .map(precio -> {
                                            Map<String, Object> estadoData = new HashMap<>();
                                            estadoData.put("codigo", estado);
                                            estadoData.put("nombre", USStates.getNombre(estado));
                                            estadoData.put("millas", millas);
                                            estadoData.put("galones", galones);
                                            estadoData.put("precioGalon", precio);
                                            estadoData.put("costoDiesel", galones * precio);
                                            return estadoData;
                                        }));
                            }
                        }

                        // 6. Procesar todos los resultados
                        return Mono.zip(dieselMonos, results -> {
                            double costoTotalDiesel = 0;
                            double totalGalones = 0;
                            List<Map<String, Object>> desgloseEstados = new ArrayList<>();

                            for (Object result : results) {
                                Map<String, Object> estadoData = (Map<String, Object>) result;
                                desgloseEstados.add(estadoData);
                                costoTotalDiesel += (Double) estadoData.get("costoDiesel");
                                totalGalones += (Double) estadoData.get("galones");
                            }

                            double precioPromedioGalon = totalGalones > 0 ? (costoTotalDiesel / totalGalones) : 0;
                            double costoOperador = totalMillasFinal * 0.45;

                            Map<String, Object> resultado = new HashMap<>();
                            resultado.put("millasTotales", totalMillasFinal);
                            resultado.put("desgloseEstados", desgloseEstados);
                            resultado.put("totalGalones", totalGalones);
                            resultado.put("precioPromedioGalon", precioPromedioGalon);
                            resultado.put("costoDiesel", costoTotalDiesel);
                            resultado.put("costoOperador", costoOperador);
                            resultado.put("costoPeajes", costoPeajesFinal);
                            resultado.put("detallePeajes", detallePeajesFinal);
                            resultado.put("costoTotal", costoTotalDiesel + costoOperador + costoPeajesFinal);
                            resultado.put("polyline", polylineFinal);
                            
                            return resultado;
                        });

                    } catch (Exception e) {
                        Map<String, Object> errorMap = new HashMap<>();
                        errorMap.put("error", "Fallo al procesar el viaje: " + e.getMessage());
                        return Mono.just(errorMap);
                    }
                })
                .onErrorResume(e -> {
                    Map<String, Object> errorMap = new HashMap<>();
                    errorMap.put("error", "Error de conexión o API: " + e.getMessage());
                    return Mono.just(errorMap);
                });
    }

    public Mono<Map<String, Object>> cotizarViajePorDireccion(String origenTexto, String destinoTexto) {
        return routingService.geocodificar(origenTexto)
                .flatMap(origenCoords ->
                        routingService.geocodificar(destinoTexto)
                                .flatMap(destinoCoords ->
                                        cotizarViaje(origenCoords, destinoCoords)
                                )
                )
                .onErrorResume(e -> {
                    Map<String, Object> errorMap = new HashMap<>();
                    errorMap.put("error", "Error al buscar dirección o calcular ruta: " + e.getMessage());
                    return Mono.just(errorMap);
                });
    }

    private Map<String, Object> elegirTarifa(List<Map<String, Object>> fares) {
        if (fares == null || fares.isEmpty()) {
            return null;
        }
        for (Map<String, Object> fare : fares) {
            if (fare.containsKey("transponders")) {
                return fare;
            }
        }
        return fares.get(0);
    }
}