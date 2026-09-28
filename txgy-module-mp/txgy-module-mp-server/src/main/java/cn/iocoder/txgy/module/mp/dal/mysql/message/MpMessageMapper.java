package cn.iocoder.txgy.module.mp.dal.mysql.message;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.mp.controller.admin.message.vo.message.MpMessagePageReqVO;
import cn.iocoder.txgy.module.mp.dal.dataobject.message.MpMessageDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MpMessageMapper extends BaseMapperX<MpMessageDO> {

    default PageResult<MpMessageDO> selectPage(MpMessagePageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<MpMessageDO>()
                .eqIfPresent(MpMessageDO::getAccountId, reqVO.getAccountId())
                .eqIfPresent(MpMessageDO::getType, reqVO.getType())
                .eqIfPresent(MpMessageDO::getOpenid, reqVO.getOpenid())
                .eqIfPresent(MpMessageDO::getUserId, reqVO.getUserId())
                .betweenIfPresent(MpMessageDO::getCreateTime, reqVO.getCreateTime())
                .orderByDesc(MpMessageDO::getId));
    }

}
