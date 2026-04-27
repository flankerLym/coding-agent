// 前端请求配置
// 默认同源访问：适合 nginx 反向代理或直接由后端托管静态页的情况。
// 如果你本地前端和后端端口不同，比如后端是 8099，可以改成：API_BASE_URL: 'http://localhost:8099'
window.CHATGPT_AGENT_CONFIG = {
    API_BASE_URL: 'http://localhost:8099',
    AGENT_LIST_URL: '/api/v1/agent/query_available_agents',
    CHAT_URL: '/api/v1/agent/auto_agent',
    DEFAULT_MAX_STEP: 5,
    // 前端不再装配智能体。装配工作由后端启动时统一完成。
    FRONTEND_ARMORY_ENABLED: false
};
