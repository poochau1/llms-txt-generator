package com.profoundai.llms.util;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.WaitUntilState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Paths;

/**
 * Helper class for client-side rendering using Playwright.
 * Renders URLs in a headless Chromium browser and returns the hydrated DOM.
 */
public class CsrRenderer {

    private static final Logger log = LoggerFactory.getLogger(CsrRenderer.class);

    /**
     * Renders a URL in a headless Chromium browser, waits for full JS execution (NETWORKIDLE),
     * and returns the hydrated DOM as a String.
     *
     * @param url The URL to render
     * @return The hydrated DOM as a String, or null if rendering fails
     */
    public String renderClientSide(String url) {
        if (url == null || url.trim().isEmpty()) {
            log.warn("Invalid URL provided: {}", url);
            return null;
        }

        Playwright playwright = null;
        Browser browser = null;

        try {
            log.debug("Initializing Playwright for URL: {}", url);
            playwright = Playwright.create();
            
            log.debug("Launching headless Chromium browser");
            browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true).setExecutablePath(Paths.get("/ms-playwright/chromium-linux/chrome")));
            
            log.debug("Creating new page");
            Page page = browser.newPage();
            
            log.debug("Navigating to URL: {} and waiting for NETWORKIDLE", url);
            page.navigate(url, new Page.NavigateOptions().setWaitUntil(WaitUntilState.NETWORKIDLE));
            
            log.debug("Extracting page content");
            String content = page.content();
            
            log.debug("Successfully rendered URL: {}", url);
            return content;

        } catch (Exception e) {
            log.error("Failed to render URL: {}", url, e);
            return null;
        } finally {
            if (browser != null) {
                try {
                    browser.close();
                    log.debug("Browser closed");
                } catch (Exception e) {
                    log.warn("Error closing browser", e);
                }
            }
            if (playwright != null) {
                try {
                    playwright.close();
                    log.debug("Playwright closed");
                } catch (Exception e) {
                    log.warn("Error closing Playwright", e);
                }
            }
        }
    }
}

