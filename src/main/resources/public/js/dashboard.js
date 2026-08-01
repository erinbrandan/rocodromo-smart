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
    let modoActual = "entrenar";       // "entrenar" o "crear"
    let filtroEstado = "proyecto";     // "proyecto", "encadenada" o "comunidad"
    let ledsSeleccionadosCreacion = []; // Guarda los LEDs que pulsemos en modo Builder
    let rutaSeleccionadaId = null;     // Guarda la ID de la ruta activa en el panel

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
    const seccionEntrenar = document.getElementById("seccion-entrenar");
    const seccionCrear = document.getElementById("seccion-crear");

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

    // Seteamos el nombre del usuario en la cabecera
    nombreUsuarioHeader.textContent = usuario.nombre;

    // Inicializamos el panel de control al arrancar la vista
    verificarEstadoSistema();
    generarMatrizSimulada();
    cargarCatalogoRutas();

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
        contenedorAccionVia.style.display = "none";
        rutaSeleccionadaId = null;
        cargarCatalogoRutas();
    });

    tabCrear.addEventListener("click", () => {
        modoActual = "crear";
        tabCrear.classList.add("activa");
        tabEntrenar.classList.remove("activa");
        seccionCrear.classList.add("activa");
        seccionEntrenar.classList.remove("activa");

        tituloMatriz.textContent = "🛠️ Diseñando Nueva Vía";
        subtituloMatriz.textContent = "Haz clic en los círculos para marcar presas (Color Azul)";

        apagarTodosLosNodosVisuales();
        contenedorAccionVia.style.display = "none";
        rutaSeleccionadaId = null;
        actualizarNodosCreadorVisual();
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

        const letras = ["A", "B", "C", "D", "E", "F"];
        let contadorLed = 1; // Mantiene tus IDs intactos (del 1 al 108)

        // FILA 0: Cabecera de letras superiores
        // Esquina superior izquierda (intersección vacía)
        const esquinaVacia = document.createElement("div");
        matrizPresasContenedor.appendChild(esquinaVacia);

        // Inyectamos las letras de la A a la F
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

            // Siguientes 6 elementos: Las presas reales de la línea (columnas A-F)
            for (let col = 0; col < 6; col++) {
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
        const ledsTest = [1, 2, 3, 15, 16, 17];
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
});