package cn.iocoder.txgy.tool.pdftoimage.cli;

import cn.iocoder.txgy.tool.pdftoimage.api.BatchSummary;
import cn.iocoder.txgy.tool.pdftoimage.api.PdfToImageOptions;
import cn.iocoder.txgy.tool.pdftoimage.batch.BatchConverter;

import java.io.File;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 命令行批量转图入口。
 *
 * <pre>
 *  用法:
 *    java -cp "target/classes;target/lib/*" cn.iocoder.txgy.tool.pdftoimage.cli.PdfToImageCli \
 *        -i 输入pdf或目录 -o 输出目录 [选项]
 *  常用选项:
 *    --dpi 200            渲染DPI（默认200）
 *    --format png|jpg     输出格式（默认png）
 *    --quality 0.9        JPG质量0.1~1.0（默认0.9）
 *    --max-width 2480     最大输出宽px，0=不限（默认2480）
 *    --max-height 3508    最大输出高px，0=不限（默认3508）
 *    --no-recursive       目录不递归
 *    --threads 2          并发线程数（默认2）
 *    --overwrite          覆盖已存在输出（默认跳过）
 *  退出码: 0=全部成功或部分失败(见report.json) 2=用法错误
 * </pre>
 */
public final class PdfToImageCli {

    private PdfToImageCli() {
    }

    public static void main(String[] args) {
        quietPdfboxLogging();
        Args parsed;
        try {
            parsed = new Args(args);
        } catch (IllegalArgumentException e) {
            System.err.println("参数错误: " + e.getMessage());
            System.err.println("用法: -i <pdf或目录> -o <输出目录> [--dpi 200] [--format png|jpg] "
                    + "[--quality 0.9] [--max-width 2480] [--max-height 3508] "
                    + "[--no-recursive] [--threads 2] [--overwrite]");
            System.exit(2);
            return;
        }
        try {
            PdfToImageOptions options = parsed.toOptions();
            BatchSummary summary = new BatchConverter().convert(parsed.input, parsed.output, options);
            System.out.println("=== 转换完成 ===");
            System.out.println("PDF文件数: " + summary.getPdfFiles()
                    + " | 成功: " + summary.getOkFiles()
                    + " | 失败: " + summary.getFailedFiles()
                    + " | 生成图片页数: " + summary.getTotalPages());
            System.out.println("报告: " + new File(parsed.output, "report.json").getAbsolutePath());
            System.out.println(summary.toJson());
            System.exit(summary.isAllOk() ? 0 : 1);
        } catch (Exception e) {
            System.err.println("转换失败: " + e.getMessage());
            System.exit(1);
        }
    }

    /** CLI 只关心结果，把 PDFBox/FontBox 的字体扫描等 JUL 日志压到 SEVERE，避免刷屏（库使用者不受影响） */
    private static void quietPdfboxLogging() {
        Logger.getLogger("org.apache.pdfbox").setLevel(Level.SEVERE);
        Logger.getLogger("org.apache.fontbox").setLevel(Level.SEVERE);
    }

    /** 极简参数解析 */
    private static class Args {
        File input;
        File output;
        int dpi = PdfToImageOptions.DEFAULT_DPI;
        String format = PdfToImageOptions.FORMAT_PNG;
        double quality = PdfToImageOptions.DEFAULT_JPEG_QUALITY;
        int maxWidth = PdfToImageOptions.DEFAULT_MAX_WIDTH;
        int maxHeight = PdfToImageOptions.DEFAULT_MAX_HEIGHT;
        boolean recursive = true;
        int threads = 2;
        boolean overwrite = false;

        Args(String[] args) {
            for (int i = 0; i < args.length; i++) {
                String a = args[i];
                if ("-i".equals(a) || "--input".equals(a)) {
                    input = new File(require(args, ++i, a));
                } else if ("-o".equals(a) || "--output".equals(a)) {
                    output = new File(require(args, ++i, a));
                } else if ("--dpi".equals(a)) {
                    dpi = Integer.parseInt(require(args, ++i, a));
                } else if ("--format".equals(a)) {
                    format = require(args, ++i, a);
                } else if ("--quality".equals(a)) {
                    quality = Double.parseDouble(require(args, ++i, a));
                } else if ("--max-width".equals(a)) {
                    maxWidth = Integer.parseInt(require(args, ++i, a));
                } else if ("--max-height".equals(a)) {
                    maxHeight = Integer.parseInt(require(args, ++i, a));
                } else if ("--no-recursive".equals(a)) {
                    recursive = false;
                } else if ("--threads".equals(a)) {
                    threads = Integer.parseInt(require(args, ++i, a));
                } else if ("--overwrite".equals(a)) {
                    overwrite = true;
                } else {
                    throw new IllegalArgumentException("未知参数: " + a);
                }
            }
            if (input == null) {
                throw new IllegalArgumentException("缺少 -i 输入");
            }
            if (output == null) {
                throw new IllegalArgumentException("缺少 -o 输出");
            }
        }

        private static String require(String[] args, int i, String flag) {
            if (i >= args.length) {
                throw new IllegalArgumentException(flag + " 缺少取值");
            }
            return args[i];
        }

        PdfToImageOptions toOptions() {
            return new PdfToImageOptions()
                    .setDpi(dpi)
                    .setFormat(format)
                    .setJpegQuality(quality)
                    .setMaxWidth(maxWidth)
                    .setMaxHeight(maxHeight)
                    .setRecursive(recursive)
                    .setThreads(threads)
                    .setOverwrite(overwrite);
        }
    }

}
