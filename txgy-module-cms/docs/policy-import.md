# 本地法规与国标导入

本批次依据用户提供的《政策咨询与绿色创新(法律法规国标)》清单准备，金融知识库不在范围内。
法规原文来自政府官方网站，4份国标PDF来自Word内嵌附件。

## 数据位置与边界

- 本地原文、校验结果与导入日志：`data/cms-import/policy-20260907/`，已加入Git忽略。
- `laws/import-plan.json`：计划；`laws/import-result.json`：成功写入并逐条回读后才生成。
- `standard-review-manifest.json`：国标原始PDF、SHA-256、页码、空白/图像页及审核状态。
- 当前只写入已有Elasticsearch别名，不写MySQL，不生成向量，不代表RAG模型已接通。
- 本次租户使用门户开发默认值2005。其他租户需要显式指定，不能依靠前端传参越权读取。
- 6部环保旧法按生态环境法典第1242条标记REPEALED，有效期截至2026-08-14；补充2026年法典作为现行依据。
- 尚未核验当前完整效力沿革的其他来源保留UNKNOWN，不根据模型推测标记EFFECTIVE。
- 国标页级提取仅作未发布草稿，尤其扫描表格、公式和GB/T 8923.1的图片必须校对。草稿被现有查询接口的published过滤排除。
- 法规查询默认未排除历史版本；现行检索应结合日期与状态，返回结果须保留效力信息。

## 可重复导入

以下命令中的`python`应替换为本机配置的Python路径，依赖为lxml、pypdf。

```powershell
python tools/prepare_cms_laws.py --output data/cms-import/policy-20260907/laws
python tools/import_cms_laws.py --directory data/cms-import/policy-20260907/laws --tenant 2005
python tools/import_cms_laws.py --directory data/cms-import/policy-20260907/laws --tenant 2005 --apply
python tools/prepare_cms_standards.py --directory data/cms-import/policy-20260907 --tenant 2005 --apply
python -m unittest discover -s tools -p test_cms_import.py
```

原始数据配置位于`tools/cms_policy_sources.json`和`tools/cms_standard_sources.json`。
法规检查条号连续性；福建住建厅转载建筑法页面将第68条粘在上一段后，提取时只恢复换行，不改写法律正文。
政策正文保留附件表格的行内容，按连续原文分块。此切分是首批关键词检索粒度，后续RAG可进一步按层级优化。

导入采用租户隔离的稳定ID和create-only写入，已有相同数据跳过，已有不同数据报错，绝不覆盖。
出现部分批次失败时可安全重跑；法律写入计划ID保存在`laws/import-pending-ids.json`。
索引是检索投影，本地原文和JSON需随业务数据一起备份；后续接入业务数据库后再统一管理发布与版本。

正式名称按PDF封面保留：GB/T 39636-2020为“钢质管道熔结环氧粉末外涂层技术规范”；GB/T 8923.1-2011为表面处理通用标准，并非3PE专用。
