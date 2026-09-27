/**
 * Sistema de Control de Rocódromo Inteligente - MoonBoard Smart
 * Copyleft © 2026. Todos los derechos reservados al desarrollador.
 */
package com.rocodromo.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.io.InputStream;
import java.sql.*;
import java.util.HashSet;
import java.util.Scanner;
import java.util.Set;

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

                    // Se actualizan las bases de datos creadas antes de existir los roles
                    migrarRolesPresas(conn);
                }
            }
        } catch (Exception e) {
            System.err.println("❌ Error crítico al inicializar las tablas: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Añade la columna 'tipo' a RUTA_PRESAS en las bases de datos que ya existían
     * antes de la incorporación de los roles de presa (inicio / intermedia / top).
     *
     * La migración NO puede vivir en init.sql porque ese script se reejecuta en cada
     * arranque dentro de un único bloque try: un 'ALTER TABLE' repetido lanzaría una
     * excepción y abortaría las sentencias siguientes. Aquí se comprueba primero el
     * esquema real con 'PRAGMA table_info', de modo que la operación es idempotente.
     */
    private static void migrarRolesPresas(Connection conn) {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("PRAGMA table_info(RUTA_PRESAS)")) {

            boolean columnaYaExiste = false;
            while (rs.next()) {
                if ("tipo".equalsIgnoreCase(rs.getString("name"))) {
                    columnaYaExiste = true;
                    break;
                }
            }

            if (columnaYaExiste) {
                return;
            }

            try (Statement alter = conn.createStatement()) {
                alter.executeUpdate("ALTER TABLE RUTA_PRESAS ADD COLUMN tipo TEXT NOT NULL DEFAULT 'intermedia'");
            }
            System.out.println("🛠️ Migración aplicada: RUTA_PRESAS.tipo (roles de presa por vía).");

        } catch (SQLException e) {
            System.err.println("❌ Error al migrar los roles de presa en RUTA_PRESAS: " + e.getMessage());
        }
    }

    /**
     * Verifica que la tabla PRESAS contenga los registros maestros del panel
     * (grid 18×11 = 198 presas). Inserta los que falten, vinculando cada presa
     * a su índice de LED (1 a 198, coincidiendo con los IDs usados por el frontend
     * y por RUTA_PRESAS.presa_id).
     */
    private static void poblarPresasSiVacia(Connection conn) {
        String sqlConsulta = "SELECT indice_led FROM PRESAS";
        String sqlInsert = "INSERT INTO PRESAS (posicion_x, posicion_y, indice_led) VALUES (?, ?, ?)";

        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sqlConsulta)) {

            Set<Integer> existentes = new HashSet<>();
            while (rs.next()) {
                existentes.add(rs.getInt("indice_led"));
            }

            if (existentes.size() >= 198 && existentes.containsAll(Set.of(1, 198))) {
                System.out.println("✅ La tabla PRESAS ya contiene los 198 registros maestros.");
                return;
            }

            try (PreparedStatement pstmt = conn.prepareStatement(sqlInsert)) {
                int insertados = 0;
                // Los índices se usan en base 1 (1 a 198) para coincidir con los IDs del frontend
                for (int i = 1; i <= 198; i++) {
                    if (existentes.contains(i)) continue;
                    pstmt.setInt(1, 0);   // posicion_x por defecto
                    pstmt.setInt(2, 0);   // posicion_y por defecto
                    pstmt.setInt(3, i);   // indice_led = número de LED (1-198)
                    pstmt.addBatch();
                    insertados++;
                }
                pstmt.executeBatch();
                if (insertados > 0) {
                    System.out.println("✅ Matriz de 198 presas asegurada: se registraron " + insertados + " presas faltantes (Mapeo LED 1-198).");
                }
            }
        } catch (SQLException e) {
            System.err.println("❌ Error al poblar la tabla maestra de presas: " + e.getMessage());
        }
    }
}