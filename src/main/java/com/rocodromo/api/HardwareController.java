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
 * Controlador REST de control directo de hardware.
 * Expone los endpoints de encendido/apagado de la tira de LEDs de la Raspberry Pi.
 *
 * @author Erin Brandan Vázquez Enes
 * @version 1.1
 */
public class HardwareController {

    // Instancia única compartida a nivel de Backend
    private static final LedService ledService = new LedService();

    /**
     * Apaga de forma inmediata toda la matriz de LEDs. POST /api/hardware/apagar
     *
     * @param ctx Contexto de Javalin para gestionar la respuesta HTTP.
     */
    public static void apagarPanel(Context ctx) {
        System.out.println("📬 [API] Petición web recibida: Apagar panel.");

        boolean exito = ledService.apagarPanel();

        if (exito) {
            ctx.status(200);
            ctx.json(Map.of("status", "success", "message", "Panel apagado correctamente."));
        } else {
            ctx.status(502);
            ctx.json(Map.of("status", "error", "message", "El hardware no respondió o el script Python falló."));
        }
    }

    /**
     * Endpoint de diagnóstico para encender una lista de LEDs enviada desde la web.
     * POST /api/hardware/encender-manual
     *
     * @param ctx Contexto con body JSON de la forma {"leds": [1, 2, 3]}
     */
    @SuppressWarnings("unchecked")
    public static void encenderManual(Context ctx) {
        System.out.println("📬 [API] Petición web recibida: Encendido manual de prueba.");

        try {
            // Se parsea el body como Map genérico para inspeccionar el campo 'leds'
            Map<String, Object> body = ctx.bodyAsClass(Map.class);
            Object ledsRaw = body.get("leds");

            if (!(ledsRaw instanceof List)) {
                ctx.status(400);
                ctx.json(Map.of("status", "error", "message", "El parámetro 'leds' debe ser una lista de números válidos."));
                return;
            }

            // Conversión segura a List<Integer>: Jackson parsea números sueltos como Long/Double
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
     * Método interno para que otros controladores soliciten el encendido
     * físico de una vía al seleccionar un proyecto o ruta comunitaria.
     *
     * @param leds Lista de IDs de presas que componen la vía.
     */
    public static boolean encenderRutaInterna(List<Integer> leds) {
        if (leds == null || leds.isEmpty()) {
            return false;
        }
        return ledService.enviarRutaAlHardware(leds);
    }
}