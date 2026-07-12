package com.rocodromo.model;

public class Usuario {
    private String correo;
    private String nombre;
    private String apellidos;
    private String contrasena;

    public Usuario() {}

    public Usuario(String correo, String nombre, String apellidos, String contrasena) {
        this.correo = correo;
        this.nombre = nombre;
        this.apellidos = apellidos;
        this.contrasena = contrasena;
    }

    // Getters y Setters
    public String getCorreo() { return correo; }
    public void setCorreo(String correo) { this.correo = correo; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getApellidos() { return apellidos; }
    public void setApellidos(String apellidos) { this.apellidos = apellidos; }

    public String getContrasena() { return contrasena; }
    public void setContrasena(String contrasena) { this.contrasena = contrasena; }
}