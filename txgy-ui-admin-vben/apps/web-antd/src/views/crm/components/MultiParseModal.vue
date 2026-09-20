<script lang="ts" setup>
/**
 * CRM「多人识别」结果确认弹窗（通用）
 *
 * 支持三种批量目标（由父级通过 target 指定）：
 *  - contact  联系人：姓名 / 手机 / 电话 / 所属客户(必填) / 职位 / 邮箱 / 微信 / QQ / 备注
 *  - clue     线索  ：线索名称(必填) / 手机 / 电话 / 行业 / 级别 / 来源 / 地址 / 邮箱 / 备注
 *  - customer 客户  ：客户名称(必填) / 手机 / 电话 / 行业 / 级别 / 来源 / 地址 / 邮箱 / 备注
 *
 * 能力：
 *  1. 列出全部识别到的条目，每条一个卡片、可勾选、字段可手工修改；
 *  2. 每条独立查重（手机号 / 名称匹配同类数据）：命中则整体标红，可选「跳过 / 覆盖 / 新建」；
 *  3. 缺少核心信息（名称、联系方式、联系人还需所属客户）的条目标记「待确认」，不允许入库；
 *  4. 勾选后一键批量新增（按 target 调用对应接口）；
 *  5. 无法自动拆分时（splittable=false），提供文本编辑框手动分割后重新识别。
 */
import type { ParsedContactItem, ParsedClue } from '#/utils/crm/clue-parse';

import { computed, ref } from 'vue';

import { DICT_TYPE } from '@vben/constants';
import { getDictOptions } from '@vben/hooks';
import { useVbenModal } from '@vben/common-ui';
import { useUserStore } from '@vben/stores';

import { message } from 'ant-design-vue';

import { createContact, updateContact } from '#/api/crm/contact';
import { createClue, updateClue } from '#/api/crm/clue';
import {
  checkDuplicate,
  createCustomer,
  getCustomerSimpleList,
  updateCustomer,
} from '#/api/crm/customer';
import { parseClueBlock, parseContactBlock, splitContactBlocks } from '#/utils/crm/clue-parse';

const emit = defineEmits(['success']);

/** 批量目标 */
type Target = 'contact' | 'clue' | 'customer';
/** 重复处理方式 */
type DupAction = 'create' | 'overwrite' | 'skip';

interface FieldDef {
  key: string;
  label: string;
  placeholder?: string;
  /** 字典类型：渲染为下拉 */
  dict?: string;
  /** 参与「待确认」判定的必填项 */
  required?: boolean;
  /** 「所属客户」特殊字段（仅联系人） */
  customerRef?: boolean;
}

/** 各目标的字段配置（决定弹窗里编辑哪些字段） */
const FIELD_DEFS: Record<Target, FieldDef[]> = {
  contact: [
    { key: 'name', label: '姓名', placeholder: '姓名', required: true },
    { key: 'mobile', label: '手机号', placeholder: '11 位手机号' },
    { key: 'telephone', label: '固定电话', placeholder: '如 010-88886666' },
    { key: 'customerId', label: '所属客户', required: true, customerRef: true },
    { key: 'post', label: '职位', placeholder: '如 采购部经理' },
    { key: 'email', label: '邮箱' },
    { key: 'wechat', label: '微信' },
    { key: 'qq', label: 'QQ' },
    { key: 'remark', label: '备注' },
  ],
  clue: [
    { key: 'name', label: '线索名称', placeholder: '公司 / 单位名称', required: true },
    { key: 'mobile', label: '手机号', placeholder: '11 位手机号' },
    { key: 'telephone', label: '固定电话', placeholder: '如 010-88886666' },
    { key: 'industryId', label: '所属行业', dict: DICT_TYPE.CRM_CUSTOMER_INDUSTRY },
    { key: 'level', label: '客户级别', dict: DICT_TYPE.CRM_CUSTOMER_LEVEL },
    { key: 'source', label: '客户来源', dict: DICT_TYPE.CRM_CUSTOMER_SOURCE },
    { key: 'detailAddress', label: '详细地址' },
    { key: 'email', label: '邮箱' },
    { key: 'remark', label: '备注' },
  ],
  customer: [
    { key: 'name', label: '客户名称', placeholder: '公司 / 单位名称', required: true },
    { key: 'mobile', label: '手机号', placeholder: '11 位手机号' },
    { key: 'telephone', label: '固定电话', placeholder: '如 010-88886666' },
    { key: 'industryId', label: '所属行业', dict: DICT_TYPE.CRM_CUSTOMER_INDUSTRY },
    { key: 'level', label: '客户级别', dict: DICT_TYPE.CRM_CUSTOMER_LEVEL },
    { key: 'source', label: '客户来源', dict: DICT_TYPE.CRM_CUSTOMER_SOURCE },
    { key: 'detailAddress', label: '详细地址' },
    { key: 'email', label: '邮箱' },
    { key: 'remark', label: '备注' },
  ],
};

/** 目标的展示文案 */
const TARGET_TEXT: Record<Target, { title: string; unit: string }> = {
  contact: { title: '识别到多位联系人，请核对后批量新增', unit: '联系人' },
  clue: { title: '识别到多条线索，请核对后批量新增', unit: '线索' },
  customer: { title: '识别到多个客户，请核对后批量新增', unit: '客户' },
};

/** 查重命中时对应的 bizType */
const DUP_BIZ_TYPE: Record<Target, string> = {
  contact: 'contact',
  clue: 'clue',
  customer: 'customer',
};

const userStore = useUserStore();

const target = ref<Target>('contact');
const rows = ref<any[]>([]);
const splittable = ref(true);
const splitHint = ref('');
const manualText = ref('');
const showManual = ref(false);
const submitting = ref(false);
const checking = ref(false);
const customerOptions = ref<any[]>([]);

const fieldDefs = computed(() => FIELD_DEFS[target.value]);
const targetText = computed(() => TARGET_TEXT[target.value]);

/** 字典下拉选项（行业 / 级别 / 来源） */
function dictOptions(type: string) {
  return getDictOptions(type as any, 'number');
}

/** 缺核心信息 → 待确认，不允许入库 */
function needConfirm(row: any): boolean {
  const hasName = !!String(row.name ?? '').trim();
  const hasPhone =
    !!String(row.mobile ?? '').trim() || !!String(row.telephone ?? '').trim();
  if (!hasName || !hasPhone) return true;
  // 联系人必须挂到某个客户下（DB 必填）
  if (target.value === 'contact' && !row.customerId) return true;
  return false;
}

/** 缺少哪些核心信息（Tooltip 提示用） */
function missingText(row: any): string {
  const miss: string[] = [];
  const nameLabel = target.value === 'contact' ? '姓名' : '名称';
  if (!String(row.name ?? '').trim()) miss.push(nameLabel);
  if (!String(row.mobile ?? '').trim() && !String(row.telephone ?? '').trim()) {
    miss.push('手机/电话');
  }
  if (target.value === 'contact' && !row.customerId) miss.push('所属客户');
  return miss.length ? `缺少：${miss.join('、')}` : '';
}

const checkedList = computed(() =>
  rows.value.filter((r) => r.checked && !needConfirm(r)),
);
const pendingCount = computed(() => rows.value.filter(needConfirm).length);
const dupCount = computed(() => rows.value.filter((r) => !!r.dup).length);

function buildRows(list: any[]): any[] {
  return (list ?? []).map((c, i) => ({
    ...c,
    key: `mp-${Date.now()}-${i}`,
    checked: true,
    dup: null,
    dupAction: 'create' as DupAction,
    dupChecked: false,
  }));
}

/** QQ 清洗：DB 为 32 位整数，超范围视为误识别 */
function sanitizeQq(v?: string): string | undefined {
  const s = String(v ?? '').replace(/\D/g, '');
  if (!s) return undefined;
  const n = Number(s);
  if (s.length > 11 || !Number.isSafeInteger(n) || n > 2147483647) {
    return undefined;
  }
  return s;
}

/** 联系人：按公司名匹配系统已有客户 */
async function matchCustomer(row: any) {
  if (target.value !== 'contact') return;
  const name = String(row.customerName ?? '').trim();
  if (!name || row.customerId) return;
  const hit = customerOptions.value.find((c: any) =>
    String(c?.name ?? '').includes(name),
  );
  if (hit?.id) {
    row.customerId = hit.id;
  }
}

/** 逐条查重：命中同类数据则标红 */
async function runDuplicateCheck() {
  checking.value = true;
  try {
    for (const row of rows.value) {
      row.dup = null;
      row.dupChecked = false;
      const mobile = String(row.mobile ?? '').trim();
      const name = String(row.name ?? '').trim();
      if (!mobile && !name) {
        row.dupChecked = true;
        continue;
      }
      try {
        const res: any = await checkDuplicate({ mobile, name });
        const hit = (res?.items ?? []).find(
          (it: any) => it?.bizType === DUP_BIZ_TYPE[target.value],
        );
        if (res?.duplicated && hit) {
          row.dup = hit;
          // 默认「跳过」，避免制造重复数据；用户可改为覆盖或强制新建
          row.dupAction = 'skip';
          row.checked = false;
        }
      } catch {
        // 查重失败不阻塞提交
      }
      row.dupChecked = true;
    }
  } finally {
    checking.value = false;
  }
}

/** 用指定文本重新拆分 */
async function resplitBy(text: string) {
  const { blocks, hint, reliable } = splitContactBlocks(text);
  if (blocks.length < 2) {
    return false;
  }
  const items =
    target.value === 'contact'
      ? blocks.map((b) => parseContactBlock(b))
      : blocks.map((b) => parseClueBlock(b));
  rows.value = buildRows(items);
  splittable.value = reliable;
  splitHint.value = hint;
  for (const row of rows.value) {
    await matchCustomer(row);
  }
  await runDuplicateCheck();
  return true;
}

/** 加载数据（打开弹窗时） */
async function init(payload: {
  target?: Target;
  items?: ParsedContactItem[] | ParsedClue[];
  splittable?: boolean;
  splitHint?: string;
  rawText?: string;
}) {
  target.value = (payload?.target ?? 'contact') as Target;
  rows.value = buildRows(payload?.items ?? []);
  splittable.value = payload?.splittable !== false;
  splitHint.value = payload?.splitHint ?? '';
  manualText.value = payload?.rawText ?? '';
  showManual.value = !splittable.value;
  if (target.value === 'contact') {
    try {
      customerOptions.value = ((await getCustomerSimpleList()) as any[]) ?? [];
    } catch {
      customerOptions.value = [];
    }
    for (const row of rows.value) {
      await matchCustomer(row);
    }
  }
  await runDuplicateCheck();
}

/** 手动分割：按用户编辑后的文本重新识别 */
async function handleResplit() {
  const text = manualText.value.trim();
  if (!text) {
    message.warning('请先填写或粘贴文本');
    return;
  }
  const ok = await resplitBy(text);
  if (!ok) {
    message.warning('仍只解析出 1 条，请用编号、空行或「每行一个」的方式分隔');
    return;
  }
  message.success(`已按当前文本重新拆分为 ${rows.value.length} 条`);
}

/** 全选 / 全不选（仅作用于信息完整的条目） */
function toggleAll(checked: boolean) {
  rows.value.forEach((r) => {
    if (!needConfirm(r)) r.checked = checked;
  });
}

/** 按 target 组装提交体 */
function buildPayload(row: any): any {
  const common: any = {
    name: String(row.name ?? '').trim(),
    mobile: String(row.mobile ?? '').trim() || undefined,
    telephone: String(row.telephone ?? '').trim() || undefined,
    email: String(row.email ?? '').trim() || undefined,
    wechat: String(row.wechat ?? '').trim() || undefined,
    qq: sanitizeQq(row.qq),
    detailAddress: String(row.detailAddress ?? '').trim() || undefined,
    remark: String(row.remark ?? '').trim() || undefined,
    ownerUserId: userStore.userInfo?.id,
  };
  if (target.value === 'contact') {
    return {
      ...common,
      customerId: row.customerId,
      post: String(row.post ?? '').trim() || undefined,
    };
  }
  // 线索 / 客户：行业、级别、来源为数字枚举（下拉选不到时后端按空处理）
  return {
    ...common,
    industryId: row.industryId || undefined,
    level: row.level || undefined,
    source: row.source || undefined,
  };
}

/** 批量新增 */
async function handleSubmit() {
  const list = checkedList.value;
  if (list.length === 0) {
    message.warning('请先勾选要新增的条目（信息不完整的需补全后才能入库）');
    return;
  }
  const t = target.value;
  const createApi: any =
    t === 'contact' ? createContact : t === 'clue' ? createClue : createCustomer;
  const updateApi: any =
    t === 'contact' ? updateContact : t === 'clue' ? updateClue : updateCustomer;

  submitting.value = true;
  let ok = 0;
  let skipped = 0;
  let failed = 0;
  try {
    for (const row of list) {
      if (row.dup && row.dupAction === 'skip') {
        skipped++;
        continue;
      }
      try {
        const payload = buildPayload(row);
        if (row.dup && row.dupAction === 'overwrite' && row.dup.id) {
          await updateApi({ ...payload, id: row.dup.id });
        } else {
          await createApi(payload);
        }
        ok++;
      } catch {
        failed++;
      }
    }
    message.success(
      `批量新增完成：成功 ${ok} 条` +
        (skipped ? `，跳过重复 ${skipped} 条` : '') +
        (failed ? `，失败 ${failed} 条` : ''),
    );
    emit('success');
    await modalApi.close();
  } finally {
    submitting.value = false;
  }
}

const [Modal, modalApi] = useVbenModal({
  onOpenChange(isOpen: boolean) {
    if (!isOpen) {
      rows.value = [];
      manualText.value = '';
      showManual.value = false;
      return;
    }
    const data = modalApi.getData<any>();
    if (data) {
      void init(data);
    }
  },
});

defineExpose({ init });
</script>

<template>
  <Modal :title="targetText.title" class="w-[900px]">
    <div class="mx-4">
      <!-- 汇总提示 -->
      <div
        class="mb-3 flex flex-wrap items-center gap-2 rounded border border-primary/30 bg-primary/5 px-3 py-2 text-xs"
      >
        <span class="font-medium">
          共识别到 {{ rows.length }} 条{{ targetText.unit }}
        </span>
        <a-tag v-if="dupCount > 0" color="red">重复 {{ dupCount }} 条</a-tag>
        <a-tag v-if="pendingCount > 0" color="orange">
          待确认 {{ pendingCount }} 条
        </a-tag>
        <a-tag v-if="splitHint" color="blue">{{ splitHint }}</a-tag>
        <span class="text-muted-foreground">
          已勾选 {{ checkedList.length }} 条可入库
        </span>
        <div class="ml-auto flex items-center gap-2">
          <a-button size="small" @click="toggleAll(true)">全选</a-button>
          <a-button size="small" @click="toggleAll(false)">全不选</a-button>
          <a-button size="small" :loading="checking" @click="runDuplicateCheck">
            重新查重
          </a-button>
        </div>
      </div>

      <!-- 兜底：无法自动拆分时手动分割 -->
      <div
        v-if="showManual"
        class="mb-3 rounded border border-orange-300 bg-orange-50 p-3"
      >
        <div class="mb-1 text-xs font-medium text-orange-600">
          未能可靠地自动拆分多人，请手动分割
        </div>
        <div class="mb-2 text-xs text-muted-foreground">
          建议：每行一个，或用「1. 2. 3.」编号、空行分隔。
        </div>
        <a-textarea v-model:value="manualText" :rows="5" />
        <div class="mt-2 flex items-center gap-2">
          <a-button type="primary" size="small" @click="handleResplit">
            按当前文本重新识别
          </a-button>
          <a-button size="small" @click="showManual = false">收起</a-button>
        </div>
      </div>

      <!-- 识别结果列表 -->
      <div class="max-h-[46vh] space-y-2 overflow-y-auto pr-1">
        <a-empty v-if="rows.length === 0" description="暂无识别结果" />
        <div
          v-for="(row, i) in rows"
          :key="row.key"
          class="rounded border px-3 py-2"
          :class="row.dup ? 'border-red-300 bg-red-50/60' : 'border-border'"
        >
          <!-- 行头：勾选 + 序号 + 状态标签 + 重复处理方式 -->
          <div class="mb-2 flex flex-wrap items-center gap-2">
            <a-checkbox
              v-model:checked="row.checked"
              :disabled="needConfirm(row)"
            />
            <span class="text-xs font-medium">#{{ i + 1 }}</span>
            <span class="text-sm font-medium">
              {{ row.name || '（未识别到名称）' }}
            </span>
            <a-tag v-if="row.dup" color="red">系统已有</a-tag>
            <a-tooltip v-if="needConfirm(row)" :title="missingText(row)">
              <a-tag color="orange">待确认</a-tag>
            </a-tooltip>
            <span v-if="row.dup" class="text-xs text-red-500">
              命中：{{ row.dup.name || '（无名称）' }}
              {{ row.dup.mobile ? `/ ${row.dup.mobile}` : '' }}
            </span>
            <a-select
              v-if="row.dup"
              v-model:value="row.dupAction"
              size="small"
              class="ml-auto w-32"
              :options="[
                { label: '跳过不新增', value: 'skip' },
                { label: '覆盖已有记录', value: 'overwrite' },
                { label: '仍然新建', value: 'create' },
              ]"
            />
          </div>

          <!-- 字段编辑区（按目标动态渲染） -->
          <div class="grid grid-cols-3 gap-2">
            <div v-for="f in fieldDefs" :key="f.key">
              <div class="mb-0.5 text-xs text-muted-foreground">
                {{ f.label }}
                <span v-if="f.required" class="text-red-500">（必填）</span>
              </div>
              <!-- 联系人的「所属客户」：可搜索下拉 -->
              <a-select
                v-if="f.customerRef"
                v-model:value="row[f.key]"
                size="small"
                class="w-full"
                show-search
                allow-clear
                option-filter-prop="label"
                placeholder="请选择所属客户"
                :options="
                  customerOptions.map((c: any) => ({
                    label: c.name,
                    value: c.id,
                  }))
                "
              />
              <!-- 字典字段：行业 / 级别 / 来源 -->
              <a-select
                v-else-if="f.dict"
                v-model:value="row[f.key]"
                size="small"
                class="w-full"
                allow-clear
                :placeholder="f.placeholder ?? f.label"
                :options="dictOptions(f.dict)"
              />
              <a-input
                v-else
                v-model:value="row[f.key]"
                size="small"
                :placeholder="f.placeholder ?? f.label"
              />
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 底部操作 -->
    <template #footer>
      <div class="flex w-full items-center">
        <a-button
          v-if="!splittable"
          type="link"
          size="small"
          @click="showManual = !showManual"
        >
          {{ showManual ? '收起手动分割' : '手动分割文本' }}
        </a-button>
        <div class="ml-auto flex items-center gap-2">
          <a-button @click="modalApi.close()">取消</a-button>
          <a-button
            type="primary"
            :loading="submitting"
            :disabled="checkedList.length === 0"
            @click="handleSubmit"
          >
            批量新增选中项（{{ checkedList.length }}）
          </a-button>
        </div>
      </div>
    </template>
  </Modal>
</template>
