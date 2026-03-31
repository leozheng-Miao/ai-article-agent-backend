package com.leo.ai.article.agent.service;

import com.leo.ai.article.agent.model.vo.AgentExecutionStats;
import com.mybatisflex.core.service.IService;
import com.leo.ai.article.agent.model.entity.AgentLog;

import java.util.List;

/**
 * 智能体执行日志表 服务层。
 *
 * @author zhengsmacbook
 * @since 2026-03-30
 */
public interface AgentLogService extends IService<AgentLog> {

    /**
     * 异步保存日志
     *
     * @param agentLog 日志
     */
    void saveLogAsync(AgentLog agentLog);

    /**
     * 根据任务ID获取日志列表
     *
     * @param taskId 任务ID
     * @return 日志列表
     */
    List<AgentLog> getLogsByTaskId(String taskId);

    /**
     * 获取任务执行统计信息
     * @param taskId
     * @return
     */
    AgentExecutionStats getExecutionStats(String taskId);

}