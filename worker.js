// natlas.org — Cloudflare Worker
// Replaces CountrySearchServer.java

let cachedData = null;

async function getCountries(env) {
  if (cachedData) return cachedData;
  const raw = await env.NATLAS_KV.get("countries", { type: "text" });
  if (!raw) return [];
  cachedData = JSON.parse(raw);
  return cachedData;
}

function searchCountries(countries, query) {
  if (!query || query.trim() === "") return [];
  const q = query.trim().toLowerCase();

  // Exact matches first, then partial
  const exact = countries.filter(c =>
    Object.values(c).some(v => String(v).toLowerCase() === q)
  );
  const partial = countries.filter(c =>
    !exact.includes(c) &&
    Object.values(c).some(v => String(v).toLowerCase().includes(q))
  );
  return [...exact, ...partial];
}

function buildHTML(countries, query, showAll) {
  const results = query ? searchCountries(countries, query) : (showAll ? countries : []);
  const headers = countries.length > 0 ? Object.keys(countries[0]) : [];

  const tableRows = results.map(row =>
    `<tr>${headers.map(h => `<td>${row[h] ?? ""}</td>`).join("")}</tr>`
  ).join("");

  const tableHTML = results.length > 0 ? `
    <div class="table-wrap">
      <table>
        <thead><tr>${headers.map(h => `<th>${h}</th>`).join("")}</tr></thead>
        <tbody>${tableRows}</tbody>
      </table>
    </div>
    <p class="count">${results.length} result${results.length !== 1 ? "s" : ""}</p>
  ` : query ? `<p class="no-results">No results found for "<strong>${escapeHtml(query)}</strong>"</p>`
             : `<p class="hint">Enter a search term above, or use "Show All Data" to browse everything.</p>`;

  return `<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>natlas.org — Country Search</title>
  <link rel="icon" href="data:image/svg+xml,<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 100 100'><text y='.9em' font-size='90'>🌍</text></svg>">
  <style>
    *, *::before, *::after { box-sizing: border-box; margin: 0; padding: 0; }
    body {
      font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
      background: #f4f5f7;
      color: #1a1a2e;
      min-height: 100vh;
    }
    header {
      background: #1a1a2e;
      color: #fff;
      padding: 24px 32px;
      display: flex;
      align-items: center;
      gap: 14px;
    }
    .globe {
      font-size: 32px;
      animation: spin 8s linear infinite;
      display: inline-block;
    }
    @keyframes spin { to { transform: rotateY(360deg); } }
    header h1 { font-size: 22px; font-weight: 600; }
    header p { font-size: 13px; color: #aab0c0; margin-top: 2px; }
    .main { max-width: 1100px; margin: 0 auto; padding: 32px 24px; }
    .search-box {
      background: #fff;
      border-radius: 12px;
      padding: 24px;
      box-shadow: 0 2px 8px rgba(0,0,0,0.06);
      margin-bottom: 24px;
    }
    .search-row {
      display: flex; gap: 10px; flex-wrap: wrap;
    }
    input[type=text] {
      flex: 1;
      min-width: 200px;
      padding: 10px 16px;
      border: 1.5px solid #dde1ea;
      border-radius: 8px;
      font-size: 15px;
      outline: none;
      transition: border-color 0.2s;
    }
    input[type=text]:focus { border-color: #3b5bdb; }
    button {
      padding: 10px 20px;
      border-radius: 8px;
      border: none;
      font-size: 14px;
      font-weight: 500;
      cursor: pointer;
      transition: background 0.15s;
    }
    .btn-search { background: #3b5bdb; color: #fff; }
    .btn-search:hover { background: #2f4ac7; }
    .btn-all { background: #f1f3f9; color: #3b5bdb; border: 1.5px solid #dde1ea; }
    .btn-all:hover { background: #e6e9f5; }
    .btn-clear { background: #f1f3f9; color: #666; border: 1.5px solid #dde1ea; }
    .btn-clear:hover { background: #e6e9f5; }
    .table-wrap { overflow-x: auto; border-radius: 10px; border: 1px solid #e4e7f0; }
    table { width: 100%; border-collapse: collapse; font-size: 14px; background: #fff; }
    thead { background: #1a1a2e; color: #fff; }
    th { padding: 12px 16px; text-align: left; font-weight: 500; white-space: nowrap; }
    td { padding: 10px 16px; border-bottom: 1px solid #f0f2f7; white-space: nowrap; }
    tr:last-child td { border-bottom: none; }
    tr:nth-child(even) td { background: #fafbfe; }
    tr:hover td { background: #eef1fc; }
    .count { font-size: 13px; color: #888; margin-top: 10px; }
    .hint, .no-results { color: #888; font-size: 14px; padding: 24px 0; }
    .no-results strong { color: #1a1a2e; }
  </style>
</head>
<body>
  <header>
    <span class="globe">🌍</span>
    <div>
      <h1>natlas.org</h1>
      <p>Country data search — ${countries.length.toLocaleString()} records loaded</p>
    </div>
  </header>
  <div class="main">
    <div class="search-box">
      <form method="GET" action="/search">
        <div class="search-row">
          <input type="text" name="q" placeholder="Search countries, codes, regions…" value="${escapeHtml(query || "")}" autofocus>
          <button type="submit" class="btn-search">🔍 Search</button>
          <button type="submit" name="all" value="1" class="btn-all">📋 Show All Data</button>
          ${query || showAll ? `<a href="/"><button type="button" class="btn-clear">✕ Clear</button></a>` : ""}
        </div>
      </form>
    </div>
    ${tableHTML}
  </div>
</body>
</html>`;
}

function escapeHtml(str) {
  return String(str)
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;");
}

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    const path = url.pathname;

    // API route — returns JSON
    if (path === "/api/search") {
      const query = url.searchParams.get("q") || "";
      const countries = await getCountries(env);
      const results = query ? searchCountries(countries, query) : [];
      return new Response(JSON.stringify(results), {
        headers: { "Content-Type": "application/json", "Access-Control-Allow-Origin": "*" }
      });
    }

    // HTML search route
    if (path === "/search" || path === "/") {
      const query = url.searchParams.get("q") || "";
      const showAll = url.searchParams.get("all") === "1";
      const countries = await getCountries(env);
      const html = buildHTML(countries, query, showAll);
      return new Response(html, {
        headers: { "Content-Type": "text/html; charset=utf-8" }
      });
    }

    return new Response("Not found", { status: 404 });
  }
};
