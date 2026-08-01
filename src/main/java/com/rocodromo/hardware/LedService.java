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
 * <p>
 * Opera en dos modos:
 * <ul>
 *   <li><b>Daemon</b> (preferente): proceso Python persistente que recibe
 *       comandos por stdin. Inicializa NeoPixel una sola vez.</li>
 *   <li><b>CLI</b> (fallback): invoca {@code leds.py} como proceso separado
 *       en cada llamada.</li>
 * </ul>
 *
 * @author Erin Brandan Vazquez Enes
 * @version 2.0
 */
public class LedService {

    private final ConfiguracionLedDAO configDAO;
    private final String rutaScript;

    private static final int TIMEOUT_SEGUNDOS = 15;
    private static final String PROP_SCRIPT_PATH = "rocodromo.script.path";

    // Daemon
    private Process daemonProcess;
    private BufferedWriter daemonStdin;
    private volatile boolean daemonMode = false;
    private final Object daemonLock = new Object();

    public LedService() {
        this.configDAO = new ConfiguracionLedDAO();
        this.rutaScript = resolverRutaScript();
    }

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

    // ---------------------------------------------------------------
    //  Gestión del daemon
    // ---------------------------------------------------------------

    /**
     * Inicia el proceso Python en modo daemon. No hace nada si ya está activo.
     */
    public boolean iniciarDaemon() {
        synchronized (daemonLock) {
            if (daemonMode) return true;

            ConfiguracionLed config = configDAO.obtenerConfiguracion();
            int totalLeds = (config != null) ? config.getTotalLeds() : 150;
            int pinGpio = (config != null) ? config.getPinGpio() : 18;
            int brillo = (config != null) ? config.getBrillo() : 50;

            try {
                ProcessBuilder pb = new ProcessBuilder(
                        "sudo", "-n", "python3", rutaScript, "daemon",
                        String.valueOf(totalLeds),
                        String.valueOf(pinGpio),
                        String.valueOf(brillo)
                );
                pb.directory(new File(System.getProperty("user.dir", ".")));
                pb.redirectErrorStream(true);

                System.out.println("🐍 [LedService] Arrancando daemon: " + String.join(" ", pb.command()));
                daemonProcess = pb.start();
                daemonStdin = new BufferedWriter(new OutputStreamWriter(daemonProcess.getOutputStream()));

                Thread reader = new Thread(() -> {
                    try (BufferedReader r = new BufferedReader(new InputStreamReader(daemonProcess.getInputStream()))) {
                        String linea;
                        while ((linea = r.readLine()) != null) {
                            System.out.println("🐍 [Daemon] " + linea);
                        }
                    } catch (IOException e) {
                        if (daemonMode) {
                            System.err.println("❌ [LedService] Daemon desconectado: " + e.getMessage());
                            daemonMode = false;
                        }
                    }
                }, "daemon-stdout");
                reader.setDaemon(true);
                reader.start();

                Thread.sleep(300);
                if (daemonProcess.isAlive()) {
                    daemonMode = true;
                    System.out.println("✅ [LedService] Daemon Python operativo.");
                    return true;
                } else {
                    System.err.println("❌ [LedService] El daemon murió al arrancar.");
                    return false;
                }
            } catch (Exception e) {
                System.err.println("❌ [LedService] Error al iniciar daemon: " + e.getMessage());
                return false;
            }
        }
    }

    /**
     * Detiene el daemon enviando el comando "salir" y esperando su terminación.
     */
    public void detenerDaemon() {
        synchronized (daemonLock) {
            daemonMode = false;
            if (daemonStdin != null) {
                try {
                    daemonStdin.write("salir");
                    daemonStdin.newLine();
                    daemonStdin.flush();
                } catch (IOException e) {
                    // ignorar
                }
            }
            if (daemonProcess != null) {
                try {
                    daemonProcess.waitFor(2, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    daemonProcess.destroyForcibly();
                }
            }
        }
    }

    /**
     * Envía un comando de texto al daemon por stdin. Si el daemon no está
     * activo, intenta arrancarlo.
     */
    private boolean enviarComandoAlDaemon(String comando) {
        synchronized (daemonLock) {
            if (!daemonMode) {
                iniciarDaemon();
            }
            if (!daemonMode || daemonStdin == null) return false;

            try {
                daemonStdin.write(comando);
                daemonStdin.newLine();
                daemonStdin.flush();
                return true;
            } catch (IOException e) {
                System.err.println("❌ [LedService] Error escribiendo al daemon: " + e.getMessage());
                daemonMode = false;
                return false;
            }
        }
    }

    // ---------------------------------------------------------------
    //  Métodos públicos de control de LEDs
    // ---------------------------------------------------------------

    /**
     * Enciende un conjunto de LEDs (limpia el panel y enciende solo esos).
     * Usa el daemon si está disponible; si no, invoca el script por CLI.
     */
    public boolean enviarRutaAlHardware(List<Integer> indicesLeds) {
        if (indicesLeds == null || indicesLeds.isEmpty()) {
            System.out.println("⚠️ [LedService] Intento de encendido abortado: La lista de LEDs está vacía.");
            return false;
        }

        List<Integer> indicesCeroBase = indicesLeds.stream()
                .map(i -> i - 1)
                .toList();

        // Daemon
        String comando = "encender:" + indicesCeroBase.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(","));
        if (enviarComandoAlDaemon(comando)) {
            return true;
        }

        // Fallback CLI
        return ejecutarPorCLI(indicesCeroBase);
    }

    /**
     * Enciende un único LED (limpia el panel primero).
     * Ideal para feedback en tiempo real al pulsar presas desde el creador.
     */
    public boolean encenderLedUnico(int indice) {
        return enviarRutaAlHardware(List.of(indice));
    }

    /**
     * Apaga todos los LEDs.
     */
    public boolean apagarPanel() {
        String comando = "apagar";
        if (enviarComandoAlDaemon(comando)) {
            return true;
        }
        return apagarPorCLI();
    }

    // ---------------------------------------------------------------
    //  Fallback por CLI (proceso independiente por llamada)
    // ---------------------------------------------------------------

    private boolean ejecutarPorCLI(List<Integer> indicesCeroBase) {
        String argumentos = indicesCeroBase.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(","));

        ConfiguracionLed config = configDAO.obtenerConfiguracion();
        int totalLeds = (config != null) ? config.getTotalLeds() : 150;
        int pinGpio = (config != null) ? config.getPinGpio() : 18;
        int brillo = (config != null) ? config.getBrillo() : 50;

        System.out.println("⚠️ [LedService] Usando fallback CLI (sin daemon).");
        return ejecutarScriptPython("encender", argumentos, totalLeds, pinGpio, brillo);
    }

    private boolean apagarPorCLI() {
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
