# llms.txt Generator Web Application

A lightweight Spring Boot web application that:

- Crawls a website
- Extracts key metadata (URL, title, description, content hash)
- Generates an up-to-date `llms.txt` file
- Automatically keeps each site monitored by re-crawling it on a schedule

> **Note:**
> - This demo intentionally does *not* respect `robots.txt`.
> - All state is in memory and resets on server restart.
> - After the first crawl, each site is automatically re-crawled every **N milliseconds** (configurable).

---

## **1. Features**

### **1.1 Website Analysis & Metadata Extraction**

- BFS crawler starting from a `baseUrl`
- Same-domain restriction
- Extracts:
  - URL
  - `<title>`
  - `<meta name="description">`
  - SHA-256 hash of visible text
- Crawler limits:
  - Max depth (default: 3)
  - Max pages (default: 200)
  - Timeout control
- Powered by **JSoup** (`CrawlService` / `CrawlServiceImpl`)

---

### **1.2 llms.txt File Generation**

Generates `llms.txt` for each crawled domain using the latest snapshot.

Example output:

llms.txt generated for https://example.com
Generated at 2025-11-30T12:34:56

URL: https://example.com/

TITLE: Home
DESCRIPTION: Welcome to our site.

URL: https://example.com/about

TITLE: About Us
DESCRIPTION: Learn more about us.


---

## **2. Live Deployment (Hosted Application)**

Deployed at:

👉 **https://llms-txt-generator-m77y.onrender.com/**

### **2.1 Trigger a crawl**
https://llms-txt-generator-m77y.onrender.com/api/crawl?baseUrl=https://example.com

### **2.2 Fetch the generated llms.txt**
https://llms-txt-generator-m77y.onrender.com/api/llms.txt?baseUrl=https://example.com


---

## **3. Tech Stack**

- Java 17
- Spring Boot
- Maven
- JSoup
- Dockerfile included
- In-memory datastore (no DB needed)

---

## **4. Running the Project Locally**

### **4.1 Clone**
```bash
git clone git@github.com:<your-username>/<repo-name>.git
cd <repo-name>
```
### **4.2 Build**
./mvnw clean package

### **4.3 Run**
./mvnw spring-boot:run

App starts at: http://localhost:8080

## **5. Docker Deployment**
Build 
docker build -t llms-txt-generator .

Run
docker run -p 8080:8080 llms-txt-generator

video

![7D268BA4-CA6F-474D-818C-973791A9B4E4_1_102_o](https://github.com/user-attachments/assets/17f9d7f9-b885-4eab-80a1-600d64cdf896)

