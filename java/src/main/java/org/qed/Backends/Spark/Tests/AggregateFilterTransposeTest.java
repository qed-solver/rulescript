package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Alias$;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
import org.apache.spark.sql.catalyst.expressions.Literal$;
import org.apache.spark.sql.catalyst.expressions.NamedExpression;
import org.apache.spark.sql.catalyst.expressions.NamedExpression$;
import org.apache.spark.sql.catalyst.expressions.aggregate.AggregateExpression$;
import org.apache.spark.sql.catalyst.expressions.aggregate.Max;
import org.apache.spark.sql.catalyst.expressions.aggregate.Complete$;
import org.apache.spark.sql.catalyst.plans.LeftOuter$;
import org.apache.spark.sql.catalyst.plans.Inner$;
import org.apache.spark.sql.catalyst.plans.JoinType;
import org.apache.spark.sql.catalyst.plans.logical.Aggregate;
import org.apache.spark.sql.catalyst.plans.logical.Filter;
import org.apache.spark.sql.catalyst.plans.logical.Join;
import org.apache.spark.sql.catalyst.plans.logical.JoinHint$;
import org.apache.spark.sql.catalyst.plans.logical.LocalRelation;
import org.apache.spark.sql.catalyst.plans.logical.LogicalPlan;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import scala.Option;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AggregateFilterTransposeTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("AggregateFilterTranspose.json"));

    private static LocalRelation[] sides() {
        return new LocalRelation[] {
            Support.relation("A", 1, List.of(), new Random(1)),
            Support.relation("B", 1, List.of(), new Random(2)),
        };
    }

    private static Join join(LocalRelation a, LocalRelation b, JoinType type) {
        return new Join(a, b, type, Option.<Expression>empty(),
                JoinHint$.MODULE$.NONE());
    }

    private static Aggregate aggregate(LogicalPlan child, Attribute key, Attribute value) {
        var max = AggregateExpression$.MODULE$.apply(new Max(value), Complete$.MODULE$, false,
                Option.<Expression>empty(), NamedExpression$.MODULE$.newExprId());
        var items = new java.util.ArrayList<NamedExpression>();
        items.add(key);
        items.add(Alias$.MODULE$.apply((Expression) max, "f", NamedExpression$.MODULE$.newExprId(),
                Catalyst.<String>seq(List.of()), Option.empty(), Catalyst.<String>seq(List.of())));
        return new Aggregate(Catalyst.seq(List.<Expression>of(key)), Catalyst.seq(items), child,
                Option.<org.apache.spark.sql.catalyst.plans.logical.AggregateHint>empty());
    }

    @Test
    void pushesGroupKeyFilterBelowAggregate() {
        var sides = sides();
        var a = sides[0];
        var b = sides[1];
        var joined = join(a, b, Inner$.MODULE$);
        var a0 = Catalyst.<Attribute>list(a.output()).get(0);
        var b0 = Catalyst.<Attribute>list(b.output()).get(0);
        var filtered = new Filter(new GreaterThan(a0, Literal$.MODULE$.apply(1)), joined);
        var plan = aggregate(filtered, a0, b0);

        var rewritten = rule.apply(plan);

        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Filter top && top.condition() instanceof GreaterThan
                && top.child() instanceof Aggregate agg && agg.child() instanceof Join);
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesFilterOnNonGroupColumnAlone() {
        var sides = sides();
        var a = sides[0];
        var b = sides[1];
        var joined = join(a, b, Inner$.MODULE$);
        var a0 = Catalyst.<Attribute>list(a.output()).get(0);
        var b0 = Catalyst.<Attribute>list(b.output()).get(0);
        var filtered = new Filter(new GreaterThan(b0, Literal$.MODULE$.apply(1)), joined);
        var plan = aggregate(filtered, a0, b0);

        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesOuterJoinAlone() {
        var sides = sides();
        var a = sides[0];
        var b = sides[1];
        var joined = join(a, b, LeftOuter$.MODULE$);
        var a0 = Catalyst.<Attribute>list(a.output()).get(0);
        var b0 = Catalyst.<Attribute>list(b.output()).get(0);
        var filtered = new Filter(new GreaterThan(a0, Literal$.MODULE$.apply(1)), joined);
        var plan = aggregate(filtered, a0, b0);

        assertEquals(plan, rule.apply(plan));
    }
}
