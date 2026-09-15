# 数据恢复工具（2026-09-10 事故复盘产物）

## 事故
2026-09-09 18:46 / 19:08，`sql/master-bidding-sync.sql` 的**早期版本**含 mysqldump 自带的 `DROP TABLE IF EXISTS`（16 张表），
在"幂等试跑"时把 bidding 16 表数据全部清空重建（CREATE_TIME 铁证），700 条外部公告等业务数据丢失。
- 不是 SQL 文件丢失：包与 history 脚本均在；丢的是**数据库数据**。
- 未被波及：ES 索引（700 文档）、infra 文件归档（raw-html/pdf blob）、system 菜单/角色、AI 配置。
- 已修复：包内 16 条 DROP 全部移除（现仅 IF NOT EXISTS / INSERT IGNORE）；README 已标注历史教训。

## 恢复步骤（本次实际执行）
1. `node sql/tools/restore-external-from-es.js | mysql … ruoyi-vue-pro`
   —— 从 ES 索引 700 文档重建 bidding_notice_external 主档（id/标题/来源/原网址/时间/项目编号/行业标签；orig_url_hash 重算保持去重键一致）。
2. 归档回填（SQL）：
   ```sql
   UPDATE bidding_notice_external e JOIN infra_file_content f
     ON f.path LIKE 'bidding/notice-external/raw-html/%'
    AND e.title = REPLACE(SUBSTRING_INDEX(f.path,'/',-1),'.html','')
   SET e.content_html = CONVERT(f.content USING utf8mb4)
   WHERE e.content_type='HTML' AND (e.content_html IS NULL OR e.content_html='');

   UPDATE bidding_notice_external e JOIN infra_file_content f
     ON f.path LIKE CONCAT('bidding/notice-external/%', e.title, '.pdf%')
   SET e.pdf_download_url = CONCAT('http://127.0.0.1:48080/admin-api/infra/file/4/get/', f.path),
       e.pdf_size = LENGTH(f.content), e.pdf_sha256 = SHA2(f.content, 256)
   WHERE e.content_type='PDF' AND e.pdf_download_url IS NULL;
   ```
3. 兜底正文：`node sql/tools/fallback-content-from-es.js | mysql …`（ES contentText → content_html，HTML 类空正文补齐）。

## 未恢复项（说明）
- fetch_task/job 历史痕迹、follow_item 关注 4 条、INACTIVE 公告 1 条（ES 仅索引 ACTIVE）——价值低，未重建，后续业务自然产生。
- subject_auth/profile 已由 master 包（19:08 重跑）补齐（tenant1 招标人发布门、2019 投标人）。

## 教训
凡"同步/重建"脚本必须与"破坏性"脚本分离：导出结构一律剥离 `DROP TABLE`（或显式标注为重建包并二次确认）。
