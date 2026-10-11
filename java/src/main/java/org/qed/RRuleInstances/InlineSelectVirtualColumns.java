package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;
import org.qed.RelType;
import org.qed.RuleBuilder;

// SCOPE: PARTIAL — one virtual (synthesized) column and one non-virtual synthesized column with two uninterpreted passthrough columns, one filter inlined into the scan below the Project (an uninterpreted predicate over the virtual column's defining expression) and one filter left above the Project; the VirtualColumns/ColsAreEmpty/IsFilterEmpty matching guards are not bag-semantic preconditions.
public record InlineSelectVirtualColumns() implements RRule {
    // The scan: two physical columns a, b (as in the rule's example).
    static final RelType.VarType aT = new RelType.VarType("A_Type", true);
    static final RelType.VarType bT = new RelType.VarType("B_Type", true);
    static final RelRN scan = RelRN.scanMany("Source", Seq.of(aT, bT));

    // The Project on the scan, as produced by optbuilder for a query with a
    // virtual column. Two synthesized columns and two passthrough columns:
    //  - f = F(a, b): the virtual column's DEFINING expression (e.g. `abs(a) AS v`);
    //  - g = G(a, b): the non-virtual synthesized column (e.g. `abs(b) AS w`);
    //  - a, b passed through so the defining expressions can be inlined on the scan.
    static final RexRN f = scan.proj("F", "F_Type");
    static final RexRN g = scan.proj("G", "G_Type");
    static final RelRN project = scan.project(Seq.of(f, g, scan.field(0), scan.field(1)));

    // The Select's filters (the rule's example: v = 5 AND w = 10):
    //  - vFilter: references the virtual column (output position 0). The rule
    //    INLINES it below the Project: the same uninterpreted predicate P applied
    //    to the virtual column's defining expression f, not to the column itself.
    //  - wFilter: references the non-virtual synthesized column (output position
    //    1). The rule deliberately does NOT inline it (the expression would be
    //    computed twice); it stays above the Project.
    static final RexRN vFilter = new RexRN.Pred(
            RuleBuilder.create().genericPredicateOp("P", true), Seq.of(project.field(0)));
    static final RexRN wFilter = new RexRN.Pred(
            RuleBuilder.create().genericPredicateOp("Q", true), Seq.of(project.field(1)));
    static final RexRN allFilters = RexRN.and(vFilter, wFilter);

    // Before: Filter(P(v) AND Q(w), Project(F(a,b), G(a,b), a, b, scan)).
    static final RelRN before = project.filter(allFilters);

    // After: the inlined filter P(F(a,b)) references only the scan's physical
    // columns, so it sits on the scan below the Project; the same uninterpreted
    // predicate symbols P and Q are reused on both sides. The non-inlinable
    // filter Q(w) is re-applied above the Project to the same synthesized column.
    static final RelRN selectInner = scan.filter(
            new RexRN.Pred(RuleBuilder.create().genericPredicateOp("P", true), Seq.of(f)));
    static final RelRN projectAfter = selectInner.project(Seq.of(f, g, scan.field(0), scan.field(1)));
    static final RelRN after = projectAfter.filter(wFilter);

    @Override
    public RelRN before() {
        return before;
    }

    @Override
    public RelRN after() {
        return after;
    }
}