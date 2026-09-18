package cn.iocoder.txgy.module.report.service.dataset;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.report.dal.dataobject.dataset.ReportDatasetDO;
import cn.iocoder.txgy.module.report.controller.admin.dataset.vo.*;

import java.util.List;
import java.util.Map;

/**
 * 报表数据集 Service 接口
 *
 * @author OPENLAB BS
 */
public interface ReportDatasetService {

    /**
     * 创建数据集
     *
     * @param reqVO 数据集信息
     * @return 数据集编号
     */
    Long createDataset(ReportDatasetSaveReqVO reqVO);

    /**
     * 更新数据集
     *
     * @param reqVO 数据集信息
     */
    void updateDataset(ReportDatasetSaveReqVO reqVO);

    /**
     * 删除数据集
     *
     * @param id 数据集编号
     */
    void deleteDataset(Long id);

    /**
     * 获取数据集详情
     *
     * @param id 数据集编号
     * @return 数据集
     */
    ReportDatasetRespVO getDataset(Long id);

    /**
     * 分页查询当前租户的数据集
     *
     * @param reqVO 分页条件
     * @return 分页结果
     */
    PageResult<ReportDatasetRespVO> getDatasetPage(ReportDatasetPageReqVO reqVO);

    /**
     * 获取可用的数据集列表（当前租户 + 平台级）
     *
     * @return 数据集列表
     */
    List<ReportDatasetRespVO> getEnabledDatasetList();

    /**
     * 执行数据集查询
     *
     * 服务端强制：租户条件注入、行数上限、查询超时、审计留痕。
     *
     * @param reqVO 数据集编码与参数
     * @return 查询结果
     */
    ReportDatasetExecuteRespVO executeDataset(ReportDatasetExecuteReqVO reqVO);

    /**
     * 简单模式创建数据集：选表 → 勾字段 → 服务端自动生成 SQL
     *
     * @param reqVO 简单模式创建参数
     * @return 数据集编号
     */
    Long createSimpleDataset(SimpleDatasetReqVO reqVO);

    /**
     * 按编码获得已启用的数据集（当前租户 → 平台级）
     *
     * @param code 数据集编码
     * @return 数据集
     */
    ReportDatasetDO getEnabledDataset(String code);

    /**
     * 执行数据集查询，直接返回行数据（供积木报表「API 数据集」使用）
     *
     * 积木要求响应形如 {@code {"data":[{...}]}}，即数据数组必须位于 data 字段下，
     * 因此这里只返回行数组，由平台的统一响应包装放进 data。
     *
     * @param code   数据集编码
     * @param params 查询参数
     * @return 行数据
     */
    List<Map<String, Object>> queryRows(String code, Map<String, Object> params);

    /**
     * 执行原始 SQL 查询（大屏逃生通道）
     *
     * 仅供持有 report:dataset:raw-sql 权限的角色使用；同样受只读校验、行数上限、超时与审计约束。
     *
     * @param sql SQL 语句
     * @return 查询结果
     */
    ReportDatasetExecuteRespVO executeRawSql(String sql);

}
