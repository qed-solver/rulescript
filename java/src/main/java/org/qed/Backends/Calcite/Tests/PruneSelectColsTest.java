package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRuleInstances.PruneSelectCols;
import org.qed.RelType;
import org.qed.RuleBuilder;

import java.util.List;

public class PruneSelectColsTest {

    public static void runTest() {
        var rule = new PruneSelectCols();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(PruneSelectCols rule) {
        var base = RuleBuilder.create();
        var t = RuleChecks.table(base, 3, false);
        var top = base.genericProjectionOp("Top", RelType.fromString("INTEGER", true));
        var plain = RuleChecks.ruleBuilder(base);

        var before = base.scan(t)
                .filter(base.call(SqlStdOperatorTable.EQUALS, base.field(0), base.field(1)))
                .project(List.of(base.call(top, base.field(0), base.field(1))))
                .build();
        var after = plain.scan(t)
                .project(List.of(plain.field(0), plain.field(1)))
                .filter(plain.call(SqlStdOperatorTable.EQUALS, plain.field(0), plain.field(1)))
                .project(List.of(plain.call(top, plain.field(0), plain.field(1))))
                .build();
        RuleChecks.rewrites(rule, before, after);

        RuleChecks.doesNotApply(rule, base.scan(t)
                .filter(base.call(SqlStdOperatorTable.EQUALS, base.field(0), base.field(2)))
                .project(List.of(base.call(top, base.field(0), base.field(1))))
                .build());
    }

    public static void main(String[] args) {
        System.out.println("Running PruneSelectCols test...");
        runTest();
    }
}
