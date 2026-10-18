package org.qed.spark;

import org.apache.spark.sql.catalyst.InternalRow;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.EqualTo;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.GenericInternalRow;
import org.apache.spark.sql.catalyst.expressions.NamedExpression;
import org.apache.spark.sql.catalyst.plans.Inner$;
import org.apache.spark.sql.catalyst.plans.JoinType;
import org.apache.spark.sql.catalyst.plans.LeftOuter$;
import org.apache.spark.sql.catalyst.plans.logical.Aggregate;
import org.apache.spark.sql.catalyst.plans.logical.AggregateHint;
import org.apache.spark.sql.catalyst.plans.logical.Join;
import org.apache.spark.sql.catalyst.plans.logical.JoinHint;
import org.apache.spark.sql.catalyst.plans.logical.LocalRelation;
import org.apache.spark.sql.catalyst.plans.logical.LogicalPlan;
import org.apache.spark.sql.catalyst.plans.logical.Project;
import org.junit.jupiter.api.Assumptions;
import scala.Option;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectJoinJoinRemoveTest {
    private final RuleScriptRule rule = RuleScriptRule.load(Support.generated().resolve("ProjectJoinJoinRemove.json"));

    private static LocalRelation table(String name, boolean nullable, int[] values) {
        var attr = Support.attribute(name, nullable);
        var rows = new ArrayList<InternalRow>();
        for (var value : values) {
            rows.add(new GenericInternalRow(new Object[] {value}));
        }
        return new LocalRelation(Catalyst.seq(List.of(attr)), Catalyst.seq(rows), false, Match.noStream());
    }

    private static LogicalPlan keyed(String name, boolean nullable, int[] values) {
        var relation = table(name, nullable, values);
        var attr = Catalyst.<Attribute>list(relation.output()).get(0);
        return new Aggregate(Catalyst.<Expression>seq(List.of(attr)), Catalyst.<NamedExpression>seq(List.of(attr)), relation,
                Option.<AggregateHint>empty());
    }

    private static LogicalPlan chain(JoinType bottomType, JoinType topType, LogicalPlan t, LogicalPlan t2, LogicalPlan c) {
        var tOut = Catalyst.<Attribute>list(t.output());
        var t2Out = Catalyst.<Attribute>list(t2.output());
        var cOut = Catalyst.<Attribute>list(c.output());
        var bottom = new Join(t, t2, bottomType,
                Catalyst.<Expression>some(new EqualTo(tOut.get(0), t2Out.get(0))), JoinHint.NONE());
        var bottomOut = Catalyst.<Attribute>list(bottom.output());
        var top = new Join(bottom, c, topType,
                Catalyst.<Expression>some(new EqualTo(bottomOut.get(0), cOut.get(0))), JoinHint.NONE());
        var topOut = Catalyst.<Attribute>list(top.output());
        return new Project(Catalyst.<NamedExpression>seq(List.of(topOut.get(0), topOut.get(2))), top);
    }

    @Test
    void collapsesSelfLeftJoinOnUniqueKeyIntoDirectLeftJoin() {
        var t = keyed("t", false, new int[] {0, 1, 2, 3});
        var t2 = Catalyst.fresh(t);
        var c = Support.relation("c", 1, List.of(0), new Random(2));
        var plan = chain(LeftOuter$.MODULE$, LeftOuter$.MODULE$, t, t2, c);
        var rewritten = rule.apply(plan);
        assertNotEquals(plan, rewritten);
        assertTrue(rewritten instanceof Project p && p.child() instanceof Join j
                && j.joinType() == LeftOuter$.MODULE$ && j.left().sameResult(t) && j.right().sameResult(c)
                && j.condition().isDefined() && j.condition().get() instanceof EqualTo);
        Assumptions.assumeTrue(Support.canExecute());
        assertEquals(Support.run(plan), Support.run(rewritten));
    }

    @Test
    void keepsInnerTopJoin() {
        var t = keyed("t", false, new int[] {0, 1, 2, 3});
        var t2 = Catalyst.fresh(t);
        var c = Support.relation("c", 1, List.of(0), new Random(2));
        var plan = chain(LeftOuter$.MODULE$, Inner$.MODULE$, t, t2, c);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void keepsInnerBottomJoin() {
        var t = keyed("t", false, new int[] {0, 1, 2, 3});
        var t2 = Catalyst.fresh(t);
        var c = Support.relation("c", 1, List.of(0), new Random(2));
        var plan = chain(Inner$.MODULE$, LeftOuter$.MODULE$, t, t2, c);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void keepsNullableKeyOfSelfJoin() {
        var t = keyed("t", true, new int[] {0, 1, 2, 3});
        var t2 = Catalyst.fresh(t);
        var c = Support.relation("c", 1, List.of(0), new Random(2));
        var plan = chain(LeftOuter$.MODULE$, LeftOuter$.MODULE$, t, t2, c);
        assertEquals(plan, rule.apply(plan));
    }

    @Test
    void keepsNonUniqueSelfJoinSource() {
        var t = table("t", false, new int[] {0, 0, 1, 2});
        var t2 = Catalyst.fresh(t);
        var c = Support.relation("c", 1, List.of(0), new Random(2));
        var plan = chain(LeftOuter$.MODULE$, LeftOuter$.MODULE$, t, t2, c);
        assertEquals(plan, rule.apply(plan));
    }
}
