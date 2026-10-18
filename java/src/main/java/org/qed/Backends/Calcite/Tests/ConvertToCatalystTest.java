package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRuleInstances.ConvertToCatalyst;
import org.qed.RuleBuilder;

import java.util.List;

import static org.qed.Backends.Calcite.Tests.RuleChecks.doesNotApply;
import static org.qed.Backends.Calcite.Tests.RuleChecks.rejectsNearMisses;
import static org.qed.Backends.Calcite.Tests.RuleChecks.rewrites;
import static org.qed.Backends.Calcite.Tests.RuleChecks.rewritesConcretePredicates;
import static org.qed.Backends.Calcite.Tests.RuleChecks.rewritesPattern;
import static org.qed.Backends.Calcite.Tests.RuleChecks.ruleBuilder;
import static org.qed.Backends.Calcite.Tests.RuleChecks.table;

public final class ConvertToCatalystTest {
    public static void runTest() {
        var rule = new ConvertToCatalyst();
        rewritesPattern(rule);
        rewritesConcretePredicates(rule);
        rejectsNearMisses(rule);

        var builder = RuleBuilder.create();
        var name = table(builder, 1, false);
        var b = ruleBuilder(builder);
        b.scan(name);
        var x = b.field(0);
        var e = b.call(SqlStdOperatorTable.PLUS, x, b.literal(1));
        var f = b.call(SqlStdOperatorTable.EQUALS, e, e);
        var before = b.filter(f).build();
        var expectedSource = ruleBuilder(builder).scan(name).build();
        var expectedChild = ruleBuilder(builder).push(expectedSource).project(List.of(x, e), List.of("c0", "e"), true).build();
        var expectedBuilder = ruleBuilder(RuleBuilder.create());
        var expected = expectedBuilder.push(expectedChild)
                .filter(expectedBuilder.call(SqlStdOperatorTable.EQUALS,
                        expectedBuilder.field(1), expectedBuilder.field(1)))
                .project(List.of(expectedBuilder.field(0)), List.of("c0"), true)
                .build();
        rewrites(rule, before, expected);

        var nearBuilder = ruleBuilder(builder);
        nearBuilder.scan(name);
        var first = nearBuilder.call(SqlStdOperatorTable.PLUS, nearBuilder.field(0), nearBuilder.literal(1));
        var second = nearBuilder.call(SqlStdOperatorTable.PLUS, nearBuilder.field(0), nearBuilder.literal(2));
        var nearMiss = nearBuilder.filter(nearBuilder.call(SqlStdOperatorTable.EQUALS, first, second)).build();
        doesNotApply(rule, nearMiss);
    }

    public static void main(String[] args) {
        System.out.println("Running ConvertToCatalyst test...");
        runTest();
    }
}
