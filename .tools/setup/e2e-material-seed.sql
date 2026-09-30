-- 2026-09-17 联调：为「一键生成」登记一条已入库的招标文件材料（TENDER + index_status=1）。
-- index_status=1 表示知识库已入库，是 app-api /task/generate 的前置条件（否则报 1_060_121_094）。
SET NAMES utf8mb4;

DELETE FROM bidding_bid_material WHERE id = 900000000000000003;

INSERT INTO bidding_bid_material
    (id, doc_type, name, bid_section_code, file_id, file_url, file_name, file_size,
     index_status, segment_count, index_message, remark, tenant_id, creator, create_time, update_time, deleted)
VALUES
    (900000000000000003, 'TENDER', '炼化公司2026年防腐保温项目施工招标技术文件.docx', 'SEC-001',
     900000000000000004, 'http://127.0.0.1:8300/tender.docx', 'tender.docx', 28141,
     1, 11, '端到端联调：已切片并向量化', '联调材料', 1, '1', NOW(), NOW(), b'0');
