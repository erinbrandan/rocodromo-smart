/**
 * Sistema de Control de Rocódromo Inteligente - MoonBoard Smart
 * Copyleft © 2026. Todos los derechos reservados al desarrollador.
 */
package com.rocodromo.dao;

import com.rocodromo.db.DatabaseConfig;
import com.rocodromo.model.PresaRuta;
import com.rocodromo.model.Ruta;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO encargado de gestionar el ciclo de vida de las rutas de escalada
 * en la base de datos. Administra las transacciones entre las tablas
 * RUTAS, RUTA_PRESAS, PRESAS e HISTORIAL_ENTRENAMIENTO.
 *
 * @author Erin Brandan Vazquez Enes
 * @version 1.5
 */
public class RutaDAO {

    /**
     * Inserta una nueva ruta de escalada, asocia sus presas en la tabla intermedia
     * (guardando el rol de cada una) y la vincula al historial del escalador en
     * estado 'proyecto'. Todo bajo una transacción atómica (ACID).
     */
    public boolean guardarRuta(Ruta ruta, List<PresaRuta> presas, String correoUsuario) {
        String sqlRuta = "INSERT INTO RUTAS (nombre, grado, equipador) VALUES (?, ?, ?)";
        String sqlRelacion = "INSERT INTO RUTA_PRESAS (ruta_id, presa_id, tipo) VALUES (?, ?, ?)";
        String sqlHistorial = "INSERT INTO HISTORIAL_ENTRENAMIENTO (usuario_id, ruta_id, estado, fecha) VALUES (?, ?, 'proyecto', CURRENT_TIMESTAMP)";

        Connection conn = null;

        try {
            conn = DatabaseConfig.getConnection();
            conn.setAutoCommit(false);

            int rutaId = 0;
            try (PreparedStatement pstmtRuta = conn.prepareStatement(sqlRuta, Statement.RETURN_GENERATED_KEYS)) {
                pstmtRuta.setString(1, ruta.getNombre());
                pstmtRuta.setString(2, ruta.getGrado());
                pstmtRuta.setString(3, ruta.getEquipador());
                pstmtRuta.executeUpdate();

                try (ResultSet generatedKeys = pstmtRuta.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        rutaId = generatedKeys.getInt(1);
                    } else {
                        throw new SQLException("❌ Fallo al obtener el ID generado para la ruta.");
                    }
                }
            }

            try (PreparedStatement pstmtRelacion = conn.prepareStatement(sqlRelacion)) {
                for (PresaRuta presa : presas) {
                    pstmtRelacion.setInt(1, rutaId);
                    pstmtRelacion.setInt(2, presa.getIndiceLed());
                    pstmtRelacion.setString(3, presa.getTipo());
                    pstmtRelacion.addBatch();
                }
                pstmtRelacion.executeBatch();
            }

            try (PreparedStatement pstmtHistorial = conn.prepareStatement(sqlHistorial)) {
                pstmtHistorial.setString(1, correoUsuario);
                pstmtHistorial.setInt(2, rutaId);
                pstmtHistorial.executeUpdate();
            }

            conn.commit();
            return true;

        } catch (SQLException e) {
            System.err.println("❌ [RutaDAO.guardarRuta] Error en la transacción. Aplicando Rollback: " + e.getMessage());
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException ex) {
                    System.err.println("❌ Error crítico al ejecutar el rollback: " + ex.getMessage());
                }
            }
            return false;
        } finally {
            if (conn != null) {
                try {
                    conn.close();
                } catch (SQLException e) {
                    System.err.println("❌ Error al cerrar la conexión: " + e.getMessage());
                }
            }
        }
    }

    /**
     * Obtiene las rutas de la base de datos filtrando por correo del escalador y estado.
     */
    public List<Ruta> obtenerRutasPorEstado(String correoUsuario, String estado) {
        List<Ruta> listaRutas = new ArrayList<>();
        String sql = "SELECT r.id, r.nombre, r.grado, r.equipador, r.fecha FROM RUTAS r " +
                "INNER JOIN HISTORIAL_ENTRENAMIENTO h ON r.id = h.ruta_id " +
                "WHERE h.usuario_id = ? AND h.estado = ? " +
                "ORDER BY r.fecha DESC";

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, correoUsuario);
            pstmt.setString(2, estado);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    Ruta ruta = new Ruta();
                    ruta.setId(rs.getInt("id"));
                    ruta.setNombre(rs.getString("nombre"));
                    ruta.setGrado(rs.getString("grado"));
                    ruta.setEquipador(rs.getString("equipador"));
                    ruta.setFecha(rs.getString("fecha"));
                    listaRutas.add(ruta);
                }
            }

        } catch (SQLException e) {
            System.err.println("❌ [RutaDAO.obtenerRutasPorEstado] Error al listar el catálogo segmentado: " + e.getMessage());
        }
        return listaRutas;
    }

    /**
     * Elimina una vía de escalada de todo el sistema, no solo de la lista del
     * usuario que la borra.
     * <p>
     * Se borra la fila de RUTAS y SQLite arrastra en cascada todo lo que
     * dependía de ella: sus RUTA_PRESAS (el diseño de iluminación) y TODOS sus
     * HISTORIAL_ENTRENAMIENTO. Gracias a esto la vía desaparece a la vez del
     * catálogo de la Comunidad y de la lista de cualquier otro escalador que la
     * tuviera adoptada. Las presas maestras de PRESAS no se tocan.
     * <p>
     * El EXISTS actúa como control de autorización: solo se borra si la vía
     * estaba realmente en la lista del usuario solicitante, de modo que
     * conocer un ID ajeno no basta para eliminarla.
     *
     * @return true si la vía existía y estaba en la lista del usuario.
     */
    public boolean eliminarRutaDeUsuario(int rutaId, String correoUsuario) {
        String sql = "DELETE FROM RUTAS WHERE id = ? "
                + "AND EXISTS (SELECT 1 FROM HISTORIAL_ENTRENAMIENTO "
                + "WHERE ruta_id = RUTAS.id AND usuario_id = ?)";

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, rutaId);
            pstmt.setString(2, correoUsuario);

            return pstmt.executeUpdate() > 0;

        } catch (SQLException e) {
            System.err.println("❌ [RutaDAO.eliminarRutaDeUsuario] Error al eliminar la vía: " + e.getMessage());
            return false;
        }
    }

    /**
     * Obtiene los índices físicos de los LEDs de una ruta específica.
     */
    public List<Integer> obtenerLedsDeRuta(int rutaId) {
        List<Integer> indicesLeds = new ArrayList<>();
        for (PresaRuta presa : obtenerPresasDeRuta(rutaId)) {
            indicesLeds.add(presa.getIndiceLed());
        }
        return indicesLeds;
    }

    /**
     * Obtiene las presas de una ruta con su rol asociado (inicio, intermedia o top),
     * que el frontend y el hardware necesitan para reproducir el diseño multicolor.
     */
    public List<PresaRuta> obtenerPresasDeRuta(int rutaId) {
        List<PresaRuta> presas = new ArrayList<>();
        String sql = "SELECT P.indice_led, RP.tipo FROM RUTA_PRESAS RP " +
                "INNER JOIN PRESAS P ON RP.presa_id = P.indice_led " +
                "WHERE RP.ruta_id = ? ORDER BY P.indice_led";

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, rutaId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    presas.add(new PresaRuta(rs.getInt("indice_led"), rs.getString("tipo")));
                }
            }

        } catch (SQLException e) {
            System.err.println("❌ [RutaDAO.obtenerPresasDeRuta] Error al extraer las presas de la ruta: " + e.getMessage());
        }
        return presas;
    }

    /**
     * Recupera todas las rutas del sistema, ordenadas por número de seguidores
     * en estado 'proyecto' de forma descendente.
     */
    public List<Ruta> obtenerTodasLasRutas() {
        List<Ruta> listaRutas = new ArrayList<>();
        String sql = "SELECT r.id, r.nombre, r.grado, r.equipador, r.fecha, " +
                "COUNT(CASE WHEN h.estado = 'proyecto' THEN 1 END) AS total_seguidores " +
                "FROM RUTAS r " +
                "LEFT JOIN HISTORIAL_ENTRENAMIENTO h ON r.id = h.ruta_id " +
                "GROUP BY r.id " +
                "ORDER BY total_seguidores DESC, r.fecha DESC";

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {

            while (rs.next()) {
                Ruta ruta = new Ruta();
                ruta.setId(rs.getInt("id"));
                ruta.setNombre(rs.getString("nombre"));
                ruta.setGrado(rs.getString("grado"));
                ruta.setEquipador(rs.getString("equipador"));
                ruta.setFecha(rs.getString("fecha"));
                listaRutas.add(ruta);
            }

        } catch (SQLException e) {
            System.err.println("❌ [RutaDAO.obtenerTodasLasRutas] Error al listar el catálogo comunitario: " + e.getMessage());
        }
        return listaRutas;
    }

    /**
     * Actualiza el estado de progreso de una vía (ej. de 'proyecto' a 'encadenada').
     */
    public boolean actualizarEstadoRuta(int rutaId, String correoUsuario, String nuevoEstado) {
        String sql = "UPDATE HISTORIAL_ENTRENAMIENTO SET estado = ? WHERE ruta_id = ? AND usuario_id = ?";

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, nuevoEstado);
            pstmt.setInt(2, rutaId);
            pstmt.setString(3, correoUsuario);

            return pstmt.executeUpdate() > 0;

        } catch (SQLException e) {
            System.err.println("❌ [RutaDAO.actualizarEstadoRuta] Error al actualizar estado de la vía: " + e.getMessage());
            return false;
        }
    }

    /**
     * Vincula una ruta comunitaria existente al historial de un usuario con
     * estado inicial de 'proyecto'. Ignora duplicados mediante INSERT OR IGNORE.
     */
    public boolean vincularRutaExistenteAUsuario(int rutaId, String correoUsuario) {
        String sql = "INSERT OR IGNORE INTO HISTORIAL_ENTRENAMIENTO (usuario_id, ruta_id, estado, fecha) VALUES (?, ?, 'proyecto', CURRENT_TIMESTAMP)";

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, correoUsuario);
            pstmt.setInt(2, rutaId);

            return pstmt.executeUpdate() > 0;

        } catch (SQLException e) {
            System.err.println("❌ [RutaDAO.vincularRutaExistenteAUsuario] Error al clonar vía comunitaria: " + e.getMessage());
            return false;
        }
    }
}
