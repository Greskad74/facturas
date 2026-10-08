package com.yesus74.facturas;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
public class RateConfirmation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Este es el número que el freelancer usa: Load#, Order#, Route#, etc.
    @Column(unique = true, nullable = false)
    private String referenciaExterna;

    // Relación: muchos RateConfirmations pertenecen a UN Broker
    @ManyToOne
    @JoinColumn(name = "broker_id")
    private Broker broker;

    private String origen;
    private String destino;
    private Integer millas;

    // Dinero: SIEMPRE usar BigDecimal, no Double (para no perder centavos)
    @Column(precision = 12, scale = 2)
    private BigDecimal rateUsd;

    private LocalDate fechaCreacion;
    private LocalDate fechaEntrega;

    // Estados posibles: ASIGNADO, EN_CURSO, ENTREGADO, SUBIDO_A_FARO, COBRADO
    private String estado;

    // Rutas de archivos en el disco
    private String archivoRC;      // PDF original del RC
    private String evidenciaPOD;   // Foto de la evidencia de entrega

    // Relación: UN RateConfirmation tiene MUCHOS Stops
    @OneToMany(mappedBy = "rateConfirmation", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Stop> stops = new ArrayList<>();

    // Constructor vacío
    public RateConfirmation() {
    }

    // Constructor útil
    public RateConfirmation(String referenciaExterna, Broker broker, String origen, String destino) {
        this.referenciaExterna = referenciaExterna;
        this.broker = broker;
        this.origen = origen;
        this.destino = destino;
        this.estado = "ASIGNADO";
    }

    // Getters y Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getReferenciaExterna() { return referenciaExterna; }
    public void setReferenciaExterna(String referenciaExterna) { this.referenciaExterna = referenciaExterna; }

    public Broker getBroker() { return broker; }
    public void setBroker(Broker broker) { this.broker = broker; }

    public String getOrigen() { return origen; }
    public void setOrigen(String origen) { this.origen = origen; }

    public String getDestino() { return destino; }
    public void setDestino(String destino) { this.destino = destino; }

    public Integer getMillas() { return millas; }
    public void setMillas(Integer millas) { this.millas = millas; }

    public BigDecimal getRateUsd() { return rateUsd; }
    public void setRateUsd(BigDecimal rateUsd) { this.rateUsd = rateUsd; }

    public LocalDate getFechaCreacion() { return fechaCreacion; }
    public void setFechaCreacion(LocalDate fechaCreacion) { this.fechaCreacion = fechaCreacion; }

    public LocalDate getFechaEntrega() { return fechaEntrega; }
    public void setFechaEntrega(LocalDate fechaEntrega) { this.fechaEntrega = fechaEntrega; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }

    public String getArchivoRC() { return archivoRC; }
    public void setArchivoRC(String archivoRC) { this.archivoRC = archivoRC; }

    public String getEvidenciaPOD() { return evidenciaPOD; }
    public void setEvidenciaPOD(String evidenciaPOD) { this.evidenciaPOD = evidenciaPOD; }

    public List<Stop> getStops() { return stops; }
    public void setStops(List<Stop> stops) { this.stops = stops; }
}