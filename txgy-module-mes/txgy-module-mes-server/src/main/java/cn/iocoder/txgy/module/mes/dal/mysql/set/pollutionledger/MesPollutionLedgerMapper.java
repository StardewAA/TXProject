package cn.iocoder.txgy.module.mes.dal.mysql.set.pollutionledger;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.mes.controller.admin.set.pollutionledger.vo.MesPollutionLedgerPageReqVO;
import cn.iocoder.txgy.module.mes.dal.dataobject.set.pollutionledger.MesPollutionLedgerDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.Arrays;
import java.util.List;

/**
 * MES 安全环保检测-污染/危废暂存台账 Mapper
 *
 * @author OPENLAB BS
 */
@Mapper
public interface MesPollutionLedgerMapper extends BaseMapperX<MesPollutionLedgerDO> {

    default MesPollutionLedgerDO selectBySourceCheckId(Long sourceCheckId) {
        return selectOne(MesPollutionLedgerDO::getSourceCheckId, sourceCheckId);
    }

    /** 该批次仍未闭环(暂存/处置中)的台账行，按 id 升序 → 最早登记的污染事实在前，投影批次戳时取首行 */
    default List<MesPollutionLedgerDO> selectOpenByBatchNo(String batchNo) {
        return selectList(new LambdaQueryWrapperX<MesPollutionLedgerDO>()
                .eq(MesPollutionLedgerDO::getBatchNo, batchNo)
                .in(MesPollutionLedgerDO::getStatus, Arrays.asList(
                        MesPollutionLedgerDO.STATUS_STORED, MesPollutionLedgerDO.STATUS_PROCESSING))
                .orderByAsc(MesPollutionLedgerDO::getId));
    }

    default PageResult<MesPollutionLedgerDO> selectPage(MesPollutionLedgerPageReqVO reqVO) {
        LambdaQueryWrapperX<MesPollutionLedgerDO> query = new LambdaQueryWrapperX<MesPollutionLedgerDO>()
                .eqIfPresent(MesPollutionLedgerDO::getBatchNo, reqVO.getBatchNo())
                .eqIfPresent(MesPollutionLedgerDO::getLocation, reqVO.getLocation())
                .eqIfPresent(MesPollutionLedgerDO::getStatus, reqVO.getStatus())
                .likeIfPresent(MesPollutionLedgerDO::getSourceRecordNo, reqVO.getSourceRecordNo())
                .likeIfPresent(MesPollutionLedgerDO::getBizNo, reqVO.getBizNo())
                .likeIfPresent(MesPollutionLedgerDO::getItemName, reqVO.getItemName())
                .orderByDesc(MesPollutionLedgerDO::getId);
        return selectPage(reqVO, query);
    }

}
