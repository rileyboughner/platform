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

  function containerTable(containers) {
    const table = el("table", { class: "container-table" });
    table.appendChild(
      el("tr", {}, [
        el("th", { text: "Container" }),
        el("th", { text: "State" }),
        el("th", { text: "CPU" }),
        el("th", { text: "RAM" }),
      ])
    );
    for (const c of containers) {
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
    return table;
  }

  // Not-running containers first so a stopped service is impossible to miss.
  const down = data.containers.filter((c) => c.state !== "running");
  const up = data.containers.filter((c) => c.state === "running");
  if (down.length > 0) {
    body.appendChild(el("div", { class: "subsection-title", text: `Not running (${down.length})` }));
    body.appendChild(containerTable(down));
  }
  body.appendChild(el("div", { class: "subsection-title", text: `Running (${up.length})` }));
  body.appendChild(containerTable(up));
}

function renderKubernetes(data) {
  const body = document.querySelector("#card-kubernetes .card-body");
  body.innerHTML = "";
  if (data.error) {
    body.appendChild(errorState(data.error));
    return;
  }
  if (!data.pods || data.pods.length === 0) {
    body.appendChild(emptyState("No pods found."));
    return;
  }
  const table = el("table", { class: "container-table" });
  table.appendChild(
    el("tr", {}, [
      el("th", { text: "Pod" }),
      el("th", { text: "Namespace" }),
      el("th", { text: "Ready" }),
      el("th", { text: "Status" }),
      el("th", { text: "Restarts" }),
    ])
  );
  for (const p of data.pods) {
    const dotClass =
      p.status === "Running" ? "running"
      : p.status === "Succeeded" ? "completed"
      : p.status === "Failed" ? "exited"
      : "other";
    table.appendChild(
      el("tr", {}, [
        el("td", {}, [el("span", { class: `dot ${dotClass}` }), el("span", { text: p.name })]),
        el("td", { text: p.namespace }),
        el("td", { text: p.ready }),
        el("td", { text: p.status }),
        el("td", { text: String(p.restarts) }),
      ])
    );
  }
  body.appendChild(table);
}

const LINKS_ORDER_KEY = "dashboard-links-order";

function loadLinksOrder() {
  try {
    return JSON.parse(localStorage.getItem(LINKS_ORDER_KEY)) || [];
  } catch {
    return [];
  }
}

function saveLinksOrder(urls) {
  localStorage.setItem(LINKS_ORDER_KEY, JSON.stringify(urls));
}

// Applies the user's saved drag order; links not yet seen (new ones) are
// appended at the end in their original order.
function applyLinksOrder(items) {
  const order = loadLinksOrder();
  const byUrl = new Map(items.map((item) => [item.url, item]));
  const ordered = [];
  for (const url of order) {
    if (byUrl.has(url)) {
      ordered.push(byUrl.get(url));
      byUrl.delete(url);
    }
  }
  ordered.push(...byUrl.values());
  return ordered;
}

let linksFilterValue = "";

function renderLinks(data) {
  const body = document.querySelector("#card-links .card-body");
  const existingFilter = body.querySelector(".links-filter");
  if (existingFilter) linksFilterValue = existingFilter.value;
  body.innerHTML = "";
  if (data.error) {
    body.appendChild(errorState(data.error));
    return;
  }
  if (!data.items || data.items.length === 0) {
    body.appendChild(emptyState("No links configured."));
    return;
  }

  const items = applyLinksOrder(data.items);

  const filterInput = el("input", {
    class: "links-filter",
    type: "text",
    placeholder: "Filter services…",
  });
  filterInput.value = linksFilterValue;
  body.appendChild(filterInput);

  const grid = el("div", { class: "links-grid" });
  body.appendChild(grid);

  let draggedUrl = null;

  function renderGrid() {
    grid.innerHTML = "";
    const query = filterInput.value.trim().toLowerCase();
    const visible = query
      ? items.filter((link) => link.label.toLowerCase().includes(query))
      : items;

    for (const link of visible) {
      const tile = el("a", {
        class: "link-tile",
        href: link.url,
        target: "_blank",
        rel: "noopener",
        text: link.label,
        draggable: "true",
      });
      tile.addEventListener("dragstart", () => {
        draggedUrl = link.url;
        tile.classList.add("dragging");
      });
      tile.addEventListener("dragend", () => tile.classList.remove("dragging"));
      tile.addEventListener("dragover", (e) => e.preventDefault());
      tile.addEventListener("drop", (e) => {
        e.preventDefault();
        if (!draggedUrl || draggedUrl === link.url) return;
        const fromIdx = items.findIndex((i) => i.url === draggedUrl);
        const toIdx = items.findIndex((i) => i.url === link.url);
        if (fromIdx === -1 || toIdx === -1) return;
        const [moved] = items.splice(fromIdx, 1);
        items.splice(toIdx, 0, moved);
        saveLinksOrder(items.map((i) => i.url));
        renderGrid();
      });
      grid.appendChild(tile);
    }

    if (visible.length === 0) {
      grid.appendChild(emptyState("No matching services."));
    }
  }

  filterInput.addEventListener("input", renderGrid);
  renderGrid();
}

async function refresh() {
  try {
    const resp = await fetch("/api/all");
    const data = await resp.json();
    renderSeerr(data.seerr || {});
    renderHomeAssistant(data.homeassistant || {});
    renderServer(data.server || {});
    renderKubernetes(data.kubernetes || {});
    renderLinks(data.links || {});
    document.getElementById("last-updated").textContent = `updated ${new Date().toLocaleTimeString()}`;
  } catch (e) {
    document.getElementById("last-updated").textContent = "update failed";
  }
}

refresh();
setInterval(refresh, REFRESH_MS);
