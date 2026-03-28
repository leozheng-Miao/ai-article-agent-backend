package com.leo.ai.article.agent.mapper;

import com.leo.ai.article.agent.model.entity.PaymentRecord;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 支付记录 Mapper
 *
 * @author <a href="https://codefather.cn">编程导航学习圈</a>
 */
@Mapper
public interface PaymentRecordMapper extends BaseMapper<PaymentRecord> {
}