function getCheckoutCart() {
    return JSON.parse(localStorage.getItem('cart') || '[]');
}

async function refreshCheckoutPrices(cart) {
    const endpoint = window.location.origin === 'http://localhost:8080'
        ? `${window.location.origin}/api/catalogue`
        : 'http://127.0.0.1:8080/api/catalogue';
    try {
        const response = await fetch(endpoint);
        if (!response.ok) {
            return false;
        }
        const catalogue = await response.json();
        if (!Array.isArray(catalogue)) {
            return false;
        }
        cart.forEach(item => {
            const current = catalogue.find(product => Number(product.id) === Number(item.id));
            if (!current) {
                return;
            }
            item.name = current.name;
            item.price = Number(current.price);
            item.image = current.image;
            const variant = (current.sizes || []).find(size => size.label === item.selectedSize);
            if (variant) {
                item.availableStock = Number(variant.stock);
            }
        });
        localStorage.setItem('cart', JSON.stringify(cart));
        return true;
    } catch (error) {
        if (!(error instanceof TypeError)) {
            console.error('Could not refresh checkout prices from MySQL:', error);
        }
        return false;
    }
}

function renderCheckoutSummary(cart) {
    const list = document.getElementById('checkoutItems');
    const quantity = cart.reduce((sum, product) => sum + product.quantity, 0);
    document.getElementById('summaryCount').innerText = `${quantity} ${quantity === 1 ? 'item' : 'items'}`;

    cart.forEach(product => {
        const row = document.createElement('li');
        const name = document.createElement('span');
        const amount = document.createElement('strong');
        name.innerText = `${product.name}${product.selectedSize ? ` · ${product.selectedSize}` : ''} × ${product.quantity}`;
        amount.innerText = `R${(product.price * product.quantity).toFixed(2)}`;
        row.appendChild(name);
        row.appendChild(amount);
        list.appendChild(row);
    });

    const subtotal = cart.reduce((total, product) => total + product.price * product.quantity, 0);
    const discountCode = localStorage.getItem('allfit.discountCode') || '';
    const discount = discountCode === 'SAVE10' ? Math.round(subtotal * 10) / 100 : 0;
    const shipping = Math.round((subtotal - discount) * 10) / 100;
    const priority = (localStorage.getItem('allfit.shippingMethod') || 'normal') === 'priority' ? 15 : 0;
    document.getElementById('checkoutSubtotal').innerText = `R${subtotal.toFixed(2)}`;
    document.getElementById('checkoutDiscount').innerText = `-R${discount.toFixed(2)}`;
    document.getElementById('checkoutDiscountLine').hidden = discount === 0;
    document.getElementById('checkoutShipping').innerText = `R${shipping.toFixed(2)}`;
    document.getElementById('checkoutPriorityLine').hidden = priority === 0;
    document.getElementById('checkoutTotal').innerText = `R${(subtotal - discount + shipping + priority).toFixed(2)}`;
}

const checkoutEndpoints = [
    ...(window.location.origin === 'http://127.0.0.1:8080' || window.location.origin === 'http://localhost:8080'
        ? [`${window.location.origin}/api/checkout`] : []),
    'http://127.0.0.1:8080/api/checkout'
].filter((endpoint, index, endpoints) => endpoints.indexOf(endpoint) === index);

async function submitOrderToCheckout(request) {
    const errors = [];

    for (const endpoint of checkoutEndpoints) {
        try {
            const response = await fetch(endpoint, {
                method: 'POST',
                headers: { 'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8' },
                body: request.toString()
            });

            const responseText = await response.text();
            let result;
            try {
                result = JSON.parse(responseText);
            } catch (error) {
                throw new Error(response.status === 403
                    ? 'The order service rejected this request (HTTP 403).'
                    : `The order service returned an invalid response (HTTP ${response.status}).`);
            }

            if (!response.ok) {
                if (response.status === 404 || response.status === 405) {
                    errors.push(`${endpoint} returned HTTP ${response.status}.`);
                    continue;
                }
                throw new Error(result.error || 'The checkout service rejected this order.');
            }

            if (result.status !== 'MOCK_APPROVED') {
                throw new Error('The checkout service did not accept this order.');
            }

            return { result, isFallback: false, endpoint };
        } catch (error) {
            if (!(error instanceof TypeError)) {
                throw error;
            }
            errors.push(`${endpoint}: ${error.message || 'connection failed'}`);
        }
    }

    throw new Error(`The MySQL checkout service is unavailable. No order was placed. ${errors.join(' ')}`);
}

async function retryReceiptDelivery(channel) {
    const trackingCode = document.getElementById('receiptTrackingCode').innerText;
    const email = document.getElementById('receiptEmail').innerText;
    const deliveryMessage = document.getElementById('receiptDeliveryMessage');
    const receiptEndpoint = window.allfitReceiptEndpoint;
    if (!receiptEndpoint || !trackingCode || !email) {
        deliveryMessage.innerText = 'Receipt delivery is unavailable for this order. Download the PDF instead.';
        return;
    }
    const request = new URLSearchParams();
    request.set('emailReceipt', String(channel === 'email'));
    request.set('whatsappReceipt', String(channel === 'whatsapp'));
    deliveryMessage.innerText = `Retrying ${channel === 'email' ? 'email' : 'WhatsApp'} delivery...`;
    try {
        const response = await fetch(`${receiptEndpoint}?trackingCode=${encodeURIComponent(trackingCode)}&email=${encodeURIComponent(email)}`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8' },
            body: request.toString()
        });
        const result = await response.json();
        if (!response.ok) {
            throw new Error(result.error || 'Receipt delivery could not be retried.');
        }
        deliveryMessage.innerText = `${channel === 'email' ? 'Email' : 'WhatsApp'} delivery: ${result[channel]}.`;
    } catch (error) {
        deliveryMessage.innerText = error.message || 'Receipt delivery could not be retried.';
    }
}

document.addEventListener('DOMContentLoaded', async function() {
    const cart = getCheckoutCart();
    if (cart.length === 0) {
        window.location.replace('cart.xhtml');
        return;
    }

    const mysqlAvailable = await refreshCheckoutPrices(cart);
    renderCheckoutSummary(cart);
    if (!mysqlAvailable) {
        document.getElementById('checkoutMessage').innerText =
            'Live prices and order placement require the Java/MySQL backend at 127.0.0.1:8080.';
    }

    const expiry = document.getElementById('cardExpiry');
    const currentDate = new Date();
    expiry.min = `${currentDate.getFullYear()}-${String(currentDate.getMonth() + 1).padStart(2, '0')}`;

    const cardNumber = document.getElementById('cardNumber');
    cardNumber.addEventListener('input', function() {
        cardNumber.value = cardNumber.value.replace(/\D/g, '').slice(0, 16);
        cardNumber.setCustomValidity('');
    });

    document.getElementById('checkoutForm').addEventListener('submit', async function(event) {
        event.preventDefault();
        const form = this;
        ['fullName', 'phone', 'address', 'city', 'postalCode', 'cardName'].forEach(name => {
            form.elements[name].value = form.elements[name].value.trim();
        });
        cardNumber.setCustomValidity(/^\d{16}$/.test(cardNumber.value) ? '' : 'Enter exactly 16 digits.');
        if (!form.reportValidity()) {
            return;
        }

        const message = document.getElementById('checkoutMessage');
        const submitButton = form.querySelector('[type="submit"]');
        const request = new URLSearchParams();
        request.set('fullName', form.elements.fullName.value);
        request.set('email', form.elements.email.value.trim());
        request.set('phone', form.elements.phone.value);
        request.set('address', form.elements.address.value);
        request.set('city', form.elements.city.value);
        request.set('postalCode', form.elements.postalCode.value);
        request.set('mockPayment', 'true');
        request.set('discountCode', localStorage.getItem('allfit.discountCode') || '');
        request.set('shippingMethod', localStorage.getItem('allfit.shippingMethod') || 'normal');
        if (!form.elements.emailReceipt.checked && !form.elements.whatsappReceipt.checked) {
            message.innerText = 'Choose at least one PDF receipt delivery option.';
            return;
        }
        const invalidProduct = cart.find(product => !product.selectedSize);
        if (invalidProduct) {
            message.innerText = `Choose a size for ${invalidProduct.name} in your bag before checking out.`;
            return;
        }
        cart.forEach(product => {
            request.append('item', `${product.id}:${product.selectedSize}:${product.quantity}`);
        });
        request.set('emailReceipt', String(form.elements.emailReceipt.checked));
        request.set('whatsappReceipt', String(form.elements.whatsappReceipt.checked));

        submitButton.disabled = true;
        message.innerText = 'Submitting order...';

        try {
            const { result, endpoint } = await submitOrderToCheckout(request);
            message.innerText = 'Order accepted and recorded in MySQL.';
            window.allfitReceiptEndpoint = endpoint.replace(/\/api\/checkout\/?$/, '/api/receipt');

            const address = `${form.elements.address.value}, ${form.elements.city.value}, ${form.elements.postalCode.value}`;
            document.getElementById('receiptNumber').innerText = result.orderNumber;
            document.getElementById('receiptTrackingCode').innerText = result.trackingCode;
            document.getElementById('receiptDate').innerText = new Date(result.processedAt).toLocaleString();
            document.getElementById('receiptCustomer').innerText = form.elements.fullName.value;
            document.getElementById('receiptEmail').innerText = form.elements.email.value.trim();
            document.getElementById('receiptAddress').innerText = address;
            document.getElementById('receiptSubtotal').innerText = `R${Number(result.subtotal).toFixed(2)}`;
            document.getElementById('receiptDiscount').innerText = `-R${Number(result.discount).toFixed(2)}`;
            document.getElementById('receiptDiscountRow').hidden = Number(result.discount) === 0;
            document.getElementById('receiptShipping').innerText = `R${Number(result.shipping).toFixed(2)}`;
            document.getElementById('receiptPriorityRow').hidden = Number(result.priorityFee) === 0;
            document.getElementById('receiptPriority').innerText = `R${Number(result.priorityFee).toFixed(2)}`;
            document.getElementById('receiptTotal').innerText = `R${Number(result.total).toFixed(2)}`;

            const pdfUrl = `${window.allfitReceiptEndpoint}?trackingCode=${encodeURIComponent(result.trackingCode)}&email=${encodeURIComponent(form.elements.email.value.trim())}`;
            document.getElementById('downloadReceiptLink').href = pdfUrl;
            document.getElementById('trackOrderLink').href =
                `order-tracking.xhtml?trackingCode=${encodeURIComponent(result.trackingCode)}`;
            const emailResult = result.emailReceipt || 'not_requested';
            const whatsappResult = result.whatsappReceipt || 'not_requested';
            document.getElementById('receiptDeliveryMessage').innerText =
                `PDF receipt delivery — email: ${emailResult}; WhatsApp: ${whatsappResult}. Download remains available below.`;

            const receiptItems = document.getElementById('receiptItems');
            receiptItems.innerHTML = '';
            (result.items || cart.map(product => ({
                name: product.name,
                size: product.selectedSize || '',
                quantity: product.quantity,
                unitPrice: product.price
            }))).forEach(product => {
                const item = document.createElement('li');
                item.innerText = `${product.name}${product.size ? ` · ${product.size}` : ''} × ${product.quantity} — R${(Number(product.unitPrice) * product.quantity).toFixed(2)}`;
                receiptItems.appendChild(item);
            });

            form.reset();
            localStorage.removeItem('cart');
            localStorage.removeItem('allfit.discountCode');
            localStorage.removeItem('allfit.shippingMethod');
            document.getElementById('checkoutShell').hidden = true;
            document.getElementById('checkoutComplete').hidden = false;

        } catch (error) {
            message.innerText = error.message || 'The checkout could not be completed.';
        } finally {
            submitButton.disabled = false;
        }
    });

    document.getElementById('printReceiptButton').addEventListener('click', function() {
        window.print();
    });
    document.getElementById('retryEmailReceipt').addEventListener('click', function() {
        retryReceiptDelivery('email');
    });
    document.getElementById('retryWhatsAppReceipt').addEventListener('click', function() {
        retryReceiptDelivery('whatsapp');
    });
});