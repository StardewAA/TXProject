/**
 * 云培训学院 · 考试题库「工种标签」自动打标脚本
 *
 * 作用：按题干/选项关键词，为考试题库现有题目批量打上 work_type 维度的工种标签，
 *       使「试卷管理 → 随机组卷」可以按工种范围抽题。
 *
 * 使用前提：
 *   1. 本地服务已启动（gateway 48080 / training 48094 / 容器 training-exam 8000）；
 *   2. 已执行 sql/2026-09-21-training-work-type-tags.sql（工种标签种子，56 个工种）；
 *   3. admin/admin123 可登录（或用环境变量覆盖账号，见下方常量）。
 *
 * 运行：node scripts/auto-tag-worktype.mjs
 * 可选环境变量：ADMIN_API_BASE（默认 http://127.0.0.1:48080/admin-api）、DB_CONTAINER（默认 txgy-mysql）
 *
 * 说明：覆盖不足 100% 时不写库（会列出未分配题目及题干，便于补充关键词规则）；
 *       幂等：重复执行自动跳过已存在的关联。
 */
import { execSync } from 'node:child_process';
const BASE = process.env.ADMIN_API_BASE || 'http://127.0.0.1:48080/admin-api';
const DB_CONTAINER = process.env.DB_CONTAINER || 'txgy-mysql';
const login = await (await fetch(`${BASE}/system/auth/login`, {
  method: 'POST',
  headers: { 'tenant-id': '1', 'Content-Type': 'application/json' },
  body: JSON.stringify({ username: 'admin', password: 'admin123' }),
})).json();
const H = { 'tenant-id': '1', Authorization: `Bearer ${login.data.accessToken}` };
const wt = await (await fetch('http://127.0.0.1:8000/api/work-types', { headers: H })).json();
const wtByName = Object.fromEntries(wt.data.map((x) => [x.name, x.id]));
const qs = await (await fetch('http://127.0.0.1:8000/api/questions', { headers: H })).json();

const L1 = [
  ['数字化/MES专员', ['MES', '数字化平台', '平台填报', '系统填报', '报表看板', '影像上传', '影像资料', '影像采集', '报工录入', '数据填报', '平台操作', '系统操作', '数据管理', '数据审核', '数据造假', '数据失真', '数字化', '平台规范', '归档']],
  ['商务与投标专员', ['招投标', '招标', '投标', '商务', '合同', '客户需求', '响应资料', '报价']],
  ['射线检测员（RT）', ['射线检测', 'RT检测', 'X射线', 'γ射线', '底片评定']],
  ['超声检测员（UT）', ['超声检测', 'UT检测', '超声波']],
  ['磁粉/渗透检测员（MT/PT）', ['磁粉检测', '渗透检测', 'MT/PT']],
  ['涡流/漏磁检测员', ['涡流', '漏磁']],
  ['无损检测工', ['无损检测', '探伤', '无损']],
  ['3PE/粉末防腐工', ['3PE', '环氧粉末', 'FBE', '三层结构', '粉末防腐']],
  ['表面处理工（喷砂/抛丸）', ['喷砂', '抛丸', '表面处理', '除锈等级', '打磨', '除锈']],
  ['补口补伤工', ['补口', '补伤', '热收缩带', '热缩套']],
  ['内涂施工工', ['内涂', '内防腐', '内壁涂']],
  ['内衬施工工', ['内衬', '玻璃钢', '陶瓷贴片', '衬里']],
  ['特种防腐工（玻璃钢/陶瓷）', ['玻璃钢', '陶瓷', '特种防腐']],
  ['储罐防腐工', ['储罐防腐', '罐壁', '罐底', '储罐']],
  ['保冷工（绝热施工）', ['保冷', '绝热', '保冷层']],
  ['冬防保温工', ['冬防', '防冻']],
  ['保温工', ['保温', '岩棉', '玻璃棉', '硅酸铝', '保温层', '隔热', '铁皮', '镀锌铁皮', '外护']],
  ['防腐保温工', ['防腐', '防腐层', '防腐涂料', '腐蚀']],
  ['防腐涂层检验员', ['涂层检验', '涂层检测', '漆膜', '防腐质量检验']],
  ['涂装工', ['涂装', '油漆', '喷涂', '刷漆']],
  ['理化试验员', ['理化试验', '理化检验', '拉伸试验', '冲击试验', '化学成分']],
  ['计量检定员', ['计量', '检定', '校准', '量具']],
  ['压力容器/管道检验员', ['压力容器', '压力管道', '特种设备']],
  ['气密性与气体检测员', ['气密性', '气体检测', '可燃气体', '有毒气体', '测爆']],
  ['电气防雷防爆检测员', ['防雷', '防爆', '静电接地', '接地电阻']],
  ['设施设备检测员', ['设施检测', '设备检测']],
  ['管材管件检测员', ['管材', '管件检验', '钢管检验']],
  ['油管修复工', ['油管修复', '抽油杆', '油管', '修复线']],
  ['油杆修复工', ['油杆修复']],
  ['管道修复补强工', ['管道修复', '补强', '夹具注胶']],
  ['套管修复工', ['套管修复', '套管']],
  ['装备钻头修复工', ['钻头']],
  ['井口作业防腐工', ['井口']],
  ['防蜡防垢作业工', ['防蜡', '防垢', '清蜡']],
  ['摩擦焊操作工', ['摩擦焊']],
  ['管线安装工', ['管线安装', '管道安装', '组对', '焊接组对']],
  ['电焊工', ['电焊', '焊条', '焊接质量', '焊接工艺', '焊工', '焊接']],
  ['电工（维修电工）', ['电工', '电缆', '配电', '电气', '电机', '断路器', '临时用电', '接地', '用电安全']],
  ['架子工', ['脚手架', '架子']],
  ['起重吊装指挥/司索', ['吊装', '司索', '起重', '指挥信号']],
  ['HSE安全员', ['HSE', '安全交底', '安全隐患', '动火作业', '受限空间', '高处作业', '应急预案', '应急', '事故复盘', '案例复盘', '吸取教训', '作业票', '消防', '职业健康', '安全生产', '安全教育', '劳保', '安全帽', '劳动防护', 'PPE']],
  ['工段长/施工负责人', ['三检制', '三检', '不合格工程', '闭环处置', '施工过程质量']],
  ['标准与资料员', ['检测报告', '报告编制', '资料归档']],
  ['防腐工艺技术员', ['防腐方案', '现场勘查', '技术方案', '方案选择']],
  ['保温工', ['供热', '采暖']],
  ['培训师/内部讲师', ['培训师', '内部讲师', '授课', '培训计划', '入职培训', '岗位职责和职业']],
  ['商务与投标专员', ['洽谈', '产品线', '业务全景', '一体化解决方案', '销售']],
  ['仓储管理员', ['仓储', '入库', '出库', '库存', '盘点', '码放', '堆放', '吊运', '库房']],
  ['材料工程师', ['材料验收', '进场验收', '来料', '材料报验', '复验', '材质证明']],
  ['材料采购员', ['采购', '供应商', '询价']],
  ['标准与资料员', ['标准规范', '资料员', '竣工资料', '文件归档', '规范引用']],
  ['生产调度', ['生产调度', '调度会', '排产']],
  ['培训师/内部讲师', ['培训师', '内部讲师', '授课', '培训计划']],
  ['班组长', ['班组长', '班组会', '班前']],
  ['工段长/施工负责人', ['工段长', '施工负责人', '现场负责']],
  ['项目经理', ['项目经理', '项目管理', '项目计划', '项目验收']],
];
const L2 = [
  ['无损检测', '超声检测员（UT）'], ['数字化平台', '数字化/MES专员'], ['数据管理', '数字化/MES专员'],
  ['材料验收', '材料工程师'], ['仓储管理', '仓储管理员'], ['质量管理', '质量检验员'],
  ['管杆修复', '油管修复工'], ['管道修复', '管道修复补强工'], ['保冷', '保冷工（绝热施工）'],
  ['保温施工', '保温工'], ['涂装施工', '防腐涂装工'], ['涂装检测', '防腐涂层检验员'],
  ['表面处理', '表面处理工（喷砂/抛丸）'], ['内衬技术', '内衬施工工'], ['冬防', '冬防保温工'],
  ['技术管理', '防腐工艺技术员'], ['项目管理', '项目经理'], ['复工', 'HSE安全员'], ['岗前培训', 'HSE安全员'],
];

const resolve = (q) => {
  const text = (q.stem || '') + JSON.stringify(q.options || '');
  const ownTags = q.tags?.tag_names || [];
  for (const [wtName, words] of L1) {
    if (!wtByName[wtName]) continue;
    for (const w of words) if (text.includes(w)) return wtName;
  }
  for (const [frag, wtName] of L2) {
    if (ownTags.some((t) => t.includes(frag)) && wtByName[wtName]) return wtName;
  }
  for (const t of ownTags) {
    const core = t.replace(/管理|技术|检测|验收|施工/g, '');
    if (core.length < 2) continue;
    const hit = wt.data.find((w) => w.name.includes(core));
    if (hit) return hit.name;
  }
  return null;
};

const assign = {}; const unmatched = [];
for (const q of qs.data) {
  const wtName = resolve(q);
  if (wtName) (assign[wtName] = assign[wtName] || []).push(q.id);
  else unmatched.push({ id: q.id, stem: (q.stem || '').slice(0, 26) });
}
console.log('=== 分配预览 ===');
Object.entries(assign).forEach(([k, v]) => console.log(`  ${k} : ${v.length} 题`));
console.log(`未分配: ${unmatched.length} 题`);
unmatched.forEach((u) => console.log(`  #${u.id} ${u.stem}`));
if (unmatched.length > 0) { console.log('存在未分配，不写库'); process.exit(2); }

const lines = ['use `ruoyi-vue-pro`;'];
let n = 0;
for (const [wtName, ids] of Object.entries(assign)) {
  const tagId = wtByName[wtName];
  for (const qid of ids) {
    lines.push(`INSERT INTO training_question_tag (question_id, tag_id, creator, updater, tenant_id) SELECT ${qid}, ${tagId}, '1', '1', 1 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM training_question_tag WHERE question_id=${qid} AND tag_id=${tagId} AND deleted=0);`);
    n++;
  }
}
lines.push(`SELECT t.name AS work_type, COUNT(*) AS questions FROM training_question_tag qt JOIN training_tag t ON t.id=qt.tag_id WHERE t.dimension='work_type' AND qt.deleted=0 GROUP BY t.name ORDER BY questions DESC;`);
execSync(`docker exec -i ${DB_CONTAINER} mysql -uroot -p123456 --default-character-set=utf8mb4`, { input: lines.join('\n'), encoding: 'utf8', maxBuffer: 10 * 1024 * 1024 });
console.log(`=== 已写入 ${n} 条工种标签关联，最终分布见上 ===`);
