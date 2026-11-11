package com.yesus74.facturas;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Controller
public class TestPDFController {
    @Autowired
    private OpenAIService openAIService;

    @Autowired
    private RateConfirmationService rcService;

    // 1. Mostrar el formulario de subida
    @GetMapping("/upload")
    public String mostrarFormulario() {
        return "upload";
    }

    // 2. Procesar el archivo subido
        @PostMapping("/upload")
    public String procesarPDF(@RequestParam("archivo") MultipartFile archivo, Model model) {
        try {
            if (archivo.isEmpty()) {
                model.addAttribute("error", "El archivo está vacío.");
                return "upload";
            }

            File carpeta = new File("uploads");
            if (!carpeta.exists()) carpeta.mkdirs();
            Path rutaDestino = Paths.get("uploads", archivo.getOriginalFilename());
            Files.write(rutaDestino, archivo.getBytes());

            PDDocument documento = Loader.loadPDF(rutaDestino.toFile());
            PDFTextStripper extractor = new PDFTextStripper();
            String texto = extractor.getText(documento);

            String jsonRespuesta;

            // 👈 AQUÍ ESTÁ LA MAGIA: Si el texto está vacío, usamos Visión
            if (texto.trim().isEmpty()) {
                System.out.println("⚠️ PDF sin texto detectado. Convirtiendo a imagen para usar Visión...");
                
                // Convertimos la primera página del PDF a imagen
                org.apache.pdfbox.rendering.PDFRenderer renderer = new org.apache.pdfbox.rendering.PDFRenderer(documento);
                java.awt.image.BufferedImage imagen = renderer.renderImageWithDPI(0, 150); // Página 0, 150 DPI
                
                // Convertimos la imagen a Base64
                java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                javax.imageio.ImageIO.write(imagen, "jpg", baos);
                String base64 = java.util.Base64.getEncoder().encodeToString(baos.toByteArray());

                // Llamamos a la IA con la imagen
                jsonRespuesta = openAIService.extraerDatosDeImagen(base64);
            } else {
                // Si sí hay texto, seguimos por el camino normal
                jsonRespuesta = openAIService.extraerDatosDePDF(texto);
            }

            documento.close();

            // Guardar en base de datos
            RateConfirmation rc = rcService.procesarJSON(jsonRespuesta);

            model.addAttribute("exito", "¡RC procesado y guardado con éxito!");
            model.addAttribute("rc", rc);
            model.addAttribute("json", jsonRespuesta);

            return "upload";

        } catch (Exception e) {
            e.printStackTrace();
            model.addAttribute("error", "Error al procesar: " + e.getMessage());
            return "upload";
        }
    }
    @Autowired
    private GmailService gmailService;

    @GetMapping("/test-gmail")
    public String testGmail() {
        gmailService.procesarCorreosNuevos();
        return "redirect:/upload?msg=GmailProcesado"; // Redirige a la página de subida
    }
        }
    

