package com.yesus74.facturas;
import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
public class Stop {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Relación inversa: este Stop pertenece a UN RateConfirmation
    @ManyToOne
    @JoinColumn(name = "rate_confirmation_id")
    private RateConfirmation rateConfirmation;

    private Integer orden;        // 1, 2, 3... para ordenar
    private String tipo;          // PICKUP, DELIVERY, YARD
    private String direccion;
    private String ciudad;
    private String estado;        // TX, PA, MN, etc.
    private LocalDate fecha;

    public Stop() {
    }

    public Stop(Integer orden, String tipo, String direccion, String ciudad, String estado) {
        this.orden = orden;
        this.tipo = tipo;
        this.direccion = direccion;
        this.ciudad = ciudad;
        this.estado = estado;
    }

    // Getters y Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public RateConfirmation getRateConfirmation() { return rateConfirmation; }
    public void setRateConfirmation(RateConfirmation rateConfirmation) { this.rateConfirmation = rateConfirmation; }

    public Integer getOrden() { return orden; }
    public void setOrden(Integer orden) { this.orden = orden; }

    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }

    public String getDireccion() { return direccion; }
    public void setDireccion(String direccion) { this.direccion = direccion; }

    public String getCiudad() { return ciudad; }
    public void setCiudad(String ciudad) { this.ciudad = ciudad; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }

    public LocalDate getFecha() { return fecha; }
    public void setFecha(LocalDate fecha) { this.fecha = fecha; }
}