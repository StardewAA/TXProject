package cn.iocoder.txgy.module.training.dal.mysql.resource;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.training.dal.dataobject.resource.VideoTranscodeTaskDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 视频转码任务 Mapper
 *
 * @author 云培训学院课题小组
 */
@Mapper
public interface VideoTranscodeTaskMapper extends BaseMapperX<VideoTranscodeTaskDO> {

    default List<VideoTranscodeTaskDO> selectListByVideoId(Long videoId) {
        return selectList(new LambdaQueryWrapperX<VideoTranscodeTaskDO>()
                .eq(VideoTranscodeTaskDO::getVideoId, videoId)
                .orderByAsc(VideoTranscodeTaskDO::getId));
    }

    default VideoTranscodeTaskDO selectSuccessByVideoAndDefinition(Long videoId, String definition) {
        return selectOne(new LambdaQueryWrapperX<VideoTranscodeTaskDO>()
                .eq(VideoTranscodeTaskDO::getVideoId, videoId)
                .eq(VideoTranscodeTaskDO::getDefinition, definition)
                .eq(VideoTranscodeTaskDO::getStatus, 2)
                .last("LIMIT 1"));
    }

}
