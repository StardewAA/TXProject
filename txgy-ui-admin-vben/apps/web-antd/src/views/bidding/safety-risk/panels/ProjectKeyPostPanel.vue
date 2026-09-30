<script setup lang="ts">
/* 安许风险联动 · 项目侧面板（2026-09-30 按主体拆分）
 * 内容 = 原 safety-risk/index.vue 的项目侧区块，逐行搬迁：
 *   ④ 项目关键岗位变更监控（L1329-1399 + L325-367）、④b 登记任职/变更弹窗（L1401-1516 + L369-516），
 *   外加项目名称下拉（L425-445）与关键岗位列定义（L518-527）。
 * 归属口径：见 docs/模块设计与规范/电子证照域-三台账信息架构与界面重设计-2026-09-28.md（项目关键岗位 → 项目域）；
 *   本块用的权限码本来就是 bidding:project:query / bidding:project:update。
 *
 * 拆分前的「区块开关 + 嵌入模式」已删除；本面板不需要企业主体（拆分前只开 ④ 的宿主同样不取主体），
 * onMounted 只预加载项目下拉。页头（原 L933-995）不搬迁：它原本就是 v-if="!props.embedded"，宿主传 embedded，从未渲染。 */
import { onMounted, reactive, ref } from 'vue';

import { message } from 'ant-design-vue';

import { getPersonnelProfile } from '#/api/bidding/personnel';
import { getProjectPage } from '#/api/bidding/project';
import {
  assignKeyPost,
  getProjectKeyPostChanges,
  type SafetyPermitApi,
} from '#/api/bidding/safetyPermit';

import { fmtDateTime } from '../../certificate/format';
import AttrFilter from '../../components/AttrFilter.vue';
import { useAssignPersons, useKeyPostOptions, useSafetyCan } from '../shared';

import '../../certificate/cert.css';

const { can } = useSafetyCan();
/* 关键岗位码表：字典 key_post 优先 + KEY_POST_OPTIONS 兜底（原为页面级 computed） */
const keyPostOptions = useKeyPostOptions();
/* 任职人员下拉：原 loadAssignPersons 原样搬到 shared.ts（企业侧风险指派也用同一份） */
const { assignPersonLoading, assignPersons, loadAssignPersons } = useAssignPersons();

/* ===== ④ 项目关键岗位变更 ===== */
const projectId = ref<number | undefined>(undefined);
const windowDays = ref<number | undefined>(undefined);
const keyPostLoading = ref(false);
const keyPost = ref<null | SafetyPermitApi.KeyPostChanges>(null);

/* 涉及人员：后端只回 personIds（员工档案主键），处理超限时看 id 无意义；
 * 解析不到姓名（档案已删 / 无 bidding:personnel:profile:query 权限）时回落 #id，
 * 不显示空白——空白会让人误以为"没有涉及人员"。 */
const personNameMap = ref<Record<number, string>>({});
function personLabel(id: number) {
  return personNameMap.value[id] ?? `#${id}`;
}
async function resolvePersonNames(ids: number[]) {
  const pending = [...new Set(ids.filter((id) => id > 0))].filter((id) => !(id in personNameMap.value));
  if (pending.length === 0) {
    return;
  }
  const results = await Promise.allSettled(pending.map((id) => getPersonnelProfile(id)));
  const next = { ...personNameMap.value };
  results.forEach((r, i) => {
    const id = pending[i] as number;
    next[id] = r.status === 'fulfilled' ? (r.value?.personName ?? `#${id}`) : `#${id}`;
  });
  personNameMap.value = next;
}

async function doKeyPost() {
  if (!projectId.value) {
    message.warning('请选择项目');
    return;
  }
  keyPostLoading.value = true;
  keyPost.value = null;
  try {
    keyPost.value = await getProjectKeyPostChanges(projectId.value, windowDays.value);
    void resolvePersonNames((keyPost.value?.items ?? []).flatMap((i) => i.personIds ?? []));
  } catch {
    // 静默
  } finally {
    keyPostLoading.value = false;
  }
}

/* ===== ④b 关键岗位任职 / 变更登记 =====
 * 口径：岗位码表走字典 key_post（兜底 KEY_POST_OPTIONS，不做自由文本，后端 isValid 只认枚举 code）；
 * 任职开始日按契约 'YYYY-MM-DD'，留空由后端取今天；变更原因为留痕字段（申诉时说明用），可空。
 * 后端会自动闭合上任任期并回传 changeSeq（该岗位第几次变更），据此在提示里给出可核对的结论。 */
const assignOpen = ref(false);
const assignSaving = ref(false);

const assignForm = reactive<SafetyPermitApi.KeyPostAssignmentReq>({
  projectId: 0,
  postCode: '',
  personId: 0,
  startAt: '',
  changeReason: '',
});
/* 登记结果：exceeded=true（窗口内变更次数达阈值）时**不关闭弹窗**，就地换成风险提示。
 * 依据 KeyPostAssignmentRespVO 的 Javadoc："登记是关键岗位变更的唯一入口，前端需要在保存成功的同一屏
 * 立刻提示挂靠嫌疑；若让前端保存后再调一次统计接口，会出现保存成功但提示闪烁/失败的中间态。" */
const assignResult = ref<null | SafetyPermitApi.KeyPostAssignmentResp>(null);
/** 上一位任职人显示名（personLabel 解析，解析不到回落 #id） */
const assignPrevLabel = ref('');

function closeAssignResult() {
  assignResult.value = null;
  assignPrevLabel.value = '';
  assignOpen.value = false;
}

/** 弹窗确认键：结果态是"知道了"（关窗），表单态才是提交 */
function onAssignOk() {
  if (assignResult.value) {
    closeAssignResult();
    return;
  }
  void submitAssign();
}

/* 项目选择器：④ 区原先是手输项目 id（用户无从得知、只记得项目名称），
 * 改为按名称远程搜索的下拉，选中即回填 projectId。
 * 无 bidding:project:query 权限时列表为空（宁可不显示，也不让用户手填无从得知的 id）。 */
const projectOptions = ref<Array<{ label: string; value: number }>>([]);
const projectLoading = ref(false);

async function loadProjects(keyword?: string) {
  projectLoading.value = true;
  try {
    const page = await getProjectPage({ pageNo: 1, pageSize: 20, projectName: keyword ?? '' });
    projectOptions.value = (page.list ?? [])
      .map((p) => ({
        label: `${p.projectName ?? '—'}（${p.projectCode ?? '无编号'}）`,
        value: Number(p.id ?? 0),
      }))
      .filter((o) => o.value > 0);
  } catch {
    // 静默：无项目查询权限时列表为空
  } finally {
    projectLoading.value = false;
  }
}

/** 监控表里对某个超限岗位直接发起变更登记（省去手抄岗位 code 的环节） */
function openAssignForPost(postCode?: string) {
  openAssign();
  assignForm.postCode = postCode ?? '';
}

function openAssign() {
  assignResult.value = null;
  assignPrevLabel.value = '';
  assignForm.projectId = Number(projectId.value ?? 0);
  assignForm.postCode = '';
  assignForm.personId = 0;
  assignForm.startAt = '';
  assignForm.changeReason = '';
  assignOpen.value = true;
  void loadAssignPersons();
}

async function submitAssign() {
  if (!assignForm.projectId) {
    message.warning('请先选择项目（④ 区选择的项目会自动带入）');
    return;
  }
  if (!assignForm.postCode) {
    message.warning('请选择关键岗位');
    return;
  }
  if (!assignForm.personId) {
    message.warning('请选择任职人员');
    return;
  }
  assignSaving.value = true;
  try {
    const resp = await assignKeyPost({
      projectId: assignForm.projectId,
      postCode: assignForm.postCode,
      personId: assignForm.personId,
      startAt: assignForm.startAt ? assignForm.startAt : undefined,
      changeReason: assignForm.changeReason?.trim() ? assignForm.changeReason.trim() : undefined,
    });
    /* 原任职人只以 id 回传，这里解析成姓名，避免让使用者拿着 id 去猜"换掉的是谁" */
    const prevId = Number(resp.previousPersonId ?? 0);
    if (prevId > 0) {
      await resolvePersonNames([prevId]);
      assignPrevLabel.value = personLabel(prevId);
    } else {
      assignPrevLabel.value = '';
    }
    const seqText = resp.changeSeq ? `第 ${resp.changeSeq} 次变更` : '首次任职';
    if (resp.exceeded) {
      /* 达阈值：保留弹窗并展示风险明细（见 assignResult 注释），列表仍刷新以便与 ④ 区数字对上 */
      assignResult.value = resp;
      message.warning(
        `${resp.postLabel ?? assignForm.postCode} ${seqText}：${resp.windowDays} 天内已变更 ${resp.windowChangeCount} 次，达阈值 ${resp.threshold}`,
      );
    } else {
      message.success(
        `已登记 ${resp.postLabel ?? assignForm.postCode}（${seqText}）${
          assignPrevLabel.value ? `；原任职人 ${assignPrevLabel.value} 任期已闭合` : ''
        }`,
      );
      assignOpen.value = false;
    }
    if (Number(projectId.value) === assignForm.projectId) await doKeyPost();
  } catch {
    // 错误提示由请求层统一处理
  } finally {
    assignSaving.value = false;
  }
}

const keyPostColumns = [
  { title: '岗位', dataIndex: 'postLabel', key: 'postLabel', width: 140 },
  { title: '岗位 code', dataIndex: 'postCode', key: 'postCode', width: 190 },
  { title: '窗口内变更次数', dataIndex: 'changeCount', key: 'changeCount', width: 130 },
  { title: '阈值', dataIndex: 'threshold', key: 'threshold', width: 80 },
  { title: '是否超限', dataIndex: 'exceeded', key: 'exceeded', width: 100 },
  { title: '最近变更日期', dataIndex: 'lastChangeDate', key: 'lastChangeDate', width: 150 },
  { title: '涉及人员', dataIndex: 'personIds', key: 'personIds', width: 220, ellipsis: true },
  { title: '操作', fixed: 'right', key: 'action', width: 110 },
];

onMounted(() => {
  /* 项目下拉预加载：原 loadProjects 无人调用导致「或按项目名称搜索」下拉恒为空。
     本面板不取企业主体、不拉员工档案（与拆分前只开 ④ 的宿主一致）。 */
  void loadProjects();
});
</script>

<template>
  <div class="ct-page ct-embedded-root">
    <div class="ct-panel">
      <div class="ct-panel-t">④ 项目关键岗位变更监控</div>
      <div class="mb-3 rounded-md border border-border bg-card px-3.5 pt-1.5 pb-2.5">
        <AttrFilter label="项目">
          <a-select
            v-model:value="projectId"
            :options="projectOptions"
            :loading="projectLoading"
            show-search
            option-filter-prop="label"
            placeholder="按项目名称搜索选择"
            style="width: 280px"
            allow-clear
            @search="loadProjects"
          />
        </AttrFilter>
        <AttrFilter label="窗口天数（空=取配置）"><a-input-number v-model:value="windowDays" :min="1" placeholder="窗口天数（空=取配置）" style="width: 200px" /></AttrFilter>
        <a-button
          v-if="can('bidding:project:update')"
          :disabled="!projectId"
          @click="openAssign"
        >
          登记任职 / 变更
        </a-button>
        <span class="ct-panel-meta">阈值缺省取配置 project.key.post.change.window.days</span>
            <div class="mt-2 flex flex-wrap items-center gap-2"><a-button v-if="can('bidding:project:query')" type="primary" :loading="keyPostLoading" @click="doKeyPost"> 查询 </a-button></div>
          </div>
      <div class="ct-panel-b">
        <template v-if="keyPost">
          <div class="mb-2">
            <a-tag :color="keyPost.anyExceeded ? 'error' : 'success'">
              {{ keyPost.anyExceeded ? '存在超限岗位' : '未超限' }}
            </a-tag>
            <span class="ct-panel-meta" style="margin-left: 8px">
              窗口 {{ keyPost.windowDays ?? '—' }} 天，起始 {{ fmtDateTime(keyPost.windowStart) }}
            </span>
          </div>
          <a-table
            class="ct-table"
            :columns="keyPostColumns"
            :data-source="keyPost.items ?? []"
            :pagination="false"
            row-key="postCode"
            size="small"
            :scroll="{ x: 1120 }"
          >
            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'exceeded'">
                <a-tag :color="record.exceeded ? 'error' : 'success'">{{ record.exceeded ? '超限' : '正常' }}</a-tag>
              </template>
              <template v-else-if="column.key === 'lastChangeDate'">{{ fmtDateTime(record.lastChangeDate) }}</template>
              <template v-else-if="column.key === 'personIds'">
                {{ (record.personIds ?? []).map(personLabel).join('、') || '—' }}
              </template>
              <template v-else-if="column.key === 'action'">
                <a-button
                  v-if="can('bidding:project:update')"
                  type="link"
                  size="small"
                  @click="openAssignForPost(record.postCode)"
                >
                  登记变更
                </a-button>
              </template>
            </template>
            <template #emptyText><div class="ct-empty">窗口内无关键岗位变更：该窗口内暂无任职 / 变更记录，可「登记任职/变更」后刷新</div></template>
          </a-table>
        </template>
        <div v-else class="ct-empty">选择项目后查询：逐岗位给出窗口内变更次数、阈值与是否超限</div>
      </div>
    </div>

    <!-- ④b 登记关键岗位任职/变更（bidding:project:update） -->
    <a-modal
      v-model:open="assignOpen"
      :title="assignResult ? '登记成功 · 变更频次风险' : '登记关键岗位任职 / 变更'"
      :confirm-loading="assignSaving"
      :ok-text="assignResult ? '知道了' : '提交登记'"
      @ok="onAssignOk"
    >
      <!-- 达阈值：后端把判定所需数字（窗口天数 / 窗口内次数 / 阈值 / 原任职人）一次回传，
           就是为了在保存成功的**同一屏**给结论，而不是让前端保存后再查一次统计接口
           —— KeyPostAssignmentRespVO 的 Javadoc 明写此口径。此处即兑现弹窗里那句
           "达变更阈值会当场提示挂靠嫌疑"。 -->
      <a-alert v-if="assignResult" type="warning" show-icon class="mb-3">
        <template #message>
          {{ assignResult.postLabel ?? assignResult.postCode }}：{{ assignResult.windowDays }} 天内已变更
          {{ assignResult.windowChangeCount }} 次，达阈值 {{ assignResult.threshold }}（挂靠嫌疑）
        </template>
        <template #description>
          <div>
            项目 #{{ assignResult.projectId }} · 本次任职 #{{ assignResult.personId
            }}{{ assignResult.startAt ? ` 起自 ${assignResult.startAt}` : '（起自今天）' }} · 第
            {{ assignResult.changeSeq }} 次变更
          </div>
          <div v-if="assignResult.previousPersonId">
            上一位任职人 {{ assignPrevLabel }}，任期已闭合至 {{ assignResult.previousEndAt ?? '—' }}
          </div>
          <div>
            统计窗口：{{ assignResult.windowStart ?? '—' }} 起 {{ assignResult.windowDays ?? '—' }} 天
          </div>
          <div>此处仅提示、不产出待办——待办由后端 Job 统一产出，两处都产出会让同一嫌疑重复推送。</div>
        </template>
      </a-alert>
      <a-alert v-if="!assignResult" type="info" show-icon class="mb-3">
        <template #message>提交后后端自动闭合该岗位上任任期</template>
        <template #description>
          <div>
            达变更阈值会当场提示挂靠嫌疑，待办由后端 Job 产出；岗位码表逐字取自 KeyPostEnum，不做自由文本。
          </div>
        </template>
      </a-alert>
      <a-form layout="horizontal">
        <!-- ① 项目与岗位（编号类 + 枚举归组） -->
        <div class="ct-sheet">
          <div class="ct-sheet-group">任职对象<span class="ct-sg-note">项目自动带入 ④ 区选择</span></div>
          <div class="ct-sheet-row">
            <div class="ct-sheet-cell">
              <a-form-item label="项目" required>
                <a-input :value="assignForm.projectId ? `#${assignForm.projectId}` : '—'" disabled />
              </a-form-item>
            </div>
            <div class="ct-sheet-cell ct-sheet-wide">
              <a-form-item label="关键岗位" required>
                <a-select
                  v-model:value="assignForm.postCode"
                  :options="keyPostOptions"
                  placeholder="选择关键岗位"
                  show-search
                  option-filter-prop="label"
                  style="width: 100%"
                />
              </a-form-item>
            </div>
          </div>
        </div>

        <!-- ② 任职信息（人员 + 时间） -->
        <div class="ct-sheet">
          <div class="ct-sheet-group">任职信息<span class="ct-sg-note">人员 / 开始日</span></div>
          <div class="ct-sheet-row">
            <div class="ct-sheet-cell ct-sheet-wide">
              <a-form-item label="任职人员" required>
                <a-select
                  v-model:value="assignForm.personId"
                  :options="assignPersons"
                  :loading="assignPersonLoading"
                  :filter-option="false"
                  placeholder="按姓名搜索员工档案"
                  show-search
                  style="width: 100%"
                  @search="loadAssignPersons"
                />
              </a-form-item>
            </div>
            <div class="ct-sheet-cell">
              <a-form-item label="任职开始日">
                <a-date-picker
                  v-model:value="assignForm.startAt"
                  value-format="YYYY-MM-DD"
                  style="width: 100%"
                  placeholder="留空 = 今天"
                />
              </a-form-item>
            </div>
          </div>
        </div>

        <!-- ③ 变更原因（自由文本最后） -->
        <div class="ct-sheet" style="margin-top: 14px">
          <div class="ct-sheet-group">变更原因</div>
          <div class="ct-sheet-row">
            <div class="ct-sheet-cell ct-sheet-wide">
              <a-form-item label="变更原因">
                <a-textarea
                  v-model:value="assignForm.changeReason"
                  :rows="2"
                  :maxlength="256"
                  show-count
                  placeholder="如：原项目经理离职，由注册建造师接任（留痕，申诉时说明用）"
                />
              </a-form-item>
            </div>
          </div>
        </div>
      </a-form>
    </a-modal>
  </div>
</template>
