-- 修复 HRM 职位类型历史节点仍直接挂在一级分类下的问题。
-- 合并重复叶子时保留历史 ID 并迁移招聘职位引用；仅修改 HRM 表。
SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TEMPORARY TABLE tmp_hrm_post_type_reparent (
  source_root varchar(255) NOT NULL,
  source_name varchar(255) NOT NULL,
  target_root varchar(255) NOT NULL,
  target_group varchar(255) NOT NULL,
  target_name varchar(255) NOT NULL,
  sort int NOT NULL,
  PRIMARY KEY(source_root,source_name)
) ENGINE=MEMORY;

INSERT INTO tmp_hrm_post_type_reparent VALUES
('施工技能类（一线操作岗）','防腐工','施工技能类（一线操作岗）','防腐蚀作业','防腐工',1),
('施工技能类（一线操作岗）','喷砂工','施工技能类（一线操作岗）','防腐蚀作业','喷砂/抛丸除锈工',2),
('施工技能类（一线操作岗）','油漆工','施工技能类（一线操作岗）','防腐蚀作业','油漆工',3),
('施工技能类（一线操作岗）','保温工','施工技能类（一线操作岗）','保温保冷作业','保温工',1),
('施工技能类（一线操作岗）','管工','施工技能类（一线操作岗）','管道与结构安装','管工',1),
('施工技能类（一线操作岗）','铆工','施工技能类（一线操作岗）','管道与结构安装','铆工',2),
('施工技能类（一线操作岗）','焊工','施工技能类（一线操作岗）','管道与结构安装','焊工',3),
('施工技能类（一线操作岗）','架子工','施工技能类（一线操作岗）','管道与结构安装','架子工',5),
('施工技能类（一线操作岗）','电工','施工技能类（一线操作岗）','管道与结构安装','电工',6),
('施工技能类（一线操作岗）','瓦工','施工技能类（一线操作岗）','土建配套','瓦工',1),
('施工技能类（一线操作岗）','钢筋工','施工技能类（一线操作岗）','土建配套','钢筋工',2),
('施工技能类（一线操作岗）','木工','施工技能类（一线操作岗）','土建配套','木工',3),
('施工技能类（一线操作岗）','缝纫工','施工技能类（一线操作岗）','生产车间操作','缝纫工',6),
('施工技能类（一线操作岗）','普工/力工','施工技能类（一线操作岗）','辅助工种','普工/力工',1),
('项目管理类','项目经理','项目管理类','项目决策','项目经理',1),
('项目管理类','项目副经理','项目管理类','项目决策','项目副经理',2),
('项目管理类','施工员','项目管理类','现场管理','施工员',1),
('项目管理类','施工队长/工长','项目管理类','现场管理','施工队长/工长',2),
('项目管理类','预算员/造价员','项目管理类','商务管理','预算员/造价员',1),
('项目管理类','安全员','项目管理类','安全管理','安全员',1),
('项目管理类','技术负责人/工程师','工程技术类','技术支持','技术负责人',2),
('质量检验类','质检员','质量检验类','质量检验','质检员',1),
('质量检验类','试验/检测员','质量检验类','质量检验','试验/检测员',3),
('质量检验类','资料员','质量检验类','质量检验','资料员',10),
('质量检验类','材料员/库管','采购与供应链类','采购与供应链','材料员',5),
('质量检验类','设备维修工','生产制造类','设备管理','设备维修工',3),
('质量检验类','机械设备管理员','生产制造类','设备管理','机械设备管理员',2),
('市场营销类','市场业务经理','市场营销类','销售','市场业务经理',1),
('市场营销类','投标专员','市场营销类','投标与资质','投标专员',1),
('市场营销类','采购员','采购与供应链类','采购与供应链','采购员',1),
('市场营销类','财务会计','职能管理类','财务','财务会计',1),
('市场营销类','行政人事专员','职能管理类','人力行政','行政人事专员',1),
('培训生/储备类','防腐保温管理（校招管培方向）','培训生/储备类','培训生/储备','防腐保温管理培训生',1);

-- 同名的新节点可能已经被前一次补数脚本创建。把其招聘职位引用合并至历史 ID。
UPDATE hrm_recruit_post p
JOIN hrm_recruit_post_type duplicate_type ON duplicate_type.id=p.post_type_id AND duplicate_type.tenant_id=p.tenant_id
JOIN hrm_recruit_post_type historical_type ON historical_type.tenant_id=duplicate_type.tenant_id
 AND historical_type.id<>duplicate_type.id AND historical_type.deleted=b'0'
JOIN hrm_recruit_post_type source_root ON source_root.tenant_id=historical_type.tenant_id
 AND source_root.id=historical_type.parent_id AND source_root.parent_id=0
JOIN tmp_hrm_post_type_reparent m ON m.source_root=source_root.name AND m.source_name=historical_type.name
JOIN hrm_recruit_post_type target_root ON target_root.tenant_id=historical_type.tenant_id
 AND target_root.parent_id=0 AND target_root.name=m.target_root AND target_root.deleted=b'0'
JOIN hrm_recruit_post_type target_group ON target_group.tenant_id=historical_type.tenant_id
 AND target_group.parent_id=target_root.id AND target_group.name=m.target_group AND target_group.deleted=b'0'
SET post_type_id=historical_type.id
WHERE duplicate_type.parent_id=target_group.id AND duplicate_type.name=m.target_name
  AND duplicate_type.deleted=b'0';

-- 已合并引用的重复新叶子删除，历史叶子随后改名并移至正确的二级分类。
DELETE duplicate_type FROM hrm_recruit_post_type duplicate_type
JOIN hrm_recruit_post_type historical_type ON historical_type.tenant_id=duplicate_type.tenant_id
 AND historical_type.id<>duplicate_type.id AND historical_type.deleted=b'0'
JOIN hrm_recruit_post_type source_root ON source_root.tenant_id=historical_type.tenant_id
 AND source_root.id=historical_type.parent_id AND source_root.parent_id=0
JOIN tmp_hrm_post_type_reparent m ON m.source_root=source_root.name AND m.source_name=historical_type.name
JOIN hrm_recruit_post_type target_root ON target_root.tenant_id=historical_type.tenant_id
 AND target_root.parent_id=0 AND target_root.name=m.target_root AND target_root.deleted=b'0'
JOIN hrm_recruit_post_type target_group ON target_group.tenant_id=historical_type.tenant_id
 AND target_group.parent_id=target_root.id AND target_group.name=m.target_group AND target_group.deleted=b'0'
WHERE duplicate_type.parent_id=target_group.id AND duplicate_type.name=m.target_name
  AND duplicate_type.deleted=b'0';

UPDATE hrm_recruit_post_type
JOIN hrm_recruit_post_type source_root ON source_root.tenant_id=hrm_recruit_post_type.tenant_id
 AND source_root.id=hrm_recruit_post_type.parent_id AND source_root.parent_id=0
JOIN tmp_hrm_post_type_reparent m ON m.source_root=source_root.name AND m.source_name=hrm_recruit_post_type.name
JOIN hrm_recruit_post_type target_root ON target_root.tenant_id=hrm_recruit_post_type.tenant_id
 AND target_root.parent_id=0 AND target_root.name=m.target_root AND target_root.deleted=b'0'
JOIN hrm_recruit_post_type target_group ON target_group.tenant_id=hrm_recruit_post_type.tenant_id
 AND target_group.parent_id=target_root.id AND target_group.name=m.target_group AND target_group.deleted=b'0'
SET hrm_recruit_post_type.name=m.target_name, hrm_recruit_post_type.parent_id=target_group.id,
    hrm_recruit_post_type.sort=m.sort, hrm_recruit_post_type.updater='seed', hrm_recruit_post_type.update_time=NOW()
WHERE hrm_recruit_post_type.deleted=b'0';

DROP TEMPORARY TABLE tmp_hrm_post_type_reparent;
