package com.chengde.smartcity.integration.kettle;

import com.chengde.smartcity.integration.config.IntegrationProperties;
import org.springframework.stereotype.Service;

/**
 * 生成真实可执行的 Kettle 转换（.ktr XML）：Table Input(源) -> Table Output(目标 ODS/DWS)。
 * 内嵌 SRC/TGT 两个 MySQL 连接；源库 host:port 依据 host-map 翻译为 Carte 容器可达地址，
 * 目标库使用平台配置的 Carte 可达地址（host-gateway）。绝不写入明文占位，密码来自解密后的真实凭据。
 */
@Service
public class KettleKtrCompiler {

    private final IntegrationProperties props;

    public KettleKtrCompiler(IntegrationProperties props) {
        this.props = props;
    }

    /** 源连接参数（真实值，密码已解密）。 */
    public static class SourceConn {
        public String host;
        public int port;
        public String database;
        public String username;
        public String password;
    }

    /**
     * 编译一个「源 SELECT -> 目标表 TableOutput」的转换。
     * @param transName 转换名（Carte 内唯一）
     * @param src 源连接（backend 视角 host/port，将按 host-map 翻译到 Carte 视角）
     * @param selectSql 源查询 SQL，列别名须与目标表列名一致
     * @param targetTable 目标物理表（平台库）
     * @param truncate 写入前是否 TRUNCATE
     */
    public String compileCopy(String transName, SourceConn src, String selectSql,
                              String targetTable, boolean truncate) {
        return compileCopy(transName, src, selectSql, props.getKettle().getTargetDatabase(),
                targetTable, truncate);
    }

    /**
     * @param targetDatabase 目标分层库（如 smart_city_ods / smart_city_dws）
     */
    public String compileCopy(String transName, SourceConn src, String selectSql,
                              String targetDatabase, String targetTable, boolean truncate) {
        String[] srcHostPort = translate(src.host, src.port);
        String tgtDb = targetDatabase == null || targetDatabase.isBlank()
                ? props.getKettle().getTargetDatabase() : targetDatabase;
        String srcConn = connectionXml("SRC", srcHostPort[0], srcHostPort[1], src.database, src.username, src.password);
        String tgtConn = connectionXml("TGT", props.getKettle().getTargetHost(),
                String.valueOf(props.getKettle().getTargetPort()), tgtDb,
                props.getKettle().getTargetUser(), props.getKettle().getTargetPassword());

        String input = tableInputStep("src_input", "SRC", selectSql);
        String output = tableOutputStep("tgt_output", "TGT", targetTable, truncate);

        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<transformation_configuration>\n"
                + "<transformation>\n"
                + "  <info>\n"
                + "    <name>" + esc(transName) + "</name>\n"
                + "    <description>chengde real collect/fusion</description>\n"
                + "    <extended_description/>\n"
                + "    <trans_version/>\n"
                + "    <trans_type>Normal</trans_type>\n"
                + "    <directory>/</directory>\n"
                + "    <parameters></parameters>\n"
                + "    <log>\n"
                + "      <trans-log-table><connection/><schema/><table/></trans-log-table>\n"
                + "      <perf-log-table><connection/><schema/><table/></perf-log-table>\n"
                + "      <channel-log-table><connection/><schema/><table/></channel-log-table>\n"
                + "      <step-log-table><connection/><schema/><table/></step-log-table>\n"
                + "    </log>\n"
                + "    <maxdate><connection/><table/><field/><offset>0.0</offset><maxdiff>0.0</maxdiff></maxdate>\n"
                + "    <size_rowset>10000</size_rowset>\n"
                + "    <sleep_time_empty>50</sleep_time_empty>\n"
                + "    <sleep_time_full>50</sleep_time_full>\n"
                + "    <unique_connections>N</unique_connections>\n"
                + "    <feedback_shown>Y</feedback_shown>\n"
                + "    <feedback_size>50000</feedback_size>\n"
                + "    <using_thread_priorities>Y</using_thread_priorities>\n"
                + "    <shared_objects_file/>\n"
                + "    <capture_step_performance>N</capture_step_performance>\n"
                + "    <step_performance_capturing_delay>1000</step_performance_capturing_delay>\n"
                + "    <step_performance_capturing_size_limit>100</step_performance_capturing_size_limit>\n"
                + "    <dependencies></dependencies>\n"
                + "    <partitionschemas></partitionschemas>\n"
                + "    <slaveservers></slaveservers>\n"
                + "    <clusterschemas></clusterschemas>\n"
                + "    <created_user>-</created_user>\n"
                + "    <modified_user>-</modified_user>\n"
                + "    <start>src_input</start>\n"
                + "  </info>\n"
                + "  <notepads></notepads>\n"
                + srcConn
                + tgtConn
                + "  <order>\n"
                + "    <hop><from>src_input</from><to>tgt_output</to><enabled>Y</enabled></hop>\n"
                + "  </order>\n"
                + input
                + output
                + "  <step_error_handling></step_error_handling>\n"
                + "  <slave_step_copy_partition_distribution></slave_step_copy_partition_distribution>\n"
                + "  <slave_transformation>N</slave_transformation>\n"
                + "</transformation>\n"
                + "  <transformation_execution_configuration>\n"
                + "    <exec_local>Y</exec_local>\n"
                + "    <exec_remote>N</exec_remote>\n"
                + "    <pass_export>N</pass_export>\n"
                + "    <exec_cluster>N</exec_cluster>\n"
                + "    <cluster_post>Y</cluster_post>\n"
                + "    <cluster_prepare>Y</cluster_prepare>\n"
                + "    <cluster_start>Y</cluster_start>\n"
                + "    <cluster_show_trans>N</cluster_show_trans>\n"
                + "    <parameters></parameters>\n"
                + "    <variables></variables>\n"
                + "    <arguments></arguments>\n"
                + "    <safe_mode>N</safe_mode>\n"
                + "    <log_level>Basic</log_level>\n"
                + "    <log_file>N</log_file>\n"
                + "    <log_file_append>N</log_file_append>\n"
                + "    <create_parent_folder>N</create_parent_folder>\n"
                + "    <clear_log>Y</clear_log>\n"
                + "    <gather_metrics>N</gather_metrics>\n"
                + "    <show_subcomponents>Y</show_subcomponents>\n"
                + "  </transformation_execution_configuration>\n"
                + "</transformation_configuration>\n";
    }

    private String[] translate(String host, int port) {
        String key = host + ":" + port;
        String mapped = props.getKettle().getHostMap().get(key);
        boolean loopback = "localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host);
        if (mapped == null && port == 3308 && loopback) {
            // compose source-mysql 默认映射；避免 host-map YAML 未绑定时 Carte 连 localhost:3308 失败
            mapped = "source-mysql:3306";
        }
        if (mapped == null && port == 3306 && loopback) {
            // 宿主机 MySQL：容器内 localhost 指向 Carte 自身，须走 host-gateway
            mapped = "host.docker.internal:3306";
        }
        if (mapped == null) {
            return new String[]{host, String.valueOf(port)};
        }
        int idx = mapped.lastIndexOf(':');
        if (idx <= 0) {
            return new String[]{mapped, String.valueOf(port)};
        }
        return new String[]{mapped.substring(0, idx), mapped.substring(idx + 1)};
    }

    private String connectionXml(String name, String host, String port, String database,
                                 String username, String password) {
        return "  <connection>\n"
                + "    <name>" + esc(name) + "</name>\n"
                + "    <server>" + esc(host) + "</server>\n"
                + "    <type>MYSQL</type>\n"
                + "    <access>Native</access>\n"
                + "    <database>" + esc(database) + "</database>\n"
                + "    <port>" + esc(port) + "</port>\n"
                + "    <username>" + esc(username) + "</username>\n"
                + "    <password>" + esc(password == null ? "" : password) + "</password>\n"
                + "    <servername/>\n"
                + "    <data_tablespace/>\n"
                + "    <index_tablespace/>\n"
                + "    <attributes>\n"
                + "      <attribute><code>EXTRA_OPTION_MYSQL.useSSL</code><attribute>false</attribute></attribute>\n"
                + "      <attribute><code>EXTRA_OPTION_MYSQL.allowPublicKeyRetrieval</code><attribute>true</attribute></attribute>\n"
                + "      <attribute><code>EXTRA_OPTION_MYSQL.characterEncoding</code><attribute>utf8</attribute></attribute>\n"
                + "    </attributes>\n"
                + "  </connection>\n";
    }

    private String tableInputStep(String name, String conn, String sql) {
        return "  <step>\n"
                + "    <name>" + esc(name) + "</name>\n"
                + "    <type>TableInput</type>\n"
                + "    <description/>\n"
                + "    <distribute>Y</distribute>\n"
                + "    <custom_distribution/>\n"
                + "    <copies>1</copies>\n"
                + "    <partitioning><method>none</method><schema_name/></partitioning>\n"
                + "    <connection>" + esc(conn) + "</connection>\n"
                + "    <sql>" + esc(sql) + "</sql>\n"
                + "    <limit>0</limit>\n"
                + "    <lookup/>\n"
                + "    <execute_each_row>N</execute_each_row>\n"
                + "    <variables_active>N</variables_active>\n"
                + "    <lazy_conversion_active>N</lazy_conversion_active>\n"
                + "    <cluster_schema/>\n"
                + "    <remotesteps><input> </input><output> </output></remotesteps>\n"
                + "    <GUI><xloc>128</xloc><yloc>128</yloc><draw>Y</draw></GUI>\n"
                + "  </step>\n";
    }

    private String tableOutputStep(String name, String conn, String table, boolean truncate) {
        return "  <step>\n"
                + "    <name>" + esc(name) + "</name>\n"
                + "    <type>TableOutput</type>\n"
                + "    <description/>\n"
                + "    <distribute>Y</distribute>\n"
                + "    <custom_distribution/>\n"
                + "    <copies>1</copies>\n"
                + "    <partitioning><method>none</method><schema_name/></partitioning>\n"
                + "    <connection>" + esc(conn) + "</connection>\n"
                + "    <schema/>\n"
                + "    <table>" + esc(table) + "</table>\n"
                + "    <commit>500</commit>\n"
                + "    <truncate>" + (truncate ? "Y" : "N") + "</truncate>\n"
                + "    <ignore_errors>N</ignore_errors>\n"
                + "    <use_batch>Y</use_batch>\n"
                + "    <specify_fields>N</specify_fields>\n"
                + "    <partitioning_enabled>N</partitioning_enabled>\n"
                + "    <partitioning_daily>N</partitioning_daily>\n"
                + "    <partitioning_monthly>N</partitioning_monthly>\n"
                + "    <tablename_in_field>N</tablename_in_field>\n"
                + "    <tablename_in_table>Y</tablename_in_table>\n"
                + "    <return_keys>N</return_keys>\n"
                + "    <return_field/>\n"
                + "    <cluster_schema/>\n"
                + "    <remotesteps><input> </input><output> </output></remotesteps>\n"
                + "    <GUI><xloc>384</xloc><yloc>128</yloc><draw>Y</draw></GUI>\n"
                + "  </step>\n";
    }

    private String esc(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
