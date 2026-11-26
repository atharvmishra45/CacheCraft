/**
 * CampusCache Frontend Client Application
 * Hybrid Mode:
 * - Detects if running on standalone Java server (calls Java REST API)
 * - If running on serverless static host (e.g. Vercel), runs the exact same
 *   LFU + LRU algorithm locally in the browser with full O(1) correctness!
 */

const API_BASE = '';

// ==========================================
// In-Browser LFU + LRU Engine (Fallback for static hosting like Vercel)
// ==========================================
class BrowserCacheNode {
  constructor(key, value) {
    this.key = key;
    this.value = value;
    this.frequency = 1;
    this.prev = null;
    this.next = null;
  }
}

class BrowserDoublyLinkedList {
  constructor() {
    this.head = new BrowserCacheNode(null, null);
    this.tail = new BrowserCacheNode(null, null);
    this.head.next = this.tail;
    this.tail.prev = this.head;
    this.size = 0;
  }

  addFirst(node) {
    node.next = this.head.next;
    node.prev = this.head;
    this.head.next.prev = node;
    this.head.next = node;
    this.size++;
  }

  remove(node) {
    if (node.prev) node.prev.next = node.next;
    if (node.next) node.next.prev = node.prev;
    node.prev = null;
    node.next = null;
    this.size--;
  }

  removeLast() {
    if (this.size === 0) return null;
    const lru = this.tail.prev;
    this.remove(lru);
    return lru;
  }

  peekLast() {
    return this.size === 0 ? null : this.tail.prev;
  }

  toList() {
    const arr = [];
    let curr = this.head.next;
    while (curr !== this.tail && curr) {
      arr.push(curr);
      curr = curr.next;
    }
    return arr;
  }
}

class BrowserCampusCache {
  constructor(capacity = 3) {
    this.capacity = capacity;
    this.keyNodeMap = new Map();
    this.freqListMap = new Map();
    this.minFrequency = 0;
    this.stats = {
      capacity: capacity,
      currentSize: 0,
      totalGetRequests: 0,
      cacheHits: 0,
      cacheMisses: 0,
      totalPutRequests: 0,
      totalEvictions: 0,
      hitRatioPercent: 0,
      utilizationPercent: 0
    };
  }

  reinitialize(newCap) {
    this.capacity = newCap;
    this.keyNodeMap.clear();
    this.freqListMap.clear();
    this.minFrequency = 0;
    this.stats = {
      capacity: newCap,
      currentSize: 0,
      totalGetRequests: 0,
      cacheHits: 0,
      cacheMisses: 0,
      totalPutRequests: 0,
      totalEvictions: 0,
      hitRatioPercent: 0,
      utilizationPercent: 0
    };
  }

  updateNodeFrequency(node) {
    const oldFreq = node.frequency;
    const oldList = this.freqListMap.get(oldFreq);
    if (oldList) {
      oldList.remove(node);
      if (oldList.size === 0) {
        this.freqListMap.delete(oldFreq);
        if (this.minFrequency === oldFreq) {
          this.minFrequency++;
        }
      }
    }
    node.frequency = oldFreq + 1;
    if (!this.freqListMap.has(node.frequency)) {
      this.freqListMap.set(node.frequency, new BrowserDoublyLinkedList());
    }
    this.freqListMap.get(node.frequency).addFirst(node);
  }

  get(key) {
    this.stats.totalGetRequests++;
    if (!key || !this.keyNodeMap.has(key)) {
      this.stats.cacheMisses++;
      this.refreshStats();
      return {
        operation: 'GET',
        key,
        value: null,
        hit: false,
        oldFrequency: 0,
        newFrequency: 0,
        evictedKey: null,
        reason: 'Key not found in cache',
        message: `CACHE MISS: Document '${key}' is not in fast memory.`
      };
    }

    this.stats.cacheHits++;
    const node = this.keyNodeMap.get(key);
    const oldFreq = node.frequency;
    this.updateNodeFrequency(node);
    this.refreshStats();

    return {
      operation: 'GET',
      key,
      value: node.value,
      hit: true,
      oldFrequency: oldFreq,
      newFrequency: node.frequency,
      evictedKey: null,
      reason: `Cache hit: access frequency incremented from ${oldFreq} to ${node.frequency}`,
      message: `CACHE HIT: Document '${key}' found with value '${node.value}'.`
    };
  }

  put(key, value) {
    this.stats.totalPutRequests++;
    if (!key) {
      return {
        operation: 'PUT',
        key: null,
        value,
        hit: false,
        reason: 'Invalid null key',
        message: 'Ignored: Key cannot be null.'
      };
    }

    if (this.capacity === 0) {
      this.refreshStats();
      return {
        operation: 'PUT',
        key,
        value,
        hit: false,
        reason: 'Capacity is 0',
        message: `Ignored: Cache capacity is 0. Document '${key}' cannot be stored.`
      };
    }

    if (this.keyNodeMap.has(key)) {
      const existing = this.keyNodeMap.get(key);
      const oldFreq = existing.frequency;
      existing.value = value;
      this.updateNodeFrequency(existing);
      this.refreshStats();

      return {
        operation: 'PUT',
        key,
        value,
        hit: true,
        oldFrequency: oldFreq,
        newFrequency: existing.frequency,
        evictedKey: null,
        reason: `Existing key updated; access frequency increased from ${oldFreq} to ${existing.frequency}`,
        message: `UPDATED: Document '${key}' updated to '${value}'.`
      };
    }

    let evictedKey = null;
    let evictedFreq = 0;
    let evictedVal = null;
    let reason = 'Inserted new file with initial frequency 1';

    if (this.keyNodeMap.size >= this.capacity) {
      const minList = this.freqListMap.get(this.minFrequency);
      if (minList && minList.size > 0) {
        const toEvict = minList.removeLast();
        if (toEvict) {
          this.keyNodeMap.delete(toEvict.key);
          this.stats.totalEvictions++;
          evictedKey = toEvict.key;
          evictedVal = toEvict.value;
          evictedFreq = toEvict.frequency;
          if (minList.size === 0) {
            this.freqListMap.delete(this.minFrequency);
          }
          reason = `Evicted '${evictedKey}' (LFU frequency=${evictedFreq}, tie-broken by LRU recency). Inserted '${key}'.`;
        }
      }
    }

    const newNode = new BrowserCacheNode(key, value);
    this.keyNodeMap.set(key, newNode);
    if (!this.freqListMap.has(1)) {
      this.freqListMap.set(1, new BrowserDoublyLinkedList());
    }
    this.freqListMap.get(1).addFirst(newNode);
    this.minFrequency = 1;

    this.refreshStats();

    let msg = `INSERTED: Document '${key}' cached (value='${value}', freq=1).`;
    if (evictedKey) msg += ` Evicted '${evictedKey}' to free fast memory.`;

    return {
      operation: 'PUT',
      key,
      value,
      hit: false,
      oldFrequency: 0,
      newFrequency: 1,
      evictedKey,
      evictedValue: evictedVal,
      evictedFrequency: evictedFreq,
      reason,
      message: msg
    };
  }

  getNextEvictionCandidate() {
    if (this.keyNodeMap.size === 0 || this.minFrequency === 0) return null;
    const minList = this.freqListMap.get(this.minFrequency);
    return minList ? minList.peekLast() : null;
  }

  getState() {
    const nextEvict = this.getNextEvictionCandidate();
    const nextEvictionKey = nextEvict ? nextEvict.key : null;

    const freqs = Array.from(this.freqListMap.keys()).sort((a, b) => a - b);
    const frequencyGroups = [];

    freqs.forEach(freq => {
      const list = this.freqListMap.get(freq);
      if (!list || list.size === 0) return;
      const nodesInList = list.toList();
      const nodeSnapshots = nodesInList.map((node, i) => ({
        key: node.key,
        value: node.value,
        frequency: node.frequency,
        isLruInGroup: i === nodesInList.length - 1,
        isNextEvictionCandidate: node.key === nextEvictionKey
      }));
      frequencyGroups.push({ frequency: freq, nodes: nodeSnapshots });
    });

    return {
      capacity: this.capacity,
      currentSize: this.keyNodeMap.size,
      minFrequency: this.minFrequency,
      nextEvictionKey,
      frequencyGroups,
      stats: { ...this.stats }
    };
  }

  refreshStats() {
    this.stats.capacity = this.capacity;
    this.stats.currentSize = this.keyNodeMap.size;
    const hitRatio = this.stats.totalGetRequests > 0
      ? (this.stats.cacheHits / this.stats.totalGetRequests) * 100
      : 0;
    const util = this.capacity > 0
      ? (this.keyNodeMap.size / this.capacity) * 100
      : 0;
    this.stats.hitRatioPercent = Math.round(hitRatio * 10) / 10;
    this.stats.utilizationPercent = Math.round(util * 10) / 10;
  }
}

// Local cache instance
const localCache = new BrowserCampusCache(3);
let useLocalEngine = false;

// DOM Elements
const capInput = document.getElementById('capInput');
const btnInitCap = document.getElementById('btnInitCap');
const btnReset = document.getElementById('btnReset');
const badgeCapDisplay = document.getElementById('badgeCapDisplay');
const txtUtilization = document.getElementById('txtUtilization');
const capProgressFill = document.getElementById('capProgressFill');

const statGet = document.getElementById('statGet');
const statHits = document.getElementById('statHits');
const statMisses = document.getElementById('statMisses');
const statPuts = document.getElementById('statPuts');
const statEvictions = document.getElementById('statEvictions');
const statMinFreq = document.getElementById('statMinFreq');
const badgeHitRatio = document.getElementById('badgeHitRatio');

const formGet = document.getElementById('formGet');
const getKeyInput = document.getElementById('getKeyInput');
const formPut = document.getElementById('formPut');
const putKeyInput = document.getElementById('putKeyInput');
const putValInput = document.getElementById('putValInput');

const btnShowState = document.getElementById('btnShowState');
const lblStateTime = document.getElementById('lblStateTime');
const visualizerContainer = document.getElementById('visualizerContainer');
const logContainer = document.getElementById('logContainer');
const btnClearLog = document.getElementById('btnClearLog');

const btnRunDemo = document.getElementById('btnRunDemo');
const btnRunDemo2 = document.getElementById('btnRunDemo2');

const seqToggleHeader = document.getElementById('seqToggleHeader');
const seqToggleIcon = document.getElementById('seqToggleIcon');
const seqBody = document.getElementById('seqBody');
const sequenceInput = document.getElementById('sequenceInput');
const btnProcessSequence = document.getElementById('btnProcessSequence');
const btnLoadExample = document.getElementById('btnLoadExample');
const btnClearSequence = document.getElementById('btnClearSequence');
const liveStatusText = document.getElementById('liveStatusText');

// Initial Setup
document.addEventListener('DOMContentLoaded', async () => {
  setupEventListeners();
  // Probe if Java backend is present; if not, seamlessly run browser engine
  try {
    const res = await fetch(`${API_BASE}/api/state`, { signal: AbortSignal.timeout(1500) });
    if (res.ok) {
      useLocalEngine = false;
      if (liveStatusText) liveStatusText.textContent = "Live Cache: Java Engine (O(1))";
      fetchState();
      return;
    }
  } catch (e) {
    // Backend unreachable -> Vercel / GitHub Pages static mode
  }
  useLocalEngine = true;
  if (liveStatusText) liveStatusText.textContent = "Live Cache: Client O(1) Engine (Vercel Mode)";
  renderState(localCache.getState());
});

function setupEventListeners() {
  btnInitCap.addEventListener('click', handleInitCapacity);
  btnReset.addEventListener('click', handleReset);

  formGet.addEventListener('submit', handleGet);
  formPut.addEventListener('submit', handlePut);
  btnShowState.addEventListener('click', () => fetchState(true));
  btnClearLog.addEventListener('click', clearLog);

  btnRunDemo.addEventListener('click', () => runDemo(1));
  btnRunDemo2.addEventListener('click', () => runDemo(2));

  btnProcessSequence.addEventListener('click', handleProcessSequence);
  btnLoadExample.addEventListener('click', () => {
    sequenceInput.value = "CAPACITY(2)\nPUT(A,10)\nPUT(B,20)\nGET(A)\nPUT(C,30)\nSTATE";
  });
  btnClearSequence.addEventListener('click', () => {
    sequenceInput.value = '';
  });

  seqToggleHeader.addEventListener('click', () => {
    const isHidden = seqBody.style.display === 'none';
    seqBody.style.display = isHidden ? 'flex' : 'none';
    seqToggleIcon.textContent = isHidden ? '▼' : '▶';
  });
}

function getFormattedTime() {
  const now = new Date();
  return now.toTimeString().split(' ')[0];
}

function appendLogEntry(type, badge, message) {
  const row = document.createElement('div');
  row.className = `log-entry log-${type}`;

  const timeSpan = document.createElement('span');
  timeSpan.className = 'log-time';
  timeSpan.textContent = getFormattedTime();

  const badgeSpan = document.createElement('span');
  badgeSpan.className = `log-badge badge-${type}`;
  badgeSpan.textContent = badge;

  const textSpan = document.createElement('span');
  textSpan.className = 'log-text';
  textSpan.innerHTML = message;

  row.appendChild(timeSpan);
  row.appendChild(badgeSpan);
  row.appendChild(textSpan);

  logContainer.insertBefore(row, logContainer.firstChild);

  while (logContainer.children.length > 100) {
    logContainer.removeChild(logContainer.lastChild);
  }
}

function clearLog() {
  logContainer.innerHTML = '';
  appendLogEntry('info', 'SYS', 'Audit log cleared.');
}

// REST & Local Cache Operations
async function handleInitCapacity() {
  const cap = parseInt(capInput.value, 10);
  if (isNaN(cap) || cap < 0 || cap > 200) {
    alert('Please enter a valid capacity between 0 and 200.');
    return;
  }

  if (useLocalEngine) {
    localCache.reinitialize(cap);
    badgeCapDisplay.textContent = `Cap: ${cap}`;
    appendLogEntry('info', 'INIT', `Fast memory reinitialized with capacity <strong>${cap}</strong>.`);
    fetchState(true);
    return;
  }

  try {
    const res = await fetch(`${API_BASE}/api/init`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ capacity: cap })
    });
    badgeCapDisplay.textContent = `Cap: ${cap}`;
    appendLogEntry('info', 'INIT', `Fast memory reinitialized with capacity <strong>${cap}</strong>.`);
    fetchState(true);
  } catch (err) {
    useLocalEngine = true;
    handleInitCapacity();
  }
}

async function handleReset() {
  if (useLocalEngine) {
    localCache.reinitialize(localCache.capacity);
    appendLogEntry('info', 'RESET', `Cache emptied. Capacity preserved at <strong>${localCache.capacity}</strong>.`);
    fetchState(true);
    return;
  }

  try {
    const res = await fetch(`${API_BASE}/api/reset`, { method: 'POST' });
    const data = await res.json();
    appendLogEntry('info', 'RESET', `Cache emptied. Capacity preserved at <strong>${data.capacity}</strong>.`);
    fetchState(true);
  } catch (err) {
    useLocalEngine = true;
    handleReset();
  }
}

async function handleGet(e) {
  if (e) e.preventDefault();
  const key = getKeyInput.value.trim();
  if (!key) return;

  if (useLocalEngine) {
    const op = localCache.get(key);
    handleOpResultLog(op);
    getKeyInput.value = '';
    return;
  }

  try {
    const res = await fetch(`${API_BASE}/api/get`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ key })
    });
    const op = await res.json();
    handleOpResultLog(op);
    getKeyInput.value = '';
  } catch (err) {
    useLocalEngine = true;
    handleGet(e);
  }
}

async function handlePut(e) {
  if (e) e.preventDefault();
  const key = putKeyInput.value.trim();
  const value = putValInput.value.trim();
  if (!key || !value) return;

  if (useLocalEngine) {
    const op = localCache.put(key, value);
    handleOpResultLog(op);
    putKeyInput.value = '';
    putValInput.value = '';
    return;
  }

  try {
    const res = await fetch(`${API_BASE}/api/put`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ key, value })
    });
    const op = await res.json();
    handleOpResultLog(op);
    putKeyInput.value = '';
    putValInput.value = '';
  } catch (err) {
    useLocalEngine = true;
    handlePut(e);
  }
}

function handleOpResultLog(op) {
  if (op.operation === 'GET') {
    if (op.hit) {
      appendLogEntry('hit', 'HIT', `<strong>GET(${op.key})</strong>: HIT &bull; Value: <code>${op.value}</code> &bull; Freq: ${op.oldFrequency} &rarr; <strong>${op.newFrequency}</strong>`);
    } else {
      appendLogEntry('miss', 'MISS', `<strong>GET(${op.key})</strong>: CACHE MISS &bull; Not present in fast memory.`);
    }
  } else if (op.operation === 'PUT') {
    let msg = `<strong>PUT(${op.key}, ${op.value})</strong>: `;
    if (op.evictedKey) {
      msg += `<span style="color:#fbbf24;">Evicted '${op.evictedKey}'</span> (LFU freq=${op.evictedFrequency}, LRU tie-breaker). `;
    }
    msg += op.message;
    const type = op.evictedKey ? 'evict' : 'put';
    appendLogEntry(type, op.evictedKey ? 'EVICT' : 'PUT', msg);
  }
}

async function fetchState(userInitiated = false) {
  if (useLocalEngine) {
    const state = localCache.getState();
    renderState(state);
    if (userInitiated) {
      appendLogEntry('info', 'STATE', `Cache state inspected on demand: <strong>${state.currentSize} / ${state.capacity}</strong> documents stored.`);
    }
    return;
  }

  try {
    const res = await fetch(`${API_BASE}/api/state`);
    const state = await res.json();
    renderState(state);
    if (userInitiated) {
      appendLogEntry('info', 'STATE', `Cache state inspected on demand: <strong>${state.currentSize} / ${state.capacity}</strong> documents stored.`);
    }
  } catch (err) {
    useLocalEngine = true;
    fetchState(userInitiated);
  }
}

function renderState(state) {
  lblStateTime.textContent = `State: Synced at ${getFormattedTime()}`;

  capInput.value = state.capacity;
  badgeCapDisplay.textContent = `Cap: ${state.capacity}`;
  const util = state.stats.utilizationPercent || 0;
  txtUtilization.textContent = `${state.currentSize} / ${state.capacity} (${util}%)`;
  capProgressFill.style.width = `${Math.min(100, util)}%`;

  statGet.textContent = state.stats.totalGetRequests || 0;
  statHits.textContent = state.stats.cacheHits || 0;
  statMisses.textContent = state.stats.cacheMisses || 0;
  statPuts.textContent = state.stats.totalPutRequests || 0;
  statEvictions.textContent = state.stats.totalEvictions || 0;
  statMinFreq.textContent = state.minFrequency || 0;
  badgeHitRatio.textContent = `${state.stats.hitRatioPercent || 0.0}% Hit Ratio`;

  if (!state.frequencyGroups || state.frequencyGroups.length === 0) {
    visualizerContainer.innerHTML = `
      <div class="empty-state">
        <div class="empty-icon">📭</div>
        <div class="empty-title">Cache is Currently Empty</div>
        <div class="empty-desc">Insert documents via PUT or run the Hackathon Demonstration to populate fast memory.</div>
      </div>
    `;
    return;
  }

  let html = `<div class="freq-groups-list">`;
  state.frequencyGroups.forEach(group => {
    const isMinFreqGroup = (group.frequency === state.minFrequency);
    html += `
      <div class="freq-group-card">
        <div class="freq-group-header">
          <div class="freq-title-badge">
            <span class="freq-number">Freq: ${group.frequency}</span>
            <span class="freq-label">${group.nodes.length} document${group.nodes.length > 1 ? 's' : ''}</span>
            ${isMinFreqGroup ? '<span class="badge badge-purple" style="font-size:0.68rem;">minFrequency List</span>' : ''}
          </div>
          <div class="freq-meta">Head &larr; MRU &bull; LRU &rarr; Tail</div>
        </div>
        <div class="nodes-chain">
    `;

    group.nodes.forEach((node, idx) => {
      let cardClasses = 'node-card';
      if (node.isNextEvictionCandidate) cardClasses += ' is-eviction-candidate';
      if (node.isLruInGroup) cardClasses += ' is-lru-group';
      if (idx === 0) cardClasses += ' is-mru-group';

      let badgesHtml = '<div class="node-pill-strip">';
      if (node.isNextEvictionCandidate) {
        badgesHtml += '<span class="legend-tag legend-target">&bull; Next Eviction</span>';
      }
      if (idx === 0) {
        badgesHtml += '<span class="legend-tag legend-mru">MRU</span>';
      }
      if (node.isLruInGroup) {
        badgesHtml += '<span class="legend-tag legend-lru">LRU in Freq</span>';
      }
      badgesHtml += '</div>';

      html += `
        <div class="${cardClasses}" title="Key: ${node.key}, Value: ${node.value}, Frequency: ${node.frequency}">
          ${badgesHtml}
          <div class="node-key">${escapeHtml(node.key)}</div>
          <div class="node-value">${escapeHtml(node.value)}</div>
        </div>
      `;

      if (idx < group.nodes.length - 1) {
        html += `<span class="node-connector">&harr;</span>`;
      }
    });

    html += `
        </div>
      </div>
    `;
  });

  html += `</div>`;
  visualizerContainer.innerHTML = html;
}

function escapeHtml(str) {
  if (!str) return '';
  return str.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;");
}

function parseClientCommand(line) {
  if (!line) return null;
  const raw = line.trim();
  if (!raw || raw.startsWith('#') || raw.startsWith('//')) return null;

  const upper = raw.toUpperCase();
  if (upper === 'STATE' || upper === 'STATE()') return { type: 'STATE' };
  if (upper === 'RESET' || upper === 'RESET()') return { type: 'RESET' };

  if (upper.startsWith('GET(') && raw.endsWith(')')) {
    const key = raw.substring(4, raw.length - 1).trim();
    return { type: 'GET', key };
  }

  if (upper.startsWith('PUT(') && raw.endsWith(')')) {
    const inside = raw.substring(4, raw.length - 1).trim();
    const comma = inside.indexOf(',');
    if (comma !== -1) {
      return {
        type: 'PUT',
        key: inside.substring(0, comma).trim(),
        value: inside.substring(comma + 1).trim()
      };
    }
  }

  if (upper.startsWith('CAPACITY(') && raw.endsWith(')')) {
    const inside = raw.substring(9, raw.length - 1).trim();
    const cap = parseInt(inside, 10);
    if (!isNaN(cap)) return { type: 'CAPACITY', capacity: cap };
  }

  return { type: 'UNKNOWN', raw };
}

async function handleProcessSequence() {
  const text = sequenceInput.value.trim();
  if (!text) {
    alert('Please enter one or more commands in the sequence box.');
    return;
  }

  appendLogEntry('info', 'SEQ', `Processing batch operation sequence...`);

  if (useLocalEngine) {
    const lines = text.split('\n');
    let stateRequested = false;
    let count = 0;
    lines.forEach(line => {
      const cmd = parseClientCommand(line);
      if (!cmd) return;
      count++;
      if (cmd.type === 'PUT') {
        const op = localCache.put(cmd.key, cmd.value);
        handleOpResultLog(op);
      } else if (cmd.type === 'GET') {
        const op = localCache.get(cmd.key);
        handleOpResultLog(op);
      } else if (cmd.type === 'CAPACITY') {
        localCache.reinitialize(cmd.capacity);
        appendLogEntry('info', 'CAP', `Capacity set to ${cmd.capacity}`);
      } else if (cmd.type === 'RESET') {
        localCache.reinitialize(localCache.capacity);
        appendLogEntry('info', 'RESET', 'Cache reset');
      } else if (cmd.type === 'STATE') {
        stateRequested = true;
      }
    });

    if (stateRequested) {
      renderState(localCache.getState());
      appendLogEntry('info', 'STATE', `Explicit STATE rendered from sequence.`);
    } else {
      appendLogEntry('info', 'SEQ', `Executed ${count} operations. Click 'Show Cache State' to view updated contents.`);
    }
    return;
  }

  try {
    const res = await fetch(`${API_BASE}/api/sequence`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ sequence: text })
    });
    const data = await res.json();
    if (data.results) data.results.forEach(op => handleOpResultLog(op));
    if (data.stateRequested && data.state) {
      renderState(data.state);
      appendLogEntry('info', 'STATE', `Explicit STATE rendered from sequence.`);
    } else {
      appendLogEntry('info', 'SEQ', `Executed ${data.executedCount} operations. Click 'Show Cache State' to view updated contents.`);
    }
  } catch (err) {
    useLocalEngine = true;
    handleProcessSequence();
  }
}

async function runDemo(demoType) {
  appendLogEntry('info', 'DEMO', `Executing Demonstration ${demoType}...`);

  if (useLocalEngine) {
    if (demoType === 1) {
      localCache.reinitialize(2);
      handleOpResultLog(localCache.put('A', '10'));
      handleOpResultLog(localCache.put('B', '20'));
      handleOpResultLog(localCache.get('A'));
      handleOpResultLog(localCache.put('C', '30'));
      renderState(localCache.getState());
      appendLogEntry('hit', 'DEMO 1', `<strong>Result:</strong> GET(A)=10. B evicted (B freq=1 vs A freq=2). Final Cache: A(freq 2), C(freq 1).`);
    } else {
      localCache.reinitialize(2);
      handleOpResultLog(localCache.put('Doc_Alpha', 'Syllabus.pdf'));
      handleOpResultLog(localCache.put('Doc_Beta', 'Timetable.pdf'));
      handleOpResultLog(localCache.put('Doc_Gamma', 'CampusMap.pdf'));
      renderState(localCache.getState());
      appendLogEntry('hit', 'DEMO 2', `<strong>Result:</strong> Doc_Alpha evicted because of LRU recency tie-breaking among frequency 1 ties.`);
    }
    return;
  }

  try {
    const res = await fetch(`${API_BASE}/api/demo?type=${demoType}`);
    const data = await res.json();
    if (data.results) data.results.forEach(op => handleOpResultLog(op));
    if (data.state) renderState(data.state);

    if (demoType === 1) {
      appendLogEntry('hit', 'DEMO 1', `<strong>Result:</strong> GET(A)=10. B evicted (B freq=1 vs A freq=2). Final Cache: A(freq 2), C(freq 1).`);
    } else {
      appendLogEntry('hit', 'DEMO 2', `<strong>Result:</strong> Doc_Alpha evicted because of LRU recency tie-breaking among frequency 1 ties.`);
    }
  } catch (err) {
    useLocalEngine = true;
    runDemo(demoType);
  }
}
