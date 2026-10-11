package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;
import org.qed.RelType;
import org.qed.RuleBuilder;

// SCOPE: PARTIAL — two synthesized (inlinable) columns and two uninterpreted passthrough columns, with every Select filter inlined below the Project (an uninterpreted predicate over each column's defining expression) and no filter left above the Project; the CanInlineProjections/FilterHasCorrelatedSubquery matching guards are not bag-semantic preconditions.
public record PushSelectIntoInlinableProject() implements RRule {
    // The input relation: two physical columns a, b (as in the rule's example).
    static final RelType.VarType aT = new RelType.VarType("A_Type", true);
    static final RelType.VarType bT = new RelType.VarType("B_Type", true);
    static final RelRN scan = RelRN.scanMany("Source", Seq.of(aT, bT));

    // The Project on the scan: two inlinable synthesized columns
    //  - f = F(a, b): first projected column (e.g. `x+1 AS x2`);
    //  - g = G(a, b): second projected column;
    //  - a, b passed through so the defining expressions can be inlined on the input.
    static final RexRN f = scan.proj("F", "F_Type");
    static final RexRN g = scan.proj("G", "G_Type");
    static final RelRN project = scan.project(Seq.of(f, g, scan.field(0), scan.field(1)));

    // The Select's filters: each references a projected (synthesized) column.
    // The rule inlines ALL of them below the Project, applying the same
    // uninterpreted predicates to the defining expressions instead of the columns.
    static final RexRN pFilter = new RexRN.Pred(
            RuleBuilder.create().genericPredicateOp("P", true), Seq.of(project.field(0)));
    static final RexRN qFilter = new RexRN.Pred(
            RuleBuilder.create().genericPredicateOp("Q", true), Seq.of(project.field(1)));
    static final RexRN allFilters = RexRN.and(pFilter, qFilter);

    // Before: Filter(P(f) AND Q(g), Project(F(a,b), G(a,b), a, b, scan)).
    static final RelRN before = project.filter(allFilters);

    // After: every filter is inlined onto the input — the same uninterpreted
    // predicate symbols P and Q now applied to the defining expressions f and g,
    // so the Select no longer references the Project's output and the Project
    // moves back on top.
    static final RelRN selectInner = scan.filter(RexRN.and(
            new RexRN.Pred(RuleBuilder.create().genericPredicateOp("P", true), Seq.of(f)),
            new RexRN.Pred(RuleBuilder.create().genericPredicateOp("Q", true), Seq.of(g))));
    static final RelRN after = selectInner.project(Seq.of(f, g, scan.field(0), scan.field(1)));

    @Override
    public RelRN before() {
        return before;
    }

    @Override
    public RelRN after() {
        return after;
    }
}