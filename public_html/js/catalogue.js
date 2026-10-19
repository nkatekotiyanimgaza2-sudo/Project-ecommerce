const womenSizes = ["XS", "S", "M", "L", "XL", "XXL"];
const menSizes = ["S", "M", "L", "XL", "XXL"];
const kidsSizes = ["2-3Y", "4-5Y", "6-7Y", "8-9Y", "10-12Y"];

function createProduct(id, name, category, subcategory, price, description, image, sizeLabels, fit) {
    const sizes = sizeLabels.map((label, index) => ({
        label,
        stock: 2 + ((id * 7 + index * 11) % 19)
    }));
    return {
        id,
        name,
        category,
        subcategory,
        fit: fit || "Regular",
        color: "Assorted",
        price,

        image,
        sizes,
        rating: 4 + (id % 2),
        reviews: 4 + ((id * 7) % 35)
    };
}

function cataloguePrice(id, category) {
    if (id <= 10) {
        return [299, 499, 349, 599, 699, 449, 549, 279, 399, 799][id - 1];
    }
    const cents = category === "Women"
        ? 17900 + ((id * 3749) % 82000)
        : category === "Men"
            ? 22900 + ((id * 4287) % 127100)
            : 8900 + ((id * 2317) % 51100);
    return cents / 100;
}

let products = [
    createProduct(1, "Square Neck Top", "Women", "Tops", 299, "A soft, fitted top with a clean square neckline, easy to style from day to evening.", "https://images.unsplash.com/photo-1515886657613-9f3515b0c78f?w=480", womenSizes),
    createProduct(2, "Wide Leg Trousers", "Women", "Bottoms", 499, "Relaxed wide-leg trousers with a high-rise shape for a polished, comfortable fit.", "https://images.unsplash.com/photo-1594633312681-425c7b97ccd1?w=480", womenSizes),
    createProduct(3, "Shoulder Bag", "Women", "Bags", 349, "A versatile everyday shoulder bag with a spacious interior for your essentials.", "https://images.unsplash.com/photo-1548036328-c9fa89d128fa?w=480", ["One size"]),
    createProduct(4, "Classic Sneakers", "Women", "Shoes", 599, "Low-profile everyday sneakers with a timeless shape and cushioned feel.", "https://images.unsplash.com/photo-1549298916-b41d501d3772?w=480", ["3", "4", "5", "6", "7", "8", "9"]),
    createProduct(5, "Midi Dress", "Women", "Dresses", 699, "An easy midi-length dress with a flattering silhouette for effortless occasion dressing.", "https://images.unsplash.com/photo-1595777457583-95e059d581b8?w=480", womenSizes),
    createProduct(6, "Knit Cardigan", "Women", "Outerwear", 449, "A soft knit cardigan made for comfortable layering through the changing seasons.", "https://images.unsplash.com/photo-1576566588028-4147f3842f27?w=480", womenSizes),
    createProduct(7, "Cargo Pants", "Men", "Bottoms", 549, "Utility-inspired cargo pants with a relaxed fit and practical everyday pockets.", "https://images.unsplash.com/photo-1517438476312-10d79c077509?w=480", menSizes),
    createProduct(8, "Halter Neck Top", "Women", "Tops", 279, "A lightweight halter-neck top that brings a clean, modern finish to warm-weather looks.", "https://images.unsplash.com/photo-1503342217505-b0a15ec3261c?w=480", womenSizes),
    createProduct(9, "Kids Denim Jacket", "Kids", "Outerwear", 399, "A durable denim layer with a classic cut, designed for everyday play and outings.", "https://images.unsplash.com/photo-1519457431-44ccd64a579b?w=480", kidsSizes),
    createProduct(10, "Plus Size Maxi Dress", "Women", "Dresses", 799, "A flowing maxi dress designed for a comfortable fit and an easy, confident look.", "https://images.unsplash.com/photo-1585487000160-6ebcfceb0d03?w=480", ["L", "XL", "XXL"], "Plus Size")
];
let databaseCatalogue = false;

async function loadCatalogueFromDatabase() {
    const backendOrigin = window.location.origin === 'http://localhost:8080'
        ? window.location.origin
        : 'http://127.0.0.1:8080';
    const endpoints = [`${backendOrigin}/api/catalogue`];

    for (const endpoint of endpoints) {
        try {
            const response = await fetch(endpoint);
            if (!response.ok) {
                continue;
            }
            const result = await response.json();
            if (!Array.isArray(result)) {
                throw new Error('The database catalogue returned an invalid response.');
            }
            if (result.length === 0) {
                throw new Error('The database catalogue contains no active products.');
            }
            products = result.map(item => ({
                id: Number(item.id),
                name: item.name,
                category: item.category,
                subcategory: item.subcategory,
                fit: item.fit || "Regular",
                color: item.color || "Assorted",
                description: item.description,
                image: item.image,
                price: Number(item.price),
                rating: Number(item.rating),
                reviews: Number(item.reviews),
                sizes: (item.sizes || []).map(size => ({
                    variantId: Number(size.variantId),
                    label: size.label,
                    stock: Number(size.stock)
                }))
            }));
            databaseCatalogue = true;
            return true;
        } catch (error) {
            if (error instanceof TypeError) {
                continue;
            }
            throw error;
        }
    }
    return false;
}

function escapeCatalogueText(value) {
    return String(value).replace(/[&<>"']/g, character => ({
        '&': '&amp;',
        '<': '&lt;',
        '>': '&gt;',
        '"': '&quot;',
        "'": '&#39;'
    })[character]);
}

const catalogueGroups = [
    { category: "Women", subcategory: "Dresses", sizes: womenSizes, description: "A considered dress with an easy-to-wear fit, made to bring a little polish to everyday plans.", images: ["photo-1496747611176-843222e1e57c", "photo-1595777457583-95e059d581b8", "photo-1585487000160-6ebcfceb0d03"], names: ["Pleated Wrap Midi", "Floral Day Dress", "Satin Slip Dress", "Button-Front Shirt Dress", "Linen Tiered Dress", "Smocked Summer Dress"] },
    { category: "Women", subcategory: "Tops", sizes: womenSizes, description: "An effortless wardrobe staple with a flattering shape and comfortable finish.", images: ["photo-1503342217505-b0a15ec3261c", "photo-1551488831-00ddcb6c6bd3", "photo-1576566588028-4147f3842f27"], names: ["Ribbed Scoop Tee", "Linen Button Blouse", "Soft Cotton Henley", "Ruched Sleeve Top", "Relaxed Poplin Shirt", "Cropped Knit Tee"] },
    { category: "Women", subcategory: "Bottoms", sizes: womenSizes, description: "A comfortable everyday bottom with an easy fit and thoughtful, versatile styling.", images: ["photo-1594633312681-425c7b97ccd1", "photo-1503342217505-b0a15ec3261c", "photo-1591047139829-d91aecb6caea"], names: ["Tailored Ankle Pants", "Straight-Leg Jeans", "Pleated Wide Trousers", "Everyday Denim Shorts", "Soft Jersey Leggings", "Linen Pull-On Pants"] },
    { category: "Women", subcategory: "Outerwear", sizes: womenSizes, description: "A versatile layer designed for comfort, easy layering, and everyday wear.", images: ["photo-1576566588028-4147f3842f27", "photo-1515886657613-9f3515b0c78f"], names: ["Classic Denim Jacket", "Lightweight Trench", "Quilted Everyday Vest"] },
    { category: "Women", subcategory: "Shoes", sizes: ["3", "4", "5", "6", "7", "8", "9"], description: "An easy-to-style pair with a comfortable feel and a versatile finish.", images: ["photo-1543163521-1bf539c55dd2", "photo-1549298916-b41d501d3772"], names: ["Leather-look Loafer", "Everyday Court Sneaker", "Strap Flat Sandal"] },
    { category: "Women", subcategory: "Bags", sizes: ["One size"], description: "A practical accessory sized for daily essentials and easy outfit pairing.", images: ["photo-1548036328-c9fa89d128fa", "photo-1584917865442-de89df76afd3"], names: ["Everyday Crossbody", "Soft Shopper Tote", "Mini Shoulder Bag"] },
    { category: "Women", subcategory: "Underwear", sizes: womenSizes, description: "A soft, comfortable everyday essential designed for a gentle feel and reliable fit.", images: ["photo-1583846783214-7229a91b20ed", "photo-1594633312681-425c7b97ccd1"], names: ["Cotton Brief Set", "Seamless Everyday Bra", "Lace-Trim Brief", "Soft Lounge Bralette", "Cotton Camisole"] },
    { category: "Men", subcategory: "Shirts", sizes: menSizes, description: "A dependable shirt with a comfortable cut, ready for workdays and weekends.", images: ["photo-1618354691373-d851c5c3a990", "photo-1603252109303-2751441dd157"], names: ["Oxford Button-Down", "Textured Polo Shirt", "Relaxed Linen Shirt", "Classic Crew T-Shirt", "Checked Casual Shirt", "Long-Sleeve Henley", "Lightweight Overshirt"] },
    { category: "Men", subcategory: "Bottoms", sizes: menSizes, description: "A practical everyday fit with comfort and movement built into the design.", images: ["photo-1517438476312-10d79c077509", "photo-1473966968600-fa801b869a1a"], names: ["Straight Fit Chinos", "Relaxed Denim Jeans", "Everyday Joggers", "Tailored Work Trousers", "Canvas Utility Shorts", "Slim Stretch Jeans"] },
    { category: "Men", subcategory: "Outerwear", sizes: menSizes, description: "A comfortable outer layer made for changing weather and easy everyday styling.", images: ["photo-1551028719-00167b16eac5", "photo-1544923246-77307dd654cb"], names: ["Canvas Field Jacket", "Lightweight Bomber", "Zip-Through Hoodie", "Denim Trucker Jacket", "Everyday Puffer Vest"] },
    { category: "Men", subcategory: "Shoes", sizes: ["6", "7", "8", "9", "10", "11", "12"], description: "A versatile everyday shoe with a comfortable fit and timeless styling.", images: ["photo-1542291026-7eec264c27ff", "photo-1549298916-b41d501d3772"], names: ["Low-Top Court Sneaker", "Everyday Walking Shoe", "Classic Canvas Trainer", "Casual Slip-On"] },
    { category: "Men", subcategory: "Underwear", sizes: menSizes, description: "A soft everyday layer designed for a comfortable fit throughout the day.", images: ["photo-1598033129183-c4f50c736f10", "photo-1618354691373-d851c5c3a990"], names: ["Cotton Boxer Briefs", "Everyday Trunk Set", "Soft Lounge Shorts"] },
    { category: "Men", subcategory: "Accessories", sizes: ["One size"], description: "A useful finishing touch designed to pair easily with everyday essentials.", images: ["photo-1523381210434-271e8be1f52b", "photo-1622560480654-d96214fdc887"], names: ["Canvas Weekend Cap", "Woven Everyday Belt", "Classic Knit Beanie", "Compact Crossbody Pouch"] },
    { category: "Kids", subcategory: "Tops", sizes: kidsSizes, description: "A soft, easy-care top made for busy school days, play, and weekends.", images: ["photo-1519238263530-99bdd11df2ea", "photo-1519457431-44ccd64a579b"], names: ["Everyday Graphic Tee", "Soft Cotton Polo", "Striped Long-Sleeve Top", "Play-All-Day Hoodie", "Button-Front School Shirt", "Colour-Block Sweatshirt"] },
    { category: "Kids", subcategory: "Bottoms", sizes: kidsSizes, description: "Comfortable bottoms designed to move easily through play and everyday adventures.", images: ["photo-1503919005314-30d93d07d823", "photo-1519457431-44ccd64a579b"], names: ["Pull-On Denim Jeans", "Comfy Jogger Pants", "Everyday Chino Shorts", "Soft Cotton Leggings", "School-Day Trousers", "Playground Cargo Shorts"] },
    { category: "Kids", subcategory: "Dresses", sizes: kidsSizes, description: "A comfortable dress with a playful, easy-care style for special days and everyday fun.", images: ["photo-1519238263530-99bdd11df2ea", "photo-1503919005314-30d93d07d823"], names: ["Floral Twirl Dress", "Soft Jersey Pinafore", "Button-Front Day Dress", "Party-Ready Tulle Dress"] },
    { category: "Kids", subcategory: "Outerwear", sizes: kidsSizes, description: "A practical layer that keeps little ones comfortable through changing weather.", images: ["photo-1519457431-44ccd64a579b", "photo-1519238263530-99bdd11df2ea"], names: ["Lightweight Rain Jacket", "Cosy Zip Hoodie", "Quilted Puffer Jacket", "Everyday Fleece Vest"] },
    { category: "Kids", subcategory: "Shoes", sizes: ["10C", "11C", "12C", "13C", "1Y", "2Y", "3Y"], description: "A comfortable, durable pair built for little feet on the move.", images: ["photo-1542291026-7eec264c27ff", "photo-1549298916-b41d501d3772"], names: ["Playground Runner", "Easy-Fit School Shoe", "Colour Pop Sneaker", "Weekend Canvas Shoe"] },
    { category: "Kids", subcategory: "Accessories", sizes: ["One size"], description: "A cheerful everyday accessory sized for school, outings, and play.", images: ["photo-1503919005314-30d93d07d823", "photo-1519238263530-99bdd11df2ea"], names: ["Mini Backpack", "Colourful Bucket Hat", "School-Day Lunch Bag", "Cosy Knit Beanie", "Printed Crossbody Bag"] }
];

let nextProductId = 11;
catalogueGroups.forEach(group => {
    group.names.forEach(name => {
        const id = nextProductId++;
        const imageId = group.images[(id - 11) % group.images.length];
        const image = `https://images.unsplash.com/${imageId}?auto=format&fit=crop&w=480&h=560&q=80`;
        const description = `${name}: ${group.description}`;
        products.push(createProduct(id, name, group.category, group.subcategory, cataloguePrice(id, group.category), description, image, group.sizes));
    });
});

const accountStorageKey = 'allfit.accounts';
const sessionStorageKey = 'allfit.session';
let accountMode = 'login';
let selectedProductId = null;
let selectedDepartment = 'all';
let selectedSubcategory = 'all';
let selectedFit = 'all';
let wishlistOnly = false;
let selectedProductSize = '';
let refreshCatalogue = function() {};
const wishlistStorageKey = 'allfit.wishlist';
const inventoryStorageKey = 'allfit.inventory';

// 2. RENDER PRODUCTS FUNCTION
function renderProducts(productList) {
    const grid = document.getElementById('productGrid');
    document.getElementById('productResults').innerText = `${productList.length} ${productList.length === 1 ? 'product' : 'products'}`;

    if (productList.length === 0) {
        grid.innerHTML = '';
        grid.innerHTML = '<p>No products found matching your criteria.</p>';
        return;
    }

    grid.innerHTML = productList.map(product => {
        const saved = readWishlist().includes(product.id);
        const imageUrl = escapeCatalogueText(product.image);
        const totalStock = product.sizes.reduce((total, size) => total + getAvailableStock(product, size.label), 0);
        const stockLabel = totalStock === 0 ? 'Out of stock'
            : totalStock <= 3 ? `Only ${totalStock} left` : 'In stock';
        const card = `
            <div class="product-card">
                <button type="button" class="wishlist-product${saved ? ' saved' : ''}" aria-label="${saved ? 'Remove' : 'Add'} ${escapeCatalogueText(product.name)} ${saved ? 'from' : 'to'} wishlist" aria-pressed="${saved}" onclick="toggleWishlist(${product.id})">${saved ? '♥' : '♡'}</button>
                <a class="product-open" href="#" aria-label="View ${escapeCatalogueText(product.name)} details" onclick="showProductDetails(${product.id}); return false;">
                    <img src="${imageUrl}" alt="${escapeCatalogueText(product.name)}" />
                </a>
                <h3><a class="product-open" href="#" onclick="showProductDetails(${product.id}); return false;">${escapeCatalogueText(product.name)}</a></h3>
                <p class="product-category-label">${escapeCatalogueText(product.category)} / ${escapeCatalogueText(product.subcategory)} · ${escapeCatalogueText(product.color)}</p>
                <p class="price">R ${product.price.toFixed(2)}</p>
                <p class="product-stock-label${totalStock === 0 ? ' sold-out' : totalStock <= 3 ? ' low-stock' : ''}">${stockLabel}</p>
                <p class="rating">${'★'.repeat(product.rating)}${'☆'.repeat(5 - product.rating)} (${product.reviews})</p>
                <button class="add-to-cart-btn" ${totalStock === 0 ? 'disabled="disabled"' : ''} onclick="showProductDetails(${product.id})">Choose size &amp; view stock</button>
            </div>
        `;
        return card;
    }).join('');
}

function showProductDetails(productId) {
    const product = products.find(item => item.id === productId);
    if (!product) {
        return;
    }

    selectedProductId = product.id;
    document.getElementById('productDetailImage').src = product.image;
    document.getElementById('productDetailImage').alt = product.name;
    document.getElementById('productDetailCategory').innerText = product.category;
    document.getElementById('productDetailSubcategory').innerText = product.subcategory;
    document.getElementById('productDetailName').innerText = product.name;
    document.getElementById('productDetailDescription').innerText = product.description;
    document.getElementById('productDetailPrice').innerText = `R ${product.price.toFixed(2)}`;
    document.getElementById('productDetailRating').innerText = `${'★'.repeat(product.rating)}${'☆'.repeat(5 - product.rating)} (${product.reviews} reviews)`;
    const sizeSelect = document.getElementById('productDetailSize');
    sizeSelect.innerHTML = product.sizes.map(size => {
        const stock = getAvailableStock(product, size.label);
        return `<option value="${size.label}"${stock === 0 ? ' disabled="disabled"' : ''}>${size.label}${stock === 0 ? ' · sold out' : ''}</option>`;
    }).join('');
    selectedProductSize = product.sizes.length === 1 ? product.sizes[0].label : '';
    sizeSelect.value = selectedProductSize;
    document.getElementById('productDetailWishlist').innerText = readWishlist().includes(product.id) ? '♥ Saved' : '♡ Save';
    updateProductStock();
    document.getElementById('productBackdrop').hidden = false;
    document.getElementById('productDetailSize').focus();
}

function readWishlist() {
    try {
        return JSON.parse(localStorage.getItem(wishlistStorageKey) || '[]');
    } catch (error) {
        return [];
    }
}

function readInventory() {
    try {
        return JSON.parse(localStorage.getItem(inventoryStorageKey) || '{}');
    } catch (error) {
        return {};
    }
}

function getAvailableStock(product, sizeLabel) {
    const size = product.sizes.find(option => option.label === sizeLabel);
    if (databaseCatalogue) {
        return size ? size.stock : 0;
    }
    const savedInventory = readInventory();
    const savedStock = savedInventory[product.id] && savedInventory[product.id][sizeLabel];
    return savedStock === undefined ? (size ? size.stock : 0) : savedStock;
}

function setAvailableStock(product, sizeLabel, stock) {
    const savedInventory = readInventory();
    if (!savedInventory[product.id]) {
        savedInventory[product.id] = {};
    }
    savedInventory[product.id][sizeLabel] = stock;
    localStorage.setItem(inventoryStorageKey, JSON.stringify(savedInventory));
}

function updateProductStock() {
    const product = products.find(item => item.id === selectedProductId);
    if (!product) {
        return;
    }
    const stock = selectedProductSize ? getAvailableStock(product, selectedProductSize) : null;
    const totalStock = product.sizes.reduce((total, size) => total + getAvailableStock(product, size.label), 0);
    document.getElementById('productDetailStock').innerText = stock === null
        ? `${totalStock} available across ${product.sizes.length} sizes`
        : stock > 0 ? `${stock} left in size ${selectedProductSize} · ${totalStock} total` : `Size ${selectedProductSize} is sold out`;
    document.getElementById('productDetailAdd').disabled = stock === null || stock === 0;
}

function toggleWishlist(productId) {
    const wishlist = readWishlist();
    const saved = wishlist.includes(productId);
    const updatedWishlist = saved ? wishlist.filter(id => id !== productId) : wishlist.concat(productId);
    localStorage.setItem(wishlistStorageKey, JSON.stringify(updatedWishlist));
    updateWishlistCount();
    if (selectedProductId === productId) {
        document.getElementById('productDetailWishlist').innerText = saved ? '♡ Save' : '♥ Saved';
    }
    refreshCatalogue();
}

function updateWishlistCount() {
    const wishlistButton = document.getElementById('wishlistButton');
    const count = readWishlist().length;
    wishlistButton.innerText = `♡ Wishlist (${count})`;
    wishlistButton.setAttribute('aria-pressed', String(wishlistOnly));
    wishlistButton.classList.toggle('active', wishlistOnly);
}

function addToCart(productId) {
    const product = products.find(item => item.id === productId);
    if (!product || !selectedProductSize) {
        return;
    }
    const availableStock = getAvailableStock(product, selectedProductSize);
    if (availableStock < 1) {
        updateProductStock();
        return;
    }

    const cart = JSON.parse(localStorage.getItem('cart') || '[]');
    const cartItem = cart.find(item => item.id === productId && item.selectedSize === selectedProductSize);

    if (databaseCatalogue && cartItem && cartItem.quantity >= availableStock) {
        document.getElementById('productDetailMessage').innerText = 'Your bag already contains all available stock in this size.';
        return;
    }
    if (cartItem) {
        cartItem.quantity++;
    } else {
        cart.push({ ...product, selectedSize: selectedProductSize, quantity: 1 });
    }

    if (!databaseCatalogue) {
        setAvailableStock(product, selectedProductSize, availableStock - 1);
    }
    localStorage.setItem('cart', JSON.stringify(cart));
    updateCartCount();
    const sizeSelect = document.getElementById('productDetailSize');
    const currentOption = product.sizes.find(size => size.label === selectedProductSize);
    sizeSelect.innerHTML = product.sizes.map(size => {
        const stock = getAvailableStock(product, size.label);
        return `<option value="${size.label}"${stock === 0 ? ' disabled="disabled"' : ''}>${size.label}${stock === 0 ? ' · sold out' : ''}</option>`;
    }).join('');
    selectedProductSize = getAvailableStock(product, currentOption.label) > 0 ? currentOption.label : '';
    sizeSelect.value = selectedProductSize;
    document.getElementById('productDetailMessage').innerText = 'Added to your bag.';
    updateProductStock();
}

function updateCartCount() {
    const cart = JSON.parse(localStorage.getItem('cart') || '[]');
    const count = cart.reduce((total, item) => total + item.quantity, 0);
    document.getElementById('cartCount').innerText = count;
}

function setAccountMode(mode) {
    accountMode = mode;
    const registering = mode === 'register';
    const resetting = mode === 'reset';
    document.getElementById('nameField').hidden = !registering;
    document.getElementById('confirmField').hidden = !registering && !resetting;
    document.getElementById('accountName').required = registering;
    document.getElementById('accountConfirm').required = registering || resetting;
    document.getElementById('accountPassword').autocomplete = registering || resetting ? 'new-password' : 'current-password';
    document.getElementById('passwordLabelText').innerText = resetting ? 'New password' : 'Password';
    document.getElementById('accountTitle').innerText = registering
        ? 'Create your account'
        : resetting ? 'Reset your password' : 'Welcome back';
    document.getElementById('accountSubmit').innerText = registering
        ? 'Create account'
        : resetting ? 'Reset password' : 'Sign in';
    document.getElementById('accountModes').hidden = resetting;
    document.getElementById('forgotPasswordButton').hidden = registering || resetting;
    document.getElementById('backToLoginButton').hidden = !resetting;
    document.getElementById('accountModeNote').hidden = !resetting;
    document.getElementById('loginMode').classList.toggle('selected', !registering);
    document.getElementById('registerMode').classList.toggle('selected', registering);
    document.getElementById('loginMode').setAttribute('aria-selected', String(!registering));
    document.getElementById('registerMode').setAttribute('aria-selected', String(registering));
    document.getElementById('accountMessage').innerText = '';
}

function updateAccountButton() {
    const accountButton = document.getElementById('accountButton');
    const session = JSON.parse(sessionStorage.getItem(sessionStorageKey) || 'null');
    accountButton.innerText = session ? `👤 ${session.name} · Sign out` : '👤 Login / Register';
    accountButton.setAttribute('aria-label', session ? `Sign out ${session.name}` : 'Login or register');
}

function createSalt() {
    const salt = crypto.getRandomValues(new Uint8Array(16));
    return btoa(String.fromCharCode.apply(null, salt));
}

async function hashPassword(password, salt) {
    const encoder = new TextEncoder();
    const key = await crypto.subtle.importKey('raw', encoder.encode(password), 'PBKDF2', false, ['deriveBits']);
    const saltBytes = Uint8Array.from(atob(salt), character => character.charCodeAt(0));
    const result = await crypto.subtle.deriveBits({
        name: 'PBKDF2',
        salt: saltBytes,
        iterations: 150000,
        hash: 'SHA-256'
    }, key, 256);
    return Array.from(new Uint8Array(result), byte => byte.toString(16).padStart(2, '0')).join('');
}

async function handleAccountSubmit(event) {
    event.preventDefault();
    const form = document.getElementById('accountForm');
    if (!form.reportValidity()) {
        return;
    }

    const email = document.getElementById('accountEmail').value.trim().toLowerCase();
    const password = document.getElementById('accountPassword').value;
    const message = document.getElementById('accountMessage');
    message.innerText = 'Checking your details...';

    try {
        if (accountMode === 'register' || accountMode === 'login') {
            const confirmation = document.getElementById('accountConfirm').value;
            const name = document.getElementById('accountName').value.trim();
            if (accountMode === 'register' && !name) {
                message.innerText = 'Enter your full name.';
                return;
            }
            if (accountMode === 'register' && password !== confirmation) {
                message.innerText = 'Your passwords do not match.';
                return;
            }
            const endpoint = window.location.origin === 'http://localhost:8080'
                ? `${window.location.origin}/api/account`
                : 'http://127.0.0.1:8080/api/account';
            const request = new URLSearchParams({
                mode: accountMode,
                email,
                password,
                ...(accountMode === 'register' ? { name } : {})
            });
            const response = await fetch(endpoint, {
                method: 'POST',
                headers: { 'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8' },
                body: request.toString()
            });
            const account = await response.json();
            if (!response.ok) {
                throw new Error(account.error || 'The account request failed.');
            }
            sessionStorage.setItem(sessionStorageKey, JSON.stringify({
                id: account.id,
                name: account.name,
                email: account.email
            }));
            document.getElementById('accountBackdrop').hidden = true;
            form.reset();
            setAccountMode('login');
            updateAccountButton();
            return;
        }

        const accounts = JSON.parse(localStorage.getItem(accountStorageKey) || '[]');
        let account;

        if (accountMode === 'reset') {
            const confirmation = document.getElementById('accountConfirm').value;
            if (password !== confirmation) {
                message.innerText = 'Your passwords do not match.';
                return;
            }

            account = accounts.find(savedAccount => savedAccount.email === email);
            if (account) {
                const salt = createSalt();
                account.salt = salt;
                account.passwordHash = await hashPassword(password, salt);
                localStorage.setItem(accountStorageKey, JSON.stringify(accounts));
            }
            message.innerText = 'This resets browser-only demo accounts. Database accounts require a verified email reset.';
            return;
        }
    } catch (error) {
        message.innerText = error.message || 'The account service is unavailable. Start the Java/MySQL backend.';
    }
}

// 4. INITIALIZE PAGE
document.addEventListener('DOMContentLoaded', async function() {
    let hasDatabaseCatalogue = false;
    let catalogueLoadError = null;
    try {
        hasDatabaseCatalogue = await loadCatalogueFromDatabase();
    } catch (error) {
        catalogueLoadError = error;
        console.error('The MySQL catalogue could not be read:', error);
    }
    document.getElementById('catalogueDataNotice').innerText = hasDatabaseCatalogue
        ? 'Live catalogue and stock from MySQL.'
        : catalogueLoadError
            ? 'The live catalogue returned an error. Showing sample items only; orders require the backend.'
            : 'Showing sample catalogue only. Live MySQL stock is unavailable; orders require the backend.';
    const searchInput = document.getElementById('searchInput');
    const searchSuggestions = document.getElementById('searchSuggestions');
    let activeSuggestionIndex = -1;

    const closeSearchSuggestions = function() {
        searchSuggestions.hidden = true;
        searchInput.setAttribute('aria-expanded', 'false');
        searchInput.removeAttribute('aria-activedescendant');
        activeSuggestionIndex = -1;
    };

    const setActiveSuggestion = function(index) {
        const options = Array.from(searchSuggestions.querySelectorAll('[role="option"]'));
        if (options.length === 0) {
            return;
        }
        activeSuggestionIndex = (index + options.length) % options.length;
        options.forEach((option, optionIndex) => {
            const isActive = optionIndex === activeSuggestionIndex;
            option.setAttribute('aria-selected', String(isActive));
            option.classList.toggle('active', isActive);
        });
        searchInput.setAttribute('aria-activedescendant', options[activeSuggestionIndex].id);
    };

    const renderSearchSuggestions = function(query) {
        const normalizedQuery = query.trim().toLowerCase();
        searchSuggestions.innerHTML = '';
        activeSuggestionIndex = -1;
        searchInput.removeAttribute('aria-activedescendant');

        if (!normalizedQuery) {
            closeSearchSuggestions();
            return;
        }

        const matches = products
            .filter(product => `${product.name} ${product.category} ${product.subcategory} ${product.description}`
                .toLowerCase().includes(normalizedQuery))
            .sort((first, second) => {
                const firstStartsWith = first.name.toLowerCase().startsWith(normalizedQuery);
                const secondStartsWith = second.name.toLowerCase().startsWith(normalizedQuery);
                return Number(secondStartsWith) - Number(firstStartsWith) || first.name.localeCompare(second.name);
            })
            .slice(0, 6);

        if (matches.length === 0) {
            closeSearchSuggestions();
            return;
        }

        matches.forEach((product, index) => {
            const option = document.createElement('button');
            const image = document.createElement('img');
            const details = document.createElement('span');
            const name = document.createElement('span');
            const category = document.createElement('span');
            const price = document.createElement('span');
            option.type = 'button';
            option.id = `searchSuggestion${index}`;
            option.className = 'search-suggestion';
            option.setAttribute('role', 'option');
            option.setAttribute('aria-selected', 'false');
            image.src = product.image;
            image.alt = '';
            image.loading = 'lazy';
            details.className = 'search-suggestion-details';
            name.className = 'search-suggestion-name';
            name.innerText = product.name;
            category.className = 'search-suggestion-category';
            category.innerText = `${product.category} · ${product.subcategory}`;
            price.className = 'search-suggestion-price';
            price.innerText = `R${product.price.toFixed(2)}`;
            details.append(name, category);
            option.append(image, details, price);
            option.addEventListener('click', function() {
                searchInput.value = product.name;
                closeSearchSuggestions();
                filterProducts();
                document.getElementById('shop').scrollIntoView({ behavior: 'smooth' });
            });
            searchSuggestions.appendChild(option);
        });

        searchSuggestions.hidden = false;
        searchInput.setAttribute('aria-expanded', 'true');
    };

    const filterProducts = function() {
        const query = searchInput.value.trim().toLowerCase();
        const maxPrice = Number(document.getElementById('priceFilter').value);
        const sizes = Array.from(document.querySelectorAll('.size-filter:checked')).map(input => input.value);
        const sortOrder = document.getElementById('sortProducts').value;
        const color = document.getElementById('colorFilter').value;
        const stockFilter = document.getElementById('stockFilter').value;
        const wishlist = readWishlist();
        const filtered = products.filter(product => {
            const matchesQuery = `${product.name} ${product.description} ${product.category} ${product.subcategory}`.toLowerCase().includes(query);
            const matchesDepartment = selectedDepartment === 'all' || product.category === selectedDepartment;
            const matchesSubcategory = selectedSubcategory === 'all' || product.subcategory === selectedSubcategory;
            const matchesFit = selectedFit === 'all' || product.fit === selectedFit;
            const matchesPrice = product.price <= maxPrice;
            const matchesSize = sizes.length === 0 || product.sizes.some(size => sizes.includes(size.label));
            const totalStock = product.sizes.reduce((total, size) => total + getAvailableStock(product, size.label), 0);
            const matchesColor = color === 'all' || product.color.toLowerCase() === color.toLowerCase();
            const matchesStock = stockFilter === 'all'
                || (stockFilter === 'in-stock' && totalStock > 3)
                || (stockFilter === 'low-stock' && totalStock > 0 && totalStock <= 3)
                || (stockFilter === 'out-of-stock' && totalStock === 0);
            const matchesWishlist = !wishlistOnly || wishlist.includes(product.id);
            return matchesQuery && matchesDepartment && matchesSubcategory && matchesFit
                && matchesPrice && matchesSize && matchesColor && matchesStock && matchesWishlist;
        });

        if (sortOrder === 'price-low') {
            filtered.sort((first, second) => first.price - second.price);
        } else if (sortOrder === 'price-high') {
            filtered.sort((first, second) => second.price - first.price);
        } else if (sortOrder === 'name') {
            filtered.sort((first, second) => first.name.localeCompare(second.name));
        }
        renderProducts(filtered);
    };
    refreshCatalogue = filterProducts;

    const categoryList = document.getElementById('categoryList');
    const sizeFilterList = document.getElementById('sizeFilterList');
    const availableSizeLabels = Array.from(new Set(products.flatMap(product => product.sizes.map(size => size.label))))
        .sort((first, second) => first.localeCompare(second, undefined, { numeric: true }));
    sizeFilterList.innerHTML = availableSizeLabels.map(size =>
        `<li><label><input type="checkbox" class="size-filter" value="${size}" /> ${size}</label></li>`
    ).join('');
    const colorFilter = document.getElementById('colorFilter');
    Array.from(new Set(products.map(product => product.color).filter(Boolean)))
        .sort((first, second) => first.localeCompare(second))
        .forEach(color => {
            const option = document.createElement('option');
            option.value = color;
            option.textContent = color;
            colorFilter.appendChild(option);
        });

    const departments = ['Women', 'Men', 'Kids'];
    categoryList.innerHTML = '<li><button type="button" class="category-filter active" data-department="all">All products <span>100</span></button></li>';
    departments.forEach(department => {
        const departmentProducts = products.filter(product => product.category === department);
        const subcategories = Array.from(new Set(departmentProducts.map(product => product.subcategory)));
        const subcategoryMarkup = subcategories.map(subcategory => {
            const count = departmentProducts.filter(product => product.subcategory === subcategory).length;
            return `<li><button type="button" class="subcategory-filter" data-department="${department}" data-subcategory="${subcategory}">${subcategory}<span>${count}</span></button></li>`;
        }).join('');
        categoryList.innerHTML += `
            <li class="department-group">
                <button type="button" class="category-filter" data-department="${department}">${department}<span>${departmentProducts.length}</span></button>
                <ul class="subcategory-list">${subcategoryMarkup}</ul>
            </li>`;
    });
    categoryList.innerHTML += '<li><button type="button" class="fit-filter" data-fit="Plus Size">Plus size</button></li>';

    searchInput.addEventListener('input', function() {
        renderSearchSuggestions(searchInput.value);
        filterProducts();
    });
    searchInput.addEventListener('keydown', function(event) {
        const options = searchSuggestions.querySelectorAll('[role="option"]');
        if (event.key === 'ArrowDown' && !searchSuggestions.hidden) {
            event.preventDefault();
            setActiveSuggestion(activeSuggestionIndex + 1);
        } else if (event.key === 'ArrowUp' && !searchSuggestions.hidden) {
            event.preventDefault();
            setActiveSuggestion(activeSuggestionIndex < 0 ? options.length - 1 : activeSuggestionIndex - 1);
        } else if (event.key === 'Enter' && activeSuggestionIndex >= 0 && !searchSuggestions.hidden) {
            event.preventDefault();
            options[activeSuggestionIndex].click();
        } else if (event.key === 'Escape') {
            closeSearchSuggestions();
        }
    });
    document.getElementById('searchForm').addEventListener('submit', function(event) {
        event.preventDefault();
        closeSearchSuggestions();
        filterProducts();
    });
    document.addEventListener('click', function(event) {
        if (!event.target.closest('#searchForm')) {
            closeSearchSuggestions();
        }
    });

    categoryList.addEventListener('click', function(event) {
        const button = event.target.closest('button');
        if (!button) {
            return;
        }
        wishlistOnly = false;
        if (button.hasAttribute('data-fit')) {
            selectedFit = button.getAttribute('data-fit');
            selectedDepartment = 'all';
            selectedSubcategory = 'all';
        } else {
            selectedDepartment = button.getAttribute('data-department');
            selectedSubcategory = button.getAttribute('data-subcategory') || 'all';
            selectedFit = 'all';
        }
        categoryList.querySelectorAll('button').forEach(item => item.classList.toggle('active', item === button));
        updateWishlistCount();
        filterProducts();
    });

    document.getElementById('priceFilter').addEventListener('input', function() {
        document.getElementById('priceFilterLabel').innerText = Number(this.value) === 1500 ? 'R1500+' : `R${this.value}`;
        filterProducts();
    });
    document.querySelectorAll('.size-filter').forEach(input => input.addEventListener('change', filterProducts));
    document.getElementById('sortProducts').addEventListener('change', filterProducts);
    colorFilter.addEventListener('change', filterProducts);
    document.getElementById('stockFilter').addEventListener('change', filterProducts);
    document.querySelector('.clear-btn').addEventListener('click', function() {
        searchInput.value = '';
        closeSearchSuggestions();
        selectedDepartment = 'all';
        selectedSubcategory = 'all';
        selectedFit = 'all';
        wishlistOnly = false;
        document.getElementById('priceFilter').value = '1500';
        document.getElementById('priceFilterLabel').innerText = 'R1500+';
        document.querySelectorAll('.size-filter').forEach(input => { input.checked = false; });
        document.getElementById('sortProducts').value = 'featured';
        colorFilter.value = 'all';
        document.getElementById('stockFilter').value = 'all';
        categoryList.querySelectorAll('button').forEach(button => button.classList.toggle('active', button.getAttribute('data-department') === 'all'));
        updateWishlistCount();
        filterProducts();
    });

    updateCartCount();
    updateWishlistCount();
    updateAccountButton();
    filterProducts();

    document.getElementById('wishlistButton').addEventListener('click', function() {
        wishlistOnly = !wishlistOnly;
        if (wishlistOnly) {
            searchInput.value = '';
            closeSearchSuggestions();
            selectedDepartment = 'all';
            selectedSubcategory = 'all';
            selectedFit = 'all';
            document.getElementById('priceFilter').value = '1500';
            document.getElementById('priceFilterLabel').innerText = 'R1500+';
            document.querySelectorAll('.size-filter').forEach(input => { input.checked = false; });
            document.getElementById('sortProducts').value = 'featured';
            categoryList.querySelectorAll('button').forEach(button => button.classList.toggle('active', button.getAttribute('data-department') === 'all'));
        }
        updateWishlistCount();
        filterProducts();
        document.getElementById('shop').scrollIntoView({ behavior: 'smooth' });
    });

    const productBackdrop = document.getElementById('productBackdrop');
    document.getElementById('closeProductButton').addEventListener('click', function() {
        productBackdrop.hidden = true;
    });
    document.getElementById('productDetailAdd').addEventListener('click', function() {
        if (selectedProductId !== null) {
            addToCart(selectedProductId);
        }
    });
    document.getElementById('productDetailSize').addEventListener('change', function() {
        selectedProductSize = this.value;
        document.getElementById('productDetailMessage').innerText = '';
        updateProductStock();
    });
    document.getElementById('productDetailWishlist').addEventListener('click', function() {
        if (selectedProductId !== null) {
            toggleWishlist(selectedProductId);
        }
    });
    productBackdrop.addEventListener('click', function(event) {
        if (event.target === productBackdrop) {
            productBackdrop.hidden = true;
        }
    });
    productBackdrop.addEventListener('keydown', function(event) {
        if (event.key === 'Escape') {
            productBackdrop.hidden = true;
        }
    });

    const accountBackdrop = document.getElementById('accountBackdrop');
    document.getElementById('accountButton').addEventListener('click', function() {
        if (sessionStorage.getItem(sessionStorageKey)) {
            sessionStorage.removeItem(sessionStorageKey);
            updateAccountButton();
            return;
        }
        const accounts = JSON.parse(localStorage.getItem(accountStorageKey) || '[]');
        setAccountMode(accounts.length === 0 ? 'register' : 'login');
        accountBackdrop.hidden = false;
        document.getElementById(accounts.length === 0 ? 'accountName' : 'accountEmail').focus();
    });
    document.getElementById('closeAccountButton').addEventListener('click', function() {
        accountBackdrop.hidden = true;
        document.getElementById('accountButton').focus();
    });
    accountBackdrop.addEventListener('click', function(event) {
        if (event.target === accountBackdrop) {
            accountBackdrop.hidden = true;
            document.getElementById('accountButton').focus();
        }
    });
    accountBackdrop.addEventListener('keydown', function(event) {
        if (event.key === 'Escape') {
            accountBackdrop.hidden = true;
            document.getElementById('accountButton').focus();
        }
        if (event.key === 'Tab') {
            const controls = Array.from(accountBackdrop.querySelectorAll('button, input'))
                .filter(control => !control.disabled && !control.closest('[hidden]'));
            const firstControl = controls[0];
            const lastControl = controls[controls.length - 1];
            if (event.shiftKey && document.activeElement === firstControl) {
                event.preventDefault();
                lastControl.focus();
            } else if (!event.shiftKey && document.activeElement === lastControl) {
                event.preventDefault();
                firstControl.focus();
            }
        }
    });
    document.getElementById('loginMode').addEventListener('click', function() {
        setAccountMode('login');
    });
    document.getElementById('registerMode').addEventListener('click', function() {
        setAccountMode('register');
    });
    document.getElementById('forgotPasswordButton').addEventListener('click', function() {
        setAccountMode('reset');
        document.getElementById('accountEmail').focus();
    });
    document.getElementById('backToLoginButton').addEventListener('click', function() {
        setAccountMode('login');
        document.getElementById('accountEmail').focus();
    });
    document.getElementById('accountForm').addEventListener('submit', handleAccountSubmit);
});
