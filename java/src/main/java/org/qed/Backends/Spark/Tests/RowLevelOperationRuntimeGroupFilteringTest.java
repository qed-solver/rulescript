package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
import org.apache.spark.sql.catalyst.expressions.Literal$;
import org.apache.spark.sql.catalyst.plans.LeftSemi$;
import org.apache.spark.sql.catalyst.plans.logical.Filter;
import org.apache.spark.sql.catalyst.plans.logical.Join;
import org.apache.spark.sql.catalyst.plans.logical.LocalRelation;
import org.apache.spark.sql.catalyst.plans.logical.Project;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RowLevelOperationRuntimeGroupFilteringTest {
    private final RuleScriptRule rule = RuleScriptRule.load(
            Support.generated().resolve("RowLevelOperationRuntimeGroupFiltering.json"));

    @Test
    void addsSemiJoinOnKeyWithTheSameFilter() {
        var base = Support.relation("t", 2, List.of(0, 1), new Random(1));
        var key = Catalyst.<Attribute>list(base.output()).get(0);
        var plan = new Filter(new GreaterThan(key, Literal$.MODULE$.apply(1)), base);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Filter outer
                && outer.child() instanceof Join join
                && join.joinType() == LeftSemi$.MODULE$
                && join.left() instanceof LocalRelation
                && join.right() instanceof Filter);
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void addsSemiJoinForPredicateOnDataColumn() {
        var base = Support.relation("t", 2, List.of(0, 1), new Random(2));
        var data = Catalyst.<Attribute>list(base.output()).get(1);
        var plan = new Filter(new GreaterThan(data, Literal$.MODULE$.apply(2)), base);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Filter outer
                && outer.child() instanceof Join join
                && join.joinType() == LeftSemi$.MODULE$);
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesFilterOverNullableKeyAlone() {
        var base = Support.relation("t", 2, List.of(1), new Random(3));
        var key = Catalyst.<Attribute>list(base.output()).get(0);
        var plan = new Filter(new GreaterThan(key, Literal$.MODULE$.apply(1)), base);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesFilterOverProjectionAlone() {
        var base = Support.relation("t", 2, List.of(0, 1), new Random(4));
        var key = Catalyst.<Attribute>list(base.output()).get(0);
        var projected = new Project(Catalyst.seq(List.of(key)), base);
        var plan = new Filter(new GreaterThan(key, Literal$.MODULE$.apply(1)), projected);
        assertEquals(plan, rule.apply(plan));
    }
}
