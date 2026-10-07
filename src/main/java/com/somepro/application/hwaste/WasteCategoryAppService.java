package com.somepro.application.hwaste;

import com.somepro.common.exception.BizException;
import com.somepro.domain.hwaste.model.CategoryDisableResult;
import com.somepro.domain.hwaste.model.CategoryImpact;
import com.somepro.domain.hwaste.model.CategoryStatus;
import com.somepro.domain.hwaste.model.HazardType;
import com.somepro.domain.hwaste.model.WasteCategory;
import com.somepro.domain.hwaste.repository.WasteCategoryRepository;
import com.somepro.domain.shared.model.PageResult;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * 危废类别名录用例编排（应用层）：录入、修改、查看、停用，外加停用前的影响面评估。
 *
 * 规矩：
 * - 一条名录一个类别，代码照 HW08 写法排，重复代码由领域校验 + 仓储锁内查重 + 库表唯一约束三道挡回；
 * - 没写状态按启用落；停用只置 DISABLED，先前在办的入库 / 联单 / 计划照走，只拦以后新开的；
 * - 停用前先看影响面：有在库批次或在办联单时默认挡回，把牵动的账摆给人看，
 *   知情后确要停用带 force=true；待批计划只提示不硬挡。
 *
 * 停用与新开（入库 / 开联单 / 立计划）的前后脚并发由仓储侧类别锁 + 锁内复核兜，这里只管编排。
 */
@Service
public class WasteCategoryAppService {

    private final WasteCategoryRepository wasteCategoryRepository;

    public WasteCategoryAppService(WasteCategoryRepository wasteCategoryRepository) {
        this.wasteCategoryRepository = wasteCategoryRepository;
    }

    /** 名录录入：校验与默认值在领域工厂里落，重复代码仓储侧挡回；状态不传按启用落。 */
    public Mono<WasteCategory> register(String categoryCode, String name, String hazardType,
                                        Integer crossProvince, String status) {
        return Mono.defer(() -> {
            WasteCategory category = WasteCategory.register(categoryCode, name, hazardType, crossProvince, status);
            return wasteCategoryRepository.create(category);
        });
    }

    /** 改名录：代码不动，只改名称 / 危险特性 / 跨省标志。改跨省标志只影响以后新开的联单，老联单不动。 */
    public Mono<WasteCategory> update(Long id, String categoryCode, String name,
                                      String hazardType, Integer crossProvince) {
        return load(id, categoryCode).flatMap(category -> {
            category.revise(name, hazardType, crossProvince);
            return wasteCategoryRepository.update(category);
        });
    }

    /** 名录详情：id 或类别代码传其一。 */
    public Mono<WasteCategory> detail(Long id, String categoryCode) {
        return load(id, categoryCode);
    }

    /** 翻名录：危险特性 / 状态随意挑，分页返回，每条都带类别代码。 */
    public Mono<PageResult<WasteCategory>> page(int pageNum, int pageSize, String hazardType, String status) {
        return Mono.defer(() -> {
            // 过滤值先过枚举，不合法别静默查出空页
            if (hazardType != null && !hazardType.isBlank()) {
                HazardType.of(hazardType);
            }
            if (status != null && !status.isBlank()) {
                CategoryStatus.of(status);
            }
            return wasteCategoryRepository.page(pageNum, pageSize, hazardType, status);
        });
    }

    /** 停用前影响面：在库批次数 / 重量、在办联单张数、待批计划份数。 */
    public Mono<CategoryImpact> impact(Long id, String categoryCode) {
        return resolveCode(id, categoryCode).flatMap(wasteCategoryRepository::impact);
    }

    /**
     * 停用：默认非强制，有在库批次或在办联单就挡回并把牵动的账说明白；
     * force=true 表示看过影响面知情后照停，老单子照走、只拦新单子。
     */
    public Mono<CategoryDisableResult> disable(Long id, String categoryCode, boolean force) {
        return resolveCode(id, categoryCode)
                .flatMap(code -> wasteCategoryRepository.disable(code, force));
    }

    /** 按 id 或代码加载类别；两个都不传或查不到都视为业务失败。 */
    private Mono<WasteCategory> load(Long id, String categoryCode) {
        Mono<WasteCategory> found;
        if (id != null) {
            found = wasteCategoryRepository.findById(id);
        } else if (categoryCode != null && !categoryCode.isBlank()) {
            found = wasteCategoryRepository.findByCode(categoryCode.trim());
        } else {
            return Mono.error(new BizException("id 或 categoryCode 必传其一"));
        }
        return found.switchIfEmpty(Mono.error(new BizException("危废类别不存在")));
    }

    /** 停用按代码上锁，按 id 进来时先解析出代码。 */
    private Mono<String> resolveCode(Long id, String categoryCode) {
        if (categoryCode != null && !categoryCode.isBlank()) {
            return Mono.just(categoryCode.trim());
        }
        if (id != null) {
            return wasteCategoryRepository.findById(id)
                    .switchIfEmpty(Mono.error(new BizException("危废类别不存在")))
                    .map(WasteCategory::getCategoryCode);
        }
        return Mono.error(new BizException("id 或 categoryCode 必传其一"));
    }
}
