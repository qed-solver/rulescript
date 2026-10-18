package org.qed.spark;

import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.Exists;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.Literal$;
import org.apache.spark.sql.catalyst.expressions.Not;
import org.apache.spark.sql.catalyst.expressions.NamedExpression$;
import org.apache.spark.sql.catalyst.plans.logical.Filter;
import org.apache.spark.sql.catalyst.plans.logical.HintInfo;
import org.apache.spark.sql.catalyst.plans.logical.LocalRelation;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import scala.Option;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EliminateExistsZeroRowsTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("EliminateExistsZeroRows.json"));

    private static LocalRelation emptyRelation(String name, int arity) {
        var attrs = Catalyst.<Attribute>list(Support.relation(name, arity, List.of(), new Random(1)).output());
        return new LocalRelation(Catalyst.seq(attrs), Catalyst.<org.apache.spark.sql.catalyst.InternalRow>seq(List.of()),
                false, Match.noStream());
    }

    private static Exists exists(LocalRelation subquery) {
        return new Exists(subquery, Catalyst.<Expression>seq(List.of()), NamedExpression$.MODULE$.newExprId(),
                Catalyst.<Expression>seq(List.of()), Option.<HintInfo>empty());
    }

    @Test
    void replacesExistsOverEmptyInputWithFalse() {
        var outer = Support.relation("outer", 1, List.of(), new Random(1));
        var plan = new Filter(exists(emptyRelation("inner", 1)), outer);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Filter f
                && f.condition().equals(Literal$.MODULE$.FalseLiteral())
                && f.child() instanceof LocalRelation child
                && child.output().equals(outer.output()));
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void leavesExistsOverEmptyInputAloneWhenOuterArityIsNotOne() {
        var outer = Support.relation("outer", 2, List.of(), new Random(2));
        var plan = new Filter(exists(emptyRelation("inner", 1)), outer);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesExistsOverNonEmptyInputAlone() {
        var outer = Support.relation("outer", 1, List.of(), new Random(1));
        var inner = Support.relation("inner", 1, List.of(), new Random(3));
        var plan = new Filter(exists(inner), outer);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void leavesNotExistsOverEmptyInputAlone() {
        var outer = Support.relation("outer", 1, List.of(), new Random(1));
        var plan = new Filter(new Not(exists(emptyRelation("inner", 1))), outer);
        assertEquals(plan, rule.apply(plan));
    }
}
