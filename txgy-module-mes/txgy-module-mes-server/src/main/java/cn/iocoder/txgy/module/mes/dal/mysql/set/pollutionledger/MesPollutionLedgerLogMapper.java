package cn.iocoder.txgy.module.mes.dal.mysql.set.pollutionledger;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.mes.dal.dataobject.set.pollutionledger.MesPollutionLedgerLogDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * MES 安全环保检测-污染/危废暂存台账-流转历史 Mapper
 *
 * @author OPENLAB BS
 */
@Mapper
public interface MesPollutionLedgerLogMapper extends BaseMapperX<MesPollutionLedgerLogDO> {

    /** 某条台账的完整流转链，按 id 升序 = 时间正序（登记→处置→闭环） */
    default List<MesPollutionLedgerLogDO> selectByLedgerId(Long ledgerId) {
        return selectList(new LambdaQueryWrapperX<MesPollutionLedgerLogDO>()
                .eq(MesPollutionLedgerLogDO::getLedgerId, ledgerId)
                .orderByAsc(MesPollutionLedgerLogDO::getId));
    }

}
