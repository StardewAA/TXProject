package cn.iocoder.txgy.module.mes.dal.mysql.pro.route;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.mes.controller.admin.pro.route.vo.MesProRoutePageReqVO;
import cn.iocoder.txgy.module.mes.dal.dataobject.pro.route.MesProRouteDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * MES 工艺路线 Mapper
 *
 * @author OPENLAB BS
 */
@Mapper
public interface MesProRouteMapper extends BaseMapperX<MesProRouteDO> {

    default PageResult<MesProRouteDO> selectPage(MesProRoutePageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<MesProRouteDO>()
                .likeIfPresent(MesProRouteDO::getCode, reqVO.getCode())
                .likeIfPresent(MesProRouteDO::getName, reqVO.getName())
                .eqIfPresent(MesProRouteDO::getStatus, reqVO.getStatus())
                .betweenIfPresent(MesProRouteDO::getCreateTime, reqVO.getCreateTime())
                .orderByDesc(MesProRouteDO::getId));
    }

    default MesProRouteDO selectByCode(String code) {
        return selectOne(MesProRouteDO::getCode, code);
    }

    default List<MesProRouteDO> selectListByStatus(Integer status) {
        return selectList(MesProRouteDO::getStatus, status);
    }

}
