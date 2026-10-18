package org.qed.spark;

import org.apache.spark.sql.catalyst.InternalRow;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.EqualTo;
import org.apache.spark.sql.catalyst.expressions.GenericInternalRow;
import org.apache.spark.sql.catalyst.expressions.LessThan;
import org.apache.spark.sql.catalyst.expressions.Literal$;
import org.apache.spark.sql.catalyst.expressions.Or;
import org.apache.spark.sql.catalyst.plans.Inner$;
import org.apache.spark.sql.catalyst.plans.LeftSemi$;
import org.apache.spark.sql.catalyst.plans.logical.Filter;
import org.apache.spark.sql.catalyst.plans.logical.Join;
import org.apache.spark.sql.catalyst.plans.logical.JoinHint$;
import org.apache.spark.sql.catalyst.plans.logical.LocalRelation;
import org.apache.spark.sql.catalyst.plans.logical.LogicalPlan;
import org.apache.spark.sql.catalyst.plans.logical.Union;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import scala.Option;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InlineAnyValuesSingleColTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("InlineAnyValuesSingleCol.json"));

    private static LocalRelation oneRow(int value) {
        var attr = Support.attribute("v", false);
        var row = new GenericInternalRow(new Object[]{value});
        return new LocalRelation(Catalyst.seq(List.of(attr)), Catalyst.<InternalRow>seq(List.of(row)), false,
                Match.noStream());
    }

    private static Union valuesOneAndThree() {
        return new Union(Catalyst.seq(List.of(oneRow(1), oneRow(3))), false, false);
    }

    @Test
    void semiJoinAgainstValuesBecomesInlinedFilter() {
        var base = Support.relation("s", 1, List.of(), new Random(7));
        var values = valuesOneAndThree();
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var v = Catalyst.<Attribute>list(values.output()).get(0);
        var plan = new Join(base, values, LeftSemi$.MODULE$, Option.apply(new EqualTo(x, v)),
                JoinHint$.MODULE$.NONE());
        var expected = new Filter(new Or(new EqualTo(x, Literal$.MODULE$.apply(1)),
                new EqualTo(x, Literal$.MODULE$.apply(3))), base);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Filter f && f.child() == base);
        assertEquals(expected, rewritten);
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void innerJoinAgainstValuesIsLeftAlone() {
        var base = Support.relation("s", 1, List.of(), new Random(7));
        var values = valuesOneAndThree();
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var v = Catalyst.<Attribute>list(values.output()).get(0);
        var plan = new Join(base, values, Inner$.MODULE$, Option.apply(new EqualTo(x, v)),
                JoinHint$.MODULE$.NONE());
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void nonEqualityCorrelationIsLeftAlone() {
        var base = Support.relation("s", 1, List.of(), new Random(7));
        var values = valuesOneAndThree();
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var v = Catalyst.<Attribute>list(values.output()).get(0);
        var plan = new Join(base, values, LeftSemi$.MODULE$, Option.apply(new LessThan(x, v)),
                JoinHint$.MODULE$.NONE());
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void wideInputIsLeftAlone() {
        var base = Support.relation("s", 2, List.of(), new Random(7));
        var values = valuesOneAndThree();
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var v = Catalyst.<Attribute>list(values.output()).get(0);
        var plan = new Join(base, values, LeftSemi$.MODULE$, Option.apply(new EqualTo(x, v)),
                JoinHint$.MODULE$.NONE());
        assertEquals(plan, rule.apply(plan));
    }
}
