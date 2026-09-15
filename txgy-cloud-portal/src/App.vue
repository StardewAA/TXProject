<script setup>
const navItems = [
  { label: '首页', path: '/' },
  { label: '智能检索', path: '/search' },
];
</script>

<template>
  <div class="portal-shell">
    <header class="portal-header">
      <div class="brand">防腐保温行业门户</div>
      <nav>
        <RouterLink
          v-for="item in navItems"
          :key="item.path"
          :to="item.path"
        >
          {{ item.label }}
        </RouterLink>
      </nav>
    </header>
    <main class="portal-main">
      <RouterView />
    </main>
  </div>
</template>

<style>
* {
  box-sizing: border-box;
}

body {
  margin: 0;
  color: #1f2937;
  background: #f3f6fa;
  font-family:
    system-ui,
    -apple-system,
    'PingFang SC',
    'Microsoft YaHei',
    sans-serif;
}

.portal-header {
  display: flex;
  align-items: center;
  min-height: 64px;
  padding: 0 32px;
  border-bottom: 1px solid #e5e7eb;
  background: #fff;
  gap: 28px;
}

.brand {
  color: #1d4ed8;
  font-size: 18px;
  font-weight: 700;
}

.portal-header nav {
  display: flex;
  gap: 8px;
}

.portal-header a {
  padding: 8px 14px;
  border-radius: 10px;
  color: #475569;
  text-decoration: none;
}

.portal-header a.router-link-active {
  background: #eff6ff;
  color: #1d4ed8;
  font-weight: 600;
}

.portal-main {
  width: min(1180px, calc(100% - 40px));
  margin: 0 auto;
  padding: 32px 0;
}
</style>
