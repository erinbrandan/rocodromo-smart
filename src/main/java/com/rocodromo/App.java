/**
 * Sistema de Control de Rocódromo Inteligente - MoonBoard Smart
 * Copyleft © 2026. Todos los derechos reservados al desarrollador.
 */
package com.rocodromo;

import com.rocodromo.api.HardwareController;
import com.rocodromo.api.JuegoController;
import com.rocodromo.api.RutaController;
import com.rocodromo.api.UsuarioController;
import com.rocodromo.db.DatabaseConfig;
import io.javalin.Javalin;
import io.javalin.http.staticfiles.Location;
import java.util.Map;

/**
 * Clase principal del ecosistema Rocódromo Smart.
 * Inicializa la base de datos local y arranca el servidor Javalin
 * para exponer la API REST y los recursos estáticos del frontend.
 *
 * @author Erin Brandan Vazquez Enes
 * @version 1.5
 */
public class App {

    public static void main(String[] args) {
        System.out.println("🧗 Iniciando servidor del Rocódromo Inteligente...");

        // 1. Inicializar la base de datos local (crea el archivo y las tablas si no existen)
        DatabaseConfig.inicializarTablas();

        // 2. Crear y configurar el servidor Javalin 6
        Javalin app = Javalin.create(config -> {

            // Enrutamiento de archivos estáticos del frontend
            config.staticFiles.add(staticFiles -> {
                staticFiles.hostedPath = "/";                   // Ruta raíz (http://localhost:8080/)
                staticFiles.directory = "public";               // Carpeta dentro de target/classes/resources
                staticFiles.location = Location.CLASSPATH;      // Resolución vía classpath del JAR
            });

            // Habilitar CORS (Cross-Origin Resource Sharing) por seguridad y pruebas locales
            config.bundledPlugins.enableCors(cors -> {
                cors.addRule(it -> it.anyHost());
            });
        });

        // --- ENRUTAMIENTO Y REDIRECCIONES DE INTERFAZ ---

        // Redirección raíz hacia la página de login
        app.get("/", ctx -> ctx.redirect("/login.html"));


        // --- DEFINICIÓN DE ENDPOINTS DE LA API REST ---

        // Endpoint de diagnóstico del estado del backend
        app.get("/api/estado", ctx -> {
            ctx.status(200);
            ctx.result("🟢 Backend del Rocódromo en línea de forma local.");
        });

        // Rutas del catálogo de escalada (Gestión de Proyectos, Encadenadas y Creador Visual)
        app.get("/api/rutas", RutaController::listarRutas);
        app.post("/api/rutas/crear", RutaController::guardarRuta);
        app.post("/api/rutas/{id}/seleccionar", RutaController::seleccionarRuta);
        app.delete("/api/rutas/{id}", RutaController::eliminarRuta);
        app.post("/api/rutas/{id}/agregar", RutaController::agregarRutaAHistorial);
        // Endpoint para el botón ¡Encadenada! (Cambio de estado del Proyecto)
        app.put("/api/rutas/{id}/estado", RutaController::actualizarEstado);

        // Rutas de control manual y directo del Hardware (Tira de LEDS)
        app.post("/api/hardware/apagar", HardwareController::apagarPanel);
        app.post("/api/hardware/encender-manual", HardwareController::encenderManual);

        // Relé del foco real (GPIO 23)
        app.get("/api/hardware/foco", HardwareController::estadoFoco);
        app.post("/api/hardware/foco", HardwareController::controlarFoco);

        // Control fino de LEDS individuales (feedback en tiempo real desde el creador de vías)
        app.post("/api/hardware/encender-led", HardwareController::encenderUnicoLed);
        app.post("/api/hardware/agregar-led", HardwareController::agregarLed);

        // Gestión de Usuarios y Autenticación real con SQLite
        app.post("/api/usuarios/registro", UsuarioController::registrar);
        app.post("/api/usuarios/login", UsuarioController::login);

        // Minijuego "Pulso Vertical" (control de partida y ranking)
        app.post("/api/juego/pulso-vertical/iniciar", JuegoController::iniciarJuego);
        app.post("/api/juego/pulso-vertical/pausar", JuegoController::pausarJuego);
        app.post("/api/juego/pulso-vertical/reanudar", JuegoController::reanudarJuego);
        app.post("/api/juego/pulso-vertical/finalizar", JuegoController::finalizarJuego);
        app.get("/api/juego/pulso-vertical/ranking", JuegoController::obtenerRanking);
        app.post("/api/juego/pulso-vertical/ranking", JuegoController::guardarMarca);


        // --- CONTROL GLOBAL DE EXCEPCIONES CRÍTICAS ---

        // Control global de excepciones no capturadas: devuelve JSON estructurado en vez de HTML por defecto
        app.exception(Exception.class, (e, ctx) -> {
            System.err.println("🚨 [App Exception] Error interno no controlado: " + e.getMessage());
            e.printStackTrace();
            ctx.status(500);
            ctx.json(Map.of(
                    "status", "error",
                    "message", "Ocurrió un error inesperado en el servidor del rocódromo."
            ));
        });


        // 3. Arrancar el servidor en el puerto 8080
        app.start(8080);

        // 4. Inicializar el daemon Python para control en tiempo real de los LEDS
        HardwareController.iniciarHardware();

        // 5. Registrar parada ordenada del daemon al cerrar la aplicación
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("🛑 Apagando servidor...");
            HardwareController.detenerHardware();
            app.stop();
        }));

        System.out.println("🌐 Servidor Web HTTP operativo de forma local.");
        System.out.println("🔗 Abre en tu navegador: http://localhost:8080");
    }
}