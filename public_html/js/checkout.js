function getCheckoutCart() {
    return JSON.parse(localStorage.getItem('cart') || '[]');
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

document.addEventListener('DOMContentLoaded', function() {
    const cart = getCheckoutCart();
    if (cart.length === 0) {
        window.location.replace('cart.xhtml');
        return;
    }

    renderCheckoutSummary(cart);

    const expiry = document.getElementById('cardExpiry');
    const currentDate = new Date();
    expiry.min = `${currentDate.getFullYear()}-${String(currentDate.getMonth() + 1).padStart(2, '0')}`;

    const cardNumber = document.getElementById('cardNumber');
    cardNumber.addEventListener('input', function() {
        cardNumber.value = cardNumber.value.replace(/\D/g, '').slice(0, 16);
        cardNumber.setCustomValidity('');
    });

    document.getElementById('checkoutForm').addEventListener('submit', function(event) {
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
        cart.forEach(product => request.append('item', `${product.id}:${product.quantity}`));

        submitButton.disabled = true;
        message.innerText = 'Submitting order...';

        fetch('http://127.0.0.1:8080/api/checkout', {
            method: 'POST',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8' },
            body: request.toString()
        }).then(async response => {
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
                throw new Error(result.error || 'The Java server rejected this order.');
            }
            return result;
        }).then(result => {
            if (result.status !== 'MOCK_APPROVED') {
                throw new Error('The Java server did not accept this order.');
            }

            const address = `${form.elements.address.value}, ${form.elements.city.value}, ${form.elements.postalCode.value}`;
            document.getElementById('receiptNumber').innerText = result.orderNumber;
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

            const receiptItems = document.getElementById('receiptItems');
            receiptItems.innerHTML = '';
            cart.forEach(product => {
                const item = document.createElement('li');
                item.innerText = `${product.name}${product.selectedSize ? ` · ${product.selectedSize}` : ''} × ${product.quantity} — R${(product.price * product.quantity).toFixed(2)}`;
                receiptItems.appendChild(item);
            });

            form.reset();
            localStorage.removeItem('cart');
            localStorage.removeItem('allfit.discountCode');
            localStorage.removeItem('allfit.shippingMethod');
            document.getElementById('checkoutShell').hidden = true;
            document.getElementById('checkoutComplete').hidden = false;
        }).catch(error => {
            message.innerText = error instanceof TypeError
                ? 'Could not connect to the Java order service at 127.0.0.1:8080.'
                : error.message;
        }).finally(() => {
            submitButton.disabled = false;
        });
    });

    document.getElementById('printReceiptButton').addEventListener('click', function() {
        window.print();
    });
});