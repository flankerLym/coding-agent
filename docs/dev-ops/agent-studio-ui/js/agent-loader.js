(function () {
  const cfg = window.AGENT_STUDIO_CONFIG || {};
  const agentSelector = document.getElementById('agentSelector');
  const agentCards = document.getElementById('agentCards');
  const loadingEl = document.getElementById('agentSelectorLoading');
  const refreshBtn = document.getElementById('refreshAgentsBtn');
  const preview = document.getElementById('selectedAgentPreview');
  const currentAgentText = document.getElementById('currentAgentText');

  window.agentOptionMap = new Map();
  window.currentSelectedAgent = null;

  const fallbackAgents = [
    { id: '1', agentCode: 'ai_agent_1', agentName: '自动自主规划', description: 'CSDN 发帖 + 通知等内置规划智能体', avatar: '📄', category: 'builtin', maxStep: 2 },
    { id: '3', agentCode: 'ai_agent_3', agentName: '智能对话分析', description: '通用对话分析与任务拆解', avatar: '💬', category: 'builtin', maxStep: 2 },
    { id: '4', agentCode: 'ai_agent_4', agentName: 'ELK日志检索分析', description: '面向日志检索、异常定位和结果总结', avatar: '📈', category: 'builtin', maxStep: 2 },
    { id: '5', agentCode: 'ai_agent_5', agentName: '智能监控分析服务', description: '监控数据分析与告警诊断', avatar: '📊', category: 'builtin', maxStep: 2 }
  ];

  function log(...args) {
    if (cfg.debug) console.log('[agent-loader]', ...args);
  }

  async function loadAgentOptions() {
    setLoading(true);
    try {
      const resp = await fetch(cfg.agentOptionsUrl || '/api/agent/options', {
        method: 'GET',
        headers: { 'Accept': 'application/json' },
        credentials: 'include'
      });

      if (!resp.ok) throw new Error(`HTTP ${resp.status}`);
      const json = await resp.json();
      const agents = parseAgentResponse(json);

      if (!agents.length) throw new Error('后端没有返回任何智能体');
      renderAgents(agents);
      selectAgent(agents[0].uniqueKey);
      log('loaded agents:', agents);
    } catch (err) {
      console.error('加载智能体失败：', err);
      if (cfg.enableFallbackAgents) {
        const agents = fallbackAgents.map(normalizeAgent);
        renderAgents(agents);
        selectAgent(agents[0].uniqueKey);
        if (loadingEl) loadingEl.textContent = '后端加载失败，当前显示前端兜底智能体。请检查 /api/agent/options。';
        return;
      }
      renderLoadError(err);
    } finally {
      setLoading(false, true);
    }
  }

  function parseAgentResponse(json) {
    // 支持这些格式：
    // 1. { code:'0000', data:{ dragAgents:[], builtinAgents:[] } }
    // 2. { data:[...] }
    // 3. [...] 
    // 4. { data:{ records:[] } }
    let raw = [];
    const data = json && json.data !== undefined ? json.data : json;

    if (Array.isArray(data)) {
      raw = data;
    } else if (data && Array.isArray(data.records)) {
      raw = data.records;
    } else if (data && (Array.isArray(data.dragAgents) || Array.isArray(data.builtinAgents))) {
      const dragAgents = (data.dragAgents || []).map(x => ({ ...x, category: x.category || 'drag' }));
      const builtinAgents = (data.builtinAgents || []).map(x => ({ ...x, category: x.category || 'builtin' }));
      raw = dragAgents.concat(builtinAgents);
    } else if (data && (Array.isArray(data.drag) || Array.isArray(data.aiAgent))) {
      raw = (data.drag || []).map(x => ({ ...x, category: x.category || 'drag' }))
        .concat((data.aiAgent || []).map(x => ({ ...x, category: x.category || 'builtin' })));
    }

    return raw.map(normalizeAgent).filter(x => x.id || x.agentCode || x.agentName);
  }

  function normalizeAgent(item) {
    const id = String(item.id ?? item.agentId ?? item.aiAgentId ?? item.agent_id ?? item.clientId ?? item.client_id ?? item.agentCode ?? item.code ?? '');
    const categoryRaw = String(item.category ?? item.source ?? item.type ?? item.agentType ?? '').toLowerCase();
    const category = categoryRaw.includes('drag') || categoryRaw.includes('flow') || categoryRaw.includes('workflow') ? 'drag' : 'builtin';
    const agentCode = String(item.agentCode ?? item.code ?? item.agent_code ?? item.clientCode ?? item.client_id ?? id);
    const agentName = String(item.agentName ?? item.name ?? item.agent_name ?? item.title ?? `智能体 ${id || agentCode}`);
    const description = String(item.description ?? item.desc ?? item.remark ?? item.prompt ?? (category === 'drag' ? '拖拉拽编排生成的智能体' : 'ai_agent 内置智能体'));
    const avatar = String(item.avatar ?? item.icon ?? (category === 'drag' ? '🧩' : '🤖'));
    const maxStep = Number(item.maxStep ?? item.max_step ?? item.step ?? 2) || 2;
    const uniqueKey = `${category}_${id || agentCode}`;
    return { id, agentCode, agentName, description, avatar, category, maxStep, uniqueKey, raw: item };
  }

  function renderAgents(agents) {
    window.agentOptionMap.clear();
    renderSelector(agents);
    renderCards(agents);
  }

  function renderSelector(agents) {
    if (!agentSelector) return;
    agentSelector.innerHTML = '';

    const dragGroup = document.createElement('optgroup');
    dragGroup.label = '拖拉拽智能体';
    const builtinGroup = document.createElement('optgroup');
    builtinGroup.label = 'ai_agent 内置智能体';

    agents.forEach(agent => {
      window.agentOptionMap.set(agent.uniqueKey, agent);
      const opt = document.createElement('option');
      opt.value = agent.uniqueKey;
      opt.textContent = `${agent.avatar} ${agent.agentName}`;
      if (agent.category === 'drag') dragGroup.appendChild(opt);
      else builtinGroup.appendChild(opt);
    });

    if (dragGroup.children.length) agentSelector.appendChild(dragGroup);
    if (builtinGroup.children.length) agentSelector.appendChild(builtinGroup);
  }

  function renderCards(agents) {
    if (!agentCards) return;
    agentCards.innerHTML = '';
    agents.forEach(agent => {
      const card = document.createElement('div');
      card.className = 'agent-card';
      card.dataset.agentKey = agent.uniqueKey;
      card.dataset.agentId = agent.id;
      card.innerHTML = `
        <div class="agent-card-top">
          <div class="agent-card-icon">${escapeHtml(agent.avatar)}</div>
          <span class="agent-type-tag">${agent.category === 'drag' ? '拖拉拽' : '内置'}</span>
        </div>
        <div class="agent-card-name">${escapeHtml(agent.agentName)}</div>
        <div class="agent-card-desc">${escapeHtml(agent.description)}</div>
      `;
      card.addEventListener('click', () => selectAgent(agent.uniqueKey));
      agentCards.appendChild(card);
    });
  }

  function selectAgent(agentKey) {
    const agent = window.agentOptionMap.get(agentKey);
    if (!agent) return;

    window.currentSelectedAgent = agent;
    window.selectedAgentId = agent.id;
    window.selectedAgentCode = agent.agentCode;
    window.selectedAgentType = agent.category;
    window.selectedAgentName = agent.agentName;

    if (agentSelector && agentSelector.value !== agentKey) agentSelector.value = agentKey;
    document.querySelectorAll('.agent-card').forEach(card => card.classList.toggle('selected', card.dataset.agentKey === agentKey));
    updatePreview(agent);

    window.dispatchEvent(new CustomEvent('agent:selected', { detail: agent }));
  }

  function updatePreview(agent) {
    if (preview) {
      preview.innerHTML = `
        <div class="preview-icon">${escapeHtml(agent.avatar)}</div>
        <div>
          <div class="preview-name">${escapeHtml(agent.agentName)}</div>
          <div class="preview-desc">${escapeHtml(agent.category === 'drag' ? '拖拉拽智能体' : 'ai_agent 内置智能体')} · ${escapeHtml(agent.description)}</div>
        </div>
      `;
    }
    if (currentAgentText) currentAgentText.textContent = `当前智能体：${agent.agentName}`;
  }

  function renderLoadError(err) {
    if (agentSelector) agentSelector.innerHTML = '<option value="">加载失败</option>';
    if (agentCards) agentCards.innerHTML = `<div class="empty-item">智能体加载失败：${escapeHtml(err.message || String(err))}</div>`;
  }

  function setLoading(show, keepText) {
    if (!loadingEl) return;
    if (!keepText) loadingEl.textContent = '正在从后端加载智能体...';
    loadingEl.style.display = show ? 'block' : (keepText ? 'block' : 'none');
  }

  function escapeHtml(str) {
    return String(str ?? '').replace(/[&<>'"]/g, c => ({ '&':'&amp;', '<':'&lt;', '>':'&gt;', "'":'&#39;', '"':'&quot;' }[c]));
  }

  if (agentSelector) {
    agentSelector.addEventListener('change', e => selectAgent(e.target.value));
  }
  if (refreshBtn) {
    refreshBtn.addEventListener('click', loadAgentOptions);
  }

  window.loadAgentOptions = loadAgentOptions;
  window.getCurrentSelectedAgent = () => window.currentSelectedAgent;

  document.addEventListener('DOMContentLoaded', loadAgentOptions);
})();
