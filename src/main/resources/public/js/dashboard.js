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
    let ledsSeleccionadosCreacion = []; // Guarda los LEDs que pulsemos en modo Builder
    let rutaSeleccionadaId = null;     // Guarda la ID de la ruta activa en el panel

    // Estado del minijuego Pulso Vertical
    let cronoPulsoIntervalo = null;    // Intervalo del cronómetro
    let cronoPulsoAcumuladoMs = 0;     // Tiempo acumulado (ms) antes de pausas
    let cronoPulsoIniciado = false;    // ¿El juego está iniciado?
    let pulsoPausado = false;          // ¿El cronómetro está en pausa?

    // --- ELEMENTOS DEL DOM ---
    const nombreUsuarioHeader = document.getElementById("nombre-usuario-header");
    const btnLogout = document.getElementById("btn-logout");
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

    // Botón de Acción Especial de Encadenar
    const contenedorAccionVia = document.getElementById("contenedor-accion-via");
    const btnCompletarVia = document.getElementById("btn-completar-via");

    // Formulario Constructor (MoonBoard Builder)
    const formCrearVia = document.getElementById("form-crear-via");
    const txtNombreVia = document.getElementById("crear-nombre");
    const selGradoVia = document.getElementById("crear-grado");
    const contadorPresasBadge = document.getElementById("contador-presas");
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

    // --- LÓGICA DE CAMBIO DE PESTAÑAS PRINCIPALES (TABS) ---
    tabEntrenar.addEventListener("click", () => {
        modoActual = "entrenar";
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
        subtituloMatriz.textContent = "Haz clic en los círculos para marcar presas (Color Azul)";

        apagarTodosLosNodosVisuales();
        contenedorAccionVia.style.display = "none";
        rutaSeleccionadaId = null;
        panelVisual.style.display = "";
        actualizarNodosCreadorVisual();
    });

    tabPulso.addEventListener("click", () => {
        modoActual = "pulso";
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

    // --- GENERACIÓN DE LA MATRIZ INTERACTIVA CON COORDENADAS ---
    function generarMatrizSimulada() {
        matrizPresasContenedor.innerHTML = "";

        const letras = Array.from({ length: 11 }, (_, i) => String.fromCharCode(65 + i));
        let contadorLed = 1; // Mantiene tus IDs intactos (del 1 al 198)

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

        // FILAS 1 a 18: Líneas del panel
        for (let fila = 1; fila <= 18; fila++) {
            // Primer elemento de la fila: El número indicador de la izquierda
            const etiquetaNumero = document.createElement("div");
            etiquetaNumero.className = "eje-coordenada";
            etiquetaNumero.textContent = fila;
            matrizPresasContenedor.appendChild(etiquetaNumero);

            // Siguientes 11 elementos: Las presas reales de la línea (columnas A-K)
            for (let col = 0; col < 11; col++) {
                const idActual = contadorLed; // Guardamos el valor actual para el listener

                const nodo = document.createElement("div");
                nodo.className = "nodo-presa";
                nodo.id = `led-${idActual}`;
                nodo.textContent = idActual; // Muestra el número de LED asignado

                nodo.addEventListener("click", () => {
                    if (modoActual === "crear") {
                        gestionarClicNodoCreador(idActual);
                    }
                });

                matrizPresasContenedor.appendChild(nodo);
                contadorLed++;
            }
        }

        console.log(`🎮 Matriz generada con éxito. Total LEDs mapeados: ${contadorLed - 1}`);
    }

    // --- GESTIÓN DE NODOS EN MODO CONSTRUCTOR ---
    function gestionarClicNodoCreador(ledId) {
        const indice = ledsSeleccionadosCreacion.indexOf(ledId);
        if (indice === -1) {
            ledsSeleccionadosCreacion.push(ledId);
        } else {
            ledsSeleccionadosCreacion.splice(indice, 1);
        }
        actualizarNodosCreadorVisual();
        iluminarSeleccionFisica();
    }

    function actualizarNodosCreadorVisual() {
        document.querySelectorAll(".nodo-presa").forEach(nodo => {
            nodo.classList.remove("creando-activa");
        });

        ledsSeleccionadosCreacion.forEach(id => {
            const el = document.getElementById(`led-${id}`);
            if (el) el.classList.add("creando-activa");
        });

        contadorPresasBadge.textContent = `${ledsSeleccionadosCreacion.length} presas elegidas`;
        btnGuardarVia.disabled = ledsSeleccionadosCreacion.length === 0;
    }

    function iluminarSeleccionFisica() {
        if (ledsSeleccionadosCreacion.length === 0) {
            fetch("/api/hardware/apagar", { method: "POST" }).catch(() => {});
            return;
        }
        fetch("/api/hardware/encender-manual", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ leds: ledsSeleccionadosCreacion })
        }).catch(err => console.error("Error iluminando selección:", err));
    }

    function limpiarSeleccionCreador() {
        if (ledsSeleccionadosCreacion.length === 0) return;
        ledsSeleccionadosCreacion = [];
        contadorPresasBadge.textContent = "0 presas elegidas";
        btnGuardarVia.disabled = true;
        actualizarNodosCreadorVisual();
        fetch("/api/hardware/apagar", { method: "POST" }).catch(() => {});
    }

    btnLimpiarCreador.addEventListener("click", () => {
        ledsSeleccionadosCreacion = [];
        actualizarNodosCreadorVisual();
        iluminarSeleccionFisica();
    });

    // --- GUARDAR NUEVA VÍA (SUBIR A JAVALIN) ---
    formCrearVia.addEventListener("submit", (e) => {
        e.preventDefault();

        const payload = {
            nombre: txtNombreVia.value,
            grado: selGradoVia.value,
            equipador: usuario.nombre,
            usuario_correo: usuario.correo || usuario.email,
            leds: ledsSeleccionadosCreacion
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
                ledsSeleccionadosCreacion = [];
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
        let url = `/api/rutas?usuario=${usuario.correo || usuario.email}&estado=${filtroEstado}`;

        if (filtroEstado === "comunidad") {
            url = "/api/rutas";
        }

        fetch(url)
            .then(res => res.json())
            .then(rutas => {
                listaRutasContenedor.innerHTML = "";
                if (rutas.length === 0) {
                    const msg = document.createElement("p");
                    msg.className = "subtitulo";
                    msg.style.margin = "1rem auto";
                    msg.style.textAlignment = "center";
                    msg.textContent = "No hay vías disponibles en este bloque todavía.";
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
                if (data.status === "error") {
                    console.warn("⚠️ Hardware no disponible:", data.message);
                }
            })
            .catch(err => console.error("Error al iluminar ruta:", err));
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
        if (!confirm("⚠️ ¿Estás seguro de que quieres eliminar esta vía de tu lista de entrenamiento?")) return;

        fetch(`/api/rutas/${id}?usuario=${usuario.correo || usuario.email}`, { method: "DELETE" })
            .then(res => {
                if (!res.ok) throw new Error("No se pudo procesar el borrado");
                return res.json();
            })
            .then(data => {
                cargarCatalogoRutas();
            })
            .catch(err => alert("❌ Error al intentar eliminar la vía de la BBDD."));
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
        const ledsTest = Array.from({ length: 198 }, (_, i) => i + 1);
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

    const SIM_TOTAL = 198;
    const SIM_COLS = 11;
    const SIM_LIM_ALTA = 66;
    const SIM_LIM_MEDIA = 132;
    const SIM_CELL_CM = 24;
    const SIM_DIST_MIN = 30;
    const SIM_DIST_MAX = 130;
    const SIM_TICK_SEGUNDOS = 3;

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

        const letras = Array.from({ length: 11 }, (_, i) => String.fromCharCode(65 + i));

        // Esquina superior izquierda + cabecera de letras (A-K)
        matrizPulso.appendChild(document.createElement("div"));
        letras.forEach(letra => {
            const etiqueta = document.createElement("div");
            etiqueta.className = "eje-coordenada";
            etiqueta.textContent = letra;
            matrizPulso.appendChild(etiqueta);
        });

        // Filas 1-18: número a la izquierda + 11 presas
        let idLed = 1;
        for (let fila = 1; fila <= 18; fila++) {
            const etiquetaNumero = document.createElement("div");
            etiquetaNumero.className = "eje-coordenada";
            etiquetaNumero.textContent = fila;
            matrizPulso.appendChild(etiquetaNumero);

            for (let col = 0; col < 11; col++) {
                const nodo = document.createElement("div");
                nodo.className = "nodo-presa";
                nodo.id = `led-pulso-${idLed}`;
                nodo.textContent = idLed;
                matrizPulso.appendChild(nodo);
                idLed++;
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
                pintarZonaPulso(1, SIM_LIM_ALTA, "rojo");
            } else if (t < 4) {
                pintarZonaPulso(1, SIM_LIM_MEDIA, "rojo");
            } else if (t < 6) {
                pintarZonaPulso(1, SIM_TOTAL, "rojo");
            } else {
                // FASE 2: Juego en verde con apagado progresivo
                // Primer tick a los 9 s (igual que scheduleWithFixedDelay de Java: 3s tras la fase verde)
                if (simPulso.pendienteHasta > 0) {
                    // Los LEDs naranja permanecen 1 segundo antes de apagarse
                    if (t >= simPulso.pendienteHasta) {
                        completarApagadoSim();
                    }
                } else {
                    const tick = Math.floor((t - 6 - SIM_TICK_SEGUNDOS) / SIM_TICK_SEGUNDOS);
                    if (tick > simPulso.lastTick) {
                        simPulso.lastTick = tick;
                        const accion = seleccionarAccionSim();
                        if (accion) {
                            simPulso.ledsNaranja = new Set(accion.aApagar);
                            simPulso.ledAEncender = accion.aEncender;
                            simPulso.pendienteHasta = t + 1;
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
        // Los LEDs en fase de apagado se superponen en naranja (1 segundo)
        simPulso.ledsNaranja.forEach(i => {
            const el = document.getElementById(`led-pulso-${i}`);
            if (el) el.classList.add("naranja");
        });
    }

    // Lógica espejo del service Java -------------------------------------

    function zonaDeSim(led) {
        if (led <= SIM_LIM_ALTA) return 1;
        if (led <= SIM_LIM_MEDIA) return 2;
        return 3;
    }

    function contarPorZonaSim(zona, estado) {
        let contador = 0;
        estado.forEach(led => {
            if (zonaDeSim(led) === zona) contador++;
        });
        return contador;
    }

    function distanciaCmSim(ledA, ledB) {
        const filaA = Math.floor((ledA - 1) / SIM_COLS);
        const colA = (ledA - 1) % SIM_COLS;
        const filaB = Math.floor((ledB - 1) / SIM_COLS);
        const colB = (ledB - 1) % SIM_COLS;
        const dx = (colA - colB) * SIM_CELL_CM;
        const dy = (filaA - filaB) * SIM_CELL_CM;
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
        const inicio = zona === 1 ? 1 : zona === 2 ? SIM_LIM_ALTA + 1 : SIM_LIM_MEDIA + 1;
        const fin = zona === 1 ? SIM_LIM_ALTA : zona === 2 ? SIM_LIM_MEDIA : SIM_TOTAL;

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