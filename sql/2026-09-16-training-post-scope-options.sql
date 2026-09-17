-- 培训班「参训岗位」下拉数据源：将培训业务岗位工种（training_job）同步为系统岗位（system_post）
--
-- 背景：培训班「参训岗位」用于按岗位圈选参训人员，后端按系统岗位 ID 匹配用户（system_user_post），
--       而系统岗位表仅有「项目经理 / 普通员工 / 人力资源 / 测试」等少量通用岗位，
--       业务岗位（防腐涂装工、保温工、3PE/粉末防腐工 … 共 56 项）只存在于培训岗位工种表 training_job，
--       导致新增培训班时「参训岗位」下拉没有业务岗位可选。
--
-- 处理：将 training_job 中启用状态的岗位工种同步写入 system_post（岗位编码 JOB-xx），
--       使业务岗位既能出现在下拉中，又能被按岗位圈选学员的逻辑命中。
--
-- 幂等：按「岗位名称 + 租户」判重，重复执行不会新增重复数据。

INSERT INTO system_post
  (code, name, sort, status, remark, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT
  CONCAT('JOB-', j.code),
  j.name,
  IFNULL(j.sort, 0),
  0,
  CONCAT('培训岗位工种自动同步：', j.seq_name, ' / ', j.code),
  'system',
  NOW(),
  'system',
  NOW(),
  b'0',
  j.tenant_id
FROM training_job j
WHERE j.deleted = b'0'
  AND j.status = 'active'
  AND NOT EXISTS (
    SELECT 1
    FROM system_post p
    WHERE p.deleted = b'0'
      AND p.tenant_id = j.tenant_id
      AND p.name = j.name
  );

-- 校验：应补齐 56 个业务岗位
-- SELECT COUNT(*) FROM system_post WHERE deleted = b'0' AND code LIKE 'JOB-%';
-- SELECT id, code, name FROM system_post WHERE deleted = b'0' AND code LIKE 'JOB-%' ORDER BY id LIMIT 10;
