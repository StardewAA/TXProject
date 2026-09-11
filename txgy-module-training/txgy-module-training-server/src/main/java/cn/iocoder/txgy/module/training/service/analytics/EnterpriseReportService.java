package cn.iocoder.txgy.module.training.service.analytics;

import cn.iocoder.txgy.framework.common.pojo.PageResult;

import java.util.Map;

/**
 * 94 企业培训报表与档案 Service 接口（严格企业数据隔离）
 *
 * @author 云培训学院课题小组
 */
public interface EnterpriseReportService {

    /**
     * 分页查询本企业全员培训档案清单（含学徒技师人才等级分布统计）
     *
     * @param pageReqVO 分页参数（后续定义专用 PageReqVO）
     * @return 员工培训档案分页
     */
    PageResult<?> getEnterpriseArchivePage(Object pageReqVO);

    /**
     * 导出培训统计报表（按个人/班组多维筛选，Excel 格式）
     *
     * @param pageReqVO 筛选参数（后续定义专用 PageReqVO）
     * @return 说明信息（框架期返回"导出功能开发中"）
     */
    String exportReport(Object pageReqVO);

    /**
     * 项目培训归档报表（输入项目ID，输出参建人员学时/考试/证书汇总）
     *
     * @param projectId 项目ID
     * @return 项目归档报表数据
     */
    Map<String, Object> getProjectArchive(Long projectId);

}
