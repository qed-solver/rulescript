package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;

// SCOPE: PARTIAL — only the IsNot(Eq, False) -> Eq case, on an INNER join, with both compared columns NOT NULL
public record SimplifyJoinNotNullEquality() implements RRule {
    // The join's two inputs, each a single-column scan whose column is NOT
    // NULL (R_Type/L_Type non-nullable). This encodes the rule's preconditions
    // (IsColNotNull2 $col1 $left $right) and (IsColNotNull2 $col2 $left
    // $right): $col1 is the left input's column, $col2 the right input's.
    static final RelRN left  = RelRN.scan("L", RexRN.varType("L_Type", false), false);
    static final RelRN right = RelRN.scan("R", RexRN.varType("R_Type", false), false);

    // $eq:(Eq $col1 $col2), referenced by ordinal from above the join.
    static final RexRN eq = new RexRN.Pred(
            SqlStdOperatorTable.EQUALS,
            Seq.of(left.joinField(0, right), left.joinField(1, right)));

    // The matched filter item: $condition:(IsNot $eq (False)), i.e. (a=b) IS NOT FALSE.
    // The rule's own example case: SimplifyNotNullEquality(eq, IsNotOp, FalseOp) = eq.
    static final RexRN item = eq.pred(SqlStdOperatorTable.IS_NOT_FALSE);

    // The other, arbitrary ON-filter items (... ... around $item in $on),
    // abstracted as one uninterpreted predicate over the join's columns.
    static final RexRN rest = left.joinPred("rest", right);

    @Override
    public RelRN before() {
        // (Join $left $right [ ... $item ... ] $private)
        return left.join(JoinRelType.INNER, RexRN.and(item, rest), right);
    }

    @Override
    public RelRN after() {
        // (Join $left $right [ ... (SimplifyNotNullEquality $eq IsNotOp FalseOp) ... ] $private)
        return left.join(JoinRelType.INNER, RexRN.and(eq, rest), right);
    }
}