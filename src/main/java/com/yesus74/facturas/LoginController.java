package com.yesus74.facturas;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class LoginController {

    @GetMapping("/login")
    public String mostrarLogin() {
        // Esto le dice a Spring: "Busca el archivo login.html en la carpeta templates"
        return "login"; 
    }
}