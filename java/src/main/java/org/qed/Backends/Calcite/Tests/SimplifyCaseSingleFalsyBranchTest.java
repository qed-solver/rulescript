package org.qed.Backends.Calcite.Tests;

import kala.collection.Seq;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.logical.LogicalFilter;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.sql.type.SqlTypeName;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RRuleInstances.SimplifyCaseSingleFalsyBranch;
import org.qed.RuleBuilder;
import org.qed.RelType;

public final class SimplifyCaseSingleFalsyBranchTest {
    public static void runTest() {
        var rule = new SimplifyCaseSingleFalsyBranch();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);

        var builder = RuleBuilder.create();
        var table = RuleChecks.table(builder, 1, false, true);
        var source = builder.scan(table).build();
        var rex = builder.getRexBuilder();
        var condition = rex.makeCall(SqlStdOperatorTable.IS_NULL,
                rex.makeInputRef(source.getRowType().getFieldList().get(0).getType(), 0));
        var falseLit = rex.makeLiteral(false);
        var nullLit = rex.makeNullLiteral(builder.getTypeFactory().createSqlType(SqlTypeName.BOOLEAN));
        var expanded = rex.makeCall(SqlStdOperatorTable.OR,
                rex.makeCall(SqlStdOperatorTable.AND, condition, falseLit),
                rex.makeCall(SqlStdOperatorTable.AND,
                        rex.makeCall(SqlStdOperatorTable.NOT, condition), nullLit));
        RelNode handBuiltMatch = LogicalFilter.create(source, expanded);
        RelNode handBuiltExpected = LogicalFilter.create(source, nullLit);
        RuleChecks.rewrites(rule, handBuiltMatch, handBuiltExpected);

        var wrongThen = rex.makeLiteral(true);
        var nearMiss = rex.makeCall(SqlStdOperatorTable.OR,
                rex.makeCall(SqlStdOperatorTable.AND, condition, wrongThen),
                rex.makeCall(SqlStdOperatorTable.AND,
                        rex.makeCall(SqlStdOperatorTable.NOT, condition), nullLit));
        RuleChecks.doesNotApply(rule, LogicalFilter.create(source, nearMiss));
    }

    public static void main(String[] args) {
        System.out.println("Running SimplifyCaseSingleFalsyBranch test...");
        runTest();
        if (CalciteTester.failures() != 0) {
            throw new AssertionError("Calcite test failures: " + CalciteTester.failures());
        }
    }
}
