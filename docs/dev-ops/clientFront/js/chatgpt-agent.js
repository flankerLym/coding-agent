(function () {
    const cfg = window.CHATGPT_AGENT_CONFIG || {};

    const state = {
        sessionId: '',
        selectedAgentId: '',
        selectedAgentName: '',
        selectedAgentMeta: null,
        maxStep: cfg.DEFAULT_MAX_STEP || 5,
        running: false,
        histories: loadJson('chatgpt_agent_histories', [])
    };

    const dom = {
        sidebar: document.getElementById('sidebar'),
        mobileSidebarBtn: document.getElementById('mobileSidebarBtn'),
        newChatBtn: document.getElementById('newChatBtn'),
        clearAllChatsBtn: document.getElementById('clearAllChatsBtn'),
        chatList: document.getElementById('chatList'),
        agentSelector: document.getElementById('agentSelector'),
        agentStatus: document.getElementById('agentStatus'),
        currentAgentName: document.getElementById('currentAgentName'),
        requestStatus: document.getElementById('requestStatus'),
        chatScroll: document.getElementById('chatScroll'),
        messageList: document.getElementById('messageList'),
        userInput: document.getElementById('userInput'),
        sendBtn: document.getElementById('sendBtn'),
        sessionIdText: document.getElementById('sessionIdText')
    };

    function apiUrl(path) {
        const base = cfg.API_BASE_URL || '';
        return `${base}${path}`;
    }

    function loadJson(key, fallback) {
        try {
            return JSON.parse(localStorage.getItem(key) || JSON.stringify(fallback));
        } catch (_) {
            return fallback;
        }
    }

    function saveJson(key, value) {
        localStorage.setItem(key, JSON.stringify(value));
    }

    function genSessionId() {
        return `session_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`;
    }

    function setRunning(running) {
        state.running = running;
        dom.sendBtn.disabled = running;
        dom.requestStatus.className = running ? 'request-status running' : 'request-status idle';
        dom.requestStatus.textContent = running ? '执行中' : '空闲';
    }

    function escapeHtml(value) {
        return String(value || '')
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#039;');
    }

    function renderMarkdown(text) {
        const raw = text || '';
        if (window.marked && window.DOMPurify) {
            try {
                return DOMPurify.sanitize(marked.parse(raw));
            } catch (_) {
                return escapeHtml(raw).replace(/\n/g, '<br/>');
            }
        }
        return escapeHtml(raw).replace(/\n/g, '<br/>');
    }

    function highlightCode(container) {
        if (!window.hljs || !container) return;
        container.querySelectorAll('pre code').forEach(block => {
            try { hljs.highlightElement(block); } catch (_) {}
        });
    }

    function scrollToBottom() {
        dom.chatScroll.scrollTop = dom.chatScroll.scrollHeight;
    }

    function resetSession() {
        state.sessionId = genSessionId();
        dom.sessionIdText.textContent = state.sessionId;
        dom.messageList.innerHTML = '';
        renderEmptyState();
        autoResizeInput();
    }

    function renderEmptyState() {
        const agentText = state.selectedAgentName ? `当前智能体：${state.selectedAgentName}` : '请先在左侧选择智能体';
        dom.messageList.innerHTML = `
            <div class="empty-state">
                <h1>有什么可以帮忙的？</h1>
                <p>${escapeHtml(agentText)}</p>
                <p>发送消息后，执行过程会折叠在回答下方，执行中会显示加载图标。</p>
            </div>
        `;
    }

    function ensureConversationStarted() {
        const empty = dom.messageList.querySelector('.empty-state');
        if (empty) dom.messageList.innerHTML = '';
    }

    function createMessage(role, text) {
        ensureConversationStarted();
        const row = document.createElement('div');
        row.className = 'message-row';
        const isUser = role === 'user';
        row.innerHTML = `
            <div class="avatar ${isUser ? 'user' : 'assistant'}">${isUser ? '你' : 'AI'}</div>
            <div class="message-content">
                <div class="message-role">${isUser ? '你' : 'AI助手'}</div>
                <div class="message-text"></div>
            </div>
        `;
        const textEl = row.querySelector('.message-text');
        textEl.innerHTML = renderMarkdown(text || '');
        dom.messageList.appendChild(row);
        highlightCode(row);
        scrollToBottom();
        return { row, textEl };
    }

    function createAssistantPendingMessage() {
        ensureConversationStarted();
        const row = document.createElement('div');
        row.className = 'message-row';
        row.innerHTML = `
            <div class="avatar assistant">AI</div>
            <div class="message-content">
                <div class="message-role">AI助手</div>
                <div class="pending-line">
                    <span class="spinner"></span>
                    <span class="pending-text">思考中...</span>
                </div>
                <div class="message-text"></div>
                <details class="reasoning-box" hidden>
                    <summary>Thought</summary>
                    <div class="reasoning-content"></div>
                </details>
                <div class="message-actions" hidden>
                    <button class="message-action copy-answer">复制</button>
                </div>
            </div>
        `;
        dom.messageList.appendChild(row);
        scrollToBottom();
        return {
            row,
            pendingLine: row.querySelector('.pending-line'),
            pendingText: row.querySelector('.pending-text'),
            answerEl: row.querySelector('.message-text'),
            reasoningBox: row.querySelector('.reasoning-box'),
            reasoningSummary: row.querySelector('.reasoning-box summary'),
            reasoningContent: row.querySelector('.reasoning-content'),
            actions: row.querySelector('.message-actions'),
            copyBtn: row.querySelector('.copy-answer')
        };
    }

    function updateAnswer(view, answer) {
        view.answerEl.innerHTML = renderMarkdown(answer || '');
        highlightCode(view.row);
        scrollToBottom();
    }

    function updateReasoning(view, reasoning, seconds) {
        const content = (reasoning || '').trim();
        if (!content) {
            view.reasoningBox.hidden = true;
            return;
        }
        view.reasoningBox.hidden = false;
        view.reasoningSummary.textContent = `Thought for ${Math.max(1, seconds || 1)}s`;
        view.reasoningContent.textContent = content;
    }

    function appendHistory(title) {
        const item = {
            id: state.sessionId,
            title: (title || '新对话').trim().slice(0, 30),
            agentName: state.selectedAgentName || '',
            time: Date.now()
        };
        state.histories = [item, ...state.histories.filter(x => x.id !== item.id)].slice(0, 40);
        saveJson('chatgpt_agent_histories', state.histories);
        renderHistories();
    }

    function renderHistories() {
        dom.chatList.innerHTML = '';
        if (!state.histories.length) {
            const li = document.createElement('li');
            li.textContent = '暂无历史对话';
            li.style.color = '#9ca3af';
            dom.chatList.appendChild(li);
            return;
        }
        state.histories.forEach(item => {
            const li = document.createElement('li');
            li.textContent = item.title || '新对话';
            li.title = item.agentName ? `${item.title} · ${item.agentName}` : item.title;
            if (item.id === state.sessionId) li.classList.add('active');
            li.addEventListener('click', () => {
                state.sessionId = item.id || genSessionId();
                dom.sessionIdText.textContent = state.sessionId;
                dom.messageList.innerHTML = '';
                createMessage('assistant', `已切换到历史会话：${item.title}\n你可以继续输入新消息。`);
                renderHistories();
            });
            dom.chatList.appendChild(li);
        });
    }

    async function loadAgents() {
        dom.agentStatus.textContent = '正在查询智能体列表...';
        dom.agentSelector.innerHTML = '<option value="">正在加载...</option>';
        try {
            const res = await fetch(apiUrl(cfg.AGENT_LIST_URL), { method: 'GET' });
            const json = await res.json();
            if (!res.ok || !json || json.code !== '0000') {
                throw new Error(json && json.info ? json.info : `HTTP ${res.status}`);
            }
            const list = Array.isArray(json.data) ? json.data : [];
            if (!list.length) {
                dom.agentSelector.innerHTML = '<option value="">暂无可用智能体</option>';
                dom.agentStatus.textContent = '没有查询到 status=1 的 ai_agent。';
                dom.currentAgentName.textContent = '暂无可用智能体';
                return;
            }
            dom.agentSelector.innerHTML = '<option value="">请选择智能体</option>';
            list.forEach(agent => {
                const option = document.createElement('option');
                option.value = agent.agentId;
                option.textContent = agent.agentName ? `${agent.agentName}（${agent.agentId}）` : agent.agentId;
                option.dataset.agentName = agent.agentName || agent.agentId;
                option.dataset.agent = JSON.stringify(agent);
                dom.agentSelector.appendChild(option);
            });
            dom.agentStatus.textContent = `已加载 ${list.length} 个智能体。选择后直接发送消息，不触发装配。`;

            // 如果只有一个智能体，自动选择；否则保持用户选择。
            if (list.length === 1) {
                dom.agentSelector.value = list[0].agentId;
                applySelectedAgent();
            }
        } catch (error) {
            dom.agentSelector.innerHTML = '<option value="">加载失败</option>';
            dom.agentStatus.textContent = `加载失败：${error.message}`;
            dom.currentAgentName.textContent = '智能体加载失败';
        }
    }

    function applySelectedAgent() {
        const option = dom.agentSelector.options[dom.agentSelector.selectedIndex];
        state.selectedAgentId = dom.agentSelector.value || '';
        state.selectedAgentName = option ? (option.dataset.agentName || option.textContent || '') : '';
        try { state.selectedAgentMeta = option && option.dataset.agent ? JSON.parse(option.dataset.agent) : null; } catch (_) { state.selectedAgentMeta = null; }
        dom.currentAgentName.textContent = state.selectedAgentName ? `当前智能体：${state.selectedAgentName}` : '请选择智能体';
        if (dom.messageList.querySelector('.empty-state')) renderEmptyState();
    }

    function autoResizeInput() {
        dom.userInput.style.height = 'auto';
        dom.userInput.style.height = `${Math.min(dom.userInput.scrollHeight, 180)}px`;
    }

    function setQuickPrompt(text) {
        dom.userInput.value = text || '';
        autoResizeInput();
        dom.userInput.focus();
    }

    function normalizeChunk(rawChunk) {
        let text = rawChunk || '';
        // 兼容 SSE 格式：data: xxx\n\n / event: xxx
        text = text.replace(/^event:\s?.*$/gm, '');
        text = text.replace(/^data:\s?/gm, '');
        return text;
    }

    function tryParseJsonLine(line) {
        const value = (line || '').trim();
        if (!value) return null;
        if (!(value.startsWith('{') && value.endsWith('}'))) return null;
        try { return JSON.parse(value); } catch (_) { return null; }
    }

    function extractPayloadText(payload) {
        if (!payload) return '';
        if (typeof payload === 'string') return payload;
        return payload.content || payload.message || payload.data || payload.text || payload.result || payload.output || '';
    }

    function isFinalPayload(payload, text) {
        const flag = `${payload?.stage || ''} ${payload?.type || ''} ${payload?.subType || ''} ${payload?.event || ''}`.toLowerCase();
        if (/final|result|summary|answer|output/.test(flag)) return true;
        if (/最终|总结|结果|回答/.test(text || '')) return true;
        return false;
    }

    function isReasoningText(text) {
        const value = text || '';
        return /执行第\s*\d+\s*步|阶段\d+|任务状态分析|工具调用|监督检查|执行过程|开始调用|步骤|🎯|📊|🔧|🧠/.test(value);
    }

    function consumeStreamText(chunk, buffers) {
        const normalized = normalizeChunk(chunk);
        if (!normalized.trim()) return;

        // 逐行尝试解析 JSON；无法解析的普通文本按启发式归类。
        const lines = normalized.split(/\r?\n/);
        let hasJson = false;

        for (const line of lines) {
            const payload = tryParseJsonLine(line);
            if (!payload) continue;
            hasJson = true;
            const text = String(extractPayloadText(payload) || '');
            if (!text.trim()) continue;
            if (isFinalPayload(payload, text)) buffers.answer += text;
            else buffers.reasoning += text + '\n';
        }

        if (hasJson) return;

        const plain = normalized;
        if (!plain.trim()) return;

        if (/执行异常|请求异常|Exception|Error|失败/.test(plain)) {
            buffers.answer += plain;
        } else if (isReasoningText(plain)) {
            buffers.reasoning += plain + '\n';
        } else {
            buffers.answer += plain;
        }
    }

    async function sendMessage() {
        const message = dom.userInput.value.trim();
        if (!message || state.running) return;
        if (!state.selectedAgentId) {
            createMessage('assistant', '请先在左侧选择一个智能体。');
            return;
        }

        createMessage('user', message);
        appendHistory(message);
        dom.userInput.value = '';
        autoResizeInput();

        const assistant = createAssistantPendingMessage();
        const buffers = { reasoning: '', answer: '' };
        const startTime = Date.now();
        setRunning(true);

        try {
            const response = await fetch(apiUrl(cfg.CHAT_URL), {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    aiAgentId: state.selectedAgentId,
                    message,
                    sessionId: state.sessionId,
                    maxStep: state.maxStep
                })
            });

            if (!response.ok) throw new Error(`HTTP ${response.status}`);
            if (!response.body) {
                const text = await response.text();
                buffers.answer += text || '后端没有返回流式内容。';
            } else {
                const reader = response.body.getReader();
                const decoder = new TextDecoder('utf-8');
                while (true) {
                    const { done, value } = await reader.read();
                    if (done) break;
                    const chunk = decoder.decode(value, { stream: true });
                    consumeStreamText(chunk, buffers);
                    const currentAnswer = buffers.answer.trim();
                    if (currentAnswer) updateAnswer(assistant, currentAnswer);
                    assistant.pendingText.textContent = '正在执行智能体...';
                }
            }

            const seconds = Math.round((Date.now() - startTime) / 1000) || 1;
            assistant.pendingLine.style.display = 'none';
            updateReasoning(assistant, buffers.reasoning, seconds);

            const finalAnswer = buffers.answer.trim();
            if (finalAnswer) updateAnswer(assistant, finalAnswer);
            else updateAnswer(assistant, '执行完成。后端没有返回最终文本，详细过程可展开查看。');

            assistant.actions.hidden = false;
            assistant.copyBtn.addEventListener('click', async () => {
                await navigator.clipboard.writeText(assistant.answerEl.innerText || '');
                assistant.copyBtn.textContent = '已复制';
                setTimeout(() => assistant.copyBtn.textContent = '复制', 1200);
            });
        } catch (error) {
            const seconds = Math.round((Date.now() - startTime) / 1000) || 1;
            assistant.pendingLine.style.display = 'none';
            updateReasoning(assistant, buffers.reasoning, seconds);
            updateAnswer(assistant, `执行异常：${error.message}`);
        } finally {
            setRunning(false);
            scrollToBottom();
        }
    }

    function bindEvents() {
        dom.newChatBtn.addEventListener('click', resetSession);
        dom.clearAllChatsBtn.addEventListener('click', () => {
            state.histories = [];
            saveJson('chatgpt_agent_histories', []);
            renderHistories();
        });
        dom.mobileSidebarBtn.addEventListener('click', () => dom.sidebar.classList.toggle('open'));
        dom.agentSelector.addEventListener('change', applySelectedAgent);
        dom.sendBtn.addEventListener('click', sendMessage);
        dom.userInput.addEventListener('input', autoResizeInput);
        dom.userInput.addEventListener('keydown', event => {
            if (event.key === 'Enter' && !event.shiftKey) {
                event.preventDefault();
                sendMessage();
            }
        });
        document.querySelectorAll('.step-btn').forEach(btn => {
            btn.addEventListener('click', () => {
                document.querySelectorAll('.step-btn').forEach(x => x.classList.remove('active'));
                btn.classList.add('active');
                state.maxStep = Number(btn.dataset.step || cfg.DEFAULT_MAX_STEP || 5);
            });
        });
        document.querySelectorAll('.quick-prompt').forEach(btn => {
            btn.addEventListener('click', () => setQuickPrompt(btn.dataset.prompt));
        });
    }

    function init() {
        bindEvents();
        renderHistories();
        resetSession();
        loadAgents();
        setRunning(false);
    }

    init();
})();
