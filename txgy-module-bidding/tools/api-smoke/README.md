# api-smoke —— 电子资质域接口冒烟工具

> 依 tools/ 目录约定：先写"硬性条件/用法"文档，再实现。实现见同目录 `smoke-cert-api.js`。

## 1. 这个工具做什么

把《后端变更请求-资质证书五页改造缺接口-2026-09-18.md》（Q1~Q23）里**新增/改动的接口**，
在服务跑起来后**一条命令跑完**，逐项打印 PASS/FAIL。

覆盖点（与《后端变更通知-给前端.md》条目 16~28 对齐）：

| # | 校验 | 对应契约 |
| --- | --- | --- |
| 1 | `certificate/stats` 返回**六态全量计数**（含暂扣/吊销/注销） | Q3 |
| 2 | `certificate/import-template` 可下载（二进制 xls） | Q1 |
| 3 | `qualification/stats` 含 `unlinkedStaff` | Q5 |
| 4 | `qualification/page` 行上含 `staffCount` | Q8 |
| 5 | `qualification/export` 可导出 | Q6 |
| 6 | `personnel-certificate/stats` | Q10 |
| 7 | `personnel-certificate/page-by-person` | Q9 |
| 8 | `safety-permit/risk/page` 状态**全部落在五态值域**（PENDING/ASSIGNED/HANDLING/HANDLED/IGNORED） | Q18 |
| 9 | `safety-permit/risk/stats` 五项齐全 | Q19 |
| 10 | `safety-permit/risk/export` 可导出 | Q21 |
| 11 | `meta/dicts` 能下发 `risk_item_status`/`alert_level`/`alert_type`/`risk_rule_cond_type` | Q18/Q23 + 通知 27 条 |
| 12 | `cert-lifecycle/get` 出参含 `files[]`（需 `--lifecycle-id`） | Q16 |
| 13 | `risk/handle` 接受 `targetStatus`（前端已发布字段名；**默认关闭**，需 `--write`，同值提交=幂等不改状态） | Q18 + 通知 28 条 |

## 2. 硬性条件

1. **服务在跑**：本地联调端口 `48110`（`txgy-module-bidding-server/bidding-dev.cmd` 起），或走网关 `48080`；
2. **令牌**：需要一个可用的登录 JWT（`--token` 或 `SMOKE_TOKEN`）；
3. **租户头**：默认 `tenant-id: 1`（`--tenant` 可改）；
4. **权限**：令牌所属角色需具备对应页面/按钮权限，否则返回 403（那说明是授权问题，不是接口缺失——脚本会原样打印 code/msg）。

取令牌（本地联调）：

```bash
curl -s -X POST http://127.0.0.1:48110/admin-api/system/auth/login \
  -H "tenant-id: 1" -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}'
```

## 3. 用法

```bash
# 只读校验（默认）
node tools/api-smoke/smoke-cert-api.js --token <JWT>

# 指定端口/网关/租户/主体
node tools/api-smoke/smoke-cert-api.js --token <JWT> --base http://127.0.0.1:48080 --tenant 1 --ent 1

# 追加：生命周期附件（Q16）与 handle 别名（Q18，写但同值 no-op）
node tools/api-smoke/smoke-cert-api.js --token <JWT> --lifecycle-id 123 --write --risk-id 456

# 机器可读
SMOKE_TOKEN=<JWT> node tools/api-smoke/smoke-cert-api.js --json
```

退出码：`0` 全部通过 / `1` 有失败 / `2` 用法或令牌缺失。

## 4. 为什么独立于 `sql/tools/`

`sql/tools/*.js` 是**静态**自检（离线可跑、由 `check-all.js` 汇总、作为交付门禁）；
本脚本是**运行期**验收（依赖活服务与登录态），两者语义不同，故不并入门禁、也不放进 `sql/tools`。

## 5. 局限（诚实说明）

- **不替代人工验收**：脚本只断言"接口通、字段在、值域对"，不看页面观感与业务语义；
- **不覆盖导入类写接口**（`certificate/import`、`qualification/import`、`batch-renew` 等）：它们会改数据，
  需造样本文件与可回滚环境，仍走人工验收；
- **空表时部分断言退化**：如 Q8 在无数据时只能提示"造一条数据后重跑"；
- **字典项非空 ≠ 文案正确**：只校验"有下发"，中文标签仍需人工看一眼。
