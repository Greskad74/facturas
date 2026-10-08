package com.yesus74.facturas;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.math.BigDecimal;
import java.util.List;

@Controller
public class MenuController {

    @Autowired
    private RateConfirmationRepository rcRepository;

    @Autowired
    private BrokerRepository brokerRepository;

    @GetMapping("/")
    public String mostrarMenu(Model model) {

        // Traemos los RCs agrupados por estado
        List<RateConfirmation> pendientesDeSubir = rcRepository.findByEstado("ENTREGADO");
        List<RateConfirmation> subidosAFaro = rcRepository.findByEstado("SUBIDO_A_FARO");
        List<RateConfirmation> cobrados = rcRepository.findByEstado("COBRADO");
        List<RateConfirmation> enCurso = rcRepository.findByEstado("EN_CURSO");

        // Los metemos en el "model" para que el HTML los pueda usar
        model.addAttribute("pendientesDeSubir", pendientesDeSubir);
        model.addAttribute("subidosAFaro", subidosAFaro);
        model.addAttribute("cobrados", cobrados);
        model.addAttribute("enCurso", enCurso);

        // Calculamos el total cobrado en USD
        BigDecimal totalCobrado = cobrados.stream()
                .map(RateConfirmation::getRateUsd)
                .filter(r -> r != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        model.addAttribute("totalCobrado", totalCobrado);

        // Calculamos el total pendiente por cobrar (subidos a Faro)
        BigDecimal totalPorCobrar = subidosAFaro.stream()
                .map(RateConfirmation::getRateUsd)
                .filter(r -> r != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        model.addAttribute("totalPorCobrar", totalPorCobrar);

        // Contadores
        model.addAttribute("cantEnCurso", enCurso.size());
        model.addAttribute("cantPendientes", pendientesDeSubir.size());
        model.addAttribute("cantSubidos", subidosAFaro.size());
        model.addAttribute("cantCobrados", cobrados.size());

        return "menu";
    }
}