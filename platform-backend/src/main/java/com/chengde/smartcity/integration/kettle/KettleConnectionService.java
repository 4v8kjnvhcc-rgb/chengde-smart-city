package com.chengde.smartcity.integration.kettle;

import com.chengde.smartcity.integration.config.IntegrationProperties;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 将平台数据源连接信息转换为 Kettle connection 片段（供后续写入 ktr 使用）。
 */
@Service
public class KettleConnectionService {

    private static final Logger log = LoggerFactory.getLogger(KettleConnectionService.class);

    private final IntegrationProperties props;

    public KettleConnectionService(IntegrationProperties props) {
        this.props = props;
    }

    public Map<String, Object> connectionMeta(String name, String host, int port,
                                              String database, String username) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", name);
        m.put("server", host);
        m.put("type", "MYSQL");
        m.put("access", "Native");
        m.put("database", database);
        m.put("port", String.valueOf(port));
        m.put("username", username);
        m.put("kettleUrl", props.getKettle() != null ? props.getKettle().getUrl() : "");
        return m;
    }

    public String toConnectionXml(String name, String host, int port,
                                  String database, String username, String password) {
        log.debug("build kettle connection xml name={}", name);
        return "<connection>\n"
                + "  <name>" + escape(name) + "</name>\n"
                + "  <server>" + escape(host) + "</server>\n"
                + "  <type>MYSQL</type>\n"
                + "  <access>Native</access>\n"
                + "  <database>" + escape(database) + "</database>\n"
                + "  <port>" + port + "</port>\n"
                + "  <username>" + escape(username) + "</username>\n"
                + "  <password>" + escape(password == null ? "" : password) + "</password>\n"
                + "</connection>\n";
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
