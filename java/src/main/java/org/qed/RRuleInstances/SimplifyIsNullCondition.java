package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;

// SCOPE: FULL
public record SimplifyIsNullCondition() implements RRule {
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

    // $item: (Is (Variable $col) (Null)), i.e. x IS NULL on the
    // NOT NULL-constrained column.
    static final RexRN isNull = input.field(0).pred(SqlStdOperatorTable.IS_NULL);

    @Override
    public RelRN before() {
        // (Select $input ( ... $item ... )) — the IS NULL conjunct
        // alongside the other conjuncts.
        return input.filter(other).filter(isNull);
    }

    @Override
    public RelRN after() {
        // (Select $input [ (FiltersItem (False)) ]) — the whole filter
        // collapses to false, since x IS NULL is unsatisfiable when x is
        // NOT NULL.
        return input.filter(RexRN.falseLiteral());
    }
}
