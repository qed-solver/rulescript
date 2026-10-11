package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.SqlOperator;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RelRN;
import org.qed.RRule;
import org.qed.RexRN;
import org.qed.RexRN.Pred;
import org.qed.RuleBuilder;

// SCOPE: PARTIAL — the left input's inlinable constant column is modeled as an INNER equality join a = c (concrete EQUALS) against a unique single-column relation (the DSL has no typed constant literals), and one occurrence of the variable column in one uninterpreted INNER join conjunct is inlined to the constant column.
public record InlineJoinConstantsLeft() implements RRule {
    // Scan of a table with one variable column `a` (the column whose value is
    // restricted to a constant).
    static final RelRN source = RelRN.scan("Source", "Source_Type");

    // The constant `c` modeled as a unique single-column relation (a constant
    // value => at most one row, i.e. its column is a key).
    static final RelRN constRel = RelRN.scan("Const", RexRN.varType("Const_Type", false), true);

    // Row references of the left input's join: field 0 = a (variable column),
    // field 1 = c (constant column).
    static final RexRN aRef = source.joinField(0, constRel);
    static final RexRN cRef = source.joinField(1, constRel);

    // The equality join condition a = c restricting `a` to the constant `c`.
    static final RexRN eqCond = new Pred(SqlStdOperatorTable.EQUALS, Seq.of(aRef, cRef));

    // The left input of the outer join: its column 1 is the inlinable constant
    // (and column 0 is the variable, equal to it on every row).
    static final RelRN left = source.join(JoinRelType.INNER, eqCond, constRel);

    // The right input of the outer join.
    static final RelRN right = RelRN.scan("Right", "Right_Type");

    // Uninterpreted join predicate, e.g. `a = r` or `f(a, r)`.
    static final SqlOperator hOp = RuleBuilder.create().genericPredicateOp("H", true);

    // Row references of the outer join's row: field 0 = a (left variable
    // column), field 1 = c (left constant column), field 2 = right's column
    // (right ordinals are shifted by the left's column count).
    static final RexRN leftVarRef = left.joinField(0, right);
    static final RexRN leftConstRef = left.joinField(1, right);
    static final RexRN rightRef = left.joinField(2, right);

    // The join condition before inlining: references the variable column.
    static final RexRN onBefore = new Pred(hOp, Seq.of(leftVarRef, rightRef));

    // The join condition after inlining: the variable reference is replaced by
    // the constant column.
    static final RexRN onAfter = new Pred(hOp, Seq.of(leftConstRef, rightRef));

    @Override
    public RelRN before() {
        // Join(left, right, H(a, r)): the join condition still references the
        // variable column `a` projected by the left input.
        return left.join(JoinRelType.INNER, onBefore, right);
    }

    @Override
    public RelRN after() {
        // Join(left, right, H(c, r)): the reference to `a` has been inlined to
        // the constant column `c`. Under the left input's a = c, H(a, r) and
        // H(c, r) evaluate identically for every row pair, so the join bags
        // are equal.
        return left.join(JoinRelType.INNER, onAfter, right);
    }
}