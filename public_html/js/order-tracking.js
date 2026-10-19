document.addEventListener('DOMContentLoaded', function() {
    const form = document.getElementById('trackingForm');
    const message = document.getElementById('trackingMessage');
    const result = document.getElementById('trackingResult');
    const initialCode = new URLSearchParams(window.location.search).get('trackingCode');
    if (initialCode) {
        form.elements.trackingCode.value = initialCode;
    }
    form.addEventListener('submit', async function(event) {
        event.preventDefault();
        const request = new URLSearchParams(new FormData(form));
        message.innerText = 'Looking up your order...';
        result.hidden = true;
        const endpoint = window.location.origin === 'http://localhost:8080'
            ? `${window.location.origin}/api/track`
            : 'http://127.0.0.1:8080/api/track';
        try {
            const response = await fetch(`${endpoint}?${request.toString()}`);
            const order = await response.json();
            if (!response.ok) {
                throw new Error(order.error || 'Order tracking failed.');
            }
            document.getElementById('trackedReference').innerText = order.reference;
            document.getElementById('trackedCode').innerText = order.trackingCode;
            document.getElementById('trackedStatus').innerText = order.status;
            document.getElementById('trackedDate').innerText = new Date(order.placedAt).toLocaleString();
            document.getElementById('trackedTotal').innerText = `R${Number(order.total).toFixed(2)}`;
            const items = document.getElementById('trackedItems');
            items.replaceChildren();
            order.items.forEach(item => {
                const row = document.createElement('li');
                row.innerText = `${item.name} · ${item.size} × ${item.quantity}`;
                items.appendChild(row);
            });
            document.querySelectorAll('#orderProgress li').forEach(step => {
                const stages = ['confirmed', 'processing', 'shipped', 'delivered'];
                step.classList.toggle('complete', stages.indexOf(step.dataset.status) <= stages.indexOf(order.status));
            });
            result.hidden = false;
            message.innerText = '';
        } catch (error) {
            message.innerText = error.message || 'Order tracking failed.';
        }
    });
});
