<script lang="ts" setup>
import type { ReportCenterApi } from '#/api/report/center';

import { computed, ref } from 'vue';

/** 分组展示顺序（未列出的分组追加在末尾） */
const GROUP_ORDER = ['常用', '生产', '品质', '设备', '库存', '自定义'];

/** 「常用」分组最多展示张数 */
const FREQUENT_LIMIT = 5;

const props = defineProps<{
  reports: ReportCenterApi.Report[];
  selectedCode: string;
}>();

const emit = defineEmits<{
  (e: 'select', code: string): void;
}>();

const keyword = ref('');
/** 折叠状态，默认全部展开 */
const collapsed = ref<Record<string, boolean>>({});

/** 命中的报表 */
interface MatchedReport {
  report: ReportCenterApi.Report;
  /** 命中原因（仅当命中报表内字段时给出，避免用户困惑） */
  reason?: string;
}

/** 分组节点 */
interface GroupNode {
  name: string;
  reports: MatchedReport[];
}

function normalize(text?: string) {
  return (text ?? '').toLowerCase();
}

/**
 * 判断报表是否命中关键词，命中则返回匹配信息
 *
 * 匹配优先级：报表名称 > 分组名 > 用途描述 > 报表内字段
 */
function matchReport(
  report: ReportCenterApi.Report,
  kw: string,
): MatchedReport | null {
  if (!kw) {
    return { report };
  }
  if (normalize(report.name).includes(kw)) {
    return { report };
  }
  if ((report.groups ?? []).some((group) => normalize(group).includes(kw))) {
    return { report };
  }
  if (normalize(report.description).includes(kw)) {
    return { report };
  }
  const hitColumn = (report.columnLabels ?? []).find((label) =>
    normalize(label).includes(kw),
  );
  if (hitColumn) {
    return { report, reason: `匹配字段：${hitColumn}` };
  }
  return null;
}

/** 关键词过滤后的报表（保持后端返回顺序） */
const matchedReports = computed(() => {
  const kw = normalize(keyword.value.trim());
  const result: MatchedReport[] = [];
  for (const report of props.reports) {
    const matched = matchReport(report, kw);
    if (matched) {
      result.push(matched);
    }
  }
  return result;
});

/**
 * 分组树
 *
 * 多归属的报表会在多个分组下各出现一次（例如「物料耗用报表」同时属于生产与库存）。
 */
const groupTree = computed<GroupNode[]>(() => {
  const buckets = new Map<string, MatchedReport[]>();
  const push = (group: string, matched: MatchedReport) => {
    if (!buckets.has(group)) {
      buckets.set(group, []);
    }
    buckets.get(group)!.push(matched);
  };

  // 1. 常用：近 30 天查询次数 > 0，按次数倒序取前 N 张
  matchedReports.value
    .filter((item) => (item.report.queryCount ?? 0) > 0)
    .sort((a, b) => (b.report.queryCount ?? 0) - (a.report.queryCount ?? 0))
    .slice(0, FREQUENT_LIMIT)
    .forEach((item) => push('常用', item));

  // 2. 业务分组
  for (const item of matchedReports.value) {
    for (const group of item.report.groups ?? []) {
      push(group, item);
    }
  }

  // 3. 按预定义顺序输出，未列出的分组排末尾
  const names = [...buckets.keys()].sort((a, b) => {
    const indexA = GROUP_ORDER.indexOf(a);
    const indexB = GROUP_ORDER.indexOf(b);
    return (
      (indexA === -1 ? Number.MAX_SAFE_INTEGER : indexA) -
      (indexB === -1 ? Number.MAX_SAFE_INTEGER : indexB)
    );
  });
  return names.map((name) => ({ name, reports: buckets.get(name)! }));
});

/** 命中张数（去重后的报表数） */
const matchedCount = computed(() => matchedReports.value.length);

function toggleGroup(name: string) {
  collapsed.value[name] = !collapsed.value[name];
}

function handleSelect(code: string) {
  emit('select', code);
}
</script>

<template>
  <aside class="rc-sidebar">
    <div class="rc-sidebar-search">
      <a-input-search
        v-model:value="keyword"
        allow-clear
        placeholder="搜索报表名称或用途"
      />
      <div v-if="keyword.trim()" class="rc-sidebar-tip">
        共 {{ matchedCount }} 张匹配
      </div>
    </div>

    <div class="rc-sidebar-body">
      <template v-for="group in groupTree" :key="group.name">
        <div class="rc-group-head" @click="toggleGroup(group.name)">
          <span class="rc-group-arrow">
            {{ collapsed[group.name] ? '▸' : '▾' }}
          </span>
          <span class="rc-group-name">{{ group.name }}</span>
          <span class="rc-group-count">{{ group.reports.length }}</span>
        </div>
        <ul v-show="!collapsed[group.name]" class="rc-group-list">
          <li
            v-for="item in group.reports"
            :key="`${group.name}-${item.report.code}`"
            :class="[
              'rc-item',
              { 'rc-item-active': item.report.code === selectedCode },
            ]"
            @click="handleSelect(item.report.code)"
          >
            <div class="rc-item-name">{{ item.report.name }}</div>
            <div v-if="item.reason" class="rc-item-reason">
              {{ item.reason }}
            </div>
          </li>
        </ul>
      </template>

      <div v-if="groupTree.length === 0" class="rc-sidebar-empty">
        <div class="rc-sidebar-empty-main">
          没有匹配「{{ keyword.trim() }}」的报表
        </div>
        <div class="rc-sidebar-empty-tip">
          试试「库存」「合格率」「工单」
        </div>
      </div>
    </div>
  </aside>
</template>

<style scoped>
.rc-sidebar {
  display: flex;
  flex-direction: column;
  width: 260px;
  flex: 0 0 260px;
  height: 100%;
  overflow: hidden;
  background: #fff;
  border: 1px solid #e3e9f0;
  border-radius: 8px;
}

.rc-sidebar-search {
  padding: 12px;
  border-bottom: 1px solid #eef1f5;
}

.rc-sidebar-tip {
  margin-top: 6px;
  font-size: 12px;
  color: #7b8794;
}

.rc-sidebar-body {
  flex: 1;
  padding: 6px 8px 12px;
  overflow-y: auto;
}

.rc-group-head {
  display: flex;
  gap: 6px;
  align-items: center;
  padding: 8px 6px 6px;
  font-size: 12px;
  color: #7b8794;
  cursor: pointer;
  user-select: none;
}

.rc-group-head:hover {
  color: #2f5480;
}

.rc-group-arrow {
  width: 10px;
  font-size: 10px;
}

.rc-group-name {
  flex: 1;
  font-weight: 600;
  letter-spacing: 0.5px;
}

.rc-group-count {
  padding: 0 6px;
  font-size: 11px;
  color: #7b8794;
  background: #f3f6fa;
  border-radius: 8px;
}

.rc-group-list {
  padding: 0;
  margin: 0 0 4px;
  list-style: none;
}

.rc-item {
  padding: 7px 10px;
  margin-bottom: 2px;
  font-size: 13px;
  color: #344356;
  cursor: pointer;
  border-left: 3px solid transparent;
  border-radius: 4px;
  transition: background 0.15s ease;
}

.rc-item:hover {
  background: #f3f6fa;
}

.rc-item-active {
  font-weight: 600;
  color: #2f5480;
  background: #eaf1f9;
  border-left-color: #2f5480;
}

.rc-item-name {
  line-height: 1.4;
}

.rc-item-reason {
  margin-top: 2px;
  font-size: 11px;
  font-weight: 400;
  color: #9aa5b1;
}

.rc-sidebar-empty {
  padding: 28px 12px;
  text-align: center;
}

.rc-sidebar-empty-main {
  font-size: 13px;
  color: #7b8794;
}

.rc-sidebar-empty-tip {
  margin-top: 6px;
  font-size: 12px;
  color: #a5afbb;
}
</style>
