package org.qed.Backends.Calcite.Tests;

import kala.collection.Seq;
import org.apache.calcite.rel.logical.LogicalFilter;
import org.apache.calcite.rel.logical.LogicalUnion;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.Backends.Calcite.CalciteRuntime;
import org.qed.RRuleInstances.InlineAnyValuesSingleCol;
import org.qed.RuleBuilder;

public class InlineAnyValuesSingleColTest {
    public static void runTest() {
        var rule = new InlineAnyValuesSingleCol();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);

        var builder = RuleBuilder.create();
        var source = builder.addTable(new org.qed.QedTable("S", Seq.of("c0"),
                Seq.of(org.qed.RelType.fromString("INTEGER", true)), kala.collection.Set.empty(),
                kala.collection.Set.empty())).scan("S").build();
        var beforeBuilder = builder.transform(CalciteRuntime::configure);
        var first = beforeBuilder.values(new String[]{"c0"}, 1).build();
        var second = beforeBuilder.values(new String[]{"c0"}, 3).build();
        var values = LogicalUnion.create(java.util.List.of(first, second), true);
        var before = beforeBuilder.push(source).push(values)
                .join(org.apache.calcite.rel.core.JoinRelType.SEMI,
                        beforeBuilder.call(SqlStdOperatorTable.EQUALS,
                                beforeBuilder.field(2, 0, 0), beforeBuilder.field(2, 1, 0)))
                .build();

        var expectedBuilder = builder.transform(CalciteRuntime::configure).push(source);
        var expected = LogicalFilter.create(source, expectedBuilder.call(SqlStdOperatorTable.OR,
                expectedBuilder.call(SqlStdOperatorTable.EQUALS, expectedBuilder.field(0), expectedBuilder.literal(1)),
                expectedBuilder.call(SqlStdOperatorTable.EQUALS, expectedBuilder.field(0), expectedBuilder.literal(3))));
        RuleChecks.rewrites(rule, before, expected);

        var nearBuilder = builder.transform(CalciteRuntime::configure);
        var nearFirst = nearBuilder.values(new String[]{"c0"}, 1).build();
        var nearSecond = nearBuilder.values(new String[]{"c0"}, 4).build();
        var nearValues = LogicalUnion.create(java.util.List.of(nearFirst, nearSecond), true);
        var near = nearBuilder.push(source).push(nearValues)
                .join(org.apache.calcite.rel.core.JoinRelType.SEMI,
                        nearBuilder.call(SqlStdOperatorTable.EQUALS,
                                nearBuilder.field(2, 0, 0), nearBuilder.field(2, 1, 0)))
                .build();
        RuleChecks.doesNotApply(rule, near);
    }

    public static void main(String[] args) {
        System.out.println("Running InlineAnyValuesSingleCol test...");
        runTest();
    }
}
