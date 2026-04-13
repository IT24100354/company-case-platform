console.log("app.js loaded");

/* ===============================
   Common UI (date + sidebar)
================================ */
(function () {
  // show today's date on dashboard pages
  const todayText = document.getElementById("todayText");
  if (todayText) {
    const d = new Date();
    todayText.textContent = d.toDateString();
  }

  // sidebar toggle
  const toggleBtn = document.getElementById("toggleSidebar");
  const sidebar = document.getElementById("sidebar");
  if (toggleBtn && sidebar) {
    toggleBtn.addEventListener("click", () => sidebar.classList.toggle("open"));
  }
})();

/* ===============================
   Dashboard Logic (counts + recent)
   Works only if these IDs exist:
   totalCount, pendingCount, resolvedCount, recentComplaintsBody
================================ */
document.addEventListener("DOMContentLoaded", loadDashboardData);

async function loadDashboardData() {
  const totalEl = document.getElementById("totalCount");
  const pendingEl = document.getElementById("pendingCount");
  const resolvedEl = document.getElementById("resolvedCount");
  const tableBody = document.getElementById("recentComplaintsBody");
  const forwardedEl = document.getElementById("forwardedCount");

  // If not on dashboard page, stop
  if (!totalEl || !pendingEl || !resolvedEl) return;

  try {
    const res = await fetch("/api/admin/complaints");
    if (!res.ok) throw new Error("Failed to load complaints: " + res.status);

    const data = await res.json(); // ✅ data exists only inside this function

    // ✅ Counts
    totalEl.textContent = data.length;
    pendingEl.textContent = data.filter(c => c.status === "PENDING").length;
    forwardedEl.textContent = data.filter (c => c.status === "FORWARDED").length;
    resolvedEl.textContent = data.filter(c => c.status === "RESOLVED").length;

    // ✅ Recent table (optional)
    if (tableBody) {
      const recent = data.slice(0, 5);
      tableBody.innerHTML = recent.map(c => `
        <tr>
          <td>#${c.id}</td>
          <td>${escapeHtml(c.title ?? "")}</td>
          <td>
            <span class="badge ${c.status === "RESOLVED" ? "ok" : "warn"}">
              ${escapeHtml(c.status ?? "")}
            </span>
          </td>
          <td>${c.createdAt ? String(c.createdAt).substring(0,10) : ""}</td>
        </tr>
      `).join("");
    }

  } catch (err) {
    console.error(err);
    totalEl.textContent = "0";
    pendingEl.textContent = "0";
    resolvedEl.textContent = "0";
  }
}

/* ===============================
   Complaints Page Logic (filter + search + status update)
   Works only if these IDs exist:
   complaintsBody, statusFilter, searchBox
================================ */
(function () {
  const bodyEl = document.getElementById("complaintsBody");
  if (!bodyEl) return; // not on complaints page

  console.log("Complaint page script is running");

  const statusFilter = document.getElementById("statusFilter");
  const searchBox = document.getElementById("searchBox");

  let allRows = [];

  async function loadComplaints() {
    bodyEl.innerHTML = `<tr><td colspan="5">Loading...</td></tr>`;

    const status = statusFilter ? statusFilter.value : "ALL";
    const url = status === "ALL"
      ? "/api/admin/complaints"
      : `/api/admin/complaints?status=${encodeURIComponent(status)}`;

    const res = await fetch(url);
    const data = await res.json();
    allRows = data;
    render();
  }

  function badgeClass(status) {
    if (status === "PENDING") return "badge warn";
    if (status === "FORWARDED") return "badge info";
    if (status === "IN_PROGRESS") return "badge info";
    if (status === "RESOLVED") return "badge ok";
    return "badge danger";
  }

  function formatDate(dt) {
    if (!dt) return "-";
    return String(dt).replace("T", " ").slice(0, 16);
  }

  function render() {
    const q = (searchBox ? searchBox.value : "").toLowerCase().trim();

    const filtered = allRows.filter(c =>
      !q || (c.title && c.title.toLowerCase().includes(q))
    );

    if (filtered.length === 0) {
      bodyEl.innerHTML = `<tr><td colspan="5">No complaints found.</td></tr>`;
      return;
    }

    bodyEl.innerHTML = filtered.map(c => `
      <tr>
        <td>#${c.id}</td>
        <td>
          <div style="font-weight:700;">${escapeHtml(c.title ?? "")}</div>
          <div style="opacity:.75; font-size:12px;">${escapeHtml(c.description ?? "")}</div>
        </td>
        <td><span class="${badgeClass(c.status)}">${escapeHtml(c.status ?? "")}</span></td>
        <td>${formatDate(c.createdAt)}</td>
        <td>
          ${c.status === "PENDING" ? `
              <button type="button" class="btn small" data-forward="${c.id}" title="Forwarded to the company">

                forward
              </button>
            ` : ""}
          <select class="input small" data-id="${c.id}">
            <option value="PENDING" ${c.status==="PENDING"?"selected":""}>PENDING</option>
            <option value="FORWARDED" ${c.status==="FORWARDED"?"selected":""}>FORWARDED</option>
            <option value="IN_PROGRESS" ${c.status==="IN_PROGRESS"?"selected":""}>IN_PROGRESS</option>
            <option value="RESOLVED" ${c.status==="RESOLVED"?"selected":""}>RESOLVED</option>
            <option value="REJECTED" ${c.status==="REJECTED"?"selected":""}>REJECTED</option>
          </select>

          <button type="button" class="btn small" data-save="${c.id}">Save</button>
        </td>
      </tr>
    `).join("");
  }

  bodyEl.addEventListener("click", async (e) => {
    const btnSave = e.target.closest("[data-save]");
    const btnForward = e.target.closest("[data-forward]");
    if (!btnSave && !btnForward) return;

    // ✅ stop page refresh 100%
    e.preventDefault();
    e.stopPropagation();

    if (btnSave) {
      const id = btnSave.getAttribute("data-save");
      const sel = bodyEl.querySelector(`select[data-id="${id}"]`);
      const newStatus = sel.value;

      btnSave.disabled = true;
      btnSave.textContent = "Saving...";

      try {
        const res = await fetch(`/api/admin/complaints/${id}/status`, {
          method: "PUT",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ status: newStatus })
        });

        if (!res.ok) alert("Failed to update status");
        else await loadComplaints();
      } finally {
        btnSave.disabled = false;
        btnSave.textContent = "Save";
      }
    }

    if (btnForward) {
      const id = btnForward.getAttribute("data-forward");

      btnForward.disabled = true;
      btnForward.textContent = "Forwarding...";

      try {
        const res = await fetch(`/api/admin/complaints/${id}/status`, {
          method: "PUT",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ status: "FORWARDED" })
        });

        if (!res.ok) alert("Failed to forward");
        else await loadComplaints();
      } finally {
        btnForward.disabled = false;
        btnForward.textContent = "➜";
      }
    }
  });

  if (statusFilter) statusFilter.addEventListener("change", loadComplaints);
  if (searchBox) searchBox.addEventListener("input", render);

  loadComplaints();
})();

/* ===============================
   Helpers
================================ */
function escapeHtml(str) {
  return String(str)
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#039;");
}