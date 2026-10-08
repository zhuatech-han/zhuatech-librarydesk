<!-- Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2 -->
<script setup>
import { ref, computed, onMounted } from "vue";
import {
  Library,
  BookOpen,
  ScanLine,
  Users,
  ClipboardList,
  ListOrdered,
  ChartColumn,
  Settings,
  Shield,
  History,
  LogOut,
  Plus,
  Search,
  ArrowLeft,
  Pencil,
  RefreshCw,
  X,
  Menu,
  KeyRound,
  ExternalLink,
} from "@lucide/vue";
import { api, resetApi, download } from "./api.js";
import {
  language,
  t,
  stateName,
  errorMessage,
  ask,
  confirmation,
  answerConfirmation,
  cloneRecord,
  dateTime,
} from "./ui.js";
import AdminPanel from "./components/AdminPanel.vue";
import RecordsPanel from "./components/RecordsPanel.vue";
const profile = ref(null),
  ready = ref(false),
  pending = ref(false),
  notice = ref(null),
  view = ref("catalog"),
  mobileNav = ref(false),
  about = ref(false),
  passwordForm = ref(null),
  loginForm = ref({ username: "", password: "" });
const options = ref({ departments: [], categories: [], accounts: [] }),
  catalog = ref({ items: [], total: 0 }),
  query = ref(""),
  category = ref(""),
  bookStatus = ref(""),
  sort = ref("title"),
  page = ref(1),
  detail = ref(null),
  patrons = ref([]),
  patronQuery = ref(""),
  patronPage = ref(1),
  desk = ref(null),
  barcode = ref(""),
  selectedPatron = ref(""),
  returnCondition = ref("AVAILABLE"),
  returnNote = ref(""),
  my = ref(null),
  myKind = ref("loans"),
  report = ref(null),
  audit = ref([]),
  auditQuery = ref(""),
  auditPage = ref(1),
  editor = ref(null),
  editorInitial = ref(""),
  adminRef = ref(null),
  recordsRef = ref(null);
const has = (code) => profile.value?.permissions.includes(code);
const menuNames = {
  catalog: () => t("馆藏目录", "Catalog"),
  desk: () => t("借还柜台", "Circulation desk"),
  loans: () => t("借阅记录", "Loans"),
  holds: () => t("预约队列", "Holds"),
  my: () => t("我的借阅", "My borrowing"),
  patrons: () => t("读者管理", "Patrons"),
  reports: () => t("流通报表", "Reports"),
  users: () => t("登录账号", "Accounts"),
  roles: () => t("角色与权限", "Roles & permissions"),
  settings: () => t("图书馆与设置", "Libraries & settings"),
  audit: () => t("操作记录", "Audit trail"),
};
const icons = {
  catalog: BookOpen,
  desk: ScanLine,
  loans: ClipboardList,
  holds: ListOrdered,
  my: Library,
  patrons: Users,
  reports: ChartColumn,
  users: Users,
  roles: Shield,
  settings: Settings,
  audit: History,
};
const menus = computed(
  () => profile.value?.menus.filter((m) => menuNames[m.code]) || [],
);
const patronRows = computed(() =>
  patrons.value.filter((p) =>
    JSON.stringify(p).toLowerCase().includes(patronQuery.value.toLowerCase()),
  ),
);
const auditRows = computed(() =>
  audit.value.filter((a) =>
    JSON.stringify(a).toLowerCase().includes(auditQuery.value.toLowerCase()),
  ),
);
const eligiblePatrons = computed(() =>
  patrons.value.filter(
    (p) =>
      p.patron.active &&
      p.accountEnabled &&
      (!desk.value || p.patron.departmentId === desk.value.book.departmentId),
  ),
);
const selectedReader = computed(() =>
  patrons.value.find((p) => p.patron.id === Number(selectedPatron.value)),
);
const categoryName = (code) => {
  let c = options.value.categories.find((x) => x.code === code);
  return c ? (language.value === "zh" ? c.name : c.nameEn || c.name) : code;
};
const libraryName = (id) =>
  options.value.departments.find((d) => d.id === id)?.name || `#${id}`;
/** 失败保持原始输入，不把未知提交结果当作成功。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
function fail(e) {
  notice.value = { type: "error", text: errorMessage(e) };
  if (e.status === 401) {
    profile.value = null;
    editor.value = null;
    passwordForm.value = null;
    resetApi();
  }
}
/** 所有写入集中防重复，服务端仍检查权限、状态和版本。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
async function perform(path, method, body) {
  if (pending.value) return null;
  pending.value = true;
  notice.value = null;
  try {
    const v = await api(path, { method, body });
    notice.value = { type: "success", text: t("已保存。", "Saved.") };
    return v;
  } catch (e) {
    fail(e);
    return null;
  } finally {
    pending.value = false;
  }
}
async function read(fn) {
  try {
    await fn();
  } catch (e) {
    fail(e);
  }
}
/** 初始化仅读取当前身份，不写入示例客户或书目。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
onMounted(async () => {
  try {
    profile.value = await api("/auth/me");
    await initialize();
  } catch (e) {
    if (e.status !== 401) fail(e);
  } finally {
    ready.value = true;
  }
});
async function initialize() {
  pending.value = true;
  try {
    options.value = await api("/options");
    view.value = menus.value[0]?.code || "catalog";
    await loadView();
  } finally {
    pending.value = false;
  }
}
async function signIn() {
  const p = await perform("/auth/login", "POST", loginForm.value);
  if (p) {
    profile.value = p;
    loginForm.value.password = "";
    notice.value = null;
    await read(initialize);
  }
}
async function signOut() {
  if (!(await canLeave())) return;
  const v = await perform("/auth/logout", "POST", {});
  if (v) {
    profile.value = null;
    detail.value = null;
    desk.value = null;
    resetApi();
    notice.value = null;
  }
}
async function canLeave() {
  if (pending.value) return false;
  if (
    editor.value &&
    JSON.stringify(editor.value.row) !== editorInitial.value &&
    !(await ask(t("放弃尚未保存的修改？", "Discard unsaved changes?")))
  )
    return false;
  if (adminRef.value?.canLeave && !(await adminRef.value.canLeave()))
    return false;
  editor.value = null;
  return true;
}
async function navigate(code) {
  if (!(await canLeave())) return;
  view.value = code;
  mobileNav.value = false;
  detail.value = null;
  notice.value = null;
  await read(loadView);
}
/** 目录和借阅都是来自真实 API 的记录。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
async function loadBooks() {
  catalog.value = await api(
    `/books?q=${encodeURIComponent(query.value)}&category=${encodeURIComponent(category.value)}&status=${bookStatus.value}&page=${page.value}&size=12&sort=${sort.value}`,
  );
}
async function loadPatrons() {
  patrons.value = await api("/patrons");
}
async function loadView() {
  if (view.value === "catalog") await loadBooks();
  else if (view.value === "patrons" || view.value === "desk") {
    await loadPatrons();
    if (view.value === "desk" && barcode.value) await lookup();
  } else if (view.value === "my") my.value = await api("/my");
  else if (view.value === "reports") report.value = await api("/reports");
  else if (view.value === "audit") audit.value = await api("/audit");
}
async function searchBooks() {
  page.value = 1;
  await read(loadBooks);
}
async function bookPage(d) {
  page.value += d;
  await read(loadBooks);
}
async function openBook(id) {
  await read(async () => (detail.value = await api("/books/" + id)));
}
async function reserve() {
  const v = await perform("/holds", "POST", { bookId: detail.value.book.id });
  if (v) {
    notice.value = {
      type: "success",
      text:
        v.status === "READY"
          ? t(
              "预约成功，单册已保留，请在截止前到馆领取。",
              "Hold ready. Collect the reserved copy before the deadline.",
            )
          : t(
              "已加入候补队列，可在“我的借阅”查看。",
              "Joined the queue. See My borrowing.",
            ),
    };
    await openBook(detail.value.book.id);
  }
}
/** 新表单拷贝记录；已有身份字段不重写。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
function edit(kind, row = null) {
  let data = row
    ? cloneRecord(row)
    : kind === "book"
      ? {
          departmentId: options.value.departments[0]?.id || "",
          title: "",
          author: "",
          isbn: "",
          category: options.value.categories[0]?.code || "",
          language: "zh-CN",
          description: "",
          active: true,
        }
      : kind === "copy"
        ? { barcode: "", shelf: "", status: "AVAILABLE", note: "" }
        : { accountId: "", cardNo: "", active: true, note: "" };
  editor.value = { kind, row: data };
  editorInitial.value = JSON.stringify(data);
}
async function closeEditor() {
  if (
    JSON.stringify(editor.value.row) !== editorInitial.value &&
    !(await ask(t("放弃尚未保存的修改？", "Discard unsaved changes?")))
  )
    return;
  editor.value = null;
}
async function saveEditor() {
  const { kind, row } = editor.value;
  let path =
    kind === "book"
      ? "/books"
      : kind === "copy"
        ? row.id
          ? "/copies"
          : `/books/${detail.value.book.id}/copies`
        : "/patrons";
  if (row.id) path += "/" + row.id;
  const v = await perform(path, row.id ? "PUT" : "POST", row);
  if (v) {
    editor.value = null;
    await read(async () => {
      options.value = await api("/options");
      if (kind === "patron") await loadPatrons();
      else {
        await loadBooks();
        await openBook(kind === "book" ? v.id : detail.value.book.id);
      }
    });
  }
}
async function remove(kind, row) {
  if (
    !(await ask(
      t(
        "删除无历史记录？已有流通记录须停用或退出。",
        "Delete unused record? Records with history must be disabled or withdrawn.",
      ),
    ))
  )
    return;
  const v = await perform(
    `/${kind}/${row.id}?version=${row.version}`,
    "DELETE",
  );
  if (v) {
    await read(async () => {
      if (kind === "books") {
        detail.value = null;
        await loadBooks();
      } else if (kind === "copies") await openBook(detail.value.book.id);
      else await loadPatrons();
    });
  }
}
/** 条码 Enter 只查询，借出和归还必须分别确认提交。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
async function lookup() {
  desk.value = null;
  selectedPatron.value = "";
  returnNote.value = "";
  returnCondition.value = "AVAILABLE";
  desk.value = await api(
    "/lookup?barcode=" + encodeURIComponent(barcode.value.trim()),
  );
  if (desk.value.hold) selectedPatron.value = desk.value.hold.hold.patronId;
}
async function openDesk(code) {
  if (!(await canLeave())) return;
  view.value = "desk";
  mobileNav.value = false;
  barcode.value = code;
  detail.value = null;
  await read(async () => {
    await loadPatrons();
    await lookup();
  });
}
async function borrow() {
  if (!selectedReader.value) return;
  if (
    !(await ask(
      t(
        `确认将《${desk.value.book.title}》借给 ${selectedReader.value.name}？`,
        `Check out “${desk.value.book.title}” to ${selectedReader.value.name}?`,
      ),
    ))
  )
    return;
  const v = await perform("/checkout", "POST", {
    copyId: desk.value.copy.id,
    patronId: Number(selectedPatron.value),
    version: desk.value.copy.version,
  });
  if (v) {
    await read(async () => {
      await lookup();
      await loadPatrons();
    });
  }
}
async function checkin() {
  const l = desk.value.loan.loan;
  if (
    !(await ask(t("确认办理归还/遗失结案？", "Confirm return / loss closure?")))
  )
    return;
  const v = await perform(`/loans/${l.id}/return`, "POST", {
    version: l.version,
    condition: returnCondition.value,
    note: returnNote.value,
  });
  if (v)
    await read(async () => {
      await lookup();
      await loadPatrons();
    });
}
async function exportCsv() {
  await read(() => download("/reports/export", "librarydesk-loans.csv"));
}
async function changePassword() {
  const v = await perform("/auth/password", "POST", passwordForm.value);
  if (v) {
    passwordForm.value = null;
    profile.value = null;
    resetApi();
    notice.value = {
      type: "success",
      text: t("密码已修改，请重新登录。", "Password changed. Sign in again."),
    };
  }
}
const metricsLabels = {
  books: () => t("书目", "Titles"),
  copies: () => t("馆藏单册", "Copies"),
  available: () => t("可借", "Available"),
  onLoan: () => t("借出", "On loan"),
  held: () => t("待领取", "Held"),
  overdue: () => t("逾期未还", "Overdue"),
  waiting: () => t("候补预约", "Waiting holds"),
  damaged: () => t("损坏", "Damaged"),
  lost: () => t("遗失", "Lost"),
  patrons: () => t("登记读者", "Patrons"),
};
</script>
<template>
  <div v-if="!ready" class="startup">
    {{ t("连接图书馆…", "Connecting to library…") }}
  </div>
  <div v-else-if="!profile" class="login-page">
    <header class="login-top">
      <a
        class="brand"
        href="https://www.zhuatech.cn/"
        target="_blank"
        rel="noopener"
        ><img src="/brand/logo.jpg" alt="知华科技" /><span
          >LibraryDesk<small>{{
            t("知华图书借阅管理", "ZhiHua Library Circulation")
          }}</small></span
        ></a
      ><button @click="language = language === 'zh' ? 'en' : 'zh'">
        {{ language === "zh" ? "English" : "中文" }}
      </button>
    </header>
    <main class="login-card">
      <div class="eyebrow">LIBRARY / CIRCULATION</div>
      <h1>{{ t("登录图书馆", "Sign in to your library") }}</h1>
      <p>
        {{
          t(
            "馆藏、借阅与读者服务。",
            "Catalog, circulation and patron services.",
          )
        }}
      </p>
      <form @submit.prevent="signIn">
        <label
          >{{ t("账号", "Username")
          }}<input
            v-model="loginForm.username"
            autocomplete="username"
            required
            maxlength="60" /></label
        ><label
          >{{ t("密码", "Password")
          }}<input
            v-model="loginForm.password"
            type="password"
            autocomplete="current-password"
            required
            maxlength="128"
        /></label>
        <p v-if="notice" class="form-error" role="alert">{{ notice.text }}</p>
        <button class="primary full" :disabled="pending">
          {{ pending ? t("登录中…", "Signing in…") : t("登录", "Sign in") }}
        </button>
      </form>
    </main>
    <footer class="login-footer">
      {{
        t(
          "知华科技（上海如静知华信息科技有限公司）",
          "ZhiHua Technology (Shanghai Rujing Zhihua Information Technology Co., Ltd.)",
        )
      }}<br />{{
        t(
          "公开源码学习版 · 非商业使用",
          "Public source learning edition · Non-commercial use",
        )
      }}<button class="link" @click="about = true">
        {{ t("商业授权与联系", "Licensing & contact") }}
      </button>
    </footer>
  </div>
  <div v-else class="app-shell">
    <aside class="sidebar" :class="{ expanded: mobileNav }">
      <a
        class="brand"
        href="https://www.zhuatech.cn/"
        target="_blank"
        rel="noopener"
        ><img src="/brand/logo.jpg" alt="知华科技" /><span
          >LibraryDesk<small>{{
            t("知华图书借阅管理", "ZhiHua library circulation")
          }}</small></span
        ></a
      >
      <div class="nav-caption">
        {{ t("图书馆工作台", "LIBRARY WORKSPACE") }}
      </div>
      <nav>
        <button
          v-for="m in menus"
          :key="m.code"
          :class="{ active: view === m.code }"
          :disabled="pending"
          @click="navigate(m.code)"
        >
          <component :is="icons[m.code]" :size="18" /><span>{{
            language === "zh" ? m.name : m.nameEn || m.name
          }}</span>
        </button>
      </nav>
      <div class="sidebar-bottom">
        <button @click="about = true">
          <ExternalLink :size="16" />{{
            t("联系知华科技", "Contact ZhiHua")
          }}</button
        ><small>{{
          t("公开源码学习版 · 非商业使用", "Public source · Non-commercial")
        }}</small>
      </div>
    </aside>
    <div class="workspace">
      <header class="topbar">
        <button
          class="mobile-toggle"
          :aria-label="t('打开菜单', 'Open menu')"
          @click="mobileNav = !mobileNav"
        >
          <Menu :size="20" /></button
        ><span
          >{{ libraryName(profile.departmentId)
          }}<small>{{
            profile.scope === "ALL"
              ? t("全部图书馆范围", "All-library scope")
              : t("本馆范围", "This-library scope")
          }}</small></span
        >
        <div class="actions">
          <button
            class="language-button"
            @click="language = language === 'zh' ? 'en' : 'zh'"
          >
            {{ language === "zh" ? "EN" : "中文" }}</button
          ><span class="account-name"
            >{{ profile.displayName }}<small>{{ profile.role }}</small></span
          ><button
            :aria-label="t('修改密码', 'Change password')"
            @click="passwordForm = { oldPassword: '', newPassword: '' }"
          >
            <KeyRound :size="16" /></button
          ><button
            :disabled="pending"
            :aria-label="t('退出登录', 'Sign out')"
            @click="signOut"
          >
            <LogOut :size="16" />
          </button>
        </div>
      </header>
      <main class="main-content">
        <div
          v-if="notice"
          class="notice"
          :class="notice.type"
          :role="notice.type === 'error' ? 'alert' : 'status'"
        >
          <span>{{ notice.text }}</span
          ><button
            :aria-label="t('关闭提示', 'Dismiss notice')"
            @click="notice = null"
          >
            <X :size="16" />
          </button>
        </div>
        <template v-if="view === 'catalog'">
          <section v-if="!detail">
            <header class="page-heading">
              <div>
                <div class="eyebrow">CATALOG</div>
                <h1>{{ t("馆藏目录", "Library catalog") }}</h1>
                <p class="muted">
                  {{
                    t(
                      "按书名、作者或 ISBN 查找图书。",
                      "Find a title by name, author or ISBN.",
                    )
                  }}
                </p>
              </div>
              <button
                v-if="has('catalog_manage')"
                class="primary"
                :disabled="pending"
                @click="edit('book')"
              >
                <Plus :size="17" />{{ t("新增书目", "Add title") }}
              </button>
            </header>
            <form class="toolbar" @submit.prevent="searchBooks">
              <div class="search-box">
                <Search :size="17" /><input
                  v-model="query"
                  :placeholder="
                    t('搜索书名、作者、ISBN', 'Search title, author, ISBN')
                  "
                  :aria-label="t('搜索馆藏', 'Search catalog')"
                />
              </div>
              <select
                v-model="category"
                :aria-label="t('图书分类', 'Category')"
              >
                <option value="">{{ t("全部分类", "All categories") }}</option>
                <option
                  v-for="c in options.categories"
                  :key="c.id"
                  :value="c.code"
                >
                  {{ categoryName(c.code) }}
                </option></select
              ><select
                v-if="has('catalog_manage')"
                v-model="bookStatus"
                :aria-label="t('书目状态', 'Title status')"
              >
                <option value="">{{ t("全部状态", "All statuses") }}</option>
                <option value="active">{{ t("在馆书目", "Active") }}</option>
                <option value="archived">
                  {{ t("归档书目", "Archived") }}
                </option></select
              ><select
                v-model="sort"
                :aria-label="t('馆藏排序', 'Catalog sorting')"
              >
                <option value="title">{{ t("按书名", "By title") }}</option>
                <option value="latest">
                  {{ t("最新上架", "Newest") }}
                </option></select
              ><button :disabled="pending">{{ t("搜索", "Search") }}</button>
            </form>
            <div class="list-heading">
              <span>{{ t("书目与可借单册", "Titles and availability") }}</span
              ><span>{{ catalog.total }} {{ t("种", "titles") }}</span>
            </div>
            <div class="catalog-list">
              <button
                v-for="b in catalog.items"
                :key="b.book.id"
                class="book-row"
                @click="openBook(b.book.id)"
              >
                <div class="book-mark"><BookOpen :size="23" /></div>
                <div class="book-title">
                  <strong>{{ b.book.title }}</strong
                  ><span
                    >{{ b.book.author }} ·
                    {{ categoryName(b.book.category) }}</span
                  ><small
                    >{{ b.book.isbn || t("未登记 ISBN", "No ISBN") }} ·
                    {{ libraryName(b.book.departmentId) }}</small
                  >
                </div>
                <div class="availability">
                  <strong
                    >{{ b.available }}<span> / {{ b.copies }}</span></strong
                  ><small>{{ t("可借 / 单册", "Available / copies") }}</small
                  ><span v-if="!b.book.active" class="status withdrawn">{{
                    t("已归档", "Archived")
                  }}</span
                  ><small v-if="b.waiting"
                    >{{ b.waiting }} {{ t("人候补", "waiting") }}</small
                  >
                </div>
              </button>
              <div v-if="!catalog.items.length" class="empty">
                {{ t("暂无符合条件的书目。", "No matching titles.") }}
              </div>
            </div>
            <div class="pager">
              <span
                >{{ page }} /
                {{ Math.max(1, Math.ceil(catalog.total / 12)) }}</span
              ><button :disabled="page === 1 || pending" @click="bookPage(-1)">
                {{ t("上一页", "Previous") }}</button
              ><button
                :disabled="page * 12 >= catalog.total || pending"
                @click="bookPage(1)"
              >
                {{ t("下一页", "Next") }}
              </button>
            </div>
          </section>
          <section v-else>
            <button class="back-button" @click="detail = null">
              <ArrowLeft :size="16" />{{ t("返回目录", "Back to catalog") }}
            </button>
            <header class="page-heading detail-heading">
              <div>
                <div class="eyebrow">
                  {{ categoryName(detail.book.category) }}
                </div>
                <h1>{{ detail.book.title }}</h1>
                <p class="muted">
                  {{ detail.book.author }} ·
                  {{ detail.book.isbn || t("无 ISBN", "No ISBN") }} ·
                  {{ detail.book.language }}
                </p>
              </div>
              <div class="actions">
                <button
                  v-if="has('catalog_manage')"
                  :disabled="pending"
                  @click="edit('book', detail.book)"
                >
                  <Pencil :size="16" />{{ t("编辑书目", "Edit title") }}</button
                ><button
                  v-if="has('borrow') && detail.book.active"
                  class="primary"
                  :disabled="pending"
                  @click="reserve"
                >
                  {{ t("预约此书", "Place hold") }}
                </button>
              </div>
            </header>
            <p v-if="detail.book.description" class="description">
              {{ detail.book.description }}
            </p>
            <div class="section-heading">
              <h2>{{ t("馆藏单册", "Copies") }}</h2>
              <button
                v-if="has('catalog_manage') && detail.book.active"
                :disabled="pending"
                @click="edit('copy')"
              >
                <Plus :size="16" />{{ t("添加单册", "Add copy") }}
              </button>
            </div>
            <div class="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>{{ t("单册条码", "Barcode") }}</th>
                    <th>{{ t("架位", "Shelf") }}</th>
                    <th>{{ t("状态", "Status") }}</th>
                    <th>{{ t("备注", "Note") }}</th>
                    <th v-if="has('catalog_manage') || has('circulation')">
                      {{ t("操作", "Actions") }}
                    </th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="c in detail.copies" :key="c.id">
                    <td class="mono">{{ c.barcode }}</td>
                    <td>{{ c.shelf || "—" }}</td>
                    <td>
                      <span class="status" :class="c.status.toLowerCase()">{{
                        stateName(c.status)
                      }}</span>
                    </td>
                    <td>{{ c.note || "—" }}</td>
                    <td v-if="has('catalog_manage') || has('circulation')">
                      <div class="row-actions">
                        <button
                          v-if="has('circulation')"
                          :disabled="pending"
                          @click="openDesk(c.barcode)"
                        >
                          {{ t("柜台办理", "Open desk") }}</button
                        ><button
                          v-if="has('catalog_manage')"
                          :disabled="pending"
                          @click="edit('copy', c)"
                        >
                          {{ t("编辑", "Edit") }}</button
                        ><button
                          v-if="
                            has('catalog_manage') && c.status === 'AVAILABLE'
                          "
                          class="link danger-text"
                          :disabled="pending"
                          @click="remove('copies', c)"
                        >
                          {{ t("删除", "Delete") }}
                        </button>
                      </div>
                    </td>
                  </tr>
                  <tr v-if="!detail.copies.length">
                    <td colspan="5" class="empty">
                      {{
                        t(
                          "暂无单册，馆员可添加实际馆藏。",
                          "No copies. Staff can add actual inventory.",
                        )
                      }}
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
            <p class="muted meta-line">
              {{ libraryName(detail.book.departmentId) }} ·
              {{
                detail.book.active
                  ? t("在馆书目", "Active title")
                  : t("已归档", "Archived")
              }}
            </p>
            <div
              v-if="detail.holds.length && !has('circulation')"
              class="inline-note"
            >
              {{
                t(
                  "你的预约记录可在“我的借阅”查看和取消。",
                  "View or cancel your holds in My borrowing.",
                )
              }}
            </div>
            <div
              v-if="has('circulation') && detail.events?.length"
              class="event-list"
            >
              <h2>{{ t("单册流通记录", "Circulation history") }}</h2>
              <div v-for="e in detail.events.slice(0, 8)" :key="e.id">
                <span class="mono">{{ e.action }}</span
                ><span>{{ e.note || "—" }}</span
                ><time>{{ dateTime(e.createdAt) }}</time>
              </div>
            </div>
            <button
              v-if="has('catalog_manage') && !detail.copies.length"
              class="link danger-text"
              :disabled="pending"
              @click="remove('books', detail.book)"
            >
              {{ t("删除空书目", "Delete unused title") }}
            </button>
          </section>
        </template>
        <section v-else-if="view === 'desk'">
          <header class="page-heading">
            <div>
              <div class="eyebrow">CIRCULATION</div>
              <h1>{{ t("借还柜台", "Circulation desk") }}</h1>
              <p class="muted">
                {{
                  t(
                    "扫描或输入单册条码，核对后办理。",
                    "Scan or enter a copy barcode, then confirm the transaction.",
                  )
                }}
              </p>
            </div>
          </header>
          <form class="barcode-form" @submit.prevent="read(lookup)">
            <ScanLine :size="24" /><label class="sr-only" for="barcode">{{
              t("单册条码", "Copy barcode")
            }}</label
            ><input
              id="barcode"
              v-model="barcode"
              class="mono"
              autocomplete="off"
              :placeholder="
                t('单册条码，按 Enter 查询', 'Copy barcode, Enter to look up')
              "
              maxlength="40"
              required
            /><button class="primary" :disabled="pending">
              {{ t("查询单册", "Look up copy") }}
            </button>
          </form>
          <div v-if="!desk" class="empty desk-empty">
            <ScanLine :size="35" />
            <h2>{{ t("等待单册条码", "Ready for a copy barcode") }}</h2>
            <p>
              {{
                t(
                  "查询不会自动借出或归还。",
                  "Looking up a barcode does not check a copy in or out.",
                )
              }}
            </p>
          </div>
          <div v-else class="desk-layout">
            <article class="copy-summary">
              <div class="eyebrow">{{ categoryName(desk.book.category) }}</div>
              <h2>{{ desk.book.title }}</h2>
              <p>{{ desk.book.author }}</p>
              <div class="barcode-label">{{ desk.copy.barcode }}</div>
              <dl>
                <dt>{{ t("架位", "Shelf") }}</dt>
                <dd>{{ desk.copy.shelf || "—" }}</dd>
                <dt>{{ t("图书馆", "Library") }}</dt>
                <dd>{{ libraryName(desk.book.departmentId) }}</dd>
                <dt>{{ t("单册状态", "Copy status") }}</dt>
                <dd>
                  <span
                    class="status"
                    :class="desk.copy.status.toLowerCase()"
                    >{{ stateName(desk.copy.status) }}</span
                  >
                </dd>
              </dl>
              <p v-if="desk.copy.note" class="muted">{{ desk.copy.note }}</p>
            </article>
            <article class="transaction-card">
              <template v-if="desk.loan"
                ><div class="section-heading">
                  <h2>{{ t("办理归还", "Check in") }}</h2>
                  <span v-if="desk.loan.overdue" class="status danger">{{
                    t("逾期", "Overdue")
                  }}</span>
                </div>
                <dl>
                  <dt>{{ t("借阅人", "Patron") }}</dt>
                  <dd>{{ desk.loan.patron }} · {{ desk.loan.cardNo }}</dd>
                  <dt>{{ t("借出日期", "Loan date") }}</dt>
                  <dd>{{ desk.loan.loan.loanDate }}</dd>
                  <dt>{{ t("应还日期", "Due date") }}</dt>
                  <dd>{{ desk.loan.loan.dueDate }}</dd>
                </dl>
                <form @submit.prevent="checkin">
                  <label
                    >{{ t("单册归还情况", "Return condition")
                    }}<select v-model="returnCondition">
                      <option
                        v-for="s in ['AVAILABLE', 'DAMAGED', 'LOST']"
                        :key="s"
                        :value="s"
                      >
                        {{
                          s === "AVAILABLE"
                            ? t("正常归还", "Normal return")
                            : s === "DAMAGED"
                              ? t("损坏归还", "Damaged return")
                              : t("遗失结案", "Close as lost")
                        }}
                      </option>
                    </select></label
                  ><label
                    >{{ t("办理备注", "Transaction note")
                    }}<textarea
                      v-model="returnNote"
                      :required="returnCondition !== 'AVAILABLE'"
                      maxlength="500"
                      rows="3"
                      :placeholder="
                        returnCondition === 'AVAILABLE'
                          ? t('可选', 'Optional')
                          : t('请说明损坏或遗失情况', 'Describe damage or loss')
                      "
                    ></textarea></label
                  ><button class="primary full" :disabled="pending">
                    {{
                      returnCondition === "LOST"
                        ? t("确认遗失结案", "Confirm loss closure")
                        : t("确认归还", "Confirm check-in")
                    }}
                  </button>
                </form></template
              ><template
                v-else-if="
                  ['AVAILABLE', 'HELD'].includes(desk.copy.status) &&
                  desk.book.active
                "
                ><h2>{{ t("办理借出", "Check out") }}</h2>
                <div v-if="desk.hold" class="inline-note">
                  {{ t("已保留给", "Reserved for") }} {{ desk.hold.patron }} ·
                  {{ desk.hold.cardNo
                  }}<small
                    >{{ t("领取截止", "Pickup deadline") }}
                    {{ dateTime(desk.hold.hold.expiresAt) }}</small
                  >
                </div>
                <form @submit.prevent="borrow">
                  <label
                    >{{ t("选择读者 / 借阅证", "Select patron / card")
                    }}<select v-model="selectedPatron" required>
                      <option value="" disabled>
                        {{ t("请选择读者", "Select a patron") }}
                      </option>
                      <option
                        v-for="p in eligiblePatrons"
                        :key="p.patron.id"
                        :value="p.patron.id"
                      >
                        {{ p.name }} · {{ p.patron.cardNo }}
                      </option>
                    </select></label
                  >
                  <p v-if="selectedReader" class="muted">
                    {{ t("当前未还", "Open loans") }}
                    {{ selectedReader.openLoans }}
                  </p>
                  <button
                    class="primary full"
                    :disabled="pending || !selectedPatron"
                  >
                    {{ t("核对并借出", "Confirm check-out") }}
                  </button>
                </form>
                <p v-if="!eligiblePatrons.length" class="inline-note">
                  {{
                    t(
                      "本馆暂无可选读者，请先创建有读者权限的账号并开通借阅证。",
                      "No eligible patrons. Create a borrowing account and register a borrower card first.",
                    )
                  }}
                </p></template
              ><template v-else
                ><h2>{{ t("暂不可办理借出", "Check-out unavailable") }}</h2>
                <p class="muted">
                  {{
                    t(
                      "请在馆藏目录核对单册与书目状态。",
                      "Review copy and title status in the catalog.",
                    )
                  }}
                </p></template
              >
            </article>
          </div>
        </section>
        <RecordsPanel
          v-else-if="['loans', 'holds'].includes(view)"
          ref="recordsRef"
          :kind="view"
          :mine="false"
          :pending="pending"
          :perform="perform"
          @error="fail"
          @counter="openDesk"
        />
        <section v-else-if="view === 'my'">
          <header class="page-heading">
            <div>
              <div class="eyebrow">PATRON SERVICES</div>
              <h1>{{ t("我的借阅", "My borrowing") }}</h1>
              <p v-if="my?.registered" class="muted">
                {{ t("借阅证", "Borrower card") }} {{ my.patron.cardNo }} ·
                {{
                  my.patron.active
                    ? t("有效", "Active")
                    : t("已停用", "Disabled")
                }}
              </p>
            </div>
          </header>
          <div v-if="my && !my.registered" class="empty">
            {{
              t(
                "尚未开通借阅证，请联系馆员。",
                "No borrower card. Contact your librarian.",
              )
            }}
          </div>
          <template v-if="my?.registered"
            ><div class="sub-tabs">
              <button
                :class="{ active: myKind === 'loans' }"
                @click="myKind = 'loans'"
              >
                {{ t("我的借阅", "My loans") }}</button
              ><button
                :class="{ active: myKind === 'holds' }"
                @click="myKind = 'holds'"
              >
                {{ t("我的预约", "My holds") }}
              </button>
            </div>
            <RecordsPanel
              :kind="myKind"
              :mine="true"
              :pending="pending"
              :perform="perform"
              @error="fail"
          /></template>
        </section>
        <section v-else-if="view === 'patrons'">
          <header class="page-heading">
            <div>
              <div class="eyebrow">PATRONS</div>
              <h1>{{ t("读者管理", "Patrons") }}</h1>
              <p class="muted">
                {{
                  t(
                    "借阅证关联登录账号，停用仍保留流通历史。",
                    "Borrower cards link to accounts. Disabling preserves circulation history.",
                  )
                }}
              </p>
            </div>
            <button class="primary" :disabled="pending" @click="edit('patron')">
              <Plus :size="16" />{{ t("开通借阅证", "Register patron") }}
            </button>
          </header>
          <div class="toolbar">
            <div class="search-box">
              <Search :size="16" /><input
                v-model="patronQuery"
                :placeholder="t('搜索读者或借阅证', 'Search patron or card')"
                :aria-label="t('搜索读者', 'Search patrons')"
                @input="patronPage = 1"
              />
            </div>
            <button :disabled="pending" @click="read(loadPatrons)">
              <RefreshCw :size="16" />{{ t("刷新", "Refresh") }}</button
            ><span class="count"
              >{{ patronRows.length }} {{ t("人", "patrons") }}</span
            >
          </div>
          <div class="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>{{ t("读者", "Patron") }}</th>
                  <th>{{ t("借阅证号", "Card number") }}</th>
                  <th>{{ t("图书馆", "Library") }}</th>
                  <th>{{ t("资格", "Privileges") }}</th>
                  <th>{{ t("未还册数", "Open loans") }}</th>
                  <th>{{ t("操作", "Actions") }}</th>
                </tr>
              </thead>
              <tbody>
                <tr
                  v-for="p in patronRows.slice(
                    (patronPage - 1) * 20,
                    patronPage * 20,
                  )"
                  :key="p.patron.id"
                >
                  <td>
                    <strong>{{ p.name }}</strong
                    ><small>{{ p.patron.note }}</small>
                  </td>
                  <td class="mono">{{ p.patron.cardNo }}</td>
                  <td>{{ libraryName(p.patron.departmentId) }}</td>
                  <td>
                    <span
                      class="status"
                      :class="
                        p.patron.active && p.accountEnabled
                          ? 'available'
                          : 'withdrawn'
                      "
                      >{{
                        p.patron.active && p.accountEnabled
                          ? t("可借阅", "Eligible")
                          : t("已停用", "Disabled")
                      }}</span
                    >
                  </td>
                  <td>{{ p.openLoans }}</td>
                  <td>
                    <div class="row-actions">
                      <button
                        :disabled="pending"
                        @click="edit('patron', p.patron)"
                      >
                        {{ t("编辑", "Edit") }}</button
                      ><button
                        v-if="!p.openLoans"
                        class="link danger-text"
                        :disabled="pending"
                        @click="remove('patrons', p.patron)"
                      >
                        {{ t("删除", "Delete") }}
                      </button>
                    </div>
                  </td>
                </tr>
                <tr v-if="!patronRows.length">
                  <td colspan="6" class="empty">
                    {{
                      t(
                        "暂无读者。先创建有读者权限的登录账号，再开通借阅证。",
                        "No patrons. Create a borrowing account, then register a card.",
                      )
                    }}
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
          <div class="pager">
            <span
              >{{ patronPage }} /
              {{ Math.max(1, Math.ceil(patronRows.length / 20)) }}</span
            ><button :disabled="patronPage === 1" @click="patronPage--">
              {{ t("上一页", "Previous") }}</button
            ><button
              :disabled="patronPage * 20 >= patronRows.length"
              @click="patronPage++"
            >
              {{ t("下一页", "Next") }}
            </button>
          </div>
        </section>
        <section v-else-if="view === 'reports'">
          <header class="page-heading">
            <div>
              <div class="eyebrow">CIRCULATION / REPORTS</div>
              <h1>{{ t("流通报表", "Circulation reports") }}</h1>
              <p class="muted">
                {{
                  t(
                    "当前授权范围；逾期按各馆时区和应还日期计算。",
                    "Current scope. Overdue status uses each library’s timezone and due date.",
                  )
                }}
              </p>
            </div>
            <div class="actions">
              <button :disabled="pending" @click="read(loadView)">
                <RefreshCw :size="16" />{{ t("刷新", "Refresh") }}</button
              ><button class="primary" @click="exportCsv">
                {{ t("导出借阅 CSV", "Export loans CSV") }}
              </button>
            </div>
          </header>
          <template v-if="report"
            ><div class="metric-grid">
              <div v-for="(v, k) in report.metrics" :key="k">
                <span>{{ metricsLabels[k]?.() || k }}</span
                ><strong>{{ v }}</strong>
              </div>
            </div>
            <div class="section-heading">
              <h2>{{ t("借阅台账", "Loan ledger") }}</h2>
              <span class="muted"
                >{{ report.rows.length }} {{ t("条", "loans") }}</span
              >
            </div>
            <div class="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>{{ t("图书 / 条码", "Title / barcode") }}</th>
                    <th>{{ t("读者 / 借阅证", "Patron / card") }}</th>
                    <th>{{ t("应还日期", "Due date") }}</th>
                    <th>{{ t("状态", "Status") }}</th>
                    <th>{{ t("续借次数", "Renewals") }}</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="l in report.rows" :key="l.loan.id">
                    <td>
                      {{ l.book }}<small class="mono">{{ l.barcode }}</small>
                    </td>
                    <td>
                      {{ l.patron }}<small>{{ l.cardNo }}</small>
                    </td>
                    <td>{{ l.loan.dueDate }}</td>
                    <td>
                      <span
                        class="status"
                        :class="
                          l.overdue ? 'danger' : l.loan.status.toLowerCase()
                        "
                        >{{
                          l.overdue
                            ? t("逾期未还", "Overdue")
                            : stateName(l.loan.status)
                        }}</span
                      >
                    </td>
                    <td>{{ l.loan.renewals }}</td>
                  </tr>
                  <tr v-if="!report.rows.length">
                    <td colspan="5" class="empty">
                      {{ t("暂无借阅记录。", "No loans yet.") }}
                    </td>
                  </tr>
                </tbody>
              </table>
            </div></template
          >
        </section>
        <AdminPanel
          v-else-if="['users', 'roles', 'settings'].includes(view)"
          ref="adminRef"
          :section="view"
          :pending="pending"
          :perform="perform"
          :message="notice?.type === 'error' ? notice.text : ''"
          @error="fail"
        />
        <section v-else-if="view === 'audit'">
          <header class="page-heading">
            <div>
              <h1>{{ t("操作记录", "Audit trail") }}</h1>
              <p class="muted">
                {{
                  t(
                    "保留账号、操作与对象编号，不记录密码。",
                    "Records actor, action and object ID; never passwords.",
                  )
                }}
              </p>
            </div>
            <button @click="read(loadView)">
              <RefreshCw :size="16" />{{ t("刷新", "Refresh") }}
            </button>
          </header>
          <div class="toolbar">
            <div class="search-box">
              <Search :size="16" /><input
                v-model="auditQuery"
                :aria-label="t('搜索操作记录', 'Search audit trail')"
                :placeholder="
                  t('搜索账号、操作、对象', 'Search actor, action, object')
                "
                @input="auditPage = 1"
              />
            </div>
            <span class="count"
              >{{ auditRows.length }} {{ t("条", "events") }}</span
            >
          </div>
          <div class="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>{{ t("时间", "Time") }}</th>
                  <th>{{ t("操作人", "Actor") }}</th>
                  <th>{{ t("操作", "Action") }}</th>
                  <th>{{ t("对象", "Object") }}</th>
                </tr>
              </thead>
              <tbody>
                <tr
                  v-for="a in auditRows.slice(
                    (auditPage - 1) * 20,
                    auditPage * 20,
                  )"
                  :key="a.id"
                >
                  <td>{{ dateTime(a.createdAt) }}</td>
                  <td>{{ a.actor }}</td>
                  <td class="mono">{{ a.action }}</td>
                  <td class="mono">{{ a.objectId }}</td>
                </tr>
                <tr v-if="!auditRows.length">
                  <td colspan="4" class="empty">
                    {{ t("暂无操作记录。", "No audit events.") }}
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
          <div class="pager">
            <span
              >{{ auditPage }} /
              {{ Math.max(1, Math.ceil(auditRows.length / 20)) }}</span
            ><button :disabled="auditPage === 1" @click="auditPage--">
              {{ t("上一页", "Previous") }}</button
            ><button
              :disabled="auditPage * 20 >= auditRows.length"
              @click="auditPage++"
            >
              {{ t("下一页", "Next") }}
            </button>
          </div>
        </section>
      </main>
    </div>
  </div>
  <div v-if="editor" class="modal-backdrop">
    <section
      class="modal"
      role="dialog"
      aria-modal="true"
      :aria-label="t('编辑记录', 'Edit record')"
    >
      <header>
        <h2>
          {{
            editor.kind === "book"
              ? t("书目资料", "Title details")
              : editor.kind === "copy"
                ? t("单册资料", "Copy details")
                : t("借阅证资料", "Borrower card")
          }}
        </h2>
        <button
          :disabled="pending"
          :aria-label="t('关闭表单', 'Close form')"
          @click="closeEditor"
        >
          <X :size="20" />
        </button>
      </header>
      <p v-if="notice?.type === 'error'" class="form-error" role="alert">
        {{ notice.text }}
      </p>
      <form @submit.prevent="saveEditor">
        <template v-if="editor.kind === 'book'"
          ><label
            >{{ t("书名", "Title")
            }}<input
              v-model="editor.row.title"
              required
              maxlength="200" /></label
          ><label
            >{{ t("作者 / 编者", "Author / editor")
            }}<input v-model="editor.row.author" required maxlength="160"
          /></label>
          <div class="form-grid">
            <label
              >ISBN<input
                v-model="editor.row.isbn"
                maxlength="25"
                :placeholder="t('可留空', 'Optional')" /></label
            ><label
              >{{ t("语言代码", "Language code")
              }}<input
                v-model="editor.row.language"
                required
                maxlength="40"
                placeholder="zh-CN" /></label
            ><label
              >{{ t("图书分类", "Category")
              }}<select v-model="editor.row.category" required>
                <option
                  v-for="c in options.categories"
                  :key="c.id"
                  :value="c.code"
                >
                  {{ categoryName(c.code) }}
                </option>
              </select></label
            ><label
              >{{ t("所属图书馆", "Library")
              }}<select
                v-model="editor.row.departmentId"
                required
                :disabled="!!editor.row.id"
              >
                <option
                  v-for="d in options.departments"
                  :key="d.id"
                  :value="d.id"
                >
                  {{ d.name }}
                </option>
              </select></label
            >
          </div>
          <label
            >{{ t("内容说明", "Description")
            }}<textarea
              v-model="editor.row.description"
              maxlength="2000"
              rows="3"
            ></textarea></label
          ><label class="check"
            ><input v-model="editor.row.active" type="checkbox" />{{
              t("在馆书目（取消后归档）", "Active title (uncheck to archive)")
            }}</label
          ></template
        ><template v-else-if="editor.kind === 'copy'"
          ><label
            >{{ t("单册条码", "Barcode")
            }}<input
              v-model="editor.row.barcode"
              class="mono"
              required
              minlength="3"
              maxlength="40"
              :disabled="!!editor.row.id"
              placeholder="LIB-000001" /></label
          ><label
            >{{ t("架位", "Shelf")
            }}<input v-model="editor.row.shelf" maxlength="80" /></label
          ><label v-if="editor.row.id"
            >{{ t("单册状态", "Copy status")
            }}<select
              v-model="editor.row.status"
              :disabled="
                ['ON_LOAN', 'HELD'].includes(
                  detail.copies.find((c) => c.id === editor.row.id)?.status,
                )
              "
            >
              <option
                v-for="s in [
                  'AVAILABLE',
                  'DAMAGED',
                  'LOST',
                  'WITHDRAWN',
                  ...(['ON_LOAN', 'HELD'].includes(editor.row.status)
                    ? [editor.row.status]
                    : []),
                ]"
                :key="s"
                :value="s"
              >
                {{ stateName(s) }}
              </option>
            </select></label
          ><label
            >{{
              t("备注（改变状态须说明）", "Note (required for state changes)")
            }}<textarea
              v-model="editor.row.note"
              maxlength="500"
              rows="3"
            ></textarea></label></template
        ><template v-else
          ><label
            >{{ t("关联账号", "Linked account")
            }}<select
              v-model="editor.row.accountId"
              required
              :disabled="!!editor.row.id"
            >
              <option value="" disabled>
                {{ t("请选择有读者权限的账号", "Select a borrowing account") }}
              </option>
              <option v-for="a in options.accounts" :key="a.id" :value="a.id">
                {{ a.displayName }} · {{ libraryName(a.departmentId) }}
              </option>
            </select></label
          >
          <p class="muted">
            {{
              t(
                "账号由管理员在“登录账号”创建并赋予读者权限。",
                "An administrator creates accounts with borrowing permission in Accounts.",
              )
            }}
          </p>
          <label
            >{{ t("借阅证号", "Card number")
            }}<input
              v-model="editor.row.cardNo"
              class="mono"
              required
              minlength="3"
              maxlength="40"
              :disabled="!!editor.row.id"
              placeholder="P-000001" /></label
          ><label class="check"
            ><input v-model="editor.row.active" type="checkbox" />{{
              t("启用借阅资格", "Enable borrowing privileges")
            }}</label
          ><label
            >{{ t("读者备注", "Patron note")
            }}<textarea
              v-model="editor.row.note"
              maxlength="500"
              rows="3"
            ></textarea></label
        ></template>
        <div class="modal-actions">
          <button type="button" :disabled="pending" @click="closeEditor">
            {{ t("取消", "Cancel") }}</button
          ><button class="primary" :disabled="pending">
            {{ t("保存", "Save") }}
          </button>
        </div>
      </form>
    </section>
  </div>
  <div v-if="passwordForm" class="modal-backdrop">
    <section
      class="modal compact"
      role="dialog"
      aria-modal="true"
      :aria-label="t('修改密码', 'Change password')"
    >
      <header>
        <h2>{{ t("修改密码", "Change password") }}</h2>
        <button
          :aria-label="t('关闭表单', 'Close form')"
          @click="passwordForm = null"
        >
          <X :size="20" />
        </button>
      </header>
      <p v-if="notice?.type === 'error'" class="form-error" role="alert">
        {{ notice.text }}
      </p>
      <form @submit.prevent="changePassword">
        <label
          >{{ t("原密码", "Current password")
          }}<input
            v-model="passwordForm.oldPassword"
            type="password"
            autocomplete="current-password"
            required /></label
        ><label
          >{{ t("新密码", "New password")
          }}<input
            v-model="passwordForm.newPassword"
            type="password"
            autocomplete="new-password"
            minlength="12"
            maxlength="72"
            required
        /></label>
        <p class="muted">
          {{
            t(
              "至少12字节，含大小写字母和数字；保存后重新登录。",
              "At least 12 bytes with upper/lowercase letters and a digit. Sign in again after saving.",
            )
          }}
        </p>
        <button class="primary full" :disabled="pending">
          {{ t("保存新密码", "Save password") }}
        </button>
      </form>
    </section>
  </div>
  <div v-if="about" class="modal-backdrop">
    <section
      class="modal contact-modal"
      role="dialog"
      aria-modal="true"
      :aria-label="t('联系知华科技', 'Contact ZhiHua')"
    >
      <header>
        <h2>{{ t("联系知华科技", "Contact ZhiHua") }}</h2>
        <button
          :aria-label="t('关闭联系信息', 'Close contact information')"
          @click="about = false"
        >
          <X :size="20" />
        </button>
      </header>
      <img class="contact-logo" src="/brand/logo.jpg" alt="知华科技" />
      <p>
        {{
          t(
            "知华科技（上海如静知华信息科技有限公司）",
            "ZhiHua Technology (Shanghai Rujing Zhihua Information Technology Co., Ltd.)",
          )
        }}
      </p>
      <p>
        {{
          t(
            "商业授权或深度定制开发请联系知华科技。",
            "Contact ZhiHua for commercial licensing, customization, deployment and integration.",
          )
        }}
      </p>
      <a href="https://www.zhuatech.cn/" target="_blank" rel="noopener"
        >https://www.zhuatech.cn/</a
      >
      <div v-if="language === 'zh'" class="qr-grid">
        <figure>
          <img src="/brand/wechat-zhuatech.png" alt="微信 zhuatech" />
          <figcaption>微信 zhuatech</figcaption>
        </figure>
        <figure>
          <img src="/brand/wechat-zhuatech2.png" alt="微信 zhuatech2" />
          <figcaption>微信 zhuatech2</figcaption>
        </figure>
      </div>
      <div v-else class="contact-links">
        <a href="mailto:han@zhuatech.cn">han@zhuatech.cn</a
        ><a href="mailto:jack@zhuatech.cn">jack@zhuatech.cn</a
        ><a href="https://wa.me/8617521234993" target="_blank" rel="noopener"
          >WhatsApp +86 17521234993</a
        >
      </div>
      <p class="muted">
        {{
          t(
            "仅限个人学习交流；未经上海如静知华信息科技有限公司授权不得商用。",
            "Personal learning and exchange only. Commercial use requires authorization from Shanghai Rujing Zhihua Information Technology Co., Ltd.",
          )
        }}
      </p>
    </section>
  </div>
  <div v-if="confirmation" class="modal-backdrop confirmation-layer">
    <section
      class="modal compact"
      role="alertdialog"
      aria-modal="true"
      :aria-label="t('确认操作', 'Confirm action')"
    >
      <h2>{{ t("确认操作", "Confirm action") }}</h2>
      <p>{{ confirmation.message }}</p>
      <div class="modal-actions">
        <button @click="answerConfirmation(false)">
          {{ t("取消", "Cancel") }}</button
        ><button class="primary" @click="answerConfirmation(true)">
          {{ t("确认", "Confirm") }}
        </button>
      </div>
    </section>
  </div>
</template>
