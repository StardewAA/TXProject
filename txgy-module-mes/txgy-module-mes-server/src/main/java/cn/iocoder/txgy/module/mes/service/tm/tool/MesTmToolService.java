package cn.iocoder.txgy.module.mes.service.tm.tool;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.mes.controller.admin.tm.tool.vo.MesTmToolPageReqVO;
import cn.iocoder.txgy.module.mes.controller.admin.tm.tool.vo.MesTmToolSaveReqVO;
import cn.iocoder.txgy.module.mes.dal.dataobject.tm.tool.MesTmToolDO;
import jakarta.validation.Valid;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import static cn.iocoder.txgy.framework.common.util.collection.CollectionUtils.convertMap;

/**
 * MES 工具台账 Service 接口
 *
 * @author OPENLAB BS
 */
public interface MesTmToolService {

    /**
     * 创建工具
     *
     * @param createReqVO 创建信息
     * @return 编号
     */
    Long createTool(@Valid MesTmToolSaveReqVO createReqVO);

    /**
     * 更新工具
     *
     * @param updateReqVO 更新信息
     */
    void updateTool(@Valid MesTmToolSaveReqVO updateReqVO);

    /**
     * 删除工具
     *
     * @param id 编号
     */
    void deleteTool(Long id);

    /**
     * 获得工具
     *
     * @param id 编号
     * @return 工具
     */
    MesTmToolDO getTool(Long id);

    /**
     * 获得工具分页
     *
     * @param pageReqVO 分页查询
     * @return 工具分页
     */
    PageResult<MesTmToolDO> getToolPage(MesTmToolPageReqVO pageReqVO);

    /**
     * 获得工具列表
     *
     * @return 工具列表
     */
    List<MesTmToolDO> getToolList();

    /**
     * 获得指定工具类型下的工具数量
     *
     * @param toolTypeId 工具类型编号
     * @return 工具数量
     */
    Long getToolCountByToolTypeId(Long toolTypeId);

    /**
     * 获得工具列表
     *
     * @param ids 编号列表
     * @return 工具列表
     */
    List<MesTmToolDO> getToolList(Collection<Long> ids);

    /**
     * 获得工具 Map
     *
     * @param ids 编号列表
     * @return 工具 Map
     */
    default Map<Long, MesTmToolDO> getToolMap(Collection<Long> ids) {
        return convertMap(getToolList(ids), MesTmToolDO::getId);
    }

    /**
     * 校验工具存在
     *
     * @param id 编号
     * @return 工具
     */
    MesTmToolDO validateToolExists(Long id);

    /**
     * 减少工具可用数量（领用）
     *
     * @param toolId   工具ID
     * @param quantity 数量
     */
    void decreaseAvailableQuantity(Long toolId, Integer quantity);

    /**
     * 增加工具可用数量（归还）
     *
     * @param toolId   工具ID
     * @param quantity 数量
     */
    void increaseAvailableQuantity(Long toolId, Integer quantity);

    /**
     * 更新工具状态
     *
     * @param toolId 工具ID
     * @param status 目标状态
     */
    void updateToolStatus(Long toolId, Integer status);

    /**
     * 报废工具：减少数量和可用数量
     *
     * @param toolId   工具ID
     * @param quantity 报废数量
     */
    void scrapTool(Long toolId, Integer quantity);

}
