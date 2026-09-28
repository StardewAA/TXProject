-- 与 deploy/mysql/repair/2026-09-25-hrm-offer-mail-accept-reject.sql 内容一致
-- HRM：录用通知（Offer）邮件模板增加「接受 / 拒绝」按钮（问题 9）
--
-- 说明：邮件里的两个按钮指向招聘平台（门户）的应聘记录/沟通页，候选人点开后二次确认即调用
--       /hrm/portal/recruit/offer/accept | reject，后台状态直接更新为「已确认 / 候选人拒绝」。
--       链接基地址取系统参数 hrm.portal.base-url（系统管理 → 参数设置，默认 http://localhost:5173）。

-- 0. 邮件日志表 template_params 只有 varchar(255)：新增 acceptUrl/rejectUrl 后，参数 JSON 会超过长度，
--    导致「发送时插入日志报 Data too long」→ 邮件整体发送失败（表现为 Offer 通知发不出去）。
--    这里放宽到 varchar(1024)（值仍很短，只是容纳两个跳转链接）。
ALTER TABLE `system_mail_log`
    MODIFY COLUMN `template_params` varchar(1024) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
        NOT NULL COMMENT '邮件参数';

UPDATE `system_mail_template`
SET `content` = '<p>{candidateName} 您好：</p>
<p>经面试评估，{companyName} 决定录用您。</p>
<p>录用岗位：{postName}<br/>计划入职日期：{plannedOnboardDate}<br/>薪酬标准：{salaryAmount} 元/月<br/>Offer 有效期至：{validUntil}</p>
<p>详细录用条件、入职需携带材料（身份证原件、特种作业操作证及岗位资格证书、体检报告、银行卡、免冠照片）与相关说明，请查看邮件附件《录用通知书》PDF。</p>
<p style="margin:18px 0;">
  <a href="{acceptUrl}" style="display:inline-block;padding:10px 22px;margin-right:12px;background:#1677ff;color:#ffffff;border-radius:6px;text-decoration:none;font-weight:600;">同意录用</a>
  <a href="{rejectUrl}" style="display:inline-block;padding:10px 22px;background:#ffffff;color:#cf1322;border:1px solid #cf1322;border-radius:6px;text-decoration:none;font-weight:600;">拒绝录用</a>
</p>
<p style="color:#8c8c8c;font-size:13px;">点击按钮会跳转到招聘平台，登录后确认一次即可完成（同意后进入待入职，拒绝后本次应聘流程结束）。如在有效期内未操作，Offer 将自动失效。</p>
<p>{companyName}</p>',
    -- 注意：params 是 JSON 列，必须是 JSON 数组（写成逗号串会导致系统模块解析失败、邮件整体发不出去）
    `params` = '["candidateName","companyName","postName","plannedOnboardDate","salaryAmount","validUntil","acceptUrl","rejectUrl"]',
    `update_time` = NOW()
WHERE `code` = 'hrm_offer_notice' AND `deleted` = b'0';

-- 校验：应看到 params 里包含 acceptUrl,rejectUrl
-- SELECT code, params FROM system_mail_template WHERE code = 'hrm_offer_notice';
