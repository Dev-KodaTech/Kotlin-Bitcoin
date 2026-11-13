// The :ui tests read Compose Resources, which the browser fetches at /composeResources/... (W-18).
// Karma serves only what is listed in `files`, under /base/, so the processed resources are added and proxied.
// basePath is web/build/wasm/packages/<module>-test; the resources live in web/ui/build/processedResources.
const path = require("path");
const resources = path.resolve(config.basePath, "../../../../ui/build/processedResources/wasmJs/main/composeResources");
config.files.push({ pattern: resources + "/**/*", included: false, served: true, watched: false, nocache: true });
config.proxies["/composeResources/"] = "/absolute" + resources + "/";
