// build.js — reads countries.csv and uploads it to Cloudflare KV
// Run automatically by GitHub Actions before each deploy

const fs = require("fs");
const path = require("path");

const CSV_FILE = path.join(__dirname, "countries.csv");
const KV_KEY = "countries";

// ── Parse CSV ────────────────────────────────────────────────────────────────
function parseCSV(text) {
  const lines = text.trim().split(/\r?\n/);
  if (lines.length < 2) throw new Error("CSV appears to be empty or has no data rows.");

  const headers = parseCSVLine(lines[0]);
  console.log(`  Headers found: ${headers.join(", ")}`);

  const rows = [];
  for (let i = 1; i < lines.length; i++) {
    if (!lines[i].trim()) continue;
    const values = parseCSVLine(lines[i]);
    const row = {};
    headers.forEach((h, idx) => {
      row[h] = values[idx] ?? "";
    });
    rows.push(row);
  }
  return rows;
}

function parseCSVLine(line) {
  const result = [];
  let current = "";
  let inQuotes = false;
  for (let i = 0; i < line.length; i++) {
    const ch = line[i];
    if (ch === '"') {
      if (inQuotes && line[i + 1] === '"') { current += '"'; i++; }
      else { inQuotes = !inQuotes; }
    } else if (ch === "," && !inQuotes) {
      result.push(current.trim());
      current = "";
    } else {
      current += ch;
    }
  }
  result.push(current.trim());
  return result;
}

// ── Upload to KV ─────────────────────────────────────────────────────────────
async function uploadToKV(data) {
  const accountId = process.env.CF_ACCOUNT_ID;
  const namespaceId = process.env.CF_KV_NAMESPACE_ID;
  const apiToken = process.env.CLOUDFLARE_API_TOKEN;

  // If env vars not set, fall back to wrangler CLI upload
  if (!accountId || !namespaceId) {
    console.log("  CF_ACCOUNT_ID or CF_KV_NAMESPACE_ID not set — using wrangler CLI...");
    return uploadViaWrangler(data);
  }

  const json = JSON.stringify(data);
  const url = `https://api.cloudflare.com/client/v4/accounts/${accountId}/storage/kv/namespaces/${namespaceId}/values/${KV_KEY}`;

  const response = await fetch(url, {
    method: "PUT",
    headers: {
      "Authorization": `Bearer ${apiToken}`,
      "Content-Type": "application/json",
    },
    body: json,
  });

  if (!response.ok) {
    const err = await response.text();
    throw new Error(`Cloudflare API error: ${response.status} — ${err}`);
  }
  console.log("  ✅ Uploaded directly via Cloudflare API");
}

async function uploadViaWrangler(data) {
  const { execSync } = require("child_process");
  const tmpFile = path.join(__dirname, ".countries-tmp.json");
  fs.writeFileSync(tmpFile, JSON.stringify(data));
  try {
    execSync(`npx wrangler kv key put --binding=NATLAS_KV "${KV_KEY}" --path "${tmpFile}"`, {
      stdio: "inherit"
    });
    console.log("  ✅ Uploaded via wrangler CLI");
  } finally {
    if (fs.existsSync(tmpFile)) fs.unlinkSync(tmpFile);
  }
}

// ── Main ─────────────────────────────────────────────────────────────────────
async function main() {
  console.log("\n📦 natlas build.js — uploading CSV to Cloudflare KV\n");

  if (!fs.existsSync(CSV_FILE)) {
    console.error(`❌ Could not find countries.csv at: ${CSV_FILE}`);
    process.exit(1);
  }

  const stat = fs.statSync(CSV_FILE);
  const sizeMB = (stat.size / 1024 / 1024).toFixed(1);
  console.log(`  Found countries.csv (${sizeMB} MB)`);

  const text = fs.readFileSync(CSV_FILE, "utf8");
  console.log("  Parsing CSV...");
  const rows = parseCSV(text);
  console.log(`  Parsed ${rows.length.toLocaleString()} rows`);

  const json = JSON.stringify(rows);
  const jsonMB = (Buffer.byteLength(json) / 1024 / 1024).toFixed(1);
  console.log(`  JSON size: ${jsonMB} MB`);

  if (parseFloat(jsonMB) > 24) {
    console.error("❌ JSON exceeds 24 MB — too large for Cloudflare KV (25 MB limit).");
    process.exit(1);
  }

  console.log("  Uploading to Cloudflare KV...");
  await uploadToKV(rows);

  console.log(`\n✅ Done! ${rows.length.toLocaleString()} countries uploaded to KV key "${KV_KEY}"\n`);
}

main().catch(err => {
  console.error("\n❌ Build failed:", err.message);
  process.exit(1);
});
