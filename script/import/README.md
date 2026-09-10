# CMS 内容批量导入工具集

> 二期 P0-2 交付物：企业提供的 Word/PDF/Excel 政策文件批量导入 cms_policy 的脚本集合。
> 依赖：`pip install python-docx pypdfium2 requests`
> 运行前提：Docker 容器 `txgy-mysql-1` 在运行（脚本通过 `docker exec` 写库）。

## 脚本清单

| 脚本 | 用途 | 典型场景 |
|---|---|---|
| `fetch_law.py` | 抓取单个网页（法律/政策全文页）并提取正文 | 单个官方全文页面导入 |
| `fetch_all_laws.py` | 批量抓取法律清单（URL 列表内置，可编辑） | 批量导入国家法律条文 |
| `fetch_carbon.py` | 下载页面附件 PDF → pypdfium2 提取全文 → 生成 UPDATE | 方案类文件以附件形式挂在官网的场景 |
| `fetch_industry.py` | 抓取产业政策全文页 → 按锚点提取 → 生成 UPDATE | 产业政策/方案类文件 |
| `extract_std_pdfs.py` | **从 Word 嵌入对象中提取 PDF**（oleObject 解析），识别标准号并托管 | 企业把标准 PDF 嵌在 Word 里发来的场景 |
| `import_law_text.py` | 将已提取的法律全文 txt 批量 UPDATE 到 cms_policy | 全文文本已就绪的批量入库 |

## 通用流程

```
企业文件（PDF/Word/Excel/网页）
    ↓ 对应脚本抓取/提取正文
生成 UPDATE/INSERT SQL（utf-8）
    ↓ docker cp + mysql 执行
cms_policy 入库（content_type=法律法规/金融政策，audit_status=1，publish_status=1）
```

## 注意事项

1. **字段约定**：`content_type` 必填（法律法规/金融政策）；`summary` 列表摘要；`source_url` 官方原文链接；扫描件 PDF 无法提取文字，只挂原件路径（`attachment_urls`）。
2. **SQL 转义**：所有脚本已处理单引号/换行/反斜杠；新增脚本请复用 `esc()` 写法。
3. **执行后校验**：`SELECT content_type, COUNT(*) FROM cms_policy WHERE deleted=0 GROUP BY content_type;` 并抽查 10 条 content 无乱码。
4. **GB/T 标准全文是版权内容**：只托管企业合法持有的文件，不要从第三方网站抓取。
5. Windows 下建议把逻辑写成 `.py` 文件执行，避免 shell heredoc 反斜杠转义问题。
