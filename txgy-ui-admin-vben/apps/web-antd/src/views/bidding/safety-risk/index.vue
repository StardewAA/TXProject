<script setup lang="ts">
/* 安许风险联动（菜单终态 60005500 / component bidding/safety-risk/index / componentName SafetyRiskIndex
 *  页面权限 bidding:qualification:permit:query；写动作需 bidding:qualification:permit:update）
 *
 * 四块能力（对应 SafetyPermitRiskController 的 7 个端点）：
 *   ① 安许 A/B/C 风险卡片——直接复用证照域组件 certificate/modules/safety-risk.vue
 *      （内含 GET /risk、dryRun 试算、POST /risk/recheck 重算、POST /grace 宽限登记、GET /risk/history 快照哈希链抽屉）
 *   ② 多岗兼任校验   POST /bidding/safety-permit/personnel/multi-post-check（权限 bidding:personnel:cert:query）
 *   ③ 人员风险       GET  /bidding/safety-permit/personnel/risk（权限 bidding:personnel:profile:query）
 *   ④ 项目关键岗位变更 GET /bidding/safety-permit/project/key-post-changes（权限 bidding:project:query）
 *   ⑤ 规则配置（阈值）RuleConfigController：GET get / POST save / POST rollback / GET history / GET audit/page
 *      （权限 bidding:rule-config:query|update）——④③②①里用到的阈值（safety.abc.*、变更窗口、检查提前量）都在这里改，
 *      页面上展示的生效值与允许边界全部来自接口，前端不内联任何阈值。
 *
 * 口径：岗位码表取自 KeyPostEnum（后端校验只认这些 code，自由文本会被判非法）；
 *   人员/安许风险标签走字典 person_risk_flag / safety_risk_flag（已注册）；
 *   时间字段 LocalDateTime → epoch 毫秒，统一过 fmtDateTime；未评估（null）一律显示「未评估」而非当成正常。 */
import { onMounted, reactive, ref } from 'vue';

import { useAccess } from '@vben/access';
import { useAccessStore } from '@vben/stores';

import { message } from 'ant-design-vue';

import { getPersonnelProfilePage, type PersonnelProfileApi } from '#/api/bidding/personnel';
import {
  assignKeyPost,
  checkMultiPost,
  getPersonnelRisk,
  getProjectKeyPostChanges,
  type SafetyPermitApi,
} from '#/api/bidding/safetyPermit';

import { fmtDateTime } from '../certificate/format';
import SafetyRisk from '../certificate/modules/safety-risk.vue';
import { useCertMeta } from '../certificate/useCertMeta';
import RuleConfigPanel from './modules/RuleConfigPanel.vue';

import '../certificate/cert.css';

const { hasAccessByCodes } = useAccess();
const accessStore = useAccessStore();
const { dictLabel, dictOptions, dictTag } = useCertMeta();

function can(code: string): boolean {
  const codes = accessStore.accessCodes ?? [];
  if (codes.length === 0) return true;
  return codes.includes('*') || codes.includes('*:*:*') || hasAccessByCodes([code]);
}

/** 关键岗位码表（逐字取自 KeyPostEnum；后端 isValid 只认这些 code，故不做自由文本） */
const KEY_POST_OPTIONS = [
  { label: '项目经理（PROJECT_MANAGER）', value: 'PROJECT_MANAGER' },
  { label: '技术负责人（TECH_LEADER）', value: 'TECH_LEADER' },
  { label: '专职安全员（SAFETY_OFFICER）', value: 'SAFETY_OFFICER' },
  { label: '质量负责人（QUALITY_OFFICER）', value: 'QUALITY_OFFICER' },
  { label: '施工员（CONSTRUCTION_ENGINEER）', value: 'CONSTRUCTION_ENGINEER' },
];

const entId = ref<number | undefined>(undefined);

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
    message.warning('人员 id、主岗、兼任岗均为必填');
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

/* ===== ③ 人员风险 ===== */
const personId = ref<number | undefined>(undefined);
const personLoading = ref(false);
const personRisk = ref<null | SafetyPermitApi.PersonRisk>(null);

async function doPersonRisk() {
  if (!personId.value) {
    message.warning('请填写人员 id');
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

/* ===== ④ 项目关键岗位变更 ===== */
const projectId = ref<number | undefined>(undefined);
const windowDays = ref<number | undefined>(undefined);
const keyPostLoading = ref(false);
const keyPost = ref<null | SafetyPermitApi.KeyPostChanges>(null);

async function doKeyPost() {
  if (!projectId.value) {
    message.warning('请填写项目 id');
    return;
  }
  keyPostLoading.value = true;
  keyPost.value = null;
  try {
    keyPost.value = await getProjectKeyPostChanges(projectId.value, windowDays.value);
  } catch {
    // 静默
  } finally {
    keyPostLoading.value = false;
  }
}

/* ===== ④b 关键岗位任职 / 变更登记 =====
 * 口径：岗位码表逐字取自 KeyPostEnum（KEY_POST_OPTIONS，不做自由文本）；
 * 任职开始日按契约 'YYYY-MM-DD'，留空由后端取今天；变更原因为留痕字段（申诉时说明用），可空。
 * 后端会自动闭合上任任期并回传 changeSeq（该岗位第几次变更），据此在提示里给出可核对的结论。 */
const assignOpen = ref(false);
const assignSaving = ref(false);
const assignPersons = ref<Array<{ label: string; value: number }>>([]);
const assignPersonLoading = ref(false);
const assignForm = reactive<SafetyPermitApi.KeyPostAssignmentReq>({
  projectId: 0,
  postCode: '',
  personId: 0,
  startAt: '',
  changeReason: '',
});

async function loadAssignPersons(keyword?: string) {
  assignPersonLoading.value = true;
  try {
    const page = await getPersonnelProfilePage({ pageNo: 1, pageSize: 20, personName: keyword ?? '' });
    assignPersons.value = (page.list ?? [])
      .map((p: PersonnelProfileApi.Profile) => ({
        // idCard 只写不读（列表回传 idCardMasked），故用姓名 + 工号做标识
        label: `${p.personName ?? '—'}${p.personNo ? `（${p.personNo}）` : ''}`,
        value: Number(p.id ?? 0),
      }))
      .filter((o) => o.value > 0);
  } catch {
    // 静默：档案列表不可用时仍可手输人员 id
  } finally {
    assignPersonLoading.value = false;
  }
}

function openAssign() {
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
    message.warning('请先填写项目 id（④ 区的项目 id 会自动带入）');
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
    message.success(
      `已登记 ${resp.postLabel ?? assignForm.postCode}${resp.changeSeq ? `（该岗位第 ${resp.changeSeq} 次变更）` : ''}`,
    );
    assignOpen.value = false;
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
  { title: '涉及人员', dataIndex: 'personIds', key: 'personIds' },
];

onMounted(() => {
  /* 首屏不自动请求：entId 由使用者填写（与证照域其它页一致，避免无谓 403） */
});
</script>

<template>
  <div class="ct-page">
    <div class="ct-head">
      <div>
        <div class="ct-title">安许风险联动</div>
        <div class="ct-sub">A/B/C 类人员配备风险 · 重算与宽限 · 多岗兼任 · 人员风险 · 项目关键岗位变更</div>
      </div>
      <div class="ct-actions">
        <a-input-number v-model:value="entId" :min="1" placeholder="企业主体 id（entId）" style="width: 200px" />
        <span class="ct-panel-meta">填入后下方风险卡片自动加载</span>
      </div>
    </div>

    <a-alert type="info" show-icon class="mb-3">
      <template #message>风险结论由后端按规则配置计算（safety.abc.*）</template>
      <template #description>
        <div>
          A/B/C 人数与阈值取自 <code>/bidding/meta/configs</code> 的 <code>safety.abc.required</code> 等键；
          未评估（riskFlag=null）表示尚未跑过评估，<strong>不等于正常</strong>。宽限期原因须在
          <code>safety.abc.grace.reason.whitelist</code> 内、天数不得超配置上限。
        </div>
        <div style="margin-top: 6px">
          多岗兼任校验会落库留痕；人员风险（12 个月岗位变更）与项目关键岗位变更仅提示、不自动拒绝。
        </div>
      </template>
    </a-alert>

    <!-- ① 风险卡片（复用证照域组件：读取/试算/重算/宽限/快照哈希链） -->
    <div class="ct-panel mb-3">
      <div class="ct-panel-t">① 安许 A/B/C 风险</div>
      <div class="ct-panel-b">
        <SafetyRisk :ent-id="entId ?? null" />
      </div>
    </div>

    <a-row :gutter="16">
      <a-col :span="12">
        <div class="ct-panel mb-3">
          <div class="ct-panel-t">② 多岗兼任校验</div>
          <div class="ct-panel-b">
            <a-space direction="vertical" style="width: 100%">
              <a-input-number v-model:value="multiForm.personId" :min="1" placeholder="人员 id" style="width: 100%" />
              <a-select
                v-model:value="multiForm.primaryPost"
                placeholder="主岗"
                allow-clear
                :options="KEY_POST_OPTIONS"
                style="width: 100%"
              />
              <a-select
                v-model:value="multiForm.secondaryPost"
                placeholder="兼任岗"
                allow-clear
                :options="KEY_POST_OPTIONS"
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
          </div>
        </div>
      </a-col>

      <a-col :span="12">
        <div class="ct-panel mb-3">
          <div class="ct-panel-t">③ 人员风险（12 个月岗位变更）</div>
          <div class="ct-panel-b">
            <a-space style="width: 100%">
              <a-input-number v-model:value="personId" :min="1" placeholder="人员 id" style="width: 180px" />
              <a-button
                v-if="can('bidding:personnel:profile:query')"
                type="primary"
                :loading="personLoading"
                @click="doPersonRisk"
              >
                查询
              </a-button>
            </a-space>
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
          </div>
        </div>
      </a-col>
    </a-row>

    <div class="ct-panel">
      <div class="ct-panel-t">④ 项目关键岗位变更监控</div>
      <div class="ct-filter">
        <a-input-number v-model:value="projectId" :min="1" placeholder="项目 id" style="width: 180px" />
        <a-input-number v-model:value="windowDays" :min="1" placeholder="窗口天数（空=取配置）" style="width: 200px" />
        <a-button v-if="can('bidding:project:query')" type="primary" :loading="keyPostLoading" @click="doKeyPost">
          查询
        </a-button>
        <a-button
          v-if="can('bidding:project:update')"
          :disabled="!projectId"
          @click="openAssign"
        >
          登记任职 / 变更
        </a-button>
        <span class="ct-panel-meta">阈值缺省取配置 project.key.post.change.window.days</span>
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
            :scroll="{ x: 1000 }"
          >
            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'exceeded'">
                <a-tag :color="record.exceeded ? 'error' : 'success'">{{ record.exceeded ? '超限' : '正常' }}</a-tag>
              </template>
              <template v-else-if="column.key === 'lastChangeDate'">{{ fmtDateTime(record.lastChangeDate) }}</template>
              <template v-else-if="column.key === 'personIds'">
                {{ (record.personIds ?? []).join('、') || '—' }}
              </template>
            </template>
            <template #emptyText><div class="ct-empty">窗口内无关键岗位变更</div></template>
          </a-table>
        </template>
        <div v-else class="ct-empty">输入项目 id 后查询：逐岗位给出窗口内变更次数、阈值与是否超限</div>
      </div>
    </div>

    <!-- ④b 登记关键岗位任职/变更（bidding:project:update） -->
    <a-modal
      v-model:open="assignOpen"
      title="登记关键岗位任职 / 变更"
      :confirm-loading="assignSaving"
      ok-text="提交登记"
      @ok="submitAssign"
    >
      <a-alert type="info" show-icon class="mb-3">
        <template #message>提交后后端自动闭合该岗位上任任期</template>
        <template #description>
          <div>
            达变更阈值会当场提示挂靠嫌疑，待办由后端 Job 产出；岗位码表逐字取自 KeyPostEnum，不做自由文本。
          </div>
        </template>
      </a-alert>
      <a-form layout="vertical">
        <a-form-item label="项目 id" required>
          <a-input-number v-model:value="assignForm.projectId" :min="1" style="width: 100%" />
          <div class="ct-panel-meta">默认带入 ④ 区已填的项目 id</div>
        </a-form-item>
        <a-form-item label="关键岗位" required>
          <a-select
            v-model:value="assignForm.postCode"
            :options="KEY_POST_OPTIONS"
            placeholder="选择关键岗位（KeyPostEnum）"
            show-search
            option-filter-prop="label"
          />
        </a-form-item>
        <a-form-item label="任职人员" required>
          <a-select
            v-model:value="assignForm.personId"
            :options="assignPersons"
            :loading="assignPersonLoading"
            :filter-option="false"
            placeholder="按姓名搜索员工档案"
            show-search
            @search="loadAssignPersons"
          />
        </a-form-item>
        <a-form-item label="任职开始日">
          <a-date-picker
            v-model:value="assignForm.startAt"
            value-format="YYYY-MM-DD"
            style="width: 100%"
            placeholder="留空 = 今天"
          />
        </a-form-item>
        <a-form-item label="变更原因">
          <a-textarea
            v-model:value="assignForm.changeReason"
            :rows="2"
            :maxlength="256"
            show-count
            placeholder="如：原项目经理离职，由注册建造师接任（留痕，申诉时说明用）"
          />
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- ⑤ 规则配置：阈值生效值 / 发布与灰度 / 版本历史与回滚 / 读取审计（bidding:rule-config:*） -->
    <div class="ct-panel">
      <div class="ct-panel-t">⑤ 规则配置（阈值 · 版本 · 读取审计）</div>
      <div class="ct-panel-b">
        <RuleConfigPanel />
      </div>
    </div>
  </div>
</template>
