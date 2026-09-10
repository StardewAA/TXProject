package cn.iocoder.txgy.module.training.dal.mysql.learning;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.training.dal.dataobject.learning.LiveDanmakuDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 直播弹幕 Mapper
 *
 * @author 云培训学院课题小组
 */
@Mapper
public interface LiveDanmakuMapper extends BaseMapperX<LiveDanmakuDO> {

    default List<LiveDanmakuDO> selectListByRoomId(Long roomId, Integer limit) {
        return selectList(new LambdaQueryWrapperX<LiveDanmakuDO>()
                .eq(LiveDanmakuDO::getRoomId, roomId)
                .orderByAsc(LiveDanmakuDO::getId)
                .last("limit " + (limit == null ? 100 : limit)));
    }

    default List<LiveDanmakuDO> selectListAfterId(Long roomId, Long afterId, Integer limit) {
        return selectList(new LambdaQueryWrapperX<LiveDanmakuDO>()
                .eq(LiveDanmakuDO::getRoomId, roomId)
                .gt(LiveDanmakuDO::getId, afterId)
                .orderByAsc(LiveDanmakuDO::getId)
                .last("limit " + (limit == null ? 100 : limit)));
    }

}
