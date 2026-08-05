/**
 * Sistema de Control de Rocódromo Inteligente - MoonBoard Smart
 * Copyleft © 2026. Todos los derechos reservados al desarrollador.
 */
package com.rocodromo.service;

import com.rocodromo.hardware.LedService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Servicio del minijuego "Pulso Vertical".
 * <p>
 * Gestiona en un hilo en segundo plano la iluminación del panel durante el
 * juego: una cuenta atrás de 6 segundos con franjas en rojo y, a partir del
 * segundo 6, la fase de juego con LEDs en verde. Durante la fase de juego las
 * presas se apagan progresivamente en cada ciclo eliminando un 35% de los LEDs
 * activos (mínimo 1, sin bajar nunca de 6) y respetando la distancia
 * biomecánica (mínimo 30 cm, máximo 130 cm) y el equilibrio entre zonas (al
 * menos 2 apoyos por zona: alta, media y baja). Cada LED que se va a apagar
 * permanece 1 segundo en color naranja antes de desaparecer. Al quedar solo 6
 * LEDs verdes se entra en un bucle infinito en el que se apaga un LED (previo
 * paso por naranja durante 1 segundo) y se enciende otro.
 *
 * @author Erin Brandan Vazquez Enes
 * @version 1.0
 */
public class JuegoPulsoVerticalService {

    // ------------------------------------------------------------------
    //  Constantes del panel (grid 18×11 = 198 LEDs, fila 1 en la parte alta)
    // ------------------------------------------------------------------
    private static final int TOTAL_LEDS = 198;
    private static final int COLS = 11;

    // Distancia real aproximada entre el centro de dos presas adyacentes (cm)
    private static final double CELL_CM = 24.0;
    private static final double DIST_MIN_CM = 30.0;
    private static final double DIST_MAX_CM = 130.0;

    // Cada cuántos segundos se apaga/aparea un LED durante el juego
    private static final long TICK_SEGUNDOS = 3;

    // Tiempo que un LED permanece en naranja antes de apagarse
    private static final long DURACION_NARANJA_SEGUNDOS = 1;

    // Zonas del panel (LEDs 1-based; filas 1-6 alta, 7-12 media, 13-18 baja)
    private static final int LIMITE_ALTA = 66;
    private static final int LIMITE_MEDIA = 132;

    // Franjas de la cuenta atrás (Filas 12-17 -> 6-17 -> 0-17, en orden superior)
    private static final List<Integer> FRANJA_1 = rangoLeds(1, LIMITE_ALTA);
    private static final List<Integer> FRANJA_12 = rangoLeds(1, LIMITE_MEDIA);
    private static final List<Integer> FRANJA_123 = rangoLeds(1, TOTAL_LEDS);

    // ------------------------------------------------------------------
    //  Estado del juego
    // ------------------------------------------------------------------
    private final LedService ledService = new LedService();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private final Object lock = new Object();
    private final Set<Integer> ledsVerdes = new TreeSet<>();
    private final Set<Integer> ledsNaranja = new TreeSet<>();
    private Integer ledAEncender = null; // Sustituto del bucle infinito (se enciende al completar el apagado)

    private final List<ScheduledFuture<?>> tareasPendientes = new ArrayList<>();
    private ScheduledFuture<?> tareaJuego;

    private volatile boolean juegoActivo = false;
    private volatile boolean pausado = false;

    /**
     * Inicia el juego: ejecuta la cuenta atrás de 6 segundos y arranca la
     * lógica de apagado progresivo. Si ya hay un juego en marcha, lo reinicia.
     */
    public void iniciar() {
        synchronized (lock) {
            cancelarTodas();
            juegoActivo = true;
            pausado = false;
            ledsVerdes.clear();
            ledsNaranja.clear();
            ledAEncender = null;

            System.out.println("🎮 [PulsoVertical] Cuenta atrás de 6 segundos iniciada.");

            tareasPendientes.add(scheduler.schedule(
                    () -> encenderFranja(FRANJA_1, LedService.COLOR_ROJO), 0, TimeUnit.SECONDS));
            tareasPendientes.add(scheduler.schedule(
                    () -> encenderFranja(FRANJA_12, LedService.COLOR_ROJO), 2, TimeUnit.SECONDS));
            tareasPendientes.add(scheduler.schedule(
                    () -> encenderFranja(FRANJA_123, LedService.COLOR_ROJO), 4, TimeUnit.SECONDS));
            tareasPendientes.add(scheduler.schedule(this::iniciarFaseVerde, 6, TimeUnit.SECONDS));
        }
    }

    /**
     * Pausa el avance del juego (mantiene las presas tal y como están).
     */
    public void pausar() {
        synchronized (lock) {
            if (juegoActivo && !pausado) {
                pausado = true;
                System.out.println("⏸️  [PulsoVertical] Juego en pausa.");
            }
        }
    }

    /**
     * Reanuda el avance del juego desde donde se pausó.
     */
    public void reanudar() {
        synchronized (lock) {
            if (juegoActivo && pausado) {
                pausado = false;
                System.out.println("▶️  [PulsoVertical] Juego reanudado.");
            }
        }
    }

    /**
     * Finaliza el juego: cancela todos los timers y apaga el panel completo.
     */
    public void finalizar() {
        synchronized (lock) {
            cancelarTodas();
            juegoActivo = false;
            pausado = false;
            ledsVerdes.clear();
            ledsNaranja.clear();
            ledAEncender = null;
            System.out.println("🏁 [PulsoVertical] Juego finalizado. Panel apagado.");
        }
        ledService.apagarPanel();
    }

    // ------------------------------------------------------------------
    //  Lógica interna
    // ------------------------------------------------------------------

    private void cancelarTodas() {
        for (ScheduledFuture<?> tarea : tareasPendientes) {
            tarea.cancel(false);
        }
        tareasPendientes.clear();
        if (tareaJuego != null) {
            tareaJuego.cancel(false);
            tareaJuego = null;
        }
    }

    private void encenderFranja(List<Integer> leds, String colorHex) {
        synchronized (lock) {
            if (!juegoActivo) return;
        }
        ledService.enviarRutaAlHardware(leds, colorHex);
    }

    private void iniciarFaseVerde() {
        synchronized (lock) {
            if (!juegoActivo) return;

            System.out.println("🟢 [PulsoVertical] ¡Fase verde! Comienza el juego de resistencia.");
            ledsNaranja.clear();
            ledAEncender = null;
            for (int i = 1; i <= TOTAL_LEDS; i++) {
                ledsVerdes.add(i);
            }
            redibujar();

            tareaJuego = scheduler.scheduleWithFixedDelay(this::tickJuego, TICK_SEGUNDOS, TICK_SEGUNDOS, TimeUnit.SECONDS);
        }
    }

    private void tickJuego() {
        synchronized (lock) {
            if (!juegoActivo || pausado) return;

            // Si un apagado quedó pendiente (pausa durante el naranja), se completa ahora
            if (!ledsNaranja.isEmpty()) {
                completarApagado();
                return;
            }

            AccionApagado accion = seleccionarAccion();
            if (accion == null) return;

            ledsNaranja.addAll(accion.aApagar);
            ledAEncender = accion.aEncender;
            redibujar(); // Pinta en naranja los LEDs que se van a apagar

            tareasPendientes.add(scheduler.schedule(this::completarApagado, DURACION_NARANJA_SEGUNDOS, TimeUnit.SECONDS));
        }
    }

    /**
     * Completa el apagado de los LEDs naranja: se añade el sustituto (bucle
     * infinito) si lo hubiera, se retiran del estado activo y se redibuja.
     */
    private void completarApagado() {
        synchronized (lock) {
            if (!juegoActivo || pausado) return;

            if (ledAEncender != null) {
                ledsVerdes.add(ledAEncender);
                ledAEncender = null;
            }
            ledsVerdes.removeAll(ledsNaranja);
            ledsNaranja.clear();
            redibujar();
        }
    }

    /**
     * Selecciona la acción de este ciclo sin mutar el estado todavía: o bien
     * los LEDs que pasarán a naranja para apagarse (reducción del 35%), o bien
     * el intercambio del bucle infinito (apagar 1 y encender 1 nuevo).
     */
    private AccionApagado seleccionarAccion() {
        int totalActivos = ledsVerdes.size();

        if (totalActivos > 6) {
            return seleccionarApagadoProgresivo(totalActivos);
        } else if (totalActivos == 6) {
            return seleccionarBucleInfinito();
        }
        return null;
    }

    private AccionApagado seleccionarApagadoProgresivo(int totalActivos) {
        // Reducción porcentual del 35% sobre los LEDs activos en cada ciclo
        int ledsAQuitar = (int) Math.floor(totalActivos * 0.35);

        // Mínimo 1 LED por ciclo mientras haya más de 6 activos
        if (ledsAQuitar < 1) {
            ledsAQuitar = 1;
        }

        // Límite de seguridad: nunca dejar el panel con menos de 6 LEDs
        if (totalActivos - ledsAQuitar < 6) {
            ledsAQuitar = totalActivos - 6;
        }

        // Se seleccionan los LEDs respetando la biomecánica sin mutar el estado
        List<Integer> aApagar = new ArrayList<>();
        Set<Integer> estadoSimulado = new TreeSet<>(ledsVerdes);

        while (aApagar.size() < ledsAQuitar && estadoSimulado.size() > 6) {
            Integer led = seleccionarUnLedParaApagar(estadoSimulado);
            if (led == null) break;
            estadoSimulado.remove(led);
            aApagar.add(led);
        }

        return aApagar.isEmpty() ? null : new AccionApagado(aApagar, null);
    }

    private AccionApagado seleccionarBucleInfinito() {
        List<Integer> candidatos = new ArrayList<>(ledsVerdes);
        Collections.shuffle(candidatos);

        int aApagar = candidatos.get(0);
        int zona = zonaDe(aApagar);
        Integer aEncender = ledApagadoValidoEnZona(zona);
        if (aEncender == null) return null;

        return new AccionApagado(List.of(aApagar), aEncender);
    }

    private Integer seleccionarUnLedParaApagar(Set<Integer> estado) {
        List<Integer> candidatos = new ArrayList<>(estado);
        Collections.shuffle(candidatos);

        for (int led : candidatos) {
            if (puedeApagarse(led, estado)) {
                return led;
            }
        }

        // Salvaguarda: si ningún candidato respeta la distancia, se elige uno
        // que mantenga al menos 2 apoyos en su zona para no bloquear el juego.
        for (int led : candidatos) {
            if (contarPorZona(zonaDe(led), estado) > 2) {
                return led;
            }
        }
        return null;
    }

    /**
     * Comprueba que un LED puede apagarse dentro del estado indicado: su zona
     * conserva al menos 2 apoyos y los vecinos que queden a ambos lados siguen
     * estando al alcance (≤ 130 cm).
     */
    private boolean puedeApagarse(int led, Set<Integer> estado) {
        if (contarPorZona(zonaDe(led), estado) <= 2) return false;

        TreeSet<Integer> ordenados = (TreeSet<Integer>) estado;
        Integer anterior = ordenados.lower(led);
        Integer posterior = ordenados.higher(led);

        if (anterior != null && posterior != null) {
            return distanciaCm(anterior, posterior) <= DIST_MAX_CM;
        }
        return true;
    }

    private Integer ledApagadoValidoEnZona(int zona) {
        int inicio;
        int fin;
        switch (zona) {
            case 1:
                inicio = 1;
                fin = LIMITE_ALTA;
                break;
            case 2:
                inicio = LIMITE_ALTA + 1;
                fin = LIMITE_MEDIA;
                break;
            default:
                inicio = LIMITE_MEDIA + 1;
                fin = TOTAL_LEDS;
                break;
        }

        List<Integer> candidatos = new ArrayList<>();
        for (int led = inicio; led <= fin; led++) {
            if (!ledsVerdes.contains(led)) {
                candidatos.add(led);
            }
        }
        Collections.shuffle(candidatos);

        for (int led : candidatos) {
            if (distanciaMinimaAlVecino(led) >= DIST_MIN_CM) {
                return led;
            }
        }
        // Salvaguarda: si no respeta la distancia mínima, se añade cualquiera
        return candidatos.isEmpty() ? null : candidatos.get(0);
    }

    private double distanciaMinimaAlVecino(int led) {
        double minima = Double.MAX_VALUE;
        for (int otro : ledsVerdes) {
            minima = Math.min(minima, distanciaCm(led, otro));
        }
        return minima == Double.MAX_VALUE ? DIST_MAX_CM : minima;
    }

    private void redibujar() {
        if (ledsVerdes.isEmpty()) return;
        ledService.enviarRutaAlHardware(new ArrayList<>(ledsVerdes), LedService.COLOR_VERDE);

        // Los LEDs en fase de apagado se superponen en naranja (1 segundo)
        if (!ledsNaranja.isEmpty()) {
            ledService.agregarLedsAlHardware(new ArrayList<>(ledsNaranja), LedService.COLOR_NARANJA);
        }
    }

    /** Acción pendiente de un ciclo: LEDs que pasarán a naranja y su sustituto (si existe). */
    private static class AccionApagado {
        final List<Integer> aApagar;
        final Integer aEncender;

        AccionApagado(List<Integer> aApagar, Integer aEncender) {
            this.aApagar = aApagar;
            this.aEncender = aEncender;
        }
    }

    // ------------------------------------------------------------------
    //  Utilidades del panel
    // ------------------------------------------------------------------

    private int zonaDe(int led) {
        if (led <= LIMITE_ALTA) return 1;      // Alta (filas 1-6)
        if (led <= LIMITE_MEDIA) return 2;     // Media (filas 7-12)
        return 3;                              // Baja (filas 13-18)
    }

    private long contarPorZona(int zona, Set<Integer> estado) {
        return estado.stream().filter(led -> zonaDe(led) == zona).count();
    }

    private double distanciaCm(int ledA, int ledB) {
        int filaA = (ledA - 1) / COLS;
        int colA = (ledA - 1) % COLS;
        int filaB = (ledB - 1) / COLS;
        int colB = (ledB - 1) % COLS;

        double dx = (colA - colB) * CELL_CM;
        double dy = (filaA - filaB) * CELL_CM;
        return Math.sqrt(dx * dx + dy * dy);
    }

    private static List<Integer> rangoLeds(int desde, int hasta) {
        List<Integer> leds = new ArrayList<>();
        for (int i = desde; i <= hasta; i++) {
            leds.add(i);
        }
        return leds;
    }
}
