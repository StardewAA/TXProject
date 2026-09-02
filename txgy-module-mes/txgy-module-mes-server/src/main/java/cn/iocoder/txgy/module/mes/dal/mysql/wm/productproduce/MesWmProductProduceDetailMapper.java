package cn.iocoder.txgy.module.mes.dal.mysql.wm.productproduce;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.module.mes.dal.dataobject.wm.productproduce.MesWmProductProduceDetailDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * MES 生产入库明细 Mapper
 *
 * @author OPENLAB BS
 */
@Mapper
public interface MesWmProductProduceDetailMapper extends BaseMapperX<MesWmProductProduceDetailDO> {

    default List<MesWmProductProduceDetailDO> selectListByLineId(Long lineId) {
        return selectList(MesWmProductProduceDetailDO::getLineId, lineId);
    }

}
