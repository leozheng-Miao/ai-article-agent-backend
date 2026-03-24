package com.leo.ai.article.agent.service;

import com.leo.ai.article.agent.model.dto.article.ArticleQueryRequest;
import com.leo.ai.article.agent.model.dto.article.ArticleState;
import com.leo.ai.article.agent.model.entity.User;
import com.leo.ai.article.agent.model.enums.ArticleStatusEnum;
import com.leo.ai.article.agent.model.vo.ArticleVO;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.service.IService;
import com.leo.ai.article.agent.model.entity.Article;

/**
 * 文章表 服务层。
 *
 * @author zhengsmacbook
 * @since 2026-03-23
 */
public interface ArticleService extends IService<Article> {

    String createArticleTask(String topic, User loginUser);

    Article getByTaskId(String taskId);

    ArticleVO getArticleDetail(String taskId, User loginUser);

    void updateArticleStatus(String taskId, ArticleStatusEnum status, String errorMessage);

    void saveArticleContent(String taskId, ArticleState state);

    Page<ArticleVO> listArticleByPage(ArticleQueryRequest request, User loginUser);

    boolean deleteArticle(Long id, User loginUser);
}