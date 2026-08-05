/**
 * Sistema de Control de Rocódromo Inteligente - MoonBoard Smart
 * Copyleft © 2026. Todos los derechos reservados al desarrollador.
 */
package com.rocodromo.model;

/**
 * Modelo de datos de una marca registrada en el minijuego "Pulso Vertical".
 * Representa el tiempo que un jugador consigue aguantar en el panel.
 *
 * @author Erin Brandan Vazquez Enes
 * @version 1.0
 */
public class RankingPulsoVertical {
    private int id;
    private String nombreJugador;
    private double tiempoSegundos;
    private String fecha;

    public RankingPulsoVertical() {
    }

    public RankingPulsoVertical(int id, String nombreJugador, double tiempoSegundos, String fecha) {
        this.id = id;
        this.nombreJugador = nombreJugador;
        this.tiempoSegundos = tiempoSegundos;
        this.fecha = fecha;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getNombreJugador() { return nombreJugador; }
    public void setNombreJugador(String nombreJugador) { this.nombreJugador = nombreJugador; }

    public double getTiempoSegundos() { return tiempoSegundos; }
    public void setTiempoSegundos(double tiempoSegundos) { this.tiempoSegundos = tiempoSegundos; }

    public String getFecha() { return fecha; }
    public void setFecha(String fecha) { this.fecha = fecha; }
}
