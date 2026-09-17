package cn.iocoder.txgy.module.mes.service.tm.toolrepair;

import cn.hutool.core.collection.CollUtil;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.module.mes.controller.admin.tm.toolrepair.vo.MesTmToolRepairLineSaveReqVO;
import cn.iocoder.txgy.module.mes.controller.admin.tm.toolrepair.vo.MesTmToolRepairPageReqVO;
import cn.iocoder.txgy.module.mes.controller.admin.tm.toolrepair.vo.MesTmToolRepairSaveReqVO;
import cn.iocoder.txgy.module.mes.dal.dataobject.tm.tool.MesTmToolDO;
import cn.iocoder.txgy.module.mes.dal.dataobject.tm.toolrepair.MesTmToolRepairDO;
import cn.iocoder.txgy.module.mes.dal.dataobject.tm.toolrepair.MesTmToolRepairLineDO;
import cn.iocoder.txgy.module.mes.dal.mysql.tm.toolrepair.MesTmToolRepairLineMapper;
import cn.iocoder.txgy.module.mes.dal.mysql.tm.toolrepair.MesTmToolRepairMapper;
import cn.iocoder.txgy.module.mes.enums.tm.MesTmToolRepairStatusEnum;
import cn.iocoder.txgy.module.mes.enums.tm.MesTmToolStatusEnum;
import cn.iocoder.txgy.module.mes.service.tm.tool.MesTmToolService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDateTime;
import java.util.List;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.mes.enums.ErrorCodeConstants.*;

/**
 * MES 工具维修单 Service 实现类
 */
@Service
@Validated
public class MesTmToolRepairServiceImpl implements MesTmToolRepairService {

    @Resource
    private MesTmToolRepairMapper repairMapper;
    @Resource
    private MesTmToolRepairLineMapper repairLineMapper;
    @Resource
    private MesTmToolService toolService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createToolRepair(MesTmToolRepairSaveReqVO createReqVO) {
        validateCodeUnique(null, createReqVO.getCode());
        MesTmToolRepairDO repair = BeanUtils.toBean(createReqVO, MesTmToolRepairDO.class);
        repair.setStatus(MesTmToolRepairStatusEnum.PREPARE.getStatus());
        repairMapper.insert(repair);
        return repair.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateToolRepair(MesTmToolRepairSaveReqVO updateReqVO) {
        validateToolRepairExistsAndPrepare(updateReqVO.getId());
        validateCodeUnique(updateReqVO.getId(), updateReqVO.getCode());
        MesTmToolRepairDO updateObj = BeanUtils.toBean(updateReqVO, MesTmToolRepairDO.class);
        repairMapper.updateById(updateObj);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteToolRepair(Long id) {
        validateToolRepairExistsAndPrepare(id);
        repairLineMapper.deleteByRepairId(id);
        repairMapper.deleteById(id);
    }

    @Override
    public MesTmToolRepairDO getToolRepair(Long id) {
        return repairMapper.selectById(id);
    }

    @Override
    public PageResult<MesTmToolRepairDO> getToolRepairPage(MesTmToolRepairPageReqVO pageReqVO) {
        return repairMapper.selectPage(pageReqVO);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirmToolRepair(Long id) {
        // 1. 校验存在且为草稿
        validateToolRepairExistsAndPrepare(id);
        // 2. 校验至少有一条行
        List<MesTmToolRepairLineDO> lines = repairLineMapper.selectListByRepairId(id);
        if (CollUtil.isEmpty(lines)) {
            throw exception(TM_TOOL_REPAIR_NO_LINE);
        }
        // 3. 逐行变更工具状态为维修中
        for (MesTmToolRepairLineDO line : lines) {
            toolService.validateToolExists(line.getToolId());
            toolService.updateToolStatus(line.getToolId(), MesTmToolStatusEnum.REPAIR.getStatus());
        }
        // 4. 更新维修单状态为已确认
        repairMapper.updateById(new MesTmToolRepairDO().setId(id)
                .setStatus(MesTmToolRepairStatusEnum.CONFIRMED.getStatus()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void finishToolRepair(Long id) {
        // 1. 校验存在且为已确认（维修中）
        MesTmToolRepairDO repair = repairMapper.selectById(id);
        if (repair == null) {
            throw exception(TM_TOOL_REPAIR_NOT_EXISTS);
        }
        if (!MesTmToolRepairStatusEnum.CONFIRMED.getStatus().equals(repair.getStatus())) {
            throw exception(TM_TOOL_REPAIR_NOT_PREPARE);
        }
        // 2. 逐行变更工具状态为在库
        List<MesTmToolRepairLineDO> lines = repairLineMapper.selectListByRepairId(id);
        for (MesTmToolRepairLineDO line : lines) {
            toolService.updateToolStatus(line.getToolId(), MesTmToolStatusEnum.STORE.getStatus());
        }
        // 3. 更新维修单状态为维修完成 + 完成日期
        repairMapper.updateById(new MesTmToolRepairDO().setId(id)
                .setStatus(MesTmToolRepairStatusEnum.COMPLETED.getStatus())
                .setFinishDate(LocalDateTime.now()));
    }

    // ========== 行操作 ==========

    @Override
    public Long createToolRepairLine(MesTmToolRepairLineSaveReqVO createReqVO) {
        validateToolRepairExistsAndPrepare(createReqVO.getRepairId());
        MesTmToolDO tool = toolService.validateToolExists(createReqVO.getToolId());
        MesTmToolRepairLineDO line = BeanUtils.toBean(createReqVO, MesTmToolRepairLineDO.class);
        line.setToolCode(tool.getCode());
        line.setToolName(tool.getName());
        repairLineMapper.insert(line);
        return line.getId();
    }

    @Override
    public void updateToolRepairLine(MesTmToolRepairLineSaveReqVO updateReqVO) {
        validateToolRepairLineExists(updateReqVO.getId());
        MesTmToolDO tool = toolService.validateToolExists(updateReqVO.getToolId());
        MesTmToolRepairLineDO line = BeanUtils.toBean(updateReqVO, MesTmToolRepairLineDO.class);
        line.setToolCode(tool.getCode());
        line.setToolName(tool.getName());
        repairLineMapper.updateById(line);
    }

    @Override
    public void deleteToolRepairLine(Long id) {
        validateToolRepairLineExists(id);
        repairLineMapper.deleteById(id);
    }

    @Override
    public List<MesTmToolRepairLineDO> getToolRepairLineListByRepairId(Long repairId) {
        return repairLineMapper.selectListByRepairId(repairId);
    }

    // ========== 内部校验 ==========

    private MesTmToolRepairDO validateToolRepairExistsAndPrepare(Long id) {
        MesTmToolRepairDO repair = repairMapper.selectById(id);
        if (repair == null) {
            throw exception(TM_TOOL_REPAIR_NOT_EXISTS);
        }
        if (!MesTmToolRepairStatusEnum.PREPARE.getStatus().equals(repair.getStatus())) {
            throw exception(TM_TOOL_REPAIR_NOT_PREPARE);
        }
        return repair;
    }

    private void validateToolRepairLineExists(Long id) {
        if (repairLineMapper.selectById(id) == null) {
            throw exception(TM_TOOL_REPAIR_LINE_NOT_EXISTS);
        }
    }

    private void validateCodeUnique(Long id, String code) {
        if (code == null) {
            return;
        }
        MesTmToolRepairDO existing = repairMapper.selectByCode(code);
        if (existing == null) {
            return;
        }
        if (!existing.getId().equals(id)) {
            throw exception(TM_TOOL_REPAIR_CODE_DUPLICATE);
        }
    }
}
