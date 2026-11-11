package com.yesus74.facturas;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PostMapping;


import java.util.Map;
@Controller
public class QuotationController {

    @Autowired
    private QuotationService quotationService;

    @GetMapping("/cotizar")
    public String mostrarFormulario() {
        return "cotizador";
    }

    @PostMapping("/cotizar")
    public String procesarCotizacion(@RequestParam String origen, @RequestParam String destino, Model model) {
        Map<String, Object> resultado = quotationService.cotizarViajePorDireccion(origen, destino).block();
        
        model.addAttribute("origen", origen);
        model.addAttribute("destino", destino);
        model.addAttribute("resultado", resultado);
        return "cotizador";
    }
}