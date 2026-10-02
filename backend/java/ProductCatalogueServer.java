import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;

public final class ProductCatalogueServer {
    private static final int PORT = 8080;
    private static final int MAX_BODY_BYTES = 65536;
    private static final Map<Integer, Product> PRODUCTS = createProducts();

    private ProductCatalogueServer() {
    }

    public static void main(String[] args) throws IOException {
        Path publicRoot = Paths.get(args.length > 0 ? args[0] : "public_html")
                .toAbsolutePath().normalize();
        if (!Files.isDirectory(publicRoot)) {
            throw new IllegalArgumentException("Web root does not exist: " + publicRoot);
        }

        HttpServer server = HttpServer.create(
                new InetSocketAddress(InetAddress.getByName("127.0.0.1"), PORT), 0);
        server.createContext("/api/checkout", ProductCatalogueServer::handleCheckout);
        server.createContext("/", exchange -> serveStatic(exchange, publicRoot));
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        System.out.println("AllFit demo server running at http://127.0.0.1:" + PORT + "/catalogue.xhtml");
        System.out.println("Checkout transactions are simulated. No payments are made.");
    }

    private static void handleCheckout(HttpExchange exchange) throws IOException {
        String origin = exchange.getRequestHeaders().getFirst("Origin");
        if (origin != null && !isAllowedLocalOrigin(origin)) {
            sendJson(exchange, 403, "{\"error\":\"Only local checkout requests are accepted.\"}");
            return;
        }
        if (origin != null) {
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", origin);
            exchange.getResponseHeaders().set("Vary", "Origin");
        }
        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "POST, OPTIONS");
            exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
            return;
        }
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, "{\"error\":\"POST is required.\"}");
            return;
        }

        try {
            byte[] body = readLimited(exchange.getRequestBody(), MAX_BODY_BYTES);
            Map<String, List<String>> form = parseForm(new String(body, StandardCharsets.UTF_8));
            requireValue(form, "fullName", 120);
            String email = requireValue(form, "email", 254);
            if (!email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
                throw new IllegalArgumentException("Enter a valid email address.");
            }
            requireValue(form, "phone", 40);
            requireValue(form, "address", 200);
            requireValue(form, "city", 100);
            requireValue(form, "postalCode", 20);
            if (!"true".equals(firstValue(form, "mockPayment"))) {
                throw new IllegalArgumentException("Mock payment approval was not requested.");
            }

            String discountCode = firstValue(form, "discountCode");
            discountCode = discountCode == null ? "" : discountCode.trim().toUpperCase(Locale.ROOT);
            if (!discountCode.isEmpty() && !"SAVE10".equals(discountCode)) {
                throw new IllegalArgumentException("The discount code is not valid.");
            }

            String shippingMethod = firstValue(form, "shippingMethod");
            shippingMethod = shippingMethod == null ? "normal" : shippingMethod.trim().toLowerCase(Locale.ROOT);
            if (!"normal".equals(shippingMethod) && !"priority".equals(shippingMethod)) {
                throw new IllegalArgumentException("Select a valid shipping method.");
            }

            List<String> itemValues = form.getOrDefault("item", Collections.emptyList());
            if (itemValues.isEmpty() || itemValues.size() > 100) {
                throw new IllegalArgumentException("Your order has no valid items.");
            }

            BigDecimal total = BigDecimal.ZERO;
            for (String itemValue : itemValues) {
                String[] itemParts = itemValue.split(":", -1);
                if (itemParts.length != 2) {
                    throw new IllegalArgumentException("An order item is invalid.");
                }
                int productId = Integer.parseInt(itemParts[0]);
                int quantity = Integer.parseInt(itemParts[1]);
                Product product = PRODUCTS.get(productId);
                if (product == null || quantity < 1 || quantity > 99) {
                    throw new IllegalArgumentException("An order item is invalid.");
                }
                total = total.add(product.price.multiply(BigDecimal.valueOf(quantity)));
            }

                BigDecimal discount = "SAVE10".equals(discountCode)
                    ? total.multiply(new BigDecimal("0.10")).setScale(2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
                BigDecimal shipping = total.subtract(discount)
                    .multiply(new BigDecimal("0.10")).setScale(2, RoundingMode.HALF_UP);
                BigDecimal priorityFee = "priority".equals(shippingMethod)
                    ? new BigDecimal("15.00") : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
                BigDecimal finalTotal = total.subtract(discount).add(shipping).add(priorityFee);

                String orderNumber = "AF-" + UUID.randomUUID().toString()
                    .substring(0, 8).toUpperCase(Locale.ROOT);
            String response = "{\"status\":\"MOCK_APPROVED\",\"orderNumber\":\""
                    + jsonEscape(orderNumber) + "\",\"processedAt\":\""
                    + jsonEscape(Instant.now().toString()) + "\",\"subtotal\":\""
                    + total.setScale(2, RoundingMode.HALF_UP).toPlainString() + "\",\"discount\":\""
                    + discount.toPlainString() + "\",\"shipping\":\""
                    + shipping.toPlainString() + "\",\"priorityFee\":\""
                    + priorityFee.toPlainString() + "\",\"shippingMethod\":\""
                    + jsonEscape(shippingMethod) + "\",\"discountCode\":\""
                    + jsonEscape(discountCode) + "\",\"total\":\""
                    + finalTotal.setScale(2, RoundingMode.HALF_UP).toPlainString() + "\"}";
            sendJson(exchange, 200, response);
        } catch (IllegalArgumentException error) {
            sendJson(exchange, 400, "{\"error\":\"" + jsonEscape(error.getMessage()) + "\"}");
        } catch (Exception error) {
            sendJson(exchange, 500, "{\"error\":\"The demo transaction could not be processed.\"}");
        }
    }

    private static boolean isAllowedLocalOrigin(String origin) {
        return "null".equals(origin)
                || origin.matches("^https?://(localhost|127\\.0\\.0\\.1)(:\\d+)?$");
    }

    private static void serveStatic(HttpExchange exchange, Path publicRoot) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())
                && !"HEAD".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendText(exchange, 405, "Method not allowed.", "text/plain; charset=utf-8");
            return;
        }

        String requestPath = exchange.getRequestURI().getPath();
        if ("/".equals(requestPath)) {
            requestPath = "/catalogue.xhtml";
        }
        Path file = publicRoot.resolve(requestPath.substring(1)).normalize();
        if (!file.startsWith(publicRoot) || !Files.isRegularFile(file)) {
            sendText(exchange, 404, "Not found.", "text/plain; charset=utf-8");
            return;
        }

        String contentType = contentType(file);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        if ("HEAD".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
            return;
        }

        byte[] content = Files.readAllBytes(file);
        exchange.sendResponseHeaders(200, content.length);
        exchange.getResponseBody().write(content);
        exchange.close();
    }

    private static String contentType(Path file) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".xhtml") || name.endsWith(".html")) {
            return "application/xhtml+xml; charset=utf-8";
        }
        if (name.endsWith(".css")) {
            return "text/css; charset=utf-8";
        }
        if (name.endsWith(".js")) {
            return "application/javascript; charset=utf-8";
        }
        if (name.endsWith(".svg")) {
            return "image/svg+xml";
        }
        return "application/octet-stream";
    }

    private static byte[] readLimited(InputStream input, int limit) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int total = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            if (total > limit) {
                throw new IllegalArgumentException("Checkout request is too large.");
            }
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private static Map<String, List<String>> parseForm(String body) throws IOException {
        Map<String, List<String>> values = new HashMap<>();
        if (body.isEmpty()) {
            return values;
        }
        for (String pair : body.split("&")) {
            String[] parts = pair.split("=", 2);
                String key = URLDecoder.decode(parts[0], "UTF-8");
            String value = parts.length == 2
                    ? URLDecoder.decode(parts[1], "UTF-8") : "";
            values.computeIfAbsent(key, ignored -> new ArrayList<>()).add(value);
        }
        return values;
    }

    private static String requireValue(Map<String, List<String>> form, String key, int maxLength) {
        String value = firstValue(form, key);
        if (value == null || value.trim().isEmpty() || value.length() > maxLength) {
            throw new IllegalArgumentException("Enter a valid " + key + ".");
        }
        return value.trim();
    }

    private static String firstValue(Map<String, List<String>> form, String key) {
        List<String> values = form.get(key);
        return values == null || values.isEmpty() ? null : values.get(0);
    }

    private static void sendJson(HttpExchange exchange, int status, String body) throws IOException {
        sendText(exchange, status, body, "application/json; charset=utf-8");
    }

    private static void sendText(HttpExchange exchange, int status, String body, String contentType)
            throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private static String jsonEscape(String value) {
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n");
    }

    private static Map<Integer, Product> createProducts() {
        Map<Integer, Product> products = new HashMap<>();
        addProduct(products, 1, "Square Neck Top", "299.00");
        addProduct(products, 2, "Wide Leg Trousers", "499.00");
        addProduct(products, 3, "Shoulder Bag", "349.00");
        addProduct(products, 4, "Classic Sneakers", "599.00");
        addProduct(products, 5, "Midi Dress", "699.00");
        addProduct(products, 6, "Knit Cardigan", "449.00");
        addProduct(products, 7, "Cargo Pants", "549.00");
        addProduct(products, 8, "Halter Neck Top", "279.00");
        addProduct(products, 9, "Kids Denim Jacket", "399.00");
        addProduct(products, 10, "Plus Size Maxi Dress", "799.00");
        for (int id = 11; id <= 100; id++) {
            long priceInCents;
            if (id <= 42) {
                priceInCents = 17900L + ((id * 3749L) % 82000L);
            } else if (id <= 71) {
                priceInCents = 22900L + ((id * 4287L) % 127100L);
            } else {
                priceInCents = 8900L + ((id * 2317L) % 51100L);
            }
            products.put(id, new Product("Catalogue item " + id, BigDecimal.valueOf(priceInCents, 2)));
        }
        return Collections.unmodifiableMap(products);
    }

    private static void addProduct(Map<Integer, Product> products, int id, String name, String price) {
        products.put(id, new Product(name, new BigDecimal(price)));
    }

    private static final class Product {
        private final String name;
        private final BigDecimal price;

        private Product(String name, BigDecimal price) {
            this.name = name;
            this.price = price;
        }
    }
}