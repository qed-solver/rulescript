package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Add;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
import org.apache.spark.sql.catalyst.expressions.Literal$;
import org.apache.spark.sql.catalyst.plans.Inner$;
import org.apache.spark.sql.catalyst.plans.LeftOuter$;
import org.apache.spark.sql.catalyst.plans.logical.Filter;
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

class PullOutNestedDataOuterRefExpressionsTest {
    private final RuleScriptRule rule = RuleScriptRule.load(
            Support.generated().resolve("PullOutNestedDataOuterRefExpressions.json"));

    @Test
    void pullsTheOuterValueIntoAProjectBeforeTheComparison() {
        var outer = Support.relation("o", 1, List.of(), new Random(1));
        var inner = Support.relation("i", 1, List.of(), new Random(2));
        var o = Catalyst.<Attribute>list(outer.output()).get(0);
        var i = Catalyst.<Attribute>list(inner.output()).get(0);
        var join = new Join(outer, inner, Inner$.MODULE$, Option.empty(), JoinHint$.MODULE$.NONE());
        var plan = new Filter(new GreaterThan(new Add(o, Literal$.MODULE$.apply(1)), i), join);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Project);
        var top = (Project) rewritten;
        assertTrue(top.child() instanceof Filter);
        var middle = (Filter) top.child();
        assertTrue(middle.condition() instanceof GreaterThan);
        assertTrue(middle.child() instanceof Project);
        var lower = (Project) middle.child();
        assertEquals(3, Catalyst.list(lower.projectList()).size());
        assertEquals(join, lower.child());
        assertEquals(Catalyst.list(plan.output()).stream().map(a -> a.exprId()).toList(),
                Catalyst.list(rewritten.output()).stream().map(a -> a.exprId()).toList());
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesALeftOuterJoinAlone() {
        var outer = Support.relation("o", 1, List.of(), new Random(1));
        var inner = Support.relation("i", 1, List.of(), new Random(2));
        var o = Catalyst.<Attribute>list(outer.output()).get(0);
        var i = Catalyst.<Attribute>list(inner.output()).get(0);
        var join = new Join(outer, inner, LeftOuter$.MODULE$, Option.empty(), JoinHint$.MODULE$.NONE());
        var plan = new Filter(new GreaterThan(new Add(o, Literal$.MODULE$.apply(1)), i), join);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesAComparisonAgainstAnOuterColumnAlone() {
        var outer = Support.relation("o", 1, List.of(), new Random(1));
        var inner = Support.relation("i", 1, List.of(), new Random(2));
        var o = Catalyst.<Attribute>list(outer.output()).get(0);
        var join = new Join(outer, inner, Inner$.MODULE$, Option.empty(), JoinHint$.MODULE$.NONE());
        var plan = new Filter(new GreaterThan(new Add(o, Literal$.MODULE$.apply(1)), o), join);
        assertEquals(plan, rule.apply(plan));
    }
}
