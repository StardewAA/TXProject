/**
 * Java 侧（txgy-module-bid）契约 Mock，仅用于本地联调与冒烟测试。
 * 覆盖 §3.1 的 5 个接口 + 影子状态上报。
 */
import http from 'node:http';

const PORT = Number(process.env.MOCK_JAVA_PORT ?? 48081);

/** 调用计数与最近一次入库入参，供本地验证脚本断言（B3/B4/B5） */
const stats = { login: 0, index: 0, delete: 0, pricing: 0, lastIndexBody: null };

const FACTS = [
  { factId: 'F-001', type: 'LICENSE', name: '营业执照编号', value: '91110000MA01B2C3XK', sourceDocName: '营业执照.pdf' },
  { factId: 'F-002', type: 'QUAL', name: '安全生产许可证编号', value: '(京)JZ安许证字[2023]001234', sourceDocName: '安全生产许可证.pdf' },
  { factId: 'F-003', type: 'FINANCE', name: '注册资本', value: '5000', unit: '万元', sourceDocName: '审计报告2025.pdf' },
  { factId: 'F-004', type: 'PERSON', name: '项目经理姓名', value: '张伟', sourceDocName: '人员证书.pdf' },
  { factId: 'F-005', type: 'PERFORMANCE', name: '类似项目业绩', value: 'XX市轨道交通3号线机电安装工程', sourceDocName: '业绩合同2024.pdf' },
  { factId: 'F-006', type: 'PLAN', name: '开工日期', value: '2026-03-01', sourceDocName: '项目计划.pdf' },
  { factId: 'F-007', type: 'PLAN', name: '竣工日期', value: '2026-09-27', sourceDocName: '项目计划.pdf' },
  { factId: 'F-008', type: 'PRICE', name: '投标报价', value: '1286.50', unit: '万元', sourceDocName: '报价表.pdf' },
  { factId: 'F-009', type: 'PERSON', name: '法定代表人', value: '张伟', sourceDocName: '营业执照.pdf' },
  { factId: 'F-010', type: 'COMPANY', name: '企业名称', value: '北京建工机电安装工程有限公司', sourceDocName: '营业执照.pdf' },
  { factId: 'F-011', type: 'COMPANY', name: '单位性质', value: '有限责任公司', sourceDocName: '营业执照.pdf' },
  { factId: 'F-012', type: 'COMPANY', name: '注册地址', value: '北京市海淀区中关村大街1号', sourceDocName: '营业执照.pdf' },
];

const CHUNK_BANK = [
  {
    chunkId: 'C-1001',
    content: '投标人不具备招标文件规定的资格条件的，其投标作废标处理；投标文件未按要求加盖公章或无法定代表人签字的，亦作废标处理。',
    sourceDocName: '招标文件.pdf',
    chapterPath: '第三章 评标办法 / 3.1 初步评审',
    pageNo: 21,
    sourceBlockIds: ['B-1001', 'B-1002'],
  },
  {
    chunkId: 'C-1002',
    content: '本项目工期要求为 210 日历天，计划开工日期 2026 年 3 月 1 日，竣工日期 2026 年 9 月 27 日。',
    sourceDocName: '招标文件.pdf',
    chapterPath: '第二章 投标人须知 / 前附表',
    pageNo: 9,
    sourceBlockIds: ['B-1003'],
  },
  {
    chunkId: 'C-1003',
    content: '工程质量应达到国家现行施工验收规范合格标准，质保期 24 个月，缺陷责任期 12 个月。',
    sourceDocName: '招标文件.pdf',
    chapterPath: '第五章 技术标准和要求',
    pageNo: 45,
    sourceBlockIds: ['B-1004'],
  },
  {
    chunkId: 'C-1004',
    content: '投标报价不得超过最高限价 1300 万元，超过者作废标处理；报价应包含暂列金额 50 万元。',
    sourceDocName: '招标文件.pdf',
    chapterPath: '第二章 投标人须知 / 前附表',
    pageNo: 11,
    sourceBlockIds: ['B-1005'],
  },
  {
    chunkId: 'C-1005',
    content: '投标人近三年须具有至少 1 项合同额不低于 1000 万元的类似机电安装业绩，并提供合同关键页扫描件。',
    sourceDocName: '招标文件.pdf',
    chapterPath: '第三章 评标办法 / 3.2 详细评审',
    pageNo: 26,
    sourceBlockIds: ['B-1006'],
  },
  {
    chunkId: 'C-2001',
    content:
      '施工组织设计：施工阶段按设备机房、管线综合、末端安装、系统调试四大阶段划分并交叉作业协调。通风空调覆盖送排风、防排烟及多联机空调；给排水及消防含室内外给排水、自动喷淋与消火栓；动力照明及低压配电按防火分区设置配电干线；火灾自动报警系统联动消防设备。关键线路以设备机房及管井移交为里程碑，主线路移交后 60 天内完成主干管安装，系统联调 30 天；关键线路节点工期偏差不超过 3 天。',
    sourceDocName: '施工组织设计-技术方案.pdf',
    chapterPath: '技术标 / 施工组织设计',
    pageNo: 12,
    sourceBlockIds: ['B-2001', 'B-2002'],
  },
];

function readBody(req) {
  return new Promise((resolve) => {
    const chunks = [];
    req.on('data', (chunk) => chunks.push(chunk));
    req.on('end', () => {
      if (chunks.length === 0) return resolve({});
      try {
        resolve(JSON.parse(Buffer.concat(chunks).toString('utf8')));
      } catch {
        resolve({});
      }
    });
  });
}

function json(res, status, body) {
  const payload = JSON.stringify(body);
  res.writeHead(status, { 'Content-Type': 'application/json; charset=utf-8', 'Content-Length': Buffer.byteLength(payload) });
  res.end(payload);
}

const server = http.createServer(async (req, res) => {
  const url = new URL(req.url ?? '/', `http://${req.headers.host ?? 'localhost'}`);
  const pathname = url.pathname.replace(/\/+$/, '') || '/';
  const method = req.method ?? 'GET';
  const body = method === 'POST' ? await readBody(req) : {};
  console.log(`[mock-java] ${method} ${url.pathname}`);

  if (pathname === '/admin-api/bid/agent/retrieve' && method === 'POST') {
    const mandatory = body.mandatory ?? [];
    const miss = String(body.query ?? '').includes('未命中');
    if (miss) return json(res, 200, { chunks: [], hit: false });
    const chunks = CHUNK_BANK.map((chunk, index) => ({
      ...chunk,
      score: Number((0.93 - index * 0.06).toFixed(3)),
      matchedBy: index === 0 ? 'mandatory' : 'vector+bm25',
    }));
    if (mandatory.length > 0) {
      chunks[0] = { ...chunks[0], matchedBy: `mandatory:${mandatory[0]}` };
    }
    return json(res, 200, { chunks, hit: chunks.length > 0 });
  }

  if (pathname.startsWith('/admin-api/bid/agent/context/') && method === 'GET') {
    return json(res, 200, {
      projectFacts: { projectName: 'XX市轨道交通3号线机电安装工程', owner: 'XX市轨道交通集团' },
      tenderParse: { bidDeadline: '2026-02-20', bidBond: '20', unit: '万元' },
      globalFacts: [
        { name: '工期', value: '210', unit: '日历天' },
        { name: '最高限价', value: '1300', unit: '万元' },
        { name: '法定代表人', value: '张伟' },
        { name: '营业执照编号', value: '91110000MA01B2C3XK' },
      ],
    });
  }

  if (pathname === '/admin-api/bid/agent/facts' && method === 'GET') {
    const types = (url.searchParams.get('types') ?? '').split(',').filter(Boolean);
    const facts = types.length > 0 ? FACTS.filter((fact) => types.includes(fact.type)) : FACTS;
    return json(res, 200, { facts });
  }

  // COPY_PRICING 通道（P2 报价数据）：按现行契约路径提供样例数据，供本地端到端联调
  if (pathname === '/admin-api/bidding/bid-agent/contract/pricing' && method === 'GET') {
    stats.pricing += 1;
    return json(res, 200, {
      shared: { bid_total: '12865000.00', measure_fee: '234567.89', duration_days: '120', quality_standard: '合格' },
      tables: {
        bill: [{ 序号: '1', 项目编号: '010101001001', 项目名称: 'mock 分部分项测试项', 计量单位: 'm³', 工程数量: '100', 综合单价: '12.50', 合价: '1250.00' }],
        cost_breakdown: [{ 费用项: '分部分项工程费', '金额（元）': '10000000.00' }],
      },
    });
  }

  if (pathname === '/admin-api/bid/agent/draft/chapter' && method === 'POST') {
    console.log(`[mock-java] 章节草稿回写 nodeId=${body.nodeId} refs=${(body.refs ?? []).length} status=${body.status}`);
    return json(res, 200, { ok: true, draftId: `D-${body.nodeId}` });
  }

  if (pathname === '/admin-api/bid/agent/task-status' && method === 'POST') {
    console.log(`[mock-java] 影子状态 taskId=${body.taskId} status=${body.status} phase=${body.phase}`);
    return json(res, 200, { ok: true });
  }

  // ---------- 知识库入库链路（B3/B4/B5 验证用） ----------
  if (pathname === '/__stats' && method === 'GET') {
    return json(res, 200, stats);
  }

  if (pathname === '/admin-api/system/auth/login' && method === 'POST') {
    stats.login += 1;
    return json(res, 200, { code: 0, data: { accessToken: `mock-token-${stats.login}` } });
  }

  if (pathname === '/admin-api/bid/segment/index' && method === 'POST') {
    stats.index += 1;
    stats.lastIndexBody = body;
    return json(res, 200, { code: 0, data: 12 });
  }

  if (pathname === '/admin-api/bid/segment/delete' && method === 'DELETE') {
    stats.delete += 1;
    return json(res, 200, { code: 0, data: true });
  }

  return json(res, 404, { ok: false, message: 'not found' });
});

server.listen(PORT, '127.0.0.1', () => {
  console.log(`[mock-java] 契约 Mock 已启动: http://127.0.0.1:${PORT}`);
});
