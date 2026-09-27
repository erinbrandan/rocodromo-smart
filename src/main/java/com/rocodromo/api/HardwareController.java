/**
 * Sistema de Control de Rocódromo Inteligente - MoonBoard Smart
 * Copyleft © 2026. Todos los derechos reservados al desarrollador.
 */
package com.rocodromo.api;

import com.rocodromo.hardware.LedService;
import com.rocodromo.model.PresaRuta;
import io.javalin.http.Context;

import java.util.ArrayList;
import java.util.LinkedHashMap;
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
     * Body legacy: {"leds": [1, 2, 3], "color": "3498DB"} (opcional)
     * Body multicolor: {"grupos": [{"leds": [1,2], "color": "3498DB"},
     *                             {"leds": [3],   "color": "00FF00"}]}
     *
     * En el modo multicolor el primer grupo limpia el panel y los siguientes
     * se superponen, permitiendo reproducir una vía con inicio verde y top rojo.
     */
    @SuppressWarnings("unchecked")
    public static void encenderManual(Context ctx) {
        System.out.println("📬 [API] Petición web recibida: Encendido manual de prueba.");

        try {
            Map<String, Object> body = ctx.bodyAsClass(Map.class);
            Map<String, List<Integer>> gruposPorColor = new LinkedHashMap<>();

            // Formato multicolor: cada grupo trae su propio color
            if (body.get("grupos") instanceof List<?> grupos) {
                for (Object grupo : grupos) {
                    if (!(grupo instanceof Map)) continue;
                    Map<?, ?> mapaGrupo = (Map<?, ?>) grupo;
                    List<Integer> leds = extraerLeds(mapaGrupo.get("leds"));
                    if (leds.isEmpty()) continue;
                    gruposPorColor
                            .computeIfAbsent(extraerColor(mapaGrupo.get("color")), k -> new ArrayList<>())
                            .addAll(leds);
                }
            }

            // Formato clásico: una única lista de LEDs con color opcional
            if (gruposPorColor.isEmpty()) {
                List<Integer> leds = extraerLeds(body.get("leds"));
                if (!leds.isEmpty()) {
                    gruposPorColor.put(extraerColor(body.get("color")), leds);
                }
            }

            if (gruposPorColor.isEmpty()) {
                ctx.status(400);
                ctx.json(Map.of("status", "error", "message", "Indica al menos un LED en 'leds' o en 'grupos'."));
                return;
            }

            boolean exito = aplicarGruposPorColor(gruposPorColor);

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

        try {
            Map<String, Object> body = ctx.bodyAsClass(Map.class);
            Object ledRaw = body.get("led");

            if (ledRaw == null) {
                ctx.status(400);
                ctx.json(Map.of("status", "error", "message", "El parámetro 'led' es obligatorio."));
                return;
            }

            int led = ((Number) ledRaw).intValue();
            // Acumulativo de verdad: superpone en vez de borrar el resto del panel
            boolean exito = ledService.agregarLedsAlHardware(List.of(led), LedService.COLOR_NARANJA);

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
     * Reproduce el diseño original agrupando las presas por su rol.
     */
    public static boolean encenderPresasInterna(List<PresaRuta> presas) {
        if (presas == null || presas.isEmpty()) {
            return false;
        }
        Map<String, List<Integer>> gruposPorColor = new LinkedHashMap<>();
        for (PresaRuta presa : presas) {
            gruposPorColor
                    .computeIfAbsent(presa.getColorHex(), k -> new ArrayList<>())
                    .add(presa.getIndiceLed());
        }
        return aplicarGruposPorColor(gruposPorColor);
    }

    /**
     * Vuelca al panel los grupos de LEDs recibidos. El primer grupo se enciende
     * limpiando el panel (comando 'encender') y los siguientes se superponen
     * (comando 'agregar'), de ahí la dependencia del orden de inserción.
     */
    private static boolean aplicarGruposPorColor(Map<String, List<Integer>> gruposPorColor) {
        boolean primerGrupo = true;
        boolean todoCorrecto = true;

        for (Map.Entry<String, List<Integer>> grupo : gruposPorColor.entrySet()) {
            List<Integer> leds = grupo.getValue();
            String color = grupo.getKey();

            boolean exito = primerGrupo
                    ? ledService.enviarRutaAlHardware(leds, color)
                    : ledService.agregarLedsAlHardware(leds, color);

            System.out.println("🔦 [Hardware] Grupo " + (primerGrupo ? "base" : "superpuesto")
                    + ": " + leds.size() + " LED(s) en #" + color + " -> " + (exito ? "OK" : "FALLO"));

            todoCorrecto &= exito;
            primerGrupo = false;
        }

        return todoCorrecto;
    }

    /**
     * Normaliza un valor JSON a lista de índices de LED, descartando cualquier
     * entrada que no sea numérica.
     */
    private static List<Integer> extraerLeds(Object ledsRaw) {
        if (!(ledsRaw instanceof List<?> lista)) {
            return List.of();
        }
        List<Integer> leds = new ArrayList<>();
        for (Object valor : lista) {
            if (valor instanceof Number numero) {
                leds.add(numero.intValue());
            }
        }
        return leds;
    }

    /**
     * Valida un color HEX RRGGBB. Si falta o es inválido se cae al verde del panel.
     */
    private static String extraerColor(Object colorRaw) {
        if (colorRaw instanceof String color && color.matches("[0-9a-fA-F]{6}")) {
            return color;
        }
        return LedService.COLOR_VERDE;
    }
}
