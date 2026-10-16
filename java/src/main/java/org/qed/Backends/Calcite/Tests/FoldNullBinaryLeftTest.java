package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.logical.LogicalFilter;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.sql.type.SqlTypeName;
import org.qed.RRuleInstances.FoldNullBinaryLeft;
import org.qed.RuleBuilder;

public class FoldNullBinaryLeftTest {

    public static void runTest() {
        var rule = new FoldNullBinaryLeft();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(FoldNullBinaryLeft rule) {
        var base = RuleBuilder.create();
        var t = RuleChecks.table(base, 1, false);
        var b = RuleChecks.ruleBuilder(base);
        b.scan(t);
        var nul = b.getRexBuilder().makeNullLiteral(b.field(0).getType());
        var nullBool = b.getRexBuilder().makeNullLiteral(b.getTypeFactory().createSqlType(SqlTypeName.BOOLEAN));
        var comparison = b.call(SqlStdOperatorTable.LESS_THAN, nul, b.field(0));
        var before = LogicalFilter.create(b.build(), comparison);
        var after = b.scan(t).filter(nullBool).build();
        RuleChecks.rewrites(rule, before, after);

        RuleChecks.doesNotApply(rule, b.scan(t).filter(b.call(SqlStdOperatorTable.LESS_THAN, b.literal(1), b.field(0))).build());
        RuleChecks.doesNotApply(rule, b.scan(t).filter(b.call(SqlStdOperatorTable.GREATER_THAN, nul, b.field(0))).build());
    }

    public static void main(String[] args) {
        System.out.println("Running FoldNullBinaryLeft test...");
        runTest();
    }
}
