package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.sql.type.SqlTypeName;
import org.qed.RRuleInstances.FoldFunctionWithNullArg;
import org.qed.RuleBuilder;

public class FoldFunctionWithNullArgTest {

    public static void runTest() {
        var rule = new FoldFunctionWithNullArg();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(FoldFunctionWithNullArg rule) {
        var base = RuleBuilder.create();
        var t = RuleChecks.table(base, 1, false);
        var b = RuleChecks.ruleBuilder(base);
        b.scan(t);
        var nul = b.getRexBuilder().makeNullLiteral(b.field(0).getType());
        var nullBool = b.getRexBuilder().makeNullLiteral(b.getTypeFactory().createSqlType(SqlTypeName.BOOLEAN));
        var before = b.project(b.call(SqlStdOperatorTable.EQUALS, b.field(0), nul)).build();
        var after = b.scan(t).project(nullBool).build();
        RuleChecks.rewrites(rule, before, after);

        RuleChecks.doesNotApply(rule, b.scan(t).project(b.call(SqlStdOperatorTable.EQUALS, nul, b.field(0))).build());
        RuleChecks.doesNotApply(rule, b.scan(t).project(b.call(SqlStdOperatorTable.EQUALS, b.field(0), b.literal(1))).build());
    }

    public static void main(String[] args) {
        System.out.println("Running FoldFunctionWithNullArg test...");
        runTest();
    }
}
