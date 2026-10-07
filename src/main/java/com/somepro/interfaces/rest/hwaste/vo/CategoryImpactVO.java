package com.somepro.interfaces.rest.hwaste.vo;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 停用影响面对外返回对象（VO，用户接口层）—— 不可变 record。
 * 停用前先看这笔账：在库批次数 / 重量、在办联单张数、待批计划份数。
 */
public record CategoryImpactVO(
        long inStockBatchCount,
        BigDecimal inStockWeightKg,
        long openManifestCount,
        long pendingPlanCount,
        boolean blocking) implements Serializable {
}
