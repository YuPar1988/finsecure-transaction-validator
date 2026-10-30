package com.finsecure.validator.requestlog;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ApiRequestLogRepository extends JpaRepository<ApiRequestLog, Long>,
        JpaSpecificationExecutor<ApiRequestLog> {

    Optional<ApiRequestLog> findByRequestId(String requestId);

    List<ApiRequestLog> findTop10ByOrderByCreatedAtDesc();

    long countByCreatedAtGreaterThanEqual(Instant since);

    long countByCreatedAtGreaterThanEqualAndStatusCodeGreaterThanEqual(Instant since, int statusCode);

    long countByCreatedAtGreaterThanEqualAndOutcome(Instant since, String outcome);

    @Query("select avg(l.durationMs) from ApiRequestLog l where l.createdAt >= :since")
    Double averageDurationSince(@Param("since") Instant since);

    @Query("select max(l.durationMs) from ApiRequestLog l where l.createdAt >= :since")
    Long maxDurationSince(@Param("since") Instant since);

    @Query("select l.createdAt from ApiRequestLog l where l.createdAt >= :since")
    List<Instant> timestampsSince(@Param("since") Instant since);

    @Query("select l.statusCode, count(l) from ApiRequestLog l where l.createdAt >= :since "
            + "group by l.statusCode order by l.statusCode")
    List<Object[]> countByStatusSince(@Param("since") Instant since);

    @Query("select l.method, l.path, count(l), avg(l.durationMs) from ApiRequestLog l where l.createdAt >= :since "
            + "group by l.method, l.path order by count(l) desc")
    List<Object[]> endpointSummarySince(@Param("since") Instant since);

    @Query("select distinct l.path from ApiRequestLog l order by l.path")
    List<String> distinctPaths();
}
