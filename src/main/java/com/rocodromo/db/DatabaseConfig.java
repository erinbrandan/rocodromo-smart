/**
 * Sistema de Control de Rocódromo Inteligente - MoonBoard Smart
 * Copyleft © 2026. Todos los derechos reservados al desarrollador.
 */
package com.rocodromo.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.io.InputStream;
import java.sql.*;
import java.util.Scanner;

/**
 * Configuración del motor de persistencia SQLite para entornos embebidos.
 * Gestiona un pool de conexiones HikariCP con modo WAL activo.
 *
 * @author Erin Brandan Vazquez Enes
 * @version 1.3
 */
public class DatabaseConfig {
    private static final HikariDataSource dataSource;

    static {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:sqlite:rocodromo.db");
        config.setPoolName("MoonBoardPool");
        config.setMaximumPoolSize(4); // Pequeño y óptimo para hardware Raspberry Pi

        // Optimización WAL (Evita bloqueos de lectura/escritura concurrentes)
        config.addDataSourceProperty("journal_mode", "WAL");
        config.addDataSourceProperty("foreign_keys", "true");

        dataSource = new HikariDataSource(config);
    }

    /**
     * Proporciona una conexión activa desde el pool de conexiones de HikariCP.
     * Al configurarse globalmente con 'foreign_keys=true', cada conexión devuelta
     * ya tiene activa de forma nativa la integridad referencial.
     *
     * @return Connection objeto de conexión JDBC listo para operar.
     * @throws SQLException Si el pool se queda sin conexiones disponibles o falla el acceso al archivo .db.
     */
    public static Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    /**
     * Lee y ejecuta el script db/init.sql, limpiando comentarios SQL
     * y fragmentando las sentencias DDL para su ejecución secuencial.
     */
    public static void inicializarTablas() {
        System.out.println("📦 Cargando configuración de base de datos desde init.sql...");

        try (InputStream is = DatabaseConfig.class.getClassLoader().getResourceAsStream("db/init.sql")) {
            if (is == null) {
                System.err.println("❌ No se encontró el script db/init.sql en resources.");
                return;
            }

            try (Scanner s = new Scanner(is).useDelimiter("\\A");
                 Connection conn = getConnection();
                 Statement stmt = conn.createStatement()) {

                String scriptCompleto = s.hasNext() ? s.next() : "";

                if (!scriptCompleto.trim().isEmpty()) {

                    // Se eliminan los comentarios de línea completa '-- ...' para evitar falsos positivos
                    String scriptLimpio = scriptCompleto.replaceAll("(?m)^\\s*--.*$", "");

                    // Se fragmenta por punto y coma el script limpio
                    String[] sentencias = scriptLimpio.split(";");

                    int tablasCreadas = 0;
                    for (String sentencia : sentencias) {
                        String sqlLimpia = sentencia.trim();

                        // Se ignoran líneas vacías o comandos PRAGMA
                        if (!sqlLimpia.isEmpty() && !sqlLimpia.toUpperCase().startsWith("PRAGMA")) {
                            stmt.executeUpdate(sqlLimpia);
                            tablasCreadas++;
                        }
                    }
                    System.out.println("🗄️ Estructura DDL procesada (" + tablasCreadas + " consultas ejecutadas limpias).");

                    // Se asegura el poblado inicial de la matriz de presas
                    poblarPresasSiVacia(conn);
                }
            }
        } catch (Exception e) {
            System.err.println("❌ Error crítico al inicializar las tablas: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Verifica si la tabla PRESAS está vacía. De ser así, inserta los 121 registros
     * maestros vinculando cada posición al índice de su LED correspondiente.
     */
    private static void poblarPresasSiVacia(Connection conn) {
        String sqlCheck = "SELECT COUNT(*) FROM PRESAS";
        String sqlInsert = "INSERT INTO PRESAS (id, posicion_x, posicion_y, indice_led) VALUES (?, ?, ?, ?)";

        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sqlCheck)) {

            if (rs.next() && rs.getInt(1) == 0) {
                System.out.println("🌱 La tabla PRESAS está vacía. Poblando matriz de 121 leds maestros...");

                try (PreparedStatement pstmt = conn.prepareStatement(sqlInsert)) {
                    // Se generan las 121 presas en lote
                    for (int i = 1; i <= 121; i++) {
                        pstmt.setInt(1, i);         // ID único de la presa (1-121)
                        pstmt.setInt(2, 0);         // posicion_x por defecto
                        pstmt.setInt(3, 0);         // posicion_y por defecto

                        // Los LEDs se direccionan en base 0 en el script Python (0 a 120)
                        pstmt.setInt(4, i - 1);

                        pstmt.addBatch();
                    }
                    pstmt.executeBatch();
                    System.out.println("✅ Matriz de 121 presas inyectada correctamente en el sistema (Mapeo LED Base 0).");
                }
            }
        } catch (SQLException e) {
            System.err.println("❌ Error al poblar la tabla maestra de presas: " + e.getMessage());
        }
    }
}