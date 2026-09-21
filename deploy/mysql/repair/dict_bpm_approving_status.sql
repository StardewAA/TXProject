-- 字典补齐：出入库 4 类单据的"BPM审批中"状态项
-- 对应后端 MesOrderStatusConstants.BPM_APPROVING = 10（销售出库枚举内专属值 6，因 10 已被待填写运单占用）

INSERT INTO system_dict_data (sort, label, value, dict_type, status, color_type, creator) VALUES
    (5, 'BPM审批中', '10', 'mes_wm_item_receipt_status',    0, 'warning', '1'),
    (7, 'BPM审批中', '6',  'mes_wm_product_sales_status',   0, 'warning', '1'),
    (6, 'BPM审批中', '10', 'mes_wm_outsource_issue_status', 0, 'warning', '1'),
    (5, 'BPM审批中', '10', 'mes_wm_return_vendor_status',   0, 'warning', '1');
