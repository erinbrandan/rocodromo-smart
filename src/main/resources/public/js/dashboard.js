/**
 * Sistema de Control de Rocódromo Inteligente - MoonBoard Smart
 * Copyleft © 2026. Todos los derechos reservados al desarrollador.
 */
document.addEventListener("DOMContentLoaded", () => {
    // PROTECCIÓN DE RUTA: Si no hay usuario guardado, patada y al login
    const sesionGuardada = localStorage.getItem("sesion_usuario");
    if (!sesionGuardada) {
        window.location.href = "/login.html";
        return;
    }

    const usuario = JSON.parse(sesionGuardada);

    // --- VARIABLES DE ESTADO LOCAL FRONTEND ---
    let modoActual = "entrenar";       // "entrenar", "crear" o "pulso"
    let filtroEstado = "proyecto";     // "proyecto", "encadenada" o "comunidad"
    let rutaSeleccionadaId = null;     // Guarda la ID de la ruta activa en el panel

    // Caché del catálogo comunitario completo. Los filtros de nombre y grado se
    // aplican en memoria sobre esta copia, así que escribir en el buscador no
    // vuelve a golpear la API.
    let catalogoComunidad = [];

    // Escala de grados ordenada de menor a mayor dificultad. El desplegable
    // filtra por grado MÍNIMO: elegir 6a muestra 6a, 6a+, 6b, 6b+ y todo lo
    // más difícil. Es la misma escala que ofrece el formulario de creación.
    const ESCALA_GRADOS = ["5a", "5a+", "5b", "5b+", "5c", "5c+", "6a", "6a+", "6b", "6b+",
                           "6c", "6c+", "7a", "7a+", "7b", "7b+", "7c", "7c+", "8a", "8a+",
                           "8b", "8b+", "8c"];

    // Diseño en construcción: id de LED -> rol de la presa.
    // Un Map evita duplicados y garantiza que cada presa tiene un único rol.
    let rolesPorPresa = new Map();
    let temporizadorPulsacion = null;  // Long press para deseleccionar una presa
    let instantePulsacionLarga = 0;    // Momento del último long press (guardia anticlic)

    // --- GEOMETRÍA DEL PANEL ---
    // Grid 18x11 = 198 presas. La numeración crece desde la esquina inferior
    // izquierda: la fila 1 es la de abajo y el LED 1 su presa más a la izquierda.
    const PANEL_FILAS = 18;
    const PANEL_COLS = 11;
    const PANEL_TOTAL = PANEL_FILAS * PANEL_COLS;   // 198

    // El cable es una sola cadena serpenteada por el muro, no 18 filas sueltas:
    // las filas impares avanzan de izquierda a derecha y las pares al revés, de
    // forma que el LED siguiente siempre queda justo encima del anterior
    // (encima de la 11 está la 12, encima de la 22 está la 23, y así sucesivamente).
    // 'columna' es 0 para la presa más a la izquierda de la fila y 10 para la de
    // la derecha, siempre en coordenadas de pantalla.
    function ledDePosicion(fila, columna) {
        const base = (fila - 1) * PANEL_COLS;
        const dentro = fila % 2 === 1 ? columna : PANEL_COLS - 1 - columna;
        return base + dentro + 1;
    }

    function posicionDeLed(led) {
        const fila = Math.floor((led - 1) / PANEL_COLS) + 1;
        const dentro = (led - 1) % PANEL_COLS;
        const columna = fila % 2 === 1 ? dentro : PANEL_COLS - 1 - dentro;
        return { fila, columna };
    }

    // Estado del minijuego Pulso Vertical
    let cronoPulsoIntervalo = null;    // Intervalo del cronómetro
    let cronoPulsoAcumuladoMs = 0;     // Tiempo acumulado (ms) antes de pausas
    let cronoPulsoIniciado = false;    // ¿El juego está iniciado?
    let pulsoPausado = false;          // ¿El cronómetro está en pausa?

    // --- ELEMENTOS DEL DOM ---
    const nombreUsuarioHeader = document.getElementById("nombre-usuario-header");
    const btnLogout = document.getElementById("btn-logout");
    const btnFoco = document.getElementById("btn-foco");
    const badgeConexion = document.getElementById("badge-conexion");

    // Botones de Hardware
    const btnApagar = document.getElementById("btn-apagar");
    const btnTest = document.getElementById("btn-test");

    // Contenedores Estructurales
    const listaRutasContenedor = document.getElementById("lista-rutas");
    const matrizPresasContenedor = document.getElementById("matriz-presas");

    // Plantilla de Vías (HTML Template)
    const plantillaRuta = document.getElementById("plantilla-item-ruta");

    // Control de Pestañas (Tabs de Navegación)
    const tabEntrenar = document.getElementById("tab-entrenar");
    const tabCrear = document.getElementById("tab-crear");
    const tabPulso = document.getElementById("tab-pulso");
    const seccionEntrenar = document.getElementById("seccion-entrenar");
    const seccionCrear = document.getElementById("seccion-crear");
    const seccionPulso = document.getElementById("seccion-pulso");
    const panelVisual = document.getElementById("panel-visual");

    // Filtros de Catálogo (Sub-pestañas)
    const filtroProyectos = document.getElementById("filtro-proyectos");
    const filtroEncadenadas = document.getElementById("filtro-encadenadas");
    const filtroComunidad = document.getElementById("filtro-comunidad");

    // Filtros avanzados del catálogo comunitario (nombre + grado mínimo)
    const contFiltrosComunidad = document.getElementById("filtros-comunidad");
    const inputBuscarComunidad = document.getElementById("buscar-nombre-comunidad");
    const selectGradoComunidad = document.getElementById("filtrar-grado-comunidad");

    // Botón de Acción Especial de Encadenar
    const contenedorAccionVia = document.getElementById("contenedor-accion-via");
    const btnCompletarVia = document.getElementById("btn-completar-via");

    // Formulario Constructor (MoonBoard Builder)
    const formCrearVia = document.getElementById("form-crear-via");
    const txtNombreVia = document.getElementById("crear-nombre");
    const selGradoVia = document.getElementById("crear-grado");
    const contadorPresasBadge = document.getElementById("contador-presas");
    const contadorInicioBadge = document.getElementById("contador-inicio");
    const contadorIntermediaBadge = document.getElementById("contador-intermedia");
    const contadorTopBadge = document.getElementById("contador-top");
    const btnGuardarVia = document.getElementById("btn-guardar-via");
    const btnLimpiarCreador = document.getElementById("btn-limpiar-creador");

    // Textos Dinámicos de la Matriz
    const tituloMatriz = document.getElementById("titulo-matriz");
    const subtituloMatriz = document.getElementById("subtitulo-matriz");

    // Elementos del minijuego Pulso Vertical
    const tarjetaPulsoInicio = document.getElementById("tarjeta-pulso-inicio");
    const tarjetaPulsoJuego = document.getElementById("tarjeta-pulso-juego");
    const btnPulsoEmpezar = document.getElementById("btn-pulso-empezar");
    const btnPulsoIniciar = document.getElementById("btn-pulso-iniciar");
    const btnPulsoPausar = document.getElementById("btn-pulso-pausar");
    const btnPulsoFinalizar = document.getElementById("btn-pulso-finalizar");
    const cronometroPulso = document.getElementById("cronometro-pulso");
    const tablaRanking = document.getElementById("tabla-ranking");
    const modalPulsoMarca = document.getElementById("modal-pulso-marca");
    const modalPulsoTiempo = document.getElementById("modal-pulso-tiempo");
    const inputPulsoNombre = document.getElementById("input-pulso-nombre");
    const btnPulsoGuardar = document.getElementById("btn-pulso-guardar");
    const btnPulsoCerrar = document.getElementById("btn-pulso-cerrar");
    const matrizPulso = document.getElementById("matriz-pulso");
    const estadoPulso = document.getElementById("estado-pulso");

    // Seteamos el nombre del usuario en la cabecera
    nombreUsuarioHeader.textContent = usuario.nombre;

    // Inicializamos el panel de control al arrancar la vista
    verificarEstadoSistema();
    generarMatrizSimulada();
    cargarCatalogoRutas();
    cargarRankingPulso();

    // BOTÓN CERRAR SESIÓN
    btnLogout.addEventListener("click", () => {
        localStorage.removeItem("sesion_usuario");
        console.log("🧹 Sesión local destruida.");
        window.location.href = "/login.html";
    });

    // BOTÓN APAGAR/ENCENDER FOCO (relé de 1 canal en el GPIO 23 → corta o restablece el circuito del foco real)
    let focoApagado = false;

    function aplicarEstadoBtnFoco() {
        btnFoco.textContent = focoApagado ? "💡 Encender Foco" : "💡 Apagar Foco";
        btnFoco.classList.toggle("apagado", focoApagado);
        btnFoco.title = focoApagado
            ? "El foco está apagado: pulsa para encenderlo"
            : "El foco está encendido: pulsa para apagarlo";
    }

    // Al cargar la página se consulta el estado real del relé para pintar el botón
    fetch("/api/hardware/foco")
        .then(res => res.json())
        .then(data => {
            focoApagado = Boolean(data.apagado);
            aplicarEstadoBtnFoco();
        })
        .catch(err => console.error("Error al consultar el estado del foco:", err));

    btnFoco.addEventListener("click", () => {
        const accion = focoApagado ? "encender" : "apagar";
        fetch("/api/hardware/foco", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ accion })
        })
            .then(res => res.json())
            .then(data => {
                if (data.apagado !== undefined) {
                    focoApagado = Boolean(data.apagado);
                    aplicarEstadoBtnFoco();
                }
                alert(data.message);
            })
            .catch(err => console.error("Error al accionar el foco:", err));
    });

    // --- LÓGICA DE CAMBIO DE PESTAÑAS PRINCIPALES (TABS) ---
    tabEntrenar.addEventListener("click", () => {
        modoActual = "entrenar";
        cancelarPulsacionLarga();
        tabEntrenar.classList.add("activa");
        tabCrear.classList.remove("activa");
        seccionEntrenar.classList.add("activa");
        seccionCrear.classList.remove("activa");

        tituloMatriz.textContent = "🧱 Matriz de Presas (Simulación)";
        subtituloMatriz.textContent = "Las presas activas se iluminarán en el color correspondiente";

        apagarTodosLosNodosVisuales();
        limpiarSeleccionCreador();
        contenedorAccionVia.style.display = "none";
        rutaSeleccionadaId = null;
        panelVisual.style.display = "";
        cargarCatalogoRutas();
    });

    tabCrear.addEventListener("click", () => {
        modoActual = "crear";
        tabCrear.classList.add("activa");
        tabEntrenar.classList.remove("activa");
        tabPulso.classList.remove("activa");
        seccionCrear.classList.add("activa");
        seccionEntrenar.classList.remove("activa");
        seccionPulso.classList.remove("activa");

        tituloMatriz.textContent = "🛠️ Diseñando Nueva Vía";
        subtituloMatriz.textContent = "1º clic azul · 2º clic verde (inicio) · 3º clic rojo (top) · 4º clic deselecciona";

        apagarTodosLosNodosVisuales();
        contenedorAccionVia.style.display = "none";
        rutaSeleccionadaId = null;
        panelVisual.style.display = "";
        actualizarNodosCreadorVisual();
    });

    tabPulso.addEventListener("click", () => {
        modoActual = "pulso";
        cancelarPulsacionLarga();
        tabPulso.classList.add("activa");
        tabEntrenar.classList.remove("activa");
        tabCrear.classList.remove("activa");
        seccionPulso.classList.add("activa");
        seccionEntrenar.classList.remove("activa");
        seccionCrear.classList.remove("activa");

        // En el Pulso Vertical NO se renderiza la matriz visual de presas
        panelVisual.style.display = "none";
        contenedorAccionVia.style.display = "none";
        rutaSeleccionadaId = null;
        generarMatrizPulso();
    });

    // --- LÓGICA DE FILTROS SUB-PESTAÑAS (PROYECTOS / ENCADENADAS / COMUNIDAD) ---
    filtroProyectos.addEventListener("click", () => {
        filtroEstado = "proyecto";
        actualizarEstiloFiltros(filtroProyectos);
        contenedorAccionVia.style.display = "none";
        cargarCatalogoRutas();
    });

    filtroEncadenadas.addEventListener("click", () => {
        filtroEstado = "encadenada";
        actualizarEstiloFiltros(filtroEncadenadas);
        contenedorAccionVia.style.display = "none";
        cargarCatalogoRutas();
    });

    filtroComunidad.addEventListener("click", () => {
        filtroEstado = "comunidad";
        actualizarEstiloFiltros(filtroComunidad);
        contenedorAccionVia.style.display = "none";
        cargarCatalogoRutas();
    });

    function actualizarEstiloFiltros(filtroActivo) {
        filtroProyectos.classList.remove("activo");
        filtroEncadenadas.classList.remove("activo");
        filtroComunidad.classList.remove("activo");
        filtroActivo.classList.add("activo");
    }

    // Los filtros avanzados son exclusivos del bloque Comunidad: se muestran y
    // se limpian solos según la sub-pestaña activa.
    function sincronizarFiltrosComunidad() {
        const activo = filtroEstado === "comunidad";
        contFiltrosComunidad.style.display = activo ? "flex" : "none";

        if (!activo && (inputBuscarComunidad.value !== "" || selectGradoComunidad.value !== "")) {
            limpiarFiltrosComunidad();
        }
    }

    function limpiarFiltrosComunidad() {
        inputBuscarComunidad.value = "";
        selectGradoComunidad.value = "";
    }

    // Los dos criterios se combinan entre sí (AND): por nombre y/o por grado.
    // Se filtran en memoria sobre la copia del catálogo, así que teclear en el
    // buscador no vuelve a pedir datos al servidor.
    inputBuscarComunidad.addEventListener("input", aplicarFiltrosComunidad);
    selectGradoComunidad.addEventListener("change", aplicarFiltrosComunidad);

    function aplicarFiltrosComunidad() {
        if (filtroEstado !== "comunidad") return;

        const texto = inputBuscarComunidad.value.trim().toLowerCase();
        const gradoMinimo = selectGradoComunidad.value;

        const filtradas = catalogoComunidad.filter(ruta => {
            const coincideNombre = texto === "" || (ruta.nombre || "").toLowerCase().includes(texto);
            const coincideGrado = gradoMinimo === "" || cumpleGradoMinimo(ruta.grado, gradoMinimo);
            return coincideNombre && coincideGrado;
        });

        pintarCatalogoRutas(filtradas, texto !== "" || gradoMinimo !== "");
    }

    /**
     * Traduce un grado a una posición numérica dentro de ESCALA_GRADOS para
     * poder compararlo. Acepta mayúsculas y espacios sobrantes. Devuelve null
     * si el grado no pertenece a la escala (por ejemplo "8z" o un doble "+").
     */
    function puntuacionGrado(grado) {
        if (!grado) return null;

        const limpio = String(grado).trim().toLowerCase();
        const exacto = ESCALA_GRADOS.indexOf(limpio);
        if (exacto !== -1) return exacto;

        // Solo admitimos un "+" de margen: si hay más, el grado no es nuestro.
        const esMas = limpio.endsWith("+");
        if (esMas && limpio.endsWith("++")) return null;

        // Si el signo "+" no está, se usa el grado base como referencia:
        // "7b" puntúa medio peldaño por debajo de "7b+".
        const indiceBase = ESCALA_GRADOS.indexOf(esMas ? limpio.slice(0, -1) : limpio + "+");
        return indiceBase === -1 ? null : indiceBase + (esMas ? 0.5 : -0.5);
    }

    /**
     * El desplegable filtra por grado MÍNIMO: elegir 6a devuelve 6a, 6a+, 6b,
     * 6b+... y todo lo más difícil. Un grado fuera de la escala se descarta,
     * porque no hay forma de saber si supera el mínimo elegido.
     */
    function cumpleGradoMinimo(gradoRuta, gradoMinimo) {
        const minimo = puntuacionGrado(gradoMinimo);
        if (minimo === null) return true;

        const actual = puntuacionGrado(gradoRuta);
        return actual !== null && actual >= minimo;
    }

    // --- GENERACIÓN DE LA MATRIZ INTERACTIVA CON COORDENADAS ---
    function generarMatrizSimulada() {
        matrizPresasContenedor.innerHTML = "";

        const letras = Array.from({ length: PANEL_COLS }, (_, i) => String.fromCharCode(65 + i));

        // FILA 0: Cabecera de letras superiores
        // Esquina superior izquierda (intersección vacía)
        const esquinaVacia = document.createElement("div");
        matrizPresasContenedor.appendChild(esquinaVacia);

        // Inyectamos las letras de la A a la K
        letras.forEach(letra => {
            const etiquetaLetra = document.createElement("div");
            etiquetaLetra.className = "eje-coordenada";
            etiquetaLetra.textContent = letra;
            matrizPresasContenedor.appendChild(etiquetaLetra);
        });

        // FILAS: la numeración crece desde abajo, así que se dibuja de la 18 a la 1.
        // Dentro de cada fila la cadena serpentea, de modo que las pares se pintan
        // al revés para que el número mostrado coincida con el LED real.
        for (let fila = PANEL_FILAS; fila >= 1; fila--) {
            // Primer elemento de la fila: El número indicador de la izquierda
            const etiquetaNumero = document.createElement("div");
            etiquetaNumero.className = "eje-coordenada";
            etiquetaNumero.textContent = fila;
            matrizPresasContenedor.appendChild(etiquetaNumero);

            // Siguientes 11 elementos: Las presas reales de la línea (columnas A-K)
            for (let col = 0; col < PANEL_COLS; col++) {
                const idActual = ledDePosicion(fila, col);

                const nodo = document.createElement("div");
                nodo.className = "nodo-presa";
                nodo.id = `led-${idActual}`;
                nodo.textContent = idActual; // Muestra el número de LED asignado

                nodo.addEventListener("click", () => {
                    if (modoActual !== "crear") return;
                    // El long press provoca un clic fantasma al soltar: se descarta
                    if (Date.now() - instantePulsacionLarga < MS_GUARDIA_CLIC) return;
                    gestionarClicNodoCreador(idActual);
                });

                // Mantener pulsado deselecciona la presa al pasar el umbral de tiempo
                nodo.addEventListener("mousedown", () => {
                    if (modoActual !== "crear") return;
                    if (!rolesPorPresa.has(idActual)) return;
                    iniciarPulsacionLarga(idActual);
                });

                ["mouseup", "mouseleave", "mouseout", "touchend", "touchcancel"].forEach(evento => {
                    nodo.addEventListener(evento, cancelarPulsacionLarga);
                });

                matrizPresasContenedor.appendChild(nodo);
            }
        }

        console.log(`🎮 Matriz generada con éxito. Total LEDs mapeados: ${PANEL_TOTAL}`);
    }

    // --- GESTIÓN DE NODOS EN MODO CONSTRUCTOR ---
    // Ciclo de roles por cada clic: intermedia (azul) -> inicio (verde) -> top (rojo) -> fuera
    const CICLO_ROLES = {
        intermedia: "inicio",
        inicio: "top",
        top: null
    };

    const ROL_POR_DEFECTO = "intermedia";

    const COLOR_HEX_POR_ROL = {
        intermedia: "0000FF",
        inicio: "00FF00",
        top: "FF0000"
    };

    const MS_PULSACION_LARGA = 500;
    const MS_GUARDIA_CLIC = 400;

    function gestionarClicNodoCreador(ledId) {
        // Si la presa no está en el diseño, el primer clic la mete como intermedia
        const rolActual = rolesPorPresa.get(ledId);
        const nuevoRol = rolActual === undefined
            ? ROL_POR_DEFECTO
            : CICLO_ROLES[rolActual];

        if (nuevoRol === null || nuevoRol === undefined) {
            rolesPorPresa.delete(ledId);
        } else {
            rolesPorPresa.set(ledId, nuevoRol);
        }

        actualizarNodosCreadorVisual();
        iluminarSeleccionFisica();
    }

    // Mantener pulsada una presa la saca del diseño (alternativa al 4º clic)
    function iniciarPulsacionLarga(ledId) {
        cancelarPulsacionLarga();

        temporizadorPulsacion = setTimeout(() => {
            rolesPorPresa.delete(ledId);
            instantePulsacionLarga = Date.now();
            actualizarNodosCreadorVisual();
            iluminarSeleccionFisica();
        }, MS_PULSACION_LARGA);
    }

    function cancelarPulsacionLarga() {
        if (temporizadorPulsacion) {
            clearTimeout(temporizadorPulsacion);
            temporizadorPulsacion = null;
        }
    }

    function contarPresasPorRol(rol) {
        let total = 0;
        rolesPorPresa.forEach(tipo => {
            if (tipo === rol) total++;
        });
        return total;
    }

    function actualizarNodosCreadorVisual() {
        document.querySelectorAll(".nodo-presa").forEach(nodo => {
            nodo.classList.remove("creando-activa", "presa-inicio", "presa-top");
        });

        rolesPorPresa.forEach((rol, id) => {
            const el = document.getElementById(`led-${id}`);
            if (!el) return;
            el.classList.add(
                rol === "inicio" ? "presa-inicio" :
                rol === "top" ? "presa-top" :
                "creando-activa"
            );
        });

        contadorPresasBadge.textContent = `${rolesPorPresa.size} presas elegidas`;
        contadorInicioBadge.textContent = contarPresasPorRol("inicio");
        contadorIntermediaBadge.textContent = contarPresasPorRol("intermedia");
        contadorTopBadge.textContent = contarPresasPorRol("top");
        btnGuardarVia.disabled = rolesPorPresa.size === 0;
    }

    // El panel físico necesita un color por grupo: primero el azul (que limpia la
    // tira) y después se superponen el verde de los inicios y el rojo de los tops.
    function iluminarSeleccionFisica() {
        if (rolesPorPresa.size === 0) {
            fetch("/api/hardware/apagar", { method: "POST" }).catch(() => {});
            return;
        }

        const grupos = [];
        ["intermedia", "inicio", "top"].forEach(rol => {
            const leds = [];
            rolesPorPresa.forEach((tipo, id) => {
                if (tipo === rol) leds.push(id);
            });
            if (leds.length > 0) {
                grupos.push({ leds: leds, color: COLOR_HEX_POR_ROL[rol] });
            }
        });

        fetch("/api/hardware/encender-manual", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ grupos: grupos })
        }).catch(err => console.error("Error iluminando selección:", err));
    }

    function limpiarSeleccionCreador() {
        cancelarPulsacionLarga();
        if (rolesPorPresa.size === 0) return;
        rolesPorPresa.clear();
        actualizarNodosCreadorVisual();
        fetch("/api/hardware/apagar", { method: "POST" }).catch(() => {});
    }

    btnLimpiarCreador.addEventListener("click", () => {
        rolesPorPresa.clear();
        actualizarNodosCreadorVisual();
        iluminarSeleccionFisica();
    });

    // --- GUARDAR NUEVA VÍA (SUBIR A JAVALIN) ---
    formCrearVia.addEventListener("submit", (e) => {
        e.preventDefault();

        const presas = [];
        rolesPorPresa.forEach((rol, id) => {
            presas.push({ led: id, tipo: rol });
        });

        const payload = {
            nombre: txtNombreVia.value,
            grado: selGradoVia.value,
            equipador: usuario.nombre,
            usuario_correo: usuario.correo || usuario.email,
            presas: presas,
            leds: presas.map(p => p.led)
        };

        fetch("/api/rutas/crear", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(payload)
        })
            .then(res => {
                if (!res.ok) throw new Error("Error en el servidor al insertar ruta");
                return res.json();
            })
            .then(data => {
                alert("✨ ¡Vía guardada con éxito y asignada a tus Proyectos!");
                formCrearVia.reset();
                rolesPorPresa.clear();
                actualizarNodosCreadorVisual();
                fetch("/api/hardware/apagar", { method: "POST" }).catch(() => {});
                tabEntrenar.click();
            })
            .catch(err => {
                alert("❌ Error de comunicación con la API de guardado. Revisa los logs de la consola o de Javalin.");
                console.error(err);
            });
    });

    // --- ACCIONES DE CATÁLOGO (LEER, SELECCIONAR Y BORRAR) ---
    function cargarCatalogoRutas() {
        sincronizarFiltrosComunidad();

        let url = `/api/rutas?usuario=${usuario.correo || usuario.email}&estado=${filtroEstado}`;

        if (filtroEstado === "comunidad") {
            url = "/api/rutas";
        }

        fetch(url)
            .then(res => res.json())
            .then(rutas => {
                if (filtroEstado === "comunidad") {
                    // Guardamos la copia íntegra y delegamos el pintado en los
                    // filtros, que ya aplican nombre y/o grado sobre ella.
                    catalogoComunidad = rutas;
                    aplicarFiltrosComunidad();
                } else {
                    pintarCatalogoRutas(rutas);
                }
            })
            .catch(err => {
                console.error("Error al cargar rutas:", err);
                listaRutasContenedor.innerHTML = "";
                const errMsg = document.createElement("p");
                errMsg.className = "subtitulo";
                errMsg.style.color = "var(--color-peligro)";
                errMsg.textContent = "Error de conexión con la API.";
                listaRutasContenedor.appendChild(errMsg);
            });
    }

    // Dibuja en el panel la lista de vías recibida. 'hayFiltros' solo cambia el
    // texto del estado vacío para distinguir "no hay nada" de "no hay nada que
    // encaje con lo que has buscado".
    function pintarCatalogoRutas(rutas, hayFiltros = false) {
        listaRutasContenedor.innerHTML = "";

        if (rutas.length === 0) {
            const msg = document.createElement("p");
            msg.className = "subtitulo";
            msg.style.margin = "1rem auto";
            msg.style.textAlignment = "center";
            msg.textContent = hayFiltros
                ? "Ninguna vía de la comunidad cumple los filtros seleccionados."
                : "No hay vías disponibles en este bloque todavía.";
            listaRutasContenedor.appendChild(msg);
            return;
        }

        rutas.forEach(ruta => {
            // Clonamos el contenido del template HTML de manera limpia
            const clon = plantillaRuta.content.cloneNode(true);
            const item = clon.querySelector(".item-ruta");

            // Rellenamos las propiedades de texto apuntando a sus selectores semánticos
            clon.querySelector(".ruta-nombre").textContent = ruta.nombre;
            clon.querySelector(".ruta-equipador").textContent = `Creador: ${ruta.equipador || 'Anónimo'}`;
            clon.querySelector(".grado-ruta").textContent = ruta.grado;

            const btnBorrar = clon.querySelector(".btn-borrar-via");
            const btnAgregar = clon.querySelector(".btn-agregar-comunidad");

            // Renderizado Condicional nativo: Conmutamos visibilidad de botones según la pestaña activa
            if (filtroEstado === "comunidad") {
                btnAgregar.style.display = "flex";

                btnAgregar.addEventListener("click", (e) => {
                    e.stopPropagation(); // Evitamos iluminar la simulación al agregar

                    const payloadAgregar = {
                        usuario: usuario.correo || usuario.email,
                        estado: "proyecto"
                    };

                    fetch(`/api/rutas/${ruta.id}/agregar`, {
                        method: "POST",
                        headers: { "Content-Type": "application/json" },
                        body: JSON.stringify(payloadAgregar)
                    })
                        .then(res => {
                            if (res.status === 409) {
                                alert("💡 Esta vía ya se encuentra añadida en tu panel de Proyectos.");
                                return;
                            }
                            if (!res.ok) throw new Error();
                            alert(`✨ "${ruta.nombre}" ha sido añadida con éxito a tu lista de Proyectos.`);
                        })
                        .catch(err => {
                            console.error(err);
                            alert("❌ No se pudo importar la ruta de la comunidad.");
                        });
                });
            } else {
                btnBorrar.style.display = "inline-block";

                btnBorrar.addEventListener("click", (e) => {
                    e.stopPropagation();
                    eliminarRutaDelCatatogo(ruta.id);
                });
            }

            // Al pinchar en la tarjeta iluminamos sus presas en la simulación
            item.addEventListener("click", () => {
                seleccionarRutaEnPanel(ruta.id);
            });

            // Inyectamos el nodo clonado procesado directamente al contenedor
            listaRutasContenedor.appendChild(clon);
        });
    }

    function seleccionarRutaEnPanel(id) {
        fetch(`/api/rutas/${id}/seleccionar`, { method: "POST" })
            .then(res => res.json())
            .then(data => {
                apagarTodosLosNodosVisuales();
                if (data.leds && Array.isArray(data.leds)) {
                    iluminarNodosVisuales(data.leds);
                    rutaSeleccionadaId = id;
                    if (filtroEstado === "proyecto") {
                        contenedorAccionVia.style.display = "block";
                    } else {
                        contenedorAccionVia.style.display = "none";
                    }
                }
                // Si el servidor devuelve los roles, la simulación respeta el diseño original
                if (data.presas && Array.isArray(data.presas)) {
                    iluminarNodosVisualesConRoles(data.presas);
                }
                if (data.status === "error") {
                    console.warn("⚠️ Hardware no disponible:", data.message);
                }
            })
            .catch(err => console.error("Error al iluminar ruta:", err));
    }

    // Pinta la vía en la simulación con el color que tendrá en el panel real
    function iluminarNodosVisualesConRoles(presas) {
        presas.forEach(presa => {
            const idLed = presa.indiceLed !== undefined ? presa.indiceLed : presa.led;
            const el = document.getElementById(`led-${idLed}`);
            if (!el) return;
            el.classList.remove("activo");
            el.classList.add(
                presa.tipo === "inicio" ? "presa-inicio" :
                presa.tipo === "top" ? "presa-top" :
                "activo"
            );
        });
    }

    // ACCIÓN DEL BOTÓN ¡ENCADENADA!
    btnCompletarVia.addEventListener("click", () => {
        if (!rutaSeleccionadaId) return;

        const payloadEstado = {
            usuario: usuario.correo || usuario.email,
            estado: "encadenada"
        };

        fetch(`/api/rutas/${rutaSeleccionadaId}/estado`, {
            method: "PUT",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(payloadEstado)
        })
            .then(res => {
                if (!res.ok) throw new Error("Fallo al actualizar el progreso");
                return res.json();
            })
            .then(() => {
                alert("💪 ¡Enhorabuena! Vía completada y movida a tus Encadenadas.");
                contenedorAccionVia.style.display = "none";
                apagarTodosLosNodosVisuales();
                rutaSeleccionadaId = null;
                cargarCatalogoRutas(); // Refrescamos la lista de proyectos
            })
            .catch(err => {
                alert("❌ No se pudo actualizar el estado en el servidor.");
                console.error(err);
            });
    });

    function eliminarRutaDelCatatogo(id) {
        // El borrado es global: la vía desaparece de tu lista Y del catálogo
        // de la Comunidad, así que el aviso lo deja claro antes de destruirla.
        if (!confirm("⚠️ ¿Eliminar esta vía?\n\nSe borrará de tu lista de entrenamiento y también del catálogo de la Comunidad. Es irreversible.")) return;

        fetch(`/api/rutas/${id}?usuario=${usuario.correo || usuario.email}`, { method: "DELETE" })
            .then(res => {
                if (!res.ok) throw new Error("No se pudo procesar el borrado");
                return res.json();
            })
            .then(data => {
                // La comunidad se queda sin esta vía, así que la sacamos también
                // de la copia en memoria para que el filtro no la repinte.
                catalogoComunidad = catalogoComunidad.filter(ruta => ruta.id !== id);

                // Si la vía borrada era la que está iluminada en el panel, la apagamos
                if (rutaSeleccionadaId === id) {
                    rutaSeleccionadaId = null;
                    contenedorAccionVia.style.display = "none";
                    apagarTodosLosNodosVisuales();
                }

                cargarCatalogoRutas();
            })
            .catch(err => {
                console.error(err);
                alert("❌ Error al intentar eliminar la vía de la BBDD.");
            });
    }

    // --- ACCIONES HARDWARE DIRECTAS ---
    btnApagar.addEventListener("click", () => {
        fetch("/api/hardware/apagar", { method: "POST" })
            .then(res => res.json())
            .then(data => {
                alert(data.message);
                apagarTodosLosNodosVisuales();
                contenedorAccionVia.style.display = "none";
            })
            .catch(err => console.error("Error al apagar el panel:", err));
    });

    btnTest.addEventListener("click", () => {
        const ledsTest = Array.from({ length: PANEL_TOTAL }, (_, i) => i + 1);
        fetch("/api/hardware/encender-manual", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ leds: ledsTest })
        })
            .then(res => res.json())
            .then(() => {
                apagarTodosLosNodosVisuales();
                contenedorAccionVia.style.display = "none";
                iluminarNodosVisuales(ledsTest);
            })
            .catch(err => console.error("Error en el test manual:", err));
    });

    function verificarEstadoSistema() {
        fetch("/api/estado")
            .then(res => {
                if(res.ok) {
                    badgeConexion.textContent = "En Línea (Local)";
                    badgeConexion.className = "badge conectado";
                }
            })
            .catch(() => {
                badgeConexion.textContent = "Desconectado";
                badgeConexion.className = "badge desconectado";
            });
    }

    function iluminarNodosVisuales(listaLeds) {
        listaLeds.forEach(idLed => {
            const el = document.getElementById(`led-${idLed}`);
            if (el) el.classList.add("activo");
        });
    }

    function apagarTodosLosNodosVisuales() {
        document.querySelectorAll(".nodo-presa").forEach(nodo => {
            nodo.classList.remove("activo");
            nodo.classList.remove("creando-activa");
            nodo.classList.remove("presa-inicio");
            nodo.classList.remove("presa-top");
        });
    }

    // =====================================================================
    // MINIJUEGO "PULSO VERTICAL"
    // =====================================================================

    function formatearTiempoPulso(ms) {
        const minutos = Math.floor(ms / 60000);
        const segundos = Math.floor((ms % 60000) / 1000);
        const decimas = Math.floor((ms % 1000) / 100);
        return `${String(minutos).padStart(2, "0")}:${String(segundos).padStart(2, "0")}.${decimas}`;
    }

    function actualizarCronometroPulso() {
        cronometroPulso.textContent = formatearTiempoPulso(cronoPulsoAcumuladoMs);
    }

    function iniciarCronometroPulso() {
        if (cronoPulsoIntervalo) return;
        const instanteInicio = Date.now() - cronoPulsoAcumuladoMs;
        cronoPulsoIntervalo = setInterval(() => {
            cronoPulsoAcumuladoMs = Date.now() - instanteInicio;
            actualizarCronometroPulso();
        }, 100);
    }

    function pausarCronometroPulso() {
        if (cronoPulsoIntervalo) {
            clearInterval(cronoPulsoIntervalo);
            cronoPulsoIntervalo = null;
        }
    }

    function restablecerCronometroPulso() {
        pausarCronometroPulso();
        cronoPulsoAcumuladoMs = 0;
        actualizarCronometroPulso();
    }

    // =====================================================================
    // SIMULADOR VISUAL DEL PULSO VERTICAL
    // Refleja la lógica del service Java (JuegoPulsoVerticalService):
    // franjas rojas en la cuenta atrás y apagado progresivo en verde.
    // =====================================================================

    const SIM_TOTAL = PANEL_TOTAL;
    const SIM_COLS = PANEL_COLS;
    // La fila 1 es la inferior, así que la zona alta del muro (filas 13-18) son
    // los LEDs más altos y la zona baja (filas 1-6) los primeros.
    const SIM_LIM_ALTA = 12 * SIM_COLS;   // 132: por debajo está la zona media
    const SIM_LIM_MEDIA = 6 * SIM_COLS;   // 66: por debajo está la zona baja
    const SIM_CELL_CM = 24;
    const SIM_DIST_MIN = 30;
    const SIM_DIST_MAX = 130;
    const SIM_TICK_SEGUNDOS = 3;
    const SIM_DURACION_NARANJA = 5;

    const simPulso = {
        iniciado: false,
        pausado: false,
        acumuladoMs: 0,
        ultimoTiempo: 0,
        lastTick: -1,
        intervalo: null,
        ledsVerdes: new Set(),
        ledsNaranja: new Set(),
        ledAEncender: null,
        pendienteHasta: 0
    };

    function generarMatrizPulso() {
        matrizPulso.innerHTML = "";

        const letras = Array.from({ length: PANEL_COLS }, (_, i) => String.fromCharCode(65 + i));

        // Esquina superior izquierda + cabecera de letras (A-K)
        matrizPulso.appendChild(document.createElement("div"));
        letras.forEach(letra => {
            const etiqueta = document.createElement("div");
            etiqueta.className = "eje-coordenada";
            etiqueta.textContent = letra;
            matrizPulso.appendChild(etiqueta);
        });

        // Filas 18-1 de arriba abajo: número a la izquierda + 11 presas
        for (let fila = PANEL_FILAS; fila >= 1; fila--) {
            const etiquetaNumero = document.createElement("div");
            etiquetaNumero.className = "eje-coordenada";
            etiquetaNumero.textContent = fila;
            matrizPulso.appendChild(etiquetaNumero);

            for (let col = 0; col < PANEL_COLS; col++) {
                const idLed = ledDePosicion(fila, col);
                const nodo = document.createElement("div");
                nodo.className = "nodo-presa";
                nodo.id = `led-pulso-${idLed}`;
                nodo.textContent = idLed;
                matrizPulso.appendChild(nodo);
            }
        }
    }

    function iniciarSimulacionPulso() {
        detenerSimulacionPulso();
        simPulso.iniciado = true;
        simPulso.pausado = false;
        simPulso.acumuladoMs = 0;
        simPulso.ultimoTiempo = Date.now();
        simPulso.lastTick = -1;
        simPulso.ledsVerdes = new Set(Array.from({ length: SIM_TOTAL }, (_, i) => i + 1));
        simPulso.ledsNaranja.clear();
        simPulso.ledAEncender = null;
        simPulso.pendienteHasta = 0;

        estadoPulso.textContent = "🔴 Cuenta atrás...";
        estadoPulso.classList.add("en-juego");

        simPulso.intervalo = setInterval(() => {
            const ahora = Date.now();
            const delta = ahora - simPulso.ultimoTiempo;
            simPulso.ultimoTiempo = ahora;
            if (!simPulso.iniciado || simPulso.pausado) return;
            simPulso.acumuladoMs += delta;

            const t = simPulso.acumuladoMs / 1000;

            // FASE 1: Cuenta atrás con franjas rojas (0s, 2s, 4s -> 6s)
            if (t < 2) {
                pintarZonaPulso(SIM_LIM_ALTA + 1, SIM_TOTAL, "rojo");
            } else if (t < 4) {
                pintarZonaPulso(SIM_LIM_MEDIA + 1, SIM_TOTAL, "rojo");
            } else if (t < 6) {
                pintarZonaPulso(1, SIM_TOTAL, "rojo");
            } else {
                // FASE 2: Juego en verde con apagado progresivo
                // Primer tick a los 9 s (igual que scheduleWithFixedDelay de Java: 3s tras la fase verde)
                if (simPulso.pendienteHasta > 0) {
                    // Los LEDs naranja permanecen 5 segundos antes de apagarse
                    if (t >= simPulso.pendienteHasta) {
                        completarApagadoSim();
                        // Como en Java, el próximo ciclo solo arranca en el siguiente tick
                        simPulso.lastTick = Math.floor((t - 6 - SIM_TICK_SEGUNDOS) / SIM_TICK_SEGUNDOS);
                    }
                } else {
                    const tick = Math.floor((t - 6 - SIM_TICK_SEGUNDOS) / SIM_TICK_SEGUNDOS);
                    if (tick > simPulso.lastTick) {
                        simPulso.lastTick = tick;
                        const accion = seleccionarAccionSim();
                        if (accion) {
                            simPulso.ledsNaranja = new Set(accion.aApagar);
                            simPulso.ledAEncender = accion.aEncender;
                            simPulso.pendienteHasta = t + SIM_DURACION_NARANJA;
                        }
                    }
                }
                pintarVerdesPulso();
                estadoPulso.textContent = simPulso.ledsNaranja.size > 0
                    ? "🟠 ¡Cuidado! Presas en naranja a punto de desaparecer..."
                    : "🟢 ¡Fase verde! Aguantando en el panel...";
            }
        }, 100);
    }

    function completarApagadoSim() {
        if (simPulso.ledAEncender !== null) {
            simPulso.ledsVerdes.add(simPulso.ledAEncender);
            simPulso.ledAEncender = null;
        }
        simPulso.ledsNaranja.forEach(led => simPulso.ledsVerdes.delete(led));
        simPulso.ledsNaranja.clear();
        simPulso.pendienteHasta = 0;
    }

    function pausarSimulacionPulso() {
        simPulso.pausado = true;
        estadoPulso.textContent = "⏸️ Pausado. Presas congeladas.";
    }

    function reanudarSimulacionPulso() {
        simPulso.pausado = false;
        simPulso.ultimoTiempo = Date.now();
        estadoPulso.textContent = "🟢 ¡Fase verde! Aguantando en el panel...";
    }

    function detenerSimulacionPulso() {
        if (simPulso.intervalo) {
            clearInterval(simPulso.intervalo);
            simPulso.intervalo = null;
        }
        simPulso.iniciado = false;
        simPulso.pausado = false;
        simPulso.ledsVerdes.clear();
        simPulso.ledsNaranja.clear();
        simPulso.ledAEncender = null;
        simPulso.pendienteHasta = 0;
    }

    function reiniciarSimulacionPulso() {
        detenerSimulacionPulso();
        limpiarMatrizPulso();
        estadoPulso.textContent = "⚪ Esperando a que inicies el juego...";
        estadoPulso.classList.remove("en-juego");
    }

    function limpiarMatrizPulso() {
        matrizPulso.querySelectorAll(".nodo-presa").forEach(nodo => {
            nodo.classList.remove("rojo");
            nodo.classList.remove("naranja");
            nodo.classList.remove("activo");
        });
    }

    function pintarZonaPulso(desde, hasta, clase) {
        limpiarMatrizPulso();
        for (let i = desde; i <= hasta; i++) {
            const el = document.getElementById(`led-pulso-${i}`);
            if (el) el.classList.add(clase);
        }
    }

    function pintarVerdesPulso() {
        limpiarMatrizPulso();
        simPulso.ledsVerdes.forEach(i => {
            const el = document.getElementById(`led-pulso-${i}`);
            if (el) el.classList.add("activo");
        });
        // Los LEDs en fase de apagado se superponen en naranja (5 segundos)
        simPulso.ledsNaranja.forEach(i => {
            const el = document.getElementById(`led-pulso-${i}`);
            if (el) el.classList.add("naranja");
        });
    }

    // Lógica espejo del service Java -------------------------------------

    function zonaDeSim(led) {
        if (led > SIM_LIM_ALTA) return 1;   // Alta (filas 13-18, parte superior)
        if (led > SIM_LIM_MEDIA) return 2;   // Media (filas 7-12)
        return 3;                           // Baja (filas 1-6, parte inferior)
    }

    function contarPorZonaSim(zona, estado) {
        let contador = 0;
        estado.forEach(led => {
            if (zonaDeSim(led) === zona) contador++;
        });
        return contador;
    }

    function distanciaCmSim(ledA, ledB) {
        const posA = posicionDeLed(ledA);
        const posB = posicionDeLed(ledB);
        const dx = (posA.columna - posB.columna) * SIM_CELL_CM;
        const dy = (posA.fila - posB.fila) * SIM_CELL_CM;
        return Math.hypot(dx, dy);
    }

    function barajarSim(lista) {
        for (let i = lista.length - 1; i > 0; i--) {
            const j = Math.floor(Math.random() * (i + 1));
            [lista[i], lista[j]] = [lista[j], lista[i]];
        }
    }

    function puedeApagarseSim(led, estado) {
        if (contarPorZonaSim(zonaDeSim(led), estado) <= 2) return false;

        const ordenados = [...estado].sort((a, b) => a - b);
        const idx = ordenados.indexOf(led);
        const anterior = idx > 0 ? ordenados[idx - 1] : null;
        const posterior = idx < ordenados.length - 1 ? ordenados[idx + 1] : null;

        if (anterior !== null && posterior !== null) {
            return distanciaCmSim(anterior, posterior) <= SIM_DIST_MAX;
        }
        return true;
    }

    function seleccionarUnLedParaApagarSim(estado) {
        const candidatos = [...estado];
        barajarSim(candidatos);

        for (const led of candidatos) {
            if (puedeApagarseSim(led, estado)) {
                return led;
            }
        }

        // Salvaguarda: apaga uno que deje al menos 2 apoyos en su zona
        for (const led of candidatos) {
            if (contarPorZonaSim(zonaDeSim(led), estado) > 2) {
                return led;
            }
        }
        return null;
    }

    function distanciaMinimaAlVecinoSim(led) {
        let minima = Infinity;
        simPulso.ledsVerdes.forEach(otro => {
            minima = Math.min(minima, distanciaCmSim(led, otro));
        });
        return minima === Infinity ? SIM_DIST_MAX : minima;
    }

    function ledApagadoValidoEnZonaSim(zona) {
        const inicio = zona === 1 ? SIM_LIM_ALTA + 1 : zona === 2 ? SIM_LIM_MEDIA + 1 : 1;
        const fin = zona === 1 ? SIM_TOTAL : zona === 2 ? SIM_LIM_ALTA : SIM_LIM_MEDIA;

        const candidatos = [];
        for (let led = inicio; led <= fin; led++) {
            if (!simPulso.ledsVerdes.has(led)) candidatos.push(led);
        }
        barajarSim(candidatos);

        for (const led of candidatos) {
            if (distanciaMinimaAlVecinoSim(led) >= SIM_DIST_MIN) return led;
        }
        return candidatos.length ? candidatos[0] : null;
    }

    function seleccionarAccionSim() {
        const totalActivos = simPulso.ledsVerdes.size;

        if (totalActivos > 6) {
            // Reducción porcentual del 35% sobre los LEDs activos en cada ciclo
            let ledsAQuitar = Math.floor(totalActivos * 0.35);

            // Mínimo 1 LED por ciclo mientras haya más de 6 activos
            if (ledsAQuitar < 1) ledsAQuitar = 1;

            // Límite de seguridad: nunca dejar el panel con menos de 6 LEDs
            if (totalActivos - ledsAQuitar < 6) ledsAQuitar = totalActivos - 6;

            // Se seleccionan los LEDs respetando la biomecánica sin mutar el estado
            const aApagar = [];
            const estadoSimulado = new Set(simPulso.ledsVerdes);
            while (aApagar.length < ledsAQuitar && estadoSimulado.size > 6) {
                const led = seleccionarUnLedParaApagarSim(estadoSimulado);
                if (led === null) break;
                estadoSimulado.delete(led);
                aApagar.push(led);
            }
            return aApagar.length ? { aApagar, aEncender: null } : null;
        } else if (totalActivos === 6) {
            // Con exactamente 6 LEDs se entra en el bucle infinito
            const candidatos = [...simPulso.ledsVerdes];
            barajarSim(candidatos);
            const aApagar = candidatos[0];
            const zona = zonaDeSim(aApagar);
            const aEncender = ledApagadoValidoEnZonaSim(zona);
            if (aEncender === null) return null;
            return { aApagar: [aApagar], aEncender };
        }
        return null;
    }

    // Botón de la tarjeta de presentación: muestra las tarjetas de juego
    btnPulsoEmpezar.addEventListener("click", () => {
        tarjetaPulsoInicio.style.display = "none";
        tarjetaPulsoJuego.style.display = "block";
        restablecerCronometroPulso();
        reiniciarSimulacionPulso();
    });

    btnPulsoIniciar.addEventListener("click", () => {
        // La simulación visual arranca siempre (para poder probarla sin hardware)
        iniciarSimulacionPulso();

        fetch("/api/juego/pulso-vertical/iniciar", { method: "POST" })
            .then(res => res.json())
            .then(data => {
                if (data.status === "error") {
                    console.warn("⚠️ " + (data.message || "No se pudo iniciar el juego en el hardware."));
                    return;
                }
                cronoPulsoIniciado = true;
                pulsoPausado = false;
                restablecerCronometroPulso();
                iniciarCronometroPulso();
                btnPulsoIniciar.style.display = "none";
                btnPulsoPausar.style.display = "inline-block";
                btnPulsoPausar.textContent = "Pausar";
            })
            .catch(err => {
                console.error("Error al iniciar el juego:", err);
                alert("❌ No se pudo iniciar el juego. ¿Está conectado el hardware?");
            });
    });

    btnPulsoPausar.addEventListener("click", () => {
        if (!cronoPulsoIniciado && !simPulso.iniciado) return;

        if (!pulsoPausado) {
            pausarSimulacionPulso();
            fetch("/api/juego/pulso-vertical/pausar", { method: "POST" })
                .then(res => res.json())
                .then(() => {
                    pulsoPausado = true;
                    pausarCronometroPulso();
                    btnPulsoPausar.textContent = "Reanudar";
                })
                .catch(err => console.error("Error al pausar el juego:", err));
        } else {
            reanudarSimulacionPulso();
            fetch("/api/juego/pulso-vertical/reanudar", { method: "POST" })
                .then(res => res.json())
                .then(() => {
                    pulsoPausado = false;
                    iniciarCronometroPulso();
                    btnPulsoPausar.textContent = "Pausar";
                })
                .catch(err => console.error("Error al reanudar el juego:", err));
        }
    });

    btnPulsoFinalizar.addEventListener("click", () => {
        if (!cronoPulsoIniciado && !simPulso.iniciado) {
            alert("ℹ️ El juego aún no está iniciado.");
            return;
        }

        detenerSimulacionPulso();
        fetch("/api/juego/pulso-vertical/finalizar", { method: "POST" })
            .then(res => res.json())
            .then(() => {
                cronoPulsoIniciado = false;
                pulsoPausado = false;
                pausarCronometroPulso();
                modalPulsoTiempo.textContent = formatearTiempoPulso(cronoPulsoAcumuladoMs);
                inputPulsoNombre.value = usuario.nombre || "";
                modalPulsoMarca.style.display = "flex";
            })
            .catch(err => {
                console.error("Error al finalizar el juego:", err);
                alert("❌ No se pudo finalizar el juego.");
            });
    });

    function guardarMarcaPulso() {
        const nombre = inputPulsoNombre.value.trim();
        if (!nombre) {
            alert("ℹ️ Introduce tu nombre para guardar la marca.");
            return;
        }

        const payload = {
            nombre_jugador: nombre,
            tiempo_segundos: cronoPulsoAcumuladoMs / 1000
        };

        fetch("/api/juego/pulso-vertical/ranking", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(payload)
        })
            .then(res => {
                if (!res.ok) throw new Error("Fallo al guardar la marca");
                return res.json();
            })
            .then(() => {
                modalPulsoMarca.style.display = "none";
                cargarRankingPulso();
                restablecerCronometroPulso();
                reiniciarSimulacionPulso();
                btnPulsoIniciar.style.display = "inline-block";
                btnPulsoPausar.style.display = "none";
            })
            .catch(err => {
                console.error("Error al guardar la marca:", err);
                alert("❌ No se pudo guardar la marca en el servidor.");
            });
    }

    btnPulsoGuardar.addEventListener("click", guardarMarcaPulso);
    btnPulsoCerrar.addEventListener("click", () => {
        modalPulsoMarca.style.display = "none";
    });
    modalPulsoMarca.addEventListener("click", (e) => {
        if (e.target === modalPulsoMarca) modalPulsoMarca.style.display = "none";
    });

    function cargarRankingPulso() {
        fetch("/api/juego/pulso-vertical/ranking")
            .then(res => res.json())
            .then(data => {
                const tbody = tablaRanking.querySelector("tbody");
                if (!data || data.length === 0) {
                    tbody.innerHTML = '<tr><td colspan="4" class="cargando">Todavía no hay marcas registradas. ¡Sé el primero!</td></tr>';
                    return;
                }
                tbody.innerHTML = "";
                data.forEach((marca, index) => {
                    const tr = document.createElement("tr");
                    const medal = index === 0 ? "🥇" : index === 1 ? "🥈" : index === 2 ? "🥉" : `${index + 1}`;
                    const tiempo = formatearTiempoPulso(Math.round(marca.tiempoSegundos * 1000));
                    const fecha = marca.fecha ? new Date(marca.fecha).toLocaleDateString("es-ES") : "—";
                    tr.innerHTML = `
                        <td>${medal}</td>
                        <td>${escapeHtml(marca.nombreJugador || "Anónimo")}</td>
                        <td>${tiempo}</td>
                        <td>${fecha}</td>
                    `;
                    tbody.appendChild(tr);
                });
            })
            .catch(err => {
                console.error("Error al cargar el ranking:", err);
                const tbody = tablaRanking.querySelector("tbody");
                tbody.innerHTML = '<tr><td colspan="4" class="cargando">Error al cargar el ranking.</td></tr>';
            });
    }

    function escapeHtml(texto) {
        const div = document.createElement("div");
        div.textContent = texto;
        return div.innerHTML;
    }
});