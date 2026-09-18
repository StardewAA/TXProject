package cn.iocoder.txgy.module.mes.dal.mysql.tm.toolreturn;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.module.mes.dal.dataobject.tm.toolreturn.MesTmToolReturnLineDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface MesTmToolReturnLineMapper extends BaseMapperX<MesTmToolReturnLineDO> {

    default List<MesTmToolReturnLineDO> selectListByReturnId(Long returnId) {
        return selectList(MesTmToolReturnLineDO::getReturnId, returnId);
    }

    default void deleteByReturnId(Long returnId) {
        delete(MesTmToolReturnLineDO::getReturnId, returnId);
    }
}
