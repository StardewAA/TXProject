package cn.iocoder.txgy.module.mes.dal.mysql.wm.productreceipt;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.mes.controller.admin.wm.productreceipt.vo.MesWmProductReceiptPageReqVO;
import cn.iocoder.txgy.module.mes.dal.dataobject.wm.productreceipt.MesWmProductReceiptDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * MES 产品收货单 Mapper
 */
@Mapper
public interface MesWmProductReceiptMapper extends BaseMapperX<MesWmProductReceiptDO> {

    default PageResult<MesWmProductReceiptDO> selectPage(MesWmProductReceiptPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<MesWmProductReceiptDO>()
                .likeIfPresent(MesWmProductReceiptDO::getCode, reqVO.getCode())
                .likeIfPresent(MesWmProductReceiptDO::getName, reqVO.getName())
                .eqIfPresent(MesWmProductReceiptDO::getWorkOrderId, reqVO.getWorkOrderId())
                .eqIfPresent(MesWmProductReceiptDO::getItemId, reqVO.getItemId())
                .orderByDesc(MesWmProductReceiptDO::getId));
    }

    default MesWmProductReceiptDO selectByCode(String code) {
        return selectOne(MesWmProductReceiptDO::getCode, code);
    }

}
