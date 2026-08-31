package cn.iocoder.txgy.module.mes.dal.mysql.wm.returnsales;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.mes.controller.admin.wm.returnsales.vo.line.MesWmReturnSalesLinePageReqVO;
import cn.iocoder.txgy.module.mes.dal.dataobject.wm.returnsales.MesWmReturnSalesLineDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * MES 销售退货单行 Mapper
 *
 * @author OPENLAB BS
 */
@Mapper
public interface MesWmReturnSalesLineMapper extends BaseMapperX<MesWmReturnSalesLineDO> {

    default PageResult<MesWmReturnSalesLineDO> selectPage(MesWmReturnSalesLinePageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<MesWmReturnSalesLineDO>()
                .eqIfPresent(MesWmReturnSalesLineDO::getReturnId, reqVO.getReturnId())
                .eqIfPresent(MesWmReturnSalesLineDO::getItemId, reqVO.getItemId())
                .orderByDesc(MesWmReturnSalesLineDO::getId));
    }

    default List<MesWmReturnSalesLineDO> selectListByReturnId(Long returnId) {
        return selectList(new LambdaQueryWrapperX<MesWmReturnSalesLineDO>()
                .eq(MesWmReturnSalesLineDO::getReturnId, returnId));
    }

    default void deleteByReturnId(Long returnId) {
        delete(new LambdaQueryWrapperX<MesWmReturnSalesLineDO>()
                .eq(MesWmReturnSalesLineDO::getReturnId, returnId));
    }

}
