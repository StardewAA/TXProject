<!--
  属性筛选行（**前台那种样式**）—— 左侧属性名 + 右侧一排文字选项，点一下即选，不弹下拉。

  用户口径（2026-09-14）："我期望的筛选框是前台那种样式而不是下拉框"。
  样式来源（**不自造颜色**）：一律用平台语义 token —— text-foreground / text-muted-foreground /
  border-border / bg-card / bg-primary + text-primary-foreground / hover:bg-accent / rounded-md / text-[13px]。
  这正是同事那轮「招投标管理端与门户页面对齐平台主题（深/浅色自适应）」的做法：颜色只认平台变量，
  深浅色由平台主题切换，页面不写死 hex。

  支持默认插槽：输入框、时间范围这类无法穷举成文字选项的筛选项，套在同一行式版式里（属性名位置一致）。
  用法：
    <AttrFilter label="任务状态" v-model="filter.status" :options="statusOptions" @change="reload" />
    <AttrFilter label="URL 关键字"><input v-model="filter.url" /></AttrFilter>
-->
<script lang="ts" setup>
defineOptions({ name: 'AttrFilter' });

const props = withDefaults(
  defineProps<{
    allLabel?: string;
    /** 是否提供「全部」项（点击后值为 undefined） */
    allowAll?: boolean;
    /** 左侧属性名（**必须写清在筛什么**——此前若干筛选项只有 placeholder 暗示，用户看不出字段含义） */
    label: string;
    modelValue?: number | string;
    /** 可选项；为空时渲染默认插槽 */
    options?: Array<{ label: string; value: number | string }>;
  }>(),
  { allowAll: true, allLabel: '全部', modelValue: undefined, options: () => [] },
);

const emit = defineEmits<{ change: []; 'update:modelValue': [number | string | undefined] }>();

function pick(v?: number | string): void {
  emit('update:modelValue', v);
  emit('change');
}
/** 「全部」态：未选、空串都算 */
const isAll = (): boolean => props.modelValue === undefined || props.modelValue === '';
/** 文字选项的统一外观（选中=主色实底，未选=悬停用平台 accent） */
const OPT_CLASS =
  'cursor-pointer select-none rounded-md px-3 py-0.5 text-[13px] leading-[26px] transition-colors focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary';
const optClass = (active: boolean): string =>
  active ? OPT_CLASS + ' bg-primary font-semibold text-primary-foreground' : OPT_CLASS + ' text-foreground hover:bg-accent';
</script>

<template>
  <div class="flex flex-wrap items-start border-b border-border py-1.5 last:border-b-0">
    <div class="w-full shrink-0 text-[13px] font-semibold leading-[26px] text-muted-foreground sm:w-[88px]">
      {{ label }}
    </div>
    <div class="flex min-w-0 flex-1 flex-wrap items-center gap-1.5">
      <template v-if="options.length">
        <span
          v-if="allowAll"
          :class="optClass(isAll())"
          role="button"
          tabindex="0"
          @click="pick(undefined)"
          @keydown.enter.prevent="pick(undefined)"
          >{{ allLabel }}</span>
        <span
          v-for="o in options"
          :key="String(o.value)"
          :class="optClass(modelValue === o.value)"
          role="button"
          tabindex="0"
          @click="pick(o.value)"
          @keydown.enter.prevent="pick(o.value)"
          >{{ o.label }}</span>
      </template>
      <slot v-else></slot>
    </div>
  </div>
</template>
