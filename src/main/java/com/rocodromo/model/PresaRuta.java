/**
 * Sistema de Control de Rocódromo Inteligente - MoonBoard Smart
 * Copyleft © 2026. Todos los derechos reservados al desarrollador.
 */
package com.rocodromo.model;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Modelo de una presa vinculada a una vía, junto con el papel que desempeña
 * dentro del diseño: los apoyos de 'inicio' (verdes), el cuerpo 'intermedia'
 * (azul) y el 'top' o remate final (rojo).
 *
 * Centraliza la equivalencia entre el rol semántico y el color RGB que
 * entiende el script Python, de modo que la API, el DAO y el frontend
 * compartan una única definición de la paleta.
 *
 * @author Erin Brandan Vázquez Enes
 * @version 1.0
 */
public class PresaRuta {

    public static final String TIPO_INTERMEDIA = "intermedia";
    public static final String TIPO_INICIO = "inicio";
    public static final String TIPO_TOP = "top";

    // Paleta física del panel (formato HEX RRGGBB)
    public static final String COLOR_INTERMEDIA = "0000FF";
    public static final String COLOR_INICIO = "00FF00";
    public static final String COLOR_TOP = "FF0000";

    private static final Map<String, String> COLORES_POR_TIPO = new LinkedHashMap<>();

    static {
        COLORES_POR_TIPO.put(TIPO_INTERMEDIA, COLOR_INTERMEDIA);
        COLORES_POR_TIPO.put(TIPO_INICIO, COLOR_INICIO);
        COLORES_POR_TIPO.put(TIPO_TOP, COLOR_TOP);
    }

    private int indiceLed;
    private String tipo;

    public PresaRuta() {
        this.tipo = TIPO_INTERMEDIA;
    }

    public PresaRuta(int indiceLed, String tipo) {
        this.indiceLed = indiceLed;
        this.tipo = normalizarTipo(tipo);
    }

    public int getIndiceLed() { return indiceLed; }
    public void setIndiceLed(int indiceLed) { this.indiceLed = indiceLed; }

    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = normalizarTipo(tipo); }

    /**
     * Traduce el rol semántico al color hexadecimal que consume leds.py.
     * Cualquier rol desconocido cae en el azul de las presas intermedias.
     */
    public String getColorHex() {
        return COLORES_POR_TIPO.getOrDefault(tipo, COLOR_INTERMEDIA);
    }

    /**
     * Normaliza el rol recibido. Tolera que llegue nulo o vacío y evita que
     * un valor inesperado termine escribiendo en la base de datos.
     */
    public static String normalizarTipo(String tipo) {
        if (tipo == null || tipo.isBlank()) {
            return TIPO_INTERMEDIA;
        }
        String limpio = tipo.trim().toLowerCase();
        return COLORES_POR_TIPO.containsKey(limpio) ? limpio : TIPO_INTERMEDIA;
    }
}
