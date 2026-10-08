package com.yesus74.facturas;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;

@Component
public class DatosIniciales implements CommandLineRunner {

    @Autowired
    private BrokerRepository brokerRepository;

    @Autowired
    private RateConfirmationRepository rcRepository;

    @Override
    public void run(String... args) throws Exception {
        if (brokerRepository.count() > 0) {
            return; // Ya hay datos, no duplicar
        }

        // 1. Crear brokers reales
        Broker chRobinson = brokerRepository.save(
                new Broker("C.H. Robinson", "Sergio Cano", "Sergio.Cano@chrobinson.com", "(773) 435-8792"));

        Broker werner = brokerRepository.save(
                new Broker("Werner Enterprises", "Fernando Mendez", "carrierimaging@werner.com", "(866) 868-5324"));

        Broker usaTruck = brokerRepository.save(
                new Broker("USA Truck", "Zachary Jacobson", "usapay@usa-truck.com", "(479) 384-4060"));

        Broker setFreight = brokerRepository.save(
                new Broker("SET Freight International", "Roxana Torres", "roxana.torres@palosgarza.com", "867 193 6386"));

        // 2. Crear Rate Confirmations reales
        RateConfirmation rc1 = new RateConfirmation("569572294", chRobinson, "Richmond, IN", "Laredo, TX");
        rc1.setMillas(1400);
        rc1.setRateUsd(new BigDecimal("2710.00"));
        rc1.setFechaCreacion(LocalDate.of(2026, 9, 30));
        rc1.setEstado("COBRADO");
        rcRepository.save(rc1);

        RateConfirmation rc2 = new RateConfirmation("2004586262", werner, "Rochester, MN", "Laredo, TX");
        rc2.setMillas(1318);
        rc2.setRateUsd(new BigDecimal("4463.00"));
        rc2.setFechaCreacion(LocalDate.of(2026, 9, 25));
        rc2.setEstado("SUBIDO_A_FARO");
        rcRepository.save(rc2);

        RateConfirmation rc3 = new RateConfirmation("9792319", usaTruck, "DEKALB, IL", "Laredo, TX");
        rc3.setMillas(1320);
        rc3.setRateUsd(new BigDecimal("2400.00"));
        rc3.setFechaCreacion(LocalDate.of(2026, 6, 24));
        rc3.setEstado("COBRADO");
        rcRepository.save(rc3);

        RateConfirmation rc4 = new RateConfirmation("SET10733/26", setFreight, "Nuevo Laredo", "Mount Bethel, PA");
        rc4.setMillas(1900);
        rc4.setRateUsd(new BigDecimal("4900.00"));
        rc4.setFechaCreacion(LocalDate.of(2026, 8, 20));
        rc4.setEstado("ENTREGADO");
        rcRepository.save(rc4);

        System.out.println("✅ Datos iniciales insertados: 4 brokers, 4 RCs");
    }
}