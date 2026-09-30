-- ======================================================================
-- 云培训学院 - 112 门课程「课程概述（summary）」扩写脚本（幂等，可重复执行）
-- 数据库：ruoyi-vue-pro
--
-- 说明：
--   1. 仅更新 training_course.summary 与 updater/update_time，
--      不改动 objectives / applicable_posts / status / 分类表等任何其他字段；
--   2. 按 tenant_id + code 精确定位，重复执行结果一致；
--   3. 每门课程概述均按所属一级域 / 二级组的专业方向单独撰写，
--      技术类体现施工、材料、工艺、质量、安全、现场管理，
--      管理类体现项目、质量、成本、组织协调，
--      安全类体现风险识别、防护、应急、作业规范；
--   4. 采用容器内 source 方式执行，避免 Windows 管道编码问题。
-- ======================================================================
START TRANSACTION;

-- A01 入职与公司认知
UPDATE training_course SET
  summary = '本课程面向新入职与转岗人员，系统介绍公司的发展沿革、组织架构与业务布局，逐条讲解防腐保温、管道内衬修复、检测检验、材料贸易等主要产品线的业务范围、服务对象与典型工程场景，说明各业务板块之间的协作关系与内部支撑流程，并结合公司近年典型项目案例说明业务交付模式，帮助学员在入职初期建立对公司业务全貌的整体认知，明确本岗位在业务链条中的位置。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'A0101';

UPDATE training_course SET
  summary = '本课程讲解公司各职能与现场序列岗位的职责边界、汇报关系与考核要求，说明考勤纪律、着装规范、请示报告流程、保密义务与廉洁从业等职业行为准则，并结合防腐保温施工现场的典型案例说明违规行为的表现形式、造成后果与处理办法，同时介绍公司奖惩制度与申诉渠道，帮助学员明确岗位责任边界与行为红线，形成规范履职、按章办事的职业习惯。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'A0102';

-- A02 HSE 基础
UPDATE training_course SET
  summary = '本课程讲解石油化工与防腐保温施工现场的主要危险源辨识方法，介绍 HSE 管理体系的基本框架、安全责任制与现场安全管理制度，重点说明动火、高处、受限空间、临时用电等高风险作业环节的通用安全要求，讲解作业票证办理、现场监护、安全交底与个人安全责任的具体内容，并结合现场常见违章行为说明纠正与处置要求，帮助学员建立基本的安全风险意识。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'A0201';

UPDATE training_course SET
  summary = '本课程系统讲解安全帽、防护服、防护手套、呼吸防护、眼面部防护及坠落防护等劳动防护用品的分类、适用场景与选型原则，演示各类用品的正确佩戴方法、使用前检查要点、日常清洁维护与报废更换标准，并结合喷砂除锈、涂料喷涂、高空保温等典型作业场景说明特殊工种防护用品的配置要求，帮助学员掌握劳保用品从选型、佩戴到维护报废的全流程管理要求。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'A0202';

UPDATE training_course SET
  summary = '本课程讲解喷砂粉尘、涂料溶剂、噪声、高温等职业危害因素的来源、侵入途径与对人体的健康影响，介绍职业接触限值、危害因素检测与评价方法，说明通风除尘、工艺替代、密闭隔离等工程控制措施与个体防护的配合使用要求，并讲解岗前、在岗、离岗职业健康检查的项目安排与档案管理要求，帮助学员理解职业健康防护要求并主动落实岗位防护措施。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'A0203';

-- A03 特殊作业许可
UPDATE training_course SET
  summary = '本课程讲解受限空间的辨识标准与风险特征，重点围绕储罐、塔罐内部防腐保温作业场景，系统说明作业许可办理流程、气体检测的项目与频次、通风置换措施、进出登记与专人监护要求，讲解应急救援装备配置与禁止盲目施救的原则，并结合罐内施工工序说明各环节的作业控制要点与异常情况处置流程，帮助学员掌握受限空间作业全过程的安全管控要求。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'A0301';

UPDATE training_course SET
  summary = '本课程讲解高处作业的分级标准、坠落风险识别与坠落防护措施，说明脚手架的搭设要求、验收程序与使用管理，讲解安全带、生命线、安全网等防护设施的设置与检查方法，以及登高用具、临边洞口防护的具体要求，并结合储罐外壁保温、管架防腐、塔器施工等场景说明高处作业的组织与监护要点，帮助学员掌握高处作业与脚手架安全的实操要求。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'A0302';

UPDATE training_course SET
  summary = '本课程讲解动火作业的分级管理与审批流程，说明可燃气体检测、可燃物清理、防火隔离与消防器材配置的作业前准备要求，讲解监火人职责、作业过程监控与作业后现场清理的管控要点，并重点介绍易燃涂料、稀释剂等危险化学品的存放、领用与使用规定，结合施工现场动火作业典型场景说明全过程管控流程，帮助学员掌握动火作业防火防爆的关键控制要求。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'A0303';

UPDATE training_course SET
  summary = '本课程讲解施工现场临时用电的组织设计、配电箱与开关箱设置、接地接零保护、漏电保护装置配置与电缆敷设要求，说明用电设备一机一闸一漏一箱的执行标准与日常巡回检查内容，并针对喷砂机、喷涂机、砂轮机、电动工具等常用工机具讲解使用前检查、操作规程、维护保养与故障处理要求，帮助学员掌握临时用电与工机具安全使用的基本规范。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'A0304';

-- A04 应急与事故
UPDATE training_course SET
  summary = '本课程讲解施工现场火灾爆炸、中毒窒息、高处坠落、物料泄漏、触电等突发事件的应急分级与响应流程，说明报警程序、信息上报路径与现场警戒疏散的组织要求，讲解逃生路线识别、应急集合点设置与自救互救基本方法，并介绍现场急救常识，包括心肺复苏、外伤止血包扎、烧伤与中毒的现场处置等内容，帮助学员在突发情况下能够正确报警、有序撤离并配合开展初期处置。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'A0401';

UPDATE training_course SET
  summary = '本课程选取石油化工与防腐保温行业的典型事故案例，围绕受限空间中毒窒息、高处坠落、火灾爆炸、物体打击、触电等类型，逐一还原事故经过、剖析直接原因与间接原因、梳理管理漏洞与违章环节，讲解事故调查与责任认定的基本逻辑，并结合本公司业务场景提出同类作业的风险防范对策与现场管控改进措施，帮助学员从事故教训中建立风险预判与主动防范能力。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'A0402';

-- A05 质量与制度
UPDATE training_course SET
  summary = '本课程讲解公司质量管理体系的框架构成、质量方针目标与岗位职责分工，说明防腐保温工程从表面处理、涂装、保温到验收的全过程质量控制要求与控制点设置，重点讲解自检、互检、专检三检制的执行流程、检查内容、记录填写与签字确认要求，并说明质量问题的上报、追溯与整改闭环机制，帮助学员理解质量管理要求并在岗位作业中落实过程控制责任。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'A0501';

UPDATE training_course SET
  summary = '本课程讲解防腐保温工程施工资料的构成与填写规范，重点说明除锈等级、表面粗糙度、涂层厚度、保温厚度、隐蔽工程等关键工序的记录要求与影像拍摄节点，讲解资料与施工进度同步、签字盖章、影像命名与归档整理的标准，并说明资料缺项、后补、造假等问题的风险与处理要求，帮助学员掌握施工过程留证与竣工资料编制的基本方法，确保质量记录可追溯。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'A0502';

-- B01 表面处理
UPDATE training_course SET
  summary = '本课程讲解 GB/T 8923 系列标准中 Sa 喷射清理与 St 手工动力工具清理各除锈等级的定义、适用范围与技术要求，说明锈蚀等级评定、表面处理质量目视判定的方法与标准照片比对技巧，讲解照明条件、观察角度与判定争议的处置原则，并结合喷砂、抛丸、手工除锈等不同工艺场景进行分级练习，帮助学员能够独立完成钢材表面除锈等级的判定与验收。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'B0101';

UPDATE training_course SET
  summary = '本课程讲解干式喷砂与抛丸除锈设备的结构组成、工作原理与工艺参数，说明磨料类型选择、粒度与硬度匹配、压缩空气压力与流量调节方法，讲解设备开机前检查、运行监控、停机保养、易损件更换与常见故障判断处理，并说明磨料回收、粉尘收集与作业环境控制要求，帮助学员掌握喷砂抛丸设备的规范操作方法，提升表面处理效率并延长设备使用寿命。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'B0102';

UPDATE training_course SET
  summary = '本课程讲解高压水射流除锈的作用原理、设备组成与关键参数，说明压力等级、流量、喷嘴选型与清洗质量之间的关系，讲解作业前设备检查、管路连接、射流操作手法与安全警戒要求，说明闪锈控制、废水收集与环境保护要求，并结合不同基材与锈蚀状态对比高压水射流与喷砂工艺的适用性与经济性，帮助学员掌握该工艺的应用场景与操作要点。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'B0103';

UPDATE training_course SET
  summary = '本课程讲解表面粗糙度的评定参数、比较样块与粗糙度仪的测量方法及测点布置原则，说明灰尘、油污、可溶性盐等表面清洁度的检测手段与判定标准，分析粗糙度与清洁度对涂层附着力、防腐寿命的影响机理，并讲解检测记录填写与不合格处理要求，帮助学员能够规范开展表面处理质量检测，为涂装工序提供可靠的验收依据。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'B0104';

-- B02 涂料与配比
UPDATE training_course SET
  summary = '本课程系统讲解环氧、聚氨酯、无机富锌等常用防腐涂料体系的成膜物质组成、固化机理、性能特点与适用环境，对比各体系在附着力、耐化学性、耐候性、耐温性等方面的差异，说明底漆、中间漆、面漆的配套原则与典型防腐配套方案的设计思路，并介绍不同介质与工况下的涂料选型要点，帮助学员建立防腐涂料知识体系与选型判断能力。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'B0201';

UPDATE training_course SET
  summary = '本课程讲解双组分防腐涂料的配比要求、称量与混合搅拌方法、熟化时间控制与稀释剂选择原则，说明不同温度条件下适用期的变化规律及其对施工质量的影响，讲解涂料胶化、沉淀、结皮等异常状态的识别与处置，并说明配料记录、剩余涂料处理与工具清洗要求，帮助学员规范完成涂料调配作业，避免因配比与适用期失控造成质量缺陷。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'B0202';

UPDATE training_course SET
  summary = '本课程讲解环境温度、相对湿度、钢板表面温度与露点之间的关系，说明露点测算方法与涂装施工环境窗口的判定标准，讲解雨雪、大雾、大风及低温条件下的停工条件与防护要求，说明环境参数测量工具的使用方法、监测频次与记录要求，并结合现场施工场景说明因环境失控导致涂层起泡、返锈、附着力不良的典型问题与预防措施。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'B0203';

-- B03 涂装施工
UPDATE training_course SET
  summary = '本课程讲解高压无气喷涂机的结构组成、工作原理与设备选型，说明喷嘴型号、口径与喷涂压力、流量之间的匹配关系，讲解喷涂距离、行走速度、搭接幅度与喷涂角度的操作手法，说明喷涂作业的安全防护、设备清洗与常见故障处理要求，并结合大面积储罐、管线涂装场景说明提高涂膜均匀性与材料利用率的实操技巧。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'B0301';

UPDATE training_course SET
  summary = '本课程讲解刷涂与滚涂工艺的适用场景、工具选型与操作手法，说明涂料用量控制、涂层均匀性与搭接处理要点，重点讲解焊缝、边角、螺栓、法兰、管束背后及狭小空间等喷涂难以覆盖部位的预涂与补涂技巧，说明条涂与预涂的施工顺序要求，帮助学员掌握复杂部位的涂装处理方法，避免出现漏涂、薄涂与涂层不连续等质量隐患。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'B0302';

UPDATE training_course SET
  summary = '本课程讲解湿膜梳与干膜测厚仪的工作原理、校准方法与使用规范，说明测点布置原则、测量频次与数据记录要求，重点讲解涂层厚度验收的 85-15 与 90-10 规则的判定方法与计算示例，说明膜厚不足或超厚的成因分析与返修处理流程，帮助学员能够在施工过程中实时控制膜厚并规范完成涂层厚度检测与验收记录。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'B0303';

UPDATE training_course SET
  summary = '本课程讲解涂层针孔、漏点的形成原因与危害，说明电火花检漏仪的工作原理、检测电压选取原则与仪器校准方法，讲解检测前涂层状态确认、探头移动速度、漏点标记与修补工艺要求，说明修补后的复检与记录要求，并结合管道、储罐内防腐场景说明不同涂层厚度与介质条件下的检测电压选取与安全管理要求。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'B0304';

UPDATE training_course SET
  summary = '本课程讲解流挂、橘皮、针孔、起泡、开裂、剥落、附着力不良等典型涂层缺陷的外观特征、形成机理与影响因素，说明缺陷等级的判定标准与现场识别方法，讲解不同类型缺陷的返修工艺、表面处理要求与修补材料匹配原则，并说明返修后的质量验证与记录要求，帮助学员准确判断缺陷成因并采取针对性的返修措施。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'B0305';

-- B04 储罐防腐
UPDATE training_course SET
  summary = '本课程讲解净化水罐、污水罐等储罐内部介质的腐蚀特性与典型腐蚀形态，说明内防腐涂层与玻璃钢衬里的选型依据与配套方案，讲解罐内表面处理、涂装工序安排、涂层厚度与固化控制要求，并重点说明罐内通风换气、防爆照明、有限空间作业配合与工序衔接要点，帮助学员掌握储罐内防腐施工的工艺组织与质量控制方法。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'B0401';

UPDATE training_course SET
  summary = '本课程讲解原油罐、沉降罐内外壁的腐蚀机理与重点腐蚀部位，说明罐底板、壁板、边缘板、浮顶等部位的防腐差异化处理要求，讲解罐内导静电涂层的性能指标与施工要点，以及外防腐配套方案的设计与施工顺序，说明罐底与边缘板防水密封、罐内加热盘管周边处理等关键细节，帮助学员掌握油罐与沉降罐内外防腐的整体施工思路。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'B0402';

UPDATE training_course SET
  summary = '本课程讲解塔器与球罐的结构特点、表面处理难点与涂装作业条件，说明脚手架、吊篮及高空作业平台的配合方式与安全管理要求，讲解现场组焊后焊道部位的表面处理与补涂要求、球体曲面涂装的顺序与膜厚控制方法，并说明保温与防腐交叉施工的工序衔接与成品保护要求，帮助学员掌握塔罐及球罐防腐施工的组织与质量控制要点。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'B0403';

UPDATE training_course SET
  summary = '本课程讲解储罐内衬玻璃钢与陶瓷涂层的材料特性、适用介质与选型原则，说明衬里层次结构、底涂层与增强层、面层的施工顺序与厚度控制要求，讲解配料、衬贴、滚压排气、固化养护等关键工序的操作要点与环境控制要求，并说明衬里针孔检测、厚度检测与附着力检验的方法，帮助学员掌握储罐内衬施工工艺与质量检验要点。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'B0404';

-- B05 管线外防腐
UPDATE training_course SET
  summary = '本课程讲解 3PE 三层聚乙烯外防腐层的结构组成与各层功能，说明环氧粉末底层、胶粘剂中间层与聚乙烯面层的材料特性与厚度要求，讲解钢管表面处理、中频加热、粉末喷涂、缠绕挤出、水冷定型等工序的工艺参数控制要点，说明剥离强度、漏点检测、厚度与外观检验的执行标准，并介绍补口补伤的配套工艺，帮助学员掌握 3PE 防腐作业线施工与质量控制要点。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'B0501';

UPDATE training_course SET
  summary = '本课程讲解熔结环氧粉末防腐层的成膜机理、性能指标与适用范围，说明钢管表面处理等级、锚纹深度与加热温度的工艺窗口，讲解静电喷涂、粉末回收、固化时间与冷却控制要点，说明涂层厚度均匀性控制、漏点检测与附着力评定方法，并分析 FBE 单层与双层结构的差异及在集输管线中的典型应用，帮助学员掌握环氧粉末防腐施工与检验要求。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'B0502';

UPDATE training_course SET
  summary = '本课程讲解地面集输管线外防腐的工艺流程与施工组织，说明管体表面处理、底漆涂装、防腐层包覆与检测补口的全过程控制要求，讲解站场管线与穿跨越段的差异化防腐处理、管托与支墩部位的防腐细节，说明防腐层成品保护、补伤与交叉施工配合要求，并结合现场常见问题说明雨季施工与赶工条件下的质量控制措施。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'B0503';

UPDATE training_course SET
  summary = '本课程讲解井下管柱与注水油管所处的高温、高压、高矿化度与腐蚀介质的服役环境特点，说明井下油管的腐蚀形态与失效机理，讲解内涂层、合金层、缓蚀剂加注等防腐技术路线的适用条件与工艺要点，说明油管入井前的检验、涂层完整性检测与使用周期管理要求，帮助学员结合井况选择合理的防腐方案并做好入井质量控制。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'B0504';

UPDATE training_course SET
  summary = '本课程讲解弯管、冷换设备管束与加热炉盘管等异形管件的结构特点与防腐难点，说明弯制部位应力集中与涂层损伤风险的特殊处理要求，讲解进管箱管束的涂装方法、加热炉盘管的高温防腐与耐热涂层选型原则，说明盘管穿管、试压、吊装过程中的成品保护与损伤修补方法，帮助学员掌握异形管件防腐施工的工艺细节与质量控制要求。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'B0505';

-- B06 补口补伤
UPDATE training_course SET
  summary = '本课程讲解管道焊缝补口在防腐体系中的作用与失效风险，说明热收缩套与热收缩带的材料构成、适用管径与选型要求，讲解管口清理、预热温度控制、底漆涂刷、收缩带安装、加热收缩与辊压排气的标准操作流程，说明补口质量的外观检查、漏点检测与剥离强度抽检要求，并介绍常见补口缺陷的成因与处理措施，帮助学员规范完成补口作业。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'B0601';

UPDATE training_course SET
  summary = '本课程讲解防腐层在运输、施工与回填过程中产生损伤的常见类型与判定标准，说明补伤材料与原防腐层的匹配原则，讲解损伤部位清理、打磨坡口、补伤片尺寸确定、加热压实的操作要点，说明热收缩带搭接长度、搭接方向、密封处理与固化要求，以及补伤后的漏点与厚度复检标准，帮助学员掌握防腐层补伤与搭接的质量控制方法。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'B0602';

-- B07 内涂
UPDATE training_course SET
  summary = '本课程讲解管道内涂层减阻防腐的作用机理、材料体系与适用工况，说明管道内表面清理、除锈除尘与管端处理要求，讲解内涂设备的组成、喷涂车行进速度与旋转雾化参数控制、涂层均匀性保证措施，说明涂层固化、管端预留与标记要求，并介绍内涂施工的安全防护与质量控制要点，帮助学员掌握管道内涂作业的工艺流程。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'B0701';

UPDATE training_course SET
  summary = '本课程讲解管道内涂层质量的检测项目与执行标准，说明涂层外观、厚度、漏点、附着力与耐磨性能检测的方法与仪器使用，讲解管内检测设备（爬行检测装置）的适用范围与操作要点，说明检测数据记录、不合格判定与返修处理流程，并介绍内涂层在输送介质与清管条件下的性能验证要求，帮助学员规范开展内涂层质量检测与评定。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'B0702';

-- B08 特种防腐
UPDATE training_course SET
  summary = '本课程讲解环氧玻璃钢防腐衬里的材料组成、树脂与增强材料选型原则与耐腐蚀性能，说明衬里结构设计、层次布置与厚度确定方法，讲解基体表面处理、底涂、衬布铺贴、滚压排气、固化养护等施工工序的操作要点与环境控制要求，说明衬里气泡、分层、针孔等缺陷的识别与修补方法，以及衬里质量的检验与验收标准。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'B0801';

UPDATE training_course SET
  summary = '本课程讲解环氧陶瓷与金属陶瓷涂层的材料特性、耐磨耐蚀机理与典型应用场景，说明涂层配套体系、厚度设计与基体表面处理要求，讲解喷涂或刮涂施工的参数控制、固化条件与层间处理要点，说明涂层硬度、耐磨性、附着力与耐冲击性能的检测方法与判定标准，并介绍涂层在油管、管件与泵件上的应用案例与质量控制要点。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'B0802';

UPDATE training_course SET
  summary = '本课程讲解抽油杆管偏磨与冲蚀磨损的成因，说明耐磨涂层与内衬油管的技术路线与选型依据，讲解内衬管穿管工艺、端部处理与接箍配套要求，说明耐磨涂层的厚度、结合强度检测与下井前检验项目，并结合不同井况与含砂、含水条件说明耐磨油管的应用效果评价与寿命管理方法，帮助学员掌握耐磨油管防腐施工与检验要点。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'B0803';

UPDATE training_course SET
  summary = '本课程讲解井口装置、采气树、阀门、法兰等设备在潮湿、含硫与含盐环境中的腐蚀特点，说明外防腐涂层体系选型、表面处理与涂装工艺要求，讲解螺栓、法兰缝隙、异形部位与保温层下腐蚀（CUI）的防护处理措施，说明设备不停产维护条件下的表面处理与涂装可行方案，帮助学员掌握井口与设备现场防腐维护的实施要点。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'B0804';

UPDATE training_course SET
  summary = '本课程讲解原油结蜡与无机结垢的形成机理、影响因素与危害表现，说明清防蜡的机械、热洗、化学与涂层防蜡技术路线及适用条件，讲解防垢剂与阻垢剂的选型、加注方式与加药浓度控制，说明防蜡防垢涂层的应用效果评价与维护周期管理要求，并介绍结垢趋势预测与现场监测方法，帮助学员结合工况制定防蜡防垢综合措施。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'B0805';

-- C01 保温基础
UPDATE training_course SET
  summary = '本课程讲解岩棉、玻璃棉、硅酸铝、聚氨酯、泡沫玻璃等常用保温绝热材料的性能参数与适用温度区间，说明导热系数、密度、含水率、燃烧性能、耐腐蚀性等关键指标的解读与选型原则，讲解不同介质温度、环境湿度与防火要求下的材料匹配方法，并说明材料进场验收与标识核对要求，帮助学员根据实际工况合理选择保温材料。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'C0101';

UPDATE training_course SET
  summary = '本课程讲解保温结构的层次构成，包括防腐层、保温层、防潮层与保护层的各自功能与设置要求，说明保温层厚度确定的经济厚度法、防烫法与防结露法的计算思路，讲解热损失核算与表面温度校核的基本方法，说明多层保温与异形部位的厚度处理方式，帮助学员理解保温设计的计算逻辑并在施工中正确执行图纸要求。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'C0102';

-- C02 保温施工
UPDATE training_course SET
  summary = '本课程讲解管道保温施工的工艺流程，包括表面清理与防腐检查、保温层下料与包扎、捆扎固定、错缝与接缝处理、弯头与三通放样下料等关键工序，说明保温层厚度均匀性、容重控制与拼缝质量的要求，讲解施工顺序与成品保护措施，并说明雨季、低温环境下的施工注意事项，帮助学员掌握管道保温施工的规范操作与质量控制要点。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'C0201';

UPDATE training_course SET
  summary = '本课程讲解设备与储罐保温施工的特点与工艺流程，说明罐壁保温支撑圈、保温钉焊接与布置要求，讲解大型设备的分层保温、错缝铺设与捆扎固定方法，说明罐顶、罐壁、罐底及附件部位的差异化处理与人孔、接管、梯子平台的保温收口做法，说明施工安全与成品保护要求，帮助学员掌握设备储罐保温施工的组织与质量控制要点。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'C0202';

UPDATE training_course SET
  summary = '本课程讲解金属保护层的作用、材料选型与厚度要求，说明铁皮与铝皮下料、压边、卷圆、咬口等预制加工方法与专用工具使用，讲解保护层安装的搭接方向、搭接长度、固定间距与防水密封处理要求，说明阀门、法兰、弯头等部位保护层的放样与收口做法，帮助学员掌握金属保护层制作安装的工艺标准与外观质量要求。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'C0203';

UPDATE training_course SET
  summary = '本课程讲解阀门、法兰、过滤器、补偿器等异形件保温的难点与可拆卸保温结构的设计要求，说明保温盒的制作尺寸确定、填充材料选择与固定方式，讲解法兰保温与管线保温的衔接处理、螺栓部位的可拆卸要求与防水密封做法，说明异形件保温在检修拆装条件下的重复使用与维护要求，帮助学员掌握异形部位保温的施工技巧。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'C0204';

-- C03 防腐绝热保冷
UPDATE training_course SET
  summary = '本课程讲解保冷与保温的区别及低温工况下的特殊要求，说明聚氨酯、泡沫玻璃等保冷材料的性能特点、适用温度与选型原则，讲解保冷层的厚度确定、分层错缝与黏结密封要求，说明冷桥部位的处理与低温收缩缝的控制措施，并介绍保冷结构的防潮层设计与施工配合要求，帮助学员掌握低温装置保冷施工的关键工艺点。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'C0301';

UPDATE training_course SET
  summary = '本课程讲解水汽渗透对保冷与保温效果的危害机理，说明防潮层、隔汽层的材料类型、性能指标与设置位置，讲解防潮层施工的基层处理、涂刷或缠包工艺、搭接密封与厚度控制要求，说明穿管、支架、阀门等节点部位的密封处理方法与常见渗漏点的检查整改要求，帮助学员掌握防潮隔汽层施工工艺，避免保温层受潮失效。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'C0302';

UPDATE training_course SET
  summary = '本课程讲解低温装置保冷工程的质量控制要点，说明保冷材料进场复验、施工过程隐蔽验收与竣工检验的项目与标准，讲解保冷层厚度、接缝严密性、防潮层完整性与外护层密封的检查方法，说明运行中结露、结冰、冷损异常的识别与处理措施，并介绍保冷工程常见缺陷的预防与返修要求，帮助学员提升低温保冷工程质量管理能力。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'C0303';

-- C04 冬防与维护
UPDATE training_course SET
  summary = '本课程讲解冬防保温工作的组织流程与方案编制要求，说明重点防冻部位排查、保温层检查修补、伴热设施投用与仪表引压管防冻措施，讲解冬防施工的时间窗口安排、材料准备与工序组织，说明极寒天气下的应急防冻措施与巡检要求，并介绍冬防保温效果检查与问题闭环整改方法，帮助学员系统开展冬防保温实施工作。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'C0401';

UPDATE training_course SET
  summary = '本课程讲解电伴热带与蒸汽伴热系统的工作原理、组成部件与适用场景，说明伴热方式选型、伴热功率与伴热管线布置原则，讲解电伴热带的敷设、固定、绝缘测试与温控装置调试要求，说明蒸汽伴热的疏水、回水与防冻处理要点，以及伴热系统与保温层的配合关系，帮助学员掌握伴热系统安装调试与运行维护的基本方法。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'C0402';

UPDATE training_course SET
  summary = '本课程讲解检维修与检测作业中保温拆装的必要性、作业流程与组织要求，说明保温拆除范围的确定、原保温材料的编号标记与可复用判定，讲解拆装过程中的防腐层保护、支架与保温钉修复、恢复施工的层次还原与密封处理要求，说明拆装作业的安全防护与防尘措施，以及恢复后外观与厚度检查标准，帮助学员规范完成保温拆装恢复作业。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'C0403';

-- D01 内衬技术
UPDATE training_course SET
  summary = '本课程讲解管道内衬修复技术的分类、适用条件与技术经济对比，说明内衬修复在老旧管线延寿、腐蚀治理与输送效率提升中的应用价值，讲解翻衬法、穿插法、涂覆法等主流工艺的原理与施工流程，说明管线预处理、内衬管安装、端口处理与试压验收的关键环节，帮助学员建立内衬修复技术的整体认知，为后续专项工艺学习打基础。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'D0101';

UPDATE training_course SET
  summary = '本课程讲解高分子聚乙烯内衬管的性能特点、规格系列与适用范围，说明旧管清洗、通径检测与内表面处理要求，讲解内衬管穿插牵引、缩径恢复、翻边法兰连接与端头密封的施工工艺与参数控制，说明内衬管与原管环空的填充与固定方式及试压验收标准，并介绍常见施工缺陷的预防与处理，帮助学员掌握高分子内衬施工要点。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'D0102';

UPDATE training_course SET
  summary = '本课程讲解非金属内衬油管在高含腐蚀介质井况下的应用优势，说明内衬材料类型、壁厚设计与承压能力校核方法，讲解内衬管穿管工艺、端部翻边密封、接箍配套与扭矩控制要求，说明下井前的通径检测、气密性检查与损伤判定标准，并介绍内衬油管在使用过程中的失效模式与维护更换周期管理，帮助学员掌握非金属油管内衬施工与检验要求。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'D0103';

UPDATE training_course SET
  summary = '本课程讲解内衬管选型的技术要素，包括介质成分、温度压力、流速、含砂量与输送介质相容性，说明壁厚、通径损失、环空强度与寿命预估的计算思路，讲解采购技术协议的编制要点、质量证明文件与型式检验要求，说明供方资质审核、样品验证与到货验收流程，帮助学员能够在技术与成本之间做出合理的内衬管选型与采购判断。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'D0104';

-- D02 油管油杆修复
UPDATE training_course SET
  summary = '本课程讲解废旧油管修复的业务流程与工艺路线，说明油管回收分类、外观检查、壁厚检测与报废判定的标准，讲解清洗除垢、通径、探伤、螺纹修理、试压等工序的操作要求与设备使用，说明修复油管的分级标识、堆放与出厂检验项目，并介绍修复质量控制与追溯管理要求，帮助学员掌握油管修复全流程的作业与检验要点。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'D0201';

UPDATE training_course SET
  summary = '本课程讲解抽油杆的失效形式与修复价值判定，说明油杆清洗、外观检查、杆体弯曲测量与螺纹检测方法，讲解校直设备的操作、校直精度控制与杆体损伤判定标准，说明修复后油杆的探伤、试拉与标识管理要求，并介绍油杆分级使用与报废处置流程，帮助学员掌握油杆修复校直的工艺要求与质量控制要点。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'D0202';

UPDATE training_course SET
  summary = '本课程讲解摩擦焊接的基本原理、设备组成与在油管接头接箍修复中的应用场景，说明焊前坡口加工、表面清理与装配对中要求，讲解摩擦时间、顶锻压力、转速等关键工艺参数的设定与监控方法，说明焊后飞边清除、热处理与焊缝检测要求，以及接头拉伸性能与密封性能验证标准，帮助学员掌握摩擦焊修复的工艺控制与质量检验要点。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'D0203';

-- D03 管道修复补强
UPDATE training_course SET
  summary = '本课程讲解管道缺陷的分类与成因，包括腐蚀减薄、裂纹、变形、焊缝缺陷与外部损伤，说明缺陷检测数据的采集、剩余强度评价与剩余寿命评估方法，讲解补强、换管、内衬、复合材料修复等不同技术方案的适用边界与决策依据，说明修复方案编制、安全评估与验收要求，帮助学员能够依据缺陷评估结果合理选择修复技术。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'D0301';

UPDATE training_course SET
  summary = '本课程讲解钢制补强套筒与复合材料补强在管道缺陷修复中的应用，说明两类技术的适用范围、材料性能与力学作用机理，讲解表面处理、套筒安装、焊接或螺栓紧固、复合材料缠绕铺层与固化的操作要点，说明补强后的检测验证与防腐恢复要求，并介绍施工安全与带压作业条件下的控制措施，帮助学员掌握管道补强修复施工工艺。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'D0302';

UPDATE training_course SET
  summary = '本课程讲解带压堵漏的适用条件、风险边界与安全原则，说明泄漏部位勘测、卡具设计、密封剂选型与注剂压力控制的工艺要点，讲解卡具安装、注剂操作、固化观察与效果确认的标准流程，说明施工过程中的个人防护、警戒隔离与应急处置要求，并介绍不宜带压堵漏的情形与替代处置方案，帮助学员理解带压堵漏的技术要求与安全红线。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'D0303';

-- D04 套管与装备
UPDATE training_course SET
  summary = '本课程讲解套管损坏的类型与检测判定方法，说明套管整形、补贴、加固与开窗侧钻等修复工艺的适用条件与技术要点，讲解套管补贴管与膨胀管施工的操作流程、参数控制与固井密封要求，说明修复后的通径、试压与测井验证标准，并介绍套管修复作业的风险控制与施工组织要点，帮助学员掌握套管修复工艺的实施要求。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'D0401';

UPDATE training_course SET
  summary = '本课程讲解管道套筒修复的结构形式与适用缺陷类型，说明钢制套筒、复合材料套筒的选型与尺寸确定方法，讲解套筒安装前的表面处理、对中定位、组对与紧固要求，说明环空填充、端部密封与外防腐恢复的施工要点，以及修复后的检测与验收标准，帮助学员规范完成套筒修复作业并做好修复记录与质量追溯。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'D0402';

UPDATE training_course SET
  summary = '本课程讲解钻头与井下装备的主要失效形式，包括切削齿磨损、轴承损坏、本体冲蚀与螺纹损伤，说明修复前的清洗检查、损伤分级与可修复性判定方法，讲解堆焊、喷焊、镶嵌与机加工修复的工艺要点与参数控制，说明修复后的尺寸检验、硬度检测与动平衡要求，并介绍修复装备的使用管理与寿命跟踪方法。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'D0403';

-- E01 无损检测基础
UPDATE training_course SET
  summary = '本课程讲解无损检测在承压设备与管道检验中的作用与法规依据，系统介绍超声、射线、磁粉、渗透、涡流、漏磁等常用方法的原理、能力边界与适用对象，说明不同材质、厚度、缺陷类型与现场条件下的检测方法选用原则，讲解检测比例确定、工艺规程编制与检测人员资格要求，帮助学员建立无损检测技术选用与组合应用的判断能力。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'E0101';

UPDATE training_course SET
  summary = '本课程讲解超声波检测的物理基础、探头类型与仪器参数设置，说明纵波、横波与斜探头的应用场景，讲解校准试块使用、扫描速度调节、灵敏度设定与缺陷定位定量方法，说明焊缝检测与壁厚测定的操作要点及常见伪缺陷信号的识别，介绍检测记录与结果评定要求，帮助学员掌握超声检测的基本操作与判读能力。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'E0102';

UPDATE training_course SET
  summary = '本课程讲解磁粉检测的适用范围、磁化原理与设备类型，说明周向磁化与纵向磁化的选择、磁化电流确定与标准试片使用方法，讲解干法与湿法磁粉的操作流程、磁悬液浓度控制与观察条件要求，说明缺陷磁痕的识别与真伪判别，以及退磁与后处理要求，帮助学员掌握铁磁性材料表面与近表面缺陷检测的操作技能。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'E0103';

UPDATE training_course SET
  summary = '本课程讲解渗透检测的原理、适用范围与材料组成，说明渗透剂、清洗剂与显像剂的选型与灵敏度等级，讲解预清洗、渗透、清洗去除、显像与观察的标准操作步骤与时间控制要点，说明缺陷痕迹的判别、记录与后清洗要求，并介绍渗透检测在非铁磁性材料与现场焊缝检测中的应用限制与安全防护要求。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'E0104';

UPDATE training_course SET
  summary = '本课程讲解射线检测的原理、透照布置与胶片或数字成像系统的选择，说明曝光参数确定、像质计使用与底片评定方法，讲解常见焊接缺陷的影像特征识别与质量等级评定标准，并重点说明放射源与射线装置的管理要求、剂量监测、警戒隔离与人员防护制度，帮助学员在掌握射线检测技术的同时严格落实辐射安全要求。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'E0105';

UPDATE training_course SET
  summary = '本课程讲解涡流检测与漏磁检测的原理、探头形式与信号处理方式，说明两种方法在管材、换热管与储罐底板检测中的适用场景与能力边界，讲解检测前表面状态要求、扫查速度控制与标定管使用，说明缺陷信号的识别、深度定量与验证方法，介绍检测报告编制与结果评定要求，帮助学员掌握电磁类检测方法的操作与判读要点。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'E0106';

-- E02 管材与管件检测
UPDATE training_course SET
  summary = '本课程讲解废旧油管回收检测的工艺流程与判定标准，说明外观检查、壁厚测量、螺纹检测、通径试验与无损抽检的项目安排，讲解腐蚀、磨损、裂纹、变形等损伤的量化评价方法与分级规则，说明不同级别油管的修复、降级使用与报废处置要求，并介绍检测数据记录、分级标识与质量追溯管理要求。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'E0201';

UPDATE training_course SET
  summary = '本课程讲解钻杆、钻具与油套管在服役过程中的典型损伤形式，说明接头、加厚过渡区与管体高应力部位的检测重点，讲解螺纹测量、壁厚检测、磁粉与超声检测的工艺组合与评定标准，说明钻具分级、报废判定与检测周期管理要求，并介绍钻具失效分析的基本思路与预防措施，帮助学员掌握钻具管材检测要点。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'E0202';

UPDATE training_course SET
  summary = '本课程讲解弯头、三通、异径管、法兰、阀门短节等高压管件的检测要求，说明材质确认、壁厚测定、表面无损检测与硬度检测项目，讲解高压工况下裂纹、冲蚀减薄与制造缺陷的识别与评定方法，说明压力试验的介质、压力与保压时间要求及合格判定标准，介绍检测记录与不合格品处置流程，帮助学员掌握高压管件检验要点。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'E0203';

UPDATE training_course SET
  summary = '本课程讲解压力管道定期检验的法规依据、检验周期与检验方案编制要求，说明宏观检查、壁厚测定、无损抽检、安全附件检查与耐压试验的项目安排，讲解腐蚀与缺陷的安全评定方法及安全状况等级划分规则，说明检验发现问题的处理建议与使用登记要求，帮助学员掌握压力管道定期检验的组织实施与结论判定方法。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'E0204';

UPDATE training_course SET
  summary = '本课程讲解工业金属管道与长输油气管道在检验标准、检测手段与管理模式上的差异，说明内检测（智能清管）、外检测与直接评价的技术组合应用，讲解防腐层检测、阴极保护检测与阴极剥离评价方法，说明管道完整性管理的流程、风险评价与维修决策依据，帮助学员掌握两类管道检测技术与完整性管理要点。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'E0205';

-- E03 容器与设备检测
UPDATE training_course SET
  summary = '本课程讲解压力容器定期检验的法规依据、检验类别与周期要求，说明宏观检查、壁厚测定、表面与埋藏缺陷检测的工艺选择与评定标准，讲解材质劣化、应力腐蚀与疲劳裂纹的识别方法，说明安全附件校验、耐压试验要求与安全状况等级评定规则，并介绍检验报告编制与问题整改闭环要求，帮助学员掌握压力容器检验要点。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'E0301';

UPDATE training_course SET
  summary = '本课程讲解储罐全面检验的准备工作与检验项目，说明罐壁板厚度测定、焊缝检测、罐底板腐蚀检测的组织方式，重点讲解罐底板漏磁检测技术的原理、扫查覆盖率、信号判读与开挖验证要求，说明腐蚀评价、剩余寿命评估与维修建议的编制方法，并介绍储罐在线检测与开罐检验的适用条件与安全管理要求。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'E0302';

UPDATE training_course SET
  summary = '本课程讲解球罐的结构特点、服役风险与检验重点，说明球壳板壁厚测定、对接焊缝无损检测、支柱与拉杆检查的项目与比例要求，讲解球罐内外检验的脚手架与安全保障措施、表面处理要求与检测工艺选择，说明耐压试验、气密性试验的执行条件与合格标准，并介绍球罐检验周期与缺陷返修后的复验要求。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'E0303';

UPDATE training_course SET
  summary = '本课程讲解锅炉定期检验的法规依据、检验范围与周期要求，说明锅筒、受热面、集箱、水冷壁等部位的检查重点，讲解水垢与腐蚀检查、壁厚测定、裂纹检测方法，说明安全阀、压力表、水位计等安全附件的校验要求，讲解水压试验的程序与合格判定及检验结论的判定规则，帮助学员掌握锅炉检验的基本流程与技术要求。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'E0304';

UPDATE training_course SET
  summary = '本课程讲解通用机械设备、钢结构件、站场设施与附属结构的检验内容与方法，说明设备运行状态检查、结构变形与连接节点检测、基础沉降观测的项目安排，讲解腐蚀与疲劳损伤的识别与评价方法，说明检测周期管理、检验记录与隐患整改闭环要求，并介绍检测与设备维修计划的衔接方式，帮助学员建立设施检测的系统思路。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'E0305';

UPDATE training_course SET
  summary = '本课程讲解抽油机井架的结构组成与受力特点，说明井架变形、腐蚀、焊缝开裂与基础沉降的常见损伤形式，讲解几何尺寸测量、垂直度检测、焊缝与螺栓连接检查的方法，说明承载校核与安全评定的基本思路，介绍检测周期、隐患处置与停用判定要求，帮助学员掌握井架检测的项目设置与安全技术要求。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'E0306';

-- E04 专项检测
UPDATE training_course SET
  summary = '本课程讲解气密性试验的适用范围、试验介质与压力选取原则，说明升压分级、保压时间与温度修正要求，讲解皂泡法、氦质谱检漏、压降法等检漏方法的适用条件与操作要点，说明泄漏判定标准与不合格部位的处理复检流程，并介绍试验过程中的安全防护、警戒隔离与应急处置要求，帮助学员规范开展气密性检测。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'E0401';

UPDATE training_course SET
  summary = '本课程讲解作业环境气体检测与固体材料性能检测两大类内容：说明可燃气体、有毒气体与氧含量检测的仪器原理、校准方法与采样点布置，讲解检测频次、报警阈值与作业许可配合要求；同时说明固体材料的成分分析、力学与耐蚀性能检测的取样与试验方法，介绍检测数据处理与报告出具要求，帮助学员掌握两类检测的基本技能。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'E0402';

UPDATE training_course SET
  summary = '本课程讲解防雷装置检测的项目、周期与法规依据，说明接闪器、引下线、接地装置的检查内容与接地电阻测量方法，讲解等电位连接与浪涌保护器检测要求；同时说明爆炸性危险区域划分、防爆电气设备的选型检查、防爆面与电缆引入装置的检查要点，介绍不合格项的整改与复检要求，帮助学员掌握防雷防爆检测技术。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'E0403';

UPDATE training_course SET
  summary = '本课程讲解输电线路与变电站的巡检项目与检测周期，说明绝缘子检测、红外测温、接地电阻测量与避雷器检测的方法，讲解导线接头过热、绝缘子劣化与设备局部放电的识别与判据，说明带电检测的安全距离、工作票与人员防护要求，介绍检测数据趋势分析与缺陷分级处置要求，帮助学员掌握电气设备检测与隐患识别方法。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'E0404';

UPDATE training_course SET
  summary = '本课程讲解入井管杆、井下工具、化学药剂等井下材料的检验项目与标准依据，说明材质证明核查、外观尺寸检查、无损抽检与性能试验的组织方式，讲解不合格品的隔离、处置与供方反馈流程，说明入井前质量确认与下井记录要求，并介绍井下材料质量问题的追溯与原因分析思路，帮助学员掌握井下材料入井质量控制要点。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'E0405';

-- E05 理化与计量
UPDATE training_course SET
  summary = '本课程讲解金属材料常用理化试验的原理与标准依据，说明拉伸、冲击、弯曲、硬度等力学性能试验的试样制备、设备操作与数据处理方法，讲解金相试样的取样、镶嵌、磨抛、侵蚀与显微组织观察要点，说明常见组织缺陷的识别与材质判定方法，介绍试验环境、设备校准与试验结果的控制要求，帮助学员掌握理化试验基本操作。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'E0501';

UPDATE training_course SET
  summary = '本课程讲解计量器具的分类管理、强制检定与非强制检定的区别，说明检定规程、校准规范与检定周期的确定依据，讲解量值溯源体系、标准器管理与期间核查要求，说明检测设备台账、标识管理与证书归档方法，介绍计量器具失准的追溯处理与风险评价思路，帮助学员建立计量器具规范化管理能力。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'E0502';

UPDATE training_course SET
  summary = '本课程讲解检测报告的构成要素、编制规范与审核签发流程，说明原始记录填写要求、数据修约规则与有效数字处理，讲解检测结果与标准限值的比较判定方法及临界值处理原则，介绍测量不确定度的概念与评定思路，说明报告修改、补充与存档要求，以及常见报告差错的识别与防范，帮助学员提升检测报告编制质量。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'E0503';

-- F01 材料认知
UPDATE training_course SET
  summary = '本课程讲解涂料、玻璃钢原料、防腐带等防腐材料的关键技术指标含义与标准依据，说明固含量、附着力、耐盐雾、耐冲击等性能指标的解读与判定，讲解材料进场验收的资料核对、外观检查、抽样复验与试验委托流程，说明不合格材料的隔离标识、退货与索赔处理要求，帮助学员掌握防腐材料验收与质量把关方法。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'F0101';

UPDATE training_course SET
  summary = '本课程讲解天然气输送常用管材的类型、钢级、制造标准与规格系列，说明钢管壁厚系列、压力等级与管材标识的识别方法，讲解管材与防腐保温配套的技术要求，说明管材质量证明文件、外观检查与到货验收要点，并介绍不同管材在集输、支线与站场场景中的选用原则，帮助学员建立天然气管材的系统认知。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'F0102';

UPDATE training_course SET
  summary = '本课程讲解保温材料进场检验的项目与标准依据，说明密度、导热系数、含水率、燃烧性能、尺寸偏差等复验指标的检测要求，讲解取样规则、组批原则与委托送检流程，说明检验结果判定、合格标识与不合格处置要求，并介绍材料堆放防潮与标识管理要求，帮助学员规范开展保温材料进场检验与验收。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'F0103';

-- F02 采购与仓储
UPDATE training_course SET
  summary = '本课程讲解防腐材料需求计划的编制、采购申请与技术要求提报流程，说明询价、比价、评标与合同签订的要点，讲解技术协议编制内容与质量责任约定，说明供方准入审核、业绩评价、分级管理与退出机制，介绍采购交期跟踪、到货协调与质量问题追溯要求，帮助学员理解防腐材料采购与供方管理的完整流程。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'F0201';

UPDATE training_course SET
  summary = '本课程讲解涂料及化工材料的仓储条件要求，包括通风、防火、防晒防冻与分区存放规定，说明批次管理、保质期跟踪与先进先出执行方法，讲解库存盘点、变质材料判定与报废处置流程，说明限额领用、退库与现场材料保管要求，并介绍易燃易爆材料的安全管理与消防配置要求，帮助学员规范开展材料仓储与领用管理。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'F0202';

-- G01 工艺与方案
UPDATE training_course SET
  summary = '本课程讲解防腐保温施工方案的编制内容、审批流程与要点，说明工程概况、施工工艺、质量标准、安全措施与资源配置的编写要求，讲解专项方案与危大工程的辨识和论证要求，说明分级技术交底的组织方式、内容要点与签字确认流程，介绍方案变更与现场执行的偏差管理，帮助学员提升方案编制与交底组织能力。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'G0101';

UPDATE training_course SET
  summary = '本课程讲解工艺卡与作业指导书的结构要素、编制依据与审批发布流程，说明工艺参数、工序顺序、检验点与记录要求的编写要点，讲解工艺卡在班组作业中的发放宣贯、现场执行与问题反馈机制，说明工艺文件与实际工况不符时的处理流程与修订要求，介绍工艺纪律检查与执行效果评价方法，帮助学员推动工艺文件落地执行。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'G0102';

-- G02 质量与验收
UPDATE training_course SET
  summary = '本课程讲解防腐保温工程的分部分项划分与验收组织程序，说明表面处理、涂装、保温、保护层等各工序的验收标准与检验方法，讲解隐蔽工程验收的组织时间、参与方与影像资料要求，说明竣工验收资料清单、整改闭环与验收签字流程，介绍常见验收争议的处理依据，帮助学员掌握工程验收的组织与标准应用。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'G0201';

UPDATE training_course SET
  summary = '本课程讲解不合格品的识别途径、判定依据与隔离标识要求，说明不合格评审的组织、责任划分与处置方式选择，讲解返工方案的编制、技术交底、过程控制与再验证要求，说明让步接收、返修与报废的适用条件与审批流程，介绍纠正预防措施的制定与闭环验证方法，帮助学员建立质量问题的规范处置与改进机制。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'G0202';

-- G03 项目与班组
UPDATE training_course SET
  summary = '本课程讲解班组管理的基本内容与班组长职责，说明班前会的组织流程、安全交底与任务布置要点，讲解派工原则、工序衔接与人员动态调配方法，说明班组考勤、工时记录与班组核算基础，介绍现场文明施工、工具材料管理与班组绩效考核要点，帮助学员提升班组组织协调与日常管理能力。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'G0301';

UPDATE training_course SET
  summary = '本课程讲解防腐保温工程量的计量规则与计算依据，说明管线按延长米、设备按展开面积等计量口径的差异，讲解现场签证的办理条件、证据要求与确认流程，说明结算资料的构成、编制顺序与审核要点，介绍常见计量争议的成因与处理依据，帮助学员掌握工程量确认与结算资料编制的基本方法。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'G0302';

UPDATE training_course SET
  summary = '本课程讲解产能建设管线工程的特点与施工组织逻辑，说明线路踏勘、征地协调、材料到场与施工顺序安排，讲解焊接、补口补伤、下沟回填与试压投产等关键工序的组织与衔接，说明与建设方、监理方的界面划分及沟通协调机制，介绍工期控制与关键节点管理要点，帮助学员掌握管线项目组织实施的重点。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'G0303';

UPDATE training_course SET
  summary = '本课程讲解天然气处理厂施工的装置区作业特点与风险特征，说明多专业交叉作业、动火与吊装作业的协调管理要求，讲解防腐保温与主体安装、机电仪施工的穿插配合，说明受限空间、高处作业与密闭厂房施工的安全措施，介绍施工进度协调与质量安全管理要点，帮助学员掌握处理厂工程现场管理的重点。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'G0304';

UPDATE training_course SET
  summary = '本课程讲解检维修与抢修工程的特点，说明计划检修的施工准备、停工窗口协调与资源预置要求，讲解抢修任务的响应流程、方案快速制定与现场指挥机制，说明检维修中拆除、检测、修复与恢复的工序衔接，介绍快速施工与质量安全的平衡方法，以及检修记录与验收交付要求，帮助学员提升维修工程组织能力。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'G0305';

-- H01 平台操作
UPDATE training_course SET
  summary = '本课程讲解公司 MES 系统的功能架构、登录方式与权限分配，说明生产工单创建、流转、报工与关闭的完整流程，讲解本岗位常用模块的界面布局、操作步骤与常见问题处理，说明系统数据的录入要求与错误更正流程，介绍账号安全管理与操作留痕机制，帮助学员能够独立完成系统日常操作并正确维护生产数据。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'H0101';

UPDATE training_course SET
  summary = '本课程讲解生产数据采集的范围、口径与时效要求，说明报工录入的字段含义、填写规范与审核流程，讲解常见录入错误类型及其对统计与核算的影响，说明数据更正、补录与异常数据反馈的处理流程，介绍数据质量检查与考核要求，帮助学员养成及时、准确、完整录入生产数据的习惯。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'H0102';

UPDATE training_course SET
  summary = '本课程讲解施工过程影像拍摄的节点设置与拍摄要求，说明关键工序、隐蔽部位与验收环节的影像留证内容，讲解照片命名规则、分辨率与上传时间要求，说明电子资料的分类归档、目录结构与权限管理要求，介绍资料缺项、错传与造假的风险与处理措施，帮助学员规范完成影像与电子资料的采集上传。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'H0103';

-- H02 数据与经营
UPDATE training_course SET
  summary = '本课程讲解经营与生产报表看板的指标定义、数据来源与统计口径，说明产量、进度、质量、成本等核心看板的阅读方法，讲解同比环比、趋势分析与异常识别的基本思路，说明看板数据在班组例会与经营分析中的应用场景，介绍数据异常的核实与反馈流程，帮助学员能够读懂看板并将其用于日常管理决策。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'H0201';

UPDATE training_course SET
  summary = '本课程讲解招标公告的构成要素与信息获取渠道，说明资质条件、业绩要求、技术要求与评分办法的解读方法，讲解报名、购标、答疑与现场踏勘的流程要点，说明响应文件的组成、编制分工与时间节点控制，介绍投标常见否决条款与风险识别方法，帮助学员掌握招投标信息识别与响应准备的基础能力。',
  updater = 'seed', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = 1 AND code = 'H0202';

COMMIT;
