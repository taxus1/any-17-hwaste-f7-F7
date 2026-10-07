package com.somepro.domain.hwaste.repository;

import com.somepro.domain.hwaste.model.CategoryDisableResult;
import com.somepro.domain.hwaste.model.CategoryImpact;
import com.somepro.domain.hwaste.model.WasteCategory;
import com.somepro.domain.shared.model.PageResult;
import reactor.core.publisher.Mono;

/**
 * 危废类别仓储端口：领域层定义，基础设施层实现。
 *
 * 供名录管理（录入 / 修改 / 查看 / 停用 / 翻页）与停用前影响面评估；
 * 入库登记、开联单、立计划时的类别启用校验也走 {@link #findByCode}。
 * 停用与新开（入库 / 联单 / 计划）的并发自洽由基础设施层的类别锁兜底：
 * 要么单子赶在停用前开出去，要么因为已停用被挡回，不会出现名录已停用却挂着停用后新开的单子。
 */
public interface WasteCategoryRepository {

    /** 按类别代码查危废类别；不存在返回空。 */
    Mono<WasteCategory> findByCode(String categoryCode);

    /** 按 id 查危废类别；不存在返回空。 */
    Mono<WasteCategory> findById(Long id);

    /**
     * 名录登记：类别代码全局唯一，重复代码一律挡回。
     * 「查重 + 插入」在类别锁内完成，几乎同时递两份相同代码只成一份。
     */
    Mono<WasteCategory> create(WasteCategory category);

    /** 改名录（名称 / 危险特性 / 跨省标志；代码不动）。 */
    Mono<WasteCategory> update(WasteCategory category);

    /** 翻名录：危险特性 / 状态随意挑，一个都不填分页列全，每条都带类别代码。 */
    Mono<PageResult<WasteCategory>> page(int pageNum, int pageSize, String hazardType, String status);

    /** 停用前影响面：在库批次数与重量、在办联单张数、待批计划份数。 */
    Mono<CategoryImpact> impact(String categoryCode);

    /**
     * 停用：类别锁内先复核影响面，再把状态由 ENABLED 条件置 DISABLED。
     * 非强制（force=false）时一旦有在库批次或在办联单，原样挡回并带上影响面，
     * 让调用方把牵动的单子给人说明白；force=true 表示知情后照停 ——
     * 先前在办的单子不受影响照常走，只是以后不再放新单子进来。
     */
    Mono<CategoryDisableResult> disable(String categoryCode, boolean force);
}
