package cn.iocoder.txgy.module.training.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 视频转码配置（application.yaml 的 training.video.transcode.*）
 *
 * @author 云培训学院课题小组
 */
@Data
@Component
@ConfigurationProperties(prefix = "training.video.transcode")
public class VideoTranscodeProperties {

    /**
     * 是否开启转码（关闭时上传后仅保留原始清晰度）
     */
    private Boolean enabled = true;

    /**
     * ffmpeg 可执行文件路径（容器内需预装 ffmpeg）
     */
    private String ffmpegPath = "ffmpeg";

    /**
     * 目标清晰度列表（默认 360P / 480P / 720P）
     */
    private List<String> targetDefinitions = new ArrayList<>(List.of("360P", "480P", "720P"));

    /**
     * 转码并发数（同时处理的任务数）
     */
    private Integer poolSize = 2;

    /**
     * 单任务超时时间（分钟）
     */
    private Integer timeoutMinutes = 180;

    /**
     * 转码输出目录（相对资源根目录，默认 video/transcode）
     */
    private String outputDir = "video/transcode";

    /**
     * 失败自动重试次数
     */
    private Integer maxRetry = 1;

    /**
     * H.264 编码速度档位（ultrafast ~ veryslow，越慢压缩率越高）
     */
    private String preset = "veryfast";

    /**
     * 各清晰度的 CRF 值（越大体积越小、画质越低）
     */
    private Integer crf360p = 28;
    private Integer crf480p = 26;
    private Integer crf720p = 24;
    private Integer crf1080p = 23;
    private Integer crf2160p = 22;

    /**
     * 各清晰度的音频码率（kbps）
     */
    private Integer audioBitrate360p = 64;
    private Integer audioBitrate480p = 96;
    private Integer audioBitrate720p = 128;
    private Integer audioBitrate1080p = 128;
    private Integer audioBitrate2160p = 160;

    public int crfOf(String definition) {
        if ("360P".equalsIgnoreCase(definition)) {
            return crf360p;
        }
        if ("480P".equalsIgnoreCase(definition)) {
            return crf480p;
        }
        if ("2160P".equalsIgnoreCase(definition)) {
            return crf2160p;
        }
        if ("1080P".equalsIgnoreCase(definition)) {
            return crf1080p;
        }
        return crf720p;
    }

    public int audioBitrateOf(String definition) {
        if ("360P".equalsIgnoreCase(definition)) {
            return audioBitrate360p;
        }
        if ("480P".equalsIgnoreCase(definition)) {
            return audioBitrate480p;
        }
        if ("2160P".equalsIgnoreCase(definition)) {
            return audioBitrate2160p;
        }
        if ("1080P".equalsIgnoreCase(definition)) {
            return audioBitrate1080p;
        }
        return audioBitrate720p;
    }
}
