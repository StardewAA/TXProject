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
        return success(BeanUtils.toBean(visitTaskService.getVisitTask(id), CrmAfterSaleVisitTaskRespVO.class));
    }

    @GetMapping("/page")
    @Operation(summary = "获得回访任务分页")
    @PreAuthorize("@ss.hasPermission('crm:after-sale-visit-task:query')")
    public CommonResult<PageResult<CrmAfterSaleVisitTaskRespVO>> getVisitTaskPage(@Valid CrmAfterSaleVisitTaskPageReqVO pageReqVO) {
        PageResult<CrmAfterSaleVisitTaskDO> pageResult = visitTaskService.getVisitTaskPage(pageReqVO);
        PageResult<CrmAfterSaleVisitTaskRespVO> respPage = BeanUtils.toBean(pageResult, CrmAfterSaleVisitTaskRespVO.class);
        if (CollUtil.isEmpty(respPage.getList())) {
            return success(respPage);
        }
        // 拼接质保、客户、合同与处理人昵称
        List<CrmAfterSaleVisitTaskRespVO> list = respPage.getList();
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
            // 任务完成后会自动生成一条回访记录，这里带出记录编号与状态，前端可直接跳转填写
            CrmAfterSaleVisitRecordDO record = visitRecordMapper.selectOneByTaskId(task.getId());
            task.setHasRecord(record != null);
            if (record != null) {
                task.setRecordId(record.getId());
                task.setRecordStatus(record.getStatus());
            }
        });
        return success(respPage);
    }

}
