package cn.iocoder.txgy.module.mes.service.tm.toolscrap;

import cn.hutool.core.collection.CollUtil;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.module.mes.controller.admin.tm.toolscrap.vo.MesTmToolScrapLineSaveReqVO;
import cn.iocoder.txgy.module.mes.controller.admin.tm.toolscrap.vo.MesTmToolScrapPageReqVO;
import cn.iocoder.txgy.module.mes.controller.admin.tm.toolscrap.vo.MesTmToolScrapSaveReqVO;
import cn.iocoder.txgy.module.mes.dal.dataobject.tm.tool.MesTmToolDO;
import cn.iocoder.txgy.module.mes.dal.dataobject.tm.toolscrap.MesTmToolScrapDO;
import cn.iocoder.txgy.module.mes.dal.dataobject.tm.toolscrap.MesTmToolScrapLineDO;
import cn.iocoder.txgy.module.mes.dal.mysql.tm.toolscrap.MesTmToolScrapLineMapper;
import cn.iocoder.txgy.module.mes.dal.mysql.tm.toolscrap.MesTmToolScrapMapper;
import cn.iocoder.txgy.module.mes.enums.tm.MesTmToolScrapStatusEnum;
import cn.iocoder.txgy.module.mes.service.tm.tool.MesTmToolService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.List;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.mes.enums.ErrorCodeConstants.*;

/**
 * MES 工具报废单 Service 实现类
 */
@Service
@Validated
public class MesTmToolScrapServiceImpl implements MesTmToolScrapService {

    @Resource
    private MesTmToolScrapMapper scrapMapper;
    @Resource
    private MesTmToolScrapLineMapper scrapLineMapper;
    @Resource
    private MesTmToolService toolService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createToolScrap(MesTmToolScrapSaveReqVO createReqVO) {
        validateCodeUnique(null, createReqVO.getCode());
        MesTmToolScrapDO scrap = BeanUtils.toBean(createReqVO, MesTmToolScrapDO.class);
        scrap.setStatus(MesTmToolScrapStatusEnum.PREPARE.getStatus());
        scrapMapper.insert(scrap);
        return scrap.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateToolScrap(MesTmToolScrapSaveReqVO updateReqVO) {
        validateToolScrapExistsAndPrepare(updateReqVO.getId());
        validateCodeUnique(updateReqVO.getId(), updateReqVO.getCode());
        MesTmToolScrapDO updateObj = BeanUtils.toBean(updateReqVO, MesTmToolScrapDO.class);
        scrapMapper.updateById(updateObj);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteToolScrap(Long id) {
        validateToolScrapExistsAndPrepare(id);
        scrapLineMapper.deleteByScrapId(id);
        scrapMapper.deleteById(id);
    }

    @Override
    public MesTmToolScrapDO getToolScrap(Long id) {
        return scrapMapper.selectById(id);
    }

    @Override
    public PageResult<MesTmToolScrapDO> getToolScrapPage(MesTmToolScrapPageReqVO pageReqVO) {
        return scrapMapper.selectPage(pageReqVO);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirmToolScrap(Long id) {
        // 1. 校验存在且为草稿
        validateToolScrapExistsAndPrepare(id);
        // 2. 校验至少有一条行
        List<MesTmToolScrapLineDO> lines = scrapLineMapper.selectListByScrapId(id);
        if (CollUtil.isEmpty(lines)) {
            throw exception(TM_TOOL_SCRAP_NO_LINE);
        }
        // 3. 逐行报废工具
        for (MesTmToolScrapLineDO line : lines) {
            toolService.scrapTool(line.getToolId(), line.getQuantity());
        }
        // 4. 更新报废单状态为已确认
        scrapMapper.updateById(new MesTmToolScrapDO().setId(id)
                .setStatus(MesTmToolScrapStatusEnum.CONFIRMED.getStatus()));
    }

    // ========== 行操作 ==========

    @Override
    public Long createToolScrapLine(MesTmToolScrapLineSaveReqVO createReqVO) {
        validateToolScrapExistsAndPrepare(createReqVO.getScrapId());
        MesTmToolDO tool = toolService.validateToolExists(createReqVO.getToolId());
        MesTmToolScrapLineDO line = BeanUtils.toBean(createReqVO, MesTmToolScrapLineDO.class);
        line.setToolCode(tool.getCode());
        line.setToolName(tool.getName());
        scrapLineMapper.insert(line);
        return line.getId();
    }

    @Override
    public void updateToolScrapLine(MesTmToolScrapLineSaveReqVO updateReqVO) {
        validateToolScrapLineExists(updateReqVO.getId());
        MesTmToolDO tool = toolService.validateToolExists(updateReqVO.getToolId());
        MesTmToolScrapLineDO line = BeanUtils.toBean(updateReqVO, MesTmToolScrapLineDO.class);
        line.setToolCode(tool.getCode());
        line.setToolName(tool.getName());
        scrapLineMapper.updateById(line);
    }

    @Override
    public void deleteToolScrapLine(Long id) {
        validateToolScrapLineExists(id);
        scrapLineMapper.deleteById(id);
    }

    @Override
    public List<MesTmToolScrapLineDO> getToolScrapLineListByScrapId(Long scrapId) {
        return scrapLineMapper.selectListByScrapId(scrapId);
    }

    // ========== 内部校验 ==========

    private MesTmToolScrapDO validateToolScrapExistsAndPrepare(Long id) {
        MesTmToolScrapDO scrap = scrapMapper.selectById(id);
        if (scrap == null) {
            throw exception(TM_TOOL_SCRAP_NOT_EXISTS);
        }
        if (!MesTmToolScrapStatusEnum.PREPARE.getStatus().equals(scrap.getStatus())) {
            throw exception(TM_TOOL_SCRAP_NOT_PREPARE);
        }
        return scrap;
    }

    private void validateToolScrapLineExists(Long id) {
        if (scrapLineMapper.selectById(id) == null) {
            throw exception(TM_TOOL_SCRAP_LINE_NOT_EXISTS);
        }
    }

    private void validateCodeUnique(Long id, String code) {
        if (code == null) {
            return;
        }
        MesTmToolScrapDO existing = scrapMapper.selectByCode(code);
        if (existing == null) {
            return;
        }
        if (!existing.getId().equals(id)) {
            throw exception(TM_TOOL_SCRAP_CODE_DUPLICATE);
        }
    }
}
