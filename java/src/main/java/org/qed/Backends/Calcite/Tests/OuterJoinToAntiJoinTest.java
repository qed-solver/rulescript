package org.qed.Backends.Calcite.Tests;

import kala.collection.Seq;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RRule;
import org.qed.RRuleInstances.OuterJoinToAntiJoin;
import org.qed.RelRN;
import org.qed.RelType;
import org.qed.RexRN;
import org.qed.RuleBuilder;

public class OuterJoinToAntiJoinTest {
    private record ForcedTypeRef(RelType type, int ordinal) implements RexRN {
        @Override
        public org.apache.calcite.rex.RexNode semantics() {
            return RuleBuilder.create().getRexBuilder().makeInputRef(type, ordinal);
        }
    }

    public static void runTest() {
        var rule = new OuterJoinToAntiJoin();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(RRule rule) {
        var left = RelRN.scan("L2", new RelType.VarType("L2_T", true), false);
        var right = RelRN.scan("R2", new RelType.VarType("R2_T", false), false);
        var condition = left.joinPred("match", right);
        var joined = left.join(JoinRelType.LEFT, condition, right);
        var rightIsNull = new RexRN.Pred(SqlStdOperatorTable.IS_NULL,
                Seq.of(new ForcedTypeRef(new RelType.VarType("R2_T", true), 1)));
        var before = joined.filter(rightIsNull).project(Seq.of(left.field(0)));
        var after = left.join(JoinRelType.ANTI, condition, right);

        RuleChecks.rewrites(rule, FaithfulPlans.of(before), FaithfulPlans.expected(after));

        var wrongColumn = new RexRN.Pred(SqlStdOperatorTable.IS_NULL,
                Seq.of(new ForcedTypeRef(new RelType.VarType("L2_T", true), 0)));
        var nearMiss = joined.filter(wrongColumn).project(Seq.of(left.field(0)));
        RuleChecks.doesNotApply(rule, FaithfulPlans.of(nearMiss));
        if (CalciteTester.failures() > 0) {
            throw new IllegalStateException("OuterJoinToAntiJoin test failed");
        }
    }

    public static void main(String[] args) {
        System.out.println("Running RULE test...");
        runTest();
    }
}
