package com.leo.ai.article.agent.aop;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.leo.ai.article.agent.annotation.AgentExecution;
import com.leo.ai.article.agent.model.dto.article.ArticleState;
import com.leo.ai.article.agent.model.entity.AgentLog;
import com.leo.ai.article.agent.service.AgentLogService;
import com.leo.ai.article.agent.utils.GsonUtils;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * @program: ai-article-agent
 * @description:
 * @author: Miao Zheng
 * @date: 2026-03-30 22:50
 **/
@Slf4j
@Aspect
@Component
public class AgentExecutionAspect {

    @Resource
    private AgentLogService agentExecutionService;

    @Around("@annotation(agentExecution)")
    public Object doInterceptor(ProceedingJoinPoint pjp, AgentExecution agentExecution) throws Throwable {
        log.info("==================== AOP 切面生效了！=====================");

        long startTime = System.currentTimeMillis();
        LocalDateTime startDateTime = LocalDateTime.now();

        String taskId = extractTaskId(pjp);
        String inputData = extractInputData(pjp);
        String prompt = extractPrompt(pjp);
        String agentName = agentExecution.value();

        AgentLog agentLog = AgentLog.builder()
                .taskId(taskId)
                .agentName(agentName)
                .startTime(startDateTime)
                .status("RUNNING")
                .prompt(prompt)
                .inputData(inputData)
                .build();

        Object res = null;

        try {
            res = pjp.proceed();
            agentLog.setStatus("SUCCESS");
            agentLog.setEndTime(LocalDateTime.now());
            agentLog.setDurationMs((int) (System.currentTimeMillis() - startTime));
            agentLog.setOutputData(extractOutputData(res));
            log.info("智能体执行成功: {}, taskId = {}, 耗时 = {}ms", agentName, taskId, agentLog.getDurationMs());
        } catch (Throwable throwable) {
            agentLog.setStatus("FAILED");
            agentLog.setEndTime(LocalDateTime.now());
            agentLog.setDurationMs((int) (System.currentTimeMillis() - startTime));
            agentLog.setOutputData(throwable.getMessage() != null ? throwable.getMessage() : throwable.getClass().getName());
            log.error("智能体执行失败: {}, taskId = {}, 耗时 = {}ms", agentName, taskId, agentLog.getDurationMs(), throwable);
            throw throwable;
        } finally {
            agentExecutionService.saveLogAsync(agentLog);
        }
        return res;
    }

    /**
     * 从方法参数中提取 taskId
     */
    private String extractTaskId(ProceedingJoinPoint pjp) {
        Object[] args = pjp.getArgs();
        if (args == null || args.length == 0) {
            return "unknown";
        }

        // 优先从 ArticleState 中获取
        for (Object arg : args) {
            if (arg instanceof ArticleState) {
                return ((ArticleState) arg).getTaskId();
            }
            // 新增：支持 OverAllState
            if (arg instanceof OverAllState) {
                OverAllState state = (OverAllState) arg;

                return state.value("taskId")
                        .map(Object::toString)
                        .orElse("unknown");
            }
        }

        // 尝试从第一个 String 参数获取（可能是 taskId）
        for (Object arg : args) {
            if (arg instanceof String) {
                return (String) arg;
            }
        }



        return "unknown";
    }

    /**
     * 提取输入数据（简化版，只记录关键信息）
     */
    private String extractInputData(ProceedingJoinPoint pjp) {
        try {
            Object[] args = pjp.getArgs();
            if (args == null || args.length == 0) {
                return null;
            }

            Map<String, Object> inputMap = new HashMap<>();
            MethodSignature signature = (MethodSignature) pjp.getSignature();
            String[] paramNames = signature.getParameterNames();

            for (int i = 0; i < args.length && i < paramNames.length; i++) {
                Object arg = args[i];
                // 只记录基本类型和简单对象，避免数据过大
                if (arg instanceof String || arg instanceof Number || arg instanceof Boolean) {
                    inputMap.put(paramNames[i], arg);
                } else if (arg instanceof ArticleState) {
                    ArticleState state = (ArticleState) arg;
                    inputMap.put("taskId", state.getTaskId());
                    if (state.getTitle() != null) {
                        inputMap.put("mainTitle", state.getTitle().getMainTitle());
                    }
                }
            }

            return inputMap.isEmpty() ? null : GsonUtils.toJson(inputMap);
        } catch (Exception e) {
            log.warn("提取输入数据失败", e);
            return null;
        }
    }

    /**
     * 提取输出数据（简化版）
     */
    private String extractOutputData(Object result) {
        try {
            if (result == null) {
                return null;
            }

            // 只记录简单类型，避免数据过大
            if (result instanceof String || result instanceof Number || result instanceof Boolean) {
                return String.valueOf(result);
            }

            // 对于集合类型，只记录数量
            if (result instanceof java.util.List) {
                return "{\"listSize\": " + ((java.util.List<?>) result).size() + "}";
            }

            return "{\"type\": \"" + result.getClass().getSimpleName() + "\"}";
        } catch (Exception e) {
            log.warn("提取输出数据失败", e);
            return null;
        }
    }

    /**
     * 提取使用的 Prompt（尝试从方法参数或 ArticleState 获取）
     */
    private String extractPrompt(ProceedingJoinPoint pjp) {
        try {
            // 可以根据方法名称推断使用的 Prompt
            // 或从参数中提取，这里简化处理
            MethodSignature signature = (MethodSignature) pjp.getSignature();
            Method method = signature.getMethod();
            return method.getDeclaringClass().getSimpleName() + "." + method.getName();
        } catch (Exception e) {
            return null;
        }
    }


}