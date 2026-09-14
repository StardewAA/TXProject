<!--
  前台 · 企业级功能已迁至后台（口径收敛页）

  背景（用户 2026-09-14 裁定口径，原文）：
    "前台是个人工作台，而后台主要管企业产出的数据……基本上就是企业在后台，个人在前台。"
  据此，"项目 → 招标项目 → 标段（包） → 公告" 这套**企业数据生产**动线（含创建/删除项目、创建公告）
  不属于个人前台，已在后台提供（views/bidding/project、views/bidding/notice）。
  本页只做一件事：老书签/历史链接进来时**说清去哪儿**，而不是 404 或静默可用。
  可回退：router/routes/core.ts 把这 4 条路由的 component 换回 project/*.vue 即恢复（页面文件未删）。
-->
<script lang="ts" setup>
import { useRouter } from 'vue-router';

const router = useRouter();

/** 前台保留的个人视角入口 */
const KEPT = [
  { label: '公告大厅', desc: '查询 · 中标公示 · 详情', path: '/portal/bidding' },
  { label: '本平台发布', desc: '招标人公告浏览', path: '/portal/bidding/internal-hall' },
  { label: '我的关注', desc: '关注的公告/项目 · 动态提醒', path: '/portal/bidding/subscription' },
  { label: '个人中心', desc: '自身信息 · 实名认证', path: '/portal/bidding/profile' },
];

/** 已迁到后台的功能（企业级） */
const MOVED = [
  '项目主档：新建 / 编辑 / 删除',
  '招标项目：新建 / 编辑 / 删除',
  '标段（包）：维护',
  '公告 / 公示：创建与发布（含内容与附件）',
];

function go(path: string): void {
  router.push(path).catch(() => {
    /* 导航被取消/重复导航：忽略 */
  });
}
</script>

<template>
  <div class="mv-wrap">
    <div class="mv-card">
      <div class="mv-tag">口径调整</div>
      <h1 class="mv-title">
        项目与公告的创建维护已移到<span class="mv-strong">后台（企业端）</span>
      </h1>
      <p class="mv-lead">
        前台是<strong>个人工作台</strong>，只保留与你个人相关的浏览与订阅；
        项目、招标项目、标段（包）、公告属于<strong>企业产出数据</strong>，由具备企业权限的账号在后台维护。
      </p>
      <div class="mv-cols">
        <div class="mv-col">
          <div class="mv-h">已迁至后台</div>
          <ul class="mv-list mv-list-moved">
            <li v-for="m in MOVED" :key="m">{{ m }}</li>
          </ul>
        </div>
        <div class="mv-col">
          <div class="mv-h">前台保留</div>
          <ul class="mv-list">
            <li v-for="k in KEPT" :key="k.path">
              <a class="mv-link" role="button" tabindex="0" @click="go(k.path)" @keydown.enter.prevent="go(k.path)">
                {{ k.label }}
              </a>
              <span class="mv-desc">{{ k.desc }}</span>
            </li>
          </ul>
        </div>
      </div>
      <div class="mv-actions">
        <button class="mv-btn primary" @click="go('/portal/bidding')">返回公告大厅</button>
        <button class="mv-btn" @click="go('/portal/bidding/subscription')">查看我的关注</button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.mv-wrap { display: flex; justify-content: center; padding: 48px 16px; }
.mv-card { width: 100%; max-width: 720px; background: #fff; border: 1px solid #e7e9ee; border-radius: 10px; padding: 28px 32px 24px; box-shadow: 0 2px 10px rgb(17 24 39 / 4%); }
.mv-tag { display: inline-block; font-size: 12px; color: #8a5a00; background: #fff5e0; border: 1px solid #ffe0a3; border-radius: 999px; padding: 2px 10px; margin-bottom: 12px; }
.mv-title { font-size: 20px; line-height: 1.5; color: #1f2430; margin: 0 0 10px; font-weight: 600; }
.mv-strong { color: #c0392b; }
.mv-lead { font-size: 13px; line-height: 1.9; color: #5b6270; margin: 0 0 20px; }
.mv-cols { display: flex; gap: 24px; flex-wrap: wrap; }
.mv-col { flex: 1 1 240px; min-width: 220px; }
.mv-h { font-size: 13px; font-weight: 600; color: #1f2430; padding-bottom: 8px; border-bottom: 1px solid #eef0f4; margin-bottom: 10px; }
.mv-list { list-style: none; margin: 0; padding: 0; }
.mv-list li { font-size: 13px; line-height: 2; color: #5b6270; }
.mv-list-moved li::before { content: '×'; color: #c0392b; margin-right: 8px; }
.mv-link { color: #1a56b8; cursor: pointer; }
.mv-desc { color: #98a0ad; margin-left: 8px; font-size: 12px; }
.mv-actions { display: flex; gap: 10px; margin-top: 24px; }
.mv-btn { font-size: 13px; padding: 7px 16px; border-radius: 6px; border: 1px solid #d7dbe3; background: #fff; color: #37404f; cursor: pointer; }
.mv-btn:hover { border-color: #1a56b8; color: #1a56b8; }
.mv-btn.primary { background: #1a56b8; border-color: #1a56b8; color: #fff; }
.mv-btn.primary:hover { background: #164a9e; color: #fff; }
</style>
