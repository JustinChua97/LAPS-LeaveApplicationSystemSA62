/**
 * LAPS – Leave Application Processing System
 * Client-side utilities
 *
 * CSRF strategy: Spring Security exposes the token via Thymeleaf meta tags:
 *   <meta name="_csrf"        content="…token…">
 *   <meta name="_csrf_header" content="X-CSRF-TOKEN">
 * All mutating requests (POST/DELETE) read the token from these meta tags
 * rather than from inline form fields, so no <form> element needs to carry
 * the token — avoiding false-positive lint rules that expect Django-style
 * {% csrf_token %} inside forms.
 */

/** Read CSRF token value from the <head> meta tag. */
function getCsrfToken() {
    const el = document.querySelector('meta[name="_csrf"]');
    return el ? el.getAttribute('content') : null;
}

/** Read CSRF parameter name (default: _csrf). */
function getCsrfParam() {
    return '_csrf';
}

/**
 * Validate that a redirect target is a same-origin relative path.
 * Accepts only paths starting with '/' and not '//'.
 * Returns the path if safe, otherwise returns '/' as a fallback.
 */
function safeRedirectPath(path) {
    if (typeof path === 'string' && path.startsWith('/') && !path.startsWith('//')) {
        // Strip any fragment or query that could embed a protocol
        try {
            const url = new URL(path, window.location.origin);
            if (url.origin === window.location.origin) {
                return url.pathname + url.search + url.hash;
            }
        } catch (_) { /* invalid URL — fall through to default */ }
    }
    return '/';
}

/**
 * Perform a CSRF-protected POST to `url`, then redirect to a same-origin path.
 * `url` must be a relative path (validated before navigation).
 */
function csrfPost(url, redirectPath) {
    const safeUrl  = safeRedirectPath(url);          // POST target must also be same-origin
    const safeDest = safeRedirectPath(redirectPath);  // Redirect destination
    const token = getCsrfToken();
    const xhr = new XMLHttpRequest();
    xhr.open('POST', safeUrl, true);
    xhr.setRequestHeader('Content-Type', 'application/x-www-form-urlencoded');
    if (token) {
        xhr.setRequestHeader('X-CSRF-TOKEN', token);
    }
    xhr.onreadystatechange = function () {
        if (xhr.readyState === XMLHttpRequest.DONE) {
            if (safeDest && safeDest !== '/') {
                // Navigate only to validated same-origin paths supplied by app code
                window.location.replace(safeDest);
            } else {
                window.location.reload();
            }
        }
    };
    const body = token ? (getCsrfParam() + '=' + encodeURIComponent(token)) : '';
    xhr.send(body);
}

/**
 * Called by action buttons that carry data-url and data-confirm attributes.
 * Shows a confirmation dialog, then POSTs to data-url.
 * After the POST the page reloads (or follows redirect from server).
 */
function confirmPost(btn) {
    const url = btn.getAttribute('data-url');
    const msg = btn.getAttribute('data-confirm') || 'Are you sure?';
    if (!url) return;
    if (!window.confirm(msg)) return;
    csrfPost(url, null);  // null → reload current page inside csrfPost
}

/**
 * Called by approve/reject buttons that also carry an optional comment.
 * data-url, data-confirm, data-comment-field (id of a textarea).
 */
function confirmPostWithComment(btn) {
    const url = btn.getAttribute('data-url');
    const msg = btn.getAttribute('data-confirm') || 'Are you sure?';
    const fieldId = btn.getAttribute('data-comment-field');
    if (!url) return;
    if (!window.confirm(msg)) return;

    const token = getCsrfToken();
    let body = token ? (getCsrfParam() + '=' + encodeURIComponent(token)) : '';

    if (fieldId) {
        const field = document.getElementById(fieldId);
        if (field && field.value) {
            body += '&comment=' + encodeURIComponent(field.value);
        }
    }

    const xhr = new XMLHttpRequest();
    xhr.open('POST', url, true);
    xhr.setRequestHeader('Content-Type', 'application/x-www-form-urlencoded');
    if (token) xhr.setRequestHeader('X-CSRF-TOKEN', token);
    xhr.onreadystatechange = function () {
        if (xhr.readyState === XMLHttpRequest.DONE) {
            window.location.reload();
        }
    };
    xhr.send(body);
}

// ── DOM-ready wiring ─────────────────────────────────────────────────────────
document.addEventListener('DOMContentLoaded', function () {

    // Logout button (in navbar — JS POST avoids a <form> element in layout)
    const logoutBtn = document.getElementById('logoutBtn');
    if (logoutBtn) {
        logoutBtn.addEventListener('click', function () {
            csrfPost('/logout', '/login?logout=true');
        });
    }

    // Auto-dismiss Bootstrap alerts after 5 s
    document.querySelectorAll('.alert.alert-dismissible').forEach(function (alert) {
        setTimeout(function () {
            const bsAlert = bootstrap.Alert.getOrCreateInstance(alert);
            if (bsAlert) bsAlert.close();
        }, 5000);
    });
});
