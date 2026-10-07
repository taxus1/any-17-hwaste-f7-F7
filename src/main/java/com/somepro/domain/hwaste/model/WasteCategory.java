package com.somepro.domain.hwaste.model;

import com.somepro.common.exception.BizException;
import com.somepro.domain.shared.model.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.util.regex.Pattern;

/**
 * 危险废物类别名录（纯领域聚合根）。一条名录一个类别，类别代码照 HW08 这样的写法排。
 *
 * 名录上留：类别代码（全局唯一）、类别名称、危险特性（五档）、跨省标志（1 不许跨省 / 0 允许）、
 * 状态（ENABLED 启用 / DISABLED 停用，没写状态按启用落）。
 *
 * 停用是名录自己的行为（{@link #disable()}），停用影响面（在库批次、在办联单、待批计划）
 * 由应用层取数编排，本聚合只管「只有启用的才能停」这条状态规矩。
 */
@Getter
@Setter
public class WasteCategory extends BaseEntity {

    /** 类别代码写法：HW 打头，后跟两位数字，如 HW08 / HW50。 */
    private static final Pattern CODE_PATTERN = Pattern.compile("HW\\d{2}");

    private Long id;

    /** 危废类别代码，形如 HW08，全局唯一。 */
    private String categoryCode;

    /** 类别名称。 */
    private String name;

    /** 危险特性：TOXIC / CORROSIVE / FLAMMABLE / REACTIVE / INFECTIOUS。 */
    private HazardType hazardType;

    /** 是否限制跨省转移：1 限制（不得跨省）/ 0 允许。 */
    private Integer crossProvince;

    private CategoryStatus status;

    /**
     * 名录登记：一条名录一个类别。代码写法不对、名称 / 危险特性没给、跨省标志不是 0/1 都挡回；
     * 状态没写按启用 ENABLED 落。代码是否与已有名录重复由仓储侧查重 + 库表唯一约束双道挡回。
     */
    public static WasteCategory register(String categoryCode, String name, String hazardType,
                                         Integer crossProvince, String status) {
        String code = requireCode(categoryCode);
        if (name == null || name.isBlank()) {
            throw new BizException("类别名称不能为空");
        }
        HazardType hazard = HazardType.of(hazardType);
        WasteCategory category = new WasteCategory();
        category.setCategoryCode(code);
        category.setName(name.trim());
        category.setHazardType(hazard);
        category.setCrossProvince(normalizeCrossProvince(crossProvince));
        category.setStatus(status == null || status.isBlank()
                ? CategoryStatus.ENABLED : CategoryStatus.of(status));
        return category;
    }

    /**
     * 改名录：代码是名录的根（库存 / 联单 / 计划都拿它对账），不允许改；
     * 只改名称、危险特性、跨省标志。这里改的是名录，老联单上当初抄下来的跨省标志原样不动。
     */
    public void revise(String name, String hazardType, Integer crossProvince) {
        if (name == null || name.isBlank()) {
            throw new BizException("类别名称不能为空");
        }
        this.name = name.trim();
        this.hazardType = HazardType.of(hazardType);
        this.crossProvince = normalizeCrossProvince(crossProvince);
    }

    /** 停用：只有还在启用的类别才谈得上停用，已停用的别重复停。 */
    public void disable() {
        if (this.status == CategoryStatus.DISABLED) {
            throw new BizException("该类别已停用，请勿重复停用");
        }
        this.status = CategoryStatus.DISABLED;
    }

    /** 只有启用（ENABLED）的类别才允许登记入库 / 开新联单 / 立新计划。 */
    public boolean isEnabled() {
        return this.status == CategoryStatus.ENABLED;
    }

    /** 名录上标了不许跨省的类别，供废与收货两头不在同一个省时联单开不出去。 */
    public boolean isCrossProvinceRestricted() {
        return this.crossProvince != null && this.crossProvince == 1;
    }

    private static String requireCode(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BizException("类别代码不能为空");
        }
        String code = raw.trim();
        if (!CODE_PATTERN.matcher(code).matches()) {
            throw new BizException("类别代码写法非法，需形如 HW08（HW + 两位数字）");
        }
        return code;
    }

    /** 跨省标志只认 1 / 0；没写按 0（允许跨省）落，与建表默认一致。 */
    private static Integer normalizeCrossProvince(Integer crossProvince) {
        if (crossProvince == null) {
            return 0;
        }
        if (crossProvince != 0 && crossProvince != 1) {
            throw new BizException("跨省标志只接受 1（限制跨省）/ 0（允许跨省）");
        }
        return crossProvince;
    }
}
