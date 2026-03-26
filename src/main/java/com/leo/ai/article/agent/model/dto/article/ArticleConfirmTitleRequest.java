package com.leo.ai.article.agent.model.dto.article;

import lombok.Data;

import java.io.Serializable;

@Data
public class ArticleConfirmTitleRequest implements Serializable {

    /**
     * 任务ID
     */
    private String taskId;

    /**
     * 选中的主标题
     */
    private String selectedMainTitle;

    /**
     * 选中的副标题
     */
    private String selectedSubTitle;

    /**
     * 用户补充描述（可选）
     */
    private String userDescription;

    private static final long serialVersionUID = 1L;
}