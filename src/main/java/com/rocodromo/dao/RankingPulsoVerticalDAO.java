/**
 * Sistema de Control de Rocódromo Inteligente - MoonBoard Smart
 * Copyleft © 2026. Todos los derechos reservados al desarrollador.
 */
package com.rocodromo.dao;

import com.rocodromo.db.DatabaseConfig;
import com.rocodromo.model.RankingPulsoVertical;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO encargado de la persistencia de las marcas del minijuego "Pulso Vertical"
 * sobre la tabla RANKING_PULSO_VERTICAL en SQLite.
 *
 * @author Erin Brandan Vazquez Enes
 * @version 1.0
 */
public class RankingPulsoVerticalDAO {

    /**
     * Registra una nueva marca de jugador en el ranking.
     *
     * @param nombre          Nombre del jugador.
     * @param tiempoSegundos  Tiempo aguantado en segundos (con decimales).
     * @return true si la marca se guardó correctamente; false en caso de error.
     */
    public boolean guardarMarca(String nombre, double tiempoSegundos) {
        String sql = "INSERT INTO RANKING_PULSO_VERTICAL (nombre_jugador, tiempo_segundos) VALUES (?, ?)";

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, nombre);
            pstmt.setDouble(2, tiempoSegundos);

            return pstmt.executeUpdate() > 0;

        } catch (SQLException e) {
            System.err.println("❌ [RankingPulsoVerticalDAO.guardarMarca] Error al insertar la marca: " + e.getMessage());
            return false;
        }
    }

    /**
     * Recupera las mejores marcas del ranking, ordenadas por tiempo de forma
     * descendente (mejor jugador primero). El límite lo indica el parámetro.
     *
     * @param limite Número máximo de marcas a devolver.
     * @return Lista de marcas ordenadas de mejor a peor.
     */
    public List<RankingPulsoVertical> obtenerTopRanking(int limite) {
        List<RankingPulsoVertical> ranking = new ArrayList<>();
        String sql = "SELECT id, nombre_jugador, tiempo_segundos, fecha " +
                "FROM RANKING_PULSO_VERTICAL " +
                "ORDER BY tiempo_segundos DESC " +
                "LIMIT ?";

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, limite);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    RankingPulsoVertical marca = new RankingPulsoVertical(
                            rs.getInt("id"),
                            rs.getString("nombre_jugador"),
                            rs.getDouble("tiempo_segundos"),
                            rs.getString("fecha")
                    );
                    ranking.add(marca);
                }
            }

        } catch (SQLException e) {
            System.err.println("❌ [RankingPulsoVerticalDAO.obtenerTopRanking] Error al consultar el ranking: " + e.getMessage());
        }
        return ranking;
    }
}
