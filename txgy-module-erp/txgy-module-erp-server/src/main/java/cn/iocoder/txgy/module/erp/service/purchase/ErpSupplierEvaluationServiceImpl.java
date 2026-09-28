package cn.iocoder.txgy.module.erp.service.purchase;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.txgy.framework.common.exception.ErrorCode;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.module.erp.controller.admin.purchase.vo.evaluation.ErpSupplierEvaluationPageReqVO;
import cn.iocoder.txgy.module.erp.controller.admin.purchase.vo.evaluation.ErpSupplierEvaluationSaveReqVO;
import cn.iocoder.txgy.module.erp.dal.dataobject.purchase.ErpSupplierEvaluationDO;
import cn.iocoder.txgy.module.erp.dal.mysql.purchase.ErpSupplierEvaluationMapper;
import cn.iocoder.txgy.module.erp.dal.redis.no.ErpNoRedisDAO;
import cn.iocoder.txgy.module.erp.enums.ErpAuditStatus;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.erp.enums.ErrorCodeConstants.*;

@Service
@Validated
public class ErpSupplierEvaluationServiceImpl implements ErpSupplierEvaluationService {

    /** 得分上限 */
    private static final BigDecimal SCORE_MAX = new BigDecimal("100");
    /** 综合得分计算权重：质量 0.4 / 交付 0.3 / 价格 0.2 / 服务 0.1 */
    private static final BigDecimal WEIGHT_QUALITY = new BigDecimal("0.4");
    private static final BigDecimal WEIGHT_DELIVERY = new BigDecimal("0.3");
    private static final BigDecimal WEIGHT_PRICE = new BigDecimal("0.2");
    private static final BigDecimal WEIGHT_SERVICE = new BigDecimal("0.1");

    @Resource private ErpSupplierEvaluationMapper evaluationMapper;
    @Resource private ErpNoRedisDAO noRedisDAO;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createSupplierEvaluation(ErpSupplierEvaluationSaveReqVO reqVO) {
        String no = noRedisDAO.generate(ErpNoRedisDAO.SUPPLIER_EVALUATION_NO_PREFIX);
        if (evaluationMapper.selectByNo(no) != null) throw exception(SUPPLIER_EVALUATION_NO_EXISTS);
        ErpSupplierEvaluationDO evaluation = buildEvaluation(reqVO)
                .setNo(no).setStatus(ErpAuditStatus.DRAFT.getStatus());
        evaluationMapper.insert(evaluation);
        return evaluation.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateSupplierEvaluation(ErpSupplierEvaluationSaveReqVO reqVO) {
        validateEditable(reqVO.getId(), SUPPLIER_EVALUATION_UPDATE_FAIL_STATUS);
        evaluationMapper.updateById(buildEvaluation(reqVO));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteSupplierEvaluation(List<Long> ids) {
        for (ErpSupplierEvaluationDO evaluation : evaluationMapper.selectByIds(ids)) {
            validateEditable(evaluation.getId(), SUPPLIER_EVALUATION_DELETE_FAIL_STATUS);
            evaluationMapper.deleteById(evaluation.getId());
        }
    }

    /** 组装评价信息：分项得分为空按 0 处理，综合得分与等级缺失时自动计算 */
    private ErpSupplierEvaluationDO buildEvaluation(ErpSupplierEvaluationSaveReqVO reqVO) {
        // 1. 分项得分
        BigDecimal qualityScore = defaultScore(reqVO.getQualityScore());
        BigDecimal deliveryScore = defaultScore(reqVO.getDeliveryScore());
        BigDecimal priceScore = defaultScore(reqVO.getPriceScore());
        BigDecimal serviceScore = defaultScore(reqVO.getServiceScore());
        // 2. 校验得分区间
        validateScore(qualityScore, deliveryScore, priceScore, serviceScore, reqVO.getTotalScore());
        // 3. 综合得分：前端未传时按权重自动计算
        BigDecimal totalScore = reqVO.getTotalScore() != null ? reqVO.getTotalScore()
                : qualityScore.multiply(WEIGHT_QUALITY).add(deliveryScore.multiply(WEIGHT_DELIVERY))
                        .add(priceScore.multiply(WEIGHT_PRICE)).add(serviceScore.multiply(WEIGHT_SERVICE));
        totalScore = totalScore.setScale(2, RoundingMode.HALF_UP);
        // 4. 评价等级：前端未传时按综合得分自动评定
        String grade = StrUtil.isBlank(reqVO.getGrade()) ? calculateGrade(totalScore) : reqVO.getGrade();
        return BeanUtils.toBean(reqVO, ErpSupplierEvaluationDO.class)
                .setQualityScore(qualityScore).setDeliveryScore(deliveryScore)
                .setPriceScore(priceScore).setServiceScore(serviceScore)
                .setTotalScore(totalScore).setGrade(grade);
    }

    private BigDecimal defaultScore(BigDecimal score) {
        return ObjectUtil.defaultIfNull(score, BigDecimal.ZERO);
    }

    private void validateScore(BigDecimal qualityScore, BigDecimal deliveryScore, BigDecimal priceScore,
                               BigDecimal serviceScore, BigDecimal totalScore) {
        for (BigDecimal score : List.of(qualityScore, deliveryScore, priceScore, serviceScore)) {
            if (score.compareTo(BigDecimal.ZERO) < 0 || score.compareTo(SCORE_MAX) > 0) {
                throw exception(SUPPLIER_EVALUATION_SCORE_INVALID);
            }
        }
        if (totalScore != null && (totalScore.compareTo(BigDecimal.ZERO) < 0 || totalScore.compareTo(SCORE_MAX) > 0)) {
            throw exception(SUPPLIER_EVALUATION_SCORE_INVALID);
        }
    }

    /** 综合得分 → 评价等级：>=90 A、>=80 B、>=70 C、其余 D */
    private String calculateGrade(BigDecimal totalScore) {
        if (totalScore.compareTo(new BigDecimal("90")) >= 0) return "A";
        if (totalScore.compareTo(new BigDecimal("80")) >= 0) return "B";
        if (totalScore.compareTo(new BigDecimal("70")) >= 0) return "C";
        return "D";
    }

    private ErpSupplierEvaluationDO validateEditable(Long id, ErrorCode code) {
        ErpSupplierEvaluationDO evaluation = validateSupplierEvaluation(id);
        if (!ObjectUtil.equal(evaluation.getStatus(), ErpAuditStatus.DRAFT.getStatus())
                && !ObjectUtil.equal(evaluation.getStatus(), ErpAuditStatus.REJECT.getStatus())
                && !ObjectUtil.equal(evaluation.getStatus(), ErpAuditStatus.CANCEL.getStatus())) {
            throw exception(code, evaluation.getNo());
        }
        return evaluation;
    }

    @Override public ErpSupplierEvaluationDO getSupplierEvaluation(Long id) { return evaluationMapper.selectById(id); }
    @Override public ErpSupplierEvaluationDO validateSupplierEvaluation(Long id) {
        ErpSupplierEvaluationDO evaluation = evaluationMapper.selectById(id);
        if (evaluation == null) throw exception(SUPPLIER_EVALUATION_NOT_EXISTS);
        return evaluation;
    }
    @Override public PageResult<ErpSupplierEvaluationDO> getSupplierEvaluationPage(ErpSupplierEvaluationPageReqVO reqVO) { return evaluationMapper.selectPage(reqVO); }
}
