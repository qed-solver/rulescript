package org.qed.spark;

import org.apache.spark.sql.catalyst.InternalRow;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
import org.apache.spark.sql.catalyst.expressions.Literal$;
import org.apache.spark.sql.catalyst.plans.logical.Filter;
import org.apache.spark.sql.catalyst.plans.logical.LocalRelation;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RemoveZeroCardLockTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("RemoveZeroCardLock.json"));

    private static LocalRelation emptyRelation(int arity) {
        var attrs = new java.util.ArrayList<Attribute>();
        for (int i = 0; i < arity; i++) {
            attrs.add(Support.attribute("empty_" + i, true));
        }
        return new LocalRelation(Catalyst.seq(attrs), Catalyst.<InternalRow>seq(List.of()), false, Match.noStream());
    }

    @Test
    void removesLockOverEmptyInput() {
        var empty = emptyRelation(1);
        var x = Catalyst.<Attribute>list(empty.output()).get(0);
        var plan = new Filter(new GreaterThan(x, Literal$.MODULE$.apply(2)), empty);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof LocalRelation);
        var result = (LocalRelation) rewritten;
        assertTrue(result.data().isEmpty());
        assertEquals(1, result.output().size());
        assertEquals(empty.output().head().exprId(), result.output().head().exprId());
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesFilterOverNonEmptyInputAlone() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var plan = new Filter(new GreaterThan(x, Literal$.MODULE$.apply(2)), base);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesFilterOverEmptyInputOfOtherArityAlone() {
        var empty = emptyRelation(2);
        var x = Catalyst.<Attribute>list(empty.output()).get(0);
        var plan = new Filter(new GreaterThan(x, Literal$.MODULE$.apply(2)), empty);
        assertEquals(plan, rule.apply(plan));
    }
}
