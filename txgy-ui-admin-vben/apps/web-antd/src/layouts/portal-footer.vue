<!--
  门户（员工端）· 全局页脚

  结构（从上到下四行，全部水平居中）：
    ① 导航链接：联系我们 - 合作伙伴 - 关于我们 - 网站地图（短横线连接）
    ② 版权与备案：版权声明 | ICP 证 | ICP 备案 | 公安联网备案（字号更小、颜色更灰）
    ③ 网站名称：本站网络实名/通用网址：‘招标投标-防腐保温智慧平台’
    ④ 咨询热线：000-00000000

  为什么这样排：页脚是「官方感」最靠后的一道兜底信息，浏览者到这儿的诉求只剩两类 ——
    找入口（第①行）和核对站点合法性（第②行），所以把这两行放前面且字号更大，
    网站名称与热线属于备案辅助信息，居后。这也是国内网站页脚的通行排布。

  备案信息说明（上线前必须处理，当前全是占位值）：
    · 国内网站按常规要求在显著位置标明 ICP 备案号与公安联网备案号，本组件把这两项与
      版权声明同排展示；占位号（京ICP证111111号 等）上线前必须换成真实编号，
      否则页脚会显示一串无效编号。真实编号可在工信部备案系统与公安联网备案系统查到。
    · 备案号通常还需要链接到对应备案系统（工信部、公安部），本组件按需求只做纯文本展示，
      没有加外链；需要时把对应 <span> 换成 <a> 即可。
    · 注意主体一致性：版权主体是「西安鸥鹏互联科技有限公司」（陕西），
      而占位备案号写的是「京」字头（北京）—— 上线前请确认主体与备案地是否匹配。

  用法：<PortalFooter />（不带参数即用上面这套默认文案，各字段都能单独覆盖）
-->
<script lang="ts" setup>
import { useRouter } from 'vue-router';

/**
 * 页脚内容全部可覆盖：备案号、热线这类信息不同部署环境不一样，
 * 写死在模板里会导致改一处要翻代码，故每一项都开一个 prop，默认值就是需求给定的文案。
 *
 * 注意这里**不加 export**：`<script setup>` 不允许 ES module 导出，
 * 写了 `export interface` 会直接编译报错；父组件也不需要这个类型。
 */
interface PortalFooterNavLink {
  /** 链接文字，如「联系我们」 */
  label: string;
  /** 目标地址；暂无对应页面时留空，点击不会跳转（只 emit nav 事件） */
  path?: string;
}

const props = withDefaults(
  defineProps<{
    /** 第一行导航链接；默认四项，path 暂空（门户还没有这些落地页） */
    navLinks?: PortalFooterNavLink[];
    /** 第二行版权声明 */
    copyright?: string;
    /** 第二行 ICP 证编号（前面带一个小图标） */
    icpLicense?: string;
    /** ICP 证图标地址；留空则用内置的红色方块占位 */
    icpLicenseIcon?: string;
    /** 第二行 ICP 备案号 */
    icpRecord?: string;
    /** 第二行公安联网备案号 */
    policeRecord?: string;
    /** 第三行网站名称（网络实名/通用网址） */
    siteName?: string;
    /** 第四行咨询热线 */
    hotline?: string;
  }>(),
  {
    navLinks: () => [
      { label: '联系我们', path: '' },
      { label: '合作伙伴', path: '' },
      { label: '关于我们', path: '' },
      { label: '网站地图', path: '' },
    ],
    // 20xx 是需求里给的占位年份，上线前换成实际年份
    copyright: 'Copyright © 2026-20xx 西安鸥鹏互联科技有限公司 Rights Reserved',
    icpLicense: '京ICP证111111号',
    icpLicenseIcon: '',
    icpRecord: '京ICP备 11111111号-11',
    policeRecord: '京公网安备 11111111111111号',
    siteName: '招标投标-防腐保温智慧平台',
    hotline: '000-00000000',
  },
);

const emit = defineEmits<{
  /** 点击某个导航项；父级可据此做路由跳转（当前默认无落地页） */
  nav: [link: PortalFooterNavLink];
}>();

const router = useRouter();

function onNav(link: PortalFooterNavLink): void {
  emit('nav', link);
  // path 为空时不跳转：模板里 href="#" + @click.prevent，保证「看起来是链接、点了不会弹回页首」。
  // 有 path 走 router.push（内部路由，别用 window.location.href —— 那会整页刷新）。
  if (link.path) {
    void router.push(link.path).catch(() => {
      /* 导航被取消/重复导航：忽略 */
    });
  }
}

/* 模板里一律写 props.xxx：不要 `const { x } = props` —— 那样解构出来的是快照，
 * 后续父级改 prop 模板不会跟着更新（本组件内容虽基本是静态，但备案号/热线确实会按环境传值）。 */
</script>

<template>
  <footer class="portal-footer">
    <!-- ① 导航链接（短横线连接） -->
    <nav class="pf-nav" aria-label="页脚导航">
      <template v-for="(link, i) in props.navLinks" :key="link.label">
        <span v-if="i > 0" class="pf-nav-sep" aria-hidden="true">-</span>
        <a
          class="pf-nav-link"
          :href="link.path || '#'"
          @click.prevent="onNav(link)"
        >{{ link.label }}</a>
      </template>
    </nav>

    <!-- ② 版权与备案信息 -->
    <div class="pf-info">
      <span class="pf-info-item">{{ props.copyright }}</span>
      <span class="pf-info-sep" aria-hidden="true">|</span>

      <span class="pf-info-item">
        <!-- 京ICP证前的红色小图标。
             当前是 span 画的 12px 红色圆角方块占位；拿到正式图标后两种换法：
               ① 传 prop：<PortalFooter :icp-license-icon="'/static/icp-badge.png'" />
               ② 直接换成 <img class="pf-icp-icon" src="/static/icp-badge.png" alt="" /> -->
        <img
          v-if="props.icpLicenseIcon"
          class="pf-icp-icon"
          :src="props.icpLicenseIcon"
          alt=""
        />
        <span v-else class="pf-icp-icon" aria-hidden="true"></span>
        {{ props.icpLicense }}
      </span>

      <span class="pf-info-sep" aria-hidden="true">|</span>
      <span class="pf-info-item">{{ props.icpRecord }}</span>
      <span class="pf-info-sep" aria-hidden="true">|</span>
      <span class="pf-info-item">{{ props.policeRecord }}</span>
    </div>

    <!-- ③ 网站名称 -->
    <div class="pf-line">本站网络实名/通用网址：‘{{ props.siteName }}’</div>

    <!-- ④ 咨询热线 -->
    <div class="pf-line">咨询热线：{{ props.hotline }}</div>
  </footer>
</template>

<style scoped>
/* 一律用字面色值、不引 --bid-* 令牌：
 *   页脚在全部门户页（首页/CMS/招聘/培训/招投标）共用，而这些页面的主题变量并不统一
 *   （招投标页才有 bid-ui.css 的令牌）；写成字面值才能保证四行颜色在任何页面都一样。
 * 需求给定的口径：白底、顶部一条细浅灰分割线、深灰字、无衬线字体、整体居中。 */
.portal-footer {
  padding: 26px 24px 24px;
  border-top: 1px solid #e8e8e8;
  background: #fff;
  color: #666;
  font-family: 'Microsoft YaHei', 'PingFang SC', Arial, 'Helvetica Neue', sans-serif;
  font-size: 13px;
  line-height: 1.9;
  text-align: center;
}

/* ① 导航链接 */
.pf-nav {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: center;
  gap: 6px;
}

.pf-nav-link {
  color: #333;
  text-decoration: none;
  transition: color 0.15s;
}

.pf-nav-link:hover {
  color: #1a56b8;
  text-decoration: underline;
}

.pf-nav-sep {
  color: #ccc;
}

/* ② 版权与备案：字号更小、颜色更灰 */
.pf-info {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: center;
  gap: 4px 8px;
  margin-top: 10px;
  color: #909399;
  font-size: 12px;
  line-height: 1.8;
}

.pf-info-item {
  display: inline-flex;
  align-items: center;
}

.pf-info-sep {
  color: #dcdcdc;
}

/* ICP 证的红色小图标（占位方块；换成 img 时同一套尺寸不变） */
.pf-icp-icon {
  display: inline-block;
  width: 12px;
  height: 12px;
  margin-right: 4px;
  border-radius: 2px;
  background: #d93026;
  object-fit: contain;
}

/* ③④ 网站名称 / 咨询热线 */
.pf-line {
  margin-top: 8px;
  color: #666;
  font-size: 12.5px;
}

@media (max-width: 720px) {
  .portal-footer {
    padding: 20px 16px 18px;
    font-size: 12.5px;
  }

  .pf-info {
    font-size: 11.5px;
  }

  .pf-line {
    font-size: 12px;
  }
}
</style>
