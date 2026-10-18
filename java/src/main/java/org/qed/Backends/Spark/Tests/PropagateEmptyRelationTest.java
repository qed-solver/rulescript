package org.qed.spark;

import org.apache.spark.sql.catalyst.InternalRow;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.EqualTo;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.plans.Inner$;
import org.apache.spark.sql.catalyst.plans.JoinType;
import org.apache.spark.sql.catalyst.plans.LeftOuter$;
import org.apache.spark.sql.catalyst.plans.logical.Join;
import org.apache.spark.sql.catalyst.plans.logical.JoinHint$;
import org.apache.spark.sql.catalyst.plans.logical.LocalRelation;
import org.apache.spark.sql.catalyst.plans.logical.LogicalPlan;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import scala.Option;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class PropagateEmptyRelationTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("PropagateEmptyRelation.json"));

    private static LocalRelation emptyRelation(String name, int arity) {
        var attrs = new ArrayList<Attribute>();
        for (int i = 0; i < arity; i++) {
            attrs.add(Support.attribute(name + "_" + i, true));
        }
        return new LocalRelation(Catalyst.seq(attrs), Catalyst.<InternalRow>seq(List.of()), false, Match.noStream());
    }

    private static Join join(LogicalPlan left, LogicalPlan right, JoinType type) {
        var l = Catalyst.<Attribute>list(left.output()).get(0);
        var r = Catalyst.<Attribute>list(right.output()).get(0);
        return new Join(left, right, type, Option.<Expression>apply(new EqualTo(l, r)), JoinHint$.MODULE$.NONE());
    }

    @Test
    void innerJoinOverEmptyLeftBecomesEmptyWithJoinOutput() {
        var plan = join(emptyRelation("L", 2), Support.relation("R", 2, List.of(), new Random(7)), Inner$.MODULE$);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertInstanceOf(LocalRelation.class, rewritten);
        var expected = new LocalRelation(Catalyst.seq(Catalyst.<Attribute>list(plan.output())),
                Catalyst.<InternalRow>seq(List.of()), false, Match.noStream());
        assertEquals(expected, rewritten);
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void innerJoinOverEmptyLeftWithFourOutputColumnsKeepsEachSide() {
        var plan = join(emptyRelation("L", 2), Support.relation("R", 2, List.of(0), new Random(11)), Inner$.MODULE$);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertEquals(4, Catalyst.list(rewritten.output()).size());
        assertEquals(Catalyst.list(plan.output()), Catalyst.list(rewritten.output()));
    }

    @Test
    void leftOuterJoinOverEmptyLeftIsLeftAlone() {
        var plan = join(emptyRelation("L", 2), Support.relation("R", 2, List.of(), new Random(7)), LeftOuter$.MODULE$);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void innerJoinOverNonEmptyLeftIsLeftAlone() {
        var plan = join(Support.relation("L", 2, List.of(), new Random(5)),
                Support.relation("R", 2, List.of(), new Random(7)), Inner$.MODULE$);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void innerJoinOverEmptyRightOnlyIsLeftAlone() {
        var plan = join(Support.relation("L", 2, List.of(), new Random(5)), emptyRelation("R", 2), Inner$.MODULE$);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void innerJoinOverEmptyLeftOfArityThreeIsLeftAlone() {
        var plan = join(emptyRelation("L", 3), Support.relation("R", 2, List.of(), new Random(7)), Inner$.MODULE$);
        assertEquals(plan, rule.apply(plan));
    }
}
