package com.somepro.domain.hwaste.model;

/**
 * 停用结果（不可变领域值对象）：停用后的类别 + 停用那一刻锁内复核出来的影响面。
 * 影响面与停用状态在同一临界区里产出，前端拿到的账和库里落的状态对得上。
 */
public record CategoryDisableResult(WasteCategory category, CategoryImpact impact) {
}
