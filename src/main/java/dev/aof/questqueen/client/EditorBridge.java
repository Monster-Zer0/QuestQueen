package dev.aof.questqueen.client;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.aof.questqueen.QuestQueen;
import dev.aof.questqueen.data.Chapter;
import dev.aof.questqueen.data.QuestPack;
import dev.aof.questqueen.net.AuthorSaveC2S;
import dev.aof.questqueen.net.QuestNetwork;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

public final class EditorBridge {
    public static final int PORT = 47821;
    /**
     * QQ-2 (Picard's shape ruling 2026-09-18, option c): the Origin check stops a hostile PAGE,
     * but it also exempts a request with NO Origin, and that exemption made the write path
     * reachable by anything that simply omits the header. Writes now additionally require a
     * per-session nonce that only this process mints and only a page served by this bridge can
     * read. Threat model, named: the browser. A local process was never in it (anything local
     * can already do anything local).
     */
    private static final String NONCE_HEADER = "X-Editor-Nonce";
    private static final SecureRandom NONCE_RANDOM = new SecureRandom();
    private static volatile String sessionNonce;
    private static final AtomicReference<String> CATALOG = new AtomicReference<>("{\"items\":[]}");
    private static final AtomicReference<String> PACK = new AtomicReference<>("{\"chapters\":[],\"scrolls\":[]}");
    private static HttpServer server;
    private static ExecutorService executor;
    /** The pack PACK was last encoded from; re-encode only when the client receives a new one. */
    private static Object encodedPack;
    /** Largest chapter body the bridge forwards. AuthorSaveC2S carries the same bound. */
    static final int MAX_CHAPTER_BYTES = AuthorSaveC2S.MAX_JSON_BYTES;
    private static boolean enabled;
    private static int ticks;

    private EditorBridge() {
    }

    public static synchronized void setEnabled(boolean next) {
        if (next) {
            if (!start()) {
                enabled = false;
                return;
            }
            enabled = true;
            return;
        }
        enabled = false;
        stop();
    }

    public static boolean enabled() {
        return enabled;
    }

    public static void tick(Minecraft minecraft) {
        try {
            if (minecraft.player == null && enabled) {
                setEnabled(false);
                return;
            }
            if (!enabled || minecraft.player == null) {
                return;
            }
            ticks++;
            // The item catalog is fixed for the session; only the pack changes (reloads, saves).
            if (ticks % 40 == 1 && ClientQuestState.pack != encodedPack) {
                refreshPack();
            }
        } catch (Exception exception) {
            QuestQueen.LOGGER.error("Quest editor tick failed; disabling editor", exception);
            fail("questqueen.editor.fail");
        }
    }

    private static boolean start() {
        stop();
        try {
            if (!refreshSnapshots()) {
                notifyPlayer("questqueen.editor.fail");
                return false;
            }
            // Mint the nonce before the socket opens: openUri below can load index.html before this method
            // returns, and a page served without the nonce answers 403 on every save until reloaded.
            sessionNonce = newNonce();
            HttpServer http = HttpServer.create(new InetSocketAddress("127.0.0.1", PORT), 0);
            http.createContext("/", EditorBridge::handle);
            executor = Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(runnable, "questqueen-editor");
                thread.setDaemon(true);
                return thread;
            });
            http.setExecutor(executor);
            http.start();
            server = http;
            String url = "http://127.0.0.1:" + PORT + "/";
            notifyPlayer("questqueen.editor.url", itemCount(), url);
            Util.getPlatform().openUri(URI.create(url));
            QuestQueen.LOGGER.info("Quest Queen editor listening on {} with {} items", url, itemCount());
            return true;
        } catch (Exception exception) {
            QuestQueen.LOGGER.error("Could not start quest editor on 127.0.0.1:{}", PORT, exception);
            stop();
            notifyPlayer("questqueen.editor.fail");
            return false;
        }
    }

    private static boolean refreshSnapshots() {
        try {
            CATALOG.set(PackCatalogJson.create());
            ItemIconPng.clear();
            return refreshPack();
        } catch (Exception exception) {
            QuestQueen.LOGGER.error("Failed to build quest editor catalog", exception);
            return false;
        }
    }

    private static boolean refreshPack() {
        try {
            Object pack = ClientQuestState.pack;
            PACK.set(QuestPack.CODEC.encodeStart(JsonOps.INSTANCE, ClientQuestState.pack)
                    .getOrThrow(RuntimeException::new)
                    .toString());
            encodedPack = pack;
            return true;
        } catch (Exception exception) {
            QuestQueen.LOGGER.error("Failed to encode the quest pack for the editor", exception);
            return false;
        }
    }

    private static void fail(String messageKey) {
        enabled = false;
        stop();
        notifyPlayer(messageKey);
    }

    private static void notifyPlayer(String key, Object... args) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            minecraft.player.displayClientMessage(Component.translatable(key, args), false);
        }
    }

    private static void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
        // HttpServer.stop does not shut down an executor it was handed.
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
        encodedPack = null;
        // The nonce dies with the session it was minted for: a stale value can never be replayed,
        // and with no live session the write gate below fails closed.
        sessionNonce = null;
    }

    private static void handle(HttpExchange exchange) throws IOException {
        if (!loopback(exchange)) {
            write(exchange, 403, "text/plain", "loopback only");
            return;
        }
        // A DNS-rebound page is same-origin with its own hostname, so its GETs carry no Origin header and
        // pass the check below. The Host header still names the attacker's host; refuse anything that is
        // not this bridge's own loopback address.
        if (!hostAllowed(exchange.getRequestHeaders().getFirst("Host"))) {
            write(exchange, 403, "text/plain", "host not allowed");
            return;
        }
        // QQ-2: loopback proves WHERE a request came from, not WHO sent it. A DNS-rebinding
        // request genuinely arrives from loopback, so the check above cannot stop a page the
        // player happens to be visiting. Origin is the control that does.
        String origin = exchange.getRequestHeaders().getFirst("Origin");
        if (!originAllowed(origin)) {
            write(exchange, 403, "text/plain", "origin not allowed");
            return;
        }
        // QQ-2: every MUTATING verb must also present the session nonce. This is the gate that
        // makes the vacant-Origin exemption stop being a write path — a request with no Origin at
        // all is still refused here unless it proves it holds the session.
        if (isMutating(exchange.getRequestMethod()) && !nonceAccepted(exchange)) {
            write(exchange, 403, "application/json",
                    "{\"ok\":false,\"error\":\"editor session nonce required\"}");
            return;
        }
        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            if (origin != null && !origin.isBlank()) {
                exchange.getResponseHeaders().add("Access-Control-Allow-Origin", origin.trim());
                exchange.getResponseHeaders().add("Vary", "Origin");
            }
            exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET,POST,OPTIONS");
            // Only reached by an origin the check above allowed; a hostile preflight was already
            // refused. A custom header cross-origin forces this preflight, which is why the nonce
            // cannot be sent by a remote page that has not been allowed.
            exchange.getResponseHeaders().add("Access-Control-Allow-Headers",
                    "Content-Type, " + NONCE_HEADER);
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
            return;
        }
        String path = exchange.getRequestURI().getPath();
        if ("/api/status".equals(path)) {
            write(exchange, 200, "application/json",
                    "{\"enabled\":" + enabled + ",\"items\":" + itemCount() + "}");
            return;
        }
        if ("/api/session".equals(path)) {
            // Read gates only, unchanged: loopback + Origin. A page on another origin cannot read
            // this (same-origin policy), and a rebinding page is refused before it gets here.
            String nonce = sessionNonce;
            if (nonce == null || nonce.isEmpty()) {
                write(exchange, 403, "application/json", "{\"ok\":false,\"error\":\"editor session is off\"}");
                return;
            }
            write(exchange, 200, "application/json", "{\"nonce\":" + jsonString(nonce) + "}");
            return;
        }
        if ("/api/catalog".equals(path)) {
            write(exchange, 200, "application/json", CATALOG.get());
            return;
        }
        if (path.startsWith("/api/icon")) {
            String id = queryParam(exchange.getRequestURI().getRawQuery(), "id");
            byte[] png = ItemIconPng.pngFor(id);
            writeBytes(exchange, 200, "image/png", png);
            return;
        }
        if ("/api/chapters".equals(path) && "GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            write(exchange, 200, "application/json", PACK.get());
            return;
        }
        if ("/api/chapter".equals(path) && "POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            handleChapterSave(exchange);
            return;
        }
        serveStatic(exchange, path);
    }

    /**
     * Save a chapter from the browser editor. The browser POST lands on the client thread's bridge,
     * so validate here before forwarding: Chapter.CODEC.parse is the same check the server applies,
     * and the author session is the same permission gate. Answers 400/403 on a rejected save instead
     * of always reporting success.
     */
    private static void handleChapterSave(HttpExchange exchange) throws IOException {
        if (!enabled) {
            write(exchange, 403, "application/json", "{\"ok\":false,\"error\":\"editor session is off\"}");
            return;
        }
        if (!ClientQuestState.progress.canAuthor()) {
            write(exchange, 403, "application/json",
                    "{\"ok\":false,\"error\":\"not in an authoring session on the server\"}");
            return;
        }
        byte[] raw = exchange.getRequestBody().readNBytes(MAX_CHAPTER_BYTES + 1);
        if (raw.length > MAX_CHAPTER_BYTES) {
            write(exchange, 413, "application/json",
                    "{\"ok\":false,\"error\":\"chapter is larger than " + MAX_CHAPTER_BYTES / 1024 + " KB\"}");
            return;
        }
        String body = new String(raw, StandardCharsets.UTF_8);
        Chapter chapter;
        try {
            chapter = Chapter.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(body))
                    .getOrThrow(RuntimeException::new);
        } catch (Exception exception) {
            QuestQueen.LOGGER.error("Editor save rejected", exception);
            write(exchange, 400, "application/json",
                    "{\"ok\":false,\"error\":" + jsonString(String.valueOf(exception.getMessage())) + "}");
            return;
        }
        // Accepting only means "queued and validated". The datapack write happens later on the
        // server, so report the real status code rather than claiming the file is on disk.
        Minecraft.getInstance().execute(() -> QuestNetwork.sendToServer(new AuthorSaveC2S(body)));
        write(exchange, 202, "application/json", "{\"ok\":true,\"queued\":true,\"id\":"
                + jsonString(chapter.id().toString()) + "}");
    }

    /** Minimal JSON string quoting for an error message we are about to hand to the browser. */
    private static String jsonString(String raw) {
        StringBuilder quoted = new StringBuilder(raw.length() + 2).append('"');
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            switch (c) {
                case '"' -> quoted.append("\\\"");
                case '\\' -> quoted.append("\\\\");
                case '\n' -> quoted.append("\\n");
                case '\r' -> quoted.append("\\r");
                case '\t' -> quoted.append("\\t");
                default -> {
                    if (c < 0x20) {
                        quoted.append(String.format("\\u%04x", (int) c));
                    } else {
                        quoted.append(c);
                    }
                }
            }
        }
        return quoted.append('"').toString();
    }

    private static void serveStatic(HttpExchange exchange, String path) throws IOException {
        String relative = path.equals("/") ? "index.html" : path.substring(1);
        if (relative.contains("..")) {
            write(exchange, 400, "text/plain", "bad path");
            return;
        }
        InputStream stream = EditorBridge.class.getResourceAsStream("/assets/questqueen/web/" + relative);
        if (stream == null && "index.html".equals(relative)) {
            write(exchange, 200, "text/html", FALLBACK_HTML);
            return;
        }
        if (stream == null) {
            write(exchange, 404, "text/plain", "missing");
            return;
        }
        byte[] bytes = stream.readAllBytes();
        stream.close();
        // QQ-2: the page this bridge serves needs the nonce in order to keep saving. The ruling's
        // shape is single-file, so the bridge injects it here rather than editing the web assets:
        // a tag the page can read plus a fetch wrapper that tags non-GET requests, all guarded.
        if ("index.html".equals(relative) && sessionNonce != null) {
            String html = new String(bytes, StandardCharsets.UTF_8);
            String tag = "<script>window.__QQ_EDITOR_NONCE__=" + jsonString(sessionNonce) + ";"
                    + "(function(){var f=window.fetch;if(!f){return;}"
                    + "window.fetch=function(i,o){o=o||{};var s=(o.method||'GET').toUpperCase();"
                    + "if(s!=='GET'&&s!=='HEAD'&&s!=='OPTIONS'){try{"
                    + "o.headers=new Headers(o.headers||{});"
                    + "o.headers.set('" + NONCE_HEADER + "',window.__QQ_EDITOR_NONCE__);"
                    // try{} catch{} closes the if; one more brace here closed the function early and made
                    // the whole tag a SyntaxError, so the wrapper never installed.
                    + "}catch(e){}}return f.call(this,i,o);};})();</script>";
            int at = html.lastIndexOf("</head>");
            html = at >= 0 ? html.substring(0, at) + tag + html.substring(at) : tag + html;
            bytes = html.getBytes(StandardCharsets.UTF_8);
        }
        writeBytes(exchange, 200, contentType(relative), bytes);
    }

    private static String queryParam(String query, String key) {
        if (query == null || query.isBlank()) {
            return "";
        }
        for (String part : query.split("&")) {
            int eq = part.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String name = part.substring(0, eq);
            if (!name.equals(key)) {
                continue;
            }
            try {
                return java.net.URLDecoder.decode(part.substring(eq + 1), StandardCharsets.UTF_8);
            } catch (Exception ignored) {
                return part.substring(eq + 1);
            }
        }
        return "";
    }

    private static boolean loopback(HttpExchange exchange) {
        return exchange.getRemoteAddress().getAddress().isLoopbackAddress();
    }

    /**
     * Is this request's Origin one this bridge serves? An ABSENT Origin means a non-browser client
     * (curl, a script) which no page can drive, so it is allowed. A PRESENT Origin must be a
     * loopback page — that is what the served editor (127.0.0.1:47821) and the Vite dev UI
     * (127.0.0.1:4173) are.
     *
     * The host is compared LITERALLY and never resolved. A lookup-based allowlist would re-admit
     * exactly what this check exists to stop: a name that resolves to 127.0.0.1 is what DNS
     * rebinding produces, so resolving the Origin host would answer "allowed" to the attacker's
     * own name and the check would be decorative.
     */
    /** Host header must be this bridge's own loopback authority; a missing header (HTTP/1.0 tools) is allowed. */
    static boolean hostAllowed(String host) {
        if (host == null || host.isBlank()) {
            return true;
        }
        String h = host.trim().toLowerCase(java.util.Locale.ROOT);
        return h.equals("127.0.0.1:" + PORT) || h.equals("localhost:" + PORT) || h.equals("[::1]:" + PORT);
    }

    static boolean originAllowed(String origin) {
        if (origin == null || origin.isBlank()) {
            return true;
        }
        URI uri;
        try {
            uri = URI.create(origin.trim());
        } catch (IllegalArgumentException exception) {
            return false;
        }
        String scheme = uri.getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            return false;
        }
        String host = uri.getHost();
        if (host == null) {
            return false;
        }
        return "localhost".equalsIgnoreCase(host)
                || "127.0.0.1".equals(host)
                || "::1".equals(host)
                || "[::1]".equals(host);
    }

    private static int itemCount() {
        try {
            return JsonParser.parseString(CATALOG.get()).getAsJsonObject().getAsJsonArray("items").size();
        } catch (Exception ignored) {
            return 0;
        }
    }

    /** A fresh nonce per editor session: 32 bytes from a CSPRNG, hex, never reused. */
    private static String newNonce() {
        byte[] bytes = new byte[32];
        NONCE_RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private static boolean isMutating(String method) {
        return "POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method)
                || "PATCH".equalsIgnoreCase(method) || "DELETE".equalsIgnoreCase(method);
    }

    /**
     * Constant-time comparison of the presented session nonce, and it FAILS CLOSED: an absent or
     * blank presented value is a refusal, and so is an empty expected value (no live session), so
     * a write can never be authorised by the absence of both sides.
     */
    private static boolean nonceAccepted(HttpExchange exchange) {
        String expected = sessionNonce;
        if (expected == null || expected.isEmpty()) {
            return false;
        }
        String presented = exchange.getRequestHeaders().getFirst(NONCE_HEADER);
        if (presented == null || presented.isBlank()) {
            return false;
        }
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                presented.trim().getBytes(StandardCharsets.UTF_8));
    }

    private static void write(HttpExchange exchange, int status, String type, String body) throws IOException {
        writeBytes(exchange, status, type, body.getBytes(StandardCharsets.UTF_8));
    }

    private static void writeBytes(HttpExchange exchange, int status, String type, byte[] body) throws IOException {
        // Echo only an origin that passed the check; never advertise a wildcard, or a page on any
        // origin can READ what this bridge serves, pack contents included.
        String origin = exchange.getRequestHeaders().getFirst("Origin");
        if (origin != null && !origin.isBlank() && originAllowed(origin)) {
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", origin.trim());
        }
        exchange.getResponseHeaders().add("Vary", "Origin");
        // Q2-2 (lands with the nonce, no behaviour change): a JSON or HTML body must not be
        // sniffed into an executable type by a client that trusts its own content guess.
        exchange.getResponseHeaders().add("X-Content-Type-Options", "nosniff");
        exchange.getResponseHeaders().add("Content-Type", type);
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(body);
        }
    }

    private static String contentType(String path) {
        if (path.endsWith(".js")) {
            return "application/javascript";
        }
        if (path.endsWith(".css")) {
            return "text/css";
        }
        if (path.endsWith(".json")) {
            return "application/json";
        }
        if (path.endsWith(".html")) {
            return "text/html; charset=utf-8";
        }
        return "application/octet-stream";
    }

    private static final String FALLBACK_HTML = """
            <!doctype html><html><head><meta charset="utf-8"><title>Quest Queen Editor</title>
            <style>body{background:#1a1028;color:#f2e6a6;font-family:monospace;padding:40px}</style></head>
            <body><h1>QUEST EDITOR</h1>
            <p>The browser editor is served from Minecraft after <code>/questqueen editor</code>.</p>
            <p>If you are developing the Vite UI, open <a href="http://127.0.0.1:4173/">http://127.0.0.1:4173/</a> — it will unlock against this pack.</p>
            </body></html>
            """;
}
