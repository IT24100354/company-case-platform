const CMS = (() => {


  const SEED_AUTH_USERS = [];
  const SEED_COMPLAINTS = [];
  const SEED_ADMIN_REQUESTS = [];
  const SEED_ADMIN_SA_MESSAGES = [];

  const BASE_URL = '/api';

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
        sessionStorage.setItem('cms_session', JSON.stringify(session));
        return session;
      }
    } catch (error) {
      console.error('MySQL login failed:', error);
    }
    return null;
  }

  function logout() {
    sessionStorage.removeItem('cms_session');
  }

  function getSession() {
    const s = sessionStorage.getItem('cms_session');
    if (!s) return null;
    const session = JSON.parse(s);
    if (session && session.role) session.role = session.role.toLowerCase();
    return session;
  }


  function requireAuth(allowedRoles) {
    const session = getSession();
    if (!session) { window.location.href = 'login.html'; return null; }
    
    // Normalize role comparison
    const currentRole = (session.role || "").toLowerCase();
    const normalizedAllowed = (allowedRoles || []).map(r => r.toLowerCase());

    if (allowedRoles && !normalizedAllowed.includes(currentRole)) {
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
      sessionStorage.setItem('cms_session', JSON.stringify(updated));
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

  async function fetchComplaintById(id) {
    try {
      const session = getSession();
      if (!session) return null;
      const resp = await fetch(`${BASE_URL}/admin/complaints/${id}?userId=${session.id}`);
      if (resp.ok) {
        const c = await resp.json();
        // Also fetch evidence
        const eResp = await fetch(`${BASE_URL}/admin/complaints/${id}/evidence?userId=${session.id}`);
        if (eResp.ok) {
          c.evidence = await eResp.json();
        }
        return c;
      }
    } catch (e) { console.error('Failed to fetch complaint detail:', e); }
    return null;
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
  async function getNotifications(role, userId) {
    try {
      const resp = await fetch(`${BASE_URL}/notifications?userId=${userId}`);
      const contentType = resp.headers.get("content-type");
      if (resp.ok && contentType && contentType.includes("application/json")) {
        return await resp.json();
      }
    } catch (e) { console.error('Failed fetch notifs:', e); }
    return [];
  }

  async function markNotifRead(id) {
    try {
      const resp = await fetch(`${BASE_URL}/notifications/${id}/read`, { method: 'POST' });
      return resp.ok;
    } catch (e) { console.error(e); }
    return false;
  }

  async function unreadCount(role, userId) {
    try {
        const list = await getNotifications(role, userId);
        if (!Array.isArray(list)) return 0;
        return list.filter(n => !n.read).length;
    } catch (e) { return 0; }
  }

  function addNotification(data) {
    console.log("Local notification added (Legacy):", data);
    if (typeof showToast === 'function') {
      showToast(data.title + ": " + (data.body || data.message), 'info');
    }
  }

  // ── Business Logic ──
  async function approveComplaint(id, adminName) {
    const session = getSession();
    return updateComplaintAction(id, 'approve', {}, session.id);
  }

  async function rejectComplaint(id, reason, adminName) {
    const session = getSession();
    return updateComplaintAction(id, 'reject', { reason }, session.id);
  }

  async function forwardComplaint(id, priority, adminName) {
    const session = getSession();
    return updateComplaintAction(id, 'forward', { priority }, session.id);
  }

  async function updateComplaintAction(id, action, body, userId) {
    try {
      const resp = await fetch(`${BASE_URL}/admin/complaints/${id}/${action}?userId=${userId}`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body)
      });
      if (resp.ok) {
        await fetchLiveComplaints();
        return await resp.json();
      }
    } catch (e) { console.error(`Failed ${action} for ${id}:`, e); }
    return false;
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

  async function approveResolution(id, adminName) {
    const session = getSession();
    try {
      const resp = await fetch(`${BASE_URL}/resolutions/${id}/approve?userId=${session.id}`, { method: 'POST' });
      if (resp.ok) {
        await fetchLiveComplaints();
        return true;
      }
    } catch (e) { console.error(e); }
    return false;
  }

  async function rejectResolution(id, reason, adminName) {
    const session = getSession();
    try {
      const resp = await fetch(`${BASE_URL}/resolutions/${id}/reject?userId=${session.id}`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ reason })
      });
      if (resp.ok) {
        await fetchLiveComplaints();
        return true;
      }
    } catch (e) { console.error(e); }
    return false;
  }

  function runOverdueCheck() {
    // Handled by backend
  }

  // ── Messaging ──
  async function sendMessage(complaintId, channel, sender, text, senderName, isSuperAdmin) {
    const session = getSession();
    const msg = {
      message: text,
      channel: channel
    };
    try {
      const resp = await fetch(`${BASE_URL}/admin/complaints/${complaintId}/chat?userId=${session.id}`, {
        method: 'POST',
        headers: {'Content-Type': 'application/json'},
        body: JSON.stringify(msg)
      });
      if (resp.ok) {
        const saved = await resp.json();
        const c = cachedComplaints.find(comp => String(comp.id) === String(complaintId));
        if (c) {
          if (!c.messages) c.messages = { employee: [], company: [], department: [] };
          // Map backend 'message' to frontend 'text' for compatibility with UI
          const frontendMsg = {
            id: saved.id,
            sender: sender, // role string
            senderName: saved.senderName,
            senderRole: saved.senderRole || sender,
            text: saved.message,
            at: saved.createdAt,
            channel: saved.channel
          };
          const ch = ['company', 'department', 'employee'].includes(saved.channel) ? saved.channel : 'employee';
          if (!c.messages[ch]) c.messages[ch] = [];
          c.messages[ch].push(frontendMsg);
        }
        return true;
      }
    } catch (e) {
      console.error('Failed to send message:', e);
    }
    return false;
  }

  async function markMessagesRead(complaintId, channel) {
    try {
      const session = getSession();
      await fetch(`${BASE_URL}/admin/complaints/${complaintId}/chat/read?userId=${session.id}&channel=${channel}`, {
        method: 'POST'
      });
    } catch (e) {
      console.error('Mark read failed:', e);
    }
  }

  // ── Communication HUB Integration ──
  async function sendMessage(complaintId, channel, senderRole, text, senderName) {
    try {
      const session = getSession();
      const response = await fetch(`${BASE_URL}/admin/complaints/${complaintId}/chat?userId=${session.id}`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          channel: channel,
          message: text,
          senderName: senderName,
          senderRole: senderRole
        })
      });
      return response.ok;
    } catch (e) {
      console.error('Send message failed:', e);
      return false;
    }
  }

  async function fetchLiveAdmins() {
    try {
      const response = await fetch(`${BASE_URL}/users/admins`);
      if (response.ok) return await response.json();
    } catch (e) { console.error('Fetch live admins failed:', e); }
    return [];
  }

  async function fetchLiveComplaints() {
    try {
      const response = await fetch(`${BASE_URL}/admin/complaints?userId=${getSession().id}`);
      if (response.ok) return await response.json();
    } catch (e) { console.error('Fetch live complaints failed:', e); }
    return [];
  }

  function getAdminSAMessages() { return []; }
  function getThreadBetween() { return []; }
  function sendAdminSAMessage() { return {}; }
  function markAdminSAMessagesRead() {}
  function getUnreadAdminSACount() { return 0; }

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
      NOT_VIEWED: ['badge-notviewed', 'bi-eye-slash', 'Not Viewed'],
      VIEWED: ['badge-viewed', 'bi-eye', 'Viewed'],
      IN_PROGRESS: ['badge-inprogress', 'bi-arrow-repeat', 'In Progress'],
      PENDING_RESOLUTION: ['badge-resolutionsent', 'bi-hourglass-split', 'Pending Resolution'],
      MORE_INFO_REQUIRED: ['badge-rejected', 'bi-exclamation-triangle', 'More Info Required'],
      OVERDUE: ['badge-overdue', 'bi-exclamation-circle', 'Overdue'],
      RESOLUTION_SENT: ['badge-resolutionsent', 'bi-send-check', 'Resolution Sent'],
      RESOLUTION_RECEIVED: ['badge-resolutionsent', 'bi-envelope-check', 'Resolution Received'],
      RESOLUTION_REJECTED: ['badge-resolutionrejected', 'bi-x-circle', 'Res. Rejected'],
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
      const session = getSession();
      if (!session) return [];
      console.log(`Fetching live complaints for user ${session.id} from /api/admin/complaints...`);
      const response = await fetch(`${BASE_URL}/admin/complaints?userId=${session.id}`);
      if (response.ok) {
        const liveData = await response.json();
        console.log(`Fetched ${liveData.length} raw complaints.`);
        
        cachedComplaints = await Promise.all(liveData.map(async c => {
          let messages = { employee: [], company: [], department: [] };
          let lastActivity = c.createdAt || new Date().toISOString();
          try {
            // Note: chat fetch is optional and shouldn't block the whole dashboard
            const mResp = await fetch(`${BASE_URL}/admin/complaints/${c.id}/chat?userId=${session.id}`);
            if (mResp.ok) {
              const msgs = await mResp.json();
              msgs.forEach(m => {
                if (m.createdAt > lastActivity) lastActivity = m.createdAt;
                const ch = ['company', 'department', 'employee'].includes(m.channel) ? m.channel : 'employee';
                let senderRole = m.senderRole || 'unknown';
                
                if (!messages[ch]) messages[ch] = [];
                messages[ch].push({
                  id: m.id,
                  sender: senderRole.toLowerCase(),
                  senderName: m.senderName,
                  senderRole: m.senderRole || senderRole,
                  text: m.message,
                  at: m.createdAt
                });
              });
            }
          } catch (me) { 
            console.warn(`Failed messages for ${c.id}`, me); 
          }

          // Ensure normalized field names for the dashboard
          return {
            ...c,
            id: c.id,
            displayId: `CMP-${String(c.id).padStart(3, '0')}`,
            complainantType: (c.complainantType || 'EMPLOYEE').toUpperCase(),
            status: (c.status || 'PENDING').toUpperCase(),
            createdAt: c.createdAt || new Date().toISOString(),
            lastActivity: lastActivity,
            complainantName: c.complainantName || 'Anonymous',
            companyName: c.companyName || 'Unknown Company',
            messages: messages
          };
        }));
        
        console.log(`Successfully processed ${cachedComplaints.length} complaints for the dashboard.`);
        return cachedComplaints;
      } else {
        console.error('API failed with status:', response.status);
      }
    } catch (e) {
      console.error('Failed to fetch complaints from MySQL:', e);
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
    getComplaints, getComplaintById, fetchComplaintById, updateComplaint, deleteComplaint,
    approveComplaint, rejectComplaint, forwardComplaint,
    markViewed, startInvestigation, submitResolution,
    approveResolution, rejectResolution, runOverdueCheck,
    getAdminRequests, saveAdminRequests, approveAdminRequest, rejectAdminRequest,
    assignResponsibility,
    getNotifications, addNotification, markNotifRead, unreadCount,
    sendMessage, fetchChat, markMessagesRead, fetchLiveAdmins, fetchLiveComplaints,
    getAdminSAMessages, sendAdminSAMessage, markAdminSAMessagesRead, getThreadBetween, getUnreadAdminSACount,
    getVisibleStatus, fmtDate, fmtDateTime,
    statusBadge, priorityBadge, evidenceIcon,
    syncLocalStorageToDB,
    _unreadCounts: {},

    fetchUnreadCounts: async function() {
        const u = CMS.getSession();
        if(!u) return;
        try {
            const resp = await fetch(`/api/complaints/unread-counts?userId=${u.id}`);
            if(resp.ok) {
                const counts = await resp.json();
                CMS._unreadCounts = counts;
                CMS.updateNotificationBadges(counts);
                window.dispatchEvent(new CustomEvent('cms-unread-updated', { detail: counts }));
            }
        } catch(e) { console.error("Badges fetch failed", e); }
    },

    getUnreadCount: function(type, id) {
        const key = type === 'private' ? 'private' : `cmp-${id}`;
        return CMS._unreadCounts[key] || 0;
    },

    updateNotificationBadges: function(counts) {
        // Global counts removed per user request. 
        // Notifications are now handled via highlights in the communication portal.
        const badge = document.getElementById('comm-unread-badge');
        if (badge) badge.style.display = 'none';
        
        const sidebarBadges = document.querySelectorAll('.notif-count');
        sidebarBadges.forEach(b => b.style.display = 'none');
    },

    pollUnreadCounts: function() {
        const u = CMS.getSession();
        if (!u) return;
        CMS.fetchUnreadCounts();
        setInterval(() => CMS.fetchUnreadCounts(), 15000);
    },
    syncDB: async function() {
      await fetchLiveAdmins();
      return await fetchLiveComplaints();
    },
    BASE_URL
  };

  async function fetchChat(complaintId) {
    try {
      const session = getSession();
      const resp = await fetch(`${BASE_URL}/admin/complaints/${complaintId}/chat?userId=${session.id}`);
      if (resp.ok) return await resp.json();
    } catch (e) { console.error('Fetch chat failed:', e); }
    return [];
  }

  async function markMessagesRead(complaintId, channel, recipientId) {
    try {
      const session = getSession();
      const key = complaintId === 0 ? 'private' : `cmp-${complaintId}`;
      
      // Update local state immediately for responsiveness
      const currentVal = CMS._unreadCounts[key] || 0;
      CMS._unreadCounts[key] = 0;
      if (CMS._unreadCounts.total) {
          CMS._unreadCounts.total = Math.max(0, CMS._unreadCounts.total - currentVal);
      }
      CMS.updateNotificationBadges(CMS._unreadCounts);

      // Notify backend
      await fetch(`${BASE_URL}/admin/complaints/${complaintId}/chat/read?userId=${session.id}&channel=${channel}${recipientId ? '&recipientId='+recipientId : ''}`, {
        method: 'POST'
      });
      
      window.dispatchEvent(new CustomEvent('cms-unread-updated', { detail: CMS._unreadCounts }));
    } catch (e) { console.error('Mark read failed:', e); }
  }

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

async function updateNotifBadge() {
  // Disabled per user request - removing all numerical counts from global UI
  document.querySelectorAll('.notif-count').forEach(el => el.style.display = 'none');
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
          ${(c.statusHistory || []).map(h => `<div>✦ <b>${h.status}</b> — ${CMS.fmtDateTime(h.at)} by ${h.by}</div>`).join('')}
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

// Global Edit Helper
function editComplaint(id) {
    if (!id) return;
    window.location.href = `complaint-form.html?editId=${id}`;
}
