-- =====================================================================
-- cert_file_versions 加作废列（F2：文件版本作废/更正元数据，《后端变更通知-给前端.md》§8）
-- 背景：证照域没有撤销/作废接口，文件版本只追加 ⇒ 用户想改任何东西只能"造一份内容不同的文件"再传；
--   判重（uk_cert_file_hash）又按内容指纹（改文件名无效）⇒ 实际痛点：撤销后不能重复上传同一份文件。
--   F2 方案：作废=软删留痕（行保留、标 voided/void_reason/voided_at），作废当前版本后该类别失去
--   当前版本，用户重新登记新版本；另加"更正元数据"（备注 + 加注件 copy_* 四要素）。
-- 执行库：**业务库**（cert_file_versions 所在库）。幂等：MySQL 8.0.29+ 支持 ADD COLUMN IF NOT EXISTS，
--   重复执行不报错（老版本 MySQL 重复执行会报"列已存在"，属预期——本脚本只对未升级库执行一次）。
-- =====================================================================

ALTER TABLE `cert_file_versions`
    ADD COLUMN IF NOT EXISTS `voided` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否作废（F2 软删留痕：作废=版本不再有效但行保留；当前版本被作废则该类别失去当前版本）' AFTER `remark`,
    ADD COLUMN IF NOT EXISTS `void_reason` varchar(512) DEFAULT NULL COMMENT '作废原因（留痕展示用）' AFTER `voided`,
    ADD COLUMN IF NOT EXISTS `voided_at` datetime DEFAULT NULL COMMENT '作废时间（留痕用；操作人见 updater）' AFTER `void_reason`,
    ADD KEY IF NOT EXISTS `idx_cert_voided` (`cert_id`, `voided`, `deleted`);
