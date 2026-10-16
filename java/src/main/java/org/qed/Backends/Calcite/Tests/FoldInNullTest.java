package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.logical.LogicalFilter;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.sql.type.SqlTypeName;
import org.qed.RRuleInstances.FoldInNull;
import org.qed.RuleBuilder;

public class FoldInNullTest {

    public static void runTest() {
        var rule = new FoldInNull();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(FoldInNull rule) {
        var base = RuleBuilder.create();
        var t = RuleChecks.table(base, 1, false);
        var b = RuleChecks.ruleBuilder(base);
        b.scan(t);
        var nul = b.getRexBuilder().makeNullLiteral(b.field(0).getType());
        var nullBool = b.getRexBuilder().makeNullLiteral(b.getTypeFactory().createSqlType(SqlTypeName.BOOLEAN));
        var comparison = b.call(SqlStdOperatorTable.EQUALS, b.field(0), nul);
        var before = LogicalFilter.create(b.build(), comparison);
        var after = b.scan(t).filter(nullBool).build();
        RuleChecks.rewrites(rule, before, after);

        RuleChecks.doesNotApply(rule, b.scan(t).filter(b.call(SqlStdOperatorTable.EQUALS, b.field(0), b.literal(1))).build());
        RuleChecks.doesNotApply(rule, b.scan(t).filter(b.call(SqlStdOperatorTable.NOT_EQUALS, b.field(0), nul)).build());
    }

    public static void main(String[] args) {
        System.out.println("Running FoldInNull test...");
        runTest();
    }
}
