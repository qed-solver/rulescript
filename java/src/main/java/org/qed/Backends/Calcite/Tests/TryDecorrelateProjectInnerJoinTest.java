package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rel.logical.LogicalJoin;
import org.apache.calcite.rel.logical.LogicalProject;
import org.apache.calcite.sql.type.SqlTypeName;
import org.qed.RRuleInstances.TryDecorrelateProjectInnerJoin;
import org.qed.RelType;
import org.qed.RuleBuilder;

import java.util.List;
import java.util.Set;

public class TryDecorrelateProjectInnerJoinTest {
    public static void runTest() {
        var rule = new TryDecorrelateProjectInnerJoin();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(TryDecorrelateProjectInnerJoin rule) {
        var qed = RuleBuilder.create();
        var outer = RuleChecks.table(qed, 1, false);
        var innerLeft = RuleChecks.table(qed, 1, false);
        var innerRight = RuleChecks.table(qed, 1, false);
        var on = qed.genericPredicateOp("on", true);
        var innerOn = qed.genericPredicateOp("innerOn", true);
        var f = qed.genericProjectionOp("f", new RelType.BaseType(SqlTypeName.INTEGER, true));
        var b = RuleChecks.ruleBuilder(qed);
        var rex = b.getRexBuilder();
        var l = b.scan(outer).build();
        var il = b.scan(innerLeft).build();
        var ir = b.scan(innerRight).build();
        var type = l.getRowType().getFieldList().get(0).getType();
        var in0 = rex.makeInputRef(type, 0);
        var in1 = rex.makeInputRef(type, 1);

        var inner = LogicalJoin.create(il, ir, List.of(), rex.makeCall(innerOn, in0, in1), Set.of(),
                JoinRelType.INNER);
        var right = LogicalProject.create(inner, List.of(),
                List.of(in0, rex.makeCall(f, in0, in1)), List.of("a", "f"));
        var rightType = right.getRowType().getFieldList();
        var before = LogicalJoin.create(l, right, List.of(),
                rex.makeCall(on, rex.makeInputRef(type, 0), rex.makeInputRef(rightType.get(0).getType(), 1),
                        rex.makeInputRef(rightType.get(1).getType(), 2)),
                Set.of(), JoinRelType.LEFT);

        var cross = LogicalJoin.create(il, ir, List.of(), rex.makeLiteral(true), Set.of(), JoinRelType.INNER);
        var widened = LogicalProject.create(cross, List.of(),
                List.of(in0, in1, rex.makeCall(f, in0, in1)), List.of("a", "b", "f"));
        var widenedType = widened.getRowType().getFieldList();
        var condition = rex.makeCall(org.apache.calcite.sql.fun.SqlStdOperatorTable.AND,
                rex.makeCall(on, rex.makeInputRef(type, 0), rex.makeInputRef(widenedType.get(0).getType(), 1),
                        rex.makeInputRef(widenedType.get(2).getType(), 3)),
                rex.makeCall(innerOn, rex.makeInputRef(widenedType.get(0).getType(), 1),
                        rex.makeInputRef(widenedType.get(1).getType(), 2)));
        var joined = LogicalJoin.create(l, widened, List.of(), condition, Set.of(), JoinRelType.LEFT);
        var joinedType = joined.getRowType().getFieldList();
        var after = LogicalProject.create(joined, List.of(), List.of(rex.makeInputRef(joinedType.get(0).getType(), 0),
                rex.makeInputRef(joinedType.get(1).getType(), 1), rex.makeInputRef(joinedType.get(3).getType(), 3)),
                List.of("l", "a", "f"));
        RuleChecks.rewrites(rule, before, after);

        var innerOuter = LogicalJoin.create(l, right, List.of(), before.getCondition(), Set.of(), JoinRelType.INNER);
        RuleChecks.doesNotApply(rule, innerOuter);
    }

    public static void main(String[] args) {
        System.out.println("Running TryDecorrelateProjectInnerJoin test...");
        runTest();
    }
}
