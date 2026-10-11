package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rex.RexSubQuery;
import org.qed.RRuleInstances.EliminateExistsZeroRows;
import org.qed.RuleBuilder;

public class EliminateExistsZeroRowsTest {

    public static void runTest() {
        var rule = new EliminateExistsZeroRows();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        emptySubquery(rule);
    }

    private static void emptySubquery(EliminateExistsZeroRows rule) {
        var builder = RuleBuilder.create();
        var outer = RuleChecks.table(builder, 1, false);
        var inner = RuleChecks.table(builder, 1, false);
        var before = builder.scan(outer)
                .filter(RexSubQuery.exists(builder.scan(inner).empty().build()))
                .build();
        var plain = RuleChecks.ruleBuilder(builder);
        var after = plain.scan(outer).filter(plain.literal(false)).build();
        RuleChecks.rewrites(rule, before, after);

        var nonEmpty = builder.scan(outer)
                .filter(RexSubQuery.exists(builder.scan(inner).build()))
                .build();
        RuleChecks.doesNotApply(rule, nonEmpty);
    }

    public static void main(String[] args) {
        System.out.println("Running EliminateExistsZeroRows test...");
        runTest();
    }
}
