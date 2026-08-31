package cn.iocoder.txgy.module.im.dal.mysql.group;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.im.controller.admin.manager.group.vo.ImGroupManagerPageReqVO;
import cn.iocoder.txgy.module.im.dal.dataobject.group.ImGroupDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * IM 群 Mapper
 *
 * @author OPENLAB BS
 */
@Mapper
public interface ImGroupMapper extends BaseMapperX<ImGroupDO> {

    default ImGroupDO selectByIdForUpdate(Long id) {
        return selectOne(new LambdaQueryWrapperX<ImGroupDO>()
                .eq(ImGroupDO::getId, id)
                .last("FOR UPDATE"));
    }

    default PageResult<ImGroupDO> selectPage(ImGroupManagerPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<ImGroupDO>()
                .likeIfPresent(ImGroupDO::getName, reqVO.getName())
                .eqIfPresent(ImGroupDO::getOwnerUserId, reqVO.getOwnerUserId())
                .eqIfPresent(ImGroupDO::getStatus, reqVO.getStatus())
                .eqIfPresent(ImGroupDO::getBanned, reqVO.getBanned())
                .betweenIfPresent(ImGroupDO::getCreateTime, reqVO.getCreateTime())
                .orderByDesc(ImGroupDO::getId));
    }

}
