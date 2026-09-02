package cn.iocoder.txgy.module.mes.dal.mysql.pro.card;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.mes.controller.admin.pro.card.vo.MesProCardPageReqVO;
import cn.iocoder.txgy.module.mes.dal.dataobject.pro.card.MesProCardDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * MES 生产流转卡 Mapper
 *
 * @author OPENLAB BS
 */
@Mapper
public interface MesProCardMapper extends BaseMapperX<MesProCardDO> {

    default PageResult<MesProCardDO> selectPage(MesProCardPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<MesProCardDO>()
                .likeIfPresent(MesProCardDO::getCode, reqVO.getCode())
                .eqIfPresent(MesProCardDO::getWorkOrderId, reqVO.getWorkOrderId())
                .eqIfPresent(MesProCardDO::getItemId, reqVO.getItemId())
                .likeIfPresent(MesProCardDO::getBatchCode, reqVO.getBatchCode())
                .orderByDesc(MesProCardDO::getId));
    }

    default MesProCardDO selectByCode(String code) {
        return selectOne(MesProCardDO::getCode, code);
    }

}
