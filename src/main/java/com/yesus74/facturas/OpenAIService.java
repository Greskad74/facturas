package com.yesus74.facturas;
import org.springframework.stereotype.Service;

import com.google.api.client.util.Value;

// NUEVAS IMPORTACIONES DE JACKSON 3
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

@Service
public class OpenAIService {
    @Value ("${open.IA.KEY}")
    private String OpenKey;
    // 
    
    private static final String API_URL = "https://api.openai.com/v1/chat/completions";

            private static final String SYSTEM_PROMPT = """
            Eres un asistente experto en logística y transporte de carga. Tu tarea es leer el contenido de un documento (texto extraído o imagen), clasificarlo y extraer la información en un formato JSON estrictamente definido.
            Reglas estrictas:
            1. Devuelve ÚNICAMENTE el objeto JSON, sin texto adicional, sin saludos, sin formato markdown (no uses ```json, solo las llaves {}).
            2. Si un dato no está en el texto o no lo puedes deducir con certeza, asigna el valor null.
            3. Las fechas deben estar en formato YYYY-MM-DD. Si hay fecha y hora, extrae solo la fecha.
            4. El monto del dinero debe ser un número flotante puro (ej. 2710.00), sin símbolos de pesos ni comas.
            5. El texto extraído puede estar desordenado o tener mucho texto legal basura. Ignora las cláusulas legales y concéntrate en los datos operativos.
            6. En "brokerNombre", trata de identificar el nombre de la empresa que emite el documento (ej. C.H. Robinson, Werner, D&G Express).
            7. En "referenciaExterna" prioriza el campo "Shipment #" o "Load #". No uses PO #, PU REF, MC # ni DOT # como referencia principal.
            8. Si un stop no tiene fecha propia, usa la fecha de "Appt Date/Time" o "Carrier ETA" de ese documento.
            9. "tipoDocumento" debe ser exactamente uno de: "RATE_CONFIRMATION", "POD" u "OTRO".
               - RATE_CONFIRMATION: documento del broker con tarifa (rate), origen, destino y condiciones para el transportista. Incluye los que ya vienen firmados.
               - POD: comprobante de entrega (Proof of Delivery), BOL firmado por quien recibe la carga, o foto/escaneo de un recibo de entrega. Suele tener firma, sellos y fecha de recepción, y normalmente NO trae una tarifa.
               - OTRO: cualquier documento que no sea ninguno de los dos (facturas, publicidad, correos).
            10. Si "tipoDocumento" es "POD" u "OTRO", deja rateUsd, origen y destino en null y stops como []. Llena solo tipoDocumento, referenciaExterna y referencias.
            11. "referencias" es la lista de TODOS los números de identificación visibles en el documento (Load #, Shipment #, Order #, PO #, BOL #, Pro #, PU REF, Delivery #), como strings y sin repetir.
            Estructura JSON requerida:
            {
              "tipoDocumento": "RATE_CONFIRMATION | POD | OTRO",
              "referenciaExterna": "string (Load #, Shipment #, Order #, etc.)",
              "referencias": ["string"],
              "brokerNombre": "string",
              "brokerEmail": "string",
              "rateUsd": float,
              "origen": { "nombre": "string", "direccion": "string", "ciudad": "string", "estado": "string", "codigoPostal": "string" },
              "destino": { "nombre": "string", "direccion": "string", "ciudad": "string", "estado": "string", "codigoPostal": "string" },
              "stops": [ { "orden": integer, "tipo": "PICKUP o DELIVERY", "nombre": "string", "direccion": "string", "ciudad": "string", "estado": "string", "codigoPostal": "string", "fecha": "YYYY-MM-DD" } ]
            }
            """;
        private static final String PROMPT_PAGO_FARO = """
            Eres un asistente que lee correos de remesas de pago de Faro Factoring LLC y extrae los datos en un JSON estrictamente definido.
            Reglas estrictas:
            1. Devuelve ÚNICAMENTE el objeto JSON, sin texto adicional y sin formato markdown.
            2. Si un dato no está o no lo puedes deducir con certeza, asigna null.
            3. Las fechas van en formato YYYY-MM-DD. La fecha del pago es la que aparece en la línea "REFERENCE #" (formato MM/DD/YYYY). Si no hay, null.
            4. Los montos son números flotantes puros y positivos, sin símbolos ni comas.
            5. El correo puede venir reenviado: ignora los encabezados de reenvío (Forwarded message, De:, To:) y el texto legal del final.
            6. Llena "pagos" solo con la tabla DETAIL, una entrada por factura. No uses la tabla SUMMARY para eso.
            7. Las filas pueden venir partidas en varias líneas. Júntalas.
            8. FV ID, Inv #, PO # y Other Ref # son enteros sin decimales; los montos siempre llevan dos decimales. "Other Ref #" puede venir vacío, así que no cuentes columnas: separa identificadores y montos con ese criterio.
            9. Los doce montos de cada fila vienen en este orden: Purchase Amount, Escrow, Cash Reserve, Discount, Misc Fees, Disp Fee, Loan, Save, Previously Paid, Due, Paid, Balance Due. Usa Purchase Amount como "montoComprado", Paid como "montoPagado" y Balance Due como "balancePendiente".
            10. "tipoCorreo" es "PAGO_FARO" si el correo es una remesa de pago de Faro Factoring; en cualquier otro caso es "OTRO" y "pagos" debe ser [].
            11. "totalPagado" es el valor de "TOTAL PAID".
            Estructura JSON requerida:
            {
              "tipoCorreo": "PAGO_FARO o OTRO",
              "referenciaPago": "string (valor de REFERENCE #)",
              "fechaPago": "YYYY-MM-DD",
              "totalPagado": float,
              "pagos": [ { "facturaFaro": "string", "invoice": "string", "po": "string", "otraRef": "string", "deudor": "string", "montoComprado": float, "montoPagado": float, "balancePendiente": float } ]
            }
            """;

    public String extraerPagoFaro(String textoCorreo) throws Exception {
        JsonMapper mapper = JsonMapper.builder().build();
        ObjectNode requestBody = mapper.createObjectNode();
        requestBody.put("model", "gpt-6-luna");

        ObjectNode responseFormat = mapper.createObjectNode();
        responseFormat.put("type", "json_object");
        requestBody.set("response_format", responseFormat);

        ArrayNode messages = mapper.createArrayNode();

        ObjectNode systemMessage = mapper.createObjectNode();
        systemMessage.put("role", "system");
        systemMessage.put("content", PROMPT_PAGO_FARO);
        messages.add(systemMessage);

        ObjectNode userMessage = mapper.createObjectNode();
        userMessage.put("role", "user");
        userMessage.put("content", textoCorreo);
        messages.add(userMessage);

        requestBody.set("messages", messages);

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + OpenKey)
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(requestBody)))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new RuntimeException("Error de OpenAI (pago Faro): " + response.body());
        }

        JsonNode rootNode = mapper.readTree(response.body());
        String contenido = rootNode.path("choices").path(0).path("message").path("content").asText();
        System.out.println("🤖 OpenAI (pago Faro) respondió: " + contenido);
        return contenido;
    }

    public String extraerDatosDePDF(String textoDelPDF) throws Exception {
        // En Jackson 3, la forma recomendada de crear el mapper es con el builder
        JsonMapper mapper = JsonMapper.builder().build();
        
        ObjectNode requestBody = mapper.createObjectNode();
        requestBody.put("model", "gpt-6-luna"); 
        
        ObjectNode responseFormat = mapper.createObjectNode();
        responseFormat.put("type", "json_object");
        requestBody.set("response_format", responseFormat);

        ArrayNode messages = mapper.createArrayNode();
        
        ObjectNode systemMessage = mapper.createObjectNode();
        systemMessage.put("role", "system");
        systemMessage.put("content", SYSTEM_PROMPT);
        messages.add(systemMessage);

        ObjectNode userMessage = mapper.createObjectNode();
        userMessage.put("role", "user");
        userMessage.put("content", textoDelPDF);
        messages.add(userMessage);

        requestBody.set("messages", messages);

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + OpenKey)
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(requestBody)))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Error de OpenAI: " + response.body());
        }

        JsonNode rootNode = mapper.readTree(response.body());
        String contenidoJSON = rootNode.path("choices").get(0).path("message").path("content").asText();

        return contenidoJSON;
    }

    public String extraerDatosDeImagen(String imagenBase64) throws Exception {
        JsonMapper mapper = JsonMapper.builder().build();
        ObjectNode requestBody = mapper.createObjectNode();
        requestBody.put("model", "gpt-6-luna"); 
        
        ObjectNode responseFormat = mapper.createObjectNode();
        responseFormat.put("type", "json_object");
        requestBody.set("response_format", responseFormat);

        ArrayNode messages = mapper.createArrayNode();
        
        ObjectNode systemMessage = mapper.createObjectNode();
        systemMessage.put("role", "system");
        systemMessage.put("content", SYSTEM_PROMPT);
        messages.add(systemMessage);

        // Aquí armamos el mensaje con la imagen
        ObjectNode userMessage = mapper.createObjectNode();
        userMessage.put("role", "user");
        ArrayNode contentArray = mapper.createArrayNode();
        
        ObjectNode textPart = mapper.createObjectNode();
        textPart.put("type", "text");
        textPart.put("text", "Clasifica este documento (Rate Confirmation, POD u OTRO) y extrae los datos.");
        contentArray.add(textPart);

        ObjectNode imagePart = mapper.createObjectNode();
        imagePart.put("type", "image_url");
        ObjectNode imageUrlObj = mapper.createObjectNode();
        imageUrlObj.put("url", "data:image/jpeg;base64," + imagenBase64);
        imagePart.set("image_url", imageUrlObj);
        contentArray.add(imagePart);

        userMessage.set("content", contentArray);
        messages.add(userMessage);

        requestBody.set("messages", messages);

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + OpenKey)
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(requestBody)))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Error de OpenAI Vision: " + response.body());
        }

        JsonNode rootNode = mapper.readTree(response.body());
        return rootNode.path("choices").get(0).path("message").path("content").asText();
    }
}