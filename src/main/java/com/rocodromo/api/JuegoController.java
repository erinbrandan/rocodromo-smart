/**
 * Sistema de Control de Rocódromo Inteligente - MoonBoard Smart
 * Copyleft © 2026. Todos los derechos reservados al desarrollador.
 */
package com.rocodromo.api;

import com.rocodromo.dao.RankingPulsoVerticalDAO;
import com.rocodromo.model.RankingPulsoVertical;
import com.rocodromo.service.JuegoPulsoVerticalService;
import io.javalin.http.Context;

import java.util.List;
import java.util.Map;

/**
 * Controlador REST del minijuego "Pulso Vertical".
 * Expone endpoints para controlar la partida (iniciar, pausar, reanudar,
 * finalizar) y para gestionar el ranking de jugadores.
 *
 * @author Erin Brandan Vazquez Enes
 * @version 1.0
 */
public class JuegoController {

    private static final JuegoPulsoVerticalService juegoService = new JuegoPulsoVerticalService();
    private static final RankingPulsoVerticalDAO rankingDAO = new RankingPulsoVerticalDAO();

    private static final int TOP_RANKING = 10;

    /**
     * POST /api/juego/pulso-vertical/iniciar
     */
    public static void iniciarJuego(Context ctx) {
        System.out.println("📬 [API] Iniciando minijuego Pulso Vertical.");
        juegoService.iniciar();
        ctx.status(200).json(Map.of("status", "success", "message", "Cuenta atrás de Pulso Vertical iniciada."));
    }

    /**
     * POST /api/juego/pulso-vertical/pausar
     */
    public static void pausarJuego(Context ctx) {
        System.out.println("📬 [API] Pausando minijuego Pulso Vertical.");
        juegoService.pausar();
        ctx.status(200).json(Map.of("status", "success", "message", "Juego en pausa."));
    }

    /**
     * POST /api/juego/pulso-vertical/reanudar
     */
    public static void reanudarJuego(Context ctx) {
        System.out.println("📬 [API] Reanudando minijuego Pulso Vertical.");
        juegoService.reanudar();
        ctx.status(200).json(Map.of("status", "success", "message", "Juego reanudado."));
    }

    /**
     * POST /api/juego/pulso-vertical/finalizar
     */
    public static void finalizarJuego(Context ctx) {
        System.out.println("📬 [API] Finalizando minijuego Pulso Vertical.");
        juegoService.finalizar();
        ctx.status(200).json(Map.of("status", "success", "message", "Juego finalizado y panel apagado."));
    }

    /**
     * GET /api/juego/pulso-vertical/ranking
     */
    public static void obtenerRanking(Context ctx) {
        System.out.println("📬 [API] Solicitando TOP " + TOP_RANKING + " del ranking Pulso Vertical.");
        List<RankingPulsoVertical> ranking = rankingDAO.obtenerTopRanking(TOP_RANKING);
        ctx.status(200).json(ranking);
    }

    /**
     * POST /api/juego/pulso-vertical/ranking
     * Body: {"nombre_jugador": "Jugador", "tiempo_segundos": 102.15}
     */
    public static void guardarMarca(Context ctx) {
        System.out.println("📬 [API] Guardando nueva marca en el ranking Pulso Vertical.");

        try {
            MarcaPulsoVerticalDTO dto = ctx.bodyAsClass(MarcaPulsoVerticalDTO.class);

            if (dto.nombre_jugador == null || dto.nombre_jugador.isBlank() || dto.tiempo_segundos == null || dto.tiempo_segundos <= 0) {
                ctx.status(400).json(Map.of("status", "error", "message", "Nombre y tiempo son obligatorios para guardar la marca."));
                return;
            }

            boolean exito = rankingDAO.guardarMarca(dto.nombre_jugador.trim(), dto.tiempo_segundos);

            if (exito) {
                ctx.status(201).json(Map.of("status", "success", "message", "Marca guardada correctamente en el ranking."));
            } else {
                ctx.status(500).json(Map.of("status", "error", "message", "No se pudo persistir la marca en la base de datos."));
            }

        } catch (Exception e) {
            ctx.status(400).json(Map.of("status", "error", "message", "Formato de payload JSON inválido."));
        }
    }

    /** DTO interno para el registro de marcas del ranking */
    private static class MarcaPulsoVerticalDTO {
        public String nombre_jugador;
        public Double tiempo_segundos;
    }
}
