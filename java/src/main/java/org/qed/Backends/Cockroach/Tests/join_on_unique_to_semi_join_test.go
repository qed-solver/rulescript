package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestJoinOnUniqueToSemiJoin(t *testing.T) {
	rule := loadRule(t, "JoinOnUniqueToSemiJoin")
	e := newEnv(t)
	l, lc := e.scan("t", 1)
	r, rc := e.scan("u", 1)
	on := func() memo.FiltersExpr { return e.filters(e.f.ConstructEq(e.v(lc[0]), e.v(rc[0]))) }
	before := e.f.ConstructProject(
		e.f.ConstructInnerJoin(l, r, on(), &memo.JoinPrivate{}), nil, opt.MakeColSet(lc[0]))
	want := e.f.ConstructProject(
		e.f.ConstructSemiJoin(l, r, on(), &memo.JoinPrivate{}), nil, opt.MakeColSet(lc[0]))
	e.expectRewrite(rule, before, want)

	e.expectNoMatch(rule, e.f.ConstructProject(
		e.f.ConstructLeftJoin(l, r, on(), &memo.JoinPrivate{}), nil, opt.MakeColSet(lc[0])))

	e.expectNoMatch(rule, e.f.ConstructProject(
		e.f.ConstructInnerJoin(l, r,
			e.filters(e.f.ConstructLt(e.v(lc[0]), e.v(rc[0]))), &memo.JoinPrivate{}),
		nil, opt.MakeColSet(lc[0])))

	ts, tc := e.scan("t", 3)
	nonUnique := e.f.ConstructProject(ts, nil, opt.MakeColSet(tc[1]))
	e.expectNoMatch(rule, e.f.ConstructProject(
		e.f.ConstructInnerJoin(l, nonUnique,
			e.filters(e.f.ConstructEq(e.v(lc[0]), e.v(tc[1]))), &memo.JoinPrivate{}),
		nil, opt.MakeColSet(lc[0])))

	e.expectNoMatch(rule, e.f.ConstructProject(
		e.f.ConstructInnerJoin(l, r, on(), &memo.JoinPrivate{}), nil, opt.MakeColSet(rc[0])))
}
