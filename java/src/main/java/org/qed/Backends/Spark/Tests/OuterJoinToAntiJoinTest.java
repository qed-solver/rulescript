package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.And;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.EqualTo;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
import org.apache.spark.sql.catalyst.expressions.IsNull;
import org.apache.spark.sql.catalyst.expressions.Literal$;
import org.apache.spark.sql.catalyst.expressions.NamedExpression;
import org.apache.spark.sql.catalyst.plans.Inner$;
import org.apache.spark.sql.catalyst.plans.LeftAnti$;
import org.apache.spark.sql.catalyst.plans.LeftOuter$;
import org.apache.spark.sql.catalyst.plans.logical.Filter;
import org.apache.spark.sql.catalyst.plans.logical.Join;
import org.apache.spark.sql.catalyst.plans.logical.JoinHint$;
import org.apache.spark.sql.catalyst.plans.logical.LogicalPlan;
import org.apache.spark.sql.catalyst.plans.logical.Project;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OuterJoinToAntiJoinTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("OuterJoinToAntiJoin.json"));

    @Test
    void leftJoinNullTestOnNonNullColumnBecomesAntiJoin() {
        var left = Support.relation("l", 1, List.of(), new Random(1));
        var right = Support.relation("r", 1, List.of(0), new Random(2));
        var l = Catalyst.<Attribute>list(left.output()).get(0);
        var r = Catalyst.<Attribute>list(right.output()).get(0);
        var join = new Join(left, right, LeftOuter$.MODULE$, Catalyst.<Expression>some(new EqualTo(l, r)),
                JoinHint$.MODULE$.NONE());
        var joined = Catalyst.<Attribute>list(join.output());
        var filter = new Filter(new IsNull(joined.get(1)), join);
        var plan = new Project(Catalyst.<NamedExpression>seq(List.of(joined.get(0))), filter);

        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Join);
        var anti = (Join) rewritten;
        assertEquals(LeftAnti$.MODULE$, anti.joinType());
        assertEquals(left, anti.left());
        assertEquals(right, anti.right());
        assertTrue(anti.condition().isDefined());

        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leftJoinNullTestOnNullableColumnIsLeftAlone() {
        var left = Support.relation("l", 1, List.of(), new Random(1));
        var right = Support.relation("r", 1, List.of(), new Random(2));
        var l = Catalyst.<Attribute>list(left.output()).get(0);
        var r = Catalyst.<Attribute>list(right.output()).get(0);
        var join = new Join(left, right, LeftOuter$.MODULE$, Catalyst.<Expression>some(new EqualTo(l, r)),
                JoinHint$.MODULE$.NONE());
        var joined = Catalyst.<Attribute>list(join.output());
        var filter = new Filter(new IsNull(joined.get(1)), join);
        var plan = new Project(Catalyst.<NamedExpression>seq(List.of(joined.get(0))), filter);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void innerJoinNullTestIsLeftAlone() {
        var left = Support.relation("l", 1, List.of(), new Random(1));
        var right = Support.relation("r", 1, List.of(0), new Random(2));
        var l = Catalyst.<Attribute>list(left.output()).get(0);
        var r = Catalyst.<Attribute>list(right.output()).get(0);
        var join = new Join(left, right, Inner$.MODULE$, Catalyst.<Expression>some(new EqualTo(l, r)),
                JoinHint$.MODULE$.NONE());
        var joined = Catalyst.<Attribute>list(join.output());
        var filter = new Filter(new IsNull(joined.get(1)), join);
        var plan = new Project(Catalyst.<NamedExpression>seq(List.of(joined.get(0))), filter);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void extraLeftOnlyConjunctIsLeftAlone() {
        var left = Support.relation("l", 1, List.of(), new Random(1));
        var right = Support.relation("r", 1, List.of(0), new Random(2));
        var l = Catalyst.<Attribute>list(left.output()).get(0);
        var r = Catalyst.<Attribute>list(right.output()).get(0);
        var join = new Join(left, right, LeftOuter$.MODULE$, Catalyst.<Expression>some(new EqualTo(l, r)),
                JoinHint$.MODULE$.NONE());
        var joined = Catalyst.<Attribute>list(join.output());
        var condition = new And(new IsNull(joined.get(1)),
                new GreaterThan(joined.get(0), Literal$.MODULE$.apply(1)));
        var filter = new Filter(condition, join);
        var plan = new Project(Catalyst.<NamedExpression>seq(List.of(joined.get(0))), filter);
        assertEquals(plan, rule.apply(plan));
    }
}
