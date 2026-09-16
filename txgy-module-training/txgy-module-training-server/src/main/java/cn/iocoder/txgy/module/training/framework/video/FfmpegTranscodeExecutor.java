package cn.iocoder.txgy.module.training.framework.video;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.txgy.module.training.config.VideoTranscodeProperties;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * FFmpeg 转码执行器
 *
 * <p>通过 ProcessBuilder 调用宿主机/容器内的 ffmpeg 二进制完成多清晰度转码，
 * 并以 {@code -progress pipe:1} 解析实时进度（out_time_us/out_time_ms）回写任务进度。</p>
 *
 * @author 云培训学院课题小组
 */
@Slf4j
@Component
public class FfmpegTranscodeExecutor {

    /**
     * ffmpeg 可用性检测结果的缓存时长（毫秒）
     */
    private static final long AVAILABLE_CACHE_MS = 60_000L;

    @Resource
    private VideoTranscodeProperties properties;

    private final AtomicLong lastCheckAt = new AtomicLong(0);
    private volatile boolean cachedAvailable = false;
    private volatile String cachedVersion = "";

    /**
     * ffmpeg 是否可用（结果缓存 1 分钟，避免频繁 fork 进程）
     */
    public boolean isAvailable() {
        long now = System.currentTimeMillis();
        if (now - lastCheckAt.get() < AVAILABLE_CACHE_MS) {
            return cachedAvailable;
        }
        lastCheckAt.set(now);
        try {
            Process process = new ProcessBuilder(properties.getFfmpegPath(), "-version")
                    .redirectErrorStream(true)
                    .start();
            StringBuilder out = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null && out.length() < 2000) {
                    out.append(line).append('\n');
                }
            }
            boolean ok = process.waitFor(30, TimeUnit.SECONDS) && process.exitValue() == 0;
            cachedAvailable = ok;
            cachedVersion = ok ? StrUtil.subBefore(out.toString().trim(), "\n", false) : "";
            if (!ok) {
                log.warn("[isAvailable][ffmpeg 不可用，path({})]", properties.getFfmpegPath());
            }
            return ok;
        } catch (Exception e) {
            cachedAvailable = false;
            cachedVersion = "";
            log.warn("[isAvailable][ffmpeg 检测失败，path({})]", properties.getFfmpegPath(), e);
            return false;
        }
    }

    /**
     * ffmpeg 版本信息（不可用返回空串）
     */
    public String version() {
        isAvailable();
        return cachedVersion;
    }

    /**
     * 执行转码
     *
     * @param source       源文件
     * @param target       输出文件
     * @param definition   目标清晰度（360P/480P/720P...）
     * @param height       目标画面高度
     * @param totalSeconds 源视频总时长（用于换算进度，未知传 0）
     * @param progress     进度回调（0-100）
     * @return 转码结果
     */
    public TranscodeResult transcode(File source, File target, String definition, int height,
                                     long totalSeconds, Consumer<Integer> progress) {
        List<String> command = buildCommand(source, target, height, definition);
        log.info("[transcode][开始转码 definition({}) height({}) source({}) target({})]",
                definition, height, source.getAbsolutePath(), target.getAbsolutePath());
        Process process;
        try {
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.redirectErrorStream(false);
            process = builder.start();
        } catch (Exception e) {
            return TranscodeResult.fail(String.join(" ", command), "启动 ffmpeg 失败：" + e.getMessage());
        }

        // 解析进度（-progress pipe:1 输出到 stdout）
        Thread progressThread = new Thread(() -> readProgress(process, totalSeconds, progress),
                "ffmpeg-progress-" + definition);
        progressThread.setDaemon(true);
        progressThread.start();
        // 收集错误输出，避免管道堵塞
        StringBuilder error = new StringBuilder();
        Thread errorThread = new Thread(() -> readError(process, error), "ffmpeg-error-" + definition);
        errorThread.setDaemon(true);
        errorThread.start();

        try {
            boolean finished = process.waitFor(properties.getTimeoutMinutes(), TimeUnit.MINUTES);
            if (!finished) {
                process.destroyForcibly();
                return TranscodeResult.fail(String.join(" ", command),
                        "转码超时（超过 " + properties.getTimeoutMinutes() + " 分钟）");
            }
            progressThread.join(5_000);
            errorThread.join(5_000);
            if (process.exitValue() != 0) {
                String msg = StrUtil.blankToDefault(error.toString().trim(),
                        "ffmpeg 退出码 " + process.exitValue());
                return TranscodeResult.fail(String.join(" ", command),
                        msg.length() > 500 ? msg.substring(0, 500) : msg);
            }
            if (target == null || !target.exists() || target.length() == 0) {
                return TranscodeResult.fail(String.join(" ", command), "转码输出文件为空");
            }
            return TranscodeResult.success(String.join(" ", command), target.length());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            return TranscodeResult.fail(String.join(" ", command), "转码被中断");
        } catch (Exception e) {
            process.destroyForcibly();
            return TranscodeResult.fail(String.join(" ", command), "转码异常：" + e.getMessage());
        }
    }

    private List<String> buildCommand(File source, File target, int height, String definition) {
        List<String> command = new ArrayList<>();
        command.add(properties.getFfmpegPath());
        command.add("-y");
        command.add("-hide_banner");
        command.add("-loglevel");
        command.add("error");
        command.add("-i");
        command.add(source.getAbsolutePath());
        command.add("-vf");
        command.add("scale=-2:" + height);
        command.add("-c:v");
        command.add("libx264");
        command.add("-preset");
        command.add(StrUtil.blankToDefault(properties.getPreset(), "veryfast"));
        command.add("-crf");
        command.add(String.valueOf(properties.crfOf(definition)));
        command.add("-pix_fmt");
        command.add("yuv420p");
        command.add("-c:a");
        command.add("aac");
        command.add("-b:a");
        command.add(properties.audioBitrateOf(definition) + "k");
        command.add("-movflags");
        command.add("+faststart");
        command.add("-progress");
        command.add("pipe:1");
        command.add("-nostats");
        command.add(target.getAbsolutePath());
        return command;
    }

    private void readProgress(Process process, long totalSeconds, Consumer<Integer> progress) {
        if (progress == null) {
            return;
        }
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            int last = -1;
            while ((line = reader.readLine()) != null) {
                Long micros = parseTimeMicros(line);
                if (micros == null || totalSeconds <= 0) {
                    continue;
                }
                int percent = (int) Math.min(99, micros / 1_000_000 * 100 / totalSeconds);
                if (percent > last) {
                    last = percent;
                    progress.accept(percent);
                }
            }
        } catch (Exception ignored) {
            // 进程被中断时忽略
        }
    }

    /**
     * 解析 ffmpeg -progress 输出中的时间字段（微秒）
     */
    private Long parseTimeMicros(String line) {
        if (line.startsWith("out_time_us=")) {
            return parseLong(line.substring("out_time_us=".length()));
        }
        if (line.startsWith("out_time_ms=")) {
            Long millis = parseLong(line.substring("out_time_ms=".length()));
            return millis == null ? null : millis * 1000;
        }
        return null;
    }

    private Long parseLong(String value) {
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void readError(Process process, StringBuilder error) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null && error.length() < 4000) {
                error.append(line).append('\n');
            }
        } catch (Exception ignored) {
            // ignore
        }
    }

    /**
     * 转码执行结果
     */
    public static class TranscodeResult {

        private final boolean success;
        private final String command;
        private final Long fileSize;
        private final String errorMsg;

        private TranscodeResult(boolean success, String command, Long fileSize, String errorMsg) {
            this.success = success;
            this.command = command;
            this.fileSize = fileSize;
            this.errorMsg = errorMsg;
        }

        public static TranscodeResult success(String command, long fileSize) {
            return new TranscodeResult(true, command, fileSize, null);
        }

        public static TranscodeResult fail(String command, String errorMsg) {
            return new TranscodeResult(false, command, null, errorMsg);
        }

        public boolean isSuccess() {
            return success;
        }

        public String getCommand() {
            return command;
        }

        public Long getFileSize() {
            return fileSize;
        }

        public String getErrorMsg() {
            return errorMsg;
        }
    }
}
