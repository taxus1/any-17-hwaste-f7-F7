package com.somepro.infrastructure.persistence.hwaste;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.github.pagehelper.PageHelper;
import com.somepro.common.exception.BizException;
import com.somepro.domain.hwaste.model.CategoryDisableResult;
import com.somepro.domain.hwaste.model.CategoryImpact;
import com.somepro.domain.hwaste.model.CategoryStatus;
import com.somepro.domain.hwaste.model.WasteCategory;
import com.somepro.domain.hwaste.repository.WasteCategoryRepository;
import com.somepro.domain.shared.model.PageResult;
import com.somepro.infrastructure.persistence.base.BaseBlockingRepository;
import com.somepro.infrastructure.persistence.hwaste.converter.WasteCategoryPoConverter;
import com.somepro.infrastructure.persistence.hwaste.po.WasteCategoryPO;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 危废类别名录仓储适配器（基础设施层）。
 *
 * 录入 / 停用都走 {@link CategoryConcurrencyGuard} 的按类别锁：
 * - 录入的「查重 + 插入」在锁内，几乎同时递两份相同代码只成一份，uk_category_code 唯一约束最后兜底；
 * - 停用的「影响面复核 + 条件置 DISABLED」也在锁内；入库（WB 锁）、开联单（EM 锁）、立计划（TP 锁）
 *   在各自业务锁内再进同一把类别锁复核启用状态，于是停用与新开严格排成先后，
 *   要么单子按停用前开出去、要么因已停用被挡回，账不会两头对不上。
 */
@Repository
public class WasteCategoryRepositoryImpl extends BaseBlockingRepository implements WasteCategoryRepository {

    private final WasteCategoryMapper wasteCategoryMapper;
    private final WasteStockMapper wasteStockMapper;
    private final TransferManifestMapper transferManifestMapper;
    private final TransferPlanMapper transferPlanMapper;
    private final CategoryConcurrencyGuard categoryGuard;

    public WasteCategoryRepositoryImpl(WasteCategoryMapper wasteCategoryMapper,
                                       WasteStockMapper wasteStockMapper,
                                       TransferManifestMapper transferManifestMapper,
                                       TransferPlanMapper transferPlanMapper,
                                       CategoryConcurrencyGuard categoryGuard) {
        this.wasteCategoryMapper = wasteCategoryMapper;
        this.wasteStockMapper = wasteStockMapper;
        this.transferManifestMapper = transferManifestMapper;
        this.transferPlanMapper = transferPlanMapper;
        this.categoryGuard = categoryGuard;
    }

    @Override
    public Mono<WasteCategory> findByCode(String categoryCode) {
        return blocking(() -> {
            WasteCategoryPO po = wasteCategoryMapper.selectOne(Wrappers.<WasteCategoryPO>lambdaQuery()
                    .eq(WasteCategoryPO::getCategoryCode, categoryCode));
            return po == null ? null : WasteCategoryPoConverter.toDomain(po);
        });
    }

    @Override
    public Mono<WasteCategory> findById(Long id) {
        return blocking(() -> {
            WasteCategoryPO po = wasteCategoryMapper.selectById(id);
            return po == null ? null : WasteCategoryPoConverter.toDomain(po);
        });
    }

    @Override
    public Mono<WasteCategory> create(WasteCategory category) {
        return blocking(() -> categoryGuard.inCategoryLock(category.getCategoryCode(), () -> {
            Long dup = wasteCategoryMapper.selectCount(Wrappers.<WasteCategoryPO>lambdaQuery()
                    .eq(WasteCategoryPO::getCategoryCode, category.getCategoryCode()));
            if (dup != null && dup > 0) {
                throw new BizException("类别代码已存在，一个代码只归一个类别，请勿重复录入");
            }
            WasteCategoryPO po = WasteCategoryPoConverter.toPo(category);
            po.setId(IdUtil.getSnowflakeNextId());
            try {
                wasteCategoryMapper.insert(po);
            } catch (DuplicateKeyException e) {
                // 跨进程并发时 JVM 锁拦不住，唯一约束兜底
                throw new BizException("类别代码已存在，一个代码只归一个类别，请勿重复录入");
            }
            return WasteCategoryPoConverter.toDomain(po);
        }));
    }

    @Override
    public Mono<WasteCategory> update(WasteCategory category) {
        return blocking(() -> {
            // 只改名录字段（名称 / 危险特性 / 跨省标志），代码与状态不碰；
            // updateById 带 id，MyBatis-Plus 只更新非空字段，用全字段 patch 显式落三块
            WasteCategoryPO patch = new WasteCategoryPO();
            patch.setName(category.getName());
            patch.setHazardType(category.getHazardType() == null ? null : category.getHazardType().name());
            patch.setCrossProvince(category.getCrossProvince());
            int rows = wasteCategoryMapper.update(patch, Wrappers.<WasteCategoryPO>lambdaUpdate()
                    .eq(WasteCategoryPO::getId, category.getId()));
            if (rows == 0) {
                throw new BizException("危废类别不存在或已删除");
            }
            return WasteCategoryPoConverter.toDomain(wasteCategoryMapper.selectById(category.getId()));
        });
    }

    @Override
    public Mono<PageResult<WasteCategory>> page(int pageNum, int pageSize, String hazardType, String status) {
        return this.<PageResult<WasteCategory>>blocking(() -> {
            try {
                PageHelper.startPage(pageNum, pageSize);
                LambdaQueryWrapper<WasteCategoryPO> wrapper = Wrappers.<WasteCategoryPO>lambdaQuery()
                        .eq(hazardType != null && !hazardType.isBlank(),
                                WasteCategoryPO::getHazardType, hazardType)
                        .eq(status != null && !status.isBlank(), WasteCategoryPO::getStatus, status)
                        .orderByAsc(WasteCategoryPO::getCategoryCode);
                List<WasteCategoryPO> rows = wasteCategoryMapper.selectList(wrapper);
                long total = rows instanceof com.github.pagehelper.Page
                        ? ((com.github.pagehelper.Page<?>) rows).getTotal()
                        : rows.size();
                List<WasteCategory> content = rows.stream()
                        .map(WasteCategoryPoConverter::toDomain)
                        .collect(Collectors.toList());
                return new PageResult<>(content, total, pageNum, pageSize);
            } finally {
                PageHelper.clearPage();
            }
        });
    }

    @Override
    public Mono<CategoryImpact> impact(String categoryCode) {
        return blocking(() -> loadImpact(categoryCode));
    }

    @Override
    public Mono<CategoryDisableResult> disable(String categoryCode, boolean force) {
        return blocking(() -> categoryGuard.inCategoryLock(categoryCode, () -> {
            WasteCategoryPO po = wasteCategoryMapper.selectOne(Wrappers.<WasteCategoryPO>lambdaQuery()
                    .eq(WasteCategoryPO::getCategoryCode, categoryCode));
            if (po == null) {
                throw new BizException("危废类别不存在");
            }
            if (CategoryStatus.DISABLED.name().equals(po.getStatus())) {
                throw new BizException("该类别已停用，请勿重复停用");
            }
            CategoryImpact impact = loadImpact(categoryCode);
            if (!force && impact.hasBlockingImpact()) {
                throw new BizException(impactBlockMessage(impact));
            }
            // 条件更新：仅 ENABLED → DISABLED。与新开经同一把类别锁排队，
            // 理论上不会再有并发抢跑；条件更新是跨进程 / 漏网情况下的最后一道闸
            int rows = wasteCategoryMapper.update(null, Wrappers.<WasteCategoryPO>lambdaUpdate()
                    .set(WasteCategoryPO::getStatus, CategoryStatus.DISABLED.name())
                    .eq(WasteCategoryPO::getId, po.getId())
                    .eq(WasteCategoryPO::getStatus, CategoryStatus.ENABLED.name()));
            if (rows == 0) {
                throw new BizException("类别状态刚发生变化，停用未生效，请刷新后重试");
            }
            WasteCategory disabled = WasteCategoryPoConverter.toDomain(wasteCategoryMapper.selectById(po.getId()));
            return new CategoryDisableResult(disabled, impact);
        }));
    }

    /** 停用硬牵动提示：把在库批次与在办联单的账摆出来，别闷头就停。 */
    private String impactBlockMessage(CategoryImpact impact) {
        return "停用被挡住：该类别当前还有在库批次 " + impact.inStockBatchCount()
                + " 批、合计 " + impact.inStockWeightKg().stripTrailingZeros().toPlainString()
                + " 千克，在办联单 " + impact.openManifestCount() + " 张"
                + "；确认停了这些老单子照走、只拦新单子，请带 force=true 再停";
    }

    /** 取停用影响面：在库批次数 / 重量、在办联单张数、待批计划份数。 */
    private CategoryImpact loadImpact(String categoryCode) {
        long inStockBatchCount = wasteStockMapper.countInStockByCategory(categoryCode);
        BigDecimal inStockWeight = wasteStockMapper.sumInStockWeightByCategory(categoryCode);
        long openManifestCount = transferManifestMapper.countOpenByCategory(categoryCode);
        long pendingPlanCount = transferPlanMapper.countPendingByCategory(categoryCode);
        return new CategoryImpact(inStockBatchCount,
                inStockWeight == null ? BigDecimal.ZERO : inStockWeight,
                openManifestCount, pendingPlanCount);
    }
}
