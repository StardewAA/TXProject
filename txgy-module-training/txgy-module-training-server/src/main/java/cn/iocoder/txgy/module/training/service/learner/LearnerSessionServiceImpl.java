package cn.iocoder.txgy.module.training.service.learner;

import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.txgy.module.training.controller.portal.learner.vo.LearnerSessionEndRespVO;
import cn.iocoder.txgy.module.training.controller.portal.learner.vo.LearnerSessionHeartbeatRespVO;
import cn.iocoder.txgy.module.training.controller.portal.learner.vo.LearnerSessionStartReqVO;
import cn.iocoder.txgy.module.training.controller.portal.learner.vo.LearnerSessionStartRespVO;
import cn.iocoder.txgy.module.training.controller.portal.learner.vo.LearnerSessionVerifyReqVO;
import cn.iocoder.txgy.module.training.dal.dataobject.course.CourseDO;
import cn.iocoder.txgy.module.training.dal.dataobject.course.CourseMediaDO;
import cn.iocoder.txgy.module.training.dal.dataobject.learning.CheckinRecordDO;
import cn.iocoder.txgy.module.training.dal.dataobject.learning.DocReadLogDO;
import cn.iocoder.txgy.module.training.dal.dataobject.learning.LearnSessionDO;
import cn.iocoder.txgy.module.training.dal.dataobject.learning.LearnerTaskDO;
import cn.iocoder.txgy.module.training.dal.dataobject.learning.LiveRoomDO;
import cn.iocoder.txgy.module.training.dal.dataobject.learning.VerifyChallengeDO;
import cn.iocoder.txgy.module.training.dal.mysql.learning.CheckinRecordMapper;
import cn.iocoder.txgy.module.training.dal.mysql.learning.DocReadLogMapper;
import cn.iocoder.txgy.module.training.dal.mysql.learning.LearnSessionMapper;
import cn.iocoder.txgy.module.training.dal.mysql.learning.LearnerTaskMapper;
import cn.iocoder.txgy.module.training.dal.mysql.learning.LiveRoomMapper;
import cn.iocoder.txgy.module.training.dal.mysql.learning.VerifyChallengeMapper;
import cn.iocoder.txgy.module.training.enums.ErrorCodeConstants;
import cn.iocoder.txgy.module.training.enums.course.CourseMediaTypeEnum;
import cn.iocoder.txgy.module.training.enums.course.CourseStatusEnum;
import cn.iocoder.txgy.module.training.enums.learner.LearnMediaTypeEnum;
import cn.iocoder.txgy.module.training.enums.learner.LearnerSessionStatusEnum;
import cn.iocoder.txgy.module.training.service.course.CourseService;
import cn.iocoder.txgy.module.training.service.learning.LearningConfigService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;

/**
 * 学习会话服务实现（防挂机核心引擎）
 *
 * @author 云培训学院课题小组
 */
@Service
@Slf4j
public class LearnerSessionServiceImpl implements LearnerSessionService {

    /**
     * 内存态活跃会话（key=会话 ID）。服务重启后依赖 DB 中 active 记录做兜底失效。
     */
    private final Map<Long, ActiveSession> activeSessions = new ConcurrentHashMap<>();

    /**
     * 直播房间活跃观看人数（key=直播房间 ID）
     */
    private final Map<Long, Long> roomViewers = new ConcurrentHashMap<>();

    @Resource
    private LearnSessionMapper learnSessionMapper;
    @Resource
    private VerifyChallengeMapper verifyChallengeMapper;
    @Resource
    private DocReadLogMapper docReadLogMapper;
    @Resource
    private CheckinRecordMapper checkinRecordMapper;
    @Resource
    private LearnerTaskMapper learnerTaskMapper;
    @Resource
    private LiveRoomMapper liveRoomMapper;
    @Resource private cn.iocoder.txgy.module.training.service.live.LiveCenterService liveCenter;
    @Resource
    private CourseService courseService;
    @Resource
    private LearningConfigService configService;
    @Resource
    private LearnerCourseService learnerCourseService;
    @Resource
    private LearnerProgressCalculator progressCalculator;

    // ========== 内存态会话 ==========

    private static class ActiveSession {

        Long sessionId;
        Long tenantId;
        Long userId;
        Long courseId;
        Long taskId;
        String mediaType;
        Long mediaId;
        long startAt;
        long lastHeartbeatAt;
        int validSeconds;
        /** 是否正在等待人机验证（验证期间暂停计时） */
        boolean verifyPending;
        String challengeQuestion;
        String challengeAnswerHash;
        int challengeFailAttempts;
        /** 下次验证触发的有效秒阈值 */
        int nextVerifyAt;
        /** 是否已打卡 */
        boolean checkedIn;
        /** 文档会话：页号 → 本会话累计停留秒 */
        final Map<Integer, Integer> docPageStay = new HashMap<>();
        int docTotalPages;
        int docCoveredPages;

    }

    // ========== 会话生命周期 ==========

    @Override
    public LearnerSessionStartRespVO start(Long userId, LearnerSessionStartReqVO reqVO) {
        LearnMediaTypeEnum mediaTypeEnum = LearnMediaTypeEnum.getByType(reqVO.getMediaType());
        if (mediaTypeEnum == null) {
            throw exception(ErrorCodeConstants.LEARNER_COURSE_MEDIA_NOT_SUPPORT);
        }
        Long courseId = reqVO.getCourseId();
        Long mediaId = reqVO.getMediaId();

        LearnerTaskDO task = null;
        // 直播/回放：校验房间
        if (mediaTypeEnum == LearnMediaTypeEnum.LIVE || mediaTypeEnum == LearnMediaTypeEnum.REPLAY) {
            liveCenter.playback(mediaId, mediaTypeEnum == LearnMediaTypeEnum.REPLAY);
            /* Legacy mapper is tenant-scoped; visibility is checked above for public broadcasts. */
            LiveRoomDO room = cn.iocoder.txgy.framework.tenant.core.util.TenantUtils.executeIgnore(() -> liveRoomMapper.selectById(mediaId));
            if (room == null) {
                throw exception(ErrorCodeConstants.LEARNER_LIVE_ROOM_NOT_EXISTS);
            }
            if (mediaTypeEnum == LearnMediaTypeEnum.LIVE && !Objects.equals(room.getStatus(), 1)) {
                throw exception(ErrorCodeConstants.LEARNER_LIVE_NOT_STARTED);
            }
            if (mediaTypeEnum == LearnMediaTypeEnum.REPLAY
                    && !(Objects.equals(room.getStatus(), 2) && StrUtil.isNotBlank(room.getReplayVideoUrl()))) {
                throw exception(ErrorCodeConstants.LEARNER_LIVE_ENDED);
            }
            courseId = null;
        } else {
            // 视频/文档：必须课程已上架且已有学习任务
            if (courseId == null) {
                throw exception(ErrorCodeConstants.LEARNER_TASK_NOT_EXISTS);
            }
            CourseDO course = courseService.getCourse(courseId);
            if (course == null) {
                throw exception(ErrorCodeConstants.COURSE_NOT_EXISTS);
            }
            if (!Objects.equals(course.getStatus(), CourseStatusEnum.ON_SHELF.getStatus())) {
                throw exception(ErrorCodeConstants.LEARNER_COURSE_NOT_ONLINE);
            }
            task = learnerTaskMapper.selectByUserIdAndCourseId(userId, courseId);
            if (task == null) {
                throw exception(ErrorCodeConstants.LEARNER_TASK_NOT_EXISTS);
            }
            CourseMediaDO media = courseService.getMediaList(courseId).stream()
                    .filter(m -> Objects.equals(m.getId(), mediaId)).findFirst().orElse(null);
            if (media == null) {
                throw exception(ErrorCodeConstants.LEARNER_TASK_NOT_EXISTS);
            }
            int expect = mediaTypeEnum == LearnMediaTypeEnum.DOCUMENT
                    ? CourseMediaTypeEnum.DOCUMENT.getType() : CourseMediaTypeEnum.VIDEO.getType();
            if (!Objects.equals(media.getMediaType(), expect)) {
                throw exception(ErrorCodeConstants.LEARNER_COURSE_MEDIA_NOT_SUPPORT);
            }
        }

        // 同一学员仅允许一个进行中会话
        ActiveSession exist = activeSessions.values().stream()
                .filter(as -> Objects.equals(as.userId, userId) && Objects.equals(as.tenantId, cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder.getTenantId())).findFirst().orElse(null);
        if (exist != null) {
            throw exception(ErrorCodeConstants.LEARNER_SESSION_ACTIVE_EXISTS);
        }

        LearnSessionDO session = new LearnSessionDO();
        session.setUserId(userId);
        session.setCourseId(courseId);
        session.setTaskId(task == null ? null : task.getId());
        session.setMediaType(mediaTypeEnum.getType());
        session.setMediaId(mediaId);
        session.setStartAt(LocalDateTime.now());
        session.setValidSeconds(0);
        session.setStatus(LearnerSessionStatusEnum.ACTIVE.getStatus());
        learnSessionMapper.insert(session);

        // 任务状态校正为学习中
        if (task != null) {
            learnerCourseService.markTaskLearning(task);
        }

        int verifyIntervalSec = configService.getInt(LearningConfigService.KEY_VERIFY_INTERVAL_MIN, 1) * 60;
        ActiveSession as = new ActiveSession();
        as.sessionId = session.getId();
        as.userId = userId;
        as.tenantId = cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder.getTenantId();
        as.courseId = courseId;
        as.taskId = session.getTaskId();
        as.mediaType = mediaTypeEnum.getType();
        as.mediaId = mediaId;
        as.startAt = System.currentTimeMillis();
        as.lastHeartbeatAt = as.startAt;
        as.nextVerifyAt = Math.max(verifyIntervalSec, 60);
        activeSessions.put(as.sessionId, as);

        if (mediaTypeEnum == LearnMediaTypeEnum.LIVE) {
            roomViewers.merge(mediaId, 1L, Long::sum);
        }

        LearnerSessionStartRespVO resp = new LearnerSessionStartRespVO();
        resp.setSessionId(as.sessionId);
        resp.setStatus(LearnerSessionStatusEnum.ACTIVE.getStatus());
        resp.setMediaType(as.mediaType);
        resp.setCourseId(as.courseId);
        resp.setMediaId(as.mediaId);
        resp.setTaskId(as.taskId);
        resp.setHeartbeatIntervalSeconds(configService.getInt(LearningConfigService.KEY_HEARTBEAT_INTERVAL_SECONDS, 5));
        resp.setValidSeconds(0);
        log.info("[start][用户 {} 开始学习 {}/{}，会话 {}]", userId, as.mediaType, as.mediaId, as.sessionId);
        return resp;
    }

    @Override
    public LearnerSessionHeartbeatRespVO heartbeat(Long userId, Long sessionId,
                                                   Integer pageNo, Integer totalPages) {
        ActiveSession as = activeSessions.get(sessionId);
        if (as == null) {
            // 服务重启导致内存态丢失：DB active 记录直接判无效，提示重新开启
            LearnSessionDO db = learnSessionMapper.selectById(sessionId);
            if (db != null && Objects.equals(db.getUserId(), userId)
                    && LearnerSessionStatusEnum.ACTIVE.getStatus().equals(db.getStatus())) {
                doInvalidate(db, "restart", null);
            }
            throw exception(ErrorCodeConstants.LEARNER_SESSION_LOCKED);
        }
        if (!Objects.equals(as.userId, userId) || !Objects.equals(as.tenantId, cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder.getTenantId())) {
            throw exception(ErrorCodeConstants.LEARNER_SESSION_NOT_EXISTS);
        }
        synchronized (as) {
            if ("live".equals(as.mediaType) || "replay".equals(as.mediaType)) {
                try { liveCenter.playback(as.mediaId, "replay".equals(as.mediaType)); }
                catch (cn.iocoder.txgy.framework.common.exception.ServiceException e) {
                    doEnd(as, "access_revoked", null); throw e;
                }
            }
            LearnerSessionHeartbeatRespVO resp = new LearnerSessionHeartbeatRespVO();
            resp.setSessionId(sessionId);
            long now = System.currentTimeMillis();
            long delta = (now - as.lastHeartbeatAt) / 1000;

            int heartbeatInterval = configService.getInt(LearningConfigService.KEY_HEARTBEAT_INTERVAL_SECONDS, 5);
            int maxDelta = configService.getInt(LearningConfigService.KEY_MAX_DELTA_SECONDS, 15);
            int timeout = configService.getInt(LearningConfigService.KEY_SESSION_TIMEOUT_SECONDS, 30);

            // 1) 心跳过快（时钟回拨/异常重放）：判无效
            if (delta <= 0) {
                doInvalidate(as, "clock", resp);
                return resp;
            }
            // 2) 超过心跳超时：服务端自动结束，不计本次
            if (delta > timeout) {
                doEnd(as, "timeout", resp);
                return resp;
            }
            // 3) 验证期间：暂停计时
            if (as.verifyPending) {
                resp.setStatus(LearnerSessionStatusEnum.ACTIVE.getStatus());
                resp.setValidSeconds(as.validSeconds);
                resp.setNeedVerify(true);
                resp.setChallengeQuestion(as.challengeQuestion);
                resp.setCanCheckin(as.validSeconds >= checkinMinSeconds() && !as.checkedIn);
                fillDoc(as, resp);
                return resp;
            }
            // 4) 单次间隔超过 maxDelta：视为异常（切后台等），本次不计
            if (delta > maxDelta) {
                as.lastHeartbeatAt = now;
                resp.setStatus(LearnerSessionStatusEnum.ACTIVE.getStatus());
                resp.setValidSeconds(as.validSeconds);
                resp.setWarnInvalid(true);
                resp.setCanCheckin(as.validSeconds >= checkinMinSeconds() && !as.checkedIn);
                fillDoc(as, resp);
                return resp;
            }
            // 5) 正常心跳：累计有效秒
            int inc = (int) delta;
            as.validSeconds += inc;
            as.lastHeartbeatAt = now;
            LearnSessionDO progress = new LearnSessionDO();
            progress.setId(as.sessionId); progress.setValidSeconds(as.validSeconds);
            learnSessionMapper.updateById(progress);
            if (as.mediaType.equals(LearnMediaTypeEnum.DOCUMENT.getType())) {
                handleDocPage(as, pageNo, totalPages, inc);
            }
            // 6) 触发人机验证
            if (as.validSeconds >= as.nextVerifyAt && !as.verifyPending) {
                issueChallenge(as);
            }
            resp.setStatus(LearnerSessionStatusEnum.ACTIVE.getStatus());
            resp.setValidSeconds(as.validSeconds);
            resp.setNeedVerify(as.verifyPending);
            if (as.verifyPending) {
                resp.setChallengeQuestion(as.challengeQuestion);
            }
            resp.setCanCheckin(as.validSeconds >= checkinMinSeconds() && !as.checkedIn);
            fillDoc(as, resp);
            return resp;
        }
    }

    private void fillDoc(ActiveSession as, LearnerSessionHeartbeatRespVO resp) {
        if (as.mediaType.equals(LearnMediaTypeEnum.DOCUMENT.getType())) {
            resp.setDocCoveredPages(as.docCoveredPages);
            resp.setDocTotalPages(as.docTotalPages);
        }
    }

    /**
     * 文档会话：累计当前页停留，页覆盖判定，写阅读明细
     */
    private void handleDocPage(ActiveSession as, Integer pageNo, Integer totalPages, int staySeconds) {
        if (pageNo == null || pageNo <= 0) {
            return;
        }
        int docMinStay = configService.getInt(LearningConfigService.KEY_DOC_MIN_STAY_SECONDS, 5);
        int stay = as.docPageStay.merge(pageNo, staySeconds, Integer::sum);
        if (totalPages != null && totalPages > 0) {
            as.docTotalPages = Math.max(as.docTotalPages, totalPages);
        }
        if (stay >= docMinStay) {
            DocReadLogDO log = docReadLogMapper.selectOne(
                    new cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX<DocReadLogDO>()
                            .eq(DocReadLogDO::getSessionId, as.sessionId)
                            .eq(DocReadLogDO::getPageNo, pageNo).last("limit 1"));
            if (log == null) {
                DocReadLogDO insert = new DocReadLogDO();
                insert.setSessionId(as.sessionId);
                insert.setUserId(as.userId);
                insert.setCourseId(as.courseId);
                insert.setMediaId(as.mediaId);
                insert.setPageNo(pageNo);
                insert.setTotalPages(as.docTotalPages);
                insert.setStaySeconds(stay);
                insert.setReportAt(LocalDateTime.now());
                docReadLogMapper.insert(insert);
                as.docCoveredPages++;
            } else {
                DocReadLogDO update = new DocReadLogDO();
                update.setId(log.getId());
                update.setStaySeconds(stay);
                update.setTotalPages(as.docTotalPages);
                update.setReportAt(LocalDateTime.now());
                docReadLogMapper.updateById(update);
            }
        }
    }

    /**
     * 下发人机验证题（随机加减乘）
     */
    private void issueChallenge(ActiveSession as) {
        int a = RandomUtil.randomInt(3, 20);
        int b = RandomUtil.randomInt(3, 20);
        String op = RandomUtil.randomEle(new String[]{"+", "-", "*"});
        int result;
        if ("-".equals(op) && a < b) {
            int tmp = a;
            a = b;
            b = tmp;
        }
        result = switch (op) {
            case "-" -> a - b;
            case "*" -> a * b;
            default -> a + b;
        };
        String question = a + " " + op + " " + b + " = ?";
        String answerHash = DigestUtil.sha256Hex(String.valueOf(result));
        as.challengeQuestion = question;
        as.challengeAnswerHash = answerHash;
        as.challengeFailAttempts = 0;
        as.verifyPending = true;

        VerifyChallengeDO challenge = new VerifyChallengeDO();
        challenge.setSessionId(as.sessionId);
        challenge.setQuestion(question);
        challenge.setAnswerHash(answerHash);
        challenge.setIssuedAt(LocalDateTime.now());
        challenge.setAttempts(0);
        challenge.setPassed(false);
        verifyChallengeMapper.insert(challenge);
        log.info("[issueChallenge][会话 {} 下发验证题：{}]", as.sessionId, question);
    }

    @Override
    public void verify(Long userId, LearnerSessionVerifyReqVO reqVO) {
        ActiveSession as = activeSessions.get(reqVO.getSessionId());
        if (as == null || !Objects.equals(as.userId, userId)) {
            throw exception(ErrorCodeConstants.LEARNER_SESSION_NOT_EXISTS);
        }
        synchronized (as) {
            if (!as.verifyPending) {
                throw exception(ErrorCodeConstants.LEARNER_VERIFY_NOT_REQUIRED);
            }
            String answerHash = DigestUtil.sha256Hex(StrUtil.trim(reqVO.getAnswer()));
            if (!answerHash.equals(as.challengeAnswerHash)) {
                as.challengeFailAttempts++;
                updateChallenge(as, false);
                if (as.challengeFailAttempts >= 3) {
                    LearnerSessionHeartbeatRespVO resp = new LearnerSessionHeartbeatRespVO();
                    resp.setSessionId(as.sessionId);
                    doInvalidate(as, "cheat", resp);
                    throw exception(ErrorCodeConstants.LEARNER_VERIFY_FAILED_LIMIT);
                }
                throw exception(ErrorCodeConstants.LEARNER_VERIFY_ANSWER_WRONG);
            }
            // 验证通过：恢复计时
            as.verifyPending = false;
            as.challengeQuestion = null;
            as.challengeAnswerHash = null;
            int verifyIntervalSec = Math.max(
                    configService.getInt(LearningConfigService.KEY_VERIFY_INTERVAL_MIN, 1) * 60, 60);
            as.nextVerifyAt = as.validSeconds + verifyIntervalSec;
            updateChallenge(as, true);
            log.info("[verify][会话 {} 验证通过]", as.sessionId);
        }
    }

    private void updateChallenge(ActiveSession as, boolean passed) {
        List<VerifyChallengeDO> challenges = verifyChallengeMapper.selectListBySessionId(as.sessionId);
        VerifyChallengeDO latest = challenges.isEmpty() ? null : challenges.get(challenges.size() - 1);
        if (latest == null) {
            return;
        }
        VerifyChallengeDO update = new VerifyChallengeDO();
        update.setId(latest.getId());
        update.setAttempts((latest.getAttempts() == null ? 0 : latest.getAttempts()) + 1);
        update.setAnsweredAt(LocalDateTime.now());
        update.setPassed(passed);
        verifyChallengeMapper.updateById(update);
    }

    @Override
    public LearnerSessionEndRespVO end(Long userId, Long sessionId) {
        ActiveSession as = activeSessions.get(sessionId);
        if (as == null || !Objects.equals(as.userId, userId)) {
            throw exception(ErrorCodeConstants.LEARNER_SESSION_NOT_EXISTS);
        }
        synchronized (as) {
            LearnerSessionEndRespVO resp = new LearnerSessionEndRespVO();
            resp.setSessionId(sessionId);
            doEnd(as, "normal", null);
            return buildEndResp(resp, as, "normal");
        }
    }

    @Override
    public LearnerSessionEndRespVO checkin(Long userId, Long sessionId) {
        ActiveSession as = activeSessions.get(sessionId);
        if (as == null || !Objects.equals(as.userId, userId)) {
            throw exception(ErrorCodeConstants.LEARNER_SESSION_NOT_EXISTS);
        }
        synchronized (as) {
            if (as.checkedIn) {
                throw exception(ErrorCodeConstants.LEARNER_CHECKIN_NOT_ALLOW);
            }
            if (as.validSeconds < checkinMinSeconds()) {
                throw exception(ErrorCodeConstants.LEARNER_CHECKIN_NOT_ALLOW);
            }
            CheckinRecordDO record = new CheckinRecordDO();
            record.setSessionId(as.sessionId);
            record.setUserId(as.userId);
            record.setCourseId(as.courseId);
            record.setMediaId(as.mediaId);
            record.setMediaType(as.mediaType);
            record.setValidSecondsBefore(as.validSeconds);
            record.setCheckinAt(LocalDateTime.now());
            checkinRecordMapper.insert(record);
            as.checkedIn = true;
            log.info("[checkin][会话 {} 打卡成功，有效秒 {}]", as.sessionId, as.validSeconds);
            // 打卡即提交本次学习：结束会话并结算任务进度
            LearnerSessionEndRespVO resp = new LearnerSessionEndRespVO();
            doEnd(as, "checkin", null);
            return buildEndResp(resp, as, "checkin");
        }
    }

    private int checkinMinSeconds() {
        return configService.getInt(LearningConfigService.KEY_MIN_VALID_SECONDS_CHECKIN, 15);
    }

    // ========== 会话结算 ==========

    /**
     * 正常结束并结算（normal/timeout）
     */
    private void doEnd(ActiveSession as, String reason, LearnerSessionHeartbeatRespVO hbResp) {
        activeSessions.remove(as.sessionId);
        if (as.mediaType.equals(LearnMediaTypeEnum.LIVE.getType())) {
            roomViewers.computeIfPresent(as.mediaId, (k, v) -> v > 1 ? v - 1 : null);
        }
        LearnSessionDO update = new LearnSessionDO();
        update.setId(as.sessionId);
        update.setEndAt(LocalDateTime.now());
        update.setValidSeconds(as.validSeconds);
        update.setStatus(LearnerSessionStatusEnum.ENDED.getStatus());
        update.setEndReason(reason);
        learnSessionMapper.updateById(update);
        if (hbResp != null) {
            hbResp.setStatus(LearnerSessionStatusEnum.ENDED.getStatus());
            hbResp.setReason(reason);
            hbResp.setValidSeconds(as.validSeconds);
            return;
        }
        // 任务进度结算
        if (as.taskId != null) {
            refreshTask(as);
        }
    }

    /**
     * 异常结束（invalid：cheat/clock/restart），秒数清零不入学时
     */
    private void doInvalidate(ActiveSession as, String reason, LearnerSessionHeartbeatRespVO hbResp) {
        activeSessions.remove(as.sessionId);
        if (as.mediaType.equals(LearnMediaTypeEnum.LIVE.getType())) {
            roomViewers.computeIfPresent(as.mediaId, (k, v) -> v > 1 ? v - 1 : null);
        }
        LearnSessionDO update = new LearnSessionDO();
        update.setId(as.sessionId);
        update.setEndAt(LocalDateTime.now());
        update.setValidSeconds(as.validSeconds);
        update.setStatus(LearnerSessionStatusEnum.INVALID.getStatus());
        update.setEndReason(reason);
        learnSessionMapper.updateById(update);
        if (hbResp != null) {
            hbResp.setStatus(LearnerSessionStatusEnum.INVALID.getStatus());
            hbResp.setReason(reason);
            hbResp.setValidSeconds(as.validSeconds);
        }
    }

    private void doInvalidate(LearnSessionDO db, String reason, LearnerSessionHeartbeatRespVO hbResp) {
        LearnSessionDO update = new LearnSessionDO();
        update.setId(db.getId());
        update.setEndAt(LocalDateTime.now());
        update.setStatus(LearnerSessionStatusEnum.INVALID.getStatus());
        update.setEndReason(reason);
        learnSessionMapper.updateById(update);
        if (hbResp != null) {
            hbResp.setStatus(LearnerSessionStatusEnum.INVALID.getStatus());
            hbResp.setReason(reason);
            hbResp.setValidSeconds(db.getValidSeconds());
        }
    }

    /**
     * 会话结束后的任务进度结算：重算并落库
     */
    private void refreshTask(ActiveSession as) {
        LearnerTaskDO task = learnerTaskMapper.selectById(as.taskId);
        if (task == null) {
            return;
        }
        LearnerProgressCalculator.TaskProgress progress = progressCalculator.calc(as.userId, as.courseId, task);
        learnerCourseService.refreshTaskProgress(progress);
    }

    private LearnerSessionEndRespVO buildEndResp(LearnerSessionEndRespVO resp, ActiveSession as, String reason) {
        resp.setSessionId(as.sessionId);
        resp.setStatus(LearnerSessionStatusEnum.ENDED.getStatus());
        resp.setReason(reason);
        resp.setValidSeconds(as.validSeconds);
        resp.setCheckedIn(as.checkedIn);
        resp.setTaskId(as.taskId);
        resp.setCourseId(as.courseId);
        if (as.taskId != null) {
            LearnerTaskDO task = learnerTaskMapper.selectById(as.taskId);
            if (task != null) {
                LearnerProgressCalculator.TaskProgress progress = progressCalculator.calc(as.userId, as.courseId, task);
                LearnerProgressCalculator.MediaStat stat = progress.getMediaStats().stream()
                        .filter(s -> Objects.equals(s.getMediaId(), as.mediaId)).findFirst().orElse(null);
                resp.setMediaCompleted(stat != null && stat.getCompleted());
                resp.setTaskCompleted(progress.getAllCompleted());
                resp.setProgressPercent(BigDecimal.valueOf(
                        (progress.getRatioAvg() == null ? 0D : progress.getRatioAvg()) * 100)
                        .setScale(2, RoundingMode.HALF_UP));
                resp.setCreditTotal(progress.getCreditTotal());
                resp.setCreditEarned(progress.getCreditEarned());
                resp.setDoneCount(progress.getDoneCount());
                resp.setTotalCount(progress.getTotalCount());
            }
        }
        return resp;
    }

    @Override
    public Map<String, Object> getSessionConfig() {
        Map<String, Object> result = new HashMap<>();
        result.put(LearningConfigService.KEY_HEARTBEAT_INTERVAL_SECONDS,
                configService.getInt(LearningConfigService.KEY_HEARTBEAT_INTERVAL_SECONDS, 5));
        result.put(LearningConfigService.KEY_MAX_DELTA_SECONDS,
                configService.getInt(LearningConfigService.KEY_MAX_DELTA_SECONDS, 15));
        result.put(LearningConfigService.KEY_SESSION_TIMEOUT_SECONDS,
                configService.getInt(LearningConfigService.KEY_SESSION_TIMEOUT_SECONDS, 30));
        result.put(LearningConfigService.KEY_VERIFY_INTERVAL_MIN,
                configService.getInt(LearningConfigService.KEY_VERIFY_INTERVAL_MIN, 1));
        result.put(LearningConfigService.KEY_MIN_VALID_SECONDS_CHECKIN, checkinMinSeconds());
        return result;
    }

    @Override
    public long countActiveViewers(Long roomId) {
        return roomViewers.getOrDefault(roomId, 0L);
    }

}
