package cn.iocoder.txgy.module.product.api.spu;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.module.product.api.spu.dto.ProductSpuRespDTO;
import cn.iocoder.txgy.module.product.convert.spu.ProductSpuConvert;
import cn.iocoder.txgy.module.product.dal.dataobject.spu.ProductSpuDO;
import cn.iocoder.txgy.module.product.service.spu.ProductSpuService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.Resource;
import java.util.Collection;
import java.util.List;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;

/**
 * 商品 SPU API 接口实现类
 *
 * @author LeeYan9
 * @since 2022-09-06
 */
@RestController // 提供 RESTful API 接口，给 Feign 调用
@Validated
public class ProductSpuApiImpl implements ProductSpuApi {

    @Resource
    private ProductSpuService spuService;
    @Resource private cn.iocoder.txgy.module.product.service.review.ProductSpuReviewService reviewService;

    @Override
    public CommonResult<List<ProductSpuRespDTO>> getSpuList(Collection<Long> ids) {
        List<ProductSpuDO> spus = spuService.getSpuList(ids);
        return success(spus.stream().filter(java.util.Objects::nonNull).map(this::convert).toList());
    }

    @Override
    public CommonResult<List<ProductSpuRespDTO>> validateSpuList(Collection<Long> ids) {
        List<ProductSpuDO> spus = spuService.validateSpuList(ids);
        return success(spus.stream().map(this::convert).toList());
    }

    @Override
    public CommonResult<ProductSpuRespDTO> getSpu(Long id) {
        ProductSpuDO spu = spuService.getSpu(id);
        return success(spu == null ? null : convert(spu));
    }

    private ProductSpuRespDTO convert(ProductSpuDO p) {
        return BeanUtils.toBean(p, ProductSpuRespDTO.class).setSaleable(reviewService.saleable(p));
    }

}
