package com.yesus74.facturas;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
public class TestQuotationController {

    @Autowired
    private QuotationService quotationService;

    @GetMapping("/test-cotizacion")
    public Mono<Map<String, Object>> probarCotizacion(
            @RequestParam String origen, 
            @RequestParam String destino) {
        
        // Imprimimos en consola para saber que la petición llegó
        System.out.println("📞 Solicitando cotización de: " + origen + " a " + destino);
        
        return quotationService.cotizarViaje(origen, destino);
    }
}