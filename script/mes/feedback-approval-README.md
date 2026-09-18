# 报工审批流程模型初始化

一键创建并发布 MES 报工审批的 BPM 流程模型（流程 Key：`mes-feedback-approval`），
把「在管理端手工建模」变成一条可重复执行的命令。

配套说明书：`docs/mes-feedback-bpm-model-config-20260914.md`
（流程变量清单、三条硬约束、联调验收步骤都在里面）

---

## 一、为什么需要它

MES 侧链路（发起 / 回写 / 异步消费 / 前端入口）已全部完成并部署，唯一卡点是 BPM 引擎里
**没有任何流程定义**（实测 `ACT_RE_PROCDEF = 0`），提交报工时会在「发起流程实例」这一步直接失败。

手工在建模器里画图有三个容易踩的点，本脚本已把它们固化：

| 约束 | 脚本中的体现 | 配错的后果 |
| --- | --- | --- |
| 流程 Key 必须为 `mes-feedback-approval` | BPMN 与脚本内硬编码并校验 | Key 不一致 → 发起失败，或回写丢失 |
| 驳回必须**结束**流程 | BPMN 内 `flowable:rejectHandlerType=1` | 实例不结束 → bpm 不发通知 → 报工单永久停在「审批中」 |
| 关闭「撤销 / 取消流程」 | `allowCancelRunningProcess = false` | 取消后最终状态为 4(CANCEL)，MES 只消费 2/3 → 同样卡住 |

另：模型 `visible = false`，流程只能由 MES 在提交报工时发起。
若设为用户可发起，有人手工发起一个「没有报工单」的实例时，回调将无单可更。

---

## 二、执行前提

1. 需要一个**租户管理员**的 access token（权限 `bpm:model:create` / `update` / `deploy`）。
   取 token 示例（`BaseUrl` 为网关根地址，不带 `/admin-api`）：

```powershell
$h = @{ 'tenant-id' = '1' }
$body = '{"username":"admin","password":"<密码>","tenantId":"1"}'
$r = Invoke-RestMethod -Method Post -Uri http://localhost:48080/admin-api/system/auth/login `
     -Headers $h -ContentType 'application/json' -Body $body
$token = $r.data.accessToken
```

> 注意：`tenant-id` 必须放在**请求头**里。只写在 body 中会返回 400「请求的租户标识未传递」。

2. `-ApproverUserId` 必须是该租户下**真实且启用**的用户；`-ManagerUserId` 需包含执行发布操作的登录用户。

---

## 三、执行

```powershell
powershell -ExecutionPolicy Bypass -File script\mes\Initialize-FeedbackApproval.ps1 `
    -BaseUrl http://localhost:48080 -Token $token -TenantId 1 `
    -ManagerUserId 1 -ApproverUserId 1
```

- 模型已存在时，脚本**默认不改动**，只打印提示；确认要覆盖重发时显式加 `-UpdateExisting`：

```powershell
... -ApproverUserId 7 -UpdateExisting
```

- `-Category` 默认 `MAIN_01`（主审批工作），可换成任意已有分类编码。

---

## 四、自定义审批人策略

一期用 `candidateStrategy = 30`（指定单个用户），这是**最快跑通联调**的方式。

生产环境建议改为**按角色**（`candidateStrategy = 10`，说明书第 3.3 节），
参考已跑通的模板 `script/wms/warehouse-role-approval.bpmn`：
把 `flowable:candidateStrategy` 改为 `10`、占位符换成角色 ID，并补上 multiInstance 或签块
（`<multiInstanceLoopCharacteristics ... flowable:collection="${coll_userList}">`）。

> 该项对应说明书待确认问题 2 / 8，属于需要 BPM 侧先决策的事项，决策后再用 `-UpdateExisting` 覆盖。

---

## 五、验收

| 步骤 | 预期结果 |
| --- | --- |
| 1 | 脚本输出 `Deployed mes-feedback-approval: modelId=...` |
| 2 | 查库 `ACT_RE_PROCDEF` 出现 `mes-feedback-approval`（执行前为 0 条） |
| 3 | MES 新建报工单并提交 → 状态变「审批中」，BPM「待办任务」出现审批任务 |
| 4 | 审批人通过 → MES 回写（已完成 / 待检验），物料扣减与产出单正确 |
| 5 | 再提交一条并驳回 → MES 退回草稿；驳回原因可在 BPM 流程详情查看 |

完整验收步骤（含重复回调等边界）见 `docs/mes-feedback-bpm-model-config-20260914.md` 第七节。

---

## 六、注意事项

- **不要**给该模型再增加第二份 HTTP 后置回调：bpm-server 的 `MesFeedbackStatusListener`
  已负责结果同步，重复配置会让 MES 收到两次回调。
- `Initialize-FeedbackApproval.ps1` 为 **UTF-8 with BOM**（`script/wms/` 下的同类脚本是无 BOM 的）。
  原因：PS 5.1 会把无 BOM 的 UTF-8 当 ANSI 解码，导致中文流程名「生产报工审批」乱码。编辑时请保留 BOM。
- 本目录只做「建模 + 发布」，不涉及业务数据；若需回退，在 BPM 管理端的流程模型列表删除该模型即可。
