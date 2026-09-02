package cn.iocoder.txgy.tool.pdftoimage.batch;

import cn.iocoder.txgy.tool.pdftoimage.api.BatchSummary;
import cn.iocoder.txgy.tool.pdftoimage.api.PageImage;
import cn.iocoder.txgy.tool.pdftoimage.api.PdfToImageOptions;
import cn.iocoder.txgy.tool.pdftoimage.core.PdfBoxPdfToImageRenderer;
import cn.iocoder.txgy.tool.pdftoimage.core.PdfToImageRenderer;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * 批量转换：目录（可递归）扫描 *.pdf → 并发逐文件转图 → 输出 report.json。
 * 单文件失败不影响整体；默认跳过已存在的输出（幂等）。
 */
public class BatchConverter {

    private final PdfToImageRenderer renderer;

    public BatchConverter() {
        this(new PdfBoxPdfToImageRenderer());
    }

    public BatchConverter(PdfToImageRenderer renderer) {
        this.renderer = renderer;
    }

    /**
     * @param input   单个 pdf 或目录
     * @param outDir  输出目录
     * @param options 转换选项
     */
    public BatchSummary convert(File input, File outDir, PdfToImageOptions options) throws IOException {
        if (outDir.exists() || outDir.mkdirs()) {
            // ok
        }
        BatchSummary summary = new BatchSummary(input, outDir);
        List<File> pdfs = collectPdfs(input, options.isRecursive());
        summary.setPdfFiles(pdfs.size());
        if (pdfs.isEmpty()) {
            writeReport(outDir, summary);
            return summary;
        }
        ExecutorService pool = Executors.newFixedThreadPool(options.getThreads());
        try {
            List<Future<Void>> futures = new ArrayList<>();
            for (File pdf : pdfs) {
                futures.add(pool.submit(new ConvertTask(pdf, outDir, options, summary)));
            }
            for (Future<Void> future : futures) {
                future.get(); // 失败已捕获进 summary，仅传播中断类异常
            }
        } catch (Exception e) {
            throw new IOException("批量转换执行异常: " + e.getMessage(), e);
        } finally {
            pool.shutdownNow();
        }
        writeReport(outDir, summary);
        return summary;
    }

    private List<File> collectPdfs(File input, boolean recursive) throws IOException {
        List<File> result = new ArrayList<>();
        if (input == null || !input.exists()) {
            return result;
        }
        if (input.isFile()) {
            if (isPdf(input)) {
                result.add(input);
            }
            return result;
        }
        Files.walk(input.toPath(), recursive ? Integer.MAX_VALUE : 1)
                .filter(p -> Files.isRegularFile(p) && isPdf(p.toFile()))
                .sorted()
                .forEach(p -> result.add(p.toFile()));
        return result;
    }

    private boolean isPdf(File f) {
        String name = f.getName().toLowerCase(Locale.ROOT);
        return name.endsWith(".pdf");
    }

    private void writeReport(File outDir, BatchSummary summary) throws IOException {
        Files.write(new File(outDir, "report.json").toPath(),
                summary.toJson().getBytes(StandardCharsets.UTF_8));
    }

    /** 单文件任务：异常不进线程外抛，而是记入 summary.failures */
    private class ConvertTask implements Callable<Void> {

        private final File pdf;
        private final File outDir;
        private final PdfToImageOptions options;
        private final BatchSummary summary;

        ConvertTask(File pdf, File outDir, PdfToImageOptions options, BatchSummary summary) {
            this.pdf = pdf;
            this.outDir = outDir;
            this.options = options;
            this.summary = summary;
        }

        @Override
        public Void call() {
            try {
                List<PageImage> pages = renderer.convert(pdf, outDir, options);
                for (PageImage page : pages) {
                    summary.addPage(page.getFile().getName(), page.getPageIndex(),
                            page.getWidth(), page.getHeight());
                }
                summary.setOkFiles(summary.getOkFiles() + 1);
                summary.setTotalPages(summary.getTotalPages() + pages.size());
            } catch (Throwable t) {
                summary.addFailure(pdf.getName(), String.valueOf(t.getMessage()));
                summary.setFailedFiles(summary.getFailedFiles() + 1);
            }
            return null;
        }
    }

}
