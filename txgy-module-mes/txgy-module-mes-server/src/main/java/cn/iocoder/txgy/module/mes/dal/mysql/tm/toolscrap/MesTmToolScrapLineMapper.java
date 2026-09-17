package cn.iocoder.txgy.module.mes.dal.mysql.tm.toolscrap;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.module.mes.dal.dataobject.tm.toolscrap.MesTmToolScrapLineDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface MesTmToolScrapLineMapper extends BaseMapperX<MesTmToolScrapLineDO> {

    default List<MesTmToolScrapLineDO> selectListByScrapId(Long scrapId) {
        return selectList(MesTmToolScrapLineDO::getScrapId, scrapId);
    }

    default void deleteByScrapId(Long scrapId) {
        delete(MesTmToolScrapLineDO::getScrapId, scrapId);
    }
}
