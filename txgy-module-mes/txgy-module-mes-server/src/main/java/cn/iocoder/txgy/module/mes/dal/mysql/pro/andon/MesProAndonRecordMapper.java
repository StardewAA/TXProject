package cn.iocoder.txgy.module.mes.dal.mysql.pro.andon;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.mes.controller.admin.pro.andon.vo.record.MesProAndonRecordPageReqVO;
import cn.iocoder.txgy.module.mes.dal.dataobject.pro.andon.MesProAndonRecordDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * MES 安灯呼叫记录 Mapper
 *
 * @author OPENLAB BS
 */
@Mapper
public interface MesProAndonRecordMapper extends BaseMapperX<MesProAndonRecordDO> {

    default PageResult<MesProAndonRecordDO> selectPage(MesProAndonRecordPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<MesProAndonRecordDO>()
                .eqIfPresent(MesProAndonRecordDO::getWorkstationId, reqVO.getWorkstationId())
                .eqIfPresent(MesProAndonRecordDO::getUserId, reqVO.getUserId())
                .eqIfPresent(MesProAndonRecordDO::getHandlerUserId, reqVO.getHandlerUserId())
                .eqIfPresent(MesProAndonRecordDO::getStatus, reqVO.getStatus())
                .betweenIfPresent(MesProAndonRecordDO::getCreateTime, reqVO.getCreateTime())
                .orderByDesc(MesProAndonRecordDO::getId));
    }

}
