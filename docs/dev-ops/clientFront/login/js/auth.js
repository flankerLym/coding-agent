(function () {
    const TOKEN_KEY = "coding_agent_token";
    const USER_KEY = "coding_agent_user";
    const LOGIN_PAGE = "login.html";
    const INDEX_PAGE = "index.html";

    function currentPageName() {
        const path = window.location.pathname;
        return path.substring(path.lastIndexOf("/") + 1) || INDEX_PAGE;
    }

    function isLoginPage() {
        return currentPageName() === LOGIN_PAGE;
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
        const page = currentPageName();
        const query = window.location.search || "";
        const redirect = encodeURIComponent(page + query);
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
        return url.startsWith("/api/") || url.includes("/api/");
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
        const nav = document.querySelector("nav .flex.items-center.gap-2:last-child") || document.querySelector("nav");
        if (!nav || document.getElementById("authUserBox")) return;

        const user = getUser() || {};
        const name = user.nickname || user.username || "已登录";

        const box = document.createElement("div");
        box.id = "authUserBox";
        box.className = "flex items-center gap-2 ml-2 pl-3 border-l border-gray-200";
        box.innerHTML = `
            <div class="hidden md:flex flex-col items-end leading-tight">
                <span class="text-xs font-medium text-gray-800">${name}</span>
                <span class="text-[10px] text-gray-400">${user.roleCode || user.roleCodes || "USER"}</span>
            </div>
            <button id="logoutBtn" class="px-3 py-2 text-sm font-medium text-gray-600 bg-white border border-gray-200 rounded-lg hover:bg-gray-50 transition-colors duration-200">退出</button>
        `;
        nav.appendChild(box);

        const logoutBtn = document.getElementById("logoutBtn");
        logoutBtn && logoutBtn.addEventListener("click", function () {
            clearAuth();
            window.location.href = LOGIN_PAGE;
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
