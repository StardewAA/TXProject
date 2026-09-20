-- 修复：培训管理二级菜单“一左一右”不对齐 + 课程分类树夹带测试遗留域
-- 背景：
--   1) 培训管理(41004)下 41011~41015 五个二级菜单无 icon，而 41152/41153 有 icon，
--      vben 侧边栏有图标与无图标的菜单项文字缩进不同，导致视觉不对齐 → 统一补齐 lucide 图标
--   2) 课程分类树一级“域”中残留验收测试数据，显示于“检测与理化试验”与“材料与采购”之间：
--      id=29 验收测试分类-模块二（code=NULL），以及 id=184~196 的 13 个域
--      （行业新规政策/国标规范解读/防腐工艺教程/保温施工工法/材料选型知识/生产操作规范/
--        质检检验教程/安全环保实操/高危作业培训/招投标合规/项目管理/职业素养/持证上岗专项课程）
--      → 全部软删（均无子分类、无课程挂载）
-- 执行方式：幂等，可重复执行

-- 1. 补齐培训管理二级菜单图标
UPDATE system_menu SET icon='lucide:users'           WHERE id=41011 AND deleted=0;
UPDATE system_menu SET icon='lucide:user-cog'        WHERE id=41012 AND deleted=0;
UPDATE system_menu SET icon='lucide:trending-up'     WHERE id=41013 AND deleted=0;
UPDATE system_menu SET icon='lucide:bell-ring'       WHERE id=41014 AND deleted=0;
UPDATE system_menu SET icon='lucide:clipboard-check' WHERE id=41015 AND deleted=0;

-- 2. 软删测试遗留课程分类域（如已删则为幂等空操作）
UPDATE training_course_category SET deleted=1 WHERE id=29 AND deleted=0;
UPDATE training_course_category SET deleted=1 WHERE id BETWEEN 184 AND 196 AND deleted=0;

-- 3. 解开挂在这些域下的课程分类引用（课程数据保留，归为未分类）
UPDATE training_course SET category_id=NULL WHERE category_id=29 AND deleted=0;
UPDATE training_course SET category_id=NULL WHERE category_id BETWEEN 184 AND 196 AND deleted=0;

-- 校验
-- SELECT id,name,icon FROM system_menu WHERE id BETWEEN 41011 AND 41015 AND deleted=0;
-- SELECT id,name,deleted+0 FROM training_course_category WHERE id=29 OR (id BETWEEN 184 AND 196);  -- 期望全为 1
-- SELECT COUNT(*) FROM training_course WHERE (category_id=29 OR category_id BETWEEN 184 AND 196) AND deleted=0;  -- 期望 0
