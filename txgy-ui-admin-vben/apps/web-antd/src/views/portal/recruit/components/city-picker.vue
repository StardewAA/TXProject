<script lang="ts" setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';

import { IconifyIcon } from '@vben/icons';

import { getAreaTree, type SystemAreaApi } from '#/api/system/area';

/**
 * 全国城市选择面板：搜索框 + 按省分组 + 城市带岗位数。
 *
 * <p>从企业目录页（company-directory.vue）里抽出来，因为求职偏好抽屉也要用同一套。
 * 复制一份的话两边迟早走岔，那就不是「一样」了。</p>
 *
 * <p>组件只管「选哪个城市」，不管「选完之后干什么」：企业目录是单选、选完关面板，
 * 求职偏好最多选 3 个、选完还要能继续加。所以这里只 emit(pick)，由调用方决定。</p>
 */
interface CityGroup {
  cities: { name: string }[];
  province: string;
}

/**
 * 地区树是嵌套结构，但 SystemAreaApi.Area 上没声明 children ——
 * 企业目录那份实现也是自己扩了一个。这里同样扩一份，不去动公共类型定义。
 */
type AreaNode = SystemAreaApi.Area & { children?: AreaNode[] };

const props = withDefaults(
  defineProps<{
    /**
     * 面板朝哪边伸。
     * 企业目录的选择器在筛选行右端 → 往左伸（right:0）；求职偏好抽屉里空间在右侧 → 往右伸。
     * 靠 right:0 又想塞进窄容器的话，面板会从左边顶出去。
     */
    align?: 'left' | 'right';
    /** 岗位数（省名 / 市名 → 条数）。不传就不显示数字 —— 求职偏好那边拿不到这份数据也不该报错 */
    counts?: Map<string, number>;
    /** 触发按钮上的文案 */
    label?: string;
    /**
     * 面板宽度。比触发按钮宽得多，所以不能由组件写死 ——
     * 容器窄的时候必须由调用方收窄，否则一样会顶出容器。
     */
    panelWidth?: string;
    /** 已选城市名，用于高亮 */
    selected?: string[];
  }>(),
  {
    align: 'right',
    counts: undefined,
    label: '全国城市',
    panelWidth: '460px',
    selected: () => [],
  },
);

const emit = defineEmits<{ pick: [name: string] }>();

const cityGroups = ref<CityGroup[]>([]);
const panelOpen = ref(false);
const keyword = ref('');
const root = ref<HTMLElement>();

/**
 * 三级地区树折算成「省 → 市」两级。
 *
 * <p>直辖市（北京/上海/天津/重庆）在树里省下面直接挂区，没有市这一级，
 * 判定方式是看省的第一个孩子有没有孩子：有 → 下面才是市；没有 → 省本身就是市。</p>
 */
function buildCityGroups(tree: AreaNode[]): CityGroup[] {
  const groups: CityGroup[] = [];
  for (const province of tree) {
    const kids = province.children ?? [];
    const hasCityLevel = kids.length > 0 && (kids[0]?.children?.length ?? 0) > 0;
    const cities = (hasCityLevel ? kids : [province]).map((item: AreaNode) => ({ name: item.name }));
    if (cities.length > 0) {
      groups.push({ province: province.name, cities });
    }
  }
  return groups;
}

async function loadCityGroups() {
  try {
    cityGroups.value = buildCityGroups(((await getAreaTree()) ?? []) as AreaNode[]);
  } catch {
    // 取不到就退化成空列表，面板仍能打开并给出提示
    cityGroups.value = [];
  }
}

/** 搜索时摊平成一张表并带上省名 —— 「新城区」这类重名光看市名分不清属于哪 */
const searchResults = computed(() => {
  const text = keyword.value.trim();
  if (!text) return [];
  const out: { name: string; province: string }[] = [];
  for (const group of cityGroups.value) {
    for (const item of group.cities) {
      if (item.name.includes(text)) out.push({ name: item.name, province: group.province });
      if (out.length >= 80) return out;
    }
  }
  return out;
});

function countOf(name: string) {
  return props.counts?.get(name) ?? 0;
}

/**
 * 省份与省内的市都按岗位数降序。
 *
 * <p>地区树是行政区划顺序（香港、澳门、北京、天津、河北…），照它铺会让岗位最多的
 * 陕西沉到中段。没有岗位的省份 count 都是 0，sort 稳定，它们保持树里的原顺序垫底。</p>
 */
const sortedGroups = computed(() =>
  [...cityGroups.value]
    .map((group) => ({
      ...group,
      cities: [...group.cities].sort((a, b) => countOf(b.name) - countOf(a.name)),
    }))
    .sort((a, b) => countOf(b.province) - countOf(a.province)),
);

const isSelected = (name: string) => props.selected.includes(name);

function onPick(name: string) {
  emit('pick', name);
  closePanel();
}

function closePanel() {
  panelOpen.value = false;
  keyword.value = '';
}

function onDocumentPointerDown(event: MouseEvent) {
  if (!panelOpen.value) return;
  if (root.value && !root.value.contains(event.target as Node)) closePanel();
}

function onKeydown(event: KeyboardEvent) {
  if (panelOpen.value && event.key === 'Escape') closePanel();
}

onMounted(() => {
  document.addEventListener('mousedown', onDocumentPointerDown);
  document.addEventListener('keydown', onKeydown);
  void loadCityGroups();
});

onBeforeUnmount(() => {
  document.removeEventListener('mousedown', onDocumentPointerDown);
  document.removeEventListener('keydown', onKeydown);
});
</script>

<template>
  <div ref="root" class="city-picker">
    <button class="city-picker-trigger" type="button" :aria-expanded="panelOpen" @click="panelOpen = !panelOpen">
      {{ label }}
      <IconifyIcon icon="lucide:chevron-down" />
    </button>

    <div v-if="panelOpen" class="city-picker-panel" :class="`is-${align}`" role="dialog" aria-label="选择城市">
      <label class="city-picker-search">
        <IconifyIcon icon="lucide:search" />
        <input v-model="keyword" type="search" placeholder="搜索城市，如：延安" aria-label="搜索城市" />
      </label>

      <div class="city-picker-body">
        <template v-if="keyword.trim()">
          <p v-if="!searchResults.length" class="city-picker-empty">没有匹配的城市</p>
          <button v-for="item in searchResults" :key="`${item.province}-${item.name}`" type="button" :class="{ active: isSelected(item.name) }" @click="onPick(item.name)">
            {{ item.name }}<small>{{ item.province }}</small>
          </button>
        </template>
        <template v-else>
          <div v-for="group in sortedGroups" :key="group.province" class="city-picker-group">
            <span class="city-picker-province">
              {{ group.province }}
              <i v-if="countOf(group.province)">{{ countOf(group.province) }} 个岗位</i>
            </span>
            <div class="city-picker-cities">
              <button v-for="item in group.cities" :key="item.name" type="button" :class="{ active: isSelected(item.name), hot: countOf(item.name) > 0 }" @click="onPick(item.name)">
                {{ item.name }}<i v-if="countOf(item.name)">{{ countOf(item.name) }}</i>
              </button>
            </div>
          </div>
          <p v-if="!cityGroups.length" class="city-picker-empty">城市列表加载失败，请稍后重试</p>
        </template>
      </div>
    </div>
  </div>
</template>

<style scoped>
.city-picker {
  position: relative;
}

.city-picker-trigger {
  display: inline-flex;
  min-height: 32px;
  align-items: center;
  gap: 6px;
  padding: 0 10px;
  border: 1px solid var(--job-line, #d7e5f4);
  border-radius: 6px;
  background: #fff;
  color: var(--job-ink, #1b2a3d);
  cursor: pointer;
  font: inherit;
  font-size: 13px;
}

.city-picker-trigger:hover {
  border-color: #a9c9e8;
}

.city-picker-panel {
  position: absolute;
  z-index: 30;
  top: calc(100% + 6px);
  display: flex;
  width: v-bind(panelWidth);
  max-width: 92vw;
  max-height: 380px;
  box-sizing: border-box;
  flex-direction: column;
  border: 1px solid var(--job-line, #d7e5f4);
  border-radius: 10px;
  background: #fff;
  box-shadow: 0 16px 36px rgb(27 42 61 / 16%);
  overflow: hidden;
}

/* 伸展方向由 align 决定，两边都写全，免得继承到对面的定位 */
.city-picker-panel.is-right {
  right: 0;
  left: auto;
}
.city-picker-panel.is-left {
  left: 0;
  right: auto;
}

.city-picker-search {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
  border-bottom: 1px solid var(--job-line, #d7e5f4);
  color: var(--job-muted, #5f7288);
}

.city-picker-search input {
  width: 100%;
  border: 0;
  outline: 0;
  background: transparent;
  color: var(--job-ink, #1b2a3d);
  font: inherit;
  font-size: 13px;
}

.city-picker-body {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 8px 0 12px;
}

.city-picker-body > button {
  display: flex;
  width: 100%;
  align-items: baseline;
  justify-content: space-between;
  gap: 10px;
  padding: 7px 16px;
  border: 0;
  background: transparent;
  color: var(--job-ink, #1b2a3d);
  cursor: pointer;
  font: inherit;
  font-size: 13px;
  text-align: left;
}

.city-picker-body > button:hover,
.city-picker-body > button.active {
  background: var(--job-blue-soft, #e8f1fb);
  color: var(--job-blue, #2f6fb3);
}

.city-picker-body > button small {
  color: var(--job-muted, #5f7288);
  font-size: 11px;
}

.city-picker-group {
  padding: 8px 16px 4px;
}

.city-picker-province {
  display: block;
  margin-bottom: 6px;
  color: var(--job-ink, #1b2a3d);
  font-size: 13px;
  font-weight: 650;
}

.city-picker-province i {
  margin-left: 8px;
  color: var(--job-muted, #5f7288);
  font-size: 11px;
  font-style: normal;
  font-weight: 400;
}

.city-picker-cities {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 10px;
}

.city-picker-cities button {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 3px 8px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: var(--job-muted, #5f7288);
  cursor: pointer;
  font: inherit;
  font-size: 13px;
}

.city-picker-cities button:hover {
  background: var(--job-blue-soft, #e8f1fb);
  color: var(--job-ink, #1b2a3d);
}

/* 有岗位的市把数字带出来，并加重颜色 —— 菜单要回答的是「哪儿活儿多」 */
.city-picker-cities button.hot {
  color: var(--job-ink, #1b2a3d);
}

.city-picker-cities button i {
  padding: 0 5px;
  border-radius: 9999px;
  background: var(--job-blue-soft, #e8f1fb);
  color: var(--job-blue, #2f6fb3);
  font-size: 11px;
  font-style: normal;
}

.city-picker-cities button.active {
  background: var(--job-blue, #2f6fb3);
  color: #fff;
}

.city-picker-cities button.active i {
  background: rgb(255 255 255 / 24%);
  color: #fff;
}

.city-picker-empty {
  margin: 0;
  padding: 18px 16px;
  color: var(--job-muted, #5f7288);
  font-size: 12px;
}
</style>
