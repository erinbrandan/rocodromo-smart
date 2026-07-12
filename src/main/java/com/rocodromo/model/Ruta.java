package com.rocodromo.model;

public class Ruta {
    private int id;
    private String nombre;
    private String grado;
    private String equipador;
    private String fecha;

    public Ruta() {}

    public Ruta(int id, String nombre, String grado, String equipador, String fecha) {
        this.id = id;
        this.nombre = nombre;
        this.grado = grado;
        this.equipador = equipador;
        this.fecha = fecha;
    }

    // Getters y Setters
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getGrado() { return grado; }
    public void setGrado(String grado) { this.grado = grado; }

    public String getEquipador() { return equipador; }
    public void setEquipador(String equipador) { this.equipador = equipador; }

    public String getFecha() { return fecha; }
    public void setFecha(String fecha) { this.fecha = fecha; }
}