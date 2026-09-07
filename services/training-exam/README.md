# 考试与认证

企业后台入口 `/training/exam`；个人门户入口「云培训学院 → 考试与证书」。个人中心也可查看已取得的考试证书。后台与个人门户复用同一考试服务，使用当前平台账号，不设置演示角色切换器。

## 已实现

- 五类题目（单选、多选、判断、实操问答、案例分析）的新增、编辑、停用、提交审核、通过、驳回及审核记录。编辑后回到草稿，已审核题目才能组卷。
- 手动选题与分值设置；按题型数量、分值、工种及安全工艺标签随机抽题；考试类型、时长、及格分、重考次数、切屏记录、证书参数。
- 选择平台真实企业人员，或维护本模块培训班后按班级排考。发布后冻结题目快照。
- 学员任务、答题保存与刷新恢复、倒计时、服务端截止时间校验、后台定时交卷、客观题评分、主观题人工阅卷、成绩查询。
- 按课程和题型过滤个人错题，显示答案解析并标记已掌握。
- 成绩合格后幂等签发电子证书，个人档案查询、在线预览、中文 PDF 和 PNG 图片下载。
- 超级管理员配置合作机构；联合认证生成待同步记录，支持实际接口连接检查、发送、失败记录与重试，不生成模拟成功回执。

## 身份与数据

`Authorization: Bearer ...` 与 `tenant-id` 由父页面通过同源消息传入，令牌仅保存在 iframe 内存，不写入 URL。每次请求向平台 `/system/auth/get-permission-info` 验证身份，平台过滤器核验令牌与企业是否匹配。服务不信任 `X-User-Id` 或 `X-Role`。

每个企业使用独立 SQLite 数据库，存于 Docker 卷 `deploy_training-exam-data`。单进程内按企业串行处理请求与截止时间任务，避免答卷重复提交、重复发证等竞争。此部署用于当前本地系统；多副本部署前需迁移到共享数据库和分布式任务锁。

权限：平台超级管理员、或同时拥有考试入口权限的 `tenant_admin` 可管理；也可授予 `training:exam:manage`。讲师使用 `training:exam:author`，审核员使用 `training:exam:review`，`training:exam:query` 为只读入口。机构配置仅允许平台超级管理员。权限菜单脚本为 `sql/training_exam_permissions.sql`，不自动给普通角色提权。

## 部署

1. 在仓库根目录运行 `docker compose -f deploy/module5/docker-compose.yml up -d --build`。默认使用标准 Python 镜像构建，便于从 Gitee 克隆后直接部署；`Dockerfile.local` 仅用于已有本地基础镜像的离线加速。
2. 在 `txgy-ui-admin-vben` 安装锁定依赖，运行 `pnpm build:antd`。本地部署配置需指定 `VITE_GLOB_API_URL=/admin-api`、`VITE_APP_TENANT_ENABLE=true`。本机 `.env.production.local` 保持原先关闭验证码的本地配置。
3. 使用 `deploy/module6/Dockerfile.frontend` 将 `apps/web-antd/dist` 构建到统一前端镜像。Nginx 把 `/training-exam/` 代理至内部 `training-exam:8000`，考试服务不单独开放公网端口。
4. 若使用开发服务，请放在 5174 等空闲端口；Vite 会把 `/training-exam` 请求代理至当前 5173 的 Nginx。

## 验证

`tests/test_workflows.py` 使用隔离临时数据库验证审核、组卷、排考、答题、阅卷、错题、证书归属、租户隔离与过期答案拒绝。

`tests/browser_server.py` 仅用于隔离浏览器验收，通过依赖替换提供测试身份，不包含在镜像中，不可用于正式服务。`tests/browser-workflow.cjs` 完成从新建试题到证书预览的实际页面操作。运行它需要提供 Playwright 模块路径与截图目录环境变量。

## 外部联合认证边界

合作机构必须提供真实 HTTPS 发证接口、密钥和协议。当前适配请求为 POST JSON，包含证书编号、用户 ID、成绩和幂等键；成功响应须包含 `ext_cert_no`。接口协议不同则需增加适配器。同步任务由管理员确认后发送。机构资质、证书全国适用范围由合作协议及真实回执决定，平台合格证明不替代外部认证。
