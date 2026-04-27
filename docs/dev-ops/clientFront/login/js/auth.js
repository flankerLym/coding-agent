(function () {
    const cfg = window.CodingAgentConfig || {
        TOKEN_KEY: "coding_agent_token",
        USER_KEY: "coding_agent_user",
        LOGIN_PAGE: "../login/login.html",
        INDEX_PAGE: "../chatIndex/index.html"
    };

    const TOKEN_KEY = cfg.TOKEN_KEY || "coding_agent_token";
    const USER_KEY = cfg.USER_KEY || "coding_agent_user";
    const LOGIN_PAGE = cfg.LOGIN_PAGE || "../login/login.html";
    const INDEX_PAGE = cfg.INDEX_PAGE || "../chatIndex/index.html";

    function currentPageName() {
        const path = window.location.pathname;
        return path.substring(path.lastIndexOf("/") + 1) || "index.html";
    }

    function isLoginPage() {
        return currentPageName() === "login.html";
    }

    function getToken() {
        return localStorage.getItem(TOKEN_KEY);
    }

    function getUser() {
        try {
            return JSON.parse(localStorage.getItem(USER_KEY) || "null");
        } catch (error) {
            return null;
        }
    }

    function clearAuth() {
        localStorage.removeItem(TOKEN_KEY);
        localStorage.removeItem(USER_KEY);
    }

    function buildRedirectLoginUrl() {
        const redirect = encodeURIComponent(window.location.pathname + (window.location.search || ""));
        return LOGIN_PAGE + "?redirect=" + redirect;
    }

    function redirectToLogin() {
        if (!isLoginPage()) {
            window.location.replace(buildRedirectLoginUrl());
        }
    }

    function redirectToIndexIfLoggedIn() {
        if (isLoginPage() && getToken()) {
            const params = new URLSearchParams(window.location.search);
            window.location.replace(params.get("redirect") || INDEX_PAGE);
        }
    }

    function shouldAttachToken(input) {
        const url = typeof input === "string" ? input : input && input.url;
        if (!url) return false;
        if (window.CodingAgentApi && typeof window.CodingAgentApi.isApiUrl === "function") {
            return window.CodingAgentApi.isApiUrl(url);
        }
        return String(url).startsWith("/api/") || String(url).includes("/api/");
    }

    function installFetchAuthHeader() {
        if (!window.fetch || window.__codingAgentFetchPatched) return;

        const rawFetch = window.fetch.bind(window);
        window.fetch = function (input, init) {
            const token = getToken();
            const options = init ? Object.assign({}, init) : {};

            if (token && shouldAttachToken(input)) {
                const headers = new Headers(options.headers || (input && input.headers) || {});
                if (!headers.has("Authorization")) {
                    headers.set("Authorization", "Bearer " + token);
                }
                options.headers = headers;
            }

            return rawFetch(input, options).then(function (response) {
                if (response.status === 401) {
                    clearAuth();
                    redirectToLogin();
                }
                return response;
            });
        };

        window.__codingAgentFetchPatched = true;
    }

    function renderUserEntry() {
        if (isLoginPage()) return;

        const headerActions = document.querySelector(".header-actions") || document.querySelector("nav");
        if (!headerActions || document.getElementById("authUserBox")) return;

        const user = getUser() || {};
        const name = user.nickname || user.username || "已登录";

        const box = document.createElement("div");
        box.id = "authUserBox";
        box.style.display = "flex";
        box.style.alignItems = "center";
        box.style.gap = "10px";
        box.style.marginLeft = "12px";
        box.innerHTML = `
            <span style="font-size:12px;color:#475569;">${name}</span>
            <button id="logoutBtn" style="height:32px;padding:0 12px;border:1px solid #e2e8f0;border-radius:10px;background:#fff;color:#475569;cursor:pointer;">退出</button>
        `;
        headerActions.appendChild(box);

        const logoutBtn = document.getElementById("logoutBtn");
        logoutBtn && logoutBtn.addEventListener("click", async function () {
            try {
                const logoutUrl = window.CodingAgentApi && cfg.LOGOUT_URL
                    ? window.CodingAgentApi.buildUrl(cfg.LOGOUT_URL)
                    : null;
                if (logoutUrl) {
                    await fetch(logoutUrl, { method: "POST" });
                }
            } catch (_) {
                // JWT 无状态退出，后端失败也允许前端清理。
            } finally {
                clearAuth();
                window.location.href = LOGIN_PAGE;
            }
        });
    }

    window.CodingAgentAuth = {
        getToken,
        getUser,
        clearAuth,
        redirectToLogin
    };

    installFetchAuthHeader();
    redirectToIndexIfLoggedIn();

    if (!isLoginPage() && !getToken()) {
        redirectToLogin();
        return;
    }

    if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", renderUserEntry);
    } else {
        renderUserEntry();
    }
})();
