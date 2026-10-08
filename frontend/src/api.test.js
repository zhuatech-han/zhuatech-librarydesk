// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
import test from "node:test";
import assert from "node:assert/strict";
import { api, resetApi, download } from "./api.js";
const reply = (body, status = 200) => ({
  ok: status >= 200 && status < 300,
  status,
  json: async () => body,
});
test("writes get actual CSRF header and serialize JSON once", async () => {
  resetApi();
  const calls = [];
  globalThis.fetch = async (path, opt) => {
    calls.push({ path, opt });
    return path.endsWith("csrf")
      ? reply({ header: "X-CSRF-TOKEN", token: "TEST" })
      : reply({ ok: true });
  };
  await api("/books", { method: "POST", body: { name: "TEST" } });
  assert.equal(calls.length, 2);
  assert.equal(calls[1].opt.headers["X-CSRF-TOKEN"], "TEST");
  assert.equal(calls[1].opt.body, '{"name":"TEST"}');
});
test("failed write is never automatically replayed", async () => {
  resetApi();
  let writes = 0;
  globalThis.fetch = async (path) => {
    if (path.endsWith("csrf"))
      return reply({ header: "X-CSRF-TOKEN", token: "TEST" });
    writes++;
    throw new Error("network");
  };
  await assert.rejects(
    api("/books", { method: "POST", body: {} }),
    /RESULT_UNKNOWN/,
  );
  assert.equal(writes, 1);
});
test("server version error preserves status and code", async () => {
  resetApi();
  globalThis.fetch = async () => reply({ code: "VERSION_CONFLICT" }, 409);
  await assert.rejects(
    api("/books/1"),
    (e) => e.status === 409 && e.message === "VERSION_CONFLICT",
  );
});
test("downloads use authenticated HEAD then native anchor", async () => {
  const calls = [];
  let clicked = false,
    removed = false;
  globalThis.fetch = async (path, opt) => {
    calls.push([path, opt]);
    return reply(null);
  };
  globalThis.document = {
    createElement: () => ({
      click() {
        clicked = true;
      },
      remove() {
        removed = true;
      },
    }),
    body: { appendChild() {} },
  };
  await download("/reports/export", "TEST.json");
  assert.equal(calls[0][1].method, "HEAD");
  assert.equal(calls[0][1].credentials, "same-origin");
  assert.equal(clicked, true);
  assert.equal(removed, true);
  delete globalThis.document;
});
test("denied HEAD prevents any download action", async () => {
  globalThis.fetch = async () => reply(null, 403);
  await assert.rejects(download("/protected", "TEST"), /FORBIDDEN/);
});

test("expired session after write denial asks for sign-in without replay", async () => {
  resetApi();
  let writes = 0;
  globalThis.fetch = async (path) => {
    if (path.endsWith("csrf"))
      return reply({ header: "X-CSRF-TOKEN", token: "TEST" });
    if (path.endsWith("me")) return reply({ code: "UNAUTHENTICATED" }, 401);
    writes++;
    return reply({ code: "FORBIDDEN" }, 403);
  };
  await assert.rejects(
    api("/books", { method: "POST", body: {} }),
    (e) => e.status === 401 && e.message === "UNAUTHENTICATED",
  );
  assert.equal(writes, 1);
});
test("valid session keeps genuine denied permission without replay", async () => {
  resetApi();
  let writes = 0;
  globalThis.fetch = async (path) => {
    if (path.endsWith("csrf"))
      return reply({ header: "X-CSRF-TOKEN", token: "TEST" });
    if (path.endsWith("me")) return reply({ id: 1 });
    writes++;
    return reply({ code: "FORBIDDEN" }, 403);
  };
  await assert.rejects(
    api("/books", { method: "POST", body: {} }),
    (e) => e.status === 403 && e.message === "FORBIDDEN",
  );
  assert.equal(writes, 1);
});
