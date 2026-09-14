# 商品物料类型保存修复

- 原因：商品表单使用未注册的字符串校验规则 `nullable`，真实 vee-validate 校验抛出 `No such validator 'nullable' exists.`，确认操作在请求发出前中断。
- 修复：删除该无效规则，沿用原有可选字段行为；商品展开 SKU 列表时带入主数据 materialType，并按商品合并类型列，避免有值仍显示“未设置”。
- 范围：仅 WMS 商品前端、回归测试及本文档；未改后端、历史物料类型、库存或移库，未提交推送。

## 验证

从 `txgy-ui-admin-vben` 执行 `D:/App/nodejs/node.exe --test tests/wms-item-material-type.test.mjs`。先复现 4 项校验失败，修复后通过；追加列表测试先失败后通过。最终 5 项通过、0 失败。

执行 `pnpm.cmd run build:antd` 成功，11/11 构建任务完成（10 缓存）。日志 `txgy-ui-admin-vben/wms-material-type-save-fix-build.log`。通过 `docker cp txgy-ui-admin-vben/apps/web-antd/dist/. wms-acceptance-frontend:/usr/share/nginx/html/` 更新专用本机容器静态资源，未重建镜像或刷新用户表单。

独立浏览器在租户 2005、宋凯账号下验证：商品 `WMS4-FG-0912` 的编辑表单类型为成品；保存验证备注后窗口关闭，重新加载并打开编辑，备注仍为“仅本机四业务验收，不用于生产物料类型保存回归验证 20260912”，类型仍为成品。最终列表显示该商品为成品、`WMS4-RAW-0912` 为原材料。测试未改变类型或执行库存。

首次测试备注保存被自动审批拒绝且未执行；用户明确授权保存该专用测试商品备注后执行成功。没有修改用户截图中的岩棉毡。

## 自测

当前旧页面仍含旧脚本。先记下未保存内容，自行关闭编辑窗口并刷新商品管理，再打开商品，选择类型后确认；保存后从列表及重新打开的编辑表单核对。入口：`http://localhost:5173/#/wms/md/item`。
