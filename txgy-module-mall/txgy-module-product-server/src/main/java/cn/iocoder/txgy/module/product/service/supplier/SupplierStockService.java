package cn.iocoder.txgy.module.product.service.supplier;

import cn.iocoder.txgy.framework.common.pojo.*;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.product.dal.dataobject.sku.*;
import cn.iocoder.txgy.module.product.dal.dataobject.spu.ProductSpuDO;
import cn.iocoder.txgy.module.product.dal.mysql.sku.*;
import cn.iocoder.txgy.module.product.dal.mysql.spu.ProductSpuMapper;
import cn.iocoder.txgy.module.product.service.enterprise.EnterpriseMembershipService;
import jakarta.annotation.Resource;
import jakarta.validation.constraints.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Set;
import static cn.iocoder.txgy.module.product.service.enterprise.EnterpriseMembershipService.invalid;

@Service
public class SupplierStockService {
    @Resource private EnterpriseMembershipService membership;
    @Resource private ProductSkuMapper skus;
    @Resource private ProductSpuMapper spus;
    @Resource private SupplierStockAdjustmentMapper adjustments;

    public record Adjust(@NotNull @Positive Long shopId, @NotNull @Positive Long spuId,
                         @NotNull @Positive Long skuId, @NotNull @Min(0) Integer expectedStock,
                         @NotNull @Min(-1000000) @Max(1000000) Integer delta,
                         @NotBlank @Size(max=200) String reason,
                         @NotBlank @Pattern(regexp="[A-Za-z0-9-]{16,64}") String requestKey) {}
    public record Entry(Long id, Long skuId, Long memberId, Integer delta, Integer beforeStock,
                        Integer afterStock, String reason, LocalDateTime createTime) {}
    private Entry entry(SupplierStockAdjustmentDO row) {
        return new Entry(row.getId(), row.getSkuId(), row.getMemberId(), row.getDelta(),
                row.getBeforeStock(), row.getAfterStock(), row.getReason(), row.getCreateTime());
    }
    private ProductSpuDO owned(Long shop, Long spu, boolean lock) {
        var q = new LambdaQueryWrapperX<ProductSpuDO>().eq(ProductSpuDO::getId, spu).eq(ProductSpuDO::getShopId, shop);
        if (lock) q.last("FOR UPDATE");
        var row = spus.selectOne(q);
        if (row == null || row.getStatus() == null || !Set.of(0,1).contains(row.getStatus())) throw invalid("商品不存在或无权管理");
        return row;
    }
    public PageResult<Entry> page(Long member, Long shop, Long spu, PageParam page) {
        membership.requireShop(member, shop);
        owned(shop, spu, false);
        var result = adjustments.selectPage(page, new LambdaQueryWrapperX<SupplierStockAdjustmentDO>()
                .eq(SupplierStockAdjustmentDO::getShopId, shop).eq(SupplierStockAdjustmentDO::getSpuId, spu)
                .orderByDesc(SupplierStockAdjustmentDO::getId));
        return new PageResult<>(result.getList().stream().map(this::entry).toList(), result.getTotal());
    }
    @Transactional(rollbackFor=Exception.class)
    public Entry adjust(Long member, Adjust req) {
        membership.requireShop(member, req.shopId());
        owned(req.shopId(), req.spuId(), false);
        if (req.delta() == null || req.delta() == 0 || Math.abs((long) req.delta()) > 1000000
                || req.expectedStock() == null || req.expectedStock() < 0
                || req.reason() == null || req.reason().isBlank() || req.reason().length() > 200)
            throw invalid("请填写有效的非零调整数量、原库存和调整原因");
        // 与订单扣库存一致先锁SKU，再锁SPU；只操作库存，不调用同时改变销量的订单接口。
        var sku = skus.selectOne(new LambdaQueryWrapperX<ProductSkuDO>().eq(ProductSkuDO::getId, req.skuId())
                .eq(ProductSkuDO::getSpuId, req.spuId()).last("FOR UPDATE"));
        if (sku == null) throw invalid("SKU不属于当前商品");
        var spu = owned(req.shopId(), req.spuId(), true);
        // 当前读保证等待同SKU并发请求后可见已提交的幂等记录。
        var previous = adjustments.selectOne(new LambdaQueryWrapperX<SupplierStockAdjustmentDO>()
                .eq(SupplierStockAdjustmentDO::getSkuId, req.skuId()).eq(SupplierStockAdjustmentDO::getMemberId, member)
                .eq(SupplierStockAdjustmentDO::getRequestKey, req.requestKey()).last("FOR UPDATE"));
        if (previous != null) {
            if (!Objects.equals(previous.getShopId(),req.shopId()) || !Objects.equals(previous.getSpuId(),req.spuId())
                    || !Objects.equals(previous.getDelta(),req.delta()) || !Objects.equals(previous.getExpectedStock(),req.expectedStock())
                    || !Objects.equals(previous.getReason(),req.reason().trim())) throw invalid("请求编号已用于其他调整");
            return entry(previous);
        }
        if (sku.getWmsSkuId() != null || sku.getWmsWarehouseId() != null) throw invalid("WMS管理的SKU不能手工调整本地库存");
        if (!Objects.equals(sku.getStock(),req.expectedStock())) throw invalid("库存已变化，请刷新后重新核对");
        long next = (long) sku.getStock() + req.delta();
        long spuNext = spu.getStock() == null ? -1 : (long) spu.getStock() + req.delta();
        if (next < 0 || next > Integer.MAX_VALUE || spuNext < 0 || spuNext > Integer.MAX_VALUE)
            throw invalid("调整后库存不能为负或超出范围，请核对库存");
        skus.updateById(new ProductSkuDO().setId(sku.getId()).setStock((int)next));
        spus.updateById(new ProductSpuDO().setId(spu.getId()).setStock((int)spuNext));
        var row = new SupplierStockAdjustmentDO();
        row.setShopId(req.shopId()); row.setSpuId(req.spuId()); row.setSkuId(req.skuId()); row.setMemberId(member);
        row.setRequestKey(req.requestKey()); row.setExpectedStock(req.expectedStock()); row.setDelta(req.delta());
        row.setBeforeStock(sku.getStock()); row.setAfterStock((int)next); row.setReason(req.reason().trim());
        adjustments.insert(row);
        return entry(row);
    }
}
