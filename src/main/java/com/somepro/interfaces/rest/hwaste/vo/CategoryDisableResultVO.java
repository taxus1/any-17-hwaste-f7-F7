package com.somepro.interfaces.rest.hwaste.vo;

import java.io.Serializable;

/**
 * 停用结果对外返回对象（VO，用户接口层）—— 不可变 record。
 * 停用后的类别与停用那一刻锁内复核的影响面一并带回，账和状态对得上。
 */
public record CategoryDisableResultVO(
        WasteCategoryVO category,
        CategoryImpactVO impact) implements Serializable {
}
