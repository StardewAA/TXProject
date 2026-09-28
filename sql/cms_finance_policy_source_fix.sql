-- 修复金融政策批量导入时把 /files/... 本地路径误写为官方来源的问题。
-- 未核实到政府网站的条目保持 NULL，本地 PDF 仍由 local_pdf_path 提供。
START TRANSACTION;

DROP TEMPORARY TABLE IF EXISTS tmp_finance_policy_official_source;
CREATE TEMPORARY TABLE tmp_finance_policy_official_source (
  filename VARCHAR(500) NOT NULL PRIMARY KEY,
  source_url VARCHAR(1000) NOT NULL
) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

INSERT INTO tmp_finance_policy_official_source (filename, source_url) VALUES
('财政部 税务总局关于进一步完善研发费用税前加计扣除政策的公告.pdf','https://fgk.chinatax.gov.cn/zcfgk/c102416/c5201978/content.html'),
('财政部 税务总局关于先进制造业企业增值税加计抵减政策的公告.pdf','https://fgk.chinatax.gov.cn/zcfgk/c102416/c5212095/content.html'),
('财政部等四部门关于公布《环境保护、节能节水项目企业所得税优惠目录（2021年版）》以及《资源综合利用企业所得税优惠目录（2021年版）》的公告.pdf','https://fgk.chinatax.gov.cn/zcfgk/c102416/c5202070/content.html'),
('工信部企业〔2022〕63号_优质中小企业梯度培育管理暂行办法.pdf','https://www.miit.gov.cn/jgsj/qyj/bszn/art/2022/art_4ea1979cf62e44b1b3461c3e2453721c.html'),
('工业和信息化部关于印发《优质中小企业梯度培育管理暂行办法》的通知.pdf','https://www.miit.gov.cn/jgsj/qyj/bszn/art/2022/art_4ea1979cf62e44b1b3461c3e2453721c.html'),
('关于进一步扩大政府采购支持绿色建材.pdf','https://app.www.gov.cn/govdata/gov/202501/12/523568/article.html'),
('关于延续西部大开发企业所得税政策的公告.pdf','https://fgk.chinatax.gov.cn/zcfgk/c102416/c5202218/content.html'),
('关于延长高新技术企业和科技型中小企业亏损结转年限的通知.pdf','https://fgk.chinatax.gov.cn/zcfgk/c102416/c5202426/content.html'),
('关于印发《陕西省制造业融资租赁财政贴息实施细则（2026年）》的通知.pdf','https://gxt.shaanxi.gov.cn/xw/tzgg/202604/t20260415_3630244.html'),
('关于优化实施设备更新贷款财政贴息政策的通知.pdf','https://gs.mof.gov.cn/zhengcefagui/202606/t20260601_3990940.htm'),
('科技部 财政部 税务总局关于修订印发《高新技术企业认定管理办法》的通知.pdf','https://www.most.gov.cn/xxgk/xinxifenlei/fdzdgknr/fgzc/gfxwj/gfxwj2016/201602/t20160205_123998.html'),
('三部门关于组织开展2025年首批次新材料保险补偿资金申请和2026年资格审定工作的通知.pdf','https://www.miit.gov.cn/zwgk/zcwj/wjfb/tz/art/2025/art_9196113d624e458e985418254d212a28.html'),
('陕财办金〔2026〕23号_秦科贷风险补偿实施细则.pdf','https://czt.shaanxi.gov.cn/xxgk/zdgk/lvyj/qywj/202607/t20260709_3655111.html'),
('陕西省秦创原创新驱动平台建设工作领导小组关于印发《秦创原创新驱动平台建设省级政策包》的通知.pdf','https://kjt.shaanxi.gov.cn/kjdt/tzgg/202311/P020250118837191802389.pdf'),
('延安市人民政府办公室_br__关于印发《延安市进一步促进民营经济高质量发展“十大行动”实施方案》的通知--市工业和信息化局1788427813000.pdf','https://gxj.yanan.gov.cn/zfxxgk/fdzdgknr/zcwj/sgxjwj/1977957847725625346.html'),
('支持绿色发展税费优惠政策指引.pdf','https://www.chinatax.gov.cn/chinatax/c102153/common_list_lsfz.html');

UPDATE cms_knowledge_document d
LEFT JOIN tmp_finance_policy_official_source m
  ON d.local_pdf_path = CONCAT('/files/finance-policy/', m.filename)
SET d.source_url = COALESCE(m.source_url, '')
WHERE d.tenant_id = 1
  AND d.doc_category = 'FINANCE_POLICY'
  AND d.deleted_at IS NULL;

UPDATE cms_document_review r
JOIN cms_knowledge_document d
  ON d.tenant_id = r.tenant_id AND d.review_id = r.id
LEFT JOIN tmp_finance_policy_official_source m
  ON d.local_pdf_path = CONCAT('/files/finance-policy/', m.filename)
SET r.snapshot = JSON_SET(
  r.snapshot,
  '$.report.metadata.sourceUrl', COALESCE(m.source_url, ''),
  '$.report.evidence.officialMetadata.sourceUrl', COALESCE(m.source_url, ''),
  '$.report.evidence.validitySourceUrl', COALESCE(m.source_url, ''),
  '$.evidenceHistory[0].officialMetadata.sourceUrl', COALESCE(m.source_url, ''),
  '$.evidenceHistory[0].validitySourceUrl', COALESCE(m.source_url, '')
)
WHERE d.tenant_id = 1
  AND d.doc_category = 'FINANCE_POLICY'
  AND d.deleted_at IS NULL;

DROP TEMPORARY TABLE tmp_finance_policy_official_source;
COMMIT;
