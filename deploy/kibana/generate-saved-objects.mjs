// =============================================================================
// Generates deploy/kibana/saved-objects.ndjson — the Kibana saved objects
// (data views, saved searches, aggregation-based visualizations, dashboards)
// for the fraud platform's four Elasticsearch indices.
//
//   node deploy/kibana/generate-saved-objects.mjs
//
// Kept in the repo (rather than only the generated NDJSON) so the field lists
// stay in lockstep with the index mappings — the single source of truth is the
// *-index.json mappings under each service's resources/elasticsearch. Emitting
// via a generator guarantees the heavy nested-JSON escaping (visState,
// searchSourceJSON, panelsJSON) is always valid.
//
// Target: Kibana 8.x / 9.x. Classic aggregation-based visualizations are used
// (not Lens) because their saved-object shape is stable and import-portable.
// Import with ./import-saved-objects.sh (Saved Objects _import API).
// =============================================================================
import { writeFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";

const OUT = join(dirname(fileURLToPath(import.meta.url)), "saved-objects.ndjson");
const PANEL_VERSION = "8.15.0";

// ---- data views (index-pattern) --------------------------------------------
// timeFieldName picks the field Discover/dashboards use for the global time
// filter: transactions/fraud/audit are event streams keyed on occurredAt;
// alerts are keyed on createdAt.
const DATA_VIEWS = [
  { id: "fdp-transactions", title: "transactions",  time: "occurredAt" },
  { id: "fdp-fraud-events", title: "fraud-events",  time: "occurredAt" },
  { id: "fdp-alerts",       title: "alerts",        time: "createdAt"  },
  { id: "fdp-audit-events", title: "audit-events",  time: "occurredAt" },
];

const objects = [];

for (const dv of DATA_VIEWS) {
  objects.push({
    type: "index-pattern",
    id: dv.id,
    attributes: { title: dv.title, name: dv.title, timeFieldName: dv.time },
    references: [],
  });
}

// ---- helpers ----------------------------------------------------------------
const indexRef = (id) => ({
  name: "kibanaSavedObjectMeta.searchSourceJSON.index",
  type: "index-pattern",
  id,
});

function searchSourceJSON(query = "") {
  return JSON.stringify({
    query: { query, language: "kuery" },
    filter: [],
    indexRefName: "kibanaSavedObjectMeta.searchSourceJSON.index",
  });
}

function viz(id, indexId, visState, { query = "" } = {}) {
  return {
    type: "visualization",
    id,
    attributes: {
      title: visState.title,
      visState: JSON.stringify(visState),
      uiStateJSON: "{}",
      description: "",
      kibanaSavedObjectMeta: { searchSourceJSON: searchSourceJSON(query) },
    },
    references: [indexRef(indexId)],
  };
}

// metric (single number): count, or an aggregation over `field`
function metricViz(id, indexId, title, { agg = "count", field, query } = {}) {
  const metricAgg = { id: "1", enabled: true, type: agg, schema: "metric", params: field ? { field } : {} };
  return viz(id, indexId, {
    title,
    type: "metric",
    aggs: [metricAgg],
    params: {
      addTooltip: true,
      addLegend: false,
      type: "metric",
      metric: {
        percentageMode: false,
        useRanges: false,
        colorSchema: "Green to Red",
        metricColorMode: "None",
        colorsRange: [{ type: "range", from: 0, to: 10000 }],
        labels: { show: true },
        invertColors: false,
        style: { bgFill: "#000", bgColor: false, labelColor: false, subText: "", fontSize: 36 },
      },
    },
  }, { query });
}

// donut/pie: count sliced by a terms aggregation
function pieViz(id, indexId, title, field, { size = 10, query } = {}) {
  return viz(id, indexId, {
    title,
    type: "pie",
    aggs: [
      { id: "1", enabled: true, type: "count", schema: "metric", params: {} },
      { id: "2", enabled: true, type: "terms", schema: "segment",
        params: { field, orderBy: "1", order: "desc", size, otherBucket: false, missingBucket: false } },
    ],
    params: {
      type: "pie",
      addTooltip: true,
      addLegend: true,
      legendPosition: "right",
      isDonut: true,
      labels: { show: false, values: true, last_level: true, truncate: 100 },
    },
  }, { query });
}

// data table: count grouped by a terms aggregation
function tableViz(id, indexId, title, field, { size = 10, query } = {}) {
  return viz(id, indexId, {
    title,
    type: "table",
    aggs: [
      { id: "1", enabled: true, type: "count", schema: "metric", params: {} },
      { id: "2", enabled: true, type: "terms", schema: "bucket",
        params: { field, orderBy: "1", order: "desc", size, otherBucket: false, missingBucket: false } },
    ],
    params: {
      perPage: 10,
      showPartialRows: false,
      showMetricsAtAllLevels: false,
      showTotal: false,
      totalFunc: "sum",
      percentageCol: "",
    },
  }, { query });
}

// vertical bar over time; optional stacked split by a terms field
function timeBarViz(id, indexId, title, timeField, { splitField, query } = {}) {
  const aggs = [
    { id: "1", enabled: true, type: "count", schema: "metric", params: {} },
    { id: "2", enabled: true, type: "date_histogram", schema: "segment",
      params: { field: timeField, useNormalizedEsInterval: true, interval: "auto", drop_partials: false, min_doc_count: 1 } },
  ];
  if (splitField) {
    aggs.push({ id: "3", enabled: true, type: "terms", schema: "group",
      params: { field: splitField, orderBy: "1", order: "desc", size: 5, otherBucket: false, missingBucket: false } });
  }
  return viz(id, indexId, {
    title,
    type: "histogram",
    aggs,
    params: {
      type: "histogram",
      grid: { categoryLines: false },
      categoryAxes: [{
        id: "CategoryAxis-1", type: "category", position: "bottom", show: true, style: {},
        scale: { type: "linear" }, labels: { show: true, filter: true, truncate: 100 }, title: {},
      }],
      valueAxes: [{
        id: "ValueAxis-1", name: "LeftAxis-1", type: "value", position: "left", show: true, style: {},
        scale: { type: "linear", mode: "normal" }, labels: { show: true, rotate: 0, filter: false, truncate: 100 },
        title: { text: "Count" },
      }],
      seriesParams: [{
        show: true, type: "histogram", mode: "stacked", data: { label: "Count", id: "1" },
        valueAxis: "ValueAxis-1", drawLinesBetweenPoints: true, lineWidth: 2, showCircles: true,
      }],
      addTooltip: true, addLegend: true, legendPosition: "right",
      times: [], addTimeMarker: false, labels: {},
      thresholdLine: { show: false, value: 10, width: 1, style: "full", color: "#E7664C" },
    },
  }, { query });
}

// ---- visualizations ---------------------------------------------------------
objects.push(
  // Fraud Operations
  metricViz("fdp-viz-tx-count",     "fdp-transactions", "Transactions (total)"),
  metricViz("fdp-viz-fraud-count",  "fdp-fraud-events", "Fraud events (total)"),
  metricViz("fdp-viz-open-alerts",  "fdp-alerts",       "Open alerts", { query: 'status: "OPEN"' }),
  metricViz("fdp-viz-avg-score",    "fdp-fraud-events", "Avg fraud score", { agg: "avg", field: "score" }),
  pieViz("fdp-viz-fraud-severity",  "fdp-fraud-events", "Fraud events by severity", "severity", { size: 4 }),
  pieViz("fdp-viz-tx-decision",     "fdp-transactions", "Transactions by decision", "decision", { size: 3 }),
  tableViz("fdp-viz-top-rules",     "fdp-fraud-events", "Top triggered rules", "triggeredRuleCodes", { size: 15 }),
  timeBarViz("fdp-viz-fraud-over-time", "fdp-fraud-events", "Fraud events over time (by severity)", "occurredAt", { splitField: "severity" }),
  // Audit & Security
  metricViz("fdp-viz-audit-count",  "fdp-audit-events", "Audit events (total)"),
  pieViz("fdp-viz-audit-type",      "fdp-audit-events", "Audit events by type", "eventType", { size: 15 }),
  tableViz("fdp-viz-audit-severity","fdp-audit-events", "Audit events by severity", "severity", { size: 6 }),
  timeBarViz("fdp-viz-audit-over-time", "fdp-audit-events", "Audit events over time", "occurredAt"),
);

// ---- saved searches (Discover) ---------------------------------------------
function savedSearch(id, indexId, title, columns, sortField, query) {
  return {
    type: "search",
    id,
    attributes: {
      title,
      description: "",
      columns,
      sort: [[sortField, "desc"]],
      kibanaSavedObjectMeta: { searchSourceJSON: searchSourceJSON(query) },
    },
    references: [indexRef(indexId)],
  };
}

objects.push(
  savedSearch("fdp-search-critical-fraud", "fdp-fraud-events",
    "Fraud events — HIGH & CRITICAL",
    ["occurredAt", "transactionId", "customerId", "score", "severity", "decision", "primaryReason"],
    "occurredAt", 'severity: "CRITICAL" or severity: "HIGH"'),
  savedSearch("fdp-search-blocked-tx", "fdp-transactions",
    "Transactions — blocked",
    ["occurredAt", "transactionId", "accountId", "amount", "currency", "score", "severity", "decision"],
    "occurredAt", 'decision: "BLOCK"'),
  savedSearch("fdp-search-open-alerts", "fdp-alerts",
    "Alerts — open",
    ["createdAt", "alertId", "transactionId", "severity", "score", "status", "title"],
    "createdAt", 'status: "OPEN"'),
);

// ---- dashboards -------------------------------------------------------------
// panels: [{ vizId, x, y, w, h }]  (grid is 24 columns wide)
function dashboard(id, title, description, panels) {
  const references = [];
  const panelsJSON = panels.map((p, i) => {
    const panelIndex = String(i + 1);
    const refName = `panel_${panelIndex}`;
    references.push({ name: refName, type: "visualization", id: p.vizId });
    return {
      version: PANEL_VERSION,
      type: "visualization",
      gridData: { x: p.x, y: p.y, w: p.w, h: p.h, i: panelIndex },
      panelIndex,
      embeddableConfig: { enhancements: {} },
      panelRefName: refName,
    };
  });
  return {
    type: "dashboard",
    id,
    attributes: {
      title,
      description,
      panelsJSON: JSON.stringify(panelsJSON),
      optionsJSON: JSON.stringify({ useMargins: true, syncColors: false, hidePanelTitles: false }),
      timeRestore: false,
      version: 1,
      kibanaSavedObjectMeta: {
        searchSourceJSON: JSON.stringify({ query: { query: "", language: "kuery" }, filter: [] }),
      },
    },
    references,
  };
}

objects.push(dashboard(
  "fdp-dashboard-fraud-ops",
  "Fraud Operations",
  "Real-time fraud posture: transaction volume, scores, severity mix, decisions, top rules, and open alerts.",
  [
    { vizId: "fdp-viz-tx-count",         x: 0,  y: 0,  w: 6,  h: 8 },
    { vizId: "fdp-viz-fraud-count",      x: 6,  y: 0,  w: 6,  h: 8 },
    { vizId: "fdp-viz-open-alerts",      x: 12, y: 0,  w: 6,  h: 8 },
    { vizId: "fdp-viz-avg-score",        x: 18, y: 0,  w: 6,  h: 8 },
    { vizId: "fdp-viz-fraud-severity",   x: 0,  y: 8,  w: 8,  h: 12 },
    { vizId: "fdp-viz-tx-decision",      x: 8,  y: 8,  w: 8,  h: 12 },
    { vizId: "fdp-viz-top-rules",        x: 16, y: 8,  w: 8,  h: 12 },
    { vizId: "fdp-viz-fraud-over-time",  x: 0,  y: 20, w: 24, h: 12 },
  ],
));

objects.push(dashboard(
  "fdp-dashboard-audit",
  "Audit & Security",
  "Audit-event stream from every service: volume, event-type and severity breakdown, and activity over time.",
  [
    { vizId: "fdp-viz-audit-count",      x: 0,  y: 0,  w: 8,  h: 8 },
    { vizId: "fdp-viz-audit-type",       x: 8,  y: 0,  w: 16, h: 8 },
    { vizId: "fdp-viz-audit-severity",   x: 0,  y: 8,  w: 8,  h: 12 },
    { vizId: "fdp-viz-audit-over-time",  x: 8,  y: 8,  w: 16, h: 12 },
  ],
));

// ---- emit -------------------------------------------------------------------
const ndjson = objects.map((o) => JSON.stringify(o)).join("\n") + "\n";
writeFileSync(OUT, ndjson, "utf8");
console.log(`Wrote ${objects.length} saved objects to ${OUT}`);
const counts = objects.reduce((m, o) => ((m[o.type] = (m[o.type] || 0) + 1), m), {});
console.log("By type:", JSON.stringify(counts));
