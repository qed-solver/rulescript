package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestFilterCorrelate(t *testing.T) {
	rule := loadRule(t, "FilterCorrelate")
	e := newEnv(t)
	l, lc := e.scan("u", 1)
	r, rc := e.scan("t", 1)
	pLeft := e.f.ConstructGt(e.v(lc[0]), e.num(1))
	pRight := e.f.ConstructGt(e.v(rc[0]), e.num(2))
	pBoth := e.f.ConstructEq(e.v(lc[0]), e.v(rc[0]))
	before := e.f.ConstructSelect(
		e.f.ConstructInnerJoin(l, r, nil, &memo.JoinPrivate{}),
		e.filters(pLeft, pRight, pBoth),
	)
	want := e.f.ConstructSelect(
		e.f.ConstructInnerJoin(
			e.f.ConstructSelect(l, e.filters(pLeft)),
			e.f.ConstructSelect(r, e.filters(pRight)),
			nil,
			&memo.JoinPrivate{},
		),
		e.filters(pBoth),
	)
	e.expectRewrite(rule, before, want)

	e.expectNoMatch(rule, e.f.ConstructSelect(
		e.f.ConstructLeftJoin(l, r, nil, &memo.JoinPrivate{}),
		e.filters(pLeft, pRight, pBoth),
	))
	e.expectNoMatch(rule, e.f.ConstructSelect(
		e.f.ConstructInnerJoin(l, r, e.filters(pBoth), &memo.JoinPrivate{}),
		e.filters(pLeft, pRight),
	))
	e.expectNoMatch(rule, e.f.ConstructSelect(
		e.f.ConstructInnerJoin(l, r, nil, &memo.JoinPrivate{}),
		e.filters(e.f.ConstructOr(pLeft, pRight)),
	))
	wide, wc := e.scan("u", 2)
	e.expectNoMatch(rule, e.f.ConstructSelect(
		e.f.ConstructInnerJoin(wide, r, nil, &memo.JoinPrivate{}),
		e.filters(
			e.f.ConstructGt(e.v(wc[0]), e.num(1)),
			e.f.ConstructGt(e.v(wc[1]), e.num(2)),
			e.f.ConstructEq(e.v(wc[0]), e.v(rc[0])),
		),
	))
}
