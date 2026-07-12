/**
 * Sistema de Control de Rocódromo Inteligente - MoonBoard Smart
 * Copyleft © 2026. Todos los derechos reservados al desarrollador.
 */
package com.rocodromo.dao;

import com.rocodromo.db.DatabaseConfig;
import com.rocodromo.model.Presa;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO encargado de la persistencia y mapeo de las presas físicas del rocódromo.
 * Traduce las coordenadas cartesianas (X, Y) del panel visual al índice secuencial
 * del LED direccionable correspondiente.
 *
 * @author Erin Brandan Vazquez Enes
 * @version 1.0
 */
public class PresaDAO {

    /**
     * Registra una nueva presa en el mapa del panel.
     * Si el índice de LED ya está asignado a otra posición, la restricción UNIQUE de la base de datos
     * abortará la operación de forma segura para evitar duplicados en la tira física.
     *
     * @param presa Objeto con las coordenadas de la cuadrícula y el índice del LED.
     * @return true si la presa se registró correctamente; false en caso de error o conflicto de hardware.
     */
    public boolean registrarPresa(Presa presa) {
        String sql = "INSERT INTO PRESAS (posicion_x, posicion_y, indice_led) VALUES (?, ?, ?)";

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, presa.getPosicionX());
            pstmt.setInt(2, presa.getPosicionY());
            pstmt.setInt(3, presa.getIndiceLed());

            int filasAfectadas = pstmt.executeUpdate();
            return filasAfectadas > 0;

        } catch (SQLException e) {
            System.err.println("❌ [PresaDAO.registrarPresa] Error al insertar la presa física: " + e.getMessage());
            return false;
        }
    }

    /**
     * Recupera la totalidad de las presas configuradas en la matriz del rocódromo.
     * Es ideal para pintar el mapa interactivo en el Frontend de la interfaz de usuario.
     *
     * @return Lista de objetos Presa con sus mapeos de coordenadas y LEDs correspondientes.
     */
    public List<Presa> obtenerMapaCompleto() {
        List<Presa> mapa = new ArrayList<>();
        String sql = "SELECT id, posicion_x, posicion_y, indice_led FROM PRESAS ORDER BY id ASC";

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {

            while (rs.next()) {
                Presa presa = new Presa(
                        rs.getInt("id"),
                        rs.getInt("posicion_x"),
                        rs.getInt("posicion_y"),
                        rs.getInt("indice_led")
                );
                mapa.add(presa);
            }

        } catch (SQLException e) {
            System.err.println("❌ [PresaDAO.obtenerMapaCompleto] Error al extraer la matriz de presas: " + e.getMessage());
        }
        return mapa;
    }

    /**
     * Elimina la totalidad de las presas configuradas en la matriz.
     * Debido a la restricción 'ON DELETE CASCADE' del diseño de base de datos, esta operación
     * limpia automáticamente las relaciones en RUTA_PRESAS, evitando registros huérfanos.
     *
     * @return true si la base de datos se limpió con éxito; false en caso de fallo.
     */
    public boolean limpiarMapaDePresas() {
        String sql = "DELETE FROM PRESAS";

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.executeUpdate();
            return true;

        } catch (SQLException e) {
            System.err.println("❌ [PresaDAO.limpiarMapaDePresas] Error al vaciar la tabla de mapeo: " + e.getMessage());
            return false;
        }
    }
}