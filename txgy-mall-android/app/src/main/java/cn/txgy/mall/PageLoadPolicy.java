package cn.txgy.mall;

/** Keep a failed image request from being mistaken for a failed document. */
public final class PageLoadPolicy {
    private PageLoadPolicy() { }

    public static boolean isMainDocumentSslError(String failedUrl, String loadingPageUrl) {
        return failedUrl != null && loadingPageUrl != null && failedUrl.equals(loadingPageUrl);
    }
}
