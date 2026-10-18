package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.Coalesce;
import org.apache.spark.sql.catalyst.expressions.Expression;
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

class FilterFlattenCorrelatedConditionTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("FilterFlattenCorrelatedCondition.json"));

    private static Expression shifted(Attribute inner) {
        return new Coalesce(Catalyst.<Expression>seq(List.of(inner, Literal$.MODULE$.apply(1))));
    }

    @Test
    void hoistsTheInnerExpressionBelowTheCorrelatedFilter() {
        var outer = Support.relation("o", 1, List.of(), new Random(1));
        var inner = Support.relation("n", 1, List.of(), new Random(2));
        var o = Catalyst.<Attribute>list(outer.output()).get(0);
        var n = Catalyst.<Attribute>list(inner.output()).get(0);
        var join = new Join(outer, inner, Inner$.MODULE$, Option.<Expression>empty(), JoinHint$.MODULE$.NONE());
        var plan = new Filter(new GreaterThan(o, shifted(n)), join);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Project top && top.projectList().size() == 2
                && top.child() instanceof Filter filter && filter.condition() instanceof GreaterThan
                && filter.child() instanceof Project middle && middle.projectList().size() == 3
                && middle.child() instanceof Join);
        assertEquals(Catalyst.list(plan.output()).size(), Catalyst.list(rewritten.output()).size());
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesALeftOuterJoinAlone() {
        var outer = Support.relation("o", 1, List.of(), new Random(1));
        var inner = Support.relation("n", 1, List.of(), new Random(2));
        var o = Catalyst.<Attribute>list(outer.output()).get(0);
        var n = Catalyst.<Attribute>list(inner.output()).get(0);
        var join = new Join(outer, inner, LeftOuter$.MODULE$, Option.<Expression>empty(), JoinHint$.MODULE$.NONE());
        var plan = new Filter(new GreaterThan(o, shifted(n)), join);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void hoistsWhenTheComparisonItselfReadsTheOuterColumnAgain() {
        var outer = Support.relation("o", 1, List.of(), new Random(1));
        var inner = Support.relation("n", 1, List.of(), new Random(2));
        var o = Catalyst.<Attribute>list(outer.output()).get(0);
        var n = Catalyst.<Attribute>list(inner.output()).get(0);
        var join = new Join(outer, inner, Inner$.MODULE$, Option.<Expression>empty(), JoinHint$.MODULE$.NONE());
        var plan = new Filter(new GreaterThan(o, new Coalesce(Catalyst.<Expression>seq(List.of(o, n)))), join);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesAWiderOuterRelationAlone() {
        var outer = Support.relation("o", 2, List.of(), new Random(1));
        var inner = Support.relation("n", 1, List.of(), new Random(2));
        var o = Catalyst.<Attribute>list(outer.output()).get(0);
        var n = Catalyst.<Attribute>list(inner.output()).get(0);
        var join = new Join(outer, inner, Inner$.MODULE$, Option.<Expression>empty(), JoinHint$.MODULE$.NONE());
        var plan = new Filter(new GreaterThan(o, shifted(n)), join);
        assertEquals(plan, rule.apply(plan));
    }
}
