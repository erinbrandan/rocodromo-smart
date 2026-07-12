-- Habilitar el soporte de claves foráneas en SQLite
PRAGMA foreign_keys = ON;

-- 1. Configuración física del hardware IoT
CREATE TABLE IF NOT EXISTS CONFIGURACION_LED (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    total_leds INTEGER NOT NULL,
    pin_gpio INTEGER NOT NULL,
    brillo INTEGER NOT NULL
);

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
CREATE TABLE IF NOT EXISTS PRESAS (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    posicion_x INTEGER NOT NULL,
    posicion_y INTEGER NOT NULL,
    indice_led INTEGER NOT NULL UNIQUE -- Clave para el mapeo con Python y JS
);

-- 5. Tabla Intermedia: Relación de LEDs por Ruta
-- CORRECCIÓN: Apuntamos directamente a 'indice_led' para que coincida con el array del Frontend
CREATE TABLE IF NOT EXISTS RUTA_PRESAS (
    ruta_id INTEGER,
    presa_id INTEGER,
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