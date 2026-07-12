package com.rocodromo.model;

public class ConfiguracionLed {
    private int id;
    private int totalLeds;
    private int pinGpio;
    private int brillo;

    // Constructor vacío (obligatorio para frameworks y mapeos)
    public ConfiguracionLed() {}

    // Constructor lleno
    public ConfiguracionLed(int id, int totalLeds, int pinGpio, int brillo) {
        this.id = id;
        this.totalLeds = totalLeds;
        this.pinGpio = pinGpio;
        this.brillo = brillo;
    }

    // Getters y Setters
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getTotalLeds() { return totalLeds; }
    public void setTotalLeds(int totalLeds) { this.totalLeds = totalLeds; }

    public int getPinGpio() { return pinGpio; }
    public void setPinGpio(int pinGpio) { this.pinGpio = pinGpio; }

    public int getBrillo() { return brillo; }
    public void setBrillo(int brillo) { this.brillo = brillo; }
}