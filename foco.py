#!/usr/bin/env python3
"""
Script de hardware para el relé del foco real (GPIO 23).

Gestiona exclusivamente el módulo relé de 1 canal (5 V con optoacoplador) que
corta o restablece el circuito del foco. Está separado de leds.py para no
mezclar el control de la tira WS2812B con el del relé.

Admite dos modos, igual que leds.py:

  - Daemon (preferente): proceso persistente que inicializa el pin una sola vez
    y atiende comandos por stdin. Es imprescindible para que el estado del relé
    se sostenga: si el proceso terminase, el GPIO se reiniciaría y el foco
    volvería a su estado de reposo (encendido).
  - CLI (fallback): invocación puntual por argumentos. Útil para pruebas
    manuales, pero el estado no se mantiene al terminar el proceso.

@author Erin Brandan Vazquez Enes
@version 1.0
"""

import sys
import os

FORCE_SIMULATION = os.environ.get("FORCE_SIMULATION", "").lower() in ("1", "true", "yes")

# GPIO que acciona el módulo relé de 1 canal (5 V con optoacoplador) del foco real.
# Cableado por contacto NC: con el relé en reposo el circuito está cerrado y el
# foco permanece encendido; energizar el relé (HIGH) abre NC y lo apaga.
PIN_FOCO_GPIO = 23

try:
    import board
    import digitalio
    GPIO_DISPONIBLE = True
except ImportError:
    GPIO_DISPONIBLE = False

# Pin del relé, compartido entre el daemon y las llamadas de control.
_pin_foco = None


def _advertencia_dependencias():
    if not GPIO_DISPONIBLE and not FORCE_SIMULATION:
        print("⚠️  [Hardware] Las librerías GPIO no están instaladas.", file=sys.stderr)
        print("⚠️  [Hardware] Ejecuta: pip install -r requirements.txt", file=sys.stderr)
        print("⚠️  [Hardware] O define FORCE_SIMULATION=1 para simular en desarrollo.", file=sys.stderr)


def controlar_foco(accion, pin_gpio=PIN_FOCO_GPIO):
    """Acciona el relé del foco real por el GPIO indicado (contacto NC).

    Con el relé en reposo (pin LOW o sin inicializar, como al arrancar la
    Raspberry Pi) el contacto NC está cerrado y el foco queda encendido.
    'apagar' pone el pin a HIGH: la bobina energiza el relé, NC se abre y
    el circuito del foco queda cortado. 'encender' devuelve el pin a LOW,
    el relé se desenergiza y NC vuelve a cerrarse.
    """
    global _pin_foco
    encender = accion == "encender"
    estado = "encendido" if encender else "apagado"
    print(f"💡 [Foco] Accionando relé en GPIO {pin_gpio}: foco {estado}...", flush=True)

    if GPIO_DISPONIBLE:
        if _pin_foco is None:
            _pin_foco = digitalio.DigitalInOut(getattr(board, f"D{pin_gpio}"))
        _pin_foco.direction = digitalio.Direction.OUTPUT
        _pin_foco.value = 0 if encender else 1
        print(f"✅ [Foco] Señal enviada al relé. Foco {estado}.", flush=True)
    else:
        print("💻 [Modo Simulación] Ejecutando en entorno de desarrollo sin GPIO.", flush=True)
        circuito = "cerraría" if encender else "abriría"
        print(f"🔮 RENDER VIRTUAL FOCO: el relé del GPIO {pin_gpio} {circuito} el circuito "
              f"y el foco quedaría {estado}.", flush=True)


def modo_daemon(pin_gpio=PIN_FOCO_GPIO):
    """Mantiene el proceso vivo para sostener el estado del relé.

    Inicializa el pin en reposo (foco encendido) y escucha por stdin los
    comandos 'apagar', 'encender' y 'salir'. Mientras el proceso siga vivo el
    relé conserva su estado aunque no llegue ningún comando nuevo.
    """
    global _pin_foco

    print(f"🤖 [Daemon Foco] Iniciando modo daemon (GPIO: {pin_gpio})")

    if GPIO_DISPONIBLE:
        _pin_foco = digitalio.DigitalInOut(getattr(board, f"D{pin_gpio}"))
        _pin_foco.direction = digitalio.Direction.OUTPUT
        _pin_foco.value = 0  # Reposo: relé desenergizado, NC cerrado, foco encendido
    else:
        print("💻 [Daemon Foco] Modo simulación (sin GPIO)")

    print("OK:daemon:iniciado", flush=True)

    for raw in sys.stdin:
        linea = raw.strip()
        if not linea:
            continue

        if linea == "salir":
            print("OK:salir", flush=True)
            break

        elif linea in ("apagar", "encender"):
            controlar_foco(linea, pin_gpio)
            print(f"OK:foco:{linea}", flush=True)

        else:
            print(f"ERR:comando_desconocido:{linea}", flush=True)


if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("❌ Error: Faltan argumentos de ejecución.", file=sys.stderr)
        sys.exit(1)

    _advertencia_dependencias()

    comando = sys.argv[1].lower()

    if comando == "daemon":
        pin = int(sys.argv[2]) if len(sys.argv) > 2 else PIN_FOCO_GPIO
        modo_daemon(pin)

    elif comando in ("apagar", "encender"):
        pin = int(sys.argv[2]) if len(sys.argv) > 2 else PIN_FOCO_GPIO
        controlar_foco(comando, pin)

    else:
        print(f"❌ Error: Comando '{comando}' no reconocido. Usa: daemon, apagar o encender.", file=sys.stderr)
        sys.exit(1)
