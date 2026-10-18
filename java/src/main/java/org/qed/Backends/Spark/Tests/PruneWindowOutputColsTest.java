package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.EqualTo;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.NamedExpression;
import org.apache.spark.sql.catalyst.expressions.NamedExpression$;
import org.apache.spark.sql.catalyst.expressions.Alias$;
import org.apache.spark.sql.catalyst.expressions.aggregate.AggregateExpression$;
import org.apache.spark.sql.catalyst.expressions.aggregate.Complete$;
import org.apache.spark.sql.catalyst.expressions.aggregate.Max;
import org.apache.spark.sql.catalyst.expressions.aggregate.Min;
import org.apache.spark.sql.catalyst.plans.Inner$;
import org.apache.spark.sql.catalyst.plans.JoinType;
import org.apache.spark.sql.catalyst.plans.LeftOuter$;
import org.apache.spark.sql.catalyst.plans.logical.Aggregate;
import org.apache.spark.sql.catalyst.plans.logical.AggregateHint;
import org.apache.spark.sql.catalyst.plans.logical.Join;
import org.apache.spark.sql.catalyst.plans.logical.JoinHint$;
import org.apache.spark.sql.catalyst.plans.logical.LogicalPlan;
import org.apache.spark.sql.catalyst.plans.logical.Project;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import scala.Option;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class PruneWindowOutputColsTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("PruneWindowOutputCols.json"));

    @Test
    void prunesTheUnusedWindowFunction() {
        var input = Support.relation("Input", 2, List.of(0, 1), new Random(1));
        var window = window(input, 2);
        var join = join(input, window, Inner$.MODULE$);
        var plan = project(join, 0, 1, 3);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        var out = assertInstanceOf(Project.class, rewritten);
        var newJoin = assertInstanceOf(Join.class, out.child());
        var newWindow = assertInstanceOf(Aggregate.class, newJoin.right());
        assertEquals(2, Catalyst.list(newWindow.aggregateExpressions()).size());
        assertEquals(List.of("Input_0", "Input_1", "w1"), names(rewritten));
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesALeftOuterJoinAlone() {
        var input = Support.relation("Input", 2, List.of(0, 1), new Random(1));
        var window = window(input, 2);
        var join = join(input, window, LeftOuter$.MODULE$);
        var plan = project(join, 0, 1, 3);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesAWindowWithThreeFunctionsAlone() {
        var input = Support.relation("Input", 2, List.of(0, 1), new Random(1));
        var window = window(input, 3);
        var join = join(input, window, Inner$.MODULE$);
        var plan = project(join, 0, 1, 3);
        assertEquals(plan, rule.apply(plan));
    }

    private static Attribute column(LogicalPlan plan, int ordinal) {
        return Catalyst.<Attribute>list(plan.output()).get(ordinal);
    }

    private static List<String> names(LogicalPlan plan) {
        return Catalyst.<Attribute>list(plan.output()).stream().map(Attribute::name).toList();
    }

    private static NamedExpression alias(Expression value, String name) {
        return Alias$.MODULE$.apply(value, name, NamedExpression$.MODULE$.newExprId(),
                Catalyst.<String>seq(List.of()), Option.empty(), Catalyst.<String>seq(List.of()));
    }

    private static Expression aggregate(org.apache.spark.sql.catalyst.expressions.aggregate.AggregateFunction function) {
        return AggregateExpression$.MODULE$.apply(function, Complete$.MODULE$, false,
                Option.<Expression>empty(), NamedExpression$.MODULE$.newExprId());
    }

    private static Aggregate window(LogicalPlan input, int functions) {
        var key = column(input, 0);
        var value = column(input, 1);
        var items = new ArrayList<NamedExpression>();
        items.add(alias(key, "k"));
        for (int i = 1; i <= functions; i++) {
            var function = i % 2 == 1 ? new Max(value) : new Min(value);
            items.add(alias(aggregate(function), "w" + i));
        }
        return new Aggregate(Catalyst.seq(List.<Expression>of(key)), Catalyst.seq(items), input,
                Option.<AggregateHint>empty());
    }

    private static Join join(LogicalPlan input, Aggregate window, JoinType type) {
        var condition = new EqualTo(column(input, 0), column(window, 0));
        return new Join(input, window, type, Option.<Expression>apply(condition), JoinHint$.MODULE$.NONE());
    }

    private static Project project(LogicalPlan join, int... ordinals) {
        var items = new ArrayList<NamedExpression>();
        for (int ordinal : ordinals) {
            items.add(column(join, ordinal));
        }
        return new Project(Catalyst.seq(items), join);
    }
}
