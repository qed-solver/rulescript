package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.logical.LogicalFilter;
import org.qed.Backends.Calcite.CalciteRuntime;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRuleInstances.FoldUnary;
import org.qed.RuleBuilder;

public class FoldUnaryTest {

    public static void runTest() {
        var rule = new FoldUnary();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        notTrue(rule);
    }

    private static void notTrue(FoldUnary rule) {
        var builder = RuleBuilder.create();
        var table = RuleChecks.table(builder, 1, false, false);
        var plain = builder.transform(config -> CalciteRuntime.configure(config).withSimplifyValues(false));
        var rex = plain.getRexBuilder();
        var before = LogicalFilter.create(plain.scan(table).build(),
                rex.makeCall(SqlStdOperatorTable.NOT, rex.makeLiteral(true)));
        var after = LogicalFilter.create(plain.scan(table).build(), rex.makeLiteral(false));
        RuleChecks.rewrites(rule, before, after);

        var negated = builder.scan(table)
                .filter(builder.call(SqlStdOperatorTable.NOT,
                        RuleChecks.gt(builder, builder.field(0), 5)))
                .build();
        RuleChecks.doesNotApply(rule, negated);
    }

    public static void main(String[] args) {
        System.out.println("Running FoldUnary test...");
        runTest();
    }
}
