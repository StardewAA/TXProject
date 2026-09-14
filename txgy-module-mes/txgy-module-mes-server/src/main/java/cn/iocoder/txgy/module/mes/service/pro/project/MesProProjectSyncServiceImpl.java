package cn.iocoder.txgy.module.mes.service.pro.project;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.txgy.framework.common.enums.CommonStatusEnum;
import cn.iocoder.txgy.framework.tenant.core.util.TenantUtils;
import cn.iocoder.txgy.module.erp.api.sale.ErpSaleOrderApi;
import cn.iocoder.txgy.module.erp.api.sale.dto.ErpSaleOrderItemRespDTO;
import cn.iocoder.txgy.module.erp.api.sale.dto.ErpSaleOrderRespDTO;
import cn.iocoder.txgy.module.mes.dal.dataobject.md.item.MesMdItemDO;
import cn.iocoder.txgy.module.mes.dal.dataobject.pro.project.MesProProjectDO;
import cn.iocoder.txgy.module.mes.dal.dataobject.pro.project.MesProProjectSyncTaskDO;
import cn.iocoder.txgy.module.mes.dal.dataobject.pro.route.MesProRouteDO;
import cn.iocoder.txgy.module.mes.dal.dataobject.pro.route.MesProRouteProductDO;
import cn.iocoder.txgy.module.mes.dal.dataobject.pro.route.MesProRouteProcessDO;
import cn.iocoder.txgy.module.mes.dal.dataobject.pro.workorder.MesProWorkOrderDO;
import cn.iocoder.txgy.module.mes.dal.mysql.md.item.MesMdItemMapper;
import cn.iocoder.txgy.module.mes.dal.mysql.pro.project.MesProProjectMapper;
import cn.iocoder.txgy.module.mes.dal.mysql.pro.project.MesProProjectSyncTaskMapper;
import cn.iocoder.txgy.module.mes.dal.mysql.pro.route.MesProRouteProductMapper;
import cn.iocoder.txgy.module.mes.dal.mysql.pro.workorder.MesProWorkOrderMapper;
import cn.iocoder.txgy.module.mes.enums.pro.MesProProjectStatusEnum;
import cn.iocoder.txgy.module.mes.enums.pro.MesProWorkOrderSourceTypeEnum;
import cn.iocoder.txgy.module.mes.enums.pro.MesProWorkOrderStatusEnum;
import cn.iocoder.txgy.module.mes.enums.pro.MesProWorkOrderTypeEnum;
import cn.iocoder.txgy.module.mes.service.pro.route.MesProRouteProcessService;
import cn.iocoder.txgy.module.mes.service.pro.route.MesProRouteProductService;
import cn.iocoder.txgy.module.mes.service.pro.route.MesProRouteService;
import cn.iocoder.txgy.module.mes.service.pro.task.MesProTaskService;
import cn.iocoder.txgy.module.mes.service.pro.workorder.MesProWorkOrderService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.mes.enums.ErrorCodeConstants.PRO_PROJECT_SALE_ORDER_NOT_APPROVED;
import static cn.iocoder.txgy.module.mes.enums.ErrorCodeConstants.PRO_PROJECT_SYNC_TASK_NOT_EXISTS;

/**
 * MES 项目同步 Service 实现类
 *
 * 流程：BPM 审批事件 → 同步任务落库 → 定时任务消费 → 查询 ERP 订单 → 幂等创建项目
 *
 * 幂等保障（双层）：
 * 1. 任务表按 saleOrderId 查重，同一订单不会重复建任务
 * 2. 项目表按 sourceType=1 + orderSourceCode 查重 + DB 唯一索引兜底
 *
 * @author OPENLAB BS
 */
@Slf4j
@Service
@Validated
public class MesProProjectSyncServiceImpl implements MesProProjectSyncService {

    /** ERP 销售订单审核状态：审批通过 */
    private static final Integer ERP_STATUS_APPROVE = 20;
    /** ERP 销售订单审核状态：审批中（ERP 事务可能尚未提交，等待重试） */
    private static final Integer ERP_STATUS_PROCESS = 10;
    /** ERP 销售订单审核状态：草稿 */
    private static final Integer ERP_STATUS_DRAFT = 0;
    /** 项目来源类型：销售订单 */
    private static final Integer SOURCE_TYPE_SALE_ORDER = 1;
    /** 项目优先级默认值：中 */
    private static final Integer DEFAULT_PRIORITY = 3;
    /** 工单类型：自行生产 */
    private static final Integer WORK_ORDER_TYPE_SELF = MesProWorkOrderTypeEnum.SELF.getType();
    /** 工单来源类型：客户订单 */
    private static final Integer WORK_ORDER_SOURCE_ORDER = MesProWorkOrderSourceTypeEnum.ORDER.getType();
    /** 工单状态：草稿（待确认） */
    private static final Integer WORK_ORDER_STATUS_PREPARE = MesProWorkOrderStatusEnum.PREPARE.getStatus();
    /** 单次重试间隔基数（分钟），随重试次数递增 */
    private static final int RETRY_INTERVAL_MINUTES = 2;

    @Resource
    private MesProProjectMapper projectMapper;
    @Resource
    private MesProProjectSyncTaskMapper syncTaskMapper;
    @Resource
    private MesProWorkOrderMapper workOrderMapper;
    @Resource
    private MesMdItemMapper itemMapper;
    @Resource
    private MesProWorkOrderService workOrderService;
    @Resource
    private MesProTaskService taskService;
    @Resource
    private MesProRouteProductService routeProductService;
    @Resource
    private MesProRouteProductMapper routeProductMapper;
    @Resource
    private MesProRouteService routeService;
    @Resource
    private MesProRouteProcessService routeProcessService;
    @Resource
    private ErpSaleOrderApi erpSaleOrderApi;

    @Override
    public Long createSyncTask(Long saleOrderId) {
        // 1. 幂等：同一订单已有任务时，成功/待处理/失败均不重复创建（失败任务走手动重试）
        MesProProjectSyncTaskDO existTask = syncTaskMapper.selectBySaleOrderId(saleOrderId);
        if (existTask != null) {
            return existTask.getId();
        }
        // 2. 创建待处理任务，由定时任务异步消费
        MesProProjectSyncTaskDO task = MesProProjectSyncTaskDO.builder()
                .saleOrderId(saleOrderId)
                .status(MesProProjectSyncTaskDO.STATUS_PENDING)
                .retryCount(0)
                .nextRetryTime(LocalDateTime.now())
                .build();
        syncTaskMapper.insert(task);
        return task.getId();
    }

    /**
     * 定时扫描待处理任务。
     *
     * 初次延迟 30 秒、之后每 30 秒扫描一次：给 BPM 并行转发给 ERP 的事务提交留出时间窗口。
     * 任务表为跨租户扫描（TenantBaseDO 自动带租户隔离），需 ignore 后统一查出，再逐条切租户处理。
     */
    @Override
    @Scheduled(initialDelay = 30_000, fixedDelay = 30_000)
    public void processPendingTasks() {
        List<MesProProjectSyncTaskDO> tasks = TenantUtils.executeIgnore(() ->
                syncTaskMapper.selectPendingList(LocalDateTime.now()));
        if (tasks == null || tasks.isEmpty()) {
            return;
        }
        for (MesProProjectSyncTaskDO task : tasks) {
            try {
                // 切换到任务所属租户上下文，保证查询 ERP、写入项目表的租户隔离正确
                TenantUtils.execute(task.getTenantId(), () -> processTask(task));
            } catch (Exception e) {
                log.error("[processPendingTasks][任务({}) 处理异常]", task.getId(), e);
            }
        }
    }

    @Override
    public void processTask(MesProProjectSyncTaskDO task) {
        try {
            // 1. 查询 ERP 销售订单
            ErpSaleOrderRespDTO saleOrder = erpSaleOrderApi.getSaleOrder(task.getSaleOrderId()).getCheckedData();
            // 2. 订单不存在：可能尚未提交或已被删除，进入重试
            if (saleOrder == null) {
                markRetry(task, "ERP 销售订单不存在，等待重试");
                return;
            }
            // 3. 订单未审批：审批中属于正常时序（ERP 事务未提交），等待重试
            if (Objects.equals(saleOrder.getStatus(), ERP_STATUS_DRAFT)
                    || Objects.equals(saleOrder.getStatus(), ERP_STATUS_PROCESS)) {
                markRetry(task, "销售订单尚未审批完成（当前状态 " + saleOrder.getStatus() + "），等待重试");
                return;
            }
            // 4. 订单被驳回/撤销：无需建项目，任务跳过
            if (!Objects.equals(saleOrder.getStatus(), ERP_STATUS_APPROVE)) {
                markSkipped(task, "销售订单已驳回或撤销（状态 " + saleOrder.getStatus() + "），无需生成项目");
                return;
            }
            // 5. 幂等：来源单据已生成过项目；补生成工单（保证订单行→工单在重试时也能补齐）
            MesProProjectDO existProject = projectMapper.selectBySourceTypeAndOrderSourceCode(
                    SOURCE_TYPE_SALE_ORDER, saleOrder.getNo());
            if (existProject != null) {
                createWorkOrdersFromSaleOrder(saleOrder, existProject.getId(), existProject.getCode());
                markSuccess(task, existProject.getId());
                return;
            }
            // 6. 创建项目并同步按订单行生成工单
            Long projectId = createProjectFromSaleOrder(saleOrder);
            markSuccess(task, projectId);
        } catch (Exception e) {
            log.error("[processTask][任务({}) 订单({}) 同步失败]", task.getId(), task.getSaleOrderId(), e);
            markRetry(task, StrUtil.maxLength(e.getMessage(), 500));
        }
    }
    /**
     * 由销售订单创建生产项目
     *
     * 项目编码按 MES 规则生成（PRJ + 日期 + 序号），ERP 订单号记录在 orderSourceCode。
     * 负责人暂不自动绑定（销售员不一定是项目负责人），由生产科确认后补充。
     *
     * @param saleOrder 销售订单
     * @return 项目编号
     */
    private Long createProjectFromSaleOrder(ErpSaleOrderRespDTO saleOrder) {
        // 1. 校验订单号
        String orderNo = saleOrder.getNo();
        if (StrUtil.isBlank(orderNo)) {
            throw exception(PRO_PROJECT_SALE_ORDER_NOT_APPROVED, "销售订单号为空");
        }
        // 2. 组装项目
        MesProProjectDO project = MesProProjectDO.builder()
                .code(generateProjectCode())
                .name(buildProjectName(saleOrder))
                .sourceType(SOURCE_TYPE_SALE_ORDER)
                .orderSourceCode(orderNo)
                .status(MesProProjectStatusEnum.NOT_STARTED.getStatus())
                .priority(DEFAULT_PRIORITY)
                .planStartTime(saleOrder.getOrderTime())
                .remark("由销售订单 " + orderNo + " 审批通过自动生成，请确认负责人与工期")
                .build();
        projectMapper.insert(project);
        // 同步按订单行生成生产工单
        createWorkOrdersFromSaleOrder(saleOrder, project.getId(), project.getCode());
        return project.getId();
    }

    /**
     * 由销售订单明细行生成生产工单（每个订单行一张工单）。
     *
     * 幂等：按确定性工单编码（WO + 项目编码 + 行号）查重，已存在则跳过，保证定时任务重试不会重复建单。
     * ERP 产品与 MES 物料为两套独立主数据，通过 material_code 关联；若 MES 侧无对应物料则自动建一条占位物料。
     *
     * @param saleOrder   销售订单
     * @param projectId   已生成的生产项目编号
     * @param projectCode 已生成的生产项目编码
     */
    private void createWorkOrdersFromSaleOrder(ErpSaleOrderRespDTO saleOrder, Long projectId, String projectCode) {
        List<ErpSaleOrderItemRespDTO> items = saleOrder.getItems();
        if (CollUtil.isEmpty(items)) {
            return;
        }
        int index = 0;
        for (ErpSaleOrderItemRespDTO item : items) {
            index++;
            String code = "WO" + projectCode + "-" + index;
            // 幂等：工单已存在则复用（补下发/补拆任务）
            MesProWorkOrderDO workOrder = workOrderMapper.selectByCode(code);
            if (workOrder == null) {
                Long productId = resolveOrCreateMesItem(item);
                workOrder = new MesProWorkOrderDO();
                workOrder.setCode(code);
                workOrder.setName(StrUtil.maxLength(
                        StrUtil.blankToDefault(item.getProductName(), "产品") + " 生产工单", 100));
                workOrder.setType(WORK_ORDER_TYPE_SELF);
                workOrder.setOrderSourceType(WORK_ORDER_SOURCE_ORDER);
                workOrder.setOrderSourceCode(saleOrder.getNo());
                workOrder.setProjectId(projectId);
                workOrder.setProductId(productId);
                workOrder.setQuantity(item.getCount() != null ? item.getCount() : BigDecimal.ZERO);
                workOrder.setQuantityProduced(BigDecimal.ZERO);
                workOrder.setRequestDate(saleOrder.getOrderTime() != null ? saleOrder.getOrderTime() : LocalDateTime.now());
                workOrder.setStatus(WORK_ORDER_STATUS_PREPARE);
                workOrder.setRemark("由销售订单 " + saleOrder.getNo() + " 第" + index + "行自动生成");
                workOrderMapper.insert(workOrder);
            }
            // 自动确认→下发→按工艺路线拆分任务（幂等，失败不阻断项目同步）
            autoIssueAndSplitTasks(workOrder);
        }
    }

    /**
     * 自动推进工单状态机并拆分任务：草稿→已确认→已下发→按工艺路线生成工序任务。
     *
     * 幂等：仅处理草稿/已确认状态的工单；任务生成按工序查重，重复调用只补缺失工序。
     * best-effort：产品未绑定工艺路线时自动绑定一条启用且有工序的默认路线；
     * 全程失败仅记录日志，不阻断项目同步（项目与工单已落库）。
     */
    private void autoIssueAndSplitTasks(MesProWorkOrderDO workOrder) {
        Integer status = workOrder.getStatus();
        if (Objects.equals(status, MesProWorkOrderStatusEnum.CANCELED.getStatus())
                || Objects.equals(status, MesProWorkOrderStatusEnum.FINISHED.getStatus())) {
            return; // 终态工单不处理
        }
        try {
            // 1. 未下发的先推进状态机：确保绑定工艺路线 → 确认 → 下发
            if (Objects.equals(status, MesProWorkOrderStatusEnum.PREPARE.getStatus())
                    || Objects.equals(status, MesProWorkOrderStatusEnum.CONFIRMED.getStatus())) {
                if (routeProductService.getRouteProductByItemId(workOrder.getProductId()) == null) {
                    Long routeId = findDefaultRouteId();
                    if (routeId == null) {
                        log.warn("[autoIssueAndSplitTasks][工单({}) 产品({}) 无可用工艺路线，保持当前状态待人工绑定]",
                                workOrder.getId(), workOrder.getProductId());
                        return;
                    }
                    MesProRouteProductDO binding = new MesProRouteProductDO();
                    binding.setRouteId(routeId);
                    binding.setItemId(workOrder.getProductId());
                    binding.setRemark("销售订单同步自动绑定默认工艺路线");
                    routeProductMapper.insert(binding);
                }
                if (Objects.equals(status, MesProWorkOrderStatusEnum.PREPARE.getStatus())) {
                    workOrderService.confirmWorkOrder(workOrder.getId());
                }
                workOrderService.issueWorkOrder(workOrder.getId());
            }
            // 2. 按工艺路线拆分工序任务（幂等：已下发/生产中的工单缺任务时也能补齐）
            int taskCount = taskService.autoGenerateTasksByRoute(workOrder.getId());
            log.info("[autoIssueAndSplitTasks][工单({}) 已自动下发并拆分 {} 个任务]", workOrder.getId(), taskCount);
        } catch (Exception e) {
            log.error("[autoIssueAndSplitTasks][工单({}) 自动下发/拆任务失败，保持当前状态]", workOrder.getId(), e);
        }
    }

    /**
     * 查找默认工艺路线：第一条启用且有工序的路线
     */
    private Long findDefaultRouteId() {
        List<MesProRouteDO> routes = routeService.getRouteListByStatus(CommonStatusEnum.ENABLE.getStatus());
        if (CollUtil.isEmpty(routes)) {
            return null;
        }
        for (MesProRouteDO route : routes) {
            List<MesProRouteProcessDO> routeProcesses =
                    routeProcessService.getRouteProcessListByRouteId(route.getId());
            if (CollUtil.isNotEmpty(routeProcesses)) {
                return route.getId();
            }
        }
        return null;
    }

    /**
     * 将 ERP 产品解析为 MES 物料：按 material_code 关联 MES 物料 code，存在则返回其编号，
     * 不存在则自动创建一条占位物料（建立 ERP 产品与 MES 物料的关联），返回新建物料编号。
     */
    private Long resolveOrCreateMesItem(ErpSaleOrderItemRespDTO item) {
        String materialCode = item.getMaterialCode();
        if (StrUtil.isNotBlank(materialCode)) {
            MesMdItemDO existItem = itemMapper.selectByCode(materialCode);
            if (existItem != null) {
                return existItem.getId();
            }
            MesMdItemDO newItem = new MesMdItemDO();
            newItem.setCode(materialCode);
            newItem.setName(StrUtil.maxLength(
                    StrUtil.blankToDefault(item.getProductName(), "ERP产品-" + item.getProductId()), 100));
            newItem.setUnitMeasureId(item.getProductUnitId() != null ? item.getProductUnitId() : 0L);
            newItem.setItemTypeId(0L);
            newItem.setStatus(CommonStatusEnum.ENABLE.getStatus());
            newItem.setBatchFlag(Boolean.FALSE);
            newItem.setSafeStockFlag(Boolean.FALSE);
            itemMapper.insert(newItem);
            return newItem.getId();
        }
        // 无 material_code 时退化为直接使用 ERP 产品编号
        return item.getProductId();
    }

    /**
     * 生成项目编码：PRJ + yyyyMMdd + 4 位当日序号，如 PRJ202609100001
     *
     * @return 项目编码
     */
    private String generateProjectCode() {
        String datePart = LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"));
        String prefix = "PRJ" + datePart;
        // 查询当日最大序号
        List<MesProProjectDO> todayProjects = projectMapper.selectListByCodeLike(prefix);
        int maxSeq = 0;
        for (MesProProjectDO project : todayProjects) {
            String code = project.getCode();
            if (code != null && code.length() > prefix.length()) {
                try {
                    maxSeq = Math.max(maxSeq, Integer.parseInt(code.substring(prefix.length())));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return prefix + String.format("%04d", maxSeq + 1);
    }

    /**
     * 项目名称：客户名-订单号；客户名为空时退化为 销售订单-订单号
     *
     * @param saleOrder 销售订单
     * @return 项目名称
     */
    private String buildProjectName(ErpSaleOrderRespDTO saleOrder) {
        String customerName = StrUtil.blankToDefault(saleOrder.getCustomerName(), "销售订单");
        String name = customerName + "-" + saleOrder.getNo();
        return StrUtil.maxLength(name, 100);
    }

    private void markSuccess(MesProProjectSyncTaskDO task, Long projectId) {
        MesProProjectSyncTaskDO updateObj = new MesProProjectSyncTaskDO();
        updateObj.setId(task.getId());
        updateObj.setStatus(MesProProjectSyncTaskDO.STATUS_SUCCESS);
        updateObj.setProjectId(projectId);
        updateObj.setErrorMessage("");
        syncTaskMapper.updateById(updateObj);
    }

    private void markSkipped(MesProProjectSyncTaskDO task, String reason) {
        MesProProjectSyncTaskDO updateObj = new MesProProjectSyncTaskDO();
        updateObj.setId(task.getId());
        updateObj.setStatus(MesProProjectSyncTaskDO.STATUS_SKIPPED);
        updateObj.setErrorMessage(reason);
        syncTaskMapper.updateById(updateObj);
    }

    /**
     * 重试：递增重试次数，超过上限标记失败，否则推迟下次重试时间
     *
     * @param task   任务
     * @param reason 本轮失败原因
     */
    private void markRetry(MesProProjectSyncTaskDO task, String reason) {
        int retryCount = (task.getRetryCount() == null ? 0 : task.getRetryCount()) + 1;
        MesProProjectSyncTaskDO updateObj = new MesProProjectSyncTaskDO();
        updateObj.setId(task.getId());
        updateObj.setRetryCount(retryCount);
        updateObj.setErrorMessage(reason);
        if (retryCount >= MesProProjectSyncTaskDO.MAX_RETRY_COUNT) {
            updateObj.setStatus(MesProProjectSyncTaskDO.STATUS_FAILED);
        } else {
            updateObj.setStatus(MesProProjectSyncTaskDO.STATUS_PENDING);
            updateObj.setNextRetryTime(LocalDateTime.now().plusMinutes((long) RETRY_INTERVAL_MINUTES * retryCount));
        }
        syncTaskMapper.updateById(updateObj);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void retrySyncTask(Long id) {
        MesProProjectSyncTaskDO task = syncTaskMapper.selectById(id);
        if (task == null) {
            throw exception(PRO_PROJECT_SYNC_TASK_NOT_EXISTS);
        }
        // 重置为待处理并立即消费一次
        MesProProjectSyncTaskDO updateObj = new MesProProjectSyncTaskDO();
        updateObj.setId(id);
        updateObj.setStatus(MesProProjectSyncTaskDO.STATUS_PENDING);
        updateObj.setRetryCount(0);
        updateObj.setNextRetryTime(LocalDateTime.now());
        syncTaskMapper.updateById(updateObj);
        task.setStatus(MesProProjectSyncTaskDO.STATUS_PENDING);
        task.setRetryCount(0);
        processTask(task);
    }

}
