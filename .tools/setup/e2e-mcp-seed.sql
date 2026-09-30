-- 2026-09-17 联调用：登记一个 stdio 型 MCP 服务，并挂到平台默认的 PARSE 智能体（profile_id=0，tenant_id=0），
-- 用于验证「管理页挂载的 MCP 服务 → Java 契约顶端下发 → Node 底座加载工具」这条链路。
-- 说明：env / headers 列是加密列，这里保持 NULL；args 必须是 JSON 字符串（与 bidding_bid_agent_mcp_server 列口径一致）。
SET NAMES utf8mb4;

DELETE FROM bidding_bid_agent_profile_mcp WHERE id = 900000000000000002;
DELETE FROM bidding_bid_agent_mcp_server WHERE id = 900000000000000001;

INSERT INTO bidding_bid_agent_mcp_server
    (id, name, transport, command, args, timeout_seconds, enabled, tenant_id, remark, create_time, update_time, deleted)
VALUES
    (900000000000000001, 'e2e-echo', 'stdio',
     'D:/招投标项目/.tools/node/node.exe',
     '["D:/天信管业防腐保温智慧平台/.tools/setup/mcp-e2e-check.mjs","--serve"]',
     60, 1, 1, '端到端联调用的最小 MCP 服务（echo_text）', NOW(), NOW(), b'0');

INSERT INTO bidding_bid_agent_profile_mcp
    (id, profile_id, mcp_server_id, tool_names, tenant_id, create_time, update_time, deleted)
VALUES
    (900000000000000002, 0, 900000000000000001, '["echo_text"]', 1, NOW(), NOW(), b'0');
