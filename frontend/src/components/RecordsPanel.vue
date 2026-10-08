<!-- Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2 -->
<script setup>
import { ref, watch } from "vue";
import { Search, RefreshCw } from "@lucide/vue";
import { api } from "../api.js";
import { t, stateName, dateTime, ask } from "../ui.js";
const props = defineProps({
  kind: { type: String, required: true },
  mine: Boolean,
  pending: Boolean,
  perform: { type: Function, required: true },
});
const emit = defineEmits(["error", "counter"]);
const records = ref({ items: [], total: 0, page: 1, size: 20 }),
  q = ref(""),
  state = ref(""),
  page = ref(1),
  sort = ref("latest");
/** 真实服务端范围、状态和分页，不使用前端假借阅。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
async function load() {
  try {
    records.value = await api(
      `/records/${props.kind}?mine=${props.mine}&q=${encodeURIComponent(q.value)}&state=${state.value}&page=${page.value}&size=20&sort=${sort.value}`,
    );
  } catch (e) {
    emit("error", e);
  }
}
watch(
  () => [props.kind, props.mine],
  () => {
    page.value = 1;
    state.value = "";
    q.value = "";
    load();
  },
  { immediate: true },
);
/** 本人续借先由服务端检查借期快照与候补。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
async function renew(row) {
  if (!(await ask(t(`续借《${row.book}》？`, `Renew “${row.book}”?`)))) return;
  const v = await props.perform(`/loans/${row.loan.id}/renew`, "POST", {
    version: row.loan.version,
  });
  if (v) await load();
}
/** 主动取消自己的预约，提交时使用当前版本，不会取消其他读者。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
async function cancel(row) {
  if (
    !(await ask(
      t(`取消《${row.book}》的预约？`, `Cancel the hold for “${row.book}”?`),
    ))
  )
    return;
  const v = await props.perform(`/holds/${row.hold.id}/cancel`, "POST", {
    version: row.hold.version,
    reason: t(
      "读者或馆员主动取消预约",
      "Hold cancelled by patron or librarian",
    ),
  });
  if (v) await load();
}
/** 馆员整理到期预约并分配空闲单册。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
async function process() {
  const v = await props.perform("/holds/process", "POST", {});
  if (v) await load();
}
function search() {
  page.value = 1;
  load();
}
function change(delta) {
  page.value += delta;
  load();
}
defineExpose({ load });
</script>
<template>
  <section>
    <header class="page-heading">
      <div>
        <h1>
          {{
            kind === "loans"
              ? t("借阅记录", "Loans")
              : t("预约队列", "Hold queue")
          }}
        </h1>
        <p class="muted">
          {{
            mine
              ? t("仅显示自己的记录。", "Your records only.")
              : t(
                  "当前图书馆范围内的流通记录。",
                  "Circulation within your library scope.",
                )
          }}
        </p>
      </div>
      <div class="actions">
        <button :disabled="pending" @click="load">
          <RefreshCw :size="16" />{{ t("刷新", "Refresh") }}</button
        ><button
          v-if="kind === 'holds' && !mine"
          :disabled="pending"
          @click="process"
        >
          {{ t("整理队列", "Process queue") }}
        </button>
      </div>
    </header>
    <form class="toolbar" @submit.prevent="search">
      <div class="search-box">
        <Search :size="16" /><input
          v-model="q"
          :placeholder="t('书名、读者、条码', 'Title, patron, barcode')"
          :aria-label="t('搜索记录', 'Search records')"
        />
      </div>
      <select v-model="state" :aria-label="t('记录状态', 'Record status')">
        <option value="">{{ t("全部状态", "All statuses") }}</option>
        <template v-if="kind === 'loans'"
          ><option
            v-for="s in ['OPEN', 'OVERDUE', 'RETURNED', 'LOST']"
            :key="s"
            :value="s"
          >
            {{ s === "OVERDUE" ? t("逾期未还", "Overdue") : stateName(s) }}
          </option></template
        ><template v-else
          ><option
            v-for="s in [
              'WAITING',
              'READY',
              'FULFILLED',
              'CANCELLED',
              'EXPIRED',
            ]"
            :key="s"
            :value="s"
          >
            {{ stateName(s) }}
          </option></template
        ></select
      ><select
        v-if="kind === 'loans'"
        v-model="sort"
        :aria-label="t('记录排序', 'Sort records')"
      >
        <option value="latest">{{ t("最新记录", "Newest") }}</option>
        <option value="due">{{ t("到期顺序", "Due date") }}</option></select
      ><button :disabled="pending">{{ t("搜索", "Search") }}</button
      ><span class="count">{{ records.total }} {{ t("条", "records") }}</span>
    </form>
    <div class="table-wrap">
      <table>
        <thead>
          <tr>
            <th>{{ t("图书", "Title") }}</th>
            <th v-if="!mine">{{ t("读者 / 借阅证", "Patron / card") }}</th>
            <th>{{ t("状态", "Status") }}</th>
            <th>
              {{
                kind === "loans"
                  ? t("借出 / 应还", "Loan / due")
                  : t("预约 / 领取截止", "Hold / pickup deadline")
              }}
            </th>
            <th>
              {{
                kind === "loans"
                  ? t("续借", "Renewals")
                  : t("单册 / 位置", "Copy / position")
              }}
            </th>
            <th>{{ t("操作", "Actions") }}</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in records.items" :key="(row.loan || row.hold).id">
            <td>
              <strong>{{ row.book }}</strong
              ><small>{{ row.barcode || "—" }}</small>
            </td>
            <td v-if="!mine">
              {{ row.patron }}<small>{{ row.cardNo }}</small>
            </td>
            <td>
              <span
                class="status"
                :class="(row.loan || row.hold).status.toLowerCase()"
                >{{ stateName((row.loan || row.hold).status) }}</span
              ><span v-if="row.overdue" class="status danger">{{
                t("逾期", "Overdue")
              }}</span
              ><small v-if="row.pickupExpired" class="danger-text">{{
                t(
                  "领取已超时，待整理释放",
                  "Pickup expired; awaiting queue processing",
                )
              }}</small>
            </td>
            <td>
              <template v-if="row.loan"
                >{{ row.loan.loanDate
                }}<small>{{ row.loan.dueDate }}</small></template
              ><template v-else
                >{{ dateTime(row.hold.createdAt)
                }}<small>{{ dateTime(row.hold.expiresAt) }}</small></template
              >
            </td>
            <td>
              {{
                row.loan
                  ? `${row.loan.renewals} / ${row.loan.maxRenewals}`
                  : row.hold.status === "WAITING"
                    ? `#${row.position}`
                    : row.barcode || "—"
              }}
            </td>
            <td>
              <div class="row-actions">
                <template v-if="row.loan?.status === 'OPEN'"
                  ><button
                    :disabled="
                      pending ||
                      row.overdue ||
                      row.loan.renewals >= row.loan.maxRenewals
                    "
                    @click="renew(row)"
                  >
                    {{ t("续借", "Renew") }}</button
                  ><button
                    v-if="!mine"
                    :disabled="pending"
                    @click="emit('counter', row.barcode)"
                  >
                    {{ t("柜台办理", "Open desk") }}
                  </button></template
                ><button
                  v-if="
                    row.hold && ['WAITING', 'READY'].includes(row.hold.status)
                  "
                  :disabled="pending"
                  @click="cancel(row)"
                >
                  {{ t("取消预约", "Cancel hold") }}</button
                ><span
                  v-if="
                    row.loan?.status !== 'OPEN' &&
                    !['WAITING', 'READY'].includes(row.hold?.status)
                  "
                  class="muted"
                  >—</span
                >
              </div>
              <small v-if="row.loan?.note || row.hold?.reason">{{
                row.loan?.note || row.hold?.reason
              }}</small>
            </td>
          </tr>
          <tr v-if="!records.items.length">
            <td colspan="6" class="empty">
              {{ t("没有符合条件的记录。", "No matching records.") }}
            </td>
          </tr>
        </tbody>
      </table>
    </div>
    <div class="pager">
      <span>{{ page }} / {{ Math.max(1, Math.ceil(records.total / 20)) }}</span
      ><button :disabled="page === 1 || pending" @click="change(-1)">
        {{ t("上一页", "Previous") }}</button
      ><button
        :disabled="page * 20 >= records.total || pending"
        @click="change(1)"
      >
        {{ t("下一页", "Next") }}
      </button>
    </div>
  </section>
</template>
