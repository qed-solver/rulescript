package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestPruneSemiAntiJoinRightCols(t *testing.T) {
	rule := loadRule(t, "PruneSemiAntiJoinRightCols")
	e := newEnv(t)
	l, lc := e.scan("t", 1)
	r, rc := e.scan("u", 2)
	on := func() memo.FiltersExpr { return e.filters(e.f.ConstructEq(e.v(lc[0]), e.v(rc[0]))) }
	before := e.f.ConstructAntiJoin(l, r, on(), &memo.JoinPrivate{})
	rightProject := e.f.ConstructProject(r, nil, opt.MakeColSet(rc[0]))
	want := e.f.ConstructAntiJoin(l, rightProject, on(), &memo.JoinPrivate{})
	e.expectRewrite(rule, before, want)

	e.expectNoMatch(rule, e.f.ConstructSemiJoin(l, r, on(), &memo.JoinPrivate{}))
	e.expectNoMatch(rule, e.f.ConstructAntiJoin(l, r,
		e.filters(e.f.ConstructEq(e.v(lc[0]), e.v(rc[1]))), &memo.JoinPrivate{}))
	r1, _ := e.scan("u", 1)
	e.expectNoMatch(rule, e.f.ConstructAntiJoin(l, r1, nil, &memo.JoinPrivate{}))
}
