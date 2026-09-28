package cn.iocoder.txgy.module.mes.dal.mysql.qc.indicator;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.mes.controller.admin.qc.indicator.vo.MesQcIndicatorPageReqVO;
import cn.iocoder.txgy.module.mes.dal.dataobject.qc.indicator.MesQcIndicatorDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * MES 质检指标 Mapper
 *
 * @author OPENLAB BS
 */
@Mapper
public interface MesQcIndicatorMapper extends BaseMapperX<MesQcIndicatorDO> {

    default PageResult<MesQcIndicatorDO> selectPage(MesQcIndicatorPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<MesQcIndicatorDO>()
                .likeIfPresent(MesQcIndicatorDO::getCode, reqVO.getCode())
                .likeIfPresent(MesQcIndicatorDO::getName, reqVO.getName())
                .eqIfPresent(MesQcIndicatorDO::getType, reqVO.getType())
                .eqIfPresent(MesQcIndicatorDO::getResultType, reqVO.getResultType())
                .orderByDesc(MesQcIndicatorDO::getId));
    }

    default MesQcIndicatorDO selectByCode(String code) {
        return selectOne(MesQcIndicatorDO::getCode, code);
    }

    default MesQcIndicatorDO selectByName(String name) {
        return selectOne(MesQcIndicatorDO::getName, name);
    }

    default List<MesQcIndicatorDO> selectList() {
        return selectList(new LambdaQueryWrapperX<MesQcIndicatorDO>()
                .orderByDesc(MesQcIndicatorDO::getId));
    }

}
