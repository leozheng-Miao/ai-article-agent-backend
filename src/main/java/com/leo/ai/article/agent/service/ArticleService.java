package com.leo.ai.article.agent.service;

import com.leo.ai.article.agent.model.dto.article.ArticleQueryRequest;
import com.leo.ai.article.agent.model.dto.article.ArticleState;
import com.leo.ai.article.agent.model.entity.User;
import com.leo.ai.article.agent.model.enums.ArticleStatusEnum;
import com.leo.ai.article.agent.model.vo.ArticleVO;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.service.IService;
import com.leo.ai.article.agent.model.entity.Article;

import java.util.List;

/**
 * 文章表 服务层。
 *
 * @author zhengsmacbook
 * @since 2026-03-23
 */
public interface ArticleService extends IService<Article> {

    String createArticleTask(String topic, String style, List<String> enabledImageMethods, User loginUser);

    /**
     * 创建文章任务（带配额检查）
     * 将配额扣减和任务创建放在同一事务中，确保原子性
     *
     * @param topic     选题
     * @param style     文章风格（可为空）
     * @param enabledImageMethods 允许的配图方式列表（可为空）
     * @param loginUser 当前登录用户
     * @return 任务ID
     */
    String createArticleTaskWithQuotaCheck(String topic, String style, List<String> enabledImageMethods, User loginUser);


    Article getByTaskId(String taskId);

    ArticleVO getArticleDetail(String taskId, User loginUser);

    void updateArticleStatus(String taskId, ArticleStatusEnum status, String errorMessage);

    void saveArticleContent(String taskId, ArticleState state);

    Page<ArticleVO> listArticleByPage(ArticleQueryRequest request, User loginUser);

    boolean deleteArticle(Long id, User loginUser);
}