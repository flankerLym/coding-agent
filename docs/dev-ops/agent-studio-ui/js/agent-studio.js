(function () {
  const cfg = window.AGENT_STUDIO_CONFIG || {};

  const dom = {
    newChatBtn: document.getElementById('newChatBtn'),
    clearAllChatsBtn: document.getElementById('clearAllChatsBtn'),
    chatList: document.getElementById('chatList'),
    thinkingMessages: document.getElementById('thinkingMessages'),
    resultMessages: document.getElementById('resultMessages'),
    loading: document.getElementById('loading'),
    userInput: document.getElementById('userInput'),
    sendBtn: document.getElementById('sendBtn'),
    sessionIdText: document.getElementById('sessionIdText'),
    caseSelectContainer: document.getElementById('caseSelectContainer')
  };

  const state = {
    sessionId: createSessionId(),
    maxStep: 2,
    chats: loadChats(),
    activeEventSource: null
  };

  window.currentSessionId = state.sessionId;
  window.currentMaxStep = state.maxStep;

  function init() {
    syncSessionText();
    renderChatList();
    bindEvents();
  }

  function bindEvents() {
    dom.newChatBtn && dom.newChatBtn.addEventListener('click', newChat);
    dom.clearAllChatsBtn && dom.clearAllChatsBtn.addEventListener('click', clearChats);
    dom.sendBtn && dom.sendBtn.addEventListener('click', handleSendMessage);

    dom.userInput && dom.userInput.addEventListener('keydown', (e) => {
      if ((e.ctrlKey || e.metaKey) && e.key === 'Enter') handleSendMessage();
    });

    document.querySelectorAll('.step-button').forEach(btn => {
      btn.addEventListener('click', () => {
        document.querySelectorAll('.step-button').forEach(x => x.classList.remove('selected'));
        btn.classList.add('selected');
        state.maxStep = Number(btn.dataset.step || 2);
        window.currentMaxStep = state.maxStep;
      });
    });

    if (dom.caseSelectContainer) {
      dom.caseSelectContainer.addEventListener('click', (e) => {
        const card = e.target.closest('.case-card');
        if (!card || !dom.userInput) return;
        dom.userInput.value = card.dataset.case || card.textContent.trim();
        dom.userInput.focus();
      });
    }
  }

  function newChat() {
    state.sessionId = createSessionId();
    window.currentSessionId = state.sessionId;
    syncSessionText();
    resetMessages();
    addMessage(dom.thinkingMessages, 'ai', '新的会话已创建，请输入任务。');
    addMessage(dom.resultMessages, 'ai', '等待执行结果。');
  }

  function clearChats() {
    if (!confirm('确认清空本地对话历史吗？')) return;
    state.chats = [];
    localStorage.removeItem('agent_studio_chats');
    renderChatList();
    newChat();
  }

  async function handleSendMessage() {
    const message = (dom.userInput && dom.userInput.value || '').trim();
    if (!message) return alert('请输入问题');

    const agent = window.getCurrentSelectedAgent ? window.getCurrentSelectedAgent() : window.currentSelectedAgent;
    if (!agent) return alert('请先选择智能体');

    setLoading(true);
    addMessage(dom.thinkingMessages, 'user', message);
    const resultNode = addMessage(dom.resultMessages, 'ai', '');
    const traceNode = addMessage(dom.thinkingMessages, 'thinking', '任务已提交，正在连接后端...');
    saveChatTitle(message, agent);
    if (dom.userInput) dom.userInput.value = '';

    const payload = {
      sessionId: state.sessionId,
      message,
      prompt: message,
      maxStep: state.maxStep,
      agentId: agent.id,
      agentCode: agent.agentCode,
      agentType: agent.category,
      agentName: agent.agentName
    };

    try {
      await requestAgent(payload, {
        onTrace: (text) => appendMarkdown(traceNode, text),
        onResult: (text) => appendMarkdown(resultNode, text, true),
        onDone: () => appendMarkdown(traceNode, '\n\n✅ 执行完成。')
      });
    } catch (err) {
      console.error('发送失败：', err);
      appendMarkdown(traceNode, `\n\n❌ 请求失败：${err.message || err}`);
      appendMarkdown(resultNode, `请求后端失败。请检查接口配置：\n\n- agentOptionsUrl：${cfg.agentOptionsUrl}\n- chatStreamUrl：${cfg.chatStreamUrl}\n- chatPostUrl：${cfg.chatPostUrl}`);
    } finally {
      setLoading(false);
    }
  }

  async function requestAgent(payload, hooks) {
    // 优先 SSE，失败后自动 POST 兜底。
    if (cfg.chatStreamUrl && window.EventSource) {
      try {
        await requestBySse(payload, hooks);
        return;
      } catch (err) {
        console.warn('SSE 失败，尝试 POST：', err);
      }
    }
    await requestByPost(payload, hooks);
  }

  function requestBySse(payload, hooks) {
    return new Promise((resolve, reject) => {
      const url = new URL(cfg.chatStreamUrl || '/api/agent/chat/stream', window.location.origin);
      Object.entries(payload).forEach(([k, v]) => url.searchParams.set(k, v == null ? '' : String(v)));

      const es = new EventSource(url.toString(), { withCredentials: true });
      state.activeEventSource = es;
      let opened = false;
      let received = false;

      const timer = setTimeout(() => {
        if (!opened && !received) {
          es.close();
          reject(new Error('SSE 连接超时'));
        }
      }, 8000);

      es.onopen = () => {
        opened = true;
        clearTimeout(timer);
        hooks.onTrace && hooks.onTrace('\n\n已建立 SSE 连接，等待流式返回...');
      };

      es.onmessage = (event) => {
        received = true;
        const data = parseEventData(event.data);
        if (isDone(data)) {
          es.close();
          hooks.onDone && hooks.onDone();
          resolve();
          return;
        }
        routeStreamData(data, hooks);
      };

      es.addEventListener('thinking', e => routeStreamData({ stage: 'thinking', content: e.data }, hooks));
      es.addEventListener('result', e => routeStreamData({ stage: 'result', content: e.data }, hooks));
      es.addEventListener('done', () => {
        es.close();
        hooks.onDone && hooks.onDone();
        resolve();
      });
      es.onerror = () => {
        clearTimeout(timer);
        es.close();
        reject(new Error('SSE 连接失败'));
      };
    });
  }

  async function requestByPost(payload, hooks) {
    if (!cfg.chatPostUrl) throw new Error('没有配置 chatPostUrl');
    hooks.onTrace && hooks.onTrace('\n\n正在使用 POST 接口请求后端...');
    const resp = await fetch(cfg.chatPostUrl, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'Accept': 'application/json, text/plain' },
      credentials: 'include',
      body: JSON.stringify(payload)
    });
    if (!resp.ok) throw new Error(`POST HTTP ${resp.status}`);

    const text = await resp.text();
    let data;
    try { data = JSON.parse(text); } catch { data = text; }
    routeStreamData(data, hooks);
    hooks.onDone && hooks.onDone();
  }

  function routeStreamData(data, hooks) {
    if (data == null) return;
    if (typeof data === 'string') {
      hooks.onResult && hooks.onResult(data);
      return;
    }

    const stage = String(data.stage || data.type || data.event || '').toLowerCase();
    const content = data.content ?? data.message ?? data.data ?? data.result ?? data.output ?? data.answer ?? '';

    if (stage.includes('think') || stage.includes('analysis') || stage.includes('tool') || stage.includes('trace')) {
      hooks.onTrace && hooks.onTrace(String(content));
    } else if (stage.includes('done')) {
      hooks.onDone && hooks.onDone();
    } else {
      hooks.onResult && hooks.onResult(String(content || JSON.stringify(data, null, 2)));
    }
  }

  function parseEventData(raw) {
    if (raw == null) return '';
    try { return JSON.parse(raw); } catch { return raw; }
  }

  function isDone(data) {
    if (data === '[DONE]' || data === 'DONE') return true;
    if (typeof data === 'object' && data) {
      const event = String(data.event || data.type || data.stage || '').toLowerCase();
      return event === 'done' || event === 'complete' || event === 'completed';
    }
    return false;
  }

  function addMessage(container, role, content) {
    if (!container) return null;
    const div = document.createElement('div');
    div.className = `message ${role}-message`;
    div.innerHTML = `
      <div class="message-avatar">${role === 'user' ? '你' : role === 'thinking' ? '↯' : 'AI'}</div>
      <div class="message-content"></div>
    `;
    const contentEl = div.querySelector('.message-content');
    renderMarkdown(contentEl, content || '');
    container.appendChild(div);
    container.scrollTop = container.scrollHeight;
    return contentEl;
  }

  function appendMarkdown(node, text, replace) {
    if (!node) return;
    const old = replace ? '' : (node.dataset.raw || '');
    node.dataset.raw = old + (text || '');
    renderMarkdown(node, node.dataset.raw);
    const list = node.closest('.message-list');
    if (list) list.scrollTop = list.scrollHeight;
  }

  function renderMarkdown(node, text) {
    if (!node) return;
    const raw = String(text || '');
    if (window.marked && window.DOMPurify) {
      node.innerHTML = DOMPurify.sanitize(marked.parse(raw));
    } else {
      node.textContent = raw;
    }
    if (window.hljs) node.querySelectorAll('pre code').forEach(block => hljs.highlightElement(block));
  }

  function setLoading(show) {
    if (dom.loading) dom.loading.classList.toggle('hidden', !show);
    if (dom.sendBtn) dom.sendBtn.disabled = show;
  }

  function createSessionId() {
    return 'session_' + Date.now() + '_' + Math.random().toString(36).slice(2, 10);
  }

  function syncSessionText() {
    if (dom.sessionIdText) dom.sessionIdText.textContent = `会话ID：${state.sessionId}`;
  }

  function resetMessages() {
    if (dom.thinkingMessages) dom.thinkingMessages.innerHTML = '';
    if (dom.resultMessages) dom.resultMessages.innerHTML = '';
  }

  function saveChatTitle(message, agent) {
    const item = {
      id: state.sessionId,
      title: message.slice(0, 28) + (message.length > 28 ? '...' : ''),
      agentName: agent.agentName,
      time: new Date().toLocaleString()
    };
    state.chats = [item, ...state.chats.filter(x => x.id !== item.id)].slice(0, 30);
    localStorage.setItem('agent_studio_chats', JSON.stringify(state.chats));
    renderChatList();
  }

  function loadChats() {
    try { return JSON.parse(localStorage.getItem('agent_studio_chats') || '[]'); } catch { return []; }
  }

  function renderChatList() {
    if (!dom.chatList) return;
    if (!state.chats.length) {
      dom.chatList.innerHTML = '<li class="empty-item">暂无历史对话</li>';
      return;
    }
    dom.chatList.innerHTML = state.chats.map(item => `
      <li data-session-id="${escapeHtml(item.id)}">
        <div style="font-weight:800; margin-bottom:6px;">${escapeHtml(item.title)}</div>
        <div style="font-size:12px;color:#90a4c4;line-height:1.5;">${escapeHtml(item.agentName || '')}<br>${escapeHtml(item.time || '')}</div>
      </li>
    `).join('');

    dom.chatList.querySelectorAll('li[data-session-id]').forEach(li => {
      li.addEventListener('click', () => {
        state.sessionId = li.dataset.sessionId;
        window.currentSessionId = state.sessionId;
        syncSessionText();
        dom.chatList.querySelectorAll('li').forEach(x => x.classList.remove('active'));
        li.classList.add('active');
      });
    });
  }

  function escapeHtml(str) {
    return String(str ?? '').replace(/[&<>'"]/g, c => ({ '&':'&amp;', '<':'&lt;', '>':'&gt;', "'":'&#39;', '"':'&quot;' }[c]));
  }

  window.handleSendMessage = handleSendMessage;
  document.addEventListener('DOMContentLoaded', init);
})();
