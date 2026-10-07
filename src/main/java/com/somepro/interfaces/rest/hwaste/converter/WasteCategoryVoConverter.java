package com.somepro.interfaces.rest.hwaste.converter;

import com.somepro.domain.hwaste.model.CategoryDisableResult;
import com.somepro.domain.hwaste.model.CategoryImpact;
import com.somepro.domain.hwaste.model.WasteCategory;
import com.somepro.domain.shared.model.PageResult;
import com.somepro.interfaces.rest.hwaste.vo.CategoryDisableResultVO;
import com.somepro.interfaces.rest.hwaste.vo.CategoryImpactVO;
import com.somepro.interfaces.rest.hwaste.vo.PageVO;
import com.somepro.interfaces.rest.hwaste.vo.WasteCategoryVO;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 危废类别领域对象 → 对外 VO 转换器（用户接口层）。
 */
public final class WasteCategoryVoConverter {

    private WasteCategoryVoConverter() {
    }

    public static WasteCategoryVO toVo(WasteCategory domain) {
        return new WasteCategoryVO(
                domain.getId(),
                domain.getCategoryCode(),
                domain.getName(),
                domain.getHazardType() == null ? null : domain.getHazardType().name(),
                domain.getCrossProvince(),
                domain.getStatus() == null ? null : domain.getStatus().name(),
                domain.getCreateTime());
    }

    public static CategoryImpactVO toImpactVo(CategoryImpact impact) {
        return new CategoryImpactVO(
                impact.inStockBatchCount(),
                impact.inStockWeightKg(),
                impact.openManifestCount(),
                impact.pendingPlanCount(),
                impact.hasBlockingImpact());
    }

    public static CategoryDisableResultVO toDisableResultVo(CategoryDisableResult result) {
        return new CategoryDisableResultVO(toVo(result.category()), toImpactVo(result.impact()));
    }

    public static PageVO<WasteCategoryVO> toPageVo(PageResult<WasteCategory> page) {
        List<WasteCategoryVO> content = page.content().stream()
                .map(WasteCategoryVoConverter::toVo)
                .collect(Collectors.toList());
        return new PageVO<>(content, page.total(), page.pageNum(), page.pageSize(), page.totalPages());
    }
}
