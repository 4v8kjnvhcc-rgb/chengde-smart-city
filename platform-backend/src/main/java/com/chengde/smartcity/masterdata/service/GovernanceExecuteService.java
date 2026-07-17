package com.chengde.smartcity.masterdata.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chengde.smartcity.common.exception.BusinessException;
import com.chengde.smartcity.masterdata.entity.GovGovernanceNodeLog;
import com.chengde.smartcity.masterdata.entity.GovGovernanceTask;
import com.chengde.smartcity.masterdata.entity.GovGovernanceTaskRun;
import com.chengde.smartcity.masterdata.mapper.GovGovernanceNodeLogMapper;
import com.chengde.smartcity.masterdata.mapper.GovGovernanceTaskMapper;
import com.chengde.smartcity.masterdata.mapper.GovGovernanceTaskRunMapper;
import com.chengde.smartcity.security.UserPrincipal;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 治理 ETL 拓扑执行：INPUT/FILTER/FIELD_PROCESS/DEDUPLICATE/MASK/OUTPUT。
 * 节点间内存传数，上限 10000 行。
 */
@Service
public class GovernanceExecuteService {

    private static final Logger log = LoggerFactory.getLogger(GovernanceExecuteService.class);
    private static final int MAX_ROWS = 10_000;
    private static final ObjectMapper OM = new ObjectMapper();

    private final GovGovernanceTaskMapper taskMapper;
    private final GovGovernanceTaskRunMapper runMapper;
    private final GovGovernanceNodeLogMapper nodeLogMapper;
    private final FusionExecuteService fusionExecuteService;

    public GovernanceExecuteService(GovGovernanceTaskMapper taskMapper,
                                    GovGovernanceTaskRunMapper runMapper,
                                    GovGovernanceNodeLogMapper nodeLogMapper,
                                    FusionExecuteService fusionExecuteService) {
        this.taskMapper = taskMapper;
        this.runMapper = runMapper;
        this.nodeLogMapper = nodeLogMapper;
        this.fusionExecuteService = fusionExecuteService;
    }

    @Transactional
    public Map<String, Object> executeTask(UserPrincipal operator, Long taskId) {
        GovGovernanceTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException(404, "治理任务不存在: " + taskId);
        }
        if ("RUNNING".equals(task.getStatus())) {
            throw new BusinessException(400, "任务已在运行中");
        }
        String graphJson = task.getGraphJson();
        if (graphJson == null || graphJson.isBlank()) {
            throw new BusinessException(400, "任务画布为空，请先设计图");
        }

        GraphModel graph;
        try {
            graph = parseGraph(graphJson);
        } catch (Exception e) {
            throw new BusinessException(400, "画布 JSON 解析失败: " + e.getMessage());
        }
        if (graph.nodes.isEmpty()) {
            throw new BusinessException(400, "画布无节点");
        }

        List<String> order = topologicalSort(graph);
        String user = operator != null ? operator.getUsername() : "system";

        GovGovernanceTaskRun run = new GovGovernanceTaskRun();
        run.setTaskId(taskId);
        run.setStatus("RUNNING");
        run.setStartedAt(LocalDateTime.now());
        run.setTotalNodes(order.size());
        run.setSuccessNodes(0);
        run.setFailedNodes(0);
        run.setRowCount(0);
        run.setTriggeredBy(user);
        run.setCreatedAt(LocalDateTime.now());
        runMapper.insert(run);

        task.setStatus("RUNNING");
        task.setLastRunAt(LocalDateTime.now());
        task.setLastMessage("运行中 runId=" + run.getId());
        task.setUpdatedAt(LocalDateTime.now());
        taskMapper.updateById(task);

        Map<String, List<Map<String, Object>>> outputs = new HashMap<>();
        int success = 0;
        int failed = 0;
        int finalRows = 0;
        boolean stopped = false;
        String failMsg = null;

        for (String nodeId : order) {
            GovGovernanceTaskRun latest = runMapper.selectById(run.getId());
            if (latest != null && "STOPPED".equals(latest.getStatus())) {
                stopped = true;
                writeNodeLog(run.getId(), taskId, nodeId, graph.nodes.get(nodeId),
                        "STOPPED", 0, 0, "任务已停止", null);
                break;
            }

            NodeDef node = graph.nodes.get(nodeId);
            LocalDateTime nStart = LocalDateTime.now();
            try {
                List<Map<String, Object>> incoming = collectIncoming(graph, nodeId, outputs);
                int inRows = incoming.size();
                List<Map<String, Object>> out = executeNode(node, nodeId, graph, outputs, incoming);
                if (out.size() > MAX_ROWS) {
                    out = new ArrayList<>(out.subList(0, MAX_ROWS));
                }
                outputs.put(nodeId, out);
                finalRows = out.size();
                writeNodeLog(run.getId(), taskId, nodeId, node, "SUCCESS", inRows, out.size(),
                        "ok", sampleDetail(out));
                success++;
            } catch (Exception e) {
                failed++;
                failMsg = e.getMessage();
                log.warn("governance node failed task={} node={}: {}", taskId, nodeId, e.getMessage());
                writeNodeLog(run.getId(), taskId, nodeId, node, "FAILED", 0, 0,
                        truncate(e.getMessage(), 500), null);
                break;
            }
            log.debug("node {} done in {}", nodeId, nStart);
        }

        run.setSuccessNodes(success);
        run.setFailedNodes(failed);
        run.setRowCount(finalRows);
        run.setEndedAt(LocalDateTime.now());
        if (stopped) {
            run.setStatus("STOPPED");
            run.setMessage("已停止");
            task.setStatus("STOPPED");
            task.setLastMessage("已停止");
        } else if (failed > 0) {
            run.setStatus("FAILED");
            run.setMessage(truncate(failMsg, 500));
            task.setStatus("READY");
            task.setLastMessage(truncate(failMsg, 500));
        } else {
            run.setStatus("SUCCESS");
            run.setMessage("成功，输出 " + finalRows + " 行");
            task.setStatus("READY");
            task.setLastMessage("成功，输出 " + finalRows + " 行");
        }
        task.setLastRunAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());
        runMapper.updateById(run);
        taskMapper.updateById(task);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("runId", run.getId());
        result.put("status", run.getStatus());
        result.put("rowCount", finalRows);
        result.put("successNodes", success);
        result.put("failedNodes", failed);
        result.put("message", run.getMessage());
        return result;
    }

    @Transactional
    public Map<String, Object> stopTask(UserPrincipal operator, Long taskId) {
        GovGovernanceTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException(404, "治理任务不存在: " + taskId);
        }
        List<GovGovernanceTaskRun> running = runMapper.selectList(new LambdaQueryWrapper<GovGovernanceTaskRun>()
                .eq(GovGovernanceTaskRun::getTaskId, taskId)
                .eq(GovGovernanceTaskRun::getStatus, "RUNNING")
                .orderByDesc(GovGovernanceTaskRun::getId)
                .last("LIMIT 1"));
        if (running.isEmpty()) {
            throw new BusinessException(400, "无运行中的实例");
        }
        GovGovernanceTaskRun run = running.get(0);
        run.setStatus("STOPPED");
        run.setEndedAt(LocalDateTime.now());
        run.setMessage("用户停止");
        runMapper.updateById(run);

        task.setStatus("STOPPED");
        task.setLastMessage("用户停止");
        task.setUpdatedAt(LocalDateTime.now());
        taskMapper.updateById(task);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("runId", run.getId());
        result.put("status", "STOPPED");
        result.put("message", "已请求停止");
        return result;
    }

    private List<Map<String, Object>> executeNode(NodeDef node, String nodeId, GraphModel graph,
                                                  Map<String, List<Map<String, Object>>> outputs,
                                                  List<Map<String, Object>> incoming) {
        String type = node.type == null ? "" : node.type.toUpperCase();
        if (isFusionType(type)) {
            List<List<Map<String, Object>>> allInputs = collectAllInputs(graph, nodeId, outputs);
            return fusionExecuteService.execute(type, node.config, allInputs);
        }
        return switch (type) {
            case "INPUT" -> execInput(node);
            case "FILTER" -> execFilter(node, incoming);
            case "FIELD_PROCESS" -> execFieldProcess(node, incoming);
            case "DEDUPLICATE" -> execDeduplicate(node, incoming);
            case "MASK" -> execMask(node, incoming);
            case "OUTPUT" -> new ArrayList<>(incoming);
            default -> throw new BusinessException(400, "未知节点类型: " + node.type);
        };
    }

    private static boolean isFusionType(String type) {
        return switch (type) {
            case "JOIN", "UNION", "SORT", "AGGREGATE", "PIVOT", "UNPIVOT" -> true;
            default -> false;
        };
    }

    private List<List<Map<String, Object>>> collectAllInputs(GraphModel graph, String nodeId,
                                                              Map<String, List<Map<String, Object>>> outputs) {
        List<String> preds = graph.incoming.getOrDefault(nodeId, Collections.emptyList());
        List<List<Map<String, Object>>> all = new ArrayList<>();
        for (String p : preds) {
            all.add(new ArrayList<>(outputs.getOrDefault(p, List.of())));
        }
        return all;
    }

    private List<Map<String, Object>> execInput(NodeDef node) {
        JsonNode cfg = node.config;
        List<Map<String, Object>> rows = new ArrayList<>();
        if (cfg != null && cfg.has("rows") && cfg.get("rows").isArray()) {
            for (JsonNode r : cfg.get("rows")) {
                Map<String, Object> m = new LinkedHashMap<>();
                r.fields().forEachRemaining(e -> m.put(e.getKey(), jsonValue(e.getValue())));
                rows.add(m);
                if (rows.size() >= MAX_ROWS) break;
            }
        } else {
            int count = 10;
            if (cfg != null && cfg.has("rowCount")) {
                count = Math.min(MAX_ROWS, Math.max(1, cfg.get("rowCount").asInt(10)));
            }
            for (int i = 1; i <= count; i++) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", i);
                m.put("name", "demo_" + i);
                m.put("phone", "138" + String.format("%08d", i));
                m.put("idCard", "13080019900101" + String.format("%04d", i));
                m.put("amount", i * 10);
                rows.add(m);
            }
        }
        return rows;
    }

    private List<Map<String, Object>> execFilter(NodeDef node, List<Map<String, Object>> incoming) {
        JsonNode cfg = node.config;
        String mode = text(cfg, "mode", "SIMPLE");
        if ("SQL".equalsIgnoreCase(mode)) {
            String sqlExpr = text(cfg, "sqlExpr", "");
            if (sqlExpr == null || sqlExpr.isBlank()) {
                return new ArrayList<>(incoming);
            }
            // 轻量近似：仅支持 field op value 的简单表达式解析（age > 18）
            return execSimpleSqlFilter(incoming, sqlExpr);
        }
        String field = text(cfg, "field", null);
        String op = text(cfg, "op", "EQ");
        String value = text(cfg, "value", "");
        if (field == null || field.isBlank()) {
            return new ArrayList<>(incoming);
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> row : incoming) {
            Object raw = row.get(field);
            if (match(raw, op, value)) {
                out.add(row);
            }
            if (out.size() >= MAX_ROWS) break;
        }
        return out;
    }

    private List<Map<String, Object>> execSimpleSqlFilter(List<Map<String, Object>> incoming, String expr) {
        // 支持: field = 'x' | field > n | field AND field2 = 'y'（简化拆分）
        String[] andParts = expr.split("(?i)\\s+AND\\s+");
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> row : incoming) {
            boolean ok = true;
            for (String part : andParts) {
                String p = part.trim();
                if (!matchSqlClause(row, p)) {
                    ok = false;
                    break;
                }
            }
            if (ok) {
                out.add(row);
            }
            if (out.size() >= MAX_ROWS) break;
        }
        return out;
    }

    private boolean matchSqlClause(Map<String, Object> row, String clause) {
        String[] ops = {"!=", "<>", ">=", "<=", "=", ">", "<"};
        for (String op : ops) {
            int idx = clause.indexOf(op);
            if (idx > 0) {
                String field = clause.substring(0, idx).trim();
                String rawVal = clause.substring(idx + op.length()).trim().replace("'", "").replace("\"", "");
                String mapped = switch (op) {
                    case "=" -> "EQ";
                    case "!=", "<>" -> "NE";
                    case ">" -> "GT";
                    case ">=" -> "GTE";
                    case "<" -> "LT";
                    case "<=" -> "LTE";
                    default -> "EQ";
                };
                return match(row.get(field), mapped, rawVal);
            }
        }
        return true;
    }

    private List<Map<String, Object>> execFieldProcess(NodeDef node, List<Map<String, Object>> incoming) {
        JsonNode cfg = node.config;
        List<Map<String, Object>> out = new ArrayList<>();
        JsonNode mappings = cfg != null ? cfg.get("mappings") : null;
        for (Map<String, Object> row : incoming) {
            Map<String, Object> next = new LinkedHashMap<>(row);
            if (mappings != null && mappings.isArray()) {
                for (JsonNode map : mappings) {
                    String from = text(map, "from", null);
                    String to = text(map, "to", from);
                    String expr = text(map, "expr", "COPY");
                    if (from == null) continue;
                    Object val = row.get(from);
                    if ("UPPER".equalsIgnoreCase(expr) && val != null) {
                        val = String.valueOf(val).toUpperCase();
                    } else if ("LOWER".equalsIgnoreCase(expr) && val != null) {
                        val = String.valueOf(val).toLowerCase();
                    } else if ("TRIM".equalsIgnoreCase(expr) && val != null) {
                        val = String.valueOf(val).trim();
                    }
                    if (to != null) {
                        next.put(to, val);
                        if (!to.equals(from) && (cfg == null || !cfg.path("keepSource").asBoolean(true))) {
                            next.remove(from);
                        }
                    }
                }
            }
            out.add(next);
            if (out.size() >= MAX_ROWS) break;
        }
        return out;
    }

    private List<Map<String, Object>> execDeduplicate(NodeDef node, List<Map<String, Object>> incoming) {
        JsonNode cfg = node.config;
        List<String> keys = new ArrayList<>();
        if (cfg != null && cfg.has("dedupKeys") && cfg.get("dedupKeys").isArray()) {
            cfg.get("dedupKeys").forEach(k -> keys.add(k.asText()));
        } else if (cfg != null && cfg.has("keys") && cfg.get("keys").isArray()) {
            cfg.get("keys").forEach(k -> keys.add(k.asText()));
        }
        if (keys.isEmpty()) {
            keys.add("id");
        }
        List<String> sortFields = new ArrayList<>();
        if (cfg != null && cfg.has("sortFields") && cfg.get("sortFields").isArray()) {
            cfg.get("sortFields").forEach(k -> sortFields.add(k.asText()));
        }
        String keep = text(cfg, "keepStrategy", "FIRST");
        List<Map<String, Object>> sorted = new ArrayList<>(incoming);
        if (!sortFields.isEmpty()) {
            sorted.sort((a, b) -> {
                for (String f : sortFields) {
                    int c = Objects.toString(a.get(f), "").compareTo(Objects.toString(b.get(f), ""));
                    if (c != 0) return c;
                }
                return 0;
            });
            if ("LAST".equalsIgnoreCase(keep)) {
                Collections.reverse(sorted);
            }
        } else if ("LAST".equalsIgnoreCase(keep)) {
            Collections.reverse(sorted);
        }
        Set<String> seen = new HashSet<>();
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> row : sorted) {
            StringBuilder sb = new StringBuilder();
            for (String k : keys) {
                sb.append(Objects.toString(row.get(k), "")).append('\u0001');
            }
            if (seen.add(sb.toString())) {
                out.add(row);
            }
            if (out.size() >= MAX_ROWS) break;
        }
        return out;
    }

    private List<Map<String, Object>> execMask(NodeDef node, List<Map<String, Object>> incoming) {
        JsonNode cfg = node.config;
        List<String> fields = new ArrayList<>();
        if (cfg != null && cfg.has("fields") && cfg.get("fields").isArray()) {
            cfg.get("fields").forEach(f -> fields.add(f.asText()));
        }
        if (fields.isEmpty()) {
            fields.add("phone");
            fields.add("idCard");
        }
        String maskType = text(cfg, "maskType", "BLUR");
        String maskChar = text(cfg, "maskChar", "*");
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> row : incoming) {
            Map<String, Object> next = new LinkedHashMap<>(row);
            for (String f : fields) {
                Object v = next.get(f);
                if (v != null) {
                    if ("MD5".equalsIgnoreCase(maskType)) {
                        next.put(f, md5Hash(String.valueOf(v)));
                    } else {
                        next.put(f, maskValue(String.valueOf(v), maskChar));
                    }
                }
            }
            out.add(next);
            if (out.size() >= MAX_ROWS) break;
        }
        return out;
    }

    private static String md5Hash(String input) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
            byte[] array = md.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : array) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return input;
        }
    }

    private static String maskValue(String s, String maskChar) {
        if (s.length() <= 4) {
            return maskChar.repeat(s.length());
        }
        int keep = 2;
        String mid = maskChar.repeat(Math.max(1, s.length() - keep * 2));
        return s.substring(0, keep) + mid + s.substring(s.length() - keep);
    }

    private static boolean match(Object raw, String op, String value) {
        String left = raw == null ? "" : String.valueOf(raw);
        String uop = op == null ? "EQ" : op.toUpperCase();
        return switch (uop) {
            case "NE", "NEQ" -> !left.equals(value);
            case "CONTAINS" -> left.contains(value);
            case "GT" -> compareNum(left, value) > 0;
            case "GTE" -> compareNum(left, value) >= 0;
            case "LT" -> compareNum(left, value) < 0;
            case "LTE" -> compareNum(left, value) <= 0;
            case "NOT_NULL" -> raw != null && !left.isBlank();
            case "IS_NULL" -> raw == null || left.isBlank();
            default -> left.equals(value);
        };
    }

    private static int compareNum(String a, String b) {
        try {
            return Double.compare(Double.parseDouble(a), Double.parseDouble(b));
        } catch (Exception e) {
            return a.compareTo(b);
        }
    }

    private List<Map<String, Object>> collectIncoming(GraphModel graph, String nodeId,
                                                      Map<String, List<Map<String, Object>>> outputs) {
        List<String> preds = graph.incoming.getOrDefault(nodeId, Collections.emptyList());
        if (preds.isEmpty()) {
            return new ArrayList<>();
        }
        if (preds.size() == 1) {
            return new ArrayList<>(outputs.getOrDefault(preds.get(0), List.of()));
        }
        // 多入边：按行位置合并字段
        List<Map<String, Object>> base = new ArrayList<>(outputs.getOrDefault(preds.get(0), List.of()));
        for (int i = 1; i < preds.size(); i++) {
            List<Map<String, Object>> other = outputs.getOrDefault(preds.get(i), List.of());
            int n = Math.min(base.size(), other.size());
            List<Map<String, Object>> merged = new ArrayList<>();
            for (int r = 0; r < n && merged.size() < MAX_ROWS; r++) {
                Map<String, Object> m = new LinkedHashMap<>(base.get(r));
                m.putAll(other.get(r));
                merged.add(m);
            }
            base = merged;
        }
        return base;
    }

    private List<String> topologicalSort(GraphModel graph) {
        Map<String, Integer> indeg = new HashMap<>();
        for (String id : graph.nodes.keySet()) {
            indeg.put(id, 0);
        }
        for (Map.Entry<String, List<String>> e : graph.outgoing.entrySet()) {
            for (String t : e.getValue()) {
                indeg.merge(t, 1, Integer::sum);
            }
        }
        ArrayDeque<String> q = new ArrayDeque<>();
        for (Map.Entry<String, Integer> e : indeg.entrySet()) {
            if (e.getValue() == 0) q.add(e.getKey());
        }
        List<String> order = new ArrayList<>();
        while (!q.isEmpty()) {
            String u = q.poll();
            order.add(u);
            for (String v : graph.outgoing.getOrDefault(u, List.of())) {
                int d = indeg.merge(v, -1, Integer::sum);
                if (d == 0) q.add(v);
            }
        }
        if (order.size() != graph.nodes.size()) {
            throw new BusinessException(400, "画布存在环，无法拓扑执行");
        }
        return order;
    }

    private GraphModel parseGraph(String json) throws Exception {
        JsonNode root = OM.readTree(json);
        GraphModel g = new GraphModel();
        JsonNode nodes = root.get("nodes");
        if (nodes != null && nodes.isArray()) {
            for (JsonNode n : nodes) {
                NodeDef def = new NodeDef();
                def.id = n.path("id").asText();
                String type = n.path("type").asText(null);
                if (type == null || type.isBlank()) {
                    type = n.path("data").path("nodeType").asText("FILTER");
                }
                // vue-flow 自定义 type 可能是 default，以 data.nodeType 为准
                String dataType = n.path("data").path("nodeType").asText(null);
                if (dataType != null && !dataType.isBlank()) {
                    type = dataType;
                }
                def.type = type;
                def.label = n.path("data").path("label").asText(
                        n.path("label").asText(def.id));
                def.config = n.path("data").path("config");
                if (def.config == null || def.config.isMissingNode()) {
                    def.config = n.path("data");
                }
                g.nodes.put(def.id, def);
            }
        }
        JsonNode edges = root.get("edges");
        if (edges != null && edges.isArray()) {
            for (JsonNode e : edges) {
                String s = e.path("source").asText();
                String t = e.path("target").asText();
                if (s.isBlank() || t.isBlank()) continue;
                g.outgoing.computeIfAbsent(s, k -> new ArrayList<>()).add(t);
                g.incoming.computeIfAbsent(t, k -> new ArrayList<>()).add(s);
            }
        }
        return g;
    }

    private void writeNodeLog(Long runId, Long taskId, String nodeId, NodeDef node,
                              String status, int inRows, int outRows, String message, String detail) {
        GovGovernanceNodeLog nl = new GovGovernanceNodeLog();
        nl.setRunId(runId);
        nl.setTaskId(taskId);
        nl.setNodeId(nodeId);
        nl.setNodeType(node != null ? node.type : "UNKNOWN");
        nl.setNodeName(node != null ? node.label : nodeId);
        nl.setStatus(status);
        nl.setStartedAt(LocalDateTime.now());
        nl.setEndedAt(LocalDateTime.now());
        nl.setInputRows(inRows);
        nl.setOutputRows(outRows);
        nl.setMessage(truncate(message, 500));
        nl.setDetailJson(detail);
        nl.setCreatedAt(LocalDateTime.now());
        nodeLogMapper.insert(nl);
    }

    private String sampleDetail(List<Map<String, Object>> rows) {
        try {
            int n = Math.min(3, rows.size());
            return OM.writeValueAsString(rows.subList(0, n));
        } catch (Exception e) {
            return null;
        }
    }

    private static Object jsonValue(JsonNode n) {
        if (n == null || n.isNull()) return null;
        if (n.isNumber()) return n.numberValue();
        if (n.isBoolean()) return n.booleanValue();
        return n.asText();
    }

    private static String text(JsonNode n, String field, String def) {
        if (n == null || !n.has(field) || n.get(field).isNull()) return def;
        String s = n.get(field).asText();
        return s == null || s.isBlank() ? def : s;
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }

    private static class GraphModel {
        final Map<String, NodeDef> nodes = new LinkedHashMap<>();
        final Map<String, List<String>> outgoing = new HashMap<>();
        final Map<String, List<String>> incoming = new HashMap<>();
    }

    private static class NodeDef {
        String id;
        String type;
        String label;
        JsonNode config;
    }
}
