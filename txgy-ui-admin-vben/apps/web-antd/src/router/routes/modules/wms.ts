import type { RouteRecordRaw } from 'vue-router';

/**
 * WMS 本地静态路由
 * 说明：WMS 的业务菜单（含「AI 智能助手」）均来自后端 system_menu 配置，
 * 菜单 component 指向 wms/ai/index，由后端动态路由机制加载，此处不再重复声明。
 */
const routes: RouteRecordRaw[] = [];

export default routes;
