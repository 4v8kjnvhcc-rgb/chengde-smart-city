package com.chengde.smartcity.masterdata.support;

import com.chengde.smartcity.common.exception.BusinessException;
import com.chengde.smartcity.exchange.entity.IngDataSource;
import com.chengde.smartcity.exchange.mapper.IngDataSourceMapper;
import com.chengde.smartcity.integration.jdbc.CredentialCipher;
import com.chengde.smartcity.integration.jdbc.JdbcProbeService;
import com.chengde.smartcity.masterdata.entity.GovMetaDataSource;
import com.chengde.smartcity.masterdata.mapper.GovMetaDataSourceMapper;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * 解析任务连接键：平台分层库名 / meta:{id} / ds:{id} → JDBC 端点。
 */
@Component
public class TaskConnectionResolver {

    private final LayerJdbcSupport layerJdbc;
    private final GovMetaDataSourceMapper metaDataSourceMapper;
    private final IngDataSourceMapper ingDataSourceMapper;
    private final JdbcProbeService jdbcProbeService;
    private final CredentialCipher credentialCipher;

    public TaskConnectionResolver(LayerJdbcSupport layerJdbc,
                                  GovMetaDataSourceMapper metaDataSourceMapper,
                                  IngDataSourceMapper ingDataSourceMapper,
                                  JdbcProbeService jdbcProbeService,
                                  CredentialCipher credentialCipher) {
        this.layerJdbc = layerJdbc;
        this.metaDataSourceMapper = metaDataSourceMapper;
        this.ingDataSourceMapper = ingDataSourceMapper;
        this.jdbcProbeService = jdbcProbeService;
        this.credentialCipher = credentialCipher;
    }

    public LayerJdbcSupport.ResolvedEndpoint resolve(String connectionKey) {
        if (connectionKey == null || connectionKey.isBlank()) {
            return layerJdbc.resolve(DataLayerSupport.ODS);
        }
        String key = connectionKey.trim();
        if (key.toLowerCase(Locale.ROOT).startsWith("meta:")) {
            return resolveMeta(Long.parseLong(key.substring(5).trim()));
        }
        if (key.toLowerCase(Locale.ROOT).startsWith("ds:")) {
            return resolveIng(Long.parseLong(key.substring(3).trim()));
        }
        return layerJdbc.resolve(key);
    }

    /** 展示用物理库名 */
    public String physicalDatabase(String connectionKey) {
        return resolve(connectionKey).database();
    }

    private LayerJdbcSupport.ResolvedEndpoint resolveMeta(Long id) {
        GovMetaDataSource row = metaDataSourceMapper.selectById(id);
        if (row == null) {
            throw new BusinessException(404, "元数据数据源不存在: " + id);
        }
        if (row.getIngSourceId() != null) {
            try {
                return resolveIng(row.getIngSourceId());
            } catch (BusinessException ignored) {
                // 回落直连接字段
            }
        }
        if (row.getDbHost() == null || row.getDbHost().isBlank()
                || row.getDbName() == null || row.getDbName().isBlank()) {
            if (row.getDbName() != null && !row.getDbName().isBlank()) {
                try {
                    return layerJdbc.resolve(row.getDbName());
                } catch (Exception ignored) {
                    // fall through
                }
            }
            throw new BusinessException(400, "数据源「" + row.getSourceName() + "」未配置完整 JDBC 信息");
        }
        String pwd = row.getPasswordCipher() == null ? "" : credentialCipher.decrypt(row.getPasswordCipher());
        return new LayerJdbcSupport.ResolvedEndpoint(
                row.getDbHost(),
                row.getDbPort() == null || row.getDbPort() <= 0 ? 3306 : row.getDbPort(),
                row.getDbName(),
                row.getUsername() == null ? "" : row.getUsername(),
                pwd == null ? "" : pwd);
    }

    private LayerJdbcSupport.ResolvedEndpoint resolveIng(Long id) {
        IngDataSource ds = ingDataSourceMapper.selectById(id);
        if (ds == null) {
            throw new BusinessException(404, "登记数据源不存在: " + id);
        }
        JdbcProbeService.ConnConfig cfg = jdbcProbeService.parse(ds.getSourceType(), ds.getConnConfigJson());
        return new LayerJdbcSupport.ResolvedEndpoint(
                cfg.host, cfg.port, cfg.database, cfg.username, cfg.password == null ? "" : cfg.password);
    }

    public boolean isPlatformLayer(String connectionKey) {
        if (connectionKey == null) {
            return false;
        }
        String k = connectionKey.trim().toLowerCase(Locale.ROOT);
        return DataLayerSupport.ODS.equals(k)
                || DataLayerSupport.DWD.equals(k)
                || DataLayerSupport.DWS.equals(k)
                || DataLayerSupport.ADS.equals(k);
    }
}
