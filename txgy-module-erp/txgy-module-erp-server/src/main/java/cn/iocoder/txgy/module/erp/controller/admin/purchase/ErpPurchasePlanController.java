package cn.iocoder.txgy.module.erp.controller.admin.purchase;

import cn.hutool.core.collection.CollUtil;
import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.common.util.collection.MapUtils;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.module.erp.controller.admin.product.vo.product.ErpProductRespVO;
import cn.iocoder.txgy.module.erp.controller.admin.purchase.vo.plan.*;
import cn.iocoder.txgy.module.erp.dal.dataobject.purchase.ErpPurchasePlanDO;
import cn.iocoder.txgy.module.erp.dal.dataobject.purchase.ErpPurchasePlanItemDO;
import cn.iocoder.txgy.module.erp.service.product.ErpProductService;
import cn.iocoder.txgy.module.erp.service.purchase.ErpPurchasePlanService;
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

@Tag(name = "管理后台 - ERP 采购计划")
@RestController
@RequestMapping("/erp/purchase-plan")
@Validated
public class ErpPurchasePlanController {
    @Resource private ErpPurchasePlanService planService;
    @Resource private ErpProductService productService;
    @Resource private AdminUserApi adminUserApi;

    @PostMapping("/create") @Operation(summary = "创建采购计划")
    @PreAuthorize("@ss.hasPermission('erp:purchase-plan:create')")
    public CommonResult<Long> create(@Valid @RequestBody ErpPurchasePlanSaveReqVO reqVO) { return success(planService.createPurchasePlan(reqVO)); }

    @PutMapping("/update") @Operation(summary = "修改采购计划")
    @PreAuthorize("@ss.hasPermission('erp:purchase-plan:update')")
    public CommonResult<Boolean> update(@Valid @RequestBody ErpPurchasePlanSaveReqVO reqVO) { planService.updatePurchasePlan(reqVO); return success(true); }

    @DeleteMapping("/delete") @Operation(summary = "删除采购计划")
    @PreAuthorize("@ss.hasPermission('erp:purchase-plan:delete')")
    public CommonResult<Boolean> delete(@RequestParam("ids") List<Long> ids) { planService.deletePurchasePlan(ids); return success(true); }

    @PutMapping("/submit") @Operation(summary = "提交采购计划审批")
    @PreAuthorize("@ss.hasPermission('erp:purchase-plan:submit')")
    public CommonResult<Boolean> submit(@RequestParam("id") Long id) { planService.submitPurchasePlan(id, getLoginUserId()); return success(true); }

    @PutMapping("/approve") @Operation(summary = "审批通过采购计划")
    @PreAuthorize("@ss.hasPermission('erp:purchase-plan:approve')")
    public CommonResult<Boolean> approve(@RequestParam("id") Long id) { planService.approvePurchasePlan(id, getLoginUserId()); return success(true); }

    @PutMapping("/reject") @Operation(summary = "审批驳回采购计划")
    @PreAuthorize("@ss.hasPermission('erp:purchase-plan:approve')")
    public CommonResult<Boolean> reject(@RequestParam("id") Long id) { planService.rejectPurchasePlan(id, getLoginUserId()); return success(true); }

    @GetMapping("/get") @Operation(summary = "获得采购计划")
    @PreAuthorize("@ss.hasPermission('erp:purchase-plan:query')")
    public CommonResult<ErpPurchasePlanRespVO> get(@RequestParam("id") Long id) {
        ErpPurchasePlanDO plan = planService.getPurchasePlan(id);
        return success(plan == null ? null : build(plan, planService.getItemListByPlanId(id)));
    }

    @GetMapping("/page") @Operation(summary = "获得采购计划分页")
    @PreAuthorize("@ss.hasPermission('erp:purchase-plan:query')")
    public CommonResult<PageResult<ErpPurchasePlanRespVO>> page(@Valid ErpPurchasePlanPageReqVO reqVO) {
        PageResult<ErpPurchasePlanDO> result = planService.getPurchasePlanPage(reqVO);
        if (CollUtil.isEmpty(result.getList())) return success(PageResult.empty(result.getTotal()));
        List<ErpPurchasePlanItemDO> items = planService.getItemListByPlanIds(convertSet(result.getList(), ErpPurchasePlanDO::getId));
        Map<Long, List<ErpPurchasePlanItemDO>> itemMap = convertMultiMap(items, ErpPurchasePlanItemDO::getPlanId);
        Map<Long, ErpProductRespVO> productMap = productService.getProductVOMap(convertSet(items, ErpPurchasePlanItemDO::getProductId));
        Map<Long, AdminUserRespDTO> userMap = adminUserApi.getUserMapIgnoreTenant(convertSet(result.getList(), r -> Long.parseLong(r.getCreator())));
        return success(BeanUtils.toBean(result, ErpPurchasePlanRespVO.class, vo -> {
            fillItems(vo, itemMap.get(vo.getId()), productMap);
            MapUtils.findAndThen(userMap, Long.parseLong(vo.getCreator()), user -> vo.setCreatorName(user.getNickname()));
        }));
    }

    private ErpPurchasePlanRespVO build(ErpPurchasePlanDO plan, List<ErpPurchasePlanItemDO> items) {
        ErpPurchasePlanRespVO vo = BeanUtils.toBean(plan, ErpPurchasePlanRespVO.class);
        fillItems(vo, items, productService.getProductVOMap(convertSet(items, ErpPurchasePlanItemDO::getProductId)));
        return vo;
    }

    private void fillItems(ErpPurchasePlanRespVO vo, List<ErpPurchasePlanItemDO> items, Map<Long, ErpProductRespVO> products) {
        // 历史数据中的商品可能已被归档或迁移。页面不能把 Java 的 null 直接拼接并展示给用户。
        // 正常数据仍取商品主档；关联缺失时明确标识，便于后续修复数据而不是静默误导。
        vo.setItems(BeanUtils.toBean(CollUtil.emptyIfNull(items), ErpPurchasePlanRespVO.Item.class, item -> {
            ErpProductRespVO product = products.get(item.getProductId());
            if (product == null) {
                item.setProductName("未关联商品").setProductUnitName("-");
                return;
            }
            item.setProductName(product.getName()).setProductUnitName(product.getUnitName());
        }));
        vo.setProductNames(CollUtil.join(vo.getItems(), "，", ErpPurchasePlanRespVO.Item::getProductName));
    }
}
