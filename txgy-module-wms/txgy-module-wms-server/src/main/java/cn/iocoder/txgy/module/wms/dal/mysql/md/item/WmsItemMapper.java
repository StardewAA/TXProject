package cn.iocoder.txgy.module.wms.dal.mysql.md.item;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.wms.controller.admin.md.item.vo.item.WmsItemListReqVO;
import cn.iocoder.txgy.module.wms.controller.admin.md.item.vo.item.WmsItemPageReqVO;
import cn.iocoder.txgy.module.wms.dal.dataobject.md.item.WmsItemDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.Collection;
import java.util.List;

/**
 * WMS 商品 Mapper
 *
 * @author OPENLAB BS
 */
@Mapper
public interface WmsItemMapper extends BaseMapperX<WmsItemDO> {

    default PageResult<WmsItemDO> selectPage(WmsItemPageReqVO reqVO, Collection<Long> categoryIds) {
        return selectPage(reqVO, new LambdaQueryWrapperX<WmsItemDO>()
                .likeIfPresent(WmsItemDO::getCode, reqVO.getCode())
                .likeIfPresent(WmsItemDO::getName, reqVO.getName())
                .inIfPresent(WmsItemDO::getCategoryId, categoryIds)
                .eqIfPresent(WmsItemDO::getBrandId, reqVO.getBrandId())
                .betweenIfPresent(WmsItemDO::getCreateTime, reqVO.getCreateTime())
                .orderByDesc(WmsItemDO::getId));
    }

    default List<WmsItemDO> selectList(WmsItemListReqVO reqVO, Collection<Long> categoryIds) {
        return selectList(new LambdaQueryWrapperX<WmsItemDO>()
                .likeIfPresent(WmsItemDO::getCode, reqVO.getCode())
                .likeIfPresent(WmsItemDO::getName, reqVO.getName())
                .inIfPresent(WmsItemDO::getCategoryId, categoryIds)
                .eqIfPresent(WmsItemDO::getBrandId, reqVO.getBrandId())
                .betweenIfPresent(WmsItemDO::getCreateTime, reqVO.getCreateTime())
                .orderByDesc(WmsItemDO::getId));
    }

    default WmsItemDO selectByName(String name) {
        return selectOne(WmsItemDO::getName, name);
    }

    default WmsItemDO selectByCode(String code) {
        return selectOne(WmsItemDO::getCode, code);
    }

    default Long selectCountByCategoryId(Long categoryId) {
        return selectCount(WmsItemDO::getCategoryId, categoryId);
    }

    default Long selectCountByBrandId(Long brandId) {
        return selectCount(WmsItemDO::getBrandId, brandId);
    }

}
