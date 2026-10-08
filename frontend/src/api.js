// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2

let csrf = null;
/** 身份改变清空CSRF；不保存密码或分享凭据。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export function resetApi() {
  csrf = null;
}
/** 同源写入带CSRF，不自动重试写操作，网络中断明示结果未知。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export async function api(path, options = {}) {
  const method = options.method || "GET";
  const write = !["GET", "HEAD"].includes(method);
  if (write && !csrf) {
    const r = await fetch("/api/auth/csrf", {
      credentials: "same-origin",
      cache: "no-store",
    });
    if (!r.ok)
      throw Object.assign(new Error("UNAUTHENTICATED"), { status: r.status });
    csrf = await r.json();
  }
  const headers = { ...options.headers };
  if (write) headers[csrf.header] = csrf.token;
  let body = options.body;
  if (body != null && !(body instanceof FormData)) {
    headers["Content-Type"] = "application/json";
    body = JSON.stringify(body);
  }
  let r;
  try {
    r = await fetch("/api" + path, {
      ...options,
      method,
      headers,
      body,
      credentials: "same-origin",
      cache: "no-store",
    });
  } catch {
    throw Object.assign(new Error(write ? "RESULT_UNKNOWN" : "NETWORK_ERROR"), {
      status: 0,
    });
  }
  if (!r.ok) {
    // A stale CSRF token after server restart can return 403 before authentication. Probe only identity, never replay the write.
    if (write && r.status === 403) {
      try {
        const identity = await fetch("/api/auth/me", {
          credentials: "same-origin",
          cache: "no-store",
        });
        if (identity.status === 401) {
          csrf = null;
          throw Object.assign(new Error("UNAUTHENTICATED"), { status: 401 });
        }
      } catch (e) {
        if (e.status === 401) throw e;
      }
    }
    let b;
    try {
      b = await r.json();
    } catch {
      b = { code: "HTTP_ERROR" };
    }
    throw Object.assign(new Error(b.code || "HTTP_ERROR"), {
      status: r.status,
      row: b.row,
    });
  }
  if (r.status === 204) return null;
  return r.json();
}
/** 下载先HEAD鉴权再交给浏览器流式保存，不把大型ZIP全读进内存。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export async function download(path, name) {
  const r = await fetch("/api" + path, {
    method: "HEAD",
    credentials: "same-origin",
    cache: "no-store",
  });
  if (!r.ok) {
    throw Object.assign(
      new Error(
        r.status === 401
          ? "UNAUTHENTICATED"
          : r.status === 403
            ? "FORBIDDEN"
            : r.status === 409
              ? "DOWNLOAD_LOCKED"
              : "DOWNLOAD_FAILED",
      ),
      { status: r.status },
    );
  }
  const a = document.createElement("a");
  a.href = "/api" + path;
  a.download = name;
  a.rel = "noopener";
  document.body.appendChild(a);
  a.click();
  a.remove();
}
