package org.qed.RRuleInstances;

import org.qed.RRule;
import org.qed.RelRN;

// ORIGIN: Spark
// SCOPE: PARTIAL — only the no-op Project elimination sub-case is encoded, with the identity project over a single-column input (the rule's no-op Window elimination is inexpressible, since QED has no Window operator)
public record RemoveNoopOperators() implements RRule {
    static final RelRN source = RelRN.scan("Source", "Source_Type");

    // A no-op (identity) project: it merely returns its child's field, so
    // child.sameOutput(project) holds and the project can be dropped.
    static final RelRN noopProject = source.project(source.field(0));

    @Override
    public RelRN before() {
        return noopProject;
    }

    @Override
    public RelRN after() {
        return source;
    }
}