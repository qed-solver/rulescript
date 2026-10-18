package org.qed.spark;

import org.apache.spark.sql.catalyst.InternalRow;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.NamedExpression;
import org.apache.spark.sql.catalyst.expressions.EqualTo;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.GenericInternalRow;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
import org.apache.spark.sql.catalyst.plans.logical.Aggregate;
import org.apache.spark.sql.catalyst.plans.logical.AggregateHint;
import org.apache.spark.sql.catalyst.plans.logical.Join;
import org.apache.spark.sql.catalyst.plans.logical.JoinHint$;
import org.apache.spark.sql.catalyst.plans.logical.LocalRelation;
import org.apache.spark.sql.catalyst.plans.logical.LogicalPlan;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import scala.Option;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class InlineJoinConstantsLeftTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("InlineJoinConstantsLeft.json"));

    private static LogicalPlan constant(int value) {
        var column = Support.attribute("const_0", false);
        var row = new GenericInternalRow(new Object[] {value});
        var values = new LocalRelation(Catalyst.seq(List.of(column)), Catalyst.<InternalRow>seq(List.of(row)), false, Match.noStream());
        return new Aggregate(Catalyst.<Expression>seq(List.of(column)), Catalyst.<NamedExpression>seq(List.of(column)), values,
                Option.<AggregateHint>empty());
    }

    private static Join join(LogicalPlan left, LogicalPlan right, String type, Expression cond) {
        return new Join(left, right, Match.joinType(type), Option.<Expression>apply(cond), JoinHint$.MODULE$.NONE());
    }

    @Test
    void inlinesConstantColumnIntoOuterCondition() {
        var source = Support.relation("src", 1, List.of(), new Random(1));
        var constant = constant(2);
        var right = Support.relation("right", 1, List.of(), new Random(2));
        var a = Catalyst.<Attribute>list(source.output()).get(0);
        var c = Catalyst.<Attribute>list(constant.output()).get(0);
        var r = Catalyst.<Attribute>list(right.output()).get(0);
        var left = join(source, constant, "inner", new EqualTo(a, c));
        var plan = join(left, right, "inner", new EqualTo(a, r));

        var rewritten = rule.apply(plan);

        assertNotEquals(plan, rewritten);
        var join = assertInstanceOf(Join.class, rewritten);
        assertEquals(Match.joinType("inner"), join.joinType());
        assertEquals(Option.<Expression>apply(new EqualTo(c, r)), join.condition());
        assertEquals(left, join.left());
        assertEquals(right, join.right());
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesLeftOuterJoinAlone() {
        var source = Support.relation("src", 1, List.of(), new Random(1));
        var constant = constant(2);
        var right = Support.relation("right", 1, List.of(), new Random(2));
        var a = Catalyst.<Attribute>list(source.output()).get(0);
        var c = Catalyst.<Attribute>list(constant.output()).get(0);
        var r = Catalyst.<Attribute>list(right.output()).get(0);
        var left = join(source, constant, "inner", new EqualTo(a, c));
        var plan = join(left, right, "left", new EqualTo(a, r));
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesNonEqualityConstantJoinAlone() {
        var source = Support.relation("src", 1, List.of(), new Random(1));
        var constant = constant(2);
        var right = Support.relation("right", 1, List.of(), new Random(2));
        var a = Catalyst.<Attribute>list(source.output()).get(0);
        var c = Catalyst.<Attribute>list(constant.output()).get(0);
        var r = Catalyst.<Attribute>list(right.output()).get(0);
        var left = join(source, constant, "inner", new GreaterThan(a, c));
        var plan = join(left, right, "inner", new EqualTo(a, r));
        assertEquals(plan, rule.apply(plan));
    }
}
