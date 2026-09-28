package cn.iocoder.txgy.module.bidding.service.report;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.bidding.controller.admin.report.vo.ReportPageReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.report.vo.ReportSaveReqVO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.report.ReportDO;

/**
 * 定标书面报告服务（招标投标法第47条：确定中标人之日起 15 日内提交书面报告）。
 */
public interface ReportService {

    /** 提交书面报告（记录提交时间/定标时间锚点）。 */
    Long submit(ReportSaveReqVO reqVO);

    /** 按主键查询。 */
    ReportDO get(Long id);

    /** 分页查询。 */
    PageResult<ReportDO> page(ReportPageReqVO reqVO);
}
