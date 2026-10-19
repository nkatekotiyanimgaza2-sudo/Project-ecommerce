let adminToken = '';
let adminProducts = [];
let adminCategories = {};
let adminApiBase = '';

async function readAdminJson(response, endpoint) {
    const body = await response.text();
    try {
        return body ? JSON.parse(body) : {};
    } catch (error) {
        const position = error instanceof SyntaxError
            ? error.message.match(/position (\d+)/)?.[1]
            : null;
        const location = position ? ` at character ${position}` : '';
        throw new Error(`The ${endpoint} API returned invalid JSON${location}. Restart the Java backend and try again.`);
    }
}

function escapeAdminText(value) {
    return String(value).replace(/[&<>"']/g, character => ({
        '&': '&amp;',
        '<': '&lt;',
        '>': '&gt;',
        '"': '&quot;',
        "'": '&#39;'
    })[character]);
}

async function adminRequest(path, options = {}) {
    let response;
    try {
        response = await fetch(`${adminApiBase}/api/admin/${path}`, {
            ...options,
            headers: {
                'X-Admin-Token': adminToken,
                ...(options.body ? { 'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8' } : {}),
                ...(options.headers || {})
            }
        });
    } catch (error) {
        if (error instanceof TypeError) {
            throw new Error('The Java backend is not reachable at http://127.0.0.1:8080. Start it with start-java-backend.bat, keep its terminal open, and then try again. Check that MYSQL_USER and MYSQL_PASSWORD are set in that terminal.');
        }
        throw error;
    }
    const result = await readAdminJson(response, 'Admin dashboard');
    if (!response.ok) {
        throw new Error(result.error || 'The admin request failed.');
    }
    return result;
}

function setSubcategoryOptions(category, selected) {
    const select = document.getElementById('productSubcategory');
    const values = adminCategories[category] || [];
    select.innerHTML = values.map(value =>
        `<option value="${escapeAdminText(value)}"${value === selected ? ' selected="selected"' : ''}>${escapeAdminText(value)}</option>`
    ).join('');
}

function updateCategoryOptions() {
    const select = document.getElementById('productCategory');
    const values = Object.keys(adminCategories).sort();
    select.innerHTML = values.map(value =>
        `<option value="${escapeAdminText(value)}">${escapeAdminText(value)}</option>`
    ).join('');
    if (values.length > 0) {
        setSubcategoryOptions(select.value);
    }
}

async function loadAdminDashboard() {
    const dashboard = await adminRequest('dashboard');
    document.getElementById('metricProducts').innerText = dashboard.stats.products;
    document.getElementById('metricStock').innerText = dashboard.stats.inventory;
    document.getElementById('metricOrders').innerText = dashboard.stats.orders;
    document.getElementById('metricSales').innerText = `R${Number(dashboard.stats.sales).toFixed(2)}`;
    adminProducts = dashboard.products;

    const catalogueResponse = await fetch(`${adminApiBase}/api/catalogue`);
    if (catalogueResponse.ok) {
        try {
            const catalogue = await readAdminJson(catalogueResponse, 'Catalogue');
            const variantsById = new Map(catalogue.map(product => [Number(product.id), product.sizes || []]));
            adminProducts.forEach(product => {
                product.sizes = variantsById.get(Number(product.id))
                    || (product.variants ? product.variants.split(',').map(value => {
                        const [label, stock] = value.split('=');
                        return { label, stock: Number(stock) };
                    }) : []);
            });
        } catch (error) {
            console.error('The admin dashboard will use its inventory summary:', error);
        }
    }
    adminCategories = {};
    adminProducts.forEach(product => {
        adminCategories[product.category] = adminCategories[product.category] || [];
        if (!adminCategories[product.category].includes(product.subcategory)) {
            adminCategories[product.category].push(product.subcategory);
        }
    });
    updateCategoryOptions();
    renderAdminProducts();
    renderAdminAccounts(dashboard.accounts);
    renderAdminOrders(dashboard.orders);
    document.getElementById('dashboard').hidden = false;
}

function renderAdminProducts() {
    document.getElementById('productRows').innerHTML = adminProducts.map(product => `
        <tr>
            <td>${escapeAdminText(product.name)}</td>
            <td>${escapeAdminText(product.category)} / ${escapeAdminText(product.subcategory)}</td>
            <td>${escapeAdminText(product.color)}</td>
            <td>R${Number(product.price).toFixed(2)}</td>
            <td>${(product.sizes || []).map(size => `${escapeAdminText(size.label)}: ${size.stock}`).join('<br />') || 'No variants'}</td>
            <td>${product.active ? 'Active' : 'Inactive'}</td>
            <td>
                <button type="button" data-edit="${product.id}">Edit</button>
                <button type="button" data-deactivate="${product.id}"${product.active ? '' : ' disabled="disabled"'}>Deactivate</button>
            </td>
        </tr>
    `).join('');
}

function renderAdminAccounts(accounts) {
    document.getElementById('accountRows').innerHTML = accounts.map(account => `
        <tr>
            <td>${escapeAdminText(account.name)}</td>
            <td>${escapeAdminText(account.email)}</td>
            <td>${escapeAdminText(account.phone || 'Not provided')}</td>
            <td>${new Date(account.createdAt).toLocaleDateString()}</td>
            <td>${account.registered ? 'Registered account' : 'Guest checkout'}</td>
        </tr>
    `).join('');
}

function renderAdminOrders(orders) {
    const statuses = ['pending', 'confirmed', 'processing', 'shipped', 'delivered', 'cancelled', 'refunded'];
    document.getElementById('orderRows').innerHTML = orders.map(order => `
        <tr>
            <td>${escapeAdminText(order.reference)}<br />${escapeAdminText(order.trackingCode)}</td>
            <td>${escapeAdminText(order.customer)}</td>
            <td>${order.items}</td>
            <td>R${Number(order.total).toFixed(2)}</td>
            <td>${new Date(order.placedAt).toLocaleString()}</td>
            <td><select data-order="${escapeAdminText(order.reference)}">
                ${statuses.map(status => `<option value="${status}"${status === order.status ? ' selected="selected"' : ''}>${status}</option>`).join('')}
            </select></td>
        </tr>
    `).join('');
}

function fillProductForm(product) {
    const form = document.getElementById('productForm');
    form.elements.productId.value = product ? product.id : '';
    form.elements.name.value = product ? product.name : '';
    form.elements.category.value = product ? product.category : Object.keys(adminCategories)[0] || '';
    setSubcategoryOptions(form.elements.category.value, product ? product.subcategory : '');
    form.elements.fit.value = product ? product.fit || 'Regular' : 'Regular';
    form.elements.color.value = product ? product.color : '';
    form.elements.price.value = product ? product.price : '';
    form.elements.image.value = product ? product.image : '';
    form.elements.description.value = product ? product.description : '';
    form.elements.variants.value = product
        ? (product.sizes || []).map(size => `${size.label}=${size.stock}`).join(',')
        : '';
    document.getElementById('productFormTitle').innerText = product ? `Edit ${product.name}` : 'Add a product';
}

document.addEventListener('DOMContentLoaded', function() {
    const message = document.getElementById('adminMessage');
    const origin = window.location.origin === 'http://localhost:8080'
        ? window.location.origin
        : 'http://127.0.0.1:8080';
    adminApiBase = origin;

    document.getElementById('adminAuth').addEventListener('submit', async function(event) {
        event.preventDefault();
        adminToken = this.elements.token.value;
        message.innerText = 'Authenticating...';
        try {
            await loadAdminDashboard();
            message.innerText = 'Connected to the protected MySQL dashboard.';
            this.elements.token.value = '';
        } catch (error) {
            adminToken = '';
            message.innerText = error.message || 'Dashboard connection failed.';
        }
    });

    document.getElementById('productCategory').addEventListener('change', function() {
        setSubcategoryOptions(this.value);
    });
    document.getElementById('resetProductForm').addEventListener('click', function() {
        document.getElementById('productForm').reset();
        fillProductForm(null);
    });
    document.getElementById('productRows').addEventListener('click', async function(event) {
        const button = event.target.closest('button');
        if (!button) {
            return;
        }
        if (button.dataset.edit) {
            fillProductForm(adminProducts.find(product => product.id === Number(button.dataset.edit)));
            window.scrollTo({ top: 0, behavior: 'smooth' });
        } else if (button.dataset.deactivate
            && window.confirm('Deactivate this product? It will no longer appear in the catalogue.')) {
            try {
                await adminRequest(`products/${button.dataset.deactivate}`, { method: 'DELETE' });
                await loadAdminDashboard();
                message.innerText = 'Product deactivated.';
            } catch (error) {
                message.innerText = error.message || 'Product deactivation failed.';
            }
        }
    });

    document.getElementById('productForm').addEventListener('submit', async function(event) {
        event.preventDefault();
        const values = new URLSearchParams();
        new FormData(this).forEach((value, key) => {
            if (key !== 'productId') {
                values.set(key, value);
            }
        });
        const productId = this.elements.productId.value;
        try {
            await adminRequest(productId ? `products/${productId}` : 'products', {
                method: productId ? 'PUT' : 'POST',
                body: values.toString()
            });
            await loadAdminDashboard();
            this.reset();
            fillProductForm(null);
            message.innerText = 'Product and inventory saved.';
        } catch (error) {
            message.innerText = error.message || 'Product could not be saved.';
        }
    });

    document.getElementById('orderRows').addEventListener('change', async function(event) {
        const select = event.target.closest('select[data-order]');
        if (!select) {
            return;
        }
        const values = new URLSearchParams({ status: select.value });
        try {
            await adminRequest(`orders/${encodeURIComponent(select.dataset.order)}`, {
                method: 'PUT',
                body: values.toString()
            });
            message.innerText = `Order ${select.dataset.order} updated.`;
        } catch (error) {
            message.innerText = error.message || 'Order status could not be updated.';
        }
    });
});
