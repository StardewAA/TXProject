const items = [
  {
    id: "Q-001", type: "Q", stage: "主线A", sev: "严重", pri: "P0",
    title: "并发入职可突破需求编制",
    module: "招聘 / Offer 入职",
    steps: "需求编制=1；两个 Offer 同时 onboard",
    expected: "仅 1 人成功，另一人提示编制已满",
    actual: "校验 :542 与核增 :566 非原子（中间 createEmployee :561），可双增",
    code: "HrmRecruitOfferServiceImpl:542,:566",
    last: "新增（代码）"
  },
  {
    id: "Q-002", type: "Q", stage: "主线A", sev: "严重", pri: "P0",
    title: "Offer 有效期 send/confirm 口径不一致",
    module: "招聘 / Offer",
    steps: "validUntil 为空发放后再 confirm",
    expected: "长期有效两端一致",
    actual: "send :341-345 空=不限；confirm :439-440 强制非空。DRAFT 免审批发放为业务口径非缺陷",
    code: "HrmRecruitOfferServiceImpl:335-347,:436-441",
    last: "新增（代码）"
  },
  {
    id: "Q-003", type: "Q", stage: "主线A", sev: "严重", pri: "P1",
    title: "招聘职位无草稿态，保存即发布",
    module: "招聘管理 / 招聘职位",
    steps: "新建职位 → 保存",
    expected: "默认草稿，另行发布",
    actual: "弹窗提示保存后直接发布到招聘门户",
    code: "views/hrm/recruit/post/modules/form.vue",
    last: "仍在"
  },
  {
    id: "Q-004", type: "Q", stage: "主线B", sev: "严重", pri: "P1",
    title: "管理端请假无新增/代提入口",
    module: "考勤管理 / 请假",
    steps: "打开 /hrm/attendance/leave",
    expected: "HR 可代提或补录请假",
    actual: "仅查询/导出/审批，无新增按钮",
    code: "views/hrm/attendance/leave/index.vue",
    last: "仍在"
  },
  {
    id: "Q-005", type: "Q", stage: "权限", sev: "严重", pri: "P0",
    title: "门户写接口缺 @PreAuthorize，归属靠实现细节",
    module: "门户 / 简历与投递",
    steps: "调用 Portal Resume/Version/Agent/投递写接口",
    expected: "接口层显式鉴权 + 归属校验",
    actual: "默认已登录 + getLoginUserId 内隐归属；证件/培训仅 isAuthenticated",
    code: "HrmPortalResumeController:41-69 等",
    last: "新增（代码）"
  },
  {
    id: "Q-006", type: "Q", stage: "权限", sev: "严重", pri: "P0",
    title: "招聘代理任务同租户 IDOR",
    module: "招聘代理 / 任务",
    steps: "用户 A 用任务 ID 请求用户 B 的任务详情",
    expected: "403/404",
    actual: "getTaskInTenant 仅校验租户（HrmRecruitAgentTaskServiceImpl:137-149）",
    code: "HrmRecruitAgentTaskServiceImpl",
    last: "新增（代码）"
  },
  {
    id: "Q-007", type: "Q", stage: "权限", sev: "一般", pri: "P1",
    title: "薪资/社保/招聘刻意不做部门数据权限",
    module: "框架 / 数据权限",
    steps: "部门 HR 查询工资表/社保/招聘",
    expected: "若薪酬需隔离则按部门过滤",
    actual: "javadoc 刻意不注册 salary/insurance/recruit/简历（产品口径），合规风险仍在",
    code: "HrmDataPermissionConfiguration:48-50",
    last: "新增（代码）"
  },
  {
    id: "Q-008", type: "Q", stage: "权限", sev: "严重", pri: "P0",
    title: "actuator / druid 公网 permitAll",
    module: "安全配置",
    steps: "匿名访问 /actuator/** /druid/**",
    expected: "401/403",
    actual: "SecurityConfiguration:28-31 permitAll",
    code: "SecurityConfiguration",
    last: "新增（代码）"
  },
  {
    id: "Q-009", type: "Q", stage: "主线A", sev: "严重", pri: "P1",
    title: "需求状态机缺失 + 编制竞态",
    module: "招聘需求",
    steps: "非法状态流转；并发核增编制",
    expected: "状态矩阵 + 行锁",
    actual: "无转移矩阵；increaseOnboardCount check-then-act（HrmRecruitDemandServiceImpl:123-150）",
    code: "HrmRecruitDemandServiceImpl",
    last: "新增（代码）"
  },
  {
    id: "Q-010", type: "Q", stage: "主线B", sev: "严重", pri: "P1",
    title: "转正双路径无锁；定时离职可绕过交接",
    module: "员工生命周期",
    steps: "并行转正；未完成交接走定时离职",
    expected: "单入口互斥；交接未完成不可离结",
    actual: "applyEmployeeChange 有锁但转正路径不一；定时离职不阻塞 handover",
    code: "HrmEmployeeServiceImpl:505-564",
    last: "新增（代码）"
  },
  {
    id: "Q-011", type: "Q", stage: "主线B", sev: "一般", pri: "P1",
    title: "合同 EXPIRED 不会自动置位",
    module: "合同管理",
    steps: "合同到期日后观察状态",
    expected: "自动 EXPIRED + 通知",
    actual: "仅有预警 Job，状态不变更",
    code: "contract job / HrmContractSignRecordServiceImpl",
    last: "新增（代码）"
  },
  {
    id: "Q-012", type: "Q", stage: "主线B", sev: "严重", pri: "P0",
    title: "工资月表可重复创建同年月",
    module: "薪资 / 月度工资表",
    steps: "同一 year-month 创建两次",
    expected: "唯一约束拒绝",
    actual: "无 (tenant,year,month) 唯一（HrmSalaryMonthRecordServiceImpl）",
    code: "HrmSalaryMonthRecordServiceImpl",
    last: "新增（代码）"
  },
  {
    id: "Q-013", type: "Q", stage: "主线B", sev: "一般", pri: "P1",
    title: "核算就绪提示非阻断，未入组静默丢人",
    module: "薪资 / 核算",
    steps: "存在未入薪资组员工时点核算",
    expected: "列出并阻断",
    actual: "readiness 提示可忽略；:228-232 静默丢弃",
    code: "HrmSalaryMonthRecordServiceImpl:228-232",
    last: "仍在"
  },
  {
    id: "Q-014", type: "Q", stage: "主线B", sev: "一般", pri: "P2",
    title: "KPI 角标与默认列表可能不一致",
    module: "绩效 / KPI",
    steps: "对比角标数与默认列表 total",
    expected: "同源同步",
    actual: "上轮实测默认 0 条/角标 3；本轮探针仍见线索",
    code: "views/hrm/performance/plan/index.vue",
    last: "仍在"
  },
  {
    id: "Q-015", type: "Q", stage: "主线B", sev: "一般", pri: "P2",
    title: "考勤组班次/打卡时段体验差",
    module: "考勤设置 / 考勤组",
    steps: "新建考勤组 → 配置班次",
    expected: "可视化、强校验",
    actual: "表格式填写复杂，历史有展示异常",
    code: "attendance/config/group/modules/shift-form.vue",
    last: "仍在（减弱）"
  },
  {
    id: "Q-016", type: "Q", stage: "体验", sev: "一般", pri: "P2",
    title: "非法详情路由无守卫",
    module: "路由",
    steps: "打开 /hrm/employee/detail 等无 id",
    expected: "提示并回列表",
    actual: "404/异常页",
    code: "router/routes/modules/hrm.ts",
    last: "新增"
  },
  {
    id: "Q-017", type: "Q", stage: "体验", sev: "一般", pri: "P2",
    title: "空结果提示不统一",
    module: "多列表",
    steps: "搜索不存在的数据",
    expected: "统一「暂无数据」",
    actual: "部分页 emptyHint 缺失",
    code: "各 index.vue",
    last: "仍在"
  },
  {
    id: "Q-018", type: "Q", stage: "体验", sev: "一般", pri: "P2",
    title: "用工主体按钮文案拆字",
    module: "组织 / 用工主体信息",
    steps: "查看按钮文案",
    expected: "新增/编辑/删除",
    actual: "「新 增/编 辑/删 除」空格拆字",
    code: "views/hrm/company/index.vue",
    last: "新增"
  },
  {
    id: "Q-019", type: "Q", stage: "体验", sev: "一般", pri: "P2",
    title: "component 目录与 URL 不一致",
    module: "薪资路由",
    steps: "对照 menu component 与 route",
    expected: "可发现、可维护",
    actual: "salary/config/attendance-rule 对应 /hrm/salary/attendance-rule",
    code: "hrm.ts + menu SQL",
    last: "仍在"
  },
  {
    id: "Q-020", type: "Q", stage: "主线A", sev: "一般", pri: "P2",
    title: "招聘沟通操作集偏多",
    module: "招聘沟通",
    steps: "打开会话页",
    expected: "按产品定义收敛",
    actual: "检测到删除/批量语义",
    code: "views/hrm/recruit/chat/index.vue",
    last: "新增"
  },
  {
    id: "Q-021", type: "Q", stage: "体验", sev: "建议", pri: "P2",
    title: "高频台账缺导出",
    module: "多模块",
    steps: "合同签订/证件/社保/薪资档案/发放记录",
    expected: "可导出对账",
    actual: "无导出按钮",
    code: "各 index.vue",
    last: "仍在"
  },
  {
    id: "I-001", type: "I", stage: "主线A", sev: "P0", pri: "P0",
    title: "入职编制原子化（条件更新/行锁）",
    module: "流程改进",
    steps: "关联 Q-001",
    expected: "并发仅一生效",
    actual: "同一事务 FOR UPDATE 或条件 UPDATE（对齐 :542/:566）",
    code: "HrmRecruitOfferServiceImpl",
    last: "建议 · M"
  },
  {
    id: "I-002", type: "I", stage: "主线A", sev: "P0", pri: "P0",
    title: "Offer 有效期语义统一（长期有效）",
    module: "流程改进",
    steps: "关联 Q-002",
    expected: "长期可确认；过期拦截",
    actual: "null=长期贯穿 send/confirm；DRAFT 免审批口径不改",
    code: "HrmRecruitOfferServiceImpl",
    last: "建议 · S"
  },
  {
    id: "I-003", type: "I", stage: "权限", sev: "P0", pri: "P0",
    title: "门户写接口显式鉴权与代理任务归属过滤",
    module: "流程改进",
    steps: "关联 Q-005/Q-006",
    expected: "越权 403/404",
    actual: "PreAuthorize + 归属校验 + assignee 过滤",
    code: "HrmPortal* / AgentTask",
    last: "建议 · M"
  },
  {
    id: "I-004", type: "I", stage: "主线B", sev: "P0", pri: "P0",
    title: "工资月表同年月唯一索引",
    module: "流程改进",
    steps: "关联 Q-012",
    expected: "重复创建失败",
    actual: "DDL 唯一键 + 冲突文案",
    code: "hrm_salary_month_record",
    last: "建议 · S"
  },
  {
    id: "I-005", type: "I", stage: "权限", sev: "P0", pri: "P0",
    title: "关闭 actuator/druid 公网暴露",
    module: "流程改进",
    steps: "关联 Q-008",
    expected: "匿名 401/403",
    actual: "网关+服务双侧收紧",
    code: "SecurityConfiguration",
    last: "建议 · S"
  },
  {
    id: "I-006", type: "I", stage: "主线B", sev: "P1", pri: "P1",
    title: "管理端请假代提/补录",
    module: "流程改进",
    steps: "关联 Q-004",
    expected: "HR ≤3 步提交代提假",
    actual: "列表头「代提请假」共用 service",
    code: "attendance/leave",
    last: "建议 · M"
  },
  {
    id: "I-007", type: "I", stage: "主线A", sev: "P1", pri: "P1",
    title: "职位草稿态",
    module: "流程改进",
    steps: "关联 Q-003",
    expected: "草稿不可见于门户",
    actual: "DRAFT + 保存并发布",
    code: "recruit/post",
    last: "建议 · M"
  },
  {
    id: "I-008", type: "I", stage: "主线B", sev: "P1", pri: "P1",
    title: "核算就绪阻断 + 一键入组/建档",
    module: "流程改进",
    steps: "关联 Q-013",
    expected: "确认前无 COMPUTED",
    actual: "清单阻断 + 批量补齐",
    code: "salary/month-record",
    last: "建议 · M"
  },
  {
    id: "I-009", type: "I", stage: "主线A", sev: "P1", pri: "P1",
    title: "需求状态矩阵与审批幂等",
    module: "流程改进",
    steps: "关联 Q-009",
    expected: "非法迁移拒绝",
    actual: "状态表 + 幂等键",
    code: "recruit/demand",
    last: "建议 · M"
  },
  {
    id: "I-010", type: "I", stage: "主线B", sev: "P1", pri: "P1",
    title: "异动单入口与离职交接互斥",
    module: "流程改进",
    steps: "关联 Q-010",
    expected: "未交接不可离结",
    actual: "统一 apply + 门禁",
    code: "HrmEmployeeServiceImpl",
    last: "建议 · M"
  },
  {
    id: "I-011", type: "I", stage: "主线B", sev: "P1", pri: "P1",
    title: "合同到期自动 EXPIRED",
    module: "流程改进",
    steps: "关联 Q-011",
    expected: "到期日状态自动变更",
    actual: "Job 置位 + 续签待办",
    code: "contract job",
    last: "建议 · S"
  },
  {
    id: "I-012", type: "I", stage: "权限", sev: "P1", pri: "P1",
    title: "评估薪资/社保是否纳入部门数据权限",
    module: "流程改进",
    steps: "关联 Q-007",
    expected: "产品决策后实现隔离或文档声明",
    actual: "当前刻意排除；若合规要求隔离则扩展规则",
    code: "HrmDataPermissionConfiguration",
    last: "建议 · L"
  },
  {
    id: "I-013", type: "I", stage: "体验", sev: "P1", pri: "P1",
    title: "高频台账补导出",
    module: "体验优化",
    steps: "关联 Q-021",
    expected: "筛选条件导出 xlsx",
    actual: "统一导出组件",
    code: "多 index.vue",
    last: "建议 · M"
  },
  {
    id: "I-014", type: "I", stage: "体验", sev: "P2", pri: "P2",
    title: "KPI 角标与列表同源",
    module: "体验优化",
    steps: "关联 Q-014",
    expected: "角标=列表 total",
    actual: "同一 API 参数",
    code: "performance/plan",
    last: "建议 · S"
  },
  {
    id: "I-015", type: "I", stage: "体验", sev: "P2", pri: "P2",
    title: "考勤班次时间轴编辑",
    module: "体验优化",
    steps: "关联 Q-015",
    expected: "重叠即时拦截",
    actual: "双滑块/时间轴",
    code: "shift-form.vue",
    last: "建议 · M"
  },
  {
    id: "I-016", type: "I", stage: "体验", sev: "P2", pri: "P2",
    title: "详情路由缺参守卫",
    module: "体验优化",
    steps: "关联 Q-016",
    expected: "toast + 回列表",
    actual: "beforeEnter",
    code: "hrm.ts",
    last: "建议 · S"
  },
  {
    id: "I-017", type: "I", stage: "体验", sev: "P2", pri: "P2",
    title: "统一空态与按钮文案",
    module: "体验优化",
    steps: "关联 Q-017/Q-018",
    expected: "暂无数据 + 连续词按钮",
    actual: "Empty 组件 + i18n",
    code: "前端公共",
    last: "建议 · S"
  },
  {
    id: "I-018", type: "I", stage: "体验", sev: "P2", pri: "P2",
    title: "招聘沟通操作收敛",
    module: "体验优化",
    steps: "关联 Q-020",
    expected: "仅归档/本人删除",
    actual: "按权限显隐",
    code: "recruit/chat",
    last: "建议 · S"
  },
  {
    id: "I-019", type: "I", stage: "体验", sev: "P2", pri: "P2",
    title: "路由目录与 component 对齐",
    module: "体验优化",
    steps: "关联 Q-019",
    expected: "5 分钟可定位页面",
    actual: "重命名目录或注释对照表",
    code: "hrm.ts",
    last: "建议 · S"
  },
  {
    id: "I-020", type: "I", stage: "衔接", sev: "P1", pri: "P1",
    title: "入职向导（薪资/考勤/社保/账号一步配齐）",
    module: "新增功能",
    steps: "Offer 确认后",
    expected: "≤3 步完成上岗配置",
    actual: "向导页 + 就绪检查通过",
    code: "employee onboard",
    last: "建议 · L"
  },
  {
    id: "I-021", type: "I", stage: "衔接", sev: "P1", pri: "P1",
    title: "跨模块待办中心",
    module: "新增功能",
    steps: "HR 工作台",
    expected: "招聘/请假/异动/绩效一条队",
    actual: "聚合并跳转",
    code: "home",
    last: "建议 · L"
  },
  {
    id: "I-022", type: "I", stage: "体验", sev: "P2", pri: "P2",
    title: "员工自助「我要」磁贴",
    module: "新增功能",
    steps: "portal/home",
    expected: "≤2 次点击到表单",
    actual: "请假/证明/转正/联系方式",
    code: "portal/home",
    last: "建议 · M"
  },
  {
    id: "I-023", type: "I", stage: "主线B", sev: "P2", pri: "P2",
    title: "工资环比差异对比",
    module: "新增功能",
    steps: "工资表详情",
    expected: "高亮增减项",
    actual: "环比面板 + 导出",
    code: "salary/month-record/detail",
    last: "建议 · M"
  },
  {
    id: "I-024", type: "I", stage: "主线B", sev: "P2", pri: "P2",
    title: "合同/证件/试用期到期日历",
    module: "新增功能",
    steps: "HR 工作台",
    expected: "月历点击直达",
    actual: "三色节点",
    code: "home",
    last: "建议 · M"
  },
  {
    id: "I-025", type: "I", stage: "衔接", sev: "P2", pri: "P2",
    title: "沙箱演示数据与清场",
    module: "新增功能",
    steps: "实施/培训",
    expected: "10 分钟跑通主线 B",
    actual: "剧本生成 + is_sandbox",
    code: "tooling",
    last: "建议 · L"
  }
];

const listEl = document.getElementById("list");
const fType = document.getElementById("f-type");
const fSev = document.getElementById("f-sev");
const fStage = document.getElementById("f-stage");
const fKw = document.getElementById("f-kw");

function sevOf(it) {
  if (it.type === "I") return it.pri;
  return it.sev;
}

function render() {
  const t = fType.value;
  const s = fSev.value;
  const st = fStage.value;
  const kw = (fKw.value || "").trim().toLowerCase();

  const rows = items.filter((it) => {
    if (t && it.type !== t) return false;
    if (s) {
      const sv = sevOf(it);
      const map = { 致命: ["致命", "P0"], 严重: ["严重", "P1"], 一般: ["一般", "P2"], 建议: ["建议"] };
      const allow = map[s] || [];
      if (!allow.includes(sv) && !(s === "致命" && sv === "P0") && !(s === "严重" && sv === "P1") && !(s === "一般" && sv === "P2")) {
        if (!(s === "致命" && it.pri === "P0" && it.type === "I") && !(s === "严重" && it.pri === "P1" && it.type === "I") && !(s === "一般" && it.pri === "P2" && it.type === "I")) {
          return false;
        }
      }
    }
    if (st) {
      const stageMap = {
        主线A: ["主线A"],
        主线B: ["主线B", "衔接"],
        权限: ["权限"],
        体验: ["体验"],
        衔接: ["衔接"]
      };
      const allowSt = stageMap[st] || [st];
      if (!allowSt.includes(it.stage)) return false;
    }
    if (kw) {
      const blob = [it.id, it.title, it.module, it.actual, it.code, it.steps].join(" ").toLowerCase();
      if (!blob.includes(kw)) return false;
    }
    return true;
  });

  document.getElementById("n-all").textContent = items.length;
  document.getElementById("n-fatal").textContent = items.filter((x) => x.type === "Q" && x.sev === "致命").length;
  document.getElementById("n-serious").textContent = items.filter((x) => x.type === "Q" && x.sev === "严重").length;
  document.getElementById("n-normal").textContent = items.filter((x) => x.type === "Q" && x.sev === "一般").length;
  document.getElementById("n-advice").textContent = items.filter((x) => x.type === "I" || x.sev === "建议").length;

  if (!rows.length) {
    listEl.innerHTML = '<div class="empty">无匹配记录</div>';
    return;
  }

  listEl.innerHTML = rows
    .map((it) => {
      const badge = sevOf(it);
      return `<article class="item">
        <header>
          <span class="id">${it.id}</span>
          <span class="badge ${badge}">${badge}</span>
          <span class="badge">${it.type === "Q" ? "问题" : "建议"}</span>
          <span class="badge">${it.stage}</span>
          <h3>${it.title}</h3>
        </header>
        <dl>
          <dt>模块</dt><dd>${it.module}</dd>
          <dt>步骤/关联</dt><dd>${it.steps}</dd>
          <dt>预期</dt><dd>${it.expected}</dd>
          <dt>实际/改法</dt><dd>${it.actual}</dd>
          <dt>代码定位</dt><dd><code>${it.code}</code></dd>
          <dt>上轮/工作量</dt><dd>${it.last}</dd>
        </dl>
      </article>`;
    })
    .join("");
}

[fType, fSev, fStage].forEach((el) => el.addEventListener("change", render));
fKw.addEventListener("input", render);
document.getElementById("f-reset").addEventListener("click", () => {
  fType.value = "";
  fSev.value = "";
  fStage.value = "";
  fKw.value = "";
  render();
});
render();
