<script setup lang="ts">
/* 招投标业务链工作台（前端设计稿，后端接口未就绪）
 * 用户口径的顺序：先「项目」→ 再「招标项目」→ 再「标段」→（招标文件/控制价文件）→ 对「标段」发「公告/公示」。
 * 严格按 VO 规范：各级表单字段全部来自 voSpec.gen.ts 自动生成的规范字段，含中文名、英文名、类型、必填、公开范围。
 * 分区口径：企业填报 = 规范字段 - 平台注入字段（平台码/时间戳/统一交易标识码/附件关联标识号）；
 *   政务公开只是"披露范围"（只报政务平台、不对外公开），照常由企业填写并标注，不隐藏。
 * 附件：每个环节都可挂多个附件（AttachmentItemVO 采集项分组 + AttachmentSetVO 结构），保存时并入 ATTACHMENT_CODE。
 * 数据：本地草稿（localStorage），字段名与结构与后端接口一致，接口就绪后替换读写实现即可。 */
import { computed, reactive, ref, watch } from 'vue';
import { useRouter } from 'vue-router';

import { Button, message, Select, Tag } from 'ant-design-vue';

import VoStageForm, { type VoGroup } from '../vo/VoStageForm.vue';
import {
  bidSectionTitle,
  loadDraft,
  newRecord,
  projectTitle,
  saveDraft,
  tenderProjectTitle,
  type ChainDraft,
  type ChainRecord,
} from '../vo/voDraft';

const router = useRouter();

type StageKey = 'bidSections' | 'controlPriceDocs' | 'projects' | 'tenderDocs' | 'tenderProjects';

const draft = reactive<ChainDraft>(loadDraft());
const step = ref(1);
const selected = reactive<Record<StageKey, string>>({
  projects: '',
  tenderProjects: '',
  bidSections: '',
  tenderDocs: '',
  controlPriceDocs: '',
});

/* ---------- 各环节 VO 分组（codes 全部取自规范字段英文名，含规范内既有拼写） ---------- */
const projectGroups: VoGroup[] = [
  { title: '一、项目标识', hint: 'ProjectVO 序号 1~6（项目编号/名称/区域/地址）', codes: ['PROJECT_CODE', 'PROJECT_NAME', 'REGION_CODE', 'ADDRESS'] },
  { title: '二、项目概况与规模', hint: '序号 8、11：行业分类（GB/T 4754-2017）、项目规模', codes: ['PROJECT_SCALE', 'INDUSTRIES_TYPE'] },
  { title: '三、资金来源', hint: '序号 4、5、9、10：是否固定资产 / 投资项目统一代码 / 资金来源 / 出资比例', codes: ['RELATE_CODE', 'INVEST_PROJECT_CODE', 'FUND_SOURCE', 'CONTRIBUTION_SCALE'] },
  { title: '四、项目审批', hint: '序号 12~14：审批文件名称/文号/单位（核准、备案部门）', codes: ['APPROVAL_NAME', 'APPROVAL_NUMBER', 'APPROVAL_AUTHORITY'] },
  { title: '五、项目法人与联系人', hint: '序号 7、36、37', codes: ['LEGAL_PERSON', 'CONTACTOR', 'CONTACT_INFORMATION'] },
  { title: '六、建立时间', hint: '序号 15：项目创建时间', codes: ['CREATE_TIME'] },
];
const tenderProjectGroups: VoGroup[] = [
  { title: '一、归属与标识', hint: 'TenderProjectVO 序号 1~6（先有项目，再有招标项目）', codes: ['PROJECT_CODE', 'TENDER_PROJECT_CODE', 'TENDER_PROJECT_NAME', 'TENDER_PROJECT_TYPE', 'REGION_CODE'] },
  { title: '二、招标内容与范围及招标方案说明', hint: '序号 7（自由文本 C..ul）', codes: ['TENDER_CONTENT'] },
  { title: '三、招标人', hint: '序号 8~10：名称/类别/代码（多个以半角分号隔开）', codes: ['TENDERER_NAME', 'TENDERER_CODE_TYPE', 'TENDERER_CODE'] },
  { title: '四、招标代理机构', hint: '序号 11~13：非自行招标时必填', codes: ['TENDER_AGENCY_NAME', 'TENDER_AGENCY_CODE_TYPE', 'TENDER_AGENCY_CODE'] },
  { title: '五、招标方式与组织', hint: '序号 14~16：招标方式/资格审查方式/招标组织形式', codes: ['TENDER_MODE', 'QULIFICATION_CHECK_MODE', 'TENDER_ORGANIZE_FORM'] },
  { title: '六、监督与审核', hint: '序号 18、21~24：监督部门、业主、审核部门', codes: ['SUPERVISE_DEPT_NAME', 'SUPERVISE_DEPT_CODE', 'OWNER_NAME', 'APPROVE_DEPT_NAME', 'APPROVE_DEPT_CODE'] },
  { title: '七、建立时间', hint: '序号 17：招标项目建立时间（完成入场登记的时间）', codes: ['CREATE_TIME'] },
];
const bidSectionGroups: VoGroup[] = [
  { title: '一、归属与标识', hint: 'BidSectionVO 序号 1~3、6：招标项目编号/标段编号/名称/招标次数', codes: ['TENDER_PROJECT_CODE', 'BID_SECTION_CODE', 'BID_SECTION_NAME', 'BID_ECTION_NUM'] },
  { title: '二、标段内容', hint: '序号 10（自由文本 C..ul）', codes: ['BID_SECTION_CONTENT'] },
  { title: '三、招标方式与区域', hint: '序号 7、8、22：招标方式/所在行政区划/标段分类代码', codes: ['BID_TENDER_MODE', 'REGION_CODE', 'TENDER_PROJECT_CLASSIFY_CODE'] },
  { title: '四、合同估算价与工期', hint: '序号 12~14、17：估算价（元）/币种/单位/预计工期', codes: ['CONTRACT_RECKON_PRICE', 'CURRENCY_CODE', 'PRICE_UNIT', 'TIME_LIMIT'] },
  { title: '五、投标人资格条件', hint: '序号 23（自由文本 C..ul）', codes: ['BID_QUALIFICATION'] },
  { title: '六、建立时间', hint: '序号 15：标段（包）建立时间', codes: ['CREATE_TIME'] },
];
const tenderDocGroups: VoGroup[] = [
  { title: '一、归属与标识', hint: 'TenderDocVO 序号 1、2、24、4、5：招标项目编号/相关标段/文件编号/文件名称', codes: ['TENDER_PROJECT_CODE', 'BID_SECTION_CODES', 'BID_SECTION_CODE', 'FILENO', 'DOC_NAME'] },
  { title: '二、投标有效期与投标保证金', hint: '序号 6~13：有效期、是否缴纳保证金（必填）、缴纳方式、价款形式与保证金金额/币种/单位/比率', codes: ['VALID_PERIOD', 'IS_MARGIN', 'MARGIN_PAY_TYPE', 'PRICE_FORM_CODE', 'MARGIN_AMOUNT', 'MARGIN_CURRENCY', 'MARGIN_AMOUNT_UNIT', 'MARGIN_PERCENT'] },
  { title: '三、评标办法', hint: '序号 14（自由文本 C..ul）', codes: ['EVALUATING_METHOD'] },
  { title: '四、招标文件类型与答疑澄清', hint: '序号 15~20、28：文件类型（必填）、是否澄清、澄清与修改主要内容、澄清次数、答疑澄清时间、是否变更开标时间及变更后时间', codes: ['DOC_TYPE', 'IS_CLARIFY', 'CLARIFY_CONTENT', 'CLARIFY_NUM', 'CLARIFY_TIME', 'IS_CHANGE_BID_OPENING_DATE', 'NEW_BID_OPENING_DATE'] },
  { title: '五、控制价（招标文件内含时）', hint: '序号 25~27：控制价（最高限价）及币种/单位', codes: ['CONTROL_PRICE', 'CONTROL_PRICE_CURRENCY', 'CONTROL_PRICE_UNIT'] },
  { title: '六、招标文件生成时间', hint: '序号 29', codes: ['SUBMIT_TIME'] },
];
const controlPriceGroups: VoGroup[] = [
  { title: '一、归属与标识', hint: 'ControlPriceDocVO 序号 1、3：相关标段（半角分号分隔）/招标项目编号', codes: ['TENDER_PROJECT_CODE', 'BID_SECTION_CODES'] },
  { title: '二、招标控制价', hint: '序号 4、5：控制价（具体金额时必填）、控制价价款形式', codes: ['TENDER_SUM_LIMIT', 'PRICE FOFM_CODE'] },
  { title: '三、备注说明', hint: '序号 6（C..2000）', codes: ['REMARK'] },
];

interface StageDef {
  key: StageKey;
  vo: string;
  stage: string;
  title: string;
  groups: VoGroup[];
  label: string;
  sub: string;
  /** 上级归属字段（从选中上级带入，避免手抄编号）：[本环节字段, 上级列表, 上级字段] */
  parent?: { self: string; from: StageKey; parentField: string };
}
const STAGES: StageDef[] = [
  { key: 'projects', vo: 'ProjectVO', stage: 'PROJECT', title: '项目', groups: projectGroups, label: '项目', sub: '招标投标法 第9条：需要履行审批手续的应先审批。项目是整条链的根。' },
  { key: 'tenderProjects', vo: 'TenderProjectVO', stage: 'TENDER_PROJECT', title: '招标项目', groups: tenderProjectGroups, label: '招标项目', sub: '一个项目可对应多个招标项目（TenderProjectVO.PROJECT_CODE 关联项目）。', parent: { self: 'PROJECT_CODE', from: 'projects', parentField: 'PROJECT_CODE' } },
  { key: 'bidSections', vo: 'BidSectionVO', stage: 'BID_SECTION', title: '标段（包）', groups: bidSectionGroups, label: '标段（包）', sub: '一个招标项目可拆多个标段；公告与招标文件都对"标段"发布（BID_SECTION_CODES 由此而来）。', parent: { self: 'TENDER_PROJECT_CODE', from: 'tenderProjects', parentField: 'TENDER_PROJECT_CODE' } },
  { key: 'tenderDocs', vo: 'TenderDocVO', stage: 'TENDER_DOC', title: '招标文件 / 答疑澄清', groups: tenderDocGroups, label: '招标文件/澄清', sub: '招标文件（含答疑澄清）数据集 TenderDocVO；相关标段可多个（半角分号分隔），附件可挂多个（招标文件正本/附件/澄清文件）。' },
  { key: 'controlPriceDocs', vo: 'ControlPriceDocVO', stage: 'CONTROL_PRICE_DOC', title: '招标控制价文件（如有）', groups: controlPriceGroups, label: '控制价文件', sub: '招标控制价文件数据集 ControlPriceDocVO（如有）；控制价按标段挂接，附件可多个。' },
];

const current = computed<StageDef>(() => STAGES[step.value - 1]!);
const currentList = computed<ChainRecord[]>(() => draft[current.value.key]);
const currentRecord = computed<ChainRecord | undefined>(() => currentList.value.find((r) => r.id === selected[current.value.key]));

function titleOf(stage: StageKey, r: ChainRecord): string {
  if (stage === 'projects') return projectTitle(r);
  if (stage === 'tenderProjects') return tenderProjectTitle(r);
  if (stage === 'bidSections') return bidSectionTitle(r);
  if (stage === 'tenderDocs') return r.fields.DOC_NAME || r.fields.FILENO || '（未命名招标文件）';
  return r.fields.TENDER_SUM_LIMIT ? '控制价 ' + r.fields.TENDER_SUM_LIMIT : '（未命名控制价文件）';
}
/** 新增记录：按下级环节从选中的上级带入归属编号（可多标段时用半角分号拼接） */
function addRecord(def: StageDef): void {
  const seed: Record<string, string> = {};
  const link = def.parent;
  if (link) {
    const parent = draft[link.from].find((r) => r.id === selected[link.from]);
    if (parent) seed[link.self] = parent.fields[link.parentField] ?? '';
  }
  // 招标文件与控制价文件按"标段"层级发布：带入当前选中标段的编号（单/多标段）
  if (def.key === 'tenderDocs' || def.key === 'controlPriceDocs') {
    const sec = draft.bidSections.find((r) => r.id === selected.bidSections);
    if (sec) {
      const code = sec.fields.BID_SECTION_CODE ?? '';
      const tp = sec.fields.TENDER_PROJECT_CODE ?? '';
      if (code) {
        seed.BID_SECTION_CODES = code;
        if (def.key === 'tenderDocs') seed.BID_SECTION_CODE = code;
      }
      if (tp && !seed.TENDER_PROJECT_CODE) seed.TENDER_PROJECT_CODE = tp;
    }
  }
  const rec = newRecord(seed);
  draft[def.key].push(rec);
  selected[def.key] = rec.id;
}
function removeRecord(stage: StageKey, id: string): void {
  const i = draft[stage].findIndex((r) => r.id === id);
  if (i >= 0) draft[stage].splice(i, 1);
  if (selected[stage] === id) selected[stage] = draft[stage][0]?.id ?? '';
}
function updateFields(rec: ChainRecord, v: Record<string, string>): void {
  rec.fields = v;
  rec.updatedAt = Date.now();
}
/** 归属编号写入（含"相关标段"多选拼接） */
function setField(rec: ChainRecord, code: string, value: string): void {
  const next = { ...rec.fields };
  if (value === '') delete next[code];
  else next[code] = value;
  rec.fields = next;
  rec.updatedAt = Date.now();
}
function goStep(n: number): void {
  step.value = n;
  const st = STAGES[n - 1];
  if (st && !selected[st.key]) selected[st.key] = draft[st.key][0]?.id ?? '';
}
watch(
  () => draft,
  (d) => saveDraft(JSON.parse(JSON.stringify(d)) as ChainDraft),
  { deep: true },
);
(function init() {
  for (const s of STAGES) if (!selected[s.key]) selected[s.key] = draft[s.key][0]?.id ?? '';
  if (draft.tenderDocs.length) step.value = 4;
  else if (draft.bidSections.length) step.value = 3;
  else if (draft.tenderProjects.length) step.value = 2;
  else step.value = 1;
})();

const chainReady = computed(() => ({
  projects: draft.projects.length,
  tenderProjects: draft.tenderProjects.length,
  bidSections: draft.bidSections.length,
  tenderDocs: draft.tenderDocs.length,
  controlPriceDocs: draft.controlPriceDocs.length,
}));
const NOTICE_STEP = STAGES.length + 1;
function openNotice(): void {
  void router.push('/portal/bidding/publish');
}
function clearAll(): void {
  draft.projects = [];
  draft.tenderProjects = [];
  draft.bidSections = [];
  draft.tenderDocs = [];
  draft.controlPriceDocs = [];
  for (const s of STAGES) selected[s.key] = '';
  message.success('已清空本地草稿');
}
/** 上级候选项（用于归属选择器） */
const parentOptions = computed<ChainRecord[]>(() => {
  const p = current.value.parent;
  return p ? draft[p.from] : [];
});
</script>

<template>
  <div class="ch">
    <header class="ch-h">
      <div class="ch-h-l">
        <h1 class="ch-title">招投标业务链工作台
          <span class="ch-mark">VO 设计稿</span>
        </h1>
        <p class="ch-sub">
          按规范 VO 逐级建立：项目（ProjectVO）→ 招标项目（TenderProjectVO）→ 标段（包）（BidSectionVO）→
          招标文件/答疑澄清（TenderDocVO）· 招标控制价文件（ControlPriceDocVO）→ 公告/公示；
          每个环节都可添加多个附件（附件采集项 AttachmentItemVO / 附件集结构 AttachmentSetVO）。
          字段与分区严格照录 VO 规范：企业填报=规范字段−平台注入参数；政务公开仅为披露范围（只报政务、不对外公开），照常填写。
          后端业务接口未就绪：数据存本地草稿（字段名与结构与 VO/接口一致，接口就绪后直接对接）。
        </p>
      </div>
      <div class="ch-h-r">
        <Button size="small" @click="clearAll">清空草稿</Button>
      </div>
    </header>

    <!-- 链路进度 -->
    <div class="ch-steps">
      <div
        v-for="(s, i) in STAGES"
        :key="s.key"
        class="ch-step"
        :class="{ on: step === i + 1 }"
        role="button"
        tabindex="0"
        @click="goStep(i + 1)"
        @keydown.enter="goStep(i + 1)"
      >
        <span class="ch-step-no">{{ i + 1 }}</span>
        <span class="ch-step-t">{{ s.label }}</span>
        <span class="ch-step-c">{{ draft[s.key].length }} 条</span>
      </div>
      <div class="ch-step ch-step-4" :class="{ on: step === NOTICE_STEP }" role="button" tabindex="0" @click="step = NOTICE_STEP" @keydown.enter="step = NOTICE_STEP">
        <span class="ch-step-no">{{ NOTICE_STEP }}</span>
        <span class="ch-step-t">公告 / 公示</span>
        <span class="ch-step-c">对所选标段发布</span>
      </div>
    </div>

    <!-- 各环节表单 -->
    <div v-if="step <= STAGES.length" class="ch-body">
      <aside class="ch-list">
        <div class="ch-list-h">
          <span class="ch-list-t">{{ current.title }}列表</span>
          <Button size="small" type="primary" @click="addRecord(current)">新建</Button>
        </div>
        <div v-if="!currentList.length" class="ch-list-empty">暂无记录，点右上「新建」开始</div>
        <div
          v-for="r in currentList"
          :key="r.id"
          class="ch-item"
          :class="{ on: selected[current.key] === r.id }"
          role="button"
          tabindex="0"
          @click="selected[current.key] = r.id"
          @keydown.enter="selected[current.key] = r.id"
        >
          <div class="ch-item-t">{{ titleOf(current.key, r) }}</div>
          <div class="ch-item-m">
            <span>{{ current.vo }}</span>
            <span>附件 {{ r.attachments.length }}</span>
          </div>
          <Button size="small" type="link" danger @click.stop="removeRecord(current.key, r.id)">删除</Button>
        </div>
      </aside>

      <main class="ch-form">
        <div v-if="!currentRecord" class="ch-none">请选择或新建一条{{ current.title }}记录</div>
        <template v-else>
          <div class="ch-form-hint">{{ current.sub }}</div>

          <!-- 上级归属（按 VO 外键字段带值，避免用户手抄编号） -->
          <div v-if="current.parent && parentOptions.length" class="ch-link">
            <span class="ch-link-t">
              归属{{ current.parent.from === 'projects' ? '项目' : '招标项目' }}（写入 {{ current.parent.self }}）
            </span>
            <Select
              :value="currentRecord.fields[current.parent.self]"
              style="min-width: 320px"
              placeholder="选择已建上级记录"
              @update:value="(v) => setField(currentRecord!, current.parent!.self, String(v ?? ''))"
            >
              <Select.Option v-for="p in parentOptions" :key="p.id" :value="p.fields[current.parent.parentField] || ''">
                {{ titleOf(current.parent.from, p) }}
              </Select.Option>
            </Select>
          </div>

          <!-- 招标文件/控制价：按标段挂接（BID_SECTION_CODES 半角分号分隔） -->
          <div v-if="current.key === 'tenderDocs' || current.key === 'controlPriceDocs'" class="ch-link">
            <span class="ch-link-t">相关标段（BID_SECTION_CODES，多选，半角分号分隔）</span>
            <Select
              mode="multiple"
              :value="(currentRecord.fields.BID_SECTION_CODES || '').split(';').filter(Boolean)"
              style="min-width: 380px"
              placeholder="选择标段（可多选）"
              @update:value="(v) => setField(currentRecord!, 'BID_SECTION_CODES', ((v as string[]) ?? []).filter(Boolean).join(';'))"
            >
              <Select.Option v-for="s in draft.bidSections" :key="s.id" :value="s.fields.BID_SECTION_CODE || ''">
                {{ bidSectionTitle(s) }}
              </Select.Option>
            </Select>
          </div>

          <VoStageForm
            :vo="current.vo"
            :stage="current.stage"
            :title="current.title + '（' + current.vo + '）'"
            :groups="current.groups"
            :fields="currentRecord.fields"
            :attachments="currentRecord.attachments"
            :attachment-title="current.title + '附件（采集项见下，可加多个）'"
            @update:fields="(v) => updateFields(currentRecord!, v)"
            @update:attachments="(v) => { currentRecord!.attachments = v; currentRecord!.updatedAt = Date.now(); }"
          />
        </template>
      </main>
    </div>

    <!-- 公告/公示 -->
    <div v-else class="ch-notice">
      <div class="ch-notice-card">
        <h3 class="ch-notice-t">最后一步 · 对所选标段发布公告 / 公示</h3>
        <p class="ch-notice-p">
          公告数据集的 <code>TENDER_PROJECT_CODE</code>（招标项目编号）、<code>BID_SECTION_CODES</code>（相关标段（包）编号）、
          <code>NOTICE_BID_NUM</code>（公告标段（包）数量）应从上面已建的招标项目与标段带出，不由用户手填。
          公告编辑器已按类型拆为四张专属表（招标公告 / 变更公告 / 中标候选人公示 / 中标结果公示），并支持公告附件（可多个）。
        </p>
        <div class="ch-notice-stat">
          <Tag color="blue">项目 {{ chainReady.projects }}</Tag>
          <Tag color="blue">招标项目 {{ chainReady.tenderProjects }}</Tag>
          <Tag color="blue">标段 {{ chainReady.bidSections }}</Tag>
          <Tag color="blue">招标文件 {{ chainReady.tenderDocs }}</Tag>
          <Tag color="blue">控制价文件 {{ chainReady.controlPriceDocs }}</Tag>
          <Tag :color="chainReady.bidSections ? 'green' : 'orange'">{{ chainReady.bidSections ? '已具备发公告条件' : '请先建立标段（公告对进入交易环节的标段发布）' }}</Tag>
        </div>
        <div class="ch-notice-act">
          <Button type="primary" @click="openNotice">打开公告发布页</Button>
          <Button @click="goStep(3)">回到标段</Button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.ch { padding: 16px 18px 24px; }
.ch-h { display: flex; align-items: flex-start; justify-content: space-between; gap: 14px; flex-wrap: wrap; margin-bottom: 14px; }
.ch-title { font-size: 21px; font-weight: 800; color: #232b36; display: flex; align-items: center; gap: 10px; margin: 0; }
.ch-mark { font-size: 11.5px; font-weight: 700; color: #7a4a00; background: #ffe9bf; border: 1px solid #f0cf90; border-radius: 2px; padding: 1px 7px; letter-spacing: 0.5px; }
.ch-sub { margin: 7px 0 0; font-size: 12.5px; line-height: 1.7; color: #5b6672; max-width: 1080px; }
.ch-steps { display: flex; gap: 8px; flex-wrap: wrap; margin-bottom: 14px; }
.ch-step {
  flex: 1 1 150px;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 9px 12px;
  background: #fff;
  border: 1px solid #d9dfe5;
  border-left: 4px solid #c7ced6;
  cursor: pointer;
  user-select: none;
}
.ch-step.on { border-left-color: #e8871e; background: #fffaf2; }
.ch-step-no { width: 20px; height: 20px; line-height: 19px; text-align: center; font-size: 12px; font-weight: 700; color: #fff; background: #8a93a0; border-radius: 2px; }
.ch-step.on .ch-step-no { background: #e8871e; }
.ch-step-t { font-size: 13.5px; font-weight: 700; color: #2e3947; }
.ch-step-c { margin-left: auto; font-size: 12px; color: #8a949e; }
.ch-body { display: grid; grid-template-columns: 290px minmax(0, 1fr); gap: 14px; align-items: start; }
.ch-list { background: #fff; border: 1px solid #d9dfe5; }
.ch-list-h { display: flex; align-items: center; justify-content: space-between; gap: 8px; padding: 9px 11px; background: #f7f9fb; border-bottom: 1px solid #d9dfe5; }
.ch-list-t { font-size: 13.5px; font-weight: 700; color: #2e3947; }
.ch-list-empty { padding: 12px 11px; font-size: 12.5px; color: #9aa4af; line-height: 1.7; }
.ch-item { padding: 9px 11px; border-bottom: 1px solid #eef1f4; cursor: pointer; position: relative; }
.ch-item.on { background: #fffaf2; box-shadow: inset 3px 0 0 #e8871e; }
.ch-item-t { font-size: 13px; font-weight: 600; color: #2e3947; padding-right: 44px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.ch-item-m { margin-top: 3px; display: flex; gap: 10px; font-size: 11.5px; color: #8a949e; }
.ch-item :deep(.ant-btn) { position: absolute; top: 16px; right: 4px; padding: 0 4px; }
.ch-form { display: flex; flex-direction: column; gap: 10px; min-width: 0; }
.ch-form-hint { font-size: 12.5px; color: #5b6672; line-height: 1.7; background: #f7f9fb; border: 1px solid #e4e8ec; padding: 8px 11px; }
.ch-none { padding: 40px 0; text-align: center; font-size: 13px; color: #9aa4af; background: #fff; border: 1px dashed #d9dfe5; }
.ch-link { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; background: #fff; border: 1px solid #e4e8ec; padding: 9px 11px; }
.ch-link-t { font-size: 12.5px; font-weight: 700; color: #2e3947; }
.ch-notice-card { background: #fff; border: 1px solid #d9dfe5; padding: 16px 18px; max-width: 900px; }
.ch-notice-t { margin: 0 0 8px; font-size: 16px; font-weight: 800; color: #232b36; }
.ch-notice-p { margin: 0 0 12px; font-size: 13px; line-height: 1.85; color: #4b5563; }
.ch-notice-p code { background: #f6f8fa; border: 1px solid #e3e8ed; border-radius: 2px; padding: 0 4px; font-size: 12px; }
.ch-notice-stat { display: flex; gap: 6px; flex-wrap: wrap; margin-bottom: 12px; }
.ch-notice-act { display: flex; gap: 8px; }
</style>
