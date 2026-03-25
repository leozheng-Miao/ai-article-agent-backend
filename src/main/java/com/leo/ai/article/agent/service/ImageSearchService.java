package com.leo.ai.article.agent.service;


import com.leo.ai.article.agent.model.dto.image.ImageData;
import com.leo.ai.article.agent.model.dto.image.ImageRequest;
import com.leo.ai.article.agent.model.enums.ImageMethodEnum;

/**
 * 图片检索服务接口
 * 抽象图片检索逻辑，便于扩展多种图片来源（如 Pexels、Unsplash、AI 生图等）
 *
 * @author <a href="https://codefather.cn">编程导航学习圈</a>
 */
public interface ImageSearchService {

    /**
     *  根据请求获取图片
     * @param request 图片请求对象， 包含 keywords、 prompt 等参数
     * @return 图片 url， 获取失败返回 null
     */
    default String getImage(ImageRequest request) {
        String param = request.getEffectiveParam(getMethod().isAiGenerated());
        return searchImage(param);
    }

    /**
     * 获取图片数据 - 用于统一上传到 COS
     * 子类可重写此方法返回更高效的数据格式
     * @param request 图片请求对象
     * @return ImageData 对象，包含图片字节或 URL
     */
    default ImageData getImageData(ImageRequest request) {
        String url = getImage(request);
        return ImageData.fromUrl(url);
    }

    /**
     * 根据关键词检索图片
     *
     * @param keywords 搜索关键词
     * @return 图片 URL，检索失败返回 null
     */
    String searchImage(String keywords);

    /**
     * 获取图片检索方式
     *
     * @return 图片检索方式枚举
     */
    ImageMethodEnum getMethod();

    /**
     * 获取降级图片 URL
     *
     * @param position 位置序号（用于生成唯一的随机图片）
     * @return 降级图片 URL
     */
    String getFallbackImage(int position);

    /**
     * 判断服务是否可用
     * 子类可重写此方法进行健康检查
     * @return 服务是否可用
     */
    default boolean isAvailable() {
        return true;
    }
}