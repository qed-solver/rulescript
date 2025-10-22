// Merges a filter above an inner join into the join condition
// Pattern: Filter(pred, Join(Inner, cond, L, R)) → Join(Inner, cond AND pred, L, R)
crate::rule! {
    FilterIntoJoinRule {
        schemas: {
            left: (l: TL),
            right: (r: TR),
        },
        functions: {
            JoinCond(TL, TR) -> Bool,
            FilterPred(TL, TR) -> Bool,
        },
        from: {
            let join = crate::join!(left, right, Inner, JoinCond(l, r));
            crate::filter!(join, FilterPred(l, r))
        },
        to: crate::join!(left, right, Inner, JoinCond(l, r) && FilterPred(l, r)),
    }
}
