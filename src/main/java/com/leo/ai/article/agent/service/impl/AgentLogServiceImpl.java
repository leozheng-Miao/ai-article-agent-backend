package com.leo.ai.article.agent.service.impl;

import com.leo.ai.article.agent.model.vo.AgentExecutionStats;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.leo.ai.article.agent.model.entity.AgentLog;
import com.leo.ai.article.agent.mapper.AgentLogMapper;
import com.leo.ai.article.agent.service.AgentLogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 智能体执行日志表 服务层实现。
 *
 * @author zhengsmacbook
 * @since 2026-03-30
 */
@Slf4j
@Service
public class AgentLogServiceImpl extends ServiceImpl<AgentLogMapper, AgentLog>  implements AgentLogService{

    @Override
    @Async
    public void saveLogAsync(AgentLog agentLog) {
        try {
            this.save(agentLog);
            log.info("智能体执行日志保存成功: taskId = {}, agentName = {}, status = {}, durationMs = {}", agentLog, agentLog.getAgentName(), agentLog.getStatus(), agentLog.getDurationMs());
        } catch (Exception e) {
            log.error("智能体执行日志保存失败: taskId = {}, agentName = {}", agentLog, agentLog.getAgentName(), e);
        }
    }

    @Override
    public List<AgentLog> getLogsByTaskId(String taskId) {
        QueryWrapper queryWrapper = QueryWrapper.create()
                .eq("taskId", taskId)
                .orderBy("createTime", true);
        return this.list(queryWrapper);
    }

    @Override
    public AgentExecutionStats getExecutionStats(String taskId) {
        List<AgentLog> logs = getLogsByTaskId(taskId);
        if (logs == null || logs.isEmpty()) {
            return AgentExecutionStats.builder()
                    .taskId(taskId)
                    .agentCount(0)
                    .totalDurationMs(0)
                    .overallStatus("NOT_FOUND")
                    .build();
        }

        int totalDuration = 0;
        Map<String, Integer> agentDurations = new HashMap<>();
        String overallStatus = "SUCCESS";
        for (AgentLog log : logs) {
            if (log.getDurationMs() != null)  {
                totalDuration += log.getDurationMs();
                agentDurations.put(log.getAgentName(), log.getDurationMs());
            }

            if ("FAILED".equals(log.getStatus())) {
                overallStatus = "FAILED";
            } else if ("RUNNING".equals(log.getStatus()) && ! "FAILED".equals(overallStatus)) {
                overallStatus = "RUNNING";
            }
        }

        return AgentExecutionStats.builder()
                .taskId(taskId)
                .agentCount(logs.size())
                .totalDurationMs(totalDuration)
                .agentDurations(agentDurations)
                .overallStatus(overallStatus)
                .logs(logs)
                .build();
    }
}