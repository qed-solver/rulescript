package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.qed.RRuleInstances.RightAssociateJoinsRight;
import org.qed.RuleBuilder;

public class RightAssociateJoinsRightTest {
    public static void runTest() {
        var rule = new RightAssociateJoinsRight();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        independentConcretePlan();
        rejectsOuterJoin();
    }

    private static void independentConcretePlan() {
        var qed = RuleBuilder.create();
        var a = RuleChecks.table(qed, 1, false);
        var b = RuleChecks.table(qed, 1, false);
        var c = RuleChecks.table(qed, 1, false);
        var builder = RuleChecks.ruleBuilder(qed);
        var scans = new org.apache.calcite.rel.RelNode[] {
                builder.scan(a).build(), builder.scan(b).build(), builder.scan(c).build()
        };
        var rex = builder.getRexBuilder();
        var type = scans[0].getRowType().getFieldList().get(0).getType();
        var bc = org.apache.calcite.rel.logical.LogicalJoin.create(scans[1], scans[2],
                java.util.List.of(), rex.makeLiteral(true), java.util.Set.of(), JoinRelType.INNER);
        var refs = new org.apache.calcite.rex.RexNode[] {
                rex.makeInputRef(type, 0), rex.makeInputRef(type, 1), rex.makeInputRef(type, 2)
        };
        var condition = rex.makeCall(org.apache.calcite.sql.fun.SqlStdOperatorTable.AND,
                RuleChecks.eq(builder, refs[0], refs[2]), RuleChecks.eq(builder, refs[0], refs[1]));
        var source = org.apache.calcite.rel.logical.LogicalJoin.create(scans[0], bc,
                java.util.List.of(), condition, java.util.Set.of(), JoinRelType.INNER);

        var expectedBuilder = RuleChecks.ruleBuilder(qed);
        var expectedAC = expectedBuilder.scan(a).scan(c)
                .join(JoinRelType.INNER, RuleChecks.eq(expectedBuilder,
                        expectedBuilder.getRexBuilder().makeInputRef(
                                expectedBuilder.peek().getRowType().getFieldList().get(0).getType(), 0),
                        expectedBuilder.getRexBuilder().makeInputRef(
                                expectedBuilder.peek().getRowType().getFieldList().get(0).getType(), 1))).build();
        expectedBuilder = RuleChecks.ruleBuilder(qed);
        var expType = expectedAC.getRowType().getFieldList().get(0).getType();
        var expRefs = new org.apache.calcite.rex.RexNode[] {
                expectedBuilder.getRexBuilder().makeInputRef(expType, 0),
                expectedBuilder.getRexBuilder().makeInputRef(expType, 1),
                expectedBuilder.getRexBuilder().makeInputRef(expType, 2)
        };
        var expectedCondition = RuleChecks.eq(expectedBuilder, expRefs[0], expRefs[2]);
        var expectedJoin = org.apache.calcite.rel.logical.LogicalJoin.create(expectedAC,
                expectedBuilder.scan(b).build(), java.util.List.of(), expectedCondition,
                java.util.Set.of(), JoinRelType.INNER);
        var expectedPlan = expectedBuilder.push(expectedJoin).project(
                expectedBuilder.getRexBuilder().makeInputRef(expType, 0),
                expectedBuilder.getRexBuilder().makeInputRef(expType, 2),
                expectedBuilder.getRexBuilder().makeInputRef(expType, 1)).build();
        RuleChecks.rewrites(new RightAssociateJoinsRight(), source, expectedPlan);
    }

    private static void rejectsOuterJoin() {
        var qed = RuleBuilder.create();
        var a = RuleChecks.table(qed, 1, false);
        var b = RuleChecks.table(qed, 1, false);
        var c = RuleChecks.table(qed, 1, false);
        var builder = RuleChecks.ruleBuilder(qed);
        var plan = builder.scan(a).scan(b).scan(c)
                .join(JoinRelType.INNER, builder.literal(true))
                .join(JoinRelType.LEFT, builder.literal(true)).build();
        RuleChecks.doesNotApply(new RightAssociateJoinsRight(), plan);
    }

    public static void main(String[] args) {
        System.out.println("Running RightAssociateJoinsRight test...");
        runTest();
    }
}
