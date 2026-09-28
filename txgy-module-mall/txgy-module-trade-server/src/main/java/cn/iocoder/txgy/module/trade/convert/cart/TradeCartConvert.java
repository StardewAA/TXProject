package cn.iocoder.txgy.module.trade.convert.cart;

import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.module.product.api.sku.dto.ProductSkuRespDTO;
import cn.iocoder.txgy.module.product.api.spu.dto.ProductSpuRespDTO;
import cn.iocoder.txgy.module.product.enums.spu.ProductSpuStatusEnum;
import cn.iocoder.txgy.module.trade.controller.app.base.sku.AppProductSkuBaseRespVO;
import cn.iocoder.txgy.module.trade.controller.app.base.spu.AppProductSpuBaseRespVO;
import cn.iocoder.txgy.module.trade.controller.app.cart.vo.AppCartListRespVO;
import cn.iocoder.txgy.module.trade.dal.dataobject.cart.CartDO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static cn.iocoder.txgy.framework.common.util.collection.CollectionUtils.convertMap;

@Mapper
public interface TradeCartConvert {

    TradeCartConvert INSTANCE = Mappers.getMapper(TradeCartConvert.class);

    default AppCartListRespVO convertList(List<CartDO> carts,
                                          List<ProductSpuRespDTO> spus, List<ProductSkuRespDTO> skus) {
        Map<Long, ProductSpuRespDTO> spuMap = convertMap(spus, ProductSpuRespDTO::getId);
        Map<Long, ProductSkuRespDTO> skuMap = convertMap(skus, ProductSkuRespDTO::getId);
        // 遍历，开始转换
        List<AppCartListRespVO.Cart> validList = new ArrayList<>(carts.size());
        List<AppCartListRespVO.Cart> invalidList = new ArrayList<>();
        carts.forEach(cart -> {
            AppCartListRespVO.Cart cartVO = new AppCartListRespVO.Cart();
            cartVO.setId(cart.getId()).setCount(cart.getCount()).setSelected(cart.getSelected());
            ProductSpuRespDTO spu = spuMap.get(cart.getSpuId());
            ProductSkuRespDTO sku = skuMap.get(cart.getSkuId());
            cartVO.setSpu(BeanUtils.toBean(spu, AppProductSpuBaseRespVO.class))
                    .setSku(BeanUtils.toBean(sku, AppProductSkuBaseRespVO.class));
            // 未纳入有效审核版本的商品保留购物车条目供删除，但不能暴露新修改的待审资料。
            boolean saleable = spu != null && Boolean.TRUE.equals(spu.getSaleable())
                    && ProductSpuStatusEnum.isEnable(spu.getStatus());
            if (!saleable) {
                cartVO.setSpu(new AppProductSpuBaseRespVO().setId(cart.getSpuId())
                        .setName("商品暂不可购买").setStatus(0).setStock(0));
                cartVO.setSku(new AppProductSkuBaseRespVO().setId(cart.getSkuId())
                        .setPrice(0).setStock(0).setProperties(List.of()));
            }
            // 当前 SKU 库存不足也不能放入可结算分组；历史订单不经过此转换。
            if (spu == null
                || !saleable
                || spu.getStock() == null || spu.getStock() <= 0
                || sku == null || sku.getStock() == null || sku.getStock() < cart.getCount()) {
                invalidList.add(cartVO);
            } else {
                validList.add(cartVO);
            }
        });
        return new AppCartListRespVO().setValidList(validList).setInvalidList(invalidList);
    }

}
