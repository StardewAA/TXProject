package cn.iocoder.txgy.module.erp.controller.admin.purchase;

import cn.hutool.core.collection.CollUtil;
import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.common.util.collection.MapUtils;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.module.erp.controller.admin.product.vo.product.ErpProductRespVO;
import cn.iocoder.txgy.module.erp.controller.admin.purchase.vo.inspection.*;
import cn.iocoder.txgy.module.erp.dal.dataobject.purchase.ErpArrivalInspectionDO;
import cn.iocoder.txgy.module.erp.dal.dataobject.purchase.ErpArrivalInspectionItemDO;
import cn.iocoder.txgy.module.erp.service.product.ErpProductService;
import cn.iocoder.txgy.module.erp.service.purchase.ErpArrivalInspectionService;
import cn.iocoder.txgy.module.system.api.user.AdminUserApi;
import cn.iocoder.txgy.module.system.api.user.dto.AdminUserRespDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;
import static cn.iocoder.txgy.framework.common.util.collection.CollectionUtils.*;
import static cn.iocoder.txgy.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "管理后台 - ERP 到货检验")
@RestController
@RequestMapping("/erp/purchase-inspection")
@Validated
public class ErpArrivalInspectionController {
    @Resource private ErpArrivalInspectionService inspectionService;
    @Resource private ErpProductService productService;
    @Resource private AdminUserApi adminUserApi;

    @PostMapping("/create") @Operation(summary = "创建到货检验")
    @PreAuthorize("@ss.hasPermission('erp:purchase-inspection:create')")
    public CommonResult<Long> create(@Valid @RequestBody ErpArrivalInspectionSaveReqVO reqVO) { return success(inspectionService.createArrivalInspection(reqVO)); }

    @PutMapping("/update") @Operation(summary = "修改到货检验")
    @PreAuthorize("@ss.hasPermission('erp:purchase-inspection:update')")
    public CommonResult<Boolean> update(@Valid @RequestBody ErpArrivalInspectionSaveReqVO reqVO) { inspectionService.updateArrivalInspection(reqVO); return success(true); }

    @DeleteMapping("/delete") @Operation(summary = "删除到货检验")
    @PreAuthorize("@ss.hasPermission('erp:purchase-inspection:delete')")
    public CommonResult<Boolean> delete(@RequestParam("ids") List<Long> ids) { inspectionService.deleteArrivalInspection(ids); return success(true); }

    @PutMapping("/submit") @Operation(summary = "提交到货检验审批")
    @PreAuthorize("@ss.hasPermission('erp:purchase-inspection:submit')")
    public CommonResult<Boolean> submit(@RequestParam("id") Long id) { inspectionService.submitArrivalInspection(id, getLoginUserId()); return success(true); }

    @PutMapping("/approve") @Operation(summary = "审批通过到货检验")
    @PreAuthorize("@ss.hasPermission('erp:purchase-inspection:approve')")
    public CommonResult<Boolean> approve(@RequestParam("id") Long id) { inspectionService.approveArrivalInspection(id, getLoginUserId()); return success(true); }

    @PutMapping("/reject") @Operation(summary = "审批驳回到货检验")
    @PreAuthorize("@ss.hasPermission('erp:purchase-inspection:approve')")
    public CommonResult<Boolean> reject(@RequestParam("id") Long id) { inspectionService.rejectArrivalInspection(id, getLoginUserId()); return success(true); }

    @GetMapping("/get") @Operation(summary = "获得到货检验")
    @PreAuthorize("@ss.hasPermission('erp:purchase-inspection:query')")
    public CommonResult<ErpArrivalInspectionRespVO> get(@RequestParam("id") Long id) {
        ErpArrivalInspectionDO inspection = inspectionService.getArrivalInspection(id);
        return success(inspection == null ? null : build(inspection, inspectionService.getItemListByInspectionId(id)));
    }

    @GetMapping("/page") @Operation(summary = "获得到货检验分页")
    @PreAuthorize("@ss.hasPermission('erp:purchase-inspection:query')")
    public CommonResult<PageResult<ErpArrivalInspectionRespVO>> page(@Valid ErpArrivalInspectionPageReqVO reqVO) {
        PageResult<ErpArrivalInspectionDO> result = inspectionService.getArrivalInspectionPage(reqVO);
        if (CollUtil.isEmpty(result.getList())) return success(PageResult.empty(result.getTotal()));
        List<ErpArrivalInspectionItemDO> items = inspectionService.getItemListByInspectionIds(convertSet(result.getList(), ErpArrivalInspectionDO::getId));
        Map<Long, List<ErpArrivalInspectionItemDO>> itemMap = convertMultiMap(items, ErpArrivalInspectionItemDO::getInspectionId);
        Map<Long, ErpProductRespVO> productMap = productService.getProductVOMap(convertSet(items, ErpArrivalInspectionItemDO::getProductId));
        Map<Long, AdminUserRespDTO> userMap = adminUserApi.getUserMapIgnoreTenant(convertSet(result.getList(), r -> Long.parseLong(r.getCreator())));
        return success(BeanUtils.toBean(result, ErpArrivalInspectionRespVO.class, vo -> {
            fillItems(vo, itemMap.get(vo.getId()), productMap);
            MapUtils.findAndThen(userMap, Long.parseLong(vo.getCreator()), user -> vo.setCreatorName(user.getNickname()));
        }));
    }

    private ErpArrivalInspectionRespVO build(ErpArrivalInspectionDO inspection, List<ErpArrivalInspectionItemDO> items) {
        ErpArrivalInspectionRespVO vo = BeanUtils.toBean(inspection, ErpArrivalInspectionRespVO.class);
        fillItems(vo, items, productService.getProductVOMap(convertSet(items, ErpArrivalInspectionItemDO::getProductId)));
        return vo;
    }

    private void fillItems(ErpArrivalInspectionRespVO vo, List<ErpArrivalInspectionItemDO> items, Map<Long, ErpProductRespVO> products) {
        vo.setItems(BeanUtils.toBean(items, ErpArrivalInspectionRespVO.Item.class, item ->
                MapUtils.findAndThen(products, item.getProductId(), product -> item.setProductName(product.getName())
                        .setProductUnitName(product.getUnitName()))));
        vo.setProductNames(CollUtil.join(vo.getItems(), "，", ErpArrivalInspectionRespVO.Item::getProductName));
    }
}
