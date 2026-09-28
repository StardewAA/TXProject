package cn.iocoder.txgy.module.wms.dal.mysql.md.item;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.wms.dal.dataobject.md.item.WmsItemQualityReportDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.Collection;
import java.util.List;

@Mapper
public interface WmsItemQualityReportMapper extends BaseMapperX<WmsItemQualityReportDO> {
    default List<WmsItemQualityReportDO> selectListByItemId(Long itemId) {
        return selectList(new LambdaQueryWrapperX<WmsItemQualityReportDO>()
                .eq(WmsItemQualityReportDO::getItemId, itemId)
                .orderByDesc(WmsItemQualityReportDO::getId));
    }

    default List<WmsItemQualityReportDO> selectListByIds(Collection<Long> ids) {
        return selectList(new LambdaQueryWrapperX<WmsItemQualityReportDO>()
                .inIfPresent(WmsItemQualityReportDO::getId, ids));
    }

    default Long selectCountByItemId(Long itemId) {
        return selectCount(WmsItemQualityReportDO::getItemId, itemId);
    }
}
