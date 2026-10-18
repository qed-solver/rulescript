package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Add$;
import org.apache.spark.sql.catalyst.expressions.Alias$;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.EqualTo;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.NamedExpression;
import org.apache.spark.sql.catalyst.expressions.NamedExpression$;
import org.apache.spark.sql.catalyst.expressions.aggregate.AggregateExpression$;
import org.apache.spark.sql.catalyst.expressions.aggregate.AggregateFunction;
import org.apache.spark.sql.catalyst.expressions.aggregate.Complete$;
import org.apache.spark.sql.catalyst.expressions.aggregate.Max;
import org.apache.spark.sql.catalyst.expressions.aggregate.Min;
import org.apache.spark.sql.catalyst.plans.Inner$;
import org.apache.spark.sql.catalyst.plans.JoinType;
import org.apache.spark.sql.catalyst.plans.LeftOuter$;
import org.apache.spark.sql.catalyst.plans.logical.Aggregate;
import org.apache.spark.sql.catalyst.plans.logical.Join;
import org.apache.spark.sql.catalyst.plans.logical.JoinHint$;
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

class ProjectWindowTransposeTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("ProjectWindowTranspose.json"));

    @Test
    void prunesPassthroughColumnBelowWindowJoin() {
        var base = Support.relation("b", 3, List.of(0), new Random(7));
        var wide = join(base, partitions(base), Inner$.MODULE$);
        var plan = new Project(Catalyst.<NamedExpression>seq(List.of(top(wide, 0, 1, 4, 5))), wide);
        var narrow = join(project(base, 0, 1), partitions(base), Inner$.MODULE$);
        var expected = new Project(Catalyst.<NamedExpression>seq(List.of(top(narrow, 0, 1, 3, 4))), narrow);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Project top && top.child() instanceof Join j
                && j.left() instanceof Project left && left.child() == base
                && j.right() instanceof Aggregate && top.output().size() == 1);
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(expected));
        assertEquals(Support.run(expected), Support.run(rewritten));
    }

    @Test
    void leavesOuterWindowJoinAlone() {
        var base = Support.relation("b", 3, List.of(0), new Random(7));
        var wide = join(base, partitions(base), LeftOuter$.MODULE$);
        var plan = new Project(Catalyst.<NamedExpression>seq(List.of(top(wide, 0, 1, 4, 5))), wide);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesProjectWithExtraColumnAlone() {
        var base = Support.relation("b", 3, List.of(0), new Random(7));
        var wide = join(base, partitions(base), Inner$.MODULE$);
        var plan = new Project(Catalyst.<NamedExpression>seq(List.of(top(wide, 0, 1, 4, 5), col(wide, 2))), wide);
        assertEquals(plan, rule.apply(plan));
    }

    static Attribute col(LogicalPlan plan, int i) {
        return Catalyst.<Attribute>list(plan.output()).get(i);
    }

    static NamedExpression top(LogicalPlan plan, int a, int b, int c, int d) {
        var ab = Add$.MODULE$.apply(col(plan, a), col(plan, b), Add$.MODULE$.apply$default$3());
        var cd = Add$.MODULE$.apply(col(plan, c), col(plan, d), Add$.MODULE$.apply$default$3());
        return named("t", Add$.MODULE$.apply(ab, cd, Add$.MODULE$.apply$default$3()));
    }

    static LogicalPlan project(LogicalPlan child, int... ordinals) {
        var items = new ArrayList<NamedExpression>();
        for (int i : ordinals) {
            items.add(col(child, i));
        }
        return new Project(Catalyst.seq(items), child);
    }

    static Join join(LogicalPlan left, LogicalPlan right, JoinType type) {
        var cond = new EqualTo(col(left, 0), col(right, 0));
        return new Join(left, right, type, Option.<Expression>apply(cond), JoinHint$.MODULE$.NONE());
    }

    static LogicalPlan partitions(LogicalPlan base) {
        return perPartition(project(base, 0, 1));
    }

    static LogicalPlan perPartition(LogicalPlan input) {
        var k = col(input, 0);
        var v = col(input, 1);
        var items = Catalyst.<NamedExpression>seq(List.of(
                named("k", k), aliased("f", new Max(v)), aliased("g", new Min(v))));
        return new Aggregate(Catalyst.<Expression>seq(List.of(k)), items, input,
                Option.<org.apache.spark.sql.catalyst.plans.logical.AggregateHint>empty());
    }

    static NamedExpression aliased(String name, AggregateFunction function) {
        var aggregate = AggregateExpression$.MODULE$.apply(function, Complete$.MODULE$, false,
                Option.<Expression>empty(), NamedExpression$.MODULE$.newExprId());
        return named(name, aggregate);
    }

    static NamedExpression named(String name, Expression value) {
        return Alias$.MODULE$.apply(value, name, NamedExpression$.MODULE$.newExprId(),
                Catalyst.<String>seq(List.of()), Option.<Metadata>empty(), Catalyst.<String>seq(List.of()));
    }
}
