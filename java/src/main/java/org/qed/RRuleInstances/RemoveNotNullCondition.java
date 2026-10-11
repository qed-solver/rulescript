package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;

// SCOPE: FULL
public record RemoveNotNullCondition() implements RRule {
    // $input:* — the filtered input relation. Column 0 is the $col from the
    // source rule, whose NOT NULL constraint (IsColNotNull $col $input) is
    // encoded as a non-nullable uninterpreted type; column 1 is an arbitrary
    // (nullable) stand-in for any other column of $input the remaining
    // filter conjuncts may reference.
    static final RelRN input = RelRN.scanMany("Input", Seq.of(
            RexRN.varType("Col_Type", false),
            RexRN.varType("Other_Type", true)));

    // $filters minus $item: the other, arbitrary conjuncts of the filter
    // list, as one uninterpreted predicate over the input's columns.
    static final RexRN other = input.pred("other");

    // $item: (IsNot (Variable $col) (Null)), i.e. x IS NOT NULL on the
    // NOT NULL-constrained column.
    static final RexRN isNotNull = input.field(0).pred(SqlStdOperatorTable.IS_NOT_NULL);

    @Override
    public RelRN before() {
        // (Select $input ( ... $item ... )) — the IS NOT NULL conjunct
        // alongside the other conjuncts.
        return input.filter(other).filter(isNotNull);
    }

    @Override
    public RelRN after() {
        // (Select $input (RemoveFiltersItem $filters $item)) — the IS
        // NOT NULL conjunct removed, everything else untouched.
        return input.filter(other);
    }
}
