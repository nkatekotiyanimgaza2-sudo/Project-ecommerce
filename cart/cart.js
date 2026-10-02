// Get the shopping cart from localStorage
let cart = JSON.parse(localStorage.getItem("cart")) || [];

// Display the products in the cart
function displayCart() {

    const cartItems = document.getElementById("cartItems");

    cartItems.innerHTML = "";

    // Check if the cart is empty
    if (cart.length === 0) {

        cartItems.innerHTML = "<p>Your shopping cart is empty.</p>";

        document.getElementById("cartTotal").innerText = "R0.00";

        return;
    }

    let total = 0;

    // Display each product
    cart.forEach(function(product, index) {

        const subtotal = product.price * product.quantity;

        total += subtotal;

        const cartItem = document.createElement("div");

        cartItem.className = "cart-item";

        cartItem.innerHTML = `
            <img src="${product.image}" alt="${product.name}" />

            <div class="product-info">
                <h3>${product.name}</h3>
                <p class="product-price">
                    Price: R${product.price.toFixed(2)}
                </p>
            </div>

            <div class="quantity-controls">

                <button type="button"
                        onclick="decreaseQuantity(${index})">
                    −
                </button>

                <span class="quantity">
                    ${product.quantity}
                </span>

                <button type="button"
                        onclick="increaseQuantity(${index})">
                    +
                </button>

            </div>

            <div class="subtotal">
                Subtotal: R${subtotal.toFixed(2)}
            </div>

            <button type="button"
                    class="remove-button"
                    onclick="removeProduct(${index})">
                Remove
            </button>
        `;

        cartItems.appendChild(cartItem);
    });

    // Display total
    document.getElementById("cartTotal").innerText =
        "R" + total.toFixed(2);
}


// Increase product quantity
function increaseQuantity(index) {

    cart[index].quantity++;

    saveCart();

    displayCart();
}


// Decrease product quantity
function decreaseQuantity(index) {

    if (cart[index].quantity > 1) {

        cart[index].quantity--;

    } else {

        cart.splice(index, 1);

    }

    saveCart();

    displayCart();
}


// Remove product from cart
function removeProduct(index) {

    cart.splice(index, 1);

    saveCart();

    displayCart();
}


// Save cart to localStorage
function saveCart() {

    localStorage.setItem("cart", JSON.stringify(cart));
}


// Checkout button
document.getElementById("checkoutButton").addEventListener(
    "click",
    function() {

        if (cart.length === 0) {

            alert("Your shopping cart is empty.");

        } else {

            alert("Proceeding to checkout.");

        }
    }
);


// Load cart when the page opens
document.addEventListener("DOMContentLoaded", function() {

    displayCart();

});