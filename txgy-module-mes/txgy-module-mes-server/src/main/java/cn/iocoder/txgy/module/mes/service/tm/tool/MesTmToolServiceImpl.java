package cn.iocoder.txgy.module.mes.service.tm.tool;

import cn.hutool.core.util.ObjUtil;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.module.mes.controller.admin.tm.tool.vo.MesTmToolPageReqVO;
import cn.iocoder.txgy.module.mes.controller.admin.tm.tool.vo.MesTmToolSaveReqVO;
import cn.iocoder.txgy.module.mes.dal.dataobject.tm.tool.MesTmToolDO;
import cn.iocoder.txgy.module.mes.dal.mysql.tm.tool.MesTmToolMapper;
import cn.iocoder.txgy.module.mes.enums.tm.MesTmMaintenTypeEnum;
import cn.iocoder.txgy.module.mes.enums.tm.MesTmToolStatusEnum;
import cn.iocoder.txgy.module.mes.enums.wm.BarcodeBizTypeEnum;
import cn.iocoder.txgy.module.mes.service.wm.barcode.MesWmBarcodeService;
import cn.iocoder.txgy.module.mes.service.wm.batch.MesWmBatchService;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.mes.enums.ErrorCodeConstants.*;

/**
 * MES 工具台账 Service 实现类
 *
 * @author OPENLAB BS
 */
@Service
@Validated
public class MesTmToolServiceImpl implements MesTmToolService {

    @Resource
    private MesTmToolMapper toolMapper;

    @Resource
    @Lazy // 延迟加载，避免循环依赖
    private MesTmToolTypeService toolTypeService;
    @Resource
    private MesWmBarcodeService barcodeService;
    @Resource
    @Lazy
    private MesWmBatchService batchService;

    @Override
    public Long createTool(MesTmToolSaveReqVO createReqVO) {
        // 校验数据
        validateToolSaveData(null, createReqVO);

        // 插入
        MesTmToolDO tool = BeanUtils.toBean(createReqVO, MesTmToolDO.class);
        toolMapper.insert(tool);

        // 自动生成条码
        barcodeService.autoGenerateBarcode(BarcodeBizTypeEnum.TOOL.getValue(),
                tool.getId(), tool.getCode(), tool.getName());
        return tool.getId();
    }

    @Override
    public void updateTool(MesTmToolSaveReqVO updateReqVO) {
        // 1.1 校验存在
        MesTmToolDO dbTool = validateToolExists(updateReqVO.getId());
        // 1.2 校验数据
        validateToolSaveData(updateReqVO.getId(), updateReqVO);

        // 2.1 保养类型互斥清理
        if (Objects.equals(updateReqVO.getMaintenType(), MesTmMaintenTypeEnum.REGULAR.getType())) {
            updateReqVO.setNextMaintenPeriod(null);
        } else if (Objects.equals(updateReqVO.getMaintenType(), MesTmMaintenTypeEnum.USAGE.getType())) {
            updateReqVO.setNextMaintenDate(null);
        }
        // 2.2 更新
        MesTmToolDO updateObj = BeanUtils.toBean(updateReqVO, MesTmToolDO.class);
        // 2.3 状态为由单据驱动的派生字段，禁止台账编辑直接覆盖
        updateObj.setStatus(dbTool.getStatus());
        // 2.4 可用数量同步库存数量的调整差额，避免把页面旧值写回导致单据变动丢失
        updateObj.setAvailableQuantity(calcAvailableQuantity(dbTool, updateReqVO.getQuantity()));
        toolMapper.updateById(updateObj);
    }

    /**
     * 计算调整后的可用数量：新增传入不做干预，编辑时按「库存数量差额」同步增减
     *
     * @param dbTool     数据库中的工具记录
     * @param newQuantity 新的库存数量
     * @return 调整后的可用数量
     */
    private Integer calcAvailableQuantity(MesTmToolDO dbTool, Integer newQuantity) {
        Integer oldQuantity = dbTool.getQuantity();
        Integer availableQuantity = dbTool.getAvailableQuantity() == null ? 0 : dbTool.getAvailableQuantity();
        if (newQuantity == null || oldQuantity == null) {
            return availableQuantity;
        }
        int delta = newQuantity - oldQuantity;
        return Math.max(0, availableQuantity + delta);
    }

    private void validateToolSaveData(Long id, MesTmToolSaveReqVO reqVO) {
        // 校验工具类型存在
        toolTypeService.validateToolTypeExists(reqVO.getToolTypeId());
        // 校验编码唯一
        validateToolCodeUnique(id, reqVO.getCode());
    }

    @Override
    public void deleteTool(Long id) {
        // 校验存在
        validateToolExists(id);
        // 校验是否被批次引用
        if (batchService.getBatchCountByToolId(id) > 0) {
            throw exception(TM_TOOL_HAS_BATCH);
        }

        // 删除
        toolMapper.deleteById(id);
    }

    private void validateToolCodeUnique(Long id, String code) {
        if (code == null) {
            return;
        }
        MesTmToolDO tool = toolMapper.selectByCode(code);
        if (tool == null) {
            return;
        }
        if (ObjUtil.notEqual(tool.getId(), id)) {
            throw exception(TM_TOOL_CODE_DUPLICATE);
        }
    }

    @Override
    public MesTmToolDO getTool(Long id) {
        return toolMapper.selectById(id);
    }

    @Override
    public PageResult<MesTmToolDO> getToolPage(MesTmToolPageReqVO pageReqVO) {
        return toolMapper.selectPage(pageReqVO);
    }

    @Override
    public Long getToolCountByToolTypeId(Long toolTypeId) {
        return toolMapper.selectCountByToolTypeId(toolTypeId);
    }

    @Override
    public List<MesTmToolDO> getToolList() {
        return toolMapper.selectList();
    }

    @Override
    public List<MesTmToolDO> getToolList(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        return toolMapper.selectByIds(ids);
    }

    @Override
    public MesTmToolDO validateToolExists(Long id) {
        MesTmToolDO tool = toolMapper.selectById(id);
        if (tool == null) {
            throw exception(TM_TOOL_NOT_EXISTS);
        }
        return tool;
    }

    @Override
    public void decreaseAvailableQuantity(Long toolId, Integer quantity) {
        MesTmToolDO tool = validateToolExists(toolId);
        if (tool.getAvailableQuantity() < quantity) {
            throw exception(TM_TOOL_AVAILABLE_QUANTITY_INSUFFICIENT);
        }
        toolMapper.updateById(new MesTmToolDO().setId(toolId)
                .setAvailableQuantity(tool.getAvailableQuantity() - quantity));
    }

    @Override
    public void increaseAvailableQuantity(Long toolId, Integer quantity) {
        MesTmToolDO tool = validateToolExists(toolId);
        toolMapper.updateById(new MesTmToolDO().setId(toolId)
                .setAvailableQuantity(tool.getAvailableQuantity() + quantity));
    }

    @Override
    public void updateToolStatus(Long toolId, Integer status) {
        validateToolExists(toolId);
        toolMapper.updateById(new MesTmToolDO().setId(toolId).setStatus(status));
    }

    @Override
    public void scrapTool(Long toolId, Integer quantity) {
        MesTmToolDO tool = validateToolExists(toolId);
        if (tool.getAvailableQuantity() < quantity) {
            throw exception(TM_TOOL_AVAILABLE_QUANTITY_INSUFFICIENT);
        }
        toolMapper.updateById(new MesTmToolDO().setId(toolId)
                .setQuantity(tool.getQuantity() - quantity)
                .setAvailableQuantity(tool.getAvailableQuantity() - quantity)
                .setStatus(tool.getQuantity() - quantity <= 0
                        ? MesTmToolStatusEnum.SCRAP.getStatus() : tool.getStatus()));
    }

}
