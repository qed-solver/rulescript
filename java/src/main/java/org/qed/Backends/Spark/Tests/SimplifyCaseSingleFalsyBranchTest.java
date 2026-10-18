package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.And;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.GreaterThan;
import org.apache.spark.sql.catalyst.expressions.Literal;
import org.apache.spark.sql.catalyst.expressions.Literal$;
import org.apache.spark.sql.catalyst.expressions.Not;
import org.apache.spark.sql.catalyst.expressions.Or;
import org.apache.spark.sql.catalyst.plans.logical.Filter;
import org.apache.spark.sql.catalyst.plans.logical.LocalRelation;
import org.apache.spark.sql.types.BooleanType$;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimplifyCaseSingleFalsyBranchTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("SimplifyCaseSingleFalsyBranch.json"));

    private static Literal nullBoolean() {
        return new Literal(null, BooleanType$.MODULE$);
    }

    private static LocalRelation base() {
        return Support.relation("t", 1, List.of(), new Random(1));
    }

    private static Expression condition(LocalRelation base) {
        var x = Catalyst.<Attribute>list(base.output()).get(0);
        return new GreaterThan(x, Literal$.MODULE$.apply(2));
    }

    private static Expression caseLike(Expression cond, Expression whenValue, Expression elseValue) {
        return new Or(new And(cond, whenValue), new And(new Not(cond), elseValue));
    }

    @Test
    void replacesCaseWithNullConditionAndKeepsRows() {
        var base = base();
        var plan = new Filter(caseLike(condition(base), Literal$.MODULE$.FalseLiteral(), nullBoolean()), base);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Filter f && f.child() == base);
        assertEquals(nullBoolean(), ((Filter) rewritten).condition());
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesCaseWithTrueWhenValueAlone() {
        var base = base();
        var plan = new Filter(caseLike(condition(base), Literal$.MODULE$.TrueLiteral(), nullBoolean()), base);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesCaseWithNonNullElseValueAlone() {
        var base = base();
        var plan = new Filter(caseLike(condition(base), Literal$.MODULE$.FalseLiteral(), Literal$.MODULE$.FalseLiteral()), base);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesCaseWithoutNegatedConditionAlone() {
        var base = base();
        var cond = condition(base);
        var plan = new Filter(new Or(new And(cond, Literal$.MODULE$.FalseLiteral()), new And(cond, nullBoolean())), base);
        assertEquals(plan, rule.apply(plan));
    }
}
