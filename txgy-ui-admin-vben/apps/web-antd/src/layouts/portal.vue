<script lang="ts" setup>
import { computed } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { useUserStore } from '@vben/stores';

import heroBg from '#/assets/imgs/bidding/hero-bg1.jpg';
import txgyLogo from '#/assets/imgs/logo.png';
import { useAuthStore } from '#/store';

import PortalFooter from './portal-footer.vue';

const router = useRouter();
const route = useRoute();
const userStore = useUserStore();
const authStore = useAuthStore();

/** 需要"通栏"布局的 bidding 页面（公告大厅 + 三个模块页 + 标书审查工作台 + 公告详情[外部/本站两个]）：
 *  它们的 Banner（公告详情为其内容列）要随窗口铺满，故容器不再限宽；
 *  其它 bidding 子页仍用默认居中宽度。
 *  判定：多数是**精确匹配**（BIDDING_WIDE_PATHS.includes），故工作台的三个子路由要逐条列出；
 *  两个公告详情都是动态路由（带上 :id），无法枚举，改用前缀匹配（见 isBiddingWidePath）。 */
const BIDDING_WIDE_PATHS = [
  '/portal/bidding',
  '/portal/bidding/bid-document',
  '/portal/bidding/process',
  // 标书审查工作台（合规审查）：三个子路由共用同一个通栏 Banner（workbench.vue），
  // 漏掉这里容器会被限到 1180px、Banner 顶部还被顶下 32px，与其它三页对不齐。
  '/portal/bidding/bidReview/review',
  '/portal/bidding/bidReview/history',
  '/portal/bidding/bidReview/result',
];

/** 公告详情前缀（/portal/bidding/detail/:id —— 路径带参数，只能前缀匹配） */
const BIDDING_DETAIL_PREFIX = '/portal/bidding/detail/';

/** 本站公告详情前缀（/portal/bidding/internal-detail/:id —— 同样是动态路由，只能前缀匹配）。
 *  两个详情页必须**同口径**通栏：漏登记本站详情会让它落回默认的 min(1180px, 100% - 48px) 限宽，
 *  页内内容列（calc(100% - --bid-side-inset)）比外部公告详情窄一大截（用户 2026-09-23 指出）。
 *  注：'/portal/bidding/internal-detail/…' 不以 '/portal/bidding/detail/' 开头，两个前缀不会互相误匹配。 */
const BIDDING_INTERNAL_DETAIL_PREFIX = '/portal/bidding/internal-detail/';

/** 是否为通栏 bidding 页：静态路由精确匹配 + 两个公告详情按前缀。
 *  公告详情必须通栏，否则被默认的 min(1180px, 100% - 48px) 限宽，
 *  页内内容列（calc(100% - --bid-side-inset)）就与公告大厅「公告部分」对不齐了。 */
function isBiddingWidePath(path: string): boolean {
  return (
    BIDDING_WIDE_PATHS.includes(path) ||
    path.startsWith(BIDDING_DETAIL_PREFIX) ||
    path.startsWith(BIDDING_INTERNAL_DETAIL_PREFIX)
  );
}

/** 需要**整页固定底图**的页 = 那四个有蓝色 Banner 的招投标页。
 *  与 BIDDING_WIDE_PATHS 的差别只有公告详情：它虽然同样是通栏页，但排版是白纸文档式、
 *  页首没有蓝条，给它铺照片底会明显改变观感，故不列进来。
 *  清单要跟着 BIDDING_WIDE_PATHS 一起维护（同样是「有子路由就逐条列」）。 */
const BIDDING_HERO_PATHS = [
  '/portal/bidding',
  '/portal/bidding/bid-document',
  '/portal/bidding/process',
  '/portal/bidding/bidReview/review',
  '/portal/bidding/bidReview/history',
  '/portal/bidding/bidReview/result',
];

/** 是否铺整页固定底图（样式见下面 .portal-shell--bidhero 的注释）。 */
const isBiddingHeroPath = computed(() => BIDDING_HERO_PATHS.includes(route.path));

const navItems = [
  { label: '首页', path: '/portal/home' },
  { label: 'CMS系统', path: '/portal/content' },
  { label: '我要找工作', path: '/portal/recruit' },
  { label: '云培训学院', path: '/portal/training' },
  { label: '招标投标', path: '/portal/bidding' },
];

const displayName = computed(
  () => userStore.userInfo?.nickname || userStore.userInfo?.username || '用户',
);

const isRecruitPortal = computed(
  () =>
    route.path.startsWith('/portal/recruit') ||
    route.path.startsWith('/portal/profile/recruit'),
);

function isActive(path: string) {
  return route.path === path || route.path.startsWith(`${path}/`);
}

async function handleLogout() {
  await authStore.logout(false);
}
</script>

<template>
  <div
    class="portal-shell portal-light"
    :class="{
      'portal-shell--recruit': isRecruitPortal,
      'portal-shell--bidhero': isBiddingHeroPath,
    }"
    :style="isBiddingHeroPath ? { '--bid-hero-img': `url(${heroBg})` } : undefined"
  >
    <!-- 招聘门户是独立整页（有自己的「天信招聘」导航与「返回主页」），
         这里不能再叠一层平台头部，否则顶部会出现两条导航（用户 2026-09-28 指出）。
         远端 origin/master 去掉了这个 v-if，合并时按本地口径恢复。 -->
    <header v-if="!isRecruitPortal" class="portal-header">
      <div class="portal-brand" @click="router.push('/portal/home')">
        <img :src="txgyLogo" alt="天信管业" class="portal-brand-logo" />
        <span>防腐保温智慧平台</span>
      </div>

      <nav class="portal-nav" aria-label="员工端导航">
        <button
          v-for="item in navItems"
          :key="item.path"
          :class="{ active: isActive(item.path) }"
          type="button"
          @click="router.push(item.path)"
        >
          {{ item.label }}
        </button>
      </nav>

      <div class="portal-user">
        <span class="portal-user-name">{{ displayName }}</span>
        <button class="portal-logout" type="button" @click="handleLogout">
          退出登录
        </button>
      </div>
    </header>

    <main
      class="portal-main"
      :class="{
        'portal-main--home': route.path.startsWith('/portal/home'),
        'portal-main--training': route.path.startsWith('/portal/training'),
        'portal-main--recruit': isRecruitPortal,
        'portal-main--bidding': isBiddingWidePath(route.path),
      }"
    >
      <RouterView />
    </main>

    <!-- 全局页脚：导航 / 版权与备案 / 网站名称 / 咨询热线四行（内容与样式见 portal-footer.vue）。
         必须紧跟 <main> 之后 —— 首页靠「.portal-main--home + .portal-footer」这条相邻兄弟选择器隐藏页脚。
         注：「点击立即咨询」按钮 2026-09-20 曾挂在本布局上（右下角固定悬浮），
         后移到招投标 FloatBar，当天又被用户要求整体撤下；布局里不再有任何相关引用。 -->
    <PortalFooter />
  </div>
</template>

<style scoped>
.portal-shell {
  position: relative;
  min-height: 100vh;
  background: hsl(var(--background-deep));
  color: hsl(var(--foreground));
}

.portal-light {
  --background: 0 0% 100%;
  --background-deep: 216 20.11% 95.47%;
  --foreground: 210 6% 21%;
  --card: 0 0% 100%;
  --border: 240 5.9% 90%;
  --muted: 240 4.8% 95.9%;
  --muted-foreground: 240 3.8% 46.1%;
  --primary: 212 100% 45%;
  --primary-foreground: 0 0% 98%;
  --destructive: 359.33 100% 65.1%;
}

.portal-shell--recruit {
  background: hsl(var(--muted));
}

/* 招投标四个蓝条页（公告大厅 / 标书制作 / 投标流程管理 / 合规审查）的**整页固定底图**。
 * 2026-09-21 用户口径：「上下滚动页面的时候蓝色背景静止不动，不然往下拉的时候突然出现截断的
 *   白色背景很突兀」＋「照片铺满整页、钉住不滚」。
 *
 * 为什么底图放在这里（.portal-shell），而不是放在页面自己的根元素上：
 *   ① 页面里原来试过「页面根元素 + ::before { position: fixed; z-index: -1 }」——**看不到**。
 *      按 CSS 层叠与绘制顺序（CSS 2.1 附录 E），负 z-index 的**定位后代**会被提升到最近的
 *      「真实」层叠上下文（这里是根），在绘制顺序里排在**祖先自身的背景之前**；
 *      而 .portal-shell 铺着一层不透明浅灰底（`hsl(var(--background-deep))`），
 *      于是照片被它整个盖住。（真正的成因是「祖先那层不透明背景画在负 z 层之后」，
 *      跟 .portal-shell 是不是 position: relative 无关 —— 它改成 static 也一样被盖。）
 *      改用 z-index: 0/1 再压内容则会连锁要求页面里每一层都定 z-index，脆而且没必要。
 *   ② .portal-shell 本来就是「整页底色」的主人，它的盒子覆盖整个文档高度，
 *      底图挂在它身上天然在全部内容之下，不需要任何 z-index 技巧。
 *
 * background-attachment: fixed 是「钉住不滚」的落点：照片定位区与尺寸区都变成**视口**，
 *   滚动时照片一动不动、内容在上面滑过；`cover` 也变成按视口裁切，永远铺满一屏 ——
 *   因为这一层覆盖整个文档高度，所以滚到哪儿都不会再出现那条「截断的浅色带」。
 *   用 fixed 而不是再加一层 fixed 元素，也是为了让页面里**不再有任何负 z-index**。
 *   （注：移动端 Safari 对 fixed 底图有已知的降级行为，本平台是桌面端，不受影响。）
 *
 * 蒙版沿用四个页面蓝条上那一套（11 47 99 / 82% → 23 72 154 / 68%）：四个页面的蓝条本身已改成
 *   透明（背景交给这一层），套同款蒙版才能让**首屏观感与改动前一致**；蓝条上的白色标题靠它保证对比度。
 * 图片经 JS 导入后绑到本元素的 --bid-hero-img 上（同招投标各页先例：src/assets 下的图必须过 Vite
 *   改名加哈希，而 url() 写进自定义属性 Vite 不保证重写）；var(..., none) 的 none 兜底保证
 *   万一忘了绑也还有品牌深蓝打底，不会整页透明。
 * 调法：整页想更亮、让照片更透 → 把 82%/68% 调小；想改裁切位置 → 改 center top。 */
.portal-shell--bidhero {
  background:
    linear-gradient(135deg, rgb(11 47 99 / 82%) 0%, rgb(23 72 154 / 68%) 100%),
    var(--bid-hero-img, none) center top / cover no-repeat #0b2f63;
  background-attachment: fixed;
}

.portal-header {
  display: flex;
  align-items: center;
  min-height: 64px;
  padding: 0 36px;
  border-bottom: 1px solid hsl(var(--border));
  background: hsl(var(--card));
  gap: 32px;
}

.portal-brand {
  display: inline-flex;
  align-items: center;
  flex-shrink: 0;
  gap: 10px;
  color: hsl(var(--foreground));
  cursor: pointer;
  font-size: 16px;
  font-weight: 650;
}

.portal-brand-logo {
  width: 34px;
  height: 34px;
  object-fit: contain;
}

.portal-nav {
  display: flex;
  align-items: stretch;
  align-self: stretch;
  gap: 4px;
  overflow-x: auto;
}

.portal-nav button {
  position: relative;
  min-width: 88px;
  padding: 0 14px;
  border: 0;
  background: transparent;
  color: hsl(var(--muted-foreground));
  cursor: pointer;
  font-size: 15px;
  white-space: nowrap;
}

.portal-nav button:hover,
.portal-nav button.active {
  color: hsl(var(--primary));
}

.portal-nav button.active::after {
  position: absolute;
  right: 14px;
  bottom: 0;
  left: 14px;
  height: 2px;
  background: linear-gradient(90deg, hsl(var(--primary)), hsl(var(--primary-700)));
  content: '';
}

.portal-user {
  display: inline-flex;
  align-items: center;
  gap: 14px;
  margin-left: auto;
  white-space: nowrap;
}

.portal-user-name {
  color: hsl(var(--foreground));
  font-size: 14px;
}

.portal-logout {
  border: 0;
  background: transparent;
  color: hsl(var(--muted-foreground));
  cursor: pointer;
  font-size: 14px;
}

.portal-logout:hover {
  color: hsl(var(--primary));
}

.portal-main {
  width: min(1180px, calc(100% - 48px));
  min-height: calc(100vh - 112px);
  margin: 0 auto;
  padding: 32px 0 48px;
}

/* 招投标门户四模块（公告大厅 + 标书制作/合规审查/投标流程管理）与标书审查工作台：容器通栏，
   让 Banner 铺满整页宽、内容区随窗口铺开；页内各区块自行约束宽度与左右留白。
   命中路径以 BIDDING_WIDE_PATHS 为准（精确匹配），公告详情等其它 bidding 子页保持默认居中宽度，
   其它模块（招聘/培训等）完全不受影响。 */
.portal-main--bidding {
  width: 100%;
  max-width: none;
  padding-top: 0;
}

.portal-main--training {
  width: min(1320px, calc(100% - 120px));
  max-width: 1320px;
  margin-right: auto;
  margin-left: auto;
  padding: 0 0 48px;
}

.portal-shell--training {
  background: hsl(var(--background));
}

.portal-shell--training .portal-header {
  min-height: 72px;
  padding-right: max(48px, calc((100% - 1320px) / 2));
  padding-left: max(48px, calc((100% - 1320px) / 2));
  gap: 18px;
}

.portal-shell--training .portal-brand {
  gap: 8px;
  font-size: 15px;
}

.portal-shell--training .portal-brand-logo {
  width: 32px;
  height: 32px;
}

.portal-shell--training .portal-nav button {
  min-width: 74px;
  padding: 0 8px;
  font-size: 13px;
}

.portal-shell--training .portal-user { gap: 9px; }

.portal-shell--training .portal-footer {
  display: none;
}

.portal-main.portal-main--home {
  width: 100%;
  min-height: calc(100vh - 64px);
  margin: 0;
  padding: 0;
}

.portal-main--recruit {
  position: relative;
}

.portal-shell--recruit :deep(.ant-btn-primary) {
  /* C 端主行动统一走品牌强调色令牌（阶段五 5.4） */
  border-color: hsl(var(--brand-accent));
  background: linear-gradient(
    135deg,
    hsl(var(--brand-accent)),
    hsl(var(--brand-accent-strong))
  );
  box-shadow: 0 6px 14px hsl(var(--brand-accent) / 18%);
}

.portal-shell--recruit :deep(.ant-btn-primary:hover) {
  border-color: hsl(var(--brand-accent-strong));
  background: linear-gradient(
    135deg,
    hsl(var(--brand-accent-strong)),
    hsl(var(--brand-accent))
  );
}

/* 页脚自身的样式（白底 / 顶部浅灰分割线 / 四行排版）已收敛进 portal-footer.vue，
 * 这里只保留一条**布局层**的规则：首页是通栏落地页，不显示页脚。
 * 依赖 <PortalFooter /> 是 <main> 的紧邻兄弟节点（见模板注释）；
 * scoped 样式会把父组件的 scope 属性一并加到子组件根元素上，故这条选择器仍命中。 */
.portal-main.portal-main--home + .portal-footer {
  display: none;
}

@media (max-width: 900px) {
  .portal-header {
    flex-wrap: wrap;
    gap: 0 20px;
    padding: 12px 20px 0;
  }

  .portal-user {
    margin-left: auto;
    padding-bottom: 12px;
  }

  .portal-nav {
    order: 3;
    width: 100%;
    min-height: 44px;
  }

  .portal-nav button {
    min-width: auto;
    padding: 0 12px;
  }

  .portal-main {
    width: min(100% - 32px, 680px);
    padding-top: 24px;
  }
}
</style>