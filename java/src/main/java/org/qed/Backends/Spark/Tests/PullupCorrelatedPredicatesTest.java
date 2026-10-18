package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.And;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.Exists;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
import org.apache.spark.sql.catalyst.expressions.LessThan;
import org.apache.spark.sql.catalyst.expressions.Literal$;
import org.apache.spark.sql.catalyst.expressions.NamedExpression$;
import org.apache.spark.sql.catalyst.plans.logical.Filter;
import org.apache.spark.sql.catalyst.plans.logical.HintInfo;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PullupCorrelatedPredicatesTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("PullupCorrelatedPredicates.json"));

    @Test
    void pullsAttributeFreeConjunctOutOfExists() {
        var outer = Support.relation("outer", 1, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(outer.output()).get(0);
        var inner = Support.relation("inner", 1, List.of(), new Random(2));
        var y = Catalyst.<Attribute>list(inner.output()).get(0);
        var correlated = new GreaterThan(Literal$.MODULE$.apply(2), Literal$.MODULE$.apply(1));
        var local = new LessThan(y, Literal$.MODULE$.apply(3));
        var subquery = new Exists(new Filter(new And(correlated, local), inner),
                Catalyst.<Expression>seq(List.of()), NamedExpression$.MODULE$.newExprId(),
                Catalyst.<Expression>seq(List.of()), scala.Option.<HintInfo>empty());
        var plan = new Filter(new And(new GreaterThan(x, Literal$.MODULE$.apply(0)), subquery), outer);

        var rewritten = rule.apply(plan);

        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Filter);
        var filter = (Filter) rewritten;
        assertSame(outer, filter.child());
        var parts = Catalyst.conjuncts(filter.condition());
        assertEquals(3, parts.size());
        assertEquals(correlated, parts.get(1));
        assertTrue(parts.get(2) instanceof Exists);
        var exists = (Exists) parts.get(2);
        assertTrue(Catalyst.list(exists.joinCond()).isEmpty());
        assertTrue(exists.plan() instanceof Filter);
        var inside = (Filter) exists.plan();
        assertSame(inner, inside.child());
        assertEquals(local, inside.condition());
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesExistsWithoutCorrelatedConjunctAlone() {
        var outer = Support.relation("outer", 1, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(outer.output()).get(0);
        var inner = Support.relation("inner", 1, List.of(), new Random(2));
        var y = Catalyst.<Attribute>list(inner.output()).get(0);
        var local = new LessThan(y, Literal$.MODULE$.apply(3));
        var subquery = new Exists(new Filter(local, inner),
                Catalyst.<Expression>seq(List.of()), NamedExpression$.MODULE$.newExprId(),
                Catalyst.<Expression>seq(List.of()), scala.Option.<HintInfo>empty());
        var plan = new Filter(new And(new GreaterThan(x, Literal$.MODULE$.apply(0)), subquery), outer);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void pullsUpCorrelatedConjunctThatReadsOuterAttribute() {
        var outer = Support.relation("outer", 1, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(outer.output()).get(0);
        var inner = Support.relation("inner", 1, List.of(), new Random(2));
        var y = Catalyst.<Attribute>list(inner.output()).get(0);
        var correlated = new GreaterThan(x, Literal$.MODULE$.apply(1));
        var local = new LessThan(y, Literal$.MODULE$.apply(3));
        var subquery = new Exists(new Filter(new And(correlated, local), inner),
                Catalyst.<Expression>seq(List.of()), NamedExpression$.MODULE$.newExprId(),
                Catalyst.<Expression>seq(List.of()), scala.Option.<HintInfo>empty());
        var plan = new Filter(new And(new GreaterThan(x, Literal$.MODULE$.apply(0)), subquery), outer);
        var rewritten = rule.apply(plan);
        org.junit.jupiter.api.Assertions.assertNotEquals(plan, rewritten);
        var cond = ((Filter) rewritten).condition();
        org.junit.jupiter.api.Assertions.assertTrue(cond instanceof And top && top.left().equals(new GreaterThan(x, Literal$.MODULE$.apply(0)))
                && top.right() instanceof And hoisted && hoisted.left().equals(correlated)
                && hoisted.right() instanceof Exists e && e.plan() instanceof Filter inside
                && inside.condition().equals(local));
    }
}
