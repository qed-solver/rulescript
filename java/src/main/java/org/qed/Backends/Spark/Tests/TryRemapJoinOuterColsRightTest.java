package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.EqualTo;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
import org.apache.spark.sql.catalyst.expressions.LateralSubquery;
import org.apache.spark.sql.catalyst.expressions.Literal$;
import org.apache.spark.sql.catalyst.expressions.NamedExpression$;
import org.apache.spark.sql.catalyst.expressions.OuterReference;
import org.apache.spark.sql.catalyst.plans.Inner$;
import org.apache.spark.sql.catalyst.plans.JoinType;
import org.apache.spark.sql.catalyst.plans.LeftOuter$;
import org.apache.spark.sql.catalyst.plans.logical.Filter;
import org.apache.spark.sql.catalyst.plans.logical.HintInfo;
import org.apache.spark.sql.catalyst.plans.logical.LateralJoin;
import org.apache.spark.sql.catalyst.plans.logical.LogicalPlan;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import scala.Option;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class TryRemapJoinOuterColsRightTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("TryRemapJoinOuterColsRight.json"));

    private static LateralJoin lateral(LogicalPlan left, LogicalPlan right, Expression correlation, JoinType type) {
        var outer = Catalyst.<Attribute>list(left.output()).get(0);
        var subquery = new LateralSubquery(new Filter(correlation, right), Catalyst.<Expression>seq(List.of(outer)),
                NamedExpression$.MODULE$.newExprId(), Catalyst.<Expression>seq(List.of()),
                Option.<HintInfo>empty());
        return new LateralJoin(left, subquery, type, Option.<Expression>empty());
    }

    @Test
    void remapsPredicateFromRightColumnToEqualLeftColumn() {
        var left = Support.relation("L", 1, List.of(), new Random(1));
        var right = Support.relation("R", 1, List.of(), new Random(2));
        var l0 = Catalyst.<Attribute>list(left.output()).get(0);
        var r0 = Catalyst.<Attribute>list(right.output()).get(0);
        var join = lateral(left, right, new EqualTo(new OuterReference(l0), r0), Inner$.MODULE$);
        var plan = new Filter(new GreaterThan(r0, Literal$.MODULE$.apply(2)), join);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        var filter = assertInstanceOf(Filter.class, rewritten);
        assertEquals(new GreaterThan(l0, Literal$.MODULE$.apply(2)), filter.condition());
        var rewrittenJoin = assertInstanceOf(LateralJoin.class, filter.child());
        assertEquals(left.output(), rewrittenJoin.left().output());
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesLeftOuterCorrelationAlone() {
        var left = Support.relation("L", 1, List.of(), new Random(1));
        var right = Support.relation("R", 1, List.of(), new Random(2));
        var l0 = Catalyst.<Attribute>list(left.output()).get(0);
        var r0 = Catalyst.<Attribute>list(right.output()).get(0);
        var join = lateral(left, right, new EqualTo(new OuterReference(l0), r0), LeftOuter$.MODULE$);
        var plan = new Filter(new GreaterThan(r0, Literal$.MODULE$.apply(2)), join);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesNonEqualityCorrelationAlone() {
        var left = Support.relation("L", 1, List.of(), new Random(1));
        var right = Support.relation("R", 1, List.of(), new Random(2));
        var l0 = Catalyst.<Attribute>list(left.output()).get(0);
        var r0 = Catalyst.<Attribute>list(right.output()).get(0);
        var join = lateral(left, right, new GreaterThan(new OuterReference(l0), r0), Inner$.MODULE$);
        var plan = new Filter(new GreaterThan(r0, Literal$.MODULE$.apply(2)), join);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesPredicateOnOuterColumnAlone() {
        var left = Support.relation("L", 1, List.of(), new Random(1));
        var right = Support.relation("R", 1, List.of(), new Random(2));
        var l0 = Catalyst.<Attribute>list(left.output()).get(0);
        var r0 = Catalyst.<Attribute>list(right.output()).get(0);
        var join = lateral(left, right, new EqualTo(new OuterReference(l0), r0), Inner$.MODULE$);
        var plan = new Filter(new GreaterThan(l0, Literal$.MODULE$.apply(2)), join);
        assertEquals(plan, rule.apply(plan));
    }
}
