package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRuleInstances.EliminateZeroCardProject;
import org.qed.RuleBuilder;

public class EliminateZeroCardProjectTest {

    public static void runTest() {
        var rule = new EliminateZeroCardProject();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        emptyInput(rule);
    }

    private static void emptyInput(EliminateZeroCardProject rule) {
        var builder = RuleBuilder.create();
        var narrow = RuleChecks.table(builder, 2, false);
        var wide = RuleChecks.table(builder, 3, false);
        var one = builder.literal(1);
        var before = builder.scan(narrow).empty()
                .project(builder.field(0), builder.field(1),
                        builder.call(SqlStdOperatorTable.PLUS, builder.field(0), one))
                .build();
        var plain = RuleChecks.ruleBuilder(builder);
        var after = plain.scan(wide).empty().build();
        RuleChecks.rewrites(rule, before, after);

        var nonEmpty = builder.scan(narrow)
                .project(builder.field(0), builder.field(1),
                        builder.call(SqlStdOperatorTable.PLUS, builder.field(0), one))
                .build();
        RuleChecks.doesNotApply(rule, nonEmpty);
    }

    public static void main(String[] args) {
        System.out.println("Running EliminateZeroCardProject test...");
        runTest();
    }
}
