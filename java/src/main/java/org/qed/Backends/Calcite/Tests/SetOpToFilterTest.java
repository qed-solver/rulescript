package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.tools.RelBuilder;
import org.qed.RRuleInstances.SetOpToFilter;
import org.qed.RuleBuilder;

import java.util.List;

public class SetOpToFilterTest {

    public static void runTest() {
        var rule = new SetOpToFilter();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(SetOpToFilter rule) {
        var base = RuleBuilder.create();
        var s = RuleChecks.table(base, 1, false);
        var other = RuleChecks.table(base, 1, false);
        var b = RuleChecks.ruleBuilder(base);
        var union = (java.util.function.BiFunction<String, Boolean, RelBuilder>) (second, all) ->
                b.scan(s).filter(RuleChecks.gt(b, b.field(0), 5))
                        .scan(second).filter(b.call(SqlStdOperatorTable.LESS_THAN, b.field(0), b.literal(2)))
                        .union(all, 2);
        var before = union.apply(s, false).build();
        var after = b.scan(s).filter(b.or(RuleChecks.gt(b, b.field(0), 5),
                b.call(SqlStdOperatorTable.LESS_THAN, b.field(0), b.literal(2)))).aggregate(b.groupKey(0)).build();
        RuleChecks.rewrites(rule, before, after);

        RuleChecks.doesNotApply(rule, union.apply(s, true).build());
        RuleChecks.doesNotApply(rule, union.apply(other, false).build());
    }

    public static void main(String[] args) {
        System.out.println("Running SetOpToFilter test...");
        runTest();
    }
}
