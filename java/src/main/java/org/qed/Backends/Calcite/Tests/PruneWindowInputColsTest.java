package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRuleInstances.PruneWindowInputCols;
import org.qed.RelType;
import org.qed.RuleBuilder;

import java.util.List;

public class PruneWindowInputColsTest {

    public static void runTest() {
        var rule = new PruneWindowInputCols();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(PruneWindowInputCols rule) {
        var base = RuleBuilder.create();
        var t = RuleChecks.table(base, 3, false);
        var top = base.genericProjectionOp("Top", RelType.fromString("INTEGER", true));
        var w = base.genericAggregateOp("w", RelType.fromString("INTEGER", true));
        var plain = RuleChecks.ruleBuilder(base);

        var before = base.scan(t)
                .scan(t).project(base.field(0), base.field(1))
                .aggregate(base.groupKey(0), base.aggregateCall(w, base.field(1)))
                .join(JoinRelType.INNER, base.call(SqlStdOperatorTable.EQUALS, base.field(2, 0, 0), base.field(2, 1, 0)))
                .project(List.of(base.call(top, base.field(0), base.field(1), base.field(4))))
                .build();
        var after = plain.scan(t).project(plain.field(0), plain.field(1))
                .scan(t).project(plain.field(0), plain.field(1))
                .aggregate(plain.groupKey(0), plain.aggregateCall(w, plain.field(1)))
                .join(JoinRelType.INNER, plain.call(SqlStdOperatorTable.EQUALS, plain.field(2, 0, 0), plain.field(2, 1, 0)))
                .project(List.of(plain.call(top, plain.field(0), plain.field(1), plain.field(3))))
                .build();
        RuleChecks.rewrites(rule, before, after);

        RuleChecks.doesNotApply(rule, base.scan(t)
                .scan(t).project(base.field(0), base.field(2))
                .aggregate(base.groupKey(0), base.aggregateCall(w, base.field(1)))
                .join(JoinRelType.INNER, base.call(SqlStdOperatorTable.EQUALS, base.field(2, 0, 0), base.field(2, 1, 0)))
                .project(List.of(base.call(top, base.field(0), base.field(1), base.field(4))))
                .build());
    }

    public static void main(String[] args) {
        System.out.println("Running PruneWindowInputCols test...");
        runTest();
    }
}
