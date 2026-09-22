/** 职业认证进阶路径数据（来源：云培训学院-岗位工种表） */

export const ROADMAP_LEVELS = ['初级工', '中级工', '高级工', '技师', '高级技师'] as const;

export const LEVEL_PREREQ: Record<string, string> = {
  初级工: '完成基础课程学习',
  中级工: '取得本工种初级工认证',
  高级工: '取得本工种中级工认证',
  技师: '取得本工种高级工认证',
  高级技师: '取得本工种技师认证',
};

/** 各等级培训介绍模板（按工种关键技能生成） */
export const LEVEL_INTRO: Record<string, (skill: string) => string> = {
  初级工: (skill) =>
    `在带教师傅指导下学习${skill}等基础操作，熟悉岗位安全规程与质量验收标准，掌握常用工机具的使用与日常维护，达到独立上岗的基本要求。`,
  中级工: (skill) =>
    `能够独立完成${skill}等常规作业，正确执行施工工艺文件与质量检验要求，具备常见现场问题的判断与处理能力，作业质量稳定合格。`,
  高级工: (skill) =>
    `熟练掌握${skill}核心技能，能够应对复杂工况与特殊环境下的作业要求，参与施工方案的优化与落实，指导初级、中级人员规范作业。`,
  技师: (skill) =>
    `精通${skill}相关工艺技术，能够组织关键工序施工、质量把控与过程验收，解决现场疑难问题，承担班组培训、技能考评与技术交底。`,
  高级技师: (skill) =>
    `系统掌握本工种全流程技术体系，主导${skill}领域的工艺改进与创新攻关，组织编制培训教材与作业标准，培养高技能人才梯队。`,
};

export interface RoadmapJob {
  name: string;
  skill: string;
  /** 最高可认证等级 */
  top: string;
}

export interface RoadmapGroup {
  key: string;
  /** 序列（二级目录名称） */
  name: string;
  jobs: RoadmapJob[];
}

const OP_TOP = '高级技师';
const TC_TOP = '技师';

export const ROADMAP_GROUPS: RoadmapGroup[] = [
  {
    key: 'op',
    name: '操作人员',
    jobs: [
      { name: '表面处理工（喷砂/抛丸）', skill: '除锈等级判定、喷砂设备操作', top: OP_TOP },
      { name: '防腐涂装工', skill: '高压无气喷涂、膜厚控制', top: OP_TOP },
      { name: '储罐防腐工', skill: '罐内防腐、内衬玻璃钢', top: OP_TOP },
      { name: '3PE/粉末防腐工', skill: '三层挤出缠带、熔结粉末涂覆', top: OP_TOP },
      { name: '补口补伤工', skill: '热收缩套施工、搭接控制', top: OP_TOP },
      { name: '内涂施工工', skill: '内涂设备操作、内涂层检测', top: OP_TOP },
      { name: '特种防腐工（玻璃钢/陶瓷）', skill: '环氧玻璃钢、金属陶瓷内衬', top: OP_TOP },
      { name: '保温工', skill: '管道与设备保温、异形件制作', top: OP_TOP },
      { name: '保冷工（绝热施工）', skill: '聚氨酯浇注、泡沫玻璃砌筑、防潮层', top: OP_TOP },
      { name: '铁皮加工安装工', skill: '保护层放样、咬口制作与安装', top: OP_TOP },
      { name: '冬防保温工', skill: '冬防保温方案实施、伴热配合', top: OP_TOP },
      { name: '内衬施工工', skill: '高分子内衬铺设、非金属油管内衬', top: OP_TOP },
      { name: '油管修复工', skill: '油管清洗、检验与修复', top: OP_TOP },
      { name: '油杆修复工', skill: '油杆校直、检测与修复', top: OP_TOP },
      { name: '摩擦焊操作工', skill: '摩擦焊接头接箍修复', top: OP_TOP },
      { name: '管道修复补强工', skill: '补强套筒安装、带压堵漏', top: OP_TOP },
      { name: '套管修复工', skill: '套管与套筒修复', top: OP_TOP },
      { name: '装备钻头修复工', skill: '钻头修复、装备维修', top: OP_TOP },
      { name: '井口作业防腐工', skill: '井口修复与防腐作业', top: OP_TOP },
      { name: '防蜡防垢作业工', skill: '清蜡除垢工艺实施', top: OP_TOP },
      { name: '管线安装工', skill: '产能建设管线敷设与安装', top: OP_TOP },
      { name: '起重吊装指挥/司索', skill: '吊装指挥、吊物绑扎与司索', top: OP_TOP },
      { name: '电焊工', skill: '手工电弧焊、氩弧焊', top: OP_TOP },
      { name: '电工（维修电工）', skill: '临时用电敷设、电伴热维护', top: OP_TOP },
      { name: '架子工', skill: '脚手架搭设与拆除', top: OP_TOP },
    ],
  },
  {
    key: 'tc',
    name: '技术人员',
    jobs: [
      { name: '防腐工艺技术员', skill: '防腐方案编制、涂层体系选型', top: TC_TOP },
      { name: '保温绝热技术员', skill: '保温厚度计算、保冷方案设计', top: TC_TOP },
      { name: '内衬修复技术员', skill: '内衬选型、缺陷评估与修复方案', top: TC_TOP },
      { name: '材料工程师', skill: '材料指标验收、供方技术评审', top: TC_TOP },
      { name: '焊接工艺技术员', skill: '焊接工艺文件编制、焊接工艺评定', top: TC_TOP },
      { name: '标准与资料员', skill: '施工资料管理、验收标准与过程留证', top: TC_TOP },
    ],
  },
  {
    key: 'qc',
    name: '质检检测人员',
    jobs: [
      { name: '防腐涂层检验员', skill: '膜厚、漏点、附着力检测', top: TC_TOP },
      { name: '保温工程检验员', skill: '厚度、保护层、防潮层验收', top: TC_TOP },
      { name: '超声检测员', skill: '超声探伤、测厚', top: TC_TOP },
      { name: '射线检测员', skill: '射线拍片、评片与辐射安全', top: TC_TOP },
      { name: '磁粉/渗透检测员', skill: '磁粉与渗透表面缺陷检测', top: TC_TOP },
      { name: '涡流/漏磁检测员', skill: '管材涡流、罐底漏磁检测', top: TC_TOP },
      { name: '管材管件检测员', skill: '废油管分级、钻具与油套管检测', top: TC_TOP },
      { name: '压力容器/管道检验员', skill: '容器与管道定期检验', top: TC_TOP },
      { name: '储罐检验员', skill: '罐壁、罐底检测', top: TC_TOP },
      { name: '电气防雷防爆检测员', skill: '防雷接地、防爆检测', top: TC_TOP },
      { name: '气密性与气体检测员', skill: '气密性试验、气体分析', top: TC_TOP },
      { name: '理化试验员', skill: '力学性能试验、金相分析', top: TC_TOP },
      { name: '计量检定员', skill: '计量器具检定与校准', top: TC_TOP },
      { name: '设施设备检测员', skill: '设施与机井架检测', top: TC_TOP },
    ],
  },
  {
    key: 'mg',
    name: '管理人员',
    jobs: [
      { name: '班组长', skill: '派工管理、日常管理、报工审核', top: TC_TOP },
      { name: '工段长/施工负责人', skill: '技术交底、进度质量安全管控', top: TC_TOP },
      { name: '项目经理', skill: '项目策划、成本控制、结算管理', top: TC_TOP },
      { name: '生产调度', skill: '资源调度、计划平衡', top: TC_TOP },
      { name: '培训师/内部讲师', skill: '授课组织、课程编写、实操考评', top: TC_TOP },
    ],
  },
  {
    key: 'sp',
    name: '支持人员',
    jobs: [
      { name: '安全员（健康安全环保）', skill: '风险识别、作业许可、应急管理', top: TC_TOP },
      { name: '设备管理员', skill: '喷砂机、喷涂机等设备管理', top: TC_TOP },
      { name: '材料采购员', skill: '采购流程管理、供方管理', top: TC_TOP },
      { name: '仓储管理员', skill: '材料收发、保质期管理', top: TC_TOP },
      { name: '数字化/制造执行专员', skill: '系统运维、数据治理', top: TC_TOP },
      { name: '商务与投标专员', skill: '招投标响应、工程量结算', top: TC_TOP },
    ],
  },
];
