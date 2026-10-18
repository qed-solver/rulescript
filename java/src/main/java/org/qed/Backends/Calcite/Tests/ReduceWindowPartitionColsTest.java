package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.tools.RelBuilder;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RRuleInstances.ReduceWindowPartitionCols;
import org.qed.RuleBuilder;

import java.util.List;

public final class ReduceWindowPartitionColsTest {
    public static void runTest() {
        var rule = new ReduceWindowPartitionCols();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(ReduceWindowPartitionCols rule) {
        var builder = RuleBuilder.create();
        var table = RuleChecks.table(builder, 2, false);
        var f = builder.genericProjectionOp("f", org.qed.RelType.fromString("INTEGER", true));
        var top = builder.genericProjectionOp("top", org.qed.RelType.fromString("BIGINT", true));
        var plan = join(builder, table, f, top, true);
        var plain = RuleChecks.ruleBuilder(builder);
        var expected = join(plain, table, f, top, false);
        RuleChecks.rewrites(rule, plan, expected);
        var nearMiss = join(builder, table, f, top, true, JoinRelType.LEFT);
        RuleChecks.doesNotApply(rule, nearMiss);
    }

    private static org.apache.calcite.rel.RelNode join(RelBuilder builder, String table,
            org.apache.calcite.sql.SqlOperator f, org.apache.calcite.sql.SqlOperator top, boolean full) {
        return join(builder, table, f, top, full, JoinRelType.INNER);
    }

    private static org.apache.calcite.rel.RelNode join(RelBuilder builder, String table,
            org.apache.calcite.sql.SqlOperator f, org.apache.calcite.sql.SqlOperator top, boolean full,
            JoinRelType joinType) {
        builder.push(inputNode(builder, table, f));
        builder.push(aggregateNode(builder, table, f, full));
        builder.join(joinType, builder.and(
                RuleChecks.eq(builder, builder.field(2, 0, 0), builder.field(2, 1, 0)),
                RuleChecks.eq(builder, builder.field(2, 0, 1), builder.field(2, 1, 1))));
        builder.project(List.of(builder.call(top, builder.field(1, 0, 0), builder.field(1, 0, 1),
                builder.field(1, 0, 2), builder.field(1, 0, 5))));
        return builder.build();
    }

    private static org.apache.calcite.rel.RelNode inputNode(RelBuilder builder, String table,
            org.apache.calcite.sql.SqlOperator f) {
        var b = builder.transform(config -> config);
        return b.scan(table).project(List.of(b.field(1, 0, 0), b.call(f, b.field(1, 0, 0)),
                b.field(1, 0, 1))).build();
    }

    private static org.apache.calcite.rel.RelNode aggregateNode(RelBuilder builder, String table,
            org.apache.calcite.sql.SqlOperator f, boolean full) {
        var b = builder.transform(config -> config);
        b.push(inputNode(builder, table, f));
        b.aggregate(b.groupKey(full ? List.of(b.field(1, 0, 0), b.field(1, 0, 1))
                : List.of(b.field(1, 0, 0))), List.of(b.count(false, "n", b.field(1, 0, 2))));
        if (!full) {
            b.project(List.of(b.field(1, 0, 0), b.call(f, b.field(1, 0, 0)),
                    b.field(1, 0, 1)));
        }
        return b.build();
    }

    public static void main(String[] args) {
        System.out.println("Running ReduceWindowPartitionCols test...");
        runTest();
        if (CalciteTester.failures() > 0) {
            throw new AssertionError(CalciteTester.failures() + " test failures");
        }
    }
}
