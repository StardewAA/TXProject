package cn.iocoder.txgy.module.mes.service.pro.project;

import cn.hutool.core.util.ObjUtil;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.module.mes.controller.admin.pro.project.vo.MesProProjectMilestoneSaveReqVO;
import cn.iocoder.txgy.module.mes.dal.dataobject.pro.project.MesProProjectMilestoneDO;
import cn.iocoder.txgy.module.mes.dal.mysql.pro.project.MesProProjectMapper;
import cn.iocoder.txgy.module.mes.dal.mysql.pro.project.MesProProjectMilestoneMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.util.Collections;
import java.util.List;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.mes.enums.ErrorCodeConstants.*;

/**
 * MES 项目里程碑 Service 实现类
 *
 * @author OPENLAB BS
 */
@Service
@Validated
public class MesProProjectMilestoneServiceImpl implements MesProProjectMilestoneService {

    @Resource
    private MesProProjectMilestoneMapper milestoneMapper;
    @Resource
    private MesProProjectMapper projectMapper;

    @Override
    public Long createMilestone(MesProProjectMilestoneSaveReqVO createReqVO) {
        // 1. 校验数据
        validateMilestoneData(null, createReqVO);
        // 2. 插入（未传状态时默认未开始）
        MesProProjectMilestoneDO milestone = BeanUtils.toBean(createReqVO, MesProProjectMilestoneDO.class);
        if (milestone.getStatus() == null) {
            milestone.setStatus(0);
        }
        milestoneMapper.insert(milestone);
        return milestone.getId();
    }

    @Override
    public void updateMilestone(MesProProjectMilestoneSaveReqVO updateReqVO) {
        // 1.1 校验存在
        validateMilestoneExists(updateReqVO.getId());
        // 1.2 校验数据
        validateMilestoneData(updateReqVO.getId(), updateReqVO);
        // 2. 更新
        MesProProjectMilestoneDO updateObj = BeanUtils.toBean(updateReqVO, MesProProjectMilestoneDO.class);
        milestoneMapper.updateById(updateObj);
    }

    @Override
    public void deleteMilestone(Long id) {
        // 1. 校验存在
        validateMilestoneExists(id);
        // 2. 删除
        milestoneMapper.deleteById(id);
    }

    @Override
    public MesProProjectMilestoneDO getMilestone(Long id) {
        return milestoneMapper.selectById(id);
    }

    @Override
    public List<MesProProjectMilestoneDO> getMilestoneList(Long projectId) {
        if (projectId == null) {
            return Collections.emptyList();
        }
        return milestoneMapper.selectListByProjectId(projectId);
    }

    // ==================== 校验方法 ====================

    private MesProProjectMilestoneDO validateMilestoneExists(Long id) {
        MesProProjectMilestoneDO milestone = milestoneMapper.selectById(id);
        if (milestone == null) {
            throw exception(PRO_PROJECT_MILESTONE_NOT_EXISTS);
        }
        return milestone;
    }

    private void validateMilestoneData(Long id, MesProProjectMilestoneSaveReqVO reqVO) {
        // 1. 校验所属项目存在
        if (projectMapper.selectById(reqVO.getProjectId()) == null) {
            throw exception(PRO_PROJECT_NOT_EXISTS);
        }
        // 2. 校验同一项目下里程碑名称不重复
        MesProProjectMilestoneDO milestone =
                milestoneMapper.selectByProjectIdAndName(reqVO.getProjectId(), reqVO.getName());
        if (milestone != null && ObjUtil.notEqual(milestone.getId(), id)) {
            throw exception(PRO_PROJECT_MILESTONE_NAME_DUPLICATE);
        }
    }

}
