package org.qed.RRuleInstances;

import org.qed.RelRN;
import org.qed.RRule;

public record ProjectRemove() implements RRule {
    static final RelRN source = RelRN.scan("Source", "Source_Type");

    @Override
    public RelRN before() {
        return source.identityProject();
    }

    @Override
    public RelRN after() {
        return source;
    }
}
