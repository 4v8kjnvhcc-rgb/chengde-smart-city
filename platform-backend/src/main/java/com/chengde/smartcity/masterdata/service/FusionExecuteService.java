package com.chengde.smartcity.masterdata.service;

import com.chengde.smartcity.common.exception.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import org.springframework.stereotype.Service;

/**
 * 融合组件内存执行：JOIN / UNION / SORT / AGGREGATE / PIVOT / UNPIVOT。
 */
@Service
public class FusionExecuteService {

    private static final int MAX_ROWS = 10_000;

    public List<Map<String, Object>> execute(String nodeType, JsonNode config,
                                             List<List<Map<String, Object>>> inputs) {
        String type = nodeType == null ? "" : nodeType.toUpperCase();
        return switch (type) {
            case "JOIN" -> execJoin(config, inputs);
            case "UNION" -> execUnion(inputs);
            case "SORT" -> execSort(config, singleInput(inputs));
            case "AGGREGATE" -> execAggregate(config, singleInput(inputs));
            case "PIVOT" -> execPivot(config, singleInput(inputs));
            case "UNPIVOT" -> execUnpivot(config, singleInput(inputs));
            default -> throw new BusinessException(400, "未知融合节点类型: " + nodeType);
        };
    }

    private List<Map<String, Object>> execJoin(JsonNode cfg, List<List<Map<String, Object>>> inputs) {
        if (inputs.size() < 2) {
            throw new BusinessException(400, "JOIN 需要两个上游节点");
        }
        List<Map<String, Object>> left = inputs.get(0);
        List<Map<String, Object>> right = inputs.get(1);
        String leftKey = text(cfg, "leftKey", "id");
        String rightKey = text(cfg, "rightKey", leftKey);
        String joinType = text(cfg, "joinType", "INNER").toUpperCase();
        List<Map<String, Object>> out = new ArrayList<>();
        Map<Object, List<Map<String, Object>>> rightIndex = new HashMap<>();
        for (Map<String, Object> r : right) {
            Object k = r.get(rightKey);
            rightIndex.computeIfAbsent(k, x -> new ArrayList<>()).add(r);
        }
        for (Map<String, Object> l : left) {
            Object k = l.get(leftKey);
            List<Map<String, Object>> matches = rightIndex.getOrDefault(k, List.of());
            if (matches.isEmpty()) {
                if ("LEFT".equals(joinType)) {
                    out.add(new LinkedHashMap<>(l));
                }
            } else {
                for (Map<String, Object> r : matches) {
                    Map<String, Object> m = new LinkedHashMap<>(l);
                    for (Map.Entry<String, Object> e : r.entrySet()) {
                        if (!m.containsKey(e.getKey())) {
                            m.put(e.getKey(), e.getValue());
                        } else {
                            m.put("r_" + e.getKey(), e.getValue());
                        }
                    }
                    out.add(m);
                }
            }
            if (out.size() >= MAX_ROWS) break;
        }
        if ("FULL".equals(joinType)) {
            for (Map<String, Object> r : right) {
                Object k = r.get(rightKey);
                boolean any = left.stream().anyMatch(l -> Objects.equals(l.get(leftKey), k));
                if (!any) {
                    out.add(new LinkedHashMap<>(r));
                    if (out.size() >= MAX_ROWS) break;
                }
            }
        }
        return out;
    }

    private List<Map<String, Object>> execUnion(List<List<Map<String, Object>>> inputs) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (List<Map<String, Object>> in : inputs) {
            for (Map<String, Object> row : in) {
                out.add(new LinkedHashMap<>(row));
                if (out.size() >= MAX_ROWS) break;
            }
            if (out.size() >= MAX_ROWS) break;
        }
        return out;
    }

    private List<Map<String, Object>> execSort(JsonNode cfg, List<Map<String, Object>> incoming) {
        String field = text(cfg, "field", "id");
        boolean asc = !"DESC".equalsIgnoreCase(text(cfg, "order", "ASC"));
        List<Map<String, Object>> out = new ArrayList<>(incoming);
        Comparator<String> strCmp = asc ? Comparator.naturalOrder() : Comparator.reverseOrder();
        out.sort(Comparator.comparing(r -> String.valueOf(r.get(field)), strCmp));
        if (out.size() > MAX_ROWS) {
            return new ArrayList<>(out.subList(0, MAX_ROWS));
        }
        return out;
    }

    private List<Map<String, Object>> execAggregate(JsonNode cfg, List<Map<String, Object>> incoming) {
        List<String> groupBy = readStringList(cfg, "groupBy");
        JsonNode aggs = cfg != null ? cfg.get("aggs") : null;
        Map<String, Map<String, Object>> groups = new LinkedHashMap<>();
        Map<String, AggState> states = new HashMap<>();
        for (Map<String, Object> row : incoming) {
            String gk = groupKey(row, groupBy);
            groups.putIfAbsent(gk, groupRow(row, groupBy));
            AggState st = states.computeIfAbsent(gk, k -> new AggState());
            if (aggs != null && aggs.isArray()) {
                for (JsonNode a : aggs) {
                    String field = text(a, "field", null);
                    String op = text(a, "op", "COUNT").toUpperCase();
                    String alias = text(a, "alias", field + "_" + op);
                    if (field == null) continue;
                    Object val = row.get(field);
                    st.accumulate(alias, op, val);
                }
            } else {
                st.accumulate("_count", "COUNT", 1);
            }
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map.Entry<String, Map<String, Object>> e : groups.entrySet()) {
            Map<String, Object> m = new LinkedHashMap<>(e.getValue());
            AggState st = states.get(e.getKey());
            if (st != null) m.putAll(st.result());
            out.add(m);
            if (out.size() >= MAX_ROWS) break;
        }
        return out;
    }

    private List<Map<String, Object>> execPivot(JsonNode cfg, List<Map<String, Object>> incoming) {
        String pivotField = text(cfg, "pivotField", null);
        String valueField = text(cfg, "valueField", null);
        List<String> groupFields = readStringList(cfg, "groupFields");
        if (pivotField == null || valueField == null) {
            throw new BusinessException(400, "PIVOT 须配置 pivotField/valueField");
        }
        Map<String, Map<String, Object>> rows = new LinkedHashMap<>();
        for (Map<String, Object> row : incoming) {
            String gk = groupKey(row, groupFields);
            Map<String, Object> m = rows.computeIfAbsent(gk, k -> groupRow(row, groupFields));
            Object pivotVal = row.get(pivotField);
            if (pivotVal != null) {
                m.put(String.valueOf(pivotVal), row.get(valueField));
            }
        }
        return new ArrayList<>(rows.values());
    }

    private List<Map<String, Object>> execUnpivot(JsonNode cfg, List<Map<String, Object>> incoming) {
        List<String> keyFields = readStringList(cfg, "keyFields");
        List<String> unpivotCols = readStringList(cfg, "unpivotColumns");
        String valueCol = text(cfg, "valueColumnName", "value");
        String nameCol = text(cfg, "nameColumnName", "attribute");
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> row : incoming) {
            if (unpivotCols.isEmpty()) {
                for (Map.Entry<String, Object> e : row.entrySet()) {
                    if (keyFields.contains(e.getKey())) continue;
                    Map<String, Object> m = groupRow(row, keyFields);
                    m.put(nameCol, e.getKey());
                    m.put(valueCol, e.getValue());
                    out.add(m);
                    if (out.size() >= MAX_ROWS) break;
                }
            } else {
                for (String col : unpivotCols) {
                    Map<String, Object> m = groupRow(row, keyFields);
                    m.put(nameCol, col);
                    m.put(valueCol, row.get(col));
                    out.add(m);
                    if (out.size() >= MAX_ROWS) break;
                }
            }
            if (out.size() >= MAX_ROWS) break;
        }
        return out;
    }

    private static List<Map<String, Object>> singleInput(List<List<Map<String, Object>>> inputs) {
        if (inputs.isEmpty()) return new ArrayList<>();
        return new ArrayList<>(inputs.get(0));
    }

    private static String groupKey(Map<String, Object> row, List<String> keys) {
        if (keys.isEmpty()) return "_all";
        StringBuilder sb = new StringBuilder();
        for (String k : keys) {
            sb.append(Objects.toString(row.get(k), "")).append('\u0001');
        }
        return sb.toString();
    }

    private static Map<String, Object> groupRow(Map<String, Object> row, List<String> keys) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (keys.isEmpty()) return m;
        for (String k : keys) {
            m.put(k, row.get(k));
        }
        return m;
    }

    private static List<String> readStringList(JsonNode cfg, String field) {
        List<String> list = new ArrayList<>();
        if (cfg != null && cfg.has(field) && cfg.get(field).isArray()) {
            cfg.get(field).forEach(n -> list.add(n.asText()));
        } else if (cfg != null && cfg.has(field)) {
            String s = cfg.get(field).asText("");
            if (!s.isBlank()) {
                for (String p : s.split(",")) {
                    if (!p.trim().isEmpty()) list.add(p.trim());
                }
            }
        }
        return list;
    }

    private static String text(JsonNode n, String field, String def) {
        if (n == null || !n.has(field) || n.get(field).isNull()) return def;
        String s = n.get(field).asText();
        return s == null || s.isBlank() ? def : s;
    }

    private static class AggState {
        private final Map<String, Double> sums = new HashMap<>();
        private final Map<String, Long> counts = new HashMap<>();
        private final Map<String, Double> maxes = new TreeMap<>();
        private final Map<String, Double> mins = new TreeMap<>();

        void accumulate(String alias, String op, Object val) {
            counts.merge(alias, 1L, Long::sum);
            double d = toDouble(val);
            switch (op) {
                case "SUM" -> sums.merge(alias, d, Double::sum);
                case "AVG" -> sums.merge(alias, d, Double::sum);
                case "MAX" -> maxes.merge(alias, d, Math::max);
                case "MIN" -> mins.merge(alias, d, Math::min);
                default -> { /* COUNT handled above */ }
            }
        }

        Map<String, Object> result() {
            Map<String, Object> m = new LinkedHashMap<>();
            for (Map.Entry<String, Long> e : counts.entrySet()) {
                String alias = e.getKey();
                m.put(alias, e.getValue());
                if (sums.containsKey(alias)) {
                    double sum = sums.get(alias);
                    if (alias.contains("AVG") || alias.endsWith("_AVG")) {
                        m.put(alias, sum / Math.max(1, e.getValue()));
                    } else {
                        m.put(alias, sum);
                    }
                }
                if (maxes.containsKey(alias)) m.put(alias, maxes.get(alias));
                if (mins.containsKey(alias)) m.put(alias, mins.get(alias));
            }
            return m;
        }

        private static double toDouble(Object val) {
            if (val == null) return 0;
            if (val instanceof Number n) return n.doubleValue();
            try { return Double.parseDouble(String.valueOf(val)); } catch (Exception ex) { return 0; }
        }
    }
}
