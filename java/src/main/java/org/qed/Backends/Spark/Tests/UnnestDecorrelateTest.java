package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.EqualTo;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.NamedExpression;
import org.apache.spark.sql.catalyst.plans.Inner$;
import org.apache.spark.sql.catalyst.plans.LeftOuter$;
import org.apache.spark.sql.catalyst.plans.logical.Join;
import org.apache.spark.sql.catalyst.plans.logical.JoinHint$;
import org.apache.spark.sql.catalyst.plans.logical.Project;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import scala.Option;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UnnestDecorrelateTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("UnnestDecorrelate.json"));

    @Test
    void unnestsArrayAndDropsTheUnusedColumn() {
        var left = Support.relation("L", 2, List.of(), new Random(1));
        var elements = Support.relation("E", 1, List.of(), new Random(2));
        var leftAttrs = Catalyst.<Attribute>list(left.output());
        var element = Catalyst.<Attribute>list(elements.output()).get(0);
        var condition = Option.<Expression>apply(new EqualTo(leftAttrs.get(0), element));
        var join = new Join(left, elements, Inner$.MODULE$, condition, JoinHint$.MODULE$.NONE());
        var plan = new Project(Catalyst.<NamedExpression>seq(List.of(element)), join);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Project outer && outer.child() instanceof Join inner
                && inner.joinType() == Inner$.MODULE$
                && inner.left() instanceof Project arrayOnly && arrayOnly.child() == left
                && inner.right() == elements);
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesALeftOuterUnnestAlone() {
        var left = Support.relation("L", 2, List.of(), new Random(1));
        var elements = Support.relation("E", 1, List.of(), new Random(2));
        var leftAttrs = Catalyst.<Attribute>list(left.output());
        var element = Catalyst.<Attribute>list(elements.output()).get(0);
        var condition = Option.<Expression>apply(new EqualTo(leftAttrs.get(0), element));
        var join = new Join(left, elements, LeftOuter$.MODULE$, condition, JoinHint$.MODULE$.NONE());
        var plan = new Project(Catalyst.<NamedExpression>seq(List.of(element)), join);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesAnUnprojectedJoinAlone() {
        var left = Support.relation("L", 2, List.of(), new Random(1));
        var elements = Support.relation("E", 1, List.of(), new Random(2));
        var leftAttrs = Catalyst.<Attribute>list(left.output());
        var element = Catalyst.<Attribute>list(elements.output()).get(0);
        var condition = Option.<Expression>apply(new EqualTo(leftAttrs.get(0), element));
        var plan = new Join(left, elements, Inner$.MODULE$, condition, JoinHint$.MODULE$.NONE());
        assertEquals(plan, rule.apply(plan));
    }
}
