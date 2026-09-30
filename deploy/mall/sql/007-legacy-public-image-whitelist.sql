-- =============================================================================
-- 007 历史公开图片白名单补录（③ 安全修复配套）
--
-- 背景：
--   ③ 采用「默认私有 + 服务端记录确认」姿态后，未命中公开白名单的文件默认拒绝读取。
--   经全量核实（script/scan-public-file-refs.py 扫描商城对外展示列 + 关联 infra_file /
--   infra_file_content），发现 1 个「确属公开用途、内容仍在、但记录为私有」的历史资源：
--     trade_delivery_express.logo → 20260922/PixPin_2026-09-22_23-05-33.png
--   该资源用于下单页展示快递公司 logo，属公开资产，需补录为 PUBLIC，否则会 401。
--
-- 处置：仅按精确 URL 将该记录置为 公开(access_type=1) + 已确认公开(bind_status=2)。
--   幂等：重复执行无副作用（已是公开则不改动语义）。
--
-- 说明：其余「公开面引用但内容已不存在」的资源（43 个）在本次变更前即为 404（内容已被删除），
--   不属于本次回归；如需恢复需另行找回源文件，不在本脚本范围内。
-- =============================================================================

UPDATE infra_file
SET access_type      = 1,                     -- PUBLIC
    bind_status      = 2,                     -- PUBLIC（服务端已确认公开）
    biz_type         = 'DELIVERY_LOGO',       -- 快递公司 logo（服务端白名单业务类型，允许公开）
    biz_id           = 'trade_delivery_express:10',
    bind_participants = NULL,
    bind_expire_time = NULL,
    update_time      = NOW()
WHERE url = 'http://127.0.0.1:48080/admin-api/infra/file/4/get/20260922/PixPin_2026-09-22_23-05-33.png'
  AND deleted = 0
  AND (access_type <> 1 OR bind_status <> 2);

-- 复核：应返回 1 行且 access_type=1 bind_status=2
-- SELECT id, access_type, bind_status, biz_type, url FROM infra_file
--  WHERE url='http://127.0.0.1:48080/admin-api/infra/file/4/get/20260922/PixPin_2026-09-22_23-05-33.png';
