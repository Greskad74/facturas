package com.yesus74.facturas;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.Comparator;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

@Service
public class ArchivoService {

    private static final Locale ES = Locale.of("es", "MX");
    private static final Set<String> EXT_POD = Set.of("pdf", "jpg", "jpeg", "png");

    private final Path base = Paths.get("uploads").toAbsolutePath().normalize();

    public Path carpetaEntrada() throws IOException {
        return Files.createDirectories(base.resolve("_entrada"));
    }

    // Año / mes / broker / viaje
    public Path carpetaViaje(RateConfirmation rc) throws IOException {
        LocalDate f = fechaReferencia(rc);
        String mes = String.format("%02d-%s", f.getMonthValue(),
                f.getMonth().getDisplayName(TextStyle.FULL, ES));
        String broker = limpiar(rc.getBroker() != null ? rc.getBroker().getNombre() : null);

        Path dir = base.resolve(String.valueOf(f.getYear()))
                .resolve(mes)
                .resolve(broker)
                .resolve(limpiar(rc.getReferenciaExterna()));
        return Files.createDirectories(dir);
    }

    // Mueve el archivo descargado a la carpeta del viaje. Devuelve la ruta relativa.
    public String guardarRC(RateConfirmation rc, File origen) throws IOException {
        String nombre = "RC_" + limpiar(rc.getReferenciaExterna()) + "." + extension(origen.getName());
        Path destino = carpetaViaje(rc).resolve(nombre);
        Files.move(origen.toPath(), destino, StandardCopyOption.REPLACE_EXISTING);
        return base.relativize(destino).toString();
    }

    // Guarda el POD junto al RC. Devuelve la ruta relativa.
    public String guardarPOD(RateConfirmation rc, MultipartFile archivo) throws IOException {
        if (archivo == null || archivo.isEmpty()) {
            throw new IOException("No se recibió ningún archivo.");
        }
        String ext = extension(archivo.getOriginalFilename());
        if (!EXT_POD.contains(ext)) {
            throw new IOException("Formato no permitido (usa PDF, JPG o PNG).");
        }

        Path carpeta = carpetaDe(rc);

        // Si ya había un POD, se reemplaza
        if (rc.getEvidenciaPOD() != null) {
            Path anterior = base.resolve(rc.getEvidenciaPOD()).normalize();
            if (anterior.startsWith(base)) Files.deleteIfExists(anterior);
        }

        Path destino = carpeta.resolve("POD_" + limpiar(rc.getReferenciaExterna()) + "." + ext);
        try (InputStream in = archivo.getInputStream()) {
            Files.copy(in, destino, StandardCopyOption.REPLACE_EXISTING);
        }
        return base.relativize(destino).toString();
    }

    // Convierte una ruta guardada en un Path seguro y existente
    public Path resolver(String rutaRelativa) throws IOException {
        Path p = base.resolve(rutaRelativa).normalize();
        if (!p.startsWith(base) || !Files.isRegularFile(p)) {
            throw new IOException("Archivo no encontrado.");
        }
        return p;
    }

    public void moverADuplicados(File archivo) {
        try {
            Path dir = Files.createDirectories(base.resolve("_duplicados"));
            Files.move(archivo.toPath(), dir.resolve(archivo.getName()), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            System.err.println("No se pudo mover a _duplicados: " + e.getMessage());
        }
    }

    // El POD va en la misma carpeta que el RC; si no hay RC guardado, se calcula la carpeta
    private Path carpetaDe(RateConfirmation rc) throws IOException {
        if (rc.getArchivoRC() != null) {
            Path rcPath = base.resolve(rc.getArchivoRC()).normalize();
            if (rcPath.startsWith(base) && rcPath.getParent() != null) {
                return Files.createDirectories(rcPath.getParent());
            }
        }
        return carpetaViaje(rc);
    }

    private LocalDate fechaReferencia(RateConfirmation rc) {
        LocalDate pickup = rc.getStops().stream()
                .filter(s -> "PICKUP".equalsIgnoreCase(s.getTipo()) && s.getFecha() != null)
                .map(Stop::getFecha)
                .min(Comparator.naturalOrder())
                .orElse(null);
        if (pickup != null) return pickup;

        LocalDate cualquiera = rc.getStops().stream()
                .map(Stop::getFecha)
                .filter(Objects::nonNull)
                .min(Comparator.naturalOrder())
                .orElse(null);
        if (cualquiera != null) return cualquiera;

        return rc.getFechaCreacion() != null ? rc.getFechaCreacion() : LocalDate.now();
    }

    // Deja solo letras, números, espacios, guion y guion bajo (evita rutas peligrosas)
    private String limpiar(String s) {
        if (s == null || s.isBlank()) return "sin-nombre";
        String c = s.replaceAll("[^\\p{L}\\p{N} _-]", "").replaceAll("\\s+", " ").trim();
        return c.isEmpty() ? "sin-nombre" : c;
    }

    private String extension(String nombre) {
        if (nombre == null || !nombre.contains(".")) return "bin";
        String ext = nombre.substring(nombre.lastIndexOf('.') + 1).toLowerCase().replaceAll("[^a-z0-9]", "");
        return ext.isEmpty() ? "bin" : ext;
    }
        // Mueve un POD recibido por correo a la carpeta del viaje. Si ya hay un POD, guarda otro con sufijo _2, _3...
    public String guardarPOD(RateConfirmation rc, Path origen) throws IOException {
        String ext = extension(origen.getFileName().toString());
        Path carpeta = carpetaDe(rc);
        String nombreBase = "POD_" + limpiar(rc.getReferenciaExterna());

        Path destino = carpeta.resolve(nombreBase + "." + ext);
        int n = 2;
        while (Files.exists(destino)) {
            destino = carpeta.resolve(nombreBase + "_" + n++ + "." + ext);
        }
        Files.move(origen, destino);
        return base.relativize(destino).toString();
    }

    // Mueve un archivo a uploads/<subcarpeta>/ y devuelve la ruta relativa
    public String moverA(File archivo, String subcarpeta) throws IOException {
        Path dir = Files.createDirectories(base.resolve(subcarpeta));
        Path destino = dir.resolve(archivo.getName());
        Files.move(archivo.toPath(), destino, StandardCopyOption.REPLACE_EXISTING);
        return base.relativize(destino).toString();
    }
}