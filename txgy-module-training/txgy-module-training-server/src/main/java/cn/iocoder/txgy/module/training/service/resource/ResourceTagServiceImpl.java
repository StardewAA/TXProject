package cn.iocoder.txgy.module.training.service.resource;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.training.controller.admin.resource.vo.ResourceTagPageReqVO;
import cn.iocoder.txgy.module.training.controller.admin.resource.vo.ResourceTagSaveReqVO;
import cn.iocoder.txgy.module.training.dal.dataobject.resource.QuestionTagDO;
import cn.iocoder.txgy.module.training.dal.dataobject.resource.ResourceTagDO;
import cn.iocoder.txgy.module.training.dal.mysql.resource.QuestionTagMapper;
import cn.iocoder.txgy.module.training.dal.mysql.resource.ResourceTagMapper;
import cn.iocoder.txgy.module.training.enums.ErrorCodeConstants;
import cn.iocoder.txgy.module.training.enums.resource.ResourceConstants;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 资源标签 Service 实现
 *
 * @author 云培训学院课题小组
 */
@Slf4j
@Service
public class ResourceTagServiceImpl implements ResourceTagService {

    @Resource
    private ResourceTagMapper tagMapper;
    @Resource
    private QuestionTagMapper questionTagMapper;
    @Resource
    private OperationLogService operationLogService;

    @Override
    public Long createTag(ResourceTagSaveReqVO reqVO) {
        checkDimension(reqVO.getDimension());
        checkNameUnique(reqVO.getName(), reqVO.getDimension(), null);
        ResourceTagDO tagDO = new ResourceTagDO();
        tagDO.setName(reqVO.getName());
        tagDO.setDimension(reqVO.getDimension());
        tagDO.setSort(ObjectUtil.defaultIfNull(reqVO.getSort(), 0));
        tagMapper.insert(tagDO);
        operationLogService.createLog(ResourceConstants.ACTION_CREATE, ResourceConstants.TARGET_TAG,
                tagDO.getId(), "新增标签：" + reqVO.getName() + "（" + reqVO.getDimension() + "）");
        return tagDO.getId();
    }

    @Override
    public void updateTag(ResourceTagSaveReqVO reqVO) {
        validateExists(reqVO.getId());
        checkDimension(reqVO.getDimension());
        checkNameUnique(reqVO.getName(), reqVO.getDimension(), reqVO.getId());
        ResourceTagDO update = new ResourceTagDO();
        update.setId(reqVO.getId());
        update.setName(reqVO.getName());
        update.setDimension(reqVO.getDimension());
        update.setSort(ObjectUtil.defaultIfNull(reqVO.getSort(), 0));
        tagMapper.updateById(update);
        operationLogService.createLog(ResourceConstants.ACTION_UPDATE, ResourceConstants.TARGET_TAG,
                reqVO.getId(), "更新标签：" + reqVO.getName());
    }

    @Override
    public void deleteTag(Long id) {
        validateExists(id);
        List<QuestionTagDO> refs = questionTagMapper.selectListByTagId(id);
        if (CollUtil.isNotEmpty(refs)) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.RESOURCE_TAG_DELETE_HAS_QUESTION);
        }
        tagMapper.deleteById(id);
        operationLogService.createLog(ResourceConstants.ACTION_DELETE, ResourceConstants.TARGET_TAG,
                id, "删除标签");
    }

    @Override
    public PageResult<ResourceTagDO> getTagPage(ResourceTagPageReqVO reqVO) {
        return tagMapper.selectPage(reqVO);
    }

    @Override
    public List<ResourceTagDO> getTagList(String dimension) {
        if (StrUtil.isNotBlank(dimension)) {
            checkDimension(dimension);
        }
        return tagMapper.selectListByDimension(dimension);
    }

    @Override
    public void validateTags(List<Long> tagIds) {
        if (CollUtil.isEmpty(tagIds)) {
            return;
        }
        for (Long tagId : tagIds) {
            if (tagMapper.selectById(tagId) == null) {
                throw ServiceExceptionUtil.exception(ErrorCodeConstants.RESOURCE_TAG_NOT_EXISTS);
            }
        }
    }

    // ========== 私有方法 ==========

    private void checkDimension(String dimension) {
        if (!ResourceConstants.TAG_DIMENSIONS.contains(dimension)) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.RESOURCE_TAG_DIMENSION_INVALID);
        }
    }

    private void checkNameUnique(String name, String dimension, Long excludeId) {
        ResourceTagDO exist = tagMapper.selectByNameAndDimension(name, dimension);
        if (exist != null && !ObjectUtil.equal(exist.getId(), excludeId)) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.RESOURCE_TAG_NAME_EXISTS);
        }
    }

    private ResourceTagDO validateExists(Long id) {
        ResourceTagDO tag = tagMapper.selectById(id);
        if (tag == null) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.RESOURCE_TAG_NOT_EXISTS);
        }
        return tag;
    }

}
