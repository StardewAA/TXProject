package cn.iocoder.txgy.module.bidding.service.tenderdoc;

import cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil;
import cn.iocoder.txgy.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.txgy.module.bidding.controller.admin.tenderdoc.vo.TenderDocDownloadRecordReqVO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.tenderdoc.TenderDocDownloadDO;
import cn.iocoder.txgy.module.bidding.dal.mysql.tenderdoc.TenderDocDownloadMapper;
import cn.iocoder.txgy.module.bidding.enums.ErrorCodeConstants;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 招标文件下载记录实现（下载名单）。
 *
 * <p>业务目标：记录下载事实作为澄清/修改定向通知（条例第21条）与异议期限计算的基础；
 * 幂等：同一文件同一用户重复下载不重复建档。
 */
@Service
@Slf4j
public class TenderDocDownloadServiceImpl implements TenderDocDownloadService {

    @Resource
    private TenderDocDownloadMapper tenderDocDownloadMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TenderDocDownloadDO record(TenderDocDownloadRecordReqVO reqVO) {
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        // 幂等：同一文件同一用户只记一条（重复下载返回既有记录，不重复建档）
        if (reqVO.getFileId() != null && userId != null) {
            TenderDocDownloadDO exist = tenderDocDownloadMapper.selectByFileAndUser(reqVO.getFileId(), userId);
            if (exist != null) {
                return exist;
            }
        }
        if (reqVO.getFileId() == null) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.TENDER_DOC_DOWNLOAD_FILE_REQUIRED);
        }
        TenderDocDownloadDO download = new TenderDocDownloadDO();
        download.setTenderProjectCode(reqVO.getTenderProjectCode());
        download.setBidSectionCode(reqVO.getBidSectionCode());
        download.setAttachmentId(reqVO.getAttachmentId());
        download.setFileId(reqVO.getFileId());
        download.setSubjectId(reqVO.getSubjectId());
        download.setUserId(userId);
        download.setDownloadTime(LocalDateTime.now());
        download.setRemark(reqVO.getRemark());
        tenderDocDownloadMapper.insert(download);
        return download;
    }

    @Override
    public List<TenderDocDownloadDO> listDownloaders(String tenderProjectCode, String bidSectionCode) {
        return tenderDocDownloadMapper.selectListByScope(tenderProjectCode, bidSectionCode);
    }

    @Override
    public long countDownloaders(String bidSectionCode) {
        return tenderDocDownloadMapper.countBySection(bidSectionCode);
    }
}
