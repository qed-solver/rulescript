package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.EqualTo;
import org.apache.spark.sql.catalyst.expressions.Exists;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
import org.apache.spark.sql.catalyst.expressions.Not;
import org.apache.spark.sql.catalyst.expressions.NamedExpression$;
import org.apache.spark.sql.catalyst.plans.LeftSemi$;
import org.apache.spark.sql.catalyst.plans.logical.Filter;
import org.apache.spark.sql.catalyst.plans.logical.HintInfo;
import org.apache.spark.sql.catalyst.plans.logical.Join;
import org.apache.spark.sql.catalyst.plans.logical.JoinHint$;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import scala.Option;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RewritePredicateSubqueryTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("RewritePredicateSubquery.json"));

    private static Exists existsOver(org.apache.spark.sql.catalyst.plans.logical.LogicalPlan inner, Expression cond) {
        return new Exists(inner, Catalyst.<Expression>seq(List.of()), NamedExpression$.MODULE$.newExprId(),
                Catalyst.<Expression>seq(List.of(cond)), Option.<HintInfo>empty());
    }

    @Test
    void filterOnExistsBecomesLeftSemiJoin() {
        var left = Support.relation("l", 1, List.of(), new Random(1));
        var right = Support.relation("r", 1, List.of(), new Random(2));
        var x = Catalyst.<Attribute>list(left.output()).get(0);
        var y = Catalyst.<Attribute>list(right.output()).get(0);
        var plan = new Filter(existsOver(right, new EqualTo(x, y)), left);

        var rewritten = rule.apply(plan);

        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Join join
                && join.joinType().equals(LeftSemi$.MODULE$)
                && join.left() == left
                && join.right() == right
                && join.condition().isDefined()
                && join.condition().get().equals(new EqualTo(x, y)));
        Assumptions.assumeTrue(Support.canExecute());
        var leftRows = Support.run(left);
        var rightRows = Support.run(right);
        var expected = leftRows.stream()
                .filter(row -> !row.equals("[null]") && rightRows.contains(row))
                .sorted()
                .toList();
        assertEquals(expected, Support.run(rewritten));
    }

    @Test
    void negatedExistsIsLeftAlone() {
        var left = Support.relation("l", 1, List.of(), new Random(1));
        var right = Support.relation("r", 1, List.of(), new Random(2));
        var x = Catalyst.<Attribute>list(left.output()).get(0);
        var y = Catalyst.<Attribute>list(right.output()).get(0);
        var plan = new Filter(new Not(existsOver(right, new EqualTo(x, y))), left);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void nonEqualityCorrelationIsLeftAlone() {
        var left = Support.relation("l", 1, List.of(), new Random(1));
        var right = Support.relation("r", 1, List.of(), new Random(2));
        var x = Catalyst.<Attribute>list(left.output()).get(0);
        var y = Catalyst.<Attribute>list(right.output()).get(0);
        var plan = new Filter(existsOver(right, new GreaterThan(x, y)), left);
        assertEquals(plan, rule.apply(plan));
    }
}
