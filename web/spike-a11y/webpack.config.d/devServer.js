// Spike dev server (T015): port 8082 (8080 is the API's nginx, 8081 the client's) and the exact CSP of
// research.md W-15, so the spike proves the production policy works with Compose/Skiko.
// The only addition is the hash of the <style> that Compose 1.12.2 injects into its shadow root (without it the
// hidden IME input and the canvas sizing rules are blocked). The hash changes when that style changes (CMP update).
const composeShadowStyle = "'sha256-2DtT88WYuzB8DiHdoLqEYkONEwxf9gqsOdTY44QuLA4='";
config.devServer = config.devServer || {};
config.devServer.port = 8082;
config.devServer.headers = {
    "Content-Security-Policy":
        "default-src 'self'; script-src 'self' 'wasm-unsafe-eval'; style-src 'self' " + composeShadowStyle + "; " +
        "img-src 'self' data: blob:; font-src 'self'; connect-src 'self'; worker-src 'self' blob:; " +
        "object-src 'none'; base-uri 'none'; form-action 'none'; frame-ancestors 'none'",
    "X-Content-Type-Options": "nosniff",
    "Referrer-Policy": "no-referrer",
};

// The development bundle must also run under that CSP: no eval-based source maps (the Kotlin plugin adds an
// EvalSourceMapDevToolPlugin) and no hot-reload client
// (its websocket goes to 0.0.0.0, outside connect-src 'self'). Production bundles never use either.
config.devtool = "source-map";
config.plugins = config.plugins.filter((plugin) => plugin.constructor.name !== "EvalSourceMapDevToolPlugin");
config.devServer.hot = false;
config.devServer.liveReload = false;
config.devServer.client = false;
