package cn.iocoder.txgy.module.hrm.service.recruit.post;

import cn.hutool.core.collection.CollUtil;
import cn.iocoder.txgy.framework.common.enums.CommonStatusEnum;
import cn.iocoder.txgy.module.hrm.controller.admin.recruit.vo.post.type.HrmRecruitPostTypeSaveReqVO;
import cn.iocoder.txgy.module.hrm.controller.admin.recruit.vo.post.type.HrmRecruitPostTypeStatusReqVO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.recruit.post.HrmRecruitPostTypeDO;
import cn.iocoder.txgy.module.hrm.dal.mysql.recruit.post.HrmRecruitPostTypeMapper;
import cn.iocoder.txgy.module.hrm.dal.mysql.recruit.post.HrmRecruitPostMapper;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.RECRUIT_POST_TYPE_NOT_EXISTS;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.RECRUIT_POST_TYPE_PARENT_ERROR;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.RECRUIT_POST_TYPE_PARENT_NOT_EXISTS;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.RECRUIT_POST_TYPE_NAME_DUPLICATE;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.RECRUIT_POST_TYPE_HAS_CHILDREN;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.RECRUIT_POST_TYPE_USED;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.RECRUIT_POST_TYPE_DISABLED;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.RECRUIT_POST_TYPE_NOT_LEAF;

/**
 * 招聘职位类型 Service 实现类
 *
 * @author OPENLAB BS
 */
@Service
@Validated
public class HrmRecruitPostTypeServiceImpl implements HrmRecruitPostTypeService {

    @Resource
    private HrmRecruitPostTypeMapper recruitPostTypeMapper;

    @Resource
    private HrmRecruitPostMapper recruitPostMapper;

    @Override
    public Long createRecruitPostType(HrmRecruitPostTypeSaveReqVO reqVO) {
        Long parentId = normalizeParentId(reqVO.getParentId());
        validateParentId(null, parentId);
        validateNameDuplicate(reqVO.getName(), parentId, null);
        HrmRecruitPostTypeDO type = BeanUtils.toBean(reqVO, HrmRecruitPostTypeDO.class)
                .setParentId(parentId)
                .setSort(reqVO.getSort() == null ? 0 : reqVO.getSort())
                .setStatus(CommonStatusEnum.ENABLE.getStatus());
        recruitPostTypeMapper.insert(type);
        return type.getId();
    }

    @Override
    public void updateRecruitPostType(HrmRecruitPostTypeSaveReqVO reqVO) {
        HrmRecruitPostTypeDO current = getRequired(reqVO.getId());
        Long parentId = normalizeParentId(reqVO.getParentId());
        validateParentId(current.getId(), parentId);
        validateNameDuplicate(reqVO.getName(), parentId, current.getId());
        recruitPostTypeMapper.updateById(BeanUtils.toBean(reqVO, HrmRecruitPostTypeDO.class)
                .setParentId(parentId)
                .setSort(reqVO.getSort() == null ? 0 : reqVO.getSort()));
    }

    @Override
    public void updateRecruitPostTypeStatus(HrmRecruitPostTypeStatusReqVO reqVO) {
        getRequired(reqVO.getId());
        recruitPostTypeMapper.updateById(new HrmRecruitPostTypeDO()
                .setId(reqVO.getId()).setStatus(reqVO.getStatus()));
    }

    @Override
    public void deleteRecruitPostType(Long id) {
        getRequired(id);
        if (hasChildren(id)) {
            throw exception(RECRUIT_POST_TYPE_HAS_CHILDREN);
        }
        if (recruitPostMapper.selectCountByPostTypeId(id) > 0) {
            throw exception(RECRUIT_POST_TYPE_USED);
        }
        recruitPostTypeMapper.deleteById(id);
    }

    @Override
    public HrmRecruitPostTypeDO getRecruitPostType(Long id) {
        return recruitPostTypeMapper.selectById(id);
    }

    @Override
    public void validateRecruitPostTypeExists(Long id) {
        if (id == null) {
            return;
        }
        if (recruitPostTypeMapper.selectById(id) == null) {
            throw exception(RECRUIT_POST_TYPE_NOT_EXISTS);
        }
    }

    @Override
    public boolean hasChildren(Long id) {
        return id != null && recruitPostTypeMapper.hasChildren(id);
    }

    @Override
    public boolean isUsableLeaf(Long id) {
        if (id == null) {
            return false;
        }
        HrmRecruitPostTypeDO type = getRequired(id);
        return CommonStatusEnum.isEnable(type.getStatus()) && !hasChildren(id);
    }

    @Override
    public void validateRecruitPostTypeUsable(Long id) {
        if (id == null) {
            return;
        }
        HrmRecruitPostTypeDO type = getRequired(id);
        if (!CommonStatusEnum.isEnable(type.getStatus())) {
            throw exception(RECRUIT_POST_TYPE_DISABLED);
        }
        if (hasChildren(id)) {
            throw exception(RECRUIT_POST_TYPE_NOT_LEAF);
        }
    }

    private HrmRecruitPostTypeDO getRequired(Long id) {
        HrmRecruitPostTypeDO type = recruitPostTypeMapper.selectById(id);
        if (type == null) {
            throw exception(RECRUIT_POST_TYPE_NOT_EXISTS);
        }
        return type;
    }

    private void validateParentId(Long id, Long parentId) {
        if (parentId == 0L) {
            return;
        }
        if (id != null && (id.equals(parentId) || isDescendant(parentId, id))) {
            throw exception(RECRUIT_POST_TYPE_PARENT_ERROR);
        }
        if (recruitPostTypeMapper.selectById(parentId) == null) {
            throw exception(RECRUIT_POST_TYPE_PARENT_NOT_EXISTS);
        }
    }

    private boolean isDescendant(Long parentId, Long id) {
        Set<Long> visited = new HashSet<>();
        Long currentId = parentId;
        while (currentId != null && currentId != 0L && visited.add(currentId)) {
            HrmRecruitPostTypeDO current = recruitPostTypeMapper.selectById(currentId);
            if (current == null) {
                return false;
            }
            if (id.equals(current.getParentId())) {
                return true;
            }
            currentId = current.getParentId();
        }
        return false;
    }

    private void validateNameDuplicate(String name, Long parentId, Long id) {
        HrmRecruitPostTypeDO same = recruitPostTypeMapper.selectByNameAndParentId(name, parentId);
        if (same != null && !same.getId().equals(id)) {
            throw exception(RECRUIT_POST_TYPE_NAME_DUPLICATE);
        }
    }

    private Long normalizeParentId(Long parentId) {
        return parentId == null ? 0L : parentId;
    }

    @Override
    public List<HrmRecruitPostTypeDO> getRecruitPostTypeList(Integer status) {
        return recruitPostTypeMapper.selectListByStatus(status);
    }

    @Override
    public List<Long> getRecruitPostTypeIdsByName(String name) {
        return recruitPostTypeMapper.selectIdsByName(name);
    }

    @Override
    public List<HrmRecruitPostTypeDO> getRecruitPostTypeListByIds(Collection<Long> ids) {
        if (CollUtil.isEmpty(ids)) {
            return Collections.emptyList();
        }
        return recruitPostTypeMapper.selectByIds(ids);
    }

}
