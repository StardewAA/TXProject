/** ERP 首页助手使用的脱敏样例数据，仅用于联调和演示，不写入数据库。 */
export interface AssistantSalesOrder {
  amount: number;
  customer: string;
  customerId: number;
  id: number;
  no: string;
  product: string;
  productId: number;
  orderTime: string;
  quantity: number;
  status: string;
  statusCode: number;
  unit: string;
}

export interface AssistantPurchaseOrder {
  amount: number;
  no: string;
  product: string;
  status: string;
  supplier: string;
}

export interface AssistantStockItem {
  available: number;
  locked: number;
  product: string;
  unit: string;
  warehouse: string;
}

export interface AssistantLogistics {
  carrier: string;
  currentNode: string;
  no: string;
  orderNo: string;
  status: string;
}

export interface AssistantMallOrder {
  no: string;
  product: string;
  purchaseNo: string;
  shortage: number;
  status: string;
}

export const assistantDemoData = {
  approvals: [
    { applicant: '业务员A', no: 'SP-260904-001', type: '销售订单', status: '待负责人审批' },
    { applicant: '采购员B', no: 'CG-260904-002', type: '采购申请', status: '待财务复核' },
  ],
  crmCustomers: [
    { contact: '联系人A', level: '重点客户', name: '西北示例工程有限公司', nextAction: '2026-09-18 回访' },
    { contact: '联系人B', level: '普通客户', name: '陕北示例化工厂', nextAction: '待补充报价' },
  ],
  employees: [
    { department: '销售部', name: '员工A', status: '在职', today: '正常出勤' },
    { department: '采购部', name: '员工B', status: '在职', today: '外出办事' },
  ],
  logistics: [
    { carrier: '示例物流A', currentNode: '咸阳分拨中心', no: 'WL-260903-001', orderNo: 'TX-SO-260902-002', status: '运输中' },
    { carrier: '示例物流B', currentNode: '西安一号仓待揽收', no: 'WL-260904-002', orderNo: 'TX-SO-260903-003', status: '待发货' },
  ] as AssistantLogistics[],
  mallOrders: [
    { no: 'SC-260904-001', product: 'DN200防腐弯头', purchaseNo: 'TX-PO-260904-001', shortage: 24, status: '缺货待采购' },
    { no: 'SC-260904-002', product: '环氧防腐涂料', purchaseNo: 'TX-PO-260904-002', shortage: 12, status: '采购中' },
  ] as AssistantMallOrder[],
  purchases: [
    { amount: 15_600, no: 'TX-PO-260904-001', product: 'DN200防腐弯头', status: '待供应商确认', supplier: '示例供应商甲' },
    { amount: 8400, no: 'TX-PO-260904-002', product: '环氧防腐涂料', status: '采购中', supplier: '示例供应商乙' },
  ] as AssistantPurchaseOrder[],
  sales: [
    { amount: 18_600, customer: '西北示例工程有限公司', customerId: 960_101, id: 960_001, no: 'TX-SO-260901-001', orderTime: '2026-09-01 09:20:00', product: 'DN200防腐弯头', productId: 960_201, quantity: 24, status: '待审核', statusCode: 0, unit: '件' },
    { amount: 42_800, customer: '西部示例工程公司', customerId: 960_102, id: 960_002, no: 'TX-SO-260902-002', orderTime: '2026-09-02 14:35:00', product: '保温管件组合', productId: 960_202, quantity: 16, status: '已确认', statusCode: 20, unit: '套' },
    { amount: 9600, customer: '陕北示例化工厂', customerId: 960_103, id: 960_003, no: 'TX-SO-260903-003', orderTime: '2026-09-03 11:05:00', product: '环氧防腐涂层', productId: 960_203, quantity: 12, status: '待发货', statusCode: 20, unit: '桶' },
  ] as AssistantSalesOrder[],
  stock: [
    { available: 128, locked: 12, product: 'DN200防腐弯头', unit: '件', warehouse: '西安示例一号仓' },
    { available: 76, locked: 8, product: '环氧防腐涂料', unit: '桶', warehouse: '西安示例一号仓' },
    { available: 42, locked: 5, product: '保温管件组合', unit: '套', warehouse: '咸阳示例二号仓' },
  ] as AssistantStockItem[],
};
