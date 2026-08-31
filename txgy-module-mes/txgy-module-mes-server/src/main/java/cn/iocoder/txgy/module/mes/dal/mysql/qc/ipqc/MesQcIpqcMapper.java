package cn.iocoder.txgy.module.mes.dal.mysql.qc.ipqc;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.mes.controller.admin.qc.ipqc.vo.MesQcIpqcPageReqVO;
import cn.iocoder.txgy.module.mes.dal.dataobject.qc.ipqc.MesQcIpqcDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * MES 过程检验单（IPQC） Mapper
 *
 * @author OPENLAB BS
 */
@Mapper
public interface MesQcIpqcMapper extends BaseMapperX<MesQcIpqcDO> {

    default PageResult<MesQcIpqcDO> selectPage(MesQcIpqcPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<MesQcIpqcDO>()
                .likeIfPresent(MesQcIpqcDO::getCode, reqVO.getCode())
                .eqIfPresent(MesQcIpqcDO::getType, reqVO.getType())
                .eqIfPresent(MesQcIpqcDO::getWorkOrderId, reqVO.getWorkOrderId())
                .eqIfPresent(MesQcIpqcDO::getItemId, reqVO.getItemId())
                .eqIfPresent(MesQcIpqcDO::getCheckResult, reqVO.getCheckResult())
                .eqIfPresent(MesQcIpqcDO::getStatus, reqVO.getStatus())
                .eqIfPresent(MesQcIpqcDO::getInspectorUserId, reqVO.getInspectorUserId())
                .orderByDesc(MesQcIpqcDO::getId));
    }

    default MesQcIpqcDO selectByCode(String code) {
        return selectOne(MesQcIpqcDO::getCode, code);
    }

}
