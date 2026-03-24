package com.leo.ai.article.agent.service.impl;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.google.gson.reflect.TypeToken;
import com.leo.ai.article.agent.constant.PromptConstant;
import com.leo.ai.article.agent.model.dto.article.ArticleState;
import com.leo.ai.article.agent.model.enums.ImageMethodEnum;
import com.leo.ai.article.agent.model.enums.SseMessageTypeEnum;
import com.leo.ai.article.agent.service.CosService;
import com.leo.ai.article.agent.service.ImageSearchService;
import com.leo.ai.article.agent.utils.GsonUtils;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ArticleAgentService {

    @Resource
    private DashScopeChatModel chatModel;

    @Resource
    private ImageSearchService imageSearchService;

    @Resource
    private CosService cosService;

    /**
     * 执行完整的文章生成流程
     *
     * @param state         文章状态
     * @param streamHandler 流式输出处理器
     */
    public void executeArticleGeneration(ArticleState state, Consumer<String> streamHandler) {
        try {
            // 智能体1：生成标题
            log.info("智能体1：开始生成标题, taskId={}", state.getTaskId());
            agent1GenerateTitle(state);
            streamHandler.accept(SseMessageTypeEnum.AGENT1_COMPLETE.getValue());

            // 智能体2：生成大纲（流式输出）
            log.info("智能体2：开始生成大纲, taskId={}", state.getTaskId());
            agent2GenerateOutline(state, streamHandler);
            streamHandler.accept(SseMessageTypeEnum.AGENT2_COMPLETE.getValue());

            // 智能体3：生成正文（流式输出）
            log.info("智能体3：开始生成正文, taskId={}", state.getTaskId());
            agent3GenerateContent(state, streamHandler);
            streamHandler.accept(SseMessageTypeEnum.AGENT3_COMPLETE.getValue());

            // 智能体4：分析配图需求
            log.info("智能体4：开始分析配图需求, taskId={}", state.getTaskId());
            agent4AnalyzeImageRequirements(state);
            streamHandler.accept(SseMessageTypeEnum.AGENT4_COMPLETE.getValue());

            // 智能体5：生成配图
            log.info("智能体5：开始生成配图, taskId={}", state.getTaskId());
            agent5GenerateImages(state, streamHandler);
            streamHandler.accept(SseMessageTypeEnum.AGENT5_COMPLETE.getValue());

            // 图文合成：将配图插入正文
            log.info("开始图文合成, taskId={}", state.getTaskId());
            mergeImagesIntoContent(state);
            streamHandler.accept(SseMessageTypeEnum.MERGE_COMPLETE.getValue());

            log.info("文章生成完成, taskId={}", state.getTaskId());
        } catch (Exception e) {
            log.error("文章生成失败, taskId={}", state.getTaskId(), e);
            throw new RuntimeException("文章生成失败: " + e.getMessage(), e);
        }
    }

    /**
     * 智能体1 - 生成 标题
     *
     * @param state
     */
    private void agent1GenerateTitle(ArticleState state) {
        String prompt = PromptConstant.AGENT1_TITLE_PROMPT.replace("{topic}", state.getTopic());

        String content = callLlm(prompt);
        ArticleState.TitleResult titleResult = parseJsonResponse(content, ArticleState.TitleResult.class, "标题");
        state.setTitle(titleResult);
        log.info("智能体1：生成标题完成, taskId={}, titleResult={}", state.getTaskId(), titleResult);

    }

    /**
     * 智能体2 - 生成 大纲
     *
     * @param state
     * @param streamHandler
     */
    private void agent2GenerateOutline(ArticleState state, Consumer<String> streamHandler) {
        String prompt = PromptConstant.AGENT2_OUTLINE_PROMPT
                .replace("{mainTitle}", state.getTitle().getMainTitle())
                .replace("{subTitle}", state.getTitle().getSubTitle());

        String content = callLlmWithStreaming(prompt, streamHandler, SseMessageTypeEnum.AGENT2_STREAMING);
        ArticleState.OutlineResult outlineResult = parseJsonResponse(content, ArticleState.OutlineResult.class, "大纲");
        state.setOutline(outlineResult);
        log.info("智能体2：生成大纲完成, taskId={}, outlineResult={}", state.getTaskId(), outlineResult);
    }


    /**
     * 智能体3 - 生成 正文
     *
     * @param state
     * @param streamHandler
     */
    private void agent3GenerateContent(ArticleState state, Consumer<String> streamHandler) {
        String outlineText = GsonUtils.toJson(state.getOutline().getSections());
        String prompt = PromptConstant.AGENT3_CONTENT_PROMPT
                .replace("{mainTitle}", state.getTitle().getMainTitle())
                .replace("{subTitle}", state.getTitle().getSubTitle())
                .replace("{outline}", outlineText);

        String content = callLlmWithStreaming(prompt, streamHandler, SseMessageTypeEnum.AGENT3_STREAMING);
        state.setContent(content);
        log.info("智能体3：生成正文完成, taskId={}, content={}", state.getTaskId(), content);
    }


    /**
     * 智能体4 - 分析图片需求
     *
     * @param state
     */
    private void agent4AnalyzeImageRequirements(ArticleState state) {
        String prompt = PromptConstant.AGENT4_IMAGE_REQUIREMENTS_PROMPT
                .replace("{mainTitle}", state.getTitle().getMainTitle())
                .replace("{content}", state.getContent());

        String content = callLlm(prompt);
        List<ArticleState.ImageRequirement> imageRequirementsResult
                = parseJsonListResponse(content,
                new TypeToken<List<ArticleState.ImageRequirement>>() {
                },
                "配图需求");
        state.setImageRequirements(imageRequirementsResult);
        log.info("智能体4：分析配图需求完成, taskId={}, imageRequirementsResult={}", state.getTaskId(), imageRequirementsResult);
    }

    /**
     * 智能体5 - 生成图片 - 串行执行
     *
     * @param state
     * @param streamHandler
     */
    private void agent5GenerateImages(ArticleState state, Consumer<String> streamHandler) {
        List<ArticleState.ImageResult> imageResults = new ArrayList<>();
        for (ArticleState.ImageRequirement requirement : state.getImageRequirements()) {
            log.info("智能体5：开始生成, position={}, keywords={}",
                    requirement.getPosition(), requirement.getKeywords());
            String imageUrl = imageSearchService.searchImage(requirement.getKeywords());
            ImageMethodEnum method = imageSearchService.getMethod();
            //降级策略
            if (imageUrl == null) {
                imageUrl = imageSearchService.getFallbackImage(requirement.getPosition());
                method = ImageMethodEnum.PICSUM;
                log.warn("智能体5：图库检索失败, position={}, keywords={}, 使用降级方案, imageUrl={}, method={}",
                        requirement.getPosition(), requirement.getKeywords(), imageUrl, method);
            }

            String finalImageUrl = cosService.useDirectUrl(imageUrl);

            //创建配图结果
            ArticleState.ImageResult imageResult = buildImageResult(requirement, finalImageUrl, method);
            imageResults.add(imageResult);
            //推送 单张配图完成信息
            String imageCompleteMessage = SseMessageTypeEnum.IMAGE_COMPLETE.getStreamingPrefix() + GsonUtils.toJson(imageResult);
            streamHandler.accept(imageCompleteMessage);

            log.info("智能体5：生成完成, position={}, keywords={}, imageUrl={}, method={}",
                    requirement.getPosition(), requirement.getKeywords(), imageUrl, method);
        }
        state.setImages(imageResults);
        log.info("智能体5：生成配图完成, taskId={}, imageResults={}", state.getTaskId(), imageResults);
    }

    /**
     * 图文合成 - 将配图插入正文对应位置
     * @param state
     */
    private void mergeImagesIntoContent(ArticleState state) {
        List<ArticleState.ImageResult> images = state.getImages();
        String content = state.getContent();
        if (images == null || images.isEmpty()) {
            state.setFullContent(content);
            return;
        }
        StringBuilder fullContent = new StringBuilder();
        String[] lines = content.split("\n");
        for (String line : lines) {
            fullContent.append(line).append("\n");
            if (line.startsWith("## ")) {
                String sectionTitle = line.substring(3).trim();
                insertImageAfterSection(fullContent, images, sectionTitle);
            }
        }
        state.setFullContent(fullContent.toString());
        log.info("合并图片完成, taskId={}, fullContent={}", state.getTaskId(), fullContent);
    }

    /**
     * 调用大模型 - 非流式
     *
     * @param prompt
     * @return
     */
    private String callLlm(String prompt) {
        ChatResponse res = chatModel.call(new Prompt(new UserMessage(prompt)));
        return res.getResult().getOutput().getText();
    }

    /**
     * 调用大模型 - 流式输出
     *
     * @param prompt
     * @param streamHandler
     * @param sseMessageTypeEnum
     * @return
     */
    private String callLlmWithStreaming(String prompt, Consumer<String> streamHandler, SseMessageTypeEnum sseMessageTypeEnum) {
        StringBuilder contentBuilder = new StringBuilder();
        Flux<ChatResponse> stream = chatModel.stream(new Prompt(new UserMessage(prompt)));
        stream.doOnNext(res -> {
                    String chunk = res.getResult().getOutput().getText();
                    if (chunk != null && !chunk.isEmpty()) {
                        contentBuilder.append(chunk);
                        streamHandler.accept(sseMessageTypeEnum.getStreamingPrefix() + chunk);
                    }
                })
                .doOnError(e -> log.error("流式调用大模型失败, prompt={}", prompt, e))
                .blockLast();
        return contentBuilder.toString();
    }

    /**
     * 解析 JSON 相应
     *
     * @param content
     * @param clazz
     * @param name
     * @param <T>
     * @return
     */
    private <T> T parseJsonResponse(String content, Class<T> clazz, String name) {
        try {
            return GsonUtils.fromJson(content, clazz);
        } catch (Exception e) {
            log.error("解析{}失败, content={}", name, content, e);
            throw new RuntimeException(name + "解析失败");
        }
    }

    /**
     * 解析JSON响应列表
     *
     * @param content
     * @param typeToken
     * @param name
     * @param <T>
     * @return
     */
    private <T> T parseJsonListResponse(String content, TypeToken<T> typeToken, String name) {
        try {
            return GsonUtils.fromJson(content, typeToken);
        } catch (Exception e) {
            log.error("解析{}失败, content={}", name, content, e);
            throw new RuntimeException(name + "解析失败");
        }
    }


    /**
     * 构造图片搜索结果
     *
     * @param requirement
     * @param finalImageUrl
     * @param method
     * @return
     */
    private ArticleState.ImageResult buildImageResult(ArticleState.ImageRequirement requirement, String finalImageUrl, ImageMethodEnum method) {
        ArticleState.ImageResult imageResult = new ArticleState.ImageResult();
        imageResult.setPosition(requirement.getPosition());
        imageResult.setKeywords(requirement.getKeywords());
        imageResult.setUrl(finalImageUrl);
        imageResult.setMethod(method.getValue());
        imageResult.setSectionTitle(requirement.getSectionTitle());
        imageResult.setDescription(requirement.getType());
        return imageResult;
    }


    /**
     * 图片插入到正文中
     *
     * @param fullContent
     * @param images
     * @param sectionTitle
     */
    private void insertImageAfterSection(StringBuilder fullContent, List<ArticleState.ImageResult> images, String sectionTitle) {
        for (ArticleState.ImageResult image : images) {
            if (image.getPosition() > 1 &&
                    image.getSectionTitle() != null &&
                    sectionTitle.contains(image.getSectionTitle().trim())) {
                fullContent.append("\n![").append(image.getDescription())
                        .append("](").append(image.getUrl()).append(")\n");
                break;
            }
        }

    }


}