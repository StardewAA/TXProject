-- 修复：培训管理二级菜单“一左一右”不对齐 + 课程分类树夹带测试遗留域
-- 背景：
--   1) 培训管理(41004)下 41011~41015 五个二级菜单无 icon，而 41152/41153 有 icon，
--      vben 侧边栏有图标与无图标的菜单项文字缩进不同，导致视觉不对齐 → 统一补齐 lucide 图标
--   2) 课程分类树中残留测试数据“验收测试分类-模块二”(id=29, code=NULL)，
--      显示于“检测与理化试验”与“材料与采购”之间，属验收测试遗留 → 软删并解开课程引用
-- 执行方式：幂等，可重复执行

-- 1. 补齐培训管理二级菜单图标
UPDATE system_menu SET icon='lucide:users'           WHERE id=41011 AND deleted=0;
UPDATE system_menu SET icon='lucide:user-cog'        WHERE id=41012 AND deleted=0;
UPDATE system_menu SET icon='lucide:trending-up'     WHERE id=41013 AND deleted=0;
UPDATE system_menu SET icon='lucide:bell-ring'       WHERE id=41014 AND deleted=0;
UPDATE system_menu SET icon='lucide:clipboard-check' WHERE id=41015 AND deleted=0;

-- 2. 软删测试遗留课程分类域（如已删则为幂等空操作）
UPDATE training_course_category SET deleted=1 WHERE id=29 AND deleted=0;

-- 3. 解开挂在该域下的课程分类引用（课程数据保留，归为未分类）
UPDATE training_course SET category_id=NULL WHERE category_id=29 AND deleted=0;

-- 校验
-- SELECT id,name,icon FROM system_menu WHERE id BETWEEN 41011 AND 41015 AND deleted=0;
-- SELECT id,name,deleted+0 FROM training_course_category WHERE id=29;
-- SELECT COUNT(*) FROM training_course WHERE category_id=29 AND deleted=0;  -- 期望 0
