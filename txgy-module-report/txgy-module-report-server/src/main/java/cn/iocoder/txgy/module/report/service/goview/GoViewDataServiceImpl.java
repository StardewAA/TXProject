package cn.iocoder.txgy.module.report.service.goview;

import cn.iocoder.txgy.module.report.controller.admin.dataset.vo.ReportDatasetExecuteReqVO;
import cn.iocoder.txgy.module.report.controller.admin.dataset.vo.ReportDatasetExecuteRespVO;
import cn.iocoder.txgy.module.report.controller.admin.goview.vo.data.GoViewDataRespVO;
import cn.iocoder.txgy.module.report.service.dataset.ReportDatasetService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.util.Map;

/**
 * GoView 数据 Service 实现类
 *
 * 取数链路已收口到「报表数据集」：
 * 1. 默认走数据集编码（受租户注入、行数上限、超时、审计约束）；
 * 2. 原始 SQL 仅作为持有高危权限角色的逃生通道，同样受限并留痕。
 *
 * @author OPENLAB BS
 */
@Service
@Validated
public class GoViewDataServiceImpl implements GoViewDataService {

    @Resource
    private ReportDatasetService datasetService;

    @Override
    public GoViewDataRespVO getDataByDataset(String code, Map<String, Object> params) {
        ReportDatasetExecuteReqVO executeReqVO = new ReportDatasetExecuteReqVO();
        executeReqVO.setCode(code);
        executeReqVO.setParams(params);
        return convert(datasetService.executeDataset(executeReqVO));
    }

    @Override
    public GoViewDataRespVO getDataBySQL(String sql) {
        return convert(datasetService.executeRawSql(sql));
    }

    private GoViewDataRespVO convert(ReportDatasetExecuteRespVO result) {
        GoViewDataRespVO respVO = new GoViewDataRespVO();
        respVO.setDimensions(result.getDimensions());
        respVO.setSource(result.getSource());
        return respVO;
    }

}
