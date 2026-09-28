package cn.iocoder.txgy.module.bidding.service.bidagent;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.common.util.json.JsonUtils;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.module.bidding.controller.admin.bidagent.vo.BidAgentTaskVO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.bidagent.BidAgentMaterialDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.bidagent.BidAgentTaskDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.bidagent.BidChapterDraftDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.bidagent.BidProjectDO;
import cn.iocoder.txgy.module.bidding.dal.mysql.bidagent.BidAgentMaterialMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.bidagent.BidAgentTaskMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.bidagent.BidChapterDraftMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.bidagent.BidProjectMapper;
import cn.iocoder.txgy.module.bidding.framework.bidagent.BidAgentOrchestratorClient;
import cn.iocoder.txgy.module.infra.api.file.FileApi;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.bidding.enums.ErrorCodeConstants.BID_AGENT_TASK_DUPLICATE;
import static cn.iocoder.txgy.module.bidding.enums.ErrorCodeConstants.BID_AGENT_TASK_NOT_EXISTS;
import static cn.iocoder.txgy.module.bidding.enums.ErrorCodeConstants.BID_AGENT_TASK_STATE_INVALID;
import static cn.iocoder.txgy.module.bidding.enums.ErrorCodeConstants.BID_AGENT_TENDER_MATERIAL_MISSING;

/**
 * 一键生成标书任务：Java 是任务与配置的权威源，执行交给 Node 底座（taskType=generate 全流程）。
 *
 * <p>为什么"懒同步"：不引入定时任务，前台/后台每次查询任务时向底座拉一次最新状态（含阶段、进度、
 * 待确认清单、产物），既保证实时性又避免常驻轮询。
 */
@Service
@Slf4j
public class BidAgentTaskServiceImpl implements BidAgentTaskService {

    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_RUNNING = "RUNNING";
    private static final String STATUS_SUCCEEDED = "SUCCEEDED";
    private static final String STATUS_FAILED = "FAILED";
    private static final String STATUS_CANCELED = "CANCELED";

    @Resource
    private BidAgentTaskMapper taskMapper;
    @Resource
    private BidAgentMaterialMapper materialMapper;
    @Resource
    // 命名注意：字段名会被 @Resource 当作 Bean 名优先匹配，必须与 Mapper 类一致的 Bean 名（bidProjectMapper），
    // 否则会命中平台既有 dal.mysql.project.ProjectMapper（JDK 代理，类型不符）导致启动失败。
    private BidProjectMapper bidProjectMapper;
    @Resource
    private BidChapterDraftMapper chapterDraftMapper;
    @Resource
    private BidAgentOrchestratorClient orchestratorClient;
    @Resource
    private ObjectProvider<FileApi> fileApiProvider;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long generate(BidAgentTaskVO.GenerateReqVO reqVO) {
        // 无需选项目/标段的一键生成：默认值兜底（projectId=0、标段=DEFAULT）
        if (reqVO.getProjectId() == null) {
            reqVO.setProjectId(0L);
        }
        if (StrUtil.isBlank(reqVO.getBidSectionCode())) {
            reqVO.setBidSectionCode("DEFAULT");
        }
        Long tenantId = TenantContextHolder.getTenantId();
        BidAgentMaterialDO tender = resolveTenderMaterial(reqVO);
        BidAgentMaterialDO template = reqVO.getTemplateMaterialId() == null ? null
                : materialMapper.selectById(reqVO.getTemplateMaterialId());

        if (!Boolean.TRUE.equals(reqVO.getForce())) {
            List<BidAgentTaskDO> running = taskMapper.selectRunningList(reqVO.getProjectId(), reqVO.getBidSectionCode());
            if (!running.isEmpty()) {
                throw exception(BID_AGENT_TASK_DUPLICATE, running.get(0).getTaskNo());
            }
        }

        BidAgentTaskDO task = new BidAgentTaskDO();
        task.setTenantId(tenantId);
        task.setTaskNo(buildTaskNo(tenantId, reqVO));
        task.setProjectId(reqVO.getProjectId());
        task.setBidSectionCode(reqVO.getBidSectionCode());
        task.setDocCategory(reqVO.getDocCategory());
        task.setTenderMaterialId(tender.getId());
        task.setTemplateMaterialId(template == null ? null : template.getId());
        task.setStatus(STATUS_PENDING);
        task.setProgress(0);
        task.setStartedAt(LocalDateTime.now());
        task.setRemark(reqVO.getRemark());
        try {
            taskMapper.insert(task);
        } catch (DuplicateKeyException ex) {
            // 并发兜底：先查后插的判重存在竞态窗口，由 DB 唯一键（running_key 生成列，进行中=租户:项目:标段）最终拦截
            throw exception(BID_AGENT_TASK_DUPLICATE, task.getTaskNo());
        }

        submitToOrchestrator(task, tender, template);
        return task.getId();
    }

    @Override
    public BidAgentTaskVO.RespVO get(Long id) {
        BidAgentTaskDO task = requireTask(id);
        sync(task);
        return toRespVO(task);
    }

    @Override
    public PageResult<BidAgentTaskVO.RespVO> page(BidAgentTaskVO.PageReqVO reqVO) {
        PageResult<BidAgentTaskDO> page = taskMapper.selectPage(reqVO, reqVO.getProjectId(), reqVO.getStatus());
        List<BidAgentTaskVO.RespVO> list = new ArrayList<>();
        for (BidAgentTaskDO task : page.getList()) {
            // 列表只对"进行中"的任务做懒同步，避免一页几十条把底座打满
            if (isRunning(task)) {
                sync(task);
            }
            list.add(toRespVO(task));
        }
        return new PageResult<>(list, page.getTotal());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long id) {
        BidAgentTaskDO task = requireTask(id);
        if (!isRunning(task)) {
            throw exception(BID_AGENT_TASK_STATE_INVALID, task.getStatus());
        }
        if (StrUtil.isNotBlank(task.getAgentTaskId())) {
            try {
                orchestratorClient.interruptTask(task.getAgentTaskId());
            } catch (Exception ex) {
                log.warn("[BidAgentTaskServiceImpl][cancel] 中断底座任务失败 taskId={} msg={}",
                        task.getAgentTaskId(), ex.getMessage());
            }
        }
        task.setStatus(STATUS_CANCELED);
        task.setFinishedAt(LocalDateTime.now());
        taskMapper.updateById(task);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirm(Long id, List<BidAgentTaskVO.ConfirmReqVO.Answer> answers) {
        BidAgentTaskDO task = requireTask(id);
        if (!isRunning(task)) {
            throw exception(BID_AGENT_TASK_STATE_INVALID, task.getStatus());
        }
        if (answers == null || answers.isEmpty()) {
            throw exception(BID_AGENT_TASK_STATE_INVALID, "确认答复不能为空");
        }
        List<Map<String, String>> list = new ArrayList<>(answers.size());
        for (BidAgentTaskVO.ConfirmReqVO.Answer answer : answers) {
            Map<String, String> item = new LinkedHashMap<>();
            item.put("questionId", answer.getQuestionId());
            item.put("value", answer.getValue());
            list.add(item);
        }
        orchestratorClient.confirmTask(task.getAgentTaskId(), list);
        // 确认后立即懒同步一次，让台账（阶段/进度/待确认清单）尽快反映底座最新进展
        sync(task);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        BidAgentTaskDO task = requireTask(id);
        // 运行中/待执行的任务，先尝试中断底座任务，避免残留后台执行
        if (isRunning(task)) {
            if (StrUtil.isNotBlank(task.getAgentTaskId())) {
                try {
                    orchestratorClient.interruptTask(task.getAgentTaskId());
                } catch (Exception ex) {
                    log.warn("[BidAgentTaskServiceImpl][delete] 中断底座任务失败 taskId={} msg={}",
                            task.getAgentTaskId(), ex.getMessage());
                }
            }
            // 先落取消终态再逻辑删除：running_key 生成列在 PENDING/RUNNING 时占用同标段唯一槽位，
            // 若带着运行态直接逻辑删除，残留的幽灵行会永久占住槽位、阻断后续一键生成（列表又查不到它）
            task.setStatus(STATUS_CANCELED);
            task.setFinishedAt(LocalDateTime.now());
            taskMapper.updateById(task);
        }
        taskMapper.deleteById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void retry(Long id) {
        BidAgentTaskDO task = requireTask(id);
        if (!STATUS_FAILED.equals(task.getStatus()) && !STATUS_CANCELED.equals(task.getStatus())) {
            throw exception(BID_AGENT_TASK_STATE_INVALID, task.getStatus());
        }
        BidAgentMaterialDO tender = materialMapper.selectById(task.getTenderMaterialId());
        if (tender == null) {
            throw exception(BID_AGENT_TENDER_MATERIAL_MISSING);
        }
        BidAgentMaterialDO template = task.getTemplateMaterialId() == null ? null
                : materialMapper.selectById(task.getTemplateMaterialId());
        task.setErrorCode(null);
        task.setErrorMessage(null);
        task.setStatus(STATUS_PENDING);
        task.setProgress(0);
        task.setStartedAt(LocalDateTime.now());
        task.setFinishedAt(null);
        submitToOrchestrator(task, tender, template);
    }

    @Override
    public BidAgentTaskVO.OutlineRespVO outline(Long id) {
        BidAgentTaskDO task = requireTask(id);
        BidProjectDO project = bidProjectMapper.selectById(task.getProjectId());
        BidAgentTaskVO.OutlineRespVO resp = new BidAgentTaskVO.OutlineRespVO();
        resp.setTaskId(task.getId());
        resp.setProjectId(task.getProjectId());
        resp.setOutlineJson(project == null ? null : project.getOutlineJson());
        List<BidAgentTaskVO.Chapter> chapters = new ArrayList<>();
        List<BidChapterDraftDO> drafts = chapterDraftMapper.selectList(
                new LambdaQueryWrapperX<BidChapterDraftDO>()
                        .eq(BidChapterDraftDO::getProjectId, task.getProjectId())
                        .orderByAsc(BidChapterDraftDO::getId));
        for (BidChapterDraftDO draft : drafts) {
            BidAgentTaskVO.Chapter chapter = new BidAgentTaskVO.Chapter();
            chapter.setNodeId(draft.getNodeId());
            chapter.setTitle(draft.getNodeId());
            chapter.setContent(draft.getContent());
            chapter.setStatus(draft.getStatus());
            chapter.setVersion(draft.getVersion());
            chapters.add(chapter);
        }
        resp.setChapters(chapters);
        return resp;
    }

    @Override
    public String artifactUrl(Long id) {
        BidAgentTaskDO task = requireTask(id);
        if (StrUtil.isBlank(task.getArtifactUrl())) {
            pullArtifact(task);
        }
        if (StrUtil.isBlank(task.getArtifactUrl())) {
            throw exception(BID_AGENT_TASK_STATE_INVALID, "产物尚未生成（当前状态 " + task.getStatus() + "）");
        }
        return task.getArtifactUrl();
    }

    private BidAgentMaterialDO resolveTenderMaterial(BidAgentTaskVO.GenerateReqVO reqVO) {
        if (reqVO.getTenderMaterialId() != null) {
            BidAgentMaterialDO material = materialMapper.selectById(reqVO.getTenderMaterialId());
            if (material == null) {
                throw exception(BID_AGENT_TENDER_MATERIAL_MISSING);
            }
            return material;
        }
        List<BidAgentMaterialDO> list = materialMapper.selectList(new LambdaQueryWrapperX<BidAgentMaterialDO>()
                .eq(BidAgentMaterialDO::getDocType, "TENDER")
                .eq(BidAgentMaterialDO::getBidSectionCode, reqVO.getBidSectionCode()));
        if (list.isEmpty()) {
            throw exception(BID_AGENT_TENDER_MATERIAL_MISSING);
        }
        return list.get(0);
    }

    private String buildTaskNo(Long tenantId, BidAgentTaskVO.GenerateReqVO reqVO) {
        return StrUtil.format("GEN-{}-{}-{}-{}", tenantId, reqVO.getProjectId(),
                StrUtil.maxLength(reqVO.getBidSectionCode(), 32), System.currentTimeMillis());
    }

    /** 把任务提交给底座（新建或重试共用） */
    private void submitToOrchestrator(BidAgentTaskDO task, BidAgentMaterialDO tender, BidAgentMaterialDO template) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("bidSectionCode", task.getBidSectionCode());
        payload.put("docCategory", task.getDocCategory());
        payload.put("sourceDocName", tender.getName());
        payload.put("tenderMaterialId", tender.getId());
        if (template != null) {
            payload.put("templateId", template.getName());
            payload.put("templateSourceDocName", template.getName());
            payload.put("templateMaterialId", template.getId());
            payload.put("templateFileUrl", template.getFileUrl());
        }
        payload.put("remark", task.getRemark());
        JsonNode node = orchestratorClient.createTask(String.valueOf(task.getTenantId()), task.getProjectId(),
                "generate", payload);
        String agentTaskId = node.path("taskId").asText(null);
        if (StrUtil.isBlank(agentTaskId)) {
            throw exception(BID_AGENT_TASK_STATE_INVALID, "底座未返回 taskId");
        }
        task.setAgentTaskId(agentTaskId);
        task.setStatus(STATUS_RUNNING);
        taskMapper.updateById(task);
    }

    private boolean isRunning(BidAgentTaskDO task) {
        return (STATUS_PENDING.equals(task.getStatus()) || STATUS_RUNNING.equals(task.getStatus()))
                && StrUtil.isNotBlank(task.getAgentTaskId());
    }

    /** 懒同步：向底座拉一次最新状态并落台账（含阶段、进度、待确认清单、产物、token） */
    private void sync(BidAgentTaskDO task) {
        if (!isRunning(task)) {
            return;
        }
        JsonNode node;
        try {
            node = orchestratorClient.getTask(task.getAgentTaskId());
        } catch (Exception ex) {
            log.warn("[BidAgentTaskServiceImpl][sync] 同步底座状态失败 taskId={} msg={}",
                    task.getAgentTaskId(), ex.getMessage());
            return;
        }
        String nodeStatus = node.path("status").asText("");
        String phase = node.path("phase").asText(null);
        double progress = node.path("progress").asDouble(0);
        task.setCurrentStep(StrUtil.blankToDefault(phase, task.getCurrentStep()));
        task.setProgress((int) Math.round(progress * 100));

        // 待确认清单：等待人工阶段（awaiting_human）的阻塞问题（含 questionId，供 /confirm 回填）优先；
        // 完成后取 result.openQuestions 的「待人工补充」摘要兜底；两者皆无则清空，避免残留上一轮的待确认清单。
        JsonNode pending = node.path("pendingQuestions");
        JsonNode result = node.path("result");
        if (pending.isArray() && pending.size() > 0) {
            task.setOpenQuestionsJson(pending.toString());
        } else if (result.hasNonNull("openQuestions")) {
            task.setOpenQuestionsJson(result.path("openQuestions").toString());
        } else {
            task.setOpenQuestionsJson(null);
        }
        JsonNode usage = node.path("usage");
        if (usage.hasNonNull("totalTokens")) {
            task.setPromptTokens(usage.path("promptTokens").asInt(0));
            task.setCompletionTokens(usage.path("completionTokens").asInt(0));
            task.setTotalTokens(usage.path("totalTokens").asInt(0));
        }

        if ("completed".equals(nodeStatus)) {
            task.setStatus(STATUS_SUCCEEDED);
            task.setProgress(100);
            task.setFinishedAt(LocalDateTime.now());
            pullArtifact(task);
        } else if ("failed".equals(nodeStatus)) {
            task.setStatus(STATUS_FAILED);
            task.setFinishedAt(LocalDateTime.now());
            task.setErrorCode(node.path("error").path("code").asText(null));
            task.setErrorMessage(StrUtil.maxLength(node.path("error").path("message").asText("底座执行失败"), 480));
        } else if ("interrupted".equals(nodeStatus) || "rolled_back".equals(nodeStatus)) {
            task.setStatus(STATUS_CANCELED);
            task.setFinishedAt(LocalDateTime.now());
        } else {
            task.setStatus(STATUS_RUNNING);
        }
        taskMapper.updateById(task);
    }

    /** 产物回收：从底座拉 docx 二进制 → 登记进 infra 文件服务 → 落台账（供前台直接下载） */
    private void pullArtifact(BidAgentTaskDO task) {
        try {
            byte[] bytes = orchestratorClient.downloadDocx(String.valueOf(task.getTenantId()), task.getProjectId(),
                    task.getAgentTaskId());
            if (bytes == null || bytes.length == 0) {
                return;
            }
            FileApi fileApi = fileApiProvider.getIfAvailable();
            if (fileApi == null) {
                log.warn("[BidAgentTaskServiceImpl][pullArtifact] FileApi 不可用，跳过产物登记 taskNo={}", task.getTaskNo());
                return;
            }
            // 标书文件命名：优先「标书名称」（一键生成时前台填写，落在 remark），清洗非法文件名字符；
            // 未填写时回退任务号（2026-09-21：用户要求产物文件名与标书名称一致）
            String base = StrUtil.isNotBlank(task.getRemark())
                    ? task.getRemark().trim().replaceAll("[\\\\/:*?\"<>|\\r\\n]", "_")
                    : task.getTaskNo();
            String name = StrUtil.maxLength(base, 80) + ".docx";
            String url = fileApi.createFile(bytes, name, "bidding/bid-agent/generate", "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
            task.setArtifactUrl(url);
            try {
                var metadata = fileApi.getMetadataByUrl(url);
                if (metadata != null && metadata.getCheckedData() != null) {
                    task.setArtifactFileId(metadata.getCheckedData().getId());
                }
            } catch (Exception ex) {
                log.warn("[BidAgentTaskServiceImpl][pullArtifact] 回查 file_id 失败 url={} msg={}", url, ex.getMessage());
            }
            taskMapper.updateById(task);
        } catch (Exception ex) {
            log.warn("[BidAgentTaskServiceImpl][pullArtifact] 产物回收失败 taskNo={} msg={}", task.getTaskNo(), ex.getMessage());
        }
    }

    private BidAgentTaskDO requireTask(Long id) {
        BidAgentTaskDO task = id == null ? null : taskMapper.selectById(id);
        if (task == null) {
            throw exception(BID_AGENT_TASK_NOT_EXISTS);
        }
        return task;
    }

    private BidAgentTaskVO.RespVO toRespVO(BidAgentTaskDO task) {
        BidAgentTaskVO.RespVO vo = BeanUtils.toBean(task, BidAgentTaskVO.RespVO.class);
        if (task.getTenderMaterialId() != null) {
            BidAgentMaterialDO material = materialMapper.selectById(task.getTenderMaterialId());
            vo.setTenderMaterialName(material == null ? null : material.getName());
        }
        return vo;
    }
}
