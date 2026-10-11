package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestMultiJoinOptimizeBushy(t *testing.T) {
	rule := loadRule(t, "MultiJoinOptimizeBushy")
	e := newEnv(t)
	a, ac := e.scan("t", 1)
	b, bc := e.scan("u", 1)
	c, cc := e.scan("nn", 1)
	d, dc := e.scan("nn3", 1)

	ab := e.f.ConstructInnerJoin(a, b, nil, &memo.JoinPrivate{})
	abc := e.f.ConstructInnerJoin(ab, c, nil, &memo.JoinPrivate{})
	on := e.filters(
		e.f.ConstructEq(e.v(ac[0]), e.v(dc[0])),
		e.f.ConstructGt(e.v(bc[0]), e.v(dc[0])),
	)
	before := e.f.ConstructInnerJoin(abc, d, on, &memo.JoinPrivate{})
	cd := e.f.ConstructInnerJoin(c, d, nil, &memo.JoinPrivate{})
	want := e.f.ConstructInnerJoin(ab, cd, on, &memo.JoinPrivate{})
	e.expectRewrite(rule, before, want)

	e.expectNoMatch(rule, e.f.ConstructLeftJoin(abc, d, on, &memo.JoinPrivate{}))

	e.expectNoMatch(rule, e.f.ConstructInnerJoin(ab, d, on, &memo.JoinPrivate{}))

	abConditioned := e.f.ConstructInnerJoin(ab, c,
		e.filters(e.f.ConstructEq(e.v(bc[0]), e.v(cc[0]))), &memo.JoinPrivate{})
	e.expectNoMatch(rule, e.f.ConstructInnerJoin(abConditioned, d, on, &memo.JoinPrivate{}))

	e.expectNoMatch(rule, a)
}
