package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.SqlOperator;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;
import org.qed.RuleBuilder;

// ORIGIN: CockroachDB
// SCOPE: PARTIAL — one column each for the ProjectSet input and the zip emission; the zip is uncorrelated with the input (the rule's IsZipCorrelated guard, negated) so the emitted-set membership M ranges over the emitted column only, not the input; ProjectSet is abstracted as an INNER join under M and the ConstructNoColsRow-ProjectSet as the M-filtered emission; the input:^(Values) side condition and EmptyJoinPrivate are not modeled (the empty ON is a cross join); these do not affect bag-semantic equivalence.
public record DecorrelateProjectSet() implements RRule {
    // $input: one column, a generic relation. (The rule's input:^(Values) side
    // condition only excludes the degenerate constant-input case and does not
    // affect bag-semantic equivalence, so it is not modeled.)
    static final RelRN L = RelRN.scan("L", "L_Type");

    // E: the rows the zip emits for one input row. ProjectSet($input, $zip) is
    // abstracted as an INNER join of the input with E under an uninterpreted
    // membership predicate M (which emitted rows belong to the input row).
    static final RelRN E = RelRN.scan("E", "E_Type");

    // The zip is UNCORRELATED with $input's output columns (the rule's premise,
    // IsZipCorrelated guard negated): the emitted set does not depend on the
    // input's value. Hence M is a predicate over the E (emitted) column only,
    // not the input column. Shared across before() and after().
    static final SqlOperator mOp = RuleBuilder.create().genericPredicateOp("M", true);

    @Override
    public RelRN before() {
        // ProjectSet(L, zip) = { (r, e) : r in L, e in E, M(e) }.
        // M references only the E column (index 1) of the [L, E] join row.
        RexRN m = new RexRN.Pred(mOp, Seq.of(L.joinField(1, E)));
        return L.join(JoinRelType.INNER, m, E);
    }

    @Override
    public RelRN after() {
        // ProjectSet(ConstructNoColsRow, zip) = the emitted set computed once
        // against a single empty row = E filtered by M (same emitted rows).
        RexRN m2 = new RexRN.Pred(mOp, Seq.of(E.field(0)));
        RelRN standalone = E.filter(m2);
        // InnerJoin($input, ProjectSet(...), [], EmptyJoinPrivate): empty ON =
        // cross join. Row [L, E].
        return L.join(JoinRelType.INNER, RexRN.trueLiteral(), standalone);
    }
}
