package com.lia.core.pipeline.batch;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

import javax.sql.DataSource;

/**
 * 배치 단일 실행 보장(D62) — Postgres 세션 advisory lock. 잠금은 <b>전용 커넥션</b>에 묶이므로
 * 실행 내내 이 객체를 들고 있다가 닫는다(닫으면 잠금 해제 + 커넥션 반납).
 */
public final class JobLock implements AutoCloseable {

    private static final String KEY = "lia-ingest-job";

    private final Connection connection;

    private JobLock(Connection connection) {
        this.connection = connection;
    }

    /** 잡으면 잠금, 다른 실행이 잡고 있으면 empty. */
    public static Optional<JobLock> tryAcquire(DataSource dataSource) {
        Connection connection = null;
        try {
            connection = dataSource.getConnection();
            try (PreparedStatement statement = connection.prepareStatement("SELECT pg_try_advisory_lock(hashtext(?))")) {
                statement.setString(1, KEY);
                try (ResultSet result = statement.executeQuery()) {
                    result.next();
                    if (result.getBoolean(1)) {
                        return Optional.of(new JobLock(connection));
                    }
                }
            }
            connection.close();
            return Optional.empty();
        } catch (SQLException e) {
            closeQuietly(connection);
            throw new IllegalStateException("배치 잠금 획득 실패", e);
        }
    }

    @Override
    public void close() {
        try (Connection held = connection;
             PreparedStatement statement = held.prepareStatement("SELECT pg_advisory_unlock(hashtext(?))")) {
            statement.setString(1, KEY);
            statement.executeQuery().close();
        } catch (SQLException e) {
            throw new IllegalStateException("배치 잠금 해제 실패", e);
        }
    }

    private static void closeQuietly(Connection connection) {
        if (connection == null) return;
        try {
            connection.close();
        } catch (SQLException ignored) {
            // 획득 실패 경로의 정리 — 원래 예외를 덮지 않는다
        }
    }
}
