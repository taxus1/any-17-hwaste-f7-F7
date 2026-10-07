package com.somepro.domain.hwaste.model;

import java.math.BigDecimal;

/**
 * 类别停用影响面（不可变领域值对象）—— 停用前先算一笔账：
 *
 * @param inStockBatchCount 当前还压在库里的批次数（IN_STOCK 批次，不分管在哪家产废单位）
 * @param inStockWeightKg   在库批次重量合计（千克）
 * @param openManifestCount 还没走完的联单张数（已提交 / 已审批 / 在途 / 已签收都算在办，
 *                          货走完了的 DISPOSED、被退回 REJECTED、作废 VOID 不算）
 * @param pendingPlanCount  还没批下来的计划份数（草稿 DRAFT / 已申报 SUBMITTED；
 *                          已批复 / 已驳回的不算待批）
 */
public record CategoryImpact(long inStockBatchCount,
                             BigDecimal inStockWeightKg,
                             long openManifestCount,
                             long pendingPlanCount) {

    /**
     * 有没有硬牵动：在库批次或在办联单任一不为 0 就算。
     * 有待批计划只提示不硬挡 —— 停用后这几份计划继续走审批，只是底下开不出新联单。
     */
    public boolean hasBlockingImpact() {
        return inStockBatchCount > 0 || openManifestCount > 0;
    }
}
