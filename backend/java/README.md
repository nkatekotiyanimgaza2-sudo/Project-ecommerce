# Java demo backend

Install a Java JDK, then run `start-java-backend.bat` from the project root. Open `http://127.0.0.1:8080/catalogue.xhtml` while the server window is running.

The server uses only the JDK. `POST /api/checkout` validates customer/order fields, calculates prices from the Java product list, and returns a simulated approval and order reference. It binds to localhost and does not receive card numbers or security codes. This is not a payment processor and must not be used for real charges.