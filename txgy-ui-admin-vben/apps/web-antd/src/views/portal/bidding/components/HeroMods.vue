<!--
  招投标门户 · 蓝色 Banner 里的四个模块入口（公告大厅 / 标书制作 / 合规审查 / 投标流程管理）

  为什么抽成组件（2026-09-21 用户口径）：
    ① 「标书制作 / 合规审查 / 投标流程管理三个页面的蓝色背景部分与公告大厅一致，都显示四个按钮」——
       四个页面共用同一份标记与样式，写一份比分在四个页面里各写一份稳；
    ② 「处于哪个页面时哪个页面的按钮保持被点击时的颜色」—— 用 `active` 属性传入当前页面的 key，
       对应的那张卡加 `.on`，高亮配色沿用模块里既有的「选中态」写法（同 FloatBar 的 `.fb-btn.on`），
       不引入任何新颜色。

  用法（放在页面 Banner 里、标题段的**下方**）：
    <section class="hero">
      <div class="hero-text"> …标题 + 副标题… </div>
      <HeroMods active="hall" />
    </section>
    active 取值：hall / bid-document / compliance / process（与四个模块的路由一一对应）。

  2026-09-21 下午改版（用户口径「把四个按钮移到下面、调整按钮尺寸，然后稍微压在玻璃卡片上；
  按钮不要与公告卡片重叠、仅与玻璃卡片重叠」＋「四个按钮的宽度与**白卡**对齐而不是玻璃卡」）：
    · **从标题右侧搬到标题下方**，成为**整行**，且**与里面的白卡对齐**（不是与玻璃卡对齐）：
      宽度 = Banner 内容盒宽 − (玻璃卡内边距 + 描边) × 2
      （Banner 内容盒宽与 `.content-shell` 的 `calc(100% - --bid-side-inset)` 同源，必然对齐；
       再往里收的那一圈取自共享变量 `--bid-glass-pad` / `--bid-glass-line`）。
    · 四张卡仍是 **grid 四等分**（等宽等高是硬要求，见下）。
    · 按钮排的下边缘**压在下方玻璃卡（.bid-glass-shell）顶边上、进去 12px**：
      靠 `margin-top: auto` + `margin-bottom: calc(50vh + 30px + var(--bid-glass-lift))` 定位，
      算式见 `.hero-mods` 的注释。玻璃卡内边距是 24px（顶面再多个 `--bid-glass-lift`），
      白卡从 +25px（24 内边距 + 1 描边）起 —— 压进 12px 只到玻璃、**碰不到白卡**（用户明确要求）。
    · **2026-09-21 下午续改**（用户口径「四个按钮稍微往上移，然后增加玻璃卡的高度，
      让四个按钮上移后可以压到玻璃卡」）：`.hero-mods` 的 margin-bottom **加**一个
      `--bid-glass-lift`（现在 20px，加得越多按钮越靠上），玻璃卡那边同步往上抬同样的量 ——
      于是按钮整体上移 20px、玻璃卡顶部多长高 20px、**压线仍是 12px**、里面的白卡原地不动。
    · `z-index: 2` 是必需的：`.content-shell` / `.brw-shell` / `.content` 都是 `position: relative;
      z-index: 1`，不抬一层的话按钮压在玻璃上的那 12px 会被玻璃卡盖住。

  ⚠ 约束：本组件用到的 `--bid-*` 令牌**不是自己定义的**，而是从祖先继承（定义在 styles/bid-ui.css 的
    `.hall / .module-page / .float-bar / .white-modal` 上）。所以它必须放在挂着这四个类名之一的页面里
    （四个招投标页面的根元素都符合）；**不要**把它塞进 teleport 到 body 的弹窗里 ——
    那时祖先链断了、令牌取不到，`var()` 会让整条声明失效（底色变透明、描边掉色、阴影消失）。
    这种情况的修法见 styles/bid-ui.css 里 `.white-modal` 那条注释。

  视觉与尺寸（2026-09-21 定稿，历次口径见下）：
    · 风格与颜色取自公告大厅原来的那排入口卡：白底 + --bid-border-soft 描边 + --bid-radius-lg 圆角 +
      --bid-shadow-md 阴影；图标是 --bid-primary-soft 底、--bid-primary 字的方形徽标；
      名称 --bid-text-1、说明 --bid-text-3；悬停「描边转蓝 + 阴影加深 + 上浮 3px」且徽标反白。
    · 尺寸**回到原来那张大白卡的量级**（2026-09-21 下午用户口径「调整按钮尺寸」）：
      徽标 60、名称 17、说明 13.5、内边距 16/18、箭头 18。
      沿革：大白卡 → 3/4（徽标 46/名称 15.5/说明 12.5，为「排进标题右侧」而缩）→ 本次回到大白卡量级
      （整行铺开后每张由约 175px 变成 370px 上下，缩着反而空）。
    · 四张卡**等宽等高**（grid 的 1fr 决定，不是 flex 的内容撑开）；**说明文字任何宽度下都不隐藏**，
      放不下时在卡内折行（四条说明都写成「A · B」，折在分隔符处）。
    · 列数按宽度降级（四列 → 两列 → 一列，只改列数不改卡宽），断点按「整行 = 内容列宽 − 玻璃卡内收」
      重算过（1400px / 640px，算式见 `.hero-mods` 上方那条注释）。
-->
<script lang="ts" setup>
import { useRouter } from 'vue-router';

import { IconifyIcon } from '@vben/icons';

const props = defineProps<{ active?: string }>();

const router = useRouter();

/* 四个模块入口。名称/说明/图标与 components/FloatBar.vue（那条服务导航，当前已撤下）保持一致：
 *   hall=lucide:home、bid-document=lucide:file-text、compliance=lucide:shield-check、process=lucide:workflow。
 * 「合规审查」指向标书审查工作台 bidReview/review（门户只保留这一套合规审查）。 */
const ITEMS = [
  { key: 'hall', icon: 'lucide:home', name: '公告大厅', desc: '查询 · 中标公示 · 详情', path: '/portal/bidding' },
  { key: 'bid-document', icon: 'lucide:file-text', name: '标书制作', desc: '编制投标文件 · 上传标书', path: '/portal/bidding/bid-document' },
  { key: 'compliance', icon: 'lucide:shield-check', name: '合规审查', desc: '标书 AI 审查 · 是否废标判定', path: '/portal/bidding/bidReview/review' },
  { key: 'process', icon: 'lucide:workflow', name: '投标流程管理', desc: '意向 · 待投标 · 已投标', path: '/portal/bidding/process' },
];

function go(path: string): void {
  router.push(path).catch(() => {
    /* 导航被取消/重复导航：忽略 */
  });
}
</script>

<template>
  <div class="hero-mods">
    <div
      v-for="c in ITEMS"
      :key="c.key"
      class="card"
      :class="{ on: props.active === c.key }"
      role="button"
      tabindex="0"
      @click="go(c.path)"
      @keydown.enter.prevent="go(c.path)"
    >
      <span class="card-icon"><IconifyIcon :icon="c.icon" /></span>
      <div class="card-body">
        <div class="card-name">{{ c.name }}</div>
        <div class="card-desc">{{ c.desc }}</div>
      </div>
      <span class="card-arrow"><IconifyIcon icon="lucide:arrow-right" /></span>
    </div>
  </div>
</template>

<style scoped>
/* 按钮排：标题下方那**整行**四个模块入口。
 * 用 **grid 四等分**而不是 flex —— 用户口径「四个按钮的尺寸大小一致」：
 *   flex 下每张卡由内容撑开、四张宽度各不相同；grid 的 1fr 让四块等宽、同行等高。
 * 2026-09-21 下午起它是一条**整行**，且**与里面那张白卡对齐、而不是与玻璃卡对齐**
 *   （用户口径先是「与公告内容部分宽度对齐」，随后修正为「四个按钮的宽度与白卡对齐而不是玻璃卡」）：
 *   Banner 内容盒宽 = `calc(100% - var(--bid-side-inset))`，正好是**玻璃卡的外缘宽**
 *   （`.hero` 的左右内边距是 `--bid-side-inset / 2`，两者包含块同为页面根，必然对齐）；
 *   而白卡的左右边缘还要再往里「玻璃卡内边距 + 1px 描边」，
 *   所以本元素宽度 = `100% − (--bid-glass-pad + --bid-glass-line) × 2`。
 *   `--bid-glass-pad` / `--bid-glass-line` 定义在 styles/bid-ui.css 的令牌区，
 *   `.bid-glass-shell` 的 padding 也引用同一个变量 —— 以后改玻璃卡内边距只改那一处，这里自动跟上。
 *   横向居中由 `.hero` 的 `align-items: center` 负责（本元素宽度小于 100%，会自动居中）。
 *   所以不能再写 `flex: 1 1 auto` / `max-width: 1200px` —— 那套是「挤在标题右边」时的口径。
 *
 * 纵向位置（用户口径「按钮稍微压在玻璃卡片上，但不要与公告卡片重叠」）：
 *   `margin-top: auto` 把它推到自己那一行的最下面，`margin-bottom` 决定它离 Banner 底边多远。
 *   目标落点：**按钮底边 = 玻璃卡顶边 + 12px**。
 *   算式（对照「玻璃卡顶边落在屏幕 50vh」那条，见各页 `.content-shell` / `.brw-shell` / `.content`）：
 *     Banner 高 100vh、下内边距 22px（上内边距 2026-09-21 下午起是 0，与本式无关），
 *     所以内容盒底边 = 顶栏 64 + 100vh − 22；
 *     要让按钮底边落在 50vh + 12，则 margin-bottom = (64 + 100vh − 22) − (50vh + 12)
 *     = **calc(50vh + 30px)**。
 *   **再加一个 `--bid-glass-lift`（2026-09-21 下午用户口径「四个按钮稍微往上移，然后增加玻璃卡的
 *   高度，让四个按钮上移后可以压到玻璃卡」）**：玻璃卡顶边被抬到 `50vh − lift`，按钮也整体上移
 *   同样的量，所以**压线恒为 12px、与 lift 取值无关**（把上面算式里的 50vh 换成 `50vh − lift` 立得）。
 *   ⚠ 这里**是「加」不是「减」**：margin-bottom 量的是「本元素底边离 Banner 内容盒底边多远」，
 *   加得越多按钮越靠上。（容易写反 —— 玻璃卡那边是 margin-top **减**、上内边距**加**。）
 *   三个量由同一个变量驱动，改一处就能整体上移，详见 styles/bid-ui.css 令牌区 --bid-glass-lift 的注释。
 *   只用 margin、不用绝对定位的好处：Banner 被内容撑高（窄屏降成两列/一列）时，
 *   玻璃卡也跟着往下走（它的负上外边距是相对自然位置的），而按钮是锚在 Banner 底边上的 ——
 *   两者同步移动，重叠量不会变。
 *   12px 的取值：玻璃卡内边距是 24px，压进去 12px 就只剩玻璃、**碰不到里面那张白卡**（用户要求）；
 *   想压得更深要先加大 `.bid-glass-shell` 的 padding。
 * `z-index: 2` 也是必需的：那三张玻璃卡都是 `position: relative; z-index: 1`，
 *   不抬一层的话压在玻璃上的那 12px 会被玻璃卡盖住（用户要的是按钮在上面）。 */
.hero-mods {
  position: relative;
  z-index: 2;
  display: grid;
  width: calc(
    100% - (var(--bid-glass-pad, 24px) + var(--bid-glass-line, 1px)) * 2
  );
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
  min-width: 0;
  margin-top: auto;
  margin-bottom: calc(50vh + 30px + var(--bid-glass-lift, 0px));
}

/* 列数降级（只改列数、不改卡宽，等宽是硬要求）。
 * 重算依据（2026-09-21 下午按「对齐白卡」后的宽度重算）：
 *   整行宽 = 内容列宽 − 玻璃卡内收 = (100% − --bid-side-inset) − (24 + 1) × 2，
 *   而 inset = min(480px, max(64px, 20%))，故窗口 ≤2400px 时整行 ≈ 窗口的 80% − 50px；
 *   窗口 1920 时整行 1486px，每张约 (1486 − 3×14) / 4 = 361px（宽裕，四条说明都是一行）。
 *   四列要「读得舒服」大约需要每张 248px：
 *     内边距 36 + 徽标 60 + 两个 12px 间距 24 + 箭头 18 + 文本 110 = 248，
 *     四条 + 3 个间距 ≈ 1034px → 0.8 × 窗口 − 50 ≥ 1034，即窗口 ≥ 1355px，断点取 1400px。
 *   两列时每张 = (整行 − 14) / 2；窗口收窄到约 640px 时每张只剩约 224px，故再降到一列。
 *   **全程只降列数、不折卡宽。**
 * ⚠ 已知既有问题（不是本次引入）：`layouts/portal.vue` 的 `@media (max-width: 900px)` 会把
 *   门户容器收到 680px、顶部再多 24px 内边距，那一档起下方玻璃卡的 `-50vh - 64px` 本来就算不准了。 */
@media (max-width: 1400px) {
  .hero-mods {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 640px) {
  .hero-mods {
    grid-template-columns: minmax(0, 1fr);
  }
}

/* 单张卡：白底 + 浅描边 + 圆角 + 阴影（公告大厅原来那套，未改）。
 * 尺寸**回到原来那张大白卡的量级**（2026-09-21 下午用户口径「调整按钮尺寸」）：
 *   内边距 16/18、徽标 60、名称 17、说明 13.5、箭头 18。
 *   卡高 = 16×2 内边距 + 2 描边 + max(徽标 60, 名称行 + 4 + 说明行)：
 *     名称行约 23px、说明一行约 20px（合计 47 < 60，所以卡高由徽标定，约 94px）；
 *     说明折成两行时约 27+4+40 = 71px，卡高这才涨到约 105px —— 要四张等高的那份由 grid 保证。 */
.card {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px 18px;
  cursor: pointer;
  background: #fff;
  border: 1px solid var(--bid-border-soft);
  border-radius: var(--bid-radius-lg);
  box-shadow: var(--bid-shadow-md);
  transition: transform 0.18s, box-shadow 0.18s, border-color 0.18s, background-color 0.18s;
}

.card:hover {
  border-color: rgb(26 86 184 / 40%);
  box-shadow: var(--bid-shadow-lg);
  transform: translateY(-3px);
}

/* 当前所在页面的那张卡：**保持被点击时的颜色**（用户口径）。
 * 沿用模块里既有的「选中态」写法（见 FloatBar 的 .fb-btn.on）：浅蓝底 + 主色描边 +
 *   主色文字 + 徽标反白 —— 都是既有令牌，没有引入新颜色。
 * 与悬停态的区别：悬停会「上浮 3px」、底色仍是白的；选中态不上浮、底色是浅蓝的，
 *   所以「我正停在这张卡上」和「我就在这一页」一眼能分开。 */
.card.on {
  border-color: var(--bid-primary);
  background: var(--bid-primary-soft);
}

.card.on .card-icon {
  background: var(--bid-primary);
  color: #fff;
}

.card.on .card-name {
  color: var(--bid-primary);
}

.card.on .card-arrow {
  color: var(--bid-primary);
}

/* 选中态不跟着悬停上浮：`.card:hover` 有 `transform: translateY(-3px)`，而 `.card.on`
 * 不定义 transform，所以鼠标划过当前页那张卡时它仍会浮动一下 —— 视觉上像「悬停」而不是「就在这页」。
 * 这里显式压掉，让选中态保持静止（其余悬停反馈照旧：阴影加深、箭头右移）。 */
.card.on:hover {
  transform: none;
}

.card-icon {
  display: grid;
  flex: none;
  place-items: center;
  width: 60px;
  height: 60px;
  border-radius: 14px;
  background: var(--bid-primary-soft);
  color: var(--bid-primary);
  font-size: 30px;
  transition: background-color 0.18s, color 0.18s;
}

.card:hover .card-icon {
  background: var(--bid-primary);
  color: #fff;
}

.card-body {
  min-width: 0;
}

.card-name {
  color: var(--bid-text-1);
  font-size: 17px;
  font-weight: 700;
}

.card-desc {
  margin-top: 4px;
  color: var(--bid-text-3);
  font-size: 13.5px;
  line-height: 1.5;
  /* 说明**不隐藏、也不写 nowrap**（用户口径：不要取消说明文字）：
     四张卡等宽后每张的文本宽度随窗口伸缩，窄一些时说明就在卡内折成两行 ——
     四条说明都带「·」分隔，折行位置自然。 */
  white-space: normal;
}

.card-arrow {
  display: inline-flex;
  margin-left: auto;
  color: var(--bid-text-4);
  font-size: 18px;
  transition: color 0.18s, transform 0.18s;
}

.card:hover .card-arrow {
  color: var(--bid-primary);
  transform: translateX(2px);
}
</style>
