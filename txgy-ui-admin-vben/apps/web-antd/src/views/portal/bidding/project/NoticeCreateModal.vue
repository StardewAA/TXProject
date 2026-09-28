<script setup lang="ts">
/* 前台 · 创建公告（挂在第三级标段下）
 * 流程：先选公告类型（4 选 1）→ 再按类型【动态加载】对应的专属表前端文件（分包懒加载，选哪个加载哪个）→ 填写并保存。
 * 四张表：招标公告 / 变更公告 / 中标候选人公示 / 中标结果公示（files: views/bidding/notice/tables/*.vue），
 *   每张表按 VO 规范数据集字段分组（必填优先），并配套该类型公告的附件采集项（AttachmentItemVO）。
 * 上下文：进入本页时已知 招标项目编号 + 标段（包）编号 → 自动写入 TENDER_PROJECT_CODE / BID_SECTION_CODES / NOTICE_BID_NUM。
 * 说明：发布前置状态限制按业务要求暂不处理（仅创建草稿）。 */
import { computed, defineAsyncComponent, ref, watch } from 'vue';

import { Button, Input, message, Modal, Radio, Tag } from 'ant-design-vue';

import { createNotice, fetchNoticeTypeMeta, type NoticeApi } from '#/api/bidding/notice';

import { buildCodeItems } from '../../../bidding/notice/noticeCodes';
import AttachmentsPanel from '../../../bidding/vo/AttachmentsPanel.vue';
import { attachmentCodeOf, type AttachmentRecord } from '../../../bidding/vo/attachmentStages';

const props = defineProps<{
  bidSectionCode: string;
  bidSectionName?: string;
  open: boolean;
  projectCode?: string;
  tenderProjectCode: string;
}>();
const emit = defineEmits<{
  (e: 'update:open', v: boolean): void;
  (e: 'created', id: number | string): void;
}>();

/* ===== 四张类型专属表：动态 import（每张表独立分包，选类型才拉取对应文件） ===== */
const TYPE_TABLES = {
  TENDER_NOTICE: defineAsyncComponent(() => import('../../../bidding/notice/tables/TenderNoticeTable.vue')),
  CHANGE_NOTICE: defineAsyncComponent(() => import('../../../bidding/notice/tables/ChangeNoticeTable.vue')),
  WIN_CANDIDATE_PUBLICITY: defineAsyncComponent(() => import('../../../bidding/notice/tables/CandidatePublicityTable.vue')),
  WIN_RESULT_PUBLICITY: defineAsyncComponent(() => import('../../../bidding/notice/tables/ResultPublicityTable.vue')),
} as const;

type TypeCode = keyof typeof TYPE_TABLES;

const TYPE_LIST: Array<{ code: TypeCode; dataset: string; desc: string; label: string; }> = [
  { code: 'TENDER_NOTICE', label: '招标公告', desc: '招标公告与资格预审公告（含获取招标文件办法、递交截止与开标安排）', dataset: 'TenderNoticeVO' },
  { code: 'CHANGE_NOTICE', label: '变更公告', desc: '对已发公告的澄清、修改与时间变更', dataset: 'TenderChangeNoticeVO' },
  { code: 'WIN_CANDIDATE_PUBLICITY', label: '中标候选人公示', desc: '中标候选人名单与排序、异议渠道与期限', dataset: 'WinCandidatePublicityVO' },
  { code: 'WIN_RESULT_PUBLICITY', label: '中标结果公示', desc: '中标人、中标价与履约信息', dataset: 'WinResultPublicityVO' },
];

/** 公告类型 → 附件环节（采集项随类型不同） */
const ATTACH_STAGE: Record<string, string> = {
  TENDER_NOTICE: 'NOTICE_TENDER',
  CHANGE_NOTICE: 'NOTICE_CHANGE',
  WIN_CANDIDATE_PUBLICITY: 'NOTICE_CANDIDATE',
  WIN_RESULT_PUBLICITY: 'NOTICE_RESULT',
};

const step = ref<1 | 2>(1);
const noticeType = ref<TypeCode>('TENDER_NOTICE');
const title = ref('');
const content = ref('');
const publicLevel = ref<'GOVERNMENT' | 'SOCIAL'>('SOCIAL');
const payloadValues = ref<Record<string, string>>({});
const attachments = ref<AttachmentRecord[]>([]);
const saving = ref(false);
const typeMeta = ref<NoticeApi.NoticeTypeMeta[]>([]);

const currentTable = computed(() => TYPE_TABLES[noticeType.value]);
const currentStage = computed(() => ATTACH_STAGE[noticeType.value] ?? 'NOTICE_TENDER');
const currentMeta = computed<NoticeApi.NoticeTypeMeta | null>(
  () => typeMeta.value.find((m) => m.type === noticeType.value) ?? null,
);
const typeLabel = computed(() => TYPE_LIST.find((t) => t.code === noticeType.value)?.label ?? noticeType.value);

function reset(): void {
  step.value = 1;
  noticeType.value = 'TENDER_NOTICE';
  title.value = '';
  content.value = '';
  publicLevel.value = 'SOCIAL';
  attachments.value = [];
  payloadValues.value = {
    TENDER_PROJECT_CODE: props.tenderProjectCode,
    BID_SECTION_CODES: props.bidSectionCode,
    NOTICE_BID_NUM: props.bidSectionCode ? '1' : '',
  };
}
/** 三级编号（项目/招标项目/标段）—— 即门户「我的关注」的三种跟踪键，保存后写进公告供跟踪扇出匹配。
 *  口径唯一定义在 views/bidding/notice/noticeCodes.ts，与本弹窗的编号带值同源。 */
const codes = computed(() =>
  buildCodeItems({
    payload: payloadValues.value,
    projectCode: props.projectCode,
  }),
);
watch(
  () => props.open,
  (v) => {
    if (v) {
      reset();
      void loadMeta();
    }
  },
  { immediate: true },
);
async function loadMeta(): Promise<void> {
  if (typeMeta.value.length) return;
  try {
    typeMeta.value = (await fetchNoticeTypeMeta()) ?? [];
  } catch {
    typeMeta.value = [];
  }
}
/** 选择类型：进入第二步并按类型重新带入上下文（切类型时清理该类型不适用字段由表自身控制） */
function chooseType(t: TypeCode): void {
  noticeType.value = t;
  step.value = 2;
}
function back(): void {
  step.value = 1;
}
async function submit(): Promise<void> {
  if (!title.value.trim()) {
    message.warning('请填写公告名称（标题）');
    return;
  }
  const nameKey = noticeType.value === 'WIN_CANDIDATE_PUBLICITY' ? 'PUBLICITY_NAME' : 'NOTICE_NAME';
  const contentKey = noticeType.value === 'WIN_CANDIDATE_PUBLICITY' ? 'PUBLICITY_CONTENT' : 'NOTICE_CONTENT';
  const payload: Record<string, unknown> = {};
  for (const [k, v] of Object.entries(payloadValues.value)) {
    if (v !== null && v !== undefined && String(v).trim() !== '') payload[k] = v;
  }
  payload[nameKey] = title.value.trim();
  if (content.value.trim()) payload[contentKey] = content.value.trim();
  if (attachments.value.length) payload.ATTACHMENT_CODE = attachmentCodeOf(attachments.value);

  saving.value = true;
  try {
    const id = await createNotice({
      noticeType: noticeType.value,
      title: title.value.trim(),
      content: content.value,
      publicLevel: publicLevel.value,
      /* 2026-09-23 修正三级编号错位：此处曾把**招标项目编号**写进公告根字段 projectCode，
         导致项目级跟踪键（真实项目编号）对不上、招标项目级/标段级关注者永不匹配——
         发布成功却无人收到动态。根字段只放真实项目编号（本页由父级路由传入）；
         招标项目编号留在数据集 TENDER_PROJECT_CODE、标段编号留在 BID_SECTION_CODES。 */
      projectCode: props.projectCode || undefined,
      payload,
    });
    message.success(typeLabel.value + '已创建（草稿）：' + title.value.trim());
    emit('created', id);
    emit('update:open', false);
  } catch (e) {
    message.error('创建失败：' + ((e as Error).message || String(e)));
  } finally {
    saving.value = false;
  }
}
function close(): void {
  emit('update:open', false);
}
</script>

<template>
  <Modal :open="open" :width="1180" :footer="null" :destroy-on-close="true" @cancel="close">
    <template #title>
      <span>创建公告 · 标段 {{ bidSectionName || bidSectionCode }}</span>
      <Tag v-if="step === 2" color="blue" style="margin-left: 8px">{{ typeLabel }}</Tag>
    </template>

    <!-- 第一步：选择公告类型（四选一） -->
    <div v-if="step === 1" class="nc">
      <div class="nc-tip">
        当前标段：<b>{{ bidSectionName || bidSectionCode }}</b>（招标项目编号由系统生成）。
        请选择要创建的公告类型，系统将按类型加载对应的专属字段表（四类数据集字段不同）。
      </div>
      <div class="nc-types">
        <div v-for="t in TYPE_LIST" :key="t.code" class="nc-type" role="button" tabindex="0" @click="chooseType(t.code as TypeCode)" @keydown.enter="chooseType(t.code as TypeCode)">
          <div class="nc-type-t">{{ t.label }}</div>
          <div class="nc-type-c">{{ t.code }}</div>
          <div class="nc-type-d">{{ t.desc }}</div>
          <div class="nc-type-v">数据集 {{ t.dataset }}</div>
        </div>
      </div>
    </div>

    <!-- 第二步：按类型动态加载专属表 -->
    <div v-else class="nc">
      <div class="nc-tip">
        类型：<b>{{ typeLabel }}</b>{{ currentMeta ? `（${ currentMeta.dataset }）` : '' }} ·
        归属：招标项目 {{ tenderProjectCode }} / 标段 {{ bidSectionCode }}（已自动写入数据集字段，无需手填编号）。
      </div>
      <!-- 三级编号：与门户「我的关注」的三级跟踪键一一对应（缺标段=标段级关注者收不到这条公告） -->
      <div class="nc-codes">
        <span v-for="c in codes" :key="c.key" class="nc-code-item"><b>{{ c.label }}</b><em>{{ c.value }}</em></span>
        <span v-if="!codes.length" class="nc-code-item nc-code-empty">未带出任何编号：保存后没有关注者能被通知</span>
      </div>

      <div class="nc-sec">
        <div class="nc-sec-h">
          <span class="nc-sec-t">一、标题与正文</span>
          <span class="nc-sec-hint">标题正文保存时并入数据集名称/正文键</span>
        </div>
        <div class="nc-grid">
          <div class="nc-cell nc-wide">
            <div class="nc-lb"><span class="nc-ast">*</span>公告名称（标题）</div>
            <Input v-model:value="title" placeholder="公告名称（标题）" />
          </div>
          <div class="nc-cell nc-wide">
            <div class="nc-lb">公告正文（全文）</div>
            <Input.TextArea v-model:value="content" :rows="4" placeholder="公告正文全文…" />
          </div>
          <div class="nc-cell">
            <div class="nc-lb">公开级别</div>
            <Radio.Group v-model:value="publicLevel">
              <Radio.Button value="SOCIAL">社会公开</Radio.Button>
              <Radio.Button value="GOVERNMENT">政务公开</Radio.Button>
            </Radio.Group>
          </div>
        </div>
      </div>

      <div class="nc-sec">
        <div class="nc-sec-h">
          <span class="nc-sec-t">二、{{ typeLabel }}专属字段（{{ currentTable ? '已按类型加载' : '加载中' }}）</span>
          <span class="nc-sec-hint">字段与分组照录 VO 规范；必填依据 meta/types 与规范"必填=是"</span>
        </div>
        <!-- 动态组件：按类型懒加载对应的表前端文件 -->
        <component :is="currentTable" v-model:values="payloadValues" :type-meta="currentMeta" />
      </div>

      <div class="nc-sec">
        <div class="nc-sec-h">
          <span class="nc-sec-t">三、公告附件</span>
          <span class="nc-sec-hint">按该类型公告的附件采集项（AttachmentItemVO）分组，可加多个</span>
        </div>
        <AttachmentsPanel v-model="attachments" :stage="currentStage" :title="`${typeLabel }附件`" directory="bidding/notice" />
      </div>

      <div class="nc-foot">
        <Button @click="back">上一步（换类型）</Button>
        <Button @click="close">取消</Button>
        <Button type="primary" :loading="saving" @click="submit">创建（草稿）</Button>
      </div>
    </div>
  </Modal>
</template>

<style scoped>
.nc { display: flex; flex-direction: column; gap: 12px; }
.nc-tip { font-size: 12.5px; line-height: 1.75; color: #5b6672; background: #f7f9fb; border: 1px solid #e4e8ec; padding: 8px 11px; }
/* 三级编号行（项目/招标项目/标段）：门户「我的关注」三种跟踪键，发布后按这三类编号扇出动态 */
.nc-codes { display: flex; flex-wrap: wrap; gap: 4px 16px; font-size: 12.5px; padding: 7px 11px; border: 1px dashed #b9cde4; background: #f6faff; }
.nc-code-item b { font-weight: 400; color: #7a8794; margin-right: 5px; }
.nc-code-item em { font-style: normal; font-family: ui-monospace, SFMono-Regular, Menlo, monospace; font-weight: 600; color: #24405c; }
.nc-code-empty { color: #a35a12; }
.nc-types { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px; }
.nc-type { border: 1px solid #d9dfe5; background: #fff; padding: 12px 14px; cursor: pointer; }
.nc-type:hover { border-color: #2b6cb0; background: #f9fbfd; }
.nc-type-t { font-size: 15px; font-weight: 700; color: #2e3947; }
.nc-type-c { font-size: 11px; color: #8a949e; margin-top: 2px; letter-spacing: .03em; }
.nc-type-d { font-size: 12.5px; color: #5b6672; line-height: 1.6; margin-top: 6px; }
.nc-type-v { font-size: 11.5px; color: #9aa4af; margin-top: 6px; }
.nc-sec { border: 1px solid #e4e8ec; background: #fff; padding: 11px 13px 13px; }
.nc-sec-h { display: flex; align-items: baseline; gap: 8px; flex-wrap: wrap; border-bottom: 1px solid #eef1f4; padding-bottom: 6px; margin-bottom: 10px; }
.nc-sec-t { font-size: 14px; font-weight: 700; color: #303a46; }
.nc-sec-hint { font-size: 12px; color: #8a949e; }
.nc-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px 18px; }
.nc-cell { min-width: 0; }
.nc-wide { grid-column: 1 / -1; }
.nc-lb { font-size: 13.5px; font-weight: 600; color: #2e3947; margin-bottom: 5px; }
.nc-ast { color: #d93025; font-weight: 700; margin-right: 3px; }
.nc-foot { display: flex; justify-content: flex-end; gap: 8px; padding-top: 6px; border-top: 1px solid #eef1f4; }
:deep(.ant-input), :deep(.ant-input-affix-wrapper), :deep(.ant-select-selector) { border: 1px solid #c9cfd6; border-radius: 3px; font-size: 13.5px; }
:deep(.ant-input:focus) { border-color: #2b6cb0; box-shadow: none; }
</style>
