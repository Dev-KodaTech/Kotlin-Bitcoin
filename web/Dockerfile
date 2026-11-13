# syntax=docker/dockerfile:1
# Web client image (research.md W-15). Build context: web/.

# 1. Kotlin/Wasm production distribution.
FROM gradle:jdk21 AS build
# The Node.js that the Kotlin plugin downloads for wasmJs needs libatomic, missing from this image.
RUN apt-get update && apt-get install -y --no-install-recommends libatomic1 && rm -rf /var/lib/apt/lists/*
WORKDIR /workspace
COPY . .
RUN ./gradlew :app:wasmJsBrowserDistribution --no-daemon

# 2. Preload links for the hashed .wasm files, then maximum pre-compression (served by gzip_static/brotli_static).
FROM alpine:3.21 AS compress
RUN apk add --no-cache brotli gzip
COPY --from=build /workspace/app/build/dist/wasmJs/productionExecutable /dist
COPY docker/inject-preload.sh /usr/local/bin/inject-preload.sh
RUN rm -f /dist/*.map \
    && inject-preload.sh /dist \
    && find /dist -type f \( -name '*.wasm' -o -name '*.js' -o -name '*.mjs' -o -name '*.css' -o -name '*.html' \
        -o -name '*.json' -o -name '*.ttf' \) -exec gzip -9 -k {} \; -exec brotli -q 11 -k {} \;

# 3. ngx_brotli as a dynamic module, compiled against the source of the exact nginx version of the final image.
FROM nginxinc/nginx-unprivileged:1.27-alpine AS brotli-module
USER root
ARG NGX_BROTLI_COMMIT=53e0fb55f250f48fe80c0ddbf6c0f7103cdbaae1
RUN apk add --no-cache git gcc g++ libc-dev make cmake linux-headers openssl-dev pcre2-dev zlib-dev \
    && version="$(nginx -v 2>&1 | sed 's#^.*/##')" \
    && mkdir /build && cd /build \
    && wget -q "https://nginx.org/download/nginx-${version}.tar.gz" \
    && tar xzf "nginx-${version}.tar.gz" \
    && git clone https://github.com/google/ngx_brotli.git \
    && cd ngx_brotli && git checkout "$NGX_BROTLI_COMMIT" && git submodule update --init --depth 1 \
    && cd deps/brotli && mkdir out && cd out \
    && cmake -DCMAKE_BUILD_TYPE=Release -DBUILD_SHARED_LIBS=OFF -DCMAKE_C_FLAGS="-O2 -fPIC" .. \
    && cmake --build . --target brotlienc \
    && cd "/build/nginx-${version}" \
    && ./configure --with-compat --add-dynamic-module=/build/ngx_brotli \
    && make modules \
    && mkdir /modules && cp objs/ngx_http_brotli_filter_module.so objs/ngx_http_brotli_static_module.so /modules/

# 4. Final image: unprivileged nginx (port 8080), no build tools.
FROM nginxinc/nginx-unprivileged:1.27-alpine
COPY --from=brotli-module /modules/ /usr/lib/nginx/modules/
COPY docker/nginx.conf /etc/nginx/nginx.conf
COPY docker/security-headers.conf /etc/nginx/security-headers.conf
COPY --from=compress /dist /usr/share/nginx/html
EXPOSE 8080
