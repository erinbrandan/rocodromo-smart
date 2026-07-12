/**
 * Sistema de Control de Rocódromo Inteligente - MoonBoard Smart
 * Copyleft © 2026. Todos los derechos reservados al desarrollador.
 */
package com.rocodromo.hardware;

import com.rocodromo.dao.ConfiguracionLedDAO;
import com.rocodromo.model.ConfiguracionLed;

import java.io.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Servicio de hardware que gestiona la comunicación con el script Python
 * de control de la tira de LEDs WS2812B.
 *
 * @author Erin Brandan Vazquez Enes
 * @version 1.0
 */
public class LedService {

    private final ConfiguracionLedDAO configDAO;
    private final String rutaScript;

    private static final int TIMEOUT_SEGUNDOS = 15;
    private static final String PROP_SCRIPT_PATH = "rocodromo.script.path";

    public LedService() {
        this.configDAO = new ConfiguracionLedDAO();
        this.rutaScript = resolverRutaScript();
    }

    /**
     * Resuelve la ruta al script leds.py buscando en: propiedad del sistema,
     * directorio de trabajo actual y ruta absoluta de despliegue.
     */
    private String resolverRutaScript() {
        String prop = System.getProperty(PROP_SCRIPT_PATH);
        if (prop != null) {
            File f = new File(prop);
            if (f.isFile() && f.canRead()) return f.getAbsolutePath();
            System.err.println("⚠️ [LedService] Propiedad '" + PROP_SCRIPT_PATH + "' apunta a un archivo inválido: " + prop);
        }

        Path cwd = Paths.get(System.getProperty("user.dir", "."));
        File rel = cwd.resolve("leds.py").toFile();
        if (rel.isFile() && rel.canRead()) return rel.getAbsolutePath();

        File abs = new File("/opt/rocodromo/leds.py");
        if (abs.isFile() && abs.canRead()) return abs.getAbsolutePath();

        System.err.println("⚠️ [LedService] No se encontró leds.py en ninguna ruta. Se usará './leds.py' (fallará si el CWD es incorrecto).");
        return "./leds.py";
    }

    /**
     * Envía la lista de LEDs al script Python para encender la ruta en el panel físico.
     *
     * @param indicesLeds Lista de índices de LEDs a iluminar.
     * @return true si el script se invocó correctamente; false en caso contrario.
     */
    public boolean enviarRutaAlHardware(List<Integer> indicesLeds) {
        if (indicesLeds == null || indicesLeds.isEmpty()) {
            System.out.println("⚠️ [LedService] Intento de encendido abortado: La lista de LEDs está vacía.");
            return false;
        }

        String ledsFormateados = indicesLeds.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(","));

        ConfiguracionLed config = configDAO.obtenerConfiguracion();

        int totalLeds = (config != null) ? config.getTotalLeds() : 150;
        int pinGpio = (config != null) ? config.getPinGpio() : 18;
        int brillo = (config != null) ? config.getBrillo() : 50;

        System.out.println("🚀 [LedService] Invocando leds.py para " + indicesLeds.size() + " LEDs...");
        System.out.println("⚙️ [Config Activa BBDD] GPIO: " + pinGpio + " | Total LEDs: " + totalLeds + " | Brillo: " + brillo);

        return ejecutarScriptPython("encender", ledsFormateados, totalLeds, pinGpio, brillo);
    }

    /** Apaga todos los LEDs del panel invocando el script Python con brillo 0. */
    public boolean apagarPanel() {
        System.out.println("🚀 [LedService] Enviando señal de apagado general al panel...");

        ConfiguracionLed config = configDAO.obtenerConfiguracion();
        int totalLeds = (config != null) ? config.getTotalLeds() : 150;
        int pinGpio = (config != null) ? config.getPinGpio() : 18;

        return ejecutarScriptPython("apagar", "", totalLeds, pinGpio, 0);
    }

    private boolean ejecutarScriptPython(String comando, String argumentos, int totalLeds, int pinGpio, int brillo) {
        try {
            ProcessBuilder pb;

            if (argumentos.isEmpty()) {
                pb = new ProcessBuilder("sudo", "-n", "python3", rutaScript, comando,
                        String.valueOf(totalLeds), String.valueOf(pinGpio));
            } else {
                pb = new ProcessBuilder("sudo", "-n", "python3", rutaScript, comando, argumentos,
                        String.valueOf(totalLeds), String.valueOf(pinGpio), String.valueOf(brillo));
            }

            System.out.println("🐍 [LedService] Ejecutando: " + String.join(" ", pb.command()));

            pb.directory(new File(System.getProperty("user.dir", ".")));
            pb.redirectErrorStream(true);
            Process proceso = pb.start();

            Thread drainer = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(proceso.getInputStream()))) {
                    String linea;
                    while ((linea = reader.readLine()) != null) {
                        System.out.println("🐍 [Script Python] " + linea);
                    }
                } catch (IOException e) {
                    System.err.println("❌ [LedService] Error leyendo salida de Python: " + e.getMessage());
                }
            }, "python-stdout-drainer");
            drainer.setDaemon(true);
            drainer.start();

            boolean rompeInmediato = proceso.waitFor(50, TimeUnit.MILLISECONDS);
            if (rompeInmediato && proceso.exitValue() != 0) {
                System.err.println("❌ [LedService] El script falló nada más arrancar (Código: " + proceso.exitValue() + ").");
                return false;
            }

            // Si el script no falló en los primeros 50ms, se asume que corre en background
            // y se libera la petición web para no bloquear el servidor
            return true;

        } catch (IOException e) {
            String msg = e.getMessage();
            if (msg != null && msg.contains("error=2")) {
                System.err.println("❌ [LedService] 'python3' o el script no se encuentran en la ruta. " +
                        "Verifica que python3 esté instalado y que leds.py exista en: " + rutaScript);
            } else if (msg != null && msg.contains("error=13")) {
                System.err.println("❌ [LedService] Permiso denegado. Verifica que el script tenga permisos de ejecución: chmod +x " + rutaScript);
            } else {
                System.err.println("❌ [LedService] Error de E/S al ejecutar Python: " + msg);
            }
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("❌ [LedService] El hilo fue interrumpido durante la ejecución de Python.");
            return false;
        }
    }
}