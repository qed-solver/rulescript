package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
import org.apache.spark.sql.catalyst.expressions.Literal$;
import org.apache.spark.sql.catalyst.plans.logical.Aggregate;
import org.apache.spark.sql.catalyst.plans.logical.AggregateHint;
import org.apache.spark.sql.catalyst.plans.logical.Filter;
import org.apache.spark.sql.catalyst.plans.logical.Union;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import scala.Option;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RemoveNoopUnionTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("RemoveNoopUnion.json"));

    @Test
    void distinctOverUnionOfTheSameRelationBecomesDistinctOfTheRelation() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var union = new Union(Catalyst.seq(List.of(base, base)), false, false);
        var plan = new Aggregate(Catalyst.seq(List.of(x)), Catalyst.seq(List.of(x)), union, Option.<AggregateHint>empty());
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Aggregate a && a.child() == base);
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void distinctOverUnionOfDifferentRelationsIsLeftAlone() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var other = Support.relation("u", 1, List.of(), new Random(2));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var union = new Union(Catalyst.seq(List.of(base, other)), false, false);
        var plan = new Aggregate(Catalyst.seq(List.of(x)), Catalyst.seq(List.of(x)), union, Option.<AggregateHint>empty());
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void distinctOverUnionOfRelationAndItsFilteredCopyIsLeftAlone() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var filtered = new Filter(new GreaterThan(x, Literal$.MODULE$.apply(2)), base);
        var union = new Union(Catalyst.seq(List.of(base, filtered)), false, false);
        var plan = new Aggregate(Catalyst.seq(List.of(x)), Catalyst.seq(List.of(x)), union, Option.<AggregateHint>empty());
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void distinctOverASingleRelationIsLeftAlone() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var plan = new Aggregate(Catalyst.seq(List.of(x)), Catalyst.seq(List.of(x)), base, Option.<AggregateHint>empty());
        assertEquals(plan, rule.apply(plan));
    }
}
