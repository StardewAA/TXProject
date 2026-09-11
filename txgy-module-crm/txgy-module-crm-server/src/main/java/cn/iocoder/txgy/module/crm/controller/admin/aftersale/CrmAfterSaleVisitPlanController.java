package cn.iocoder.txgy.module.crm.controller.admin.aftersale;

import cn.hutool.core.collection.CollUtil;
import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.common.util.collection.MapUtils;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.module.crm.controller.admin.aftersale.vo.visitplan.CrmAfterSaleVisitPlanPageReqVO;
import cn.iocoder.txgy.module.crm.controller.admin.aftersale.vo.visitplan.CrmAfterSaleVisitPlanRespVO;
import cn.iocoder.txgy.module.crm.controller.admin.aftersale.vo.visitplan.CrmAfterSaleVisitPlanSaveReqVO;
import cn.iocoder.txgy.module.crm.dal.dataobject.aftersale.CrmAfterSaleVisitPlanDO;
import cn.iocoder.txgy.module.crm.dal.dataobject.aftersale.CrmAfterSaleWarrantyDO;
import cn.iocoder.txgy.module.crm.dal.dataobject.customer.CrmCustomerDO;
import cn.iocoder.txgy.module.crm.service.aftersale.CrmAfterSaleVisitPlanService;
import cn.iocoder.txgy.module.crm.service.aftersale.CrmAfterSaleWarrantyService;
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

@Tag(name = "管理后台 - CRM 售后回访计划")
@RestController
@RequestMapping("/crm/after-sale/visit-plan")
@Validated
public class CrmAfterSaleVisitPlanController {

    @Resource
    private CrmAfterSaleVisitPlanService visitPlanService;
    @Resource
    private CrmAfterSaleWarrantyService warrantyService;
    @Resource
    private CrmCustomerService customerService;
    @Resource
    private AdminUserApi adminUserApi;

    @PostMapping("/create")
    @Operation(summary = "创建回访计划")
    @PreAuthorize("@ss.hasPermission('crm:after-sale-visit-plan:create')")
    public CommonResult<Long> createVisitPlan(@Valid @RequestBody CrmAfterSaleVisitPlanSaveReqVO createReqVO) {
        return success(visitPlanService.createVisitPlan(createReqVO, getLoginUserId()));
    }

    @PutMapping("/update")
    @Operation(summary = "更新回访计划")
    @PreAuthorize("@ss.hasPermission('crm:after-sale-visit-plan:update')")
    public CommonResult<Boolean> updateVisitPlan(@Valid @RequestBody CrmAfterSaleVisitPlanSaveReqVO updateReqVO) {
        visitPlanService.updateVisitPlan(updateReqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除回访计划")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('crm:after-sale-visit-plan:delete')")
    public CommonResult<Boolean> deleteVisitPlan(@RequestParam("id") Long id) {
        visitPlanService.deleteVisitPlan(id);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获得回访计划")
    @Parameter(name = "id", description = "编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('crm:after-sale-visit-plan:query')")
    public CommonResult<CrmAfterSaleVisitPlanRespVO> getVisitPlan(@RequestParam("id") Long id) {
        return success(BeanUtils.toBean(visitPlanService.getVisitPlan(id), CrmAfterSaleVisitPlanRespVO.class));
    }

    @GetMapping("/page")
    @Operation(summary = "获得回访计划分页")
    @PreAuthorize("@ss.hasPermission('crm:after-sale-visit-plan:query')")
    public CommonResult<PageResult<CrmAfterSaleVisitPlanRespVO>> getVisitPlanPage(@Valid CrmAfterSaleVisitPlanPageReqVO pageReqVO) {
        PageResult<CrmAfterSaleVisitPlanDO> pageResult = visitPlanService.getVisitPlanPage(pageReqVO);
        PageResult<CrmAfterSaleVisitPlanRespVO> respPage = BeanUtils.toBean(pageResult,
                CrmAfterSaleVisitPlanRespVO.class);
        if (CollUtil.isEmpty(respPage.getList())) {
            return success(respPage);
        }
        // 拼接质保档案、客户与指定处理人昵称
        List<CrmAfterSaleVisitPlanRespVO> list = respPage.getList();
        Map<Long, CrmAfterSaleWarrantyDO> warrantyMap = warrantyService.getWarrantyMap(
                convertSet(list, CrmAfterSaleVisitPlanRespVO::getWarrantyId));
        Map<Long, CrmCustomerDO> customerMap = customerService.getCustomerMap(
                warrantyMap.values().stream().map(CrmAfterSaleWarrantyDO::getCustomerId).toList());
        Map<Long, AdminUserRespDTO> userMap = adminUserApi.getUserMap(
                list.stream().map(CrmAfterSaleVisitPlanRespVO::getAssigneeUserId).filter(Objects::nonNull).toList());
        list.forEach(plan -> {
            MapUtils.findAndThen(warrantyMap, plan.getWarrantyId(), warranty -> {
                plan.setWarrantyNo(warranty.getNo());
                plan.setProjectName(warranty.getProjectName());
                MapUtils.findAndThen(customerMap, warranty.getCustomerId(),
                        customer -> plan.setCustomerName(customer.getName()));
            });
            MapUtils.findAndThen(userMap, plan.getAssigneeUserId(),
                    user -> plan.setAssigneeNickname(user.getNickname()));
        });
        return success(respPage);
    }

    @PostMapping("/generate-tasks")
    @Operation(summary = "生成回访任务（按计划周期断点续排）")
    @Parameter(name = "id", description = "回访计划编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('crm:after-sale-visit-plan:update')")
    public CommonResult<Integer> generateTasks(@RequestParam("id") Long id) {
        return success(visitPlanService.generateTasks(id, getLoginUserId()));
    }

}
