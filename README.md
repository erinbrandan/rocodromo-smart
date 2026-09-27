# 🧗 Rocódromo Inteligente — MoonBoard Smart Control System

Sistema IoT completo para la gestión de rutas de escalada en paneles MoonBoard, con control en tiempo real de iluminación LED sobre hardware Raspberry Pi. Backend híbrido Java/Python, base de datos SQLite embebida y frontend web ligero servido desde el propio dispositivo.

---

## 🏗️ Arquitectura Híbrida y Justificación Tecnológica

El sistema se compone de tres capas especializadas, cada una resolviendo un problema de diseño distinto:

### ☕ Java — Backend Principal (Javalin / Maven)

Java actúa como el **cerebro central** del sistema. La elección de Javalin 6 como framework HTTP se debe a su peso mínimo (~150 KB), su API funcional basada en lambdas y su rendimiento competitivo frente a soluciones más pesadas como Spring Boot, algo crítico cuando se ejecuta en un procesador ARM con 1 GB de RAM.

La capa de persistencia utiliza **SQLite** a través del driver JDBC de Xerial, gestionado por un pool de conexiones **HikariCP** en modo WAL (Write-Ahead Logging). Esta combinación elimina bloqueos de lectura/escritura concurrentes y permite atender múltiples peticiones de la interfaz web de forma simultánea sin degradación, manteniendo la simplicidad de un archivo `.db` autocontenido sin necesidad de un servidor de base de datos externo.

La API REST expone endpoints RESTful para autenticación de usuarios, CRUD del catálogo de rutas, control directo del hardware y gestión de estados de entrenamiento (proyecto → encadenada). La serialización JSON se gestiona con Jackson.

### 🐍 Python — Script Esclavo de Hardware (`leds.py`)

El control de la tira de LEDs WS2812B/WS2811 se delega a un **script Python independiente** que se invoca desde la JVM mediante `ProcessBuilder`. Esta arquitectura por separación de procesos no es un capricho: es una **decisión de ingeniería defensible**.

Los LEDs direccionables WS2812B requieren una señal de datos con tiempos de microsegundos (~800 KHz). El **Garbage Collector de Java (G1/ZGC)** introduce pausas impredecibles de recolección que, si la JVM tuviera acceso directo al bus GPIO, provocarían parpadeos, corrupción de colores o apagados momentáneos en la tira. Al ejecutar el control GPIO en un proceso Python separado, se **aisla la capa de tiempo real del hardware** de las pausas de GC de la JVM.

`leds.py` habla con el panel a través de **Adafruit Blinka** (`board` + `neopixel`), que es el driver indicado en `requirements.txt`.

La comunicación entre ambos procesos tiene **dos modos**, y el servicio elige el mejor disponible en cada llamada:

**a) Daemon (preferente).** Un proceso Python persistente que se arranca una sola vez y se queda esperando órdenes por **stdin**, de modo que la tira se inicializa una única vez y no hay coste de arranque por comando. El protocolo es un texto por línea: `encender:<ids>:<color>`, `agregar:<ids>:<color>`, `apagar` y `salir`. Java confirma cada orden leyendo la línea de respuesta (`OK:...` / `ERR:...`).

**b) CLI (fallback).** Si el daemon no está disponible, se lanza `leds.py` como proceso independiente por llamada, pasándole los parámetros (comando, lista de LEDs, total de LEDs, pin GPIO, brillo y color) como argumentos de consola. El hilo de la petición HTTP se libera tras un chequeo de 50 ms: si el script no falla en ese intervalo, se asume que la tira se está actualizando en background y la respuesta JSON se devuelve al cliente sin bloquear el servidor.

```text
┌─────────────┐   Fetch / JSON   ┌─────────────┐   stdin / pipes   ┌──────────────┐
│   Frontend  │  ◄─────────────► │  Java API   │ ◄───────────────► │   leds.py    │
│  (Browser)  │                  │  (Javalin)  │   daemon: 1 proc  │              │
└─────────────┘                  └──────┬──────┘   CLI: N procs     └──────┬───────┘
                                        │                                 │
                                   ┌────▼──────┐                   ┌──────▼───────┐
                                   │  SQLite   │                   │  Blinka      │
                                   │ (HikariCP)│                   │ (neopixel)   │
                                   └───────────┘                   └──────┬───────┘
                                                                          │
                                                                   ┌──────▼───────┐
                                                                   │ Tira WS2812B │
                                                                   │  GPIO 18     │
                                                                   └──────────────┘
```


### 🌐 Frontend — HTML5, CSS3, JavaScript Vanilla

La interfaz web se construye con tecnologías estándar **sin frameworks de JavaScript**. Esta decisión es intencional: al ejecutarse como servidor web embebido en una Raspberry Pi 3 B con procesador ARM Cortex-A53 y 1 GB de RAM, un framework como React, Angular o Vue añadiría entre 20–80 MB de consumo de memoria y tiempos de parsing que saturarían el CPU durante el renderizado inicial.

En su lugar, se emplea **Fetch API nativa** para las comunicaciones HTTP, DOM manipulation estándar y CSS3 con transiciones para la retroalimentación visual. El resultado es una interfaz que carga en menos de 200 ms en el navegador local y consume prácticamente memoria despreciable.

Las páginas del frontend son:

| Archivo | Función |
|---|---|
| `login.html` | Formulario de autenticación de escaladores |
| `registro.html` | Alta de nuevos usuarios con hash SHA-256 |
| `dashboard.html` | Panel principal: catálogo de rutas, simulador LED, control de hardware y minijuego Pulso Vertical |
| `css/estilos.css` | Hoja de estilos global del sistema (simulador de presas, roles por color, filtros del catálogo y ranking del Pulso Vertical) |
| `js/login.js` | Lógica de autenticación vía Fetch API |
| `js/registro.js` | Validación y envío del formulario de registro |
| `js/dashboard.js` | Motor de interacción: catálogo y sus filtros, selección de vías, roles en el MoonBoard Builder, envío de comandos LED multicolor y simulador del Pulso Vertical |

---

## 🗄️ Modelo de Datos (SQLite)

La base de datos `rocodromo.db` se crea automáticamente al iniciar el servidor por primera vez, ejecutando las migraciones DDL contenidas en `src/main/resources/db/init.sql`. El esquema relacional consta de 7 tablas:

### Diagrama Relacional

```sql
┌──────────────────────────┐       ┌──────────────────────────┐
│       USUARIOS           │       │      CONFIGURACION_LED   │
├──────────────────────────┤       ├──────────────────────────┤
│ PK correo       TEXT     │       │ PK id           INTEGER  │
│    nombre       TEXT     │       │    total_leds   INTEGER  │
│    apellidos    TEXT     │       │    pin_gpio     INTEGER  │
│    contrasena   TEXT     │       │    brillo       INTEGER  │
└──────────┬───────────────┘       └──────────────────────────┘
           │
           │ 1:N
           ▼
┌──────────────────────────────────┐
│    HISTORIAL_ENTRENAMIENTO       │
├──────────────────────────────────┤
│ PK,FK usuario_id  TEXT           │
│ PK,FK ruta_id     INTEGER        │
│    fecha           TIMESTAMP     │
│    estado          TEXT (CHECK)  │──► 'proyecto' | 'encadenada'
└───────────────┬──────────────────┘
                │ N:1
                ▼
┌──────────────────────────┐       ┌──────────────────────────┐
│         RUTAS            │       │         PRESAS           │
├──────────────────────────┤       ├──────────────────────────┤
│ PK id          INTEGER   │       │ PK id            INTEGER │
│    nombre      TEXT      │       │    posicion_x    INTEGER │
│    grado       TEXT      │       │    posicion_y    INTEGER │
│    equipador   TEXT      │       │ PK indice_led    INTEGER │ UNIQUE
│    fecha       TIMESTAMP │       └──────────┬───────────────┘
└──────────┬───────────────┘                  │
           │                                  │ N:1
           │ N:M                              ▼
           └──────────────────►┌──────────────────────────┐
                               │       RUTA_PRESAS        │
                               ├──────────────────────────┤
                               │ PK,FK ruta_id   INTEGER  │
                               │ PK,FK presa_id  INTEGER  │
                               │    tipo        TEXT (CHK)│──► 'inicio'|'intermedia'|'top'
                               └──────────────────────────┘

┌──────────────────────────────────────────┐
│        RANKING_PULSO_VERTICAL            │
├──────────────────────────────────────────┤
│ PK id                INTEGER             │
│    nombre_jugador    TEXT                │
│    tiempo_segundos   REAL                │
│    fecha             TIMESTAMP (Default) │
└──────────────────────────────────────────┘
```

### Descripción de Tablas

| Tabla | Propósito |
|---|---|
| `CONFIGURACION_LED` | Parámetros físicos de la tira (total de LEDs, pin GPIO, brillo). Registro único por sistema. |
| `USUARIOS` | Credenciales y datos de los escaladores. La contraseña se almacena como hash SHA-256. Clave primaria: correo electrónico. |
| `RUTAS` | Catálogo global de vías de escalada creadas por los usuarios (nombre, grado, equipador, fecha de creación). |
| `PRESAS` | Diccionario de coordenadas del panel physical (X, Y) vinculadas al índice del LED correspondiente. Se pre-cargan 198 registros maestros (grid 18×11). |
| `RUTA_PRESAS` | Tabla intermedia N:M que asocia cada ruta con la lista de presas/LEDs que la componen. La columna `tipo` guarda el papel de cada presa dentro del diseño de la vía, con constraint CHECK: `inicio`, `intermedia` o `top` (ver [Roles de presa](#roles-de-presa-por-vía)). |
| `HISTORIAL_ENTRENAMIENTO` | Registro de progresión del escalador. Clave primaria compuesta `(usuario_id, ruta_id)` para evitar duplicados. Campo `estado` con constraint CHECK: `proyecto` o `encadenada`. Las tres tablas cuelgan de `RUTAS` con `ON DELETE CASCADE`, así que al borrar una vía desaparecen en cascada sus presas y todas sus adoptaciones. |
| `RANKING_PULSO_VERTICAL` | Marcas registradas en el minijuego Pulso Vertical: nombre del jugador, tiempo aguantado en segundos (con decimales) y fecha de registro. |

---

## 🧗 Catálogo de Vías

El catálogo es **global**: toda vía creada por cualquier escalador vive en `RUTAS` y es visible en el bloque "Comunidad" para todo el mundo. Cada escalador mantiene además su **propia lista** de vías, en `HISTORIAL_ENTRENAMIENTO`, con dos estados posibles (`proyecto` y `encadenada`).

### Ciclo de vida de una vía

| Acción | Qué ocurre |
|---|---|
| **Crear** | Se inserta en `RUTAS`, sus presas con su rol en `RUTA_PRESAS` y el vínculo del autor en `HISTORIAL_ENTRENAMIENTO` con estado `proyecto`. Todo en una única transacción ACID (`RutaDAO.guardarRuta`). |
| **Adoptar desde la Comunidad** | `POST /api/rutas/{id}/agregar` inserta el vínculo del usuario. Usa `INSERT OR IGNORE`, así que adoptar dos veces la misma vía es idempotente y devuelve `409` en el frontend. |
| **Encadenar** | `PUT /api/rutas/{id}/estado` promociona la vía de `proyecto` a `encadenada` en la lista del usuario. |
| **Eliminar** | `DELETE /api/rutas/{id}` borra la fila de `RUTAS` y, **por cascada**, sus `RUTA_PRESAS` y *todos* sus `HISTORIAL_ENTRENAMIENTO`: la vía desaparece de la Comunidad y de la lista de cualquier escalador que la hubiera adoptado. El borrado solo se permite si la vía estaba en la lista del usuario solicitante, de modo que conocer un ID ajeno no basta. |

### Roles de presa por vía

Cada presa de una vía tiene un papel semántico que se guarda en `RUTA_PRESAS.tipo` y que se traduce a un color en el panel físico. Es lo que permite que una misma vía se encienda multicolor, replicando el diseño original del MoonBoard:

| Rol | Significado | Color |
|---|---|---|
| `inicio` | Los apoyos de salida | Verde `00FF00` |
| `intermedia` | El cuerpo de la vía | Azul `0000FF` |
| `top` | El remate final | Rojo `FF0000` |

En el MoonBoard Builder se asignan clicando sucesivamente sobre la matriz: 1er clic → intermedia, 2º → inicio, 3º → top, y un 4º clic o mantener pulsado deselecciona. `PresaRuta.java` centraliza la equivalencia rol ↔ color para que backend, hardware y frontend compartan una sola definición de la paleta.

Al seleccionar una vía, el backend agrupa las presas por color y las envía en ese orden: el primer grupo limpia el panel y los siguientes se superponen, de modo que un solo viaje por el bus pinta la vía entera multicolor.

### Filtros del catálogo

En el bloque "Comunidad" hay dos filtros combinables (se aplican a la vez, con AND):

- **Por nombre:** búsqueda de texto sobre el nombre de la vía, sin distinguir mayúsculas.
- **Por grado mínimo:** un desplegable con un apartado inicial "Todos los grados" que anula el criterio. Elegir `6a` devuelve `6a`, `6a+`, `6b`, `6b+` y todo lo más difícil. La comparación se hace sobre una escala ordenada (`ESCALA_GRADOS` en `dashboard.js`); un grado que no esté en la escala se descarta al filtrar, porque no hay forma de saber si supera el mínimo.

El filtrado se resuelve en el navegador sobre la respuesta ya descargada de `/api/rutas`, así que escribir en el buscador no genera peticiones adicionales. Los filtros se limpian solos al salir de la pestaña Comunidad.

---

## ⚙️ Requisitos del Sistema

### Hardware

| Componente | Especificación |
|---|---|
| **SBC** | Raspberry Pi 3 Modelo B (Broadcom BCM2837, 4 núcleos ARM Cortex-A53 @ 1.2 GHz, 1 GB RAM) |
| **Fuente de alimentación** | Regulada a 5 V / mínimo 5 A (10 A recomendado para tiras largas). **Nunca alimentar la tira desde los pines GPIO de la RPi.** |
| **Tira de LEDs** | WS2812B o WS2811 (Neopixel Compatible) — Conexión de datos al **Pin Físico 12 (GPIO 18)** compartiendo masa (GND) con la SBC |
| **Almacenamiento** | MicroSD Clase 10 (mín. 16 GB) con Raspberry Pi OS |

> **Nota de hardware:** El Pin Físico 12 corresponde al GPIO 18, que es el canal PWM0 del BCM2837. `leds.py` recibe el número de pin como parámetro y lo resuelve con `board.D<pin>` a través de Blinka, así que el cable de datos de la tira va a esa pata y la masa a un GND común con la RPi. Los parámetros vienen de la tabla `CONFIGURACION_LED`, así que se pueden cambiar sin tocar el código.

### Software

| Componente | Versión requerida |
|---|---|
| **SO** | Debian 12 (Bookworm) / Raspberry Pi OS (64-bit) |
| **Java** | JDK 17 o superior (OpenJDK recomendado) |
| **Python** | Python 3.9+ |
| **Maven** | 3.8+ (para compilar el JAR en entorno de desarrollo) |
| **Librerías Python** | `Adafruit-Blinka>=8.0.0`, `adafruit-circuitpython-neopixel>=6.0.0` |

---

## 🚀 Guía de Instalación y Despliegue

### Paso 1 — Configurar Permisos de Hardware (sudo sin contraseña)

El script `leds.py` necesita acceso directo al bus DMA/PWM de la Raspberry Pi para generar la señal de datos de los LEDs. Esto requiere ejecutarse con privilegios `root`. Sin embargo, si el servidor Java tuviera que solicitar la contraseña de `sudo` en cada llamada, el flujo HTTP se bloquearía esperando input interactivo.

**Solución:** Configurar una regla `NOPASSWD` en el archivo sudoers para que el usuario del servidor pueda invocar `python3 leds.py` sin interacción.

```bash
# Abrir el editor seguro de sudoers
sudo visudo

# Añadir la siguiente línea al final del archivo
# (reemplaza 'pi' por tu usuario real si es diferente)
pi ALL=(ALL) NOPASSWD: /usr/bin/python3 /home/pi/rocodromo-smart/leds.py
```

> **Seguridad:** La regla se restringe al script específico y al binario `python3`. No se concede acceso irrestricto a `sudo`.

### Paso 2 — Instalar Dependencias Python

```bash
cd /home/pi/rocodromo-smart
pip install -r requirements.txt
```

### Paso 3 — Compilar el JAR (desde máquina de desarrollo)

El proyecto utiliza **Maven Shade Plugin** para generar un Uber-JAR autocontenido con todas las dependencias empaquetadas:

```bash
mvn clean package
```

El archivo resultante se genera en:
```
target/rocodromo-smart-1.0-SNAPSHOT.jar
```

### Paso 4 — Desplegar en la Raspberry Pi

```bash
# Transferir el JAR compilado a la RPi
scp target/rocodromo-smart-1.0-SNAPSHOT.jar pi@<IP_RASPBERRY>:/home/pi/rocodromo-smart/

# Conectar por SSH
ssh pi@<IP_RASPBERRY>

# Iniciar el servidor
cd /home/pi/rocodromo-smart
java -jar rocodromo-smart-1.0-SNAPSHOT.jar
```

Al ejecutarse por primera vez, el sistema:

1. Creará automáticamente el archivo `rocodromo.db` en el directorio de ejecución
2. Ejecutará las migraciones DDL desde `db/init.sql` (creación de tablas)
3. Poblará la tabla `PRESAS` con los 198 registros maestros del grid 18×11 (mapeo LED 1-198)
4. Arrancará el servidor HTTP en el puerto **8080**

### Desarrollo sin hardware GPIO

En una máquina de desarrollo que no sea una Raspberry Pi (o sin la tira conectada), `leds.py` detecta que Blinka no está disponible y entra en **modo simulación**: no toca el GPIO y en su lugar imprime en la consola un resumen de lo que habría pintado en el panel. La API, la base de datos y el frontend funcionan igual, así que se puede desarrollar toda la parte web sin panel físico.

Ese modo se activa solo (`leds.py` captura el `ImportError` de `board`/`neopixel`), pero se puede forzar con la variable de entorno `FORCE_SIMULATION`, útil para silenciar el aviso de dependencias ausentes:

```bash
# Arrancar el servidor sin tocar el GPIO
FORCE_SIMULATION=1 java -jar rocodromo-smart-1.0-SNAPSHOT.jar
```

### Paso 5 — Acceso

Abrir en cualquier navegador conectado a la misma red local:

```
http://<IP_RASPBERRY>:8080
```

El servidor redirige automáticamente a `/login.html`.

---
## 📶 Configuración del Punto de Acceso Wi-Fi

La Raspberry Pi puede funcionar como un **Punto de Acceso (Access Point)** para que dispositivos móviles o portátiles se conecten directamente al sistema sin necesidad de un router externo.

### Componentes utilizados

- **hostapd**: crea y gestiona la red Wi-Fi.
- **dnsmasq**: proporciona servicio DHCP para asignar direcciones IP automáticamente.
- **NetworkManager + systemd-networkd**: gestionan la configuración de red en Raspberry Pi OS Bookworm.

### Configuración recomendada

- Configurar `hostapd` indicando el archivo `hostapd.conf` mediante `DAEMON_CONF`.
- Asignar una **IP estática** a `wlan0` (por ejemplo `192.168.4.1/24`), imprescindible para que `dnsmasq` pueda entregar direcciones IP a los clientes.
- Configurar `NetworkManager` para que no gestione `wlan0`, evitando que pierda la IP estática tras reinicios o cambios en la red Ethernet.
- En caso de que la interfaz Wi-Fi aparezca bloqueada, desbloquearla con:

```bash
sudo rfkill unblock wlan
```

### Comandos de comprobación

```bash
# Comprobar hostapd
sudo hostapd /etc/hostapd/hostapd.conf

# Comprobar servidor DHCP
sudo dnsmasq -d

# Asignar temporalmente una IP estática
sudo ip addr add 192.168.4.1/24 dev wlan0
```

Esta configuración garantiza que cualquier dispositivo pueda conectarse directamente a la Raspberry Pi y acceder a la aplicación web incluso sin conexión a Internet.

## 📁 Estructura del Proyecto

```
rocodromo-smart/
├── pom.xml                          # Configuración Maven (Javalin 6.1.3, HikariCP, Jackson)
├── leds.py                          # Script Python de control de hardware (WS2812B)
├── requirements.txt                 # Dependencias Python
├── rocodromo.db                     # Base de datos SQLite (generada automáticamente)
└── src/main/
    ├── java/com/rocodromo/
    │   ├── App.java                 # Punto de entrada — Configuración de Javalin y rutas
    │   ├── api/
    │   │   ├── HardwareController.java    # Endpoints de control directo de LEDs
    │   │   ├── JuegoController.java       # Endpoints del minijuego Pulso Vertical y ranking
    │   │   ├── RutaController.java        # CRUD de rutas y selección de vías
    │   │   └── UsuarioController.java     # Registro y autenticación
    │   ├── dao/
    │   │   ├── ConfiguracionLedDAO.java   # Persistencia de parámetros hardware
    │   │   ├── PresaDAO.java              # Mapeo de coordenadas del panel
    │   │   ├── RankingPulsoVerticalDAO.java # Marcas del ranking del minijuego
    │   │   ├── RutaDAO.java               # Transacciones del catálogo de vías
    │   │   └── UsuarioDAO.java            # Gestión de usuarios + hash SHA-256
    │   ├── db/
    │   │   └── DatabaseConfig.java        # Pool HikariCP + inicialización DDL
    │   ├── hardware/
    │   │   └── LedService.java            # Bridge Java → Python (daemon + ProcessBuilder)
    │   ├── model/
    │   │   ├── ConfiguracionLed.java      # Modelo de configuración de hardware
    │   │   ├── Presa.java                 # Modelo de presa/LED
    │   │   ├── PresaRuta.java             # Presa + rol dentro de una vía (inicio/intermedia/top)
    │   │   ├── RankingPulsoVertical.java  # Modelo de marca del ranking
    │   │   ├── Ruta.java                  # Modelo de ruta de escalada
    │   │   └── Usuario.java               # Modelo de usuario
    │   └── service/
    │       └── JuegoPulsoVerticalService.java # Hilo de fondo del minijuego (secuencia de luces)
    └── resources/
        ├── db/
        │   └── init.sql                   # Migraciones DDL del esquema relacional
        └── public/
            ├── login.html                 # Pantalla de autenticación
            ├── registro.html              # Formulario de alta de escaladores
            ├── dashboard.html             # Panel principal del sistema
            ├── css/estilos.css            # Estilos globales
            └── js/
                ├── login.js               # Lógica de login
                ├── registro.js            # Lógica de registro
                └── dashboard.js           # Motor de interacción del panel
```

---

## 🔧 Endpoints de la API REST

| Método | Ruta | Descripción |
|---|---|---|
| `GET` | `/api/estado` | Diagnóstico del estado del backend |
| `GET` | `/api/rutas` | Listado de rutas (global o filtrado por usuario/estado) |
| `POST` | `/api/rutas/crear` | Crear una nueva vía de escalada |
| `POST` | `/api/rutas/{id}/seleccionar` | Seleccionar y encender una ruta en el panel LED |
| `POST` | `/api/rutas/{id}/agregar` | Vincular ruta comunitaria al historial del usuario |
| `PUT` | `/api/rutas/{id}/estado` | Cambiar estado de la vía (`proyecto` → `encadenada`) |
| `DELETE` | `/api/rutas/{id}` | Eliminar la vía **de todo el sistema** (ver [Ciclo de vida de una vía](#ciclo-de-vida-de-una-vía)) |
| `POST` | `/api/hardware/apagar` | Apagar todos los LEDs del panel |
| `POST` | `/api/hardware/encender-manual` | Encender LEDs por grupos con color (diagnóstico y diseño de vías) |
| `POST` | `/api/hardware/encender-led` | Encender un único LED (feedback en tiempo real) |
| `POST` | `/api/hardware/agregar-led` | Superponer un único LED sin limpiar el panel |
| `POST` | `/api/usuarios/registro` | Registrar un nuevo escalador |
| `POST` | `/api/usuarios/login` | Autenticar escalador |
| `POST` | `/api/juego/pulso-vertical/iniciar` | Iniciar el minijuego (cuenta atrás de 6 s) |
| `POST` | `/api/juego/pulso-vertical/pausar` | Pausar el avance del juego |
| `POST` | `/api/juego/pulso-vertical/reanudar` | Reanudar el juego desde la pausa |
| `POST` | `/api/juego/pulso-vertical/finalizar` | Finalizar el juego y apagar el panel |
| `GET` | `/api/juego/pulso-vertical/ranking` | Top 10 del ranking del minijuego |
| `POST` | `/api/juego/pulso-vertical/ranking` | Guardar una marca (`nombre_jugador`, `tiempo_segundos`) |

---

## 🎮 Minijuego: Pulso Vertical

Modo de entrenamiento lúdico en tiempo real implementado sobre el panel LED. El objetivo es aguantar en pie el mayor tiempo posible mientras las presas van desapareciendo. Incluye cronómetro, ranking persistente y un **simulador visual** en el frontend que replica la lógica del backend sin necesidad de hardware.

### Flujo de la partida

1. **Cuenta atrás (6 segundos):** el panel se enciende por franjas en **rojo** (filas 1–6 a los 0 s, filas 1–12 a los 2 s y el panel completo a los 4 s).
2. **Fase verde:** en el segundo 6, los 198 LEDs se encienden en **verde** y comienza el juego de resistencia.
3. **Reducción progresiva:** cada ciclo de 3 segundos se elimina un **35%** de los LEDs activos (`Math.floor(activos * 0.35)`), con un mínimo de 1 LED por ciclo y un límite de seguridad que **nunca deja el panel con menos de 6 LEDs**.
4. **Fase naranja (1 segundo):** los LEDs seleccionados para apagarse permanecen 1 segundo en color **naranja** antes de desaparecer, avisando al escalador del cambio.
5. **Bucle infinito:** con exactamente 6 LEDs (mínimo 2 apoyos por zona: alta, media y baja) se entra en un bucle en el que se apaga 1 LED (previo paso por naranja) y se enciende 1 LED nuevo biomecánicamente válido.

### Restricciones biomecánicas

- **Distancia mínima de 30 cm** entre presas activas simultáneas.
- **Alcance máximo de 130 cm** entre presas consecutivas alcanzables.
- **Equilibrio entre zonas:** siempre quedan al menos 2 apoyos en la zona alta (filas 1–6), 2 en la media (filas 7–12) y 2 en la baja (filas 13–18).

### Arquitectura del minijuego

| Componente | Responsabilidad |
|---|---|
| `service/JuegoPulsoVerticalService.java` | Hilo en segundo plano (`ScheduledExecutorService`) que gobierna la secuencia de luces: cuenta atrás, fase verde, reducción del 35%, fase naranja y bucle infinito |
| `api/JuegoController.java` | Endpoints REST de control de partida y gestión del ranking |
| `dao/RankingPulsoVerticalDAO.java` + `model/RankingPulsoVertical.java` | Persistencia de las marcas en `RANKING_PULSO_VERTICAL` |
| Pestaña "Pulso Vertical" en `dashboard.html` + `dashboard.js` | Cronómetro, botones de control, ranking y **simulador visual 18×11** que espeja la lógica del servicio en JavaScript |
| `css/estilos.css` | Estilos del cronómetro, ranking, modal y colores de los LEDs (rojo/verde/naranja) del simulador |

### Soporte de color en el hardware

El color por LED es una capacidad transversal, no exclusiva del minijuego. La comparten el minijego y el diseño de vías, así que el protocolo Java ↔ Python lo define en tres piezas:

- `LedService.java` define los colores de estado del panel: `COLOR_VERDE` (`00FF00`), `COLOR_ROJO` (`FF0000`) y `COLOR_NARANJA` (`FFA500`). Los roles de presa tienen su propia paleta en `PresaRuta.java`.
- El método `agregarLedsAlHardware()` **superpone** LEDs sin limpiar el resto del panel (comando daemon `agregar:`). Es lo que permite pintar de naranja sobre el verde en la fase de aviso del minijuego, y lo que permite a una vía multicolormandarse en varios viajes sin apagarse entre grupos.
- `leds.py` acepta el color en formato HEX (`RRGGBB`) como parámetro adicional tanto en el modo CLI como en el daemon, añade el comando `agregar` y el parámetro `limpiar` (para no borrar la escena previa).
 
> **Percepción del color:** el brillo del panel es un único valor (`CONFIGURACION_LED.brillo`) que se aplica por igual a los tres canales. En los WS2812B el verde tiene bastante más eficacia lumínica que el azul, así que ambos no se ven igual de intensos con el mismo brillo. Para igualarlos habría que aplicar una ganancia por canal antes de enviar el color al bus.

## 📄 Licencia

Copyleft © 2026 — Todos los derechos reservados al desarrollador.

**Autor:** Erin Brandan Vázquez Enes

**Nota:** Esta configuración está orientada a Raspberry Pi OS Bookworm (Debian 12), donde `NetworkManager` sustituye a `dhcpcd` como gestor de red por defecto.
