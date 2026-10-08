package com.yesus74.facturas;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
public class Broker {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nombre;      // "C.H. Robinson", "Werner", etc.
    private String contacto;    // Nombre de la persona de contacto
    private String email;
    private String telefono;
    private String notas;

    // Constructor vacío (obligatorio para JPA)
    public Broker() {
    }

    // Constructor útil para crear datos rápido
    public Broker(String nombre, String contacto, String email, String telefono) {
        this.nombre = nombre;
        this.contacto = contacto;
        this.email = email;
        this.telefono = telefono;
    }

    // Getters y Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getContacto() { return contacto; }
    public void setContacto(String contacto) { this.contacto = contacto; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getTelefono() { return telefono; }
    public void setTelefono(String telefono) { this.telefono = telefono; }

    public String getNotas() { return notas; }
    public void setNotas(String notas) { this.notas = notas; }
}