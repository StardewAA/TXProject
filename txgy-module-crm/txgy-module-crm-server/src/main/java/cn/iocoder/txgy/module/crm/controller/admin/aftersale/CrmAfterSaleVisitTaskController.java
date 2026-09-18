package cn.iocoder.txgy.module.crm.controller.admin.aftersale;

import cn.hutool.core.collection.CollUtil;
import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.common.util.collection.MapUtils;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.module.crm.controller.admin.aftersale.vo.visittask.CrmAfterSaleVisitTaskPageReqVO;
import cn.iocoder.txgy.module.crm.controller.admin.aftersale.vo.visittask.CrmAfterSaleVisitTaskRespVO;
import cn.iocoder.txgy.module.crm.controller.admin.aftersale.vo.visittask.CrmAfterSaleVisitTaskSaveReqVO;
import cn.iocoder.txgy.module.crm.dal.dataobject.aftersale.CrmAfterSaleVisitRecordDO;
import cn.iocoder.txgy.module.crm.dal.dataobject.aftersale.CrmAfterSaleVisitTaskDO;
import cn.iocoder.txgy.module.crm.dal.dataobject.aftersale.CrmAfterSaleWarrantyDO;
import cn.iocoder.txgy.module.crm.dal.dataobject.contract.CrmContractDO;
import cn.iocoder.txgy.module.crm.dal.dataobject.customer.CrmCustomerDO;
import cn.iocoder.txgy.module.crm.dal.mysql.aftersale.CrmAfterSaleVisitRecordMapper;
import cn.iocoder.txgy.module.crm.service.aftersale.CrmAfterSaleVisitTaskService;
import cn.iocoder.txgy.module.crm.service.aftersale.CrmAfterSaleWarrantyService;
import cn.iocoder.txgy.module.crm.service.contract.CrmContractService;
import cn.iocoder.txgy.module.crm.service.customer.CrmCustomerService;
import cn.iocoder.txgy.module.system.api.user.AdminUserApi;
import cn.iocoder.txgy.module.system.api.user.dto.AdminUserRespDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;
import static cn.iocoder.txgy.framework.common.util.collection.CollectionUtils.convertSet;
import static cn.iocoder.txgy.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "管理后台 - CRM 售后回访任务")
@RestController
@RequestMapping("/crm/after-sale/visit-task")
@Validated
public class CrmAfterSaleVisitTaskController {

    @Resource
    private CrmAfterSaleVisitTaskService visitTaskService;
    @Resource
    private CrmAfterSaleWarrantyService warrantyService;
    @Resource
    private CrmContractService contractService;
    @Resource
    private CrmCustomerService customerService;
    @Resource
    private CrmAfterSaleVisitRecordMapper visitRecordMapper;
    @Resource
    private AdminUserApi adminUserApi;

    @PostMapping("/create")
    @Operation(summary = "手动创建回访任务")
    @PreAuthorize("@ss.hasPermission('crm:after-sale-visit-task:create')")
    public CommonResult<Long> createVisitTask(@Valid @RequestBody CrmAfterSaleVisitTaskSaveReqVO createReqVO) {
        return success(visitTaskService.createVisitTask(createReqVO, getLoginUserId()));
    }

    @PutMapping("/update")
    @Operation(summary = "更新回访任务")
    @PreAuthorize("@ss.hasPermission('crm:after-sale-visit-task:update')")
    public CommonResult<Boolean> updateVisitTask(@Valid @RequestBody CrmAfterSaleVisitTaskSaveReqVO updateReqVO) {
        visitTaskService.updateVisitTask(updateReqVO, getLoginUserId());
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除回访任务")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('crm:after-sale-visit-task:delete')")
    public CommonResult<Boolean> deleteVisitTask(@RequestParam("id") Long id) {
        visitTaskService.deleteVisitTask(id, getLoginUserId());
        return success(true);
    }

    @PostMapping("/finish")
    @Operation(summary = "完成任务")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('crm:after-sale-visit-task:update')")
    public CommonResult<Boolean> finishVisitTask(@RequestParam("id") Long id) {
        visitTaskService.finishVisitTask(id, getLoginUserId());
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获得回访任务")
    @Parameter(name = "id", description = "编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('crm:after-sale-visit-task:query')")
    public CommonResult<CrmAfterSaleVisitTaskRespVO> getVisitTask(@RequestParam("id") Long id) {
        CrmAfterSaleVisitTaskRespVO task = BeanUtils.toBean(visitTaskService.getVisitTask(id),
                CrmAfterSaleVisitTaskRespVO.class);
        // 与分页接口保持一致：带出质保、客户、合同、处理人昵称、窗口期与回访记录信息
        fillTaskExtraInfo(List.of(task));
        return success(task);
    }

    @GetMapping("/simple-list")
    @Operation(summary = "获得回访任务精简列表（按质保档案，供回访记录表单绑定任务）")
    @Parameter(name = "warrantyId", description = "质保档案编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('crm:after-sale-visit-task:query')")
    public CommonResult<List<CrmAfterSaleVisitTaskRespVO>> getVisitTaskSimpleList(@RequestParam("warrantyId") Long warrantyId) {
        List<CrmAfterSaleVisitTaskRespVO> list = BeanUtils.toBean(
                visitTaskService.getVisitTaskListByWarrantyId(warrantyId), CrmAfterSaleVisitTaskRespVO.class);
        // 窗口期截止日期：按当前售后设置实时计算（改配置立即生效），不使用任务上的快照值
        list.forEach(task -> task.setWindowEndDate(visitTaskService.calcWindowEndDate(task.getTaskDate())));
        return success(list);
    }

    @GetMapping("/page")
    @Operation(summary = "获得回访任务分页")
    @PreAuthorize("@ss.hasPermission('crm:after-sale-visit-task:query')")
    public CommonResult<PageResult<CrmAfterSaleVisitTaskRespVO>> getVisitTaskPage(@Valid CrmAfterSaleVisitTaskPageReqVO pageReqVO) {
        // remindType = 临期 时用于待办「回访任务临期」，归属范围按当前登录人（本人 + 下属）过滤
        PageResult<CrmAfterSaleVisitTaskDO> pageResult = visitTaskService.getVisitTaskPage(pageReqVO, getLoginUserId());
        PageResult<CrmAfterSaleVisitTaskRespVO> respPage = BeanUtils.toBean(pageResult, CrmAfterSaleVisitTaskRespVO.class);
        if (CollUtil.isEmpty(respPage.getList())) {
            return success(respPage);
        }
        // 拼接质保、客户、合同、处理人昵称等展示字段
        fillTaskExtraInfo(respPage.getList());
        return success(respPage);
    }

    @GetMapping("/remind-count")
    @Operation(summary = "获得「回访任务临期」待办数量（实时查询，不落库）")
    @PreAuthorize("@ss.hasPermission('crm:after-sale-visit-task:query')")
    public CommonResult<Long> getVisitTaskRemindCount() {
        return success(visitTaskService.getVisitTaskRemindCount(getLoginUserId()));
    }

    /**
     * 填充回访任务的扩展展示字段
     *
     * <p>包含：质保编号 / 工程项目名称、合同名称、客户名称、处理人昵称、
     * 窗口期截止日期（按当前售后设置实时计算，不使用任务上的快照值）与关联回访记录信息；
     * 分页与详情接口共用，保证各入口展示口径一致。
     */
    private void fillTaskExtraInfo(List<CrmAfterSaleVisitTaskRespVO> list) {
        if (CollUtil.isEmpty(list)) {
            return;
        }
        Map<Long, CrmAfterSaleWarrantyDO> warrantyMap = warrantyService.getWarrantyMap(
                convertSet(list, CrmAfterSaleVisitTaskRespVO::getWarrantyId));
        Map<Long, CrmContractDO> contractMap = contractService.getContractMap(
                convertSet(list, CrmAfterSaleVisitTaskRespVO::getContractId));
        Map<Long, CrmCustomerDO> customerMap = customerService.getCustomerMap(
                convertSet(list, CrmAfterSaleVisitTaskRespVO::getCustomerId));
        Map<Long, AdminUserRespDTO> userMap = adminUserApi.getUserMap(
                list.stream().map(CrmAfterSaleVisitTaskRespVO::getAssigneeUserId).filter(Objects::nonNull).toList());
        list.forEach(task -> {
            MapUtils.findAndThen(warrantyMap, task.getWarrantyId(), warranty -> {
                task.setWarrantyNo(warranty.getNo());
                task.setProjectName(warranty.getProjectName());
            });
            MapUtils.findAndThen(contractMap, task.getContractId(),
                    contract -> task.setContractName(contract.getName()));
            MapUtils.findAndThen(customerMap, task.getCustomerId(),
                    customer -> task.setCustomerName(customer.getName()));
            MapUtils.findAndThen(userMap, task.getAssigneeUserId(),
                    user -> task.setAssigneeNickname(user.getNickname()));
            // 窗口期截止日期：按当前售后设置实时计算（改配置立即生效），不使用任务上的快照值
            task.setWindowEndDate(visitTaskService.calcWindowEndDate(task.getTaskDate()));
            // 任务完成后会自动生成一条回访记录，这里带出记录编号与状态，前端可直接跳转填写
            CrmAfterSaleVisitRecordDO record = visitRecordMapper.selectOneByTaskId(task.getId());
            task.setHasRecord(record != null);
            if (record != null) {
                task.setRecordId(record.getId());
                task.setRecordStatus(record.getStatus());
            }
        });
    }

}
