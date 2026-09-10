package cn.iocoder.txgy.module.training.dal.mysql.learning;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.training.controller.admin.learning.vo.LiveRoomPageReqVO;
import cn.iocoder.txgy.module.training.dal.dataobject.learning.LiveRoomDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 直播房间 Mapper
 *
 * @author 云培训学院课题小组
 */
@Mapper
public interface LiveRoomMapper extends BaseMapperX<LiveRoomDO> {

    default PageResult<LiveRoomDO> selectPage(LiveRoomPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<LiveRoomDO>()
                .likeIfPresent(LiveRoomDO::getTitle, reqVO.getTitle())
                .eqIfPresent(LiveRoomDO::getStatus, reqVO.getStatus())
                .orderByAsc(LiveRoomDO::getSort)
                .orderByDesc(LiveRoomDO::getId));
    }

    default List<LiveRoomDO> selectListByStatus(Integer status) {
        return selectList(new LambdaQueryWrapperX<LiveRoomDO>()
                .eqIfPresent(LiveRoomDO::getStatus, status)
                .orderByAsc(LiveRoomDO::getSort)
                .orderByDesc(LiveRoomDO::getId));
    }

}
