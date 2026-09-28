<script setup lang="ts">
/* 安许风险联动 · 员工侧面板（2026-09-30 按主体拆分）
 * 内容 = 原 safety-risk/index.vue 的员工侧区块，逐行搬迁：
 *   ② 多岗兼任校验（L1202-1259 + L255-286）、③ 人员风险（L1261-1327 + L288-323），
 *   外加两者共用的员工检索索引（L138-232：员工档案 + 员工证书在前端建索引，证书接口失败降级为只搜姓名/工号）。
 * 归属口径：见 docs/模块设计与规范/电子证照域-三台账信息架构与界面重设计-2026-09-28.md（员工侧 → 员工证件台账「安许配备」）。
 *
 * 拆分前的「区块开关 + 嵌入模式」已删除。原来 a-col 的 span 是"两块都开则各占 12"
 * （:span="props.showPersonRisk ? 12 : 24"），本面板恒含两块 → 固定 12/12，栅格与拆分前一致。
 * 页头（原 L933-995）不搬迁：它原本就是 v-if="!props.embedded"，宿主全部传 embedded，从未渲染过。 */
import { computed, onMounted, reactive, ref } from 'vue';

import { message } from 'ant-design-vue';

import {
  getPersonnelCertPage,
  getPersonnelProfilePage,
  type PersonnelCertApi,
} from '#/api/bidding/personnel';
import {
  checkMultiPost,
  getPersonnelRisk,
  type SafetyPermitApi,
} from '#/api/bidding/safetyPermit';

import { fmtDateTime } from '../../certificate/format';
import { useCertMeta } from '../../certificate/useCertMeta';
import { useCurrentSubject, useKeyPostOptions, useSafetyCan } from '../shared';

import '../../certificate/cert.css';

const { can } = useSafetyCan();
const { dictLabel, dictOptions, dictTag } = useCertMeta();
/* 企业主体 → entId：员工档案 / 员工证书索引按 entId 过滤（原为页面级单例 ref） */
const { entId, loadCurrentSubject } = useCurrentSubject();
/* 关键岗位码表：字典 key_post 优先 + KEY_POST_OPTIONS 兜底（原为页面级 computed） */
const keyPostOptions = useKeyPostOptions();

/* 员工下拉（不让用户手填无从得知的 personId）：候选取当前主体员工档案。
 * 2026-09-21 能力补强（#4）：原下拉只能按 姓名/工号 搜（option-filter-prop 只匹配 label），
 *   而"这人有哪张证 / 证书编号是多少"是查人员风险时更常用的入口 —— 尤其员工证书列表页的
 *   批量体检已按职责分工收归本页，"选人"就得更准。做法：一次拉齐 员工档案 + 员工证书，
 *   在前端建索引，不新增接口：
 *     · searchText = 姓名 + 工号 + 该人全部证书名/证书编号 → 一个搜索框四类关键词都能搜；
 *     · personCertOptions = 证书维度选项（逐张证一行），选中即带出持证人；
 *     · certsByPerson / certsByPersonMap → 选中后回显"这人挂着哪些证"，避免搜到了却看不出所以然。
 *   降级：证书接口失败（无权限/未启动）时只按 姓名/工号 检索，不阻断、不弹错。 */
type PersonOption = { certs: PersonnelCertApi.Cert[]; label: string; searchText: string; value: number };
const personOptions = ref<PersonOption[]>([]);
const personOptionsLoading = ref(false);
/** 证书维度下拉选项（value = 持证人 personId，同一人多张证就多条） */
const personCertOptions = ref<{ label: string; value: number }[]>([]);
const certOptionsLoading = ref(false);
/** personId → 证书，供"证书选项里的人不在员工档案前 200 条"时也能回显 */
const certsByPersonMap = ref<Map<number, PersonnelCertApi.Cert[]>>(new Map());
/** 索引降级说明（证书接口没取到 / 本主体无在册员工）：空下拉必须给出原因，不能静默为空 */
const personIndexNote = ref('');

/** 证书展示文案：证书名（编号），两者都缺时退到证书类型，避免出现空白选项 */
function certLabel(c: PersonnelCertApi.Cert): string {
  const name = c.certName || c.certType || '证书';
  return c.certNo ? name + '（' + c.certNo + '）' : name;
}

/** 自定义过滤：匹配 searchText（姓名/工号/证书名/证书编号），而不是只匹配 label（label 里没有证书信息） */
function filterPersonOption(input: string, option: PersonOption): boolean {
  const kw = String(input ?? '').trim().toLowerCase();
  if (!kw) return true;
  return (option?.searchText ?? option?.label ?? '').toLowerCase().includes(kw);
}

async function loadPersonOptions(): Promise<void> {
  if (!entId.value) {
    personOptions.value = [];
    personCertOptions.value = [];
    certsByPersonMap.value = new Map();
    personIndexNote.value = '';
    return;
  }
  personOptionsLoading.value = true;
  certOptionsLoading.value = true;
  try {
    const [page, certPage] = await Promise.all([
      getPersonnelProfilePage({ pageNo: 1, pageSize: 200, entId: entId.value }),
      /* 证书查询失败不阻断员工检索（降级为只按 姓名/工号 搜）。
       * ⚠️ pageSize 上限是 **200**（实测 500 会被后端判 400「每页条数最大值为 200」），
       * 写大了会被 catch 吞成"证书索引为空"，表现为功能没生效却毫无提示。 */
      getPersonnelCertPage({ pageNo: 1, pageSize: 200, entId: entId.value }).catch(() => null),
    ]);
    const map = new Map<number, PersonnelCertApi.Cert[]>();
    (certPage?.list ?? []).forEach((c) => {
      const pid = Number(c.personId ?? 0);
      if (!pid) return;
      const arr = map.get(pid) ?? [];
      arr.push(c);
      map.set(pid, arr);
    });
    certsByPersonMap.value = map;
    personOptions.value = (page.list ?? []).map((p) => {
      const id = Number(p.id);
      const certs = map.get(id) ?? [];
      const nameAndNo = p.personNo ? (p.personName ?? '') + '（' + p.personNo + '）' : (p.personName ?? '');
      return {
        certs,
        // label 保持短（下拉宽度有限），证书信息进 searchText：能搜到，但不挤占显示
        label: certs.length > 0 ? nameAndNo + ' · ' + certs.length + ' 证' : nameAndNo,
        searchText: [nameAndNo, ...certs.map((c) => certLabel(c))].join(' '),
        value: id,
      };
    });
    personCertOptions.value = (certPage?.list ?? [])
      .map((c) => ({
        label: certLabel(c) + ' · ' + (c.personName ?? '#' + c.personId),
        value: Number(c.personId ?? 0),
      }))
      .filter((o) => o.value > 0);
    /* "搜不到"必须给出原因，否则使用者会以为是搜索坏了：
       · 证书接口没取到（无权限/异常）→ 说清降级；· 本主体确实没有在册员工 → 指向建档入口。 */
    personIndexNote.value = !certPage
      ? '证书检索暂不可用（证书接口未取到数据，如账号无 bidding:personnel:cert:query）：当前只能按 姓名/工号 搜索。'
      : (page.list ?? []).length === 0
        ? '当前主体下没有在册员工档案（0 条）：请先在员工证书页登记员工，③ 才有可选人员。'
        : '';
  } catch {
    personOptions.value = [];
    personCertOptions.value = [];
    certsByPersonMap.value = new Map();
    personIndexNote.value = '员工档案未取到（如账号无 bidding:personnel:profile:query）：③ 的人员下拉为空。';
  } finally {
    personOptionsLoading.value = false;
    certOptionsLoading.value = false;
  }
}

/* ===== ② 多岗兼任校验 ===== */
const multiForm = reactive<{ personId?: number; primaryPost?: string; secondaryPost?: string }>({
  personId: undefined,
  primaryPost: undefined,
  secondaryPost: undefined,
});
const multiLoading = ref(false);
const multiResult = ref<null | SafetyPermitApi.MultiPostCheck>(null);

async function doMultiCheck() {
  if (!multiForm.personId || !multiForm.primaryPost || !multiForm.secondaryPost) {
    message.warning('人员、主岗、兼任岗不能为空');
    return;
  }
  if (multiForm.primaryPost === multiForm.secondaryPost) {
    message.warning('主岗与兼任岗不能相同');
    return;
  }
  multiLoading.value = true;
  multiResult.value = null;
  try {
    multiResult.value = await checkMultiPost({
      personId: multiForm.personId,
      primaryPost: multiForm.primaryPost,
      secondaryPost: multiForm.secondaryPost,
    });
  } catch {
    // 静默：错误结构由请求层呈现
  } finally {
    multiLoading.value = false;
  }
}

/* ===== ③ 人员风险 =====
 * 入参只有 personId（后端 GET /bidding/safety-permit/personnel/risk），所以"选人"就是本块的唯一门槛：
 * 支持 姓名/工号/证书名/证书编号 四类关键词检索（索引见上方 loadPersonOptions），并提供证书维度直达。
 * 查询动作仍是**显式点按钮**：改选人员只清空上一次结论，不自动发请求 ——
 * 该端点会写"评估留痕"，不能让"换着看看"变成一串无意义的评估记录。 */
const personId = ref<number | undefined>(undefined);
const personLoading = ref(false);
const personRisk = ref<null | SafetyPermitApi.PersonRisk>(null);

/** 选中员工在册证书：让人确认"搜到的证书确实挂在这人身上"（含只在证书维度出现、不在档案页里的人） */
const selectedPersonCerts = computed(() => {
  const pid = Number(personId.value ?? 0);
  if (!pid) return [] as PersonnelCertApi.Cert[];
  return personOptions.value.find((o) => o.value === pid)?.certs ?? certsByPersonMap.value.get(pid) ?? [];
});

/** 换人即清掉上一个人的结论：否则会把上一个人的风险标签看成这个人的 */
function onPickPerson(): void {
  personRisk.value = null;
}

async function doPersonRisk() {
  if (!personId.value) {
    message.warning('请选择员工');
    return;
  }
  personLoading.value = true;
  personRisk.value = null;
  try {
    personRisk.value = await getPersonnelRisk(personId.value);
  } catch {
    // 静默
  } finally {
    personLoading.value = false;
  }
}

onMounted(() => {
  /* 员工侧两块都靠「员工档案 + 员工证书」索引（② 的人员下拉、③ 的员工检索与在册证书回显），
     索引按 entId 过滤，故先取企业主体再加载 —— 拆分前 onMounted 里
     showMultiPost || showPersonRisk 分支的原样搬迁（本面板恒含这两块）。 */
  void loadCurrentSubject().then(() => {
    void loadPersonOptions();
  });
});
</script>

<template>
  <div class="ct-page ct-embedded-root">
    <a-row :gutter="16">
      <a-col :span="12">
        <div class="ct-panel mb-3">
          <div class="ct-panel-t">② 多岗兼任校验</div>
          <div class="ct-panel-b">
            <a-space direction="vertical" style="width: 100%">
              <a-select
                v-model:value="multiForm.personId"
                :options="personOptions"
                :loading="personOptionsLoading"
                show-search
                option-filter-prop="label"
                placeholder="选择员工（来自员工档案）"
                allow-clear
                style="width: 100%"
              />
              <a-select
                v-model:value="multiForm.primaryPost"
                placeholder="主岗"
                allow-clear
                :options="keyPostOptions"
                style="width: 100%"
              />
              <a-select
                v-model:value="multiForm.secondaryPost"
                placeholder="兼任岗"
                allow-clear
                :options="keyPostOptions"
                style="width: 100%"
              />
              <a-button
                v-if="can('bidding:personnel:cert:query')"
                type="primary"
                :loading="multiLoading"
                block
                @click="doMultiCheck"
              >
                校验兼任资格
              </a-button>
            </a-space>
            <div v-if="multiResult" class="mt-3">
              <a-tag :color="multiResult.pass ? 'success' : 'error'">
                {{ multiResult.pass ? '通过' : '不通过' }}
              </a-tag>
              <a-descriptions :column="1" size="small" bordered class="mt-2">
                <a-descriptions-item label="要求证书类型">
                  {{ multiResult.requiredCertType || '—' }}
                  <span v-if="multiResult.requiredCertSubType"> / {{ multiResult.requiredCertSubType }}</span>
                </a-descriptions-item>
                <a-descriptions-item label="匹配证书 id">{{ multiResult.matchedCertId ?? '—' }}</a-descriptions-item>
                <a-descriptions-item label="不通过原因">{{ multiResult.failReason || '—' }}</a-descriptions-item>
                <a-descriptions-item label="校验时间">{{ fmtDateTime(multiResult.checkedAt) }}</a-descriptions-item>
              </a-descriptions>
            </div>
            <div v-else class="ct-empty">选择员工与 主岗/兼任岗 后点「校验」：重复任职校验会落库留痕</div>
          </div>
        </div>
      </a-col>

      <a-col :span="12">
        <div class="ct-panel mb-3">
          <div class="ct-panel-t">③ 人员风险（12 个月岗位变更）</div>
          <div class="ct-panel-b">
            <a-space style="width: 100%" wrap>
              <!-- 员工检索：姓名/工号/证书名/证书编号 四类关键词都能命中（走自定义 filterPersonOption） -->
              <a-select
                v-model:value="personId"
                :options="personOptions"
                :loading="personOptionsLoading"
                :filter-option="filterPersonOption"
                show-search
                placeholder="按 姓名 / 工号 / 证书名 / 证书编号 搜索员工"
                allow-clear
                style="width: 320px"
                @change="onPickPerson"
              />
              <!-- 证书维度直达：同一人多张证就多条，选中即带出持证人（选项 value 就是 personId） -->
              <a-select
                v-model:value="personId"
                :options="personCertOptions"
                :loading="certOptionsLoading"
                show-search
                option-filter-prop="label"
                placeholder="或按证书定位员工（证书名 / 证书编号）"
                allow-clear
                style="width: 320px"
                @change="onPickPerson"
              />
              <a-button
                v-if="can('bidding:personnel:profile:query')"
                type="primary"
                :loading="personLoading"
                @click="doPersonRisk"
              >
                查询
              </a-button>
            </a-space>
            <div v-if="selectedPersonCerts.length > 0" class="ct-panel-meta mt-2">
              该员工在册证书：
              <a-tag v-for="c in selectedPersonCerts" :key="c.id">{{ certLabel(c) }}</a-tag>
            </div>
            <div v-if="personIndexNote" class="ct-panel-meta mt-2">{{ personIndexNote }}</div>
            <div v-if="personRisk" class="mt-3">
              <a-descriptions :column="1" size="small" bordered>
                <a-descriptions-item label="风险标签">
                  <a-tag
                    v-if="personRisk.personRiskFlag"
                    :color="dictOptions('person_risk_flag').length > 0 ? dictTag('person_risk_flag', personRisk.personRiskFlag).color : 'default'"
                  >
                    {{ dictOptions('person_risk_flag').length > 0 ? dictLabel('person_risk_flag', personRisk.personRiskFlag) : personRisk.personRiskFlag }}
                  </a-tag>
                  <span v-else>未评估</span>
                </a-descriptions-item>
                <a-descriptions-item label="窗口内变更次数">{{ personRisk.jobChangeCount12m ?? '—' }}</a-descriptions-item>
                <a-descriptions-item label="窗口起始">{{ fmtDateTime(personRisk.jobChangeWindowStart) }}</a-descriptions-item>
                <a-descriptions-item label="是否异常">
                  <a-tag :color="personRisk.abnormal ? 'error' : 'success'">{{ personRisk.abnormal ? '异常' : '正常' }}</a-tag>
                </a-descriptions-item>
                <a-descriptions-item label="评估时间">{{ fmtDateTime(personRisk.personRiskEvaluatedAt) }}</a-descriptions-item>
              </a-descriptions>
            </div>
            <div v-else class="ct-empty">按 姓名/工号/证书 检索并选择员工后点「查询」：给出 12 个月岗位变更次数与风险标签（未评估 ≠ 正常）</div>
          </div>
        </div>
      </a-col>
    </a-row>
  </div>
</template>
