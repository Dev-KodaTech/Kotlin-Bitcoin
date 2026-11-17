// Development server of :app (T096): port 8082 (8080 is the API's nginx, 8081 the client's), /api proxied to the
// API's nginx so the browser sees one origin, and the production CSP of research.md W-15 (with the hash of the
// <style> that Compose 1.12.2 injects into its shadow root; it changes when that style changes).
const composeShadowStyle = "'sha256-2DtT88WYuzB8DiHdoLqEYkONEwxf9gqsOdTY44QuLA4='";
config.devServer = config.devServer || {};
config.devServer.port = 8082;
config.devServer.proxy = [{ context: ["/api"], target: "http://localhost:8080", changeOrigin: false }];
config.devServer.headers = {
    "Content-Security-Policy":
        "default-src 'self'; script-src 'self' 'wasm-unsafe-eval'; style-src 'self' " + composeShadowStyle + "; " +
        "img-src 'self' data: blob:; font-src 'self'; connect-src 'self'; worker-src 'self' blob:; " +
        "object-src 'none'; base-uri 'none'; form-action 'none'; frame-ancestors 'none'",
    "X-Content-Type-Options": "nosniff",
    "Referrer-Policy": "no-referrer",
};

// The development bundle must also run under that CSP: no eval-based source maps and no hot-reload client
// (its websocket goes outside connect-src 'self'). Production bundles never use either, and their source map is
// already configured by the Kotlin plugin (setting devtool there too emits app.js.map twice).
if (config.mode === "development") {
    config.devtool = "source-map";
    config.plugins = config.plugins.filter((plugin) => plugin.constructor.name !== "EvalSourceMapDevToolPlugin");
    config.devServer.hot = false;
    config.devServer.liveReload = false;
    config.devServer.client = false;
}
