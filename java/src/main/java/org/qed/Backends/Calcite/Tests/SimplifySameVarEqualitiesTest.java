package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.logical.LogicalFilter;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.Backends.Calcite.CalciteRuntime;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RRuleInstances.SimplifySameVarEqualities;
import org.qed.RuleBuilder;

public final class SimplifySameVarEqualitiesTest {
    public static void runTest() {
        var rule = new SimplifySameVarEqualities();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);

        var qed = RuleBuilder.create();
        var table = RuleChecks.table(qed, 1, false, true);
        var rb = RuleChecks.ruleBuilder(qed);
        var scan = rb.scan(table).build();
        var x = rb.getRexBuilder().makeInputRef(scan, 0);
        var same = rb.getRexBuilder().makeCall(SqlStdOperatorTable.EQUALS, x, x);
        var expected = rb.getRexBuilder().makeCall(SqlStdOperatorTable.OR,
                rb.getRexBuilder().makeCall(SqlStdOperatorTable.IS_NOT_NULL, x),
                rb.getRexBuilder().makeNullLiteral(rb.getTypeFactory().createSqlType(org.apache.calcite.sql.type.SqlTypeName.INTEGER)));
        RuleChecks.rewrites(rule, LogicalFilter.create(scan, same), LogicalFilter.create(scan, expected));

        var differentOperands = rb.getRexBuilder().makeCall(SqlStdOperatorTable.EQUALS, x, rb.literal(7));
        RuleChecks.doesNotApply(rule, LogicalFilter.create(scan, differentOperands));
    }

    public static void main(String[] args) {
        System.out.println("Running SimplifySameVarEqualities test...");
        runTest();
    }
}
