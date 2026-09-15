from playwright.sync_api import sync_playwright


def main():
    with sync_playwright() as playwright:
        browser = playwright.chromium.launch(headless=True)
        page = browser.new_page()
        errors = []
        page.on("pageerror", lambda error: errors.append(str(error)))
        page.goto("http://localhost:5173/#/portal/home", wait_until="networkidle")
        print(f"url={page.url}")
        print(f"title={page.title()}")
        print(f"page_errors={len(errors)}")
        for error in errors:
            print(error)
        browser.close()
        if errors:
            raise SystemExit(1)


if __name__ == "__main__":
    main()
