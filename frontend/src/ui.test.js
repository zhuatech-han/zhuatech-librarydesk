// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
import test from "node:test";
import assert from "node:assert/strict";
import { reactive } from "vue";
import {
  language,
  t,
  stateName,
  errorMessage,
  cloneRecord,
  ask,
  answerConfirmation,
  confirmation,
} from "./ui.js";
test("language changes visible statuses and errors", () => {
  language.value = "en";
  assert.equal(stateName("WAITING"), "Waiting");
  assert.match(errorMessage(new Error("HOLD_WAITING")), /waiting/);
  language.value = "zh";
  assert.equal(t("馆藏", "Catalog"), "馆藏");
});
test("unknown errors retain diagnostic code", () =>
  assert.match(errorMessage(new Error("UNKNOWN")), /UNKNOWN/));
test("reactive editing cannot mutate source list", () => {
  const row = reactive({ id: 1, permissions: ["catalog"] });
  const copy = cloneRecord(row);
  copy.permissions.push("users");
  assert.deepEqual(row.permissions, ["catalog"]);
});
test("cancel and confirm are distinct promises", async () => {
  const pending = ask("TEST");
  assert.ok(confirmation.value);
  assert.equal(await ask("second"), false);
  answerConfirmation(false);
  assert.equal(await pending, false);
  const next = ask("next");
  answerConfirmation(true);
  assert.equal(await next, true);
});
