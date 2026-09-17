package cn.iocoder.txgy.module.report.service.goview;

import cn.iocoder.txgy.module.report.controller.admin.goview.vo.data.GoViewDataRespVO;

import java.util.Map;

/**
 * GoView 数据 Service 接口
 *
 * @author OPENLAB BS
 */
public interface GoViewDataService {

    /**
     * 使用数据集编码查询数据（推荐方式）
     *
     * @param code   数据集编码
     * @param params 查询参数
     * @return 数据
     */
    GoViewDataRespVO getDataByDataset(String code, Map<String, Object> params);

    /**
     * 使用 SQL 查询数据（受限逃生通道）
     *
     * 需要 report:dataset:raw-sql 权限；同样受只读校验、行数上限、超时与审计约束。
     *
     * @param sql SQL 语句
     * @return 数据
     */
    GoViewDataRespVO getDataBySQL(String sql);

}
