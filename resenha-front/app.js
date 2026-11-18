const API_BASE = localStorage.getItem('resenha-api') || 'http://localhost:8081';
const tokenKey = 'resenha-token';
const roleKey = 'resenha-account-role';
const $ = (s, root = document) => root.querySelector(s);
const $$ = (s, root = document) => [...root.querySelectorAll(s)];
const escapeHtml = value => String(value ?? '').replace(/[&<>"']/g, char => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[char]));
let venuesCache = [];
let courtsCache = [];
let toastTimer;

function notify(message, kind = 'success') {
  const toast = $('#toast');
  toast.textContent = message;
  toast.className = `toast show ${kind}`;
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => toast.classList.remove('show'), 3600);
}

async function api(path, options = {}) {
  const headers = { ...(options.body ? { 'Content-Type': 'application/json' } : {}), ...(localStorage.getItem(tokenKey) ? { Authorization: `Bearer ${localStorage.getItem(tokenKey)}` } : {}), ...options.headers };
  let response;
  try {
    response = await fetch(`${API_BASE}${path}`, { ...options, headers });
  } catch {
    $('#api-status').textContent = 'backend indisponível';
    $('.status-dot').style.background = '#e66b57';
    throw new Error(`Não consegui conectar à API em ${API_BASE}. Confira se o backend está rodando.`);
  }
  $('#api-status').textContent = `conectada · ${new URL(API_BASE).host}`;
  $('.status-dot').style.background = '#91d444';
  const raw = await response.text();
  let data;
  try { data = raw ? JSON.parse(raw) : null; } catch { data = raw; }
  if (!response.ok) {
    const detail = typeof data === 'string' ? data : data?.message || data?.error || '';
    throw new Error(detail || `${response.status} ${response.statusText}${response.status === 403 ? ' — sua conta não tem permissão para esta ação.' : ''}`);
  }
  return data;
}

const viewsByRole = {
  PLAYER: ['inicio', 'jogador', 'matches', 'bookings'],
  OWNER: ['inicio', 'quadras'],
  ADMIN: ['inicio', 'admin']
};

function canOpen(view) {
  return !!localStorage.getItem(tokenKey) && !!viewsByRole[localStorage.getItem(roleKey)]?.includes(view);
}

function setAuth(isAuthenticated) {
  $('#auth-screen').classList.toggle('hidden', isAuthenticated);
  $('#dashboard').classList.toggle('hidden', !isAuthenticated);
  $$('.content-view').forEach(view => view.classList.add('hidden'));
  $$('[data-view], [data-go], [data-requires-view]').forEach(element => {
    const view = element.dataset.view || element.dataset.go || element.dataset.requiresView;
    element.classList.toggle('hidden', !isAuthenticated || !canOpen(view));
  });
  $('#logout').classList.toggle('hidden', !isAuthenticated);
  if (isAuthenticated) {
    $$('.nav-link[data-view]').forEach(link => link.classList.toggle('active', link.dataset.view === 'inicio'));
    $('#role-pill').textContent = roleLabel(localStorage.getItem(roleKey));
    $('#avatar').textContent = localStorage.getItem('resenha-email')?.[0]?.toUpperCase() || 'R';
    $('#crumb-current').textContent = 'VISÃO GERAL';
    loadOverview();
  }
}

function roleLabel(role) { return ({ PLAYER: 'JOGADOR', OWNER: 'PROPRIETÁRIO', ADMIN: 'ADMINISTRADOR' })[role] || 'JOGADOR'; }

function showView(name) {
  if (!canOpen(name)) return;
  $('#dashboard').classList.toggle('hidden', name !== 'inicio');
  $$('.content-view').forEach(view => view.classList.toggle('hidden', view.id !== `${name}-view` && view.id !== `${name}as-view`));
  if (name === 'quadras') $('#quadras-view').classList.remove('hidden');
  if (name === 'jogador') $('#player-view').classList.remove('hidden');
  if (name === 'admin') $('#admin-view').classList.remove('hidden');
  const titles = { inicio: 'VISÃO GERAL', quadras: 'MINHAS QUADRAS', jogador: 'PERFIL DE JOGADOR', admin: 'JOGADORES', bookings: 'AGENDAMENTOS', matches: 'PARTIDAS' };
  $('#crumb-current').textContent = titles[name] || 'VISÃO GERAL';
  $$('.nav-link[data-view]').forEach(link => link.classList.toggle('active', link.dataset.view === name));
  if (name === 'quadras') loadOwnerData();
  if (name === 'jogador') loadPlayer();
  if (name === 'admin') loadPlayers();
  if (name === 'bookings') loadBookingView();
  if (name === 'matches') loadMatchView();
}

async function loadOverview() {
  const role = localStorage.getItem(roleKey);
  $('#stat-profile').textContent = role === 'PLAYER' ? 'ATIVO' : role === 'OWNER' ? 'DONO' : 'ADMIN';
  $('#stat-profile-note').textContent = roleLabel(role).toLowerCase();
  if (role === 'OWNER') {
    try {
      const page = await api('/venues?size=100');
      const venues = page.content || [];
      $('#stat-venues').textContent = page.totalElements ?? venues.length;
      let totalCourts = 0;
      for (const venue of venues) totalCourts += Number(venue.courtCount || 0);
      $('#stat-courts').textContent = totalCourts;
    } catch (error) { $('#stat-venues').textContent = '—'; $('#stat-courts').textContent = '—'; }
  } else {
    $('#stat-venues').textContent = '—';
    $('#stat-courts').textContent = '—';
  }
  if (role === 'PLAYER') {
    try { await api('/players/me'); $('#stat-profile').textContent = 'OK'; }
    catch { $('#stat-profile').textContent = 'NOVO'; $('#stat-profile-note').textContent = 'cadastre seu perfil'; }
  }
}

function errorMessage(error, target) {
  if (target) target.innerHTML = `<div class="empty-state"><b>Não carregou desta vez.</b>${escapeHtml(error.message)}</div>`;
  else notify(error.message, 'error');
}

$('#login-form').addEventListener('submit', async event => {
  event.preventDefault();
  const form = new FormData(event.currentTarget);
  const message = $('#auth-message');
  message.className = 'auth-message'; message.textContent = 'Conectando à Resenha…';
  try {
    const result = await api('/auth/login', { method: 'POST', body: JSON.stringify({ email: form.get('email'), password: form.get('password') }) });
    localStorage.setItem(tokenKey, result.token);
    localStorage.setItem(roleKey, result.role);
    localStorage.setItem('resenha-email', form.get('email'));
    setAuth(true);
    notify('Boa! Você entrou na Resenha.');
    if (localStorage.getItem(roleKey) === 'PLAYER' && location.hash.startsWith('#invite=')) showView('matches');
  } catch (error) { message.textContent = error.message; }
});

$('#register-form').addEventListener('submit', async event => {
  event.preventDefault();
  const form = new FormData(event.currentTarget);
  const message = $('#auth-message');
  message.className = 'auth-message'; message.textContent = 'Criando sua conta…';
  try {
    await api('/auth/register', { method: 'POST', body: JSON.stringify({ email: form.get('email'), password: form.get('password'), role: form.get('role') }) });
    $('#login-form [name="email"]').value = form.get('email');
    $('#login-form [name="password"]').value = form.get('password');
    message.className = 'auth-message success'; message.textContent = 'Conta criada! Entre com os mesmos dados para continuar.';
    $$('.auth-tab').forEach(tab => tab.classList.toggle('selected', tab.dataset.auth === 'login'));
    $('#login-panel').classList.remove('hidden'); $('#register-panel').classList.add('hidden');
  } catch (error) { message.className = 'auth-message'; message.textContent = error.message; }
});

$$('.auth-tab').forEach(tab => tab.addEventListener('click', () => {
  const registering = tab.dataset.auth === 'register';
  $$('.auth-tab').forEach(item => item.classList.toggle('selected', item === tab));
  $('#login-panel').classList.toggle('hidden', registering);
  $('#register-panel').classList.toggle('hidden', !registering);
  $('#auth-message').textContent = '';
}));

$$('.nav-link[data-view]').forEach(link => link.addEventListener('click', () => showView(link.dataset.view)));
$$('[data-go]').forEach(button => button.addEventListener('click', () => showView(button.dataset.go)));
$('#logout').addEventListener('click', () => { localStorage.removeItem(tokenKey); localStorage.removeItem(roleKey); setAuth(false); notify('Você saiu da conta.'); });

function formatAddress(address) {
  if (!address) return '';
  return [address.street, address.number, address.neighborhood, address.city, address.state].filter(Boolean).join(', ');
}

async function loadOwnerData() {
  if (localStorage.getItem(roleKey) !== 'OWNER') {
    $('#venues-list').innerHTML = '<div class="empty-state"><b>Área de proprietário</b>Entre como dono de estabelecimento para ver e administrar suas quadras.</div>';
    $('#courts-list').innerHTML = '';
    return;
  }
  $('#venues-list').innerHTML = '<div class="empty-state">Buscando seus estabelecimentos…</div>';
  try {
    const page = await api('/venues?size=100');
    venuesCache = page.content || [];
    $('#stat-venues').textContent = page.totalElements ?? venuesCache.length;
    drawVenues();
    await loadCourts();
  } catch (error) { errorMessage(error, $('#venues-list')); }
}

function drawVenues() {
  const list = $('#venues-list');
  if (!venuesCache.length) {
    list.innerHTML = '<div class="empty-state"><b>Seu primeiro espaço começa aqui.</b>Cadastre um estabelecimento para organizar as quadras e receber a galera.<br /><br /><button class="button button-primary" onclick="document.querySelector(\'#venue-dialog\').showModal()">+ CADASTRAR ESPAÇO</button></div>';
    return;
  }
  list.innerHTML = venuesCache.map(venue => `<article class="list-item"><div><h3>${escapeHtml(venue.name)}</h3><p>${escapeHtml(venue.openingTime || '--:--')}–${escapeHtml(venue.closingTime || '--:--')} · ${escapeHtml(venue.courtCount || 0)} quadra(s)<br>${escapeHtml(venue.description || '')}</p></div><div class="list-actions"><button class="mini-button" data-venue-detail="${venue.id}">DETALHES</button><button class="mini-button danger" data-venue-delete="${venue.id}">EXCLUIR</button></div></article>`).join('');
  $$('[data-venue-detail]').forEach(button => button.addEventListener('click', () => showVenueDetails(button.dataset.venueDetail)));
  $$('[data-venue-delete]').forEach(button => button.addEventListener('click', () => deleteVenue(button.dataset.venueDelete)));
}

async function loadCourts() {
  const list = $('#courts-list');
  if (!venuesCache.length) { courtsCache = []; $('#stat-courts').textContent = '0'; list.innerHTML = '<div class="empty-state">Cadastre um estabelecimento para adicionar quadras.</div>'; return; }
  list.innerHTML = '<div class="empty-state">Buscando quadras…</div>';
  try {
    const page = await api('/courts?size=100');
    courtsCache = page.content || [];
    $('#stat-courts').textContent = courtsCache.length;
    drawCourts();
  } catch (error) { errorMessage(error, list); }
}

function drawCourts() {
  const list = $('#courts-list');
  if (!courtsCache.length) { list.innerHTML = '<div class="empty-state"><b>Nenhuma quadra cadastrada</b>Adicione a primeira quadra para completar seu espaço.</div>'; return; }
  list.innerHTML = courtsCache.map(court => `<article class="list-item"><div><h3>${escapeHtml(court.name)}</h3><p>Mínimo: ${escapeHtml(court.minimumParticipants ?? 'não configurado')} participante(s)</p></div><div class="list-actions"><span class="court-type">${escapeHtml(court.type)}</span><button class="mini-button" data-court-edit="${court.id}">CONFIGURAR</button><button class="mini-button" data-court-slots="${court.id}">HORÁRIOS</button><button class="mini-button danger" data-court-delete="${court.id}">EXCLUIR</button></div></article>`).join('');
  $$('[data-court-edit]').forEach(button => button.addEventListener('click', () => editCourt(button.dataset.courtEdit)));
  $$('[data-court-slots]').forEach(button => button.addEventListener('click', () => showCourtSlots(button.dataset.courtSlots)));
  $$('[data-court-delete]').forEach(button => button.addEventListener('click', () => deleteCourt(button.dataset.courtDelete)));
}

async function showVenueDetails(id) {
  try {
    const venue = await api(`/venues/${id}`);
    notify(`${venue.name} · ${venue.openingTime}–${venue.closingTime} · ${formatAddress(venue.address)}`);
  } catch (error) { notify(error.message, 'error'); }
}

async function deleteVenue(id) {
  if (!confirm('Excluir este estabelecimento e as quadras vinculadas?')) return;
  try { await api(`/venues/${id}`, { method: 'DELETE' }); notify('Estabelecimento excluído.'); await loadOwnerData(); }
  catch (error) { notify(error.message, 'error'); }
}
async function deleteCourt(id) {
  if (!confirm('Excluir esta quadra?')) return;
  try { await api(`/courts/${id}`, { method: 'DELETE' }); notify('Quadra excluída.'); await loadCourts(); }
  catch (error) { notify(error.message, 'error'); }
}

$('#new-venue').addEventListener('click', () => {
  if (localStorage.getItem(roleKey) !== 'OWNER') return notify('Entre como proprietário para cadastrar um espaço.', 'error');
  $('#venue-form').reset(); $('#venue-dialog').showModal();
});
$('#refresh-venues').addEventListener('click', loadOwnerData);
$('#new-court').addEventListener('click', () => {
  if (localStorage.getItem(roleKey) !== 'OWNER') return notify('Entre como proprietário para adicionar uma quadra.', 'error');
  if (!venuesCache.length) return notify('Cadastre um estabelecimento primeiro.', 'error');
  const select = $('#court-form [name="venueId"]');
  select.innerHTML = venuesCache.map(venue => `<option value="${venue.id}">${escapeHtml(venue.name)}</option>`).join('');
  $('#court-form').reset(); delete $('#court-form').dataset.id; select.disabled = false; $('#court-dialog h2').textContent = 'Adicionar quadra'; $('#court-dialog').showModal();
});

$('#venue-form').addEventListener('submit', async event => {
  event.preventDefault();
  const form = new FormData(event.currentTarget);
  const address = Object.fromEntries(['street','number','neighborhood','complement','zipCode','city','state'].map(key => [key, form.get(key) || null]));
  const body = { name: form.get('name'), description: form.get('description'), openingTime: form.get('openingTime'), closingTime: form.get('closingTime'), courtCount: Number(form.get('courtCount')), address };
  try { await api('/venues', { method: 'POST', body: JSON.stringify(body) }); $('#venue-dialog').close(); notify('Estabelecimento cadastrado!'); await loadOwnerData(); }
  catch (error) { notify(error.message, 'error'); }
});

$('#court-form').addEventListener('submit', async event => {
  event.preventDefault();
  const form = new FormData(event.currentTarget);
  const body = { name: form.get('name'), type: form.get('type'), venueId: Number(form.get('venueId')), minimumParticipants: Number(form.get('minimumParticipants')) };
  const id = event.currentTarget.dataset.id;
  if (id) body.id = Number(id);
  try { await api('/courts', { method: id ? 'PUT' : 'POST', body: JSON.stringify(body) }); $('#court-dialog').close(); notify('Quadra cadastrada!'); await loadCourts(); }
  catch (error) { notify(error.message, 'error'); }
});

function renderPlayerForm(player = null) {
  const form = $('#player-form');
  if (player) {
    const address = player.address || {};
    $('#player-profile').innerHTML = `<div class="profile-details"><div><span>NOME</span><b>${escapeHtml(player.name)}</b></div><div><span>IDADE</span><b>${escapeHtml(player.age)} anos</b></div><div><span>E-MAIL</span><b>${escapeHtml(player.email)}</b></div><div class="wide"><span>ENDEREÇO</span><b>${escapeHtml(formatAddress(address) || 'Não informado')}</b></div><div><span>STATUS</span><b>${escapeHtml(player.playerStatus || 'ACTIVE')}</b></div></div>`;
    form.innerHTML = `<label>Nome completo<input name="name" value="${escapeHtml(player.name)}" required /></label><label>Idade<input name="age" type="number" min="1" value="${escapeHtml(player.age)}" required /></label><label class="wide">Rua<input name="street" value="${escapeHtml(address.street)}" required /></label><label>Número<input name="number" value="${escapeHtml(address.number)}" /></label><label>Bairro<input name="neighborhood" value="${escapeHtml(address.neighborhood)}" required /></label><label>CEP<input name="zipCode" value="${escapeHtml(address.zipCode)}" pattern="[0-9]{8}" required /></label><label>Complemento<input name="complement" value="${escapeHtml(address.complement)}" /></label><label>Cidade<input name="city" value="${escapeHtml(address.city)}" required /></label><label>Estado<input name="state" value="${escapeHtml(address.state)}" maxlength="2" required /></label><button class="button button-primary" type="submit">SALVAR ALTERAÇÕES ↗</button>`;
    return;
  }
  $('#player-profile').innerHTML = '';
  form.innerHTML = `<label>Nome completo<input name="name" required placeholder="Seu nome" /></label><label>Idade<input name="age" type="number" min="1" required placeholder="Ex.: 25" /></label><label class="wide">E-mail de contato<input name="email" type="email" required value="${escapeHtml(localStorage.getItem('resenha-email'))}" /></label><label class="wide">Rua<input name="street" required /></label><label>Número<input name="number" /></label><label>Bairro<input name="neighborhood" required /></label><label>CEP<input name="zipCode" pattern="[0-9]{8}" maxlength="8" required placeholder="00000000" /></label><label>Complemento<input name="complement" /></label><label>Cidade<input name="city" required /></label><label>Estado<input name="state" maxlength="2" required placeholder="SP" /></label><button class="button button-primary" type="submit">CRIAR PERFIL DE JOGADOR ↗</button>`;
}

async function loadPlayer() {
  if (localStorage.getItem(roleKey) !== 'PLAYER') {
    $('#player-profile').innerHTML = '<div class="empty-state"><b>Área de jogador</b>Entre como jogador para consultar ou cadastrar um perfil.</div>';
    $('#player-form').innerHTML = '';
    return;
  }
  try { const player = await api('/players/me'); renderPlayerForm(player); }
  catch (error) {
    if (error.message.includes('404') || error.message.toLowerCase().includes('não existe') || error.message.toLowerCase().includes('não encontrado')) renderPlayerForm();
    else { $('#player-profile').innerHTML = `<div class="empty-state"><b>Perfil ainda não cadastrado</b>Preencha os dados abaixo para criar seu perfil.<br><small>${escapeHtml(error.message)}</small></div>`; renderPlayerForm(); }
  }
}

$('#player-form').addEventListener('submit', async event => {
  event.preventDefault();
  const form = new FormData(event.currentTarget);
  const address = Object.fromEntries(['street','number','neighborhood','complement','zipCode','city','state'].map(key => [key, form.get(key) || null]));
  const existing = !!$('#player-profile .profile-details');
  const body = { name: form.get('name'), age: Number(form.get('age')), address };
  try {
    if (existing) await api('/players/me', { method: 'PUT', body: JSON.stringify(body) });
    else await api('/players', { method: 'POST', body: JSON.stringify({ ...body, email: form.get('email'), address }) });
    notify(existing ? 'Perfil atualizado.' : 'Perfil criado. Bem-vindo ao time!');
    await loadPlayer();
  } catch (error) { notify(error.message, 'error'); }
});

async function loadPlayers() {
  if (localStorage.getItem(roleKey) !== 'ADMIN') { $('#admin-list').innerHTML = '<div class="empty-state"><b>Acesso de administrador</b>Entre com uma conta ADMIN para consultar o elenco.</div>'; return; }
  $('#admin-list').innerHTML = '<div class="empty-state">Buscando o elenco…</div>';
  try {
    const page = await api('/admin/players?size=100');
    const players = page.content || [];
    if (!players.length) { $('#admin-list').innerHTML = '<div class="empty-state"><b>O elenco ainda está vazio.</b>Novos perfis aparecerão aqui.</div>'; return; }
    $('#admin-list').innerHTML = `<table class="player-table"><thead><tr><th>NOME</th><th>IDADE</th><th>E-MAIL</th><th>STATUS</th><th></th></tr></thead><tbody>${players.map(player => `<tr><td>${escapeHtml(player.name)}</td><td>${escapeHtml(player.age)}</td><td>${escapeHtml(player.email)}</td><td>${escapeHtml(player.playerStatus)}</td><td><button class="mini-button danger" data-player-delete="${player.id}">DESATIVAR</button></td></tr>`).join('')}</tbody></table>`;
    $$('[data-player-delete]').forEach(button => button.addEventListener('click', async () => {
      if (!confirm('Desativar este jogador?')) return;
      try { await api(`/admin/players/${button.dataset.playerDelete}`, { method: 'DELETE' }); notify('Jogador desativado.'); loadPlayers(); }
      catch (error) { notify(error.message, 'error'); }
    }));
  } catch (error) { errorMessage(error, $('#admin-list')); }
}

$('#refresh-players').addEventListener('click', loadPlayers);
$$('[data-auth]').forEach(tab => tab.addEventListener('keydown', event => { if (event.key === 'Enter') tab.click(); }));
if (!canOpen('inicio')) localStorage.removeItem(tokenKey);
setAuth(canOpen('inicio'));


function editCourt(id) {
  const court = courtsCache.find(item => String(item.id) === String(id));
  const form = $('#court-form');
  form.reset(); form.dataset.id = id;
  form.elements.name.value = court.name;
  form.elements.type.value = court.type;
  form.elements.minimumParticipants.value = court.minimumParticipants ?? '';
  form.elements.venueId.innerHTML = venuesCache.map(venue => `<option value="${venue.id}">${escapeHtml(venue.name)}</option>`).join('');
  form.elements.venueId.value = court.venueId;
  form.elements.venueId.disabled = true;
  $('#court-dialog h2').textContent = 'Configurar quadra';
  $('#court-dialog').showModal();
}

async function showCourtSlots(id) {
  $('#slots-dialog').dataset.courtId = id;
  const today = new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Sao_Paulo', year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date());
  $('#slots-date').value = today;
  $('#slots-date').min = today;
  $('#slots-dialog').showModal();
  await loadCourtSlots();
}

let slotsRequestId = 0;
async function loadCourtSlots() {
  const requestId = ++slotsRequestId;
  const dialog = $('#slots-dialog'), date = $('#slots-date').value;
  if (!date) return;
  $('#slots-list').textContent = 'Carregando horários…';
  try {
    const slots = await api(`/courts/${dialog.dataset.courtId}/availability?date=${encodeURIComponent(date)}`);
    if (requestId !== slotsRequestId) return;
    $('#slots-list').innerHTML = slots.length ? slots.map(slot => `<article class="list-item"><div><h3>${escapeHtml(slot.startsAt.replace('T', ' '))} – ${escapeHtml(slot.endsAt.slice(11, 16))}</h3><p>${slot.status === 'RESERVED' ? 'Reservado' : slot.available ? 'Livre' : 'Bloqueado'}</p></div>${slot.status === 'RESERVED' ? '<span class="court-type">RESERVADO</span>' : `<button class="mini-button" data-slot-start="${escapeHtml(slot.startsAt)}" data-slot-available="${slot.available}">${slot.available ? 'BLOQUEAR' : 'LIBERAR'}</button>`}</article>`).join('') : '<p>Nenhum slot futuro de 1 hora neste dia.</p>';
    $$('[data-slot-start]').forEach(button => button.addEventListener('click', async () => {
      button.disabled = true;
      try {
        await api(`/courts/${dialog.dataset.courtId}/availability`, { method: 'PUT', body: JSON.stringify({ startsAt: button.dataset.slotStart, available: button.dataset.slotAvailable !== 'true' }) });
        await loadCourtSlots();
      } catch (error) { button.disabled = false; notify(error.message, 'error'); }
    }));
  } catch (error) { if (requestId === slotsRequestId) errorMessage(error, $('#slots-list')); }
}
$('#slots-date').addEventListener('change', loadCourtSlots);
$('#close-slots').addEventListener('click', () => $('#slots-dialog').close());


let bookingCourtPage = 0, bookingHistoryPage = 0, bookingCourts = [], bookingSlotRequest = 0;
const sportLabel = sport => ({ SOCIETY: 'Society', CAMPO: 'Futebol', FUTSAL: 'Futsal', VOLEI: 'Vôlei', BASQUETE: 'Basquete' })[sport] || sport;
function saoPauloToday() {
  return new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Sao_Paulo', year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date());
}
async function loadBookingView() {
  if (localStorage.getItem(roleKey) !== 'PLAYER') return;
  bookingCourtPage = 0; bookingHistoryPage = 0;
  const today = saoPauloToday();
  const tomorrow = new Date(`${today}T12:00:00Z`); tomorrow.setUTCDate(tomorrow.getUTCDate() + 1);
  $('#booking-date').value = tomorrow.toISOString().slice(0, 10);
  // Yesterday can be the opening date of an overnight operation continuing today.
  const yesterday = new Date(`${today}T12:00:00Z`); yesterday.setUTCDate(yesterday.getUTCDate() - 1);
  $('#booking-date').min = yesterday.toISOString().slice(0, 10);
  $('#booking-message').textContent = '';
  await Promise.all([loadBookingCourts(), loadBookingHistory()]);
}
async function loadBookingCourts() {
  $('#booking-submit').disabled = true;
  try {
    const page = await api(`/availability/courts?page=${bookingCourtPage}`);
    bookingCourts = page.content;
    $('#booking-court').innerHTML = bookingCourts.map(court => `<option value="${court.id}">${escapeHtml(court.venueName)} · ${escapeHtml(court.name)} · ${escapeHtml(sportLabel(court.sport))}</option>`).join('');
    $('#booking-courts-prev').disabled = page.first;
    $('#booking-courts-next').disabled = page.last;
    await loadBookingSlots();
  } catch (error) { $('#booking-message').textContent = error.message; }
}
async function loadBookingSlots() {
  const requestId = ++bookingSlotRequest;
  const court = bookingCourts.find(c => String(c.id) === $('#booking-court').value);
  $('#booking-submit').disabled = true;
  $('#booking-slot').innerHTML = '';
  $('#booking-court-info').textContent = court ? `Mínimo: ${court.minimumParticipants} participantes · Horários de São Paulo` : 'Nenhuma quadra aprovada e configurada disponível.';
  if (!court || !$('#booking-date').value) return;
  try {
    const slots = await api(`/availability/courts/${court.id}?date=${encodeURIComponent($('#booking-date').value)}`);
    if (requestId !== bookingSlotRequest) return;
    const free = slots.filter(slot => slot.available);
    $('#booking-slot').innerHTML = free.map(slot => `<option value="${escapeHtml(slot.startsAt)}">${escapeHtml(slot.startsAt.replace('T', ' '))} – ${escapeHtml(slot.endsAt.replace('T', ' '))}</option>`).join('');
    $('#booking-submit').disabled = free.length === 0;
    $('#booking-message').textContent = free.length ? '' : 'Nenhum horário livre nessa data.';
  } catch (error) { if (requestId === bookingSlotRequest) $('#booking-message').textContent = error.message; }
}
async function loadBookingHistory() {
  try {
    const page = await api(`/bookings?page=${bookingHistoryPage}`);
    $('#bookings-list').innerHTML = page.content.length ? page.content.map(item => `<article class="list-item"><div><h3>Agendamento #${item.id} · ${escapeHtml(sportLabel(item.sport))}</h3><p>${escapeHtml(item.startsAt.replace('T', ' '))} – ${escapeHtml(item.endsAt.replace('T', ' '))}<br>${item.visibility === 'PRIVATE' ? 'Privada' : 'Pública'} · ${item.status === 'PENDING_PAYMENT' ? 'Pagamento pendente' : item.status === 'OPEN' ? 'Aberta' : 'Cancelada'} · Mínimo ${item.minimumParticipants} participantes</p>${item.paymentDue != null ? `<p>Pagamento simulado: ${currency.format(item.paymentAmount)} de ${currency.format(item.paymentDue)}</p>` : ''}</div>${item.status === 'OPEN' ? `<button class="mini-button" data-manage-match="${item.id}">GERENCIAR PARTICIPAÇÃO</button>` : ''}</article>`).join('') : '<p>Você ainda não criou agendamentos.</p>';
    $$('[data-manage-match]').forEach(button => button.addEventListener('click', async () => { showView('matches'); await manageMatch(button.dataset.manageMatch); }));
    $('#bookings-prev').disabled = page.first;
    $('#bookings-next').disabled = page.last;
  } catch (error) { errorMessage(error, $('#bookings-list')); }
}
$('#booking-court').addEventListener('change', loadBookingSlots);
$('#booking-date').addEventListener('change', loadBookingSlots);
$('#booking-courts-prev').addEventListener('click', () => { bookingCourtPage--; loadBookingCourts(); });
$('#booking-courts-next').addEventListener('click', () => { bookingCourtPage++; loadBookingCourts(); });
$('#bookings-prev').addEventListener('click', () => { bookingHistoryPage--; loadBookingHistory(); });
$('#bookings-next').addEventListener('click', () => { bookingHistoryPage++; loadBookingHistory(); });
$('#refresh-bookings').addEventListener('click', loadBookingHistory);
const paymentDialog = $('#payment-dialog');
const currency = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' });
let bookingDraft;
let sendingPayment = false;

$('#booking-form').addEventListener('submit', async event => {
  event.preventDefault();
  const court = bookingCourts.find(c => String(c.id) === $('#booking-court').value);
  if (!court || paymentDialog.open) return;
  const draft = { courtId: court.id, startsAt: $('#booking-slot').value, sport: court.sport,
    visibility: new FormData(event.currentTarget).get('visibility') };
  bookingDraft = draft;
  $('#payment-form').reset();
  $('#payment-error').textContent = '';
  $('#payment-price').textContent = 'Consultando valor…';
  $('#submit-payment').disabled = true;
  paymentDialog.showModal();
  try {
    const price = await api('/bookings/simulated-price');
    if (bookingDraft !== draft) return;
    $('#payment-price').textContent = 'Valor do agendamento: ' + currency.format(price);
    $('#submit-payment').disabled = false;
  } catch (error) { if (bookingDraft === draft) $('#payment-error').textContent = error.message; }
});
$('#cancel-payment').addEventListener('click', () => paymentDialog.close());
paymentDialog.addEventListener('cancel', event => { if (sendingPayment) event.preventDefault(); });
paymentDialog.addEventListener('close', () => { bookingDraft = null; });
window.addEventListener('pagehide', () => { if (paymentDialog.open) paymentDialog.close(); });

$('#payment-form').addEventListener('submit', async event => {
  event.preventDefault();
  if (!bookingDraft || sendingPayment || $('#submit-payment').disabled) return;
  const form = new FormData(event.currentTarget);
  sendingPayment = true;
  $('#submit-payment').disabled = true;
  $('#cancel-payment').disabled = true;
  try {
    const result = await api('/bookings', { method: 'POST', body: JSON.stringify({ ...bookingDraft,
      paymentMethod: form.get('paymentMethod'), paymentAmount: form.get('paymentAmount') }) });
    paymentDialog.close();
    notify(result.status === 'PENDING_PAYMENT' ? 'Agendamento pendente de pagamento.' : 'Pagamento simulado. Partida aberta!');
    bookingHistoryPage = 0;
    await Promise.all([loadBookingHistory(), loadBookingSlots()]);
  } catch (error) {
    $('#payment-error').textContent = error.message;
    await loadBookingSlots();
  } finally {
    sendingPayment = false;
    $('#submit-payment').disabled = false;
    $('#cancel-payment').disabled = false;
  }
});


let matchesPage = 0, requestsPage = 0, participationPage = 0, selectedMatchId = null, activeInvitation = null;
const participationLabel = status => ({ REQUESTED: 'Aguardando aprovação', PENDING_PAYMENT: 'Aguardando pagamento — vaga não garantida', REJECTED: 'Solicitação rejeitada', CONFIRMED: 'Participação confirmada' })[status] || status;
async function loadMatchView() {
  if (localStorage.getItem(roleKey) !== 'PLAYER') return;
  matchesPage = 0; participationPage = 0;
  $('#match-manage').classList.add('hidden'); $('#match-participants').classList.add('hidden');
  await Promise.all([loadMatches(), loadMyParticipations()]);
  if (location.hash.startsWith('#invite=')) {
    try { $('#invitation-code').value = decodeURIComponent(location.hash.slice(8)); await resolveInvitation(); }
    catch (error) { $('#match-message').textContent = error.message; }
  }
}
function renderMatches(matches) {
  $('#matches-list').innerHTML = matches.length ? matches.map(match => `<article class="list-item match-card" data-match-id="${match.id}"><div><h3>${escapeHtml(match.venueName)} · ${escapeHtml(sportLabel(match.sport))}</h3><p>${escapeHtml(match.courtName)} · ${escapeHtml(match.city)}<br>${escapeHtml(match.startsAt.replace('T', ' '))} · ${match.visibility === 'PRIVATE' ? 'Privada — identidades ocultas' : 'Pública'}<br>${match.participantCount} participante(s) confirmado(s) · Mínimo ${match.minimumParticipants}</p>${match.myParticipationStatus ? `<p>${escapeHtml(participationLabel(match.myParticipationStatus))}</p>` : ''}</div><div class="list-actions">${!match.myParticipationStatus && match.joinable ? `<button class="mini-button" data-join-match="${match.id}">${match.visibility === 'PRIVATE' && !match.createdByMe ? 'SOLICITAR ACESSO' : 'PARTICIPAR'}</button>` : ''}${match.createdByMe ? `<button class="mini-button" data-manage-list-match="${match.id}">GERENCIAR</button>` : ''}${match.visibility === 'PUBLIC' || match.createdByMe ? `<button class="mini-button" data-participants-match="${match.id}">PARTICIPANTES</button>` : ''}${!match.joinable ? '<span>Entradas encerradas</span>' : ''}</div></article>`).join('') : '<p>Nenhuma partida encontrada.</p>';
  $$('[data-join-match]').forEach(button => button.addEventListener('click', () => joinMatch(button.dataset.joinMatch, button)));
  $$('[data-manage-list-match]').forEach(button => button.addEventListener('click', () => manageMatch(button.dataset.manageListMatch)));
  $$('[data-participants-match]').forEach(button => button.addEventListener('click', () => loadMatchParticipants(button.dataset.participantsMatch)));
}
async function loadMatches() {
  activeInvitation = null;
  const query = new URLSearchParams();
  for (const [key, value] of new FormData($('#match-filters'))) if (value) query.set(key, value);
  query.set('page', matchesPage);
  try {
    const page = await api(`/matches?${query}`);
    renderMatches(page.content);
    $('#matches-prev').disabled = page.first; $('#matches-next').disabled = page.last;
  } catch (error) { errorMessage(error, $('#matches-list')); }
}
async function joinMatch(id, button) {
  button.disabled = true;
  try {
    const body = activeInvitation && String(activeInvitation.id) === String(id) ? { invitationCode: activeInvitation.code } : {};
    const result = await api(`/matches/${id}/participations`, { method: 'POST', body: JSON.stringify(body) });
    $('#match-message').textContent = participationLabel(result.status);
    await Promise.all([loadMatches(), loadMyParticipations()]);
  } catch (error) { button.disabled = false; $('#match-message').textContent = error.message; }
}
async function manageMatch(id) {
  selectedMatchId = id; requestsPage = 0;
  $('#match-manage').classList.remove('hidden'); $('#match-manage-title').textContent = `Gerenciar partida #${id}`;
  $('#match-invitation-link').value = '';
  try {
    const match = await api(`/matches/${id}`);
    $('#generate-invitation').disabled = !match.joinable || match.visibility !== 'PRIVATE';
    await loadMatchRequests();
  } catch (error) { errorMessage(error, $('#match-requests')); }
}
async function loadMatchRequests() {
  try {
    const page = await api(`/matches/${selectedMatchId}/requests?page=${requestsPage}`);
    $('#match-requests').innerHTML = page.content.length ? page.content.map(item => `<article class="list-item"><div><h3>${escapeHtml(item.name)}</h3><p>${escapeHtml(participationLabel(item.status))}</p></div>${item.status === 'REQUESTED' ? `<div class="list-actions"><button class="mini-button" data-decide-request="${item.id}" data-approved="true">APROVAR</button><button class="mini-button danger" data-decide-request="${item.id}" data-approved="false">REJEITAR</button></div>` : ''}</article>`).join('') : '<p>Nenhuma solicitação.</p>';
    $('#requests-prev').disabled = page.first; $('#requests-next').disabled = page.last;
    $$('[data-decide-request]').forEach(button => button.addEventListener('click', async () => {
      button.disabled = true;
      try {
        await api(`/matches/${selectedMatchId}/requests/${button.dataset.decideRequest}`, { method: 'PATCH', body: JSON.stringify({ approved: button.dataset.approved === 'true' }) });
        await loadMatchRequests();
      } catch (error) { button.disabled = false; $('#match-message').textContent = error.message; }
    }));
  } catch (error) { errorMessage(error, $('#match-requests')); }
}
async function loadMyParticipations() {
  try {
    const page = await api(`/participations/me?page=${participationPage}`);
    $('#participations-list').innerHTML = page.content.length ? page.content.map(item => `<article class="list-item"><div><h3>Partida #${item.matchId}</h3><p>${escapeHtml(participationLabel(item.status))}</p></div></article>`).join('') : '<p>Você ainda não solicitou participação.</p>';
    $('#participations-prev').disabled = page.first; $('#participations-next').disabled = page.last;
  } catch (error) { errorMessage(error, $('#participations-list')); }
}
async function loadMatchParticipants(id, pageNumber = 0) {
  $('#match-participants').classList.remove('hidden');
  try {
    const page = await api(`/matches/${id}/participants?page=${pageNumber}`);
    $('#match-participants').innerHTML = `<h2>Participantes confirmados (${page.totalElements})</h2><p>${page.content.map(item => escapeHtml(item.name)).join(', ') || 'Nenhum participante confirmado por pagamento.'}</p><button id="confirmed-prev" class="mini-button" ${page.first ? 'disabled' : ''}>ANTERIORES</button><button id="confirmed-next" class="mini-button" ${page.last ? 'disabled' : ''}>PRÓXIMOS</button>`;
    $('#confirmed-prev').addEventListener('click', () => loadMatchParticipants(id, pageNumber - 1));
    $('#confirmed-next').addEventListener('click', () => loadMatchParticipants(id, pageNumber + 1));
  } catch (error) { errorMessage(error, $('#match-participants')); }
}
async function resolveInvitation() {
  let code = $('#invitation-code').value.trim();
  if (code.includes('#invite=')) code = decodeURIComponent(code.split('#invite=')[1]);
  try {
    const match = await api('/match-invitations/resolve', { method: 'POST', body: JSON.stringify({ code }) });
    activeInvitation = { id: match.id, code }; renderMatches([match]);
    $('#matches-prev').disabled = true; $('#matches-next').disabled = true;
    $('#match-message').textContent = 'Convite localizado. Solicite acesso para o criador aprovar.';
  } catch (error) { $('#match-message').textContent = error.message; }
}
$('#match-filters').addEventListener('submit', event => { event.preventDefault(); matchesPage = 0; loadMatches(); });
$('#matches-prev').addEventListener('click', () => { matchesPage--; loadMatches(); });
$('#matches-next').addEventListener('click', () => { matchesPage++; loadMatches(); });
$('#requests-prev').addEventListener('click', () => { requestsPage--; loadMatchRequests(); });
$('#requests-next').addEventListener('click', () => { requestsPage++; loadMatchRequests(); });
$('#participations-prev').addEventListener('click', () => { participationPage--; loadMyParticipations(); });
$('#participations-next').addEventListener('click', () => { participationPage++; loadMyParticipations(); });
$('#refresh-participations').addEventListener('click', loadMyParticipations);
$('#invitation-form').addEventListener('submit', event => { event.preventDefault(); resolveInvitation(); });
$('#generate-invitation').addEventListener('click', async () => {
  try {
    const invitation = await api(`/matches/${selectedMatchId}/invitation`, { method: 'POST' });
    $('#match-invitation-link').value = `${location.origin}${location.pathname}#invite=${encodeURIComponent(invitation.code)}`;
  } catch (error) { $('#match-message').textContent = error.message; }
});
if (localStorage.getItem(roleKey) === 'PLAYER' && localStorage.getItem(tokenKey) && location.hash.startsWith('#invite=')) showView('matches');
