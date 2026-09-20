<script lang="ts" setup>
import { QuestionCircleOutlined } from '@ant-design/icons-vue';
import { Switch, Tooltip } from 'ant-design-vue';

/**
 * 多个开关横向排列的组件
 *
 * 用于「管理开关」这类布尔配置项：若每个开关单独占一个表单项，
 * 在双栏布局下右侧会留出大片空白；合并成一行可显著压缩表单高度。
 *
 * 通过 v-model 读写一个对象，例如：
 *   { batchManage: true, shelfLifeManage: false }
 */
defineOptions({ name: 'WmsSwitchGroup', inheritAttrs: false });

interface SwitchItem {
  /** 对象中的字段名 */
  field: string;
  /** 显示标签 */
  label: string;
  /** 开启时显示的文字 */
  checkedChildren?: string;
  /** 关闭时显示的文字 */
  unCheckedChildren?: string;
  /** 帮助提示（鼠标悬停显示），空则不展示图标 */
  help?: string;
}

const props = withDefaults(
  defineProps<{
    /** 开关列表 */
    items?: SwitchItem[];
    /** 当前值对象 */
    modelValue?: Record<string, any>;
    /** 是否禁用 */
    disabled?: boolean;
  }>(),
  {
    items: () => [],
    modelValue: () => ({}),
    disabled: false,
  },
);

const emit = defineEmits<{
  'update:modelValue': [value: Record<string, any>];
}>();

/** 读取当前值 */
function valueOf(field: string) {
  return (props.modelValue ?? {})[field];
}

/** 单个开关变化：整体回写新对象，保证 v-model 生效 */
function handleChange(field: string, checked: any) {
  emit('update:modelValue', { ...(props.modelValue ?? {}), [field]: checked });
}
</script>

<template>
  <!-- h-8.5 = 34px，与同一表单里输入框的高度一致：
       这样整行的控件区高度和相邻输入行相同，标签与开关都自然对齐 -->
  <div class="flex h-[34px] flex-wrap items-center gap-x-10 gap-y-2">
    <div v-for="item in items" :key="item.field" class="flex items-center gap-2">
      <span class="text-sm text-gray-600">{{ item.label }}</span>
      <Switch
        :checked="valueOf(item.field)"
        :checked-children="item.checkedChildren ?? '是'"
        :disabled="disabled"
        :un-checked-children="item.unCheckedChildren ?? '否'"
        @change="(checked: any) => handleChange(item.field, checked)"
      />
      <!-- 保留原表单项的 help 提示（如批次管理的说明），避免合并后丢失 -->
      <Tooltip v-if="item.help" :title="item.help">
        <QuestionCircleOutlined class="cursor-help text-gray-400" />
      </Tooltip>
    </div>
  </div>
</template>