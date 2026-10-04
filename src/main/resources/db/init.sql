-- Habilitar el soporte de claves foráneas en SQLite
PRAGMA foreign_keys = ON;

-- 1. Configuración física del hardware IoT
CREATE TABLE IF NOT EXISTS CONFIGURACION_LED (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    total_leds INTEGER NOT NULL,
    pin_gpio INTEGER NOT NULL,
    brillo INTEGER NOT NULL
);

-- 1.b Registro único maestro de configuración (panel de 198 LEDs, GPIO 18, brillo 50)
INSERT OR REPLACE INTO CONFIGURACION_LED (id, total_leds, pin_gpio, brillo) VALUES (1, 198, 18, 50);

-- 2. Gestión de Usuarios
CREATE TABLE IF NOT EXISTS USUARIOS (
    correo TEXT PRIMARY KEY,
    nombre TEXT NOT NULL,
    apellidos TEXT,
    contrasena TEXT NOT NULL
);

-- 3. Catálogo de Vías Globales
CREATE TABLE IF NOT EXISTS RUTAS (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    nombre TEXT NOT NULL,
    grado TEXT NOT NULL,
    equipador TEXT,
    fecha TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 4. Diccionario de Presas (Mapeo de coordenadas al número de LED)
-- La numeración crece desde la esquina INFERIOR izquierda: la fila 1 es la de abajo
-- y el LED 1 es su presa más a la izquierda. posicion_x = columna (1 = A, 11 = K),
-- posicion_y = fila (1 = inferior, 18 = superior).
CREATE TABLE IF NOT EXISTS PRESAS (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    posicion_x INTEGER NOT NULL,
    posicion_y INTEGER NOT NULL,
    indice_led INTEGER NOT NULL UNIQUE -- Clave para el mapeo con Python y JS
);

-- 5. Tabla Intermedia: Relación de LEDs por Ruta
-- CORRECCIÓN: Apuntamos directamente a 'indice_led' para que coincida con el array del Frontend
-- NOTA: La columna 'tipo' define el papel de la presa en la vía (inicio / intermedia / top).
-- Las bases de datos ya creadas se actualizan con la migración de DatabaseConfig.
CREATE TABLE IF NOT EXISTS RUTA_PRESAS (
    ruta_id INTEGER,
    presa_id INTEGER,
    tipo TEXT NOT NULL DEFAULT 'intermedia' CHECK(tipo IN ('intermedia', 'inicio', 'top')),
    PRIMARY KEY (ruta_id, presa_id),
    FOREIGN KEY (ruta_id) REFERENCES RUTAS(id) ON DELETE CASCADE,
    FOREIGN KEY (presa_id) REFERENCES PRESAS(indice_led) ON DELETE CASCADE
);

-- 6. Historial de Progresión (Proyectos / Encadenadas)
-- CORRECCIÓN: Clave primaria compuesta para evitar duplicados de la misma vía por usuario
CREATE TABLE IF NOT EXISTS HISTORIAL_ENTRENAMIENTO (
    usuario_id TEXT,
    ruta_id INTEGER,
    fecha TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    estado TEXT CHECK(estado IN ('encadenada', 'proyecto')) DEFAULT 'proyecto',
    PRIMARY KEY (usuario_id, ruta_id),
    FOREIGN KEY (usuario_id) REFERENCES USUARIOS(correo) ON DELETE CASCADE,
    FOREIGN KEY (ruta_id) REFERENCES RUTAS(id) ON DELETE CASCADE
);

-- 7. Ranking del minijuego "Pulso Vertical"
CREATE TABLE IF NOT EXISTS RANKING_PULSO_VERTICAL (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    nombre_jugador TEXT NOT NULL,
    tiempo_segundos REAL NOT NULL,
    fecha TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);