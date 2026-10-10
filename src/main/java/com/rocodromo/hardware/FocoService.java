/**
 * Sistema de Control de Rocódromo Inteligente - MoonBoard Smart
 * Copyleft © 2026. Todos los derechos reservados al desarrollador.
 */
package com.rocodromo.hardware;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Servicio de hardware que gestiona la comunicación con el script Python
 * {@code foco.py}, encargado del relé que corta o restablece el circuito del
 * foco real (GPIO 23).
 * <p>
 * Es el equivalente a {@link LedService} pero para el relé, y existe para no
 * mezclar el control de la tira WS2812B con el del foco. Opera en dos modos:
 * <ul>
 *   <li><b>Daemon</b> (preferente): proceso Python persistente que mantiene el
 *       pin una sola vez y recibe comandos por stdin. Es necesario para que el
 *       estado del relé se sostenga entre peticiones.</li>
 *   <li><b>CLI</b> (fallback): invoca {@code foco.py} como proceso separado en
 *       cada llamada.</li>
 * </ul>
 *
 * @author Erin Brandan Vazquez Enes
 * @version 1.0
 */
public class FocoService {

    private static final int TIMEOUT_SEGUNDOS = 15;
    private static final String PROP_SCRIPT_PATH = "rocodromo.foco.script.path";
    private static final int PIN_FOCO_GPIO_DEFECTO = 23;

    private final String rutaScript;
    private final int pinGpio;

    // Daemon
    private Process daemonProcess;
    private BufferedWriter daemonStdin;
    private volatile boolean daemonMode = false;
    private volatile boolean apagado = false;
    private final Object daemonLock = new Object();

    public FocoService() {
        this.rutaScript = resolverRutaScript();
        this.pinGpio = PIN_FOCO_GPIO_DEFECTO;
    }

    private String resolverRutaScript() {
        String prop = System.getProperty(PROP_SCRIPT_PATH);
        if (prop != null) {
            File f = new File(prop);
            if (f.isFile() && f.canRead()) return f.getAbsolutePath();
            System.err.println("⚠️ [FocoService] Propiedad '" + PROP_SCRIPT_PATH + "' apunta a un archivo inválido: " + prop);
        }

        Path cwd = Paths.get(System.getProperty("user.dir", "."));
        File rel = cwd.resolve("foco.py").toFile();
        if (rel.isFile() && rel.canRead()) return rel.getAbsolutePath();

        File abs = new File("/opt/rocodromo/foco.py");
        if (abs.isFile() && abs.canRead()) return abs.getAbsolutePath();

        System.err.println("⚠️ [FocoService] No se encontró foco.py en ninguna ruta. Se usará './foco.py' (fallará si el CWD es incorrecto).");
        return "./foco.py";
    }

    /**
     * Indica si el foco está actualmente apagado (relé energizado).
     */
    public boolean isApagado() {
        return apagado;
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

            try {
                ProcessBuilder pb = new ProcessBuilder(
                        "sudo", "-n", "python3", rutaScript, "daemon",
                        String.valueOf(pinGpio)
                );
                pb.directory(new File(System.getProperty("user.dir", ".")));
                pb.redirectErrorStream(true);

                System.out.println("🐍 [FocoService] Arrancando daemon: " + String.join(" ", pb.command()));
                daemonProcess = pb.start();
                daemonStdin = new BufferedWriter(new OutputStreamWriter(daemonProcess.getOutputStream()));

                Thread reader = new Thread(() -> {
                    try (BufferedReader r = new BufferedReader(new InputStreamReader(daemonProcess.getInputStream()))) {
                        String linea;
                        while ((linea = r.readLine()) != null) {
                            System.out.println("🐍 [Daemon Foco] " + linea);
                        }
                    } catch (IOException e) {
                        if (daemonMode) {
                            System.err.println("❌ [FocoService] Daemon desconectado: " + e.getMessage());
                            daemonMode = false;
                        }
                    }
                }, "foco-daemon-stdout");
                reader.setDaemon(true);
                reader.start();

                Thread.sleep(300);
                if (daemonProcess.isAlive()) {
                    daemonMode = true;
                    System.out.println("✅ [FocoService] Daemon Python del foco operativo.");
                    return true;
                } else {
                    System.err.println("❌ [FocoService] El daemon murió al arrancar.");
                    return false;
                }
            } catch (Exception e) {
                System.err.println("❌ [FocoService] Error al iniciar daemon: " + e.getMessage());
                return false;
            }
        }
    }

    /**
     * Detiene el daemon enviando el comando "salir" y esperando su terminación.
     * Al liberar el pin, el relé vuelve a reposo y el foco a su estado encendido.
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
                System.err.println("❌ [FocoService] Error escribiendo al daemon: " + e.getMessage());
                daemonMode = false;
                return false;
            }
        }
    }

    /**
     * Acciona el relé del foco. "apagar" corta el circuito (pin HIGH) y
     * "encender" lo restablece (pin LOW). Daemon si está disponible; si no, CLI.
     *
     * @param accion "apagar" o "encender"
     */
    public boolean controlarFoco(String accion) {
        if (!"apagar".equals(accion) && !"encender".equals(accion)) {
            System.err.println("⚠️ [FocoService] Acción de foco no válida: " + accion);
            return false;
        }

        boolean exito = enviarComandoAlDaemon(accion);
        if (!exito) {
            exito = ejecutarScriptPython(accion);
        }

        if (exito) {
            apagado = "apagar".equals(accion);
        }
        return exito;
    }

    // ---------------------------------------------------------------
    //  Fallback por CLI (proceso independiente por llamada)
    // ---------------------------------------------------------------

    private boolean ejecutarScriptPython(String accion) {
        try {
            List<String> comandos = new ArrayList<>();
            comandos.add("sudo");
            comandos.add("-n");
            comandos.add("python3");
            comandos.add(rutaScript);
            comandos.add(accion);
            comandos.add(String.valueOf(pinGpio));

            ProcessBuilder pb = new ProcessBuilder(comandos);

            System.out.println("⚠️ [FocoService] Usando fallback CLI (sin daemon).");
            System.out.println("🐍 [FocoService] Ejecutando: " + String.join(" ", pb.command()));

            pb.directory(new File(System.getProperty("user.dir", ".")));
            pb.redirectErrorStream(true);
            Process proceso = pb.start();

            Thread drainer = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(proceso.getInputStream()))) {
                    String linea;
                    while ((linea = reader.readLine()) != null) {
                        System.out.println("🐍 [Script Foco] " + linea);
                    }
                } catch (IOException e) {
                    System.err.println("❌ [FocoService] Error leyendo salida de Python: " + e.getMessage());
                }
            }, "python-foco-stdout-drainer");
            drainer.setDaemon(true);
            drainer.start();

            boolean rompeInmediato = proceso.waitFor(50, TimeUnit.MILLISECONDS);
            if (rompeInmediato && proceso.exitValue() != 0) {
                System.err.println("❌ [FocoService] El script falló nada más arrancar (Código: " + proceso.exitValue() + ").");
                return false;
            }

            return true;

        } catch (IOException e) {
            String msg = e.getMessage();
            if (msg != null && msg.contains("error=2")) {
                System.err.println("❌ [FocoService] 'python3' o el script no se encuentran en la ruta. " +
                        "Verifica que python3 esté instalado y que foco.py exista en: " + rutaScript);
            } else if (msg != null && msg.contains("error=13")) {
                System.err.println("❌ [FocoService] Permiso denegado. Verifica que el script tenga permisos de ejecución: chmod +x " + rutaScript);
            } else {
                System.err.println("❌ [FocoService] Error de E/S al ejecutar Python: " + msg);
            }
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("❌ [FocoService] El hilo fue interrumpido durante la ejecución de Python.");
            return false;
        }
    }
}
