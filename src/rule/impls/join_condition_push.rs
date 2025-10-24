// Pushes single-table predicates from join condition down as filters on inputs
// Pattern: Join(Inner, LeftCond ∧ RightCond ∧ CrossCond, L, R) → Join(Inner, CrossCond, Filter(LeftCond, L), Filter(RightCond, R))
crate::rule! {
    JoinConditionPushRule {
        schemas: {
            left: (l: TL),
            right: (r: TR),
        },
        functions: {
            LeftCond(TL) -> Bool,
            RightCond(TR) -> Bool,
            CrossCond(TL, TR) -> Bool,
        },
        from: crate::join!(
            left,
            right,
            Inner,
            LeftCond(l) && RightCond(r) && CrossCond(l, r)
        ),
        to: {
            let filtered_left = crate::filter!(left, LeftCond(l));
            let filtered_right = crate::filter!(right, RightCond(r));
            crate::join!(filtered_left, filtered_right, Inner, CrossCond(l, r))
        },
    }
}
