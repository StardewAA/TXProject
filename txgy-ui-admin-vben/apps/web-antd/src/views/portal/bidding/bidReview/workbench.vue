<script lang="ts" setup>
/**
 * 门户 · 标书审查工作台（个人端）—— **一张大卡片 = 顶部标签栏 + 内容面板**
 *
 * 子路由：
 *   /portal/bidding/bidReview/review   标书审查（上传标书 → 一键审查）
 *   /portal/bidding/bidReview/history  审查历史（历史任务、结论、报告）
 *   /portal/bidding/bidReview/result   审查结论（是否废标 + 废标原因，从上面两页跳入）
 *
 * 2026-09-22 重建说明（本次 pull 的合并提交 da315e4b0 把本页整体取回了旧版，此役还原）：
 *   · Banner 恢复为**透明底 + 标题 44/20 居中**（与公告大厅 / 标书制作 / 投标流程管理逐字同值），
 *     底图由 layouts/portal.vue 的 .portal-shell--bidhero 钉在整页上；
 *   · 恢复「大卡片 = 顶部标签栏(.brw-tabs) + 内容面板(.brw-main)」，取代被回退回来的
 *     「左侧栏 .brw-sider + 右栏」布局；
 *   · 补回 `import HeroMods`（旧版丢了这行，导致四个模块入口卡直接渲染不出来）与本页
 *     `import '../styles/bid-ui.css'`；
 *   · **保留**上游新加的 KeepAlive（见 .brw-main 里那段注释）—— 那是本次 pull 带来的新改动。
 */
import { computed } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import HeroMods from '../components/HeroMods.vue';

/* 引共享令牌：共享类与共享组件的颜色都走 --bid-* 变量，而变量挂在
 * .hall / .module-page / .float-bar / .white-modal 这组类名上，故同时给根元素挂 module-page。
 * 该文件不含任何 .brw-* 规则（已核对），引进来不会影响本页原有样式。*/
import '../styles/bid-ui.css';

const route = useRoute();
const router = useRouter();

const menus = [
  {
    key: 'review',
    label: '标书审查',
    desc: '上传标书 · 一键审查',
    path: '/portal/bidding/bidReview/review',
  },
  {
    key: 'history',
    label: '审查历史',
    desc: '历史任务 · 结论与报告',
    path: '/portal/bidding/bidReview/history',
  },
];

/** 结论页从哪进来就高亮哪一项：review 进来默认高亮「标书审查」，history 进来高亮「审查历史」 */
const active = computed(() => (route.path.includes('/history') ? 'history' : 'review'));

function go(path: string) {
  router.push(path);
}
</script>

<template>
  <div class="brw-page module-page">
    <!-- 合规审查 Hero（三个子页面共用同一个工作台页面）。
         Banner 与其它三页同一套底纹。2026-09-21 起它自己**不再画背景** ——
         整页底图已上移到 layouts/portal.vue 的 .portal-shell--bidhero（钉在视口上的一层），
         本元素只负责占位与排版。这样照片整页连续，滚动时蓝条下沿不会切出接缝。 -->
    <section class="brw-hero">
      <!-- 上：平台名 + 小字。.hero-text 是共享类（styles/bid-ui.css），2026-09-21 下午起居中、宽度自适应。 -->
      <div class="hero-text">
        <!-- 标题与下面那行小字（2026-09-21 用户口径）：四个页面统一用平台名「防腐保温招投标智慧平台」
             和公告大厅那一行小字 —— 于是四页的标题段逐字相同、且逐字同样大小，
             本页是哪个模块只由下方高亮的那张入口卡体现。
             （此前这里放的是组长 2026-09-20 给本模块写的价值句「先审后投…」，
               现按用户要求换掉，句子已不再使用。） -->
        <h1 class="brw-hero__title">防腐保温招投标<span class="hl">智慧</span>平台</h1>
        <div class="brw-hero__sub">《公告大厅》《免费查阅 · 依法公开》《智慧标书》</div>
      </div>

      <!-- 下：四个模块入口那整行（共享组件 components/HeroMods.vue）。
           2026-09-21 用户口径：四个页面的蓝色背景部分都要与公告大厅一致、都显示这四个按钮；
           active 传本页的 key，让「合规审查」这张卡保持被点击时的高亮色。
           下午起它从标题右侧搬到标题下方，并压到下方玻璃卡顶边下 12px。 -->
      <HeroMods active="compliance" />
    </section>

    <!-- 2026-09-21 用户口径：下面的内容部分底下加一张「略大一些的半透明底圆角卡片」——
         卡片用共享类 .bid-glass-shell（见 styles/bid-ui.css），本页另加 .brw-shell 给它列宽与上提位置；
         内容自身那张白卡（.brw）保留不动，于是观感是「白卡坐在一张略大的玻璃卡片上」。
       （本页原有一行「返回公告大厅」工具栏 .brw-toolbar，2026-09-21 用户口径已整块删除：
         顶部蓝条里那排模块入口卡已经承担了页面间跳转，这行按钮是重复入口。） -->
    <div class="brw-shell bid-glass-shell">
    <div class="brw">
      <!-- 顶部导航栏：原来是左侧独立的一栏（.brw-sider 白卡片），2026-09-21 用户先要求
           「并进大框」、再要求「并在大卡片的上方」，最后要求「接一条与页面同宽的导航栏、
           均分为两块」—— 现为卡片的第一行，整条与卡片同宽，两块各占一半、点击切换。
           原来那行灰色小标题「标书审查」已删 —— 它与第一个标签的文字完全重复。 -->
      <nav class="brw-tabs" aria-label="标书审查导航">
        <div
          v-for="m in menus"
          :key="m.key"
          class="brw-tab"
          :class="{ 'is-active': active === m.key }"
          role="button"
          tabindex="0"
          @click="go(m.path)"
          @keydown.enter.prevent="go(m.path)"
        >
          <div class="brw-tab__label">{{ m.label }}</div>
          <div class="brw-tab__desc">{{ m.desc }}</div>
        </div>
      </nav>

      <main class="brw-main">
        <!--
          KeepAlive 只保活「标书审查 / 审查历史」两个页签（用户 2026-09-21）：
          审查是异步的，点完「开始审查」切到历史再切回来，之前会重建页面 —— 进度、当前任务、
          已填表单全丢，表现得像「新开了一页」。结论页（result）刻意不保活：它按 ?taskId 取数，
          保活后换任务不会重新加载。
        -->
        <RouterView v-slot="{ Component }">
          <KeepAlive :include="['BidReviewPortalReview', 'BidReviewPortalHistory']">
            <component :is="Component" />
          </KeepAlive>
        </RouterView>
      </main>
    </div>
    </div>

  </div>
</template>

<style scoped>
/* Hero 规格与公告大厅 .hero **逐项对齐**（用户 2026-09-21 最新口径）：
 *   **min-height: 100vh（满屏）**、标题 **44px**、副标题 **20px**、gap 0，
 *   底图由布局层的 .portal-shell--bidhero 铺满整页并钉住 ——
 *   四页（公告大厅 / 标书制作 / 投标流程管理 / 合规审查）蓝色背景与标题段完全一致。
 * 左右内边距：2026-09-21 起本页的列口径统一到 `calc(100% - var(--bid-side-inset))`，
 *   于是这里与公告大厅 / 标书制作 / 投标流程管理三页写法**完全相同**：
 *   左右内边距取 --bid-side-inset 的一半 —— 内容盒宽 = 100% − inset，
 *   与下方 `.brw-shell` 那张玻璃卡同宽同左右边缘（同源、包含块相同，必然对齐）。
 * 与 Hero 的重叠：**大卡片压在蓝条上、顶边落在屏幕纵向正中再往上抬 --bid-glass-lift**
 *   —— `.brw-shell` 的 `margin-top: calc(-50vh - 64px - var(--bid-glass-lift))`；
 *   标题下方那排按钮也是照这条线压进去 12px 的（算式在 HeroMods.vue 里）。 */
.brw-hero {
  position: relative;
  /* **一列两段、水平居中**（2026-09-21 下午用户口径：标题与小字移到正中间、放大字号；
     四个模块入口从标题右侧搬到标题下方那**整行**），与公告大厅 `.hero` 逐项一致。
     `align-items: center` 管的是交叉轴（＝水平）对齐 —— 标题段与下方那排按钮都居中。 */
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: flex-start;
  /* **`gap: 0`**：标题段的纵向位置交给它自己的 `margin-top: auto`（见 styles/bid-ui.css 里
     `.hero-text` 的注释）。这里若还留 16px 行间距，标题与按钮之间就比上方多 16px，
     标题会被顶到中点上方约 8px —— 用户口径要的是「导航栏与按钮之间的正中间」。 */
  gap: 0;
  /* 满屏（2026-09-21 用户口径「加高背景部分高度，铺满试试」）：200px → min-height: 100vh。
     仍用 min-height 而非 height：屏幕特别矮、或入口按钮降列数后内容变高时，条会跟着变高。 */
  min-height: 100vh;
  /* **上内边距 0**（原 26px）：标题段靠 `margin-top: auto` 定位，「导航栏与按钮之间的正中间」
     是拿本元素的内容盒顶边当上界的 —— 内容盒顶边必须正好等于门户导航栏下沿，留 26px 就会
     让标题比中点低 13px。左右仍是 `--bid-side-inset / 2`（决定内容盒宽＝内容列宽），下 22px 不变。 */
  padding: 0 calc(var(--bid-side-inset, 64px) / 2) 22px;
  overflow: hidden;
  color: #fff;
  /* 居中（2026-09-21 下午用户口径）。原来这里是 left（标题在左上角的口径）。 */
  text-align: center;
  /* 背景**透明**（2026-09-21 用户口径「照片铺满整页、钉住不滚」）：底图与蒙版已上移到
     layouts/portal.vue 的 .portal-shell--bidhero（钉在视口上的一层）。
     ⚠ 本页原先在这里自绘 `linear-gradient(135deg, #0e3a78, #123c8c)`，那会让照片在蓝条处断掉；
     不要再加回去。只留 `overflow: hidden` 与 `color: #fff`：白字仍压在那层照片上。 */
  background: transparent;
}

/* 标题与下面那行小字：2026-09-21 下午放大（用户口径「都移到正中间并放大字号」），
 *   26px → 44px、14px → 20px；**四页必须同值**（与公告大厅 .hero-title / .hero-sub 一致）。 */
.brw-hero__title {
  margin: 0;
  font-size: 44px;
  font-weight: 800;
  /* 字距随字号收一档：字号 26px 时 1px 约 3.8%，44px 时 1px 只有 2.3%、显得松散 */
  letter-spacing: 2px;
  line-height: 1.25;
}

.brw-hero__title .hl {
  color: #f0cb77;
}

/* Banner 小字：浅白字；句子内容 2026-09-21 起与公告大厅统一
   （原来是组长给本模块单独写的那句价值话，已按用户口径换掉） */
.brw-hero__sub {
  color: rgb(255 255 255 / 82%);
  font-size: 20px;
  line-height: 1.7;
  letter-spacing: 0.8px;
}

/* 内容区底衬（2026-09-21 用户口径「下面的内容部分加一张略大一些的半透明底圆角卡片」）：
 * 卡片本身的样子来自共享类 .bid-glass-shell（半透明白 + 毛玻璃 + 圆角 + 描边 + 阴影 + 24px 内边距），
 * 这里只给这张卡片**列宽与上提位置**：
 *   · 列宽与内容列同口径（`calc(100% - var(--bid-side-inset))` 居中）；
 *   · `margin-top: calc(-50vh - 64px - var(--bid-glass-lift))` —— 让这张卡片的**顶边落在屏幕
 *     纵向正中再往上抬 --bid-glass-lift**（2026-09-21 用户口径「四个按钮稍微往上移，然后增加
 *     玻璃卡的高度，让四个按钮上移后可以压到玻璃卡」）。
 *     算式：文档流里它的自然位置在蓝条下方 = 顶栏 64px + 蓝条 100vh，要把它提到 `50vh − lift` 处，
 *     需要回压 `-(64px + 50vh + lift)`。64px 是门户顶栏的高度（layouts/portal.vue 的 min-height）。
 *     抬手同时把 `.bid-glass-shell` 的上内边距加同样的量，于是卡是**往上长高**、里面的白卡
 *     （`.brw`）原地不动；按钮排那边也同步上移（见 components/HeroMods.vue）。
 *     **要调高度只改 --bid-glass-lift 那一处。**
 *   · position/z-index 是**必需**的：Banner 是 position: relative，不给自己定位就会被蓝条盖住；
 *     有了这一层，里面的 .brw 就不需要各自再定位了。 */
.brw-shell {
  position: relative;
  z-index: 1;
  width: calc(100% - var(--bid-side-inset));
  margin: calc(-50vh - 64px - var(--bid-glass-lift, 0px)) auto 56px;
}

/* 下面 .brw 则是「坐在玻璃卡片上的那张白卡」，保留原有的白底 / 描边 / 圆角 / 阴影。
 * 宽度撑满玻璃卡片的内容区（卡片那 24px 内边距就是「略大一些」的那圈）；
 * 另外 **本卡片不能用 overflow: hidden 裁自己的圆角** —— 那会让它成为滚动容器，
 * 顶部那条 position: sticky 的导航栏会退化成「相对这张卡片」定位、失去吸附；
 * 所以上下两组圆角分别由导航条与内容面板自己画。 */
.brw {
  width: 100%;
  padding: 0;
  margin: 0;
  background: var(--bid-surface);
  border: 1px solid var(--bid-border);
  border-radius: var(--bid-radius-lg);
  box-shadow: var(--bid-shadow-md);
}

/* 顶部导航栏：与大卡片同宽，**两块均分**（各占一半），点哪块切到哪页。
 *   sticky(top: 0)：门户顶栏 .portal-header 不是吸顶的，整页一起滚，
 *   所以导航条吸在视口最上方即可，不会和顶栏打架；「审查历史」表格很长时两个标签一直可见。
 *   ⚠ **本行不能加纵向 padding** —— .brw-tab 的 -1px 下外边距正是靠「本行没有纵向 padding、
 *     标签高度撑满一行」才能让选中态的 2px 深蓝色条压在这条 1px 下边线上；
 *     加了纵向 padding 就会漏缝，那时要改成 ::after 绝对定位画那 2px。 */
.brw-tabs {
  position: sticky;
  top: 0;
  z-index: 2;
  display: flex;
  align-items: stretch;
  padding: 0;
  background: var(--bid-surface);
  border-bottom: 1px solid var(--bid-border);
  border-radius: var(--bid-radius-lg) var(--bid-radius-lg) 0 0;
}

/* 标签：两块均分整条（flex: 1），文字水平居中；内部仍是「标签名 + 一行说明」两行。
 *   选中态改走模块品牌深蓝（--bid-primary / --bid-primary-soft）——
 *   原来是 antd 蓝 #e6f4ff / #1677ff，导航并进品牌卡片里之后与周围不是一套，故一并改掉。
 *   margin-bottom: -1px + 2px 下边框 = 选中态的深蓝色条正好压在下边线上。 */
.brw-tab {
  display: flex;
  flex: 1;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 9px 12px;
  margin-bottom: -1px;
  text-align: center;
  cursor: pointer;
  border-bottom: 2px solid transparent;
  transition: background 0.15s, border-color 0.15s;
}

/* 两块之间的竖分隔线：原来两块间有 4px 间隔分开，现在紧挨着，用一条线分区 */
.brw-tab + .brw-tab {
  border-left: 1px solid var(--bid-border);
}

/* 两块各自的外上角要跟着大卡片的圆角走：
 *   标签是无圆角的矩形，而第一块 / 第二块的外上角正好落在卡片的圆角起点上；选中态那块带浅蓝底
 *   （--bid-primary-soft），不修的话它的直角会把卡片的圆角和 1px 描边盖掉一角，卡片看起来
 *   「被削方」—— 而且两块里总有一块是选中态，所以这一角总是缺的。
 *   半径取「圆角 − 1px」，与卡片内缘同心（卡片有 1px 描边，内缘半径比外缘少 1px）。
 *   注意：**别改成给 .brw-tabs 加 overflow: hidden** —— 那会把选中态的 2px 深蓝色条裁成 1px。 */
.brw-tab:first-child {
  border-radius: calc(var(--bid-radius-lg) - 1px) 0 0 0;
}

.brw-tab:last-child {
  border-radius: 0 calc(var(--bid-radius-lg) - 1px) 0 0;
}

.brw-tab:hover {
  background: var(--bid-primary-soft);
}

.brw-tab.is-active {
  background: var(--bid-primary-soft);
  border-bottom-color: var(--bid-primary);
}

.brw-tab__label {
  font-size: 14px;
  font-weight: 600;
  color: var(--bid-text-1);
}

.brw-tab.is-active .brw-tab__label {
  color: var(--bid-primary);
}

.brw-tab__desc {
  max-width: 100%;
  margin-top: 1px;
  overflow: hidden;
  font-size: 12px;
  color: var(--bid-text-3);
  /* 两块各占一半，极窄屏（约 <340px）说明文字会放不下 —— 省略号收尾，
     别让它换行把整条导航栏撑高（导航栏高度一变，标签的 2px 选中条就对不上边线了）。 */
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 内容区：导航栏下方那一整块，**直接铺满大卡片** —— 大卡片本身就是页面卡片，
 *   三个子页的内容与导航栏同宽、不再各自套一张卡片，所以这里既不上底色也不留内边距。
 *   overflow: hidden + 下部圆角 = 把子页最后一块裁进大卡片的圆角里：
 *   子页若有「用负边距铺到卡片边缘」的浅底条 / 分隔条（定稿版的 .brp-foot 就是这种），
 *   不裁的话它的直角会戳出大卡片的圆角。当前上游这版三个子页都没有这种条，
 *   这条规则是留给后续新增区块的保险，成本为零。
 *   这里的裁剪**不影响顶部导航栏的 sticky** —— 导航栏是本元素的兄弟，不是后代，
 *   sticky 的包含块仍是 .brw（.brw 自己没有 overflow）。 */
.brw-main {
  padding: 0;
  overflow: hidden;
  border-radius: 0 0 calc(var(--bid-radius-lg) - 1px) calc(var(--bid-radius-lg) - 1px);
}

/* 搜索框统一白色（覆盖子页面的输入框 / 下拉 / 带前后缀的输入框） */
.brw :deep(.ant-input),
.brw :deep(.ant-input-affix-wrapper),
.brw :deep(.ant-select:not(.ant-select-customize-input) .ant-select-selector),
.brw :deep(.ant-input-search .ant-input) {
  background: #fff !important;
}
</style>
