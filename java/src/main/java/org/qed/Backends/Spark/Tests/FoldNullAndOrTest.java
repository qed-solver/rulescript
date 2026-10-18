package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.And;
import org.apache.spark.sql.catalyst.expressions.Literal;
import org.apache.spark.sql.catalyst.expressions.Or;
import org.apache.spark.sql.catalyst.plans.logical.Filter;
import org.apache.spark.sql.types.BooleanType$;
import org.apache.spark.sql.types.IntegerType$;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class FoldNullAndOrTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("FoldNullAndOr.json"));

    @Test
    void foldsAndOfTwoBooleanNullsIntoOneNull() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var nullBool = new Literal(null, BooleanType$.MODULE$);
        var plan = new Filter(new And(nullBool, new Literal(null, BooleanType$.MODULE$)), base);
        var expected = new Filter(new Literal(null, BooleanType$.MODULE$), base);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertEquals(expected, rewritten);
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesOrOfTwoBooleanNullsAlone() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var plan = new Filter(new Or(new Literal(null, BooleanType$.MODULE$), new Literal(null, BooleanType$.MODULE$)), base);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesAndOfTwoNullsOfAnotherTypeAlone() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var plan = new Filter(new And(new Literal(null, IntegerType$.MODULE$), new Literal(null, IntegerType$.MODULE$)), base);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesAndOfTwoNullsOverAnotherArityAlone() {
        var base = Support.relation("t", 2, List.of(), new Random(1));
        var nullBool = new Literal(null, BooleanType$.MODULE$);
        var plan = new Filter(new And(nullBool, new Literal(null, BooleanType$.MODULE$)), base);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesASingleNullConditionAlone() {
        var base = Support.relation("t", 1, List.of(), new Random(1));
        var plan = new Filter(new Literal(null, BooleanType$.MODULE$), base);
        assertEquals(plan, rule.apply(plan));
    }
}
