package cn.iocoder.txgy.module.mes.service.tm.toolissue;

import cn.hutool.core.collection.CollUtil;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.module.mes.controller.admin.tm.toolissue.vo.MesTmToolIssueLineSaveReqVO;
import cn.iocoder.txgy.module.mes.controller.admin.tm.toolissue.vo.MesTmToolIssuePageReqVO;
import cn.iocoder.txgy.module.mes.controller.admin.tm.toolissue.vo.MesTmToolIssueSaveReqVO;
import cn.iocoder.txgy.module.mes.dal.dataobject.tm.tool.MesTmToolDO;
import cn.iocoder.txgy.module.mes.dal.dataobject.tm.toolissue.MesTmToolIssueDO;
import cn.iocoder.txgy.module.mes.dal.dataobject.tm.toolissue.MesTmToolIssueLineDO;
import cn.iocoder.txgy.module.mes.dal.mysql.tm.toolissue.MesTmToolIssueLineMapper;
import cn.iocoder.txgy.module.mes.dal.mysql.tm.toolissue.MesTmToolIssueMapper;
import cn.iocoder.txgy.module.mes.enums.tm.MesTmToolIssueStatusEnum;
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
 * MES 工具领用单 Service 实现类
 */
@Service
@Validated
public class MesTmToolIssueServiceImpl implements MesTmToolIssueService {

    @Resource
    private MesTmToolIssueMapper issueMapper;
    @Resource
    private MesTmToolIssueLineMapper issueLineMapper;
    @Resource
    private MesTmToolService toolService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createToolIssue(MesTmToolIssueSaveReqVO createReqVO) {
        validateCodeUnique(null, createReqVO.getCode());
        MesTmToolIssueDO issue = BeanUtils.toBean(createReqVO, MesTmToolIssueDO.class);
        issue.setStatus(MesTmToolIssueStatusEnum.PREPARE.getStatus());
        issueMapper.insert(issue);
        return issue.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateToolIssue(MesTmToolIssueSaveReqVO updateReqVO) {
        validateToolIssueExistsAndPrepare(updateReqVO.getId());
        validateCodeUnique(updateReqVO.getId(), updateReqVO.getCode());
        MesTmToolIssueDO updateObj = BeanUtils.toBean(updateReqVO, MesTmToolIssueDO.class);
        issueMapper.updateById(updateObj);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteToolIssue(Long id) {
        validateToolIssueExistsAndPrepare(id);
        issueLineMapper.deleteByIssueId(id);
        issueMapper.deleteById(id);
    }

    @Override
    public MesTmToolIssueDO getToolIssue(Long id) {
        return issueMapper.selectById(id);
    }

    @Override
    public PageResult<MesTmToolIssueDO> getToolIssuePage(MesTmToolIssuePageReqVO pageReqVO) {
        return issueMapper.selectPage(pageReqVO);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirmToolIssue(Long id) {
        // 1. 校验存在且为草稿
        validateToolIssueExistsAndPrepare(id);
        // 2. 校验至少有一条行
        List<MesTmToolIssueLineDO> lines = issueLineMapper.selectListByIssueId(id);
        if (CollUtil.isEmpty(lines)) {
            throw exception(TM_TOOL_ISSUE_NO_LINE);
        }
        // 3. 逐行扣减工具可用数量 + 变更状态
        for (MesTmToolIssueLineDO line : lines) {
            MesTmToolDO tool = toolService.validateToolExists(line.getToolId());
            if (tool.getAvailableQuantity() < line.getQuantity()) {
                throw exception(TM_TOOL_ISSUE_LINE_QUANTITY_EXCEED);
            }
            toolService.decreaseAvailableQuantity(line.getToolId(), line.getQuantity());
            toolService.updateToolStatus(line.getToolId(), MesTmToolStatusEnum.ISSUE.getStatus());
        }
        // 4. 更新领用单状态为已确认
        issueMapper.updateById(new MesTmToolIssueDO().setId(id)
                .setStatus(MesTmToolIssueStatusEnum.CONFIRMED.getStatus()));
    }

    // ========== 行操作 ==========

    @Override
    public Long createToolIssueLine(MesTmToolIssueLineSaveReqVO createReqVO) {
        validateToolIssueExistsAndPrepare(createReqVO.getIssueId());
        MesTmToolDO tool = toolService.validateToolExists(createReqVO.getToolId());
        MesTmToolIssueLineDO line = BeanUtils.toBean(createReqVO, MesTmToolIssueLineDO.class);
        line.setToolCode(tool.getCode());
        line.setToolName(tool.getName());
        issueLineMapper.insert(line);
        return line.getId();
    }

    @Override
    public void updateToolIssueLine(MesTmToolIssueLineSaveReqVO updateReqVO) {
        validateToolIssueLineExists(updateReqVO.getId());
        MesTmToolDO tool = toolService.validateToolExists(updateReqVO.getToolId());
        MesTmToolIssueLineDO line = BeanUtils.toBean(updateReqVO, MesTmToolIssueLineDO.class);
        line.setToolCode(tool.getCode());
        line.setToolName(tool.getName());
        issueLineMapper.updateById(line);
    }

    @Override
    public void deleteToolIssueLine(Long id) {
        validateToolIssueLineExists(id);
        issueLineMapper.deleteById(id);
    }

    @Override
    public List<MesTmToolIssueLineDO> getToolIssueLineListByIssueId(Long issueId) {
        return issueLineMapper.selectListByIssueId(issueId);
    }

    // ========== 内部校验 ==========

    private MesTmToolIssueDO validateToolIssueExistsAndPrepare(Long id) {
        MesTmToolIssueDO issue = issueMapper.selectById(id);
        if (issue == null) {
            throw exception(TM_TOOL_ISSUE_NOT_EXISTS);
        }
        if (!MesTmToolIssueStatusEnum.PREPARE.getStatus().equals(issue.getStatus())) {
            throw exception(TM_TOOL_ISSUE_NOT_PREPARE);
        }
        return issue;
    }

    private void validateToolIssueLineExists(Long id) {
        if (issueLineMapper.selectById(id) == null) {
            throw exception(TM_TOOL_ISSUE_LINE_NOT_EXISTS);
        }
    }

    private void validateCodeUnique(Long id, String code) {
        if (code == null) {
            return;
        }
        MesTmToolIssueDO existing = issueMapper.selectByCode(code);
        if (existing == null) {
            return;
        }
        if (!existing.getId().equals(id)) {
            throw exception(TM_TOOL_ISSUE_CODE_DUPLICATE);
        }
    }
}
