package com.chengde.smartcity.exchange.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chengde.smartcity.exchange.entity.IngArchiveJob;
import com.chengde.smartcity.exchange.entity.IngBackupJob;
import com.chengde.smartcity.exchange.mapper.IngArchiveJobMapper;
import com.chengde.smartcity.exchange.mapper.IngBackupJobMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class IngestAssetGovernService {

    private final IngBackupJobMapper backupMapper;
    private final IngArchiveJobMapper archiveMapper;

    public IngestAssetGovernService(IngBackupJobMapper backupMapper, IngArchiveJobMapper archiveMapper) {
        this.backupMapper = backupMapper;
        this.archiveMapper = archiveMapper;
    }

    public List<Map<String, Object>> listBackupJobs() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (IngBackupJob j : backupMapper.selectList(new LambdaQueryWrapper<IngBackupJob>().orderByAsc(IngBackupJob::getId))) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", j.getId());
            m.put("jobCode", j.getJobCode());
            m.put("scheduleCron", j.getScheduleCron());
            m.put("backupPath", j.getBackupPath());
            m.put("lastRestorePoint", j.getLastRestorePoint());
            m.put("status", j.getStatus());
            out.add(m);
        }
        return out;
    }

    public List<Map<String, Object>> listArchiveJobs() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (IngArchiveJob j : archiveMapper.selectList(new LambdaQueryWrapper<IngArchiveJob>().orderByAsc(IngArchiveJob::getId))) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", j.getId());
            m.put("jobCode", j.getJobCode());
            m.put("archivePath", j.getArchivePath());
            m.put("retentionDays", j.getRetentionDays());
            m.put("status", j.getStatus());
            out.add(m);
        }
        return out;
    }
}
