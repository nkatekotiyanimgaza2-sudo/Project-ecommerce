# AllFit Fashion

A responsive clothing-store website built with XHTML, CSS and JavaScript, with a Java/MySQL backend for live catalogue inventory and checkout. The storefront keeps its current layout and styling; the new admin and order-tracking views are separate pages.

## Features

- Product catalogue with search suggestions, category/size/price/colour filters, sorting and live stock labels.
- Product detail/size selection and local shopping bag.
- MySQL-backed checkout with transactional order/item/payment writes and stock decrement.
- Random tracking codes generated at checkout, printed in the receipt, and used for customer tracking with checkout email.
- Protected admin dashboard for registered accounts and guest checkout customers, sales, per-size inventory, product editing and order statuses.
- MySQL-backed account registration and sign-in with salted PBKDF2 password hashes.
- PDF receipts, customer-selected email/WhatsApp delivery, download and delivery retry.
- Gmail SMTP and Meta WhatsApp Cloud API adapters, configured only from environment variables.
- Simulated payment only. Card fields are checked in the browser and never sent to the backend.

## Run the MySQL-backed Java application

1. Install MySQL 8.0.16+ and Java 21.
2. In MySQL Workbench, run `database/schema.sql` and confirm the `allfit_fashion` schema was created. The script seeds sample catalogue data; the sample product colours are placeholders and can be corrected in Store admin.
3. From a PowerShell terminal at the repository root, set environment variables for that terminal session:

   ```powershell
   $env:MYSQL_USER = "your-mysql-user"
   $env:MYSQL_PASSWORD = "your-mysql-password"
   $env:MYSQL_URL = "jdbc:mysql://127.0.0.1:3306/allfit_fashion?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC"
   .\start-java-backend.bat
   ```

   `MYSQL_URL` is optional when using the default URL shown above. If `ADMIN_TOKEN` is not set, the launcher prompts for it; enter the token configured for this demo. Keep credentials and tokens out of source control. The launcher downloads Connector/J to the system temporary directory and compiles the backend.

   Alternatively, run `.\start-java-backend-secure.ps1` in PowerShell. It prompts for the MySQL username (default `root`) and reads the password with hidden input, keeps credentials only in the launcher process environment, and starts the same Java backend.
4. Keep the backend terminal open and open `http://127.0.0.1:8080/catalogue.xhtml` (and `http://127.0.0.1:8080/admin.xhtml` for admin). The admin token is checked by the Java backend, so entering it cannot work while that server is stopped. The catalogue should announce that its stock is coming from MySQL. If the database is not available, the page labels the fallback catalogue as sample-only and checkout will not fabricate an order.
5. Add an available size to the bag and place a demo order. Verify the new order/payment records and reduced `product_variant.stock_quantity` in MySQL Workbench.
6. The receipt displays a separate randomly generated tracking code. Open `http://127.0.0.1:8080/order-tracking.xhtml` and enter that code plus the checkout email. Open `http://127.0.0.1:8080/admin.xhtml` and enter the configured admin token to view all accounts/customers and inventory by size.
7. Register an account from the storefront. Confirm it appears in the admin Customer accounts list as a registered account; guest orders also appear as Guest checkout. Password hashes are stored in MySQL, not plaintext passwords.

Database account password reset is not enabled until an email-verification flow is configured. The storefront's browser-demo reset does not change a MySQL account password.

## Configure PDF receipt delivery

The checkout asks the customer which channels to use. Email is selected by default; WhatsApp requires the customer to opt in. Delivery runs after the order and inventory transaction has committed. A provider failure never cancels a committed order; the receipt page reports each channel and keeps a PDF download plus retry actions.

For Gmail SMTP, create an app password for the sending Gmail account and set:

```powershell
$env:GMAIL_SMTP_USER = "sender@gmail.com"
$env:GMAIL_SMTP_APP_PASSWORD = "gmail-app-password"
```

For WhatsApp Cloud API, configure a Meta Business number and an approved message template with a **document header** and one body text parameter for the order reference:

```powershell
$env:WHATSAPP_ACCESS_TOKEN = "meta-access-token"
$env:WHATSAPP_PHONE_NUMBER_ID = "meta-phone-number-id"
$env:WHATSAPP_DOCUMENT_TEMPLATE = "approved_document_template_name"
$env:WHATSAPP_TEMPLATE_LANGUAGE = "en_US"
```

WhatsApp numbers must include the country code. The customer must have opted in, and Meta's template/business messaging rules apply. The application sends a real PDF attachment through the Cloud API; it does not treat a prepared text link as delivery. Missing provider settings are reported as `not_configured`, and provider errors as `failed`. Set provider variables in the same terminal before launching the Java backend. Never commit tokens or app passwords.

## Other backend and assignment report

`backend/csharp/AllFitCheckoutApi` is a local mock checkout only; it does not connect to MySQL and does not provide these admin, tracking, or PDF delivery APIs. Use the Java backend for database-backed orders.

The schema and assignment documentation are in [database/schema.sql](database/schema.sql) and [database/INFS222_Database_Report.md](database/INFS222_Database_Report.md). Schema installation, MySQL account registration, order/stock transactions, Gmail delivery, and WhatsApp delivery have not been tested in this environment; validate each against configured services before relying on it. The payment is a simulated approval recorded with `payment_status = 'mock_approved'`; it is not a real charge.
