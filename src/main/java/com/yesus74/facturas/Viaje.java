package com.yesus74.facturas;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
public class Viaje {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String folio;
    private String cliente;
    private String origen;
    private String destino;
    private String estado; // COBRADO, PENDIENTE, EN_CURSO

    // Constructor vacío (obligatorio para JPA)
    public Viaje() {
    }

    // Constructor con parámetros (útil para crear objetos rápido)
    public Viaje(String folio, String cliente, String origen, String destino, String estado) {
        this.folio = folio;
        this.cliente = cliente;
        this.origen = origen;
        this.destino = destino;
        this.estado = estado;
    }

    // Getters y Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getFolio() { return folio; }
    public void setFolio(String folio) { this.folio = folio; }

    public String getCliente() { return cliente; }
    public void setCliente(String cliente) { this.cliente = cliente; }

    public String getOrigen() { return origen; }
    public void setOrigen(String origen) { this.origen = origen; }

    public String getDestino() { return destino; }
    public void setDestino(String destino) { this.destino = destino; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }
}