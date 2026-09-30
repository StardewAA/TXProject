package cn.iocoder.txgy.module.bidding.service.bidreview;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.module.bidding.controller.admin.bidreview.vo.BidReviewRuleItemPageReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.bidreview.vo.BidReviewRuleItemSaveReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.bidreview.vo.BidReviewRuleSetPageReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.bidreview.vo.BidReviewRuleSetSaveReqVO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.bidreview.BidReviewRuleItemDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.bidreview.BidReviewRuleSetDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.bidreview.BidReviewTaskDO;
import cn.iocoder.txgy.module.bidding.dal.mysql.bidreview.BidReviewRuleItemMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.bidreview.BidReviewRuleSetMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.bidreview.BidReviewTaskMapper;
import cn.iocoder.txgy.module.bidding.enums.ErrorCodeConstants;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;

/**
 * 智慧标书审查 · 规则库实现。
 *
 * <p>方案依据：§2.2、§5.2。设计遵循：
 * ① 平台默认规则集是个人端"未传招标文件"的兜底，内置 12 条通用废标/格式/评分项（不依赖外部数据即可跑通）；
 * ② 招标文件解析出的规则集按 tender_file_sha256 复用，同一份招标文件不重复调模型；
 * ③ 规则集被任务引用时不允许删除（避免历史任务失去审查依据）。
 */
@Slf4j
@Service
public class BidReviewRuleServiceImpl implements BidReviewRuleService {

    private static final String DEFAULT_RULE_SET_CODE = "PLATFORM_DEFAULT";

    @Resource
    private BidReviewRuleSetMapper ruleSetMapper;
    @Resource
    private BidReviewRuleItemMapper ruleItemMapper;
    @Resource
    private BidReviewTaskMapper taskMapper;
    @Resource
    private BidReviewAiReviewService aiReviewService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BidReviewRuleSetDO resolveRuleSetForTask(BidReviewTaskDO task, String tenderText) {
        if (task.getRuleSetId() != null) {
            BidReviewRuleSetDO specified = ruleSetMapper.selectById(task.getRuleSetId());
            if (specified != null) {
                return specified;
            }
        }
        if (StrUtil.isNotBlank(tenderText) && aiReviewService.isAvailable()) {
            String sha = DigestUtil.sha256Hex(tenderText);
            BidReviewRuleSetDO reused = ruleSetMapper.selectByTenderSha(sha);
            if (reused != null) {
                return reused;
            }
            try {
                List<BidReviewAiReviewService.RuleDraft> drafts =
                        aiReviewService.extractRules(tenderText, task.getId());
                if (!drafts.isEmpty()) {
                    BidReviewRuleSetDO ruleSet = new BidReviewRuleSetDO();
                    ruleSet.setRuleSetCode("TD" + StrUtil.sub(sha, 0, 12));
                    ruleSet.setRuleSetName("招标文件解析规则 - " + StrUtil.blankToDefault(task.getTaskName(), task.getTaskNo()));
                    ruleSet.setSource("TENDER_DOC");
                    ruleSet.setTenderFileId(task.getTenderFileId());
                    ruleSet.setTenderFileSha256(sha);
                    ruleSet.setParseStatus("SUCCESS");
                    ruleSet.setRuleCnt(drafts.size());
                    ruleSet.setEnable(true);
                    ruleSet.setDefaultFlag(false);
                    ruleSetMapper.insert(ruleSet);
                    int sort = 0;
                    for (BidReviewAiReviewService.RuleDraft draft : drafts) {
                        ruleItemMapper.insert(toItem(ruleSet.getId(), draft, sort++));
                    }
                    return ruleSet;
                }
            } catch (Exception e) {
                // 抽规则失败不阻断：退回平台默认规则集（流水线会继续跑，报告会体现降级）
                log.warn("[resolveRuleSetForTask][招标文件抽规则失败，退回平台规则库 taskId={}]", task.getId(), e);
            }
        }
        return getOrCreateDefaultRuleSet();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BidReviewRuleSetDO getOrCreateDefaultRuleSet() {
        BidReviewRuleSetDO existing = ruleSetMapper.selectDefaultSet();
        if (existing != null) {
            return existing;
        }
        BidReviewRuleSetDO ruleSet = new BidReviewRuleSetDO();
        ruleSet.setRuleSetCode(DEFAULT_RULE_SET_CODE);
        ruleSet.setRuleSetName("平台默认审查规则集");
        ruleSet.setSource("PLATFORM");
        ruleSet.setParseStatus("SUCCESS");
        ruleSet.setEnable(true);
        ruleSet.setDefaultFlag(true);
        ruleSet.setRemark("平台通用废标项/格式要求/评分项，个人端未上传招标文件时使用");
        ruleSetMapper.insert(ruleSet);
        List<BidReviewRuleItemDO> seeds = defaultSeedItems(ruleSet.getId());
        for (BidReviewRuleItemDO seed : seeds) {
            ruleItemMapper.insert(seed);
        }
        ruleSet.setRuleCnt(seeds.size());
        ruleSetMapper.updateById(ruleSet);
        return ruleSet;
    }

    /** 平台默认规则种子（通用、可判定、覆盖典型废标场景；业务可按需增删）。 */
    private List<BidReviewRuleItemDO> defaultSeedItems(Long ruleSetId) {
        List<BidReviewRuleItemDO> items = new ArrayList<>();
        items.add(seed(ruleSetId, "P001", "投标函是否盖章并签字", "FORM", "DISQUALIFY", "RULE", "HIGH", null,
                "投标函须加盖单位公章并由法定代表人或授权代表签字", "投标函|盖章|签字", "第三章 投标人须知"));
        items.add(seed(ruleSetId, "P002", "法定代表人身份证明或授权委托书", "FORM", "DISQUALIFY", "RULE", "HIGH", null,
                "委托他人投标须提供授权委托书", "法定代表人|授权委托", "第三章 投标人须知"));
        items.add(seed(ruleSetId, "P003", "投标保证金凭证", "FORM", "DISQUALIFY", "RULE", "HIGH", null,
                "须提供投标保证金缴纳凭证且在有效期内", "保证金", "第三章 投标人须知"));
        items.add(seed(ruleSetId, "P004", "营业执照与经营范围", "QUALIFICATION", "QUALIFICATION", "AI", "HIGH", null,
                "具备有效营业执照且经营范围覆盖招标内容", "", "第二章 投标人资格要求"));
        items.add(seed(ruleSetId, "P005", "资质等级满足招标要求", "QUALIFICATION", "QUALIFICATION", "AI", "HIGH", null,
                "具备招标文件要求的资质等级", "", "第二章 投标人资格要求"));
        items.add(seed(ruleSetId, "P006", "类似业绩数量与规模", "QUALIFICATION", "QUALIFICATION", "AI", "MEDIUM", null,
                "满足招标文件要求的类似业绩数量与规模", "", "第二章 投标人资格要求"));
        items.add(seed(ruleSetId, "P007", "项目负责人及关键人员证书", "QUALIFICATION", "QUALIFICATION", "AI", "MEDIUM", null,
                "拟派项目负责人及关键岗位人员具备相应资格证书", "", "第二章 投标人资格要求"));
        items.add(seed(ruleSetId, "P008", "工期与质量承诺响应", "RESPONSE", "DISQUALIFY", "AI", "HIGH", null,
                "对招标文件的工期、质量要求作出实质性响应", "", "第三章 评审办法"));
        items.add(seed(ruleSetId, "P009", "报价唯一且不超最高限价", "PRICE", "DISQUALIFY", "AI", "HIGH", null,
                "只能有一个有效报价，且不得超过招标文件最高限价", "投标报价", "第三章 评审办法"));
        items.add(seed(ruleSetId, "P010", "施工组织设计完整性", "TECH", "SCORING", "AI", "LOW", BigDecimal.valueOf(40),
                "施工组织设计内容完整、针对性强", "", "第三章 评分办法"));
        items.add(seed(ruleSetId, "P011", "主要施工工艺及技术措施", "TECH", "SCORING", "AI", "LOW", BigDecimal.valueOf(20),
                "主要施工工艺与技术措施可行", "", "第三章 评分办法"));
        items.add(seed(ruleSetId, "P012", "商务条款响应与优惠承诺", "BUSINESS", "SCORING", "AI", "LOW", BigDecimal.valueOf(10),
                "商务条款响应完整、有无优惠承诺", "", "第三章 评分办法"));
        return items;
    }

    private BidReviewRuleItemDO seed(Long ruleSetId, String code, String name, String category, String ruleType,
                                    String checkMode, String severity, BigDecimal scoreMax, String requirement,
                                    String hint, String sourceRef) {
        BidReviewRuleItemDO item = new BidReviewRuleItemDO();
        item.setRuleSetId(ruleSetId);
        item.setRuleCode(code);
        item.setRuleName(name);
        item.setCategory(category);
        item.setRuleType(ruleType);
        item.setCheckMode(checkMode);
        item.setSeverity(severity);
        item.setScoreMax(scoreMax);
        item.setRequirement(requirement);
        item.setEvidenceHint(hint);
        item.setSourceRef(sourceRef);
        item.setSort(Integer.parseInt(code.substring(1)));
        item.setEnable(true);
        return item;
    }

    private BidReviewRuleItemDO toItem(Long ruleSetId, BidReviewAiReviewService.RuleDraft draft, int sort) {
        BidReviewRuleItemDO item = new BidReviewRuleItemDO();
        item.setRuleSetId(ruleSetId);
        item.setRuleCode(draft.ruleCode());
        item.setRuleName(draft.ruleName());
        item.setCategory(draft.category());
        item.setRuleType(draft.ruleType());
        item.setCheckMode(draft.checkMode());
        item.setSeverity(draft.severity());
        item.setScoreMax(draft.scoreMax());
        item.setRequirement(draft.requirement());
        item.setEvidenceHint(draft.evidenceHint());
        item.setSourceRef(draft.sourceRef());
        item.setSort(sort);
        item.setEnable(true);
        return item;
    }

    @Override
    public Long createRuleSet(BidReviewRuleSetSaveReqVO reqVO) {
        BidReviewRuleSetDO ruleSet = BeanUtils.toBean(reqVO, BidReviewRuleSetDO.class);
        if (StrUtil.isBlank(ruleSet.getRuleSetCode())) {
            ruleSet.setRuleSetCode("RS" + System.currentTimeMillis());
        }
        ruleSet.setRuleCnt(0);
        ruleSet.setParseStatus("SUCCESS");
        ruleSetMapper.insert(ruleSet);
        return ruleSet.getId();
    }

    @Override
    public void updateRuleSet(BidReviewRuleSetSaveReqVO reqVO) {
        BidReviewRuleSetDO ruleSet = getRuleSetOrThrow(reqVO.getId());
        ruleSet.setRuleSetName(reqVO.getRuleSetName());
        ruleSet.setEnable(reqVO.getEnable());
        ruleSet.setDefaultFlag(reqVO.getDefaultFlag());
        ruleSet.setRemark(reqVO.getRemark());
        ruleSetMapper.updateById(ruleSet);
    }

    @Override
    public void deleteRuleSet(Long id) {
        getRuleSetOrThrow(id);
        // 被任务引用则不允许删（历史任务的审查依据不能悬空）
        Long used = taskMapper.selectCount(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<BidReviewTaskDO>()
                .eq(BidReviewTaskDO::getRuleSetId, id));
        if (used != null && used > 0) {
            throw exception(ErrorCodeConstants.BID_REVIEW_RULE_SET_IN_USE);
        }
        ruleSetMapper.deleteById(id);
        ruleItemMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<BidReviewRuleItemDO>()
                .eq(BidReviewRuleItemDO::getRuleSetId, id));
    }

    @Override
    public void enableRuleSet(Long id, Boolean enable) {
        BidReviewRuleSetDO ruleSet = getRuleSetOrThrow(id);
        ruleSet.setEnable(enable);
        ruleSetMapper.updateById(ruleSet);
    }

    @Override
    public BidReviewRuleSetDO getRuleSet(Long id) {
        return getRuleSetOrThrow(id);
    }

    @Override
    public PageResult<BidReviewRuleSetDO> getRuleSetPage(BidReviewRuleSetPageReqVO reqVO) {
        return ruleSetMapper.selectPage(reqVO);
    }

    @Override
    public List<BidReviewRuleItemDO> getRuleItems(Long ruleSetId) {
        return ruleItemMapper.selectListByRuleSetId(ruleSetId);
    }

    @Override
    public Long createRuleItem(BidReviewRuleItemSaveReqVO reqVO) {
        getRuleSetOrThrow(reqVO.getRuleSetId());
        BidReviewRuleItemDO item = BeanUtils.toBean(reqVO, BidReviewRuleItemDO.class);
        if (StrUtil.isBlank(item.getRuleCode())) {
            item.setRuleCode("R" + System.currentTimeMillis());
        }
        ruleItemMapper.insert(item);
        refreshRuleCount(reqVO.getRuleSetId());
        return item.getId();
    }

    @Override
    public void updateRuleItem(BidReviewRuleItemSaveReqVO reqVO) {
        BidReviewRuleItemDO item = ruleItemMapper.selectById(reqVO.getId());
        if (item == null) {
            throw exception(ErrorCodeConstants.BID_REVIEW_RULE_SET_NOT_EXISTS);
        }
        BidReviewRuleItemDO update = BeanUtils.toBean(reqVO, BidReviewRuleItemDO.class);
        update.setId(item.getId());
        ruleItemMapper.updateById(update);
    }

    @Override
    public void deleteRuleItem(Long id) {
        BidReviewRuleItemDO item = ruleItemMapper.selectById(id);
        ruleItemMapper.deleteById(id);
        if (item != null) {
            refreshRuleCount(item.getRuleSetId());
        }
    }

    @Override
    public PageResult<BidReviewRuleItemDO> getRuleItemPage(BidReviewRuleItemPageReqVO reqVO) {
        return ruleItemMapper.selectPage(reqVO);
    }

    private void refreshRuleCount(Long ruleSetId) {
        BidReviewRuleSetDO ruleSet = ruleSetMapper.selectById(ruleSetId);
        if (ruleSet == null) {
            return;
        }
        ruleSet.setRuleCnt(ruleItemMapper.selectListByRuleSetId(ruleSetId).size());
        ruleSetMapper.updateById(ruleSet);
    }

    private BidReviewRuleSetDO getRuleSetOrThrow(Long id) {
        BidReviewRuleSetDO ruleSet = id == null ? null : ruleSetMapper.selectById(id);
        if (ruleSet == null) {
            throw exception(ErrorCodeConstants.BID_REVIEW_RULE_SET_NOT_EXISTS);
        }
        return ruleSet;
    }
}
