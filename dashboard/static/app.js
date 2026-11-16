const REFRESH_MS = 60000;

function el(tag, props = {}, children = []) {
  const node = document.createElement(tag);
  for (const [k, v] of Object.entries(props)) {
    if (k === "text") node.textContent = v;
    else if (k === "class") node.className = v;
    else node.setAttribute(k, v);
  }
  for (const child of children) node.appendChild(child);
  return node;
}

function errorState(message) {
  return el("div", { class: "error-state", text: message });
}

function emptyState(message) {
  return el("div", { class: "empty-state", text: message });
}

function formatBytes(bytes) {
  if (bytes == null) return "—";
  const units = ["B", "KB", "MB", "GB", "TB"];
  let v = bytes;
  let i = 0;
  while (v >= 1024 && i < units.length - 1) {
    v /= 1024;
    i++;
  }
  return `${v.toFixed(1)} ${units[i]}`;
}

function formatUptime(seconds) {
  if (seconds == null) return "—";
  const days = Math.floor(seconds / 86400);
  const hours = Math.floor((seconds % 86400) / 3600);
  return `${days}d ${hours}h`;
}

function renderJellyfin(data) {
  const body = document.querySelector("#card-jellyfin .card-body");
  body.innerHTML = "";
  if (data.error) {
    body.appendChild(errorState(data.error));
    return;
  }
  if (!data.items || data.items.length === 0) {
    body.appendChild(emptyState("Nothing in progress."));
    return;
  }
  const list = el("ul", { class: "item-list" });
  for (const item of data.items) {
    const row = el("li", { class: "item-row" }, [
      el("span", { class: "item-title", text: item.title }),
      el("div", { class: "progress-track" }, [
        el("div", { class: "progress-fill", style: `width:${item.percent}%` }),
      ]),
    ]);
    row.querySelector(".progress-fill").style.width = `${item.percent}%`;
    list.appendChild(row);
  }
  body.appendChild(list);
}

function statusSlug(status) {
  return status.toLowerCase().replace(/\s+/g, "-");
}

function renderSeerr(data) {
  const body = document.querySelector("#card-seerr .card-body");
  body.innerHTML = "";
  if (data.error) {
    body.appendChild(errorState(data.error));
    return;
  }
  if (!data.items || data.items.length === 0) {
    body.appendChild(emptyState("No requests yet."));
    return;
  }
  const list = el("ul", { class: "item-list" });
  for (const item of data.items) {
    list.appendChild(
      el("li", { class: "request-row" }, [
        el("span", { class: "item-title", text: item.title }),
        el("span", { class: `badge status-${statusSlug(item.status)}`, text: item.status }),
      ])
    );
  }
  body.appendChild(list);
}

function renderHomeAssistant(data) {
  const body = document.querySelector("#card-homeassistant .card-body");
  body.innerHTML = "";
  if (data.error) {
    body.appendChild(errorState(data.error));
    return;
  }
  body.appendChild(el("div", { class: "presence", text: data.presence || "unknown" }));
  if (!data.states || data.states.length === 0) {
    body.appendChild(emptyState("No key states available."));
    return;
  }
  for (const s of data.states) {
    body.appendChild(
      el("div", { class: "kv-row" }, [
        el("span", { text: s.entity_id }),
        el("span", { text: s.state }),
      ])
    );
  }
}

function renderServer(data) {
  const body = document.querySelector("#card-server .card-body");
  body.innerHTML = "";

  const grid = el("div", { class: "server-grid" });
  const host = data.host || {};
  grid.appendChild(
    el("div", { class: "stat-tile" }, [
      el("div", { class: "stat-label", text: "CPU" }),
      el("div", {
        class: "stat-value",
        text: host.cpu_percent != null ? `${host.cpu_percent}%` : "—",
      }),
    ])
  );
  grid.appendChild(
    el("div", { class: "stat-tile" }, [
      el("div", { class: "stat-label", text: "RAM" }),
      el("div", {
        class: "stat-value",
        text:
          host.memory_used_bytes != null && host.memory_total_bytes != null
            ? `${formatBytes(host.memory_used_bytes)} / ${formatBytes(host.memory_total_bytes)}`
            : "—",
      }),
    ])
  );
  grid.appendChild(
    el("div", { class: "stat-tile" }, [
      el("div", { class: "stat-label", text: "/mnt/tank" }),
      el("div", {
        class: "stat-value",
        text: data.disk ? `${formatBytes(data.disk.used)} / ${formatBytes(data.disk.total)}` : "—",
      }),
    ])
  );
  grid.appendChild(
    el("div", { class: "stat-tile" }, [
      el("div", { class: "stat-label", text: "Uptime" }),
      el("div", { class: "stat-value", text: formatUptime(data.uptime_seconds) }),
    ])
  );
  body.appendChild(grid);

  if (data.host_error) body.appendChild(errorState(data.host_error));
  if (data.disk_error) body.appendChild(errorState(data.disk_error));
  if (data.uptime_error) body.appendChild(errorState(data.uptime_error));

  if (data.containers_error) {
    body.appendChild(errorState(data.containers_error));
    return;
  }
  if (!data.containers || data.containers.length === 0) {
    body.appendChild(emptyState("No containers found."));
    return;
  }

  const table = el("table", { class: "container-table" });
  table.appendChild(
    el("tr", {}, [
      el("th", { text: "Container" }),
      el("th", { text: "State" }),
      el("th", { text: "CPU" }),
      el("th", { text: "RAM" }),
    ])
  );
  for (const c of data.containers) {
    const dotClass = c.state === "running" ? "running" : c.state === "exited" || c.state === "dead" ? "exited" : "other";
    table.appendChild(
      el("tr", {}, [
        el("td", {}, [el("span", { class: `dot ${dotClass}` }), el("span", { text: c.name })]),
        el("td", { text: c.state || "—" }),
        el("td", { text: c.cpu_percent != null ? `${c.cpu_percent}%` : "—" }),
        el("td", { text: c.memory_bytes != null ? formatBytes(c.memory_bytes) : "—" }),
      ])
    );
  }
  body.appendChild(table);
}

function renderLinks(data) {
  const body = document.querySelector("#card-links .card-body");
  body.innerHTML = "";
  if (data.error) {
    body.appendChild(errorState(data.error));
    return;
  }
  if (!data.items || data.items.length === 0) {
    body.appendChild(emptyState("No links configured."));
    return;
  }
  const grid = el("div", { class: "links-grid" });
  for (const link of data.items) {
    const a = el("a", { class: "link-tile", href: link.url, target: "_blank", rel: "noopener", text: link.label });
    grid.appendChild(a);
  }
  body.appendChild(grid);
}

async function refresh() {
  try {
    const resp = await fetch("/api/all");
    const data = await resp.json();
    renderJellyfin(data.jellyfin || {});
    renderSeerr(data.seerr || {});
    renderHomeAssistant(data.homeassistant || {});
    renderServer(data.server || {});
    renderLinks(data.links || {});
    document.getElementById("last-updated").textContent = `updated ${new Date().toLocaleTimeString()}`;
  } catch (e) {
    document.getElementById("last-updated").textContent = "update failed";
  }
}

refresh();
setInterval(refresh, REFRESH_MS);
