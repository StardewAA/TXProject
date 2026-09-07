package cn.iocoder.txgy.module.mes.dal.mysql.set.pollutioncheck;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.mes.dal.dataobject.set.pollutioncheck.MesPollutionCheckLogDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * MES 安全环保检测-污染判定履历 Mapper
 *
 * @author OPENLAB BS
 */
@Mapper
public interface MesPollutionCheckLogMapper extends BaseMapperX<MesPollutionCheckLogDO> {

    /** 某条判定的完整履历，按 id 升序 = 时间正序（创建→…→复核） */
    default List<MesPollutionCheckLogDO> selectByCheckId(Long checkId) {
        return selectList(new LambdaQueryWrapperX<MesPollutionCheckLogDO>()
                .eq(MesPollutionCheckLogDO::getCheckId, checkId)
                .orderByAsc(MesPollutionCheckLogDO::getId));
    }

}
