function readCart() {
    return JSON.parse(localStorage.getItem('cart') || '[]');
}

function saveCart(cart) {
    localStorage.setItem('cart', JSON.stringify(cart));
}

const discountStorageKey = 'allfit.discountCode';
const shippingStorageKey = 'allfit.shippingMethod';
const discountRates = { SAVE10: 0.10 };

function changeSavedStock(product, amount) {
    if (!product.selectedSize || !product.sizes) {
        return true;
    }

    const inventory = JSON.parse(localStorage.getItem('allfit.inventory') || '{}');
    const sizes = inventory[product.id] || {};
    const size = product.sizes.find(option => option.label === product.selectedSize);
    const available = sizes[product.selectedSize] === undefined ? (size ? size.stock : 0) : sizes[product.selectedSize];
    if (amount < 0 && available < 1) {
        return false;
    }
    if (!inventory[product.id]) {
        inventory[product.id] = {};
    }
    inventory[product.id][product.selectedSize] = available + amount;
    localStorage.setItem('allfit.inventory', JSON.stringify(inventory));
    return true;
}

function calculateCartTotals(cart) {
    const subtotal = cart.reduce((total, product) => total + product.price * product.quantity, 0);
    const discountCode = localStorage.getItem(discountStorageKey) || '';
    const discount = Math.round(subtotal * (discountRates[discountCode] || 0) * 100) / 100;
    const shipping = Math.round((subtotal - discount) * 0.10 * 100) / 100;
    const shippingMethod = localStorage.getItem(shippingStorageKey) || 'normal';
    const priority = shippingMethod === 'priority' ? 15 : 0;
    return { subtotal, discount, shipping, priority, total: subtotal - discount + shipping + priority };
}

function displayCartTotals(cart) {
    const totals = calculateCartTotals(cart);
    document.getElementById('cartSubtotal').innerText = `R${totals.subtotal.toFixed(2)}`;
    document.getElementById('cartDiscount').innerText = `-R${totals.discount.toFixed(2)}`;
    document.getElementById('cartDiscountLine').hidden = totals.discount === 0;
    document.getElementById('cartShipping').innerText = `R${totals.shipping.toFixed(2)}`;
    document.getElementById('cartPriorityLine').hidden = totals.priority === 0;
    document.getElementById('cartTotal').innerText = `R${totals.total.toFixed(2)}`;
}

function displayCart() {
    const cart = readCart();
    const cartItems = document.getElementById('cartItems');
    const itemCount = cart.reduce((total, product) => total + product.quantity, 0);
    document.getElementById('cartItemCount').innerText = `${itemCount} ${itemCount === 1 ? 'item' : 'items'} in your bag`;
    document.getElementById('clearCartButton').disabled = cart.length === 0;
    document.getElementById('checkoutButton').disabled = cart.length === 0;
    displayCartTotals(cart);
    cartItems.innerHTML = '';

    if (cart.length === 0) {
        cartItems.innerHTML = '<p>Your shopping cart is empty.</p>';
        return;
    }

    cart.forEach((product, index) => {
        const subtotal = product.price * product.quantity;
        const imageUrl = String(product.image || '').replace(/&/g, '&amp;').replace(/"/g, '&quot;');

        const item = document.createElement('div');
        item.className = 'cart-item';
        item.innerHTML = `
            <img src="${imageUrl}" alt="${product.name}" />
            <div class="product-info">
                <h3>${product.name}</h3>
                ${product.selectedSize ? `<p class="cart-size">Size: ${product.selectedSize}</p>` : ''}
                <p class="product-price">Price: R${product.price.toFixed(2)}</p>
            </div>
            <div class="quantity-controls">
                <button type="button" onclick="changeQuantity(${index}, -1)" aria-label="Decrease quantity">−</button>
                <span class="quantity">${product.quantity}</span>
                <button type="button" onclick="changeQuantity(${index}, 1)" aria-label="Increase quantity">+</button>
            </div>
            <div class="subtotal">Subtotal: R${subtotal.toFixed(2)}</div>
            <button type="button" class="remove-button" onclick="removeProduct(${index})">Remove</button>
        `;
        cartItems.appendChild(item);
    });

}

function changeQuantity(index, amount) {
    const cart = readCart();
    if (amount > 0 && !changeSavedStock(cart[index], -1)) {
        return;
    }
    if (amount < 0) {
        changeSavedStock(cart[index], 1);
    }
    cart[index].quantity += amount;
    if (cart[index].quantity <= 0) {
        cart.splice(index, 1);
    }
    saveCart(cart);
    displayCart();
}

function removeProduct(index) {
    const cart = readCart();
    for (let quantity = 0; quantity < cart[index].quantity; quantity++) {
        changeSavedStock(cart[index], 1);
    }
    cart.splice(index, 1);
    saveCart(cart);
    displayCart();
}

document.addEventListener('DOMContentLoaded', function() {
    const discountInput = document.getElementById('discountCode');
    const discountMessage = document.getElementById('discountMessage');
    const savedCode = localStorage.getItem(discountStorageKey) || '';
    discountInput.value = savedCode;
    if (savedCode) {
        discountMessage.innerText = 'SAVE10 applied: 10% off items.';
    }

    const savedShipping = localStorage.getItem(shippingStorageKey) || 'normal';
    document.querySelectorAll('input[name="shippingMethod"]').forEach(input => {
        input.checked = input.value === savedShipping;
        input.addEventListener('change', function() {
            localStorage.setItem(shippingStorageKey, this.value);
            displayCart();
        });
    });

    displayCart();
    document.getElementById('discountForm').addEventListener('submit', function(event) {
        event.preventDefault();
        const code = discountInput.value.trim().toUpperCase();
        if (!code) {
            localStorage.removeItem(discountStorageKey);
            discountMessage.innerText = 'Discount code removed.';
        } else if (discountRates[code]) {
            localStorage.setItem(discountStorageKey, code);
            discountInput.value = code;
            discountMessage.innerText = 'SAVE10 applied: 10% off items.';
        } else {
            localStorage.removeItem(discountStorageKey);
            discountMessage.innerText = 'That discount code is not valid.';
        }
        displayCart();
    });
    document.getElementById('clearCartButton').addEventListener('click', function() {
        if (readCart().length > 0 && window.confirm('Remove all items from your cart?')) {
            readCart().forEach(product => {
                for (let quantity = 0; quantity < product.quantity; quantity++) {
                    changeSavedStock(product, 1);
                }
            });
            saveCart([]);
            displayCart();
        }
    });
    document.getElementById('checkoutButton').addEventListener('click', function() {
        if (readCart().length > 0) {
            window.location.href = 'checkout.xhtml';
        }
    });
});