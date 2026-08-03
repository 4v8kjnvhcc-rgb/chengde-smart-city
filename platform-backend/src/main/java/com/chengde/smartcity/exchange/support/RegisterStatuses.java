package com.chengde.smartcity.exchange.support;

/** 数据资产登记审核状态（sjzc） */
public final class RegisterStatuses {
    public static final String DRAFT = "DRAFT";
    public static final String PENDING_REVIEW = "PENDING_REVIEW";
    public static final String APPROVED = "APPROVED";
    public static final String REJECTED = "REJECTED";

    private RegisterStatuses() {}

    public static String zh(String code) {
        if (code == null) return "—";
        return switch (code.trim().toUpperCase()) {
            case DRAFT -> "草稿";
            case PENDING_REVIEW, "PENDING" -> "待审核";
            case APPROVED -> "审核通过";
            case REJECTED -> "驳回待提交";
            default -> code;
        };
    }

    public static boolean canEdit(String status) {
        String s = status == null ? DRAFT : status.trim().toUpperCase();
        return DRAFT.equals(s) || REJECTED.equals(s);
    }

    public static boolean canSubmit(String status) {
        return canEdit(status);
    }

    public static boolean canAudit(String status) {
        String s = status == null ? "" : status.trim().toUpperCase();
        return PENDING_REVIEW.equals(s) || "PENDING".equals(s) || "PENDING_ARCHIVE".equals(s);
    }
}
