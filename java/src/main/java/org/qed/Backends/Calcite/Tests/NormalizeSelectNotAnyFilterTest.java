package org.qed.Backends.Calcite.Tests;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.apache.calcite.rel.logical.LogicalFilter;
import org.apache.calcite.rex.RexSubQuery;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRuleInstances.NormalizeSelectNotAnyFilter;
import org.qed.RuleBuilder;

public class NormalizeSelectNotAnyFilterTest {

    public static void runTest() {
        var rule = new NormalizeSelectNotAnyFilter();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(NormalizeSelectNotAnyFilter rule) {
        var base = RuleBuilder.create();
        var s = RuleChecks.table(base, 1, false);
        var v = RuleChecks.table(base, 1, false);
        var b = RuleChecks.ruleBuilder(base);
        var outer = b.scan(s).build();
        var sub = b.scan(v).build();
        var membership = RexSubQuery.in(sub, ImmutableList.of(b.push(outer).field(0)));
        var before = LogicalFilter.create(b.build(), b.call(SqlStdOperatorTable.NOT, membership));
        var exists = RuleChecks.anyAsExists(b, outer.getRowType(), sub, true);
        var after = LogicalFilter.create(outer, exists.condition(), ImmutableSet.of(exists.id()));
        RuleChecks.rewrites(rule, before, after);

        RuleChecks.doesNotApply(rule, LogicalFilter.create(outer, RexSubQuery.in(sub, ImmutableList.of(b.push(outer).field(0)))));
        RuleChecks.doesNotApply(rule, b.push(outer).filter(RuleChecks.gt(b, b.field(0), 0)).build());
    }

    public static void main(String[] args) {
        System.out.println("Running NormalizeSelectNotAnyFilter test...");
        runTest();
    }
}
