import {
  defineOverridesPreferences,
  definePreferencesExtension,
} from '@vben/preferences';

import logo from './assets/imgs/logo.png';

interface WebAntdPreferencesExtension {
  defaultTableSize: number;
  enableFormFullscreen: boolean;
  reportTitle: string;
  tenantMode: 'multi' | 'single';
}

/**
 * 登录后落地的首页路径
 *
 * 背景：远程仓库的「WMS AI 首页改造」提交把默认首页改成了 /wms/ai-center，
 * 但该路径在本项目里没有注册路由（后端也没有对应的 AI 菜单），
 * 登录后会被带到不存在的路由上直接 404。这里恢复为「概览 → 分析页」，
 * 与合并远程仓库前的行为保持一致。
 *
 * 注意：偏好设置会持久化到浏览器缓存，且缓存优先级高于代码默认值
 * （见 initPreferences：cachedPreferences 先于 initialPreferences 合并）。
 * 所以 main.ts 会在初始化后用这个常量强制覆盖一次，避免老缓存把落地页带偏。
 */
export const APP_HOME_PATH = '/dashboard/analytics';

/**
 * @description 项目配置文件
 * 只需要覆盖项目中的一部分配置，不需要的配置不用覆盖，会自动使用默认配置
 * !!! 更改配置后请清空缓存，否则可能不生效
 */
export const overridesPreferences = defineOverridesPreferences({
  // overrides
  app: {
    /** 后端路由模式 */
    accessMode: 'backend',
    name: '防腐保温智慧平台',
    enableRefreshToken: true,
    /**
     * 登录过期时弹窗提示并停留在当前页，而不是直接把页面跳回登录页。
     * 默认值 page 会让用户感觉「网站自己退出了 / 闪退」，改成 modal 体验更好。
     */
    loginExpiredMode: 'modal',
    /** 登录后默认进入「概览 → 分析页」 */
    defaultHomePath: APP_HOME_PATH,
  },
  tabbar: {
    /** 关闭顶部多标签页栏：页面切换靠左侧菜单，不再累积打开页签 */
    enable: false,
  },
  logo: {
    /** 登录页 / 首页 LOGO */
    source: logo,
  },
  footer: {
    /** 默认关闭 footer 页脚，因为有一定遮挡 */
    enable: false,
    fixed: false,
  },
  copyright: {
    companyName: '防腐保温智慧平台',
    companySiteLink: 'https://gitee.com/txgycode/txgy-ui-admin-vben',
  },
});

export const preferencesExtension =
  definePreferencesExtension<WebAntdPreferencesExtension>({
    tabLabel: 'preferences.antd.tabLabel',
    title: 'preferences.antd.title',
    fields: [
      {
        component: 'switch',
        defaultValue: true,
        key: 'enableFormFullscreen',
        label: 'preferences.antd.fields.enableFormFullscreen.label',
        tip: 'preferences.antd.fields.enableFormFullscreen.tip',
      },
      {
        component: 'select',
        defaultValue: 'single',
        key: 'tenantMode',
        label: 'preferences.antd.fields.tenantMode.label',
        options: [
          {
            label: 'preferences.antd.fields.tenantMode.options.single.label',
            value: 'single',
          },
          {
            label: 'preferences.antd.fields.tenantMode.options.multi.label',
            value: 'multi',
          },
        ],
      },
      {
        component: 'number',
        componentProps: {
          max: 200,
          min: 10,
          step: 10,
        },
        defaultValue: 20,
        key: 'defaultTableSize',
        label: 'preferences.antd.fields.defaultTableSize.label',
      },
      {
        component: 'input',
        defaultValue: '',
        key: 'reportTitle',
        label: 'preferences.antd.fields.reportTitle.label',
        placeholder: 'preferences.antd.fields.reportTitle.placeholder',
      },
    ],
  });
