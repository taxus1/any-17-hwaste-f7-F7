package com.somepro.interfaces.rest.hwaste;

import com.somepro.application.hwaste.WasteCategoryAppService;
import com.somepro.common.Result;
import com.somepro.interfaces.rest.hwaste.converter.WasteCategoryVoConverter;
import com.somepro.interfaces.rest.hwaste.vo.CategoryDisableResultVO;
import com.somepro.interfaces.rest.hwaste.vo.CategoryImpactVO;
import com.somepro.interfaces.rest.hwaste.vo.PageVO;
import com.somepro.interfaces.rest.hwaste.vo.WasteCategoryVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * 危废类别名录接口（用户接口层）：录入、修改、查看、停用、停用前影响面评估与多条件翻页。
 *
 * 定位参数：id 或 categoryCode 传其一即可。
 * 名录是入库、报计划、开联单共同照用的底册，停用不是删除 —— 老单子照走，只拦新单子。
 */
@RestController
@RequestMapping("/api/hwaste/category")
public class WasteCategoryController {

    private final WasteCategoryAppService wasteCategoryAppService;

    public WasteCategoryController(WasteCategoryAppService wasteCategoryAppService) {
        this.wasteCategoryAppService = wasteCategoryAppService;
    }

    /**
     * 名录录入：代码照 HW08 写法排且全局唯一（重复挡回），名称 / 危险特性（五档）/ 跨省标志（1/0），
     * status 不传按 ENABLED 落。
     */
    @PostMapping("/register")
    public Mono<Result<WasteCategoryVO>> register(@RequestParam(required = false) String categoryCode,
                                                  @RequestParam(required = false) String name,
                                                  @RequestParam(required = false) String hazardType,
                                                  @RequestParam(required = false) Integer crossProvince,
                                                  @RequestParam(required = false) String status) {
        return wasteCategoryAppService.register(categoryCode, name, hazardType, crossProvince, status)
                .map(WasteCategoryVoConverter::toVo)
                .map(Result::ok);
    }

    /** 改名录：代码不动，只改名称 / 危险特性 / 跨省标志；改标志只影响以后新开的联单，老联单原样不动。 */
    @PostMapping("/update")
    public Mono<Result<WasteCategoryVO>> update(@RequestParam(required = false) Long id,
                                                @RequestParam(required = false) String categoryCode,
                                                @RequestParam(required = false) String name,
                                                @RequestParam(required = false) String hazardType,
                                                @RequestParam(required = false) Integer crossProvince) {
        return wasteCategoryAppService.update(id, categoryCode, name, hazardType, crossProvince)
                .map(WasteCategoryVoConverter::toVo)
                .map(Result::ok);
    }

    /** 名录详情：id 或 categoryCode 传其一。 */
    @GetMapping("/detail")
    public Mono<Result<WasteCategoryVO>> detail(@RequestParam(required = false) Long id,
                                                @RequestParam(required = false) String categoryCode) {
        return wasteCategoryAppService.detail(id, categoryCode)
                .map(WasteCategoryVoConverter::toVo)
                .map(Result::ok);
    }

    /** 分页查询：危险特性 / 状态均可选，都不传分页列全；每行回类别代码。 */
    @GetMapping("/page")
    public Mono<Result<PageVO<WasteCategoryVO>>> page(@RequestParam(defaultValue = "1") int pageNum,
                                                      @RequestParam(defaultValue = "20") int pageSize,
                                                      @RequestParam(required = false) String hazardType,
                                                      @RequestParam(required = false) String status) {
        return wasteCategoryAppService.page(pageNum, pageSize, hazardType, status)
                .map(WasteCategoryVoConverter::toPageVo)
                .map(Result::ok);
    }

    /** 停用前影响面：在库批次数 / 重量、在办联单张数、待批计划份数，停用前先看这笔账。 */
    @GetMapping("/impact")
    public Mono<Result<CategoryImpactVO>> impact(@RequestParam(required = false) Long id,
                                                 @RequestParam(required = false) String categoryCode) {
        return wasteCategoryAppService.impact(id, categoryCode)
                .map(WasteCategoryVoConverter::toImpactVo)
                .map(Result::ok);
    }

    /**
     * 停用：有在库批次或在办联单时默认挡回并把牵动的单子说明白；
     * 看过影响面知情后确要停用，带 force=true —— 先前在办的单子照走，只是以后不再登新入库、
     * 不再开新联单和新计划。
     */
    @PostMapping("/disable")
    public Mono<Result<CategoryDisableResultVO>> disable(@RequestParam(required = false) Long id,
                                                         @RequestParam(required = false) String categoryCode,
                                                         @RequestParam(defaultValue = "false") boolean force) {
        return wasteCategoryAppService.disable(id, categoryCode, force)
                .map(WasteCategoryVoConverter::toDisableResultVo)
                .map(Result::ok);
    }
}
