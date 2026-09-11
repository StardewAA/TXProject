package cn.iocoder.txgy.module.mes.service.pro.project;

import cn.hutool.core.util.ObjUtil;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.module.mes.controller.admin.pro.project.vo.MesProProjectMemberSaveReqVO;
import cn.iocoder.txgy.module.mes.dal.dataobject.pro.project.MesProProjectMemberDO;
import cn.iocoder.txgy.module.mes.dal.mysql.pro.project.MesProProjectMapper;
import cn.iocoder.txgy.module.mes.dal.mysql.pro.project.MesProProjectMemberMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.util.Collections;
import java.util.List;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.mes.enums.ErrorCodeConstants.*;

/**
 * MES 项目成员 Service 实现类
 *
 * @author OPENLAB BS
 */
@Service
@Validated
public class MesProProjectMemberServiceImpl implements MesProProjectMemberService {

    @Resource
    private MesProProjectMemberMapper projectMemberMapper;
    @Resource
    private MesProProjectMapper projectMapper;

    @Override
    public Long createProjectMember(MesProProjectMemberSaveReqVO createReqVO) {
        // 1. 校验数据
        validateProjectMemberData(null, createReqVO);
        // 2. 插入
        MesProProjectMemberDO member = BeanUtils.toBean(createReqVO, MesProProjectMemberDO.class);
        projectMemberMapper.insert(member);
        return member.getId();
    }

    @Override
    public void updateProjectMember(MesProProjectMemberSaveReqVO updateReqVO) {
        // 1.1 校验存在
        validateProjectMemberExists(updateReqVO.getId());
        // 1.2 校验数据
        validateProjectMemberData(updateReqVO.getId(), updateReqVO);
        // 2. 更新
        MesProProjectMemberDO updateObj = BeanUtils.toBean(updateReqVO, MesProProjectMemberDO.class);
        projectMemberMapper.updateById(updateObj);
    }

    @Override
    public void deleteProjectMember(Long id) {
        // 1. 校验存在
        validateProjectMemberExists(id);
        // 2. 删除
        projectMemberMapper.deleteById(id);
    }

    @Override
    public MesProProjectMemberDO getProjectMember(Long id) {
        return projectMemberMapper.selectById(id);
    }

    @Override
    public List<MesProProjectMemberDO> getProjectMemberList(Long projectId) {
        if (projectId == null) {
            return Collections.emptyList();
        }
        return projectMemberMapper.selectListByProjectId(projectId);
    }

    // ==================== 校验方法 ====================

    private MesProProjectMemberDO validateProjectMemberExists(Long id) {
        MesProProjectMemberDO member = projectMemberMapper.selectById(id);
        if (member == null) {
            throw exception(PRO_PROJECT_MEMBER_NOT_EXISTS);
        }
        return member;
    }

    private void validateProjectMemberData(Long id, MesProProjectMemberSaveReqVO reqVO) {
        // 1. 校验所属项目存在
        if (projectMapper.selectById(reqVO.getProjectId()) == null) {
            throw exception(PRO_PROJECT_NOT_EXISTS);
        }
        // 2. 校验同一项目下成员姓名不重复
        MesProProjectMemberDO member =
                projectMemberMapper.selectByProjectIdAndName(reqVO.getProjectId(), reqVO.getUserName());
        if (member != null && ObjUtil.notEqual(member.getId(), id)) {
            throw exception(PRO_PROJECT_MEMBER_NAME_DUPLICATE);
        }
    }

}
