const assert = require("assert/strict");
const fs = require("fs");
const vm = require("vm");

const source = fs.readFileSync(
  "src/main/webapp/assets/js/api/client.js",
  "utf8"
);
const calls = [];
const responses = [];

function response(status, body) {
  return {
    ok: status >= 200 && status < 300,
    status,
    async text() {
      return body === null ? "" : JSON.stringify(body);
    }
  };
}

const sandbox = {
  window: {
    location: { pathname: "/ticketscenter/pages/buyer/checkout.html" },
    crypto: { randomUUID: () => "00000000-0000-4000-8000-000000000001" }
  },
  document: {},
  Headers,
  FormData,
  URLSearchParams,
  fetch: async (url, options) => {
    calls.push({ url, options });
    return responses.shift();
  },
  console
};

vm.runInNewContext(source, sandbox, { filename: "client.js" });
const api = sandbox.window.TicketsCenter.api;

responses.push(
  response(200, { data: { token: "csrf-test-token" } }),
  response(200, { data: { holdId: "hold-1", status: "ACTIVE" } })
);

api.holds.create({
  eventId: "event-1",
  items: [{ zoneId: "zone-1", seatId: null, quantity: 2 }]
}).then(() => {
  assert.equal(calls[0].url, "/ticketscenter/api/auth/csrf");
  assert.equal(calls[1].url, "/ticketscenter/api/holds");
  assert.equal(calls[1].options.headers.get("X-CSRF-Token"), "csrf-test-token");
  assert.equal(calls[1].options.headers.get("Idempotency-Key"), "00000000-0000-4000-8000-000000000001");
  assert.deepEqual(JSON.parse(calls[1].options.body), {
    eventId: "event-1",
    items: [{ zoneId: "zone-1", seatId: null, quantity: 2 }]
  });

  responses.push(response(400, {
    error: { code: "VALIDATION_FAILED", message: "Request is invalid", correlationId: "corr-1" }
  }));
  return api.request("/events").then(
    () => { throw new Error("Expected API request to fail"); },
    error => {
      assert.equal(error.name, "ApiError");
      assert.equal(error.code, "VALIDATION_FAILED");
      assert.equal(error.correlationId, "corr-1");
    }
  );
}).then(() => {
  console.log("[SUCCESS] API client contract checks passed.");
}).catch(error => {
  console.error(error);
  process.exitCode = 1;
});
