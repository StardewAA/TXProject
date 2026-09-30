export interface TrainingCatalogCourse {
  code: string;
  name: string;
  stdHours: number | null;
  required: boolean;
}

export interface TrainingCatalogGroup {
  code: string;
  name: string;
  courses: TrainingCatalogCourse[];
}

export interface TrainingCatalogCategory {
  code: string;
  name: string;
  groups: TrainingCatalogGroup[];
}

/** 云培训学院课程三级分类，来源：云培训学院-课程分类表.xlsx */
export const TRAINING_CATALOG: TrainingCatalogCategory[] = [
  {
    "code": "A",
    "name": "通用基础与安全",
    "groups": [
      {
        "code": "A01",
        "name": "入职与公司认知",
        "courses": [
          {
            "code": "A0101",
            "name": "公司业务全景与产品线认知",
            "stdHours": 2,
            "required": true
          },
          {
            "code": "A0102",
            "name": "岗位职责与职业行为规范",
            "stdHours": 1,
            "required": true
          }
        ]
      },
      {
        "code": "A02",
        "name": "HSE 基础",
        "courses": [
          {
            "code": "A0201",
            "name": "石油化工现场安全通识",
            "stdHours": 4,
            "required": true
          },
          {
            "code": "A0202",
            "name": "劳动防护用品正确使用",
            "stdHours": 1,
            "required": true
          },
          {
            "code": "A0203",
            "name": "职业健康与粉尘/溶剂危害防护",
            "stdHours": 2,
            "required": true
          }
        ]
      },
      {
        "code": "A03",
        "name": "特殊作业许可",
        "courses": [
          {
            "code": "A0301",
            "name": "受限空间作业（储罐/塔罐内部）",
            "stdHours": 4,
            "required": true
          },
          {
            "code": "A0302",
            "name": "高处作业与脚手架安全",
            "stdHours": 3,
            "required": true
          },
          {
            "code": "A0303",
            "name": "动火作业与防火防爆",
            "stdHours": 3,
            "required": true
          },
          {
            "code": "A0304",
            "name": "临时用电与工机具安全",
            "stdHours": 2,
            "required": true
          }
        ]
      },
      {
        "code": "A04",
        "name": "应急与事故",
        "courses": [
          {
            "code": "A0401",
            "name": "现场应急处置与逃生",
            "stdHours": 2,
            "required": true
          },
          {
            "code": "A0402",
            "name": "典型事故案例复盘",
            "stdHours": 2,
            "required": true
          }
        ]
      },
      {
        "code": "A05",
        "name": "质量与制度",
        "courses": [
          {
            "code": "A0501",
            "name": "质量管理体系与三检制",
            "stdHours": 2,
            "required": true
          },
          {
            "code": "A0502",
            "name": "施工资料与影像留证规范",
            "stdHours": 2,
            "required": true
          }
        ]
      }
    ]
  },
  {
    "code": "B",
    "name": "防腐涂装",
    "groups": [
      {
        "code": "B01",
        "name": "表面处理",
        "courses": [
          {
            "code": "B0101",
            "name": "除锈等级判定（Sa/St 标准）",
            "stdHours": 3,
            "required": false
          },
          {
            "code": "B0102",
            "name": "喷砂/抛丸设备操作与维护",
            "stdHours": 6,
            "required": false
          },
          {
            "code": "B0103",
            "name": "高压水射流除锈",
            "stdHours": 4,
            "required": false
          },
          {
            "code": "B0104",
            "name": "表面粗糙度与清洁度检测",
            "stdHours": 3,
            "required": false
          }
        ]
      },
      {
        "code": "B02",
        "name": "涂料与配比",
        "courses": [
          {
            "code": "B0201",
            "name": "防腐涂料体系认知（环氧/聚氨酯/无机锌）",
            "stdHours": 4,
            "required": false
          },
          {
            "code": "B0202",
            "name": "涂料调配、稀释与适用期控制",
            "stdHours": 3,
            "required": false
          },
          {
            "code": "B0203",
            "name": "环境温湿度露点控制",
            "stdHours": 2,
            "required": false
          }
        ]
      },
      {
        "code": "B03",
        "name": "涂装施工",
        "courses": [
          {
            "code": "B0301",
            "name": "高压无气喷涂操作",
            "stdHours": 8,
            "required": false
          },
          {
            "code": "B0302",
            "name": "刷涂/滚涂与复杂部位处理",
            "stdHours": 4,
            "required": false
          },
          {
            "code": "B0303",
            "name": "干膜/湿膜厚度控制与检测",
            "stdHours": 4,
            "required": false
          },
          {
            "code": "B0304",
            "name": "漏点（电火花）检测",
            "stdHours": 3,
            "required": false
          },
          {
            "code": "B0305",
            "name": "涂层缺陷识别与返修",
            "stdHours": 4,
            "required": false
          }
        ]
      },
      {
        "code": "B04",
        "name": "储罐防腐",
        "courses": [
          {
            "code": "B0401",
            "name": "净化罐/污水罐内防腐工艺",
            "stdHours": 6,
            "required": false
          },
          {
            "code": "B0402",
            "name": "油罐、沉降罐内外防腐",
            "stdHours": 6,
            "required": false
          },
          {
            "code": "B0403",
            "name": "塔罐及球罐防腐",
            "stdHours": 4,
            "required": false
          },
          {
            "code": "B0404",
            "name": "储罐内衬玻璃钢/陶瓷施工",
            "stdHours": 6,
            "required": false
          }
        ]
      },
      {
        "code": "B05",
        "name": "管线外防腐",
        "courses": [
          {
            "code": "B0501",
            "name": "3PE 三层聚乙烯外防腐工艺",
            "stdHours": 8,
            "required": false
          },
          {
            "code": "B0502",
            "name": "环氧粉末（FBE）熔结防腐",
            "stdHours": 6,
            "required": false
          },
          {
            "code": "B0503",
            "name": "地面管线防腐施工",
            "stdHours": 4,
            "required": false
          },
          {
            "code": "B0504",
            "name": "井下管线与注水油管防腐",
            "stdHours": 4,
            "required": false
          },
          {
            "code": "B0505",
            "name": "弯管、冷器管、加热炉盘管防腐",
            "stdHours": 4,
            "required": false
          }
        ]
      },
      {
        "code": "B06",
        "name": "补口补伤",
        "courses": [
          {
            "code": "B0601",
            "name": "热收缩套/带补口工艺",
            "stdHours": 6,
            "required": false
          },
          {
            "code": "B0602",
            "name": "补伤与搭接质量控制",
            "stdHours": 3,
            "required": false
          }
        ]
      },
      {
        "code": "B07",
        "name": "内涂",
        "courses": [
          {
            "code": "B0701",
            "name": "管道内涂工艺与设备",
            "stdHours": 6,
            "required": false
          },
          {
            "code": "B0702",
            "name": "内涂层质量检测",
            "stdHours": 3,
            "required": false
          }
        ]
      },
      {
        "code": "B08",
        "name": "特种防腐",
        "courses": [
          {
            "code": "B0801",
            "name": "环氧玻璃钢防腐",
            "stdHours": 4,
            "required": false
          },
          {
            "code": "B0802",
            "name": "环氧陶瓷/金属陶瓷防腐",
            "stdHours": 4,
            "required": false
          },
          {
            "code": "B0803",
            "name": "耐磨油管防腐",
            "stdHours": 4,
            "required": false
          },
          {
            "code": "B0804",
            "name": "井口与设备防腐",
            "stdHours": 4,
            "required": false
          },
          {
            "code": "B0805",
            "name": "防蜡防垢技术",
            "stdHours": 3,
            "required": false
          }
        ]
      }
    ]
  },
  {
    "code": "C",
    "name": "保温绝热与保冷",
    "groups": [
      {
        "code": "C01",
        "name": "保温基础",
        "courses": [
          {
            "code": "C0101",
            "name": "保温材料认知与选型",
            "stdHours": 3,
            "required": false
          },
          {
            "code": "C0102",
            "name": "保温结构层次与厚度计算",
            "stdHours": 4,
            "required": false
          }
        ]
      },
      {
        "code": "C02",
        "name": "保温施工",
        "courses": [
          {
            "code": "C0201",
            "name": "管道保温施工工艺",
            "stdHours": 6,
            "required": false
          },
          {
            "code": "C0202",
            "name": "设备/储罐保温施工",
            "stdHours": 6,
            "required": false
          },
          {
            "code": "C0203",
            "name": "铁皮/铝皮保护层制作与安装",
            "stdHours": 6,
            "required": false
          },
          {
            "code": "C0204",
            "name": "阀门、法兰等异形件保温",
            "stdHours": 4,
            "required": false
          }
        ]
      },
      {
        "code": "C03",
        "name": "防腐绝热保冷",
        "courses": [
          {
            "code": "C0301",
            "name": "保冷工艺与材料（聚氨酯/泡沫玻璃）",
            "stdHours": 6,
            "required": false
          },
          {
            "code": "C0302",
            "name": "防潮层与隔汽层施工",
            "stdHours": 4,
            "required": false
          },
          {
            "code": "C0303",
            "name": "低温装置保冷质量控制",
            "stdHours": 4,
            "required": false
          }
        ]
      },
      {
        "code": "C04",
        "name": "冬防与维护",
        "courses": [
          {
            "code": "C0401",
            "name": "冬防保温方案与实施",
            "stdHours": 4,
            "required": false
          },
          {
            "code": "C0402",
            "name": "电伴热与蒸汽伴热配合",
            "stdHours": 3,
            "required": false
          },
          {
            "code": "C0403",
            "name": "保温拆装恢复作业",
            "stdHours": 4,
            "required": false
          }
        ]
      }
    ]
  },
  {
    "code": "D",
    "name": "内衬与管杆修复",
    "groups": [
      {
        "code": "D01",
        "name": "内衬技术",
        "courses": [
          {
            "code": "D0101",
            "name": "管道内衬修复工艺总览",
            "stdHours": 4,
            "required": false
          },
          {
            "code": "D0102",
            "name": "高分子聚乙烯内衬施工",
            "stdHours": 6,
            "required": false
          },
          {
            "code": "D0103",
            "name": "非金属油管内衬",
            "stdHours": 6,
            "required": false
          },
          {
            "code": "D0104",
            "name": "内衬管选型与采购要点",
            "stdHours": 3,
            "required": false
          }
        ]
      },
      {
        "code": "D02",
        "name": "油管油杆修复",
        "courses": [
          {
            "code": "D0201",
            "name": "油管修复流程与检验",
            "stdHours": 6,
            "required": false
          },
          {
            "code": "D0202",
            "name": "油杆修复与校直",
            "stdHours": 6,
            "required": false
          },
          {
            "code": "D0203",
            "name": "摩擦焊修复油管接头接箍",
            "stdHours": 8,
            "required": false
          }
        ]
      },
      {
        "code": "D03",
        "name": "管道修复补强",
        "courses": [
          {
            "code": "D0301",
            "name": "管道缺陷评估与修复选型",
            "stdHours": 4,
            "required": false
          },
          {
            "code": "D0302",
            "name": "补强套筒/复合材料补强",
            "stdHours": 6,
            "required": false
          },
          {
            "code": "D0303",
            "name": "带压堵漏技术",
            "stdHours": 6,
            "required": false
          }
        ]
      },
      {
        "code": "D04",
        "name": "套管与装备",
        "courses": [
          {
            "code": "D0401",
            "name": "套管修复工艺",
            "stdHours": 4,
            "required": false
          },
          {
            "code": "D0402",
            "name": "套筒修复",
            "stdHours": 4,
            "required": false
          },
          {
            "code": "D0403",
            "name": "钻头及装备修复",
            "stdHours": 4,
            "required": false
          }
        ]
      }
    ]
  },
  {
    "code": "E",
    "name": "检测与理化试验",
    "groups": [
      {
        "code": "E01",
        "name": "无损检测基础",
        "courses": [
          {
            "code": "E0101",
            "name": "无损检测方法总览与选用",
            "stdHours": 4,
            "required": false
          },
          {
            "code": "E0102",
            "name": "超声检测（UT）基础与实操",
            "stdHours": 12,
            "required": false
          },
          {
            "code": "E0103",
            "name": "磁粉检测（MT）",
            "stdHours": 8,
            "required": false
          },
          {
            "code": "E0104",
            "name": "渗透检测（PT）",
            "stdHours": 8,
            "required": false
          },
          {
            "code": "E0105",
            "name": "射线检测（RT）与辐射安全",
            "stdHours": 12,
            "required": false
          },
          {
            "code": "E0106",
            "name": "涡流/漏磁检测",
            "stdHours": 8,
            "required": false
          }
        ]
      },
      {
        "code": "E02",
        "name": "管材与管件检测",
        "courses": [
          {
            "code": "E0201",
            "name": "废油管检测与分级",
            "stdHours": 4,
            "required": false
          },
          {
            "code": "E0202",
            "name": "钻杆、钻具油套管检测",
            "stdHours": 6,
            "required": false
          },
          {
            "code": "E0203",
            "name": "高压管件检测",
            "stdHours": 4,
            "required": false
          },
          {
            "code": "E0204",
            "name": "压力管道检测",
            "stdHours": 8,
            "required": false
          },
          {
            "code": "E0205",
            "name": "工业管道与长输油管检测",
            "stdHours": 8,
            "required": false
          }
        ]
      },
      {
        "code": "E03",
        "name": "容器与设备检测",
        "courses": [
          {
            "code": "E0301",
            "name": "压力容器检测",
            "stdHours": 8,
            "required": false
          },
          {
            "code": "E0302",
            "name": "储罐检测（含罐底漏磁）",
            "stdHours": 6,
            "required": false
          },
          {
            "code": "E0303",
            "name": "球罐检测",
            "stdHours": 6,
            "required": false
          },
          {
            "code": "E0304",
            "name": "锅炉检测",
            "stdHours": 6,
            "required": false
          },
          {
            "code": "E0305",
            "name": "设备与设施检测",
            "stdHours": 4,
            "required": false
          },
          {
            "code": "E0306",
            "name": "机井架检测",
            "stdHours": 4,
            "required": false
          }
        ]
      },
      {
        "code": "E04",
        "name": "专项检测",
        "courses": [
          {
            "code": "E0401",
            "name": "气密性检测",
            "stdHours": 4,
            "required": false
          },
          {
            "code": "E0402",
            "name": "气体与固体检测",
            "stdHours": 4,
            "required": false
          },
          {
            "code": "E0403",
            "name": "防雷、防爆检测",
            "stdHours": 6,
            "required": false
          },
          {
            "code": "E0404",
            "name": "输电线路与变电站检测",
            "stdHours": 6,
            "required": false
          },
          {
            "code": "E0405",
            "name": "井下材料检测",
            "stdHours": 4,
            "required": false
          }
        ]
      },
      {
        "code": "E05",
        "name": "理化与计量",
        "courses": [
          {
            "code": "E0501",
            "name": "理化试验基础（力学/金相）",
            "stdHours": 8,
            "required": false
          },
          {
            "code": "E0502",
            "name": "计量器具检定与校准",
            "stdHours": 4,
            "required": false
          },
          {
            "code": "E0503",
            "name": "检测报告编制与数据判定",
            "stdHours": 4,
            "required": false
          }
        ]
      }
    ]
  },
  {
    "code": "F",
    "name": "材料与采购",
    "groups": [
      {
        "code": "F01",
        "name": "材料认知",
        "courses": [
          {
            "code": "F0101",
            "name": "防腐材料技术指标与验收",
            "stdHours": 4,
            "required": false
          },
          {
            "code": "F0102",
            "name": "天然气管材认知",
            "stdHours": 4,
            "required": false
          },
          {
            "code": "F0103",
            "name": "保温材料进场检验",
            "stdHours": 3,
            "required": false
          }
        ]
      },
      {
        "code": "F02",
        "name": "采购与仓储",
        "courses": [
          {
            "code": "F0201",
            "name": "防腐材料采购流程与供方管理",
            "stdHours": 3,
            "required": false
          },
          {
            "code": "F0202",
            "name": "材料储存、保质期与领用管理",
            "stdHours": 3,
            "required": false
          }
        ]
      }
    ]
  },
  {
    "code": "G",
    "name": "施工管理与项目交付",
    "groups": [
      {
        "code": "G01",
        "name": "工艺与方案",
        "courses": [
          {
            "code": "G0101",
            "name": "施工方案编制与技术交底",
            "stdHours": 4,
            "required": false
          },
          {
            "code": "G0102",
            "name": "工艺卡与作业指导书应用",
            "stdHours": 3,
            "required": false
          }
        ]
      },
      {
        "code": "G02",
        "name": "质量与验收",
        "courses": [
          {
            "code": "G0201",
            "name": "防腐保温工程验收标准",
            "stdHours": 4,
            "required": false
          },
          {
            "code": "G0202",
            "name": "不合格品处置与返工管理",
            "stdHours": 3,
            "required": false
          }
        ]
      },
      {
        "code": "G03",
        "name": "项目与班组",
        "courses": [
          {
            "code": "G0301",
            "name": "班组日常管理与派工",
            "stdHours": 4,
            "required": false
          },
          {
            "code": "G0302",
            "name": "工程量确认与结算基础",
            "stdHours": 4,
            "required": false
          },
          {
            "code": "G0303",
            "name": "产能建设管线项目要点",
            "stdHours": 4,
            "required": false
          },
          {
            "code": "G0304",
            "name": "天然气处理厂工程要点",
            "stdHours": 4,
            "required": false
          },
          {
            "code": "G0305",
            "name": "维修工程组织与管理",
            "stdHours": 4,
            "required": false
          }
        ]
      }
    ]
  },
  {
    "code": "H",
    "name": "数字化与平台工具",
    "groups": [
      {
        "code": "H01",
        "name": "平台操作",
        "courses": [
          {
            "code": "H0101",
            "name": "MES 系统基础操作",
            "stdHours": 4,
            "required": false
          },
          {
            "code": "H0102",
            "name": "生产数据与报工录入规范",
            "stdHours": 3,
            "required": false
          },
          {
            "code": "H0103",
            "name": "施工影像与资料上传规范",
            "stdHours": 2,
            "required": false
          }
        ]
      },
      {
        "code": "H02",
        "name": "数据与经营",
        "courses": [
          {
            "code": "H0201",
            "name": "报表看板阅读与使用",
            "stdHours": 2,
            "required": false
          },
          {
            "code": "H0202",
            "name": "招投标信息识别与响应基础",
            "stdHours": 3,
            "required": false
          }
        ]
      }
    ]
  }
];
