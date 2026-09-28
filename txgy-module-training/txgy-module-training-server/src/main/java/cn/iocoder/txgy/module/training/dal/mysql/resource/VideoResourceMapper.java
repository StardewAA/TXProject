package cn.iocoder.txgy.module.training.dal.mysql.resource;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.training.controller.admin.resource.vo.VideoPageReqVO;
import cn.iocoder.txgy.module.training.dal.dataobject.resource.VideoResourceDO;
import cn.iocoder.txgy.module.training.enums.resource.ResourceStatusEnum;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 视频资源 Mapper
 *
 * @author 云培训学院课题小组
 */
@Mapper
public interface VideoResourceMapper extends BaseMapperX<VideoResourceDO> {

    default PageResult<VideoResourceDO> selectPage(VideoPageReqVO reqVO) {
        LambdaQueryWrapperX<VideoResourceDO> query = new LambdaQueryWrapperX<VideoResourceDO>()
                .likeIfPresent(VideoResourceDO::getTitle, reqVO.getKeyword())
                .eqIfPresent(VideoResourceDO::getCategoryId, reqVO.getCategoryId())
                .eqIfPresent(VideoResourceDO::getStatus, reqVO.getStatus())
                .eqIfPresent(VideoResourceDO::getTranscodeStatus, reqVO.getTranscodeStatus())
                .orderByDesc(VideoResourceDO::getId);
        // 默认不展示归档资源（includeArchived=true 时全量展示）
        if (Boolean.FALSE.equals(reqVO.getIncludeArchived())) {
            query.ne(VideoResourceDO::getStatus, ResourceStatusEnum.ARCHIVED.getStatus());
        }
        return selectPage(reqVO, query);
    }

    default List<VideoResourceDO> selectListByCategoryId(Long categoryId) {
        return selectList(new LambdaQueryWrapperX<VideoResourceDO>()
                .eq(VideoResourceDO::getCategoryId, categoryId));
    }

    default Long selectCountByStatus(Integer status) {
        return selectCount(new LambdaQueryWrapperX<VideoResourceDO>()
                .eqIfPresent(VideoResourceDO::getStatus, status));
    }

}
