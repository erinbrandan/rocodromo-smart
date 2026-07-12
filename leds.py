#!/usr/bin/env python3
"""
Script de hardware para la tira de LEDs WS2812B (NeoPixel).
Recibe las órdenes del backend Java a través de argumentos de consola.

@author Erin Brandan Vazquez Enes
@version 1.2
"""

import sys
import os

FORCE_SIMULATION = os.environ.get("FORCE_SIMULATION", "").lower() in ("1", "true", "yes")

try:
    import board
    import neopixel
    GPIO_DISPONIBLE = True
except ImportError:
    GPIO_DISPONIBLE = False


def _advertencia_dependencias():
    if not GPIO_DISPONIBLE and not FORCE_SIMULATION:
        print("⚠️  [Hardware] Las librerías GPIO no están instaladas.", file=sys.stderr)
        print("⚠️  [Hardware] Ejecuta: pip install -r requirements.txt", file=sys.stderr)
        print("⚠️  [Hardware] O define FORCE_SIMULATION=1 para simular en desarrollo.", file=sys.stderr)


def encender_ruta(indices_leds, brillo=50, pin_gpio=18, total_leds=150):
    print(f"🤖 [Python Hardware] Procesando orden de iluminación...")
    print(f"-> Parámetros: Total LEDs: {total_leds} | Pin GPIO: {pin_gpio} | Brillo: {brillo}")
    print(f"-> LEDs a encender: {indices_leds}")

    if GPIO_DISPONIBLE:
        pin_placa = getattr(board, f"D{pin_gpio}")
        tira = neopixel.NeoPixel(pin_placa, total_leds, brightness=brillo / 255.0, auto_write=False)
        tira.fill((0, 0, 0))
        for indice in indices_leds:
            if 0 <= indice < total_leds:
                tira[indice] = (0, 255, 150)
        tira.show()
        print("💡 [Hardware] Tira de LEDs física actualizada correctamente.")
    else:
        print("💻 [Modo Simulación] Ejecutando en entorno de desarrollo sin GPIO.")
        print(f"🔮 RENDER VIRTUAL PANEL: Los LEDs {indices_leds} brillarían ahora en la pared "
              f"con brillo {brillo} en el PIN {pin_gpio}.")


def apagar_tira(pin_gpio=18, total_leds=150):
    print(f"🤖 [Python Hardware] Apagando panel por completo (PIN: {pin_gpio} | Total: {total_leds})...")
    if GPIO_DISPONIBLE:
        pin_placa = getattr(board, f"D{pin_gpio}")
        tira = neopixel.NeoPixel(pin_placa, total_leds, auto_write=True)
        tira.fill((0, 0, 0))
    print("⬛ Panel completamente a oscuras.")


if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("❌ Error: Faltan argumentos de ejecución.", file=sys.stderr)
        sys.exit(1)

    _advertencia_dependencias()

    comando = sys.argv[1].lower()

    if comando == "encender":
        if len(sys.argv) < 6:
            print("❌ Error: El comando 'encender' ahora requiere la configuración de hardware completa.",
                  file=sys.stderr)
            print("Uso: python3 leds.py encender [lista_leds] [total_leds] [pin_gpio] [brillo]", file=sys.stderr)
            sys.exit(1)

        try:
            lista_enteros = [int(x) for x in sys.argv[2].split(",")]
            total_leds_dinamico = int(sys.argv[3])
            pin_gpio_dinamico = int(sys.argv[4])
            brillo_dinamico = int(sys.argv[5])

            encender_ruta(
                indices_leds=lista_enteros,
                brillo=brillo_dinamico,
                pin_gpio=pin_gpio_dinamico,
                total_leds=total_leds_dinamico
            )
        except ValueError:
            print("❌ Error: Los parámetros de hardware o la lista de LEDs deben ser números enteros válidos.",
                  file=sys.stderr)
            sys.exit(1)

    elif comando == "apagar":
        if len(sys.argv) < 4:
            apagar_tira()
        else:
            try:
                total_leds_dinamico = int(sys.argv[2])
                pin_gpio_dinamico = int(sys.argv[3])
                apagar_tira(pin_gpio=pin_gpio_dinamico, total_leds=total_leds_dinamico)
            except ValueError:
                print("❌ Error: Los parámetros de apagado deben ser números enteros válidos.", file=sys.stderr)
                sys.exit(1)
    else:
        print(f"❌ Error: Comando '{comando}' no reconocido por el hardware.", file=sys.stderr)
        sys.exit(1)