package cn.iocoder.txgy.module.mes.service.set.pollutioncheck;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.module.mes.controller.admin.set.pollutioncheck.vo.MesSetPollutionCheckAiRespVO;
import cn.iocoder.txgy.module.mes.controller.admin.set.pollutioncheck.vo.MesSetPollutionCheckPageReqVO;
import cn.iocoder.txgy.module.mes.controller.admin.set.pollutioncheck.vo.MesSetPollutionCheckReviewReqVO;
import cn.iocoder.txgy.module.mes.controller.admin.set.pollutioncheck.vo.MesSetPollutionCheckSaveReqVO;
import cn.iocoder.txgy.module.mes.dal.dataobject.set.pollutioncheck.MesPollutionCheckLogDO;
import cn.iocoder.txgy.module.mes.dal.dataobject.set.pollutioncheck.MesSetPollutionCheckDO;
import cn.iocoder.txgy.module.mes.dal.mysql.set.pollutioncheck.MesPollutionCheckLogMapper;
import cn.iocoder.txgy.module.mes.dal.mysql.set.pollutioncheck.MesSetPollutionCheckMapper;
import cn.iocoder.txgy.module.mes.service.pollution.MesPollutionControlService;
import cn.iocoder.txgy.module.mes.service.set.pollutioncheck.MesSetPollutionCheckAiService.PrescreenResult;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;
import static cn.iocoder.txgy.framework.security.core.util.SecurityFrameworkUtils.getLoginUserNickname;
import static cn.iocoder.txgy.module.mes.enums.ErrorCodeConstants.SET_POLLUTION_CHECK_ALREADY_REVIEWED;
import static cn.iocoder.txgy.module.mes.enums.ErrorCodeConstants.SET_POLLUTION_CHECK_NOT_EXISTS;
import static cn.iocoder.txgy.module.mes.enums.ErrorCodeConstants.SET_POLLUTION_CHECK_REVIEW_RESULT_INVALID;
import static cn.iocoder.txgy.module.mes.enums.ErrorCodeConstants.SET_POLLUTION_CHECK_STAGE_INVALID;

/**
 * MES 安全环保检测-污染判定记录 Service 实现类
 *
 * @author OPENLAB BS
 */
@Service
@Validated
public class MesSetPollutionCheckServiceImpl implements MesSetPollutionCheckService {

    /**
     * 环节：采购入库
     */
    public static final String STAGE_PURCHASE_INBOUND = "PURCHASE_INBOUND";
    /**
     * 环节：生产领用
     */
    public static final String STAGE_MATERIAL_ISSUE = "MATERIAL_ISSUE";
    /**
     * 环节：中间废弃物
     */
    public static final String STAGE_WASTE_INTERMEDIATE = "WASTE_INTERMEDIATE";
    /**
     * 环节：成品
     */
    public static final String STAGE_FINISHED_PRODUCT = "FINISHED_PRODUCT";

    /**
     * 人工复核结果：无污染
     */
    public static final String REVIEW_CLEAN = "CLEAN";
    /**
     * 人工复核结果：有污染
     */
    public static final String REVIEW_POLLUTED = "POLLUTED";

    private static final Set<String> STAGES = new HashSet<>(Arrays.asList(
            STAGE_PURCHASE_INBOUND, STAGE_MATERIAL_ISSUE, STAGE_WASTE_INTERMEDIATE, STAGE_FINISHED_PRODUCT));
    private static final Set<String> REVIEW_RESULTS = new HashSet<>(Arrays.asList(REVIEW_CLEAN, REVIEW_POLLUTED));

    private static final DateTimeFormatter RECORD_NO_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    @Resource
    private MesSetPollutionCheckMapper pollutionCheckMapper;

    @Resource
    private MesPollutionCheckLogMapper pollutionCheckLogMapper;

    @Resource
    private MesSetPollutionCheckAiService pollutionCheckAiService;

    @Resource
    private MesPollutionControlService pollutionControlService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createPollutionCheck(MesSetPollutionCheckSaveReqVO createReqVO) {
        validateStage(createReqVO.getStage());
        // 1. 组装记录，自动编号
        MesSetPollutionCheckDO pollutionCheck = BeanUtils.toBean(createReqVO, MesSetPollutionCheckDO.class);
        pollutionCheck.setRecordNo(generateRecordNo());
        // 2. AI 初筛回填，落为待复核（reviewResult=null）
        applyAiPrescreen(pollutionCheck);
        // 3. 插入
        pollutionCheckMapper.insert(pollutionCheck);
        // 4. 履历留痕：创建(含 AI 初筛快照)
        appendCheckLog(pollutionCheck, MesPollutionCheckLogDO.OP_CREATE, getCurrentUserName(), LocalDateTime.now());
        return pollutionCheck.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updatePollutionCheck(MesSetPollutionCheckSaveReqVO updateReqVO) {
        // 1. 校验存在 + 仅待复核可改
        MesSetPollutionCheckDO exist = validatePollutionCheckExists(updateReqVO.getId());
        if (exist.getReviewResult() != null) {
            throw exception(SET_POLLUTION_CHECK_ALREADY_REVIEWED);
        }
        validateStage(updateReqVO.getStage());
        // 2. 更新基础信息并重新 AI 初筛
        MesSetPollutionCheckDO updateObj = BeanUtils.toBean(updateReqVO, MesSetPollutionCheckDO.class);
        applyAiPrescreen(updateObj);
        pollutionCheckMapper.updateById(updateObj);
        // 3. 履历留痕：改单后的真实状态快照(含 AI 重筛结果)，旧版不覆盖
        MesSetPollutionCheckDO fresh = pollutionCheckMapper.selectById(exist.getId());
        appendCheckLog(fresh, MesPollutionCheckLogDO.OP_UPDATE, getCurrentUserName(), LocalDateTime.now());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deletePollutionCheck(Long id) {
        // 1. 校验存在 + 仅待复核可删
        MesSetPollutionCheckDO exist = validatePollutionCheckExists(id);
        if (exist.getReviewResult() != null) {
            throw exception(SET_POLLUTION_CHECK_ALREADY_REVIEWED);
        }
        // 2. 删除
        pollutionCheckMapper.deleteById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reviewPollutionCheck(MesSetPollutionCheckReviewReqVO reviewReqVO) {
        // 1. 校验存在 + 未复核
        MesSetPollutionCheckDO exist = validatePollutionCheckExists(reviewReqVO.getId());
        if (exist.getReviewResult() != null) {
            throw exception(SET_POLLUTION_CHECK_ALREADY_REVIEWED);
        }
        String reviewResult = reviewReqVO.getReviewResult();
        if (!REVIEW_RESULTS.contains(reviewResult)) {
            throw exception(SET_POLLUTION_CHECK_REVIEW_RESULT_INVALID);
        }
        // 2. 组装复核结果（终态二值收口）
        boolean polluted = REVIEW_POLLUTED.equals(reviewResult);
        MesSetPollutionCheckDO updateObj = MesSetPollutionCheckDO.builder()
                .id(exist.getId())
                .reviewResult(reviewResult)
                .storageMethod(defaultStorageMethod(reviewResult, exist.getSuggestedStorage()))
                .disposition(defaultDisposition(exist.getStage(), reviewResult))
                .marked(reviewReqVO.getMarked() != null ? reviewReqVO.getMarked() : polluted)
                .reviewBy(getCurrentUserName())
                .reviewTime(LocalDateTime.now())
                .remark(reviewReqVO.getRemark())
                .build();
        // 可选的字段按人工复核补充
        if (reviewReqVO.getStorageMethod() != null && !reviewReqVO.getStorageMethod().isEmpty()) {
            updateObj.setStorageMethod(reviewReqVO.getStorageMethod());
        }
        if (reviewReqVO.getDisposition() != null && !reviewReqVO.getDisposition().isEmpty()) {
            updateObj.setDisposition(reviewReqVO.getDisposition());
        }
        updateObj.setLocation(reviewReqVO.getLocation());
        pollutionCheckMapper.updateById(updateObj);
        // 复核写透（P2）：戳真实批次污染戳 + 有污染自动登记台账（A1+A2），同事务
        MesSetPollutionCheckDO reviewed = pollutionCheckMapper.selectById(exist.getId());
        pollutionControlService.applyReviewEffect(reviewed);
        // 履历留痕：人工复核终态快照（在写透成功后再记，复核被校验拒绝则整体回滚不落痕）
        appendCheckLog(reviewed, MesPollutionCheckLogDO.OP_REVIEW, reviewed.getReviewBy(), reviewed.getReviewTime());
    }

    @Override
    public MesSetPollutionCheckAiRespVO prescreenPollutionCheck(MesSetPollutionCheckSaveReqVO reqVO) {
        PrescreenResult result = pollutionCheckAiService.prescreen(reqVO.getItemName());
        return BeanUtils.toBean(result, MesSetPollutionCheckAiRespVO.class);
    }

    @Override
    public MesSetPollutionCheckDO getPollutionCheck(Long id) {
        return pollutionCheckMapper.selectById(id);
    }

    @Override
    public PageResult<MesSetPollutionCheckDO> getPollutionCheckPage(MesSetPollutionCheckPageReqVO pageReqVO) {
        return pollutionCheckMapper.selectPage(pageReqVO);
    }

    @Override
    public List<MesPollutionCheckLogDO> getPollutionCheckLogList(Long checkId) {
        return pollutionCheckLogMapper.selectByCheckId(checkId);
    }

    // ==================== 私有方法 ====================

    /**
     * 追加一条判定履历：把"该次操作之后"的真实快照(物料名+AI初筛+人工复核)落成不可改的一行。
     * 供 CREATE(初筛)/UPDATE(重筛)/REVIEW(复核) 三处调用，须在同一事务内。
     */
    private void appendCheckLog(MesSetPollutionCheckDO source, String opType, String opBy, LocalDateTime opTime) {
        if (source == null || source.getId() == null) {
            return;
        }
        MesPollutionCheckLogDO log = MesPollutionCheckLogDO.builder()
                .checkId(source.getId())
                .recordNo(source.getRecordNo())
                .opType(opType)
                .itemName(source.getItemName())
                .aiResult(source.getAiResult())
                .aiConfidence(source.getAiConfidence())
                .aiReason(source.getAiReason())
                .suggestedStorage(source.getSuggestedStorage())
                .reviewResult(source.getReviewResult())
                .storageMethod(source.getStorageMethod())
                .disposition(source.getDisposition())
                .location(source.getLocation())
                .marked(source.getMarked())
                .remark(source.getRemark())
                .opBy(opBy)
                .opTime(opTime)
                .build();
        pollutionCheckLogMapper.insert(log);
    }

    /**
     * AI 初筛回填（未复核状态只应持有 AI 预分类）
     */
    private void applyAiPrescreen(MesSetPollutionCheckDO pollutionCheck) {
        PrescreenResult result = pollutionCheckAiService.prescreen(pollutionCheck.getItemName());
        pollutionCheck.setAiResult(result.getAiResult());
        pollutionCheck.setAiConfidence(result.getAiConfidence());
        pollutionCheck.setAiReason(result.getAiReason());
        pollutionCheck.setSuggestedStorage(result.getSuggestedStorage());
        pollutionCheck.setReviewResult(null);
    }

    /**
     * 记录编号：PC-yyyyMMddHHmmss+3随机数
     */
    private String generateRecordNo() {
        String time = RECORD_NO_FORMATTER.format(LocalDateTime.now());
        int random = ThreadLocalRandom.current().nextInt(0, 1000);
        return String.format("PC-%s%03d", time, random);
    }

    /**
     * 最终存储方法默认值：无污染普通仓；有污染沿用 AI 推荐（无则受控隔离）
     */
    private String defaultStorageMethod(String reviewResult, String suggestedStorage) {
        if (REVIEW_CLEAN.equals(reviewResult)) {
            return "普通仓储";
        }
        return (suggestedStorage != null && !suggestedStorage.isEmpty()) ? suggestedStorage : "污染受控隔离存储";
    }

    /**
     * 处置方式默认值：按 环节 × 人工复核结果
     */
    private String defaultDisposition(String stage, String reviewResult) {
        boolean polluted = REVIEW_POLLUTED.equals(reviewResult);
        if (polluted) {
            if (STAGE_MATERIAL_ISSUE.equals(stage)) {
                return "REJECT_ISSUE"; // 拒绝领用
            }
            if (STAGE_WASTE_INTERMEDIATE.equals(stage)) {
                return "ISOLATE_STORAGE"; // 隔离暂存/保存处理
            }
            return "CONTROLLED_STORAGE"; // 采购入库/成品 → 受控存储
        }
        if (STAGE_MATERIAL_ISSUE.equals(stage)) {
            return "ISSUE_ALLOWED"; // 允许领用
        }
        if (STAGE_WASTE_INTERMEDIATE.equals(stage)) {
            return "REUSE"; // 回用（人工也可改选排放 DISCHARGE）
        }
        return "NORMAL_INBOUND"; // 采购入库/成品 → 正常入库/直接存储
    }

    private void validateStage(String stage) {
        if (!STAGES.contains(stage)) {
            throw exception(SET_POLLUTION_CHECK_STAGE_INVALID);
        }
    }

    private MesSetPollutionCheckDO validatePollutionCheckExists(Long id) {
        MesSetPollutionCheckDO pollutionCheck = pollutionCheckMapper.selectById(id);
        if (pollutionCheck == null) {
            throw exception(SET_POLLUTION_CHECK_NOT_EXISTS);
        }
        return pollutionCheck;
    }

    /**
     * 当前登录人昵称，取不到则回退为登录用户 id
     */
    private String getCurrentUserName() {
        String nickname = getLoginUserNickname();
        if (nickname == null || nickname.isEmpty()) {
            return String.valueOf(getLoginUserId());
        }
        return nickname;
    }

}
