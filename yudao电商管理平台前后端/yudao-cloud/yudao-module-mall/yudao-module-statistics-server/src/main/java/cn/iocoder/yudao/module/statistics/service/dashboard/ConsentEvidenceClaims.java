package cn.iocoder.yudao.module.statistics.service.dashboard;

import java.time.Instant;

public record ConsentEvidenceClaims(String nonce, Instant issuedAt, Instant expiresAt) {
}
