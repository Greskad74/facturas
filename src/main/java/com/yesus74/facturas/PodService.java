package com.yesus74.facturas;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class PodService {

    public enum Resultado { ASIGNADO, PENDIENTE }

    // Referencias más cortas que esto se ignoran para evitar coincidencias falsas
    private static final int MIN_LARGO = 4;

    @Autowired
    private RateConfirmationRepository rcRepository;

    @Autowired
    private PodPendienteRepository pendienteRepository;

    @Autowired
    private ArchivoService archivoService;

    private final JsonMapper mapper = JsonMapper.builder().build();

    // Si la IA no trae tipo, se asume que es un RC (comportamiento anterior)
    public String tipoDocumento(String json) {
        try {
            JsonNode n = mapper.readTree(json).path("tipoDocumento");
            if (n.isMissingNode() || n.isNull()) return "RATE_CONFIRMATION";
            String v = n.asText().trim().toUpperCase();
            return (v.isEmpty() || v.equals("NULL")) ? "RATE_CONFIRMATION" : v;
        } catch (Exception e) {
            return "RATE_CONFIRMATION";
        }
    }

    @Transactional
    public Resultado procesarPod(File archivo, String json, String asunto, String nombreOriginal) throws Exception {
        List<String> refs = leerReferencias(json);
        String contexto = recortar((asunto == null ? "" : asunto) + " " + (nombreOriginal == null ? "" : nombreOriginal));

        List<RateConfirmation> coincidencias = buscar(refs, contexto);

        if (coincidencias.size() == 1) {
            RateConfirmation rc = coincidencias.get(0);
            vincular(rc, archivo.toPath());
            System.out.println("✅ POD vinculado al viaje " + rc.getReferenciaExterna() + " (estado: " + rc.getEstado() + ")");
            return Resultado.ASIGNADO;
        }

        String motivo = coincidencias.isEmpty() ? "no coincide con ningún viaje" : "coincide con varios viajes";
        System.out.println("⚠️ POD " + nombreOriginal + " " + motivo + ". Referencias leídas: " + refs);

        PodPendiente p = new PodPendiente();
        p.setArchivo(archivoService.moverA(archivo, "_pods_sin_asignar"));
        p.setReferencias(String.join("|", refs));
        p.setContexto(contexto);
        p.setFechaRecibido(LocalDate.now());
        pendienteRepository.save(p);
        return Resultado.PENDIENTE;
    }

    // Se llama cuando entra un RC nuevo: intenta relacionar los PODs que estaban esperando
    @Transactional
    public int reintentarPendientes() {
        int vinculados = 0;
        for (PodPendiente p : pendienteRepository.findAll()) {
            try {
                List<RateConfirmation> c = buscar(dividir(p.getReferencias()), p.getContexto());
                if (c.size() == 1) {
                    Path origen = archivoService.resolver(p.getArchivo());
                    vincular(c.get(0), origen);
                    pendienteRepository.delete(p);
                    vinculados++;
                    System.out.println("✅ POD pendiente vinculado al viaje " + c.get(0).getReferenciaExterna());
                }
            } catch (IOException e) {
                System.err.println("No se pudo reintentar el POD pendiente " + p.getArchivo() + ": " + e.getMessage());
            }
        }
        return vinculados;
    }

    private void vincular(RateConfirmation rc, Path origen) throws IOException {
        String ruta = archivoService.guardarPOD(rc, origen);
        if (rc.getEvidenciaPOD() == null) {
            rc.setEvidenciaPOD(ruta);
        }
        // Solo avanza; nunca regresa un viaje que ya está en Faro o cobrado
        if ("ASIGNADO".equals(rc.getEstado()) || "EN_CURSO".equals(rc.getEstado())) {
            rc.setEstado("ENTREGADO");
            rc.setFechaEntrega(LocalDate.now());
        }
        rcRepository.save(rc);
    }

    // Primero busca por la referencia principal; si no hay, por las alternas (PO, BOL...)
    private List<RateConfirmation> buscar(List<String> refsIA, String contexto) {
        Set<String> refsNorm = refsIA.stream()
                .map(this::normalizar)
                .filter(s -> s.length() >= MIN_LARGO)
                .collect(Collectors.toSet());

        List<RateConfirmation> todos = rcRepository.findAll();

        List<RateConfirmation> fuertes = todos.stream()
                .filter(rc -> coincide(List.of(rc.getReferenciaExterna()), refsNorm, contexto))
                .toList();
        if (!fuertes.isEmpty()) return fuertes;

        return todos.stream()
                .filter(rc -> coincide(dividir(rc.getReferenciasAlternas()), refsNorm, contexto))
                .toList();
    }

    private boolean coincide(List<String> candidatas, Set<String> refsNorm, String contexto) {
        for (String c : candidatas) {
            if (c == null) continue;
            String n = normalizar(c);
            if (n.length() < MIN_LARGO) continue;
            if (refsNorm.contains(n) || apareceEnTexto(c, contexto)) return true;
        }
        return false;
    }

    // Busca la referencia como palabra completa en el texto, tolerando guion o guion bajo
    // (DGX4240-02 coincide con DGX4240_02, pero 30007 no coincide dentro de 130007)
    private boolean apareceEnTexto(String ref, String texto) {
        if (texto == null || texto.isBlank()) return false;
        List<String> partes = Arrays.stream(ref.toUpperCase(Locale.ROOT).split("[^A-Z0-9]+"))
                .filter(t -> !t.isEmpty())
                .map(Pattern::quote)
                .toList();
        if (partes.isEmpty()) return false;
        String regex = "(?<![A-Z0-9])" + String.join("[^A-Z0-9]{0,2}", partes) + "(?![A-Z0-9])";
        return Pattern.compile(regex).matcher(texto.toUpperCase(Locale.ROOT)).find();
    }

    private List<String> leerReferencias(String json) throws Exception {
        JsonNode root = mapper.readTree(json);
        Set<String> refs = new LinkedHashSet<>();
        agregar(refs, root.path("referenciaExterna"));
        JsonNode arr = root.path("referencias");
        if (arr.isArray()) {
            for (JsonNode n : arr) agregar(refs, n);
        }
        return new ArrayList<>(refs);
    }

    private void agregar(Set<String> refs, JsonNode n) {
        if (n.isMissingNode() || n.isNull()) return;
        String v = n.asText().trim();
        if (!v.isEmpty() && !v.equalsIgnoreCase("null")) refs.add(v);
    }

    private List<String> dividir(String s) {
        if (s == null || s.isBlank()) return List.of();
        return Arrays.asList(s.split("\\|"));
    }

    private String normalizar(String s) {
        return s == null ? "" : s.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
    }

    private String recortar(String s) {
        return s.length() > 1000 ? s.substring(0, 1000) : s;
    }
}