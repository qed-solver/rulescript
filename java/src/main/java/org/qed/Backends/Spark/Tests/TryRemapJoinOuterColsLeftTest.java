package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.EqualTo;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
import org.apache.spark.sql.catalyst.expressions.LateralSubquery;
import org.apache.spark.sql.catalyst.expressions.LessThan;
import org.apache.spark.sql.catalyst.expressions.Literal$;
import org.apache.spark.sql.catalyst.expressions.NamedExpression$;
import org.apache.spark.sql.catalyst.expressions.OuterReference;
import org.apache.spark.sql.catalyst.plans.logical.Filter;
import org.apache.spark.sql.catalyst.plans.logical.LateralJoin;
import org.apache.spark.sql.catalyst.plans.logical.LogicalPlan;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import scala.Option;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TryRemapJoinOuterColsLeftTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("TryRemapJoinOuterColsLeft.json"));

    private static LateralJoin correlate(LogicalPlan left, LogicalPlan right, String type, Expression cond, Attribute outer) {
        var subquery = new LateralSubquery(new Filter(cond, right), Catalyst.<Expression>seq(List.<Expression>of(outer)),
                NamedExpression$.MODULE$.newExprId(), Catalyst.<Expression>seq(List.of()),
                Option.<org.apache.spark.sql.catalyst.plans.logical.HintInfo>empty());
        return new LateralJoin(left, subquery, Match.joinType(type), Option.<Expression>empty());
    }

    @Test
    void movesFilterOnLeftColumnOfEqualityJoinToRightColumn() {
        var left = Support.relation("L", 1, List.of(), new Random(1));
        var right = Support.relation("R", 1, List.of(), new Random(2));
        var l0 = Catalyst.<Attribute>list(left.output()).get(0);
        var r0 = Catalyst.<Attribute>list(right.output()).get(0);
        var join = correlate(left, right, "inner", new EqualTo(new OuterReference(l0), r0), l0);
        var plan = new Filter(new GreaterThan(l0, Literal$.MODULE$.apply(2)), join);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Filter f && f.child() instanceof LateralJoin
                && f.condition().equals(new GreaterThan(r0, Literal$.MODULE$.apply(2))));
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesLeftOuterJoinAlone() {
        var left = Support.relation("L", 1, List.of(), new Random(1));
        var right = Support.relation("R", 1, List.of(), new Random(2));
        var l0 = Catalyst.<Attribute>list(left.output()).get(0);
        var r0 = Catalyst.<Attribute>list(right.output()).get(0);
        var join = correlate(left, right, "left", new EqualTo(new OuterReference(l0), r0), l0);
        var plan = new Filter(new GreaterThan(l0, Literal$.MODULE$.apply(2)), join);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesNonEqualityJoinAlone() {
        var left = Support.relation("L", 1, List.of(), new Random(1));
        var right = Support.relation("R", 1, List.of(), new Random(2));
        var l0 = Catalyst.<Attribute>list(left.output()).get(0);
        var r0 = Catalyst.<Attribute>list(right.output()).get(0);
        var join = correlate(left, right, "inner", new LessThan(new OuterReference(l0), r0), l0);
        var plan = new Filter(new GreaterThan(l0, Literal$.MODULE$.apply(2)), join);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesPredicateOnRightColumnAlone() {
        var left = Support.relation("L", 1, List.of(), new Random(1));
        var right = Support.relation("R", 1, List.of(), new Random(2));
        var l0 = Catalyst.<Attribute>list(left.output()).get(0);
        var r0 = Catalyst.<Attribute>list(right.output()).get(0);
        var join = correlate(left, right, "inner", new EqualTo(new OuterReference(l0), r0), l0);
        var plan = new Filter(new GreaterThan(r0, Literal$.MODULE$.apply(2)), join);
        assertEquals(plan, rule.apply(plan));
    }
}
