package org.qed.spark;

import org.apache.spark.sql.SparkSession;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EndToEndTest {
    private static SparkSession session(String... rules) {
        SparkSession.clearActiveSession();
        SparkSession.clearDefaultSession();
        return SparkSession.builder().master("local[1]").appName("rulescript-e2e")
                .config("spark.ui.enabled", "false")
                .config("spark.sql.adaptive.enabled", "false")

                .config("spark.sql.optimizer.excludedRules",
                        String.join(",", "org.apache.spark.sql.catalyst.optimizer.CombineFilters",
                                "org.apache.spark.sql.catalyst.optimizer.CollapseProject",
                                "org.apache.spark.sql.catalyst.optimizer.PushDownPredicates",
                                "org.apache.spark.sql.catalyst.optimizer.PushPredicateThroughNonJoin",
                                "org.apache.spark.sql.catalyst.optimizer.ColumnPruning",
                                "org.apache.spark.sql.catalyst.optimizer.RewritePredicateSubquery",
                                "org.apache.spark.sql.catalyst.optimizer.RewriteLateralSubquery"))
                .withExtensions(ext -> {
                    RuleScriptExtensions.install(ext, java.util.Arrays.stream(rules)
                            .map(r -> RuleScriptRule.load(Support.generated().resolve(r + ".json"))).toList());
                    return null;
                }).getOrCreate();
    }

    private static String optimized(SparkSession session, String sql) {
        return session.sql(sql).queryExecution().optimizedPlan().toString();
    }

    @Test
    void filterMergeFiresInTheOptimizer() {
        var spark = session("FilterMerge");
        spark.range(20).createOrReplaceTempView("t");
        var query = "SELECT id FROM (SELECT id FROM t WHERE id > 2) WHERE id < 15";
        var plan = optimized(spark, query);
        assertTrue(plan.contains("((id#") && plan.contains("AND"), plan);
        assertEquals(12, spark.sql(query).count());
        var execution = spark.sql(query).queryExecution();
        execution.optimizedPlan();
        var rules = execution.tracker().rules();
        assertTrue(rules.contains("FilterMerge"), "FilterMerge did not run: " + rules.keySet());
        assertTrue(rules.get("FilterMerge").get().numEffectiveInvocations() > 0);
    }

    @Test
    void joinCommuteAndProjectMergeKeepResults() {
        var spark = session("JoinCommute", "ProjectMerge");
        spark.range(10).createOrReplaceTempView("a");
        spark.range(5).createOrReplaceTempView("b");
        var query = "SELECT a.id + 1 AS x FROM a JOIN b ON a.id = b.id";
        var expected = spark.sql(query).collectAsList();
        assertEquals(List.of(1L, 2L, 3L, 4L, 5L), expected.stream().map(r -> r.getLong(0)).sorted().toList());
    }

    @Test
    void subqueryRuleTurnsExistsIntoASemiJoin() {
        var spark = session("RewritePredicateSubquery");
        spark.range(10).createOrReplaceTempView("a");
        spark.range(5).createOrReplaceTempView("b");
        var query = "SELECT id FROM a WHERE EXISTS (SELECT * FROM b WHERE a.id = b.id)";
        var execution = spark.sql(query).queryExecution();
        var plan = execution.optimizedPlan().toString();
        assertTrue(plan.contains("LeftSemi"), plan);
        assertTrue(execution.tracker().rules().get("RewritePredicateSubquery").get().numEffectiveInvocations() > 0, plan);
        assertEquals(List.of(0L, 1L, 2L, 3L, 4L),
                spark.sql(query).collectAsList().stream().map(r -> r.getLong(0)).sorted().toList());
    }

    @Test
    void lateralJoinQueriesKeepTheirResultsWithTheRulesInstalled() {
        var spark = session("RewriteLateralSubquery", "DecorrelateJoin");
        spark.range(4).createOrReplaceTempView("a");
        spark.range(3).createOrReplaceTempView("b");
        var query = "SELECT a.id, s.id FROM a, LATERAL (SELECT id FROM b WHERE a.id = b.id) s";
        assertEquals(3, spark.sql(query).collectAsList().size());
    }
}
