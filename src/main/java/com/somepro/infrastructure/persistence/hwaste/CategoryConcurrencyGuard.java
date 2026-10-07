package com.somepro.infrastructure.persistence.hwaste;

import com.somepro.common.exception.BizException;
import com.somepro.domain.hwaste.model.CategoryStatus;
import com.somepro.domain.hwaste.model.WasteCategory;
import com.somepro.infrastructure.persistence.hwaste.po.WasteCategoryPO;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * 危废类别并发闸（基础设施层）：按类别代码分锁，把「停用」与「新开（入库 / 联单 / 计划）」
 * 这两件几乎同时发生的事排成先后，保证落到库里的账自洽 ——
 *
 * - 新开的单子先拿到锁：复核类别 ENABLED 通过后落单，随后停用的请求才拿锁、置 DISABLED。
 *   这张单子是停用前开出去的，合法，照走；
 * - 停用先拿到锁：置 DISABLED 后释放，随后新开的请求拿锁、复核到 DISABLED 被挡回。
 *
 * 不会出现「名录显示已停用、却还挂着一张停用之后才新开的联单」这种两头对不上的账。
 *
 * 锁序约定（防死锁）：各业务线一律「先拿自己的业务锁（WB / TP / EM），再拿类别锁」，
 * 停用一侧只拿类别锁，全局只有这一个方向，不成环。
 * 与 {@link BizNoService} 同理，JVM 内串行；库表状态 / 唯一约束是跨进程的最后兜底。
 */
@Component
public class CategoryConcurrencyGuard {

    /** 按类别代码分锁：不同类别互不阻塞，同一类别的停用与新开串行。 */
    private final Map<String, ReentrantLock> locks = new ConcurrentHashMap<>();

    private final WasteCategoryMapper wasteCategoryMapper;

    public CategoryConcurrencyGuard(WasteCategoryMapper wasteCategoryMapper) {
        this.wasteCategoryMapper = wasteCategoryMapper;
    }

    /** 在指定类别的锁里执行一段临界区。 */
    public <T> T inCategoryLock(String categoryCode, Supplier<T> body) {
        ReentrantLock lock = locks.computeIfAbsent(categoryCode, k -> new ReentrantLock());
        lock.lock();
        try {
            return body.get();
        } finally {
            lock.unlock();
        }
    }

    /**
     * 锁内复核：类别必须仍处于启用，否则挡回。新开单子（入库 / 联单 / 计划）在自己的业务锁里调它。
     * 停用先到一步把状态置成 DISABLED 后，后到的新开在这里读到 DISABLED 被挡回。
     */
    public WasteCategory requireEnabled(String categoryCode, String action) {
        WasteCategoryPO po = wasteCategoryMapper.selectOne(Wrappers.<WasteCategoryPO>lambdaQuery()
                .eq(WasteCategoryPO::getCategoryCode, categoryCode));
        if (po == null) {
            throw new BizException("危废类别不存在");
        }
        if (!CategoryStatus.ENABLED.name().equals(po.getStatus())) {
            throw new BizException("危废类别已停用，禁止" + action);
        }
        return com.somepro.infrastructure.persistence.hwaste.converter.WasteCategoryPoConverter.toDomain(po);
    }
}
