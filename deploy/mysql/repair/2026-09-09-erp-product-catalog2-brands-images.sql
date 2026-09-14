-- ERP 商品品牌与图片补充（租户 1 / 店铺 1）
-- 执行顺序：请在 2026-09-09-erp-product-catalog.sql 之后执行本文件。
-- 品牌名称以 ERP 产品档案为准；WMS 品牌编号写入品牌描述，便于两边核对。
-- WMS 文档说明品牌档案不单独维护 LOGO，因此品牌图片复用对应商品图片。
-- 图片来自公开网络商品页，仅用于本地测试展示；生产环境请替换为已获授权的素材。
SET NAMES utf8mb4;
START TRANSACTION;

SET @img_paint = 'https://img10.360buyimg.com/n1/s720x720_jfs/t1/336332/32/23860/119786/68f9d9c3F71e1c1de/e9e7c36953dd04b2.png';
SET @img_fire = 'https://image.made-in-china.com/365f3j00DfOMRqemEkui/Outdoor-Intumescent-Thin-Water-Based-Fire-Resistive-Good-Decorative-Effect-Coating-for-Steel-Structures.webp';
SET @img_powder = 'https://ko.onekapaint.com/js/htmledit/kindeditor/attached/20210812/20210812095150_70467.jpg';
SET @img_adhesive = 'https://image.made-in-china.com/202f0j00vwNbJoekdBcq/Dry-Laminating-Adhesive-for-Food-Soft-Packing-PU-501-G75-.webp';
SET @img_pe = 'https://image.made-in-china.com/202f0j00epPcvEGMAJqB/Cold-Applied-3PE-Anticorrosion-Pipeline-Tape.webp';
SET @img_glass = 'https://image.made-in-china.com/2f0j00pTSqQKfRmiza/Huali-Ceiling-Insulation-Suspended-Ceiling-Glass-Wool-Insulation-Fiber-Glass-Wool-Roll-with-Aluminum-Foil.webp';
SET @img_silicate = 'https://img.alicdn.com/i3/3988624257/O1CN01rkfSTA1hJktK5WZGY_%21%213988624257.jpg';
SET @img_pir = 'https://14060438.s21i.faiusr.com/2/ABUIABACGAAgl_bT5gUo-LT_ugUw1As4xQk%21700x700.jpg';
SET @img_puf = 'https://img11.360buyimg.com/n1/s720x720_jfs/t1/360557/12/2760/72707/69140923Ff135e0a4/8b1dc28828e909f3.jpg';
SET @img_aerogel = 'https://image.made-in-china.com/202f0j00RbwiMnPKfcGz/Aerogel-Insualtion-Padding-Silica-Aerogel-Blanket-Roof-and-Building.webp';
SET @img_moisture = 'https://r2.shopwis.cn/400/product/1172/image/2025/10/20/748e81db8a64674e080afa50188e8a14.webp';
SET @img_tape = 'https://img13.360buyimg.com/n12/jfs/t1/288878/24/7532/140867/68aae2dfF15c8cfc9/ecd61162204d2fc9.jpg';
SET @img_metal = 'https://thai.baodupanel.com/photo/pc151721673-customized_profiled_metal_sheeting_roofing_panel_prefabricated_0_3_3mm.jpg';
SET @img_strap = 'https://down-tw.img.susercontent.com/file/tw-11134207-7rase-m1v5ln0o4izh45';
SET @img_support = 'https://image.made-in-china.com/202f0j00DQMoTFhgHlpm/Chilled-Water-Pipeline-Cooling-Pipe-Bracket-High-Density-Polyurethane-Pipe-Support.webp';
SET @img_sand = 'https://image.made-in-china.com/202f0j00vDjoHCqWLIgA/20-40-Mesh-Quartz-Sand-Particles-for-Sand-Blasting-and-Rust-Removal.webp';
SET @img_mesh = 'https://iororwxhqopojr5p-static.micyjz.com/cloud/lnBplKqojlSRnkrnqioljq/2.jpg';
SET @img_screws = 'https://img.alicdn.com/bao/uploaded/i1/2201440460220/O1CN01i2gh0u1DUnjny1x1X_%21%210-item_pic.jpg';
SET @img_mask = 'https://img.alicdn.com/i4/3081431082/TB28vFyuuuSBuNjy1XcXXcYjFXa_%21%213081431082.jpg';
SET @img_harness = 'https://img.alicdn.com/imgextra/i1/2212595544309/O1CN01ExXyb21hhZR3KtKVO_%21%212212595544309.jpg_400x400.jpg_.webp';
SET @img_sprayer = 'https://www.graco.com/content/dam/pim/graco/ced/paint/249617_GH_833_Big_Rig_Complete_Sprayer_Main.jpg';

-- WMS BR001-BR007 + ERP 产品档案中的补充品牌。
INSERT INTO product_brand
  (id,name,pic_url,sort,description,status,creator,create_time,updater,update_time,deleted,tenant_id)
VALUES
  (930001,'海虹老人',@img_paint,1,'ERP/WMS BR001；防腐涂料品牌，主要供应商 GYS001 江苏鑫盛防腐涂料。',0,'erp-import',NOW(),'erp-import',NOW(),b'0',1),
  (930002,'华美',@img_glass,2,'WMS BR002；玻璃棉品牌，主要供应商 GYS004 河北中兴绝热材料。',0,'erp-import',NOW(),'erp-import',NOW(),b'0',1),
  (930003,'鲁阳节能',@img_silicate,3,'WMS BR003；陶瓷纤维品牌，主要供应商 GYS004 河北中兴绝热材料。',0,'erp-import',NOW(),'erp-import',NOW(),b'0',1),
  (930004,'欧文斯科宁',@img_pir,4,'WMS BR004；保温/保冷材料品牌，主要供应商 GYS004 河北中兴绝热材料。',0,'erp-import',NOW(),'erp-import',NOW(),b'0',1),
  (930005,'四川蜀立',@img_fire,5,'WMS BR005；防火涂料专业品牌，主要供应商 GYS005 四川蜀立消防科技。',0,'erp-import',NOW(),'erp-import',NOW(),b'0',1),
  (930006,'金彩',@img_metal,6,'WMS BR006；外护板材品牌，主要供应商 GYS003 山东金彩板业。',0,'erp-import',NOW(),'erp-import',NOW(),b'0',1),
  (930007,'齐鲁石化',@img_pe,7,'WMS BR007；3PE/FBE 防腐材料品牌，主要供应商 GYS007 山东齐鲁石化防腐材料。',0,'erp-import',NOW(),'erp-import',NOW(),b'0',1),
  (930008,'阿克苏诺贝尔',@img_powder,8,'ERP 产品档案品牌；FBE 环氧粉末涂料。',0,'erp-import',NOW(),'erp-import',NOW(),b'0',1),
  (930009,'陶氏化学',@img_adhesive,9,'ERP 产品档案品牌；3PE 共聚胶粘剂。',0,'erp-import',NOW(),'erp-import',NOW(),b'0',1),
  (930010,'中石化齐鲁',@img_pe,10,'ERP 产品档案品牌；聚乙烯外层（PE）。',0,'erp-import',NOW(),'erp-import',NOW(),b'0',1),
  (930011,'巴斯夫',@img_pir,11,'ERP 产品档案品牌；PIR 聚异氰脲酸酯保冷板。',0,'erp-import',NOW(),'erp-import',NOW(),b'0',1),
  (930012,'万华化学',@img_puf,12,'ERP 产品档案品牌；PUF 聚氨酯硬泡保冷管壳。',0,'erp-import',NOW(),'erp-import',NOW(),b'0',1),
  (930013,'埃力生',@img_aerogel,13,'ERP 产品档案品牌；纳米气凝胶保冷毡。',0,'erp-import',NOW(),'erp-import',NOW(),b'0',1),
  (930014,'科顺',@img_moisture,14,'ERP 产品档案品牌；聚氨酯防潮卷材。',0,'erp-import',NOW(),'erp-import',NOW(),b'0',1),
  (930015,'3M',@img_tape,15,'ERP 产品档案品牌；铝箔玻纤防潮胶带及防尘防毒面罩。',0,'erp-import',NOW(),'erp-import',NOW(),b'0',1),
  (930016,'宝钢',@img_metal,16,'ERP 产品档案品牌；镀铝锌彩钢板。',0,'erp-import',NOW(),'erp-import',NOW(),b'0',1),
  (930017,'南山铝业',@img_metal,17,'ERP 产品档案品牌；铝皮（保温外护）。',0,'erp-import',NOW(),'erp-import',NOW(),b'0',1),
  (930018,'廊坊新元',@img_support,18,'ERP 产品档案品牌；PIR 保冷管托。',0,'erp-import',NOW(),'erp-import',NOW(),b'0',1),
  (930019,'巨力',@img_harness,19,'ERP 产品档案品牌；高空作业安全带。',0,'erp-import',NOW(),'erp-import',NOW(),b'0',1),
  (930020,'固瑞克',@img_sprayer,20,'ERP 产品档案品牌；GH833 无气喷涂机。',0,'erp-import',NOW(),'erp-import',NOW(),b'0',1),
  (930021,'无品牌',@img_sand,21,'WMS/ERP 档案标记为无品牌；用于保持商品品牌字段完整。',0,'erp-import',NOW(),'erp-import',NOW(),b'0',1)
ON DUPLICATE KEY UPDATE
  name=VALUES(name),pic_url=VALUES(pic_url),sort=VALUES(sort),description=VALUES(description),
  status=VALUES(status),updater='erp-import',update_time=NOW(),deleted=b'0',tenant_id=1;

-- 将 ERP 产品档案中的精确品牌绑定到租户 1 商品。无品牌商品保留 NULL。
UPDATE product_spu
SET brand_id = CASE id
  WHEN 910001 THEN 930001
  WHEN 910002 THEN 930001
  WHEN 910003 THEN 930001
  WHEN 910004 THEN 930008
  WHEN 910005 THEN 930009
  WHEN 910006 THEN 930010
  WHEN 910007 THEN 930005
  WHEN 910008 THEN 930005
  WHEN 910009 THEN 930002
  WHEN 910010 THEN 930003
  WHEN 910011 THEN 930011
  WHEN 910012 THEN 930012
  WHEN 910013 THEN 930013
  WHEN 910014 THEN 930014
  WHEN 910015 THEN 930015
  WHEN 910016 THEN 930016
  WHEN 910017 THEN 930017
  WHEN 910018 THEN 930021
  WHEN 910019 THEN 930018
  WHEN 910020 THEN 930018
  WHEN 910021 THEN 930018
  WHEN 910022 THEN 930018
  WHEN 910023 THEN 930021
  WHEN 910024 THEN 930001
  WHEN 910025 THEN 930021
  WHEN 910026 THEN 930021
  WHEN 910027 THEN 930015
  WHEN 910028 THEN 930019
  WHEN 910029 THEN 930020
  WHEN 910030 THEN 930021
  ELSE brand_id END,
  pic_url = CASE id
    WHEN 910001 THEN @img_paint WHEN 910002 THEN @img_paint WHEN 910003 THEN @img_paint
    WHEN 910004 THEN @img_powder WHEN 910005 THEN @img_adhesive WHEN 910006 THEN @img_pe
    WHEN 910007 THEN @img_fire WHEN 910008 THEN @img_fire WHEN 910009 THEN @img_glass
    WHEN 910010 THEN @img_silicate WHEN 910011 THEN @img_pir WHEN 910012 THEN @img_puf
    WHEN 910013 THEN @img_aerogel WHEN 910014 THEN @img_moisture WHEN 910015 THEN @img_tape
    WHEN 910016 THEN @img_metal WHEN 910017 THEN @img_metal WHEN 910018 THEN @img_strap
    WHEN 910019 THEN @img_support WHEN 910020 THEN @img_support WHEN 910021 THEN @img_support
    WHEN 910022 THEN @img_support WHEN 910023 THEN @img_sand WHEN 910024 THEN @img_paint
    WHEN 910025 THEN @img_mesh WHEN 910026 THEN @img_screws WHEN 910027 THEN @img_mask
    WHEN 910028 THEN @img_harness WHEN 910029 THEN @img_sprayer WHEN 910030 THEN @img_metal
    ELSE pic_url END,
  slider_pic_urls = CASE id
    WHEN 910001 THEN JSON_ARRAY(@img_paint) WHEN 910002 THEN JSON_ARRAY(@img_paint) WHEN 910003 THEN JSON_ARRAY(@img_paint)
    WHEN 910004 THEN JSON_ARRAY(@img_powder) WHEN 910005 THEN JSON_ARRAY(@img_adhesive) WHEN 910006 THEN JSON_ARRAY(@img_pe)
    WHEN 910007 THEN JSON_ARRAY(@img_fire) WHEN 910008 THEN JSON_ARRAY(@img_fire) WHEN 910009 THEN JSON_ARRAY(@img_glass)
    WHEN 910010 THEN JSON_ARRAY(@img_silicate) WHEN 910011 THEN JSON_ARRAY(@img_pir) WHEN 910012 THEN JSON_ARRAY(@img_puf)
    WHEN 910013 THEN JSON_ARRAY(@img_aerogel) WHEN 910014 THEN JSON_ARRAY(@img_moisture) WHEN 910015 THEN JSON_ARRAY(@img_tape)
    WHEN 910016 THEN JSON_ARRAY(@img_metal) WHEN 910017 THEN JSON_ARRAY(@img_metal) WHEN 910018 THEN JSON_ARRAY(@img_strap)
    WHEN 910019 THEN JSON_ARRAY(@img_support) WHEN 910020 THEN JSON_ARRAY(@img_support) WHEN 910021 THEN JSON_ARRAY(@img_support)
    WHEN 910022 THEN JSON_ARRAY(@img_support) WHEN 910023 THEN JSON_ARRAY(@img_sand) WHEN 910024 THEN JSON_ARRAY(@img_paint)
    WHEN 910025 THEN JSON_ARRAY(@img_mesh) WHEN 910026 THEN JSON_ARRAY(@img_screws) WHEN 910027 THEN JSON_ARRAY(@img_mask)
    WHEN 910028 THEN JSON_ARRAY(@img_harness) WHEN 910029 THEN JSON_ARRAY(@img_sprayer) WHEN 910030 THEN JSON_ARRAY(@img_metal)
    ELSE slider_pic_urls END,
  updater='erp-import',update_time=NOW()
WHERE tenant_id=1 AND id BETWEEN 910001 AND 910030 AND deleted=b'0';

-- SKU 图与 SPU 保持一致，避免商品详情切换规格后回到旧占位图。
UPDATE product_sku sku
JOIN product_spu spu ON spu.id=sku.spu_id AND spu.tenant_id=1
SET sku.pic_url=spu.pic_url, sku.updater='erp-import', sku.update_time=NOW()
WHERE sku.tenant_id=1 AND sku.deleted=b'0' AND spu.id BETWEEN 910001 AND 910030;

COMMIT;
