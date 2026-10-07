package com.somepro.interfaces.rest.hwaste.vo;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 危废类别名录对外返回对象（VO，用户接口层）—— 不可变 record。
 * 列表与详情共用；delFlag / createBy / updateBy / updateTime 不进 API 契约；每条都回类别代码。
 */
public record WasteCategoryVO(
        Long id,
        String categoryCode,
        String name,
        String hazardType,
        Integer crossProvince,
        String status,
        LocalDateTime createTime) implements Serializable {
}
