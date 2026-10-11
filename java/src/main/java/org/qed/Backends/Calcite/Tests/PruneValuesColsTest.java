package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.sql.SqlOperator;
import org.qed.RRuleInstances.PruneValuesCols;
import org.qed.RelType;
import org.qed.RuleBuilder;

public class PruneValuesColsTest {

    public static void runTest() {
        var rule = new PruneValuesCols();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        singleRowValues(rule);
    }

    private static void singleRowValues(PruneValuesCols rule) {
        var builder = RuleBuilder.create();
        SqlOperator top = builder.genericProjectionOp("Top", RelType.fromString("INTEGER", true));
        var before = builder.values(new String[]{"c0", "c1", "c2"}, 1, 2, 3)
                .project(builder.call(top, builder.field(0)), builder.field(1))
                .build();
        var plain = RuleChecks.ruleBuilder(builder);
        var after = plain.values(new String[]{"c0", "c1"}, 1, 2)
                .project(plain.call(top, plain.field(0)), plain.field(1))
                .build();
        RuleChecks.rewrites(rule, before, after);

        RuleChecks.doesNotApply(rule, builder.values(new String[]{"c0", "c1", "c2"}, 1, 2, 4)
                .project(builder.call(top, builder.field(0)), builder.field(1))
                .build());
        RuleChecks.doesNotApply(rule, builder.values(new String[]{"c0", "c1", "c2"}, 1, 2, 3)
                .project(builder.call(top, builder.field(0)), builder.field(2))
                .build());
    }

    public static void main(String[] args) {
        System.out.println("Running PruneValuesCols test...");
        runTest();
    }
}
