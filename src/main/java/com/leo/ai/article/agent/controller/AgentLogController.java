package com.leo.ai.article.agent.controller;

import com.leo.ai.article.agent.common.BaseResponse;
import com.leo.ai.article.agent.common.ResultUtils;
import com.leo.ai.article.agent.exception.ErrorCode;
import com.leo.ai.article.agent.exception.ThrowUtils;
import com.leo.ai.article.agent.model.vo.AgentExecutionStats;
import com.mybatisflex.core.paginate.Page;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.beans.factory.annotation.Autowired;
import com.leo.ai.article.agent.model.entity.AgentLog;
import com.leo.ai.article.agent.service.AgentLogService;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

/**
 * 智能体执行日志表 控制层。
 *
 * @author zhengsmacbook
 * @since 2026-03-30
 */
@RestController
@RequestMapping("/agentLog")
public class AgentLogController {

    @Resource
    private AgentLogService agentLogService;

    @GetMapping("execution-logs/{taskId}")
    @Operation(summary = "根据任务ID获取智能体执行日志")
    public BaseResponse<AgentExecutionStats> getExecutionLogs(@PathVariable String taskId) {
        ThrowUtils.throwIf(taskId == null || taskId.isEmpty(), ErrorCode.PARAMS_ERROR, "任务ID不能为空");
        AgentExecutionStats executionStats = agentLogService.getExecutionStats(taskId);
        return ResultUtils.success(executionStats);
    }

}