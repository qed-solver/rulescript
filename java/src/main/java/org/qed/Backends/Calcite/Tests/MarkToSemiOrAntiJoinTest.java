package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rel.logical.LogicalFilter;
import org.apache.calcite.rel.logical.LogicalUnion;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRuleInstances.MarkToSemiOrAntiJoin;
import org.qed.RuleBuilder;

import java.util.List;

public class MarkToSemiOrAntiJoinTest {

    public static void runTest() {
        var rule = new MarkToSemiOrAntiJoin();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(MarkToSemiOrAntiJoin rule) {
        var base = RuleBuilder.create();
        var l = RuleChecks.table(base, 1, false);
        var r = RuleChecks.table(base, 1, false);
        var b = RuleChecks.ruleBuilder(base);
        var cond = (java.util.function.Supplier<RexNode>) () -> RuleChecks.eq(b, b.field(2, 0, 0), b.field(2, 1, 0));
        var semi = (java.util.function.Supplier<RelNode>) () -> b.scan(l).scan(r).join(JoinRelType.SEMI, cond.get()).build();
        var anti = (java.util.function.Supplier<RelNode>) () -> b.scan(l).scan(r).join(JoinRelType.ANTI, cond.get()).build();
        var marked = LogicalUnion.create(List.of(
                b.push(semi.get()).project(b.field(0), b.literal(true)).build(),
                b.push(anti.get()).project(b.field(0), b.literal(false)).build()), true);
        var filter = b.push(marked).and(b.field(1), RuleChecks.gt(b, b.field(0), 0));
        var before = b.push(LogicalFilter.create(marked, filter))
                .project(b.call(SqlStdOperatorTable.UNARY_MINUS, b.field(0))).build();
        var after = b.push(semi.get()).filter(RuleChecks.gt(b, b.field(0), 0))
                .project(b.call(SqlStdOperatorTable.UNARY_MINUS, b.field(0))).build();
        RuleChecks.rewrites(rule, before, after);

        var unmarked = b.push(LogicalFilter.create(marked, RuleChecks.gt(b.push(marked), b.field(0), 0)))
                .project(b.call(SqlStdOperatorTable.UNARY_MINUS, b.field(0))).build();
        RuleChecks.doesNotApply(rule, unmarked);
    }

    public static void main(String[] args) {
        System.out.println("Running MarkToSemiOrAntiJoin test...");
        runTest();
    }
}
