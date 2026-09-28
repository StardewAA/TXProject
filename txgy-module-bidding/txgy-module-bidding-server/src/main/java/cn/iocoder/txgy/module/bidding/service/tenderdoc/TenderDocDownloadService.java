package cn.iocoder.txgy.module.bidding.service.tenderdoc;

import cn.iocoder.txgy.module.bidding.controller.admin.tenderdoc.vo.TenderDocDownloadRecordReqVO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.tenderdoc.TenderDocDownloadDO;

import java.util.List;

/**
 * 招标文件下载记录服务（下载名单）。
 *
 * <p>设计依据：《电子招标投标全流程说明书》§5；《实施条例》第21条
 * （澄清/修改应书面通知所有获取招标文件的潜在投标人——下载名单是通知的事实基础）。
 * 文件本体经 FileApi 上传换取 fileId，本服务只记录「谁在何时下载了哪个标段的招标文件」。
 */
public interface TenderDocDownloadService {

    /**
     * 记录下载（投标方下载招标文件时调用；幂等：同一文件同一用户只记一条）。
     *
     * @param reqVO 下载记录入参（含招标项目/标段/文件ID）
     * @return 下载记录
     */
    TenderDocDownloadDO record(TenderDocDownloadRecordReqVO reqVO);

    /**
     * 下载名单（招标方查询；按招标项目/标段聚合，供澄清/异议定向通知）。
     *
     * @param tenderProjectCode 招标项目编号
     * @param bidSectionCode    标段编号（空=项目级全部）
     * @return 下载记录列表（按下载时间倒序）
     */
    List<TenderDocDownloadDO> listDownloaders(String tenderProjectCode, String bidSectionCode);

    /**
     * 下载人数统计（按标段）。
     *
     * @param bidSectionCode 标段编号
     * @return 下载人数
     */
    long countDownloaders(String bidSectionCode);
}
