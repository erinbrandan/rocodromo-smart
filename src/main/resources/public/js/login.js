document.addEventListener("DOMContentLoaded", () => {
    if (localStorage.getItem("sesion_usuario")) {
        window.location.href = "/dashboard.html";
        return;
    }

    const formLogin = document.getElementById("form-login");

    formLogin.addEventListener("submit", (e) => {
        e.preventDefault();
        const correo = document.getElementById("login-correo").value;
        const password = document.getElementById("login-password").value;

        // Petición real al Backend de Javalin
        fetch("/api/usuarios/login", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ correo: correo, password: password })
        })
            .then(res => {
                if (!res.ok) throw new Error("Credenciales inválidas");
                return res.json();
            })
            .then(data => {
                if (data.status === "success") {
                    // Guardamos en el localStorage el nombre real que viene de la BBDD
                    localStorage.setItem("sesion_usuario", JSON.stringify({
                        correo: data.correo,
                        nombre: data.nombre
                    }));
                    window.location.href = "/dashboard.html";
                }
            })
            .catch(err => {
                alert("❌ Error: Correo o contraseña incorrectos.");
                console.error(err);
            });
    });
});