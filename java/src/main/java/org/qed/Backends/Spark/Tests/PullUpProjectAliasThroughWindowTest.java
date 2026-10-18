package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Alias;
import org.apache.spark.sql.catalyst.expressions.Alias$;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.Coalesce;
import org.apache.spark.sql.catalyst.expressions.EqualTo;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.Literal$;
import org.apache.spark.sql.catalyst.expressions.NamedExpression;
import org.apache.spark.sql.catalyst.expressions.NamedExpression$;
import org.apache.spark.sql.catalyst.expressions.aggregate.AggregateExpression$;
import org.apache.spark.sql.catalyst.expressions.aggregate.Complete$;
import org.apache.spark.sql.catalyst.expressions.aggregate.Max;
import org.apache.spark.sql.catalyst.plans.Inner$;
import org.apache.spark.sql.catalyst.plans.LeftOuter$;
import org.apache.spark.sql.catalyst.plans.JoinType;
import org.apache.spark.sql.catalyst.plans.logical.Aggregate;
import org.apache.spark.sql.catalyst.plans.logical.JoinHint$;
import org.apache.spark.sql.catalyst.plans.logical.Join;
import org.apache.spark.sql.catalyst.plans.logical.LogicalPlan;
import org.apache.spark.sql.catalyst.plans.logical.Project;
import org.apache.spark.sql.types.Metadata;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import scala.Option;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PullUpProjectAliasThroughWindowTest {
    private final RuleScriptRule rule = RuleScriptRule.load(
            Support.generated().resolve("PullUpProjectAliasThroughWindow.json"));

    private static Alias alias(Expression child, String name) {
        return Alias$.MODULE$.apply(child, name, NamedExpression$.MODULE$.newExprId(),
                Catalyst.<String>seq(List.of()), Option.<Metadata>empty(), Catalyst.<String>seq(List.of()));
    }

    private static Alias windowAggregate(Expression column, String name) {
        var function = AggregateExpression$.MODULE$.apply(new Max(column), Complete$.MODULE$, false,
                Option.<Expression>empty(), NamedExpression$.MODULE$.newExprId());
        return alias(function, name);
    }

    private static Expression plusOne(Expression value) {
        return new Coalesce(Catalyst.<Expression>seq(List.of(value, Literal$.MODULE$.apply(1))));
    }

    private static List<Attribute> attributes(LogicalPlan plan) {
        return Catalyst.<Attribute>list(plan.output());
    }

    private static List<String> names(LogicalPlan plan) {
        var out = new ArrayList<String>();
        attributes(plan).forEach(a -> out.add(a.name()));
        return out;
    }

    private static LogicalPlan windowShape(LogicalPlan base, JoinType type) {
        var baseAttrs = attributes(base);
        var k = baseAttrs.get(0);
        var v = baseAttrs.get(1);
        var lower = new Project(Catalyst.<NamedExpression>seq(List.of(k, v, alias(plusOne(v), "x"))), base);
        var lowerAttrs = attributes(lower);
        var lk = lowerAttrs.get(0);
        var lv = lowerAttrs.get(1);
        var agg = new Aggregate(Catalyst.<Expression>seq(List.of(lk)),
                Catalyst.<NamedExpression>seq(List.of(alias(lk, "k"), windowAggregate(lv, "w"))), lower,
                Option.empty());
        var aggAttrs = attributes(agg);
        var condition = new EqualTo(lk, aggAttrs.get(0));
        var join = new Join(lower, agg, type, Option.<Expression>apply(condition), JoinHint$.MODULE$.NONE());
        var joinAttrs = attributes(join);
        var window = new Project(Catalyst.<NamedExpression>seq(List.of(
                joinAttrs.get(0), joinAttrs.get(1), joinAttrs.get(2), joinAttrs.get(4))), join);
        var windowAttrs = attributes(window);
        return new Project(Catalyst.<NamedExpression>seq(List.of(
                windowAttrs.get(0), windowAttrs.get(1), windowAttrs.get(2), windowAttrs.get(3))), window);
    }

    @Test
    void pullsTheAliasAboveTheWindow() {
        var base = Support.relation("Base", 2, List.of(0), new Random(1));
        var plan = windowShape(base, Inner$.MODULE$);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertEquals(names(plan), names(rewritten));
        assertTrue(rewritten instanceof Project top && top.projectList().size() == 4
                && top.child() instanceof Project mid && mid.projectList().size() == 3
                && mid.child() instanceof Join);
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesALeftOuterWindowAlone() {
        var base = Support.relation("Base", 2, List.of(0), new Random(2));
        var plan = windowShape(base, LeftOuter$.MODULE$);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesANullablePartitionKeyAlone() {
        var base = Support.relation("Base", 2, List.of(), new Random(3));
        var plan = windowShape(base, Inner$.MODULE$);
        assertEquals(plan, rule.apply(plan));
    }
}
