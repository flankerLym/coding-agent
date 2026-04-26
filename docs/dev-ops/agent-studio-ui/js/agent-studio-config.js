// Agent Studio 全局配置
// 这里集中配置后端接口路径。部署在 nginx 下时，建议 nginx 把 /api/ 反向代理到 Spring Boot 8099。
window.AGENT_STUDIO_CONFIG = {
  // 必须：加载智能体选项。后端需要合并“拖拉拽智能体”和“ai_agent 内置智能体”。
  agentOptionsUrl: '/api/agent/options',

  // 聊天接口：优先使用 SSE；如果你的后端不是这个路径，只改这里即可。
  // SSE GET 示例：/api/agent/chat/stream?sessionId=xxx&message=xxx&agentId=xxx&agentCode=xxx&agentType=xxx&maxStep=2
  chatStreamUrl: '/api/agent/chat/stream',

  // POST 兜底接口：如果 SSE 不可用，会尝试 POST。
  chatPostUrl: '/api/agent/chat',

  // true：后端接口失败时显示内置兜底智能体，方便页面调试；正式环境也可保留。
  enableFallbackAgents: true,

  // 是否在控制台打印调试日志
  debug: true
};
