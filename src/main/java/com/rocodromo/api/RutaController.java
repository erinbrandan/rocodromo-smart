/**
 * Sistema de Control de Rocódromo Inteligente - MoonBoard Smart
 * Copyleft © 2026. Todos los derechos reservados al desarrollador.
 */
package com.rocodromo.api;

import com.rocodromo.dao.RutaDAO;
import com.rocodromo.model.PresaRuta;
import com.rocodromo.model.Ruta;
import io.javalin.http.Context;
import java.util.ArrayList;
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
     *
     * Acepta el payload moderno con roles ('presas': [{led, tipo}]) y, por
     * compatibilidad con clientes antiguos, el clásico 'leds': [1, 2, 3] en
     * el que todas las presas se registran como intermedias.
     */
    public static void guardarRuta(Context ctx) {
        System.out.println("📬 [API] Recibiendo payload para registrar nueva vía en caliente...");

        try {
            CrearRutaDTO dto = ctx.bodyAsClass(CrearRutaDTO.class);

            List<PresaRuta> presas = traducirPresas(dto);

            // Se validan campos obligatorios y que la lista de presas no esté vacía
            if (dto.nombre == null || dto.nombre.isBlank() ||
                    dto.grado == null || dto.grado.isBlank() ||
                    dto.usuario_correo == null || dto.usuario_correo.isBlank() ||
                    presas.isEmpty()) {

                ctx.status(400);
                ctx.json(Map.of("status", "error", "message", "Datos de creación insuficientes o mapa de presas vacío."));
                return;
            }

            Ruta nuevaRuta = new Ruta();
            nuevaRuta.setNombre(dto.nombre.trim());
            nuevaRuta.setGrado(dto.grado.trim());
            nuevaRuta.setEquipador(dto.equipador != null && !dto.equipador.isBlank() ? dto.equipador.trim() : "Anónimo");

            boolean exito = rutaDAO.guardarRuta(nuevaRuta, presas, dto.usuario_correo.trim());

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
     * Convierte el cuerpo de la petición en la lista definitiva de presas con rol,
     * priorizando el payload con roles y usando el plano como plan B.
     */
    private static List<PresaRuta> traducirPresas(CrearRutaDTO dto) {
        List<PresaRuta> presas = new ArrayList<>();

        if (dto.presas != null) {
            for (PresaDTO presa : dto.presas) {
                if (presa == null || presa.led == null) continue;
                presas.add(new PresaRuta(presa.led, presa.tipo));
            }
        }

        if (presas.isEmpty() && dto.leds != null) {
            for (Integer led : dto.leds) {
                if (led == null) continue;
                presas.add(new PresaRuta(led, PresaRuta.TIPO_INTERMEDIA));
            }
        }

        return presas;
    }

    /**
     * Selecciona una ruta por ID, obtiene sus presas asociadas con su rol,
     * activa el hardware multicolor y retorna la lista para el simulador web.
     * POST /api/rutas/{id}/seleccionar
     */
    public static void seleccionarRuta(Context ctx) {
        try {
            int rutaId = Integer.parseInt(ctx.pathParam("id"));
            System.out.println("📬 [API] Escalador ha seleccionado la ruta con ID: " + rutaId);

            List<PresaRuta> presas = rutaDAO.obtenerPresasDeRuta(rutaId);

            if (presas.isEmpty()) {
                ctx.status(404);
                ctx.json(Map.of("status", "error", "message", "La ruta no contiene presas o no existe."));
                return;
            }

            boolean exitoHardware = HardwareController.encenderPresasInterna(presas);

            List<Integer> indicesLeds = new ArrayList<>();
            for (PresaRuta presa : presas) {
                indicesLeds.add(presa.getIndiceLed());
            }

            if (exitoHardware) {
                ctx.status(200);
                ctx.json(Map.of(
                        "status", "success",
                        "message", "Ruta cargada en el panel.",
                        "leds", indicesLeds,
                        "presas", presas
                ));
            } else {
                ctx.status(502);
                ctx.json(Map.of(
                        "status", "error",
                        "message", "El hardware no pudo iluminar la ruta. Verifica leds.py y el bus GPIO.",
                        "leds", indicesLeds,
                        "presas", presas
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
     * Elimina una vía de escalada de todo el sistema: desaparece de la lista
     * del usuario y también del catálogo de la Comunidad.
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
                ctx.json(Map.of("status", "success", "message", "Vía eliminada de tu catálogo y del catálogo de la Comunidad."));
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
        public List<PresaDTO> presas;  // Payload moderno con el rol de cada presa
        public List<Integer> leds;     // Payload clásico sin roles (compatibilidad)
    }

    /** DTO de una presa dentro del payload de creación */
    private static class PresaDTO {
        public Integer led;
        public String tipo; // "intermedia", "inicio" o "top"
    }

    /** DTO para la petición de cambio de estado */
    private static class EstadoRutaDTO {
        public String usuario;
        public String estado;
    }
}