import cn.txgy.mall.PageLoadPolicy;

public final class PageLoadPolicyTest {
    public static void main(String[] args) {
        String page = "http://192.168.1.20:3000/#/pages/user/index";
        check(PageLoadPolicy.isMainDocumentSslError(page, page), "Main document SSL failure must be shown");
        check(!PageLoadPolicy.isMainDocumentSslError("https://images.example.com/product.jpg", page), "Failed product image must not close the page");
        check(!PageLoadPolicy.isMainDocumentSslError(null, page), "Missing resource URL must not close the page");
        check(!PageLoadPolicy.isMainDocumentSslError(page, null), "No active main document must not close the page");
        System.out.println("Page load failure policy passed: main document only.");
    }

    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
