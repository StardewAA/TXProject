-- 科盾租户（2009）商城验收数据 + 公共商城租户（3000）。
-- 所有新增记录使用固定保留编号和 mall-kedun-20260917 标识，可重复执行；不删除或改写历史资金记录。
SET NAMES utf8mb4;
START TRANSACTION;

INSERT INTO system_tenant
  (id,name,contact_user_id,contact_name,contact_mobile,status,websites,package_id,expire_time,account_count,creator,updater,deleted)
VALUES
  (3000,'商城平台公共用户',NULL,'平台商城','13900003000',0,NULL,119,'2099-12-31 00:00:00',100,
   'mall-kedun-20260917','mall-kedun-20260917',b'0')
ON DUPLICATE KEY UPDATE name=VALUES(name),status=0,package_id=119,expire_time=VALUES(expire_time),
  updater='mall-kedun-20260917',update_time=NOW(),deleted=b'0';

INSERT INTO product_enterprise
  (id,tenant_id,name,credit_code,type,license_url,contact_name,contact_mobile,address,introduction,
   audit_status,audit_remark,creator,updater,deleted)
VALUES
  (940400,3000,'平台自营企业','MALL-PLATFORM-3000',2,NULL,'平台商城','13900003000','陕西省西安市',
   '商城公共租户的客户服务归属企业，不自动授予采购或销售权限。',1,'平台演示档案','mall-kedun-20260917','mall-kedun-20260917',b'0'),
  (940401,2009,'科盾防护科技股份有限公司','KD-DEMO-2009-01',2,NULL,'罗欣','13900002091','陕西省西安市高新区',
   '工业重防腐涂料与配套体系供应商。',1,'科盾租户验收数据','mall-kedun-20260917','mall-kedun-20260917',b'0'),
  (940402,2009,'秦盾防腐材料有限公司','KD-DEMO-2009-02',2,NULL,'王宁','13900002092','陕西省咸阳市秦都区',
   '环氧底漆、中间漆及防火涂料生产企业。',1,'科盾租户验收数据','mall-kedun-20260917','mall-kedun-20260917',b'0'),
  (940403,2009,'西北恒温保温材料有限公司','KD-DEMO-2009-03',2,NULL,'赵敏','13900002093','陕西省西安市临潼区',
   '岩棉、玻璃棉及橡塑保温材料供应企业。',1,'科盾租户验收数据','mall-kedun-20260917','mall-kedun-20260917',b'0'),
  (940404,2009,'华秦管道防护设备有限公司','KD-DEMO-2009-04',2,NULL,'刘洋','13900002094','陕西省渭南市临渭区',
   '预制保温管、防腐钢管及配套管件生产企业。',1,'科盾租户验收数据','mall-kedun-20260917','mall-kedun-20260917',b'0'),
  (940405,2009,'陕能耐火保温科技有限公司','KD-DEMO-2009-05',2,NULL,'陈静','13900002095','陕西省宝鸡市高新区',
   '硅酸铝纤维、耐火模块及高温保温制品供应企业。',1,'科盾租户验收数据','mall-kedun-20260917','mall-kedun-20260917',b'0')
ON DUPLICATE KEY UPDATE name=VALUES(name),type=VALUES(type),contact_name=VALUES(contact_name),
 contact_mobile=VALUES(contact_mobile),address=VALUES(address),introduction=VALUES(introduction),audit_status=1,
 updater='mall-kedun-20260917',update_time=NOW(),deleted=b'0';

INSERT INTO product_shop
 (id,enterprise_id,name,logo_url,contact_name,contact_mobile,manager_user_id,introduction,status,tenant_id,creator,updater,deleted)
VALUES
 (940500,940400,'平台自营商城',NULL,'平台商城','13900003000',NULL,'平台公共租户自营店铺',0,3000,'mall-kedun-20260917','mall-kedun-20260917',b'0'),
 (940501,940401,'科盾工业防护旗舰店',NULL,'罗欣','13900002091',840001,'科盾工业防腐涂料官方店',0,2009,'mall-kedun-20260917','mall-kedun-20260917',b'0'),
 (940502,940402,'秦盾防腐材料店',NULL,'王宁','13900002092',840002,'秦盾防腐材料官方店',0,2009,'mall-kedun-20260917','mall-kedun-20260917',b'0'),
 (940503,940403,'恒温保温材料店',NULL,'赵敏','13900002093',840003,'西北恒温保温材料官方店',0,2009,'mall-kedun-20260917','mall-kedun-20260917',b'0'),
 (940504,940404,'华秦管道防护店',NULL,'刘洋','13900002094',840004,'华秦管道防护设备官方店',0,2009,'mall-kedun-20260917','mall-kedun-20260917',b'0'),
 (940505,940405,'陕能耐火保温店',NULL,'陈静','13900002095',840005,'陕能耐火保温科技官方店',0,2009,'mall-kedun-20260917','mall-kedun-20260917',b'0')
ON DUPLICATE KEY UPDATE enterprise_id=VALUES(enterprise_id),name=VALUES(name),contact_name=VALUES(contact_name),
 contact_mobile=VALUES(contact_mobile),manager_user_id=VALUES(manager_user_id),introduction=VALUES(introduction),
 status=0,updater='mall-kedun-20260917',update_time=NOW(),deleted=b'0';

INSERT INTO product_brand(id,name,pic_url,sort,description,status,creator,updater,deleted,tenant_id) VALUES
 (940101,'科盾防护','',10,'工业防腐涂料品牌',0,'mall-kedun-20260917','mall-kedun-20260917',b'0',2009),
 (940102,'固邦防腐','',20,'防腐、防火及工业保护涂层品牌',0,'mall-kedun-20260917','mall-kedun-20260917',b'0',2009),
 (940103,'华瀚节能','',30,'聚氨酯与PIR保温保冷材料品牌',0,'mall-kedun-20260917','mall-kedun-20260917',b'0',2009),
 (940104,'锐捷管道','',40,'预制保温管、防腐管及管件品牌',0,'mall-kedun-20260917','mall-kedun-20260917',b'0',2009),
 (940105,'卓远耐火','',50,'硅酸铝及高温耐火保温材料品牌',0,'mall-kedun-20260917','mall-kedun-20260917',b'0',2009)
ON DUPLICATE KEY UPDATE name=VALUES(name),description=VALUES(description),status=0,updater='mall-kedun-20260917',update_time=NOW(),deleted=b'0';

INSERT INTO product_category(id,parent_id,name,pic_url,sort,status,creator,updater,deleted,tenant_id) VALUES
 (940001,0,'防腐材料','',10,0,'mall-kedun-20260917','mall-kedun-20260917',b'0',2009),
 (940002,940001,'底漆与中间漆','',11,0,'mall-kedun-20260917','mall-kedun-20260917',b'0',2009),
 (940003,940001,'防腐面漆','',12,0,'mall-kedun-20260917','mall-kedun-20260917',b'0',2009),
 (940004,0,'保温材料','',20,0,'mall-kedun-20260917','mall-kedun-20260917',b'0',2009),
 (940005,0,'管材管件','',30,0,'mall-kedun-20260917','mall-kedun-20260917',b'0',2009),
 (940006,0,'耐火材料','',40,0,'mall-kedun-20260917','mall-kedun-20260917',b'0',2009)
ON DUPLICATE KEY UPDATE parent_id=VALUES(parent_id),name=VALUES(name),sort=VALUES(sort),status=0,updater='mall-kedun-20260917',update_time=NOW(),deleted=b'0';

INSERT INTO trade_delivery_express_template(id,name,charge_mode,sort,creator,updater,deleted,tenant_id) VALUES
 (940901,'科盾商城全国快递模板',1,100,'mall-kedun-20260917','mall-kedun-20260917',b'0',2009)
ON DUPLICATE KEY UPDATE name=VALUES(name),charge_mode=1,sort=100,updater='mall-kedun-20260917',update_time=NOW(),deleted=b'0';
INSERT INTO trade_delivery_express_template_charge
 (id,template_id,area_ids,charge_mode,start_count,start_price,extra_count,extra_price,creator,updater,deleted,tenant_id)
VALUES (940902,940901,'110000,120000,130000,140000,150000,210000,220000,230000,310000,320000,330000,340000,350000,360000,370000,410000,420000,430000,440000,450000,460000,500000,510000,520000,530000,540000,610000,620000,630000,640000,650000,710000,810000,820000',1,1,1200,1,300,
 'mall-kedun-20260917','mall-kedun-20260917',b'0',2009)
ON DUPLICATE KEY UPDATE area_ids=VALUES(area_ids),start_count=1,start_price=1200,extra_count=1,extra_price=300,
 updater='mall-kedun-20260917',update_time=NOW(),deleted=b'0';

INSERT INTO product_spu
 (id,shop_id,name,keyword,introduction,description,category_id,brand_id,pic_url,slider_pic_urls,sort,status,spec_type,
  price,market_price,cost_price,stock,delivery_types,delivery_template_id,give_integral,sub_commission_type,sales_count,
  virtual_sales_count,browse_count,creator,updater,deleted,tenant_id,review_status,content_version,approved_version,submission_id,review_remark)
VALUES
 (940201,940501,'环氧富锌底漆 20kg','环氧富锌 底漆','钢结构重防腐底漆','适用于钢结构与管道重防腐体系。',940002,940101,'','[]',10,1,b'0',39800,42800,31000,300,'1',940901,398,b'0',86,20,560,'mall-kedun-20260917','mall-kedun-20260917',b'0',2009,20,1,1,'KD-940201','演示商品已审核'),
 (940202,940501,'脂肪族聚氨酯面漆 20kg','聚氨酯 面漆','耐候高光工业面漆','适用于户外钢结构防腐面层。',940003,940101,'','[]',20,1,b'0',45800,48800,35000,220,'1',940901,458,b'0',72,15,430,'mall-kedun-20260917','mall-kedun-20260917',b'0',2009,20,1,1,'KD-940202','演示商品已审核'),
 (940203,940502,'环氧云铁中间漆 20kg','环氧云铁 中间漆','高固体份中间漆','增强漆膜厚度与屏蔽性能。',940002,940102,'','[]',30,1,b'0',32800,35800,25000,260,'1',940901,328,b'0',64,18,390,'mall-kedun-20260917','mall-kedun-20260917',b'0',2009,20,1,1,'KD-940203','演示商品已审核'),
 (940204,940502,'薄型钢结构防火涂料 25kg','防火涂料','钢结构防火保护','适用于室内钢结构防火工程。',940007,940102,'','[]',40,1,b'0',26800,29800,21000,180,'1',940901,268,b'0',48,10,310,'mall-kedun-20260917','mall-kedun-20260917',b'0',2009,20,1,1,'KD-940204','演示商品已审核'),
 (940205,940503,'憎水岩棉板 100mm','岩棉板 保温','A级防火保温板','工业设备与建筑围护保温。',940008,940106,'','[]',50,1,b'0',8600,9800,6500,500,'1',940901,86,b'0',120,35,780,'mall-kedun-20260917','mall-kedun-20260917',b'0',2009,20,1,1,'KD-940205','演示商品已审核'),
 (940206,940503,'橡塑保温管壳 B1级','橡塑 管壳','闭孔橡塑保温管壳','适用于空调和工艺管道保温。',940009,940107,'','[]',60,1,b'0',12800,14800,9600,420,'1',940901,128,b'0',96,25,640,'mall-kedun-20260917','mall-kedun-20260917',b'0',2009,20,1,1,'KD-940206','演示商品已审核'),
 (940207,940504,'预制直埋保温管 DN100','保温管 管道','聚氨酯预制直埋保温管','城镇供热工程管道成品。',940010,940104,'','[]',70,1,b'0',28600,31800,22000,160,'1',940901,286,b'0',43,8,270,'mall-kedun-20260917','mall-kedun-20260917',b'0',2009,20,1,1,'KD-940207','演示商品已审核'),
 (940208,940504,'3PE 防腐钢管 DN150','防腐钢管 3PE','三层聚乙烯防腐钢管','适用于埋地输送管线。',940010,940104,'','[]',80,1,b'0',36800,39800,29000,140,'1',940901,368,b'0',39,6,240,'mall-kedun-20260917','mall-kedun-20260917',b'0',2009,20,1,1,'KD-940208','演示商品已审核'),
 (940209,940505,'硅酸铝纤维毯 50mm','硅酸铝 纤维毯','高温设备保温材料','耐高温、低导热的工业炉保温材料。',940006,940105,'','[]',90,1,b'0',17600,19800,13200,280,'1',940901,176,b'0',58,12,360,'mall-kedun-20260917','mall-kedun-20260917',b'0',2009,20,1,1,'KD-940209','演示商品已审核'),
 (940210,940505,'耐火纤维模块 300mm','耐火模块','工业炉衬耐火模块','适用于高温炉窑内衬施工。',940006,940105,'','[]',100,1,b'0',23800,26800,18500,190,'1',940901,238,b'0',35,8,210,'mall-kedun-20260917','mall-kedun-20260917',b'0',2009,20,1,1,'KD-940210','演示商品已审核')
ON DUPLICATE KEY UPDATE shop_id=VALUES(shop_id),name=VALUES(name),category_id=VALUES(category_id),brand_id=VALUES(brand_id),
 price=VALUES(price),market_price=VALUES(market_price),cost_price=VALUES(cost_price),stock=VALUES(stock),status=1,
 delivery_types='1',delivery_template_id=940901,review_status=20,approved_version=content_version,
 updater='mall-kedun-20260917',update_time=NOW(),deleted=b'0';

-- 扩展到 35 个有业务含义的商品；JSON 仅作为紧凑的种子清单，不在运行期解析。
INSERT INTO product_spu
 (id,shop_id,name,keyword,introduction,description,category_id,brand_id,pic_url,slider_pic_urls,sort,status,spec_type,
  price,market_price,cost_price,stock,delivery_types,delivery_template_id,give_integral,sub_commission_type,sales_count,
  virtual_sales_count,browse_count,creator,updater,deleted,tenant_id,review_status,content_version,approved_version,submission_id,review_remark)
SELECT j.id,j.shop_id,j.name,j.keyword,j.name,CONCAT(j.name,'，适用于防腐保温工程采购。'),j.category_id,j.brand_id,'','[]',j.id-940100,1,b'0',
 j.price,j.price+3000,j.cost_price,j.stock,'1',940901,FLOOR(j.price/100),b'0',j.sales_count,FLOOR(j.sales_count/3),j.sales_count*6,
 'mall-kedun-20260917','mall-kedun-20260917',b'0',2009,20,1,1,CONCAT('KD-',j.id),'演示商品已审核'
FROM JSON_TABLE('[
 {"id":940211,"shop":940501,"name":"环氧磷酸锌底漆 20kg","keyword":"环氧底漆 磷酸锌","category":940002,"brand":940101,"price":35600,"cost":27000,"stock":260,"sales":52},
 {"id":940212,"shop":940501,"name":"无机富锌底漆 25kg","keyword":"无机富锌 底漆","category":940002,"brand":940101,"price":42600,"cost":33000,"stock":180,"sales":41},
 {"id":940213,"shop":940501,"name":"环氧玻璃鳞片涂料 20kg","keyword":"玻璃鳞片 防腐","category":940003,"brand":940101,"price":47800,"cost":36500,"stock":170,"sales":38},
 {"id":940214,"shop":940501,"name":"环氧煤沥青防腐漆 20kg","keyword":"煤沥青 防腐漆","category":940003,"brand":940101,"price":29800,"cost":22000,"stock":310,"sales":69},
 {"id":940215,"shop":940501,"name":"氯化橡胶防腐面漆 20kg","keyword":"氯化橡胶 面漆","category":940003,"brand":940101,"price":33800,"cost":25500,"stock":230,"sales":47},
 {"id":940216,"shop":940502,"name":"丙烯酸聚氨酯面漆 20kg","keyword":"丙烯酸 聚氨酯","category":940003,"brand":940102,"price":44800,"cost":34200,"stock":240,"sales":61},
 {"id":940217,"shop":940502,"name":"水性工业防腐底漆 20kg","keyword":"水性 防腐底漆","category":940002,"brand":940102,"price":31800,"cost":23800,"stock":270,"sales":44},
 {"id":940218,"shop":940502,"name":"厚型钢结构防火涂料 25kg","keyword":"厚型 防火涂料","category":940007,"brand":940102,"price":18800,"cost":13900,"stock":360,"sales":82},
 {"id":940219,"shop":940502,"name":"超薄型钢结构防火涂料 20kg","keyword":"超薄型 防火涂料","category":940007,"brand":940102,"price":35800,"cost":27200,"stock":210,"sales":55},
 {"id":940220,"shop":940502,"name":"有机硅耐高温漆 20kg","keyword":"耐高温 有机硅","category":940003,"brand":940102,"price":46800,"cost":35600,"stock":160,"sales":33},
 {"id":940221,"shop":940503,"name":"岩棉保温管壳 DN100","keyword":"岩棉 管壳","category":940008,"brand":940106,"price":9600,"cost":7100,"stock":520,"sales":128},
 {"id":940222,"shop":940503,"name":"离心玻璃棉卷毡 50mm","keyword":"玻璃棉 卷毡","category":940008,"brand":940106,"price":7800,"cost":5700,"stock":620,"sales":146},
 {"id":940223,"shop":940503,"name":"硅酸铝针刺毯 50mm","keyword":"硅酸铝 针刺毯","category":940006,"brand":940105,"price":16800,"cost":12600,"stock":330,"sales":74},
 {"id":940224,"shop":940503,"name":"B1级橡塑保温板 30mm","keyword":"橡塑 保温板","category":940009,"brand":940107,"price":13800,"cost":10200,"stock":390,"sales":97},
 {"id":940225,"shop":940503,"name":"硬泡聚氨酯保温板 50mm","keyword":"聚氨酯 保温板","category":940009,"brand":940103,"price":15600,"cost":11600,"stock":350,"sales":83},
 {"id":940226,"shop":940503,"name":"PIR低温保冷板 50mm","keyword":"PIR 保冷板","category":940009,"brand":940103,"price":22600,"cost":17400,"stock":260,"sales":49},
 {"id":940227,"shop":940504,"name":"保温外护铝皮 0.5mm","keyword":"铝皮 外护","category":940012,"brand":940104,"price":11800,"cost":8900,"stock":420,"sales":101},
 {"id":940228,"shop":940504,"name":"保温外护镀锌铁皮 0.5mm","keyword":"镀锌铁皮 外护","category":940012,"brand":940104,"price":9800,"cost":7200,"stock":460,"sales":112},
 {"id":940229,"shop":940504,"name":"防潮密封玛蹄脂 20kg","keyword":"玛蹄脂 防潮","category":940012,"brand":940107,"price":25800,"cost":19500,"stock":220,"sales":57},
 {"id":940230,"shop":940504,"name":"无碱玻璃纤维布 200g","keyword":"玻纤布 防腐","category":940012,"brand":940108,"price":6800,"cost":4900,"stock":760,"sales":166},
 {"id":940231,"shop":940504,"name":"聚氨酯直埋保温管 DN200","keyword":"直埋保温管 DN200","category":940010,"brand":940104,"price":52800,"cost":41800,"stock":90,"sales":24},
 {"id":940232,"shop":940504,"name":"钢套钢蒸汽保温管 DN150","keyword":"钢套钢 蒸汽管","category":940010,"brand":940104,"price":68600,"cost":54800,"stock":70,"sales":18},
 {"id":940233,"shop":940504,"name":"3PE防腐弯头 DN100","keyword":"防腐弯头 3PE","category":940011,"brand":940104,"price":19800,"cost":14800,"stock":180,"sales":46},
 {"id":940234,"shop":940505,"name":"低温保冷管托 DN150","keyword":"保冷管托","category":940011,"brand":940104,"price":28600,"cost":21800,"stock":130,"sales":31},
 {"id":940235,"shop":940505,"name":"高温纤维粘结剂 20kg","keyword":"高温 粘结剂","category":940006,"brand":940105,"price":15800,"cost":11700,"stock":240,"sales":53}
 ]','$[*]' COLUMNS(
   id BIGINT PATH '$.id',shop_id BIGINT PATH '$.shop',name VARCHAR(128) PATH '$.name',keyword VARCHAR(256) PATH '$.keyword',
   category_id BIGINT PATH '$.category',brand_id BIGINT PATH '$.brand',price INT PATH '$.price',cost_price INT PATH '$.cost',
   stock INT PATH '$.stock',sales_count INT PATH '$.sales'
 )) j
ON DUPLICATE KEY UPDATE shop_id=VALUES(shop_id),name=VALUES(name),keyword=VALUES(keyword),category_id=VALUES(category_id),
 brand_id=VALUES(brand_id),price=VALUES(price),market_price=VALUES(market_price),cost_price=VALUES(cost_price),stock=VALUES(stock),
  status=1,delivery_types='1',delivery_template_id=940901,review_status=20,approved_version=content_version,
 updater='mall-kedun-20260917',update_time=NOW(),deleted=b'0';

INSERT INTO product_sku
 (id,spu_id,properties,price,market_price,cost_price,bar_code,pic_url,stock,weight,volume,sales_count,creator,updater,deleted,tenant_id)
SELECT 940300+ROW_NUMBER() OVER(ORDER BY id),id,'[{"propertyId":0,"propertyName":"默认","valueId":0,"valueName":"默认"}]',
 price,market_price,cost_price,CONCAT('KD2009',id),'',stock,20,0.04,sales_count,'mall-kedun-20260917','mall-kedun-20260917',b'0',2009
FROM product_spu p WHERE p.tenant_id=2009 AND p.id BETWEEN 940201 AND 940235
ON DUPLICATE KEY UPDATE price=VALUES(price),market_price=VALUES(market_price),cost_price=VALUES(cost_price),stock=VALUES(stock),
 updater='mall-kedun-20260917',update_time=NOW(),deleted=b'0';

-- 本地验收账号统一初始密码 Mall@2026；仓库只保存 BCrypt，不保存明文凭据。
INSERT INTO system_users
 (id,username,password,nickname,remark,dept_id,post_ids,email,mobile,sex,avatar,status,creator,updater,deleted,tenant_id)
VALUES
 (840001,'kedun_shop','$2b$10$Z.QMFjVaiFGqUmFb4J1FvediKwG9PlIfbL0i8YXOaBBik8FYXFiwO','科盾店铺运营','科盾商城验收账号',NULL,'[]',NULL,'13900002101',1,'',0,'mall-kedun-20260917','mall-kedun-20260917',b'0',2009),
 (840002,'qindun_shop','$2b$10$Z.QMFjVaiFGqUmFb4J1FvediKwG9PlIfbL0i8YXOaBBik8FYXFiwO','秦盾店铺运营','科盾商城验收账号',NULL,'[]',NULL,'13900002102',1,'',0,'mall-kedun-20260917','mall-kedun-20260917',b'0',2009),
 (840003,'hengwen_shop','$2b$10$Z.QMFjVaiFGqUmFb4J1FvediKwG9PlIfbL0i8YXOaBBik8FYXFiwO','恒温店铺运营','科盾商城验收账号',NULL,'[]',NULL,'13900002103',1,'',0,'mall-kedun-20260917','mall-kedun-20260917',b'0',2009),
 (840004,'huaqin_shop','$2b$10$Z.QMFjVaiFGqUmFb4J1FvediKwG9PlIfbL0i8YXOaBBik8FYXFiwO','华秦店铺运营','科盾商城验收账号',NULL,'[]',NULL,'13900002104',1,'',0,'mall-kedun-20260917','mall-kedun-20260917',b'0',2009),
 (840005,'shanneng_shop','$2b$10$Z.QMFjVaiFGqUmFb4J1FvediKwG9PlIfbL0i8YXOaBBik8FYXFiwO','陕能店铺运营','科盾商城验收账号',NULL,'[]',NULL,'13900002105',1,'',0,'mall-kedun-20260917','mall-kedun-20260917',b'0',2009),
 (840011,'kedun_kefu01','$2b$10$Z.QMFjVaiFGqUmFb4J1FvediKwG9PlIfbL0i8YXOaBBik8FYXFiwO','科盾客服一号','科盾商城客服',NULL,'[]',NULL,'13900002111',2,'',0,'mall-kedun-20260917','mall-kedun-20260917',b'0',2009),
 (840012,'kedun_kefu02','$2b$10$Z.QMFjVaiFGqUmFb4J1FvediKwG9PlIfbL0i8YXOaBBik8FYXFiwO','科盾客服二号','科盾商城客服',NULL,'[]',NULL,'13900002112',2,'',0,'mall-kedun-20260917','mall-kedun-20260917',b'0',2009),
 (840013,'kedun_kefu03','$2b$10$Z.QMFjVaiFGqUmFb4J1FvediKwG9PlIfbL0i8YXOaBBik8FYXFiwO','科盾客服三号','科盾商城客服',NULL,'[]',NULL,'13900002113',2,'',0,'mall-kedun-20260917','mall-kedun-20260917',b'0',2009)
ON DUPLICATE KEY UPDATE password=VALUES(password),nickname=VALUES(nickname),remark=VALUES(remark),mobile=VALUES(mobile),status=0,
 updater='mall-kedun-20260917',update_time=NOW(),deleted=b'0';

INSERT INTO system_role(id,name,code,sort,data_scope,data_scope_dept_ids,status,type,remark,creator,updater,deleted,tenant_id) VALUES
 (640091,'商城店铺商家','mall_shop_merchant',80,1,'[]',0,2,'仅管理本人负责店铺','mall-kedun-20260917','mall-kedun-20260917',b'0',2009),
 (640092,'商城店铺客服','mall_shop_kefu',81,1,'[]',0,2,'仅接待明确授权店铺','mall-kedun-20260917','mall-kedun-20260917',b'0',2009)
ON DUPLICATE KEY UPDATE name=VALUES(name),status=0,remark=VALUES(remark),updater='mall-kedun-20260917',update_time=NOW(),deleted=b'0';

INSERT INTO system_role_menu(role_id,menu_id,creator,updater,deleted,tenant_id)
SELECT 640091,rm.menu_id,'mall-kedun-20260917','mall-kedun-20260917',b'0',2009 FROM system_role_menu rm
WHERE rm.role_id=60467 AND rm.deleted=b'0' AND NOT EXISTS(SELECT 1 FROM system_role_menu x WHERE x.role_id=640091 AND x.menu_id=rm.menu_id AND x.deleted=b'0');
INSERT INTO system_role_menu(role_id,menu_id,creator,updater,deleted,tenant_id)
SELECT 640092,m.id,'mall-kedun-20260917','mall-kedun-20260917',b'0',2009 FROM system_menu m
WHERE (m.id=2362 OR m.permission IN ('promotion:kefu-agent:receive','promotion:kefu-faq:manage')) AND m.deleted=b'0'
AND NOT EXISTS(SELECT 1 FROM system_role_menu x WHERE x.role_id=640092 AND x.menu_id=m.id AND x.deleted=b'0');
INSERT INTO system_user_role(user_id,role_id,creator,updater,deleted,tenant_id)
SELECT u.id,IF(u.id<840011,640091,640092),'mall-kedun-20260917','mall-kedun-20260917',b'0',2009 FROM system_users u
WHERE u.id BETWEEN 840001 AND 840013 AND (u.id<=840005 OR u.id>=840011)
AND NOT EXISTS(SELECT 1 FROM system_user_role x WHERE x.user_id=u.id AND x.role_id=IF(u.id<840011,640091,640092) AND x.deleted=b'0');

INSERT INTO member_user
 (id,mobile,email,password,status,register_ip,register_terminal,nickname,avatar,name,sex,mark,point,experience,
  creator,updater,deleted,tenant_id,member_identity,login_name,contact_mobile)
VALUES
 (940001,'13900002201',NULL,'$2b$10$Z.QMFjVaiFGqUmFb4J1FvediKwG9PlIfbL0i8YXOaBBik8FYXFiwO',0,'127.0.0.1',4,'科盾采购测试','','张工',1,'科盾租户用户端验收',500,100,'mall-kedun-20260917','mall-kedun-20260917',b'0',2009,'buyer','kedun_buyer','13900002201'),
 (940002,'13900002202',NULL,'$2b$10$Z.QMFjVaiFGqUmFb4J1FvediKwG9PlIfbL0i8YXOaBBik8FYXFiwO',0,'127.0.0.1',4,'秦盾采购测试','','李工',1,'科盾租户用户端验收',300,80,'mall-kedun-20260917','mall-kedun-20260917',b'0',2009,'buyer','qindun_buyer','13900002202'),
 (940003,'13900002203',NULL,'$2b$10$Z.QMFjVaiFGqUmFb4J1FvediKwG9PlIfbL0i8YXOaBBik8FYXFiwO',0,'127.0.0.1',4,'工程采购测试','','周工',2,'科盾租户用户端验收',200,50,'mall-kedun-20260917','mall-kedun-20260917',b'0',2009,'buyer','project_buyer','13900002203')
ON DUPLICATE KEY UPDATE password=VALUES(password),status=0,nickname=VALUES(nickname),mark=VALUES(mark),
 updater='mall-kedun-20260917',update_time=NOW(),deleted=b'0';

INSERT INTO promotion_kefu_agent(tenant_id,scope,shop_id,actor_type,actor_id,enabled,version,update_time) VALUES
 (2009,'SHOP',940501,2,840011,1,1,NOW()),(2009,'SHOP',940502,2,840011,1,1,NOW()),
 (2009,'SHOP',940503,2,840012,1,1,NOW()),(2009,'SHOP',940504,2,840012,1,1,NOW()),
 (2009,'SHOP',940505,2,840013,1,1,NOW())
ON DUPLICATE KEY UPDATE enabled=1,version=GREATEST(version,1),update_time=NOW();
INSERT INTO promotion_kefu_faq_config(tenant_id,scope,shop_id,version,draft_json,published_version,published_json,update_time)
SELECT 2009,'SHOP',s.id,1,
 '[{"id":"delivery","question":"什么时候发货？","keywords":["发货时间","多久发货"],"answer":"现货商品通常在付款后两个工作日内发出。","enabled":true},{"id":"spec","question":"规格如何选择？","keywords":["规格选择","型号推荐"],"answer":"请发送商品卡片和使用工况，客服会协助核对规格。","enabled":true}]',
 1,'[{"id":"delivery","question":"什么时候发货？","keywords":["发货时间","多久发货"],"answer":"现货商品通常在付款后两个工作日内发出。","enabled":true},{"id":"spec","question":"规格如何选择？","keywords":["规格选择","型号推荐"],"answer":"请发送商品卡片和使用工况，客服会协助核对规格。","enabled":true}]',NOW()
FROM product_shop s WHERE s.tenant_id=2009 AND s.id BETWEEN 940501 AND 940505
ON DUPLICATE KEY UPDATE version=VALUES(version),draft_json=VALUES(draft_json),published_version=VALUES(published_version),published_json=VALUES(published_json),update_time=NOW();

-- 5 笔独立、可识别的演示支付；不改写现有 54 笔历史支付。
INSERT INTO pay_app(id,app_key,name,status,remark,order_notify_url,refund_notify_url,transfer_notify_url,creator,updater,deleted,tenant_id)
VALUES(940001,'mall','科盾商城演示支付',0,'用于本地商城订单与模拟支付','http://trade-server/admin-api/pay/notify/order','http://trade-server/admin-api/pay/notify/refund',NULL,'mall-kedun-20260917','mall-kedun-20260917',b'0',2009)
ON DUPLICATE KEY UPDATE app_key=VALUES(app_key),name=VALUES(name),status=0,remark=VALUES(remark),updater='mall-kedun-20260917',update_time=NOW(),deleted=b'0';

INSERT INTO pay_channel
 (id,code,status,remark,fee_rate,app_id,config,creator,updater,deleted,tenant_id)
VALUES
 (940011,'mock',0,'本地模拟支付',0,940001,'{"@class":"cn.iocoder.txgy.framework.pay.core.client.impl.NonePayClientConfig","name":"mock-conf"}','mall-kedun-20260917','mall-kedun-20260917',b'0',2009),
 (940012,'wallet',0,'会员余额支付',0,940001,'{"@class":"cn.iocoder.txgy.framework.pay.core.client.impl.NonePayClientConfig","name":"mock-conf"}','mall-kedun-20260917','mall-kedun-20260917',b'0',2009)
ON DUPLICATE KEY UPDATE code=VALUES(code),status=VALUES(status),remark=VALUES(remark),fee_rate=VALUES(fee_rate),
 app_id=VALUES(app_id),config=VALUES(config),updater=VALUES(updater),update_time=NOW(),deleted=b'0';

INSERT INTO pay_order
 (id,app_id,channel_id,channel_code,user_id,user_type,merchant_order_id,subject,body,notify_url,price,status,user_ip,
  expire_time,success_time,no,refund_price,channel_order_no,creator,updater,deleted,tenant_id)
VALUES
 (940601,940001,NULL,'mock',940001,2,'940701','[演示]科盾防腐材料订单','科盾商城历史支付','http://trade-server/admin-api/pay/notify/order',39800,20,'127.0.0.1','2026-08-01 10:30:00','2026-08-01 10:05:00','KD-PAY-940601',0,'KD-MOCK-940601','mall-kedun-20260917','mall-kedun-20260917',b'0',2009),
 (940602,940001,NULL,'mock',940002,2,'940702','[演示]秦盾防腐材料订单','科盾商城历史支付','http://trade-server/admin-api/pay/notify/order',32800,20,'127.0.0.1','2026-08-06 11:30:00','2026-08-06 11:08:00','KD-PAY-940602',0,'KD-MOCK-940602','mall-kedun-20260917','mall-kedun-20260917',b'0',2009),
 (940603,940001,NULL,'mock',940003,2,'940703','[演示]恒温保温材料订单','科盾商城历史支付','http://trade-server/admin-api/pay/notify/order',17200,20,'127.0.0.1','2026-08-12 09:30:00','2026-08-12 09:12:00','KD-PAY-940603',0,'KD-MOCK-940603','mall-kedun-20260917','mall-kedun-20260917',b'0',2009),
 (940604,940001,NULL,'mock',940001,2,'940704','[演示]华秦管道订单','科盾商城历史支付','http://trade-server/admin-api/pay/notify/order',28600,20,'127.0.0.1','2026-08-20 15:30:00','2026-08-20 15:03:00','KD-PAY-940604',0,'KD-MOCK-940604','mall-kedun-20260917','mall-kedun-20260917',b'0',2009),
 (940605,940001,NULL,'mock',940002,2,'940705','[演示]陕能耐火材料订单','科盾商城历史支付','http://trade-server/admin-api/pay/notify/order',23800,20,'127.0.0.1','2026-08-26 14:30:00','2026-08-26 14:16:00','KD-PAY-940605',0,'KD-MOCK-940605','mall-kedun-20260917','mall-kedun-20260917',b'0',2009)
ON DUPLICATE KEY UPDATE subject=VALUES(subject),body=VALUES(body),price=VALUES(price),status=20,success_time=VALUES(success_time),
 updater='mall-kedun-20260917',update_time=NOW(),deleted=b'0';

INSERT INTO trade_order
 (id,no,type,terminal,user_id,user_ip,user_remark,status,product_count,remark,comment_status,pay_order_id,pay_status,pay_time,pay_channel_code,
 finish_time,total_price,discount_price,delivery_price,adjust_price,pay_price,delivery_type,logistics_id,logistics_no,delivery_time,receive_time,
 receiver_name,receiver_mobile,receiver_area_id,receiver_detail_address,refund_status,refund_price,coupon_price,use_point,point_price,give_point,
 refund_point,vip_price,creator,updater,deleted,tenant_id,shop_id,shop_name,seller_user_id,pay_expire_time,pay_expire_seconds)
VALUES
 (940701,'KD202608010001',0,20,940001,'127.0.0.1','演示历史订单',30,1,'已完成',b'1',940601,b'1','2026-08-01 10:05:00','mock','2026-08-05 16:00:00',39800,0,0,0,39800,1,NULL,'KD-DEMO-001','2026-08-02 09:00:00','2026-08-05 16:00:00','张工','13900002201',610113,'高新区科盾测试地址',0,0,0,0,0,398,0,0,'mall-kedun-20260917','mall-kedun-20260917',b'0',2009,940501,'科盾工业防护旗舰店',840001,'2026-08-01 10:30:00',1800),
 (940702,'KD202608060001',0,20,940002,'127.0.0.1','演示历史订单',30,1,'已完成',b'1',940602,b'1','2026-08-06 11:08:00','mock','2026-08-10 14:00:00',32800,0,0,0,32800,1,NULL,'KD-DEMO-002','2026-08-07 09:30:00','2026-08-10 14:00:00','李工','13900002202',610404,'秦都区测试地址',0,0,0,0,0,328,0,0,'mall-kedun-20260917','mall-kedun-20260917',b'0',2009,940502,'秦盾防腐材料店',840002,'2026-08-06 11:30:00',1800),
 (940703,'KD202608120001',0,20,940003,'127.0.0.1','演示历史订单',30,2,'已完成',b'1',940603,b'1','2026-08-12 09:12:00','mock','2026-08-16 17:00:00',17200,0,0,0,17200,1,NULL,'KD-DEMO-003','2026-08-13 10:00:00','2026-08-16 17:00:00','周工','13900002203',610115,'临潼区测试地址',0,0,0,0,0,172,0,0,'mall-kedun-20260917','mall-kedun-20260917',b'0',2009,940503,'恒温保温材料店',840003,'2026-08-12 09:30:00',1800),
 (940704,'KD202608200001',0,20,940001,'127.0.0.1','演示历史订单',20,1,'已发货',b'0',940604,b'1','2026-08-20 15:03:00','mock',NULL,28600,0,0,0,28600,1,NULL,'KD-DEMO-004','2026-08-21 08:40:00',NULL,'张工','13900002201',610502,'临渭区测试地址',0,0,0,0,0,286,0,0,'mall-kedun-20260917','mall-kedun-20260917',b'0',2009,940504,'华秦管道防护店',840004,'2026-08-20 15:30:00',1800),
 (940705,'KD202608260001',0,20,940002,'127.0.0.1','演示历史订单',30,1,'已完成',b'1',940605,b'1','2026-08-26 14:16:00','mock','2026-08-30 11:00:00',23800,0,0,0,23800,1,NULL,'KD-DEMO-005','2026-08-27 10:20:00','2026-08-30 11:00:00','李工','13900002202',610302,'宝鸡高新区测试地址',0,0,0,0,0,238,0,0,'mall-kedun-20260917','mall-kedun-20260917',b'0',2009,940505,'陕能耐火保温店',840005,'2026-08-26 14:30:00',1800)
ON DUPLICATE KEY UPDATE status=VALUES(status),pay_order_id=VALUES(pay_order_id),pay_status=b'1',pay_time=VALUES(pay_time),
 pay_channel_code='mock',finish_time=VALUES(finish_time),updater='mall-kedun-20260917',update_time=NOW(),deleted=b'0';

INSERT INTO trade_order_item
 (id,user_id,order_id,spu_id,spu_name,sku_id,properties,pic_url,count,comment_status,price,discount_price,delivery_price,
  adjust_price,pay_price,coupon_price,point_price,use_point,give_point,vip_price,after_sale_status,creator,updater,deleted,tenant_id,shop_id,shop_name)
VALUES
 (940801,940001,940701,940201,'环氧富锌底漆 20kg',940301,'[]','',1,b'1',39800,0,0,0,39800,0,0,0,398,0,0,'mall-kedun-20260917','mall-kedun-20260917',b'0',2009,940501,'科盾工业防护旗舰店'),
 (940802,940002,940702,940203,'环氧云铁中间漆 20kg',940303,'[]','',1,b'1',32800,0,0,0,32800,0,0,0,328,0,0,'mall-kedun-20260917','mall-kedun-20260917',b'0',2009,940502,'秦盾防腐材料店'),
 (940803,940003,940703,940205,'憎水岩棉板 100mm',940305,'[]','',2,b'1',8600,0,0,0,17200,0,0,0,172,0,0,'mall-kedun-20260917','mall-kedun-20260917',b'0',2009,940503,'恒温保温材料店'),
 (940804,940001,940704,940207,'预制直埋保温管 DN100',940307,'[]','',1,b'0',28600,0,0,0,28600,0,0,0,286,0,0,'mall-kedun-20260917','mall-kedun-20260917',b'0',2009,940504,'华秦管道防护店'),
 (940805,940002,940705,940210,'耐火纤维模块 300mm',940310,'[]','',1,b'1',23800,0,0,0,23800,0,0,0,238,0,0,'mall-kedun-20260917','mall-kedun-20260917',b'0',2009,940505,'陕能耐火保温店')
ON DUPLICATE KEY UPDATE count=VALUES(count),comment_status=VALUES(comment_status),pay_price=VALUES(pay_price),
 updater='mall-kedun-20260917',update_time=NOW(),deleted=b'0';

INSERT INTO product_comment
 (id,user_id,user_nickname,user_avatar,anonymous,order_id,order_item_id,spu_id,spu_name,sku_id,sku_pic_url,sku_properties,
  visible,scores,description_scores,benefit_scores,content,pic_urls,reply_status,reply_user_id,reply_content,reply_time,creator,updater,deleted,tenant_id)
VALUES
 (940901,940001,'科盾采购测试','',b'0',940701,940801,940201,'环氧富锌底漆 20kg',940301,'','默认',b'1',5,5,5,'包装完整，批次清晰，配套施工说明很实用。','[]',b'1',840001,'感谢认可，如需配套面漆可继续咨询。','2026-08-06 10:00:00','mall-kedun-20260917','mall-kedun-20260917',b'0',2009),
 (940902,940002,'秦盾采购测试','',b'0',940702,940802,940203,'环氧云铁中间漆 20kg',940303,'','默认',b'1',5,5,4,'漆膜效果符合项目要求，发货及时。','[]',b'1',840002,'感谢您的评价。','2026-08-11 09:00:00','mall-kedun-20260917','mall-kedun-20260917',b'0',2009),
 (940903,940003,'工程采购测试','',b'0',940703,940803,940205,'憎水岩棉板 100mm',940305,'','默认',b'1',4,5,4,'密度均匀，尺寸误差在可接受范围。','[]',b'1',840003,'后续批次将继续加强包装。','2026-08-17 14:00:00','mall-kedun-20260917','mall-kedun-20260917',b'0',2009),
 (940904,940002,'秦盾采购测试','',b'1',940705,940805,940210,'耐火纤维模块 300mm',940310,'','默认',b'1',5,5,5,'模块规整，安装配合度高。','[]',b'1',840005,'感谢支持。','2026-08-31 09:30:00','mall-kedun-20260917','mall-kedun-20260917',b'0',2009)
ON DUPLICATE KEY UPDATE visible=b'1',scores=VALUES(scores),content=VALUES(content),reply_status=VALUES(reply_status),
 reply_content=VALUES(reply_content),updater='mall-kedun-20260917',update_time=NOW(),deleted=b'0';

-- 补齐本批新增商城数据的展示图片。复用已导入文件中心的防腐保温实物图，
-- 统一走网关文件接口，PC 与 H5 使用同一 URL；企业资质证照不使用演示图片伪造。
DROP TEMPORARY TABLE IF EXISTS tmp_kedun_spu_image;
CREATE TEMPORARY TABLE tmp_kedun_spu_image (
  spu_id BIGINT PRIMARY KEY,
  source_spu_id BIGINT NOT NULL
);
INSERT INTO tmp_kedun_spu_image(spu_id,source_spu_id) VALUES
 (940201,910001),(940202,910003),(940203,910002),(940204,910007),(940205,910009),
 (940206,910012),(940207,910012),(940208,910004),(940209,910010),(940210,910010),
 (940211,910001),(940212,910001),(940213,910001),(940214,910001),(940215,910003),
 (940216,910003),(940217,910001),(940218,910007),(940219,910008),(940220,910003),
 (940221,910009),(940222,910009),(940223,910010),(940224,910012),(940225,910011),
 (940226,910011),(940227,910017),(940228,910016),(940229,910014),(940230,910025),
 (940231,910012),(940232,910012),(940233,910004),(940234,910020),(940235,910010);

UPDATE product_spu target
JOIN tmp_kedun_spu_image image_map ON image_map.spu_id=target.id
JOIN product_spu source ON source.id=image_map.source_spu_id AND source.tenant_id=1 AND source.deleted=b'0'
SET target.pic_url=source.pic_url,
    target.slider_pic_urls=JSON_ARRAY(source.pic_url),
    target.updater='mall-kedun-20260917',target.update_time=NOW()
WHERE target.tenant_id=2009 AND target.deleted=b'0' AND source.pic_url IS NOT NULL AND source.pic_url<>''
  AND target.pic_url NOT LIKE '%/mall/catalog/20260917/kedun/%' AND target.id<>940215;

UPDATE product_sku sku
JOIN product_spu spu ON spu.id=sku.spu_id AND spu.tenant_id=2009 AND spu.deleted=b'0'
SET sku.pic_url=spu.pic_url,sku.updater='mall-kedun-20260917',sku.update_time=NOW()
WHERE sku.tenant_id=2009 AND sku.spu_id BETWEEN 940201 AND 940235 AND sku.deleted=b'0';

UPDATE product_brand brand
JOIN product_spu source ON source.id=CASE brand.id
  WHEN 940101 THEN 910001 WHEN 940102 THEN 910007 WHEN 940103 THEN 910009
  WHEN 940104 THEN 910012 WHEN 940105 THEN 910010 END AND source.tenant_id=1 AND source.deleted=b'0'
SET brand.pic_url=source.pic_url,brand.updater='mall-kedun-20260917',brand.update_time=NOW()
WHERE brand.tenant_id=2009 AND brand.id BETWEEN 940101 AND 940105 AND brand.deleted=b'0'
  AND brand.pic_url NOT LIKE '%/mall/catalog/20260917/%';

UPDATE product_category category
JOIN product_spu source ON source.id=CASE category.id
  WHEN 940001 THEN 910001 WHEN 940002 THEN 910002 WHEN 940003 THEN 910007
  WHEN 940004 THEN 910009 WHEN 940005 THEN 910012 WHEN 940006 THEN 910010 END
  AND source.tenant_id=1 AND source.deleted=b'0'
SET category.pic_url=source.pic_url,category.updater='mall-kedun-20260917',category.update_time=NOW()
WHERE category.tenant_id=2009 AND category.id BETWEEN 940001 AND 940006 AND category.deleted=b'0'
  AND category.pic_url NOT LIKE '%/mall/catalog/20260917/%';

UPDATE product_shop shop
JOIN product_spu source ON source.id=CASE shop.id
  WHEN 940501 THEN 910001 WHEN 940502 THEN 910007 WHEN 940503 THEN 910009
  WHEN 940504 THEN 910012 WHEN 940505 THEN 910010 END AND source.tenant_id=1 AND source.deleted=b'0'
SET shop.logo_url=source.pic_url,shop.updater='mall-kedun-20260917',shop.update_time=NOW()
WHERE shop.tenant_id=2009 AND shop.id BETWEEN 940501 AND 940505 AND shop.deleted=b'0';

-- 详情正文展示工程应用说明和实物大图；轮播同时展示主图与同分类参考图。
UPDATE product_spu spu
JOIN product_category category ON category.id=spu.category_id AND category.tenant_id=2009 AND category.deleted=b'0'
SET spu.slider_pic_urls=JSON_ARRAY(spu.pic_url,category.pic_url),
    spu.description=CONCAT(
      '<section style="line-height:1.8;color:#24322f">',
      '<h2>',spu.name,'</h2>',
      '<p>',spu.introduction,'。适用于防腐保温工程采购，具体用量、施工条件及配套体系请结合项目设计确认。</p>',
      '<img src="',spu.pic_url,'" alt="',spu.name,'" style="display:block;width:100%;height:auto;margin:16px 0"/>',
      '<h3>采购与验收提示</h3>',
      '<p>下单前请核对规格、数量和收货条件；到货后按包装标识、外观及随货资料进行验收。</p>',
      '</section>'),
    spu.updater='mall-kedun-20260917',spu.update_time=NOW()
WHERE spu.tenant_id=2009 AND spu.id BETWEEN 940201 AND 940235 AND spu.deleted=b'0'
  AND spu.pic_url IS NOT NULL AND spu.pic_url<>'';

-- 典型商品启用多规格：涂料按包装、板材按厚度、保温管按公称直径选购。
INSERT INTO product_property(id,name,status,remark,creator,updater,tenant_id,deleted) VALUES
 (944001,'包装规格',0,'科盾商城商品规格','mall-kedun-20260917','mall-kedun-20260917',2009,b'0'),
 (944002,'厚度',0,'科盾商城商品规格','mall-kedun-20260917','mall-kedun-20260917',2009,b'0'),
 (944003,'公称直径',0,'科盾商城商品规格','mall-kedun-20260917','mall-kedun-20260917',2009,b'0')
ON DUPLICATE KEY UPDATE name=VALUES(name),status=0,remark=VALUES(remark),
 updater='mall-kedun-20260917',update_time=NOW(),deleted=b'0';

INSERT INTO product_property_value(id,property_id,name,remark,creator,updater,tenant_id,deleted) VALUES
 (944101,944001,'10kg/组','涂料包装','mall-kedun-20260917','mall-kedun-20260917',2009,b'0'),
 (944102,944001,'20kg/组','涂料包装','mall-kedun-20260917','mall-kedun-20260917',2009,b'0'),
 (944201,944002,'50mm','保温层厚度','mall-kedun-20260917','mall-kedun-20260917',2009,b'0'),
 (944202,944002,'75mm','保温层厚度','mall-kedun-20260917','mall-kedun-20260917',2009,b'0'),
 (944203,944002,'100mm','保温层厚度','mall-kedun-20260917','mall-kedun-20260917',2009,b'0'),
 (944301,944003,'DN100','管道公称直径','mall-kedun-20260917','mall-kedun-20260917',2009,b'0'),
 (944302,944003,'DN150','管道公称直径','mall-kedun-20260917','mall-kedun-20260917',2009,b'0'),
 (944303,944003,'DN200','管道公称直径','mall-kedun-20260917','mall-kedun-20260917',2009,b'0')
ON DUPLICATE KEY UPDATE property_id=VALUES(property_id),name=VALUES(name),remark=VALUES(remark),
 updater='mall-kedun-20260917',update_time=NOW(),deleted=b'0';

UPDATE product_spu SET name='环氧富锌底漆',keyword='环氧富锌 底漆',spec_type=b'1',price=21800,market_price=23800,cost_price=16800,stock=300,
 updater='mall-kedun-20260917',update_time=NOW()
WHERE tenant_id=2009 AND id=940201 AND deleted=b'0';
UPDATE product_sku SET properties=JSON_ARRAY(JSON_OBJECT('propertyId',944001,'propertyName','包装规格','valueId',944102,'valueName','20kg/组')),
 price=39800,market_price=42800,cost_price=31000,stock=200,bar_code='KD2009-940201-20KG',
 updater='mall-kedun-20260917',update_time=NOW()
WHERE tenant_id=2009 AND id=940301 AND spu_id=940201 AND deleted=b'0';

UPDATE product_spu SET name='憎水岩棉板',keyword='岩棉板 保温',spec_type=b'1',price=5200,market_price=6200,cost_price=3900,stock=500,
 updater='mall-kedun-20260917',update_time=NOW()
WHERE tenant_id=2009 AND id=940205 AND deleted=b'0';
UPDATE product_sku SET properties=JSON_ARRAY(JSON_OBJECT('propertyId',944002,'propertyName','厚度','valueId',944203,'valueName','100mm')),
 price=8600,market_price=9800,cost_price=6500,stock=200,bar_code='KD2009-940205-100MM',
 updater='mall-kedun-20260917',update_time=NOW()
WHERE tenant_id=2009 AND id=940305 AND spu_id=940205 AND deleted=b'0';

UPDATE product_spu SET name='聚氨酯直埋保温管',keyword='直埋保温管 聚氨酯',spec_type=b'1',
 price=32800,market_price=35800,cost_price=25800,stock=90,
 updater='mall-kedun-20260917',update_time=NOW()
WHERE tenant_id=2009 AND id=940231 AND deleted=b'0';
UPDATE product_sku SET properties=JSON_ARRAY(JSON_OBJECT('propertyId',944003,'propertyName','公称直径','valueId',944303,'valueName','DN200')),
 price=52800,market_price=55800,cost_price=41800,stock=30,bar_code='KD2009-940231-DN200',
 updater='mall-kedun-20260917',update_time=NOW()
WHERE tenant_id=2009 AND id=940331 AND spu_id=940231 AND deleted=b'0';

INSERT INTO product_sku
 (id,spu_id,properties,price,market_price,cost_price,bar_code,pic_url,stock,weight,volume,sales_count,creator,updater,deleted,tenant_id)
SELECT 945001,940201,JSON_ARRAY(JSON_OBJECT('propertyId',944001,'propertyName','包装规格','valueId',944101,'valueName','10kg/组')),
 21800,23800,16800,'KD2009-940201-10KG',pic_url,100,10,0.02,31,'mall-kedun-20260917','mall-kedun-20260917',b'0',2009
FROM product_spu WHERE tenant_id=2009 AND id=940201 AND deleted=b'0'
UNION ALL
SELECT 945002,940205,JSON_ARRAY(JSON_OBJECT('propertyId',944002,'propertyName','厚度','valueId',944201,'valueName','50mm')),
 5200,6200,3900,'KD2009-940205-50MM',pic_url,150,8,0.08,46,'mall-kedun-20260917','mall-kedun-20260917',b'0',2009
FROM product_spu WHERE tenant_id=2009 AND id=940205 AND deleted=b'0'
UNION ALL
SELECT 945003,940205,JSON_ARRAY(JSON_OBJECT('propertyId',944002,'propertyName','厚度','valueId',944202,'valueName','75mm')),
 6900,7900,5200,'KD2009-940205-75MM',pic_url,150,12,0.12,39,'mall-kedun-20260917','mall-kedun-20260917',b'0',2009
FROM product_spu WHERE tenant_id=2009 AND id=940205 AND deleted=b'0'
UNION ALL
SELECT 945004,940231,JSON_ARRAY(JSON_OBJECT('propertyId',944003,'propertyName','公称直径','valueId',944301,'valueName','DN100')),
 32800,35800,25800,'KD2009-940231-DN100',pic_url,30,35,0.18,12,'mall-kedun-20260917','mall-kedun-20260917',b'0',2009
FROM product_spu WHERE tenant_id=2009 AND id=940231 AND deleted=b'0'
UNION ALL
SELECT 945005,940231,JSON_ARRAY(JSON_OBJECT('propertyId',944003,'propertyName','公称直径','valueId',944302,'valueName','DN150')),
 42800,45800,33800,'KD2009-940231-DN150',pic_url,30,52,0.25,9,'mall-kedun-20260917','mall-kedun-20260917',b'0',2009
FROM product_spu WHERE tenant_id=2009 AND id=940231 AND deleted=b'0'
ON DUPLICATE KEY UPDATE properties=VALUES(properties),price=VALUES(price),market_price=VALUES(market_price),
 cost_price=VALUES(cost_price),bar_code=VALUES(bar_code),pic_url=VALUES(pic_url),stock=VALUES(stock),
 weight=VALUES(weight),volume=VALUES(volume),sales_count=VALUES(sales_count),
 updater='mall-kedun-20260917',update_time=NOW(),deleted=b'0';

-- 多规格商品去掉名称中的单一规格，并同步刷新详情标题。
UPDATE product_spu
SET description=CONCAT(
      '<section style="line-height:1.8;color:#24322f">',
      '<h2>',name,'</h2>',
      '<p>',introduction,'。本商品提供多种规格，请按工程设计选择后下单。</p>',
      '<img src="',pic_url,'" alt="',name,'" style="display:block;width:100%;height:auto;margin:16px 0"/>',
      '<h3>采购与验收提示</h3>',
      '<p>下单前请核对规格、数量和收货条件；到货后按包装标识、外观及随货资料进行验收。</p>',
      '</section>'),
    updater='mall-kedun-20260917',update_time=NOW()
WHERE tenant_id=2009 AND id IN (940201,940205,940231) AND deleted=b'0';

UPDATE system_users
SET avatar='http://127.0.0.1:48080/admin-api/infra/file/4/get/20260905/1.png',
    updater='mall-kedun-20260917',update_time=NOW()
WHERE tenant_id=2009 AND id IN (840001,840002,840003,840004,840005,840011,840012,840013) AND deleted=b'0';

UPDATE member_user
SET avatar='http://127.0.0.1:48080/admin-api/infra/file/4/get/20260905/1.png',
    updater='mall-kedun-20260917',update_time=NOW()
WHERE tenant_id=2009 AND id BETWEEN 940001 AND 940003 AND deleted=b'0';

UPDATE trade_order orders
JOIN product_shop shop ON shop.id=orders.shop_id AND shop.tenant_id=2009 AND shop.deleted=b'0'
SET orders.shop_logo_url=shop.logo_url,orders.updater='mall-kedun-20260917',orders.update_time=NOW()
WHERE orders.tenant_id=2009 AND orders.id BETWEEN 940701 AND 940705 AND orders.deleted=b'0';

UPDATE trade_order_item item
JOIN product_spu spu ON spu.id=item.spu_id AND spu.tenant_id=2009 AND spu.deleted=b'0'
SET item.pic_url=spu.pic_url,item.updater='mall-kedun-20260917',item.update_time=NOW()
WHERE item.tenant_id=2009 AND item.id BETWEEN 940801 AND 940805 AND item.deleted=b'0';

UPDATE product_comment comment
JOIN product_spu spu ON spu.id=comment.spu_id AND spu.tenant_id=2009 AND spu.deleted=b'0'
JOIN product_sku sku ON sku.id=comment.sku_id AND sku.tenant_id=2009 AND sku.deleted=b'0'
JOIN member_user member ON member.id=comment.user_id AND member.tenant_id=2009 AND member.deleted=b'0'
SET comment.user_avatar=member.avatar,comment.sku_pic_url=spu.pic_url,
    comment.sku_properties=sku.properties,comment.pic_urls=JSON_ARRAY(spu.pic_url),
    comment.updater='mall-kedun-20260917',comment.update_time=NOW()
WHERE comment.tenant_id=2009 AND comment.id BETWEEN 940901 AND 940904 AND comment.deleted=b'0';

-- 本地模拟 ERP 独立库存账本；只初始化新 SKU，不重置已发生预占或出库的记录。
INSERT IGNORE INTO trade_erp_mock_stock (tenant_id,sku_id,initial_quantity,quantity,reserved)
SELECT sku.tenant_id,sku.id,sku.stock,sku.stock,0
FROM product_sku sku
WHERE sku.tenant_id=2009 AND sku.deleted=b'0';

DROP TEMPORARY TABLE tmp_kedun_spu_image;

COMMIT;

SELECT 'enterprise' item,COUNT(*) count FROM product_enterprise WHERE tenant_id=2009 AND id BETWEEN 940401 AND 940405 AND deleted=b'0'
UNION ALL SELECT 'shop',COUNT(*) FROM product_shop WHERE tenant_id=2009 AND id BETWEEN 940501 AND 940505 AND deleted=b'0'
UNION ALL SELECT 'spu',COUNT(*) FROM product_spu WHERE tenant_id=2009 AND id BETWEEN 940201 AND 940235 AND deleted=b'0'
UNION ALL SELECT 'member',COUNT(*) FROM member_user WHERE tenant_id=2009 AND id BETWEEN 940001 AND 940003 AND deleted=b'0'
UNION ALL SELECT 'paid-history',COUNT(*) FROM pay_order WHERE tenant_id=2009 AND id BETWEEN 940601 AND 940605 AND status=20 AND deleted=b'0';
