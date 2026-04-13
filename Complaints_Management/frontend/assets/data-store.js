const CMS = (() => {


  const SEED_AUTH_USERS = [];
  const SEED_COMPLAINTS = [];
  const SEED_ADMIN_REQUESTS = [];
  const SEED_ADMIN_SA_MESSAGES = [];

  const BASE_URL = 'http://localhost:8080/api';

  // Init
  function init() {
    // MySQL is the source of truth
  }

  // ── Auth Functions ──
  async function login(username, password) {
    try {
      const response = await fetch(`${BASE_URL}/auth/login`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username, password })
      });

      if (response.ok) {
        const user = await response.json();
        const session = { 
          ...user, 
          role: user.role ? user.role.toLowerCase() : 'admin'
        };
        localStorage.setItem('cms_session', JSON.stringify(session));
        return session;
      }
    } catch (error) {
      console.error('MySQL login failed:', error);
    }
    return null;
  }

  function logout() {
    localStorage.removeItem('cms_session');
  }

  function getSession() {
    const s = localStorage.getItem('cms_session');
    if (!s) return null;
    const session = JSON.parse(s);
    if (session && session.role) session.role = session.role.toLowerCase();
    return session;
  }


  function requireAuth(allowedRoles) {
    const session = getSession();
    if (!session) { window.location.href = 'login.html'; return null; }
    if (allowedRoles && !allowedRoles.includes(session.role)) {
      window.location.href = 'login.html'; return null;
    }
    return session;
  }

  function getAuthUsers() {
    return JSON.parse(localStorage.getItem('cms_auth_users') || '[]');
  }

  function saveAuthUsers(users) {
    localStorage.setItem('cms_auth_users', JSON.stringify(users));
  }

  function updateUserProfile(id, patch) {
    const users = getAuthUsers();
    const idx = users.findIndex(u => u.id === id);
    if (idx === -1) return false;
    users[idx] = { ...users[idx], ...patch };
    saveAuthUsers(users);
    // Update session too
    const session = getSession();
    if (session && session.id === id) {
      const updated = { ...session, ...patch };
      localStorage.setItem('cms_session', JSON.stringify(updated));
    }
    return users[idx];
  }

  function getUserById(id) {
    return getAuthUsers().find(u => u.id === id) || null;
  }

  // ── Complaint CRUD ──
  // ── Cache ── (MySQL is now the ONLY source of truth)
  let cachedComplaints = [];

  function getComplaints() {
    return cachedComplaints;
  }

  function getComplaintById(id) {
    return cachedComplaints.find(c => (c.dbId || c.id) == id) || null;
  }

  async function updateComplaint(id, patch) {
    try {
      const response = await fetch(`${BASE_URL}/admin/complaints/${id}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(patch)
      });
      if (response.ok) {
        await fetchLiveComplaints();
        return await response.json();
      }
    } catch (e) { console.error('Backend update failed:', e); }
    return false;
  }

  async function deleteComplaint(id) {
    try {
      const response = await fetch(`${BASE_URL}/admin/complaints/${id}`, { method: 'DELETE' });
      if (response.ok) {
        await fetchLiveComplaints();
        return true;
      }
    } catch (e) { console.error('Backend delete failed:', e); }
    return false;
  }

  // ── Admin Requests ──
  function getAdminRequests() {
    return JSON.parse(localStorage.getItem('cms_admin_requests') || '[]');
  }

  function saveAdminRequests(list) {
    localStorage.setItem('cms_admin_requests', JSON.stringify(list));
  }

  function approveAdminRequest(reqId, approvedName, approvedEmail) {
    const list = getAdminRequests();
    const idx = list.findIndex(r => r.id === reqId);
    if (idx === -1) return false;
    list[idx].status = 'APPROVED';
    saveAdminRequests(list);

    const req = list[idx];
    const users = getAuthUsers();
    const newAdmin = {
      id: 'ADM-' + Date.now(),
      username: req.username,
      password: 'admin@' + req.username.slice(0, 4),
      role: 'admin',
      name: req.name,
      email: req.email,
      phone: '',
      department: 'Complaint Management',
      joinDate: new Date().toISOString().split('T')[0],
      profilePic: null,
      assignedCompanies: [],
      firstLogin: true
    };
    users.push(newAdmin);
    saveAuthUsers(users);

    addNotification({ role: 'super_admin', type: 'admin_approved', title: 'Admin Approved', body: `You approved ${req.name} as a new admin. They have been notified by email.` });
    return newAdmin;
  }

  function rejectAdminRequest(reqId, reason) {
    const list = getAdminRequests();
    const idx = list.findIndex(r => r.id === reqId);
    if (idx === -1) return false;
    list[idx].status = 'REJECTED';
    list[idx].rejectReason = reason || 'Application did not meet requirements.';
    saveAdminRequests(list);
    addNotification({ role: 'super_admin', type: 'admin_rejected', title: 'Admin Request Rejected', body: `${list[idx].name}'s admin request has been rejected.` });
    return true;
  }

  // ── Responsibility Assignment ──
  function assignResponsibility(companyId, newAdminId) {
    const users = getAuthUsers();
    const oldAdmin = users.find(u => u.role === 'admin' && u.assignedCompanies && u.assignedCompanies.includes(companyId));
    const newAdmin = users.find(u => u.id === newAdminId);

    if (!newAdmin) return false;

    if (oldAdmin) {
      oldAdmin.assignedCompanies = oldAdmin.assignedCompanies.filter(c => c !== companyId);
      addNotification({
        role: 'admin',
        targetAdminId: oldAdmin.id,
        type: 'responsibility_removed',
        title: '⚠️ Responsibility Changed',
        body: `Your responsibility for company ${companyId} has been transferred. Super Admin has made this change.`
      });
    }

    if (!newAdmin.assignedCompanies) newAdmin.assignedCompanies = [];
    if (!newAdmin.assignedCompanies.includes(companyId)) {
      newAdmin.assignedCompanies.push(companyId);
    }
    saveAuthUsers(users);

    return true;
  }

  // ── Notifications ──
  function getNotifications(role, adminId) {
    const all = JSON.parse(localStorage.getItem('cms_notifications') || '[]');
    if (adminId) return all.filter(n => n.targetAdminId === adminId || (n.role === role && !n.targetAdminId));
    if (role) return all.filter(n => n.role === role);
    return all;
  }

  function addNotification(notif) {
    const list = JSON.parse(localStorage.getItem('cms_notifications') || '[]');
    list.unshift({ id: Date.now(), read: false, at: new Date().toISOString(), ...notif });
    localStorage.setItem('cms_notifications', JSON.stringify(list));
  }

  function markNotifRead(id) {
    const list = JSON.parse(localStorage.getItem('cms_notifications') || '[]').map(n => n.id === id ? { ...n, read: true } : n);
    localStorage.setItem('cms_notifications', JSON.stringify(list));
  }

  function unreadCount(role, adminId) {
    return getNotifications(role, adminId).filter(n => !n.read).length;
  }

  // ── Business Logic ──
  async function approveComplaint(id, adminName) {
    return updateComplaint(id, { status: 'APPROVED' });
  }

  async function rejectComplaint(id, reason, adminName) {
    return updateComplaint(id, { status: 'REJECTED', rejectionReason: reason });
  }

  async function forwardComplaint(id, priority, adminName) {
    return updateComplaint(id, { status: 'FORWARDED', priority: priority.toUpperCase() });
  }

  function markViewed(id) {
    return updateComplaint(id, { status: 'VIEWED' });
  }

  function startInvestigation(id) {
    return updateComplaint(id, { status: 'IN_PROGRESS' });
  }

  function submitResolution(id, docName) {
    return updateComplaint(id, { status: 'RESOLVED' });
  }

  function approveResolution(id, adminName) {
    return updateComplaint(id, { status: 'RESOLVED' });
  }

  function rejectResolution(id, reason, adminName) {
    return updateComplaint(id, { status: 'IN_PROGRESS' });
  }

  function runOverdueCheck() {
    // Handled by backend
  }

  // ── Messaging ──
  function sendMessage(complaintId, channel, sender, text, senderName, isSuperAdmin) {
    return true;
  }

  function markMessagesRead(complaintId, channel, readerRole) {
  }

  // ── Admin-SuperAdmin Messages ──
  function getAdminSAMessages() {
    return JSON.parse(localStorage.getItem('cms_admin_sa_messages') || '[]');
  }

  function saveAdminSAMessages(list) {
    localStorage.setItem('cms_admin_sa_messages', JSON.stringify(list));
  }

  function getThreadBetween(id1, id2) {
    const all = getAdminSAMessages();
    return all.filter(m => (m.from === id1 && m.to === id2) || (m.from === id2 && m.to === id1));
  }

  function sendAdminSAMessage(fromId, toId, text) {
    const all = getAdminSAMessages();
    const msg = { id: Date.now(), from: fromId, to: toId, text, at: new Date().toISOString(), read: false, status: 'sent' };
    all.push(msg);
    saveAdminSAMessages(all);
    return msg;
  }

  function markAdminSAMessagesRead(fromId, toId) {
    const all = getAdminSAMessages().map(m => {
      if (m.from === fromId && m.to === toId && !m.read) {
        return { ...m, read: true, status: 'read' };
      }
      return m;
    });
    saveAdminSAMessages(all);
  }

  function getUnreadAdminSACount(toId) {
    return getAdminSAMessages().filter(m => m.to === toId && !m.read).length;
  }

  // ── Helpers ──
  function getVisibleStatus(complaint, viewerRole) {
    return complaint.status;
  }

  function fmtDate(iso) {
    if (!iso) return '—';
    return new Date(iso).toLocaleDateString('en-IN', { day: '2-digit', month: 'short', year: 'numeric' });
  }

  function fmtDateTime(iso) {
    if (!iso) return '—';
    return new Date(iso).toLocaleString('en-IN', { day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit' });
  }

  function statusBadge(status, viewerRole) {
    const map = {
      PENDING: ['badge-pending', 'bi-clock', 'Pending'],
      APPROVED: ['badge-approved', 'bi-check-circle', 'Approved'],
      REJECTED: ['badge-rejected', 'bi-x-circle', 'Rejected'],
      FORWARDED: ['badge-forwarded', 'bi-send', 'Forwarded'],
      VIEWED: ['badge-viewed', 'bi-eye', 'Viewed'],
      IN_PROGRESS: ['badge-inprogress', 'bi-arrow-repeat', 'In Progress'],
      OVERDUE: ['badge-overdue', 'bi-exclamation-circle', 'Overdue'],
      RESOLVED: ['badge-resolved', 'bi-shield-check', 'Resolved']
    };
    const [cls, icon, label] = map[status] || ['badge-pending', 'bi-question', 'Unknown'];
    return `<span class="status-badge ${cls}"><i class="bi ${icon}"></i>${label}</span>`;
  }

  function priorityBadge(priority) {
    if (!priority) return '<span class="text-muted-cms">—</span>';
    const map = { LOW: 'priority-low', MEDIUM: 'priority-medium', HIGH: 'priority-high' };
    return `<span class="status-badge ${map[priority] || ''}">${priority}</span>`;
  }

  function evidenceIcon(type) {
    const m = {
      pdf: 'bi-file-earmark-pdf text-danger',
      docx: 'bi-file-earmark-word text-primary',
      xlsx: 'bi-file-earmark-excel text-success',
      zip: 'bi-file-zip text-warning'
    };
    return m[type] || 'bi-file-earmark';
  }

  // ── Backend Sync ──
  async function fetchLiveComplaints() {
    try {
      const response = await fetch(`${BASE_URL}/admin/complaints`);
      if (response.ok) {
        const liveData = await response.json();
        cachedComplaints = liveData.map(c => ({
          ...c,
          displayId: `CMP-${String(c.id).padStart(3, '0')}`,
          submittedAt: c.createdAt || new Date().toISOString(),
          statusHistory: [{ status: c.status, at: c.createdAt || new Date().toISOString(), by: 'System' }],
          messages: { employee: [], company: [] }
        }));
        return cachedComplaints;
      }
    } catch (e) {
      console.error('Failed to fetch from MySQL:', e);
    }
    return cachedComplaints;
  }

  let cachedAdmins = [];
  async function fetchLiveAdmins() {
    try {
      const resp = await fetch(`${BASE_URL}/users/admins`);
      if (resp.ok) {
        cachedAdmins = await resp.json();
        saveAuthUsers(cachedAdmins); // Sync to local storage for components that use it
        return cachedAdmins;
      }
    } catch (e) { console.error('Failed to fetch admins:', e); }
    return cachedAdmins;
  }

  function getAdmins() {
    return cachedAdmins;
  }

  return {
    init, login, logout, getSession, requireAuth,
    getAuthUsers, updateUserProfile, getUserById, getAdmins,
    getComplaints, getComplaintById, updateComplaint, deleteComplaint,
    approveComplaint, rejectComplaint, forwardComplaint,
    markViewed, startInvestigation, submitResolution,
    approveResolution, rejectResolution, runOverdueCheck,
    getAdminRequests, saveAdminRequests, approveAdminRequest, rejectAdminRequest,
    assignResponsibility,
    getNotifications, addNotification, markNotifRead, unreadCount,
    sendMessage, markMessagesRead,
    getAdminSAMessages, sendAdminSAMessage, markAdminSAMessagesRead, getThreadBetween, getUnreadAdminSACount,
    getVisibleStatus, fmtDate, fmtDateTime,
    statusBadge, priorityBadge, evidenceIcon,
    fetchLiveComplaints, fetchLiveAdmins,
    syncLocalStorageToDB
  };

  // ── Data Migration Logic ──
  async function syncLocalStorageToDB() {
    const local = JSON.parse(localStorage.getItem('cms_complaints') || '[]');
    if (!local.length) return { success: true, count: 0 };
    
    let synced = 0;
    for (const c of local) {
      if (c.dbId) continue; 
      try {
        const resp = await fetch(`${BASE_URL}/admin/complaints/sync`, {
          method: 'POST',
          headers: {'Content-Type': 'application/json'},
          body: JSON.stringify({
            title: c.title,
            description: c.description,
            category: c.category,
            employeeId: c.employeeId,
            employeeName: c.employeeName,
            employeeEmail: c.employeeEmail,
            employeeDept: c.employeeDept,
            companyId: c.companyId,
            companyName: c.companyName,
            status: c.status,
            priority: c.priority,
            dueDate: c.dueDate
          })
        });
        if (resp.ok) {
          const saved = await resp.json();
          c.dbId = saved.id;
          synced++;
        }
      } catch (e) { console.error(e); }
    }
    if (synced > 0) {
      localStorage.setItem('cms_complaints', JSON.stringify(local));
      await fetchLiveComplaints();
    }
    return { success: true, count: synced };
  }
})();

CMS.init();
CMS.runOverdueCheck();

// ── Global Utilities ──

function showToast(message, type = 'info', duration = 4000) {
  const container = document.getElementById('toast-container');
  if (!container) return;
  const icons = { success: 'bi-check-circle-fill', error: 'bi-x-circle-fill', info: 'bi-info-circle-fill', warning: 'bi-exclamation-triangle-fill' };
  const colors = { success: '#4ade80', error: '#f87171', info: '#22d3ee', warning: '#fbbf24' };
  const el = document.createElement('div');
  el.className = `toast-item toast-${type}`;
  el.innerHTML = `<i class="bi ${icons[type]}" style="color:${colors[type]};flex-shrink:0"></i><span>${message}</span>`;
  container.appendChild(el);
  setTimeout(() => { el.style.animation = 'fadeOut 0.3s ease forwards'; setTimeout(() => el.remove(), 300); }, duration);
}

function validateFields(configArray) {
  let valid = true;
  configArray.forEach(({ field, msg, rule }) => {
    const el = typeof field === 'string' ? document.getElementById(field) : field;
    const err = el?.parentElement?.querySelector('.invalid-feedback-msg');
    const val = el?.value?.trim();
    let bad = false;
    if (rule === 'required') bad = !val;
    else if (typeof rule === 'function') bad = !rule(val, el);
    if (bad) {
      el?.classList.add('is-invalid');
      if (err) { err.textContent = msg; err.classList.add('show'); }
      valid = false;
    } else {
      el?.classList.remove('is-invalid');
      if (err) err.classList.remove('show');
    }
  });
  return valid;
}

function timeAgo(iso) {
  if (!iso) return '';
  const diff = Date.now() - new Date(iso).getTime();
  const m = Math.floor(diff / 60000);
  if (m < 1) return 'Just now';
  if (m < 60) return `${m}m ago`;
  const h = Math.floor(m / 60);
  if (h < 24) return `${h}h ago`;
  return `${Math.floor(h / 24)}d ago`;
}

function updateNotifBadge() {
  const session = CMS.getSession();
  const n = session ? CMS.unreadCount(session.role, session.id) : 0;
  document.querySelectorAll('.notif-count').forEach(el => {
    el.textContent = n > 99 ? '99+' : n;
    el.style.display = n === 0 ? 'none' : 'flex';
  });
}

function generatePdf(complaintId) {
  const c = CMS.getComplaintById(complaintId);
  if (!c) { showToast('Complaint not found', 'error'); return; }

  // Use the HTML approach to trigger a clean PDF save dialog in browser
  viewPdf(complaintId, true);
}

function viewPdf(complaintId) {
  const c = CMS.getComplaintById(complaintId);
  if (!c) return;

  const content = `
    <!DOCTYPE html>
    <html>
    <head>
      <title>Complaint ${c.id} - Report</title>
      <style>
        body { font-family: 'Segoe UI', sans-serif; max-width: 800px; margin: 40px auto; padding: 20px; color: #1e293b; }
        h1 { color: #2563eb; border-bottom: 3px solid #2563eb; padding-bottom: 10px; }
        .badge { display: inline-block; padding: 4px 12px; border-radius: 999px; font-size: 12px; font-weight: 700; }
        .badge-pending { background: #fef3c7; color: #b45309; }
        .badge-approved { background: #dcfce7; color: #16a34a; }
        .badge-forwarded { background: #dbeafe; color: #2563eb; }
        .badge-resolved { background: #dcfce7; color: #15803d; }
        .badge-rejected { background: #fee2e2; color: #dc2626; }
        .section { margin: 20px 0; padding: 16px; background: #f8fafc; border-radius: 8px; border: 1px solid #e2e8f0; }
        .section h3 { margin: 0 0 10px; color: #64748b; text-transform: uppercase; font-size: 11px; letter-spacing: 1px; }
        .grid { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; }
        .item { }
        .item label { font-size: 11px; color: #94a3b8; text-transform: uppercase; display: block; }
        .item span { font-size: 14px; font-weight: 600; }
        .timeline div { padding: 4px 0; font-size: 13px; }
        @media print { body { margin: 0; } }
      </style>
    </head>
    <body>
      <h1>📋 Complaint Report</h1>
      <div class="section">
        <div class="grid">
          <div class="item"><label>Complaint ID</label><span>${c.id}</span></div>
          <div class="item"><label>Status</label><span class="badge badge-${c.status.toLowerCase()}">${c.status}</span></div>
          <div class="item"><label>Category</label><span>${c.category}</span></div>
          <div class="item"><label>Priority</label><span>${c.priority || '—'}</span></div>
          <div class="item" style="grid-column:span 2"><label>Title</label><span>${c.title}</span></div>
        </div>
      </div>
      <div class="section">
        <h3>Employee Details</h3>
        <div class="grid">
          <div class="item"><label>Name</label><span>${c.employeeName}</span></div>
          <div class="item"><label>ID</label><span>${c.employeeId}</span></div>
          <div class="item"><label>Email</label><span>${c.employeeEmail}</span></div>
          <div class="item"><label>Department</label><span>${c.employeeDept}</span></div>
        </div>
      </div>
      <div class="section">
        <h3>Company</h3>
        <div class="grid">
          <div class="item"><label>Name</label><span>${c.companyName}</span></div>
          <div class="item"><label>ID</label><span>${c.companyId}</span></div>
        </div>
      </div>
      <div class="section">
        <h3>Description</h3>
        <p style="font-size:14px;line-height:1.7">${c.description}</p>
      </div>
      <div class="section">
        <h3>Key Dates</h3>
        <div class="grid">
          <div class="item"><label>Submitted</label><span>${CMS.fmtDateTime(c.submittedAt)}</span></div>
          ${c.approvedAt ? `<div class="item"><label>Approved</label><span>${CMS.fmtDateTime(c.approvedAt)}</span></div>` : ''}
          ${c.forwardedAt ? `<div class="item"><label>Forwarded</label><span>${CMS.fmtDateTime(c.forwardedAt)}</span></div>` : ''}
          ${c.dueDate ? `<div class="item"><label>Due Date</label><span>${CMS.fmtDate(c.dueDate)}</span></div>` : ''}
          ${c.resolvedAt ? `<div class="item"><label>Resolved</label><span>${CMS.fmtDateTime(c.resolvedAt)}</span></div>` : ''}
        </div>
      </div>
      <div class="section">
        <h3>Status History</h3>
        <div class="timeline">
          ${c.statusHistory.map(h => `<div>✦ <b>${h.status}</b> — ${CMS.fmtDateTime(h.at)} by ${h.by}</div>`).join('')}
        </div>
      </div>
      ${c.rejectionReason ? `<div class="section" style="border-color:#fee2e2;background:#fef2f2"><h3 style="color:#dc2626">Rejection Reason</h3><p style="color:#dc2626">${c.rejectionReason}</p></div>` : ''}
      <p style="text-align:center;color:#94a3b8;font-size:12px;margin-top:30px">Generated by Complaint Management System — ${new Date().toLocaleString('en-IN')}</p>
      <p style="text-align:center;margin-top:10px"><button onclick="window.print()" style="background:#2563eb;color:#fff;border:none;padding:10px 24px;border-radius:8px;cursor:pointer;font-size:14px">🖨️ Print / Save as PDF</button></p>
    </body>
    </html>
  `;

  const win = window.open('', '_blank');
  win.document.write(content);
  win.document.close();
  
  // Wait for it to render then open print dialog if autoPrint is true
  if (arguments[1] === true) {
    win.onload = function() {
        setTimeout(() => { win.print(); }, 200);
    };
  }
}

