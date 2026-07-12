/**
 * Sistema de Control de Rocódromo Inteligente - MoonBoard Smart
 * Copyleft © 2026. Todos los derechos reservados al desarrollador.
 */
package com.rocodromo.dao;

import com.rocodromo.db.DatabaseConfig;
import com.rocodromo.model.Usuario;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.*;

/**
 * DAO encargado de gestionar el registro y la autenticación de usuarios
 * del sistema. Aplica hashing SHA-256 sobre las contraseñas antes de persistirlas.
 *
 * @author Erin Brandan Vazquez Enes
 * @version 1.1
 */
public class UsuarioDAO {

    /**
     * Registra un nuevo usuario en el sistema. Se aplica hash SHA-256
     * a la contraseña antes de persistirla en SQLite.
     *
     * @param usuario Objeto con la información del escalador.
     * @return true si el registro fue exitoso; false en caso de error o duplicado.
     */
    public boolean registrarUsuario(Usuario usuario) {
        String sql = "INSERT INTO USUARIOS (correo, nombre, apellidos, contrasena) VALUES (?, ?, ?, ?)";

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, usuario.getCorreo());
            pstmt.setString(2, usuario.getNombre());
            pstmt.setString(3, usuario.getApellidos());

            // Hash SHA-256 de la contraseña antes de almacenar
            String passwordHasheada = hashPassword(usuario.getContrasena());
            pstmt.setString(4, passwordHasheada);

            int filasAfectadas = pstmt.executeUpdate();
            return filasAfectadas > 0;

        } catch (SQLException e) {
            if (e.getMessage().contains("UNIQUE constraint failed")) {
                System.err.println("⚠️ [UsuarioDAO.registrarUsuario] Intento de registro duplicado para el correo: " + usuario.getCorreo());
            } else {
                System.err.println("❌ Error al registrar usuario en SQLite: " + e.getMessage());
            }
            return false;
        }
    }

    /**
     * Valida las credenciales de acceso contrastando el hash de la contraseña
     * proporcionada con el registrado en la base de datos.
     *
     * @param correo Correo electrónico del usuario (login ID).
     * @param password Contraseña en texto plano introducida por el usuario.
     * @return Objeto Usuario mapeado si las credenciales son válidas; null en caso contrario.
     */
    public Usuario validarLogin(String correo, String password) {
        String sql = "SELECT correo, nombre, apellidos FROM USUARIOS WHERE correo = ? AND contrasena = ?";

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, correo);

            // Se hashea la contraseña entrante para compararla con el registro en BD
            pstmt.setString(2, hashPassword(password));

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    Usuario usuario = new Usuario();
                    usuario.setCorreo(rs.getString("correo"));
                    usuario.setNombre(rs.getString("nombre"));
                    usuario.setApellidos(rs.getString("apellidos"));
                    return usuario;
                }
            }
        } catch (SQLException e) {
            System.err.println("❌ Error al validar el login en SQLite: " + e.getMessage());
        }
        return null;
    }

    /**
     * Transforma una cadena de texto en su representación hexadecimal SHA-256.
     */
    private String hashPassword(String password) {
        if (password == null) return "";
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(password.getBytes());
            StringBuilder hexString = new StringBuilder();

            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            System.err.println("❌ Error crítico: No se encontró el algoritmo SHA-256. Almacenando texto plano de contingencia.");
            return password;
        }
    }
}