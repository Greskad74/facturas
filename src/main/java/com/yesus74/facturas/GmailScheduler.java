package com.yesus74.facturas;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class GmailScheduler {

    @Autowired
    private GmailService gmailService;

    // Se ejecuta cada 10 minutos, en el minuto 0, 10, 20, 30, etc.
    // El formato cron es: segundo minuto hora día-del-mes mes día-de-la-semana
    @Scheduled(cron = "0 0/10 * * * ?") 
    public void revisarCorreosCada10Minutos() {
        System.out.println("⏰ [Scheduler] Revisando correos nuevos...");
        gmailService.procesarCorreosNuevos();
    }
}