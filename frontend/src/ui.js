// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
import { ref } from "vue";
export const language = ref("zh");
/** 双语界面，不改写业务内容。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export function t(zh, en) {
  return language.value === "zh" ? zh : en;
}
const states = {
  AVAILABLE: ["可借", "Available"],
  ON_LOAN: ["借出", "On loan"],
  HELD: ["待领取", "Held"],
  DAMAGED: ["损坏", "Damaged"],
  LOST: ["遗失", "Lost"],
  WITHDRAWN: ["已退出", "Withdrawn"],
  OPEN: ["借阅中", "Open"],
  RETURNED: ["已归还", "Returned"],
  WAITING: ["候补中", "Waiting"],
  READY: ["可领取", "Ready"],
  FULFILLED: ["已领取", "Fulfilled"],
  CANCELLED: ["已取消", "Cancelled"],
  EXPIRED: ["已过期", "Expired"],
};
/** 当前服务端状态名称。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export function stateName(code) {
  return states[code] ? t(...states[code]) : code;
}
const errors = {
  UNAUTHENTICATED: [
    "会话已失效，请重新登录。",
    "Session expired. Sign in again.",
  ],
  LOGIN_FAILED: [
    "账号或密码不正确，或账号已停用。",
    "Invalid credentials or disabled account.",
  ],
  LOGIN_THROTTLED: [
    "尝试过多，请五分钟后重试。",
    "Too many attempts. Retry in five minutes.",
  ],
  FORBIDDEN: ["没有此项操作权限。", "You do not have permission."],
  OUT_OF_SCOPE: [
    "此记录不在你的访问范围。",
    "This record is outside your scope.",
  ],
  VERSION_CONFLICT: [
    "记录已变更，请刷新核对后再操作。",
    "Record changed. Refresh and check before retrying.",
  ],
  RESULT_UNKNOWN: [
    "网络中断，提交结果未知。请刷新核对，勿重复提交。",
    "Connection interrupted; result unknown. Refresh and check before resubmitting.",
  ],
  NETWORK_ERROR: [
    "连接失败，请检查网络后刷新。",
    "Connection failed. Check your network and refresh.",
  ],
  INVALID_INPUT: [
    "字段格式不正确，请检查表单。",
    "Invalid fields. Check the form.",
  ],
  ISBN_INVALID: [
    "ISBN 格式或检查位错误；没有ISBN可留空。",
    "Invalid ISBN format or checksum; leave it empty if unknown.",
  ],
  ISBN_DUPLICATE: [
    "本馆已登记此ISBN，请为现有书目添加单册。",
    "ISBN already exists in this library. Add a copy to the existing title.",
  ],
  CODE_INVALID: [
    "条码/借阅证号需3–40位字母、数字及 _ . -。",
    "Code requires 3–40 letters, digits or _ . -.",
  ],
  BARCODE_DUPLICATE: ["此单册条码已存在。", "Copy barcode already exists."],
  PATRON_DUPLICATE: [
    "账号或借阅证号已登记。",
    "Account or card is already registered.",
  ],
  PATRON_NOT_REGISTERED: [
    "尚未开通借阅证，请联系馆员。",
    "No borrower card. Contact your librarian.",
  ],
  PATRON_DISABLED: [
    "读者借阅资格或账号已停用；仍可由馆员办理归还。",
    "Borrowing privileges or account disabled. Staff can still record returns.",
  ],
  PATRON_OVERDUE: [
    "有逾期未还图书，请先办理归还。",
    "Overdue loans must be resolved first.",
  ],
  BOOK_ARCHIVED: [
    "书目已归档，不能新增借阅或预约。",
    "Title archived; new loans and holds are disabled.",
  ],
  COPY_BUSY: [
    "单册正在借出或待领，不能重复借出或修改状态。",
    "Copy is on loan or held. It cannot be checked out again or rewritten.",
  ],
  COPY_UNAVAILABLE: ["单册不可借出，请检查状态。", "Copy is not available."],
  COPY_STATE_CONFLICT: [
    "单册状态与借阅记录不一致，请联系管理员。",
    "Copy and loan state conflict. Contact an administrator.",
  ],
  HOLD_RESERVED: [
    "此单册已为其他读者保留。",
    "This copy is held for another patron.",
  ],
  HOLD_DUPLICATE: [
    "你已有该书的有效预约。",
    "An active hold already exists for this title.",
  ],
  HOLD_LIMIT: ["已达到有效预约上限。", "Active hold limit reached."],
  ALREADY_BORROWED: [
    "已借有此书，不能再次预约同一书目。",
    "You already have this title on loan.",
  ],
  HOLD_WAITING: [
    "有其他读者预约此书，暂不能续借。",
    "Another patron is waiting. Renewal blocked.",
  ],
  HOLD_CLOSED: ["此预约已结束。", "This hold is already closed."],
  LOAN_CLOSED: ["此借阅已结案。", "This loan is already closed."],
  LOAN_LIMIT: ["已达到当前借阅册数上限。", "Current loan limit reached."],
  RENEWAL_LIMIT: [
    "已达到本次借阅的续借上限。",
    "Renewal limit reached for this loan.",
  ],
  LIBRARY_MISMATCH: [
    "读者与图书不属于同一图书馆。",
    "Patron and copy belong to different libraries.",
  ],
  ACTIVE_HOLDS: [
    "书目仍有有效预约，请先处理。",
    "Resolve active holds before archiving.",
  ],
  RECORD_REFERENCED: [
    "记录已有流通或关联数据，请使用停用/退出保留历史。",
    "Record has history. Disable or withdraw it instead.",
  ],
  IDENTITY_LOCKED: [
    "关联账号、馆或条码身份不能修改。",
    "Account, library or barcode identity cannot be changed.",
  ],
  ACCOUNT_ASSIGNED: [
    "账号已关联借阅证，不能更换馆。",
    "Account has a borrower card; library cannot be changed.",
  ],
  ACCOUNT_INELIGIBLE: [
    "账号未启用或没有读者权限。",
    "Account is disabled or lacks borrowing permission.",
  ],
  CATEGORY_DISABLED: [
    "分类不存在或已停用。",
    "Category is missing or disabled.",
  ],
  DEPARTMENT_DISABLED: ["图书馆已停用。", "Library disabled."],
  LAST_ADMIN: [
    "必须保留一个启用的完整管理员。",
    "Keep at least one enabled full administrator.",
  ],
  PASSWORD_WEAK: [
    "密码需12–72字节，含大写、小写字母和数字。",
    "Password requires 12–72 bytes, uppercase, lowercase and a digit.",
  ],
  OLD_PASSWORD_INVALID: ["原密码不正确。", "Current password is incorrect."],
  LANGUAGE_INVALID: [
    "语言代码格式错误，例如zh-CN或en。",
    "Use a locale code such as zh-CN or en.",
  ],
  NOT_FOUND: ["记录不存在或已移除。", "Record not found."],
  CONFLICT: [
    "重复记录或数据仍被使用，请核对后操作。",
    "Duplicate or referenced data. Check before saving.",
  ],
  RESOURCE_LIMIT: [
    "超出本实例的安全查询上限。",
    "Instance safety query limit exceeded.",
  ],
};
/** 失败影响和可理解操作提示。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export function errorMessage(error) {
  return errors[error.message]
    ? t(...errors[error.message])
    : `${t("操作未完成", "Action failed")} (${error.message})`;
}
export const confirmation = ref(null);
/** 页面内确认，避免原生弹窗阻塞。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export function ask(message) {
  if (confirmation.value) return Promise.resolve(false);
  return new Promise((resolve) => {
    confirmation.value = { message, resolve };
  });
}
/** 明确同意或取消后完成确认。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export function answerConfirmation(ok) {
  const p = confirmation.value;
  confirmation.value = null;
  p?.resolve(ok);
}
/** 拷贝响应式API记录，表单不直接改列表。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export function cloneRecord(row) {
  return JSON.parse(JSON.stringify(row));
}
/** 整理日期仅用于显示，不替代服务端馆时区判定。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export function dateTime(value) {
  if (!value) return "—";
  return new Intl.DateTimeFormat(language.value === "zh" ? "zh-CN" : "en", {
    dateStyle: "medium",
    timeStyle: "short",
  }).format(new Date(value));
}
