/**
 * Sistema de Control de Rocódromo Inteligente - MoonBoard Smart
 * Copyleft © 2026. Todos los derechos reservados al desarrollador.
 */
package com.rocodromo.dao;

import com.rocodromo.db.DatabaseConfig;
import com.rocodromo.model.ConfiguracionLed;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * DAO encargado de gestionar la persistencia de los parámetros físicos
 * de la tira de LEDs WS2812B. Opera sobre la tabla CONFIGURACION_LED en SQLite.
 *
 * @author Erin Brandan Vazquez Enes
 * @version 1.0
 */
public class ConfiguracionLedDAO {

    /**
     * Recupera la configuración activa de hardware de la base de datos.
     * Como el sistema es monopanel, solo se espera un único registro.
     *
     * @return Objeto ConfiguracionLed con los datos mapeados, o null si no existe configuración.
     */
    public ConfiguracionLed obtenerConfiguracion() {
        String sql = "SELECT id, total_leds, pin_gpio, brillo FROM CONFIGURACION_LED LIMIT 1";

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {

            if (rs.next()) {
                ConfiguracionLed config = new ConfiguracionLed();
                config.setId(rs.getInt("id"));
                config.setTotalLeds(rs.getInt("total_leds"));
                config.setPinGpio(rs.getInt("pin_gpio"));
                config.setBrillo(rs.getInt("brillo"));
                return config;
            }

        } catch (SQLException e) {
            System.err.println("❌ [ConfiguracionLedDAO.obtenerConfiguracion] Error en la consulta SQL: " + e.getMessage());
        }
        return null;
    }

    /**
     * Guarda o actualiza los parámetros físicos de la tira de LEDs.
     * Se utiliza INSERT OR REPLACE con ID fijo (1) para mantener un único registro maestro.
     *
     * @param config Objeto con los nuevos parámetros físicos (total de LEDs, pin GPIO y brillo).
     * @return true si la operación fue exitosa; false en caso contrario.
     */
    public boolean guardarOActualizar(ConfiguracionLed config) {
        // ID fijo (1) para garantizar un único registro de configuración en el sistema
        String sql = "INSERT OR REPLACE INTO CONFIGURACION_LED (id, total_leds, pin_gpio, brillo) VALUES (1, ?, ?, ?)";

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, config.getTotalLeds());
            pstmt.setInt(2, config.getPinGpio());
            pstmt.setInt(3, config.getBrillo());

            int filasAfectadas = pstmt.executeUpdate();
            return filasAfectadas > 0;

        } catch (SQLException e) {
            System.err.println("❌ [ConfiguracionLedDAO.guardarOActualizar] Error al persistir la configuración: " + e.getMessage());
            return false;
        }
    }
}