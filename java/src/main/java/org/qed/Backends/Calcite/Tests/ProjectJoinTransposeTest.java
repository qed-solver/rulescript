package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rel.logical.LogicalJoin;
import org.apache.calcite.rel.logical.LogicalProject;
import org.apache.calcite.sql.type.SqlTypeName;
import org.qed.RRuleInstances.ProjectJoinTranspose;
import org.qed.RelType;
import org.qed.RuleBuilder;

import java.util.List;
import java.util.Set;

public class ProjectJoinTransposeTest {
    public static void runTest() {
        var rule = new ProjectJoinTranspose();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(ProjectJoinTranspose rule) {
        var qed = RuleBuilder.create();
        var leftTable = RuleChecks.table(qed, 1, false);
        var rightTable = RuleChecks.table(qed, 1, false);
        var integer = new RelType.BaseType(SqlTypeName.INTEGER, true);
        var tl = qed.genericProjectionOp("TL", integer);
        var tr = qed.genericProjectionOp("TR", integer);
        var g = qed.genericProjectionOp("G", integer);
        var c = qed.genericPredicateOp("C", true);
        var b = RuleChecks.ruleBuilder(qed);
        var rex = b.getRexBuilder();
        var left = b.scan(leftTable).build();
        var right = b.scan(rightTable).build();

        var tlOfLeft = rex.makeCall(tl, rex.makeInputRef(left, 0));
        var trOfRight = rex.makeCall(tr, rex.makeInputRef(right.getRowType().getFieldList().get(0).getType(), 1));
        var join = LogicalJoin.create(left, right, List.of(), rex.makeCall(c, tlOfLeft, trOfRight), Set.of(),
                JoinRelType.INNER);
        var before = LogicalProject.create(join, List.of(), List.of(rex.makeCall(g, tlOfLeft, trOfRight)),
                List.of("g"));

        var projectedLeft = LogicalProject.create(left, List.of(), List.of(rex.makeCall(tl, rex.makeInputRef(left, 0))),
                List.of("tl"));
        var projectedRight = LogicalProject.create(right, List.of(),
                List.of(rex.makeCall(tr, rex.makeInputRef(right, 0))), List.of("tr"));
        var l0 = rex.makeInputRef(projectedLeft.getRowType().getFieldList().get(0).getType(), 0);
        var r0 = rex.makeInputRef(projectedRight.getRowType().getFieldList().get(0).getType(), 1);
        var transposed = LogicalJoin.create(projectedLeft, projectedRight, List.of(), rex.makeCall(c, l0, r0),
                Set.of(), JoinRelType.INNER);
        var after = LogicalProject.create(transposed, List.of(), List.of(rex.makeCall(g, l0, r0)), List.of("g"));
        RuleChecks.rewrites(rule, before, after);

        var leftJoin = LogicalJoin.create(left, right, List.of(), rex.makeCall(c, tlOfLeft, trOfRight), Set.of(),
                JoinRelType.LEFT);
        RuleChecks.doesNotApply(rule, LogicalProject.create(leftJoin, List.of(),
                List.of(rex.makeCall(g, tlOfLeft, trOfRight)), List.of("g")));
    }

    public static void main(String[] args) {
        System.out.println("Running ProjectJoinTranspose test...");
        runTest();
    }
}
