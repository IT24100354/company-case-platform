document.addEventListener("DOMContentLoaded", loadForwarded);

async function loadForwarded() {
  const body = document.getElementById("companyComplaintsBody");

  const res = await fetch("/api/admin/complaints?status=FORWARDED");
  const data = await res.json();

  if (data.length === 0) {
    body.innerHTML = "<tr><td colspan='5'>No forwarded complaints</td></tr>";
    return;
  }

  body.innerHTML = data.map(c => `
    <tr>
      <td>#${c.id}</td>
      <td>${c.title}</td>
      <td>${c.description}</td>
      <td>${c.status}</td>
      <td>${c.createdAt ? c.createdAt.substring(0,10) : ""}</td>
    </tr>
  `).join("");
}