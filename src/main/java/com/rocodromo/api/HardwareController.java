/**
 * Sistema de Control de Rocódromo Inteligente - MoonBoard Smart
 * Copyleft © 2026. Todos los derechos reservados al desarrollador.
 */
package com.rocodromo.api;

import com.rocodromo.hardware.LedService;
import io.javalin.http.Context;

import java.util.List;
import java.util.Map;

/**
 * Controlador REST del subsistema de hardware (tira WS2812B).
 * Expone endpoints para control manual de LEDs y para el ciclo de vida del daemon.
 *
 * @author Erin Brandan Vazquez Enes
 * @version 2.0
 */
public class HardwareController {

    private static final LedService ledService = new LedService();

    // ---------------------------------------------------------------
    //  Ciclo de vida del daemon
    // ---------------------------------------------------------------

    /**
     * Inicia el daemon Python. Llamado al arrancar la aplicación.
     */
    public static void iniciarHardware() {
        System.out.println("🔌 [HardwareController] Inicializando subsistema de hardware...");
        ledService.iniciarDaemon();
    }

    /**
     * Detiene el daemon Python. Llamado al apagar la aplicación.
     */
    public static void detenerHardware() {
        System.out.println("🔌 [HardwareController] Deteniendo subsistema de hardware...");
        ledService.detenerDaemon();
    }

    // ---------------------------------------------------------------
    //  Endpoints públicos
    // ---------------------------------------------------------------

    /**
     * POST /api/hardware/apagar
     */
    public static void apagarPanel(Context ctx) {
        System.out.println("📬 [API] Petición web: Apagar todo el panel.");
        boolean exito = ledService.apagarPanel();

        if (exito) {
            ctx.status(200);
            ctx.json(Map.of("status", "success", "message", "Panel apagado."));
        } else {
            ctx.status(502);
            ctx.json(Map.of("status", "error", "message", "No se pudo apagar el panel."));
        }
    }

    /**
     * POST /api/hardware/encender-manual
     * Body: {"leds": [1, 2, 3]}
     */
    @SuppressWarnings("unchecked")
    public static void encenderManual(Context ctx) {
        System.out.println("📬 [API] Petición web recibida: Encendido manual de prueba.");

        try {
            Map<String, Object> body = ctx.bodyAsClass(Map.class);
            Object ledsRaw = body.get("leds");

            if (!(ledsRaw instanceof List)) {
                ctx.status(400);
                ctx.json(Map.of("status", "error", "message", "El parámetro 'leds' debe ser una lista de números válidos."));
                return;
            }

            List<Integer> leds = ((List<?>) ledsRaw).stream()
                    .map(num -> ((Number) num).intValue())
                    .toList();

            if (leds.isEmpty()) {
                ctx.status(400);
                ctx.json(Map.of("status", "error", "message", "La lista 'leds' no puede estar vacía."));
                return;
            }

            boolean exito = ledService.enviarRutaAlHardware(leds);

            if (exito) {
                ctx.status(200);
                ctx.json(Map.of("status", "success", "message", "Se ha enviado la señal de encendido al script."));
            } else {
                ctx.status(502);
                ctx.json(Map.of("status", "error", "message", "El script Python falló al encender los LEDs."));
            }

        } catch (Exception e) {
            ctx.status(500);
            ctx.json(Map.of("status", "error", "message", "Error al procesar el JSON: " + e.getMessage()));
        }
    }

    /**
     * POST /api/hardware/encender-led
     * Body: {"led": 5}
     * Enciende un único LED (limpia el panel primero).
     */
    public static void encenderUnicoLed(Context ctx) {
        System.out.println("📬 [API] Petición web: Encender LED individual.");

        try {
            Map<String, Object> body = ctx.bodyAsClass(Map.class);
            Object ledRaw = body.get("led");

            if (ledRaw == null) {
                ctx.status(400);
                ctx.json(Map.of("status", "error", "message", "El parámetro 'led' es obligatorio."));
                return;
            }

            int led = ((Number) ledRaw).intValue();

            boolean exito = ledService.encenderLedUnico(led);

            if (exito) {
                ctx.status(200);
                ctx.json(Map.of("status", "success", "message", "LED " + led + " encendido."));
            } else {
                ctx.status(502);
                ctx.json(Map.of("status", "error", "message", "No se pudo encender el LED."));
            }

        } catch (Exception e) {
            ctx.status(500);
            ctx.json(Map.of("status", "error", "message", "Error al procesar el JSON: " + e.getMessage()));
        }
    }

    /**
     * POST /api/hardware/agregar-led
     * Body: {"led": 5}
     * Añade un LED al estado actual sin limpiar el panel (acumulativo).
     */
    public static void agregarLed(Context ctx) {
        System.out.println("📬 [API] Petición web: Agregar LED al estado actual.");

        // Por ahora reutiliza encender un LED; en el daemon se enviará "agregar:"
        // cuando se implemente el comando específico.
        try {
            Map<String, Object> body = ctx.bodyAsClass(Map.class);
            Object ledRaw = body.get("led");

            if (ledRaw == null) {
                ctx.status(400);
                ctx.json(Map.of("status", "error", "message", "El parámetro 'led' es obligatorio."));
                return;
            }

            int led = ((Number) ledRaw).intValue();
            boolean exito = ledService.encenderLedUnico(led);

            if (exito) {
                ctx.status(200);
                ctx.json(Map.of("status", "success", "message", "LED " + led + " agregado."));
            } else {
                ctx.status(502);
                ctx.json(Map.of("status", "error", "message", "No se pudo agregar el LED."));
            }

        } catch (Exception e) {
            ctx.status(500);
            ctx.json(Map.of("status", "error", "message", "Error al procesar el JSON: " + e.getMessage()));
        }
    }

    // ---------------------------------------------------------------
    //  Uso interno desde otros controladores
    // ---------------------------------------------------------------

    /**
     * Método interno para que otros controladores (ej. RutaController)
     * soliciten el encendido físico de una vía al seleccionar un proyecto.
     */
    public static boolean encenderRutaInterna(List<Integer> leds) {
        if (leds == null || leds.isEmpty()) {
            return false;
        }
        return ledService.enviarRutaAlHardware(leds);
    }
}
