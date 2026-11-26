/**
 * CampusCache Frontend Client Application
 * Communicates with backend REST API and renders real-time visualizations,
 * sequence parser, audit logging, and demonstrations.
 */

const API_BASE = ''; // Same-origin relative URLs for seamless serving

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

// Initial Setup
document.addEventListener('DOMContentLoaded', () => {
  setupEventListeners();
  // Fetch initial state once on load
  fetchState();
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

  // Keep max 100 entries
  while (logContainer.children.length > 100) {
    logContainer.removeChild(logContainer.lastChild);
  }
}

function clearLog() {
  logContainer.innerHTML = '';
  appendLogEntry('info', 'SYS', 'Audit log cleared.');
}

// REST Calls
async function handleInitCapacity() {
  const cap = parseInt(capInput.value, 10);
  if (isNaN(cap) || cap < 0 || cap > 200) {
    alert('Please enter a valid capacity between 0 and 200.');
    return;
  }
  try {
    const res = await fetch(`${API_BASE}/api/init`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ capacity: cap })
    });
    const data = await res.json();
    badgeCapDisplay.textContent = `Cap: ${cap}`;
    appendLogEntry('info', 'INIT', `Fast memory reinitialized with capacity <strong>${cap}</strong>.`);
    fetchState(true);
  } catch (err) {
    console.error(err);
    appendLogEntry('miss', 'ERR', `Failed to initialize capacity: ${err.message}`);
  }
}

async function handleReset() {
  try {
    const res = await fetch(`${API_BASE}/api/reset`, { method: 'POST' });
    const data = await res.json();
    appendLogEntry('info', 'RESET', `Cache emptied. Capacity preserved at <strong>${data.capacity}</strong>.`);
    fetchState(true);
  } catch (err) {
    console.error(err);
  }
}

async function handleGet(e) {
  if (e) e.preventDefault();
  const key = getKeyInput.value.trim();
  if (!key) return;

  try {
    const res = await fetch(`${API_BASE}/api/get`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ key })
    });
    const op = await res.json();
    handleOpResultLog(op);
    getKeyInput.value = '';
    // Note: Do not auto fetch state per requirement!
  } catch (err) {
    console.error(err);
  }
}

async function handlePut(e) {
  if (e) e.preventDefault();
  const key = putKeyInput.value.trim();
  const value = putValInput.value.trim();
  if (!key || !value) return;

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
    console.error(err);
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
  try {
    const res = await fetch(`${API_BASE}/api/state`);
    const state = await res.json();
    renderState(state);
    if (userInitiated) {
      appendLogEntry('info', 'STATE', `Cache state inspected on demand: <strong>${state.currentSize} / ${state.capacity}</strong> documents stored.`);
    }
  } catch (err) {
    console.error('Failed to fetch cache state:', err);
  }
}

function renderState(state) {
  lblStateTime.textContent = `State: Synced at ${getFormattedTime()}`;

  // Update Stats & Progress
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

  // Render Frequency Groups and Nodes
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

async function handleProcessSequence() {
  const text = sequenceInput.value.trim();
  if (!text) {
    alert('Please enter one or more commands in the sequence box.');
    return;
  }

  appendLogEntry('info', 'SEQ', `Processing batch operation sequence...`);

  try {
    const res = await fetch(`${API_BASE}/api/sequence`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ sequence: text })
    });
    const data = await res.json();

    if (data.results) {
      data.results.forEach(op => {
        handleOpResultLog(op);
      });
    }

    if (data.stateRequested && data.state) {
      renderState(data.state);
      appendLogEntry('info', 'STATE', `Explicit STATE rendered from sequence.`);
    } else {
      appendLogEntry('info', 'SEQ', `Executed ${data.executedCount} operations. Click 'Show Cache State' to view updated contents.`);
    }
  } catch (err) {
    console.error(err);
    appendLogEntry('miss', 'ERR', `Sequence execution failed: ${err.message}`);
  }
}

async function runDemo(demoType) {
  appendLogEntry('info', 'DEMO', `Executing Demonstration ${demoType}...`);
  try {
    const res = await fetch(`${API_BASE}/api/demo?type=${demoType}`);
    const data = await res.json();

    if (data.results) {
      data.results.forEach(op => {
        handleOpResultLog(op);
      });
    }

    if (data.state) {
      renderState(data.state);
    }

    if (demoType === 1) {
      appendLogEntry('hit', 'DEMO 1', `<strong>Result:</strong> GET(A)=10. B evicted (B freq=1 vs A freq=2). Final Cache: A(freq 2), C(freq 1).`);
    } else {
      appendLogEntry('hit', 'DEMO 2', `<strong>Result:</strong> Doc_Alpha evicted because of LRU recency tie-breaking among frequency 1 ties.`);
    }
  } catch (err) {
    console.error(err);
  }
}
