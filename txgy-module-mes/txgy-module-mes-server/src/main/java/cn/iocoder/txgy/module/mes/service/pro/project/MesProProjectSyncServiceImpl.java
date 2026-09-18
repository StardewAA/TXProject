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
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.mes.controller.admin.pro.workorder.vo.MesProWorkOrderSaveReqVO;
import cn.iocoder.txgy.module.mes.enums.md.autocode.MesMdAutoCodeRuleCodeEnum;
import cn.iocoder.txgy.module.mes.enums.pro.MesProWorkOrderSourceTypeEnum;
import cn.iocoder.txgy.module.mes.enums.pro.MesProWorkOrderTypeEnum;
import cn.iocoder.txgy.module.mes.service.pro.route.MesProRouteProcessService;
import cn.iocoder.txgy.module.mes.service.pro.route.MesProRouteProductService;
import cn.iocoder.txgy.module.mes.service.pro.route.MesProRouteService;
import cn.iocoder.txgy.module.mes.service.md.autocode.MesMdAutoCodeRecordService;
import cn.iocoder.txgy.module.mes.service.pro.task.MesProTaskService;
import cn.iocoder.txgy.module.mes.service.pro.workorder.MesProWorkOrderService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
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
    /** 工单来源类型：ERP 订单 */
    private static final Integer WORK_ORDER_SOURCE_ERP = MesProWorkOrderSourceTypeEnum.ERP.getType();
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
    @Lazy
    private MesProWorkOrderService workOrderService;
    @Resource
    @Lazy
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
    @Resource
    private MesMdAutoCodeRecordService autoCodeRecordService;

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
                // 统一走同一套建单逻辑（内部按「订单+物料+项目」幂等，重试不会重复建单）
                generateWorkOrdersFromSaleOrder(existProject.getId(), saleOrder);
                markSuccess(task, existProject.getId());
                return;
            }
            // 6. 创建项目并同步按订单行生成工单
            Long projectId = createProjectFromSaleOrder(saleOrder);
            markSuccess(task, projectId);
            // 7. 按订单行自动生成生产工单并拆分任务（P3：订单→项目→工单→任务全自动链）
            generateWorkOrdersFromSaleOrder(projectId, saleOrder);
        } catch (Exception e) {
            log.error("[processTask][任务({}) 订单({}) 同步失败]", task.getId(), task.getSaleOrderId(), e);
            markRetry(task, StrUtil.maxLength(e.getMessage(), 500));
        }
    }

    /**
     * 按销售订单行自动生成生产工单并拆分任务（P3 全自动链的最后一环）。
     *
     * 每行生成一个自行生产工单（来源=ERP、绑定项目）；ERP 产品通过 material_code 关联 MES 物料，
     * MES 无对应物料时自动建一条占位物料。
     * 幂等：按「来源单号 + 来源类型 + 物料 + 项目」查重，定时任务重试不会重复建单。
     * 建单后自动确保工艺路线（未绑定时绑定默认路线）→ 确认 → 下发 → 按工艺路线拆分任务。
     * 任一行失败不影响其余行，失败明细记录在日志中（项目已创建，人工可补建工单）。
     *
     * @param projectId 项目编号
     * @param saleOrder 销售订单（含订单行明细）
     */
    private void generateWorkOrdersFromSaleOrder(Long projectId, ErpSaleOrderRespDTO saleOrder) {
        List<ErpSaleOrderItemRespDTO> items = saleOrder.getItems();
        if (CollUtil.isEmpty(items)) {
            log.info("[generateWorkOrdersFromSaleOrder][订单({}) 无订单行明细，跳过工单生成]", saleOrder.getNo());
            return;
        }
        int created = 0;
        int index = 0;
        for (ErpSaleOrderItemRespDTO item : items) {
            index++;
            try {
                // 1. ERP 产品 → MES 物料（两套主数据体系，按 material_code 关联；不存在则自动建占位物料）
                Long productId = resolveOrCreateMesItem(item);
                // 2. 幂等：同一订单 + 同一物料 + 同一项目已生成过工单则跳过（重试/重复触发不重复建单）
                Long existCount = workOrderMapper.selectCount(new LambdaQueryWrapperX<MesProWorkOrderDO>()
                        .eq(MesProWorkOrderDO::getOrderSourceType, WORK_ORDER_SOURCE_ERP)
                        .eq(MesProWorkOrderDO::getOrderSourceCode, saleOrder.getNo())
                        .eq(MesProWorkOrderDO::getProjectId, projectId)
                        .eq(MesProWorkOrderDO::getProductId, productId));
                if (existCount != null && existCount > 0) {
                    log.info("[generateWorkOrdersFromSaleOrder][订单({}) 第{}行物料({})已存在工单，跳过]",
                            saleOrder.getNo(), index, productId);
                    continue;
                }
                // 3. 创建工单（ERP 来源，绑定项目，类型=自行生产）
                MesProWorkOrderSaveReqVO workOrder = new MesProWorkOrderSaveReqVO();
                workOrder.setCode(autoCodeRecordService.generateAutoCode(
                        MesMdAutoCodeRuleCodeEnum.PRO_WORK_ORDER_CODE.getCode()));
                workOrder.setName(StrUtil.maxLength(
                        StrUtil.blankToDefault(saleOrder.getCustomerName(), "销售订单") + "-"
                                + StrUtil.blankToDefault(item.getProductName(), "产品"), 100));
                workOrder.setType(MesProWorkOrderTypeEnum.SELF.getType());
                workOrder.setOrderSourceType(WORK_ORDER_SOURCE_ERP);
                workOrder.setOrderSourceCode(saleOrder.getNo());
                workOrder.setProjectId(projectId);
                workOrder.setProductId(productId);
                workOrder.setQuantity(item.getCount() != null ? item.getCount() : BigDecimal.ZERO);
                workOrder.setRequestDate(saleOrder.getOrderTime() != null
                        ? saleOrder.getOrderTime() : LocalDateTime.now());
                workOrder.setRemark("由销售订单 " + saleOrder.getNo() + " 第" + index + "行自动生成");
                Long workOrderId = workOrderService.createWorkOrder(workOrder);
                // 4. 确保产品已绑定工艺路线（下发接口会校验；未绑定时自动绑定默认路线）
                ensureRouteBinding(productId);
                // 5. 确认 → 下发（下发校验：产品必须已绑定工艺路线）
                workOrderService.confirmWorkOrder(workOrderId);
                workOrderService.issueWorkOrder(workOrderId);
                // 6. 按工艺路线自动拆分任务（复用 P1 能力）
                int taskCount = taskService.autoGenerateTasksByRoute(workOrderId);
                created++;
                log.info("[generateWorkOrdersFromSaleOrder][订单({}) 第{}行已生成工单({})，自动拆分任务 {} 个]",
                        saleOrder.getNo(), index, workOrderId, taskCount);
            } catch (Exception e) {
                log.error("[generateWorkOrdersFromSaleOrder][订单({}) 第{}行自动生成工单失败，跳过该行]",
                        saleOrder.getNo(), index, e);
            }
        }
        log.info("[generateWorkOrdersFromSaleOrder][订单({}) 工单自动生成完成，成功 {} / 共 {} 行]",
                saleOrder.getNo(), created, items.size());
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
        // 说明：订单行 → 工单 的生成统一由 generateWorkOrdersFromSaleOrder() 负责（在 processTask 中调用），
        // 此处不再建单，避免两套实现重复建单。
        return project.getId();
    }

    /**
     * 确保产品已绑定工艺路线：未绑定时自动绑定一条启用且有工序的默认路线。
     *
     * 下发接口会校验「产品必须已绑定工艺路线」，未绑定会导致该订单行下发失败被跳过；
     * 无可用路线时仅记录告警，由下发校验兜底拦截。
     *
     * @param productId 产品（MES 物料）编号
     */
    private void ensureRouteBinding(Long productId) {
        if (routeProductService.getRouteProductByItemId(productId) != null) {
            return;
        }
        Long routeId = findDefaultRouteId();
        if (routeId == null) {
            log.warn("[ensureRouteBinding][产品({}) 无可用工艺路线，下发时将被校验拦截，请人工绑定]", productId);
            return;
        }
        MesProRouteProductDO binding = new MesProRouteProductDO();
        binding.setRouteId(routeId);
        binding.setItemId(productId);
        binding.setRemark("销售订单同步自动绑定默认工艺路线");
        routeProductMapper.insert(binding);
        log.info("[ensureRouteBinding][产品({}) 未绑定工艺路线，已自动绑定默认路线({})]", productId, routeId);
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
