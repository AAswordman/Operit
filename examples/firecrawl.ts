/* METADATA
{
    "name": "firecrawl",

    "display_name": {
        "zh": "Firecrawl 搜索与网页提取",
        "en": "Firecrawl Search & Scrape"
    },
    "description": {
        "zh": "使用 Firecrawl 搜索网页，并把网页（包括 PDF 和需要 JS 渲染的页面）转换为干净的 Markdown 供智能体使用。",
        "en": "Search the web with Firecrawl and turn pages, including PDFs and JS-rendered pages, into clean markdown for the agent."
    },
    "env": [
        {
            "name": "FIRECRAWL_API_KEY",
            "description": {
                "zh": "Firecrawl API Key（可选）。使用官方 API 时，search 和 scrape 无需 Key 即可使用，但用量有限；map 需要 Key。获取地址：https://www.firecrawl.dev/app/api-keys?utm_source=operit&utm_medium=integration",
                "en": "Firecrawl API key (optional). On the hosted API, search and scrape work without a key with a small usage limit; map needs a key. Get a key: https://www.firecrawl.dev/app/api-keys?utm_source=operit&utm_medium=integration"
            },
            "required": false
        },
        {
            "name": "FIRECRAWL_API_URL",
            "description": {
                "zh": "Firecrawl API 地址（可选，用于自托管 Firecrawl；官方地址为 https://api.firecrawl.dev）",
                "en": "Firecrawl API URL (optional, for self-hosted Firecrawl; the hosted API is https://api.firecrawl.dev)"
            },
            "required": false
        }
    ],
    "category": "Search",
    "tools": [
        {
            "name": "search",
            "description": { "zh": "使用 Firecrawl 搜索网页或新闻，可选同时返回每个结果页面的 Markdown 内容。", "en": "Search the web or news with Firecrawl, optionally returning each result page as markdown." },
            "parameters": [
                { "name": "query", "description": { "zh": "搜索查询", "en": "Search query" }, "type": "string", "required": true },
                { "name": "limit", "description": { "zh": "返回的最大结果数", "en": "Maximum number of results" }, "type": "number", "required": false, "default": 5 },
                { "name": "sources", "description": { "zh": "搜索来源，可以是 'web' 或 'news'", "en": "Search source: 'web' or 'news'" }, "type": "string", "required": false, "default": "web" },
                { "name": "country", "description": { "zh": "用于地区定向的 ISO 国家代码，例如 'US'、'CN'", "en": "ISO country code for geo-targeting, e.g. 'US', 'CN'" }, "type": "string", "required": false },
                { "name": "tbs", "description": { "zh": "时间范围，例如 'qdr:d'（过去一天）、'qdr:w'（过去一周）、'qdr:m'（过去一月）", "en": "Time range, e.g. 'qdr:d' (past day), 'qdr:w' (past week), 'qdr:m' (past month)" }, "type": "string", "required": false },
                { "name": "include_domains", "description": { "zh": "只返回这些域名的结果（不能与 exclude_domains 同时使用）", "en": "Only return results from these domains (cannot be used with exclude_domains)" }, "type": "array", "required": false },
                { "name": "exclude_domains", "description": { "zh": "排除这些域名的结果（不能与 include_domains 同时使用）", "en": "Exclude results from these domains (cannot be used with include_domains)" }, "type": "array", "required": false },
                { "name": "scrape_results", "description": { "zh": "同时抓取每个结果页面并返回 Markdown 内容", "en": "Also scrape each result page and return its markdown" }, "type": "boolean", "required": false, "default": false },
                { "name": "max_chars", "description": { "zh": "scrape_results 开启时，每个结果 Markdown 的最大字符数", "en": "Maximum markdown characters per result when scrape_results is on" }, "type": "number", "required": false, "default": 4000 }
            ]
        },
        {
            "name": "scrape",
            "description": { "zh": "把任意 URL（包括 PDF 和需要 JS 渲染的页面）转换为干净的 Markdown。", "en": "Turn any URL, including PDFs and JS-rendered pages, into clean markdown." },
            "parameters": [
                { "name": "url", "description": { "zh": "要提取内容的 URL", "en": "URL to scrape" }, "type": "string", "required": true },
                { "name": "only_main_content", "description": { "zh": "只返回正文内容，去掉导航、页眉和页脚", "en": "Return only the main content, without navigation, headers and footers" }, "type": "boolean", "required": false, "default": true },
                { "name": "max_chars", "description": { "zh": "返回 Markdown 的最大字符数", "en": "Maximum markdown characters to return" }, "type": "number", "required": false, "default": 20000 }
            ]
        },
        {
            "name": "map",
            "description": { "zh": "列出一个网站的 URL，可按关键词排序。使用官方 API 时需要 FIRECRAWL_API_KEY。", "en": "List the URLs of a website, optionally ordered by a search term. Needs FIRECRAWL_API_KEY on the hosted API." },
            "parameters": [
                { "name": "url", "description": { "zh": "要映射的网站根 URL", "en": "Root URL of the website to map" }, "type": "string", "required": true },
                { "name": "search", "description": { "zh": "按与该关键词的相关度排序 URL", "en": "Order URLs by relevance to this term" }, "type": "string", "required": false },
                { "name": "limit", "description": { "zh": "返回的最大 URL 数", "en": "Maximum number of URLs to return" }, "type": "number", "required": false, "default": 100 },
                { "name": "include_subdomains", "description": { "zh": "是否包含子域名的 URL（默认包含）", "en": "Whether to include URLs from subdomains (included by default)" }, "type": "boolean", "required": false, "default": true }
            ]
        }
    ]
}*/

const firecrawl = (function () {
    const DEFAULT_API_URL = 'https://api.firecrawl.dev';
    const API_KEY_URL = 'https://www.firecrawl.dev/app/api-keys?utm_source=operit&utm_medium=integration';
    const ORIGIN = 'operit';
    // Firecrawl scrapes can take longer than the default 30s read timeout, especially with scrape_results on.
    const TIMEOUT = 90000;
    const client = OkHttp.newBuilder()
        .connectTimeout(TIMEOUT)
        .readTimeout(TIMEOUT)
        .writeTimeout(TIMEOUT)
        .build();

    type SearchParams = {
        query: string;
        limit?: number;
        sources?: string;
        country?: string;
        tbs?: string;
        include_domains?: string[];
        exclude_domains?: string[];
        scrape_results?: boolean;
        max_chars?: number;
    };

    type ScrapeParams = {
        url: string;
        only_main_content?: boolean;
        max_chars?: number;
    };

    type MapParams = {
        url: string;
        search?: string;
        limit?: number;
        include_subdomains?: boolean;
    };

    type ScrapeOptions = { formats: string[]; onlyMainContent: boolean };

    type SearchRequest = {
        query: string;
        limit: number;
        sources: string[];
        country?: string;
        tbs?: string;
        includeDomains?: string[];
        excludeDomains?: string[];
        scrapeOptions?: ScrapeOptions;
    };

    type ScrapeRequest = { url: string } & ScrapeOptions;

    type MapRequest = {
        url: string;
        limit: number;
        search?: string;
        includeSubdomains?: boolean;
    };

    type WebResult = { url: string; title?: string; description?: string; markdown?: string };
    type NewsResult = { url: string; title?: string; snippet?: string; date?: string; markdown?: string };
    type SearchResponse = { data: { web?: WebResult[]; news?: NewsResult[] } };
    type ScrapeResponse = { data: { markdown?: string; metadata: { title?: string; sourceURL: string; statusCode?: number } } };
    type MapResponse = { links: Array<{ url: string; title?: string; description?: string }> };

    function getApiKey(): string {
        return (getEnv("FIRECRAWL_API_KEY") ?? "").trim();
    }

    function getApiUrl(): string {
        const configured = (getEnv("FIRECRAWL_API_URL") ?? "").trim();
        const base = configured === "" ? DEFAULT_API_URL : configured;
        return base.replace(/\/+$/, "");
    }

    function errorHint(statusCode: number, hasKey: boolean): string {
        if (statusCode === 401 && !hasKey) {
            return `This tool needs FIRECRAWL_API_KEY. Get a key: ${API_KEY_URL}`;
        }
        if (statusCode === 401) {
            return `FIRECRAWL_API_KEY was rejected. Check the key: ${API_KEY_URL}`;
        }
        if (statusCode === 402) {
            return `Firecrawl credits are used up. Check the account: ${API_KEY_URL}`;
        }
        if (statusCode === 429 && !hasKey) {
            return `The usage limit for requests without a key is reached. Set FIRECRAWL_API_KEY to continue: ${API_KEY_URL}`;
        }
        if (statusCode === 429) {
            return "Rate limited by Firecrawl. Try again shortly.";
        }
        return "";
    }

    async function makeFirecrawlRequest<TReq, TRes>(path: string, params: TReq): Promise<TRes> {
        const apiKey = getApiKey();
        const requestBody = { ...params, origin: ORIGIN };

        const headers: Record<string, string> = {
            'accept': 'application/json',
            'content-type': 'application/json'
        };
        // The hosted API serves search and scrape without a key under a small usage limit, so the header is only sent when a key is set.
        if (apiKey !== "") {
            headers['Authorization'] = `Bearer ${apiKey}`;
        }

        try {
            const request = client.newRequest()
                .url(`${getApiUrl()}${path}`)
                .method('POST')
                .headers(headers)
                .body(JSON.stringify(requestBody), 'json');

            const response = await request.build().execute();

            if (!response.isSuccessful()) {
                const hint = errorHint(response.statusCode, apiKey !== "");
                const message = `Firecrawl API Error: ${response.statusCode} - ${response.content}`;
                throw new Error(hint === "" ? message : `${message}\n${hint}`);
            }
            return JSON.parse(response.content) as TRes;
        } catch (error: any) {
            console.error(`Firecrawl request failed: ${error.message}`);
            throw error;
        }
    }

    function truncate(text: string, maxChars: number): string {
        if (text.length <= maxChars) {
            return text;
        }
        return `${text.substring(0, maxChars)}\n...[truncated ${text.length - maxChars} characters]`;
    }

    function formatSearchResults(response: SearchResponse, maxChars: number): string {
        const web = response.data.web ?? [];
        const news = response.data.news ?? [];
        if (web.length === 0 && news.length === 0) {
            return 'No results.';
        }
        const output: string[] = [];
        output.push('Detailed Results:');
        web.forEach((result) => {
            output.push('');
            if (result.title) {
                output.push(`Title: ${result.title}`);
            }
            output.push(`URL: ${result.url}`);
            if (result.description) {
                output.push(`Content: ${result.description}`);
            }
            if (result.markdown) {
                output.push(`Markdown: ${truncate(result.markdown, maxChars)}`);
            }
        });
        news.forEach((result) => {
            output.push('');
            if (result.title) {
                output.push(`Title: ${result.title}`);
            }
            output.push(`URL: ${result.url}`);
            if (result.date) {
                output.push(`Date: ${result.date}`);
            }
            if (result.snippet) {
                output.push(`Content: ${result.snippet}`);
            }
            if (result.markdown) {
                output.push(`Markdown: ${truncate(result.markdown, maxChars)}`);
            }
        });
        return output.join('\n');
    }

    function formatScrapeResult(response: ScrapeResponse, maxChars: number): string {
        const metadata = response.data.metadata;
        const markdown = response.data.markdown;
        if (markdown === undefined) {
            throw new Error(`Firecrawl returned no markdown for ${metadata.sourceURL}`);
        }
        const output: string[] = [];
        if (metadata.title) {
            output.push(`Title: ${metadata.title}`);
        }
        output.push(`URL: ${metadata.sourceURL}`);
        // A 404 or 500 page still comes back as markdown, so the status tells the agent the content is an error page.
        if (metadata.statusCode !== undefined && metadata.statusCode >= 400) {
            output.push(`Status: ${metadata.statusCode}`);
        }
        output.push('\nMarkdown:');
        output.push(truncate(markdown, maxChars));
        return output.join('\n');
    }

    function formatMapResults(response: MapResponse, url: string): string {
        const output: string[] = [];
        output.push(`Site Map Results:`);
        output.push(`Base URL: ${url}`);
        output.push('\nMapped Pages:');
        response.links.forEach((link, index) => {
            output.push(`\n[${index + 1}] URL: ${link.url}`);
            if (link.title) {
                output.push(`Title: ${link.title}`);
            }
            if (link.description) {
                output.push(`Description: ${link.description}`);
            }
        });
        return output.join('\n');
    }

    async function search(params: SearchParams) {
        const source = params.sources ?? "web";
        if (source !== "web" && source !== "news") {
            throw new Error(`sources must be 'web' or 'news', got '${source}'`);
        }
        const includeDomains = params.include_domains ?? [];
        const excludeDomains = params.exclude_domains ?? [];
        if (includeDomains.length > 0 && excludeDomains.length > 0) {
            throw new Error("include_domains and exclude_domains cannot be used together");
        }

        const body: SearchRequest = {
            query: params.query,
            limit: params.limit ?? 5,
            sources: [source]
        };
        if (params.country) {
            body.country = params.country;
        }
        if (params.tbs) {
            body.tbs = params.tbs;
        }
        if (includeDomains.length > 0) {
            body.includeDomains = includeDomains;
        }
        if (excludeDomains.length > 0) {
            body.excludeDomains = excludeDomains;
        }
        if (params.scrape_results === true) {
            body.scrapeOptions = { formats: ["markdown"], onlyMainContent: true };
        }

        const response = await makeFirecrawlRequest<SearchRequest, SearchResponse>('/v2/search', body);
        return formatSearchResults(response, params.max_chars ?? 4000);
    }

    async function scrape(params: ScrapeParams) {
        const body: ScrapeRequest = {
            url: params.url,
            formats: ["markdown"],
            onlyMainContent: params.only_main_content ?? true
        };
        const response = await makeFirecrawlRequest<ScrapeRequest, ScrapeResponse>('/v2/scrape', body);
        return formatScrapeResult(response, params.max_chars ?? 20000);
    }

    async function map(params: MapParams) {
        const body: MapRequest = {
            url: params.url,
            limit: params.limit ?? 100
        };
        if (params.search) {
            body.search = params.search;
        }
        if (params.include_subdomains !== undefined) {
            body.includeSubdomains = params.include_subdomains;
        }
        const response = await makeFirecrawlRequest<MapRequest, MapResponse>('/v2/map', body);
        return formatMapResults(response, params.url);
    }

    async function wrap<T>(func: (params: T) => Promise<string>, params: T, successMessage: string, failMessage: string) {
        try {
            const result = await func(params);
            complete({ success: true, message: successMessage, data: result });
        } catch (error: any) {
            console.error(`Function ${func.name} failed! Error: ${error.message}`);
            complete({ success: false, message: `${failMessage}: ${error.message}`, error_stack: error.stack });
        }
    }

    return {
        search: (p: SearchParams) => wrap(search, p, '搜索成功', '搜索失败'),
        scrape: (p: ScrapeParams) => wrap(scrape, p, '提取成功', '提取失败'),
        map: (p: MapParams) => wrap(map, p, '映射成功', '映射失败')
    };
})();

exports.search = firecrawl.search;
exports.scrape = firecrawl.scrape;
exports.map = firecrawl.map;
