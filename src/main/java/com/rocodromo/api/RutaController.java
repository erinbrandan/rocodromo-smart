/**
 * Sistema de Control de Rocódromo Inteligente - MoonBoard Smart
 * Copyleft © 2026. Todos los derechos reservados al desarrollador.
 */
package com.rocodromo.api;

import com.rocodromo.dao.RutaDAO;
import com.rocodromo.model.Ruta;
import io.javalin.http.Context;
import java.util.List;
import java.util.Map;

/**
 * Controlador REST de rutas de escalada. Gestiona el catálogo de vías,
 * la asignación de estados y su proyección lumínica sobre el panel.
 *
 * @author Erin Brandan Vázquez Enes
 * @version 1.4
 */
public class RutaController {

    private static final RutaDAO rutaDAO = new RutaDAO();

    /**
     * Devuelve el catálogo de rutas en formato JSON. Si se proporcionan los parámetros
     * 'usuario' y 'estado', segmenta por el historial del escalador; en caso contrario,
     * devuelve el ranking global ordenado por popularidad.
     */
    public static void listarRutas(Context ctx) {
        String usuario = ctx.queryParam("usuario");
        String estado = ctx.queryParam("estado");

        List<Ruta> rutas;

        if (usuario != null && estado != null && !usuario.isBlank() && !estado.isBlank()) {
            System.out.println("📬 [API] Solicitando catálogo personal para: " + usuario + " [" + estado + "]");
            rutas = rutaDAO.obtenerRutasPorEstado(usuario, estado);
        } else {
            System.out.println("📬 [API] Solicitando catálogo global de rutas (Ranking de Popularidad)...");
            rutas = rutaDAO.obtenerTodasLasRutas();
        }

        ctx.status(200);
        ctx.json(rutas);
    }

    /**
     * Recibe la definición de una vía en JSON, la persiste en el sistema
     * y la vincula al historial del creador. POST /api/rutas/crear
     */
    public static void guardarRuta(Context ctx) {
        System.out.println("📬 [API] Recibiendo payload para registrar nueva vía en caliente...");

        try {
            CrearRutaDTO dto = ctx.bodyAsClass(CrearRutaDTO.class);

            // Se validan campos obligatorios y que la lista de LEDs no esté vacía
            if (dto.nombre == null || dto.nombre.isBlank() ||
                    dto.grado == null || dto.grado.isBlank() ||
                    dto.usuario_correo == null || dto.usuario_correo.isBlank() ||
                    dto.leds == null || dto.leds.isEmpty()) {

                ctx.status(400);
                ctx.json(Map.of("status", "error", "message", "Datos de creación insuficientes o mapa de LEDs vacío."));
                return;
            }

            Ruta nuevaRuta = new Ruta();
            nuevaRuta.setNombre(dto.nombre.trim());
            nuevaRuta.setGrado(dto.grado.trim());
            nuevaRuta.setEquipador(dto.equipador != null && !dto.equipador.isBlank() ? dto.equipador.trim() : "Anónimo");

            boolean exito = rutaDAO.guardarRuta(nuevaRuta, dto.leds, dto.usuario_correo.trim());

            if (exito) {
                ctx.status(201);
                ctx.json(Map.of("status", "success", "message", "¡Vía guardada e insertada en tu lista de proyectos!"));
            } else {
                ctx.status(500);
                ctx.json(Map.of("status", "error", "message", "Fallo interno al escribir la transacción en rocodromo.db."));
            }

        } catch (Exception e) {
            ctx.status(400);
            ctx.json(Map.of("status", "error", "message", "Formato de solicitud HTTP inválido o corrupto."));
        }
    }

    /**
     * Selecciona una ruta por ID, obtiene sus LEDs asociados, activa el hardware
     * y retorna la lista de LEDs para actualizar el simulador web.
     * POST /api/rutas/{id}/seleccionar
     */
    public static void seleccionarRuta(Context ctx) {
        try {
            int rutaId = Integer.parseInt(ctx.pathParam("id"));
            System.out.println("📬 [API] Escalador ha seleccionado la ruta con ID: " + rutaId);

            List<Integer> ledsAEncender = rutaDAO.obtenerLedsDeRuta(rutaId);

            if (ledsAEncender.isEmpty()) {
                ctx.status(404);
                ctx.json(Map.of("status", "error", "message", "La ruta no contiene presas o no existe."));
                return;
            }

            boolean exitoHardware = HardwareController.encenderRutaInterna(ledsAEncender);

            if (exitoHardware) {
                ctx.status(200);
                ctx.json(Map.of(
                        "status", "success",
                        "message", "Ruta cargada en el panel.",
                        "leds", ledsAEncender
                ));
            } else {
                ctx.status(502);
                ctx.json(Map.of(
                        "status", "error",
                        "message", "El hardware no pudo iluminar la ruta. Verifica leds.py y el bus GPIO.",
                        "leds", ledsAEncender
                ));
            }

        } catch (NumberFormatException e) {
            ctx.status(400);
            ctx.json(Map.of("status", "error", "message", "El ID de la ruta debe ser un formato numérico válido."));
        }
    }

    /**
     * Actualiza el progreso de una ruta (ej: de 'proyecto' a 'encadenada').
     * PUT /api/rutas/{id}/estado
     */
    public static void actualizarEstado(Context ctx) {
        try {
            int rutaId = Integer.parseInt(ctx.pathParam("id"));
            EstadoRutaDTO dto = ctx.bodyAsClass(EstadoRutaDTO.class);

            System.out.println("📬 [API] Actualizando estado de la ruta ID " + rutaId + " para el usuario " + dto.usuario + " a: [" + dto.estado + "]");

            if (dto.usuario == null || dto.estado == null || dto.usuario.isBlank() || dto.estado.isBlank()) {
                ctx.status(400);
                ctx.json(Map.of("status", "error", "message", "Cuerpo de solicitud incompleto. Falta usuario o estado."));
                return;
            }

            boolean exito = rutaDAO.actualizarEstadoRuta(rutaId, dto.usuario.trim(), dto.estado.trim());

            if (exito) {
                ctx.status(200);
                ctx.json(Map.of("status", "success", "message", "¡Estado de la vía actualizado correctamente!"));
            } else {
                ctx.status(404);
                ctx.json(Map.of("status", "error", "message", "No se encontró el registro de entrenamiento para el usuario especificado."));
            }

        } catch (NumberFormatException e) {
            ctx.status(400);
            ctx.json(Map.of("status", "error", "message", "El identificador de la ruta debe ser numérico."));
        } catch (Exception e) {
            ctx.status(400);
            ctx.json(Map.of("status", "error", "message", "Formato JSON erróneo."));
        }
    }

    /**
     * Elimina la vinculación de una ruta con el historial del usuario.
     * DELETE /api/rutas/{id}?usuario=correo@ejemplo.com
     */
    public static void eliminarRuta(Context ctx) {
        try {
            int rutaId = Integer.parseInt(ctx.pathParam("id"));
            String usuario = ctx.queryParam("usuario");

            System.out.println("📬 [API] Solicitud de borrado para la ruta ID: " + rutaId + " de la lista de: " + usuario);

            if (usuario == null || usuario.isBlank()) {
                ctx.status(400);
                ctx.json(Map.of("status", "error", "message", "Falta el parámetro del usuario solicitante."));
                return;
            }

            boolean borrado = rutaDAO.eliminarRutaDeUsuario(rutaId, usuario.trim());

            if (borrado) {
                ctx.status(200);
                ctx.json(Map.of("status", "success", "message", "Vía removida correctamente de tu catálogo."));
            } else {
                ctx.status(404);
                ctx.json(Map.of("status", "error", "message", "La vía no formaba parte de tu catálogo o no existe."));
            }

        } catch (NumberFormatException e) {
            ctx.status(400);
            ctx.json(Map.of("status", "error", "message", "El identificador de ruta proporcionado no es válido."));
        }
    }

    /**
     * Vincula una ruta comunitaria existente al historial de un usuario
     * con estado inicial de 'proyecto'. POST /api/rutas/{id}/agregar
     */
    public static void agregarRutaAHistorial(Context ctx) {
        try {
            int rutaId = Integer.parseInt(ctx.pathParam("id"));
            EstadoRutaDTO dto = ctx.bodyAsClass(EstadoRutaDTO.class);

            System.out.println("📬 [API] El usuario " + dto.usuario + " está añadiendo la vía ID " + rutaId + " a sus proyectos.");

            if (dto.usuario == null || dto.usuario.isBlank()) {
                ctx.status(400);
                ctx.json(Map.of("status", "error", "message", "Falta el identificador del usuario solicitante."));
                return;
            }

            boolean exito = rutaDAO.vincularRutaExistenteAUsuario(rutaId, dto.usuario.trim());

            if (exito) {
                ctx.status(201);
                ctx.json(Map.of("status", "success", "message", "¡Vía importada correctamente a tus proyectos!"));
            } else {
                ctx.status(409);
                ctx.json(Map.of("status", "error", "message", "Esta vía ya forma parte de tu catálogo o ha ocurrido un fallo interno."));
            }

        } catch (NumberFormatException e) {
            ctx.status(400);
            ctx.json(Map.of("status", "error", "message", "El identificador de la ruta no es válido."));
        } catch (Exception e) {
            ctx.status(400);
            ctx.json(Map.of("status", "error", "message", "Formato de payload JSON erróneo."));
        }
    }

    /** DTO para la creación de nuevas vías */
    private static class CrearRutaDTO {
        public String nombre;
        public String grado;
        public String equipador;
        public String usuario_correo; // Nombre del campo esperado por el cliente (snake_case)
        public List<Integer> leds;
    }

    /** DTO para la petición de cambio de estado */
    private static class EstadoRutaDTO {
        public String usuario;
        public String estado;
    }
}