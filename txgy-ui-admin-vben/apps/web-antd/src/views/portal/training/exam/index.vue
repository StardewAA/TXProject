<script lang="ts" setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';

import { Button, Carousel } from 'ant-design-vue';

import {
  CERT_FIELDS,
  EXAM_STEPS,
  LEVELS,
  NEWS,
  PANORAMA_DESC,
  RESOURCES,
} from './cert-data';
import { ROADMAP_GROUPS } from './roadmap-data';

defineOptions({ name: 'PortalTrainingExam' });

/** 顶部横幅：防腐保温行业主题 */
const BANNERS = [
  {
    badge: '2026 第三届',
    tag: 'HOT',
    title: '工程防水防腐保温行业大会',
    theme: 'blue',
    style: 'a',
    img: '/banner/banner2.png',
    sub: '合肥行业盛会，400 余名代表共商高质量发展',
    strong: '持证上岗 · 岗位人才认证专项激励',
    tips: ['认证报名截止：10.31', '活动申请截止：11.30'],
    cta: '查看认证计划',
    action: 'roadmap',
  },
  {
    badge: '全国大赛',
    tag: '进行中',
    title: '防腐保温职业技能竞赛',
    theme: 'amber',
    style: 'b',
    img: '/banner/banner3.png',
    sub: '「防水工」赛项全国选拔，以赛促学、以证提技',
    strong: '获奖选手可直通高级工认证',
    tips: ['初赛 · 复赛 · 全国决赛', '报名截止：06.30'],
    cta: '竞赛报名入口',
    action: 'exam',
    kicker: '以赛促学 · 以证提技',
  },
  {
    badge: '职业认证',
    tag: 'NEW',
    title: '防腐保温工程师认证体系',
    theme: 'green',
    style: 'c',
    img: '/banner/banner1.png',
    sub: '覆盖防腐工、保温工、检测员等核心岗位',
    strong: '三级递进：初级 · 中级 · 高级',
    tips: ['线上学习 + 实操实训', '证书行业互认'],
    cta: '查看认证体系',
    action: 'roadmap',
  },
  {
    badge: '绿色低碳',
    tag: '直播',
    title: '防腐保温一体化解决方案',
    theme: 'teal',
    style: 'd',
    img: '/banner/banner5.png',
    sub: '新材料、新工艺在储罐 / 管道 / 建筑中的应用',
    strong: '节能降耗 · 安全环保 · 全生命周期',
    tips: ['每周四 19:30 专家直播', 'VR 实操课程同步上线'],
    cta: '进入课程中心',
    action: 'courses',
  },
  {
    badge: '产教融合',
    tag: '推荐',
    title: '校企共建实训基地',
    theme: 'indigo',
    style: 'e',
    img: '/banner/banner4.png',
    sub: '真实工况场景实训，学完即上岗',
    strong: '就业直通 · 订单式人才培养',
    tips: ['真实工况场景', '订单式培养'],
    cta: '进入实训基地',
    action: 'vr',
    stats: [
      { num: '30+', label: '合作企业' },
      { num: '95%', label: '学员就业率' },
      { num: '12', label: '实训基地' },
    ],
  },
];

const BANNER_LINK: Record<string, string> = {
  roadmap: '/portal/training/exam/roadmap',
  exam: '/portal/training/exam/workspace',
  courses: '/portal/training/courses',
  vr: '/portal/training/vr',
};

function goBanner(action: string) {
  router.push(BANNER_LINK[action] ?? '/portal/training/workbench');
}

const bannerRef = ref();

function prevSlide() {
  bannerRef.value?.prev();
}

function nextSlide() {
  bannerRef.value?.next();
}

const router = useRouter();

function goLearningCenter() {
  router.push('/portal/training/workbench');
}

function goRoadmap() {
  router.push('/portal/training/exam/roadmap');
}
const STEP_LINKS = [
  '/portal/training/exam/booking',
  {
    path: '/portal/training/exam/doc',
    query: {
      title: '获取与验证证书',
      sub: '证书获取流程 · 在线验真指引',
      id: '101',
    },
  },
  '/portal/training/exam/recert',
] as const;

function onStep(index: number) {
  const link = STEP_LINKS[index];
  if (!link) return;
  if (typeof link === 'string') {
    router.push(link);
  } else {
    router.push(link);
  }
}

/** 学习资源「了解更多」 */
function goResource(link: string) {
  router.push(link);
}

/** 认证资讯公告 → 占位文档 */
function goNews(n: (typeof NEWS)[number]) {
  router.push({
    path: '/portal/training/exam/doc',
    query: { title: n.title, sub: n.sub, id: n.id },
  });
}

const activeField = ref('op');

const activeFieldGroup = computed(
  () => ROADMAP_GROUPS.find((g) => g.key === activeField.value) ?? ROADMAP_GROUPS[0]!,
);

function goCertDetail(jobName: string) {
  router.push({
    path: '/portal/training/exam/cert-detail',
    query: { field: activeField.value, job: jobName },
  });
}

const NAV_ITEMS = [
  { key: 'overview', label: '概述' },
  { key: 'certs', label: '职业认证' },
  { key: 'exam', label: '参加考试' },
  { key: 'resources', label: '学习资源' },
  { key: 'news', label: '认证资讯' },
];

const activeNav = ref('overview');
let programmaticScroll = false;

function scrollTo(key: string) {
  activeNav.value = key;
  programmaticScroll = true;
  document
    .getElementById(`cert-${key}`)
    ?.scrollIntoView({ behavior: 'smooth', block: 'start' });
  window.setTimeout(() => {
    programmaticScroll = false;
  }, 800);
}

function onScroll() {
  if (programmaticScroll) return;
  const offset = 120;
  let current = NAV_ITEMS[0]!.key;
  for (const n of NAV_ITEMS) {
    const el = document.getElementById(`cert-${n.key}`);
    if (el && el.getBoundingClientRect().top <= offset) {
      current = n.key;
    }
  }
  activeNav.value = current;
}

onMounted(() => window.addEventListener('scroll', onScroll, { passive: true }));
onBeforeUnmount(() => window.removeEventListener('scroll', onScroll));
</script>

<template>
  <div class="cert">
    <section class="top-banner">
      <Button class="banner-back" shape="round" @click="goLearningCenter">
        返回学习中心 →
      </Button>
      <Carousel
        ref="bannerRef"
        autoplay
        :autoplay-speed="5000"
        effect="fade"
        :dots="true"
      >
        <div
          v-for="(b, i) in BANNERS"
          :key="i"
          class="banner-slide"
          :class="[`theme-${b.theme}`, `style-${b.style}`]"
        >
          <img class="bg-photo" :src="b.img" alt="" />
          <span class="bg-mask"></span>
          <span class="bg-grid"></span>
          <span class="bg-glow g1"></span>
          <span class="bg-glow g2"></span>
          <span class="bg-wedge"></span>
          <span class="bg-shine"></span>

          <div class="banner-inner">
            <div class="banner-text">
              <p v-if="b.kicker" class="kicker">{{ b.kicker }}</p>
              <p class="eyebrow">
                <span class="badge">{{ b.badge }}</span>
                <span class="chip">{{ b.tag }}</span>
              </p>
              <h3 class="banner-title">{{ b.title }}</h3>
              <p class="banner-sub">{{ b.sub }}</p>
              <p class="banner-strong">{{ b.strong }}</p>
              <div class="banner-tips">
                <span v-for="t in b.tips" :key="t" class="tip">
                  <i>✓</i>{{ t }}
                </span>
              </div>
              <button class="banner-cta" @click="goBanner(b.action)">
                {{ b.cta }}
                <em>→</em>
              </button>
            </div>

            <div v-if="b.stats" class="banner-stats">
              <div v-for="s in b.stats" :key="s.label" class="stat">
                <b>{{ s.num }}</b>
                <span>{{ s.label }}</span>
              </div>
            </div>
            <div v-else class="banner-deco">
              <span class="halo"></span>
              <span class="ring r1"></span>
              <span class="ring r2"></span>
              <span class="ring r3"></span>
              <span class="core">
                <b>防腐</b>
                <b>保温</b>
              </span>
              <span class="dot d1"></span>
              <span class="dot d2"></span>
              <span class="dot d3"></span>
              <span class="seq">{{ String(i + 1).padStart(2, '0') }}<em>/05</em></span>
            </div>
          </div>
        </div>
      </Carousel>
      <button class="banner-nav left" aria-label="上一张" @click="prevSlide">
        <svg viewBox="0 0 24 24" width="16" height="16" fill="none">
          <path
            d="M14.5 6.5 9 12l5.5 5.5"
            stroke="currentColor"
            stroke-width="2.4"
            stroke-linecap="round"
            stroke-linejoin="round"
          />
        </svg>
      </button>
      <button class="banner-nav right" aria-label="下一张" @click="nextSlide">
        <svg viewBox="0 0 24 24" width="16" height="16" fill="none">
          <path
            d="m9.5 6.5 5.5 5.5-5.5 5.5"
            stroke="currentColor"
            stroke-width="2.4"
            stroke-linecap="round"
            stroke-linejoin="round"
          />
        </svg>
      </button>
    </section>

    <nav class="anchor-nav">
      <div class="nav-inner">
        <a
          v-for="n in NAV_ITEMS"
          :key="n.key"
          :class="['nav-item', { on: activeNav === n.key }]"
          @click="scrollTo(n.key)"
        >{{ n.label }}</a>
      </div>
    </nav>

    <section id="cert-overview" class="card">
      <h1 class="page-title">职业认证全景图</h1>
      <p class="page-desc">{{ PANORAMA_DESC }}</p>
      <div class="panorama">
        <img src="/cert-panorama.jpg" alt="职业认证全景图" class="panorama-img" />
      </div>

      <h2 class="sec-title center">推荐职业认证进阶路线</h2>
      <p class="sec-desc center">根据从业者学习与进阶需求，职业认证分为初级、中级和高级三个认证等级。</p>
      <div class="levels">
        <div v-for="lv in LEVELS" :key="lv.code" class="level">
          <span class="lv-tag">{{ lv.code }}</span>
          <strong>{{ lv.name }}</strong>
          <p>{{ lv.desc }}</p>
        </div>
      </div>
      <div class="more-wrap">
        <a class="more" @click="goRoadmap">职业认证进阶路径 »</a>
      </div>
    </section>

    <section id="cert-certs" class="card">
      <h2 class="sec-title center">职业认证</h2>
      <div class="field-tabs">
        <div
          v-for="f in CERT_FIELDS"
          :key="f.key"
          :class="['field-tab', { on: activeField === f.key }]"
          @click="activeField = f.key"
        >
          <span class="field-icon" :style="{ background: f.color }">{{ f.icon }}</span>
          <span class="field-name">{{ f.name }}</span>
        </div>
      </div>
      <div class="job-band">
        <a
          v-for="j in activeFieldGroup.jobs"
          :key="j.name"
          class="col-item"
          :title="j.name"
          @click="goCertDetail(j.name)"
        >{{ j.name }}</a>
      </div>
    </section>

    <section id="cert-exam" class="card">
      <h2 class="sec-title center">参加考试</h2>
      <div class="steps">
        <div v-for="(s, i) in EXAM_STEPS" :key="s.title" class="step">
          <span class="num">{{ i + 1 }}</span>
          <strong>{{ s.title }}</strong>
          <p>{{ s.text }}</p>
          <a class="link" @click="onStep(i)">{{ s.action }}</a>
        </div>
      </div>
    </section>

    <section id="cert-resources" class="card">
      <h2 class="sec-title center">学习资源</h2>
      <div class="res">
        <div v-for="r in RESOURCES" :key="r.title" class="res-item">
          <strong>{{ r.title }}</strong>
          <p>{{ r.sub }}</p>
          <a class="link" @click="goResource(r.link)">{{ r.action }} »</a>
        </div>
      </div>
    </section>

    <section id="cert-news" class="card">
      <h2 class="sec-title center">认证资讯</h2>
      <div class="news">
        <a v-for="n in NEWS" :key="n.title" class="news-item" @click="goNews(n)">
          <span>{{ n.title }}</span>
          <em>{{ n.date }}</em>
        </a>
      </div>
    </section>

  </div>
</template>

<style lang="scss" scoped>
.cert {
  max-width: 1200px;
  padding: 0 0 32px;
}

.top-banner {
  position: relative;
  overflow: hidden;
  margin-bottom: 12px;
  background: #eee;
  border-radius: 8px;
}

/* 轮播右上角返回按钮 */
.banner-back {
  position: absolute;
  top: 14px;
  right: 16px;
  z-index: 6;
  color: #fff;
  background: rgb(15 23 41 / 35%);
  border: 1px solid rgb(255 255 255 / 45%);
  backdrop-filter: blur(6px);

  &:hover {
    color: #0f1729;
    background: #fff;
    border-color: #fff;
  }
}

/* 现场照片背景与暗色蒙层 */
.bg-photo {
  position: absolute;
  inset: 0;
  z-index: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
  filter: saturate(1.08) contrast(1.05) brightness(0.98);
}

/* 版式 A：文字在左，暗角自左向右渐隐 */
.bg-mask {
  position: absolute;
  inset: 0;
  z-index: 1;
  background: radial-gradient(
    118% 150% at 18% 50%,
    rgb(8 16 30 / 40%) 0%,
    rgb(8 16 30 / 62%) 55%,
    rgb(6 12 24 / 80%) 100%
  );
}

/* ---------- 横幅幻灯片 ---------- */
.banner-slide {
  position: relative;
  height: 0;
  padding-bottom: 33.2%;
  overflow: hidden;
  isolation: isolate;
}

.theme-blue {
  --accent: #7fb4ff;

  background: linear-gradient(105deg, #16233d 0%, #1e3a63 48%, #2b5c94 100%);
}

.theme-amber {
  --accent: #ffc978;

  background: linear-gradient(105deg, #2b1f16 0%, #4a3220 48%, #7d5629 100%);
}

.theme-green {
  --accent: #7fe3b4;

  background: linear-gradient(105deg, #12281f 0%, #1d4635 48%, #2f6d51 100%);
}

.theme-teal {
  --accent: #7fe6dc;

  background: linear-gradient(105deg, #10272b 0%, #1b474d 48%, #2c6f72 100%);
}

.theme-indigo {
  --accent: #b3bcff;

  background: linear-gradient(105deg, #1d2038 0%, #333a63 48%, #4d5690 100%);
}

/* 斜向网格纹理 */
.bg-grid {
  position: absolute;
  inset: 0;
  background-image:
    repeating-linear-gradient(
      60deg,
      rgb(255 255 255 / 6%) 0 1px,
      transparent 1px 26px
    ),
    repeating-linear-gradient(
      -60deg,
      rgb(255 255 255 / 5%) 0 1px,
      transparent 1px 26px
    );
  opacity: 0.7;
}

.bg-glow {
  position: absolute;
  border-radius: 50%;
  filter: blur(60px);
}

.g1 {
  top: -80px;
  right: 8%;
  width: 420px;
  height: 300px;
  background: var(--accent);
  opacity: 0.14;
}

.g2 {
  bottom: -120px;
  left: 12%;
  width: 380px;
  height: 260px;
  background: var(--accent);
  opacity: 0.09;
}

/* 缓慢扫过的高光 */
.bg-shine {
  position: absolute;
  inset: 0;
  background: linear-gradient(
    100deg,
    transparent 20%,
    rgb(255 255 255 / 14%) 45%,
    transparent 70%
  );
  animation: shine 7s ease-in-out infinite;
}

@keyframes shine {
  0%,
  100% {
    transform: translateX(-40%);
    opacity: 0;
  }

  35%,
  55% {
    opacity: 1;
  }

  90% {
    transform: translateX(40%);
    opacity: 0;
  }
}

/* 斜切色块（部分版式启用） */
.bg-wedge {
  position: absolute;
  inset: 0;
  display: none;
}

.kicker {
  display: none;
  margin: 0 0 10px;
  font-size: 13px;
  font-weight: 600;
  letter-spacing: 6px;
  color: var(--accent);
  text-transform: uppercase;
}

.banner-stats {
  position: relative;
  z-index: 2;
  display: flex;
  gap: 26px;
  flex-shrink: 0;
  padding: 18px 24px;
  background: rgb(255 255 255 / 10%);
  border: 1px solid rgb(255 255 255 / 20%);
  border-radius: 14px;
  backdrop-filter: blur(8px);

  .stat {
    display: flex;
    flex-direction: column;
    gap: 2px;
    align-items: center;
    min-width: 74px;

    b {
      font-size: 26px;
      font-weight: 800;
      line-height: 1.1;
      color: var(--accent);
    }

    span {
      font-size: 12px;
      color: rgb(255 255 255 / 70%);
    }
  }
}

.banner-inner {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 13% 0 6%;
}

/* ========== 版式 A：左文 + 右侧旋转光环（默认） ========== */
.style-a .bg-grid {
  background-image:
    repeating-linear-gradient(60deg, rgb(255 255 255 / 6%) 0 1px, transparent 1px 26px),
    repeating-linear-gradient(-60deg, rgb(255 255 255 / 5%) 0 1px, transparent 1px 26px);
}

/* ========== 版式 B：居中对称 + 波点纹理 ========== */
.style-b {
  .bg-mask {
    background: radial-gradient(
      92% 128% at 50% 50%,
      rgb(8 16 30 / 46%) 0%,
      rgb(8 16 30 / 66%) 62%,
      rgb(6 12 24 / 80%) 100%
    );
  }

  .bg-grid {
    background-image: radial-gradient(rgb(255 255 255 / 12%) 1px, transparent 1px);
    background-size: 22px 22px;
    opacity: 0.8;
  }

  .bg-glow.g1 {
    right: 50%;
    transform: translateX(50%);
    opacity: 0.28;
  }

  .banner-inner {
    justify-content: center;
    padding: 0 6%;
  }

  .banner-text {
    text-align: center;

    .eyebrow {
      justify-content: center;
    }

    .kicker {
      display: block;
    }

    .banner-tips {
      justify-content: center;
    }
  }

  .banner-title {
    font-size: 34px;
  }

  .banner-deco {
    position: absolute;
    top: 50%;
    left: 50%;
    z-index: 1;
    width: 340px;
    height: 260px;
    opacity: 0.3;
    transform: translate(-50%, -50%);
  }
}

/* ========== 版式 C：镜像布局（文字在右）+ 竖条纹 ========== */
.style-c {
  .bg-mask {
    background: radial-gradient(
      118% 150% at 82% 50%,
      rgb(8 16 30 / 40%) 0%,
      rgb(8 16 30 / 62%) 55%,
      rgb(6 12 24 / 80%) 100%
    );
  }

  .bg-grid {
    background-image: repeating-linear-gradient(
      90deg,
      rgb(255 255 255 / 7%) 0 2px,
      transparent 2px 30px
    );
  }

  .banner-inner {
    flex-direction: row-reverse;
    padding: 0 6% 0 13%;
  }

  .banner-deco .r3 {
    border-radius: 10px;
  }

  .banner-strong {
    font-size: 15px;
  }
}

/* ========== 版式 D：底部对齐 + 斜切色块 + 横幅式大标题 ========== */
.style-d {
  .bg-mask {
    background: linear-gradient(
      to top,
      rgb(6 12 24 / 82%) 0%,
      rgb(7 14 26 / 52%) 46%,
      rgb(8 16 30 / 34%) 100%
    );
  }

  .bg-grid {
    background-image: repeating-linear-gradient(
      -45deg,
      rgb(255 255 255 / 6%) 0 1px,
      transparent 1px 18px
    );
  }

  .bg-wedge {
    display: block;
    background: linear-gradient(100deg, transparent 52%, rgb(255 255 255 / 10%) 52%);
    clip-path: polygon(52% 0, 100% 0, 100% 100%, 38% 100%);
  }

  .banner-inner {
    align-items: flex-end;
    padding: 0 10% 12% 6%;
  }

  .banner-title {
    font-size: 32px;
  }

  .banner-deco {
    width: 180px;
    height: 140px;

    .r1 {
      top: 0;
      right: 0;
      width: 110px;
      height: 110px;
    }

    .r2 {
      bottom: 0;
      left: 0;
      width: 76px;
      height: 76px;
    }

    .r3 {
      display: none;
    }
  }
}

/* ========== 版式 E：左文 + 右侧数据面板 + 斜切分割 ========== */
.style-e {
  .bg-mask {
    background: linear-gradient(
      95deg,
      rgb(6 12 24 / 76%) 0%,
      rgb(7 14 26 / 50%) 52%,
      rgb(8 16 30 / 34%) 100%
    );
  }

  .bg-grid {
    background-image:
      linear-gradient(90deg, rgb(255 255 255 / 5%) 0 1px, transparent 1px),
      linear-gradient(rgb(255 255 255 / 5%) 0 1px, transparent 1px);
    background-size: 40px 40px;
  }

  .bg-wedge {
    display: block;
    background: rgb(255 255 255 / 8%);
    clip-path: polygon(62% 0, 100% 0, 100% 100%, 48% 100%);
  }

  .banner-inner {
    padding: 0 6% 0 6%;
  }

  .banner-title {
    font-size: 28px;
  }

  .banner-cta {
    margin-top: 14px;
  }
}

.banner-text {
  position: relative;
  z-index: 2;
  color: #fff;

  .eyebrow {
    display: flex;
    gap: 8px;
    align-items: center;
    margin: 0 0 12px;
  }

  .badge {
    flex-shrink: 0;
    padding: 3px 9px;
    font-size: 13px;
    font-weight: 600;
    letter-spacing: 0.5px;
    color: #0f1729;
    background: var(--accent);
    border-radius: 4px;
  }

  .chip {
    padding: 2px 8px;
    font-size: 12px;
    color: var(--accent);
    border: 1px solid var(--accent);
    border-radius: 999px;
    animation: pulse-chip 2.4s ease-in-out infinite;
  }

  @keyframes pulse-chip {
    0%,
    100% {
      opacity: 0.65;
    }

    50% {
      opacity: 1;
    }
  }

  .banner-title {
    margin: 0;
    font-size: 30px;
    font-weight: 800;
    line-height: 1.2;
    letter-spacing: 2px;
    text-shadow: 0 6px 18px rgb(0 0 0 / 35%);
  }

  .banner-sub {
    margin: 8px 0 0;
    font-size: 15px;
    color: rgb(255 255 255 / 82%);
  }

  .banner-strong {
    display: inline-block;
    margin: 12px 0 0;
    padding: 4px 0 4px 12px;
    font-size: 16px;
    font-weight: 700;
    color: #fff;
    border-left: 3px solid var(--accent);
  }

  .banner-tips {
    display: flex;
    gap: 10px;
    margin-top: 12px;
  }

  .tip {
    display: inline-flex;
    gap: 6px;
    align-items: center;
    padding: 3px 10px;
    font-size: 13px;
    color: rgb(255 255 255 / 85%);
    background: rgb(255 255 255 / 10%);
    border: 1px solid rgb(255 255 255 / 14%);
    border-radius: 999px;
    backdrop-filter: blur(4px);
  }

  .tip i {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 14px;
    height: 14px;
    font-size: 10px;
    font-style: normal;
    color: #0f1729;
    background: var(--accent);
    border-radius: 50%;
  }
}

.banner-cta {
  display: inline-flex;
  gap: 6px;
  align-items: center;
  margin-top: 16px;
  padding: 8px 18px;
  font-size: 14px;
  font-weight: 600;
  color: #fff;
  cursor: pointer;
  background: rgb(255 255 255 / 12%);
  border: 1px solid rgb(255 255 255 / 38%);
  border-radius: 999px;
  transition: all 0.25s;

  em {
    font-style: normal;
    transition: transform 0.25s;
  }

  &:hover {
    color: #0f1729;
    background: var(--accent);
    border-color: var(--accent);
    box-shadow: 0 6px 18px rgb(0 0 0 / 28%);

    em {
      transform: translateX(4px);
    }
  }
}

.banner-deco {
  position: relative;
  z-index: 2;
  width: 220px;
  height: 180px;
  flex-shrink: 0;

  .halo {
    position: absolute;
    top: 50%;
    left: 50%;
    width: 170px;
    height: 170px;
    background: var(--accent);
    border-radius: 50%;
    filter: blur(42px);
    opacity: 0.35;
    transform: translate(-50%, -50%);
  }

  .ring {
    position: absolute;
    border-radius: 50%;
  }

  .r1 {
    top: 4px;
    right: 0;
    width: 150px;
    height: 150px;
    border: 1px solid rgb(255 255 255 / 22%);
    animation: spin 22s linear infinite;

    &::after {
      position: absolute;
      top: -4px;
      left: 50%;
      width: 8px;
      height: 8px;
      content: '';
      background: var(--accent);
      border-radius: 50%;
      box-shadow: 0 0 12px var(--accent);
    }
  }

  .r2 {
    bottom: 12px;
    left: 6px;
    width: 96px;
    height: 96px;
    border: 1px dashed rgb(255 255 255 / 24%);
    animation: spin 16s linear infinite reverse;
  }

  .r3 {
    top: 26px;
    left: 34px;
    width: 130px;
    height: 130px;
    border: 1px solid rgb(255 255 255 / 12%);
    background: linear-gradient(140deg, rgb(255 255 255 / 10%), transparent 60%);
    backdrop-filter: blur(1px);
  }

  @keyframes spin {
    to {
      transform: rotate(360deg);
    }
  }

  .core {
    position: absolute;
    top: 50%;
    left: 50%;
    display: flex;
    flex-direction: column;
    gap: 2px;
    align-items: center;
    justify-content: center;
    padding: 16px 20px;
    border-radius: 16px;
    background: rgb(255 255 255 / 10%);
    border: 1px solid rgb(255 255 255 / 26%);
    transform: translate(-50%, -50%);
    backdrop-filter: blur(6px);

    b {
      font-size: 19px;
      font-weight: 800;
      letter-spacing: 4px;
      color: #fff;
      text-shadow: 0 0 14px rgb(255 255 255 / 45%);
    }
  }

  .dot {
    position: absolute;
    background: var(--accent);
    border-radius: 50%;
    opacity: 0.9;
  }

  .d1 {
    top: 8px;
    left: 26px;
    width: 7px;
    height: 7px;
    animation: float 5s ease-in-out infinite;
  }

  .d2 {
    right: 22px;
    bottom: 8px;
    width: 11px;
    height: 11px;
    animation: float 6.5s ease-in-out infinite 0.6s;
  }

  .d3 {
    top: 46px;
    right: -6px;
    width: 5px;
    height: 5px;
    animation: float 4.2s ease-in-out infinite 1.2s;
  }

  @keyframes float {
    0%,
    100% {
      transform: translateY(0);
      opacity: 0.45;
    }

    50% {
      transform: translateY(-10px);
      opacity: 1;
    }
  }

  .seq {
    position: absolute;
    right: 0;
    bottom: -6px;
    font-size: 34px;
    font-weight: 800;
    line-height: 1;
    color: rgb(255 255 255 / 16%);
    letter-spacing: 1px;

    em {
      font-size: 14px;
      font-style: normal;
      font-weight: 600;
      color: rgb(255 255 255 / 35%);
    }
  }
}

/* ---------- 左右切换按钮 ---------- */
.banner-nav {
  position: absolute;
  top: 50%;
  z-index: 5;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 38px;
  height: 38px;
  font-size: 16px;
  color: #fff;
  cursor: pointer;
  background: rgb(255 255 255 / 25%);
  border: none;
  border-radius: 50%;
  transform: translateY(-50%);
  transition: background 0.2s;

  &:hover {
    background: rgb(255 255 255 / 45%);
  }

  &.left {
    left: 14px;
  }

  &.right {
    right: 14px;
  }
}

.top-banner :deep(.slick-dots) {
  bottom: 14px;
}

.top-banner :deep(.slick-dots li) {
  width: 40px;
  height: 4px;
  margin: 0 4px;
}

.top-banner :deep(.slick-dots li button) {
  width: 40px;
  height: 4px;
  background: rgb(255 255 255 / 50%);
  border-radius: 2px;
  opacity: 1;
}

.top-banner :deep(.slick-dots li.slick-active button) {
  background: #fff;
}

.anchor-nav {
  position: sticky;
  top: 0;
  z-index: 20;
  margin-bottom: 16px;
  background: #fff;
  border-bottom: 1px solid #f0f0f0;
  box-shadow: 0 2px 8px rgb(0 0 0 / 4%);
}

.nav-inner {
  display: flex;
  justify-content: space-around;
  max-width: 1200px;
  margin: 0 auto;
}

.nav-item {
  position: relative;
  padding: 14px 8px;
  color: #333;
  font-size: 15px;
  cursor: pointer;
  transition: color 0.2s;

  &:hover {
    color: #1257c9;
  }

  &.on {
    color: #1257c9;
    font-weight: 600;

    &::after {
      position: absolute;
      right: 20%;
      bottom: 0;
      left: 20%;
      height: 3px;
      content: '';
      background: #1257c9;
      border-radius: 2px;
    }
  }
}

.card {
  scroll-margin-top: 60px;
}

.page-title {
  margin: 8px 0 10px;
  font-size: 30px;
  font-weight: 700;
  color: #1f2329;
  text-align: center;
}

.page-desc {
  max-width: 820px;
  margin: 0 auto 24px;
  color: #646a73;
  font-size: 14px;
  line-height: 1.8;
  text-align: center;
}

.sec-title.center {
  margin-top: 32px;
  text-align: center;
}

.sec-desc.center {
  text-align: center;
}

.more-wrap {
  margin-top: 16px;
  text-align: center;
}





.card {
  padding: 24px;
  margin-bottom: 16px;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 2px 12px rgb(15 34 67 / 8%);
  transition: box-shadow 0.2s;

  &:hover {
    box-shadow: 0 8px 24px rgb(15 34 67 / 12%);
  }
}

.sec-title {
  position: relative;
  margin: 0 0 8px;
  font-size: 26px;
  font-weight: 700;
}

.sec-desc {
  margin: 0 0 16px;
  color: #8c8c8c;
  font-size: 14px;
}

.panorama {
  overflow: hidden;
  background: linear-gradient(180deg, #eef4ff, #fff);
  border: 1px solid #e6f0ff;
  border-radius: 10px;
  line-height: 0;
}

.panorama-img {
  display: block;
  width: 100%;
  height: auto;
}

.levels {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 12px;
}

.level {
  padding: 18px;
  background: linear-gradient(160deg, #f4f8ff, #fff);
  border: 1px solid #e6f0ff;
  border-radius: 10px;
  transition: transform 0.2s, box-shadow 0.2s;

  &:hover {
    box-shadow: 0 8px 20px rgb(18 87 201 / 14%);
    transform: translateY(-2px);
  }

  strong {
    display: block;
    margin: 6px 0 4px;
    font-size: 16px;
  }

  p {
    margin: 0;
    color: #8c8c8c;
    font-size: 13px;
    line-height: 1.6;
  }
}

.lv-tag {
  padding: 3px 10px;
  color: #fff;
  font-size: 12px;
  background: linear-gradient(90deg, #2f8bff, #1257c9);
  border-radius: 12px;
}

.more {
  display: inline-block;
  margin-top: 16px;
  color: #1257c9;
  font-size: 16px;
  font-weight: 600;
  cursor: pointer;

  &:hover {
    text-decoration: underline;
  }
}

.field-tabs {
  display: flex;
  justify-content: space-around;
  gap: 8px;
  padding: 18px 0 20px;
  border-bottom: 1px solid #f0f2f5;
}

.field-tab {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  color: #595959;
  font-size: 13px;
  cursor: pointer;
  transition: color 0.2s;

  .field-icon {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 52px;
    height: 52px;
    color: #fff;
    font-size: 22px;
    font-weight: 700;
    border-radius: 14px;
    transition:
      transform 0.2s,
      box-shadow 0.2s;
  }

  &:hover {
    color: #1257c9;

    .field-icon {
      transform: translateY(-3px);
      box-shadow: 0 8px 16px rgb(18 87 201 / 20%);
    }
  }

  &.on {
    color: #1257c9;
    font-weight: 600;
  }
}

.job-band {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: 4px 24px;
  padding: 28px 32px;
  margin-top: 20px;
  background: #f5f7fa;
  border-radius: 4px;
}

.col-item {
  display: block;
  overflow: hidden;
  padding: 8px 0;
  color: #595959;
  font-size: 15px;
  white-space: nowrap;
  cursor: pointer;
  text-overflow: ellipsis;
  transition: color 0.2s;

  &:hover {
    color: #1257c9;
  }
}

.steps {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
}

.step {
  padding: 18px 16px;
  background: linear-gradient(160deg, #f4f8ff, #fff);
  border: 1px solid #e6f0ff;
  border-radius: 10px;
  transition: transform 0.2s, box-shadow 0.2s;

  &:hover {
    box-shadow: 0 8px 20px rgb(18 87 201 / 14%);
    transform: translateY(-2px);
  }

  strong {
    display: block;
    margin: 8px 0 6px;
    font-size: 15px;
  }

  p {
    margin: 0 0 8px;
    color: #8c8c8c;
    font-size: 13px;
    line-height: 1.6;
  }
}

.num {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  color: #fff;
  font-size: 13px;
  font-weight: 700;
  background: linear-gradient(135deg, #2f8bff, #0a2a6b);
  border-radius: 50%;
  box-shadow: 0 4px 10px rgb(18 87 201 / 30%);
}

.link {
  color: #1677ff;
  font-size: 13px;
  cursor: pointer;
}

.res {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
}

.res-item {
  padding: 18px;
  background: linear-gradient(160deg, #f7faff, #fff);
  border: 1px solid #edf3ff;
  border-radius: 10px;
  transition: transform 0.2s, box-shadow 0.2s;

  &:hover {
    box-shadow: 0 8px 20px rgb(18 87 201 / 14%);
    transform: translateY(-2px);
  }

  strong {
    font-size: 16px;
  }

  p {
    margin: 4px 0 8px;
    color: #8c8c8c;
    font-size: 13px;
  }
}

.news-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 0;
  font-size: 14px;
  border-bottom: 1px dashed #eef1f6;
  transition: color 0.2s;

  &:hover {
    color: #1257c9;
  }

  em {
    color: #bfbfbf;
    font-size: 12px;
    font-style: normal;
  }
}
</style>
