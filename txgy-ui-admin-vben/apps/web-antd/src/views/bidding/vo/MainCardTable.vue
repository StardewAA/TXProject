<script setup lang="ts">
/* 实体主档「结构化分组表格 + 行内编辑」共用组件（项目 / 招标项目 / 标段（包）三处复用）
 * 分层语义：
 *   · 字段标签严格照录 VO 规范"字段名称"列（VO_SPEC 为唯一真源，含括号/空格/拼写瑕疵）；
 *   · 代码值字段转中文（codeTables.ts，未知代码原样展示）；金额千分位 + 单位"元"；CREATE_TIME 过 fmtDateTime；
 *   · 空值显示 —。
 * 可编辑 / 不可编辑严格区分（三重防护）：
 *   ① 渲染层 —— 不可编辑字段编辑态不渲染输入框，只读展示 + `<span class="readonly-tag">系统生成</span>` 灰化(#999) + Tooltip"系统生成，不可修改"；
 *   ② 数据层 —— editValues 只装载 editableFields，readonly 字段根本不进入编辑值；
 *   ③ 提交层 —— onSave 只取「发生变更的可编辑字段」，并对 readonlyFields 做 FORBIDDEN 硬性剔除。
 * 保存：仅提交变更的可编辑字段（assembleSave 组装），成功后 emit('saved') 由父组件刷新主档并退出编辑态。
 * 字段校验与连续录入（2026-09-18 增）：
 *   · 校验规则唯一真源 = voFieldRules.ts（按 VO 规范 dtype 推导）；
 *   · 即时校验：字段失焦即判，输错立即标红 + 给原因；失焦过的字段此后边输边复核；
 *     保存再全量判一次，任一字段不合格一律不允许保存（汇总提示 + 标红 + 聚焦首个错误）；
 *   · 比例字段失焦时把输入统一显示成百分比形式（0.5 → 50%）；提交仍是数值 50；
 *   · 编辑态只校验/提交「发生变更」的字段——存量不合规数据不阻断无关编辑；
 *   · 回车 = 顺序跳到下一个输入框（长文本域保留换行，末行不跳）；
 *   · 上下键 = 在纯文本输入框之间上下移动（长文本域、下拉框的上下键归控件本身）。 */
import { computed, ref, watch } from 'vue';

import { message } from 'ant-design-vue';

import { fmtDateTime } from '../certificate/format';
import { CODE_TABLES, codeLabel, codeOptions } from './codeTables';
import { assembleSave, type EntityDef, recordToFields } from './entityForms';
import { fieldHint, isRatioField, isRequiredField, normalizeFieldValue, ratioDisplayValue, validateField } from './voFieldRules';
import { voField, VO_SPEC } from './voSpec.gen';

export type MasterType = 'bidSection' | 'project' | 'tenderProject';

/** 不可编辑字段悬停提示（统一口径，用户 2026-09-16 定稿） */
const READONLY_HINT = '系统生成，不可修改';

/** 长文本整行字段（按实体） */
const LONG_BY_TYPE: Record<MasterType, ReadonlySet<string>> = {
  project: new Set<string>(['PROJECT_SCALE', 'FUND_SOURCE', 'OTHER_INSTRUCTIONS']),
  tenderProject: new Set<string>(['TENDER_CONTENT']),
  bidSection: new Set<string>(['BID_SECTION_CONTENT', 'BID_QUALIFICATION', 'REMARK']),
};

/** 金额字段（千分位 + 单位"元"） */
const AMOUNT_BY_TYPE: Record<MasterType, ReadonlySet<string>> = {
  project: new Set<string>(['INVEST_RECKON', 'OTHER_OWNED_FUND', 'PRIVATE_FUND', 'FOREIGN_FUND', 'FOREIGN_PRIVATE_FUND']),
  tenderProject: new Set<string>([]),
  bidSection: new Set<string>(['CONTRACT_RECKON_PRICE']),
};

const props = withDefaults(
  defineProps<{
    title: string;
    type: MasterType;
    def: EntityDef;
    record: null | Record<string, unknown>;
    groups: Array<{ title: string; codes: string[] }>;
    /** 可编辑规范码 */
    editableFields: string[];
    /** 不可编辑规范码（系统生成） */
    readonlyFields: string[];
    /** 只读态右上按钮文案 */
    editLabel?: string;
    save: (data: Record<string, unknown>) => Promise<unknown>;
    /** 新建态（内存草稿）：进页即进入编辑、编号/建立时间显示「保存后自动生成」、保存为创建 */
    createMode?: boolean;
    /** 新建态恢复用户已填字段（来自草稿 fields） */
    seedFields?: Record<string, string>;
  }>(),
  { record: null, editLabel: '编辑', createMode: false, seedFields: () => ({}) },
);
const emit = defineEmits<{
  (e: 'saved', id?: unknown): void;
  (e: 'update:edit-values', v: Record<string, string>): void;
}>();

const editing = ref(false);
const saving = ref(false);
const editValues = ref<Record<string, string>>({});
/** 字段级错误（规范码 → 中文错误文案）：校验未通过的字段标红 + 明示原因 */
const errors = ref<Record<string, string>>({});
/** 已「离开过」（失焦）的字段：之后边输边复核；没离开过的字段不在输入过程中打断（定长字段输到一半不该标红） */
const touched = new Set<string>();
/** 主档面板根节点（回车跳转 / 错误定位用 querySelector 取控件） */
const panelRef = ref<HTMLElement | null>(null);

/** 不可编辑字段的系统标签/提示（新建态 =「保存后自动生成」，正常态 =「系统生成」） */
const sysTag = computed(() => (props.createMode ? '保存后自动生成' : '系统生成'));
const sysTagTip = computed(() => (props.createMode ? '保存后由系统自动生成编号，无需填写' : READONLY_HINT));

const editableSet = computed(() => new Set(props.editableFields));
const readonlySet = computed(() => new Set(props.readonlyFields));
const longSet = computed(() => LONG_BY_TYPE[props.type]);
const amountSet = computed(() => AMOUNT_BY_TYPE[props.type]);

/** 输入框占位提示：下拉框提示选择；其余按「格式要求」提示（推导自 voFieldRules.fieldHint，与校验同源），
 *  无格式要求回退「请输入」；金额字段补单位（AMOUNT_BY_TYPE 皆为元）。 */
function placeholderOf(code: string): string {
  if (CODE_TABLES[code]) return '请选择';
  const f = voField(props.def.vo, code);
  if (!f) return '请输入';
  const hint = fieldHint(f);
  if (!hint) return '请输入';
  return amountSet.value.has(code) ? `${hint}（单位：元）` : hint;
}

/** 主档全量字段（规范码 → 原始值）；CREATE_TIME 用主列 record.createTime（epoch 毫秒）兜底，避免误读 fields 里 yyyyMMddHHmmss 原始串 */
const base = computed<Record<string, string>>(() => {
  const fields = recordToFields(props.def, props.record);
  const t = props.record?.createTime;
  if (t !== undefined && t !== null && t !== '') fields.CREATE_TIME = String(t);
  return fields;
});

/** 金额：千分位 + 单位"元"；非数字原样 */
function fmtAmount(raw: string): string {
  const n = Number(raw);
  if (!Number.isFinite(n)) return raw || '—';
  return n.toLocaleString('zh-CN', { maximumFractionDigits: 2 }) + ' 元';
}

interface FieldView {
  code: string;
  label: string;
  raw: string;
  display: string;
  editable: boolean;
  readonly: boolean;
  isLong: boolean;
  isSelect: boolean;
}

/** 逐字段视图（展示值已做：代码转中文 / 金额格式化 / 时间格式化 / 空值 '—'） */
const fields = computed<FieldView[]>(() => {
  const vo = VO_SPEC[props.def.vo]?.fields ?? [];
  return props.groups.flatMap((g) =>
    g.codes.map((code) => {
      const vf = vo.find((f) => f.code === code);
      const label = vf?.label ?? code;
      const raw = base.value[code] ?? '';
      const editable = editableSet.value.has(code);
      const readonly = readonlySet.value.has(code);
      const isLong = longSet.value.has(code);
      const isSelect = !!CODE_TABLES[code];
      const isAmount = amountSet.value.has(code) && !!raw;
      let display: string;
      if (code === 'CREATE_TIME') display = fmtDateTime(raw as never);
      else if (isAmount) display = fmtAmount(raw);
      else if (isSelect && raw) display = codeLabel(code, raw);
      else display = raw === '' ? '—' : raw;
      return { code, label, raw, display, editable, readonly, isLong, isSelect };
    }),
  );
});

interface GroupView {
  title: string;
  rowFields: FieldView[];
  longField: FieldView | null;
}
const groups = computed<GroupView[]>(() =>
  props.groups.map((g) => {
    const f = fields.value.filter((x) => g.codes.includes(x.code));
    return { title: g.title, rowFields: f.filter((x) => !x.isLong), longField: f.find((x) => x.isLong) ?? null };
  }),
);

/** 是否发生了可编辑字段变更（trim 比较） */
const dirty = computed(() => {
  for (const code of props.editableFields) {
    if ((editValues.value[code] ?? '').trim() !== (base.value[code] ?? '').trim()) return true;
  }
  return false;
});

function startEdit(): void {
  // ② 数据层：只装载可编辑字段，readonly 字段不进 editValues
  const ev: Record<string, string> = {};
  for (const code of props.editableFields) {
    // 新建态以草稿已填字段为初值；正常态以记录当前值为初值
    const v = props.createMode ? (props.seedFields[code] ?? '') : (base.value[code] ?? '');
    ev[code] = v;
  }
  editValues.value = ev;
  errors.value = {};
  touched.clear();
  editing.value = true;
}
function cancel(): void {
  editing.value = false;
  editValues.value = {};
  errors.value = {};
  touched.clear();
}

/** 新建态：record 就绪后自动进入编辑态（record 由父组件传入，非空） */
watch(
  () => [props.createMode, props.record, props.seedFields],
  () => {
    if (props.createMode && props.record && !editing.value) startEdit();
  },
  { immediate: true, deep: true },
);

/** 编辑值变化实时外发（父组件在新建态据此同步草稿 fields，返回再进字段还在）；
 *  已失焦过的字段同时边输边复核：改对立即撒红、改坏立即标红。 */
watch(
  editValues,
  () => {
    emit('update:edit-values', editValues.value);
    if (touched.size === 0) return;
    for (const code of touched) recheck(code);
  },
  { deep: true },
);

/** 失焦即时处理：① 比例字段把输入统一显示成百分比形式；② 校验本字段并标红/撒红
 *  （focusout 会冒泡，故在面板上统一委派；按回车跳格同样会失焦，连录时逐格生效） */
function onPanelFocusOut(e: FocusEvent): void {
  const holder = (e.target as HTMLElement | null)?.closest?.('[data-code]') as HTMLElement | null;
  const code = holder?.dataset.code;
  if (!code) return;
  touched.add(code);
  // ① 比例显示归一：仅对「本次改动过」的字段回写，避免把未改动的存量值也改写（会凭空产生一次提交）
  const f = voField(props.def.vo, code);
  if (f && isRatioField(f) && scopedCodes.value.includes(code)) {
    const cur = (editValues.value[code] ?? '').trim();
    const shown = ratioDisplayValue(f, cur);
    if (shown !== cur) editValues.value[code] = shown;
  }
  // ② 校验（在显示归一之后判，保证标红与用户看到的值一致）
  recheck(code);
}

/* ============================================================
 * 键盘连续录入（回车跳行 / 上下键上下跳行）
 *   · 顺序 = 渲染顺序（分组 → 行内字段），长文本整行字段不在其中；
 *   · 回车：跳到下一行对应输入框（按顺序含下拉框；下拉展开时让给 antd 选值）；
 *   · 上下键：只在纯文本输入框之间上下移动 —— 长文本域要用它移动光标、下拉框要用它选值，
 *     这两类控件都不参与跳行（用户 2026-09-18 定稿）。
 * ============================================================ */
/** 回车跳行顺序：全部可编辑行内字段（含下拉框），与 DOM 顺序一致 */
const jumpOrder = computed<string[]>(() =>
  groups.value.flatMap((g) => g.rowFields.filter((f) => f.editable).map((f) => f.code)),
);
/** 上下键跳行顺序：仅纯文本输入框（排除下拉框，让上下键仍可用于选值） */
const arrowOrder = computed<string[]>(() =>
  groups.value.flatMap((g) => g.rowFields.filter((f) => f.editable && !f.isSelect).map((f) => f.code)),
);

/** 取某规范码对应的可聚焦控件：输入框/文本域取自身，下拉框取内部输入框 */
function controlOf(code: string): HTMLElement | null {
  const root = panelRef.value?.querySelector<HTMLElement>(`[data-code="${code}"]`);
  if (!root) return null;
  if (root.tagName === 'INPUT' || root.tagName === 'TEXTAREA') return root;
  return root.querySelector<HTMLElement>('input') ?? null;
}

/** 聚焦并滚动到某字段（错误定位 / 回车跳转共用） */
function focusField(code: string): void {
  const el = controlOf(code);
  if (!el) return;
  el.scrollIntoView({ block: 'center', behavior: 'smooth' });
  el.focus();
}

/** 跳到下一个可输入框；末行不跳（不循环） */
function focusNextControl(code: string): void {
  const i = jumpOrder.value.indexOf(code);
  if (i < 0 || i >= jumpOrder.value.length - 1) return;
  focusField(jumpOrder.value[i + 1]!);
}

/** 面板内键盘委派：回车 → 下一行；上下键 → 上下移动（跳行顺序见 jumpOrder / arrowOrder） */
function onPanelKeydown(e: KeyboardEvent): void {
  if (e.isComposing) return; // 中文输入法组合中，回车用于上屏
  const isEnter = e.key === 'Enter';
  const isArrow = e.key === 'ArrowDown' || e.key === 'ArrowUp';
  if (!isEnter && !isArrow) return;
  const holder = (e.target as HTMLElement | null)?.closest?.('[data-code]') as HTMLElement | null;
  const code = holder?.dataset.code;
  if (!holder || !code) return;
  if (holder.tagName === 'TEXTAREA') return; // 长文本域：回车=换行、上下键=文本内移动光标
  // 下拉框：data-code 在外层 div，需向下找 .ant-select
  const sel = holder.querySelector('.ant-select') ?? holder.closest('.ant-select');
  const selOpen = !!sel?.classList.contains('ant-select-open');

  if (isArrow) {
    if (sel) return; // 下拉框：上下键用于打开/选值，不参与跳行
    const i = arrowOrder.value.indexOf(code);
    if (i < 0) return;
    const next = e.key === 'ArrowDown' ? i + 1 : i - 1;
    if (next < 0 || next >= arrowOrder.value.length) return; // 到顶/到底不循环
    e.preventDefault();
    focusField(arrowOrder.value[next]!);
    return;
  }

  if (selOpen) return; // 下拉展开：回车让给 antd 确认选项
  e.preventDefault();
  focusNextControl(code);
}

/* ============================================================
 * 字段校验（业务规则唯一真源 voFieldRules.ts）
 *   · 即时校验：字段失焦即判，输错立刻标红 + 给原因；失焦过的字段此后边输边复核（touched 集合）——
 *     定长字段（如 6 位行政区域代码）输到一半不标红，避免打字过程满屏红；
 *   · 保存校验：点保存再全量判一次，不合格一律不允许保存（汇总提示 + 标红 + 聚焦首个错误）；
 *   · 新建态：校验全部可编辑字段的「格式」（有值才判）；
 *   · 草稿宽松（2026-09-27 契约29 §3.4）：新建态即草稿，空值不判必填 —— 除系统生成编号外全空可落库，
 *     必填唯一强制点移到状态流转事件（提交审批/发布）；编辑态才按必填口径拦空值；
 *   · 编辑态：只校验「发生变更」的字段 —— 未改动的存量值不参与，避免历史不合规数据阻断无关编辑；
 *   · 下拉框字段跳过长度/格式校验（取值由选项集约束，且规范 dtype 与选项集存在不一致）。
 * ============================================================ */
/** 本次保存的字段范围：新建态=全部可编辑字段；编辑态=仅发生变更的字段。
 *  变更判定统一按「原始输入 trim 比较」（与 dirty / 校验口径一致）；提交值再单独做归一化——
 *  否则未改动的存量值（如比例存着 "50%"）会被归一化后误判成变更、被悄悄重写。 */
const scopedCodes = computed<string[]>(() =>
  props.createMode
    ? props.editableFields
    : props.editableFields.filter((c) => (editValues.value[c] ?? '').trim() !== (base.value[c] ?? '').trim()),
);

/** 单字段校验（即时校验与保存校验同源，避免两套口径跑偏）：返回错误文案或 null */
function issueOf(code: string): null | string {
  // 编辑态只判「本次发生变更」的字段，与保存口径一致——
  // 否则未改动的存量不合规值会在失焦时标红，却又能正常保存，出现「标红却保存成功」的错位。
  if (!props.createMode && !scopedCodes.value.includes(code)) return null;
  const f = voField(props.def.vo, code);
  const val = (editValues.value[code] ?? '').trim();
  if (!val) {
    // [草稿宽松 2026-09-27 契约29 §3.4] 新建态（createMode）即草稿：空值不判必填（后端同步放宽，
    //   新建即落库空白草稿、仅系统生成编号；必填唯一强制点 = 状态流转事件）。编辑态仍按必填口径拦截。
    const required = !props.createMode && isRequiredField(props.def.vo, code) && editableSet.value.has(code);
    return required ? '必填，不能为空' : null;
  }
  // 下拉框字段取值由选项集约束；规范无此字段时无可判规则
  if (!f || CODE_TABLES[code]) return null;
  return validateField(f, val);
}

/** 即时复核单个字段：设置或清除其标红 */
function recheck(code: string): void {
  const msg = issueOf(code);
  const next = { ...errors.value };
  if (msg) next[code] = msg;
  else delete next[code];
  errors.value = next;
}

/** 保存前全量校验：只判本次保存范围内的字段 */
function collectIssues(): Array<{ code: string; label: string; msg: string }> {
  const issues: Array<{ code: string; label: string; msg: string }> = [];
  for (const code of scopedCodes.value) {
    const msg = issueOf(code);
    if (msg) issues.push({ code, label: voField(props.def.vo, code)?.label ?? code, msg });
  }
  return issues;
}

async function onSave(): Promise<void> {
  // 正常态无变更 → 直接退出；新建态即使表单全空也要过校验
  if (!dirty.value && !props.createMode) {
    cancel();
    return;
  }
  // ① 校验：任一字段不符合字段要求 → 不允许保存，标红 + 明示原因 + 聚焦首个错误
  const issues = collectIssues();
  if (issues.length) {
    const map: Record<string, string> = {};
    for (const i of issues) map[i.code] = i.msg;
    errors.value = map;
    const head = issues.slice(0, 4).map((i) => `${i.label}：${i.msg}`).join('；');
    message.error(`字段校验未通过（共 ${issues.length} 项）：${head}${issues.length > 4 ? ' 等' : ''}`);
    focusField(issues[0]!.code);
    return;
  }
  errors.value = {};

  saving.value = true;
  try {
    // 只取本次范围内的可编辑字段；提交值取归一化结果（比例统一换算为百分数值）
    const changed: Record<string, string> = {};
    for (const code of scopedCodes.value) {
      changed[code] = normalizeFieldValue(voField(props.def.vo, code), editValues.value[code] ?? '');
    }
    // ③ 提交层：FORBIDDEN 硬性剔除（readonly 字段即使被注入也一律拦截；新建态编号留空 → 后端自动生成）
    for (const code of props.readonlyFields) delete changed[code];
    // ④ 新建态：补上级归属编号。它是页内不可编辑的「上下文」字段（不在 editableFields 里），
    //    但后端生成层级编号时必须拿到它——招标项目 20 位 = 父项目 17 位 + 3 位；标段 23 位 = 父招标项目 20 位 + 3 位。
    //    取自 record（新建页由路由 query 带入，经 recordToFields 落在 base）；缺失时后端报
    //    「须挂接所属项目（projectCode）后才能生成编号」。
    //    ⚠️ 刻意放在③的 readonly 剔除「之后」：标段的 TENDER_PROJECT_CODE 同时登记在 readonlyFields 里
    //    （它被当作"系统生成"灰显，其实是上级归属编号），但创建标段时后端 `BidSectionSaveReqVO.tenderProjectCode`
    //    是 @NotBlank 必填。故这一处必须补回，不是漏删——改前先看《后端变更请求-编号体系归位》§2.1/2.2。
    if (props.createMode && props.def.parent) {
      const parentCode = (base.value[props.def.parent.self] ?? '').trim();
      if (parentCode) changed[props.def.parent.self] = parentCode;
    }
    const data = assembleSave(props.def, changed, base.value['ATTACHMENT_CODE'] ?? '', props.record?.id as number | string | undefined);
    const res = await props.save(data);
    emit('saved', res);
    editing.value = false;
    editValues.value = {};
    errors.value = {};
    message.success('保存成功');
  } catch {
    // 失败原因由请求层统一 toast 后端 msg（本页不再重复提示，避免「保存失败：[object Object]」这类噪音）；
    // 保持编辑态、保留已填值，便于用户改后重试。
  } finally {
    saving.value = false;
  }
}
</script>

<template>
  <div ref="panelRef" class="ct-panel mb-3" @focusout="onPanelFocusOut" @keydown="onPanelKeydown">
    <div class="mct-head">
      <span class="mct-title">{{ title }}</span>
      <a-space size="small">
        <template v-if="!editing">
          <a-button size="small" type="primary" @click="startEdit">{{ editLabel }}</a-button>
        </template>
        <template v-else>
          <a-button size="small" type="primary" :loading="saving" @click="onSave">保存</a-button>
          <a-button size="small" @click="cancel">取消</a-button>
        </template>
      </a-space>
    </div>

    <template v-if="record">
      <template v-if="groups.length">
        <div v-for="g in groups" :key="g.title" class="mct-group">
          <div class="mct-group-title">{{ g.title }}</div>
          <table class="mct-tbl">
            <tbody>
              <!-- 长文本整行：标签一行 + 值跨两列 -->
              <template v-if="g.longField">
                <tr>
                  <td colspan="2" class="mct-k">
                    <span class="mct-lbl">{{ g.longField.label }}</span>
                    <a-tooltip v-if="g.longField.readonly" :title="sysTagTip" placement="top">
                      <span class="readonly-tag">{{ sysTag }}</span>
                    </a-tooltip>
                  </td>
                </tr>
                <tr>
                  <td colspan="2" class="mct-v">
                    <template v-if="editing && g.longField.editable">
                      <a-textarea
                        v-model:value="editValues[g.longField.code]"
                        :data-code="g.longField.code"
                        :status="errors[g.longField.code] ? 'error' : ''"
                        :rows="4"
                        :placeholder="placeholderOf(g.longField.code)"
                      />
                      <div v-if="errors[g.longField.code]" class="mct-err">{{ errors[g.longField.code] }}</div>
                    </template>
                    <span v-else class="mct-long" :class="{ 'mct-ro': g.longField.readonly }">{{ g.longField.display }}</span>
                  </td>
                </tr>
              </template>
              <tr v-for="f in g.rowFields" :key="f.code">
                <td class="mct-k">
                  <span class="mct-lbl">{{ f.label }}</span>
                  <a-tooltip v-if="f.readonly" :title="sysTagTip" placement="top">
                    <span class="readonly-tag">{{ sysTag }}</span>
                  </a-tooltip>
                </td>
                <td class="mct-v">
                  <template v-if="editing && f.editable">
                    <!-- ① 渲染层：readonly 字段编辑态不渲染输入框，走下方只读 span -->
                    <!-- data-code 标在外层 div：回车跳转 / 错误定位按规范码取控件，不依赖组件对 $attrs 的转发行为 -->
                    <div :data-code="f.code">
                      <a-select
                        v-if="f.isSelect"
                        v-model:value="editValues[f.code]"
                        :options="codeOptions(f.code, editValues[f.code])"
                        :status="errors[f.code] ? 'error' : ''"
                        style="width: 100%"
                        :placeholder="placeholderOf(f.code)"
                      />
                      <a-input
                        v-else
                        v-model:value="editValues[f.code]"
                        :status="errors[f.code] ? 'error' : ''"
                        :placeholder="placeholderOf(f.code)"
                      />
                    </div>
                    <div v-if="errors[f.code]" class="mct-err">{{ errors[f.code] }}</div>
                  </template>
                  <span v-else :class="{ 'mct-ro': f.readonly, 'mct-empty': !f.readonly }">{{ f.display }}</span>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </template>
      <div v-else class="ct-empty">未取到主档字段</div>
    </template>
    <div v-else class="ct-empty">未取到主档数据</div>
  </div>
</template>

<style scoped>
.mct-head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 8px 16px;
  border-bottom: 1px solid #eef1f4;
}
.mct-title {
  font-size: 13.5px;
  font-weight: 600;
  color: hsl(var(--foreground));
}
.mct-group {
  padding: 4px 16px 8px;
}
.mct-group-title {
  margin: 10px 0 6px;
  font-size: 13.5px;
  font-weight: 700;
  color: hsl(var(--foreground));
}
.mct-tbl {
  width: 100%;
  border-collapse: collapse;
}
.mct-tbl td {
  padding: 7px 10px;
  line-height: 1.6;
  vertical-align: top;
  border: 1px solid hsl(var(--border));
  word-break: break-all;
}
.mct-k {
  width: 220px;
  min-width: 220px;
  font-size: 12.5px;
  color: hsl(var(--muted-foreground));
  background: hsl(var(--muted) / 45%);
}
.mct-lbl {
  color: hsl(var(--muted-foreground));
}
.mct-v {
  color: hsl(var(--foreground));
}
.mct-long {
  white-space: pre-wrap;
}
/* 不可编辑字段：灰化值 */
.mct-ro {
  color: #999;
}
.mct-empty {
  color: #bbb;
}
/* 字段校验未通过：明确原因（配合控件 status="error" 的红色边框） */
.mct-err {
  margin-top: 4px;
  font-size: 12px;
  line-height: 1.5;
  color: #c6362e;
}
/* 系统生成 灰色小标签 */
.readonly-tag {
  margin-left: 8px;
  padding: 0 5px;
  font-size: 11px;
  line-height: 17px;
  color: #999;
  background: #f2f4f7;
  border: 1px solid #e3e6eb;
  border-radius: 3px;
  cursor: help;
}
</style>
