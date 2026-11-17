// Browser check before loading the app (W-16). Kotlin/Wasm needs WasmGC and Wasm exception handling (W-01).
// External file, so it works under the CSP (no inline script). Loads app.js only when both are supported.
(function () {
    "use strict";
    // (module (type (struct))) — a GC struct type.
    var gcModule = new Uint8Array([0, 97, 115, 109, 1, 0, 0, 0, 1, 3, 1, 95, 0]);
    // (module (type (func)) (tag (type 0))) — an exception tag.
    var exceptionModule = new Uint8Array([0, 97, 115, 109, 1, 0, 0, 0, 1, 4, 1, 96, 0, 0, 13, 3, 1, 0, 0]);

    function supported() {
        try {
            return typeof WebAssembly === "object" &&
                WebAssembly.validate(gcModule) === true &&
                WebAssembly.validate(exceptionModule) === true;
        } catch (e) {
            return false;
        }
    }

    if (!supported()) {
        var boot = document.getElementById("boot");
        var message = document.getElementById("boot-message");
        if (boot) boot.className = "unsupported";
        if (message) message.textContent = "Seu navegador não é compatível. Atualize para a versão mais recente.";
        return;
    }
    var script = document.createElement("script");
    script.src = "app.js";
    document.body.appendChild(script);
})();
