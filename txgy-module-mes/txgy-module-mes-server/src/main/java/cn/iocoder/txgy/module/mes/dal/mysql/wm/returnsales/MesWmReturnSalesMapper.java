package cn.iocoder.txgy.module.mes.dal.mysql.wm.returnsales;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.mes.controller.admin.wm.returnsales.vo.MesWmReturnSalesPageReqVO;
import cn.iocoder.txgy.module.mes.dal.dataobject.wm.returnsales.MesWmReturnSalesDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * MES 销售退货单 Mapper
 *
 * @author OPENLAB BS
 */
@Mapper
public interface MesWmReturnSalesMapper extends BaseMapperX<MesWmReturnSalesDO> {

    default PageResult<MesWmReturnSalesDO> selectPage(MesWmReturnSalesPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<MesWmReturnSalesDO>()
                .likeIfPresent(MesWmReturnSalesDO::getCode, reqVO.getCode())
                .likeIfPresent(MesWmReturnSalesDO::getName, reqVO.getName())
                .likeIfPresent(MesWmReturnSalesDO::getSalesOrderCode, reqVO.getSalesOrderCode())
                .eqIfPresent(MesWmReturnSalesDO::getClientId, reqVO.getClientId())
                .eqIfPresent(MesWmReturnSalesDO::getStatus, reqVO.getStatus())
                .betweenIfPresent(MesWmReturnSalesDO::getReturnDate, reqVO.getReturnDate())
                .orderByDesc(MesWmReturnSalesDO::getId));
    }

    default MesWmReturnSalesDO selectByCode(String code) {
        return selectOne(new LambdaQueryWrapperX<MesWmReturnSalesDO>()
                .eq(MesWmReturnSalesDO::getCode, code));
    }

}
