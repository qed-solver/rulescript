package org.qed.Backends.Calcite.Tests;

import kala.collection.Seq;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.sql.type.SqlTypeName;
import org.qed.Backends.Calcite.CalciteRuntime;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RRuleInstances.FoldNullUnary;
import org.qed.RuleBuilder;

public final class FoldNullUnaryTest {
    public static void runTest() {
        var rule = new FoldNullUnary();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        rewritesConcreteNull();
        rejectsOtherUnaryOperator();
    }

    private static void rewritesConcreteNull() {
        var builder = RuleBuilder.create();
        var table = RuleChecks.table(builder, 1, false);
        var b = RuleChecks.ruleBuilder(builder);
        RexNode nullInt = b.getRexBuilder().makeNullLiteral(b.getTypeFactory().createSqlType(SqlTypeName.INTEGER));
        RelNode before = b.scan(table).project(b.call(SqlStdOperatorTable.UNARY_MINUS, nullInt)).build();
        var expectedBuilder = RuleBuilder.create();
        var expectedTable = RuleChecks.table(expectedBuilder, 1, false);
        var eb = RuleChecks.ruleBuilder(expectedBuilder);
        RexNode expectedNull = eb.getRexBuilder().makeNullLiteral(
                eb.getTypeFactory().createSqlType(SqlTypeName.INTEGER));
        RelNode expected = eb.scan(expectedTable).project(expectedNull).build();
        RuleChecks.rewrites(new FoldNullUnary(), before, expected);
    }

    private static void rejectsOtherUnaryOperator() {
        var builder = RuleBuilder.create();
        var table = RuleChecks.table(builder, 1, false);
        var b = RuleChecks.ruleBuilder(builder);
        RexNode nullInt = b.getRexBuilder().makeNullLiteral(
                b.getTypeFactory().createSqlType(SqlTypeName.INTEGER));
        RelNode before = b.scan(table).project(b.call(SqlStdOperatorTable.UNARY_PLUS, nullInt)).build();
        RuleChecks.doesNotApply(new FoldNullUnary(), before);
    }

    public static void main(String[] args) {
        System.out.println("Running FoldNullUnary test...");
        runTest();
        if (CalciteTester.failures() > 0) {
            System.exit(1);
        }
    }
}
