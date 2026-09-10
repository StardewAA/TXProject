package cn.iocoder.txgy.module.training.service.course;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.training.controller.admin.course.vo.GrowthLevelPageReqVO;
import cn.iocoder.txgy.module.training.controller.admin.course.vo.GrowthLevelSaveReqVO;
import cn.iocoder.txgy.module.training.dal.dataobject.course.GrowthLevelDO;
import cn.iocoder.txgy.module.training.dal.mysql.course.CourseMapper;
import cn.iocoder.txgy.module.training.dal.mysql.course.GrowthLevelMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.training.enums.ErrorCodeConstants.*;

/**
 * 成长等级 Service 实现
 *
 * @author 云培训学院课题小组
 */
@Service
@Slf4j
public class GrowthLevelServiceImpl implements GrowthLevelService {

    @Resource
    private GrowthLevelMapper growthLevelMapper;
    @Resource
    private CourseMapper courseMapper;

    @Override
    public Long createGrowthLevel(GrowthLevelSaveReqVO createReqVO) {
        validateUnique(null, createReqVO.getCode(), createReqVO.getLevelNo(), createReqVO.getName());
        GrowthLevelDO growthLevel = BeanUtils.toBean(createReqVO, GrowthLevelDO.class);
        growthLevel.setMaxApprentice(growthLevel.getMaxApprentice() == null ? 0 : growthLevel.getMaxApprentice());
        growthLevel.setNeedReview(growthLevel.getNeedReview() == null ? Boolean.FALSE : growthLevel.getNeedReview());
        growthLevel.setSort(growthLevel.getSort() == null ? 0 : growthLevel.getSort());
        growthLevelMapper.insert(growthLevel);
        return growthLevel.getId();
    }

    @Override
    public void updateGrowthLevel(GrowthLevelSaveReqVO updateReqVO) {
        GrowthLevelDO growthLevel = validateGrowthLevelExists(updateReqVO.getId());
        validateUnique(growthLevel.getId(), updateReqVO.getCode(), updateReqVO.getLevelNo(), updateReqVO.getName());
        GrowthLevelDO updateObj = BeanUtils.toBean(updateReqVO, GrowthLevelDO.class);
        growthLevelMapper.updateById(updateObj);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteGrowthLevel(Long id) {
        validateGrowthLevelExists(id);
        // 删除保护：已被课程成长等级标记引用时禁止删除（可先调整课程等级），409
        GrowthLevelDO growthLevel = growthLevelMapper.selectById(id);
        Long courseCount = courseMapper.selectCountByCourseLevel(growthLevel.getCode());
        if (courseCount != null && courseCount > 0) {
            throw exception(GROWTH_LEVEL_HAS_COURSE);
        }
        growthLevelMapper.deleteById(id);
    }

    @Override
    public PageResult<GrowthLevelDO> getGrowthLevelPage(GrowthLevelPageReqVO pageReqVO) {
        return growthLevelMapper.selectPage(pageReqVO);
    }

    @Override
    public List<GrowthLevelDO> getGrowthLevelList() {
        return growthLevelMapper.selectList();
    }

    @Override
    public GrowthLevelDO getGrowthLevel(Long id) {
        return growthLevelMapper.selectById(id);
    }

    private GrowthLevelDO validateGrowthLevelExists(Long id) {
        GrowthLevelDO growthLevel = growthLevelMapper.selectById(id);
        if (growthLevel == null) {
            throw exception(GROWTH_LEVEL_NOT_EXISTS);
        }
        return growthLevel;
    }

    /**
     * 编码/序号/名称唯一校验（排除自身）
     */
    private void validateUnique(Long excludeId, String code, Integer levelNo, String name) {
        if (StrUtil.isNotBlank(code)) {
            GrowthLevelDO sameCode = growthLevelMapper.selectOne(new LambdaQueryWrapperX<GrowthLevelDO>()
                    .eq(GrowthLevelDO::getCode, code)
                    .last("limit 1"));
            if (sameCode != null && (excludeId == null || !sameCode.getId().equals(excludeId))) {
                throw exception(GROWTH_LEVEL_CODE_EXISTS);
            }
        }
        if (levelNo != null) {
            GrowthLevelDO sameNo = growthLevelMapper.selectOne(new LambdaQueryWrapperX<GrowthLevelDO>()
                    .eq(GrowthLevelDO::getLevelNo, levelNo)
                    .last("limit 1"));
            if (sameNo != null && (excludeId == null || !sameNo.getId().equals(excludeId))) {
                throw exception(GROWTH_LEVEL_NO_EXISTS);
            }
        }
        if (StrUtil.isNotBlank(name)) {
            GrowthLevelDO sameName = growthLevelMapper.selectOne(new LambdaQueryWrapperX<GrowthLevelDO>()
                    .eq(GrowthLevelDO::getName, name)
                    .last("limit 1"));
            if (sameName != null && (excludeId == null || !sameName.getId().equals(excludeId))) {
                throw exception(GROWTH_LEVEL_NAME_EXISTS);
            }
        }
    }

}
