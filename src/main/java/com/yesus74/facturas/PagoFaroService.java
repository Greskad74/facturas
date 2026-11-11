package com.yesus74.facturas;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Service
public class PagoFaroService {

    private static final int MIN_LARGO = 4;

    @Autowired
    private RateConfirmationRepository rcRepository;

    private final JsonMapper mapper = JsonMapper.builder().build();

    public boolean esPagoFaro(String json) {
        try {
            return "PAGO_FARO".equalsIgnoreCase(mapper.readTree(json).path("tipoCorreo").asText(""));
        } catch (Exception e) {
            return false;
        }
    }

    // Devuelve un resumen por factura para imprimir en consola
    @Transactional
    public List<String> procesar(String json) throws Exception {
        JsonNode root = mapper.readTree(json);
        JsonNode pagos = root.path("pagos");
        if (!pagos.isArray() || pagos.size() == 0) {
            throw new IllegalArgumentException("El correo no trae filas de pago. JSON: " + json);
        }

        // Validación: la suma de las filas debe igualar TOTAL PAID
        BigDecimal suma = BigDecimal.ZERO;
        for (JsonNode p : pagos) {
            BigDecimal m = decimal(p, "montoPagado");
            if (m == null) throw new IllegalArgumentException("Fila sin montoPagado: " + p);
            suma = suma.add(m);
        }
        BigDecimal total = decimal(root, "totalPagado");
        if (total == null || suma.subtract(total).abs().compareTo(new BigDecimal("0.02")) > 0) {
            throw new IllegalArgumentException("La suma de las filas (" + suma
                    + ") no coincide con TOTAL PAID (" + total + "). No se aplicó nada.");
        }

        LocalDate fecha = fecha(root, "fechaPago");
        if (fecha == null) fecha = LocalDate.now();
        String refPago = texto(root, "referenciaPago");

        List<RateConfirmation> todos = rcRepository.findAll();
        List<String> resumen = new ArrayList<>();

        for (JsonNode p : pagos) {
            List<String> ids = new ArrayList<>();
            for (String campo : List.of("invoice", "po", "otraRef")) {
                String v = texto(p, campo);
                if (v != null) ids.add(normalizar(v));
            }
            ids.removeIf(s -> s.length() < MIN_LARGO);

            List<RateConfirmation> c = todos.stream().filter(rc -> coincide(rc, ids)).toList();
            if (c.size() != 1) {
                resumen.add("⚠️ Factura " + ids + ": " + (c.isEmpty() ? "no hay un viaje con ese número" : "coincide con varios viajes") + ". No se cambió nada.");
                continue;
            }

            RateConfirmation rc = c.get(0);
            String estado = rc.getEstado();

            if ("COBRADO".equals(estado)) {
                resumen.add("ℹ️ " + rc.getReferenciaExterna() + " ya estaba cobrado.");
                continue;
            }
            if (!"SUBIDO_A_FARO".equals(estado) && !"ENTREGADO".equals(estado)) {
                resumen.add("⚠️ " + rc.getReferenciaExterna() + " está en " + estado + ", no se cambió.");
                continue;
            }

            BigDecimal comprado = decimal(p, "montoComprado");
            if (comprado != null && rc.getRateUsd() != null && comprado.compareTo(rc.getRateUsd()) != 0) {
                resumen.add("⚠️ " + rc.getReferenciaExterna() + ": Faro compró " + comprado + " y el RC dice " + rc.getRateUsd() + ". Revisa.");
            }

            rc.setEstado("COBRADO");
            rc.setMontoPagadoUsd(decimal(p, "montoPagado"));
            rc.setFechaPago(fecha);
            rc.setReferenciaPagoFaro(refPago);
            rcRepository.save(rc);
            resumen.add("💰 " + rc.getReferenciaExterna() + " → COBRADO (" + decimal(p, "montoPagado") + " USD)");
        }
        return resumen;
    }

    private boolean coincide(RateConfirmation rc, List<String> ids) {
        if (ids.contains(normalizar(rc.getReferenciaExterna()))) return true;
        if (rc.getReferenciasAlternas() == null) return false;
        for (String a : rc.getReferenciasAlternas().split("\\|")) {
            if (ids.contains(normalizar(a))) return true;
        }
        return false;
    }

    private String texto(JsonNode node, String campo) {
        JsonNode n = node.path(campo);
        if (n.isMissingNode() || n.isNull()) return null;
        String v = n.asText().trim();
        return (v.isEmpty() || v.equalsIgnoreCase("null")) ? null : v;
    }

    private BigDecimal decimal(JsonNode node, String campo) {
        String v = texto(node, campo);
        if (v == null) return null;
        try {
            return new BigDecimal(v.replace(",", ""));
        } catch (NumberFormatException e) {
            return null;
        }
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

    private String normalizar(String s) {
        return s == null ? "" : s.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
    }
}