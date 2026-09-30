<script setup lang="ts">

import { computed, onMounted, onUnmounted, ref } from 'vue';



import LawSearchPanel from '../../cms/_shared/law-search-panel.vue';
import PortalContentSections from '../components/portal-content-sections.vue';
import PortalServiceSections from '../components/portal-service-sections.vue';
import '../../cms/_shared/cms-glass-theme.css';


defineOptions({ name: 'PortalContent' });

/** 回到顶部：滚回智能检索区 */
function backToTop() {
  document.querySelector('.cms-modules')?.scrollIntoView({ behavior: 'smooth', block: 'start' });
}


/** 桌面端滚轮阻尼：滚动值逐步逼近目标位置，形成惯性缓动。 */
let dampedScrollFrame: number | undefined;
let dampedTargetY = 0;
let dampedCurrentY = 0;
let dampedScrollEnabled = false;

function clampScroll(value: number) {
  const maximum = Math.max(0, document.documentElement.scrollHeight - window.innerHeight);
  return Math.min(maximum, Math.max(0, value));
}

function animateDampedScroll() {
  const distance = dampedTargetY - dampedCurrentY;
  if (Math.abs(distance) < 0.5) {
    dampedCurrentY = dampedTargetY;
    window.scrollTo(0, dampedCurrentY);
    dampedScrollFrame = undefined;
    return;
  }
  dampedCurrentY += distance * 0.14;
  window.scrollTo(0, dampedCurrentY);
  dampedScrollFrame = window.requestAnimationFrame(animateDampedScroll);
}

function onDampedWheel(event: WheelEvent) {
  if (!dampedScrollEnabled || event.ctrlKey || Math.abs(event.deltaY) < 1) {
    return;
  }
  const target = event.target as Element | null;
  if (target?.closest('input, textarea, select, [contenteditable="true"], .ant-modal, .ant-drawer, .ant-select-dropdown')) {
    return;
  }
  event.preventDefault();
  dampedTargetY = clampScroll(dampedTargetY + event.deltaY * 1.08);
  if (dampedScrollFrame === undefined) {
    dampedScrollFrame = window.requestAnimationFrame(animateDampedScroll);
  }
}

function enableDampedScroll() {
  const finePointer = window.matchMedia('(hover: hover) and (pointer: fine)').matches;
  const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  dampedScrollEnabled = finePointer && !reducedMotion;
  if (!dampedScrollEnabled) {
    return;
  }
  dampedTargetY = window.scrollY;
  dampedCurrentY = window.scrollY;
  window.addEventListener('wheel', onDampedWheel, { passive: false });
}

function disableDampedScroll() {
  window.removeEventListener('wheel', onDampedWheel);
  if (dampedScrollFrame !== undefined) {
    window.cancelAnimationFrame(dampedScrollFrame);
    dampedScrollFrame = undefined;
  }
}
/** 企业形象大图（取自 CMS 素材库 · 关于天信 / 厂区风貌） */
const heroSlides = [
  {
    img: '/files/assets/关于天信/厂区风貌/厂区风貌_1651042980272159.png',
    sub: '树天信品牌 · 创一流企业',
    title: '天信管业科技集团有限公司',
  },
  {
    img: '/files/assets/关于天信/厂区风貌/厂区风貌_1651043040835094.png',
    sub: '防腐保温专业承包一级资质',
    title: '专注管道防腐与保温二十年',
  },
  {
    img: '/files/assets/关于天信/厂区风貌/厂区风貌_1651043080249263.png',
    sub: '42 项专利技术 · 83 亩生产基地',
    title: '研发驱动 · 智能制造',
  },
  {
    img: '/files/assets/关于天信/厂区风貌/厂区风貌_1651043143842341.png',
    sub: '延长油田 · 长庆油田长期合作单位',
    title: '工程业绩遍布能源行业',
  },
];

const heroIndex = ref(0);
const dragging = ref(false);
const dragOffset = ref(0);
const hero = computed(() => heroSlides[heroIndex.value]);
let heroTimer: number | undefined;
let dragStartX = 0;

/** 自动播放 */
function startHeroTimer() {
  if (heroTimer) {
    window.clearInterval(heroTimer);
  }
  heroTimer = window.setInterval(() => {
    heroIndex.value = (heroIndex.value + 1) % heroSlides.length;
  }, 6000);
}

function stopHeroTimer() {
  if (heroTimer) {
    window.clearInterval(heroTimer);
    heroTimer = undefined;
  }
}

function goHero(step: number) {
  const total = heroSlides.length;
  heroIndex.value = (heroIndex.value + step + total) % total;
  startHeroTimer();
}

function setHero(index: number) {
  heroIndex.value = index;
  startHeroTimer();
}

/** 鼠标 / 触摸拖拽换图 */
function onPointerDown(event: PointerEvent) {
  dragging.value = true;
  dragStartX = event.clientX;
  dragOffset.value = 0;
  stopHeroTimer();
  const target = event.currentTarget as HTMLElement | null;
  try {
    target?.setPointerCapture?.(event.pointerId);
  } catch {
    // 忽略不支持指针捕获的环境
  }
}

function onPointerMove(event: PointerEvent) {
  if (!dragging.value) {
    return;
  }
  const delta = event.clientX - dragStartX;
  dragOffset.value = Math.max(-160, Math.min(160, delta));
}

function onPointerUp(event: PointerEvent) {
  if (!dragging.value) {
    return;
  }
  dragging.value = false;
  const delta = event.clientX - dragStartX;
  dragOffset.value = 0;
  if (Math.abs(delta) >= 55) {
    goHero(delta < 0 ? 1 : -1);
  } else {
    startHeroTimer();
  }
}

onMounted(() => {
  heroSlides.forEach((slide) => {
    const image = new Image();
    image.src = slide.img;
  });
  startHeroTimer();
  enableDampedScroll();
});

onUnmounted(() => {
  stopHeroTimer();
  disableDampedScroll();
});
</script>

<template>
  <div class="portal-cms-page">
    <div class="portal-shell-wrap">
      <!-- 1) 智能检索 -->
      <section class="cms-modules">
        <LawSearchPanel portal />
      </section>

      <!-- 2) 统计条 -->
      <PortalServiceSections part="stats" />

      <!-- 3) 通知公告 / 政策动态 -->
      <PortalContentSections part="news" />

      <!-- 4) 法规库 / 政策库 -->
      <PortalServiceSections part="rest" />

      <!-- 5) 推荐阅读 / 产品中心 / 关于天信 / 技术资料 -->
      <PortalContentSections part="rest" />
    </div>

    <!-- 回到顶部（回到智能检索区） -->
    <button class="portal-back-top" type="button" title="回到顶部" @click="backToTop">↑</button>
  </div>
</template>

<style scoped>
.portal-cms-page {
  width: 100%;
  overflow-x: hidden;
  padding-bottom: 40px;
}

/* 回到顶部：固定在右下角（避开右下角助手悬浮球） */
.portal-back-top {
  position: fixed;
  right: 26px;
  bottom: 132px;
  z-index: 60;
  display: grid;
  width: 44px;
  height: 44px;
  font-size: 20px;
  color: #fff;
  cursor: pointer;
  background: #1a4480;
  border: 0;
  border-radius: 50%;
  box-shadow: 0 6px 18px rgb(10 37 64 / 28%);
  place-items: center;
  transition: background 0.2s ease, transform 0.2s ease;
}

.portal-back-top:hover {
  background: #0a2540;
  transform: translateY(-2px);
}

/* 企业形象大图（参考延安大学官网首页：大图 + 缓慢推进 + 拖拽切换） */
.tx-hero {
  position: relative;
  width: 100vw;
  height: clamp(300px, 30vw, 460px);
  margin-right: calc(50% - 50vw);
  margin-left: calc(50% - 50vw);
  overflow: hidden;
  cursor: grab;
  background: #10182b;
  user-select: none;
  touch-action: pan-y;
}

.tx-hero.dragging {
  cursor: grabbing;
}

.tx-hero-stage {
  position: absolute;
  inset: 0;
  will-change: transform;
  transition: transform 0.5s cubic-bezier(0.22, 0.61, 0.36, 1);
}

.tx-hero.dragging .tx-hero-stage {
  transition: none;
}

.tx-hero-slide {
  position: absolute;
  inset: 0;
  opacity: 0;
  transition: opacity 1.1s cubic-bezier(0.4, 0, 0.2, 1);
}

.tx-hero-slide img {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
  transform: scale(1.02);
  -webkit-user-drag: none;
}

.tx-hero-slide.on {
  opacity: 1;
}

/* 微微动图：当前图 9 秒缓慢推进 */
.tx-hero-slide.on img {
  animation: txHeroKen 9s ease-out both;
}

@keyframes txHeroKen {
  from {
    transform: scale(1.02) translate3d(0, 0, 0);
  }

  to {
    transform: scale(1.1) translate3d(-1.2%, -1%, 0);
  }
}

.tx-hero::after {
  position: absolute;
  inset: 0;
  pointer-events: none;
  content: '';
  background: linear-gradient(
    180deg,
    rgb(0 0 0 / 18%) 0%,
    rgb(0 0 0 / 0%) 32%,
    rgb(0 0 0 / 62%) 100%
  );
}

.tx-hero-cap {
  position: absolute;
  right: 0;
  bottom: 0;
  left: 0;
  z-index: 4;
  padding: 70px 0 32px;
  pointer-events: none;
  animation: txHeroCap 0.9s cubic-bezier(0.22, 0.61, 0.36, 1) both;
}

.tx-hero-cap-inner {
  max-width: 1600px;
  padding: 0 24px;
  margin: 0 auto;
}

.tx-hero-cap h2 {
  font-size: clamp(22px, 2vw, 32px);
  font-weight: 700;
  color: #fff;
  letter-spacing: 4px;
  text-shadow: 0 2px 18px rgb(0 0 0 / 45%);
}

.tx-hero-cap p {
  margin-top: 10px;
  font-size: clamp(13px, 1vw, 15px);
  letter-spacing: 3px;
  color: #f0dfc4;
  text-shadow: 0 1px 10px rgb(0 0 0 / 40%);
}

@keyframes txHeroCap {
  from {
    opacity: 0;
    transform: translateY(16px);
  }

  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.tx-hero-arrow {
  position: absolute;
  top: 50%;
  z-index: 6;
  display: grid;
  width: 46px;
  height: 46px;
  font-size: 26px;
  line-height: 1;
  color: #fff;
  cursor: pointer;
  background: rgb(0 0 0 / 28%);
  border: 1px solid rgb(255 255 255 / 35%);
  border-radius: 50%;
  opacity: 0;
  backdrop-filter: blur(4px);
  transition: all 0.35s ease;
  transform: translateY(-50%);
  place-items: center;
}

.tx-hero:hover .tx-hero-arrow {
  opacity: 1;
}

.tx-hero-arrow:hover {
  background: #9e1b32;
  border-color: #9e1b32;
}

.tx-hero-arrow.prev {
  left: 28px;
}

.tx-hero-arrow.next {
  right: 28px;
}

.tx-hero-dots {
  position: absolute;
  right: 24px;
  bottom: 24px;
  z-index: 6;
  display: flex;
  gap: 10px;
  align-items: center;
}

.tx-hero-dots span {
  position: relative;
  width: 10px;
  height: 10px;
  overflow: hidden;
  cursor: pointer;
  background: rgb(255 255 255 / 55%);
  border-radius: 50%;
  transition: all 0.4s cubic-bezier(0.22, 0.61, 0.36, 1);
}

.tx-hero-dots span.on {
  width: 40px;
  background: rgb(255 255 255 / 35%);
  border-radius: 6px;
}

.tx-hero-dots span.on::after {
  position: absolute;
  inset: 0;
  content: '';
  background: #c9a961;
  border-radius: 6px;
  transform-origin: left center;
  animation: txHeroProgress 6s linear both;
}

@keyframes txHeroProgress {
  from {
    transform: scaleX(0);
  }

  to {
    transform: scaleX(1);
  }
}

.tx-hero-tip {
  position: absolute;
  bottom: 24px;
  left: 24px;
  z-index: 6;
  padding: 6px 14px;
  font-size: 12px;
  letter-spacing: 1px;
  color: #fff;
  pointer-events: none;
  background: rgb(0 0 0 / 30%);
  border: 1px solid rgb(255 255 255 / 22%);
  border-radius: 20px;
  opacity: 0;
  backdrop-filter: blur(4px);
  transition: opacity 0.35s ease;
}

.tx-hero:hover .tx-hero-tip {
  opacity: 1;
}

/* 检索区容器 */
.portal-shell-wrap {
  max-width: 1600px;
  padding: 18px 24px 0;
  margin: 0 auto;
}

.cms-modules {
  padding: 4px 24px 24px;
  padding: 18px 24px 24px;
  border-radius: 12px;
  box-shadow: 0 6px 20px rgb(10 37 64 / 8%);
}

.module-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 18px 0 12px;
  border-bottom: 1px solid #eef1f6;
}

.module-heading strong {
  display: block;
  font-size: 19px;
  font-weight: 700;
  color: #0a2540;
}

.module-heading span {
  font-size: 12px;
  letter-spacing: 2px;
  color: #8a93a3;
}

@media (max-width: 1024px) {
  .tx-hero {
    height: 320px;
  }

  .tx-hero-cap {
    padding: 70px 22px 26px;
  }

  .tx-hero-arrow {
    display: none;
  }

  .tx-hero-tip {
    display: none;
  }

  .tx-hero-dots {
    right: 22px;
  }
}
</style>
