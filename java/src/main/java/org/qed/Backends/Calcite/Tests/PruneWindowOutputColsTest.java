package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRuleInstances.PruneWindowOutputCols;
import org.qed.RuleBuilder;

import java.util.List;

public class PruneWindowOutputColsTest {

    public static void runTest() {
        var rule = new PruneWindowOutputCols();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(PruneWindowOutputCols rule) {
        var base = RuleBuilder.create();
        var table = RuleChecks.table(base, 2, false, false);
        var plain = RuleChecks.ruleBuilder(base);

        var input = base.scan(table).build();
        var window = base.push(input)
                .aggregate(base.groupKey(0),
                        base.aggregateCall(SqlStdOperatorTable.SUM, base.field(1)),
                        base.aggregateCall(SqlStdOperatorTable.MAX, base.field(1)))
                .build();
        var before = base.push(input).push(window)
                .join(JoinRelType.INNER, RuleChecks.eq(base, base.field(2, 0, 0), base.field(2, 1, 0)))
                .project(List.of(base.field(0), base.field(1), base.field(3)))
                .build();

        var leftIn = plain.scan(table).build();
        var rightIn = plain.scan(table)
                .aggregate(plain.groupKey(0), plain.aggregateCall(SqlStdOperatorTable.SUM, plain.field(1)))
                .build();
        var after = plain.push(leftIn).push(rightIn)
                .join(JoinRelType.INNER, RuleChecks.eq(plain, plain.field(2, 0, 0), plain.field(2, 1, 0)))
                .project(List.of(plain.field(0), plain.field(1), plain.field(3)))
                .build();
        RuleChecks.rewrites(rule, before, after);

        var misSelected = base.push(input).push(window)
                .join(JoinRelType.INNER, RuleChecks.eq(base, base.field(2, 0, 0), base.field(2, 1, 0)))
                .project(List.of(base.field(0), base.field(1), base.field(4)))
                .build();
        RuleChecks.doesNotApply(rule, misSelected);
    }

    public static void main(String[] args) {
        System.out.println("Running PruneWindowOutputCols test...");
        runTest();
    }
}
