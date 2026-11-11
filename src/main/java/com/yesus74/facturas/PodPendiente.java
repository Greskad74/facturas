package com.yesus74.facturas;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
public class PodPendiente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Ruta relativa dentro de uploads/
    private String archivo;

    // Referencias que leyó la IA, separadas por "|"
    @Column(length = 1000)
    private String referencias;

    // Asunto del correo + nombre original del archivo
    @Column(length = 1000)
    private String contexto;

    private LocalDate fechaRecibido;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getArchivo() { return archivo; }
    public void setArchivo(String archivo) { this.archivo = archivo; }

    public String getReferencias() { return referencias; }
    public void setReferencias(String referencias) { this.referencias = referencias; }

    public String getContexto() { return contexto; }
    public void setContexto(String contexto) { this.contexto = contexto; }

    public LocalDate getFechaRecibido() { return fechaRecibido; }
    public void setFechaRecibido(LocalDate fechaRecibido) { this.fechaRecibido = fechaRecibido; }
}