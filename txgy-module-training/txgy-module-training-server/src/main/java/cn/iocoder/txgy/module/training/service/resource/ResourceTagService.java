package cn.iocoder.txgy.module.training.service.resource;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.training.controller.admin.resource.vo.ResourceTagPageReqVO;
import cn.iocoder.txgy.module.training.controller.admin.resource.vo.ResourceTagSaveReqVO;
import cn.iocoder.txgy.module.training.dal.dataobject.resource.ResourceTagDO;

import java.util.List;

/**
 * 资源标签 Service
 *
 * @author 云培训学院课题小组
 */
public interface ResourceTagService {

    Long createTag(ResourceTagSaveReqVO reqVO);

    void updateTag(ResourceTagSaveReqVO reqVO);

    void deleteTag(Long id);

    PageResult<ResourceTagDO> getTagPage(ResourceTagPageReqVO reqVO);

    List<ResourceTagDO> getTagList(String dimension);

    /**
     * 按标签 ID 集合批量校验存在性（编辑试题时打标校验）
     */
    void validateTags(List<Long> tagIds);

}
