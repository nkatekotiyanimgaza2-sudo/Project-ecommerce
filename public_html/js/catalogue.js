// 1. DUMMY DATA (Replace this with a fetch() call to the backend later)
// These images use online URLs. You can change them to local images later.
const products = [
    { id: 1, name: "Square Neck Top", price: 299.00, category: "Women", image: "https://images.unsplash.com/photo-1515886657613-9f3515b0c78f?w=400", rating: 5, reviews: 12 },
    { id: 2, name: "Wide Leg Trousers", price: 499.00, category: "Women", image: "https://images.unsplash.com/photo-1594633312681-425c7b97ccd1?w=400", rating: 4, reviews: 8 },
    { id: 3, name: "Shoulder Bag", price: 349.00, category: "Accessories", image: "https://images.unsplash.com/photo-1548036328-c9fa89d128fa?w=400", rating: 5, reviews: 15 },
    { id: 4, name: "Classic Sneakers", price: 599.00, category: "Shoes", image: "https://images.unsplash.com/photo-1549298916-b41d501d3772?w=400", rating: 4, reviews: 21 },
    { id: 5, name: "Midi Dress", price: 699.00, category: "Women", image: "https://images.unsplash.com/photo-1595777457583-95e059d581b8?w=400", rating: 5, reviews: 10 },
    { id: 6, name: "Knit Cardigan", price: 449.00, category: "Women", image: "https://images.unsplash.com/photo-1576566588028-4147f3842f27?w=400", rating: 4, reviews: 14 },
    { id: 7, name: "Cargo Pants", price: 549.00, category: "Men", image: "https://images.unsplash.com/photo-1517438476312-10d79c077509?w=400", rating: 4, reviews: 6 },
    { id: 8, name: "Halter Neck Top", price: 279.00, category: "Women", image: "https://images.unsplash.com/photo-1503342217505-b0a15ec3261c?w=400", rating: 5, reviews: 9 },
    { id: 9, name: "Kids Denim Jacket", price: 399.00, category: "Children", image: "https://images.unsplash.com/photo-1519457431-44ccd64a579b?w=400", rating: 5, reviews: 4 },
    { id: 10, name: "Plus Size Maxi Dress", price: 799.00, category: "Plus Size", image: "https://images.unsplash.com/photo-1585487000160-6ebcfceb0d03?w=400", rating: 5, reviews: 11 }
];

// 2. RENDER PRODUCTS FUNCTION
function renderProducts(productList) {
    const grid = document.getElementById('productGrid');
    grid.innerHTML = ''; // Clear existing products

    if (productList.length === 0) {
        grid.innerHTML = '<p>No products found matching your criteria.</p>';
        return;
    }

    productList.forEach(product => {
        const card = `
            <div class="product-card">
                <img src="${product.image}" alt="${product.name}" />
                <h3>${product.name}</h3>
                <p class="price">R ${product.price.toFixed(2)}</p>
                <p class="rating">${'★'.repeat(product.rating)}${'☆'.repeat(5 - product.rating)} (${product.reviews})</p>
                <button class="add-to-cart-btn" onclick="addToCart(${product.id})">Add to Cart</button>
            </div>
        `;
        grid.innerHTML += card;
    });
}

// 3. ADD TO CART FUNCTION
let cartTotal = 0;
function addToCart(productId) {
    cartTotal++;
    document.getElementById('cartCount').innerText = cartTotal;
    
    // Find the product name for a nice alert
    const product = products.find(p => p.id === productId);
    alert(`Added "${product.name}" to your cart!`);
    
    // NOTE FOR LATER: This is where you will send a fetch() request to the backend
    // fetch('/api/cart', { method: 'POST', body: JSON.stringify({ productId: productId }) });
}

// 4. SEARCH FUNCTIONALITY
document.getElementById('searchInput').addEventListener('input', function(e) {
    const query = e.target.value.toLowerCase();
    const filtered = products.filter(p => p.name.toLowerCase().includes(query));
    renderProducts(filtered);
});

// 5. CATEGORY FILTERING
document.querySelectorAll('.category-list a').forEach(link => {
    link.addEventListener('click', function(e) {
        e.preventDefault();
        
        // Remove active class from all, add to clicked
        document.querySelectorAll('.category-list a').forEach(a => a.classList.remove('active'));
        this.classList.add('active');

        const category = this.getAttribute('data-category');
        
        if (category === 'all') {
            renderProducts(products);
        } else {
            const filtered = products.filter(p => p.category === category);
            renderProducts(filtered);
        }
    });
});

// 6. INITIALIZE PAGE
document.addEventListener('DOMContentLoaded', function() {
    renderProducts(products);
});
