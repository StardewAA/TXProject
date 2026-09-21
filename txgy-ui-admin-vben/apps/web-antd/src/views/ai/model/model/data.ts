import type { VbenFormSchema } from '#/adapter/form';
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { AiModelApiKeyApi } from '#/api/ai/model/apiKey';

import { AiModelTypeEnum, CommonStatusEnum, DICT_TYPE } from '@vben/constants';
import { getDictOptions } from '@vben/hooks';

import { z } from '#/adapter/form';
import { getApiKeySimpleList } from '#/api/ai/model/apiKey';

/** 关联数据 */
let apiKeyList: AiModelApiKeyApi.ApiKey[] = [];
getApiKeySimpleList().then((data) => (apiKeyList = data));

/**
 * 各平台的「模型标识」示例。
 *
 * 这是本页面最容易填错的一项：同一模型在不同厂商的写法不同，
 * 硅基流动等平台还要带 `deepseek-ai/` 这类前缀。选中平台后直接把示例填进 placeholder，
 * 用户对着抄即可，避免反复试错。
 */
const MODEL_ID_HINTS: Record<string, string> = {
  Anthropic: 'claude-3-5-sonnet-20241022',
  BaiChuan: 'Baichuan4',
  DeepSeek: 'deepseek-chat',
  DouBao: 'doubao-seed-2-1-pro-260915',
  Gemini: 'gemini-2.0-flash',
  Grok: 'grok-2-latest',
  HunYuan: 'hunyuan-turbo',
  MiniMax: 'abab6.5s-chat',
  Moonshot: 'moonshot-v1-8k',
  Ollama: 'qwen2.5:7b',
  OpenAI: 'gpt-4o-mini',
  SiliconFlow: 'deepseek-ai/DeepSeek-V3',
  StepFun: 'step-1-8k',
  TongYi: 'qwen-plus',
  YiYan: 'ernie-4.0-8k',
  ZhiPu: 'glm-4-plus',
};

/** 取某平台的模型标识示例（未收录时给通用示例） */
function modelIdHint(platform?: string) {
  return (platform && MODEL_ID_HINTS[platform]) || 'deepseek-chat';
}

/**
 * 弹窗顶部常驻的对照表（只列最常用的几行）。
 *
 * 工具提示需要悬停才可见，而「模型标识」是本页面出错率最高的一项，
 * 因此把最常见的几条直接铺在表单上方，做到「看一眼就能照抄」。
 */
export const MODEL_ID_REFERENCE: { model: string; platform: string }[] = [
  { platform: 'DeepSeek', model: 'deepseek-chat / deepseek-reasoner' },
  { platform: '字节豆包', model: 'doubao-seed-2-1-pro-260915' },
  { platform: '阿里通义', model: 'qwen-plus / qwen-max' },
  { platform: '硅基流动', model: 'deepseek-ai/DeepSeek-V3（注意前缀）' },
  { platform: 'OpenAI', model: 'gpt-4o-mini' },
  { platform: '智谱', model: 'glm-4-plus' },
];

/**
 * 用当前配置里已有的对话模型，给「温度 / Token / 上下文」三个参数提供参考默认值，
 * 让新建对话模型时不必手填（仍可修改）。
 */
const DEFAULT_TEMPERATURE = 0.3;
const DEFAULT_MAX_TOKENS = 2048;
const DEFAULT_MAX_CONTEXTS = 4;

/** 新增/修改的表单 */
export function useFormSchema(): VbenFormSchema[] {
  return [
    {
      component: 'Input',
      fieldName: 'id',
      dependencies: {
        triggerFields: [''],
        show: () => false,
      },
    },
    {
      fieldName: 'platform',
      label: '所属平台',
      component: 'Select',
      // help 写在 schema 顶层，会渲染成标签旁的问号气泡
      help: '决定 API Key 用哪家的、以及「模型标识」该怎么写',
      componentProps: {
        // placeholder 给「怎么选」，而不是重复标签
        placeholder: '选择你购买 API 的厂商',
        options: getDictOptions(DICT_TYPE.AI_PLATFORM, 'string'),
        allowClear: true,
      },
      rules: 'required',
    },
    {
      fieldName: 'type',
      label: '模型类型',
      component: 'Select',
      help: '接入 AI 客服必须选「对话」；其余类型（绘画/写作等）不能用于助手与知识库问答',
      componentProps: (values) => {
        return {
          placeholder: '选择该模型的用途',
          disabled: !!values.id,
          options: getDictOptions(DICT_TYPE.AI_MODEL_TYPE, 'number'),
          allowClear: true,
        };
      },
      rules: 'required',
    },
    {
      fieldName: 'keyId',
      label: 'API 密钥',
      component: 'ApiSelect',
      help: '列表来自「AI 客服管理 → API 密钥」；没有可选项时，请先到那里新增（填 Key + 选平台）',
      componentProps: {
        placeholder: '选择已配置的密钥',
        api: getApiKeySimpleList,
        labelField: 'name',
        valueField: 'id',
        allowClear: true,
      },
      rules: 'required',
    },
    {
      component: 'Input',
      fieldName: 'name',
      label: '模型名字',
      help: '仅用于界面展示，可随意取；建议带上厂商名，方便在「AI 助手配置」的下拉里一眼认出',
      rules: 'required',
      componentProps: {
        placeholder: '例如 DeepSeek 对话',
      },
    },
    {
      component: 'Input',
      fieldName: 'model',
      label: '模型标识',
      help: '必须与厂商文档完全一致（区分大小写、不能有多余空格）；填错会报「模型不存在」',
      rules: 'required',
      // 选中平台后 placeholder 跟着换成该平台的真实示例
      dependencies: {
        triggerFields: ['platform'],
      },
      componentProps: (values) => ({
        placeholder: `例如 ${modelIdHint(values.platform)}`,
      }),
    },
    {
      fieldName: 'sort',
      label: '模型排序',
      component: 'InputNumber',
      help: '数字越小越靠前，仅影响下拉里的展示顺序',
      componentProps: {
        class: '!w-full',
        placeholder: '例如 1',
      },
      rules: z.number().default(1),
    },
    {
      fieldName: 'status',
      label: '开启状态',
      component: 'RadioGroup',
      help: '关闭后该模型不会出现在「AI 助手配置」的下拉里',
      componentProps: {
        options: getDictOptions(DICT_TYPE.COMMON_STATUS, 'number'),
        buttonStyle: 'solid',
        optionType: 'button',
      },
      rules: z.number().default(CommonStatusEnum.ENABLE),
    },
    {
      fieldName: 'temperature',
      label: '温度参数',
      component: 'InputNumber',
      help: '0 最稳定、答案最固定；2 最发散。客服与取数场景建议 0～0.3',
      componentProps: {
        class: '!w-full',
        placeholder: `例如 ${DEFAULT_TEMPERATURE}`,
        min: 0,
        max: 2,
      },
      dependencies: {
        triggerFields: ['type'],
        show: (values) => {
          return [AiModelTypeEnum.CHAT].includes(values.type);
        },
      },
      rules: z.number().default(DEFAULT_TEMPERATURE),
    },
    {
      fieldName: 'maxTokens',
      label: '回复数 Token 数',
      component: 'InputNumber',
      help: '单次回复的最大长度；太小会把回答截断，客服问答 2048 足够',
      componentProps: {
        class: '!w-full',
        min: 0,
        max: 8192,
        placeholder: `例如 ${DEFAULT_MAX_TOKENS}`,
      },
      dependencies: {
        triggerFields: ['type'],
        show: (values) => {
          return [AiModelTypeEnum.CHAT].includes(values.type);
        },
      },
      rules: z.number().default(DEFAULT_MAX_TOKENS),
    },
    {
      fieldName: 'maxContexts',
      label: '上下文数量',
      component: 'InputNumber',
      help: '携带的历史对话轮数；越大越“记得住”，但更慢也更贵，4～10 较合适',
      componentProps: {
        class: '!w-full',
        min: 0,
        max: 20,
        placeholder: `例如 ${DEFAULT_MAX_CONTEXTS}`,
      },
      dependencies: {
        triggerFields: ['type'],
        show: (values) => {
          return [AiModelTypeEnum.CHAT].includes(values.type);
        },
      },
      rules: z.number().default(DEFAULT_MAX_CONTEXTS),
    },
  ];
}

/** 列表的搜索表单 */
export function useGridFormSchema(): VbenFormSchema[] {
  return [
    {
      fieldName: 'name',
      label: '模型名字',
      component: 'Input',
      componentProps: {
        placeholder: '例如 DeepSeek 对话',
        allowClear: true,
      },
    },
    {
      fieldName: 'model',
      label: '模型标识',
      component: 'Input',
      componentProps: {
        placeholder: '例如 deepseek-chat',
        allowClear: true,
      },
    },
    {
      fieldName: 'platform',
      label: '模型平台',
      component: 'Select',
      componentProps: {
        placeholder: '选择平台',
        options: getDictOptions(DICT_TYPE.AI_PLATFORM, 'string'),
        allowClear: true,
      },
    },
  ];
}

/** 列表的字段 */
export function useGridColumns(): VxeTableGridOptions['columns'] {
  return [
    {
      field: 'platform',
      title: '所属平台',
      cellRender: {
        name: 'CellDict',
        props: { type: DICT_TYPE.AI_PLATFORM },
      },
      minWidth: 100,
    },
    {
      field: 'type',
      title: '模型类型',
      cellRender: {
        name: 'CellDict',
        props: { type: DICT_TYPE.AI_MODEL_TYPE },
      },
      minWidth: 100,
    },
    {
      field: 'name',
      title: '模型名字',
      minWidth: 180,
    },
    {
      title: '模型标识',
      field: 'model',
      minWidth: 180,
    },
    {
      title: 'API 密钥',
      field: 'keyId',
      formatter: ({ cellValue }) => {
        return (
          apiKeyList.find((apiKey) => apiKey.id === cellValue)?.name || '-'
        );
      },
      minWidth: 140,
    },
    {
      title: '排序',
      field: 'sort',
      minWidth: 80,
    },
    {
      field: 'status',
      title: '状态',
      cellRender: {
        name: 'CellDict',
        props: { type: DICT_TYPE.COMMON_STATUS },
      },
      minWidth: 80,
    },
    {
      field: 'temperature',
      title: '温度参数',
      minWidth: 100,
    },
    {
      title: '回复数 Token 数',
      field: 'maxTokens',
      minWidth: 140,
    },
    {
      title: '上下文数量',
      field: 'maxContexts',
      minWidth: 120,
    },
    {
      title: '操作',
      width: 130,
      fixed: 'right',
      slots: { default: 'actions' },
    },
  ];
}
