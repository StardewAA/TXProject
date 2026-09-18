package cn.iocoder.txgy.module.crm.controller.admin.aftersale;

import cn.hutool.core.collection.CollUtil;
import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.common.util.collection.MapUtils;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.module.crm.controller.admin.aftersale.vo.warranty.CrmAfterSaleWarrantyPageReqVO;
import cn.iocoder.txgy.module.crm.controller.admin.aftersale.vo.warranty.CrmAfterSaleWarrantyRespVO;
import cn.iocoder.txgy.module.crm.controller.admin.aftersale.vo.warranty.CrmAfterSaleWarrantySaveReqVO;
import cn.iocoder.txgy.module.crm.dal.dataobject.aftersale.CrmAfterSaleWarrantyDO;
import cn.iocoder.txgy.module.crm.dal.dataobject.contract.CrmContractDO;
import cn.iocoder.txgy.module.crm.dal.dataobject.customer.CrmCustomerDO;
import cn.iocoder.txgy.module.crm.service.aftersale.CrmAfterSaleWarrantyService;
import cn.iocoder.txgy.module.crm.service.contract.CrmContractService;
import cn.iocoder.txgy.module.crm.service.customer.CrmCustomerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;
import static cn.iocoder.txgy.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "管理后台 - CRM 售后质保档案")
@RestController
@RequestMapping("/crm/after-sale/warranty")
@Validated
public class CrmAfterSaleWarrantyController {

    @Resource
    private CrmAfterSaleWarrantyService warrantyService;
    @Resource
    private CrmContractService contractService;
    @Resource
    private CrmCustomerService customerService;

    @PostMapping("/create")
    @Operation(summary = "创建质保档案")
    @PreAuthorize("@ss.hasPermission('crm:after-sale-warranty:create')")
    public CommonResult<Long> createWarranty(@Valid @RequestBody CrmAfterSaleWarrantySaveReqVO createReqVO) {
        return success(warrantyService.createWarranty(createReqVO, getLoginUserId()));
    }

    @PutMapping("/update")
    @Operation(summary = "更新质保档案")
    @PreAuthorize("@ss.hasPermission('crm:after-sale-warranty:update')")
    public CommonResult<Boolean> updateWarranty(@Valid @RequestBody CrmAfterSaleWarrantySaveReqVO updateReqVO) {
        warrantyService.updateWarranty(updateReqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除质保档案")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('crm:after-sale-warranty:delete')")
    public CommonResult<Boolean> deleteWarranty(@RequestParam("id") Long id) {
        warrantyService.deleteWarranty(id);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获得质保档案")
    @Parameter(name = "id", description = "编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('crm:after-sale-warranty:query')")
    public CommonResult<CrmAfterSaleWarrantyRespVO> getWarranty(@RequestParam("id") Long id) {
        CrmAfterSaleWarrantyRespVO warranty = BeanUtils.toBean(warrantyService.getWarranty(id),
                CrmAfterSaleWarrantyRespVO.class);
        // 与分页接口保持一致：带出合同、客户名称与剩余天数，供详情页展示
        fillWarrantyExtraInfo(List.of(warranty));
        return success(warranty);
    }

    @GetMapping("/page")
    @Operation(summary = "获得质保档案分页")
    @PreAuthorize("@ss.hasPermission('crm:after-sale-warranty:query')")
    public CommonResult<PageResult<CrmAfterSaleWarrantyRespVO>> getWarrantyPage(@Valid CrmAfterSaleWarrantyPageReqVO pageReqVO) {
        // expiryType = 即将到期 时用于待办「质保即将到期」，归属范围按当前登录人（本人 + 下属）过滤
        PageResult<CrmAfterSaleWarrantyDO> pageResult = warrantyService.getWarrantyPage(pageReqVO, getLoginUserId());
        PageResult<CrmAfterSaleWarrantyRespVO> respPage = BeanUtils.toBean(pageResult, CrmAfterSaleWarrantyRespVO.class);
        if (CollUtil.isEmpty(respPage.getList())) {
            return success(respPage);
        }
        // 拼接关联的合同、客户名称与剩余天数
        fillWarrantyExtraInfo(respPage.getList());
        return success(respPage);
    }

    /**
     * 填充质保档案的扩展展示字段：合同编号/名称、客户名称与质保剩余天数
     *
     * <p>分页与详情（/get）接口共用，保证各入口展示口径一致
     */
    private void fillWarrantyExtraInfo(List<CrmAfterSaleWarrantyRespVO> list) {
        if (CollUtil.isEmpty(list)) {
            return;
        }
        Map<Long, CrmContractDO> contractMap = contractService.getContractMap(
                list.stream().map(CrmAfterSaleWarrantyRespVO::getContractId).toList());
        Map<Long, CrmCustomerDO> customerMap = customerService.getCustomerMap(
                list.stream().map(CrmAfterSaleWarrantyRespVO::getCustomerId).toList());
        list.forEach(warranty -> {
            MapUtils.findAndThen(contractMap, warranty.getContractId(), contract -> {
                warranty.setContractNo(contract.getNo());
                warranty.setContractName(contract.getName());
            });
            MapUtils.findAndThen(customerMap, warranty.getCustomerId(),
                    customer -> warranty.setCustomerName(customer.getName()));
            if (warranty.getWarrantyEndDate() != null) {
                warranty.setRemainDays(ChronoUnit.DAYS.between(LocalDate.now(), warranty.getWarrantyEndDate()));
            }
        });
    }

    @GetMapping("/remind-count")
    @Operation(summary = "获得「质保即将到期」待办数量（实时查询，不落库）")
    @PreAuthorize("@ss.hasPermission('crm:after-sale-warranty:query')")
    public CommonResult<Long> getWarrantyRemindCount() {
        return success(warrantyService.getWarrantyRemindCount(getLoginUserId()));
    }

}
