package org.qed.spark;

import org.apache.spark.sql.catalyst.InternalRow;
import org.apache.spark.sql.catalyst.expressions.Alias;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
import org.apache.spark.sql.catalyst.expressions.Literal;
import org.apache.spark.sql.catalyst.expressions.Literal$;
import org.apache.spark.sql.catalyst.expressions.NamedExpression;
import org.apache.spark.sql.catalyst.plans.Inner$;
import org.apache.spark.sql.catalyst.plans.LeftOuter$;
import org.apache.spark.sql.catalyst.plans.logical.Join;
import org.apache.spark.sql.catalyst.plans.logical.JoinHint$;
import org.apache.spark.sql.catalyst.plans.logical.LocalRelation;
import org.apache.spark.sql.catalyst.plans.logical.Project;
import org.apache.spark.sql.types.IntegerType$;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimplifyLeftJoinWithZeroRowsRightTest {
    private final RuleScriptRule rule = RuleScriptRule.load(
            Support.generated().resolve("SimplifyLeftJoinWithZeroRowsRight.json"));

    private static LocalRelation emptyRelation(String name, int arity) {
        var attrs = new java.util.ArrayList<Attribute>();
        for (int i = 0; i < arity; i++) {
            attrs.add(Support.attribute(name + "_" + i, true));
        }
        return new LocalRelation(Catalyst.seq(attrs), Catalyst.<InternalRow>seq(List.of()), false, Match.noStream());
    }

    @Test
    void leftJoinWithEmptyRightBecomesNullProjection() {
        var left = Support.relation("left", 2, List.of(), new Random(1));
        var right = emptyRelation("right", 2);
        var leftAttrs = Catalyst.<Attribute>list(left.output());
        var condition = new GreaterThan(leftAttrs.get(0), Literal$.MODULE$.apply(2));
        var plan = new Join(left, right, LeftOuter$.MODULE$, Catalyst.<Expression>some(condition), JoinHint$.MODULE$.NONE());

        var rewritten = rule.apply(plan);

        assertNotEquals(plan, rewritten);
        assertEquals(Catalyst.list(plan.output()).stream().map(a -> a.exprId()).toList(),
                Catalyst.list(rewritten.output()).stream().map(a -> a.exprId()).toList());
        assertTrue(rewritten instanceof Project);
        var top = (Project) rewritten;
        assertTrue(top.child() instanceof Project);
        var inner = (Project) top.child();
        assertSame(left, inner.child());
        var items = Catalyst.<NamedExpression>list(inner.projectList());
        assertEquals(4, items.size());
        assertEquals(leftAttrs.get(0).exprId(), items.get(0).exprId());
        assertEquals(leftAttrs.get(1).exprId(), items.get(1).exprId());
        assertTrue(items.get(2) instanceof Alias a2 && a2.child() instanceof Literal l2
                && l2.value() == null && l2.dataType().equals(IntegerType$.MODULE$));
        assertTrue(items.get(3) instanceof Alias a3 && a3.child() instanceof Literal l3
                && l3.value() == null && l3.dataType().equals(IntegerType$.MODULE$));
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void innerJoinWithEmptyRightIsLeftAlone() {
        var left = Support.relation("left", 2, List.of(), new Random(1));
        var right = emptyRelation("right", 2);
        var leftAttrs = Catalyst.<Attribute>list(left.output());
        var condition = new GreaterThan(leftAttrs.get(0), Literal$.MODULE$.apply(2));
        var plan = new Join(left, right, Inner$.MODULE$, Catalyst.<Expression>some(condition), JoinHint$.MODULE$.NONE());
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leftJoinWithNonEmptyRightIsLeftAlone() {
        var left = Support.relation("left", 2, List.of(), new Random(1));
        var right = Support.relation("right", 2, List.of(), new Random(2));
        var leftAttrs = Catalyst.<Attribute>list(left.output());
        var condition = new GreaterThan(leftAttrs.get(0), Literal$.MODULE$.apply(2));
        var plan = new Join(left, right, LeftOuter$.MODULE$, Catalyst.<Expression>some(condition), JoinHint$.MODULE$.NONE());
        assertEquals(plan, rule.apply(plan));
    }
}
