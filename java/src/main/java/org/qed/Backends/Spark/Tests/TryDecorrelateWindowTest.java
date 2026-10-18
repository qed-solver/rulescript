package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Alias$;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.EqualTo;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.NamedExpression;
import org.apache.spark.sql.catalyst.expressions.NamedExpression$;
import org.apache.spark.sql.catalyst.expressions.aggregate.AggregateExpression$;
import org.apache.spark.sql.catalyst.expressions.aggregate.Complete$;
import org.apache.spark.sql.catalyst.expressions.aggregate.Max;
import org.apache.spark.sql.catalyst.plans.Inner$;
import org.apache.spark.sql.catalyst.plans.JoinType;
import org.apache.spark.sql.catalyst.plans.LeftOuter$;
import org.apache.spark.sql.catalyst.plans.logical.Aggregate;
import org.apache.spark.sql.catalyst.plans.logical.AggregateHint;
import org.apache.spark.sql.catalyst.plans.logical.Filter;
import org.apache.spark.sql.catalyst.plans.logical.Join;
import org.apache.spark.sql.catalyst.plans.logical.JoinHint$;
import org.apache.spark.sql.catalyst.plans.logical.LogicalPlan;
import org.apache.spark.sql.catalyst.plans.logical.Project;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import scala.Option;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TryDecorrelateWindowTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("TryDecorrelateWindow.json"));

    private static Attribute first(LogicalPlan plan) {
        return Catalyst.<Attribute>list(plan.output()).get(0);
    }

    private static List<String> names(LogicalPlan plan) {
        return Catalyst.<Attribute>list(plan.output()).stream().map(Attribute::name).toList();
    }

    private static boolean has(LogicalPlan plan, Class<?> type) {
        if (type.isInstance(plan)) {
            return true;
        }
        return Catalyst.list(plan.children()).stream().anyMatch(child -> has(child, type));
    }

    private static LogicalPlan distinct(LogicalPlan plan) {
        var key = first(plan);
        return new Aggregate(Catalyst.<Expression>seq(List.of(key)), Catalyst.<NamedExpression>seq(List.of(key)), plan,
                Option.<AggregateHint>empty());
    }

    private static Aggregate wholeInputMax(LogicalPlan input) {
        var max = AggregateExpression$.MODULE$.apply(new Max(first(input)), Complete$.MODULE$, false,
                Option.<Expression>empty(), NamedExpression$.MODULE$.newExprId());
        var w = Alias$.MODULE$.apply((Expression) max, "w", NamedExpression$.MODULE$.newExprId(),
                Catalyst.<String>seq(List.of()), Option.empty(), Catalyst.<String>seq(List.of()));
        return new Aggregate(Catalyst.<Expression>seq(List.of()), Catalyst.<NamedExpression>seq(List.of(w)), input,
                Option.<AggregateHint>empty());
    }

    private static LogicalPlan before(LogicalPlan left, LogicalPlan input, JoinType type) {
        var window = new Join(input, wholeInputMax(input), Inner$.MODULE$, Option.<Expression>empty(),
                JoinHint$.MODULE$.NONE());
        var on = new EqualTo(first(left), first(input));
        return new Join(left, window, type, Option.<Expression>apply(on), JoinHint$.MODULE$.NONE());
    }

    @Test
    void decorrelatesWholeInputWindowOverDistinctLeft() {
        var left = distinct(Support.relation("l", 1, List.of(0), new Random(3)));
        var input = Support.relation("input", 1, List.of(0), new Random(7));
        var plan = before(left, input, Inner$.MODULE$);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertInstanceOf(Project.class, rewritten);
        assertEquals(List.of("l_0", "input_0", "w"), names(rewritten));
        assertTrue(has(rewritten, Filter.class));
        assertTrue(has(rewritten, Aggregate.class));
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesLeftOuterWindowJoinAlone() {
        var left = distinct(Support.relation("l", 1, List.of(0), new Random(3)));
        var input = Support.relation("input", 1, List.of(0), new Random(7));
        var plan = before(left, input, LeftOuter$.MODULE$);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesNullableLeftAlone() {
        var left = distinct(Support.relation("l", 1, List.of(), new Random(3)));
        var input = Support.relation("input", 1, List.of(0), new Random(7));
        var plan = before(left, input, Inner$.MODULE$);
        assertEquals(plan, rule.apply(plan));
    }
}
