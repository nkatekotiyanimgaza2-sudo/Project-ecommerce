# Java/MySQL backend

Run `start-java-backend.bat` from the repository root after installing Java 21 and Connector/J will be downloaded to the system temporary folder. Configure `MYSQL_USER` and `MYSQL_PASSWORD` in that terminal first. `MYSQL_URL` is optional and defaults to `allfit_fashion` on `127.0.0.1:3306`.

The server binds to `127.0.0.1:8080` and serves the existing `public_html` site. The launcher asks for `ADMIN_TOKEN` when it is not already configured; enter the value selected by the store owner.

- `GET /api/catalogue` returns active catalogue products, colours, sizes and current MySQL stock.
- `POST /api/checkout` uses parameterized SQL and a transaction to validate stock, store the customer/order/order items/demo payment, decrement stock, and commit.
- `GET /api/track?trackingCode=...&email=...` returns an order only when the random tracking code and matching checkout email are supplied.
- `POST /api/account` registers/signs in customer accounts; passwords are stored using PBKDF2-HMAC-SHA256 with a per-account random salt.
- `GET /api/receipt?reference=...&email=...` returns a generated PDF. `POST` to the same endpoint with `emailReceipt=true` and/or `whatsappReceipt=true` retries delivery.
- `/api/admin/*` provides dashboard/product/order/account and inventory operations and requires `X-Admin-Token`. The dashboard lists registered accounts and guest checkout customers, and stock by size.

Receipt sending is optional configuration. Gmail uses `GMAIL_SMTP_USER` and `GMAIL_SMTP_APP_PASSWORD` (Gmail app password). WhatsApp uses `WHATSAPP_ACCESS_TOKEN`, `WHATSAPP_PHONE_NUMBER_ID`, `WHATSAPP_DOCUMENT_TEMPLATE`, and optionally `WHATSAPP_TEMPLATE_LANGUAGE`. Create an approved WhatsApp template with a document header and one body text parameter containing the order reference. The customer selects and consents to the delivery channel during checkout. The order commits before provider calls; a send failure does not reverse an order. The receipt page reports provider status and keeps download/retry actions available.

Payment is a demonstration only. The backend does not receive card numbers or security codes and does not process a real payment. Do not use this local server as a production deployment without hardened authentication, TLS, operational monitoring, and payment-provider integration.
