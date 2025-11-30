# llms.txt Generator Web Application

A lightweight Spring Boot web application that:

- Crawls a website
- Extracts key metadata (URL, title, description, content hash)
- Generates an `llms.txt` file
- Automatically keeps it up-to-date by re-crawling sites on a schedule
- This doesn't respect robots.txt
- State resets on restart
Once a site has been crawled **once**, it is automatically monitored and re-crawled every N milliseconds (configurable), without needing to be hard-coded in configuration.

---

## ✨ Features

### 1. Website Analysis & Content Extraction

- BFS-style crawler starting from a `baseUrl`
- Same-domain restriction (does not cross to other hosts)
- Extracts:
    - `URL`
    - `<title>`
    - `<meta name="description">`
    - SHA-256 hash of visible page text
- Limits:
    - Max depth (default: 3)
    - Max pages (default: 200)
    - Request timeout per page

Implementation: `CrawlService` / `CrawlServiceImpl` using JSoup.

---

### 2. llms.txt File Generation

- Uses the latest crawl snapshot for a given `baseUrl`
- Formats pages into a simple `llms.txt` format:

```text
# llms.txt generated for https://example.com
# Generated at 2025-11-30T12:34:56

URL: https://example.com/
TITLE: Home
DESCRIPTION: Welcome to our site.

URL: https://example.com/about
TITLE: About Us
DESCRIPTION: Learn more about us.
