<script lang="ts" setup>
import { computed, ref } from 'vue';

import { Button, Input, Select } from 'ant-design-vue';

import { cmsContentConfigs } from '../../cms/content/config';

defineOptions({ name: 'PortalContent' });

const keyword = ref('');
const moduleType = ref('standard');

const modules = [
  {
    desc: '国标、行标、地方规范',
    icon: '标',
    key: 'standard',
    title: '标准规范',
  },
  {
    desc: '材料参数与选型',
    icon: '材',
    key: 'material',
    title: '材料参数',
  },
  {
    desc: '工艺工法与案例',
    icon: '艺',
    key: 'process',
    title: '工艺案例',
  },
  {
    desc: '定额与造价资料',
    icon: '额',
    key: 'quota',
    title: '定额资料',
  },
  {
    desc: '行业政策资讯',
    icon: '策',
    key: 'policy',
    title: '政策资讯',
  },
];

const items = computed(() =>
  modules.flatMap((module) => {
    const config = cmsContentConfigs[module.key];
    return config.records
      .filter((record) => record.status === '已发布')
      .map((record) => ({
        ...record,
        group: module.title,
        moduleKey: module.key,
        title:
          record.title ||
          record.name ||
          record.standardNo ||
          '未命名内容',
      }));
  }),
);

const filteredItems = computed(() => {
  const value = keyword.value.trim().toLowerCase();
  return items.value.filter((record) => {
    const matchedModule =
      moduleType.value === 'all' || record.moduleKey === moduleType.value;
    const matchedKeyword =
      !value ||
      Object.values(record).some((item) =>
        String(item ?? '').toLowerCase().includes(value),
      );
    return matchedModule && matchedKeyword;
  });
});

const groupResults = computed(() =>
  modules
    .map((module) => ({
      ...module,
      rows: filteredItems.value.filter(
        (record) => record.moduleKey === module.key,
      ),
    }))
    .filter((module) => module.rows.length > 0),
);
</script>

<template>
  <div class="portal-content-page">
    <section class="content-hero">
      <p class="content-eyebrow">CMS 内容管理</p>
      <h1>标准、材料、工艺、定额、政策统一查询</h1>
      <p>这里集中浏览防腐保温行业公开内容，支持分类筛选与关键词检索。</p>
      <div class="content-search">
        <Select
          v-model:value="moduleType"
          class="content-module-select"
          :options="[
            { label: '全部内容', value: 'all' },
            ...modules.map((module) => ({
              label: module.title,
              value: module.key,
            })),
          ]"
        />
        <Input
          v-model:value="keyword"
          class="content-keyword-input"
          placeholder="输入标准号、名称或关键词"
        />
        <Button type="primary">查询</Button>
      </div>
    </section>

    <section class="content-block">
      <div class="content-block-heading">
        <h2>内容模块</h2>
        <span>{{ items.length }} 条公开内容</span>
      </div>
      <div class="content-module-grid">
        <button
          v-for="module in modules"
          :key="module.key"
          type="button"
          @click="
            moduleType = module.key;
            keyword = '';
          "
        >
          <span class="content-module-icon">{{ module.icon }}</span>
          <strong>{{ module.title }}</strong>
          <small>{{ module.desc }}</small>
        </button>
      </div>
    </section>

    <section v-if="keyword || moduleType !== 'all'" class="content-block">
      <div class="content-block-heading">
        <h2>查询结果</h2>
        <span>{{ filteredItems.length }} 条</span>
      </div>
      <div v-for="group in groupResults" :key="group.key" class="content-result-group">
        <h3>{{ group.title }}</h3>
        <div v-for="record in group.rows" :key="record.id" class="content-result-row">
          <strong>{{ record.title }}</strong>
          <span>{{ record.standardNo || record.category || record.resourceType || record.department || record.quotaType || '-' }}</span>
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
.portal-content-page {
  display: grid;
  gap: 20px;
}

.content-hero {
  padding: 32px 36px;
  border: 1px solid #c7d2fe;
  border-radius: 20px;
  background:
    linear-gradient(120deg, rgb(79 70 229 / 14%), transparent 60%),
    #fff;
}

.content-eyebrow {
  margin: 0 0 8px;
  color: #4f46e5;
  font-size: 14px;
  font-weight: 700;
}

.content-hero h1 {
  margin: 0;
  color: #0f172a;
  font-size: 30px;
  line-height: 1.3;
}

.content-hero > p:last-of-type {
  margin: 10px 0 0;
  color: #475569;
  font-size: 15px;
}

.content-search {
  display: flex;
  max-width: 820px;
  margin-top: 24px;
  gap: 10px;
}

.content-module-select {
  width: 150px;
}

.content-keyword-input {
  flex: 1;
}

.content-block {
  padding: 22px 24px;
  border: 1px solid #e2e8f0;
  border-radius: 16px;
  background: #fff;
}

.content-block-heading {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: 16px;
}

.content-block-heading h2 {
  margin: 0;
  color: #0f172a;
  font-size: 20px;
}

.content-block-heading span {
  color: #94a3b8;
  font-size: 13px;
}

.content-module-grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 12px;
}

.content-module-grid button {
  display: flex;
  min-height: 150px;
  padding: 18px;
  border: 1px solid #e2e8f0;
  border-radius: 16px;
  background: #fff;
  cursor: pointer;
  flex-direction: column;
  align-items: flex-start;
  text-align: left;
}

.content-module-grid button:hover {
  border-color: #a5b4fc;
  box-shadow: 0 8px 20px rgb(79 70 229 / 10%);
}

.content-module-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 46px;
  height: 46px;
  border-radius: 14px;
  background: #eef2ff;
  color: #4f46e5;
  font-size: 20px;
  font-weight: 700;
}

.content-module-grid strong {
  margin-top: 14px;
  color: #0f172a;
  font-size: 17px;
}

.content-module-grid small {
  margin-top: 8px;
  color: #64748b;
  font-size: 13px;
  line-height: 1.5;
}

.content-result-group {
  margin-bottom: 18px;
}

.content-result-group h3 {
  margin: 0 0 8px;
  color: #0f172a;
  font-size: 16px;
}

.content-result-row {
  display: flex;
  justify-content: space-between;
  padding: 12px 0;
  border-bottom: 1px solid #f1f5f9;
  gap: 16px;
}

.content-result-row strong {
  color: #1f2937;
  font-size: 14px;
  font-weight: 600;
}

.content-result-row span {
  color: #64748b;
  font-size: 13px;
  white-space: nowrap;
}

@media (max-width: 1000px) {
  .content-module-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .content-search {
    flex-wrap: wrap;
  }

  .content-module-select,
  .content-keyword-input {
    width: 100%;
  }
}
</style>
