package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.tools.RelBuilder;
import org.qed.RRuleInstances.ProjectFilterTranspose;
import org.qed.RuleBuilder;

import java.util.List;

public class ProjectFilterTransposeTest {

    public static void runTest() {
        var rule = new ProjectFilterTranspose();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(ProjectFilterTranspose rule) {
        var base = RuleBuilder.create();
        var s = RuleChecks.table(base, 2, false);
        var b = RuleChecks.ruleBuilder(base);
        var filtered = (java.util.function.Function<RexNode, RelBuilder>) cond -> {
            b.scan(s);
            return b.filter(cond);
        };
        b.scan(s);
        var e = b.call(SqlStdOperatorTable.PLUS, b.field(0), b.field(1));
        var f = b.call(SqlStdOperatorTable.MULTIPLY, b.field(0), b.literal(2));
        var before = filtered.apply(b.call(SqlStdOperatorTable.LESS_THAN, e, b.field(1))).project(e, f).build();

        b.scan(s);
        e = b.call(SqlStdOperatorTable.PLUS, b.field(0), b.field(1));
        f = b.call(SqlStdOperatorTable.MULTIPLY, b.field(0), b.literal(2));
        var after = b.project(b.field(0), b.field(1), e, f)
                .filter(b.call(SqlStdOperatorTable.LESS_THAN, b.field(2), b.field(1)))
                .project(b.field(2), b.field(3)).build();
        RuleChecks.rewrites(rule, before, after);

        b.scan(s);
        e = b.call(SqlStdOperatorTable.PLUS, b.field(0), b.field(1));
        f = b.call(SqlStdOperatorTable.MULTIPLY, b.field(0), b.literal(2));
        RuleChecks.doesNotApply(rule, filtered.apply(RuleChecks.gt(b, b.field(0), 5)).project(e, f).build());
        b.scan(s);
        e = b.call(SqlStdOperatorTable.PLUS, b.field(0), b.field(1));
        RuleChecks.doesNotApply(rule, filtered.apply(b.call(SqlStdOperatorTable.LESS_THAN, e, b.field(1)))
                .project(e).build());
    }

    public static void main(String[] args) {
        System.out.println("Running ProjectFilterTranspose test...");
        runTest();
    }
}
