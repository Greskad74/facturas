package com.yesus74.facturas;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

@Controller
public class ViajeController {

    // Spring nos "inyecta" automáticamente el repositorio
    @Autowired
    private ViajeRepository viajeRepository;

    @GetMapping("/viajes")
    public String mostrarViajes(Model model) {
        // Traemos TODOS los viajes de la base de datos
        List<Viaje> viajes = viajeRepository.findAll();

        // Se los pasamos al HTML
        model.addAttribute("listaViajes", viajes);

        return "viajes";
    }
}