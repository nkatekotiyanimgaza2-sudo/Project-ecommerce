import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.OutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.Executors;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

public final class ProductCatalogueServer {
    private static final int PORT = 8080;
    private static final int MAX_BODY_BYTES = 65536;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final String JDBC_URL = environmentOrDefault(
            "MYSQL_URL",
            "jdbc:mysql://127.0.0.1:3306/allfit_fashion?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC");
    private static final String MYSQL_USER = System.getenv("MYSQL_USER");
    private static final String MYSQL_PASSWORD = System.getenv("MYSQL_PASSWORD");

    private ProductCatalogueServer() {
    }

    public static void main(String[] args) throws IOException {
        Path publicRoot = Paths.get(args.length > 0 ? args[0] : "public_html")
                .toAbsolutePath().normalize();
        if (!Files.isDirectory(publicRoot)) {
            throw new IllegalArgumentException("Web root does not exist: " + publicRoot);
        }
        if (MYSQL_USER == null || MYSQL_PASSWORD == null) {
            throw new IllegalStateException(
                    "Set MYSQL_USER and MYSQL_PASSWORD before starting the Java backend.");
        }
        if (isBlank(System.getenv("ADMIN_TOKEN"))) {
            throw new IllegalStateException(
                    "Set ADMIN_TOKEN to the admin token you selected before starting the Java backend.");
        }
        try (Connection connection = openDatabase()) {
            if (!connection.isValid(2)) {
                throw new SQLException("The initial MySQL connection is not valid.");
            }
            System.out.println("Connected to the AllFit MySQL database.");
        } catch (SQLException error) {
            throw new IllegalStateException(
                    "Could not connect to MySQL. Check MYSQL_URL, MYSQL_USER, MYSQL_PASSWORD, and server status.",
                    error);
        }

        HttpServer server = HttpServer.create(
                new InetSocketAddress(InetAddress.getByName("127.0.0.1"), PORT), 0);
        server.createContext("/api/checkout", ProductCatalogueServer::handleCheckout);
        server.createContext("/api/catalogue", ProductCatalogueServer::handleCatalogue);
        server.createContext("/api/admin/", ProductCatalogueServer::handleAdmin);
        server.createContext("/api/track", ProductCatalogueServer::handleTracking);
        server.createContext("/api/receipt", ProductCatalogueServer::handleReceipt);
        server.createContext("/api/account", ProductCatalogueServer::handleAccount);
        server.createContext("/", exchange -> serveStatic(exchange, publicRoot));
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        System.out.println("AllFit server running at http://127.0.0.1:" + PORT + "/catalogue.xhtml");
        System.out.println("Checkout writes order records and decrements MySQL inventory transactionally.");
    }

    private static void handleCheckout(HttpExchange exchange) throws IOException {
        if (!prepareApiRequest(exchange, "POST")) {
            return;
        }
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, "{\"error\":\"POST is required.\"}");
            return;
        }

        try (Connection connection = openDatabase()) {
            byte[] body = readLimited(exchange.getRequestBody(), MAX_BODY_BYTES);
            Map<String, List<String>> form = parseForm(new String(body, StandardCharsets.UTF_8));
            String fullName = requireValue(form, "fullName", 120);
            String email = requireValue(form, "email", 254).toLowerCase(Locale.ROOT);
            if (!email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
                throw new IllegalArgumentException("Enter a valid email address.");
            }
            String phone = requireValue(form, "phone", 40);
            String address = requireValue(form, "address", 200);
            String city = requireValue(form, "city", 100);
            String postalCode = requireValue(form, "postalCode", 20);
            if (!"true".equals(firstValue(form, "mockPayment"))) {
                throw new IllegalArgumentException("Mock payment approval was not requested.");
            }
            boolean sendEmail = "true".equals(firstValue(form, "emailReceipt"));
            boolean sendWhatsApp = "true".equals(firstValue(form, "whatsappReceipt"));
            if (!sendEmail && !sendWhatsApp) {
                throw new IllegalArgumentException("Select at least one PDF receipt delivery option.");
            }

            String discountCode = firstValue(form, "discountCode");
            discountCode = discountCode == null ? "" : discountCode.trim().toUpperCase(Locale.ROOT);
            String shippingMethod = firstValue(form, "shippingMethod");
            shippingMethod = shippingMethod == null ? "normal" : shippingMethod.trim().toLowerCase(Locale.ROOT);
            if (!"normal".equals(shippingMethod) && !"priority".equals(shippingMethod)) {
                throw new IllegalArgumentException("Select a valid shipping method.");
            }

            List<String> itemValues = form.getOrDefault("item", Collections.emptyList());
            if (itemValues.isEmpty() || itemValues.size() > 100) {
                throw new IllegalArgumentException("Your order has no valid items.");
            }

            Map<String, Integer> requestedItems = new LinkedHashMap<>();
            for (String itemValue : itemValues) {
                String[] itemParts = itemValue.split(":", 3);
                if (itemParts.length != 3) {
                    throw new IllegalArgumentException("An order item is invalid.");
                }
                int productId;
                int quantity;
                try {
                    productId = Integer.parseInt(itemParts[0]);
                    quantity = Integer.parseInt(itemParts[2]);
                } catch (NumberFormatException error) {
                    throw new IllegalArgumentException("An order item is invalid.");
                }
                String size = itemParts[1].trim();
                if (productId < 1 || size.isEmpty() || size.length() > 20
                        || quantity < 1 || quantity > 99) {
                    throw new IllegalArgumentException("An order item is invalid.");
                }
                String key = productId + ":" + size;
                int combinedQuantity = requestedItems.getOrDefault(key, 0) + quantity;
                if (combinedQuantity > 99) {
                    throw new IllegalArgumentException("An order item quantity is invalid.");
                }
                requestedItems.put(key, combinedQuantity);
            }

            connection.setAutoCommit(false);
            try {
                long customerId = findOrCreateCustomer(connection, fullName, email, phone);
                List<OrderLine> orderLines = lockAndValidateItems(connection, requestedItems);
                BigDecimal subtotal = orderLines.stream()
                        .map(line -> line.unitPrice.multiply(BigDecimal.valueOf(line.quantity)))
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .setScale(2, RoundingMode.HALF_UP);
                Discount discountRule = findDiscount(connection, discountCode);
                BigDecimal discount = discountRule == null
                        ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                        : discountRule.calculate(subtotal);
                BigDecimal shipping = subtotal.subtract(discount)
                        .multiply(new BigDecimal("0.10")).setScale(2, RoundingMode.HALF_UP);
                BigDecimal priorityFee = "priority".equals(shippingMethod)
                        ? new BigDecimal("15.00") : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
                BigDecimal total = subtotal.subtract(discount).add(shipping).add(priorityFee);
                String orderNumber = "AF-" + UUID.randomUUID().toString()
                        .substring(0, 8).toUpperCase(Locale.ROOT);
                String trackingCode = createTrackingCode();
                Instant processedAt = Instant.now();

                long orderId = insertOrder(connection, orderNumber, trackingCode, customerId, fullName, email, phone,
                        address, city, postalCode, discountRule, discountCode, shippingMethod,
                        subtotal, discount, shipping, priorityFee, total);
                insertOrderItems(connection, orderId, orderLines);
                insertMockPayment(connection, orderId, total, orderNumber);
                decrementInventory(connection, orderLines);
                connection.commit();

                OrderReceipt receipt = new OrderReceipt(orderNumber, trackingCode, fullName, email, phone,
                        address + ", " + city + ", " + postalCode, "confirmed", processedAt,
                        subtotal, discount, shipping, priorityFee, total);
                for (OrderLine line : orderLines) {
                    receipt.items.add(new ReceiptItem(line.productName, line.size, line.quantity, line.unitPrice));
                }
                String emailStatus = "not_requested";
                String whatsappStatus = "not_requested";
                try {
                    byte[] receiptPdf = createReceiptPdf(receipt);
                    emailStatus = sendEmail ? sendEmailReceipt(receipt, receiptPdf) : "not_requested";
                    whatsappStatus = sendWhatsApp ? sendWhatsAppReceipt(receipt, receiptPdf) : "not_requested";
                } catch (IOException error) {
                    System.err.println("Receipt PDF generation failed: " + error.getClass().getSimpleName());
                    emailStatus = sendEmail ? "failed" : "not_requested";
                    whatsappStatus = sendWhatsApp ? "failed" : "not_requested";
                }
                sendJson(exchange, 200, checkoutJson(orderNumber, processedAt, subtotal, discount,
                        shipping, priorityFee, total, shippingMethod, discountCode, emailStatus, whatsappStatus,
                        orderLines, trackingCode));
            } catch (Exception error) {
                connection.rollback();
                throw error;
            }
        } catch (ApiException error) {
            sendJson(exchange, error.statusCode, "{\"error\":\"" + jsonEscape(error.getMessage()) + "\"}");
        } catch (IllegalArgumentException error) {
            sendJson(exchange, 400, "{\"error\":\"" + jsonEscape(error.getMessage()) + "\"}");
        } catch (SQLException error) {
            System.err.println("Database checkout failed: " + error.getClass().getSimpleName());
            sendJson(exchange, 500,
                    "{\"error\":\"Checkout could not be completed. The database transaction was rolled back.\"}");
        } catch (Exception error) {
            System.err.println("Checkout failed: " + error.getClass().getSimpleName());
            sendJson(exchange, 500, "{\"error\":\"Checkout could not be completed.\"}");
        }
    }

    private static void handleCatalogue(HttpExchange exchange) throws IOException {
        if (!prepareApiRequest(exchange, "GET")) {
            return;
        }
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, "{\"error\":\"GET is required.\"}");
            return;
        }

        try (Connection connection = openDatabase();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT p.product_id, p.product_name, c.category_name, sc.subcategory_name, "
                             + "p.fit_label, p.color_name, p.description, p.image_url, p.current_price, "
                             + "COALESCE((SELECT ROUND(AVG(r.rating)) FROM product_review r "
                             + "WHERE r.product_id = p.product_id AND r.is_approved = TRUE), 0) AS rating, "
                             + "(SELECT COUNT(*) FROM product_review r "
                             + "WHERE r.product_id = p.product_id AND r.is_approved = TRUE) AS review_count, "
                             + "pv.product_variant_id, pv.size_label, pv.stock_quantity "
                             + "FROM product p "
                             + "JOIN subcategory sc ON sc.subcategory_id = p.subcategory_id "
                             + "JOIN category c ON c.category_id = sc.category_id "
                             + "JOIN product_variant pv ON pv.product_id = p.product_id "
                             + "WHERE p.is_active = TRUE "
                             + "ORDER BY p.product_id, pv.product_variant_id");
             ResultSet rows = statement.executeQuery()) {
            Map<Integer, CatalogueProduct> products = new LinkedHashMap<>();
            while (rows.next()) {
                int id = rows.getInt("product_id");
                CatalogueProduct product = products.get(id);
                if (product == null) {
                    product = new CatalogueProduct(id, value(rows, "product_name"),
                            value(rows, "category_name"), value(rows, "subcategory_name"),
                            value(rows, "fit_label"), value(rows, "color_name"),
                            value(rows, "description"), value(rows, "image_url"),
                            decimal(rows, "current_price"), rows.getInt("rating"), rows.getInt("review_count"));
                    products.put(id, product);
                }
                long variantId = rows.getLong("product_variant_id");
                if (!rows.wasNull()) {
                    product.sizes.add(new CatalogueSize(variantId, value(rows, "size_label"),
                            rows.getInt("stock_quantity")));
                }
            }
            sendJson(exchange, 200, catalogueJson(products));
        } catch (SQLException error) {
            System.err.println("Catalogue database query failed: " + error.getClass().getSimpleName());
            sendJson(exchange, 503, "{\"error\":\"Product availability could not be loaded from the database.\"}");
        }
    }

    private static void handleAccount(HttpExchange exchange) throws IOException {
            if (!prepareApiRequest(exchange, "POST")) {
                return;
            }
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJson(exchange, 405, "{\"error\":\"POST is required.\"}");
                return;
            }
            try {
                Map<String, List<String>> form = readForm(exchange);
                String mode = requireValue(form, "mode", 16).toLowerCase(Locale.ROOT);
                String email = requireValue(form, "email", 254).toLowerCase(Locale.ROOT);
                if (!email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
                    throw new IllegalArgumentException("Enter a valid email address.");
                }
                String password = requireValue(form, "password", 128);
                if (password.length() < 8) {
                    throw new IllegalArgumentException("Password must contain at least 8 characters.");
                }
                try (Connection connection = openDatabase()) {
                    if ("register".equals(mode)) {
                        String name = requireValue(form, "name", 120);
                        connection.setAutoCommit(false);
                        try {
                            long customerId = createAccountCustomer(connection, name, email);
                            byte[] saltBytes = new byte[16];
                            SECURE_RANDOM.nextBytes(saltBytes);
                            String salt = Base64.getEncoder().encodeToString(saltBytes);
                            String passwordHash = hashAccountPassword(password, saltBytes);
                            try (PreparedStatement statement = connection.prepareStatement(
                                    "INSERT INTO customer_account (customer_id, password_salt, password_hash) "
                                            + "VALUES (?, ?, ?)")) {
                                statement.setLong(1, customerId);
                                statement.setString(2, salt);
                                statement.setString(3, passwordHash);
                                statement.executeUpdate();
                            }
                            connection.commit();
                            sendJson(exchange, 201, accountJson(customerId, name, email));
                        } catch (Exception error) {
                            connection.rollback();
                            if (error instanceof SQLException sqlError && "23000".equals(sqlError.getSQLState())) {
                                sendJson(exchange, 409, "{\"error\":\"An account with this email already exists.\"}");
                            } else if (error instanceof IllegalArgumentException inputError) {
                                sendJson(exchange, 400, "{\"error\":\"" + jsonEscape(inputError.getMessage()) + "\"}");
                            } else {
                                throw error;
                            }
                        }
                    } else if ("login".equals(mode)) {
                        try (PreparedStatement statement = connection.prepareStatement(
                                "SELECT c.customer_id, c.full_name, ca.password_salt, ca.password_hash "
                                        + "FROM customer c JOIN customer_account ca ON ca.customer_id = c.customer_id "
                                        + "WHERE LOWER(c.email) = ?")) {
                            statement.setString(1, email);
                            try (ResultSet row = statement.executeQuery()) {
                                if (!row.next()) {
                                    sendJson(exchange, 401, "{\"error\":\"Email or password is incorrect.\"}");
                                    return;
                                }
                                byte[] salt = Base64.getDecoder().decode(value(row, "password_salt"));
                                byte[] expected = hexToBytes(value(row, "password_hash"));
                                byte[] actual = hexToBytes(hashAccountPassword(password, salt));
                                if (!MessageDigest.isEqual(expected, actual)) {
                                    sendJson(exchange, 401, "{\"error\":\"Email or password is incorrect.\"}");
                                    return;
                                }
                                sendJson(exchange, 200, accountJson(row.getLong("customer_id"),
                                        value(row, "full_name"), email));
                            }
                        }
                    } else {
                        throw new IllegalArgumentException("Choose register or login.");
                    }
                }
            } catch (IllegalArgumentException error) {
                sendJson(exchange, 400, "{\"error\":\"" + jsonEscape(error.getMessage()) + "\"}");
            } catch (SQLException | java.security.GeneralSecurityException error) {
                System.err.println("Account operation failed: " + error.getClass().getSimpleName());
                sendJson(exchange, 503, "{\"error\":\"Account service is temporarily unavailable.\"}");
            } catch (Exception error) {
                System.err.println("Account operation failed: " + error.getClass().getSimpleName());
                sendJson(exchange, 500, "{\"error\":\"The account operation could not be completed.\"}");
            }
        }

        private static long createAccountCustomer(Connection connection, String name, String email)
                throws SQLException {
            long existingCustomerId = 0;
            try (PreparedStatement find = connection.prepareStatement(
                    "SELECT customer_id FROM customer WHERE LOWER(email) = ?")) {
                find.setString(1, email);
                try (ResultSet row = find.executeQuery()) {
                    if (row.next()) {
                        existingCustomerId = row.getLong(1);
                    }
                }
            }
            if (existingCustomerId != 0) {
                try (PreparedStatement update = connection.prepareStatement(
                        "UPDATE customer SET full_name = ? WHERE customer_id = ?")) {
                    update.setString(1, name);
                    update.setLong(2, existingCustomerId);
                    update.executeUpdate();
                }
                return existingCustomerId;
            }
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO customer (full_name, email, phone) VALUES (?, ?, '')",
                    Statement.RETURN_GENERATED_KEYS)) {
                insert.setString(1, name);
                insert.setString(2, email);
                insert.executeUpdate();
                try (ResultSet keys = insert.getGeneratedKeys()) {
                    if (keys.next()) {
                        return keys.getLong(1);
                    }
                }
            }
            throw new SQLException("Customer ID was not returned.");
        }

        private static String hashAccountPassword(String password, byte[] salt)
                throws java.security.GeneralSecurityException {
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, 150000, 256);
            try {
                byte[] derived = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                        .generateSecret(spec).getEncoded();
                StringBuilder hex = new StringBuilder(derived.length * 2);
                for (byte value : derived) {
                    hex.append(String.format(Locale.ROOT, "%02x", value & 0xff));
                }
                return hex.toString();
            } finally {
                spec.clearPassword();
            }
        }

        private static byte[] hexToBytes(String value) {
            if (value.length() != 64) {
                throw new IllegalArgumentException("Stored account credentials are invalid.");
            }
            byte[] bytes = new byte[value.length() / 2];
            for (int index = 0; index < bytes.length; index++) {
                bytes[index] = (byte) Integer.parseInt(value.substring(index * 2, index * 2 + 2), 16);
            }
            return bytes;
        }

        private static String accountJson(long customerId, String name, String email) {
            return "{\"id\":" + customerId + ",\"name\":\"" + jsonEscape(name)
                    + "\",\"email\":\"" + jsonEscape(email) + "\"}";
        }

    private static void handleTracking(HttpExchange exchange) throws IOException {
        if (!prepareApiRequest(exchange, "GET")) {
            return;
        }
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, "{\"error\":\"GET is required.\"}");
            return;
        }
        Map<String, List<String>> query = parseForm(exchange.getRequestURI().getRawQuery());
        String trackingCode = requireValue(query, "trackingCode", 20).toUpperCase(Locale.ROOT);
        String email = requireValue(query, "email", 254).toLowerCase(Locale.ROOT);

        try (Connection connection = openDatabase();
             PreparedStatement orderStatement = connection.prepareStatement(
                     "SELECT order_id, order_reference, tracking_code, order_status, placed_at, total_amount "
                             + "FROM `orders` WHERE tracking_code = ? AND LOWER(shipping_email) = ?")) {
            orderStatement.setString(1, trackingCode);
            orderStatement.setString(2, email);
            try (ResultSet order = orderStatement.executeQuery()) {
                if (!order.next()) {
                    sendJson(exchange, 404,
                            "{\"error\":\"No order matched that tracking code and email address.\"}");
                    return;
                }
                long orderId = order.getLong("order_id");
                StringBuilder json = new StringBuilder("{\"reference\":\"")
                        .append(jsonEscape(value(order, "order_reference")))
                        .append("\",\"trackingCode\":\"").append(jsonEscape(value(order, "tracking_code")))
                        .append("\",\"status\":\"").append(jsonEscape(value(order, "order_status")))
                        .append("\",\"placedAt\":\"").append(jsonEscape(order.getTimestamp("placed_at").toInstant().toString()))
                        .append("\",\"total\":\"").append(money(decimal(order, "total_amount")))
                        .append("\",\"items\":[");
                try (PreparedStatement itemStatement = connection.prepareStatement(
                        "SELECT product_name_at_purchase, size_label_at_purchase, quantity "
                                + "FROM order_item WHERE order_id = ? ORDER BY order_item_id")) {
                    itemStatement.setLong(1, orderId);
                    try (ResultSet items = itemStatement.executeQuery()) {
                        boolean first = true;
                        while (items.next()) {
                            if (!first) {
                                json.append(',');
                            }
                            first = false;
                            json.append("{\"name\":\"").append(jsonEscape(value(items, "product_name_at_purchase")))
                                    .append("\",\"size\":\"").append(jsonEscape(value(items, "size_label_at_purchase")))
                                    .append("\",\"quantity\":").append(items.getInt("quantity")).append('}');
                        }
                    }
                }
                sendJson(exchange, 200, json.append("]}").toString());
            }
        } catch (IllegalArgumentException error) {
            sendJson(exchange, 400, "{\"error\":\"" + jsonEscape(error.getMessage()) + "\"}");
        } catch (SQLException error) {
            System.err.println("Order tracking query failed: " + error.getClass().getSimpleName());
            sendJson(exchange, 503, "{\"error\":\"Order tracking is temporarily unavailable.\"}");
        }
    }

    private static void handleReceipt(HttpExchange exchange) throws IOException {
        if (!prepareApiRequest(exchange, "GET")) {
            return;
        }
        String method = exchange.getRequestMethod().toUpperCase(Locale.ROOT);
        if (!"GET".equals(method) && !"POST".equals(method)) {
            sendJson(exchange, 405, "{\"error\":\"GET or POST is required.\"}");
            return;
        }
        try {
            Map<String, List<String>> query = parseForm(exchange.getRequestURI().getRawQuery());
            String trackingCode = requireValue(query, "trackingCode", 20).toUpperCase(Locale.ROOT);
            String email = requireValue(query, "email", 254).toLowerCase(Locale.ROOT);
            OrderReceipt receipt;
            try (Connection connection = openDatabase()) {
                receipt = loadReceipt(connection, trackingCode, email);
            }
            if ("GET".equals(method)) {
                sendPdf(exchange, 200, createReceiptPdf(receipt), receipt.reference);
                return;
            }

            Map<String, List<String>> form = readForm(exchange);
            boolean emailRequested = "true".equals(firstValue(form, "emailReceipt"));
            boolean whatsappRequested = "true".equals(firstValue(form, "whatsappReceipt"));
            if (!emailRequested && !whatsappRequested) {
                throw new IllegalArgumentException("Select at least one receipt delivery option.");
            }
            byte[] pdf = createReceiptPdf(receipt);
            String emailStatus = emailRequested ? sendEmailReceipt(receipt, pdf) : "not_requested";
            String whatsappStatus = whatsappRequested ? sendWhatsAppReceipt(receipt, pdf) : "not_requested";
            sendJson(exchange, 200, "{\"email\":\"" + emailStatus + "\",\"whatsapp\":\""
                    + whatsappStatus + "\",\"pdfAvailable\":true}");
        } catch (IllegalArgumentException error) {
            sendJson(exchange, 400, "{\"error\":\"" + jsonEscape(error.getMessage()) + "\"}");
        } catch (ReceiptNotFoundException error) {
            sendJson(exchange, 404, "{\"error\":\"No order matched that tracking code and email address.\"}");
        } catch (SQLException error) {
            System.err.println("Receipt lookup failed: " + error.getClass().getSimpleName());
            sendJson(exchange, 503, "{\"error\":\"Receipt service is temporarily unavailable.\"}");
        }
    }

    private static OrderReceipt loadReceipt(Connection connection, String trackingCode, String email)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT order_reference, tracking_code, shipping_full_name, shipping_email, shipping_phone, "
                        + "shipping_address_line1, shipping_city, shipping_postal_code, "
                        + "order_status, placed_at, subtotal, discount_amount, shipping_amount, "
                        + "priority_fee, total_amount FROM `orders` "
                        + "WHERE tracking_code = ? AND LOWER(shipping_email) = ?")) {
            statement.setString(1, trackingCode);
            statement.setString(2, email);
            try (ResultSet row = statement.executeQuery()) {
                if (!row.next()) {
                    throw new ReceiptNotFoundException();
                }
                OrderReceipt receipt = new OrderReceipt(value(row, "order_reference"),
                        value(row, "tracking_code"),
                        value(row, "shipping_full_name"), value(row, "shipping_email"),
                        value(row, "shipping_phone"),
                        value(row, "shipping_address_line1") + ", " + value(row, "shipping_city")
                                + ", " + value(row, "shipping_postal_code"),
                        value(row, "order_status"), row.getTimestamp("placed_at").toInstant(),
                        decimal(row, "subtotal"), decimal(row, "discount_amount"),
                        decimal(row, "shipping_amount"), decimal(row, "priority_fee"),
                        decimal(row, "total_amount"));
                try (PreparedStatement itemStatement = connection.prepareStatement(
                        "SELECT product_name_at_purchase, size_label_at_purchase, quantity, "
                                + "unit_price_at_purchase FROM order_item WHERE order_id = "
                                + "(SELECT order_id FROM `orders` WHERE tracking_code = ?) "
                                + "ORDER BY order_item_id")) {
                    itemStatement.setString(1, trackingCode);
                    try (ResultSet items = itemStatement.executeQuery()) {
                        while (items.next()) {
                            receipt.items.add(new ReceiptItem(value(items, "product_name_at_purchase"),
                                    value(items, "size_label_at_purchase"), items.getInt("quantity"),
                                    decimal(items, "unit_price_at_purchase")));
                        }
                    }
                }
                return receipt;
            }
        }
    }

    private static byte[] createReceiptPdf(OrderReceipt receipt) throws IOException {
        List<String> lines = new ArrayList<>();
        lines.add("ALLFIT FASHION - ORDER RECEIPT");
        lines.add("Order reference: " + receipt.reference);
        lines.add("Tracking code: " + receipt.trackingCode);
        lines.add("Date: " + receipt.placedAt);
        lines.add("Status: " + receipt.status);
        lines.add("Customer: " + receipt.name);
        lines.add("Email: " + receipt.email);
        lines.add("Delivery: " + receipt.address);
        lines.add("");
        lines.add("ITEMS");
        for (ReceiptItem item : receipt.items) {
            lines.add(item.name + " / " + item.size + " x " + item.quantity + " @ R"
                    + money(item.unitPrice) + " = R"
                    + money(item.unitPrice.multiply(BigDecimal.valueOf(item.quantity))));
        }
        lines.add("");
        lines.add("Subtotal: R" + money(receipt.subtotal));
        lines.add("Discount: R" + money(receipt.discount));
        lines.add("Shipping: R" + money(receipt.shipping));
        lines.add("Priority fee: R" + money(receipt.priority));
        lines.add("TOTAL: R" + money(receipt.total));
        lines.add("");
        lines.add("Demonstration checkout only. Payment was simulated.");

        List<String> wrappedLines = new ArrayList<>();
        for (String line : lines) {
            wrappedLines.addAll(wrapPdfLine(line, 88));
        }
        List<List<String>> pages = new ArrayList<>();
        for (int start = 0; start < wrappedLines.size(); start += 42) {
            pages.add(wrappedLines.subList(start, Math.min(start + 42, wrappedLines.size())));
        }
        if (pages.isEmpty()) {
            pages.add(List.of("AllFit Fashion receipt"));
        }
        int fontObjectId = 3 + pages.size() * 2;
        StringBuilder kids = new StringBuilder();
        List<byte[]> objects = new ArrayList<>();
        objects.add(ascii("<< /Type /Catalog /Pages 2 0 R >>"));
        for (int page = 0; page < pages.size(); page++) {
            int pageObjectId = 3 + page * 2;
            int contentObjectId = pageObjectId + 1;
            if (page > 0) {
                kids.append(' ');
            }
            kids.append(pageObjectId).append(" 0 R");
        }
        objects.add(ascii("<< /Type /Pages /Kids [" + kids + "] /Count " + pages.size() + " >>"));
        for (int page = 0; page < pages.size(); page++) {
            int pageObjectId = 3 + page * 2;
            int contentObjectId = pageObjectId + 1;
            StringBuilder content = new StringBuilder("BT\n/F1 10 Tf\n50 790 Td\n");
            for (int line = 0; line < pages.get(page).size(); line++) {
                if (line > 0) {
                    content.append("0 -17 Td\n");
                }
                content.append('(').append(pdfEscape(pages.get(page).get(line))).append(") Tj\n");
            }
            content.append("ET");
            byte[] stream = content.toString().getBytes(StandardCharsets.US_ASCII);
            objects.add(ascii("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] "
                    + "/Resources << /Font << /F1 " + fontObjectId + " 0 R >> >> "
                    + "/Contents " + contentObjectId + " 0 R >>"));
            objects.add(concat(ascii("<< /Length " + stream.length + " >>\nstream\n"),
                    stream, ascii("\nendstream")));
        }
        objects.add(ascii("<< /Type /Font /Subtype /Type1 /BaseFont /Courier >>"));
        ByteArrayOutputStream pdf = new ByteArrayOutputStream();
        pdf.write(ascii("%PDF-1.4\n"));
        List<Integer> offsets = new ArrayList<>();
        offsets.add(0);
        for (int i = 0; i < objects.size(); i++) {
            offsets.add(pdf.size());
            pdf.write(ascii((i + 1) + " 0 obj\n"));
            pdf.write(objects.get(i));
            pdf.write(ascii("\nendobj\n"));
        }
        int xrefOffset = pdf.size();
        pdf.write(ascii("xref\n0 " + (objects.size() + 1) + "\n"));
        pdf.write(ascii("0000000000 65535 f \n"));
        for (int i = 1; i < offsets.size(); i++) {
            pdf.write(ascii(String.format(Locale.ROOT, "%010d 00000 n \n", offsets.get(i))));
        }
        pdf.write(ascii("trailer\n<< /Size " + (objects.size() + 1) + " /Root 1 0 R >>\n"
                + "startxref\n" + xrefOffset + "\n%%EOF"));
        return pdf.toByteArray();
    }

    private static List<String> wrapPdfLine(String line, int maximum) {
        String asciiLine = line.replaceAll("[^\\x20-\\x7E]", "?");
        List<String> result = new ArrayList<>();
        while (asciiLine.length() > maximum) {
            int split = asciiLine.lastIndexOf(' ', maximum);
            if (split < 1) {
                split = maximum;
            }
            result.add(asciiLine.substring(0, split));
            asciiLine = asciiLine.substring(split).stripLeading();
        }
        result.add(asciiLine);
        return result;
    }

    private static String pdfEscape(String text) {
        return text.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)");
    }

    private static String sendEmailReceipt(OrderReceipt receipt, byte[] pdf) {
        String username = System.getenv("GMAIL_SMTP_USER");
        String password = System.getenv("GMAIL_SMTP_APP_PASSWORD");
        if (isBlank(username) || isBlank(password)) {
            return "not_configured";
        }
        username = username.trim();
        password = password.replaceAll("\\s", "");
        try (SSLSocket socket = (SSLSocket) SSLSocketFactory.getDefault()
                .createSocket("smtp.gmail.com", 465)) {
            socket.setSoTimeout(15000);
            javax.net.ssl.SSLParameters sslParameters = socket.getSSLParameters();
            sslParameters.setEndpointIdentificationAlgorithm("HTTPS");
            socket.setSSLParameters(sslParameters);
            socket.startHandshake();
            BufferedReader input = new BufferedReader(new InputStreamReader(
                    socket.getInputStream(), StandardCharsets.UTF_8));
            BufferedWriter output = new BufferedWriter(new OutputStreamWriter(
                    socket.getOutputStream(), StandardCharsets.UTF_8));
            expectSmtp(input, 220);
            smtpCommand(input, output, "EHLO allfit.local", 250);
            smtpCommand(input, output, "AUTH LOGIN", 334);
            smtpCommand(input, output, Base64.getEncoder().encodeToString(username.getBytes(StandardCharsets.UTF_8)), 334);
            smtpCommand(input, output, Base64.getEncoder().encodeToString(password.getBytes(StandardCharsets.UTF_8)), 235);
            smtpCommand(input, output, "MAIL FROM:<" + username + ">", 250);
            smtpCommand(input, output, "RCPT TO:<" + receipt.email + ">", 250);
            smtpCommand(input, output, "DATA", 354);
            String boundary = "allfit-" + UUID.randomUUID();
            String encodedPdf = Base64.getMimeEncoder(76, "\r\n".getBytes(StandardCharsets.US_ASCII))
                    .encodeToString(pdf);
            String message = "From: AllFit Fashion <" + username + ">\r\n"
                    + "To: <" + receipt.email + ">\r\n"
                    + "Subject: AllFit Fashion receipt " + receipt.reference + "\r\n"
                    + "MIME-Version: 1.0\r\n"
                    + "Content-Type: multipart/mixed; boundary=\"" + boundary + "\"\r\n\r\n"
                    + "--" + boundary + "\r\nContent-Type: text/plain; charset=UTF-8\r\n"
                    + "Content-Transfer-Encoding: 8bit\r\n\r\n"
                    + "Thank you for shopping with AllFit Fashion. Your PDF receipt is attached.\r\n"
                    + "--" + boundary + "\r\nContent-Type: application/pdf; name=\""
                    + receipt.reference + ".pdf\"\r\n"
                    + "Content-Transfer-Encoding: base64\r\n"
                    + "Content-Disposition: attachment; filename=\"" + receipt.reference + ".pdf\"\r\n\r\n"
                    + encodedPdf + "\r\n--" + boundary + "--\r\n";
            output.write(message.replace("\n.", "\n.."));
            output.write("\r\n.\r\n");
            output.flush();
            expectSmtp(input, 250);
            smtpCommand(input, output, "QUIT", 221);
            return "sent";
        } catch (Exception error) {
            System.err.println("Email receipt delivery failed: " + error.getClass().getSimpleName());
            return "failed";
        }
    }

    private static void smtpCommand(BufferedReader input, BufferedWriter output, String command, int expected)
            throws IOException {
        output.write(command);
        output.write("\r\n");
        output.flush();
        expectSmtp(input, expected);
    }

    private static void expectSmtp(BufferedReader input, int expected) throws IOException {
        String line;
        do {
            line = input.readLine();
            if (line == null || line.length() < 3
                    || !line.substring(0, 3).equals(Integer.toString(expected))) {
                throw new IOException("SMTP server rejected a receipt operation.");
            }
        } while (line.length() > 3 && line.charAt(3) == '-');
    }

    private static String sendWhatsAppReceipt(OrderReceipt receipt, byte[] pdf) {
        String token = System.getenv("WHATSAPP_ACCESS_TOKEN");
        String phoneId = System.getenv("WHATSAPP_PHONE_NUMBER_ID");
        String template = System.getenv("WHATSAPP_DOCUMENT_TEMPLATE");
        String language = environmentOrDefault("WHATSAPP_TEMPLATE_LANGUAGE", "en_US");
        if (isBlank(token) || isBlank(phoneId) || isBlank(template)) {
            return "not_configured";
        }
        String phone = receipt.phone.replaceAll("[^0-9]", "");
        if (phone.length() < 8 || phone.length() > 15) {
            return "invalid_phone";
        }
        try {
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
            String boundary = "allfit-" + UUID.randomUUID();
            ByteArrayOutputStream multipart = new ByteArrayOutputStream();
            writeMultipart(multipart, boundary, "messaging_product", "whatsapp");
            multipart.write(ascii("--" + boundary + "\r\n"
                    + "Content-Disposition: form-data; name=\"file\"; filename=\""
                    + receipt.reference + ".pdf\"\r\nContent-Type: application/pdf\r\n\r\n"));
            multipart.write(pdf);
            multipart.write(ascii("\r\n--" + boundary + "--\r\n"));
            HttpRequest upload = HttpRequest.newBuilder(URI.create(
                            "https://graph.facebook.com/v22.0/" + URLEncoder.encode(phoneId, StandardCharsets.UTF_8) + "/media"))
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(multipart.toByteArray())).build();
            HttpResponse<String> uploaded = client.send(upload, HttpResponse.BodyHandlers.ofString());
            Matcher mediaIdMatcher = Pattern.compile("\"id\"\\s*:\\s*\"([^\"]+)\"").matcher(uploaded.body());
            if (uploaded.statusCode() < 200 || uploaded.statusCode() >= 300 || !mediaIdMatcher.find()) {
                return "failed";
            }
            String mediaId = mediaIdMatcher.group(1);
            String json = "{\"messaging_product\":\"whatsapp\",\"to\":\"" + jsonEscape(phone)
                    + "\",\"type\":\"template\",\"template\":{\"name\":\"" + jsonEscape(template)
                    + "\",\"language\":{\"code\":\"" + jsonEscape(language)
                    + "\"},\"components\":[{\"type\":\"header\",\"parameters\":[{\"type\":\"document\","
                    + "\"document\":{\"id\":\"" + jsonEscape(mediaId) + "\",\"filename\":\""
                    + jsonEscape(receipt.reference) + ".pdf\"}}]},{\"type\":\"body\",\"parameters\":["
                    + "{\"type\":\"text\",\"text\":\"" + jsonEscape(receipt.reference) + "\"}]}]}}";
            HttpRequest send = HttpRequest.newBuilder(URI.create(
                            "https://graph.facebook.com/v22.0/" + URLEncoder.encode(phoneId, StandardCharsets.UTF_8) + "/messages"))
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json)).build();
            HttpResponse<String> sent = client.send(send, HttpResponse.BodyHandlers.ofString());
            return sent.statusCode() >= 200 && sent.statusCode() < 300 ? "sent" : "failed";
        } catch (Exception error) {
            System.err.println("WhatsApp receipt delivery failed: " + error.getClass().getSimpleName());
            return "failed";
        }
    }

    private static void writeMultipart(OutputStream output, String boundary, String name, String value)
            throws IOException {
        output.write(ascii("--" + boundary + "\r\nContent-Disposition: form-data; name=\""
                + name + "\"\r\n\r\n" + value + "\r\n"));
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static byte[] ascii(String value) {
        return value.getBytes(StandardCharsets.US_ASCII);
    }

    private static byte[] concat(byte[]... arrays) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        for (byte[] array : arrays) {
            output.write(array);
        }
        return output.toByteArray();
    }

    private static void sendPdf(HttpExchange exchange, int status, byte[] pdf, String reference)
            throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/pdf");
        exchange.getResponseHeaders().set("Content-Disposition",
                "attachment; filename=\"" + reference + ".pdf\"");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, pdf.length);
        exchange.getResponseBody().write(pdf);
        exchange.close();
    }

    private static void handleAdmin(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod().toUpperCase(Locale.ROOT);
        if (!prepareApiRequest(exchange, method)) {
            return;
        }
        String configuredToken = System.getenv("ADMIN_TOKEN");
        String suppliedToken = exchange.getRequestHeaders().getFirst("X-Admin-Token");
        if (configuredToken == null || configuredToken.isBlank()
                || suppliedToken == null
                || !java.security.MessageDigest.isEqual(
                        configuredToken.getBytes(StandardCharsets.UTF_8),
                        suppliedToken.getBytes(StandardCharsets.UTF_8))) {
            sendJson(exchange, 401, "{\"error\":\"Admin authentication failed.\"}");
            return;
        }

        String path = exchange.getRequestURI().getPath().substring("/api/admin/".length());
        try (Connection connection = openDatabase()) {
            if ("GET".equals(method) && "dashboard".equals(path)) {
                sendJson(exchange, 200, adminDashboardJson(connection));
            } else if ("POST".equals(method) && "products".equals(path)) {
                Map<String, List<String>> form = readForm(exchange);
                int productId = saveAdminProduct(connection, form, null);
                sendJson(exchange, 201, "{\"productId\":" + productId + "}");
            } else if (path.startsWith("products/")) {
                int productId = parsePositiveId(path.substring("products/".length()));
                if ("PUT".equals(method)) {
                    saveAdminProduct(connection, readForm(exchange), productId);
                    sendJson(exchange, 200, "{\"updated\":true}");
                } else if ("DELETE".equals(method)) {
                    try (PreparedStatement statement = connection.prepareStatement(
                            "UPDATE product SET is_active = FALSE WHERE product_id = ?")) {
                        statement.setInt(1, productId);
                        if (statement.executeUpdate() != 1) {
                            sendJson(exchange, 404, "{\"error\":\"Product not found.\"}");
                        } else {
                            sendJson(exchange, 200, "{\"deactivated\":true}");
                        }
                    }
                } else {
                    sendJson(exchange, 405, "{\"error\":\"Use PUT or DELETE for a product.\"}");
                }
            } else if (path.startsWith("orders/") && "PUT".equals(method)) {
                String reference = path.substring("orders/".length()).toUpperCase(Locale.ROOT);
                Map<String, List<String>> form = readForm(exchange);
                String status = requireValue(form, "status", 24).toLowerCase(Locale.ROOT);
                if (!List.of("pending", "confirmed", "processing", "shipped", "delivered",
                        "cancelled", "refunded").contains(status)) {
                    throw new IllegalArgumentException("Select a valid order status.");
                }
                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE `orders` SET order_status = ? WHERE order_reference = ?")) {
                    statement.setString(1, status);
                    statement.setString(2, reference);
                    if (statement.executeUpdate() != 1) {
                        sendJson(exchange, 404, "{\"error\":\"Order not found.\"}");
                    } else {
                        sendJson(exchange, 200, "{\"updated\":true}");
                    }
                }
            } else {
                sendJson(exchange, 404, "{\"error\":\"Admin endpoint not found.\"}");
            }
        } catch (IllegalArgumentException error) {
            sendJson(exchange, 400, "{\"error\":\"" + jsonEscape(error.getMessage()) + "\"}");
        } catch (SQLException error) {
            System.err.println("Admin database operation failed: " + error.getClass().getSimpleName());
            sendJson(exchange, 500, "{\"error\":\"The admin operation could not be completed.\"}");
        }
    }

    private static String adminDashboardJson(Connection connection) throws SQLException {
        StringBuilder json = new StringBuilder("{\"stats\":{");
        try (Statement statement = connection.createStatement();
             ResultSet stats = statement.executeQuery(
                     "SELECT (SELECT COUNT(*) FROM product WHERE is_active = TRUE) AS product_count, "
                             + "(SELECT COALESCE(SUM(pv.stock_quantity), 0) FROM product_variant pv "
                             + "JOIN product p ON p.product_id = pv.product_id WHERE p.is_active = TRUE) AS stock_count, "
                             + "(SELECT COUNT(*) FROM `orders`) AS order_count, "
                             + "(SELECT COALESCE(SUM(total_amount), 0) FROM `orders` "
                             + "WHERE order_status NOT IN ('cancelled', 'refunded')) AS sales_total")) {
            stats.next();
            json.append("\"products\":").append(stats.getInt("product_count"))
                    .append(",\"inventory\":").append(stats.getLong("stock_count"))
                    .append(",\"orders\":").append(stats.getLong("order_count"))
                    .append(",\"sales\":\"").append(money(decimal(stats, "sales_total"))).append("\"},\"products\":[");
        }
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(
                     "SELECT p.product_id, p.product_name, p.current_price, p.color_name, p.fit_label, "
                             + "p.description, p.image_url, p.is_active, c.category_name, sc.subcategory_name, "
                             + "COALESCE(SUM(pv.stock_quantity), 0) AS stock_count, "
                             + "GROUP_CONCAT(CONCAT(pv.size_label, '=', pv.stock_quantity) "
                             + "ORDER BY pv.product_variant_id SEPARATOR ',') AS variant_text "
                             + "FROM product p JOIN subcategory sc ON sc.subcategory_id = p.subcategory_id "
                             + "JOIN category c ON c.category_id = sc.category_id "
                             + "LEFT JOIN product_variant pv ON pv.product_id = p.product_id "
                             + "GROUP BY p.product_id, p.product_name, p.current_price, p.color_name, p.fit_label, "
                             + "p.description, p.image_url, p.is_active, c.category_name, sc.subcategory_name "
                             + "ORDER BY p.product_id")) {
            boolean first = true;
            while (rows.next()) {
                if (!first) {
                    json.append(',');
                }
                first = false;
                json.append("{\"id\":").append(rows.getInt("product_id"))
                        .append(",\"name\":\"").append(jsonEscape(value(rows, "product_name")))
                        .append("\",\"price\":\"").append(money(decimal(rows, "current_price")))
                        .append("\",\"color\":\"").append(jsonEscape(value(rows, "color_name")))
                        .append("\",\"fit\":\"").append(jsonEscape(value(rows, "fit_label")))
                        .append("\",\"description\":\"").append(jsonEscape(value(rows, "description")))
                        .append("\",\"image\":\"").append(jsonEscape(value(rows, "image_url")))
                        .append("\",\"category\":\"").append(jsonEscape(value(rows, "category_name")))
                        .append("\",\"subcategory\":\"").append(jsonEscape(value(rows, "subcategory_name")))
                        .append("\",\"stock\":").append(rows.getLong("stock_count"))
                        .append(",\"variants\":\"").append(jsonEscape(
                                rows.getString("variant_text") == null ? "" : rows.getString("variant_text")))
                        .append("\",\"active\":").append(rows.getBoolean("is_active")).append('}');
            }
        }
        json.append("],\"orders\":[");
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(
                     "SELECT o.order_reference, o.tracking_code, o.shipping_full_name, o.order_status, "
                             + "o.total_amount, o.placed_at, COUNT(oi.order_item_id) AS item_count "
                             + "FROM `orders` o LEFT JOIN order_item oi ON oi.order_id = o.order_id "
                             + "GROUP BY o.order_id, o.order_reference, o.shipping_full_name, "
                             + "o.order_status, o.total_amount, o.placed_at ORDER BY o.placed_at DESC "
                             + "LIMIT 100")) {
            boolean first = true;
            while (rows.next()) {
                if (!first) {
                    json.append(',');
                }
                first = false;
                json.append("{\"reference\":\"").append(jsonEscape(value(rows, "order_reference")))
                        .append("\",\"trackingCode\":\"").append(jsonEscape(value(rows, "tracking_code")))
                        .append("\",\"customer\":\"").append(jsonEscape(value(rows, "shipping_full_name")))
                        .append("\",\"status\":\"").append(jsonEscape(value(rows, "order_status")))
                        .append("\",\"total\":\"").append(money(decimal(rows, "total_amount")))
                        .append("\",\"placedAt\":\"").append(jsonEscape(rows.getTimestamp("placed_at").toInstant().toString()))
                        .append("\",\"items\":").append(rows.getInt("item_count")).append('}');
            }
        }
        json.append("],\"accounts\":[");
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(
                     "SELECT c.customer_id, c.full_name, c.email, c.phone, c.created_at, "
                             + "(ca.customer_id IS NOT NULL) AS has_account "
                             + "FROM customer c LEFT JOIN customer_account ca ON ca.customer_id = c.customer_id "
                             + "ORDER BY c.created_at DESC, c.customer_id DESC")) {
            boolean first = true;
            while (rows.next()) {
                if (!first) {
                    json.append(',');
                }
                first = false;
                json.append("{\"id\":").append(rows.getLong("customer_id"))
                        .append(",\"name\":\"").append(jsonEscape(value(rows, "full_name")))
                        .append("\",\"email\":\"").append(jsonEscape(value(rows, "email")))
                        .append("\",\"phone\":\"").append(jsonEscape(value(rows, "phone")))
                        .append("\",\"createdAt\":\"")
                        .append(jsonEscape(rows.getTimestamp("created_at").toInstant().toString()))
                        .append("\",\"registered\":").append(rows.getBoolean("has_account")).append('}');
            }
        }
        return json.append("]}").toString();
    }

    private static int saveAdminProduct(
            Connection connection, Map<String, List<String>> form, Integer requestedId) throws SQLException {
        boolean originalAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            int productId = saveAdminProductChanges(connection, form, requestedId);
            connection.commit();
            return productId;
        } catch (SQLException | RuntimeException error) {
            connection.rollback();
            throw error;
        } finally {
            connection.setAutoCommit(originalAutoCommit);
        }
    }

    private static int saveAdminProductChanges(
            Connection connection, Map<String, List<String>> form, Integer requestedId) throws SQLException {
        String name = requireValue(form, "name", 160);
        String category = requireValue(form, "category", 40);
        String subcategory = requireValue(form, "subcategory", 60);
        String fit = requireValue(form, "fit", 40);
        String color = requireValue(form, "color", 40);
        String description = requireValue(form, "description", 4000);
        String image = requireValue(form, "image", 600);
        if (!(image.startsWith("https://") || image.startsWith("http://"))) {
            throw new IllegalArgumentException("Use an HTTP or HTTPS image URL.");
        }
        BigDecimal price;
        try {
            price = new BigDecimal(requireValue(form, "price", 20)).setScale(2, RoundingMode.UNNECESSARY);
        } catch (NumberFormatException | ArithmeticException error) {
            throw new IllegalArgumentException("Enter a valid price with at most two decimal places.");
        }
        if (price.signum() < 0) {
            throw new IllegalArgumentException("Price cannot be negative.");
        }
        int subcategoryId;
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT sc.subcategory_id FROM subcategory sc "
                        + "JOIN category c ON c.category_id = sc.category_id "
                        + "WHERE c.category_name = ? AND sc.subcategory_name = ?")) {
            statement.setString(1, category);
            statement.setString(2, subcategory);
            try (ResultSet row = statement.executeQuery()) {
                if (!row.next()) {
                    throw new IllegalArgumentException("Select an existing category and subcategory.");
                }
                subcategoryId = row.getInt(1);
            }
        }
        int productId = requestedId == null ? nextProductId(connection) : requestedId;
        if (requestedId == null) {
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO product (product_id, subcategory_id, product_name, fit_label, color_name, "
                            + "description, image_url, current_price, is_active) VALUES (?, ?, ?, ?, ?, ?, ?, ?, TRUE)")) {
                statement.setInt(1, productId);
                statement.setInt(2, subcategoryId);
                statement.setString(3, name);
                statement.setString(4, fit);
                statement.setString(5, color);
                statement.setString(6, description);
                statement.setString(7, image);
                statement.setBigDecimal(8, price);
                statement.executeUpdate();
            }
        } else {
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE product SET subcategory_id = ?, product_name = ?, fit_label = ?, color_name = ?, "
                            + "description = ?, image_url = ?, current_price = ?, is_active = TRUE "
                            + "WHERE product_id = ?")) {
                statement.setInt(1, subcategoryId);
                statement.setString(2, name);
                statement.setString(3, fit);
                statement.setString(4, color);
                statement.setString(5, description);
                statement.setString(6, image);
                statement.setBigDecimal(7, price);
                statement.setInt(8, productId);
                if (statement.executeUpdate() != 1) {
                    throw new IllegalArgumentException("Product not found.");
                }
            }
        }
        List<VariantInput> variants = parseVariants(requireValue(form, "variants", 1000));
        try (PreparedStatement zeroStock = connection.prepareStatement(
                "UPDATE product_variant SET stock_quantity = 0 WHERE product_id = ?");
             PreparedStatement upsert = connection.prepareStatement(
                     "INSERT INTO product_variant (product_id, size_label, stock_quantity) VALUES (?, ?, ?) "
                             + "ON DUPLICATE KEY UPDATE stock_quantity = VALUES(stock_quantity)")) {
            zeroStock.setInt(1, productId);
            zeroStock.executeUpdate();
            for (VariantInput variant : variants) {
                upsert.setInt(1, productId);
                upsert.setString(2, variant.size);
                upsert.setInt(3, variant.stock);
                upsert.addBatch();
            }
            upsert.executeBatch();
        }
        return productId;
    }

    private static int nextProductId(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet row = statement.executeQuery("SELECT COALESCE(MAX(product_id), 0) + 1 FROM product")) {
            row.next();
            return row.getInt(1);
        }
    }

    private static List<VariantInput> parseVariants(String input) {
        List<VariantInput> variants = new ArrayList<>();
        for (String entry : input.split(",")) {
            String[] parts = entry.trim().split("=", 2);
            if (parts.length != 2 || !parts[0].trim().matches("^[A-Za-z0-9][A-Za-z0-9 -]{0,19}$")) {
                throw new IllegalArgumentException("Variants must use Size=Stock entries separated by commas.");
            }
            int stock;
            try {
                stock = Integer.parseInt(parts[1].trim());
            } catch (NumberFormatException error) {
                throw new IllegalArgumentException("Variant stock must be a non-negative whole number.");
            }
            if (stock < 0 || stock > 1_000_000) {
                throw new IllegalArgumentException("Variant stock must be between 0 and 1000000.");
            }
            if (variants.stream().anyMatch(variant -> variant.size.equalsIgnoreCase(parts[0].trim()))) {
                throw new IllegalArgumentException("Each size may appear only once.");
            }
            variants.add(new VariantInput(parts[0].trim(), stock));
        }
        if (variants.isEmpty() || variants.size() > 20) {
            throw new IllegalArgumentException("Add between 1 and 20 size variants.");
        }
        return variants;
    }

    private static int parsePositiveId(String input) {
        try {
            int id = Integer.parseInt(input);
            if (id < 1) {
                throw new NumberFormatException();
            }
            return id;
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException("Product ID is invalid.");
        }
    }

    private static Map<String, List<String>> readForm(HttpExchange exchange) throws IOException {
        return parseForm(new String(readLimited(exchange.getRequestBody(), MAX_BODY_BYTES), StandardCharsets.UTF_8));
    }

    private static boolean prepareApiRequest(HttpExchange exchange, String allowedMethod) throws IOException {
        String origin = exchange.getRequestHeaders().getFirst("Origin");
        if (origin != null && !isAllowedLocalOrigin(origin)) {
            sendJson(exchange, 403, "{\"error\":\"Only local website requests are accepted.\"}");
            return false;
        }
        if (origin != null) {
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", origin);
            exchange.getResponseHeaders().set("Vary", "Origin");
        }
        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.getResponseHeaders().set("Access-Control-Allow-Methods",
                    allowedMethod + ", GET, POST, PUT, DELETE, OPTIONS");
            exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, X-Admin-Token");
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
            return false;
        }
        return true;
    }

    private static Connection openDatabase() throws SQLException {
        return DriverManager.getConnection(JDBC_URL, MYSQL_USER, MYSQL_PASSWORD);
    }

    private static String environmentOrDefault(String key, String defaultValue) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private static long findOrCreateCustomer(Connection connection, String fullName, String email, String phone)
            throws SQLException {
        String sql = "INSERT INTO customer (full_name, email, phone) VALUES (?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE customer_id = LAST_INSERT_ID(customer_id), "
                + "full_name = VALUES(full_name), phone = IF(VALUES(phone) = '', phone, VALUES(phone))";
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, fullName);
            statement.setString(2, email);
            statement.setString(3, phone);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
        }
        throw new SQLException("Customer ID was not returned.");
    }

    private static String createTrackingCode() {
        byte[] random = new byte[8];
        SECURE_RANDOM.nextBytes(random);
        StringBuilder code = new StringBuilder("TRK-");
        for (byte value : random) {
            code.append(String.format(Locale.ROOT, "%02X", value & 0xff));
        }
        return code.toString();
    }

    private static List<OrderLine> lockAndValidateItems(
            Connection connection, Map<String, Integer> requestedItems) throws SQLException {
        List<OrderLine> orderLines = new ArrayList<>();
        String sql = "SELECT pv.product_variant_id, pv.stock_quantity, p.product_name, p.current_price "
                + "FROM product_variant pv "
                + "JOIN product p ON p.product_id = pv.product_id "
                + "WHERE pv.product_id = ? AND pv.size_label = ? AND p.is_active = TRUE FOR UPDATE";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (Map.Entry<String, Integer> requested : requestedItems.entrySet()) {
                String[] parts = requested.getKey().split(":", 2);
                statement.setInt(1, Integer.parseInt(parts[0]));
                statement.setString(2, parts[1]);
                try (ResultSet row = statement.executeQuery()) {
                    if (!row.next()) {
                        throw new ApiException(409,
                                "A selected product or size is no longer available. Refresh the catalogue.");
                    }
                    int quantity = requested.getValue();
                    if (row.getInt("stock_quantity") < quantity) {
                        throw new ApiException(409,
                                "Not enough stock is available for " + value(row, "product_name")
                                        + " in size " + parts[1] + ".");
                    }
                    orderLines.add(new OrderLine(row.getLong("product_variant_id"),
                            value(row, "product_name"), parts[1], quantity,
                            decimal(row, "current_price")));
                }
            }
        }
        return orderLines;
    }

    private static Discount findDiscount(Connection connection, String code) throws SQLException {
        if (code.isEmpty()) {
            return null;
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT discount_id, discount_type, discount_value FROM discount "
                        + "WHERE discount_code = ? AND is_active = TRUE "
                        + "AND (valid_from IS NULL OR valid_from <= CURRENT_TIMESTAMP) "
                        + "AND (valid_until IS NULL OR valid_until > CURRENT_TIMESTAMP)")) {
            statement.setString(1, code);
            try (ResultSet row = statement.executeQuery()) {
                if (!row.next()) {
                    throw new IllegalArgumentException("The discount code is not valid.");
                }
                return new Discount(row.getInt("discount_id"), value(row, "discount_type"),
                        decimal(row, "discount_value"));
            }
        }
    }

    private static long insertOrder(
            Connection connection, String reference, String trackingCode, long customerId, String fullName, String email,
            String phone, String address, String city, String postalCode, Discount discountRule,
            String discountCode, String shippingMethod, BigDecimal subtotal, BigDecimal discount,
            BigDecimal shipping, BigDecimal priorityFee, BigDecimal total) throws SQLException {
        String sql = "INSERT INTO `orders` (order_reference, tracking_code, customer_id, discount_id, "
                + "discount_code_at_purchase, order_status, shipping_method, currency, "
                + "shipping_full_name, shipping_email, shipping_phone, shipping_address_line1, "
                + "shipping_city, shipping_postal_code, shipping_country_code, subtotal, "
                + "discount_amount, shipping_amount, priority_fee, total_amount) "
                + "VALUES (?, ?, ?, ?, ?, 'confirmed', ?, 'ZAR', ?, ?, ?, ?, ?, ?, 'ZA', ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, reference);
            statement.setString(2, trackingCode);
            statement.setLong(3, customerId);
            if (discountRule == null) {
                statement.setNull(4, java.sql.Types.INTEGER);
            } else {
                statement.setInt(4, discountRule.id);
            }
            if (discountCode.isEmpty()) {
                statement.setNull(5, java.sql.Types.VARCHAR);
            } else {
                statement.setString(5, discountCode);
            }
            statement.setString(6, shippingMethod);
            statement.setString(7, fullName);
            statement.setString(8, email);
            statement.setString(9, phone);
            statement.setString(10, address);
            statement.setString(11, city);
            statement.setString(12, postalCode);
            statement.setBigDecimal(13, subtotal);
            statement.setBigDecimal(14, discount);
            statement.setBigDecimal(15, shipping);
            statement.setBigDecimal(16, priorityFee);
            statement.setBigDecimal(17, total);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
        }
        throw new SQLException("Order ID was not returned.");
    }

    private static void insertOrderItems(Connection connection, long orderId, List<OrderLine> lines)
            throws SQLException {
        String sql = "INSERT INTO order_item (order_id, product_variant_id, product_name_at_purchase, "
                + "size_label_at_purchase, quantity, unit_price_at_purchase) VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (OrderLine line : lines) {
                statement.setLong(1, orderId);
                statement.setLong(2, line.variantId);
                statement.setString(3, line.productName);
                statement.setString(4, line.size);
                statement.setInt(5, line.quantity);
                statement.setBigDecimal(6, line.unitPrice);
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private static void insertMockPayment(
            Connection connection, long orderId, BigDecimal total, String reference) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO payment (order_id, amount, currency, payment_method, payment_status, "
                        + "provider_name, transaction_reference, processed_at) "
                        + "VALUES (?, ?, 'ZAR', 'demo', 'mock_approved', ?, ?, CURRENT_TIMESTAMP)")) {
            statement.setLong(1, orderId);
            statement.setBigDecimal(2, total);
            statement.setString(3, "AllFit demo checkout");
            statement.setString(4, "DEMO-" + reference);
            statement.executeUpdate();
        }
    }

    private static void decrementInventory(Connection connection, List<OrderLine> lines) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE product_variant SET stock_quantity = stock_quantity - ? "
                        + "WHERE product_variant_id = ? AND stock_quantity >= ?")) {
            for (OrderLine line : lines) {
                statement.setInt(1, line.quantity);
                statement.setLong(2, line.variantId);
                statement.setInt(3, line.quantity);
                if (statement.executeUpdate() != 1) {
                    throw new ApiException(409,
                            "Stock changed while the order was being placed. Please try again.");
                }
            }
        }
    }

    private static String checkoutJson(String reference, Instant processedAt, BigDecimal subtotal,
                                       BigDecimal discount, BigDecimal shipping, BigDecimal priorityFee,
                                       BigDecimal total, String shippingMethod, String discountCode,
                                       String emailStatus, String whatsappStatus, List<OrderLine> lines,
                                       String trackingCode) {
        StringBuilder json = new StringBuilder("{\"status\":\"MOCK_APPROVED\",\"orderNumber\":\"")
                .append(jsonEscape(reference))
                .append("\",\"processedAt\":\"").append(jsonEscape(processedAt.toString()))
                .append("\",\"subtotal\":\"").append(money(subtotal))
                .append("\",\"discount\":\"").append(money(discount))
                .append("\",\"shipping\":\"").append(money(shipping))
                .append("\",\"priorityFee\":\"").append(money(priorityFee))
                .append("\",\"shippingMethod\":\"").append(jsonEscape(shippingMethod))
                .append("\",\"discountCode\":\"").append(jsonEscape(discountCode))
                .append("\",\"emailReceipt\":\"").append(jsonEscape(emailStatus))
                .append("\",\"whatsappReceipt\":\"").append(jsonEscape(whatsappStatus))
                .append("\",\"trackingCode\":\"").append(jsonEscape(trackingCode))
                .append("\",\"paymentStatus\":\"mock_approved")
                .append("\",\"total\":\"").append(money(total)).append("\",\"items\":[");
        for (int index = 0; index < lines.size(); index++) {
            if (index > 0) {
                json.append(',');
            }
            OrderLine line = lines.get(index);
            json.append("{\"name\":\"").append(jsonEscape(line.productName))
                    .append("\",\"size\":\"").append(jsonEscape(line.size))
                    .append("\",\"quantity\":").append(line.quantity)
                    .append(",\"unitPrice\":\"").append(money(line.unitPrice)).append("\"}");
        }
        return json.append("]}").toString();
    }

    private static String catalogueJson(Map<Integer, CatalogueProduct> products) {
        StringBuilder json = new StringBuilder("[");
        boolean firstProduct = true;
        for (CatalogueProduct product : products.values()) {
            if (!firstProduct) {
                json.append(',');
            }
            firstProduct = false;
            json.append("{\"id\":").append(product.id)
                    .append(",\"name\":\"").append(jsonEscape(product.name))
                    .append("\",\"category\":\"").append(jsonEscape(product.category))
                    .append("\",\"subcategory\":\"").append(jsonEscape(product.subcategory))
                    .append("\",\"fit\":\"").append(jsonEscape(product.fit))
                    .append("\",\"color\":\"").append(jsonEscape(product.color))
                    .append("\",\"description\":\"").append(jsonEscape(product.description))
                    .append("\",\"image\":\"").append(jsonEscape(product.image))
                    .append("\",\"price\":").append(money(product.price))
                    .append(",\"rating\":").append(product.rating)
                    .append(",\"reviews\":").append(product.reviews)
                    .append(",\"sizes\":[");
            boolean firstSize = true;
            for (CatalogueSize size : product.sizes) {
                if (!firstSize) {
                    json.append(',');
                }
                firstSize = false;
                json.append("{\"variantId\":").append(size.variantId)
                        .append(",\"label\":\"").append(jsonEscape(size.label))
                        .append("\",\"stock\":").append(size.stock).append('}');
            }
            json.append("]}");
        }
        return json.append(']').toString();
    }

    private static String value(ResultSet row, String column) {
        try {
            return row.getString(column);
        } catch (SQLException error) {
            throw new IllegalStateException("Could not read database result.", error);
        }
    }

    private static BigDecimal decimal(ResultSet row, String column) {
        try {
            return row.getBigDecimal(column);
        } catch (SQLException error) {
            throw new IllegalStateException("Could not read database result.", error);
        }
    }

    private static String money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString();
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
        if (body == null || body.isEmpty()) {
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

    private static final class VariantInput {
        private final String size;
        private final int stock;

        private VariantInput(String size, int stock) {
            this.size = size;
            this.stock = stock;
        }
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
        StringBuilder escaped = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '\\' -> escaped.append("\\\\");
                case '"' -> escaped.append("\\\"");
                case '\b' -> escaped.append("\\b");
                case '\f' -> escaped.append("\\f");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (character < 0x20) {
                        escaped.append(String.format("\\u%04x", (int) character));
                    } else {
                        escaped.append(character);
                    }
                }
            }
        }
        return escaped.toString();
    }

    private static final class ApiException extends RuntimeException {
        private static final long serialVersionUID = 1L;
        private final int statusCode;

        private ApiException(int statusCode, String message) {
            super(message);
            this.statusCode = statusCode;
        }
    }

    private static final class ReceiptNotFoundException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }

    private static final class OrderReceipt {
            private final String reference;
            private final String trackingCode;
            private final String name;
            private final String email;
            private final String phone;
            private final String address;
            private final String status;
            private final Instant placedAt;
            private final BigDecimal subtotal;
            private final BigDecimal discount;
            private final BigDecimal shipping;
            private final BigDecimal priority;
            private final BigDecimal total;
            private final List<ReceiptItem> items = new ArrayList<>();

            private OrderReceipt(String reference, String trackingCode, String name, String email,
                                 String phone, String address,
                                 String status, Instant placedAt, BigDecimal subtotal, BigDecimal discount,
                                 BigDecimal shipping, BigDecimal priority, BigDecimal total) {
                this.reference = reference;
                this.trackingCode = trackingCode;
                this.name = name;
                this.email = email;
                this.phone = phone;
                this.address = address;
                this.status = status;
                this.placedAt = placedAt;
                this.subtotal = subtotal;
                this.discount = discount;
                this.shipping = shipping;
                this.priority = priority;
                this.total = total;
            }
    }

    private static final class ReceiptItem {
            private final String name;
            private final String size;
            private final int quantity;
            private final BigDecimal unitPrice;

            private ReceiptItem(String name, String size, int quantity, BigDecimal unitPrice) {
                this.name = name;
                this.size = size;
                this.quantity = quantity;
                this.unitPrice = unitPrice;
        }
    }

    private static final class Discount {
        private final int id;
        private final String type;
        private final BigDecimal value;

        private Discount(int id, String type, BigDecimal value) {
            this.id = id;
            this.type = type;
            this.value = value;
        }

        private BigDecimal calculate(BigDecimal subtotal) {
            BigDecimal amount = "percentage".equals(type)
                    ? subtotal.multiply(value).movePointLeft(2)
                    : value;
            return amount.min(subtotal).setScale(2, RoundingMode.HALF_UP);
        }
    }

    private static final class OrderLine {
        private final long variantId;
        private final String productName;
        private final String size;
        private final int quantity;
        private final BigDecimal unitPrice;

        private OrderLine(long variantId, String productName, String size, int quantity, BigDecimal unitPrice) {
            this.variantId = variantId;
            this.productName = productName;
            this.size = size;
            this.quantity = quantity;
            this.unitPrice = unitPrice;
        }
    }

    private static final class CatalogueProduct {
        private final int id;
        private final String name;
        private final String category;
        private final String subcategory;
        private final String fit;
        private final String color;
        private final String description;
        private final String image;
        private final BigDecimal price;
        private final int rating;
        private final int reviews;
        private final List<CatalogueSize> sizes = new ArrayList<>();

        private CatalogueProduct(int id, String name, String category, String subcategory, String fit,
                                 String color, String description, String image, BigDecimal price,
                                 int rating, int reviews) {
            this.id = id;
            this.name = name;
            this.category = category;
            this.subcategory = subcategory;
            this.fit = fit;
            this.color = color;
            this.description = description;
            this.image = image;
            this.price = price;
            this.rating = rating;
            this.reviews = reviews;
        }
    }

    private static final class CatalogueSize {
        private final long variantId;
        private final String label;
        private final int stock;

        private CatalogueSize(long variantId, String label, int stock) {
            this.variantId = variantId;
            this.label = label;
            this.stock = stock;
        }
    }
}