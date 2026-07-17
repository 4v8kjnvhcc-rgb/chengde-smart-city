package com.chengde.smartcity.masterdata.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.xml.parsers.DocumentBuilderFactory;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * 画布 DAG ↔ Kettle .ktr XML 转换引擎（先稳妥支持单向导出 + 基础导入）。
 */
@Service
public class KettleTransConverterService {

    private static final ObjectMapper OM = new ObjectMapper();

    private static final Map<String, String> NODE_TO_KETTLE = Map.ofEntries(
            Map.entry("INPUT", "TableInput"),
            Map.entry("FILTER", "FilterRows"),
            Map.entry("FIELD_PROCESS", "SelectValues"),
            Map.entry("DEDUPLICATE", "Unique"),
            Map.entry("MASK", "Calculator"),
            Map.entry("OUTPUT", "TableOutput"),
            Map.entry("JOIN", "MergeJoin"),
            Map.entry("UNION", "Union"),
            Map.entry("SORT", "SortRows"),
            Map.entry("AGGREGATE", "GroupBy"),
            Map.entry("PIVOT", "Denormaliser"),
            Map.entry("UNPIVOT", "Normaliser"),
            Map.entry("SET_VARIABLE", "SetVariable")
    );

    private static final Map<String, String> KETTLE_TO_NODE = reverse(NODE_TO_KETTLE);

    private static Map<String, String> reverse(Map<String, String> src) {
        Map<String, String> m = new HashMap<>();
        for (Map.Entry<String, String> e : src.entrySet()) {
            m.put(e.getValue().toUpperCase(), e.getKey());
        }
        // 兼容别名
        m.put("UNIQUEROWS", "DEDUPLICATE");
        m.put("UNIQUE", "DEDUPLICATE");
        m.put("SORTEDROWS", "SORT");
        m.put("SORTROWS", "SORT");
        m.put("ROWDENORMALISER", "PIVOT");
        m.put("DENORMALISER", "PIVOT");
        m.put("ROWNORMALISER", "UNPIVOT");
        m.put("NORMALISER", "UNPIVOT");
        m.put("DUMMY", "FILTER");
        m.put("TEXTFILEINPUT", "INPUT");
        m.put("TEXTFILEOUTPUT", "OUTPUT");
        return m;
    }

    public String graphToKtr(String graphJson, String transName) {
        try {
            GraphModel graph = parseGraph(graphJson);
            StringBuilder xml = new StringBuilder();
            xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
            xml.append("<transformation>\n");
            xml.append("  <info>\n");
            xml.append("    <name>").append(escapeXml(transName)).append("</name>\n");
            xml.append("    <description>治理任务自动生成</description>\n");
            xml.append("    <created_user>system</created_user>\n");
            xml.append("    <created_date>").append(java.time.LocalDateTime.now()).append("</created_date>\n");
            xml.append("  </info>\n");

            for (NodeDef node : graph.nodes.values()) {
                xml.append(generateStepXml(node));
            }

            xml.append("  <order>\n");
            for (EdgeDef edge : graph.edges) {
                NodeDef from = graph.nodes.get(edge.source);
                NodeDef to = graph.nodes.get(edge.target);
                if (from == null || to == null) continue;
                String fromName = labelOf(from);
                String toName = labelOf(to);
                xml.append("    <hop>\n");
                xml.append("      <from>").append(escapeXml(fromName)).append("</from>\n");
                xml.append("      <to>").append(escapeXml(toName)).append("</to>\n");
                xml.append("      <enabled>Y</enabled>\n");
                xml.append("    </hop>\n");
            }
            xml.append("  </order>\n");
            xml.append("</transformation>");
            return xml.toString();
        } catch (Exception e) {
            throw new RuntimeException("转换DAG到KTR失败: " + e.getMessage(), e);
        }
    }

    /**
     * 将 .ktr XML 导入为平台画布 JSON。
     * 未知 Step 类型降级为 FILTER，并在 config.unknownStepType 中保留原类型。
     */
    public String ktrToGraph(String ktrXml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setExpandEntityReferences(false);
            Document doc = factory.newDocumentBuilder()
                    .parse(new ByteArrayInputStream(ktrXml.getBytes(StandardCharsets.UTF_8)));
            doc.getDocumentElement().normalize();

            NodeList stepNodes = doc.getElementsByTagName("step");
            Map<String, String> nameToId = new LinkedHashMap<>();
            ArrayNode nodesArr = OM.createArrayNode();
            int idx = 0;
            for (int i = 0; i < stepNodes.getLength(); i++) {
                Node n = stepNodes.item(i);
                if (n.getNodeType() != Node.ELEMENT_NODE) continue;
                Element step = (Element) n;
                String name = textChild(step, "name");
                String type = textChild(step, "type");
                if (name == null || name.isBlank()) continue;
                String nodeType = KETTLE_TO_NODE.getOrDefault(type == null ? "" : type.toUpperCase(), "FILTER");
                String id = "n_" + nodeType + "_" + (++idx);
                nameToId.put(name, id);

                ObjectNode node = OM.createObjectNode();
                node.put("id", id);
                node.put("type", "default");
                node.put("label", name);
                ObjectNode position = OM.createObjectNode();
                position.put("x", 120 + (idx % 5) * 160);
                position.put("y", 80 + (idx / 5) * 100);
                node.set("position", position);

                ObjectNode data = OM.createObjectNode();
                data.put("nodeType", nodeType);
                data.put("label", name);
                ObjectNode config = OM.createObjectNode();
                if ("FILTER".equals(nodeType) && type != null && !NODE_TO_KETTLE.containsValue(type)) {
                    config.put("unknownStepType", type);
                }
                data.set("config", config);
                node.set("data", data);
                nodesArr.add(node);
            }

            ArrayNode edgesArr = OM.createArrayNode();
            NodeList hopNodes = doc.getElementsByTagName("hop");
            int eIdx = 0;
            for (int i = 0; i < hopNodes.getLength(); i++) {
                Node n = hopNodes.item(i);
                if (n.getNodeType() != Node.ELEMENT_NODE) continue;
                Element hop = (Element) n;
                String from = textChild(hop, "from");
                String to = textChild(hop, "to");
                String source = nameToId.get(from);
                String target = nameToId.get(to);
                if (source == null || target == null) continue;
                ObjectNode edge = OM.createObjectNode();
                edge.put("id", "e_" + (++eIdx));
                edge.put("source", source);
                edge.put("target", target);
                edgesArr.add(edge);
            }

            ObjectNode root = OM.createObjectNode();
            root.set("nodes", nodesArr);
            root.set("edges", edgesArr);
            return OM.writeValueAsString(root);
        } catch (Exception e) {
            throw new RuntimeException("导入KTR失败: " + e.getMessage(), e);
        }
    }

    private String generateStepXml(NodeDef node) {
        String nodeType = node.data.nodeType;
        String stepName = labelOf(node);
        String kettleType = NODE_TO_KETTLE.getOrDefault(nodeType, "Dummy");
        StringBuilder sb = new StringBuilder();
        sb.append("  <step>\n");
        sb.append("    <name>").append(escapeXml(stepName)).append("</name>\n");
        sb.append("    <type>").append(kettleType).append("</type>\n");
        sb.append("    <description/>\n");
        sb.append("    <distribute>Y</distribute>\n");
        sb.append("    <copies>1</copies>\n");
        sb.append(generateStepConfig(node));
        sb.append("  </step>\n");
        return sb.toString();
    }

    /**
     * 根据画布节点 config 生成 Kettle Step 内部配置 XML。
     */
    private String generateStepConfig(NodeDef node) {
        String nodeType = node.data.nodeType == null ? "" : node.data.nodeType;
        JsonNode cfg = node.data.config;
        return switch (nodeType) {
            case "INPUT" -> cfgInput(cfg);
            case "OUTPUT" -> cfgOutput(cfg);
            case "FILTER" -> cfgFilter(cfg);
            case "FIELD_PROCESS" -> cfgFieldProcess(cfg);
            case "DEDUPLICATE" -> cfgDeduplicate(cfg);
            case "MASK" -> cfgMask(cfg);
            case "JOIN" -> cfgJoin(cfg);
            case "UNION" -> cfgUnion(cfg);
            case "SORT" -> cfgSort(cfg);
            case "AGGREGATE" -> cfgAggregate(cfg);
            case "PIVOT" -> cfgPivot(cfg);
            case "UNPIVOT" -> cfgUnpivot(cfg);
            case "SET_VARIABLE" -> cfgSetVariable(cfg);
            default -> "";
        };
    }

    private String cfgInput(JsonNode cfg) {
        String conn = cfgText(cfg, "connection", "default");
        String sql = cfgText(cfg, "sql", "SELECT 1 AS id");
        int limit = cfgInt(cfg, "rowCount", 0);
        if (limit <= 0) {
            limit = cfgInt(cfg, "limit", 0);
        }
        return "    <connection>" + escapeXml(conn) + "</connection>\n"
                + "    <sql>" + escapeXml(sql) + "</sql>\n"
                + "    <limit>" + limit + "</limit>\n"
                + "    <variables_active>Y</variables_active>\n";
    }

    private String cfgOutput(JsonNode cfg) {
        String conn = cfgText(cfg, "connection", "default");
        String table = cfgText(cfg, "table", "output_table");
        int commit = cfgInt(cfg, "commit", 1000);
        return "    <connection>" + escapeXml(conn) + "</connection>\n"
                + "    <schema/>\n"
                + "    <table>" + escapeXml(table) + "</table>\n"
                + "    <commit>" + commit + "</commit>\n"
                + "    <truncate>N</truncate>\n"
                + "    <ignore_errors>N</ignore_errors>\n"
                + "    <use_batch>Y</use_batch>\n";
    }

    private String cfgFilter(JsonNode cfg) {
        StringBuilder sb = new StringBuilder();
        sb.append("    <send_true_to/>\n");
        sb.append("    <send_false_to/>\n");
        sb.append("    <compare>\n");
        String mode = cfgText(cfg, "mode", "SIMPLE");
        if ("SQL".equalsIgnoreCase(mode)) {
            String expr = cfgText(cfg, "sqlExpr", "1=1");
            // FilterRows 无原生 SQL WHERE，将表达式写入 condition 文本供后续/文档兼容
            sb.append("      <condition>\n");
            sb.append("        <negated>N</negated>\n");
            sb.append("        <conditions>\n");
            sb.append("          <condition>\n");
            sb.append("            <negated>N</negated>\n");
            sb.append("            <leftvalue>").append(escapeXml(expr)).append("</leftvalue>\n");
            sb.append("            <function>=</function>\n");
            sb.append("            <rightvalue>Y</rightvalue>\n");
            sb.append("          </condition>\n");
            sb.append("        </conditions>\n");
            sb.append("      </condition>\n");
        } else {
            String field = cfgText(cfg, "field", "id");
            String op = mapFilterOp(cfgText(cfg, "op", "EQ"));
            String value = cfgText(cfg, "value", "");
            sb.append("      <condition>\n");
            sb.append("        <negated>N</negated>\n");
            sb.append("        <conditions>\n");
            sb.append("          <condition>\n");
            sb.append("            <negated>N</negated>\n");
            sb.append("            <leftvalue>").append(escapeXml(field)).append("</leftvalue>\n");
            sb.append("            <function>").append(escapeXml(op)).append("</function>\n");
            sb.append("            <rightvalue>").append(escapeXml(value)).append("</rightvalue>\n");
            sb.append("          </condition>\n");
            sb.append("        </conditions>\n");
            sb.append("      </condition>\n");
        }
        sb.append("    </compare>\n");
        return sb.toString();
    }

    private String cfgFieldProcess(JsonNode cfg) {
        StringBuilder sb = new StringBuilder();
        sb.append("    <fields>\n");
        JsonNode mappings = cfg != null ? cfg.get("mappings") : null;
        boolean any = false;
        if (mappings != null && mappings.isArray()) {
            for (JsonNode m : mappings) {
                String from = cfgText(m, "from", null);
                String to = cfgText(m, "to", from);
                String expr = cfgText(m, "expr", "COPY");
                if (from == null || from.isBlank()) continue;
                any = true;
                sb.append("      <field>\n");
                sb.append("        <name>").append(escapeXml(from)).append("</name>\n");
                sb.append("        <rename>").append(escapeXml(to != null ? to : from)).append("</rename>\n");
                sb.append("        <length>-1</length>\n");
                sb.append("        <precision>-1</precision>\n");
                if (expr != null && !"COPY".equalsIgnoreCase(expr)) {
                    sb.append("        <comments>").append(escapeXml(expr)).append("</comments>\n");
                }
                sb.append("      </field>\n");
            }
        }
        if (!any) {
            sb.append("      <field>\n");
            sb.append("        <name>id</name>\n");
            sb.append("        <rename>id</rename>\n");
            sb.append("        <length>-1</length>\n");
            sb.append("        <precision>-1</precision>\n");
            sb.append("      </field>\n");
        }
        sb.append("      <select_unspecified>N</select_unspecified>\n");
        sb.append("    </fields>\n");
        return sb.toString();
    }

    private String cfgDeduplicate(JsonNode cfg) {
        List<String> keys = cfgStringList(cfg, "dedupKeys");
        if (keys.isEmpty()) {
            keys = cfgStringList(cfg, "keys");
        }
        if (keys.isEmpty()) {
            keys = List.of("id");
        }
        String keep = cfgText(cfg, "keepStrategy", "FIRST");
        List<String> sortFields = cfgStringList(cfg, "sortFields");
        StringBuilder sb = new StringBuilder();
        sb.append("    <count_rows>N</count_rows>\n");
        sb.append("    <count_field/>\n");
        sb.append("    <reject_duplicate_row>N</reject_duplicate_row>\n");
        sb.append("    <error_description/>\n");
        sb.append("    <fields>\n");
        for (String k : keys) {
            sb.append("      <field>\n");
            sb.append("        <name>").append(escapeXml(k)).append("</name>\n");
            sb.append("        <case_insensitive>N</case_insensitive>\n");
            sb.append("      </field>\n");
        }
        sb.append("    </fields>\n");
        if (!sortFields.isEmpty()) {
            sb.append("    <!-- sortFields=").append(escapeXml(String.join(",", sortFields)))
                    .append("; keepStrategy=").append(escapeXml(keep)).append(" -->\n");
        } else {
            sb.append("    <!-- keepStrategy=").append(escapeXml(keep)).append(" -->\n");
        }
        return sb.toString();
    }

    private String cfgMask(JsonNode cfg) {
        List<String> fields = cfgStringList(cfg, "fields");
        if (fields.isEmpty()) {
            fields = List.of("phone");
        }
        String maskType = cfgText(cfg, "maskType", "BLUR");
        String maskChar = cfgText(cfg, "maskChar", "*");
        StringBuilder sb = new StringBuilder();
        sb.append("    <calculation>\n");
        for (String f : fields) {
            sb.append("      <calc>\n");
            sb.append("        <field_name>").append(escapeXml(f)).append("_masked</field_name>\n");
            sb.append("        <calc_type>");
            if ("MD5".equalsIgnoreCase(maskType)) {
                sb.append("MD5");
            } else {
                // 模糊：用 REPLACE 近似，备注掩码字符
                sb.append("REPLACE");
            }
            sb.append("</calc_type>\n");
            sb.append("        <field_a>").append(escapeXml(f)).append("</field_a>\n");
            sb.append("        <field_b/>\n");
            sb.append("        <field_c/>\n");
            sb.append("        <value_type>String</value_type>\n");
            sb.append("        <value_length>-1</value_length>\n");
            sb.append("        <value_precision>-1</value_precision>\n");
            sb.append("        <remove>N</remove>\n");
            sb.append("        <comments>maskChar=").append(escapeXml(maskChar)).append("</comments>\n");
            sb.append("      </calc>\n");
        }
        sb.append("    </calculation>\n");
        return sb.toString();
    }

    private String cfgJoin(JsonNode cfg) {
        String leftKey = cfgText(cfg, "leftKey", "id");
        String rightKey = cfgText(cfg, "rightKey", leftKey);
        String joinType = cfgText(cfg, "joinType", "INNER");
        String kettleJoin = switch (joinType.toUpperCase()) {
            case "LEFT" -> "LEFT OUTER";
            case "FULL" -> "FULL OUTER";
            default -> "INNER";
        };
        return "    <join_type>" + escapeXml(kettleJoin) + "</join_type>\n"
                + "    <key_1>" + escapeXml(leftKey) + "</key_1>\n"
                + "    <key_2>" + escapeXml(rightKey) + "</key_2>\n";
    }

    private String cfgUnion(JsonNode cfg) {
        // UnionAll in PDI 常为「追加流」步骤；这里写占位元数据
        return "    <pick_copy>0</pick_copy>\n"
                + "    <comments>" + escapeXml(cfgText(cfg, "comment", "union all inputs")) + "</comments>\n";
    }

    private String cfgSort(JsonNode cfg) {
        String field = cfgText(cfg, "field", "id");
        String order = cfgText(cfg, "order", "ASC");
        boolean asc = !"DESC".equalsIgnoreCase(order);
        return "    <directory>%%java.io.tmpdir%%</directory>\n"
                + "    <prefix>out</prefix>\n"
                + "    <sort_size>1000000</sort_size>\n"
                + "    <free_memory>25</free_memory>\n"
                + "    <compress>N</compress>\n"
                + "    <compress_variable/>\n"
                + "    <unique_rows>N</unique_rows>\n"
                + "    <fields>\n"
                + "      <field>\n"
                + "        <name>" + escapeXml(field) + "</name>\n"
                + "        <ascending>" + (asc ? "Y" : "N") + "</ascending>\n"
                + "        <case_sensitive>N</case_sensitive>\n"
                + "        <collator_enabled>N</collator_enabled>\n"
                + "        <collator_strength>0</collator_strength>\n"
                + "        <presorted>N</presorted>\n"
                + "      </field>\n"
                + "    </fields>\n";
    }

    private String cfgAggregate(JsonNode cfg) {
        List<String> groupBy = cfgStringList(cfg, "groupBy");
        JsonNode aggs = cfg != null ? cfg.get("aggs") : null;
        StringBuilder sb = new StringBuilder();
        sb.append("    <all_rows>N</all_rows>\n");
        sb.append("    <ignore_aggregate>N</ignore_aggregate>\n");
        sb.append("    <field_ignore/>\n");
        sb.append("    <directory>%%java.io.tmpdir%%</directory>\n");
        sb.append("    <prefix>grp</prefix>\n");
        sb.append("    <add_line_nr>N</add_line_nr>\n");
        sb.append("    <line_nr_field/>\n");
        sb.append("    <give_back_row>N</give_back_row>\n");
        sb.append("    <group>\n");
        if (groupBy.isEmpty()) {
            sb.append("      <field><name>id</name></field>\n");
        } else {
            for (String g : groupBy) {
                sb.append("      <field><name>").append(escapeXml(g)).append("</name></field>\n");
            }
        }
        sb.append("    </group>\n");
        sb.append("    <fields>\n");
        if (aggs != null && aggs.isArray() && aggs.size() > 0) {
            for (JsonNode a : aggs) {
                String field = cfgText(a, "field", null);
                String op = cfgText(a, "op", "COUNT");
                String alias = cfgText(a, "alias", field + "_" + op);
                if (field == null) continue;
                sb.append("      <field>\n");
                sb.append("        <aggregate>").append(escapeXml(field)).append("</aggregate>\n");
                sb.append("        <subject>").append(escapeXml(field)).append("</subject>\n");
                sb.append("        <type>").append(escapeXml(mapAggType(op))).append("</type>\n");
                sb.append("        <valuefield>").append(escapeXml(alias)).append("</valuefield>\n");
                sb.append("      </field>\n");
            }
        } else {
            sb.append("      <field>\n");
            sb.append("        <aggregate>id</aggregate>\n");
            sb.append("        <subject>id</subject>\n");
            sb.append("        <type>COUNT_ALL</type>\n");
            sb.append("        <valuefield>_count</valuefield>\n");
            sb.append("      </field>\n");
        }
        sb.append("    </fields>\n");
        return sb.toString();
    }

    private String cfgPivot(JsonNode cfg) {
        String pivotField = cfgText(cfg, "pivotField", "category");
        String valueField = cfgText(cfg, "valueField", "amount");
        List<String> groupFields = cfgStringList(cfg, "groupFields");
        StringBuilder sb = new StringBuilder();
        sb.append("    <key_field>").append(escapeXml(pivotField)).append("</key_field>\n");
        sb.append("    <key_fields>\n");
        if (groupFields.isEmpty()) {
            sb.append("      <key_field><key_field>id</key_field></key_field>\n");
        } else {
            for (String g : groupFields) {
                sb.append("      <key_field><key_field>").append(escapeXml(g)).append("</key_field></key_field>\n");
            }
        }
        sb.append("    </key_fields>\n");
        sb.append("    <fields>\n");
        sb.append("      <field>\n");
        sb.append("        <field_name>").append(escapeXml(valueField)).append("</field_name>\n");
        sb.append("        <key_value>DEFAULT</key_value>\n");
        sb.append("        <target_name>").append(escapeXml(valueField)).append("</target_name>\n");
        sb.append("        <value_type>None</value_type>\n");
        sb.append("      </field>\n");
        sb.append("    </fields>\n");
        return sb.toString();
    }

    private String cfgUnpivot(JsonNode cfg) {
        List<String> keyFields = cfgStringList(cfg, "keyFields");
        List<String> unpivotCols = cfgStringList(cfg, "unpivotColumns");
        String nameCol = cfgText(cfg, "nameColumnName", "attribute");
        String valueCol = cfgText(cfg, "valueColumnName", "value");
        StringBuilder sb = new StringBuilder();
        sb.append("    <typefield>").append(escapeXml(nameCol)).append("</typefield>\n");
        sb.append("    <fields>\n");
        if (unpivotCols.isEmpty()) {
            sb.append("      <field>\n");
            sb.append("        <name>col_a</name>\n");
            sb.append("        <value>").append(escapeXml(valueCol)).append("</value>\n");
            sb.append("        <norm>A</norm>\n");
            sb.append("      </field>\n");
        } else {
            for (String col : unpivotCols) {
                sb.append("      <field>\n");
                sb.append("        <name>").append(escapeXml(col)).append("</name>\n");
                sb.append("        <value>").append(escapeXml(valueCol)).append("</value>\n");
                sb.append("        <norm>").append(escapeXml(col)).append("</norm>\n");
                sb.append("      </field>\n");
            }
        }
        sb.append("    </fields>\n");
        if (!keyFields.isEmpty()) {
            sb.append("    <!-- keyFields: ").append(escapeXml(String.join(",", keyFields))).append(" -->\n");
        }
        return sb.toString();
    }

    private String cfgSetVariable(JsonNode cfg) {
        String name = cfgText(cfg, "variableName", cfgText(cfg, "field", "var1"));
        String value = cfgText(cfg, "variableValue", cfgText(cfg, "value", ""));
        return "    <fields>\n"
                + "      <field>\n"
                + "        <field_name>" + escapeXml(name) + "</field_name>\n"
                + "        <variable_name>" + escapeXml(name) + "</variable_name>\n"
                + "        <variable_type>VALID_IN_JVM</variable_type>\n"
                + "        <default_value>" + escapeXml(value) + "</default_value>\n"
                + "      </field>\n"
                + "    </fields>\n"
                + "    <use_formatting>N</use_formatting>\n";
    }

    private static String mapFilterOp(String op) {
        if (op == null) return "=";
        return switch (op.toUpperCase()) {
            case "NE", "NEQ" -> "<>";
            case "GT" -> ">";
            case "GTE" -> ">=";
            case "LT" -> "<";
            case "LTE" -> "<=";
            case "CONTAINS" -> "LIKE";
            case "NOT_NULL" -> "IS NOT NULL";
            case "IS_NULL" -> "IS NULL";
            default -> "=";
        };
    }

    private static String mapAggType(String op) {
        if (op == null) return "COUNT_ALL";
        return switch (op.toUpperCase()) {
            case "SUM" -> "SUM";
            case "AVG", "AVERAGE" -> "AVERAGE";
            case "MAX" -> "MAX";
            case "MIN" -> "MIN";
            case "COUNT" -> "COUNT_ALL";
            default -> "COUNT_ALL";
        };
    }

    private static String cfgText(JsonNode cfg, String field, String def) {
        if (cfg == null || !cfg.has(field) || cfg.get(field).isNull()) return def;
        String s = cfg.get(field).asText();
        return s == null || s.isBlank() ? def : s;
    }

    private static int cfgInt(JsonNode cfg, String field, int def) {
        if (cfg == null || !cfg.has(field) || cfg.get(field).isNull()) return def;
        try {
            return cfg.get(field).asInt(def);
        } catch (Exception e) {
            return def;
        }
    }

    private static List<String> cfgStringList(JsonNode cfg, String field) {
        List<String> list = new ArrayList<>();
        if (cfg == null || !cfg.has(field) || cfg.get(field).isNull()) return list;
        JsonNode n = cfg.get(field);
        if (n.isArray()) {
            n.forEach(item -> {
                String s = item.asText();
                if (s != null && !s.isBlank()) list.add(s.trim());
            });
        } else {
            String s = n.asText("");
            if (!s.isBlank()) {
                for (String p : s.split(",")) {
                    if (!p.trim().isEmpty()) list.add(p.trim());
                }
            }
        }
        return list;
    }

    private static String labelOf(NodeDef node) {
        return node.data.label != null && !node.data.label.isBlank() ? node.data.label : node.id;
    }

    private static String textChild(Element parent, String tag) {
        NodeList list = parent.getElementsByTagName(tag);
        if (list.getLength() == 0) return null;
        return list.item(0).getTextContent();
    }

    private String escapeXml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    private GraphModel parseGraph(String json) throws Exception {
        GraphModel graph = new GraphModel();
        JsonNode root = OM.readTree(json);
        JsonNode nodes = root.get("nodes");
        if (nodes != null && nodes.isArray()) {
            for (JsonNode n : nodes) {
                NodeDef node = new NodeDef();
                node.id = n.path("id").asText();
                JsonNode data = n.path("data");
                node.data = new NodeData();
                node.data.nodeType = data.path("nodeType").asText("FILTER");
                node.data.label = data.path("label").asText(node.id);
                JsonNode cfg = data.path("config");
                if (cfg != null && !cfg.isMissingNode() && !cfg.isNull()) {
                    node.data.config = cfg;
                } else if (!data.isMissingNode()) {
                    // 兼容 config 直接落在 data 上
                    node.data.config = data;
                }
                graph.nodes.put(node.id, node);
            }
        }
        JsonNode edges = root.get("edges");
        if (edges != null && edges.isArray()) {
            for (JsonNode e : edges) {
                EdgeDef edge = new EdgeDef();
                edge.source = e.path("source").asText();
                edge.target = e.path("target").asText();
                graph.edges.add(edge);
            }
        }
        return graph;
    }

    private static class GraphModel {
        Map<String, NodeDef> nodes = new LinkedHashMap<>();
        List<EdgeDef> edges = new ArrayList<>();
    }

    private static class NodeDef {
        String id;
        NodeData data;
    }

    private static class NodeData {
        String nodeType;
        String label;
        JsonNode config;
    }

    private static class EdgeDef {
        String source;
        String target;
    }
}
