package com.somepro.domain.hwaste.model;

import com.somepro.common.exception.BizException;

/**
 * 危险特性（纯领域枚举，落库时存 name() 字符串）。一共五档，名录登记时必须落在其中一档。
 */
public enum HazardType {

    /** 毒性。 */
    TOXIC,
    /** 腐蚀性。 */
    CORROSIVE,
    /** 易燃。 */
    FLAMMABLE,
    /** 反应性。 */
    REACTIVE,
    /** 感染性。 */
    INFECTIOUS;

    /** 按名解析，传不认识的值（含 null / 空白）一律挡回，别静默落成某一档。 */
    public static HazardType of(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BizException("危险特性不能为空");
        }
        try {
            return valueOf(raw.trim());
        } catch (IllegalArgumentException e) {
            throw new BizException("危险特性取值非法，只接受 TOXIC / CORROSIVE / FLAMMABLE / REACTIVE / INFECTIOUS");
        }
    }
}
