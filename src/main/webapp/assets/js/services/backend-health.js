document.addEventListener("DOMContentLoaded", () => {
    const statusBadge = document.getElementById("system-status-badge");
    const statusText = document.getElementById("system-status-text");
    const checkButton = document.getElementById("btn-check-health");

    if (!statusBadge || !statusText || !checkButton) return;

    const setStatus = (state, text) => {
        statusBadge.className = `status-badge d-inline-flex align-items-center gap-2 ${state === "up"
            ? "bg-success-subtle text-success border border-success-subtle"
            : "bg-warning-subtle text-warning border border-warning-subtle"}`;
        statusText.textContent = text;
    };

    const checkHealth = async () => {
        checkButton.disabled = true;
        statusText.textContent = "Đang kiểm tra...";

        try {
            const response = await fetch("../health/live", { headers: { Accept: "application/json" } });
            const payload = await response.json();
            if (!response.ok || payload?.data?.status !== "UP") throw new Error("Health check failed");
            setStatus("up", "Hệ thống sẵn sàng (UP)");
        } catch {
            setStatus("down", "Đang ngoại tuyến hoặc khởi động");
        } finally {
            checkButton.disabled = false;
        }
    };

    checkButton.addEventListener("click", checkHealth);
    checkHealth();
});
