package com.chengde.smartcity.masterdata.service;

import com.chengde.smartcity.integration.config.IntegrationProperties;
import com.chengde.smartcity.integration.kettle.KettleConnectionService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.xml.parsers.DocumentBuilderFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * 画布 DAG ↔ Kettle .ktr XML 转换引擎（导出含平台目标库 connection，可供 Carte 执行）。
 */
@Service
public class KettleTransConverterService {

    private static final ObjectMapper OM = new ObjectMapper();
    private static final Logger log = LoggerFactory.getLogger(KettleTransConverterService.class);

    private final IntegrationProperties integrationProperties;
    private final KettleConnectionService connectionService;

    public KettleTransConverterService(IntegrationProperties integrationProperties,
                                       KettleConnectionService connectionService) {
        this.integrationProperties = integrationProperties;
        this.connectionService = connectionService;
    }

    private static final Map<String, String> NODE_TO_KETTLE = Map.ofEntries(
            Map.entry("INPUT", "TableInput"),
            Map.entry("FILTER", "FilterRows"),
            Map.entry("FIELD_PROCESS", "SelectValues"),
            Map.entry("DEDUPLICATE", "Unique"),
            Map.entry("MASK", "ScriptValueMod"),
            Map.entry("OUTPUT", "TableOutput"),
            Map.entry("JOIN", "MergeJoin"),
            Map.entry("UNION", "Union"),
            Map.entry("SORT", "SortRows"),
            Map.entry("AGGREGATE", "GroupBy"),
            Map.entry("PIVOT", "Denormaliser"),
            Map.entry("UNPIVOT", "Normaliser"),
            Map.entry("SET_VARIABLE", "SetVariable"),
            Map.entry("SPLIT", "SplitField"),
            Map.entry("VALUE_MAPPER", "ValueMapper"),
            Map.entry("CONSTANT", "Constant"),
            Map.entry("FORMULA", "Formula"),
            Map.entry("STRING_CUT", "StringCut"),
            Map.entry("REPLACE_STRING", "ReplaceString"),
            Map.entry("NULL_IF", "NullIf"),
            Map.entry("IF_NULL", "IfNull"),
            Map.entry("TYPE_CONVERT", "SelectValues"),
            Map.entry("SELECT_FIELDS", "SelectValues"),
            Map.entry("SWITCH_CASE", "SwitchCase"),
            Map.entry("VALIDATOR", "Validator"),
            Map.entry("SCRIPT", "ScriptValueMod"),
            Map.entry("TEXT_INPUT", "TextFileInput"),
            Map.entry("TEXT_OUTPUT", "TextFileOutput"),
            Map.entry("EXCEL_INPUT", "ExcelInput"),
            Map.entry("INSERT_UPDATE", "InsertUpdate"),
            Map.entry("DB_LOOKUP", "DBLookup"),
            Map.entry("HTTP", "Rest")
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
        m.put("TEXTFILEINPUT", "TEXT_INPUT");
        m.put("TEXTFILEOUTPUT", "TEXT_OUTPUT");
        m.put("SPLITFIELD", "SPLIT");
        m.put("SPLITFIELDS", "SPLIT");
        m.put("VALUEMAPPER", "VALUE_MAPPER");
        m.put("STRINGCUT", "STRING_CUT");
        m.put("REPLACESTRING", "REPLACE_STRING");
        m.put("NULLIF", "NULL_IF");
        m.put("IFNULL", "IF_NULL");
        m.put("SWITCHCASE", "SWITCH_CASE");
        m.put("SCRIPTVALUEMOD", "SCRIPT");
        m.put("EXCELINPUT", "EXCEL_INPUT");
        m.put("INSERTUPDATE", "INSERT_UPDATE");
        m.put("DBLOOKUP", "DB_LOOKUP");
        m.put("REST", "HTTP");
        return m;
    }

    /**
     * 校验画布输出落层：须有输出节点；表输出须配置目标表；写 ODS 须显式 allowOdsWriteback。
     * @return 错误信息，通过则 null
     */
    public String validateGraphOutputRules(String graphJson) {
        if (graphJson == null || graphJson.isBlank()) {
            return "画布为空";
        }
        try {
            JsonNode root = OM.readTree(graphJson);
            JsonNode nodes = root.get("nodes");
            if (nodes == null || !nodes.isArray() || nodes.isEmpty()) {
                return "画布无节点";
            }
            boolean hasOut = false;
            for (JsonNode n : nodes) {
                String type = n.path("data").path("nodeType").asText("");
                if ("OUTPUT".equals(type) || "INSERT_UPDATE".equals(type) || "TEXT_OUTPUT".equals(type)) {
                    hasOut = true;
                }
            }
            if (!hasOut) {
                return "请先添加输出节点（或文本输出试跑），治理结果须明确写出目标";
            }
            for (JsonNode n : nodes) {
                JsonNode data = n.path("data");
                String type = data.path("nodeType").asText("");
                String label = data.path("label").asText(n.path("id").asText("node"));
                JsonNode cfg = data.path("config");
                if ("INPUT".equals(type)) {
                    String mode = textOr(cfg, "inputMode", "TABLE");
                    if ("TABLE".equalsIgnoreCase(mode)) {
                        String table = textOr(cfg, "tableName", "");
                        if (table.isBlank() || isPlaceholderTable(table)) {
                            return "输入节点「" + label + "」指定表模式下未配置真实表名";
                        }
                        String conn = textOr(cfg, "connection", "");
                        if (conn.isBlank()) {
                            return "输入节点「" + label + "」未选择数据源";
                        }
                    } else if ("SQL".equalsIgnoreCase(mode)) {
                        String sql = textOr(cfg, "sql", "");
                        if (sql.isBlank() || isPlaceholderSql(sql)) {
                            return "输入节点「" + label + "」SQL 模式下请填写有效查询（勿使用 table_name 占位）";
                        }
                    }
                    continue;
                }
                if (!"OUTPUT".equals(type) && !"INSERT_UPDATE".equals(type)) {
                    continue;
                }
                String table = textOr(cfg, "table", textOr(cfg, "outputTable", ""));
                String conn = textOr(cfg, "connection", textOr(cfg, "outputConnection", ""));
                if (table.isBlank() || "output_table".equals(table)) {
                    return "输出节点「" + label + "」未配置目标表";
                }
                boolean allowOds = cfg.path("allowOdsWriteback").asBoolean(false);
                if ("smart_city_ods".equals(conn) && !allowOds) {
                    return "写回 ODS 须在输出节点勾选「允许回写 ODS」";
                }
            }
            return null;
        } catch (Exception e) {
            log.warn("validateGraphOutputRules failed: {}", e.getMessage());
            return "画布 JSON 解析失败";
        }
    }

    private static String textOr(JsonNode cfg, String field, String def) {
        if (cfg == null || cfg.isMissingNode() || cfg.isNull()) return def;
        JsonNode n = cfg.get(field);
        if (n == null || n.isNull()) return def;
        String v = n.asText("").trim();
        return v.isEmpty() ? def : v;
    }

    public String graphToKtr(String graphJson, String transName) {
        try {
            GraphModel graph = parseGraph(graphJson);
            String startStep = firstStepLabel(graph);
            StringBuilder xml = new StringBuilder();
            xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
            xml.append("<transformation_configuration>\n");
            xml.append("<transformation>\n");
            xml.append("  <info>\n");
            xml.append("    <name>").append(escapeXml(transName)).append("</name>\n");
            xml.append("    <description>治理任务自动生成</description>\n");
            xml.append("    <extended_description/>\n");
            xml.append("    <trans_version/>\n");
            xml.append("    <trans_type>Normal</trans_type>\n");
            xml.append("    <directory>/</directory>\n");
            xml.append("    <parameters></parameters>\n");
            xml.append("    <log>\n");
            xml.append("      <trans-log-table><connection/><schema/><table/></trans-log-table>\n");
            xml.append("      <perf-log-table><connection/><schema/><table/></perf-log-table>\n");
            xml.append("      <channel-log-table><connection/><schema/><table/></channel-log-table>\n");
            xml.append("      <step-log-table><connection/><schema/><table/></step-log-table>\n");
            xml.append("    </log>\n");
            xml.append("    <maxdate><connection/><table/><field/><offset>0.0</offset><maxdiff>0.0</maxdiff></maxdate>\n");
            xml.append("    <size_rowset>10000</size_rowset>\n");
            xml.append("    <sleep_time_empty>50</sleep_time_empty>\n");
            xml.append("    <sleep_time_full>50</sleep_time_full>\n");
            xml.append("    <unique_connections>N</unique_connections>\n");
            xml.append("    <feedback_shown>Y</feedback_shown>\n");
            xml.append("    <feedback_size>50000</feedback_size>\n");
            xml.append("    <using_thread_priorities>Y</using_thread_priorities>\n");
            xml.append("    <shared_objects_file/>\n");
            xml.append("    <capture_step_performance>N</capture_step_performance>\n");
            xml.append("    <step_performance_capturing_delay>1000</step_performance_capturing_delay>\n");
            xml.append("    <step_performance_capturing_size_limit>100</step_performance_capturing_size_limit>\n");
            xml.append("    <dependencies></dependencies>\n");
            xml.append("    <partitionschemas></partitionschemas>\n");
            xml.append("    <slaveservers></slaveservers>\n");
            xml.append("    <clusterschemas></clusterschemas>\n");
            xml.append("    <created_user>system</created_user>\n");
            xml.append("    <modified_user>system</modified_user>\n");
            if (startStep != null) {
                xml.append("    <start>").append(escapeXml(startStep)).append("</start>\n");
            }
            xml.append("  </info>\n");
            xml.append("  <notepads></notepads>\n");

            // 嵌入平台目标库连接（Carte 可达），节点可引用 PLATFORM / default / smart_city_*
            xml.append(buildPlatformConnectionsXml());

            xml.append("  <order>\n");
            for (EdgeDef edge : graph.edges) {
                NodeDef from = graph.nodes.get(edge.source);
                NodeDef to = graph.nodes.get(edge.target);
                if (from == null || to == null) continue;
                String fromName = stepNameOf(from);
                String toName = stepNameOf(to);
                xml.append("    <hop>\n");
                xml.append("      <from>").append(escapeXml(fromName)).append("</from>\n");
                xml.append("      <to>").append(escapeXml(toName)).append("</to>\n");
                xml.append("      <enabled>Y</enabled>\n");
                if (edge.edgeRole != null && !edge.edgeRole.isBlank() && !"COPY".equalsIgnoreCase(edge.edgeRole)) {
                    xml.append("      <!-- edgeRole=").append(escapeXml(edge.edgeRole));
                    if (edge.caseValue != null && !edge.caseValue.isBlank()) {
                        xml.append(" caseValue=").append(escapeXml(edge.caseValue));
                    }
                    if (edge.sourceHandle != null) {
                        xml.append(" sourceHandle=").append(escapeXml(edge.sourceHandle));
                    }
                    if (edge.targetHandle != null) {
                        xml.append(" targetHandle=").append(escapeXml(edge.targetHandle));
                    }
                    xml.append(" -->\n");
                }
                xml.append("    </hop>\n");
            }
            xml.append("  </order>\n");

            // FILTER / SWITCH：把出口目标步写回步骤配置（基于边角色）
            enrichBranchTargets(graph);

            for (NodeDef node : graph.nodes.values()) {
                xml.append(generateStepXml(node, graph));
            }

            xml.append("  <step_error_handling></step_error_handling>\n");
            xml.append("  <slave_step_copy_partition_distribution></slave_step_copy_partition_distribution>\n");
            xml.append("  <slave_transformation>N</slave_transformation>\n");
            xml.append("</transformation>\n");
            xml.append("  <transformation_execution_configuration>\n");
            xml.append("    <exec_local>Y</exec_local>\n");
            xml.append("    <exec_remote>N</exec_remote>\n");
            xml.append("    <pass_export>N</pass_export>\n");
            xml.append("    <exec_cluster>N</exec_cluster>\n");
            xml.append("    <cluster_post>Y</cluster_post>\n");
            xml.append("    <cluster_prepare>Y</cluster_prepare>\n");
            xml.append("    <cluster_start>Y</cluster_start>\n");
            xml.append("    <cluster_show_trans>N</cluster_show_trans>\n");
            xml.append("    <parameters></parameters>\n");
            xml.append("    <variables></variables>\n");
            xml.append("    <arguments></arguments>\n");
            xml.append("    <safe_mode>N</safe_mode>\n");
            xml.append("    <log_level>Basic</log_level>\n");
            xml.append("    <log_file>N</log_file>\n");
            xml.append("    <log_file_append>N</log_file_append>\n");
            xml.append("    <create_parent_folder>N</create_parent_folder>\n");
            xml.append("    <clear_log>Y</clear_log>\n");
            xml.append("    <gather_metrics>N</gather_metrics>\n");
            xml.append("    <show_subcomponents>Y</show_subcomponents>\n");
            xml.append("  </transformation_execution_configuration>\n");
            xml.append("</transformation_configuration>\n");
            return xml.toString();
        } catch (Exception e) {
            throw new RuntimeException("转换DAG到KTR失败: " + e.getMessage(), e);
        }
    }

    /** 取拓扑中入度为 0 的首个节点作为 start（与汇聚 KTR 一致） */
    private String firstStepLabel(GraphModel graph) {
        if (graph.nodes.isEmpty()) return null;
        java.util.Set<String> targets = new java.util.HashSet<>();
        for (EdgeDef e : graph.edges) {
            if (e.target != null) targets.add(e.target);
        }
        for (NodeDef n : graph.nodes.values()) {
            if (!targets.contains(n.id)) {
                return stepNameOf(n);
            }
        }
        return stepNameOf(graph.nodes.values().iterator().next());
    }

    /** 画布节点数（用于与 Carte step 数对账） */
    public int countGraphNodes(String graphJson) {
        if (graphJson == null || graphJson.isBlank()) {
            return 0;
        }
        try {
            return parseGraph(graphJson).nodes.size();
        } catch (Exception e) {
            return 0;
        }
    }

    /** KTR 中 <step> 顶层数量（粗计） */
    public int countStepsInKtr(String ktrXml) {
        if (ktrXml == null || ktrXml.isBlank()) {
            return 0;
        }
        int n = 0;
        int idx = 0;
        while (true) {
            idx = ktrXml.indexOf("<step>", idx);
            if (idx < 0) {
                break;
            }
            n++;
            idx += 6;
        }
        return n;
    }

    /** 归档 KTR 到 compose/kettle-repository（若目录存在） */
    public void archiveKtr(String transName, String ktrXml) {
        try {
            Path dir = Path.of("compose", "kettle-repository");
            if (!Files.isDirectory(dir)) {
                Files.createDirectories(dir);
            }
            Path file = dir.resolve(transName + ".ktr");
            Files.writeString(file, ktrXml, StandardCharsets.UTF_8);
            log.info("archived ktr to {}", file.toAbsolutePath());
        } catch (Exception e) {
            log.warn("archive ktr skipped: {}", e.getMessage());
        }
    }

    private String buildPlatformConnectionsXml() {
        var k = integrationProperties.getKettle();
        if (k == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        String host = k.getTargetHost() != null ? k.getTargetHost() : "host.docker.internal";
        int port = k.getTargetPort();
        String user = k.getTargetUser() != null ? k.getTargetUser() : "root";
        String pass = k.getTargetPassword() != null ? k.getTargetPassword() : "";
        // 默认连接名
        sb.append(connectionService.toConnectionXml("default", host, port,
                k.getTargetDatabase() != null ? k.getTargetDatabase() : "smart_city_ods", user, pass));
        sb.append(connectionService.toConnectionXml("PLATFORM", host, port,
                k.getTargetDatabase() != null ? k.getTargetDatabase() : "smart_city_ods", user, pass));
        for (String db : List.of("smart_city", "smart_city_ods", "smart_city_dwd", "smart_city_dws", "smart_city_ads")) {
            sb.append(connectionService.toConnectionXml(db, host, port, db, user, pass));
        }
        return sb.toString();
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
                // 导入保真：回填常见 Step 参数
                String conn = textChild(step, "connection");
                if (conn != null && !conn.isBlank()) config.put("connection", conn);
                String sql = textChild(step, "sql");
                if (sql != null && !sql.isBlank()) {
                    config.put("sql", sql);
                    config.put("inputMode", "SQL");
                }
                String table = textChild(step, "table");
                if (table != null && !table.isBlank()) {
                    config.put("table", table);
                    config.put("outputTable", table);
                    config.put("tableName", table);
                    if (!config.has("inputMode")) config.put("inputMode", "TABLE");
                }
                String splitfield = textChild(step, "splitfield");
                if (splitfield != null && !splitfield.isBlank()) config.put("sourceField", splitfield);
                String delimiter = textChild(step, "delimiter");
                if (delimiter != null && !delimiter.isBlank()) config.put("delimiter", delimiter);
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

    private String generateStepXml(NodeDef node, GraphModel graph) {
        String nodeType = node.data.nodeType;
        String stepName = stepNameOf(node);
        String kettleType = NODE_TO_KETTLE.getOrDefault(nodeType, "Dummy");
        if (("FIELD_PROCESS".equals(nodeType) || "TYPE_CONVERT".equals(nodeType))
                && hasStringCalcMappings(node.data.config)) {
            kettleType = "Calculator";
        }
        // 未配置条件的 FILTER：退化为 Dummy，避免 FilterRows 空比较导致 prepareExecution 失败
        if ("FILTER".equals(nodeType) && isPassThroughFilter(node.data.config)) {
            kettleType = "Dummy";
        }
        String label = labelOf(node);
        StringBuilder sb = new StringBuilder();
        sb.append("  <step>\n");
        sb.append("    <name>").append(escapeXml(stepName)).append("</name>\n");
        sb.append("    <type>").append(kettleType).append("</type>\n");
        sb.append("    <description>").append(escapeXml(label)).append("</description>\n");
        // FILTER/SWITCH 按条件路由；其它多出边默认复制分发
        boolean branch = "FILTER".equals(nodeType) && !"Dummy".equals(kettleType)
                || "SWITCH_CASE".equals(nodeType);
        sb.append("    <distribute>").append(branch ? "N" : "Y").append("</distribute>\n");
        sb.append("    <copies>1</copies>\n");
        if ("Dummy".equals(kettleType) && "FILTER".equals(nodeType)) {
            sb.append("    <!-- pass-through empty FILTER -->\n");
        } else {
            sb.append(generateStepConfig(node, graph));
        }
        sb.append("  </step>\n");
        return sb.toString();
    }

    private boolean hasStringCalcMappings(JsonNode cfg) {
        JsonNode mappings = cfg != null ? cfg.get("mappings") : null;
        if (mappings == null || !mappings.isArray()) return false;
        for (JsonNode m : mappings) {
            String expr = cfgText(m, "expr", "COPY");
            if (expr != null) {
                String u = expr.toUpperCase(Locale.ROOT);
                if ("UPPER".equals(u) || "LOWER".equals(u) || "TRIM".equals(u)) return true;
            }
        }
        return false;
    }

    /**
     * 根据画布节点 config 生成 Kettle Step 内部配置 XML。
     */
    private String generateStepConfig(NodeDef node, GraphModel graph) {
        String nodeType = node.data.nodeType == null ? "" : node.data.nodeType;
        JsonNode cfg = node.data.config;
        return switch (nodeType) {
            case "INPUT" -> cfgInput(cfg, orderByKeysForInput(node, graph));
            case "OUTPUT", "INSERT_UPDATE" -> cfgOutput(cfg);
            case "FILTER" -> cfgFilter(cfg);
            case "FIELD_PROCESS", "SELECT_FIELDS", "TYPE_CONVERT" -> cfgFieldProcess(cfg);
            case "DEDUPLICATE" -> cfgDeduplicate(cfg);
            case "MASK" -> cfgMask(cfg);
            case "JOIN" -> cfgJoin(cfg, predecessorStepNames(graph, node.id));
            case "UNION" -> cfgUnion(cfg);
            case "SORT" -> cfgSort(cfg);
            case "AGGREGATE" -> cfgAggregate(cfg);
            case "PIVOT" -> cfgPivot(cfg);
            case "UNPIVOT" -> cfgUnpivot(cfg);
            case "SET_VARIABLE" -> cfgSetVariable(cfg);
            case "SPLIT" -> cfgSplit(cfg);
            case "VALUE_MAPPER" -> cfgValueMapper(cfg);
            case "CONSTANT" -> cfgConstant(cfg);
            case "FORMULA" -> cfgFormula(cfg);
            case "STRING_CUT" -> cfgStringCut(cfg);
            case "REPLACE_STRING" -> cfgReplaceString(cfg);
            case "NULL_IF" -> cfgNullIf(cfg);
            case "IF_NULL" -> cfgIfNull(cfg);
            case "SWITCH_CASE" -> cfgSwitchCase(cfg);
            case "VALIDATOR" -> cfgValidator(cfg);
            case "SCRIPT" -> cfgScript(cfg);
            case "TEXT_INPUT", "EXCEL_INPUT" -> cfgFileInput(cfg);
            case "TEXT_OUTPUT" -> cfgFileOutput(cfg);
            case "DB_LOOKUP" -> cfgDbLookup(cfg);
            case "HTTP" -> cfgHttp(cfg);
            default -> "";
        };
    }

    private String resolveConn(JsonNode cfg, String key, String def) {
        String c = cfgText(cfg, key, def);
        if (c == null || c.isBlank() || "default".equalsIgnoreCase(c)) {
            return "PLATFORM";
        }
        if (c.startsWith("ds:")) {
            return "PLATFORM";
        }
        return c;
    }

    private String cfgInput(JsonNode cfg, List<String> orderByKeys) {
        String conn = resolveConn(cfg, "connection", "PLATFORM");
        String mode = cfgText(cfg, "inputMode", "TABLE");
        String table = cfgText(cfg, "tableName", "");
        boolean unlimited = cfgBool(cfg, "unlimited", false) || cfgBool(cfg, "fullTable", false);
        String sql;
        int limit;

        if ("SAMPLE".equalsIgnoreCase(mode)) {
            // 不依赖物理表的单行样例；rowCount 仅作备注（TableInput 无法凭 limit 复制行）
            sql = "SELECT 1 AS id, '张三' AS name, '13800000000' AS phone,"
                    + " '110101199001011234' AS idCard, 100.5 AS amount, 'a@b.com' AS email";
            limit = cfgInt(cfg, "rowCount", 10);
            if (limit <= 0) {
                limit = 10;
            }
        } else if ("TABLE".equalsIgnoreCase(mode)) {
            // 指定表：必须用 tableName 生成 SQL，忽略表单残留的占位 sql
            String safeTable = sanitizeIdent(table);
            if (safeTable.isBlank()) {
                safeTable = "table_name";
            }
            sql = "SELECT * FROM `" + safeTable + "`";
            if (orderByKeys != null && !orderByKeys.isEmpty()) {
                StringBuilder ob = new StringBuilder();
                for (String k : orderByKeys) {
                    String sk = sanitizeIdent(k);
                    if (sk.isBlank()) continue;
                    if (ob.length() > 0) ob.append(", ");
                    ob.append('`').append(sk).append('`');
                }
                if (ob.length() > 0) {
                    sql = sql + " ORDER BY " + ob;
                }
            }
            limit = cfgInt(cfg, "limit", 0);
            if (!unlimited && limit <= 0) {
                limit = 1000;
            }
        } else {
            // SQL 模式
            sql = cfgText(cfg, "sql", "");
            if (sql == null || sql.isBlank() || isPlaceholderSql(sql)) {
                String safeTable = sanitizeIdent(table);
                if (!safeTable.isBlank() && !isPlaceholderTable(safeTable)) {
                    sql = "SELECT * FROM `" + safeTable + "`";
                } else {
                    sql = "SELECT 1 AS id";
                }
            }
            limit = cfgInt(cfg, "limit", 0);
            if (!unlimited && limit <= 0) {
                limit = 1000;
            }
        }
        return "    <connection>" + escapeXml(conn) + "</connection>\n"
                + "    <sql>" + escapeXml(sql) + "</sql>\n"
                + "    <limit>" + limit + "</limit>\n"
                + "    <variables_active>Y</variables_active>\n"
                + "    <!-- inputMode=" + escapeXml(mode) + " -->\n";
    }

    private static boolean isPlaceholderSql(String sql) {
        if (sql == null || sql.isBlank()) {
            return true;
        }
        String s = sql.trim().toLowerCase().replaceAll("\\s+", " ");
        return s.contains("from table_name") || "select * from table_name".equals(s);
    }

    private static boolean isPlaceholderTable(String table) {
        if (table == null || table.isBlank()) {
            return true;
        }
        String t = table.trim().toLowerCase();
        return "table_name".equals(t) || "output_table".equals(t);
    }

    private static String sanitizeIdent(String raw) {
        if (raw == null) {
            return "";
        }
        String t = raw.trim();
        if (t.isEmpty()) {
            return "";
        }
        // 仅保留库表常见标识符字符，防止注入到 KTR SQL
        return t.replace("`", "").replaceAll("[^a-zA-Z0-9_]", "");
    }

    private String cfgOutput(JsonNode cfg) {
        String conn = resolveConn(cfg, "connection", "PLATFORM");
        if (cfg != null && cfg.has("outputConnection") && !cfg.get("outputConnection").asText("").isBlank()) {
            conn = resolveConn(cfg, "outputConnection", conn);
        }
        String table = cfgText(cfg, "table", null);
        if (table == null || table.isBlank()) {
            table = cfgText(cfg, "outputTable", "output_table");
        }
        int commit = cfgInt(cfg, "commit", 1000);
        if (commit <= 0) commit = cfgInt(cfg, "commitSize", 1000);
        String mode = cfgText(cfg, "outputMode", "INSERT");
        boolean truncate = "TRUNCATE_INSERT".equalsIgnoreCase(mode);
        return "    <connection>" + escapeXml(conn) + "</connection>\n"
                + "    <schema/>\n"
                + "    <table>" + escapeXml(table) + "</table>\n"
                + "    <commit>" + commit + "</commit>\n"
                + "    <truncate>" + (truncate ? "Y" : "N") + "</truncate>\n"
                + "    <ignore_errors>N</ignore_errors>\n"
                + "    <use_batch>Y</use_batch>\n";
    }

    private String cfgSplit(JsonNode cfg) {
        String field = cfgText(cfg, "sourceField", "col");
        String delimiter = cfgText(cfg, "delimiter", ",");
        List<String> targets = cfgStringList(cfg, "targetFields");
        if (targets.isEmpty()) {
            String raw = cfgText(cfg, "targetFieldsCsv", "col1,col2");
            for (String p : raw.split(",")) {
                if (!p.isBlank()) targets.add(p.trim());
            }
        }
        StringBuilder sb = new StringBuilder();
        sb.append("    <splitfield>").append(escapeXml(field)).append("</splitfield>\n");
        sb.append("    <delimiter>").append(escapeXml(delimiter)).append("</delimiter>\n");
        sb.append("    <fields>\n");
        for (String t : targets) {
            sb.append("      <field><name>").append(escapeXml(t)).append("</name></field>\n");
        }
        sb.append("    </fields>\n");
        return sb.toString();
    }

    private String cfgValueMapper(JsonNode cfg) {
        String field = cfgText(cfg, "field", "code");
        String target = cfgText(cfg, "targetField", field + "_mapped");
        return "    <field_to_use>" + escapeXml(field) + "</field_to_use>\n"
                + "    <target_field>" + escapeXml(target) + "</target_field>\n"
                + "    <non_match_default>" + escapeXml(cfgText(cfg, "defaultValue", "")) + "</non_match_default>\n"
                + "    <fields>\n"
                + "      <field><source_value>" + escapeXml(cfgText(cfg, "fromValue", "A"))
                + "</source_value><target_value>" + escapeXml(cfgText(cfg, "toValue", "甲"))
                + "</target_value></field>\n"
                + "    </fields>\n";
    }

    private String cfgConstant(JsonNode cfg) {
        String name = cfgText(cfg, "field", "const_col");
        String value = cfgText(cfg, "value", "");
        return "    <fields>\n"
                + "      <field><name>" + escapeXml(name) + "</name><type>String</type>"
                + "<nullif>" + escapeXml(value) + "</nullif></field>\n"
                + "    </fields>\n";
    }

    private String cfgFormula(JsonNode cfg) {
        String name = cfgText(cfg, "field", "calc");
        String formula = cfgText(cfg, "formula", "[a]+[b]");
        return "    <formula>\n"
                + "      <field_name>" + escapeXml(name) + "</field_name>\n"
                + "      <formula_string>" + escapeXml(formula) + "</formula_string>\n"
                + "    </formula>\n";
    }

    private String cfgStringCut(JsonNode cfg) {
        String field = cfgText(cfg, "field", "name");
        int from = cfgInt(cfg, "cutFrom", 0);
        int to = cfgInt(cfg, "cutTo", 1);
        return "    <fields>\n"
                + "      <field><in_stream_name>" + escapeXml(field) + "</in_stream_name>"
                + "<out_stream_name>" + escapeXml(cfgText(cfg, "targetField", field)) + "</out_stream_name>"
                + "<cut_from>" + from + "</cut_from><cut_to>" + to + "</cut_to></field>\n"
                + "    </fields>\n";
    }

    private String cfgReplaceString(JsonNode cfg) {
        String field = cfgText(cfg, "field", "name");
        return "    <fields>\n"
                + "      <field><in_stream_name>" + escapeXml(field) + "</in_stream_name>"
                + "<out_stream_name>" + escapeXml(cfgText(cfg, "targetField", field)) + "</out_stream_name>"
                + "<replace>" + escapeXml(cfgText(cfg, "search", "")) + "</replace>"
                + "<replace_by>" + escapeXml(cfgText(cfg, "replace", "")) + "</replace_by></field>\n"
                + "    </fields>\n";
    }

    private String cfgNullIf(JsonNode cfg) {
        return "    <fields>\n"
                + "      <field><name>" + escapeXml(cfgText(cfg, "field", "col"))
                + "</name><value>" + escapeXml(cfgText(cfg, "value", "")) + "</value></field>\n"
                + "    </fields>\n";
    }

    private String cfgIfNull(JsonNode cfg) {
        return "    <fields>\n"
                + "      <field><name>" + escapeXml(cfgText(cfg, "field", "col"))
                + "</name><value>" + escapeXml(cfgText(cfg, "replaceValue", "")) + "</value>"
                + "<replace>Y</replace></field>\n"
                + "    </fields>\n";
    }

    private String cfgSwitchCase(JsonNode cfg) {
        String field = cfgText(cfg, "field", "status");
        String defaultTarget = cfgText(cfg, "defaultTarget", "");
        StringBuilder sb = new StringBuilder();
        sb.append("    <fieldname>").append(escapeXml(field)).append("</fieldname>\n");
        sb.append("    <use_contains>N</use_contains>\n");
        sb.append("    <case_value_type>String</case_value_type>\n");
        sb.append("    <default_target_step>").append(escapeXml(defaultTarget)).append("</default_target_step>\n");
        sb.append("    <cases>\n");
        JsonNode cases = cfg != null ? cfg.get("cases") : null;
        JsonNode caseTargets = cfg != null ? cfg.get("caseTargets") : null;
        if (cases != null && cases.isArray()) {
            int i = 0;
            for (JsonNode c : cases) {
                String value = cfgText(c, "value", "");
                String target = "";
                if (caseTargets != null && caseTargets.isArray() && i < caseTargets.size()) {
                    target = cfgText(caseTargets.get(i), "target", "");
                }
                sb.append("      <case>\n");
                sb.append("        <value>").append(escapeXml(value)).append("</value>\n");
                sb.append("        <target_step>").append(escapeXml(target)).append("</target_step>\n");
                sb.append("      </case>\n");
                i++;
            }
        }
        sb.append("    </cases>\n");
        return sb.toString();
    }

    private String cfgValidator(JsonNode cfg) {
        return "    <validations>\n"
                + "      <validation><name>check</name><field_name>"
                + escapeXml(cfgText(cfg, "field", "id"))
                + "</field_name><null_allowed>N</null_allowed></validation>\n"
                + "    </validations>\n";
    }

    private String cfgScript(JsonNode cfg) {
        String script = cfgText(cfg, "script", "// var i = 0;");
        return "    <jsScripts>\n"
                + "      <jsScript><jsScript_type>0</jsScript_type>"
                + "<jsScript_name>Script 1</jsScript_name>"
                + "<jsScript_script>" + escapeXml(script) + "</jsScript_script></jsScript>\n"
                + "    </jsScripts>\n";
    }

    private String cfgFileInput(JsonNode cfg) {
        return "    <file><name>" + escapeXml(cfgText(cfg, "filePath", "/tmp/input.csv"))
                + "</name></file>\n"
                + "    <separator>" + escapeXml(cfgText(cfg, "separator", ",")) + "</separator>\n"
                + "    <header>Y</header>\n";
    }

    private String cfgFileOutput(JsonNode cfg) {
        return "    <file><name>" + escapeXml(cfgText(cfg, "filePath", "/tmp/output.csv"))
                + "</name></file>\n"
                + "    <separator>" + escapeXml(cfgText(cfg, "separator", ",")) + "</separator>\n"
                + "    <header>Y</header>\n";
    }

    private String cfgDbLookup(JsonNode cfg) {
        String conn = resolveConn(cfg, "connection", "PLATFORM");
        return "    <connection>" + escapeXml(conn) + "</connection>\n"
                + "    <lookup><schema/><table>" + escapeXml(cfgText(cfg, "table", "dim"))
                + "</table></lookup>\n"
                + "    <key><name>" + escapeXml(cfgText(cfg, "keyField", "id"))
                + "</name><field>" + escapeXml(cfgText(cfg, "lookupKey", "id"))
                + "</field></key>\n";
    }

    private String cfgHttp(JsonNode cfg) {
        return "    <url>" + escapeXml(cfgText(cfg, "url", "http://localhost/")) + "</url>\n"
                + "    <method>" + escapeXml(cfgText(cfg, "method", "GET")) + "</method>\n";
    }

    private void enrichBranchTargets(GraphModel graph) {
        for (NodeDef node : graph.nodes.values()) {
            if (node.data == null) continue;
            String type = node.data.nodeType == null ? "" : node.data.nodeType;
            if (!"FILTER".equals(type) && !"SWITCH_CASE".equals(type)) continue;
            ObjectNode cfg = node.data.config != null && node.data.config.isObject()
                    ? ((ObjectNode) node.data.config).deepCopy()
                    : OM.createObjectNode();
            List<String> untypedTargets = new ArrayList<>();
            for (EdgeDef edge : graph.edges) {
                if (!node.id.equals(edge.source)) continue;
                NodeDef target = graph.nodes.get(edge.target);
                if (target == null) continue;
                String targetStep = stepNameOf(target);
                String role = edge.edgeRole == null ? "" : edge.edgeRole.toUpperCase(Locale.ROOT);
                String sh = edge.sourceHandle == null ? "" : edge.sourceHandle;
                if ("FILTER".equals(type)) {
                    if ("TRUE".equals(role) || "out_true".equals(sh) || "true".equals(sh)) {
                        cfg.put("trueTarget", targetStep);
                    } else if ("FALSE".equals(role) || "out_false".equals(sh) || "false".equals(sh)) {
                        cfg.put("falseTarget", targetStep);
                    } else {
                        untypedTargets.add(targetStep);
                    }
                } else if ("SWITCH_CASE".equals(type)) {
                    if ("DEFAULT".equals(role) || "out_default".equals(sh)) {
                        cfg.put("defaultTarget", targetStep);
                    } else if ("CASE".equals(role) || sh.startsWith("out_case_")) {
                        ArrayNode arr = cfg.has("caseTargets") && cfg.get("caseTargets").isArray()
                                ? (ArrayNode) cfg.get("caseTargets")
                                : cfg.putArray("caseTargets");
                        ObjectNode item = arr.addObject();
                        item.put("target", targetStep);
                        if (edge.caseValue != null) item.put("value", edge.caseValue);
                    }
                }
            }
            // 单出口未标 TRUE/FALSE 时，默认走真分支（融合初始化图常见）
            if ("FILTER".equals(type) && !cfg.has("trueTarget") && untypedTargets.size() == 1) {
                cfg.put("trueTarget", untypedTargets.get(0));
            }
            node.data.config = cfg;
        }
    }

    private String cfgFilter(JsonNode cfg) {
        String trueTarget = cfgText(cfg, "trueTarget", "");
        String falseTarget = cfgText(cfg, "falseTarget", "");
        StringBuilder sb = new StringBuilder();
        sb.append("    <send_true_to>").append(escapeXml(trueTarget)).append("</send_true_to>\n");
        sb.append("    <send_false_to>").append(escapeXml(falseTarget)).append("</send_false_to>\n");
        sb.append("    <compare>\n");
        String mode = cfgText(cfg, "mode", "SIMPLE");
        if ("SQL".equalsIgnoreCase(mode)) {
            String expr = cfgText(cfg, "sqlExpr", "1=1");
            sb.append("      <condition>\n");
            sb.append("        <negated>N</negated>\n");
            sb.append("        <conditions>\n");
            appendFilterAtomicCondition(sb, expr, "=", "Y", false);
            sb.append("        </conditions>\n");
            sb.append("      </condition>\n");
        } else {
            JsonNode conditions = cfg != null ? cfg.get("conditions") : null;
            sb.append("      <condition>\n");
            sb.append("        <negated>N</negated>\n");
            String logic = cfgText(cfg, "logic", "AND");
            sb.append("        <operator>").append("OR".equalsIgnoreCase(logic) ? "OR" : "AND").append("</operator>\n");
            sb.append("        <conditions>\n");
            if (conditions != null && conditions.isArray() && conditions.size() > 0) {
                for (JsonNode c : conditions) {
                    String field = cfgText(c, "field", "id");
                    String op = mapFilterOp(cfgText(c, "op", "EQ"));
                    String value = cfgText(c, "value", "");
                    // 字段对字段：config.compareMode=FIELD 时 value 视为右字段名
                    boolean valueIsField = "FIELD".equalsIgnoreCase(cfgText(c, "compareMode", ""));
                    appendFilterAtomicCondition(sb, field, op, value, valueIsField);
                }
            } else {
                String field = cfgText(cfg, "field", "id");
                String op = mapFilterOp(cfgText(cfg, "op", "EQ"));
                String value = cfgText(cfg, "value", "");
                boolean valueIsField = "FIELD".equalsIgnoreCase(cfgText(cfg, "compareMode", ""));
                appendFilterAtomicCondition(sb, field, op, value, valueIsField);
            }
            sb.append("        </conditions>\n");
            sb.append("      </condition>\n");
        }
        sb.append("    </compare>\n");
        return sb.toString();
    }

    /**
     * Kettle FilterRows：rightvalue 只能是字段名；常量必须写在 &lt;value&gt; 节点，
     * 否则会把字面量（如「A单位」）误当成流字段导致初始化失败。
     */
    private void appendFilterAtomicCondition(StringBuilder sb, String leftField, String op,
                                             String value, boolean valueIsField) {
        sb.append("          <condition>\n");
        sb.append("            <negated>N</negated>\n");
        sb.append("            <leftvalue>").append(escapeXml(leftField)).append("</leftvalue>\n");
        sb.append("            <function>").append(escapeXml(op)).append("</function>\n");
        boolean nullCheck = "IS NULL".equalsIgnoreCase(op) || "IS NOT NULL".equalsIgnoreCase(op);
        if (nullCheck) {
            sb.append("            <rightvalue/>\n");
        } else if (valueIsField) {
            sb.append("            <rightvalue>").append(escapeXml(value)).append("</rightvalue>\n");
        } else {
            // 常量比较
            String text = value == null ? "" : value;
            if ("LIKE".equals(op) && !text.contains("%") && !text.contains("_")) {
                text = "%" + text + "%";
            }
            String type = guessFilterValueType(text);
            sb.append("            <rightvalue/>\n");
            sb.append("            <value>\n");
            sb.append("              <name>constant</name>\n");
            sb.append("              <type>").append(type).append("</type>\n");
            sb.append("              <text>").append(escapeXml(text)).append("</text>\n");
            sb.append("              <length>-1</length>\n");
            sb.append("              <precision>-1</precision>\n");
            sb.append("              <isnull>N</isnull>\n");
            sb.append("              <mask/>\n");
            sb.append("            </value>\n");
        }
        sb.append("          </condition>\n");
    }

    private static String guessFilterValueType(String text) {
        if (text == null || text.isBlank()) return "String";
        if (text.matches("^-?\\d+$")) return "Integer";
        if (text.matches("^-?\\d+(\\.\\d+)?$")) return "Number";
        return "String";
    }

    private String cfgFieldProcess(JsonNode cfg) {
        StringBuilder sb = new StringBuilder();
        // 若含 UPPER/LOWER/TRIM：用 Calculator 先变换，再由 SelectValues 语义通过 rename 落列
        JsonNode mappings = cfg != null ? cfg.get("mappings") : null;
        boolean needCalc = false;
        if (mappings != null && mappings.isArray()) {
            for (JsonNode m : mappings) {
                String expr = cfgText(m, "expr", "COPY");
                if (expr != null && !"COPY".equalsIgnoreCase(expr) && !"DROP".equalsIgnoreCase(expr)
                        && !"KEEP".equalsIgnoreCase(expr)) {
                    needCalc = true;
                    break;
                }
            }
        }
        if (needCalc) {
            sb.append("    <calculation>\n");
            if (mappings != null) {
                for (JsonNode m : mappings) {
                    String from = cfgText(m, "from", null);
                    String to = cfgText(m, "to", from);
                    String expr = cfgText(m, "expr", "COPY");
                    if (from == null || from.isBlank()) continue;
                    if ("COPY".equalsIgnoreCase(expr) || "DROP".equalsIgnoreCase(expr) || "KEEP".equalsIgnoreCase(expr)) {
                        continue;
                    }
                    String calcType = switch (expr.toUpperCase(Locale.ROOT)) {
                        case "UPPER" -> "UPPER";
                        case "LOWER" -> "LOWER";
                        case "TRIM" -> "TRIM";
                        default -> "COPY_FIELD";
                    };
                    sb.append("      <calc>\n");
                    sb.append("        <field_name>").append(escapeXml(to != null ? to : from)).append("</field_name>\n");
                    sb.append("        <calc_type>").append(calcType).append("</calc_type>\n");
                    sb.append("        <field_a>").append(escapeXml(from)).append("</field_a>\n");
                    sb.append("        <field_b/>\n");
                    sb.append("        <field_c/>\n");
                    sb.append("        <value_type>String</value_type>\n");
                    sb.append("        <value_length>-1</value_length>\n");
                    sb.append("        <value_precision>-1</value_precision>\n");
                    boolean remove = cfg == null || !cfg.path("keepSource").asBoolean(true);
                    sb.append("        <remove>").append(remove && to != null && !to.equals(from) ? "Y" : "N").append("</remove>\n");
                    sb.append("      </calc>\n");
                }
            }
            sb.append("    </calculation>\n");
            return sb.toString();
        }
        sb.append("    <fields>\n");
        boolean any = false;
        if (mappings != null && mappings.isArray()) {
            for (JsonNode m : mappings) {
                String from = cfgText(m, "from", null);
                String to = cfgText(m, "to", from);
                String expr = cfgText(m, "expr", "COPY");
                if (from == null || from.isBlank()) continue;
                if ("DROP".equalsIgnoreCase(expr)) continue;
                any = true;
                sb.append("      <field>\n");
                sb.append("        <name>").append(escapeXml(from)).append("</name>\n");
                sb.append("        <rename>").append(escapeXml(to != null ? to : from)).append("</rename>\n");
                String targetType = cfgText(m, "targetType", null);
                if (targetType != null && !targetType.isBlank()) {
                    sb.append("        <type>").append(escapeXml(targetType)).append("</type>\n");
                }
                sb.append("        <length>-1</length>\n");
                sb.append("        <precision>-1</precision>\n");
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
        // 不再默认 id：ODS 表常无 id（如 record_id/unit_id），默认 id 会导致
        // Couldn't find field [id] in row!
        String keep = cfgText(cfg, "keepStrategy", "FIRST");
        List<String> sortFields = cfgStringList(cfg, "sortFields");
        StringBuilder sb = new StringBuilder();
        sb.append("    <count_rows>N</count_rows>\n");
        sb.append("    <count_field/>\n");
        sb.append("    <reject_duplicate_row>N</reject_duplicate_row>\n");
        sb.append("    <error_description/>\n");
        sb.append("    <fields>\n");
        for (String k : keys) {
            if (k == null || k.isBlank()) continue;
            sb.append("      <field>\n");
            sb.append("        <name>").append(escapeXml(k)).append("</name>\n");
            sb.append("        <case_insensitive>N</case_insensitive>\n");
            sb.append("      </field>\n");
        }
        sb.append("    </fields>\n");
        if (keys.isEmpty()) {
            sb.append("    <!-- warn: 未配置去重键，Unique 可能无法正确去重 -->\n");
        }
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
            String one = cfgText(cfg, "field", "");
            fields = one == null || one.isBlank() ? List.of() : List.of(one);
        }
        String maskType = cfgText(cfg, "maskType", "BLUR");
        String maskChar = cfgText(cfg, "maskChar", "*");
        if (maskChar == null || maskChar.isBlank()) {
            maskChar = "*";
        }
        // 单字符掩码，避免破坏 JS 字面量
        String ch = maskChar.substring(0, 1).replace("\\", "").replace("'", "");
        if (ch.isEmpty()) {
            ch = "*";
        }
        // MD5 结果是 32 位十六进制字符串，覆盖 DATE/数值列会导致 TableOutput 报 Incorrect date/value
        boolean md5 = "MD5".equalsIgnoreCase(maskType);
        boolean newColumn = md5
                || "NEW_COLUMN".equalsIgnoreCase(cfgText(cfg, "writeMode", "OVERWRITE"));
        String suffix = cfgText(cfg, "targetSuffix", "_masked");
        if (suffix == null || suffix.isBlank()) {
            suffix = "_masked";
        }

        // ScriptValueMod：必须在 <fields> 声明 replace，脚本赋值才会写回行流
        StringBuilder js = new StringBuilder();
        js.append("// auto-generated mask (Kettle ScriptValueMod)\n");
        if (md5) {
            js.append("// MD5 always writes new column to protect typed source fields\n");
        }
        if (fields.isEmpty()) {
            js.append("// warn: no mask fields configured\n");
        }
        StringBuilder fieldXml = new StringBuilder();
        fieldXml.append("    <fields>\n");
        for (String f : fields) {
            if (f == null || f.isBlank()) {
                continue;
            }
            String src = f.trim();
            // 日期/时间类字段即使选了「覆盖」也强制写新列，避免破坏 DATE 类型落库
            boolean fieldNewCol = newColumn || looksLikeNonStringField(src);
            String target = fieldNewCol ? src + suffix : src;
            js.append("{\n");
            js.append("  var __v = ").append(src).append(";\n");
            js.append("  if (__v != null) {\n");
            js.append("    var __s = '' + __v;\n");
            if ("MD5".equalsIgnoreCase(maskType)) {
                js.append("    try {\n");
                js.append("      ").append(target)
                        .append(" = Packages.org.apache.commons.codec.digest.DigestUtils.md5Hex(__s);\n");
                js.append("    } catch (e) {\n");
                js.append("      ").append(target).append(" = __s;\n");
                js.append("    }\n");
            } else {
                // BLUR：保留首尾字符，中间替换为掩码字符
                js.append("    if (__s.length <= 2) {\n");
                js.append("      var __o = '';\n");
                js.append("      for (var __i = 0; __i < __s.length; __i++) __o += '").append(ch).append("';\n");
                js.append("      ").append(target).append(" = __o;\n");
                js.append("    } else {\n");
                js.append("      var __mid = '';\n");
                js.append("      for (var __j = 0; __j < __s.length - 2; __j++) __mid += '").append(ch).append("';\n");
                js.append("      ").append(target)
                        .append(" = __s.substring(0,1) + __mid + __s.substring(__s.length - 1);\n");
                js.append("    }\n");
            }
            js.append("  } else {\n");
            js.append("    ").append(target).append(" = __v;\n");
            js.append("  }\n");
            js.append("}\n");

            fieldXml.append("      <field>\n");
            fieldXml.append("        <name>").append(escapeXml(target)).append("</name>\n");
            fieldXml.append("        <rename/>\n");
            fieldXml.append("        <type>String</type>\n");
            fieldXml.append("        <length>-1</length>\n");
            fieldXml.append("        <precision>-1</precision>\n");
            // 覆盖原列 Y；新增列 N（否则下游拿不到脱敏结果）
            fieldXml.append("        <replace>").append(fieldNewCol ? "N" : "Y").append("</replace>\n");
            fieldXml.append("      </field>\n");
        }
        fieldXml.append("    </fields>\n");

        return "    <compatible>N</compatible>\n"
                + "    <optimizationLevel>9</optimizationLevel>\n"
                + "    <jsScripts>\n"
                + "      <jsScript><jsScript_type>0</jsScript_type>"
                + "<jsScript_name>Mask</jsScript_name>"
                + "<jsScript_script>" + escapeXml(js.toString()) + "</jsScript_script></jsScript>\n"
                + "    </jsScripts>\n"
                + fieldXml;
    }

    /** 日期/时间/数值倾向字段：脱敏结果为字符串，禁止覆盖原列 */
    private static boolean looksLikeNonStringField(String fieldName) {
        if (fieldName == null || fieldName.isBlank()) {
            return false;
        }
        String n = fieldName.trim().toLowerCase(Locale.ROOT);
        if (n.contains("idcard") || n.contains("id_card") || n.contains("phone") || n.contains("mobile")
                || n.contains("email") || n.contains("name") || n.contains("address") || n.contains("code")) {
            return false;
        }
        return n.contains("date") || n.contains("time") || n.endsWith("_at") || n.endsWith("_dt")
                || n.startsWith("amt_") || n.endsWith("_amt") || n.contains("amount")
                || n.contains("count") || n.contains("qty") || n.contains("price")
                || n.startsWith("is_") || n.startsWith("has_");
    }

    private String cfgJoin(JsonNode cfg, List<String> predecessorSteps) {
        String joinType = cfgText(cfg, "joinType", "INNER");
        String kettleJoin = switch (joinType.toUpperCase(Locale.ROOT)) {
            case "LEFT" -> "LEFT OUTER";
            case "FULL" -> "FULL OUTER";
            default -> "INNER";
        };
        List<String> leftKeys = cfgStringList(cfg, "leftKeys");
        List<String> rightKeys = cfgStringList(cfg, "rightKeys");
        if (leftKeys.isEmpty()) {
            String lk = cfgText(cfg, "leftKey", "id");
            if (lk != null && !lk.isBlank()) leftKeys.add(lk);
        }
        if (rightKeys.isEmpty()) {
            String rk = cfgText(cfg, "rightKey", leftKeys.isEmpty() ? "id" : leftKeys.get(0));
            if (rk != null && !rk.isBlank()) rightKeys.add(rk);
        }
        if (leftKeys.isEmpty()) leftKeys.add("id");
        if (rightKeys.isEmpty()) rightKeys.add(leftKeys.get(0));

        String step1 = predecessorSteps.size() > 0 ? predecessorSteps.get(0) : "";
        String step2 = predecessorSteps.size() > 1 ? predecessorSteps.get(1) : "";

        StringBuilder sb = new StringBuilder();
        sb.append("    <join_type>").append(escapeXml(kettleJoin)).append("</join_type>\n");
        sb.append("    <step1>").append(escapeXml(step1)).append("</step1>\n");
        sb.append("    <step2>").append(escapeXml(step2)).append("</step2>\n");
        sb.append("    <keys_1>\n");
        for (String k : leftKeys) {
            sb.append("      <key>").append(escapeXml(k)).append("</key>\n");
        }
        sb.append("    </keys_1>\n");
        sb.append("    <keys_2>\n");
        for (String k : rightKeys) {
            sb.append("      <key>").append(escapeXml(k)).append("</key>\n");
        }
        sb.append("    </keys_2>\n");
        return sb.toString();
    }

    /** MergeJoin 两路前驱：LEFT 优先于 RIGHT，否则按边顺序 */
    private List<String> predecessorStepNames(GraphModel graph, String nodeId) {
        List<EdgeDef> ins = new ArrayList<>();
        for (EdgeDef e : graph.edges) {
            if (nodeId.equals(e.target)) {
                ins.add(e);
            }
        }
        ins.sort((a, b) -> Integer.compare(joinSideRank(a), joinSideRank(b)));
        List<String> names = new ArrayList<>();
        for (EdgeDef e : ins) {
            NodeDef from = graph.nodes.get(e.source);
            if (from != null) {
                names.add(stepNameOf(from));
            }
        }
        return names;
    }

    private static int joinSideRank(EdgeDef e) {
        String role = e.edgeRole == null ? "" : e.edgeRole.toUpperCase(Locale.ROOT);
        String th = e.targetHandle == null ? "" : e.targetHandle;
        if ("LEFT".equals(role) || "in_left".equals(th) || "left".equals(th)) return 0;
        if ("RIGHT".equals(role) || "in_right".equals(th) || "right".equals(th)) return 1;
        return 2;
    }

    /**
     * 输入步若直接连到 JOIN：按本侧关联键 ORDER BY，满足 MergeJoin 有序流要求。
     */
    private List<String> orderByKeysForInput(NodeDef inputNode, GraphModel graph) {
        for (EdgeDef e : graph.edges) {
            if (!inputNode.id.equals(e.source)) continue;
            NodeDef to = graph.nodes.get(e.target);
            if (to == null || to.data == null || !"JOIN".equals(to.data.nodeType)) continue;
            JsonNode joinCfg = to.data.config;
            int side = joinSideRank(e);
            List<String> keys;
            if (side == 1) {
                keys = cfgStringList(joinCfg, "rightKeys");
                if (keys.isEmpty()) {
                    String rk = cfgText(joinCfg, "rightKey", null);
                    if (rk != null) keys.add(rk);
                }
            } else {
                keys = cfgStringList(joinCfg, "leftKeys");
                if (keys.isEmpty()) {
                    String lk = cfgText(joinCfg, "leftKey", "id");
                    if (lk != null) keys.add(lk);
                }
            }
            if (keys.isEmpty()) keys.add("id");
            return keys;
        }
        return List.of();
    }

    /** 无有效过滤条件：视为直通，不生成 FilterRows */
    private static boolean isPassThroughFilter(JsonNode cfg) {
        if (cfg == null || cfg.isNull() || cfg.isMissingNode()) return true;
        String mode = cfgText(cfg, "mode", "SIMPLE");
        if ("SQL".equalsIgnoreCase(mode)) {
            String expr = cfgText(cfg, "sqlExpr", "");
            return expr == null || expr.isBlank();
        }
        JsonNode conditions = cfg.get("conditions");
        if (conditions != null && conditions.isArray() && conditions.size() > 0) {
            for (JsonNode c : conditions) {
                String field = cfgText(c, "field", "");
                if (field != null && !field.isBlank()) return false;
            }
            return true;
        }
        String field = cfg.has("field") && !cfg.get("field").isNull() ? cfg.get("field").asText("") : "";
        return field == null || field.isBlank();
    }

    private String cfgUnion(JsonNode cfg) {
        // UnionAll in PDI 常为「追加流」步骤；这里写占位元数据
        return "    <pick_copy>0</pick_copy>\n"
                + "    <comments>" + escapeXml(cfgText(cfg, "comment", "union all inputs")) + "</comments>\n";
    }

    private String cfgSort(JsonNode cfg) {
        JsonNode sortKeys = cfg != null ? cfg.get("sortKeys") : null;
        StringBuilder sb = new StringBuilder();
        sb.append("    <directory>%%java.io.tmpdir%%</directory>\n");
        sb.append("    <prefix>out</prefix>\n");
        sb.append("    <sort_size>1000000</sort_size>\n");
        sb.append("    <free_memory>25</free_memory>\n");
        sb.append("    <compress>N</compress>\n");
        sb.append("    <compress_variable/>\n");
        sb.append("    <unique_rows>N</unique_rows>\n");
        sb.append("    <fields>\n");
        if (sortKeys != null && sortKeys.isArray() && sortKeys.size() > 0) {
            for (JsonNode s : sortKeys) {
                String field = cfgText(s, "field", null);
                if (field == null || field.isBlank()) continue;
                boolean asc = !"DESC".equalsIgnoreCase(cfgText(s, "order", "ASC"));
                sb.append("      <field>\n");
                sb.append("        <name>").append(escapeXml(field)).append("</name>\n");
                sb.append("        <ascending>").append(asc ? "Y" : "N").append("</ascending>\n");
                sb.append("        <case_sensitive>N</case_sensitive>\n");
                sb.append("        <collator_enabled>N</collator_enabled>\n");
                sb.append("        <collator_strength>0</collator_strength>\n");
                sb.append("        <presorted>N</presorted>\n");
                sb.append("      </field>\n");
            }
        } else {
            String field = cfgText(cfg, "field", "id");
            String order = cfgText(cfg, "order", "ASC");
            boolean asc = !"DESC".equalsIgnoreCase(order);
            sb.append("      <field>\n");
            sb.append("        <name>").append(escapeXml(field)).append("</name>\n");
            sb.append("        <ascending>").append(asc ? "Y" : "N").append("</ascending>\n");
            sb.append("        <case_sensitive>N</case_sensitive>\n");
            sb.append("        <collator_enabled>N</collator_enabled>\n");
            sb.append("        <collator_strength>0</collator_strength>\n");
            sb.append("        <presorted>N</presorted>\n");
            sb.append("      </field>\n");
        }
        sb.append("    </fields>\n");
        return sb.toString();
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

    private static boolean cfgBool(JsonNode cfg, String field, boolean def) {
        if (cfg == null || !cfg.has(field) || cfg.get(field).isNull()) return def;
        JsonNode n = cfg.get(field);
        if (n.isBoolean()) return n.asBoolean(def);
        String s = n.asText("");
        if ("true".equalsIgnoreCase(s) || "1".equals(s) || "Y".equalsIgnoreCase(s)) return true;
        if ("false".equalsIgnoreCase(s) || "0".equals(s) || "N".equalsIgnoreCase(s)) return false;
        return def;
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

    /** 展示用中文标签（仅写 description，不作为 Kettle step name） */
    private static String labelOf(NodeDef node) {
        return node.data.label != null && !node.data.label.isBlank() ? node.data.label : node.id;
    }

    /**
     * Kettle hop/step 名称：优先 ASCII 的 nodeId，避免中文在 registerTrans 编码损坏后撞名丢步。
     */
    private static String stepNameOf(NodeDef node) {
        String id = node.id == null ? "" : node.id.trim();
        if (!id.isBlank() && isSafeStepName(id)) {
            return id;
        }
        String base = !id.isBlank() ? id : labelOf(node);
        StringBuilder sb = new StringBuilder("s_");
        for (int i = 0; i < base.length(); i++) {
            char c = base.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_' || c == '-') {
                sb.append(c);
            } else if (c > 127) {
                sb.append(String.format("%04x", (int) c));
            } else {
                sb.append('_');
            }
        }
        return sb.length() > 2 ? sb.toString() : "s_step";
    }

    private static boolean isSafeStepName(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c > 127) return false;
            if (!(Character.isLetterOrDigit(c) || c == '_' || c == '-' || c == '.')) return false;
        }
        return true;
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
                edge.sourceHandle = textOrNull(e, "sourceHandle");
                edge.targetHandle = textOrNull(e, "targetHandle");
                JsonNode data = e.get("data");
                if (data != null && data.isObject()) {
                    edge.edgeRole = textOrNull(data, "edgeRole");
                    edge.caseValue = textOrNull(data, "caseValue");
                }
                if (edge.edgeRole == null || edge.edgeRole.isBlank()) {
                    edge.edgeRole = inferEdgeRole(edge.sourceHandle, edge.targetHandle);
                }
                graph.edges.add(edge);
            }
        }
        return graph;
    }

    private static String textOrNull(JsonNode n, String field) {
        if (n == null || !n.has(field) || n.get(field).isNull()) return null;
        String s = n.get(field).asText();
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static String inferEdgeRole(String sourceHandle, String targetHandle) {
        String sh = sourceHandle == null ? "" : sourceHandle;
        String th = targetHandle == null ? "" : targetHandle;
        if ("out_true".equals(sh) || "true".equals(sh)) return "TRUE";
        if ("out_false".equals(sh) || "false".equals(sh)) return "FALSE";
        if ("out_default".equals(sh) || "default".equals(sh)) return "DEFAULT";
        if (sh.startsWith("out_case_") || sh.startsWith("case_")) return "CASE";
        if ("in_left".equals(th) || "left".equals(th)) return "LEFT";
        if ("in_right".equals(th) || "right".equals(th)) return "RIGHT";
        return "COPY";
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
        String sourceHandle;
        String targetHandle;
        String edgeRole;
        String caseValue;
    }
}
