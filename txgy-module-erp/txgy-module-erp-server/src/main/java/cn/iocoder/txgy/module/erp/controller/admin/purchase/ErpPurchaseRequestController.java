package cn.iocoder.txgy.module.erp.controller.admin.purchase;

import cn.hutool.core.collection.CollUtil;
import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.common.util.collection.MapUtils;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.module.erp.controller.admin.product.vo.product.ErpProductRespVO;
import cn.iocoder.txgy.module.erp.controller.admin.purchase.vo.request.*;
import cn.iocoder.txgy.module.erp.dal.dataobject.purchase.ErpPurchaseRequestDO;
import cn.iocoder.txgy.module.erp.dal.dataobject.purchase.ErpPurchaseRequestItemDO;
import cn.iocoder.txgy.module.erp.service.product.ErpProductService;
import cn.iocoder.txgy.module.erp.service.purchase.ErpPurchaseRequestService;
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

@Tag(name = "管理后台 - ERP 采购申请")
@RestController
@RequestMapping("/erp/purchase-request")
@Validated
public class ErpPurchaseRequestController {
    @Resource private ErpPurchaseRequestService requestService;
    @Resource private ErpProductService productService;
    @Resource private AdminUserApi adminUserApi;

    @PostMapping("/create") @Operation(summary = "创建采购申请")
    @PreAuthorize("@ss.hasPermission('erp:purchase-request:create')")
    public CommonResult<Long> create(@Valid @RequestBody ErpPurchaseRequestSaveReqVO reqVO) { return success(requestService.createPurchaseRequest(reqVO)); }

    @PutMapping("/update") @Operation(summary = "修改采购申请")
    @PreAuthorize("@ss.hasPermission('erp:purchase-request:update')")
    public CommonResult<Boolean> update(@Valid @RequestBody ErpPurchaseRequestSaveReqVO reqVO) { requestService.updatePurchaseRequest(reqVO); return success(true); }

    @DeleteMapping("/delete") @Operation(summary = "删除采购申请")
    @PreAuthorize("@ss.hasPermission('erp:purchase-request:delete')")
    public CommonResult<Boolean> delete(@RequestParam("ids") List<Long> ids) { requestService.deletePurchaseRequest(ids); return success(true); }

    @PutMapping("/submit") @Operation(summary = "提交采购申请审批")
    @PreAuthorize("@ss.hasPermission('erp:purchase-request:submit')")
    public CommonResult<Boolean> submit(@RequestParam("id") Long id) { requestService.submitPurchaseRequest(id, getLoginUserId()); return success(true); }

    @GetMapping("/get") @Operation(summary = "获得采购申请")
    @PreAuthorize("@ss.hasPermission('erp:purchase-request:query')")
    public CommonResult<ErpPurchaseRequestRespVO> get(@RequestParam("id") Long id) {
        ErpPurchaseRequestDO request = requestService.getPurchaseRequest(id);
        return success(request == null ? null : build(request, requestService.getItemListByRequestId(id)));
    }

    @GetMapping("/page") @Operation(summary = "获得采购申请分页")
    @PreAuthorize("@ss.hasPermission('erp:purchase-request:query')")
    public CommonResult<PageResult<ErpPurchaseRequestRespVO>> page(@Valid ErpPurchaseRequestPageReqVO reqVO) {
        PageResult<ErpPurchaseRequestDO> result = requestService.getPurchaseRequestPage(reqVO);
        if (CollUtil.isEmpty(result.getList())) return success(PageResult.empty(result.getTotal()));
        List<ErpPurchaseRequestItemDO> items = requestService.getItemListByRequestIds(convertSet(result.getList(), ErpPurchaseRequestDO::getId));
        Map<Long, List<ErpPurchaseRequestItemDO>> itemMap = convertMultiMap(items, ErpPurchaseRequestItemDO::getRequestId);
        Map<Long, ErpProductRespVO> productMap = productService.getProductVOMap(convertSet(items, ErpPurchaseRequestItemDO::getProductId));
        Map<Long, AdminUserRespDTO> userMap = adminUserApi.getUserMap(convertSet(result.getList(), r -> Long.parseLong(r.getCreator())));
        return success(BeanUtils.toBean(result, ErpPurchaseRequestRespVO.class, vo -> {
            fillItems(vo, itemMap.get(vo.getId()), productMap);
            MapUtils.findAndThen(userMap, Long.parseLong(vo.getCreator()), user -> vo.setCreatorName(user.getNickname()));
        }));
    }

    private ErpPurchaseRequestRespVO build(ErpPurchaseRequestDO request, List<ErpPurchaseRequestItemDO> items) {
        ErpPurchaseRequestRespVO vo = BeanUtils.toBean(request, ErpPurchaseRequestRespVO.class);
        fillItems(vo, items, productService.getProductVOMap(convertSet(items, ErpPurchaseRequestItemDO::getProductId)));
        return vo;
    }

    private void fillItems(ErpPurchaseRequestRespVO vo, List<ErpPurchaseRequestItemDO> items, Map<Long, ErpProductRespVO> products) {
        vo.setItems(BeanUtils.toBean(items, ErpPurchaseRequestRespVO.Item.class, item ->
                MapUtils.findAndThen(products, item.getProductId(), product -> item.setProductName(product.getName())
                        .setProductBarCode(product.getBarCode()).setProductUnitName(product.getUnitName()))));
        vo.setProductNames(CollUtil.join(vo.getItems(), "，", ErpPurchaseRequestRespVO.Item::getProductName));
    }
}
