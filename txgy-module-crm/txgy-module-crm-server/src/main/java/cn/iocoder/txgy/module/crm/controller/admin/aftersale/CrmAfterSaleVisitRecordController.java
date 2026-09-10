package cn.iocoder.txgy.module.crm.controller.admin.aftersale;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.common.util.collection.MapUtils;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.module.crm.controller.admin.aftersale.vo.visitrecord.CrmAfterSaleVisitRecordPageReqVO;
import cn.iocoder.txgy.module.crm.controller.admin.aftersale.vo.visitrecord.CrmAfterSaleVisitRecordRespVO;
import cn.iocoder.txgy.module.crm.controller.admin.aftersale.vo.visitrecord.CrmAfterSaleVisitRecordSaveReqVO;
import cn.iocoder.txgy.module.crm.dal.dataobject.aftersale.CrmAfterSaleVisitRecordDO;
import cn.iocoder.txgy.module.crm.dal.dataobject.aftersale.CrmAfterSaleWarrantyDO;
import cn.iocoder.txgy.module.crm.dal.dataobject.contract.CrmContractDO;
import cn.iocoder.txgy.module.crm.dal.dataobject.customer.CrmCustomerDO;
import cn.iocoder.txgy.module.crm.service.aftersale.CrmAfterSaleVisitRecordService;
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
import java.util.Set;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;
import static cn.iocoder.txgy.framework.common.util.collection.CollectionUtils.convertSet;
import static cn.iocoder.txgy.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "管理后台 - CRM 售后回访记录")
@RestController
@RequestMapping("/crm/after-sale/visit-record")
@Validated
public class CrmAfterSaleVisitRecordController {

    @Resource
    private CrmAfterSaleVisitRecordService visitRecordService;
    @Resource
    private CrmAfterSaleWarrantyService warrantyService;
    @Resource
    private CrmContractService contractService;
    @Resource
    private CrmCustomerService customerService;
    @Resource
    private AdminUserApi adminUserApi;

    @PostMapping("/create")
    @Operation(summary = "登记回访记录")
    @PreAuthorize("@ss.hasPermission('crm:after-sale-visit-record:create')")
    public CommonResult<Long> createVisitRecord(@Valid @RequestBody CrmAfterSaleVisitRecordSaveReqVO createReqVO) {
        return success(visitRecordService.createVisitRecord(createReqVO, getLoginUserId()));
    }

    @PutMapping("/update")
    @Operation(summary = "更新回访记录")
    @PreAuthorize("@ss.hasPermission('crm:after-sale-visit-record:update')")
    public CommonResult<Boolean> updateVisitRecord(@Valid @RequestBody CrmAfterSaleVisitRecordSaveReqVO updateReqVO) {
        visitRecordService.updateVisitRecord(updateReqVO, getLoginUserId());
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除回访记录")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('crm:after-sale-visit-record:delete')")
    public CommonResult<Boolean> deleteVisitRecord(@RequestParam("id") Long id) {
        visitRecordService.deleteVisitRecord(id, getLoginUserId());
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获得回访记录")
    @Parameter(name = "id", description = "编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('crm:after-sale-visit-record:query')")
    public CommonResult<CrmAfterSaleVisitRecordRespVO> getVisitRecord(@RequestParam("id") Long id) {
        return success(BeanUtils.toBean(visitRecordService.getVisitRecord(id), CrmAfterSaleVisitRecordRespVO.class));
    }

    @GetMapping("/page")
    @Operation(summary = "获得回访记录分页")
    @PreAuthorize("@ss.hasPermission('crm:after-sale-visit-record:query')")
    public CommonResult<PageResult<CrmAfterSaleVisitRecordRespVO>> getVisitRecordPage(@Valid CrmAfterSaleVisitRecordPageReqVO pageReqVO) {
        PageResult<CrmAfterSaleVisitRecordDO> pageResult = visitRecordService.getVisitRecordPage(pageReqVO);
        PageResult<CrmAfterSaleVisitRecordRespVO> respPage = BeanUtils.toBean(pageResult, CrmAfterSaleVisitRecordRespVO.class);
        if (CollUtil.isEmpty(respPage.getList())) {
            return success(respPage);
        }
        // 拼接质保、客户、合同与登记人昵称
        List<CrmAfterSaleVisitRecordRespVO> list = respPage.getList();
        Map<Long, CrmAfterSaleWarrantyDO> warrantyMap = warrantyService.getWarrantyMap(
                convertSet(list, CrmAfterSaleVisitRecordRespVO::getWarrantyId));
        Map<Long, CrmContractDO> contractMap = contractService.getContractMap(
                convertSet(list, CrmAfterSaleVisitRecordRespVO::getContractId));
        Map<Long, CrmCustomerDO> customerMap = customerService.getCustomerMap(
                convertSet(list, CrmAfterSaleVisitRecordRespVO::getCustomerId));
        list.forEach(record -> {
            MapUtils.findAndThen(warrantyMap, record.getWarrantyId(), warranty -> {
                record.setWarrantyNo(warranty.getNo());
                record.setProjectName(warranty.getProjectName());
            });
            MapUtils.findAndThen(contractMap, record.getContractId(),
                    contract -> record.setContractName(contract.getName()));
            MapUtils.findAndThen(customerMap, record.getCustomerId(),
                    customer -> record.setCustomerName(customer.getName()));
        });
        // 登记人昵称（creator 为用户编号字符串）
        Map<Long, AdminUserRespDTO> userMap = adminUserApi.getUserMap(convertCreatorIds(list));
        list.forEach(record -> MapUtils.findAndThen(userMap, parseCreatorId(record),
                user -> record.setCreatorNickname(user.getNickname())));
        return success(respPage);
    }

    private static Set<Long> convertCreatorIds(List<CrmAfterSaleVisitRecordRespVO> list) {
        return list.stream().map(CrmAfterSaleVisitRecordController::parseCreatorId)
                .filter(java.util.Objects::nonNull).collect(java.util.stream.Collectors.toSet());
    }

    private static Long parseCreatorId(CrmAfterSaleVisitRecordDO record) {
        return StrUtil.isNumeric(record.getCreator()) ? Long.valueOf(record.getCreator()) : null;
    }

}
