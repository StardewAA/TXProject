<script setup lang="ts">
/* 企业资质（菜单终态 60005200 / component bidding/qualification/index / componentName QualificationIndex
 *  权限：页面 bidding:qualification:query；本页已接动作 create / alert-run / material:query）
 *
 * 现状（2026-09 核查，含 qualification 包 13 个端点逐一核对）：
 *   后端资质包**只有写接口**，无 page/get/get-by-code（EnterpriseQualificationMapper 亦无 selectPage）
 *   → 无「资质台账」列表可渲染；延续申报(qualificationId+version)、审批(applyId+version)、
 *     要素录入(qualId)、安许建档 的入参只能来自列表/详情 → 本页**不提供**这些动作（硬填 ID 是反模式）。
 *   本页只做三件入参自洽或有真实数据的事：
 *     ① 材料完备性自查（GET /bidding/enterprise-material/completeness，字段逐字对齐 RespVO）
 *     ② 资质建档（POST /bidding/qualification/create）
 *     ③ 到期预警扫描（POST /bidding/qualification-alert-run/scan）
 *
 * 字典口径（契约 §0.5，禁止硬编码枚举）：material_result / material_status / material_category
 *   皆已注册进 bidding_dict_data，故标签与颜色一律走 useCertMeta().dictTag（缺字典时用契约兜底文案）；
 *   qual_serial 尚无字典，仅按 v3.3 表 15-1 DDL 注释给建议值下拉（页面内已标注）。 */
import { computed, reactive, ref } from 'vue';

import { useAccess } from '@vben/access';
import { useAccessStore } from '@vben/stores';

import { message } from 'ant-design-vue';

import { type CertificateApi, getMaterialCompleteness } from '#/api/bidding/certificate';
import {
  createQualification,
  QualificationApi,
  runQualificationAlertScan,
} from '#/api/bidding/qualification';

import { fmtDateTime } from '../certificate/format';
import { useCertMeta } from '../certificate/useCertMeta';

import '../certificate/cert.css';

const { hasAccessByCodes } = useAccess();
const accessStore = useAccessStore();
const { dictLabel, dictOptions, dictTag } = useCertMeta();

function can(code: string): boolean {
  const codes = accessStore.accessCodes ?? [];
  if (codes.length === 0) return true;
  return codes.includes('*') || codes.includes('*:*:*') || hasAccessByCodes([code]);
}

/* ===== ① 材料完备性自查 ===== */
const completenessQuery = reactive<{ asOf?: string; entId?: number }>({ entId: undefined, asOf: undefined });
const completeness = ref<CertificateApi.MaterialCompleteness | null>(null);
const completenessLoading = ref(false);
const completenessError = ref('');

/** 契约 §7.1 三值兜底：字典未装载时仍能显示中文结论 */
const RESULT_FALLBACK: Record<string, { color: string; label: string }> = {
  PASS: { color: 'green', label: '齐备' },
  RISK: { color: 'orange', label: '部分缺失或临近到期' },
  FAIL: { color: 'red', label: '必备材料缺失' },
};
const resultTag = computed(() => {
  const r = completeness.value?.result;
  if (!r) return { color: 'default', label: '—' };
  if (dictOptions('material_result').length > 0) return dictTag('material_result', r);
  return RESULT_FALLBACK[r] ?? { color: 'default', label: r };
});

/** 逐项明细列（字段逐字对齐 MaterialItem） */
const itemColumns = [
  { title: '材料名称', dataIndex: 'materialLabel', key: 'materialLabel', width: 220 },
  { title: '类型 code', dataIndex: 'materialType', key: 'materialType', width: 170 },
  { title: '大类', dataIndex: 'category', key: 'category', width: 110 },
  { title: '必备', dataIndex: 'mandatory', key: 'mandatory', width: 80 },
  { title: '状态', dataIndex: 'status', key: 'status', width: 110 },
  { title: '有效期止', dataIndex: 'validTo', key: 'validTo', width: 120 },
  { title: '匹配记录', dataIndex: 'refName', key: 'refName', width: 180 },
  { title: '说明', dataIndex: 'message', key: 'message' },
];

async function loadCompleteness() {
  if (!completenessQuery.entId) {
    message.warning('请先填写企业主体 id（entId）');
    return;
  }
  completenessLoading.value = true;
  completenessError.value = '';
  completeness.value = null;
  try {
    completeness.value = await getMaterialCompleteness(completenessQuery.entId, completenessQuery.asOf);
  } catch (error: any) {
    // 1060107013「企业材料不完备」是业务结论（可能以阻断形式返回）：优先用 data 渲染，无 data 才当错误
    const data = error?.data ?? error?.response?.data?.data;
    if (data?.result) {
      completeness.value = data;
    } else {
      completenessError.value = error?.msg ?? error?.message ?? '完备性自查失败';
    }
  } finally {
    completenessLoading.value = false;
  }
}

/* ===== ③ 到期预警扫描 ===== */
const scanning = ref(false);
const scanHits = ref<null | number>(null);
const scanAt = ref<null | number>(null);

async function doScan() {
  scanning.value = true;
  try {
    scanHits.value = await runQualificationAlertScan();
    scanAt.value = Date.now();
    message.success('预警扫描已执行');
  } catch {
    // 静默失败：错误结构由请求层呈现，本页只负责按钮态
  } finally {
    scanning.value = false;
  }
}

/* ===== ② 资质建档 ===== */
const formOpen = ref(false);
const saving = ref(false);
const formRef = ref();
const createdId = ref<null | number>(null);
const form = reactive<QualificationApi.SaveReq>({
  entId: undefined as unknown as number,
  qualSerial: 'CONSTRUCTION',
  qualCategory: '',
  qualGrade: '',
  certNo: '',
});
const rules = {
  entId: [{ required: true, message: '企业主体 id 必填', trigger: 'change' }],
  qualSerial: [{ required: true, message: '资质序列必填', trigger: 'change' }],
  qualCategory: [{ required: true, message: '资质类别必填', trigger: 'blur' }],
  qualGrade: [{ required: true, message: '资质等级必填', trigger: 'blur' }],
  certNo: [{ required: true, message: '资质证书编号必填（租户内唯一）', trigger: 'blur' }],
};

function onCreate() {
  Object.assign(form, {
    id: undefined,
    version: undefined,
    entId: completenessQuery.entId ?? (undefined as unknown as number),
    qualSerial: 'CONSTRUCTION',
    qualCategory: '',
    qualGrade: '',
    certNo: '',
    issuingAuthority: undefined,
    issueDate: undefined,
    validTo: undefined,
    approvalScope: undefined,
    bindedCertId: undefined,
    remark: undefined,
  });
  createdId.value = null;
  formOpen.value = true;
}

async function submit() {
  try {
    await formRef.value?.validate();
  } catch {
    return;
  }
  saving.value = true;
  try {
    createdId.value = await createQualification({ ...form });
    message.success(`资质建档成功（id=${createdId.value}）`);
    formOpen.value = false;
  } catch {
    // §0.4 静默：失败详情（fieldErrors/traceId）由请求层统一呈现
  } finally {
    saving.value = false;
  }
}
</script>

<template>
  <div class="ct-page">
    <div class="ct-head">
      <div>
        <div class="ct-title">企业资质</div>
        <div class="ct-sub">材料完备性自查 · 资质建档 · 到期预警扫描</div>
      </div>
      <div class="ct-actions">
        <a-button v-if="can('bidding:qualification:create')" type="primary" @click="onCreate">资质建档</a-button>
        <a-button v-if="can('bidding:qualification:alert-run')" :loading="scanning" @click="doScan">执行预警扫描</a-button>
      </div>
    </div>

    <a-alert type="warning" show-icon class="mb-3">
      <template #message>资质台账查询接口待后端补齐（本页暂不含列表 / 延续申报 / 延续审批 / 要素录入）</template>
      <template #description>
        <div>
          现状：QualificationController 仅
          <code>create / renew / approve / reject</code>，qualification 包内<strong>不存在</strong>
          page / get / get-by-code，也没有 QualificationRespVO、QualificationPageReqVO（Mapper 亦无 selectPage）。
          延续申报需 <code>qualificationId+version</code>、审批需 <code>applyId+version</code>、要素录入需
          <code>qualId</code>，这些入参只能由列表/详情提供，故本页只保留入参自洽的动作。
        </div>
        <div style="margin-top: 6px">
          待补接口：<code>GET /bidding/qualification/page</code>（qualStatus/validTo/version）、
          <code>GET /bidding/qualification/get</code>、
          <code>GET /bidding/qualification/apply/page</code>（延续申报台账，供审批选单）。
        </div>
        <div style="margin-top: 6px">
          另：<code>qual_status / qual_serial / qual_grade / apply_status / permit_status</code> 等码表尚未登记字典，
          列表补齐后这些字段也只能先显示英文 code（<code>?types=qual_status</code> 会抛 1060108008）。
        </div>
      </template>
    </a-alert>

    <div class="ct-panel mb-3">
      <div class="ct-panel-t">① 企业材料完备性自查</div>
      <div class="ct-filter">
        <a-input-number
          v-model:value="completenessQuery.entId"
          placeholder="企业主体 id（entId）"
          :min="1"
          style="width: 200px"
        />
        <a-date-picker
          v-model:value="completenessQuery.asOf"
          placeholder="核验基准日（可空=今天）"
          value-format="YYYY-MM-DD"
          style="width: 210px"
        />
        <a-button
          v-if="can('bidding:qualification:material:query')"
          type="primary"
          :loading="completenessLoading"
          @click="loadCompleteness"
        >
          自查
        </a-button>
        <span class="ct-panel-meta">可填未来日期，预演「投标截止日是否仍有效」</span>
      </div>
      <div class="ct-panel-b">
        <a-spin :spinning="completenessLoading">
          <a-alert v-if="completenessError" type="error" show-icon :message="completenessError" />
          <template v-else-if="completeness">
            <a-descriptions :column="3" size="small" bordered class="mb-3">
              <a-descriptions-item label="企业主体">{{ completeness.entId }}</a-descriptions-item>
              <a-descriptions-item label="基准日">{{ completenessQuery.asOf || '今天' }}</a-descriptions-item>
              <a-descriptions-item label="结论">
                <a-tag :color="resultTag.color">{{ resultTag.label }}</a-tag>
              </a-descriptions-item>
              <a-descriptions-item label="必备材料">
                {{ completeness.readyCount }} / {{ completeness.requiredCount }} 已齐备
              </a-descriptions-item>
              <a-descriptions-item label="缺失/过期">{{ completeness.missingItems?.length ?? 0 }} 项</a-descriptions-item>
              <a-descriptions-item label="临近到期">
                {{ completeness.expiringItems?.length ?? 0 }} 项
              </a-descriptions-item>
            </a-descriptions>

            <a-alert
              v-if="completeness.missingItems?.length"
              class="mb-2"
              type="error"
              show-icon
              :message="`缺失/过期 ${completeness.missingItems.length} 项`"
              :description="completeness.missingItems.join('、')"
            />
            <a-alert
              v-if="completeness.expiringItems?.length"
              class="mb-3"
              type="warning"
              show-icon
              :message="`临近到期 ${completeness.expiringItems.length} 项`"
              :description="completeness.expiringItems.join('、')"
            />

            <a-table
              class="ct-table"
              :columns="itemColumns"
              :data-source="completeness.items ?? []"
              :pagination="false"
              row-key="materialType"
              size="small"
              :scroll="{ x: 1250 }"
            >
              <template #bodyCell="{ column, record }">
                <template v-if="column.key === 'category'">
                  {{ dictLabel('material_category', record.category) }}
                </template>
                <template v-else-if="column.key === 'mandatory'">
                  <a-tag :color="record.mandatory ? 'red' : 'default'">{{ record.mandatory ? '必备' : '非必备' }}</a-tag>
                </template>
                <template v-else-if="column.key === 'status'">
                  <a-tag :color="dictTag('material_status', record.status).color">
                    {{ dictTag('material_status', record.status).label }}
                  </a-tag>
                </template>
                <template v-else-if="column.key === 'validTo'">{{ record.validTo || '长期' }}</template>
              </template>
              <template #emptyText><div class="ct-empty">无逐项明细</div></template>
            </a-table>
          </template>
          <div v-else class="ct-empty">输入企业主体 id 后点「自查」，返回身份类 / 资质类 / 实力类三档结论</div>
        </a-spin>
      </div>
    </div>

    <div class="ct-panel">
      <div class="ct-panel-t">② 资质到期预警扫描</div>
      <div class="ct-panel-b">
        <a-space>
          <a-button v-if="can('bidding:qualification:alert-run')" :loading="scanning" @click="doScan">
            执行一次 QUAL_EXPIRY 扫描
          </a-button>
          <span class="ct-panel-meta">同日幂等（后端 @Idempotent，按 LocalDate.now() 扫三源）</span>
        </a-space>
        <div v-if="scanHits !== null" class="mt-3">
          <a-tag color="processing">命中 {{ scanHits }} 条</a-tag>
          <span class="ct-panel-meta" style="margin-left: 8px">执行时间 {{ fmtDateTime(scanAt) }}</span>
        </div>
      </div>
    </div>

    <a-modal
      v-model:open="formOpen"
      title="资质建档"
      :width="860"
      :confirm-loading="saving"
      ok-text="建档"
      cancel-text="取消"
      destroy-on-close
      @ok="submit"
    >
      <a-alert
        v-if="createdId"
        type="success"
        show-icon
        class="mb-3"
        :message="`已创建，资质 id=${createdId}（列表接口补齐后可在此回显）`"
      />
      <a-form ref="formRef" :model="form" :rules="rules" layout="vertical">
        <a-row :gutter="16">
          <a-col :span="8">
            <a-form-item label="企业主体 id（entId）" name="entId">
              <a-input-number v-model:value="form.entId" :min="1" style="width: 100%" />
            </a-form-item>
          </a-col>
          <a-col :span="8">
            <a-form-item label="资质序列" name="qualSerial">
              <a-select v-model:value="form.qualSerial" :options="QualificationApi.QUAL_SERIAL_OPTIONS" />
            </a-form-item>
          </a-col>
          <a-col :span="8">
            <a-form-item label="资质证书编号" name="certNo">
              <a-input v-model:value="form.certNo" placeholder="租户内唯一（UK 判重）" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="资质类别" name="qualCategory">
              <a-input v-model:value="form.qualCategory" placeholder="如：市政公用工程施工总承包" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="资质等级" name="qualGrade">
              <a-input v-model:value="form.qualGrade" placeholder="如：一级（对齐国标表110）" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="发证/许可机关" name="issuingAuthority">
              <a-input v-model:value="form.issuingAuthority" />
            </a-form-item>
          </a-col>
          <a-col :span="6">
            <a-form-item label="发证日期" name="issueDate">
              <a-date-picker v-model:value="form.issueDate" value-format="YYYY-MM-DD" style="width: 100%" />
            </a-form-item>
          </a-col>
          <a-col :span="6">
            <a-form-item label="有效期至（空=长期）" name="validTo">
              <a-date-picker v-model:value="form.validTo" value-format="YYYY-MM-DD" style="width: 100%" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="关联证照（certificate_info id）" name="bindedCertId">
              <a-input-number
                v-model:value="form.bindedCertId"
                :min="1"
                style="width: 100%"
                placeholder="可空；存在性由后端校验"
              />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="批准承包/承揽范围" name="approvalScope">
              <a-input v-model:value="form.approvalScope" />
            </a-form-item>
          </a-col>
          <a-col :span="24">
            <a-form-item label="备注" name="remark"><a-textarea v-model:value="form.remark" :rows="2" /></a-form-item>
          </a-col>
        </a-row>
        <div class="ct-tip">
          资质序列码表取自 v3.3 表 15-1 DDL 注释（施工/勘察/设计/监理/检测，QualificationSerialEnum 同值）；
          该码表暂未登记字典（§0.5），下拉仅作建议值；类别与等级为自由文本，登记字典后改读 /bidding/meta/dicts。
        </div>
      </a-form>
    </a-modal>
  </div>
</template>
