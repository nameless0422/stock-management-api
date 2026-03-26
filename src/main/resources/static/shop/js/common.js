'use strict';

// ── API ─────────────────────────────────────────────────────────────────────
async function api(method, path, body) {
  const headers = { 'Content-Type': 'application/json' };
  const token = localStorage.getItem('shop_token');
  if (token) headers['Authorization'] = `Bearer ${token}`;
  const res = await fetch(path, {
    method, headers,
    body: body !== undefined ? JSON.stringify(body) : undefined,
  });
  if (res.status === 204) return { success: true, data: null };
  if (res.status === 401) {
    localStorage.removeItem('shop_token');
    localStorage.removeItem('shop_username');
  }
  return res.json().catch(() => ({ success: false, message: '응답 파싱 오류' }));
}

// ── 인증 ─────────────────────────────────────────────────────────────────────
function isLoggedIn() { return !!localStorage.getItem('shop_token'); }
function getUsername() { return localStorage.getItem('shop_username') ?? ''; }

function requireAuth(redirectBack = true) {
  if (!isLoggedIn()) {
    const back = redirectBack ? `?redirect=${encodeURIComponent(location.href)}` : '';
    location.href = `/shop/index.html${back}`;
    return false;
  }
  return true;
}

async function logout() {
  const token = localStorage.getItem('shop_token');
  if (token) {
    try {
      await fetch('/api/auth/logout', {
        method: 'POST',
        headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
      });
    } catch {}
  }
  localStorage.removeItem('shop_token');
  localStorage.removeItem('shop_username');
  location.href = '/shop/index.html';
}

// ── 유틸 ─────────────────────────────────────────────────────────────────────
function esc(str) {
  return String(str ?? '')
    .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
}
function fmtMoney(n) { return Number(n ?? 0).toLocaleString('ko-KR'); }
function fmtDate(d) {
  if (!d) return '';
  return new Date(d).toLocaleDateString('ko-KR', { year: '2-digit', month: '2-digit', day: '2-digit' });
}
function fmtDateTime(d) {
  if (!d) return '';
  return new Date(d).toLocaleString('ko-KR', { year: '2-digit', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' });
}
function starHtml(n, total = 5) {
  return Array.from({ length: total }, (_, i) =>
    `<i class="bi bi-star${i < n ? '-fill' : ''}" style="color:${i < n ? '#f5a623' : '#ddd'}"></i>`
  ).join('');
}
function orderStatusLabel(s) {
  return { PENDING: '결제대기', CONFIRMED: '결제완료', CANCELLED: '취소됨' }[s] ?? s;
}
function shipmentStatusLabel(s) {
  return { PREPARING: '배송준비', SHIPPED: '배송중', DELIVERED: '배송완료', RETURNED: '반품' }[s] ?? s;
}
function productStatusBadge(status) {
  if (status === 'ACTIVE')       return '<span class="text-success fw-bold small">● 판매중</span>';
  if (status === 'INACTIVE')     return '<span class="text-warning fw-bold small">● 품절</span>';
  if (status === 'DISCONTINUED') return '<span class="text-secondary fw-bold small">● 단종</span>';
  return '';
}

// ── 토스트 ───────────────────────────────────────────────────────────────────
function showToast(msg, type = 'info') {
  let container = document.getElementById('toast-container');
  if (!container) {
    container = document.createElement('div');
    container.id = 'toast-container';
    container.style.cssText = 'position:fixed;bottom:1.2rem;right:1.2rem;z-index:9999;min-width:200px';
    document.body.appendChild(container);
  }
  const el = document.createElement('div');
  el.className = `my-toast${type === 'danger' ? ' danger' : type === 'success' ? ' success' : ''}`;
  el.textContent = msg;
  container.appendChild(el);
  setTimeout(() => el.remove(), 3200);
}

// ── 장바구니 뱃지 ─────────────────────────────────────────────────────────────
async function loadCartCount() {
  if (!isLoggedIn()) { updateCartBadge(0); return; }
  try {
    const json = await api('GET', '/api/cart');
    updateCartBadge(json.success ? (json.data?.items?.length ?? 0) : 0);
  } catch { updateCartBadge(0); }
}
function updateCartBadge(count) {
  document.querySelectorAll('.cart-badge').forEach(el => {
    el.textContent = count;
    el.classList.toggle('show', count > 0);
  });
}

// ── 헤더 초기화 ──────────────────────────────────────────────────────────────
function initHeader() {
  const loggedIn = isLoggedIn();

  // 로그인/유저 표시 전환
  document.getElementById('loginOpenBtn')?.classList.toggle('d-none', loggedIn);
  document.getElementById('userInfo')?.classList.toggle('d-none', !loggedIn);
  document.getElementById('myPageBtn')?.classList.toggle('d-none', !loggedIn);
  const nameEl = document.getElementById('userNameDisplay');
  if (nameEl) nameEl.textContent = getUsername();

  // 버튼 이벤트
  document.getElementById('logoutBtn')?.addEventListener('click', logout);
  document.getElementById('myPageBtn')?.addEventListener('click', () => { location.href = '/shop/mypage.html'; });
  document.getElementById('cartBtn')?.addEventListener('click', () => { location.href = '/shop/cart.html'; });
  document.getElementById('wishlistBtn')?.addEventListener('click', () => { location.href = '/shop/mypage.html?tab=wishlist'; });

  // 장바구니 뱃지
  loadCartCount();
}

// ── 로딩/빈 상태 ─────────────────────────────────────────────────────────────
function setLoading(btn, loading, loadingText = '처리 중...') {
  if (loading) {
    btn.disabled = true;
    btn._origText = btn.innerHTML;
    btn.innerHTML = `<span class="spinner-border spinner-border-sm me-1"></span>${loadingText}`;
  } else {
    btn.disabled = false;
    if (btn._origText) btn.innerHTML = btn._origText;
  }
}

// ── 페이지네이션 ─────────────────────────────────────────────────────────────
function renderPager(containerId, page, totalPages, onPage) {
  const el = document.getElementById(containerId);
  if (!el) return;
  if (totalPages <= 1) { el.innerHTML = ''; return; }
  const start = Math.max(0, page - 4);
  const end   = Math.min(totalPages - 1, page + 4);
  const btns  = [
    `<button class="page-btn ${page === 0 ? 'disabled' : ''}" data-p="${page - 1}"><i class="bi bi-chevron-left"></i></button>`,
    ...Array.from({ length: end - start + 1 }, (_, i) => {
      const p = start + i;
      return `<button class="page-btn ${p === page ? 'active' : ''}" data-p="${p}">${p + 1}</button>`;
    }),
    `<button class="page-btn ${page >= totalPages - 1 ? 'disabled' : ''}" data-p="${page + 1}"><i class="bi bi-chevron-right"></i></button>`,
  ];
  el.innerHTML = btns.join('');
  el.querySelectorAll('.page-btn[data-p]:not(.disabled):not(.active)').forEach(btn => {
    btn.addEventListener('click', () => onPage(+btn.dataset.p));
  });
}
