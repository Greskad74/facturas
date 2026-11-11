package com.yesus74.facturas;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Controller
public class ArchivoController {

    @Autowired
    private RateConfirmationRepository rcRepository;

    @Autowired
    private ArchivoService archivoService;

    @PostMapping("/rc/{id}/pod")
    public String subirPod(@PathVariable Long id,
                           @RequestParam("archivo") MultipartFile archivo,
                           RedirectAttributes ra) {
        RateConfirmation rc = rcRepository.findById(id).orElse(null);
        if (rc == null) {
            ra.addFlashAttribute("error", "No existe el viaje.");
            return "redirect:/";
        }
        try {
            String ruta = archivoService.guardarPOD(rc, archivo);
            rc.setEvidenciaPOD(ruta);
            rcRepository.save(rc);
            ra.addFlashAttribute("mensaje", "POD guardado para " + rc.getReferenciaExterna());
        } catch (Exception e) {
            ra.addFlashAttribute("error", "No se pudo guardar el POD: " + e.getMessage());
        }
        return "redirect:/";
    }

    // tipo = "pod" o "rc"
    @GetMapping("/rc/{id}/archivo/{tipo}")
    public ResponseEntity<Resource> descargar(@PathVariable Long id, @PathVariable String tipo) {
        RateConfirmation rc = rcRepository.findById(id).orElse(null);
        if (rc == null) return ResponseEntity.notFound().build();

        String ruta = switch (tipo) {
            case "pod" -> rc.getEvidenciaPOD();
            case "rc" -> rc.getArchivoRC();
            default -> null;
        };
        if (ruta == null) return ResponseEntity.notFound().build();

        try {
            Path p = archivoService.resolver(ruta);
            String mime = Files.probeContentType(p);
            if (mime == null) mime = "application/octet-stream";

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            ContentDisposition.attachment().filename(p.getFileName().toString()).build().toString())
                    .contentType(MediaType.parseMediaType(mime))
                    .body(new FileSystemResource(p));
        } catch (IOException e) {
            return ResponseEntity.notFound().build();
        }
    }
}