package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
import org.apache.spark.sql.catalyst.expressions.Literal;
import org.apache.spark.sql.catalyst.expressions.Literal$;
import org.apache.spark.sql.catalyst.expressions.Not;
import org.apache.spark.sql.catalyst.plans.logical.Filter;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FoldNotFalseTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("FoldNotFalse.json"));

    @Test
    void replacesNotFalseConditionWithTrue() {
        var base = Support.relation("Source", 1, List.of(), new Random(1));
        var plan = new Filter(new Not(Literal$.MODULE$.FalseLiteral()), base);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Filter f
                && f.condition() instanceof Literal l && Boolean.TRUE.equals(l.value())
                && f.child().output().equals(base.output()));
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesBareFalseConditionAlone() {
        var base = Support.relation("Source", 1, List.of(), new Random(1));
        var plan = new Filter(Literal$.MODULE$.FalseLiteral(), base);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesNotTrueConditionAlone() {
        var base = Support.relation("Source", 1, List.of(), new Random(1));
        var plan = new Filter(new Not(Literal$.MODULE$.TrueLiteral()), base);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesNotOfNonLiteralAlone() {
        var base = Support.relation("Source", 1, List.of(), new Random(1));
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        var plan = new Filter(new Not(new GreaterThan(x, Literal$.MODULE$.apply(2))), base);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesNotFalseOverTwoColumnRelationAlone() {
        var base = Support.relation("Source", 2, List.of(), new Random(1));
        var plan = new Filter(new Not(Literal$.MODULE$.FalseLiteral()), base);
        assertEquals(plan, rule.apply(plan));
    }
}
