<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue';
import { useRouter } from 'vue-router';

import { IconifyIcon } from '@vben/icons';
import { useUserStore } from '@vben/stores';

import { getHrmRecruitFilePreviewUrl } from '#/api/hrm/portal/recruit/file-url';
import recruitAssistantRobot from '#/assets/imgs/recruit-assistant-robot.webp';

import { loadRecruitAvatar, recruitAvatarUrl } from './recruit-avatar';
import ResumeToolsMenu from './resume-tools-menu.vue';

const props = withDefaults(
  defineProps<{
    active?: 'companies' | 'home' | 'jobs' | 'messages' | 'resume' | 'user';
    displayName?: string;
    embedded?: boolean;
    guardProfileActions?: boolean;
    showResumeTools?: boolean;
    themed?: boolean;
  }>(),
  {
    active: 'home',
    displayName: '',
    embedded: false,
    guardProfileActions: false,
    showResumeTools: true,
    themed: true,
  },
);

const emit = defineEmits<{
  resumeAi: [];
  resumeEdit: [];
  resumeGenerate: [];
  resumeUpload: [];
  resumeVersion: [];
  section: [section: 'companies' | 'home' | 'jobs' | 'messages' | 'resume' | 'user'];
}>();

const router = useRouter();
const userStore = useUserStore();
const isScrolled = ref(false);
const accountName = computed(() => {
  const name = props.displayName || userStore.userInfo?.nickname || userStore.userInfo?.username || '用户';
  return /^1\d{10}$/.test(name) ? `${name.slice(0, 3)}****${name.slice(-4)}` : name;
});

const accountAvatarUrl = computed(() =>
  getHrmRecruitFilePreviewUrl(recruitAvatarUrl.value),
);
/** 图挂了（比如文件被删）就退回默认图标，别在导航里留一个裂图 */
const avatarFailed = ref(false);

watch(accountAvatarUrl, () => {
  avatarFailed.value = false;
});

function go(section: 'companies' | 'home' | 'jobs' | 'messages' | 'resume' | 'user') {
  if (
    props.guardProfileActions &&
    (section === 'messages' || section === 'resume' || section === 'user')
  ) {
    emit('section', section);
    return;
  }
  if (section === 'home') {
    void router.push({ name: 'PortalRecruit' });
    return;
  }
  if (section === 'jobs' || section === 'companies') {
    void router.push({ name: 'PortalRecruit', query: { section } });
    return;
  }
  void router.push({ name: 'PortalRecruit', query: { section } });
}

function returnHome() {
  void router.push('/portal/home');
}

/** 吊在导航条下面的机器人：点它进求职助理 */
function openAssistant() {
  void router.push({ name: 'AiResumeHome' });
}

/** 机器人是否已收起。点绳子收上去，留在栏下的那一小截绳子就是放下来的把手 */
const mascotStowed = ref(false);

function syncScrollState() {
  isScrolled.value = window.scrollY > 8;
}

onMounted(() => {
  syncScrollState();
  window.addEventListener('scroll', syncScrollState, { passive: true });
  void loadRecruitAvatar();
});

onUnmounted(() => {
  window.removeEventListener('scroll', syncScrollState);
});
</script>

<template>
  <header
    v-if="!embedded"
    class="recruit-topbar"
    :class="{ 'is-themed': themed, 'is-scrolled': isScrolled }"
  >
    <button class="recruit-topbar-brand" type="button" aria-label="天信招聘首页" @click="go('home')">
      <img src="/hrm/brand-logo.png" alt="天信管业" />
      <span><strong>天信招聘</strong><small>TIANXIN CAREERS</small></span>
    </button>

    <nav class="recruit-topbar-nav" aria-label="招聘导航">
      <button :class="{ active: active === 'home' }" type="button" @click="go('home')">首页</button>
      <button :class="{ active: active === 'jobs' }" type="button" @click="go('jobs')">职位</button>
      <button :class="{ active: active === 'companies' }" type="button" @click="go('companies')">公司</button>
    </nav>

    <!--
      求职助理入口：机器人吊在导航条下沿。

      绳子是画在图片里的 —— 手真的攥着它，所以没有单独的绳子元素，
      只在绳子上方盖了一块透明按钮，用来「收起」。

      每层各管一件事，别合并：
        .recruit-mascot       定位，同时是透明按钮和提示框的锚点
        .recruit-mascot-clip  裁剪盒，收起时把机器人裁在导航条下沿
        .recruit-mascot-shift 收起：整体上移
        .recruit-mascot-drop  入场：从栏里垂下
        .recruit-mascot-body  摆动：绕绳子顶端转
      裁剪必须单独一层：提示框要挂在机器人左侧（裁剪盒外面），共用一个
      overflow:hidden 会被直接裁掉。上移和摆动也必须分家：摆动是 animation、
      上移是 transition，落在同一条 transform 上时 animation 会一直压着 transition。
    -->
    <div v-if="active === 'home'" class="recruit-mascot" :class="{ 'is-stowed': mascotStowed }">
      <div class="recruit-mascot-clip">
        <div class="recruit-mascot-shift">
          <div class="recruit-mascot-drop">
            <button
              class="recruit-mascot-body"
              type="button"
              aria-label="打开求职助理"
              :aria-hidden="mascotStowed"
              :tabindex="mascotStowed ? -1 : undefined"
              @click="openAssistant"
            >
              <img class="recruit-mascot-image" :src="recruitAssistantRobot" alt="" />
            </button>
          </div>
        </div>
      </div>
      <button
        class="recruit-mascot-rope"
        type="button"
        :aria-label="mascotStowed ? '放下求职助理' : '收起求职助理'"
        @click="mascotStowed = !mascotStowed"
      ></button>
      <span class="recruit-mascot-tip" aria-hidden="true">
        <strong>打开求职助理</strong>
        <small>帮你匹配合适的工作</small>
      </span>
    </div>

    <div class="recruit-topbar-actions">
      <button :class="{ active: active === 'messages' }" type="button" @click="go('messages')">
        <span>消息</span>
      </button>
      <ResumeToolsMenu
        v-if="showResumeTools"
        variant="dropdown"
        trigger-label="简历"
        trigger-action="navigate"
        :active="active === 'resume'"
        @ai="emit('resumeAi')"
        @edit="emit('resumeEdit')"
        @generate="emit('resumeGenerate')"
        @trigger="go('resume')"
        @upload="emit('resumeUpload')"
        @version="emit('resumeVersion')"
      />
      <button v-else :class="{ active: active === 'resume' }" type="button" @click="go('resume')">简历</button>
      <button class="recruit-topbar-user" :class="{ active: active === 'user' }" type="button" @click="go('user')">
        <span class="recruit-topbar-user-name">{{ accountName }}</span>
        <span class="recruit-topbar-avatar">
          <img
            v-if="accountAvatarUrl && !avatarFailed"
            :src="accountAvatarUrl"
            alt=""
            @error="avatarFailed = true"
          />
          <IconifyIcon v-else icon="lucide:user-round" />
        </span>
      </button>
      <button class="recruit-topbar-home" type="button" @click="returnHome">返回主页</button>
    </div>
  </header>
</template>

<style scoped>
.recruit-topbar {
  display: flex;
  align-items: center;
  box-sizing: border-box;
  width: 100%;
  height: 60px;
  min-height: 60px;
  margin: 0;
  /* 全屏导航的内容始终按视口中心 1180px 基线对齐，避免详情/企业页父容器较窄时偏移。 */
  padding: 0 max(24px, calc((100vw - 1180px) / 2));
  border-bottom: 1px solid #e7eef0;
  background: rgb(255 255 255 / 96%);
  box-shadow: 0 4px 18px rgb(20 64 73 / 5%);
  color: #284b55;
  gap: 34px;
  position: sticky;
  top: 0;
  z-index: 40;
  transition: background 600ms ease, border-color 600ms ease, box-shadow 600ms ease, backdrop-filter 240ms ease;
}
.recruit-topbar button { border: 0; background: transparent; color: #000; cursor: pointer; font: inherit; }
.recruit-topbar-brand { display: inline-flex; align-items: center; flex: 0 0 auto; gap: 10px; padding: 0; text-align: left; }
.recruit-topbar-brand img { width: 42px; height: 42px; border-radius: 8px; object-fit: contain; }
.recruit-topbar-brand span { display: grid; gap: 1px; }
.recruit-topbar-brand strong { color: #173e49; font-size: 17px; line-height: 1.2; }
.recruit-topbar-brand small { color: #8b9da1; font-size: 9px; letter-spacing: .12em; line-height: 1.2; }
.recruit-topbar-nav, .recruit-topbar-actions { display: flex; align-items: center; }
.recruit-topbar-nav { gap: 30px; }
.recruit-topbar-nav button { position: relative; height: 60px; padding: 0 2px; font-size: 14px; }
.recruit-topbar-nav button::after { position: absolute; right: 0; bottom: 0; left: 0; height: 3px; border-radius: 3px 3px 0 0; background: #d43d2f; content: ''; opacity: 0; transform: scaleX(.45); transition: opacity 160ms ease, transform 160ms ease; }
.recruit-topbar-nav button:hover, .recruit-topbar-nav button.active { color: #000; }
.recruit-topbar-nav button.active::after { opacity: 1; transform: scaleX(1); }
.recruit-topbar-actions { margin-left: auto; gap: 22px; white-space: nowrap; }
.recruit-topbar-actions > button { display: inline-flex; align-items: center; gap: 7px; height: 60px; min-height: 60px; padding: 0; font-size: 14px; }
.recruit-topbar-actions > button:hover, .recruit-topbar-actions > button.active { color: #000; }
.recruit-topbar-actions > button.active:not(.recruit-topbar-user) { position: relative; }
.recruit-topbar-actions > button.active:not(.recruit-topbar-user)::after {
  position: absolute;
  right: 0;
  bottom: 0;
  left: 0;
  height: 3px;
  border-radius: 3px 3px 0 0;
  background: #fff;
  content: '';
}
.recruit-topbar :deep(.resume-tools-trigger) { height: 60px; min-height: 60px; padding: 0; color: #000; font-size: 14px; font-weight: 400; }
.recruit-topbar :deep(.resume-tools-trigger:hover), .recruit-topbar :deep(.resume-tools-trigger[aria-expanded='true']) { color: #000; }
.recruit-topbar-user { max-width: 180px; overflow: visible; }
.recruit-topbar-user-name { position: relative; display: inline-block; max-width: 145px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.recruit-topbar-user.active .recruit-topbar-user-name::after {
  position: absolute;
  right: 0;
  bottom: -18px;
  left: 0;
  height: 3px;
  border-radius: 3px 3px 0 0;
  background: #fff;
  content: '';
}
.recruit-topbar-avatar { display: grid; width: 28px; height: 28px; place-items: center; overflow: hidden; border: 1px solid #b7d6f5; border-radius: 50%; background: #eff6ff; color: #2f80ed; }
.recruit-topbar-avatar img { width: 100%; height: 100%; object-fit: cover; }
.recruit-topbar-home { padding-left: 20px !important; border-left: 1px solid #e7eef0 !important; color: #000 !important; }
.recruit-topbar-home:hover { color: #000 !important; }

/* 显示尺寸 92×142。资源 240×370，按机器人本体宽度对齐到之前那一档。 */
.recruit-mascot {
  position: absolute;
  top: 100%;
  /* 让绳心（图片 10px 处）落在内容基线右内侧 85px，和用户标的那条线对齐 */
  right: calc(max(24px, (100vw - 1180px) / 2) + 3px);
  width: 92px;
  height: 142px;
  pointer-events: none;
}

/*
 * 裁剪盒：只为了在收起时把机器人裁在导航条下沿。
 *
 * 它必须比图片大一圈，而且要往左上留出余量 —— 摆动是绕绳子上端（图片 x=10）转的，
 * 图片左边缘在吊点左边，一转就会荡到盒子左边外面去（±8.4° 时约 -21px），
 * 盒子按图片尺寸做会把机器人削掉一块。
 * 往右下也要余量：右下角同样会荡出去（约 +112 / +152）。
 * 用 padding-left 而不是给内容加偏移：内容仍然从锚点 x=0 开始，
 * 裁剪边界则落在 padding 盒（也就是左移 30px、放大后的那个框）上。
 */
.recruit-mascot-clip {
  position: absolute;
  top: 0;
  left: -30px;
  width: 158px;
  height: 180px;
  box-sizing: border-box;
  padding-left: 30px;
  overflow: hidden;
  pointer-events: none;
}

/*
 * 收起：整体上移，一直移到完全出画面（图片高 142）。
 *
 * 不能「只上移一部分、留一截绳子在栏下」—— 绳子和机器人现在在同一张图里，
 * 往上平移时最先出局的是图片顶边，留在栏下的必然变成图片底部（脚）。
 * 栏下那一小截绳头改用 CSS 画，见 .recruit-mascot-rope::before。
 * 和摆动分开写：摆动在 .recruit-mascot-body 上，两条 transform 落在不同元素上。
 */
.recruit-mascot-shift {
  transition: transform 420ms cubic-bezier(0.4, 0, 0.2, 1);
}

.recruit-mascot.is-stowed .recruit-mascot-shift {
  transform: translateY(-142px);
}

/*
 * 入场：从栏里垂下来。
 * 用 translateY 而不是改高度 —— 盒子高度不变，裁剪盒不用跟着动。
 * 缓动特意不带回弹：绳子和机器人是刚性连接，荡过头再弹回来像根皮筋。
 */
.recruit-mascot-drop {
  animation: recruit-mascot-drop 560ms cubic-bezier(0.22, 0.61, 0.36, 1) both;
}

@keyframes recruit-mascot-drop {
  from {
    transform: translateY(-100%);
  }

  to {
    transform: translateY(0);
  }
}

/*
 * 摆动：绕绳子顶端转。
 *
 * transform-origin 定在 10px 0 —— 横坐标是绳子的位置（资源里绳心在 10.8%，
 * 92px 宽换算过来 10px），纵坐标是绳子上端。绕元素中心转的话会像整块招牌在晃。
 * 幅度 ±8.4°、周期 2.4s：这两个数是从你给的 ai-job-robot-animation.mp4 里量的，
 * 视频 121 帧 24fps，对躯干质心做 DFT 得到主周期 60.5 帧（2.52s），
 * 平滑后的摆幅 ±66.6px，按吊点距离 450px 换算即 ±8.4°。
 * 周期按视频原值取 2.4s，没按尺寸做物理缩放（那样会缩到 1s 左右，对一个装饰元素太快）。
 */
.recruit-mascot-body {
  display: block;
  width: 92px;
  padding: 0;
  border: 0;
  background: transparent;
  cursor: pointer;
  pointer-events: auto;
  transform-origin: 10px 0;
  animation: recruit-mascot-sway 2.4s ease-in-out infinite;
}

@keyframes recruit-mascot-sway {
  0%,
  100% {
    transform: rotate(-8.4deg);
  }

  50% {
    transform: rotate(8.4deg);
  }
}

/* 降低动效偏好下的替代版：振幅降到 1.6°、周期拉到 9s */
@keyframes recruit-mascot-sway-gentle {
  0%,
  100% {
    transform: rotate(-1.6deg);
  }

  50% {
    transform: rotate(1.6deg);
  }
}

/* 收起时不摆：把动画冻在当前角度，免得撤回去的过程里还在晃 */
.recruit-mascot.is-stowed .recruit-mascot-body {
  animation-play-state: paused;
}

.recruit-mascot-image {
  display: block;
  width: 92px;
  height: auto;
  transition: filter 220ms ease;
}

/*
 * 悬停给一点抬起感。用投影而不是放大 —— 摆动已经占用了 body 的 transform，
 * 再叠一个 scale 会互相覆盖。投影落在透明 PNG 的轮廓上，正好是机器人的形状。
 */
.recruit-mascot-body:hover .recruit-mascot-image {
  filter: drop-shadow(0 6px 14px rgb(21 60 147 / 26%));
}

/*
 * 「收起」的把手：盖在绳子那一段上的透明按钮。
 * 绳子本身只有 3px 宽，直接点太难点，所以按钮做成 26×22（到头顶为止）。
 * 它放在裁剪盒外面 —— 收起时整块上移，这个按钮不用跟着走，得一直留在栏下。
 */
.recruit-mascot-rope {
  position: absolute;
  top: 0;
  left: 0;
  width: 26px;
  height: 22px;
  padding: 0;
  border: 0;
  background: transparent;
  cursor: pointer;
  pointer-events: auto;
}

/*
 * 收起后栏下留着的那一截绳头。
 * 收起时整张图已经全部移出画面，所以这一截必须自己画 —— 位置和颜色都是对着资源量的：
 * 资源 240 宽，绳子在 x 23..29（7px），换算到显示 92px 就是 x≈10、宽 2.7px。
 * 颜色取自同一处的像素（#F6D04E 亮部 / #D2AA25 暗部）。
 * 不收起时不画：那时候图里的绳子就在同一个位置，画了会叠成两根。
 */
.recruit-mascot-rope::before {
  position: absolute;
  top: 0;
  left: 9px;
  width: 3px;
  height: 16px;
  border-radius: 1px;
  background: linear-gradient(180deg, #f6d04e, #e5bd39 55%, #d2aa25);
  opacity: 0;
  content: '';
}

.recruit-mascot.is-stowed .recruit-mascot-rope::before {
  opacity: 1;
}

/*
 * 悬停提示。挂到机器人左边，所以它必须待在裁剪盒外面。
 *
 * 触发写在 :has() 上：整个 .recruit-mascot 是 pointer-events:none（不能挡住下面的页面），
 * 它自己收不到 :hover，只能盯里面那个真正可点的按钮。
 * 键盘用户 tab 到按钮时同样给提示。
 */
.recruit-mascot-tip {
  position: absolute;
  top: 92px;
  right: calc(100% + 12px);
  display: grid;
  gap: 2px;
  padding: 8px 12px;
  border-radius: 8px;
  background: rgb(27 42 61 / 96%);
  box-shadow: 0 6px 18px rgb(21 60 147 / 22%);
  color: #fff;
  line-height: 1.5;
  white-space: nowrap;
  opacity: 0;
  pointer-events: none;
  transition: opacity 180ms ease, transform 180ms ease;
  transform: translate(6px, -50%);
}

.recruit-mascot-tip strong {
  font-size: 12px;
  font-weight: 600;
}

.recruit-mascot-tip small {
  color: rgb(255 255 255 / 72%);
  font-size: 11px;
}

/* 指向机器人的小三角 */
.recruit-mascot-tip::after {
  position: absolute;
  top: 50%;
  left: 100%;
  margin-top: -5px;
  border: 5px solid transparent;
  border-left-color: rgb(27 42 61 / 96%);
  content: '';
}

.recruit-mascot:has(.recruit-mascot-body:hover) .recruit-mascot-tip,
.recruit-mascot:has(.recruit-mascot-body:focus-visible) .recruit-mascot-tip {
  opacity: 1;
  transform: translate(0, -50%);
}

/* 窄屏时右边没有余量，机器人会压到搜索区 */
@media (max-width: 1080px) {
  .recruit-mascot { display: none; }
}

/* 窄屏时右边没有余量，机器人会压到搜索区 */
@media (max-width: 1080px) {
  .recruit-mascot { display: none; }
}

.recruit-topbar.is-themed {
  border-bottom-color: color-mix(in srgb, var(--banner-color-deep, #0b4a88) 72%, #fff 28%);
  background: linear-gradient(
    180deg,
    color-mix(in srgb, var(--banner-color-deep, #0b4a88) 94%, #000 6%),
    color-mix(in srgb, var(--banner-color-deep, #0b4a88) 78%, var(--banner-color, #2a91d7) 22%)
  );
  box-shadow: 0 7px 22px color-mix(in srgb, var(--banner-color-deep, #0b4a88) 28%, transparent);
}
.recruit-topbar.is-themed.is-scrolled {
  border-bottom-color: color-mix(in srgb, var(--banner-color-deep, #0b4a88) 55%, #fff 45%);
  background: color-mix(in srgb, var(--banner-color-deep, #0b4a88) 82%, transparent);
  box-shadow: 0 9px 26px color-mix(in srgb, var(--banner-color-deep, #0b4a88) 36%, transparent);
  backdrop-filter: blur(16px) saturate(135%);
  -webkit-backdrop-filter: blur(16px) saturate(135%);
}
.recruit-topbar.is-themed button,
.recruit-topbar.is-themed button:hover,
.recruit-topbar.is-themed button.active,
.recruit-topbar.is-themed :deep(.resume-tools-trigger),
.recruit-topbar.is-themed :deep(.resume-tools-trigger:hover),
.recruit-topbar.is-themed :deep(.resume-tools-trigger[aria-expanded='true']) {
  color: var(--banner-foreground, #fff) !important;
}
.recruit-topbar.is-themed .recruit-topbar-brand strong { color: var(--banner-foreground, #fff); }
.recruit-topbar.is-themed .recruit-topbar-brand small { color: rgb(255 255 255 / 72%); }
.recruit-topbar.is-themed .recruit-topbar-nav button::after { background: #fff; }
.recruit-topbar.is-themed .recruit-topbar-avatar {
  border-color: rgb(255 255 255 / 58%);
  background: rgb(255 255 255 / 16%);
  color: #fff;
}
.recruit-topbar.is-themed .recruit-topbar-home { border-left-color: rgb(255 255 255 / 26%) !important; }

/*
 * 降低动效偏好。
 *
 * 入场这种一次性动画直接省掉；摆动只减速减幅，不整个关掉 —— 早先这里写的是
 * animation: none，结果「机器人根本不会动」：这条机器人是首页进求职助理的唯一入口，
 * 完全静止等于把这个入口做没了。实测在开了「关闭动画效果」的机器上，
 * 浏览器就会报 reduce，于是所有人看到的就是一个死物。
 * 振幅压到 1.6°、周期拉到 9s：对前庭敏感的人几乎无感，但仍然是活的。
 */
@media (prefers-reduced-motion: reduce) {
  .recruit-topbar,
  .recruit-mascot-image,
  .recruit-mascot-shift,
  .recruit-mascot-tip { transition: none; }

  .recruit-mascot-drop { animation: none; }

  .recruit-mascot-body {
    animation: recruit-mascot-sway-gentle 9s ease-in-out infinite;
  }
}
@media (min-width: 1280px) and (max-width: 900px) {
  .recruit-topbar { flex-wrap: wrap; gap: 0 22px; padding: 0 18px; }
  .recruit-topbar-brand, .recruit-topbar-nav { min-height: 60px; }
  .recruit-topbar-nav button { height: 60px; }
  .recruit-topbar-actions { width: 100%; min-height: 48px; justify-content: flex-end; gap: 16px; border-top: 1px solid #edf2f3; }
  .recruit-topbar-actions > button { min-height: 42px; }
}
@media (min-width: 1280px) and (max-width: 560px) {
  .recruit-topbar { gap: 0 14px; padding: 0 14px; }
  .recruit-topbar-brand img { width: 34px; height: 34px; }
  .recruit-topbar-brand strong { font-size: 15px; }
  .recruit-topbar-brand small { display: none; }
  .recruit-topbar-nav { gap: 16px; }
  .recruit-topbar-nav button { font-size: 14px; }
  .recruit-topbar-actions { justify-content: space-between; gap: 8px; overflow-x: auto; }
  .recruit-topbar-home { padding-left: 10px !important; }
}
</style>
