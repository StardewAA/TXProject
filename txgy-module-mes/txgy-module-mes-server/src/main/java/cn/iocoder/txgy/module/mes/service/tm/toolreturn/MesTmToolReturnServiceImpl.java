package cn.iocoder.txgy.module.mes.service.tm.toolreturn;

import cn.hutool.core.collection.CollUtil;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.module.mes.controller.admin.tm.toolreturn.vo.MesTmToolReturnLineSaveReqVO;
import cn.iocoder.txgy.module.mes.controller.admin.tm.toolreturn.vo.MesTmToolReturnPageReqVO;
import cn.iocoder.txgy.module.mes.controller.admin.tm.toolreturn.vo.MesTmToolReturnSaveReqVO;
import cn.iocoder.txgy.module.mes.dal.dataobject.tm.tool.MesTmToolDO;
import cn.iocoder.txgy.module.mes.dal.dataobject.tm.toolreturn.MesTmToolReturnDO;
import cn.iocoder.txgy.module.mes.dal.dataobject.tm.toolreturn.MesTmToolReturnLineDO;
import cn.iocoder.txgy.module.mes.dal.mysql.tm.toolreturn.MesTmToolReturnLineMapper;
import cn.iocoder.txgy.module.mes.dal.mysql.tm.toolreturn.MesTmToolReturnMapper;
import cn.iocoder.txgy.module.mes.enums.tm.MesTmToolReturnStatusEnum;
import cn.iocoder.txgy.module.mes.enums.tm.MesTmToolStatusEnum;
import cn.iocoder.txgy.module.mes.service.tm.tool.MesTmToolService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.List;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.mes.enums.ErrorCodeConstants.*;

/**
 * MES 工具归还单 Service 实现类
 */
@Service
@Validated
public class MesTmToolReturnServiceImpl implements MesTmToolReturnService {

    @Resource
    private MesTmToolReturnMapper returnMapper;
    @Resource
    private MesTmToolReturnLineMapper returnLineMapper;
    @Resource
    private MesTmToolService toolService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createToolReturn(MesTmToolReturnSaveReqVO createReqVO) {
        validateCodeUnique(null, createReqVO.getCode());
        MesTmToolReturnDO ret = BeanUtils.toBean(createReqVO, MesTmToolReturnDO.class);
        ret.setStatus(MesTmToolReturnStatusEnum.PREPARE.getStatus());
        returnMapper.insert(ret);
        return ret.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateToolReturn(MesTmToolReturnSaveReqVO updateReqVO) {
        validateToolReturnExistsAndPrepare(updateReqVO.getId());
        validateCodeUnique(updateReqVO.getId(), updateReqVO.getCode());
        MesTmToolReturnDO updateObj = BeanUtils.toBean(updateReqVO, MesTmToolReturnDO.class);
        returnMapper.updateById(updateObj);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteToolReturn(Long id) {
        validateToolReturnExistsAndPrepare(id);
        returnLineMapper.deleteByReturnId(id);
        returnMapper.deleteById(id);
    }

    @Override
    public MesTmToolReturnDO getToolReturn(Long id) {
        return returnMapper.selectById(id);
    }

    @Override
    public PageResult<MesTmToolReturnDO> getToolReturnPage(MesTmToolReturnPageReqVO pageReqVO) {
        return returnMapper.selectPage(pageReqVO);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirmToolReturn(Long id) {
        // 1. 校验存在且为草稿
        validateToolReturnExistsAndPrepare(id);
        // 2. 校验至少有一条行
        List<MesTmToolReturnLineDO> lines = returnLineMapper.selectListByReturnId(id);
        if (CollUtil.isEmpty(lines)) {
            throw exception(TM_TOOL_RETURN_NO_LINE);
        }
        // 3. 逐行增加工具可用数量 + 状态变在库
        for (MesTmToolReturnLineDO line : lines) {
            MesTmToolDO tool = toolService.validateToolExists(line.getToolId());
            // 归还数量不能超过（总数量 - 可用数量）= 领用中数量
            int issuedQuantity = tool.getQuantity() - tool.getAvailableQuantity();
            if (line.getQuantity() > issuedQuantity) {
                throw exception(TM_TOOL_RETURN_LINE_QUANTITY_EXCEED);
            }
            toolService.increaseAvailableQuantity(line.getToolId(), line.getQuantity());
            toolService.updateToolStatus(line.getToolId(), MesTmToolStatusEnum.STORE.getStatus());
        }
        // 4. 更新归还单状态为已确认
        returnMapper.updateById(new MesTmToolReturnDO().setId(id)
                .setStatus(MesTmToolReturnStatusEnum.CONFIRMED.getStatus()));
    }

    // ========== 行操作 ==========

    @Override
    public Long createToolReturnLine(MesTmToolReturnLineSaveReqVO createReqVO) {
        validateToolReturnExistsAndPrepare(createReqVO.getReturnId());
        MesTmToolDO tool = toolService.validateToolExists(createReqVO.getToolId());
        MesTmToolReturnLineDO line = BeanUtils.toBean(createReqVO, MesTmToolReturnLineDO.class);
        line.setToolCode(tool.getCode());
        line.setToolName(tool.getName());
        returnLineMapper.insert(line);
        return line.getId();
    }

    @Override
    public void updateToolReturnLine(MesTmToolReturnLineSaveReqVO updateReqVO) {
        validateToolReturnLineExists(updateReqVO.getId());
        MesTmToolDO tool = toolService.validateToolExists(updateReqVO.getToolId());
        MesTmToolReturnLineDO line = BeanUtils.toBean(updateReqVO, MesTmToolReturnLineDO.class);
        line.setToolCode(tool.getCode());
        line.setToolName(tool.getName());
        returnLineMapper.updateById(line);
    }

    @Override
    public void deleteToolReturnLine(Long id) {
        validateToolReturnLineExists(id);
        returnLineMapper.deleteById(id);
    }

    @Override
    public List<MesTmToolReturnLineDO> getToolReturnLineListByReturnId(Long returnId) {
        return returnLineMapper.selectListByReturnId(returnId);
    }

    // ========== 内部校验 ==========

    private MesTmToolReturnDO validateToolReturnExistsAndPrepare(Long id) {
        MesTmToolReturnDO ret = returnMapper.selectById(id);
        if (ret == null) {
            throw exception(TM_TOOL_RETURN_NOT_EXISTS);
        }
        if (!MesTmToolReturnStatusEnum.PREPARE.getStatus().equals(ret.getStatus())) {
            throw exception(TM_TOOL_RETURN_NOT_PREPARE);
        }
        return ret;
    }

    private void validateToolReturnLineExists(Long id) {
        if (returnLineMapper.selectById(id) == null) {
            throw exception(TM_TOOL_RETURN_LINE_NOT_EXISTS);
        }
    }

    private void validateCodeUnique(Long id, String code) {
        if (code == null) {
            return;
        }
        MesTmToolReturnDO existing = returnMapper.selectByCode(code);
        if (existing == null) {
            return;
        }
        if (!existing.getId().equals(id)) {
            throw exception(TM_TOOL_RETURN_CODE_DUPLICATE);
        }
    }
}
