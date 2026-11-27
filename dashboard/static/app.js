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
}

let servicesFilterValue = "";
let servicesSources = { Docker: true, Kubernetes: true };
let servicesSortKey = "name";
let servicesSortDir = 1;

function serviceDot(source, status) {
  if (source === "Docker") {
    return status === "running" ? "running"
      : status === "exited" || status === "dead" ? "exited"
      : "other";
  }
  return status === "Running" ? "running"
    : status === "Succeeded" ? "completed"
    : status === "Failed" ? "exited"
    : "other";
}

// Sortable value per column; null means "no data" and always sorts last.
function serviceSortValue(s, key) {
  switch (key) {
    case "name": return s.name.toLowerCase();
    case "source": return s.source;
    case "status": return s.status.toLowerCase();
    case "cpu": return s.cpuNum;
    case "memory": return s.memNum;
    case "ready": return s.readyNum;
    case "restarts": return s.restartsNum;
    default: return s.name.toLowerCase();
  }
}

function compareServices(a, b) {
  const va = serviceSortValue(a, servicesSortKey);
  const vb = serviceSortValue(b, servicesSortKey);
  if (va == null && vb == null) return 0;
  if (va == null) return 1;
  if (vb == null) return -1;
  const cmp = va < vb ? -1 : va > vb ? 1 : 0;
  if (cmp !== 0) return cmp * servicesSortDir;
  return a.name.toLowerCase() < b.name.toLowerCase() ? -1 : 1;
}

// One filterable list of every service: Docker containers and Kubernetes
// pods together. A name is a link when its published port (Docker) or its
// NodePort service (Kubernetes) has a public cloudflared hostname.
function renderServices(dockerData, k8sData) {
  const body = document.querySelector("#card-services .card-body");
  const existingFilter = body.querySelector(".services-filter");
  if (existingFilter) servicesFilterValue = existingFilter.value;
  body.innerHTML = "";

  if (dockerData.error) body.appendChild(errorState(dockerData.error));
  if (dockerData.stats_error) body.appendChild(errorState(dockerData.stats_error));
  if (k8sData.error) body.appendChild(errorState(k8sData.error));

  const services = [];
  for (const c of dockerData.containers || []) {
    services.push({
      name: c.name,
      sub: null,
      source: "Docker",
      status: c.state || "—",
      up: c.state === "running",
      dot: serviceDot("Docker", c.state),
      cpu: c.cpu_percent != null ? `${c.cpu_percent}%` : "—",
      cpuNum: c.cpu_percent != null ? c.cpu_percent : null,
      memory: c.memory_bytes != null ? formatBytes(c.memory_bytes) : "—",
      memNum: c.memory_bytes != null ? c.memory_bytes : null,
      ready: "—",
      readyNum: null,
      restarts: "—",
      restartsNum: null,
      url: c.url || null,
    });
  }
  for (const p of k8sData.pods || []) {
    const readyMatch = /^(\d+)\/(\d+)$/.exec(p.ready || "");
    services.push({
      name: p.name,
      sub: p.namespace,
      source: "Kubernetes",
      status: p.status || "—",
      up: p.status === "Running" || p.status === "Succeeded",
      dot: serviceDot("Kubernetes", p.status),
      cpu: "—",
      cpuNum: null,
      memory: "—",
      memNum: null,
      ready: p.ready,
      readyNum: readyMatch ? parseInt(readyMatch[1], 10) : null,
      restarts: String(p.restarts),
      restartsNum: p.restarts,
      url: p.url || null,
    });
  }

  if (services.length === 0) {
    if (!dockerData.error && !k8sData.error) {
      body.appendChild(emptyState("No services found."));
    }
    return;
  }

  const controls = el("div", { class: "services-controls" });
  const filterInput = el("input", {
    class: "services-filter",
    type: "text",
    placeholder: "Filter services…",
  });
  filterInput.value = servicesFilterValue;
  controls.appendChild(filterInput);
  for (const source of ["Docker", "Kubernetes"]) {
    const toggle = el("button", {
      type: "button",
      class: servicesSources[source] ? "source-toggle active" : "source-toggle",
      text: source,
    });
    toggle.addEventListener("click", () => {
      servicesSources[source] = !servicesSources[source];
      toggle.classList.toggle("active", servicesSources[source]);
      renderTable();
    });
    controls.appendChild(toggle);
  }
  body.appendChild(controls);

  const tableWrap = el("div");
  body.appendChild(tableWrap);

  function sortHeader(label, key) {
    const th = el("th", { class: "sortable", text: label });
    if (servicesSortKey === key) {
      th.appendChild(
        el("span", { class: "sort-ind", text: servicesSortDir === 1 ? " ▲" : " ▼" })
      );
    }
    th.addEventListener("click", () => {
      if (servicesSortKey === key) {
        servicesSortDir = -servicesSortDir;
      } else {
        servicesSortKey = key;
        servicesSortDir = 1;
      }
      renderTable();
    });
    return th;
  }

  function serviceTable(rows) {
    const table = el("table", { class: "container-table" });
    table.appendChild(
      el("tr", {}, [
        sortHeader("Service", "name"),
        sortHeader("Source", "source"),
        sortHeader("Status", "status"),
        sortHeader("CPU", "cpu"),
        sortHeader("Memory", "memory"),
        sortHeader("Ready", "ready"),
        sortHeader("Restarts", "restarts"),
      ])
    );
    for (const s of rows) {
      const nameCell = el("td", {});
      nameCell.appendChild(el("span", { class: `dot ${s.dot}` }));
      if (s.url) {
        nameCell.appendChild(
          el("a", {
            class: "service-link",
            href: s.url,
            target: "_blank",
            rel: "noopener",
            text: s.name,
          })
        );
      } else {
        nameCell.appendChild(el("span", { text: s.name }));
      }
      if (s.sub) {
        nameCell.appendChild(el("span", { class: "muted service-sub", text: ` ${s.sub}` }));
      }
      table.appendChild(
        el("tr", {}, [
          nameCell,
          el("td", {}, [el("span", { class: "badge", text: s.source })]),
          el("td", { text: s.status }),
          el("td", { text: s.cpu }),
          el("td", { text: s.memory }),
          el("td", { text: s.ready }),
          el("td", { text: s.restarts }),
        ])
      );
    }
    return table;
  }

  function renderTable() {
    tableWrap.innerHTML = "";
    const query = filterInput.value.trim().toLowerCase();
    const visible = services.filter((s) => {
      if (!servicesSources[s.source]) return false;
      if (!query) return true;
      return (
        s.name.toLowerCase().includes(query) ||
        (s.sub && s.sub.toLowerCase().includes(query))
      );
    });
    if (visible.length === 0) {
      tableWrap.appendChild(emptyState("No matching services."));
      return;
    }
    // Not-running services first so a stopped service is impossible to miss;
    // the chosen sort applies inside each group.
    const down = visible.filter((s) => !s.up).sort(compareServices);
    const up = visible.filter((s) => s.up).sort(compareServices);
    if (down.length > 0) {
      tableWrap.appendChild(
        el("div", { class: "subsection-title", text: `Not running (${down.length})` })
      );
      tableWrap.appendChild(serviceTable(down));
    }
    tableWrap.appendChild(
      el("div", { class: "subsection-title", text: `Running (${up.length})` })
    );
    tableWrap.appendChild(serviceTable(up));
  }

  filterInput.addEventListener("input", renderTable);
  renderTable();
}

async function refresh() {
  try {
    const resp = await fetch("/api/all");
    const data = await resp.json();
    renderSeerr(data.seerr || {});
    renderServer(data.server || {});
    renderServices(data.docker || {}, data.kubernetes || {});
    document.getElementById("last-updated").textContent = `updated ${new Date().toLocaleTimeString()}`;
  } catch (e) {
    document.getElementById("last-updated").textContent = "update failed";
  }
}

refresh();
setInterval(refresh, REFRESH_MS);
