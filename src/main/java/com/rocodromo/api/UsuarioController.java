/**
 * Sistema de Control de Rocódromo Inteligente - MoonBoard Smart
 * Copyleft © 2026. Todos los derechos reservados al desarrollador.
 */
package com.rocodromo.api;

import com.rocodromo.dao.UsuarioDAO;
import com.rocodromo.model.Usuario;
import io.javalin.http.Context;
import java.util.Map;

/**
 * Controlador REST de usuarios. Gestiona el registro de escaladores
 * y la validación de sesiones de acceso.
 *
 * @author Erin Brandan Vázquez Enes
 * @version 1.1
 */
public class UsuarioController {

    private static final UsuarioDAO usuarioDAO = new UsuarioDAO();

    /**
     * Endpoint de registro de un nuevo escalador. POST /api/usuarios/registro
     */
    public static void registrar(Context ctx) {
        try {
            Usuario nuevoUsuario = ctx.bodyAsClass(Usuario.class);

            // Se validan campos obligatorios (incluyendo strings vacíos o solo espacios)
            if (nuevoUsuario.getCorreo() == null || nuevoUsuario.getCorreo().isBlank() ||
                    nuevoUsuario.getContrasena() == null || nuevoUsuario.getContrasena().isBlank() ||
                    nuevoUsuario.getNombre() == null || nuevoUsuario.getNombre().isBlank()) {

                ctx.status(400).json(Map.of("status", "error", "message", "Faltan campos obligatorios o están vacíos."));
                return;
            }

            // Limpiamos espacios residuales por seguridad en los datos de entrada
            nuevoUsuario.setCorreo(nuevoUsuario.getCorreo().trim());
            nuevoUsuario.setNombre(nuevoUsuario.getNombre().trim());
            if (nuevoUsuario.getApellidos() != null) {
                nuevoUsuario.setApellidos(nuevoUsuario.getApellidos().trim());
            }

            boolean exito = usuarioDAO.registrarUsuario(nuevoUsuario);

            if (exito) {
                ctx.status(201).json(Map.of(
                        "status", "success",
                        "message", "Usuario creado correctamente.",
                        "nombre", nuevoUsuario.getNombre()
                ));
            } else {
                ctx.status(409).json(Map.of("status", "error", "message", "El correo ya está registrado en el sistema."));
            }
        } catch (Exception e) {
            ctx.status(400).json(Map.of("status", "error", "message", "Formato de payload JSON inválido."));
        }
    }

    /**
     * Endpoint de autenticación de escalador. POST /api/usuarios/login
     */
    public static void login(Context ctx) {
        try {
            // Se usa DTO interno en vez de Map.class para evitar casts ambiguos
            LoginDTO dto = ctx.bodyAsClass(LoginDTO.class);

            if (dto.correo == null || dto.correo.isBlank() || dto.password == null || dto.password.isBlank()) {
                ctx.status(400).json(Map.of("status", "error", "message", "Identificadores de login incompletos."));
                return;
            }

            Usuario usuarioValidado = usuarioDAO.validarLogin(dto.correo.trim(), dto.password);

            if (usuarioValidado != null) {
                ctx.status(200).json(Map.of(
                        "status", "success",
                        "message", "Autenticación correcta.",
                        "nombre", usuarioValidado.getNombre(),
                        "correo", usuarioValidado.getCorreo()
                ));
            } else {
                ctx.status(401).json(Map.of("status", "error", "message", "Correo o contraseña incorrectos."));
            }
        } catch (Exception e) {
            ctx.status(400).json(Map.of("status", "error", "message", "Formato de solicitud erróneo."));
        }
    }

    /** DTO interno para peticiones de autenticación */
    private static class LoginDTO {
        public String correo;
        public String password;
    }
}