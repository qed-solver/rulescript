package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.And;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.EqualNullSafe;
import org.apache.spark.sql.catalyst.expressions.EqualTo;
import org.apache.spark.sql.catalyst.expressions.IsNull;
import org.apache.spark.sql.catalyst.expressions.Literal$;
import org.apache.spark.sql.catalyst.expressions.Or;
import org.apache.spark.sql.catalyst.plans.logical.Filter;
import org.apache.spark.sql.catalyst.plans.logical.Join;
import org.apache.spark.sql.catalyst.plans.logical.JoinHint$;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import scala.Option;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class FilterRemoveIsNotDistinctFromTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("FilterRemoveIsNotDistinctFrom.json"));

    private static Join innerJoin(String type) {
        var left = Support.relation("L", 1, List.of(), new Random(1));
        var right = Support.relation("R", 1, List.of(), new Random(2));
        return new Join(left, right, Match.joinType(type), Option.<org.apache.spark.sql.catalyst.expressions.Expression>empty(),
                JoinHint$.MODULE$.NONE());
    }

    @Test
    void rewritesNullSafeEqualityOfTheTwoJoinedColumns() {
        var join = innerJoin("inner");
        var columns = Catalyst.<Attribute>list(join.output());
        var x = columns.get(0);
        var y = columns.get(1);
        var plan = new Filter(new EqualNullSafe(x, y), join);
        var expected = new Filter(new Or(
                new And(new IsNull(x), new IsNull(y)),
                new EqualNullSafe(new EqualTo(x, y), Literal$.MODULE$.apply(true))), join);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertEquals(expected, rewritten);
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesAPlainEqualityAlone() {
        var join = innerJoin("inner");
        var columns = Catalyst.<Attribute>list(join.output());
        var plan = new Filter(new EqualTo(columns.get(0), columns.get(1)), join);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesALeftOuterJoinAlone() {
        var join = innerJoin("left");
        var columns = Catalyst.<Attribute>list(join.output());
        var plan = new Filter(new EqualNullSafe(columns.get(0), columns.get(1)), join);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesASameColumnComparisonAlone() {
        var join = innerJoin("inner");
        var columns = Catalyst.<Attribute>list(join.output());
        var plan = new Filter(new EqualNullSafe(columns.get(0), columns.get(0)), join);
        assertEquals(plan, rule.apply(plan));
    }
}
