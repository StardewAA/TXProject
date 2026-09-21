-- ----------------------------------------------------------------------------
-- 云培训学院侧边栏菜单图标补齐
--
-- 背景：远端重构后 /training 全套路由由 system_menu 动态菜单驱动（本地静态
-- 路由已删除），但二级菜单 icon 大量为 NULL。vben 菜单项没有 icon 就不渲染
-- 图标占位，导致与有图标的兄弟菜单相比文字缩进前移、视觉上图标"消失"。
--
-- 本脚本为培训二级菜单补齐 lucide 图标（均已在离线 iconify 集合中校验存在），
-- 使同一父菜单下所有子项缩进一致。
--
-- 冲突说明（2026-09-20）：远程 master 已有
-- sql/2026-09-20-training-menu-icon-align-and-category-cleanup.sql，覆盖 41011~41015。
-- 本脚本对这 5 个菜单沿用远程取值（41011=users、41012=user-cog），避免两边打架；
-- 其余 25 个（考试与认证 / 校企合作 / 学员中心）为本地补充。
-- 可重复执行。
-- ----------------------------------------------------------------------------

-- 培训管理
UPDATE system_menu SET icon = 'lucide:users'            WHERE id = 41011 AND deleted = 0;
UPDATE system_menu SET icon = 'lucide:user-cog'         WHERE id = 41012 AND deleted = 0;
UPDATE system_menu SET icon = 'lucide:trending-up'       WHERE id = 41013 AND deleted = 0;
UPDATE system_menu SET icon = 'lucide:bell-ring'         WHERE id = 41014 AND deleted = 0;
UPDATE system_menu SET icon = 'lucide:clipboard-check'   WHERE id = 41015 AND deleted = 0;

-- 考试与认证
UPDATE system_menu SET icon = 'lucide:layout-dashboard'  WHERE id = 41700 AND deleted = 0;
UPDATE system_menu SET icon = 'lucide:library'           WHERE id = 41701 AND deleted = 0;
UPDATE system_menu SET icon = 'lucide:clipboard-list'    WHERE id = 41702 AND deleted = 0;
UPDATE system_menu SET icon = 'lucide:calendar-clock'    WHERE id = 41703 AND deleted = 0;
UPDATE system_menu SET icon = 'lucide:pen-line'          WHERE id = 41704 AND deleted = 0;
UPDATE system_menu SET icon = 'lucide:award'             WHERE id = 41705 AND deleted = 0;
UPDATE system_menu SET icon = 'lucide:badge-check'       WHERE id = 41706 AND deleted = 0;
UPDATE system_menu SET icon = 'lucide:monitor-check'     WHERE id = 41707 AND deleted = 0;
UPDATE system_menu SET icon = 'lucide:rotate-ccw'        WHERE id = 41708 AND deleted = 0;
UPDATE system_menu SET icon = 'lucide:id-card'           WHERE id = 41709 AND deleted = 0;

-- 直播与讲师体系
UPDATE system_menu SET icon = 'lucide:monitor-play'      WHERE id = 41800 AND deleted = 0;
UPDATE system_menu SET icon = 'lucide:radio'             WHERE id = 41801 AND deleted = 0;
UPDATE system_menu SET icon = 'lucide:building-2'        WHERE id = 41802 AND deleted = 0;
UPDATE system_menu SET icon = 'lucide:user-plus'         WHERE id = 41803 AND deleted = 0;
UPDATE system_menu SET icon = 'lucide:users-round'       WHERE id = 41804 AND deleted = 0;
UPDATE system_menu SET icon = 'lucide:activity'          WHERE id = 41805 AND deleted = 0;
UPDATE system_menu SET icon = 'lucide:scale'             WHERE id = 41806 AND deleted = 0;

-- 校企合作与创新大赛
UPDATE system_menu SET icon = 'lucide:school'            WHERE id = 41040 AND deleted = 0;
UPDATE system_menu SET icon = 'lucide:graduation-cap'    WHERE id = 41041 AND deleted = 0;
UPDATE system_menu SET icon = 'lucide:folder-sync'       WHERE id = 41042 AND deleted = 0;
UPDATE system_menu SET icon = 'lucide:user-check'        WHERE id = 41043 AND deleted = 0;
UPDATE system_menu SET icon = 'lucide:trophy'            WHERE id = 41044 AND deleted = 0;
UPDATE system_menu SET icon = 'lucide:file-up'           WHERE id = 41045 AND deleted = 0;
UPDATE system_menu SET icon = 'lucide:medal'             WHERE id = 41046 AND deleted = 0;
UPDATE system_menu SET icon = 'lucide:folder-open'       WHERE id = 41047 AND deleted = 0;
