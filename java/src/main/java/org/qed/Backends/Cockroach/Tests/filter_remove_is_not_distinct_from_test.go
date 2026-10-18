package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestFilterRemoveIsNotDistinctFrom(t *testing.T) {
	rule := loadRule(t, "FilterRemoveIsNotDistinctFrom")
	e := newEnv(t)
	l, lc := e.scan("u", 1)
	r, rc := e.scan("t", 1)
	join := e.f.ConstructInnerJoin(l, r, nil, &memo.JoinPrivate{})
	x := e.v(lc[0])
	y := e.v(rc[0])
	nullInt := func() opt.ScalarExpr { return e.f.ConstructNull(types.Int) }
	before := e.f.ConstructSelect(join, e.filters(e.f.ConstructIs(x, y)))
	after := e.f.ConstructSelect(join, e.filters(e.f.ConstructOr(
		e.f.ConstructAnd(
			e.f.ConstructIs(x, nullInt()),
			e.f.ConstructIs(y, nullInt()),
		),
		e.f.ConstructIs(e.f.ConstructEq(x, y), e.f.ConstructTrue()),
	)))
	e.expectRewrite(rule, before, after)

	e.expectNoMatch(rule, e.f.ConstructSelect(join, e.filters(e.f.ConstructEq(x, y))))

	leftJoin := e.f.ConstructLeftJoin(l, r, nil, &memo.JoinPrivate{})
	e.expectNoMatch(rule, e.f.ConstructSelect(leftJoin, e.filters(e.f.ConstructIs(x, y))))

	wide, wc := e.scan("t", 2)
	wideJoin := e.f.ConstructInnerJoin(wide, r, nil, &memo.JoinPrivate{})
	e.expectNoMatch(rule, e.f.ConstructSelect(wideJoin, e.filters(e.f.ConstructIs(e.v(wc[0]), e.v(rc[0])))))
}
