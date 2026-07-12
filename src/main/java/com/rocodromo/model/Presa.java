package com.rocodromo.model;

public class Presa {
    private int id;
    private int posicionX;
    private int posicionY;
    private int indiceLed;

    public Presa() {}

    public Presa(int id, int posicionX, int posicionY, int indiceLed) {
        this.id = id;
        this.posicionX = posicionX;
        this.posicionY = posicionY;
        this.indiceLed = indiceLed;
    }

    // Getters y Setters
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getPosicionX() { return posicionX; }
    public void setPosicionX(int posicionX) { this.posicionX = posicionX; }

    public int getPosicionY() { return posicionY; }
    public void setPosicionY(int posicionY) { this.posicionY = posicionY; }

    public int getIndiceLed() { return indiceLed; }
    public void setIndiceLed(int indiceLed) { this.indiceLed = indiceLed; }
}