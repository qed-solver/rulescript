package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;
import org.qed.RexRN.Pred;

// SCOPE: PARTIAL — encodes the left-operand-never-null branch of EitherExprIsNeverNull: $left is a never-null (NOT NULL) column and $right is an arbitrary nullable column of the same uninterpreted type
public record SimplifyIsCondition() implements RRule {
    // $input:* — the filtered input relation.
    //   Column 0 is $left, whose never-null status (EitherExprIsNeverNull,
    //   left branch: ExprIsNeverNull($left, NotNullCols $input)) is encoded
    //   as a non-nullable uninterpreted type.
    //   Column 1 is $right, an arbitrary nullable column.
    // IdenticalTypes((TypeOf $left),(TypeOf $right)) is captured by giving
    // both columns the same type symbol "V". Neither operand is a tuple:
    // both are plain scalar columns (uninterpreted non-struct values).
    static final RelRN input = RelRN.scanMany("Input", Seq.of(
            RexRN.varType("V", false),
            RexRN.varType("V", true)));

    // $filters minus $item: the other, arbitrary conjuncts of the filter
    // list, as one uninterpreted predicate over the input's columns
    // (filter lists are conjunctions, so one predicate is equivalent).
    static final RexRN other = input.pred("other");

    // $item: (Is $left $right), i.e. x IS NOT DISTINCT FROM y, in the
    // filtering context where NULL is falsy.
    static final RexRN left = input.field(0);
    static final RexRN right = input.field(1);
    static final RexRN isNotDistinctFrom =
            new Pred(SqlStdOperatorTable.IS_NOT_DISTINCT_FROM, Seq.of(left, right));

    @Override
    public RelRN before() {
        // (Select $input ( ... $item ... ))
        return input.filter(other).filter(isNotDistinctFrom);
    }

    @Override
    public RelRN after() {
        // (Select $input (ReplaceFiltersItem $filters $item (Eq $left $right)))
        //
        // Validity: with $left never null, the row-level truth values agree
        // on every instantiation of $right:
        //   $right = NULL  : left IS NOT DISTINCT FROM right = FALSE ; left = right = NULL (falsy)
        //   $right = non-NULL : left IS NOT DISTINCT FROM right = (left = right); left = right
        return input.filter(other).filter(new Pred(SqlStdOperatorTable.EQUALS, Seq.of(left, right)));
    }
}
