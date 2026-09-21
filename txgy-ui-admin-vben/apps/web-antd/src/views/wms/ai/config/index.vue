<script lang="ts" setup>
/**
 * WMS AI 客服配置
 *
 * 决定「仓储 AI 助手（AI 智能助手）」用哪个模型。
 *
 * 模型与 API Key 不在这里维护 —— 它们由 AI 模块统一管理：
 *   · AI 客服管理 → 模型配置（新增/编辑模型，绑定平台与密钥）
 *   · AI 客服管理 → API 密钥（各厂商 Key 与网关地址）
 * 本页只负责「从已有模型里挑一个作为助手默认模型」，并展示当前实际生效的来源，
 * 便于排查「改了配置为什么不生效」。
 */
import type { WmsAiConfigApi } from '#/api/wms/ai/config';

import { computed, onMounted, ref } from 'vue';

import { IconifyIcon } from '@vben/icons';

import { Alert, Button, Card, InputNumber, message, Select, Switch } from 'ant-design-vue';

import { getModelSimpleList } from '#/api/ai/model/model';
import { getAiConfig, saveAiConfig, testAiModel } from '#/api/wms/ai/config';

defineOptions({ name: 'WmsAiConfig' });

const loading = ref(false);
const saving = ref(false);
const testing = ref(false);
const config = ref<WmsAiConfigApi.Config>({});
const models = ref<Awaited<ReturnType<typeof getModelSimpleList>>>([]);

const form = ref<WmsAiConfigApi.SaveReqVO>({
  modelId: 0,
  enabled: true,
  timeoutSeconds: 240,
  remark: '',
});

/** 生效来源 → 展示文案与颜色 */
const sourceMeta = computed(() => {
  switch (config.value.effectiveSource) {
    case 'CONFIG': {
      return { type: 'success' as const, text: '界面配置生效' };
    }
    case 'ENV': {
      return { type: 'warning' as const, text: '环境变量生效（未在界面配置）' };
    }
    default: {
      return { type: 'error' as const, text: '尚未配置，AI 助手不可用' };
    }
  }
});

/** 模型下拉：/ai/model/simple-list 已只返回启用中的模型，且响应不含 status 字段，不再按它过滤 */
const modelOptions = computed(() =>
  models.value
    .filter((m) => !m.type || m.type === 1)
    .map((m) => ({
      // 当前正在生效的模型直接标出来，避免「选了却不知道跑的是哪个」
      label:
        m.id === (config.value.modelId ?? 0)
          ? `✅ ${m.name}（${m.platform}）— 当前生效`
          : `${m.name}（${m.platform}）`,
      value: m.id!,
    })),
);

async function load() {
  loading.value = true;
  try {
    const [cfg, list] = await Promise.all([
      getAiConfig(),
      getModelSimpleList(1).catch(() => []),
    ]);
    config.value = cfg || {};
    models.value = list || [];
    form.value = {
      modelId: cfg?.modelId ?? 0,
      enabled: cfg?.enabled ?? true,
      timeoutSeconds: cfg?.timeoutSeconds ?? 240,
      remark: cfg?.remark ?? '',
    };
  } catch (error: any) {
    message.error(error?.message || '加载 AI 客服配置失败');
  } finally {
    loading.value = false;
  }
}

async function handleSave() {
  if (!form.value.modelId) {
    message.warning('请先选择 AI 助手使用的模型');
    return;
  }
  saving.value = true;
  try {
    await saveAiConfig(form.value);
    message.success('保存成功，AI 助手已切换到该模型（立即生效，无需重启）');
    await load();
  } catch (error: any) {
    message.error(error?.message || '保存失败');
  } finally {
    saving.value = false;
  }
}

/**
 * 连通性测试：用所选模型发一句最短对话，验证「地址 + Key + 模型名」三者可用。
 * 配置错误（Key 过期、模型名写错、网关不通）在保存前就能暴露，不必等聊天时才发现。
 */
async function handleTest() {
  if (!form.value.modelId) {
    message.warning('请先选择要测试的模型');
    return;
  }
  testing.value = true;
  const hide = message.loading({ content: '正在测试模型连通性…', duration: 0 });
  try {
    const elapsed = await testAiModel(form.value.modelId);
    message.success(`连通正常，耗时 ${elapsed} ms`);
  } catch (error: any) {
    // 后端返回的失败原因已包含 HTTP 状态与响应体，直接展示
    message.error(error?.message || '连通性测试失败', 8);
  } finally {
    hide();
    testing.value = false;
  }
}

/** 当前已保存并生效的模型编号（用于下拉里标注「当前生效」） */
const effectiveModelId = computed(() => config.value.modelId ?? 0);

onMounted(load);
</script>

<template>
  <div class="p-4">
    <Alert
      class="mb-4"
      :type="sourceMeta.type"
      show-icon
      :message="`当前生效：${sourceMeta.text}`"
    >
      <template #description>
        <div>模型：{{ config.effectiveModel || '未配置' }}</div>
        <div class="break-all">
          调用地址：{{ config.effectiveBaseUrl || '未配置' }}
        </div>
      </template>
    </Alert>

    <Card :loading="loading" title="AI 助手模型">
      <div class="max-w-2xl space-y-4">
        <div>
          <div class="mb-1.5 text-sm text-gray-600">使用的模型</div>
          <Select
            v-model:value="form.modelId"
            :options="modelOptions"
            class="w-full"
            placeholder="请选择一个已配置密钥的聊天模型"
            show-search
            :filter-option="
              (input: string, option: any) =>
                String(option.label).toLowerCase().includes(input.toLowerCase())
            "
          />
          <div class="mt-1 text-xs text-gray-400">
            列表来自「AI 客服管理 → 模型配置」，只展示已启用的聊天模型。
            若要接入其他厂商，请先在「API 密钥」里新增该厂商的 Key，再到「模型配置」新建模型。
          </div>
          <div v-if="effectiveModelId" class="mt-1 text-xs text-green-600">
            <IconifyIcon class="mr-1 inline size-3.5" icon="lucide:circle-check" />
            当前生效：{{ config.modelName || config.effectiveModel }}（编号
            {{ effectiveModelId }}）。下拉里带此标记的即为当前正在使用的模型。
          </div>
        </div>

        <div>
          <div class="mb-1.5 text-sm text-gray-600">启用界面配置</div>
          <Switch v-model:checked="form.enabled" />
          <span class="ml-2 text-xs text-gray-400">
            关闭后回落容器环境变量 txgy.ai.siliconflow.*
          </span>
        </div>

        <div>
          <div class="mb-1.5 text-sm text-gray-600">调用超时（秒）</div>
          <InputNumber
            v-model:value="form.timeoutSeconds"
            :max="600"
            :min="10"
            class="w-40"
          />
        </div>

        <div>
          <div class="mb-1.5 text-sm text-gray-600">备注</div>
          <input
            v-model="form.remark"
            class="w-full rounded border border-gray-300 px-2 py-1.5 text-sm focus:border-blue-400 focus:outline-none"
            maxlength="255"
            placeholder="例如：WMS 客服默认用豆包 lite"
          />
        </div>

        <div class="flex gap-2">
          <Button :loading="saving" type="primary" @click="handleSave">
            <IconifyIcon class="mr-1 size-4" icon="lucide:save" />
            保存
          </Button>
          <Button :disabled="saving" :loading="testing" @click="handleTest">
            <IconifyIcon class="mr-1 size-4" icon="lucide:plug-zap" />
            测试连通性
          </Button>
          <Button :disabled="saving || testing" @click="load">刷新</Button>
        </div>
      </div>
    </Card>
  </div>
</template>
