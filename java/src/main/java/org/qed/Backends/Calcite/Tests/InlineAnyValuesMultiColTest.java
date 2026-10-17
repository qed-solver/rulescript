package org.qed.Backends.Calcite.Tests;

import kala.collection.Seq;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRule;
import org.qed.RRuleInstances.InlineAnyValuesMultiCol;
import org.qed.RelRN;
import org.qed.RexRN;
import org.qed.RuleBuilder;

public class InlineAnyValuesMultiColTest {
    private record Values(Object[] row) implements RelRN {
        @Override
        public RelNode semantics() {
            var fields = new String[row.length];
            for (var i = 0; i < row.length; i++) fields[i] = "c" + i;
            return RuleBuilder.create().values(fields, row).build();
        }
    }

    private record IntLiteral(int value) implements RexRN {
        @Override
        public RexNode semantics() {
            return RuleBuilder.create().literal(value);
        }
    }

    public static void runTest() {
        var rule = new InlineAnyValuesMultiCol();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        rewritesHandBuiltPlan(rule);
        rejectsSwappedValueColumns(rule);
    }

    private static void rewritesHandBuiltPlan(RRule rule) {
        var source = RelRN.scanMany("Input", Seq.of(RexRN.varType("T", true), RexRN.varType("T", true)));
        var values = new Values(new Object[]{1, 2}).union(true, new Values(new Object[]{3, 4}));
        var condition = RexRN.and(
                new RexRN.Pred(SqlStdOperatorTable.EQUALS,
                        Seq.of(source.joinField(0, values), source.joinField(2, values))),
                new RexRN.Pred(SqlStdOperatorTable.EQUALS,
                        Seq.of(source.joinField(1, values), source.joinField(3, values))));
        var before = source.join(JoinRelType.SEMI, condition, values);
        var expected = source.filter(new RexRN.Or(Seq.of(
                RexRN.and(
                        new RexRN.Pred(SqlStdOperatorTable.EQUALS, Seq.of(source.field(0), new IntLiteral(1))),
                        new RexRN.Pred(SqlStdOperatorTable.EQUALS, Seq.of(source.field(1), new IntLiteral(2)))),
                RexRN.and(
                        new RexRN.Pred(SqlStdOperatorTable.EQUALS, Seq.of(source.field(0), new IntLiteral(3))),
                        new RexRN.Pred(SqlStdOperatorTable.EQUALS, Seq.of(source.field(1), new IntLiteral(4)))))));
        RuleChecks.rewrites(rule, FaithfulPlans.of(before), FaithfulPlans.expected(expected));
    }

    private static void rejectsSwappedValueColumns(RRule rule) {
        var source = RelRN.scanMany("Input", Seq.of(RexRN.varType("T", true), RexRN.varType("T", true)));
        var values = new Values(new Object[]{2, 1}).union(true, new Values(new Object[]{4, 3}));
        var condition = RexRN.and(
                new RexRN.Pred(SqlStdOperatorTable.EQUALS,
                        Seq.of(source.joinField(0, values), source.joinField(2, values))),
                new RexRN.Pred(SqlStdOperatorTable.EQUALS,
                        Seq.of(source.joinField(1, values), source.joinField(3, values))));
        RuleChecks.doesNotApply(rule, FaithfulPlans.of(source.join(JoinRelType.SEMI, condition, values)));
    }

    public static void main(String[] args) {
        System.out.println("Running InlineAnyValuesMultiCol test...");
        runTest();
    }
}
