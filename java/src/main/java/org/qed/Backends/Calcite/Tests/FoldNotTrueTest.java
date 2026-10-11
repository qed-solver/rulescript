package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.logical.LogicalFilter;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRuleInstances.FoldNotTrue;
import org.qed.RuleBuilder;

public class FoldNotTrueTest {

    public static void runTest() {
        var rule = new FoldNotTrue();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        notTrue(rule);
    }

    private static void notTrue(FoldNotTrue rule) {
        var builder = RuleBuilder.create();
        var table = RuleChecks.table(builder, 1, false);
        var plain = RuleChecks.ruleBuilder(builder);
        var rex = plain.getRexBuilder();
        var scan = plain.scan(table).build();
        var before = LogicalFilter.create(scan, rex.makeCall(SqlStdOperatorTable.NOT, rex.makeLiteral(true)));
        var after = plain.scan(table)
                .filter(plain.literal(false))
                .build();
        RuleChecks.rewrites(rule, before, after);

        var negated = builder.scan(table)
                .filter(builder.call(SqlStdOperatorTable.NOT,
                        RuleChecks.gt(builder, builder.field(0), 5)))
                .build();
        RuleChecks.doesNotApply(rule, negated);
    }

    public static void main(String[] args) {
        System.out.println("Running FoldNotTrue test...");
        runTest();
    }
}
