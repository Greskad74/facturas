package com.yesus74.facturas;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.GmailScopes;
import com.google.api.services.gmail.model.Message;
import com.google.api.services.gmail.model.MessagePart;
import com.google.api.services.gmail.model.MessagePartBody;
import com.google.api.services.gmail.model.MessagePartHeader;
import com.google.api.services.gmail.model.ModifyMessageRequest;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.nio.charset.StandardCharsets;

@Service
public class GmailService {

    @Value("${google.client.id}")
    private String clientId;

    @Value("${google.client.secret}")
    private String clientSecret;

    @Autowired
    private OpenAIService openAIService;

    @Autowired
    private RateConfirmationService rcService;

    @Autowired
    private ArchivoService archivoService;

    @Autowired
    private PodService podService;

    private static final String APPLICATION_NAME = "JSLH Transport App";
    private static final String TOKENS_DIRECTORY_PATH = "tokens";

    public Gmail getGmailService() throws Exception {
        final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
        GsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();

        GoogleClientSecrets.Details details = new GoogleClientSecrets.Details();
        details.setClientId(clientId);
        details.setClientSecret(clientSecret);
        GoogleClientSecrets clientSecrets = new GoogleClientSecrets().setInstalled(details);

        List<String> SCOPES = Collections.singletonList(GmailScopes.GMAIL_MODIFY);

        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, SCOPES)
                .setDataStoreFactory(new FileDataStoreFactory(new File(TOKENS_DIRECTORY_PATH)))
                .setAccessType("offline")
                .build();

        LocalServerReceiver receiver = new LocalServerReceiver.Builder().setPort(8889).build();
        Credential credential = new AuthorizationCodeInstalledApp(flow, receiver).authorize("user");

        return new Gmail.Builder(HTTP_TRANSPORT, JSON_FACTORY, credential)
                .setApplicationName(APPLICATION_NAME)
                .build();
    }

    public void procesarAdjuntos() {
        try {
            Gmail service = getGmailService();

            List<Message> messages = service.users().messages().list("me")
                    .setQ("is:unread has:attachment")
                    .execute().getMessages();

            if (messages == null || messages.isEmpty()) {
                System.out.println("📭 No hay correos nuevos con adjuntos.");
                return;
            }

            System.out.println("📬 Se encontraron " + messages.size() + " correos nuevos.");
            File carpetaEntrada = archivoService.carpetaEntrada().toFile();

            for (Message msg : messages) {
                Message fullMsg = service.users().messages().get("me", msg.getId()).execute();
                String asunto = obtenerAsunto(fullMsg);
                boolean todoOk = true;

                List<MessagePart> partes = new ArrayList<>();
                recolectarPartes(fullMsg.getPayload(), partes);

                for (MessagePart part : partes) {
                    String filename = part.getFilename();
                    if (filename == null || filename.isEmpty()) continue;

                    String lower = filename.toLowerCase();
                    if (!(lower.endsWith(".pdf") || lower.endsWith(".jpg")
                            || lower.endsWith(".jpeg") || lower.endsWith(".png"))) continue;

                    String attId = part.getBody() != null ? part.getBody().getAttachmentId() : null;
                    if (attId == null) continue;

                    System.out.println("📎 Descargando adjunto: " + filename + " (asunto: " + asunto + ")");
                    MessagePartBody attachPart = service.users().messages().attachments()
                            .get("me", msg.getId(), attId).execute();
                    byte[] fileData = Base64.getUrlDecoder().decode(attachPart.getData());

                    // Archivo temporal en uploads/_entrada (prefijo con el id del correo para no pisar nombres)
                    String nombreSeguro = msg.getId() + "_" + filename.replaceAll("[\\\\/]", "_");
                    File temporal = new File(carpetaEntrada, nombreSeguro);
                    Files.write(temporal.toPath(), fileData);

                    if (!procesarArchivoConIA(temporal, filename, asunto)) {
                        todoOk = false;
                    }
                }

                // Solo se marca como leído si todo salió bien (o era duplicado / POD pendiente)
                if (todoOk) {
                    service.users().messages().modify("me", msg.getId(),
                            new ModifyMessageRequest().setRemoveLabelIds(List.of("UNREAD")))
                            .execute();
                } else {
                    System.err.println("⚠️ El correo " + msg.getId() + " se dejó como no leído para reintentar.");
                }
            }
        } catch (Exception e) {
            System.err.println("❌ Error procesando correos: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private String obtenerAsunto(Message mensaje) {
        if (mensaje.getPayload() == null || mensaje.getPayload().getHeaders() == null) return "";
        for (MessagePartHeader h : mensaje.getPayload().getHeaders()) {
            if ("Subject".equalsIgnoreCase(h.getName())) {
                return h.getValue() == null ? "" : h.getValue();
            }
        }
        return "";
    }

    // Busca adjuntos también dentro de partes anidadas
    private void recolectarPartes(MessagePart parte, List<MessagePart> acumulado) {
        if (parte == null) return;
        acumulado.add(parte);
        if (parte.getParts() != null) {
            for (MessagePart hija : parte.getParts()) {
                recolectarPartes(hija, acumulado);
            }
        }
    }

    // Devuelve true si el archivo quedó resuelto (RC guardado, POD vinculado/pendiente, duplicado u otro);
    // false si hubo un error y conviene reintentar
    private boolean procesarArchivoConIA(File archivo, String nombreOriginal, String asunto) {
        try {
            String jsonRespuesta;

            if (archivo.getName().toLowerCase().endsWith(".pdf")) {
                try (PDDocument documento = Loader.loadPDF(archivo)) {
                    String texto = new PDFTextStripper().getText(documento);

                    if (texto.trim().isEmpty()) {
                        System.out.println("⚠️ PDF sin texto (escaneado). Usando visión...");
                        PDFRenderer renderer = new PDFRenderer(documento);
                        BufferedImage imagen = renderer.renderImageWithDPI(0, 150);
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        ImageIO.write(imagen, "jpg", baos);
                        String base64 = Base64.getEncoder().encodeToString(baos.toByteArray());
                        jsonRespuesta = openAIService.extraerDatosDeImagen(base64);
                    } else {
                        jsonRespuesta = openAIService.extraerDatosDePDF(texto);
                    }
                }
            } else {
                System.out.println("🖼️ Procesando imagen con visión...");
                byte[] bytes = Files.readAllBytes(archivo.toPath());
                String base64 = Base64.getEncoder().encodeToString(bytes);
                jsonRespuesta = openAIService.extraerDatosDeImagen(base64);
            }

            String tipo = podService.tipoDocumento(jsonRespuesta);
            System.out.println("🤖 IA respondió. Tipo de documento: " + tipo);

            // --- POD: se relaciona con su viaje y lo pasa a ENTREGADO ---
            if ("POD".equals(tipo)) {
                podService.procesarPod(archivo, jsonRespuesta, asunto, nombreOriginal);
                return true;
            }

            // --- Ni RC ni POD: se aparta para no reprocesarlo ---
            if ("OTRO".equals(tipo)) {
                archivoService.moverA(archivo, "_otros");
                System.out.println("ℹ️ " + nombreOriginal + " no es RC ni POD. Movido a uploads/_otros.");
                return true;
            }

            // --- Rate Confirmation ---
            RateConfirmation rc = rcService.procesarJSON(jsonRespuesta);
            String ruta = archivoService.guardarRC(rc, archivo);
            rcService.asignarArchivoRC(rc.getId(), ruta);
            System.out.println("🎉 RC " + rc.getReferenciaExterna() + " guardado en: " + ruta);

            // Si algún POD llegó antes que este RC, se relaciona ahora
            int vinculados = podService.reintentarPendientes();
            if (vinculados > 0) {
                System.out.println("📦 " + vinculados + " POD(s) pendiente(s) vinculado(s).");
            }
            return true;

        } catch (IllegalStateException e) {
            System.out.println("⚠️ " + archivo.getName() + " ya había sido procesado: " + e.getMessage());
            archivoService.moverADuplicados(archivo);
            return true;
        } catch (Exception e) {
            System.err.println("❌ Error procesando el archivo " + archivo.getName() + ": " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
        @Autowired
    private PagoFaroService pagoFaroService;

    // Solo remitentes conocidos: evita que cualquiera marque viajes como cobrados mandando un correo con ese asunto
    private static final String QUERY_PAGOS_FARO =
   
    "is:unread subject:\"Payment Remittance\" from:(notification@factorview.com OR jsuarez@jslhtransport.com)";

    public void procesarCorreosNuevos() {
        procesarAdjuntos();
        procesarPagosFaro();
    }
       private void procesarPagosFaro() {
        try {
            Gmail service = getGmailService();

            List<Message> messages = service.users().messages().list("me")
                    .setQ(QUERY_PAGOS_FARO)
                    .execute().getMessages();

            if (messages == null || messages.isEmpty()) return;

            System.out.println("💵 " + messages.size() + " correo(s) de pago de Faro por procesar.");

            for (Message msg : messages) {
                try {
                    Message full = service.users().messages().get("me", msg.getId()).execute();
                    String texto = extraerTexto(full.getPayload());
                    if (texto.isBlank()) {
                        System.err.println("⚠️ El correo " + msg.getId() + " no trae texto legible. Se deja sin leer.");
                        continue;
                    }

                    String json = openAIService.extraerPagoFaro(texto);

                    if (!pagoFaroService.esPagoFaro(json)) {
                        System.out.println("ℹ️ El correo " + msg.getId() + " no es una remesa de Faro.");
                    } else {
                        for (String linea : pagoFaroService.procesar(json)) {
                            System.out.println(linea);
                        }
                    }

                    // Solo llega aquí si la lectura fue válida; los errores dejan el correo sin leer para reintentar
                    service.users().messages().modify("me", msg.getId(),
                            new ModifyMessageRequest().setRemoveLabelIds(List.of("UNREAD")))
                            .execute();

                } catch (Exception e) {
                    System.err.println("❌ Error procesando el pago de Faro " + msg.getId() + ": " + e.getMessage());
                }
            }
        } catch (Exception e) {
            System.err.println("❌ Error buscando pagos de Faro: " + e.getMessage());
        }
    } 
        // Busca la parte text/plain del correo (Gmail ya la entrega decodificada)
    private String extraerTexto(MessagePart parte) {
        if (parte == null) return "";
        if ("text/plain".equalsIgnoreCase(parte.getMimeType())
                && parte.getBody() != null && parte.getBody().getData() != null) {
            return new String(Base64.getUrlDecoder().decode(parte.getBody().getData()), StandardCharsets.UTF_8);
        }
        if (parte.getParts() != null) {
            for (MessagePart hija : parte.getParts()) {
                String t = extraerTexto(hija);
                if (!t.isBlank()) return t;
            }
        }
        return "";
    }
}