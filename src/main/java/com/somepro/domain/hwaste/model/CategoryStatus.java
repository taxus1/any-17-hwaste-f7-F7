package com.somepro.domain.hwaste.model;

import com.somepro.common.exception.BizException;

/**
 * 危废类别状态（纯领域枚举）。
 */
public enum CategoryStatus {

    /** 启用。 */
    ENABLED,
    /** 停用。 */
    DISABLED;

    /** 按名解析，传不认识的值一律挡回。 */
    public static CategoryStatus of(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BizException("状态取值非法，只接受 ENABLED / DISABLED");
        }
        try {
            return valueOf(raw.trim());
        } catch (IllegalArgumentException e) {
            throw new BizException("状态取值非法，只接受 ENABLED / DISABLED");
        }
    }
}
