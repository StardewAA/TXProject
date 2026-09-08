package cn.iocoder.txgy.module.mes.service.pro.project;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.module.mes.controller.admin.pro.project.vo.MesProProjectFileSaveReqVO;
import cn.iocoder.txgy.module.mes.dal.dataobject.pro.project.MesProProjectFileDO;
import cn.iocoder.txgy.module.mes.dal.mysql.pro.project.MesProProjectFileMapper;
import cn.iocoder.txgy.module.mes.dal.mysql.pro.project.MesProProjectMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.util.Collections;
import java.util.List;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.mes.enums.ErrorCodeConstants.*;

/**
 * MES 项目附件 Service 实现类
 *
 * @author OPENLAB BS
 */
@Service
@Validated
public class MesProProjectFileServiceImpl implements MesProProjectFileService {

    @Resource
    private MesProProjectFileMapper projectFileMapper;
    @Resource
    private MesProProjectMapper projectMapper;

    @Override
    public Long createProjectFile(MesProProjectFileSaveReqVO createReqVO) {
        // 1. 校验所属项目存在
        if (projectMapper.selectById(createReqVO.getProjectId()) == null) {
            throw exception(PRO_PROJECT_NOT_EXISTS);
        }
        // 2. 校验文件名称不为空
        if (StrUtil.isBlank(createReqVO.getFileName())) {
            throw exception(PRO_PROJECT_FILE_NAME_EMPTY);
        }
        // 3. 保存关联
        MesProProjectFileDO file = BeanUtils.toBean(createReqVO, MesProProjectFileDO.class);
        projectFileMapper.insert(file);
        return file.getId();
    }

    @Override
    public void deleteProjectFile(Long id) {
        // 1. 校验存在
        MesProProjectFileDO file = projectFileMapper.selectById(id);
        if (file == null) {
            throw exception(PRO_PROJECT_FILE_NOT_EXISTS);
        }
        // 2. 删除关联（文件实体保留在 infra，避免其他引用失效）
        projectFileMapper.deleteById(id);
    }

    @Override
    public List<MesProProjectFileDO> getProjectFileList(Long projectId) {
        if (projectId == null) {
            return Collections.emptyList();
        }
        return projectFileMapper.selectListByProjectId(projectId);
    }

}
