package com.yesus74.facturas;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import jakarta.persistence.Column;
import java.util.ArrayList;
import java.util.List;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;


@Service
public class RateConfirmationService {

    @Autowired
    private RateConfirmationRepository rcRepository;
        // Otros números del viaje (PO, BOL, etc.) separados por "|", sirven para relacionar PODs
    
    

    @Autowired
    private BrokerRepository brokerRepository;

    // Devuelve null si el campo falta, es null, está vacío o es el texto "null"
    private String texto(JsonNode node, String campo) {
        JsonNode n = node.path(campo);
        if (n.isMissingNode() || n.isNull()) return null;
        String v = n.asText().trim();
        return (v.isEmpty() || v.equalsIgnoreCase("null")) ? null : v;
    }

    private LocalDate fecha(JsonNode node, String campo) {
        String v = texto(node, campo);
        if (v == null) return null;
        try {
            return LocalDate.parse(v);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private String ciudadEstado(JsonNode node) {
        String ciudad = texto(node, "ciudad");
        String estado = texto(node, "estado");
        if (ciudad == null && estado == null) return null;
        if (ciudad == null) return estado;
        if (estado == null) return ciudad;
        return ciudad + ", " + estado;
    }

    public RateConfirmation procesarJSON(String json) throws Exception {
        JsonMapper mapper = JsonMapper.builder().build();
        JsonNode root = mapper.readTree(json);

        String referencia = texto(root, "referenciaExterna");
        String brokerNombre = texto(root, "brokerNombre");

        if (referencia == null || brokerNombre == null) {
            throw new IllegalArgumentException(
                    "No se pudo leer la referencia o el broker del documento. JSON recibido: " + json);
        }

        // Validar duplicado ANTES de crear nada
        if (rcRepository.findByReferenciaExterna(referencia).isPresent()) {
            throw new IllegalStateException("Ya existe un Rate Confirmation con la referencia " + referencia);
        }

        // 1. Buscar o crear el Broker
        Broker broker = brokerRepository.findByNombreIgnoreCase(brokerNombre)
                .orElseGet(() -> {
                    Broker nuevo = new Broker();
                    nuevo.setNombre(brokerNombre);
                    nuevo.setEmail(texto(root, "brokerEmail"));
                    return brokerRepository.save(nuevo);
                });

        // 2. Crear el RateConfirmation
        RateConfirmation rc = new RateConfirmation();
        rc.setReferenciaExterna(referencia);
        rc.setBroker(broker);

        String rate = texto(root, "rateUsd");
        if (rate != null) {
            rc.setRateUsd(new BigDecimal(rate));
        }

        rc.setEstado("ASIGNADO");
                // Otros números del documento (PO, BOL...) para relacionar PODs después
        JsonNode refs = root.path("referencias");
        if (refs.isArray()) {
            List<String> alternas = new ArrayList<>();
            for (JsonNode r : refs) {
                String v = r.asText("").trim();
                if (!v.isEmpty() && !v.equalsIgnoreCase("null") && !v.equalsIgnoreCase(referencia)) {
                    alternas.add(v);
                }
            }
            String unidas = String.join("|", alternas);
            rc.setReferenciasAlternas(unidas.length() > 1000 ? unidas.substring(0, 1000) : unidas);
        }
        rc.setFechaCreacion(LocalDate.now());
        rc.setOrigen(ciudadEstado(root.path("origen")));
        rc.setDestino(ciudadEstado(root.path("destino")));

        // 3. Agregar los Stops
        JsonNode stopsArray = root.path("stops");
        if (stopsArray.isArray()) {
            for (JsonNode s : stopsArray) {
                Stop stop = new Stop();
                stop.setOrden(s.path("orden").asInt());
                stop.setTipo(texto(s, "tipo"));
                stop.setDireccion(texto(s, "direccion"));
                stop.setCiudad(texto(s, "ciudad"));
                stop.setEstado(texto(s, "estado"));
                stop.setFecha(fecha(s, "fecha"));
                stop.setRateConfirmation(rc);
                rc.getStops().add(stop);
            }
        }

        return rcRepository.save(rc);
    }
    public void asignarArchivoRC(Long id, String ruta) {
    rcRepository.findById(id).ifPresent(rc -> {
        rc.setArchivoRC(ruta);
        rcRepository.save(rc);
    });
}
}