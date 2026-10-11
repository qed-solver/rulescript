package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestFilterCorrelate(t *testing.T) {
	rule := loadRule(t, "FilterCorrelate")
	e := newEnv(t)
	l, lc := e.scan("t", 1)
	r, rc := e.scan("u", 1)

	pLeft := e.f.ConstructGt(e.v(lc[0]), e.num(1))
	pRight := e.f.ConstructLt(e.v(rc[0]), e.num(5))
	pBoth := e.f.ConstructEq(e.v(lc[0]), e.v(rc[0]))

	join := e.f.ConstructInnerJoin(l, r, nil, &memo.JoinPrivate{})
	before := e.f.ConstructSelect(join, e.filters(pLeft, pRight, pBoth))
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

	leftJoin := e.f.ConstructLeftJoin(l, r, nil, &memo.JoinPrivate{})
	e.expectNoMatch(rule, e.f.ConstructSelect(leftJoin, e.filters(pLeft, pRight, pBoth)))
	e.expectNoMatch(rule, e.f.ConstructSelect(join, e.filters(pRight, pLeft, pBoth)))
	e.expectNoMatch(rule, e.f.ConstructSelect(join, e.filters(pLeft, pRight)))
}
