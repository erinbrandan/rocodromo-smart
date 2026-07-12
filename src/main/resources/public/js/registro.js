document.addEventListener("DOMContentLoaded", () => {
    if (localStorage.getItem("sesion_usuario")) {
        window.location.href = "/dashboard.html";
        return;
    }

    const formRegistro = document.getElementById("form-registro");

    formRegistro.addEventListener("submit", (e) => {
        e.preventDefault();
        const nombre = document.getElementById("reg-nombre").value;
        const apellidos = document.getElementById("reg-apellidos").value;
        const correo = document.getElementById("reg-correo").value;
        const password = document.getElementById("reg-password").value;

        // Petición real de registro
        fetch("/api/usuarios/registro", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({
                nombre: nombre,
                apellidos: apellidos,
                correo: correo,
                contrasena: password // 'contrasena' es lo que leerá Java para meter en el modelo
            })
        })
            .then(res => {
                if (!res.ok) throw new Error("Error en el registro");
                return res.json();
            })
            .then(data => {
                if (data.status === "success") {
                    alert("✨ ¡Cuenta creada con éxito! Iniciando sesión...");
                    // Iniciamos sesión automáticamente tras registrarse exitosamente
                    localStorage.setItem("sesion_usuario", JSON.stringify({
                        correo: correo,
                        nombre: nombre
                    }));
                    window.location.href = "/dashboard.html";
                }
            })
            .catch(err => {
                alert("❌ Error al registrar: El correo ya existe o los datos son inválidos.");
                console.error(err);
            });
    });
});