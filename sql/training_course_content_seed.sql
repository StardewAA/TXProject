-- ======================================================================
-- 云培训学院 - 112 门课程详情内容导入（课程概述 / 适合学习人员 / 授课目标）
-- 数据库：ruoyi-vue-pro
-- 字段映射（基于真实源码 CourseDO / CourseRespVO / detail.vue）：
--   课程概述     → training_course.summary         (text)
--   适合学习人员 → training_course.applicable_posts (JSON 数组字符串，详情页按"、"连接展示)
--   授课目标     → training_course.objectives       (text)
-- 特性：
--   1. 仅按 code 精确 UPDATE 三字段 + updater/update_time，不触碰其他字段；
--   2. 幂等：重复执行结果一致；
--   3. 不修改 training_course_category，不删除已有数据；
--   4. 内容按每门课程名称/所属专业方向（一级域/二级组）分别撰写，无互相复制。
-- ======================================================================
START TRANSACTION;

-- ========== A 域：通用基础与安全（13 门） ==========

UPDATE training_course SET
  summary = '介绍公司发展历程、组织架构与业务布局，系统讲解防腐保温、内衬修复、检测试验、材料贸易等主要产品线与服务范围，帮助新员工建立对公司业务的整体认知。',
  applicable_posts = '["新入职员工","转岗人员","职能管理人员"]',
  objectives = '了解公司发展沿革与组织架构，能够说出各产品线的业务范围与典型应用场景，明确本岗位在公司业务链条中的位置与协作关系。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'A0101';

UPDATE training_course SET
  summary = '讲解公司各序列岗位的职责边界、考勤纪律、保密义务与职业行为准则，结合防腐保温施工现场典型案例说明行为规范要求。',
  applicable_posts = '["新入职员工","全员必修"]',
  objectives = '掌握本岗位职责与汇报关系，知晓职业行为红线与保密要求，能够按规范开展日常工作并正确处理现场行为准则问题。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'A0102';

UPDATE training_course SET
  summary = '讲解石油化工与防腐保温施工现场的主要危险源、HSE 管理制度、动火、高处、受限空间等作业环节的通用安全要求，以及个人安全责任。',
  applicable_posts = '["新入职员工","现场作业人员","班组长","安全管理人员"]',
  objectives = '能够识别现场常见危险源与违章行为，掌握 HSE 基本制度与报告流程，具备进入石油化工现场作业的安全意识与自我防护能力。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'A0201';

UPDATE training_course SET
  summary = '讲解安全帽、防护服、呼吸防护、眼面部及坠落防护等劳保用品的选型、佩戴方法、检查维护与报废标准，结合喷涂、喷砂等作业场景示范正确使用。',
  applicable_posts = '["现场作业人员","喷涂工","喷砂工","安全管理人员"]',
  objectives = '能够按作业岗位正确选用和佩戴劳保用品，会进行使用前检查与日常维护，杜绝错用、漏用和超期使用防护用品。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'A0202';

UPDATE training_course SET
  summary = '讲解喷砂粉尘、涂料溶剂等职业危害因素对健康的影响、职业接触限值、工程与个体防护措施以及职业健康检查要求。',
  applicable_posts = '["喷砂工","喷涂工","配料工","职业健康管理人员"]',
  objectives = '了解粉尘与溶剂的职业危害途径及限值标准，掌握工程防护与个体防护措施，知晓职业健康检查及职业病防护的权利与义务。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'A0203';

UPDATE training_course SET
  summary = '讲解储罐、塔罐等受限空间作业的辨识标准、许可流程、气体检测、通风置换、监护与应急救援要求，结合罐内防腐作业场景说明控制要点。',
  applicable_posts = '["罐内防腐作业人员","受限空间监护人","作业负责人","安全管理人员"]',
  objectives = '能够辨识受限空间作业风险，掌握作业许可审批与气体检测要求，会执行通风、监护与应急措施，确保罐内防腐等作业合规安全。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'A0301';

UPDATE training_course SET
  summary = '讲解高处作业分级与坠落防护、脚手架搭设验收、安全带使用与生命线设置要求，结合储罐外壁保温、管架防腐等作业场景说明控制要点。',
  applicable_posts = '["高处作业人员","脚手架搭设人员","班组长","安全管理人员"]',
  objectives = '掌握高处作业分级与防护要求，会检查和正确使用安全带与锚点，能够识别脚手架隐患，规范开展罐壁、管架等高处防腐保温作业。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'A0302';

UPDATE training_course SET
  summary = '讲解动火作业分级审批、可燃气检测、防火隔离、监火要求及易燃涂料溶剂的存放使用规定，结合施工现场动火场景说明管控流程。',
  applicable_posts = '["动火作业人员","监火人","作业负责人","安全管理人员"]',
  objectives = '掌握动火许可办理流程与安全距离要求，会进行动火前可燃气检测与隔离措施，能够落实监火与灭火配置，防范施工现场火灾爆炸事故。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'A0303';

UPDATE training_course SET
  summary = '讲解施工现场临时用电的配电箱设置、接地接零、漏电保护要求，以及喷砂机、喷涂机等常用工机具的检查与安全使用方法。',
  applicable_posts = '["电工","设备操作人员","现场作业人员","安全管理人员"]',
  objectives = '掌握三级配电两级保护要求，会检查使用电动工机具与电缆线路，能够识别并纠正施工现场临时用电隐患。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'A0304';

UPDATE training_course SET
  summary = '讲解施工现场火灾、中毒窒息、高坠、泄漏等突发事件的应急处置流程、报警程序、逃生路线与现场急救常识。',
  applicable_posts = '["全员必修","应急队员","班组长"]',
  objectives = '熟知本岗位应急处置卡与逃生路线，会正确报警与开展初期处置，掌握心肺复苏等基本急救技能，提高突发事件自救互救能力。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'A0401';

UPDATE training_course SET
  summary = '剖析石油化工与防腐保温行业受限空间中毒、高处坠落、火灾爆炸等典型事故的经过、原因与教训，讲解同类作业的风险防范对策。',
  applicable_posts = '["全员必修","班组长","安全管理人员"]',
  objectives = '能够从事故案例中识别管理漏洞与违章成因，掌握事故致因分析方法，将防范措施落实到本岗位作业环节。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'A0402';

UPDATE training_course SET
  summary = '讲解公司质量管理体系框架、防腐保温工程全过程质量控制要求，以及自检、互检、专检"三检制"的执行要点与记录要求。',
  applicable_posts = '["现场作业人员","质检员","班组长","施工管理人员"]',
  objectives = '理解质量体系基本要求与三检制流程，能够按规定开展工序自检互检并填写记录，明确质量控制的关键停止点。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'A0501';

UPDATE training_course SET
  summary = '讲解防腐保温工程施工过程中除锈等级、涂层厚度、隐蔽工程等环节的资料填写、影像留证要求与归档标准。',
  applicable_posts = '["质检员","施工技术人员","资料员","班组长"]',
  objectives = '掌握各工序留证节点与拍摄要求，能够规范填写施工记录并整理影像资料，保证工程验收资料完整可追溯。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'A0502';

-- ========== B 域：防腐涂装（30 门） ==========

UPDATE training_course SET
  summary = '讲解 GB/T 8923 标准中 Sa、St 系列除锈等级的定义、适用范围与目视判定方法，结合标准照片对比练习表面预处理质量验收。',
  applicable_posts = '["喷砂工","质检员","监理对接人员","防腐施工人员"]',
  objectives = '熟记 Sa2.5、St3 等常用等级的判定特征，能够目视评定钢材表面除锈质量并出具判定结论，掌握等级不符时的处理要求。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'B0101';

UPDATE training_course SET
  summary = '讲解干喷砂与抛丸设备的结构原理、磨料选择、压力参数调节、日常保养与常见故障处理，以及作业粉尘控制要求。',
  applicable_posts = '["喷砂工","设备维护人员","防腐施工人员"]',
  objectives = '掌握喷砂、抛丸设备的开机、参数调节与停机操作，能够进行日常保养和常见故障排查，按规程完成金属表面处理作业。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'B0102';

UPDATE training_course SET
  summary = '讲解高压水射流除锈的原理、设备组成、压力与流量参数、安全防护要求，以及与喷砂工艺的适用场景对比。',
  applicable_posts = '["水射流作业人员","防腐施工人员","设备操作人员"]',
  objectives = '了解高压水射流除锈工艺特点与适用范围，掌握设备操作参数与安全距离要求，能够安全完成水射流表面处理作业。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'B0103';

UPDATE training_course SET
  summary = '讲解表面粗糙度的评定参数与测量方法、灰尘与可溶性盐等清洁度检测手段，及其对涂层附着力的影响。',
  applicable_posts = '["质检员","实验室人员","防腐技术人员"]',
  objectives = '掌握粗糙度仪与清洁度检测方法，能够判定表面处理是否满足涂料体系要求，会记录并反馈检测数据。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'B0104';

UPDATE training_course SET
  summary = '系统讲解环氧、聚氨酯、无机富锌等主要涂料体系的组成、固化机理、性能特点与典型应用场景，建立涂料选型的知识框架。',
  applicable_posts = '["防腐技术人员","配料工","喷涂工","材料管理人员"]',
  objectives = '能够区分常用涂料体系的性能与适用工况，理解底漆、中间漆、面漆的配套逻辑，具备初步的涂料体系选型判断能力。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'B0201';

UPDATE training_course SET
  summary = '讲解双组分涂料的配比要求、混合搅拌、熟化与稀释操作，以及适用期控制对施工质量的影响。',
  applicable_posts = '["配料工","喷涂工","防腐技术人员"]',
  objectives = '掌握双组分涂料调配流程与配比换算，会控制熟化时间与适用期，避免固化不良、结胶等质量事故。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'B0202';

UPDATE training_course SET
  summary = '讲解环境温度、相对湿度、钢板温度与露点的关系，以及涂装作业的环境窗口判定标准与雨雪大风天气的停工条件。',
  applicable_posts = '["喷涂工","质检员","施工管理人员"]',
  objectives = '会使用温湿度仪与露点计算方法判定涂装环境是否达标，掌握底材温度须高于露点 3℃ 的判据，合理安排涂装作业窗口。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'B0203';

UPDATE training_course SET
  summary = '讲解高压无气喷涂机的结构原理、喷嘴选型、压力参数、喷涂行走速度与搭接手法，以及喷涂作业的安全防护要求。',
  applicable_posts = '["喷涂工","防腐施工人员","设备维护人员"]',
  objectives = '熟练操作高压无气喷涂设备，掌握喷嘴选型与喷涂手法，能够获得均匀无缺陷的涂层，并落实喷涂作业安全措施。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'B0301';

UPDATE training_course SET
  summary = '讲解刷涂、滚涂的适用场景与操作手法，以及焊缝、边角、螺栓、管束背后等复杂部位的预涂与处理技巧。',
  applicable_posts = '["防腐施工人员","喷涂工","补涂人员"]',
  objectives = '掌握刷涂滚涂手法与膜厚控制，能够按规范对焊缝及复杂部位进行预涂和补涂，保证整体涂层质量均匀。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'B0302';

UPDATE training_course SET
  summary = '讲解湿膜梳与干膜测厚仪的使用方法、测点布置原则、85-15 与 90-10 验收规则，以及膜厚不合格的返修处理。',
  applicable_posts = '["质检员","喷涂工","防腐技术人员"]',
  objectives = '会使用湿膜梳控制施工过程膜厚，能够规范测厚并按验收规则判定合格性，掌握局部超厚或膜厚不足的返修要求。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'B0303';

UPDATE training_course SET
  summary = '讲解涂层针孔与漏点的成因、电火花检漏仪的电压选取原则、检测操作方法与漏点修补要求。',
  applicable_posts = '["质检员","防腐施工人员","实验室人员"]',
  objectives = '能够根据涂层总厚度选取检测电压，规范操作电火花检漏仪，会标记漏点并按工艺要求完成修补与复检。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'B0304';

UPDATE training_course SET
  summary = '讲解流挂、橘皮、针孔、起泡、开裂、附着力不良等典型涂层缺陷的外观特征、成因分析与返修工艺。',
  applicable_posts = '["质检员","防腐施工人员","补涂人员"]',
  objectives = '能够识别常见涂层缺陷并分析成因，制定针对性返修方案，掌握打磨、补涂等返修操作要点，防止缺陷重复发生。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'B0305';

UPDATE training_course SET
  summary = '讲解净化水罐、污水罐内部介质的腐蚀特性、内防腐涂层与玻璃钢衬里选型，以及罐内施工的通风、防爆与工序控制要点。',
  applicable_posts = '["罐内防腐作业人员","防腐技术人员","施工管理人员"]',
  objectives = '掌握污水介质环境下内防腐体系的施工工艺与质量控制点，能够组织罐内受限空间条件下的防腐作业并满足验收标准。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'B0401';

UPDATE training_course SET
  summary = '讲解原油罐、沉降罐内外壁腐蚀机理、底板与边缘板防腐、罐内导静电涂层要求及内外防腐配套施工方案。',
  applicable_posts = '["储罐防腐作业人员","防腐技术人员","施工管理人员"]',
  objectives = '了解储罐各部位腐蚀特点，掌握罐内外防腐配套体系与施工工序，能够组织实施储罐大修防腐工程。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'B0402';

UPDATE training_course SET
  summary = '讲解塔器、球罐的结构特点、表面处理与涂装难点、脚手架与吊篮作业配合，以及现场组焊后焊道补涂要求。',
  applicable_posts = '["储罐防腐作业人员","防腐技术人员","高处作业人员"]',
  objectives = '掌握塔罐球罐防腐施工组织要点，能够处理现场焊缝补涂与高空作业衔接，保证整体涂层完整并验收合格。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'B0403';

UPDATE training_course SET
  summary = '讲解储罐内衬玻璃钢与陶瓷涂层的材料特性、衬里层次结构、施工工艺、固化控制与质量检验方法。',
  applicable_posts = '["衬里施工人员","防腐技术人员","质检员"]',
  objectives = '掌握玻璃钢、陶瓷衬里的铺贴与固化工艺，能够控制气泡、脱层等衬里缺陷，按标准完成衬里质量检验。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'B0404';

UPDATE training_course SET
  summary = '讲解 3PE 防腐层的环氧粉末、胶粘剂、聚乙烯三层结构与性能，以及作业线抛丸除锈、喷涂、包覆、水冷工序与在线检测要求。',
  applicable_posts = '["防腐作业线操作人员","防腐技术人员","质检员"]',
  objectives = '理解三层结构的协同防腐机理，掌握作业线各工序参数控制要点，能够判定 3PE 层厚度、剥离强度等质量指标。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'B0501';

UPDATE training_course SET
  summary = '讲解熔结环氧粉末涂料的固化特性、静电喷涂工艺、预热温度控制与涂层检测要求及与补口作业的配套。',
  applicable_posts = '["防腐作业线操作人员","防腐技术人员","质检员"]',
  objectives = '掌握 FBE 施工的温度与时间窗口控制，能够执行涂层厚度、针孔与附着力检测，了解单双层 FBE 的应用差异。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'B0502';

UPDATE training_course SET
  summary = '讲解站场地面管线的腐蚀环境、防腐涂层选型、现场涂装施工与管沟回填中的成品保护要求。',
  applicable_posts = '["防腐施工人员","防腐技术人员","施工管理人员"]',
  objectives = '掌握地面管线防腐涂层选型与现场施工工艺，能够组织地面管线防腐施工并落实成品保护与验收。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'B0503';

UPDATE training_course SET
  summary = '讲解井下管柱与注水油管的腐蚀机理、内涂层与涂料油管技术、修复工艺流程及入井前检验要求。',
  applicable_posts = '["油管修复人员","防腐技术人员","质检员"]',
  objectives = '了解井下腐蚀环境与防腐技术路线，掌握注水油管内外防腐工艺流程及检验标准，支持油管修复与再利用。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'B0504';

UPDATE training_course SET
  summary = '讲解弯管、冷换设备管束与加热炉盘管的高温与腐蚀工况、涂层选择、施工与固化工艺及检测要求。',
  applicable_posts = '["防腐施工人员","防腐技术人员","设备维修人员"]',
  objectives = '掌握特殊管件的防腐涂层选型与施工要点，能够按耐温与介质条件组织弯管、盘管防腐作业并通过检验。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'B0505';

UPDATE training_course SET
  summary = '讲解管道补口部位热收缩套、带的结构与选型、表面处理、火焰加热安装手法与补口质量检测方法。',
  applicable_posts = '["补口作业人员","防腐技术人员","质检员"]',
  objectives = '掌握热收缩套补口的烘烤安装手法与温度控制，会进行补口外观、气泡与粘结力检测，保证补口质量与管体一致。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'B0601';

UPDATE training_course SET
  summary = '讲解防腐层运输与施工损伤的评定分级、补伤材料选择、修补工艺及新旧涂层的搭接质量要求。',
  applicable_posts = '["补口补伤人员","质检员","防腐施工人员"]',
  objectives = '能够对防腐层损伤进行分级评定，按工艺完成补伤与搭接处理，确保修补部位密封与附着力满足标准要求。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'B0602';

UPDATE training_course SET
  summary = '讲解管道内涂减阻与内防腐的目的、内涂作业线设备组成、喷涂参数控制与内涂层质量检测要求。',
  applicable_posts = '["内涂作业线操作人员","防腐技术人员","设备维护人员"]',
  objectives = '了解内涂工艺流程与设备原理，掌握内喷涂参数控制与涂层检测方法，保证内涂层均匀与质量合格。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'B0701';

UPDATE training_course SET
  summary = '讲解内涂层的厚度、附着力、针孔检测方法与管内窥检查手段，以及不合格项的判定与处理。',
  applicable_posts = '["质检员","内涂作业线操作人员","实验室人员"]',
  objectives = '掌握内涂层各项指标的检测方法与判定标准，能够使用内窥等手段检查涂层完整性并处置缺陷。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'B0702';

UPDATE training_course SET
  summary = '讲解环氧玻璃钢的材料组成、手糊与缠绕成型工艺、层间质量控制与固化检验，及其在酸碱环境设备防护中的应用。',
  applicable_posts = '["玻璃钢施工人员","防腐技术人员","质检员"]',
  objectives = '掌握环氧玻璃钢衬里与整体成型的施工工艺及配料要求，能够控制含胶量与层间质量，按标准检验固化效果。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'B0801';

UPDATE training_course SET
  summary = '讲解环氧陶瓷与金属陶瓷涂层的耐磨耐蚀机理、适用工况、喷涂与镘涂施工工艺及质量检测方法。',
  applicable_posts = '["防腐施工人员","防腐技术人员","配料工"]',
  objectives = '了解陶瓷类涂层的性能边界与选型依据，掌握其施工工艺与养护要求，能够完成耐磨防腐部位施工与检验。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'B0802';

UPDATE training_course SET
  summary = '讲解出砂磨损工况下油管的耐磨防腐要求、内衬与涂层技术路线、耐磨性能评价方法与检验标准。',
  applicable_posts = '["油管修复人员","防腐技术人员","质检员"]',
  objectives = '掌握耐磨油管防腐的技术方案与工艺要点，能够按检验规程评价涂层耐磨与结合性能，指导耐磨油管的应用。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'B0803';

UPDATE training_course SET
  summary = '讲解井口装置、采油树及站场设备的腐蚀环境、现场防腐涂层配套、法兰与异形部位处理及维护周期。',
  applicable_posts = '["防腐施工人员","设备维修人员","防腐技术人员"]',
  objectives = '能够针对井口设备的异形结构与工况选择涂层体系，掌握现场施工与补涂维护方法，延长设备使用寿命。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'B0804';

UPDATE training_course SET
  summary = '讲解油井结蜡与结垢的机理、化学与涂层防蜡防垢技术、内衬油管应用及现场效果评价方法。',
  applicable_posts = '["防腐技术人员","油管修复人员","生产维护人员"]',
  objectives = '了解蜡垢形成条件与防治技术路线，掌握防蜡防垢涂层与内衬的应用要点，能配合生产进行效果跟踪。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'B0805';

-- ========== C 域：保温绝热与保冷（12 门） ==========

UPDATE training_course SET
  summary = '讲解岩棉、硅酸铝、复合硅酸盐、聚氨酯等常用保温材料的性能指标、适用温度范围与选型原则。',
  applicable_posts = '["保温工","技术员","材料管理人员"]',
  objectives = '掌握各类保温材料的导热系数、密度与使用温度边界，能够按介质温度与工况正确选型，避免材料错用。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'C0101';

UPDATE training_course SET
  summary = '讲解保温层、防潮层、保护层的结构组成与功能，以及按 GB 50264 进行经济厚度与防烫伤厚度计算的方法。',
  applicable_posts = '["技术员","设计配合人员","施工管理人员"]',
  objectives = '理解保温结构各层功能与顺序，掌握保温厚度计算的基本方法与工具使用，会校核防烫与防结露要求。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'C0102';

UPDATE training_course SET
  summary = '讲解管道保温层捆扎、接缝处理、伸缩缝设置与防潮层、保护层安装的工序要求及质量控制点。',
  applicable_posts = '["保温工","管道安装人员","质检员"]',
  objectives = '掌握管道保温的规范工序与捆扎间距要求，能够处理接缝与异形部位，保证保温结构严密牢固。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'C0201';

UPDATE training_course SET
  summary = '讲解大型设备与储罐保温的支撑件设置、保温板块拼砌、销钉固定与外保护层搭接排水构造。',
  applicable_posts = '["保温工","施工管理人员","质检员"]',
  objectives = '掌握储罐保温的支撑与固定方式，能够控制拼缝密度与保护层排水坡度，组织大型设备保温施工。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'C0202';

UPDATE training_course SET
  summary = '讲解金属保护层的放样、卷圆、咬口与搭接工艺，以及弯头、三通等异形件的白铁制作与安装手法。',
  applicable_posts = '["白铁工","保温工","施工班组"]',
  objectives = '掌握保护层放样与机械咬口技能，会制作安装弯头、异形件外护结构，做到表面平整、搭接顺水、外形美观。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'C0203';

UPDATE training_course SET
  summary = '讲解阀门、法兰、人孔等可拆部位的保温盒制作、捆扎与标识要求，兼顾检修拆装便利与保温效果。',
  applicable_posts = '["保温工","白铁工","检修人员"]',
  objectives = '能够制作可拆式保温盒并规范捆扎标识，掌握检修后恢复安装要求，保证异形件部位保温完整。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'C0204';

UPDATE training_course SET
  summary = '讲解深冷与低温工况的保冷机理、聚氨酯与泡沫玻璃保冷材料特性、保冷结构层次与施工工艺。',
  applicable_posts = '["保冷施工人员","技术员","质检员"]',
  objectives = '理解保冷与保温的结构差异，掌握聚氨酯、泡沫玻璃保冷施工要点与防潮隔汽要求，保证保冷工程质量。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'C0301';

UPDATE training_course SET
  summary = '讲解保冷结构中防潮层、隔汽层的作用机理、材料种类与施工工艺，以及接缝与破损处的处理方法。',
  applicable_posts = '["保冷施工人员","保温工","质检员"]',
  objectives = '理解水汽渗透对保冷效果的破坏机理，掌握隔汽层的包覆搭接与密封工艺，防止冷桥与结露。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'C0302';

UPDATE training_course SET
  summary = '讲解 LNG、轻烃等低温装置保冷工程的验收标准、常见质量问题识别与全过程质量控制方法。',
  applicable_posts = '["质检员","保冷施工人员","施工管理人员"]',
  objectives = '掌握低温保冷的质量控制要点与验收标准，能够识别与处置冷桥、缝隙、隔汽破损等质量问题。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'C0303';

UPDATE training_course SET
  summary = '讲解油田与石化装置越冬前的冬防保温范围确定、方案编制、材料选择与实施验收要求。',
  applicable_posts = '["施工管理人员","技术员","运维人员"]',
  objectives = '能够编制冬防保温方案并组织实施，掌握重点部位防冻措施与验收要点，保障装置安全越冬。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'C0401';

UPDATE training_course SET
  summary = '讲解电伴热带与蒸汽伴热的选型、安装、与保温层的配合施工及温控与电气安全要求。',
  applicable_posts = '["保温施工人员","电工","运维人员"]',
  objectives = '了解两种伴热方式的适用场景，掌握伴热管线与保温层配合施工要点，能正确安装与调试伴热系统。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'C0402';

UPDATE training_course SET
  summary = '讲解检修期间保温层的拆除、标识、保管、损坏评估与恢复安装要求，控制材料损耗与施工质量。',
  applicable_posts = '["保温工","检修人员","施工班组"]',
  objectives = '掌握保温拆装作业的规范流程与标识管理，能够评估旧料复用条件，保证恢复后保温效果不低于原状。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'C0403';

-- ========== D 域：内衬与管杆修复（13 门） ==========

UPDATE training_course SET
  summary = '系统介绍非开挖内衬修复的主要技术路线（穿插内衬、折叠内衬、原位固化等）的原理、适用工况与选型比较。',
  applicable_posts = '["内衬技术人员","管道修复人员","施工管理人员"]',
  objectives = '建立内衬修复技术全景认知，能够按管道损伤状况与运行工况进行初步工艺路线选型。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'D0101';

UPDATE training_course SET
  summary = '讲解高分子聚乙烯内衬管的材料特性、穿插工艺、端口处理与质量检验，以及旧管清洗清管的配合要求。',
  applicable_posts = '["内衬施工人员","管道修复人员","质检员"]',
  objectives = '掌握聚乙烯内衬的穿插施工工序与端面处理工艺，能够组织清管与内衬作业并完成质量检验。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'D0102';

UPDATE training_course SET
  summary = '讲解非金属内衬油管的结构、耐温耐压性能、内衬作业工艺与入井使用维护要求。',
  applicable_posts = '["油管修复人员","内衬施工人员","技术员"]',
  objectives = '掌握非金属内衬油管的工艺流程与检验标准，了解其适用井况与失效形式，支持内衬油管推广应用。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'D0103';

UPDATE training_course SET
  summary = '讲解内衬管材的规格系列、性能参数、选型计算与采购验收关键指标，以及供应商技术评估要点。',
  applicable_posts = '["采购人员","技术人员","质量验收人员"]',
  objectives = '掌握内衬管选型参数与验收指标，能够在采购环节把控材质证明与关键性能，降低选型与采购风险。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'D0104';

UPDATE training_course SET
  summary = '讲解废旧油管回收、清洗、检测分级、修复、试压与成品检验的全流程管理及各环节质量控制。',
  applicable_posts = '["油管修复人员","质检员","车间管理人员"]',
  objectives = '掌握油管修复全流程工序与检验标准，能够按分级结果安排修复工艺，保证修复油管再利用质量。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'D0201';

UPDATE training_course SET
  summary = '讲解抽油杆的清洗、探伤、校直、修复与检验工艺，以及杆体损伤评定与报废判据。',
  applicable_posts = '["油杆修复人员","质检员","车间操作人员"]',
  objectives = '掌握油杆校直机操作与校直质量判定，能够按探伤结果分级处置，规范完成油杆修复作业。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'D0202';

UPDATE training_course SET
  summary = '讲解摩擦焊修复油管接头、接箍的原理、焊接参数控制、焊后加工与无损检验要求。',
  applicable_posts = '["摩擦焊操作人员","油管修复人员","质检员"]',
  objectives = '理解摩擦焊工艺特点与参数影响，掌握焊后飞边处理与无损检验要求，保证接头修复质量稳定。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'D0203';

UPDATE training_course SET
  summary = '讲解管道腐蚀与缺陷的检测评定方法、剩余寿命评估思路，以及补强、换管、内衬等修复方案的比选决策。',
  applicable_posts = '["管道工程师","技术员","施工管理人员"]',
  objectives = '掌握缺陷评定基本方法与判据，能够按缺陷等级与运行工况进行修复方案比选，提出经济合理的处置建议。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'D0301';

UPDATE training_course SET
  summary = '讲解钢质补强套筒与碳纤维复合材料补强的适用场景、安装工艺、材料要求与质量检验方法。',
  applicable_posts = '["管道修复人员","施工技术人员","质检员"]',
  objectives = '掌握两类补强工艺的施工要点与适用边界，能够按规范完成补强作业并通过检验。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'D0302';

UPDATE training_course SET
  summary = '讲解注剂式封堵、卡具夹具、粘接堵漏等带压堵漏技术的原理、适用压力介质、操作流程与安全防护。',
  applicable_posts = '["带压堵漏作业人员","维修人员","安全管理人员"]',
  objectives = '掌握常用带压堵漏方法的选用与操作流程，能够评估作业风险并落实防护措施，安全完成不停输堵漏。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'D0303';

UPDATE training_course SET
  summary = '讲解油水井套管损坏类型、检测评价方法、补贴与取换套等修复工艺及施工组织要点。',
  applicable_posts = '["井下作业人员","套管修复人员","技术员"]',
  objectives = '了解套损类型与修复工艺路线，掌握补贴器等工具的使用要求，能够配合完成套管修复施工。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'D0401';

UPDATE training_course SET
  summary = '讲解套筒修复（对开式、整体式）的加工、安装、密封与检验工艺及其在管道与设备上的应用。',
  applicable_posts = '["管道修复人员","机加工人员","质检员"]',
  objectives = '掌握套筒修复的安装与密封工艺要点，能够完成套筒修复施工与压力检验。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'D0402';

UPDATE training_course SET
  summary = '讲解钻头、钻具等井下工具与施工装备的磨损检测、修复工艺、堆焊与机加工要求及修复后检验。',
  applicable_posts = '["装备修复人员","机加工人员","质检员"]',
  objectives = '掌握常见装备磨损件的检测与修复工艺，能够判定修复件可用性，保证修复装备满足使用要求。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'D0403';

-- ========== E 域：检测与理化试验（25 门） ==========

UPDATE training_course SET
  summary = '讲解 UT、MT、PT、RT、涡流等无损检测方法的原理、适用对象与局限性，以及按材料、缺陷类型与结构选用检测方法的决策逻辑。',
  applicable_posts = '["无损检测人员","质检员","技术人员"]',
  objectives = '建立五大常规无损检测方法的全局认知，能够按检测对象与验收要求进行检测方法组合选用。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'E0101';

UPDATE training_course SET
  summary = '讲解超声波检测的物理基础、仪器与探头、耦合与扫查方式、缺陷定位定量与评定标准，并配套实操训练。',
  applicable_posts = '["无损检测人员","检测技术人员"]',
  objectives = '掌握超声检测基本原理与仪器操作，能够完成平板与对接焊缝的检测并按标准评定缺陷。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'E0102';

UPDATE training_course SET
  summary = '讲解磁化方法、磁悬液配制、干湿法工艺、磁痕分析与退磁要求，以及铁磁材料表面和近表面缺陷的检测应用。',
  applicable_posts = '["无损检测人员","质检员"]',
  objectives = '掌握磁粉检测工艺参数选择与操作流程，能够识别与分析磁痕，正确判定缺陷性质并记录。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'E0103';

UPDATE training_course SET
  summary = '讲解渗透剂、显像剂的性能要求，渗透、清洗、显像的工序控制，缺陷显示的识别及非多孔材料的适用范围。',
  applicable_posts = '["无损检测人员","质检员"]',
  objectives = '掌握渗透检测各工序的参数控制，能够规范操作并识别缺陷显示，正确评定与记录检测结果。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'E0104';

UPDATE training_course SET
  summary = '讲解射线透照工艺、像质评定与底片评定基础，以及放射源与射线装置的辐射防护、许可管理与应急预案。',
  applicable_posts = '["无损检测人员","辐射安全管理人员"]',
  objectives = '掌握射线检测工艺与底片评定基础，能够执行辐射安全防护规定与剂量管理，持证规范开展 RT 作业。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'E0105';

UPDATE training_course SET
  summary = '讲解涡流与漏磁检测的电磁原理、探头与设备组成、在管材与在役检测中的应用及信号判读基础。',
  applicable_posts = '["无损检测人员","检测技术人员"]',
  objectives = '理解电磁检测方法的适用场景，掌握基本操作与信号判读，能够配合完成管材与储罐底板检测。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'E0106';

UPDATE training_course SET
  summary = '讲解废旧油管的清洗、几何尺寸与缺陷检测、壁厚测量与分级判据，为修复再利用提供分级依据。',
  applicable_posts = '["油管检测人员","质检员","油管修复人员"]',
  objectives = '掌握废油管检测流程与分级标准，能够正确实施壁厚测量与缺陷评定，出具分级结论。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'E0201';

UPDATE training_course SET
  summary = '讲解钻杆、钻铤、油套管的管体与螺纹检测方法、疲劳与磨损评定标准及分级使用要求。',
  applicable_posts = '["无损检测人员","管材检验人员"]',
  objectives = '掌握各类管杆的检测项目与方法，能够按标准分级评定，指导管材分级使用与报废。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'E0202';

UPDATE training_course SET
  summary = '讲解高压管件（弯头、三通、阀门短节等）的外观、尺寸、无损检测与压力试验要求。',
  applicable_posts = '["质检员","无损检测人员","压力试验人员"]',
  objectives = '掌握高压管件的检验项目与合格判据，能够组织实施检测与试压并出具报告。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'E0203';

UPDATE training_course SET
  summary = '讲解压力管道定期检验的法规要求、检验项目、腐蚀与缺陷评定方法及安全状况等级划分。',
  applicable_posts = '["检验检测人员","安全管理人员","技术人员"]',
  objectives = '掌握压力管道定检流程与评定标准，能够编制检验方案并确定管道安全状况等级。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'E0204';

UPDATE training_course SET
  summary = '讲解工业金属管道与长输油气管道的检验差异、内外检测技术组合、腐蚀评价与完整性管理要点。',
  applicable_posts = '["检验检测人员","管道工程师","技术人员"]',
  objectives = '理解两类管道的检验要求与检测技术组合，能够参与完整性检测与腐蚀评价工作。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'E0205';

UPDATE training_course SET
  summary = '讲解压力容器定期检验的法规依据、宏观检查、壁厚测定、表面与埋藏缺陷检测及安全状况等级评定。',
  applicable_posts = '["检验检测人员","安全管理人员","设备管理人员"]',
  objectives = '掌握压力容器定检项目与方法，能够按 TSG 规范开展检验并评定安全状况等级。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'E0301';

UPDATE training_course SET
  summary = '讲解储罐全面检验的内容、罐底板漏磁检测技术、腐蚀评价、剩余寿命评估与维修建议编制。',
  applicable_posts = '["检验检测人员","储罐管理人员","技术人员"]',
  objectives = '掌握储罐检验流程与罐底漏磁检测实施要点，能够进行腐蚀评价并提出维修决策建议。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'E0302';

UPDATE training_course SET
  summary = '讲解球罐的结构与检验特点、支柱与焊缝检测、内外部检验项目及耐压试验要求。',
  applicable_posts = '["检验检测人员","无损检测人员","设备管理人员"]',
  objectives = '掌握球罐检验项目与检测重点，能够组织实施球罐定检并评定结果。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'E0303';

UPDATE training_course SET
  summary = '讲解锅炉定期检验的范围、受压元件检测、水垢与腐蚀检查、安全附件校验及检验结论判定。',
  applicable_posts = '["检验检测人员","锅炉运行维护人员"]',
  objectives = '掌握锅炉检验项目与判定标准，能够配合完成锅炉定检并落实问题整改。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'E0304';

UPDATE training_course SET
  summary = '讲解通用机械设备、钢结构件与站场设施的检验内容、检测方法与检验周期管理。',
  applicable_posts = '["检验检测人员","设备管理人员","技术人员"]',
  objectives = '掌握通用设备设施的检验要求，能够编制检验计划并实施周期检测管理。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'E0305';

UPDATE training_course SET
  summary = '讲解抽油机井架的结构特点、变形与裂纹检测方法、承载校核与安全评定要求。',
  applicable_posts = '["检验检测人员","无损检测人员","设备管理人员"]',
  objectives = '掌握井架检测项目与方法，能够进行变形测量与承载评定，提出加固或报废建议。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'E0306';

UPDATE training_course SET
  summary = '讲解气密性试验的介质与压力选取、升压检漏程序、检漏方法（皂泡、氦检等）与合格判定标准。',
  applicable_posts = '["检验检测人员","试压作业人员","质检员"]',
  objectives = '掌握气密试验的工艺流程与安全要求，能够规范实施检漏并判定合格性。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'E0401';

UPDATE training_course SET
  summary = '讲解作业环境气体检测（可燃气、有毒气、氧含量）与固体材料成分性能检测的方法、仪器与标准。',
  applicable_posts = '["检测人员","安全管理人员","实验室人员"]',
  objectives = '会使用气体检测仪进行受限空间等环境检测，掌握固体材料的检测送检与判定要求。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'E0402';

UPDATE training_course SET
  summary = '讲解防雷装置检测的项目与仪器、接闪引下与接地电阻测量，以及防爆电气设备检查与防爆区域管理要求。',
  applicable_posts = '["电气检测人员","安全管理人员","仪表人员"]',
  objectives = '掌握防雷接地检测方法与判据，能够检查防爆设备的选型、安装与完好性，满足石化装置防爆要求。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'E0403';

UPDATE training_course SET
  summary = '讲解输电线路与变电站的巡检项目、绝缘检测、红外测温、接地检测及带电检测的安全要求。',
  applicable_posts = '["电气检测人员","电气运维人员"]',
  objectives = '掌握电力设施检测项目与仪器使用，能够开展红外测温与接地检测并按规定安全作业。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'E0404';

UPDATE training_course SET
  summary = '讲解入井管杆、工具、药剂等井下材料的检验项目、入井质量控制要求与不合格品处置。',
  applicable_posts = '["质检员","井下作业人员","材料管理人员"]',
  objectives = '掌握井下材料入井前的检验要求与流程，能够把好入井质量关并及时处置不合格品。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'E0405';

UPDATE training_course SET
  summary = '讲解拉伸、冲击、硬度等力学性能试验与金相分析的原理、试样制备、设备操作及标准应用。',
  applicable_posts = '["实验室人员","理化试验人员","技术人员"]',
  objectives = '掌握常用理化试验方法与试样制备，能够按标准开展试验并分析数据，支撑材料与工艺评定。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'E0501';

UPDATE training_course SET
  summary = '讲解计量器具的分类管理、检定与校准的区别、检定周期确定、量值溯源要求及检测设备台账管理。',
  applicable_posts = '["计量管理人员","质检员","设备管理人员"]',
  objectives = '理解量值溯源体系要求，能够建立计量器具台账与周期计划，规范实施检定校准与标识管理。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'E0502';

UPDATE training_course SET
  summary = '讲解检测报告的编制规范、数据修约与判定规则、测量不确定度概念及报告审核签发流程。',
  applicable_posts = '["检测人员","报告编制人员","审核人员"]',
  objectives = '能够规范编制检测报告，正确进行数据修约与合格判定，理解报告审核要求与相应责任。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'E0503';

-- ========== F 域：材料与采购（5 门） ==========

UPDATE training_course SET
  summary = '讲解涂料、玻璃钢原料等防腐材料的关键技术指标、抽样复验要求、进场验收流程与不合格品处置。',
  applicable_posts = '["材料验收人员","质检员","技术人员"]',
  objectives = '掌握防腐材料的关键指标与检验方法，能够组织进场验收与抽样复验，把住材料质量关。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'F0101';

UPDATE training_course SET
  summary = '讲解天然气常用管材的类型、钢级、规格系列、防腐配套要求及管材标识识别方法。',
  applicable_posts = '["材料管理人员","施工技术人员","质检员"]',
  objectives = '能够识别常用管材的钢级与规格标识，掌握管材与防腐层的配套关系，正确进行管材验收与保管。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'F0102';

UPDATE training_course SET
  summary = '讲解保温材料的密度、导热系数、燃烧性能等复验指标、抽样规则与现场进场检验要点。',
  applicable_posts = '["材料验收人员","质检员","施工管理人员"]',
  objectives = '掌握保温材料进场复验项目与判定标准，能够规范抽样送检并处置不合格材料。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'F0103';

UPDATE training_course SET
  summary = '讲解防腐材料的需求计划、采购流程、技术协议签订、供方评价与准入退出管理要求。',
  applicable_posts = '["采购人员","技术人员","供方管理人员"]',
  objectives = '掌握采购流程与关键控制点，能够参与供方评价与技术协议编制，防范采购质量与供应风险。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'F0201';

UPDATE training_course SET
  summary = '讲解涂料等材料的仓储条件、批次与保质期管理、先进先出原则及限额领用与退库管理。',
  applicable_posts = '["库房管理人员","材料管理人员","班组长"]',
  objectives = '掌握材料储存与保质期管理要求，能够执行先进先出与限额领用制度，减少材料变质与浪费。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'F0202';

-- ========== G 域：施工管理与项目交付（9 门） ==========

UPDATE training_course SET
  summary = '讲解防腐保温工程施工方案的编制内容与审批流程，以及分级技术交底的组织与签字确认要求。',
  applicable_posts = '["施工技术人员","施工管理人员","班组长"]',
  objectives = '能够编制专项施工方案并组织审批，掌握技术交底的内容与流程，确保方案要求落实到作业层。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'G0101';

UPDATE training_course SET
  summary = '讲解工艺卡与作业指导书的结构要素、编制要点及其在班组作业中的执行与反馈机制。',
  applicable_posts = '["技术人员","班组长","质检员"]',
  objectives = '能够读懂并使用工艺卡指导作业，掌握作业指导书的编制与更新方法，实现工艺执行的标准化。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'G0102';

UPDATE training_course SET
  summary = '讲解防腐保温工程的分部分项划分、各工序验收标准、隐蔽工程验收组织与验收资料要求。',
  applicable_posts = '["质检员","施工管理人员","技术人员"]',
  objectives = '掌握各工序验收指标与检验方法，能够组织隐蔽验收与报验，保证工程质量符合规范与合同要求。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'G0201';

UPDATE training_course SET
  summary = '讲解不合格品的识别、隔离、评审与处置流程，返工方案的制定与再验证及纠正预防措施闭环。',
  applicable_posts = '["质检员","施工管理人员","技术人员"]',
  objectives = '掌握不合格品处置流程与权限，能够组织返工返修并跟踪再验证，落实纠正预防措施。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'G0202';

UPDATE training_course SET
  summary = '讲解班组日常管理内容、班前会组织、派工与工序衔接、人员动态管理与班组核算基础。',
  applicable_posts = '["班组长","施工管理人员"]',
  objectives = '掌握班组管理的核心方法，能够组织班前会与合理派工，提升班组执行效率与安全质量表现。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'G0301';

UPDATE training_course SET
  summary = '讲解防腐保温工程量的计量规则、签证确认流程、结算资料编制与争议处理基础。',
  applicable_posts = '["施工管理人员","预算人员","班组长"]',
  objectives = '掌握工程量计量与签证要求，能够规范收集结算资料，参与结算对量并减少漏项损失。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'G0302';

UPDATE training_course SET
  summary = '讲解产能建设管线工程的特点、施工组织、工序穿插、与建设方的界面管理及关键控制点。',
  applicable_posts = '["项目经理","施工管理人员","技术人员"]',
  objectives = '了解产能建设项目的管理要求，能够组织管线防腐保温施工的进度与界面协调，保证项目履约。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'G0303';

UPDATE training_course SET
  summary = '讲解天然气处理厂施工的装置区作业特点、动火与交叉作业管理、防腐保温与主体施工的配合要点。',
  applicable_posts = '["项目经理","施工管理人员","安全管理人员"]',
  objectives = '掌握厂区内施工的安全与协调要求，能够组织装置区防腐保温作业与各方配合，确保按期投产。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'G0304';

UPDATE training_course SET
  summary = '讲解检维修工程的特点、抢修组织、计划检修的施工准备、停工窗口管理及快速施工与质量的平衡。',
  applicable_posts = '["项目经理","施工管理人员","班组长"]',
  objectives = '掌握维修工程的组织方法与应急抢修流程，能够在停工窗口内保质保量完成防腐保温检修任务。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'G0305';

-- ========== H 域：数字化与平台工具（5 门） ==========

UPDATE training_course SET
  summary = '讲解公司 MES 系统的功能架构、登录与权限、生产工单流转及本岗位常用模块的操作方法。',
  applicable_posts = '["生产管理人员","车间操作人员","班组长"]',
  objectives = '掌握 MES 系统基础操作，能够完成本岗位业务流程的线上办理，理解数据录入对生产管理的作用。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'H0101';

UPDATE training_course SET
  summary = '讲解生产数据的采集要求、报工录入的及时性与准确性规范、常见错误类型与更正流程。',
  applicable_posts = '["车间操作人员","班组长","统计人员"]',
  objectives = '掌握报工录入规范与校验要求，能够准确及时完成数据录入与更正，保证生产数据真实可用。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'H0102';

UPDATE training_course SET
  summary = '讲解施工过程影像拍摄的节点要求、命名与上传规范，以及电子资料的归档标准。',
  applicable_posts = '["施工技术人员","质检员","资料员"]',
  objectives = '掌握影像留证节点与上传规范，能够按标准完成施工过程影像资料的采集与归档。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'H0103';

UPDATE training_course SET
  summary = '讲解经营与生产报表看板的指标含义、数据来源、常用分析方法与管理应用场景。',
  applicable_posts = '["管理人员","班组长","经营分析人员"]',
  objectives = '能够读懂核心指标含义与趋势，掌握看板数据的基本分析方法，运用数据支持日常管理决策。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'H0201';

UPDATE training_course SET
  summary = '讲解招标公告的要素识别、资质与业绩要求解读、报名与响应文件准备的基础流程。',
  applicable_posts = '["经营人员","投标人员","技术人员"]',
  objectives = '能够识别招标信息的关键要素与门槛条件，掌握投标响应的基础流程与协作分工要求。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'H0202';

COMMIT;
