(function (window, document) {
    "use strict";

    const API_PREFIX = "/api";
    let csrfTokenPromise = null;

    class ApiError extends Error {
        constructor(message, status, code, correlationId) {
            super(message);
            this.name = "ApiError";
            this.status = status;
            this.code = code;
            this.correlationId = correlationId;
        }
    }

    function contextPath() {
        const pathname = window.location.pathname;
        const markers = ["/pages/", "/index.html", "/backend/", "/swagger/"];
        const indexes = markers
            .map(marker => pathname.indexOf(marker))
            .filter(index => index >= 0);
        return indexes.length === 0 ? "" : pathname.slice(0, Math.min(...indexes));
    }

    function endpoint(path) {
        const normalized = path.startsWith("/") ? path : `/${path}`;
        return `${contextPath()}${API_PREFIX}${normalized}`;
    }

    function responseError(payload, response) {
        const error = payload && payload.error ? payload.error : {};
        return new ApiError(
            error.message || `Yêu cầu thất bại (${response.status})`,
            response.status,
            error.code || "REQUEST_FAILED",
            error.correlationId || null
        );
    }

    async function readJson(response) {
        const text = await response.text();
        if (!text) return null;
        try {
            return JSON.parse(text);
        } catch {
            throw new ApiError("Backend trả về dữ liệu không hợp lệ", response.status, "INVALID_RESPONSE", null);
        }
    }

    async function getCsrfToken() {
        if (!csrfTokenPromise) {
            csrfTokenPromise = fetch(endpoint("/auth/csrf"), {
                credentials: "include",
                headers: { Accept: "application/json" }
            }).then(async response => {
                const payload = await readJson(response);
                if (!response.ok || !payload || !payload.data || !payload.data.token) {
                    throw responseError(payload, response);
                }
                return payload.data.token;
            }).catch(error => {
                csrfTokenPromise = null;
                throw error;
            });
        }
        return csrfTokenPromise;
    }

    function idempotencyKey() {
        if (window.crypto && typeof window.crypto.randomUUID === "function") {
            return window.crypto.randomUUID();
        }
        return "xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx".replace(/[xy]/g, character => {
            const random = Math.random() * 16 | 0;
            const value = character === "x" ? random : (random & 3 | 8);
            return value.toString(16);
        });
    }

    async function request(path, options = {}, retryCsrf = true) {
        const method = (options.method || "GET").toUpperCase();
        const headers = new Headers(options.headers || {});
        headers.set("Accept", "application/json");
        if (options.body !== undefined && !(options.body instanceof FormData)) {
            headers.set("Content-Type", "application/json");
        }
        if (!["GET", "HEAD", "OPTIONS"].includes(method)) {
            headers.set("X-CSRF-Token", await getCsrfToken());
        }

        const response = await fetch(endpoint(path), {
            ...options,
            method,
            headers,
            credentials: "include"
        });
        const payload = await readJson(response);
        if (!response.ok) {
            if (retryCsrf && response.status === 403 && payload?.error?.code === "CSRF_INVALID") {
                csrfTokenPromise = null;
                return request(path, options, false);
            }
            throw responseError(payload, response);
        }
        return payload?.data ?? payload;
    }

    function jsonRequest(path, method, body, headers = {}) {
        return request(path, {
            method,
            headers,
            body: JSON.stringify(body)
        });
    }

    const api = {
        ApiError,
        contextPath,
        request,
        auth: {
            login: credentials => jsonRequest("/auth/login", "POST", credentials),
            register: credentials => jsonRequest("/auth/register", "POST", credentials),
            logout: () => jsonRequest("/auth/logout", "POST", {}),
            me: () => request("/me"),
            sendOtp: body => jsonRequest("/auth/otp/send", "POST", body),
            verifyOtp: body => jsonRequest("/auth/otp/verify", "POST", body)
        },
        events: {
            list: params => request(`/events?${new URLSearchParams(params).toString()}`),
            get: eventId => request(`/events/${encodeURIComponent(eventId)}`),
            zones: eventId => request(`/events/${encodeURIComponent(eventId)}/zones`),
            categories: () => request("/event-categories")
        },
        holds: {
            create: body => jsonRequest("/holds", "POST", body, { "Idempotency-Key": idempotencyKey() }),
            cancel: holdId => jsonRequest(`/holds/${encodeURIComponent(holdId)}/cancel`, "POST", {})
        },
        orders: {
            create: holdId => jsonRequest("/orders", "POST", { holdId }),
            applyCoupon: (orderId, couponCode) => jsonRequest(`/orders/${encodeURIComponent(orderId)}/coupon`, "POST", { couponCode })
        },
        payments: {
            confirm: (orderId, body = {}) => jsonRequest(`/payments/${encodeURIComponent(orderId)}`, "POST", body)
        },
        tickets: {
            mine: () => request("/me/tickets"),
            qrUrl: ticketId => endpoint(`/tickets/${encodeURIComponent(ticketId)}/qr`)
        },
        profile: () => request("/me/profile"),
        memberships: () => request("/me/memberships"),
        organizationRequests: {
            mine: () => request("/me/organization-requests"),
            create: body => jsonRequest("/organization-requests", "POST", body)
        },
        organizations: {
            overview: organizationId => request(`/organizations/${encodeURIComponent(organizationId)}/overview`),
            events: (organizationId, params = {}) => request(`/organizations/${encodeURIComponent(organizationId)}/events?${new URLSearchParams(params).toString()}`),
            members: organizationId => request(`/organizations/${encodeURIComponent(organizationId)}/members`),
            reports: (organizationId, params = {}) => request(`/organizations/${encodeURIComponent(organizationId)}/reports?${new URLSearchParams(params).toString()}`),
            addMember: (organizationId, body) => jsonRequest(`/organizations/${encodeURIComponent(organizationId)}/members`, "POST", body)
        },
        vouchers: {
            validate: body => jsonRequest("/vouchers/validate", "POST", body)
        },
        refunds: {
            refundableTickets: orderId => request(`/orders/${encodeURIComponent(orderId)}/refundable-tickets`),
            create: body => jsonRequest("/refund-requests", "POST", body)
        },
        checkIns: {
            create: body => jsonRequest("/check-ins", "POST", body)
        },
        admin: {
            overview: () => request("/admin/overview"),
            dashboard: () => request("/admin/dashboard"),
            organizationRequests: params => request(`/admin/organization-requests?${new URLSearchParams(params || {}).toString()}`),
            approveOrganization: requestId => jsonRequest(`/admin/organization-requests/${encodeURIComponent(requestId)}/approve`, "POST", {}),
            rejectOrganization: (requestId, reason) => jsonRequest(`/admin/organization-requests/${encodeURIComponent(requestId)}/reject`, "POST", { reason }),
            publishEvent: (eventId, commissionRuleId) => jsonRequest(`/admin/events/${encodeURIComponent(eventId)}/publish`, "POST", { commissionRuleId }),
            rejectEvent: (eventId, reason) => jsonRequest(`/admin/events/${encodeURIComponent(eventId)}/reject`, "POST", { reason }),
            cancelEvent: eventId => jsonRequest(`/admin/events/${encodeURIComponent(eventId)}/cancel`, "POST", {}),
            cancellationProgress: eventId => request(`/admin/events/${encodeURIComponent(eventId)}/cancellation-progress`),
            reports: params => request(`/admin/reports?${new URLSearchParams(params || {}).toString()}`),
            auditLogs: params => request(`/admin/audit-logs?${new URLSearchParams(params || {}).toString()}`)
        }
    };

    window.TicketsCenter = window.TicketsCenter || {};
    window.TicketsCenter.api = api;
    window.TicketsCenter.formatMoney = value => new Intl.NumberFormat("vi-VN", {
        style: "currency",
        currency: "VND",
        maximumFractionDigits: 0
    }).format(Number(value || 0));
    window.TicketsCenter.formatDate = value => value ? new Intl.DateTimeFormat("vi-VN", {
        dateStyle: "medium",
        timeStyle: "short"
    }).format(new Date(value)) : "—";
    window.TicketsCenter.escapeHtml = value => String(value ?? "")
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll('"', "&quot;")
        .replaceAll("'", "&#039;");
}(window, document));
