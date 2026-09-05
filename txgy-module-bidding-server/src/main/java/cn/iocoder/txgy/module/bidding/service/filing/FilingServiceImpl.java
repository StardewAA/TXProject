package cn.iocoder.txgy.module.bidding.service.filing;
// 规范依据：05 §2.1/§3.1/§3.2；《招投标模块开发规范》§4.5（幂等）

import cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil;
import cn.iocoder.txgy.module.bidding.controller.admin.filing.vo.FilingResultReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.filing.vo.FilingSubmitReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.filing.vo.FilingSubmitRespVO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.filing.FilingDO;
import cn.iocoder.txgy.module.bidding.dal.mysql.filing.FilingMapper;
import cn.iocoder.txgy.module.bidding.enums.ErrorCodeConstants;
import cn.iocoder.txgy.module.bidding.enums.FilingStatusEnum;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 备案服务实现（T 轨；受理记录落 bidding_filing）。
 */
@Service
public class FilingServiceImpl implements FilingService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Resource
    private FilingMapper filingMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FilingSubmitRespVO submitFiling(FilingSubmitReqVO reqVO) {
        // 幂等命中：同（filingType, projectCode）已存在直接报"已提交"
        FilingDO exists = filingMapper.selectOne(new LambdaQueryWrapper<FilingDO>()
                .eq(FilingDO::getFilingType, reqVO.getFilingType())
                .eq(FilingDO::getProjectCode, reqVO.getProjectCode())
                .last("LIMIT 1"));
        if (exists != null) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.FILING_ALREADY_SUBMITTED);
        }
        FilingDO filing = new FilingDO();
        filing.setFilingType(reqVO.getFilingType());
        filing.setProjectCode(reqVO.getProjectCode());
        filing.setTenderProjectCode(reqVO.getTenderProjectCode());
        filing.setUnifiedDealCode(reqVO.getUnifiedDealCode());
        filing.setTenderMode(reqVO.getTenderMode());
        filing.setOrgForm(reqVO.getOrgForm());
        filing.setSuperviseDeptCode(reqVO.getSuperviseDeptCode());
        filing.setSuperviseDeptName(reqVO.getSuperviseDeptName());
        filing.setSubmitTime(reqVO.getSubmitTime() != null ? reqVO.getSubmitTime() : LocalDateTime.now());
        filing.setDataTimestamp(reqVO.getDataTimestamp());
        filing.setExtraJson(writeExtra(reqVO.getAttachmentFileIds()));
        filing.setStatus(FilingStatusEnum.PENDING.name());
        filing.setReceiptNo(generateReceiptNo());
        filingMapper.insert(filing);

        FilingSubmitRespVO respVO = new FilingSubmitRespVO();
        respVO.setReceiptNo(filing.getReceiptNo());
        respVO.setStatus(filing.getStatus());
        respVO.setSubmitTime(filing.getSubmitTime());
        return respVO;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void receiveFilingResult(FilingResultReqVO reqVO) {
        if (!FilingStatusEnum.isReceivable(reqVO.getResultStatus())) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.FILING_STATUS_INVALID);
        }
        FilingDO filing = filingMapper.selectOne(new LambdaQueryWrapper<FilingDO>()
                .eq(FilingDO::getReceiptNo, reqVO.getReceiptNo())
                .last("LIMIT 1"));
        if (filing == null) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.FILING_NOT_EXISTS);
        }
        if (!FilingStatusEnum.PENDING.name().equals(filing.getStatus())) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.FILING_STATUS_INVALID);
        }
        filing.setStatus(reqVO.getResultStatus());
        filing.setOpinion(reqVO.getOpinion());
        filing.setReceiptTime(LocalDateTime.now());
        filingMapper.updateById(filing);
    }

    private String writeExtra(java.util.List<Long> attachmentFileIds) {
        try {
            return MAPPER.writeValueAsString(java.util.Map.of(
                    "attachmentFileIds", attachmentFileIds == null ? java.util.List.of() : attachmentFileIds));
        } catch (Exception e) {
            throw new IllegalStateException("附件快照序列化失败", e);
        }
    }

    private String generateReceiptNo() {
        return "FB" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
    }
}
