package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.And;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.EqualTo;
import org.apache.spark.sql.catalyst.expressions.Exists;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
import org.apache.spark.sql.catalyst.expressions.LessThan;
import org.apache.spark.sql.catalyst.expressions.Literal$;
import org.apache.spark.sql.catalyst.expressions.NamedExpression$;
import org.apache.spark.sql.catalyst.expressions.Not;
import org.apache.spark.sql.catalyst.plans.LeftAnti$;
import org.apache.spark.sql.catalyst.plans.logical.Filter;
import org.apache.spark.sql.catalyst.plans.logical.HintInfo;
import org.apache.spark.sql.catalyst.plans.logical.Join;
import org.apache.spark.sql.catalyst.plans.logical.LogicalPlan;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import scala.Option;

import java.nio.file.Path;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HoistSelectNotExistsTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("HoistSelectNotExists.json"));

    @Test
    void hoistsUncorrelatedNotExistsIntoAntiJoin() {
        var outer = Support.relation("l", 1, List.of(), new Random(1));
        var inner = Support.relation("s", 1, List.of(), new Random(2));
        var x = Catalyst.<Attribute>list(outer.output()).get(0);
        var y = Catalyst.<Attribute>list(inner.output()).get(0);
        var innerFilter = new Filter(new GreaterThan(y, Literal$.MODULE$.apply(2)), inner);
        var exists = existsOf(innerFilter, List.of());
        var plan = new Filter(new And(new Not(exists), new LessThan(x, Literal$.MODULE$.apply(3))), outer);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Filter top && top.condition().equals(new LessThan(x, Literal$.MODULE$.apply(3)))
                && top.child() instanceof Join join && join.joinType() == LeftAnti$.MODULE$
                && join.left().equals(outer) && join.right().equals(innerFilter)
                && (join.condition().isEmpty() || Catalyst.isTrue(join.condition().get())));
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesPlainExistsAlone() {
        var outer = Support.relation("l", 1, List.of(), new Random(1));
        var inner = Support.relation("s", 1, List.of(), new Random(2));
        var x = Catalyst.<Attribute>list(outer.output()).get(0);
        var y = Catalyst.<Attribute>list(inner.output()).get(0);
        var innerFilter = new Filter(new GreaterThan(y, Literal$.MODULE$.apply(2)), inner);
        var plan = new Filter(new And(existsOf(innerFilter, List.of()), new LessThan(x, Literal$.MODULE$.apply(3))), outer);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesCorrelatedNotExistsAlone() {
        var outer = Support.relation("l", 1, List.of(), new Random(1));
        var inner = Support.relation("s", 1, List.of(), new Random(2));
        var x = Catalyst.<Attribute>list(outer.output()).get(0);
        var y = Catalyst.<Attribute>list(inner.output()).get(0);
        var innerFilter = new Filter(new GreaterThan(y, Literal$.MODULE$.apply(2)), inner);
        var correlated = new Exists(innerFilter, Catalyst.<Expression>seq(List.of(x)), NamedExpression$.MODULE$.newExprId(),
                Catalyst.<Expression>seq(List.of(new EqualTo(y, x))), Option.<HintInfo>empty());
        var plan = new Filter(new And(new Not(correlated), new LessThan(x, Literal$.MODULE$.apply(3))), outer);
        assertEquals(plan, rule.apply(plan));
    }

    private static Exists existsOf(LogicalPlan subquery, List<Expression> joinCond) {
        return new Exists(subquery, Catalyst.<Expression>seq(List.of()), NamedExpression$.MODULE$.newExprId(),
                Catalyst.<Expression>seq(joinCond), Option.<HintInfo>empty());
    }
}
