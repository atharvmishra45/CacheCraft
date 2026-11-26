package campuscache;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * CampusCacheServer:
 * Lightweight HTTP server serving:
 * 1. REST API endpoints for GET, PUT, STATE, INIT, RESET, SEQUENCE, DEMO.
 * 2. Static web dashboard assets (HTML, CSS, JS).
 *
 * Uses built-in Java SE com.sun.net.httpserver: zero external frameworks or heavy dependencies!
 */
public class CampusCacheServer {

    private static final int DEFAULT_PORT = 8080;
    private final CampusCache cache;
    private final Path webRoot;

    public CampusCacheServer(int initialCapacity, Path webRoot) {
        this.cache = new CampusCache(initialCapacity);
        this.webRoot = webRoot;
    }

    public void start(int port) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        // API routes
        server.createContext("/api/get", new GetHandler());
        server.createContext("/api/put", new PutHandler());
        server.createContext("/api/state", new StateHandler());
        server.createContext("/api/init", new InitHandler());
        server.createContext("/api/reset", new ResetHandler());
        server.createContext("/api/sequence", new SequenceHandler());
        server.createContext("/api/demo", new DemoHandler());

        // Static files handler
        server.createContext("/", new StaticFileHandler(webRoot));

        server.setExecutor(null); // default executor
        server.start();

        System.out.println("========================================================");
        System.out.println("  CampusCache Server started successfully!");
        System.out.println("  Port: " + port);
        System.out.println("  Dashboard URL: http://localhost:" + port);
        System.out.println("  Web assets: " + webRoot.toAbsolutePath());
        System.out.println("========================================================");
    }

    private static void sendJsonResponse(HttpExchange exchange, int statusCode, String jsonResponse) throws IOException {
        byte[] bytes = jsonResponse.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");

        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(204, -1);
            return;
        }

        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static String readRequestBody(HttpExchange exchange) throws IOException {
        try (InputStream is = exchange.getRequestBody();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            byte[] buf = new byte[1024];
            int read;
            while ((read = is.read(buf)) != -1) {
                baos.write(buf, 0, read);
            }
            return baos.toString(StandardCharsets.UTF_8);
        }
    }

    private class GetHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, 204, "");
                return;
            }
            String key = null;
            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                String body = readRequestBody(exchange);
                Map<String, String> map = JsonUtils.parseSimpleJson(body);
                key = map.get("key");
            } else {
                // Query param ?key=...
                String query = exchange.getRequestURI().getQuery();
                if (query != null) {
                    for (String part : query.split("&")) {
                        String[] kv = part.split("=");
                        if (kv.length >= 2 && kv[0].equalsIgnoreCase("key")) {
                            key = java.net.URLDecoder.decode(kv[1], StandardCharsets.UTF_8);
                        }
                    }
                }
            }

            CampusCache.OpResult result = cache.get(key);
            sendJsonResponse(exchange, 200, JsonUtils.opResultToJson(result));
        }
    }

    private class PutHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, 204, "");
                return;
            }
            String body = readRequestBody(exchange);
            Map<String, String> map = JsonUtils.parseSimpleJson(body);
            String key = map.get("key");
            String value = map.get("value");

            CampusCache.OpResult result = cache.put(key, value);
            sendJsonResponse(exchange, 200, JsonUtils.opResultToJson(result));
        }
    }

    private class StateHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, 204, "");
                return;
            }
            CampusCache.CacheState state = cache.getState();
            sendJsonResponse(exchange, 200, JsonUtils.cacheStateToJson(state));
        }
    }

    private class InitHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, 204, "");
                return;
            }
            String body = readRequestBody(exchange);
            Map<String, String> map = JsonUtils.parseSimpleJson(body);
            int capacity = 5;
            try {
                if (map.containsKey("capacity")) {
                    capacity = Integer.parseInt(map.get("capacity"));
                }
            } catch (NumberFormatException ignored) {}

            if (capacity < 0 || capacity > 200) {
                sendJsonResponse(exchange, 400, "{\"error\":\"Capacity must be between 0 and 200\"}");
                return;
            }

            cache.reinitialize(capacity);
            sendJsonResponse(exchange, 200, "{\"success\":true,\"capacity\":" + capacity + ",\"message\":\"Cache initialized with capacity " + capacity + "\"}");
        }
    }

    private class ResetHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, 204, "");
                return;
            }
            int cap = cache.getCapacity();
            cache.reinitialize(cap);
            sendJsonResponse(exchange, 200, "{\"success\":true,\"capacity\":" + cap + ",\"message\":\"Cache reset successfully\"}");
        }
    }

    private class SequenceHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, 204, "");
                return;
            }
            String body = readRequestBody(exchange);
            Map<String, String> map = JsonUtils.parseSimpleJson(body);
            String sequenceText = map.get("sequence");
            if (sequenceText == null) sequenceText = "";

            // The sequence text may have escaped newlines if passed in JSON
            sequenceText = sequenceText.replace("\\n", "\n").replace("\\r", "");

            List<CommandParser.Command> commands = CommandParser.parseSequence(sequenceText);
            List<String> opResultsJson = new ArrayList<>();
            boolean stateRequested = false;

            for (CommandParser.Command cmd : commands) {
                switch (cmd.type) {
                    case PUT:
                        CampusCache.OpResult putRes = cache.put(cmd.key, cmd.value);
                        opResultsJson.add(JsonUtils.opResultToJson(putRes));
                        break;
                    case GET:
                        CampusCache.OpResult getRes = cache.get(cmd.key);
                        opResultsJson.add(JsonUtils.opResultToJson(getRes));
                        break;
                    case CAPACITY:
                        cache.reinitialize(cmd.capacity);
                        opResultsJson.add("{\"operation\":\"CAPACITY\",\"message\":\"Capacity set to " + cmd.capacity + "\",\"hit\":false,\"reason\":\"Reinitialized\"}");
                        break;
                    case RESET:
                        cache.reinitialize(cache.getCapacity());
                        opResultsJson.add("{\"operation\":\"RESET\",\"message\":\"Cache reset\",\"hit\":false,\"reason\":\"Manual reset\"}");
                        break;
                    case STATE:
                        stateRequested = true;
                        break;
                    default:
                        opResultsJson.add("{\"operation\":\"UNKNOWN\",\"message\":\"Unknown command: " + JsonUtils.escape(cmd.raw) + "\",\"hit\":false,\"reason\":\"Syntax error\"}");
                }
            }

            StringBuilder sb = new StringBuilder("{");
            sb.append("\"executedCount\":").append(opResultsJson.size()).append(",");
            sb.append("\"stateRequested\":").append(stateRequested).append(",");
            sb.append("\"results\":[");
            for (int i = 0; i < opResultsJson.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append(opResultsJson.get(i));
            }
            sb.append("]");

            if (stateRequested) {
                CampusCache.CacheState state = cache.getState();
                sb.append(",\"state\":").append(JsonUtils.cacheStateToJson(state));
            }

            sb.append("}");
            sendJsonResponse(exchange, 200, sb.toString());
        }
    }

    private class DemoHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, 204, "");
                return;
            }
            String query = exchange.getRequestURI().getQuery();
            int demoNum = 1;
            if (query != null && query.contains("type=2")) {
                demoNum = 2;
            }

            List<String> resultsJson = new ArrayList<>();

            if (demoNum == 1) {
                // Demo 1: Problem Statement
                // Capacity = 2
                // PUT(A,10)
                // PUT(B,20)
                // GET(A)
                // PUT(C,30)
                // STATE
                cache.reinitialize(2);
                resultsJson.add(JsonUtils.opResultToJson(cache.put("A", "10")));
                resultsJson.add(JsonUtils.opResultToJson(cache.put("B", "20")));
                resultsJson.add(JsonUtils.opResultToJson(cache.get("A")));
                resultsJson.add(JsonUtils.opResultToJson(cache.put("C", "30")));
            } else {
                // Demo 2: LRU Tie-breaking
                // Capacity = 2
                // PUT(Doc_Alpha, Syllabus.pdf)
                // PUT(Doc_Beta, Timetable.pdf)
                // Both freq 1. Inserting Doc_Gamma must evict Doc_Alpha (LRU tie-breaker)
                cache.reinitialize(2);
                resultsJson.add(JsonUtils.opResultToJson(cache.put("Doc_Alpha", "Syllabus.pdf")));
                resultsJson.add(JsonUtils.opResultToJson(cache.put("Doc_Beta", "Timetable.pdf")));
                resultsJson.add(JsonUtils.opResultToJson(cache.put("Doc_Gamma", "CampusMap.pdf")));
            }

            CampusCache.CacheState state = cache.getState();

            StringBuilder sb = new StringBuilder("{");
            sb.append("\"demo\":").append(demoNum).append(",");
            sb.append("\"results\":[");
            for (int i = 0; i < resultsJson.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append(resultsJson.get(i));
            }
            sb.append("],");
            sb.append("\"state\":").append(JsonUtils.cacheStateToJson(state));
            sb.append("}");

            sendJsonResponse(exchange, 200, sb.toString());
        }
    }

    private static class StaticFileHandler implements HttpHandler {
        private final Path webRoot;

        public StaticFileHandler(Path webRoot) {
            this.webRoot = webRoot;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            if (path == null || path.equals("/") || path.isEmpty()) {
                path = "/index.html";
            }

            // Prevent path traversal
            Path resolved = webRoot.resolve(path.substring(1)).normalize();
            if (!resolved.startsWith(webRoot) || !Files.exists(resolved) || Files.isDirectory(resolved)) {
                String notFound = "404 Not Found: " + path;
                exchange.sendResponseHeaders(404, notFound.length());
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(notFound.getBytes(StandardCharsets.UTF_8));
                }
                return;
            }

            String contentType = "text/plain";
            if (path.endsWith(".html")) contentType = "text/html; charset=UTF-8";
            else if (path.endsWith(".css")) contentType = "text/css; charset=UTF-8";
            else if (path.endsWith(".js")) contentType = "application/javascript; charset=UTF-8";
            else if (path.endsWith(".svg")) contentType = "image/svg+xml";
            else if (path.endsWith(".json")) contentType = "application/json";

            byte[] bytes = Files.readAllBytes(resolved);
            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }

    public static void main(String[] args) {
        int port = DEFAULT_PORT;
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException ignored) {}
        }

        Path webPath = Paths.get("web");
        if (!Files.exists(webPath)) {
            webPath = Paths.get("/Users/atharvmishra/.gemini/antigravity/scratch/CampusCache/web");
        }

        try {
            CampusCacheServer server = new CampusCacheServer(3, webPath);
            server.start(port);
        } catch (IOException e) {
            System.err.println("Failed to start server: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
