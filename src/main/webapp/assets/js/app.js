(function (window, document) {
    "use strict";

    const { api, formatMoney, formatDate, escapeHtml } = window.TicketsCenter;
    const stateKey = "ticketscenter.checkout";

    function pageName() {
        const file = window.location.pathname.split("/").pop() || "index.html";
        const match = file.match(/^UI-(\d+)-/i);
        if (match) return `ui-${match[1]}`;
        return file.replace(/\.html$/i, "").toLowerCase();
    }

    function query(name) {
        return new URLSearchParams(window.location.search).get(name);
    }

    function showMessage(message, type = "danger") {
        let element = document.getElementById("api-message");
        if (!element) {
            element = document.createElement("div");
            element.id = "api-message";
            element.className = "container mt-3";
            document.body.prepend(element);
        }
        element.innerHTML = `<div class="alert alert-${type} mb-0" role="alert">${escapeHtml(message)}</div>`;
    }

    function clearMessage() {
        document.getElementById("api-message")?.remove();
    }

    function setBusy(button, busy, text = "Đang xử lý...") {
        if (!button) return;
        if (busy) {
            button.dataset.originalText = button.innerHTML;
            button.disabled = true;
            button.innerHTML = text;
        } else {
            button.disabled = false;
            button.innerHTML = button.dataset.originalText || button.innerHTML;
        }
    }

    function handleError(error) {
        console.error("TicketsCenter API error", error);
        showMessage(error?.message || "Không thể kết nối tới backend.");
    }

    function readCheckoutState() {
        try {
            return JSON.parse(sessionStorage.getItem(stateKey) || "null");
        } catch {
            return null;
        }
    }

    function saveCheckoutState(value) {
        sessionStorage.setItem(stateKey, JSON.stringify(value));
    }

    function relativeEventDetailLink() {
        return window.location.pathname.includes("/pages/")
            ? "event-detail.html"
            : "pages/buyer/event-detail.html";
    }

    async function initHome() {
        const grid = [...document.querySelectorAll("main .row.g-4")]
            .find(element => element.querySelector(".event-card"));
        if (!grid) return;

        const search = document.querySelector(".search-input");
        const searchButton = document.querySelector(".search-btn");
        const categories = document.querySelector(".category-nav");
        let categoryId = "";

        const renderCategories = payload => {
            if (!categories) return;
            const items = payload.items || [];
            categories.innerHTML = [
                `<a href="#" data-category-id="" class="active">Tất cả</a>`,
                ...items.map(item => `<a href="#" data-category-id="${escapeHtml(item.id)}">${escapeHtml(item.name)}</a>`)
            ].join("");
            categories.addEventListener("click", event => {
                const link = event.target.closest("[data-category-id]");
                if (!link) return;
                event.preventDefault();
                categoryId = link.dataset.categoryId;
                categories.querySelectorAll("a").forEach(item => item.classList.toggle("active", item === link));
                loadEvents();
            });
        };

        const renderEvents = payload => {
            const events = payload.items || [];
            grid.innerHTML = events.length ? events.map(event => `
                <div class="col-12 col-sm-6 col-lg-3">
                    <a href="${relativeEventDetailLink()}?id=${encodeURIComponent(event.id)}" class="event-card">
                        <div class="event-image">
                            <img src="${escapeHtml(event.coverImageUrl || "assets/images/event-placeholder.jpg")}" alt="${escapeHtml(event.title)}">
                            <span class="event-category-badge">${escapeHtml(event.categoryName || "Sự kiện")}</span>
                        </div>
                        <div class="event-content">
                            <h3 class="event-title">${escapeHtml(event.title)}</h3>
                            <div class="event-meta">${escapeHtml(formatDate(event.startTime))} • ${escapeHtml(event.venueName || event.venueAddress || "")}</div>
                            <div class="event-price">Từ ${formatMoney(event.minimumPrice)}</div>
                        </div>
                    </a>
                </div>`).join("") : `<div class="col-12"><div class="alert alert-light border">Không tìm thấy sự kiện phù hợp.</div></div>`;
        };

        const loadEvents = async () => {
            try {
                clearMessage();
                renderEvents(await api.events.list({ q: search?.value.trim() || "", categoryId, page: "1", pageSize: "100", sort: "start" }));
            } catch (error) {
                handleError(error);
            }
        };

        searchButton?.addEventListener("click", event => {
            event.preventDefault();
            loadEvents();
        });
        search?.addEventListener("keydown", event => {
            if (event.key === "Enter") {
                event.preventDefault();
                loadEvents();
            }
        });
        try {
            await renderCategories(await api.events.categories());
        } catch (error) {
            handleError(error);
        }
        await loadEvents();
    }

    async function initEventDetail() {
        const eventId = query("id") || query("eventId");
        if (!eventId) return;
        const main = document.querySelector("main");
        if (!main) return;

        try {
            const [event, zonesPayload] = await Promise.all([api.events.get(eventId), api.events.zones(eventId)]);
            const zones = zonesPayload.items || [];
            const heading = main.querySelector("h1");
            if (heading) heading.textContent = event.title;
            const textNodes = [...main.querySelectorAll(".small, p")];
            const venue = textNodes.find(node => node.textContent.includes("Địa điểm tổ chức"));
            if (venue) venue.querySelector("span:last-child")?.replaceChildren(document.createTextNode(`${event.venueName} — ${event.venueAddress}`));
            const price = [...main.querySelectorAll(".text-danger")].find(node => node.textContent.includes("Từ"));
            if (price) price.textContent = `Từ ${formatMoney(event.minimumPrice)}`;

            const seatGrid = main.querySelector(".seat-grid");
            if (!seatGrid) return;
            const selected = new Map();
            const quantities = new Map();
            const render = () => {
                const lines = [];
                let total = 0;
                let count = 0;
                selected.forEach((selection, key) => {
                    lines.push(`<div class="d-flex justify-content-between py-2 border-bottom"><span>${escapeHtml(selection.label)}</span><strong>${formatMoney(selection.price)}</strong></div>`);
                    total += selection.price;
                    count++;
                });
                quantities.forEach((quantity, zoneId) => {
                    const zone = zones.find(item => item.id === zoneId);
                    if (!zone || quantity <= 0) return;
                    lines.push(`<div class="d-flex justify-content-between py-2 border-bottom"><span>${quantity}x ${escapeHtml(zone.name)}</span><strong>${formatMoney(quantity * Number(zone.price))}</strong></div>`);
                    total += quantity * Number(zone.price);
                    count += quantity;
                });
                const list = document.getElementById("selectionList");
                if (list) list.innerHTML = lines.join("") || `<span class="text-muted">Chưa chọn vé</span>`;
                document.getElementById("ticketCountBadge")?.replaceChildren(document.createTextNode(`${count} / 8 vé`));
                document.getElementById("totalPriceText")?.replaceChildren(document.createTextNode(formatMoney(total)));
            };
            const toggleSeat = (button, seat, zone) => {
                const key = `${zone.id}:${seat.id}`;
                if (seat.status !== "AVAILABLE") return;
                if (selected.has(key)) {
                    selected.delete(key);
                    button.classList.remove("selected");
                } else if (selected.size + [...quantities.values()].reduce((sum, value) => sum + value, 0) < 8) {
                    selected.set(key, { zoneId: zone.id, seatId: seat.id, label: `${zone.name} — ${seat.row}-${seat.number}`, price: Number(zone.price) });
                    button.classList.add("selected");
                } else {
                    showMessage("Mỗi lần giữ vé tối đa 8 vé.", "warning");
                }
                render();
            };
            seatGrid.innerHTML = zones.filter(zone => zone.type === "SEATED").flatMap(zone => zone.seats.map(seat => `
                <button type="button" class="seat-btn ${seat.status !== "AVAILABLE" ? "occupied" : ""}" data-zone-id="${zone.id}" data-seat-id="${seat.id}">${escapeHtml(`${seat.row}-${seat.number}`)}</button>`)).join("") || `<div class="alert alert-light">Sự kiện này dùng vé khu đứng.</div>`;
            seatGrid.querySelectorAll("[data-seat-id]").forEach(button => {
                const zone = zones.find(item => item.id === button.dataset.zoneId);
                const seat = zone?.seats.find(item => item.id === button.dataset.seatId);
                button.addEventListener("click", () => toggleSeat(button, seat, zone));
            });
            const standingZones = zones.filter(zone => zone.type === "STANDING");
            standingZones.forEach(zone => quantities.set(zone.id, 0));
            if (standingZones.length) {
                seatGrid.insertAdjacentHTML("beforeend", standingZones.map(zone => `
                    <div class="d-flex align-items-center justify-content-between border-top mt-3 pt-3 w-100" data-standing-zone="${zone.id}">
                        <span><strong>${escapeHtml(zone.name)}</strong><small class="d-block text-muted">${formatMoney(zone.price)} / vé • Còn ${zone.availableQuantity}</small></span>
                        <span class="d-flex align-items-center gap-2"><button type="button" class="btn btn-sm btn-outline-secondary" data-standing-action="decrease" data-zone-id="${zone.id}">−</button><strong data-standing-quantity="${zone.id}">0</strong><button type="button" class="btn btn-sm btn-outline-secondary" data-standing-action="increase" data-zone-id="${zone.id}">+</button></span>
                    </div>`).join(""));
                seatGrid.querySelectorAll("[data-standing-action]").forEach(button => button.addEventListener("click", () => {
                    const zone = standingZones.find(item => item.id === button.dataset.zoneId);
                    const current = quantities.get(zone.id) || 0;
                    const selectedCount = selected.size + [...quantities.entries()]
                        .filter(([zoneId]) => zoneId !== zone.id)
                        .reduce((sum, [, value]) => sum + value, 0);
                    const next = button.dataset.standingAction === "increase"
                        ? Math.min(current + 1, Math.min(zone.availableQuantity, 8 - selectedCount))
                        : Math.max(current - 1, 0);
                    quantities.set(zone.id, next);
                    seatGrid.querySelector(`[data-standing-quantity="${zone.id}"]`).textContent = next;
                    render();
                }));
            }
            const continueButton = [...main.querySelectorAll("a,button")].find(element => element.textContent.includes("Tiếp tục thanh toán"));
            continueButton?.addEventListener("click", async event => {
                event.preventDefault();
                const items = [...selected.values()].map(item => ({ zoneId: item.zoneId, seatId: item.seatId, quantity: 1 }));
                quantities.forEach((quantity, zoneId) => {
                    if (quantity > 0) items.push({ zoneId, seatId: null, quantity });
                });
                if (!items.length) {
                    showMessage("Vui lòng chọn ít nhất một vé.", "warning");
                    return;
                }
                try {
                    setBusy(continueButton, true, "Đang giữ vé...");
                    const hold = await api.holds.create({ eventId, items });
                    saveCheckoutState({ eventId, hold, items });
                    window.location.href = `checkout.html?holdId=${encodeURIComponent(hold.holdId)}`;
                } catch (error) {
                    handleError(error);
                    setBusy(continueButton, false);
                }
            });
            render();
        } catch (error) {
            handleError(error);
        }
    }

    async function initAuth() {
        const loginForm = document.getElementById("formLogin");
        const registerForm = document.getElementById("formRegister");
        const otpForm = document.getElementById("formOtp");
        if (!loginForm || !registerForm || !otpForm) return;
        const loginButton = [...loginForm.querySelectorAll("button")].find(button => button.textContent.includes("Đăng nhập"));
        const registerButton = [...registerForm.querySelectorAll("button")].find(button => button.textContent.includes("Đăng ký"));
        const otpButton = [...otpForm.querySelectorAll("button")].find(button => button.textContent.includes("Xác nhận"));
        let registeredEmail = sessionStorage.getItem("ticketscenter.verificationEmail") || "";
        const login = async event => {
            event.preventDefault();
            const inputs = loginForm.querySelectorAll("input");
            try {
                setBusy(loginButton, true);
                await api.auth.login({ identifier: inputs[0].value.trim(), password: inputs[1].value });
                window.location.href = query("redirect") || "../buyer/my-orders.html";
            } catch (error) {
                handleError(error);
                setBusy(loginButton, false);
            }
        };
        const register = async event => {
            event.preventDefault();
            const email = [...registerForm.querySelectorAll('input[type="email"]')][0]?.value.trim();
            const password = [...registerForm.querySelectorAll('input[type="password"]')][0]?.value;
            try {
                setBusy(registerButton, true);
                await api.auth.register({ email, password });
                registeredEmail = email;
                sessionStorage.setItem("ticketscenter.verificationEmail", email);
                await api.auth.sendOtp({ purpose: "VERIFY_EMAIL", email });
                const recipient = otpForm.querySelector("strong");
                if (recipient) recipient.textContent = email;
                showMessage("Mã OTP đã được gửi tới email đăng ký.", "success");
                window.switchTab?.("otp");
            } catch (error) {
                handleError(error);
            } finally {
                setBusy(registerButton, false);
            }
        };
        const verifyOtp = async event => {
            event.preventDefault();
            const code = [...otpForm.querySelectorAll(".otp-input")].map(input => input.value.trim()).join("");
            if (!registeredEmail || code.length !== 6) {
                showMessage("Vui lòng nhập đủ 6 chữ số OTP.", "warning");
                return;
            }
            try {
                setBusy(otpButton, true);
                await api.auth.verifyOtp({ purpose: "VERIFY_EMAIL", email: registeredEmail, code });
                sessionStorage.removeItem("ticketscenter.verificationEmail");
                showMessage("Xác thực email thành công. Bạn có thể đăng nhập.", "success");
                window.switchTab?.("login");
            } catch (error) {
                handleError(error);
            } finally {
                setBusy(otpButton, false);
            }
        };
        loginButton?.addEventListener("click", login);
        registerButton?.addEventListener("click", register);
        otpButton?.addEventListener("click", verifyOtp);
    }

    async function initCheckout() {
        const state = readCheckoutState();
        const holdId = query("holdId") || state?.hold?.holdId;
        if (!holdId) return;
        const main = document.querySelector("main");
        const couponInput = main?.querySelector("input[placeholder*='khuyến']");
        const couponButton = [...(main?.querySelectorAll("button") || [])].find(button => button.textContent.includes("Áp dụng"));
        const payButton = [...(main?.querySelectorAll("a,button") || [])].find(element => element.textContent.includes("Thanh toán an toàn"));
        let order = null;
        try {
            order = state?.order || await api.orders.create(holdId);
            saveCheckoutState({ ...state, holdId, order });
            const code = document.querySelector(".font-monospace");
            if (code) code.textContent = `Mã đơn: ${order.orderCode || order.orderId}`;
            const total = [...main.querySelectorAll("span")].find(element => element.id === "finalTotal");
            if (total) total.textContent = formatMoney(order.total);
            const subtotal = [...main.querySelectorAll("span")].find(element => element.textContent.includes("280.000"));
            if (subtotal) subtotal.textContent = formatMoney(order.subtotal);
            if (couponButton) couponButton.addEventListener("click", async event => {
                event.preventDefault();
                try {
                    setBusy(couponButton, true);
                    order = await api.orders.applyCoupon(order.orderId, couponInput?.value.trim() || null);
                    saveCheckoutState({ ...readCheckoutState(), order });
                    document.getElementById("discountAmount")?.replaceChildren(document.createTextNode(`-${formatMoney(order.discountAmount)}`));
                    document.getElementById("finalTotal")?.replaceChildren(document.createTextNode(formatMoney(order.total)));
                    showMessage("Đã cập nhật mã giảm giá từ backend.", "success");
                } catch (error) {
                    handleError(error);
                } finally {
                    setBusy(couponButton, false);
                }
            });
            payButton?.addEventListener("click", async event => {
                event.preventDefault();
                const providerReference = query("providerReference");
                const paymentId = query("paymentId");
                if (!providerReference) {
                    showMessage("Chưa nhận được xác nhận thanh toán từ cổng thanh toán. Không thể tự đánh dấu đơn là đã trả tiền.", "warning");
                    return;
                }
                try {
                    setBusy(payButton, true, "Đang xác nhận...");
                    const result = await api.payments.confirm(order.orderId, { paymentId, providerReference });
                    saveCheckoutState({ ...readCheckoutState(), order, payment: result });
                    window.location.href = `payment-result.html?orderId=${encodeURIComponent(order.orderId)}`;
                } catch (error) {
                    handleError(error);
                    setBusy(payButton, false);
                }
            });
        } catch (error) {
            handleError(error);
        }
    }

    async function initTickets() {
        const list = document.querySelector("main");
        if (!list || !document.querySelector(".ticket-card")) return;
        try {
            const payload = await api.tickets.mine();
            const tickets = payload.items || payload;
            const cards = tickets.map(ticket => `
                <div class="ticket-card"><div class="row g-0">
                    <div class="col-md-8 ticket-left">
                        <span class="status-pill status-success">${escapeHtml(ticket.status)}</span>
                        <h3 class="h4 fw-bold mb-2 text-dark">${escapeHtml(ticket.eventTitle)}</h3>
                        <p class="small text-muted mb-4">${escapeHtml(ticket.venueName)}<br>${escapeHtml(ticket.venueAddress)}<br>${escapeHtml(formatDate(ticket.issuedAt))}</p>
                        <div class="row g-3 pt-3 border-top small">
                            <div class="col-6 col-sm-4"><span class="text-muted d-block">Vị trí:</span><strong>${escapeHtml(ticket.seatLabel || ticket.zoneName)}</strong></div>
                            <div class="col-6 col-sm-4"><span class="text-muted d-block">Giá vé:</span><strong>${formatMoney(ticket.paidAmount)}</strong></div>
                            <div class="col-6 col-sm-4"><span class="text-muted d-block">Trạng thái:</span><strong>${escapeHtml(ticket.status)}</strong></div>
                        </div>
                    </div>
                    <div class="col-md-4 ticket-right"><img class="qr-placeholder mb-2" src="${api.tickets.qrUrl(ticket.ticketId)}" alt="Mã QR vé"><span class="font-monospace small">${escapeHtml(ticket.ticketId)}</span></div>
                </div></div>`).join("");
            const oldCards = [...list.querySelectorAll(".ticket-card")];
            const insertionPoint = oldCards[0];
            if (insertionPoint) {
                insertionPoint.insertAdjacentHTML("beforebegin", cards || `<div class="alert alert-light">Bạn chưa có vé.</div>`);
                oldCards.forEach(card => card.remove());
            }
        } catch (error) {
            handleError(error);
        }
    }

    async function initMyOrders() {
        const list = [...document.querySelectorAll("main .d-flex.flex-column.gap-4")]
            .find(element => element.querySelector(".panel-box"));
        if (!list) return;
        try {
            const payload = await api.tickets.mine();
            const tickets = payload.items || payload;
            const groups = new Map();
            tickets.forEach(ticket => {
                const group = groups.get(ticket.eventId) || { ...ticket, tickets: [], total: 0 };
                group.tickets.push(ticket);
                group.total += Number(ticket.paidAmount || 0);
                groups.set(ticket.eventId, group);
            });
            list.innerHTML = [...groups.values()].map(order => `
                <div class="panel-box mb-0">
                    <div class="panel-header">
                        <div class="d-flex align-items-center gap-3"><span class="panel-title">${escapeHtml(order.eventTitle)}</span><span class="status-pill status-success">${escapeHtml(order.status)}</span></div>
                        <span class="small text-muted">${escapeHtml(formatDate(order.issuedAt))}</span>
                    </div>
                    <div class="row g-4 align-items-center">
                        <div class="col-md-7"><p class="small text-muted mb-2">${escapeHtml(order.venueName)}<br>${escapeHtml(order.venueAddress)}</p><div class="small text-dark"><strong>Chi tiết vé:</strong> ${order.tickets.length} vé (${order.tickets.map(ticket => escapeHtml(ticket.seatLabel || ticket.zoneName)).join(", ")})</div></div>
                        <div class="col-md-5 d-flex flex-column align-items-md-end justify-content-between"><div class="text-md-end mb-3"><span class="small text-muted d-block">Tổng tiền:</span><span class="h4 fw-bold text-danger mb-0">${formatMoney(order.total)}</span></div><a href="tickets.html" class="btn-primary-custom">Xem vé vào cửa</a></div>
                    </div>
                </div>`).join("") || `<div class="alert alert-light">Bạn chưa có vé đã phát hành.</div>`;
        } catch (error) {
            handleError(error);
        }
    }

    async function initProfile() {
        if (!document.getElementById("inp-name")) return;
        try {
            const profile = await api.profile();
            const value = profile;
            const mappings = { "inp-name": value.fullName, "inp-email": value.email, "inp-phone": value.phone };
            Object.entries(mappings).forEach(([id, content]) => {
                const input = document.getElementById(id);
                if (input && content != null) input.value = content;
            });
        } catch (error) {
            handleError(error);
        }
    }

    async function initOrganizerDashboard() {
        if (!window.location.pathname.includes("organizer-dashboard") && !pageName().includes("ui-10")) return;
        try {
            const memberships = await api.memberships();
            const organization = (memberships.items || []).find(item => item.active && item.role === "MANAGER");
            if (!organization) return;
            const [overview, events] = await Promise.all([
                api.organizations.overview(organization.organizationId),
                api.organizations.events(organization.organizationId, { page: "1", pageSize: "100" })
            ]);
            const metrics = [...document.querySelectorAll("main .panel-box .h3")].slice(0, 4);
            [overview.grossRevenue, overview.activeTickets, overview.usedTickets, overview.paidOrderCount].forEach((value, index) => {
                if (metrics[index]) metrics[index].textContent = index === 0 ? formatMoney(value) : Number(value || 0).toLocaleString("vi-VN");
            });
            const rows = document.querySelectorAll("main table tbody tr");
            (events.items || []).forEach((event, index) => {
                const row = rows[index];
                if (!row) return;
                const cells = row.querySelectorAll("td");
                if (cells[0]) cells[0].querySelector("strong")?.replaceChildren(document.createTextNode(event.title));
                if (cells[1]) cells[1].textContent = formatDate(event.startTime);
                if (cells[2]) cells[2].textContent = event.venueName || event.venueAddress || "—";
                if (cells[4]) cells[4].textContent = event.status;
            });
        } catch (error) {
            handleError(error);
        }
    }

    async function activeOrganization() {
        const memberships = await api.memberships();
        return (memberships.items || []).find(item => item.active && ["MANAGER", "CHECK_IN_STAFF"].includes(item.role));
    }

    async function initOrganizerEvents() {
        if (!window.location.pathname.includes("organizer-events") && !pageName().includes("ui-12")) return;
        try {
            const organization = await activeOrganization();
            if (!organization) return;
            const payload = await api.organizations.events(organization.organizationId, { page: "1", pageSize: "100" });
            const rows = document.querySelectorAll("main table tbody tr");
            (payload.items || []).forEach((event, index) => {
                const row = rows[index];
                if (!row) return;
                const cells = row.querySelectorAll("td");
                if (cells[0]) cells[0].querySelector("strong")?.replaceChildren(document.createTextNode(event.title));
                if (cells[1]) cells[1].textContent = formatDate(event.startTime);
                if (cells[2]) cells[2].textContent = event.venueName || event.venueAddress || "—";
                if (cells[4]) cells[4].textContent = event.status;
            });
        } catch (error) {
            handleError(error);
        }
    }

    async function initOrganizerMembers() {
        if (!window.location.pathname.includes("organizer-members") && !pageName().includes("ui-11")) return;
        try {
            const organization = await activeOrganization();
            if (!organization) return;
            const payload = await api.organizations.members(organization.organizationId);
            const rows = document.querySelectorAll("main table tbody tr");
            (payload.items || []).forEach((member, index) => {
                const row = rows[index];
                if (!row) return;
                const cells = row.querySelectorAll("td");
                if (cells[0]) cells[0].querySelector("strong")?.replaceChildren(document.createTextNode(member.fullName || member.email));
                if (cells[1]) cells[1].textContent = member.email;
                if (cells[2]) cells[2].textContent = member.role;
                if (cells[3]) cells[3].textContent = member.active ? "Đang hoạt động" : "Đã khóa";
            });
        } catch (error) {
            handleError(error);
        }
    }

    async function initCheckinSelect() {
        if (!window.location.pathname.includes("checkin-select") && !pageName().includes("ui-15")) return;
        try {
            const organization = await activeOrganization();
            if (!organization) return;
            const payload = await api.organizations.events(organization.organizationId, { page: "1", pageSize: "100" });
            const cards = [...document.querySelectorAll("main .panel-box")];
            (payload.items || []).forEach((event, index) => {
                const card = cards[index];
                if (!card) return;
                card.querySelector("h3, h4, h5")?.replaceChildren(document.createTextNode(event.title));
                const link = card.querySelector("a[href*='scanner']") || card.querySelector("a");
                if (link) link.href = `checkin-scanner.html?eventId=${encodeURIComponent(event.id)}`;
            });
        } catch (error) {
            handleError(error);
        }
    }

    async function initReports() {
        if (!window.location.pathname.includes("organizer-reports") && !pageName().includes("ui-17")) return;
        try {
            const organization = await activeOrganization();
            if (!organization) return;
            const payload = await api.organizations.reports(organization.organizationId, { page: "1", pageSize: "100" });
            const metrics = payload.metrics || {};
            const values = [metrics.netCashFlow, metrics.ticketCapturedAmount, metrics.paidOrderCount, metrics.commissionAmount];
            [...document.querySelectorAll("main .h3, main .display-6")].slice(0, values.length).forEach((node, index) => {
                node.textContent = index === 2 ? Number(values[index] || 0).toLocaleString("vi-VN") : formatMoney(values[index]);
            });
        } catch (error) {
            handleError(error);
        }
    }

    async function initAdminAudit() {
        if (!window.location.pathname.includes("admin-audit") && !pageName().includes("ui-23")) return;
        try {
            const payload = await api.admin.auditLogs({ page: "1", pageSize: "100" });
            const rows = document.querySelectorAll("main table tbody tr");
            (payload.items || []).forEach((entry, index) => {
                const row = rows[index];
                if (!row) return;
                const cells = row.querySelectorAll("td");
                if (cells[0]) cells[0].textContent = formatDate(entry.createdAt);
                if (cells[1]) cells[1].querySelector("strong")?.replaceChildren(document.createTextNode(entry.actorEmail || entry.actorId));
                if (cells[2]) cells[2].textContent = entry.action;
                if (cells[3]) cells[3].textContent = `${entry.aggregateType} ${entry.aggregateId}`;
                if (cells[4]) cells[4].textContent = "Đã ghi nhận";
            });
        } catch (error) {
            handleError(error);
        }
    }

    async function initOrganizationRequest() {
        const form = document.querySelector("form");
        if (!form || !document.querySelector("input[placeholder*='Công ty']")) return;
        const inputs = [...form.querySelectorAll("input")];
        form.addEventListener("submit", async event => {
            event.preventDefault();
            try {
                await api.organizationRequests.create({
                    name: inputs[0]?.value.trim(), contactEmail: inputs[5]?.value.trim(),
                    contactPhone: inputs[6]?.value.trim(), description: document.querySelector("textarea")?.value.trim()
                });
                showMessage("Đã gửi yêu cầu tạo tổ chức.", "success");
                form.reset();
            } catch (error) {
                handleError(error);
            }
        });
    }

    async function initCheckin() {
        const codeInput = document.getElementById("manualCode");
        if (!codeInput) return;
        const button = [...document.querySelectorAll("button")].find(element => element.textContent.includes("Kiểm tra"));
        const eventId = query("eventId") || sessionStorage.getItem("ticketscenter.checkinEventId");
        const submit = async event => {
            event.preventDefault();
            if (!eventId) {
                showMessage("Thiếu eventId cho ca check-in.", "warning");
                return;
            }
            try {
                setBusy(button, true);
                const result = await api.checkIns.create({ eventId, ticketCode: codeInput.value.trim() });
                window.showResult?.(result.result);
                showMessage(`Kết quả check-in: ${result.result}`, result.result === "VALID" ? "success" : "warning");
            } catch (error) {
                handleError(error);
            } finally {
                setBusy(button, false);
            }
        };
        button?.addEventListener("click", submit);
    }

    async function initAdminDashboard() {
        if (!document.querySelector(".dashboard-content") || !window.location.pathname.includes("admin")) return;
        try {
            const overview = await api.admin.overview();
            const numbers = [...document.querySelectorAll(".admin-queue-val")];
            [overview.pendingOrganizationRequests, overview.pendingEvents, overview.pendingRefundRequests, overview.pendingSettlements].forEach((value, index) => {
                if (numbers[index]) numbers[index].textContent = value;
            });
        } catch (error) {
            handleError(error);
        }
    }

    async function initAdminOrganizations() {
        if (!window.location.pathname.includes("admin-org") && !pageName().includes("ui-19")) return;
        try {
            const payload = await api.admin.organizationRequests({ status: "PENDING", page: "1", pageSize: "100" });
            const rows = [...document.querySelectorAll("table tbody tr")];
            (payload.items || []).forEach((request, index) => {
                const row = rows[index];
                if (!row) return;
                row.dataset.requestId = request.id;
                const cells = row.querySelectorAll("td");
                if (cells[0]) cells[0].textContent = request.name;
                if (cells[1]) cells[1].textContent = request.contactEmail;
                const approve = [...row.querySelectorAll("button")].find(button => button.textContent.includes("phê duyệt"));
                approve?.addEventListener("click", async event => {
                    event.preventDefault();
                    try { await api.admin.approveOrganization(request.id); row.remove(); } catch (error) { handleError(error); }
                });
            });
        } catch (error) {
            handleError(error);
        }
    }

    async function initRefundRequest() {
        const submit = [...document.querySelectorAll("button")].find(button => button.textContent.includes("Gửi yêu cầu hoàn"));
        const orderId = query("orderId");
        if (!submit || !orderId) return;
        try {
            const payload = await api.refunds.refundableTickets(orderId);
            const checks = [...document.querySelectorAll(".ticket-check")];
            (payload.items || []).forEach((ticket, index) => {
                if (!checks[index]) return;
                checks[index].dataset.ticketId = ticket.ticketId;
                checks[index].dataset.amount = ticket.paidAmount;
                const label = checks[index].closest("label") || checks[index].parentElement;
                if (label) label.querySelector(".ticket-label")?.replaceChildren(document.createTextNode(`${ticket.zoneName} ${ticket.seatLabel || ""}`));
            });
            submit.addEventListener("click", async event => {
                event.preventDefault();
                const ticketIds = checks.filter(input => input.checked && input.dataset.ticketId).map(input => input.dataset.ticketId);
                try { await api.refunds.create({ orderId, ticketIds, reason: document.querySelector("textarea")?.value.trim() }); showMessage("Đã gửi yêu cầu hoàn vé.", "success"); } catch (error) { handleError(error); }
            });
        } catch (error) {
            handleError(error);
        }
    }

    async function init() {
        const current = pageName();
        if (["index", "ui-01"].includes(current)) await initHome();
        if (["event-detail", "ui-02"].includes(current)) await initEventDetail();
        if (["auth", "ui-03"].includes(current)) await initAuth();
        if (["checkout", "ui-04"].includes(current)) await initCheckout();
        if (["tickets", "ui-07"].includes(current)) await initTickets();
        if (["my-orders", "ui-06"].includes(current)) await initMyOrders();
        if (["profile", "ui-24"].includes(current)) await initProfile();
        if (["organizer-request", "ui-09"].includes(current)) await initOrganizationRequest();
        if (["checkin-scanner", "ui-16"].includes(current)) await initCheckin();
        if (["refund-request", "ui-08"].includes(current)) await initRefundRequest();
        if (current === "admin-dashboard" || current === "ui-18") await initAdminDashboard();
        if (current === "admin-org-approval" || current === "ui-19") await initAdminOrganizations();
        await initOrganizerEvents();
        await initOrganizerMembers();
        await initCheckinSelect();
        await initReports();
        await initAdminAudit();
        await initOrganizerDashboard();
    }

    document.addEventListener("DOMContentLoaded", () => init().catch(handleError));
}(window, document));
