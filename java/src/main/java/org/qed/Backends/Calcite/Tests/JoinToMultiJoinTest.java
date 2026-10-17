package org.qed.Backends.Calcite.Tests;

import kala.collection.Seq;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rel.logical.LogicalJoin;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.tools.RelBuilder;
import org.qed.Backends.Calcite.CalciteRuntime;
import org.qed.RRuleInstances.JoinToMultiJoin;
import org.qed.RuleBuilder;

public class JoinToMultiJoinTest {
    public static void runTest() {
        var rule = new JoinToMultiJoin();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(JoinToMultiJoin rule) {
        var schema = RuleBuilder.create();
        var xName = RuleChecks.table(schema, 1, false, false);
        var yName = RuleChecks.table(schema, 1, false, false);
        var zName = RuleChecks.table(schema, 1, false, false);
        var b = schema.transform(CalciteRuntime::configure);
        RelNode x = schema.scan(xName).build();
        RelNode y = schema.scan(yName).build();
        RelNode z = schema.scan(zName).build();
        RelNode yz = b.push(y).push(z).join(JoinRelType.INNER, b.call(SqlStdOperatorTable.GREATER_THAN,
                b.getRexBuilder().makeInputRef(y.getRowType().getFieldList().get(0).getType(), 1), b.literal(4))).build();
        RelNode before = b.push(x).push(yz).join(JoinRelType.INNER, b.call(SqlStdOperatorTable.GREATER_THAN,
                b.getRexBuilder().makeInputRef(yz.getRowType().getFieldList().get(1).getType(), 2), b.literal(8))).build();

        RelNode expectedX = schema.scan(xName).build();
        RelNode expectedY = schema.scan(yName).build();
        RelNode expectedZ = schema.scan(zName).build();
        var rex = schema.getRexBuilder();
        RelNode flatYZ = LogicalJoin.create(expectedY, expectedZ, java.util.List.of(),
                rex.makeLiteral(true), java.util.Set.of(), JoinRelType.INNER);
        RexNode mappedNested = rex.makeCall(SqlStdOperatorTable.GREATER_THAN,
                rex.makeInputRef(flatYZ.getRowType().getFieldList().get(1).getType(), 2), rex.makeExactLiteral(java.math.BigDecimal.valueOf(4)));
        RexNode mappedOuter = rex.makeCall(SqlStdOperatorTable.GREATER_THAN,
                rex.makeInputRef(flatYZ.getRowType().getFieldList().get(1).getType(), 2), rex.makeExactLiteral(java.math.BigDecimal.valueOf(8)));
        RelNode expected = LogicalJoin.create(expectedX, flatYZ, java.util.List.of(),
                rex.makeCall(SqlStdOperatorTable.AND, mappedNested, mappedOuter), java.util.Set.of(), JoinRelType.INNER);
        RuleChecks.rewrites(rule, before, expected);

        RelNode outerInner = b.push(x).push(yz).join(JoinRelType.LEFT,
                b.call(SqlStdOperatorTable.GREATER_THAN, b.getRexBuilder().makeInputRef(yz.getRowType().getFieldList().get(1).getType(), 2), b.literal(8))).build();
        RuleChecks.doesNotApply(rule, outerInner);
    }

    public static void main(String[] args) {
        System.out.println("Running RULE test...");
        runTest();
    }
}
