package org.qed.Backends.Calcite.Tests;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rel.logical.LogicalJoin;
import org.apache.calcite.rex.RexSubQuery;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRuleInstances.NormalizeJoinNotAnyFilter;
import org.qed.RuleBuilder;

import java.util.List;

public class NormalizeJoinNotAnyFilterTest {

    public static void runTest() {
        var rule = new NormalizeJoinNotAnyFilter();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(NormalizeJoinNotAnyFilter rule) {
        var base = RuleBuilder.create();
        var l = RuleChecks.table(base, 1, false);
        var r = RuleChecks.table(base, 1, false);
        var v = RuleChecks.table(base, 1, false);
        var b = RuleChecks.ruleBuilder(base);
        var left = b.scan(l).build();
        var right = b.scan(r).build();
        var sub = b.scan(v).build();
        var membership = RexSubQuery.in(sub, ImmutableList.of(b.push(left).field(0)));
        var notIn = b.call(SqlStdOperatorTable.NOT, membership);
        var before = LogicalJoin.create(left, right, List.of(), notIn, ImmutableSet.of(), JoinRelType.INNER);
        var exists = RuleChecks.anyAsExists(b, before.getRowType(), sub, true);
        var after = LogicalJoin.create(left, right, List.of(), exists.condition(), ImmutableSet.of(exists.id()),
                JoinRelType.INNER);
        RuleChecks.rewrites(rule, before, after);

        RuleChecks.doesNotApply(rule, LogicalJoin.create(left, right, List.of(), membership, ImmutableSet.of(),
                JoinRelType.INNER));
        RuleChecks.doesNotApply(rule, LogicalJoin.create(left, right, List.of(), b.literal(true), ImmutableSet.of(),
                JoinRelType.INNER));
    }

    public static void main(String[] args) {
        System.out.println("Running NormalizeJoinNotAnyFilter test...");
        runTest();
    }
}
