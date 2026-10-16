// ORIGIN: Spark
// SCOPE: PARTIAL — the source rule's commonApplyFunc covers joins, unions and unary nodes whose children are empty (or a join with a false literal condition); this encoding pins the INNER-join branch with an empty left side (fixed two-column shape per side).
package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.apache.calcite.rel.core.JoinRelType;
import org.qed.RelRN;
import org.qed.RelType;
import org.qed.RRule;

/**
 * Port of the join branch of Spark Catalyst's PropagateEmptyRelation
 * (sql/catalyst/src/main/scala/org/apache/spark/sql/catalyst/optimizer/
 *  PropagateEmptyRelation.scala, lines 219-224, via the base class's
 *  commonApplyFunc): when one side of a join is a known empty LocalRelation
 *  (isEmpty = p.data.isEmpty), the join collapses to an empty LocalRelation
 *  carrying the join's own output schema (empty(p) = LocalRelation(p.output,
 *  data = Seq.empty)).
 *
 * Representative member: an INNER join whose left input is the empty relation.
 * An INNER join requires a matched pair from both sides; with zero left rows no
 * pair can ever form, so the whole join is the empty relation of the join's
 * schema (left columns ++ right columns), regardless of the (irrelevant) join
 * condition.
 */
public record PropagateEmptyRelation() implements RRule {
    static final RelType.VarType l0 = new RelType.VarType("Left_Type0", true);
    static final RelType.VarType l1 = new RelType.VarType("Left_Type1", true);
    static final RelType.VarType r0 = new RelType.VarType("Right_Type0", true);
    static final RelType.VarType r1 = new RelType.VarType("Right_Type1", true);

    // The left input is the empty relation: zero rows of the left side's row
    // type (Spark's LocalRelation with data = Seq.empty, i.e. isEmpty).
    static final RelRN leftEmpty = RelRN.scanMany("Left", Seq.of(l0, l1)).empty();
    static final RelRN right = RelRN.scanMany("Right", Seq.of(r0, r1));

    // A fresh scan whose row type is exactly the join's output row type
    // (left columns ++ right columns); its Empty child is the independent
    // empty relation the rule rewrites to — not a wrapper around the join.
    static final RelRN joinOutSchema = RelRN.scanMany("JoinOut", Seq.of(l0, l1, r0, r1));

    @Override
    public RelRN before() {
        // InnerJoin(Empty(Left), Right) ON <uninterpreted join cond>.
        return leftEmpty.join(JoinRelType.INNER, "join", right);
    }

    @Override
    public RelRN after() {
        // empty(p) = LocalRelation(p.output, data = Seq.empty): an empty
        // relation of the join's output row type (left ++ right), built
        // independently of the join itself.
        return joinOutSchema.empty();
    }
}
