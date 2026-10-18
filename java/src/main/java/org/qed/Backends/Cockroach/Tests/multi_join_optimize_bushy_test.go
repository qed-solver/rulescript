package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestMultiJoinOptimizeBushy(t *testing.T) {
	rule := loadRule(t, "MultiJoinOptimizeBushy")
	e := newEnv(t)
	a, ac := e.scan("t", 1)
	b, _ := e.scan("u", 1)
	c, _ := e.scan("nn", 1)
	d, dc := e.scan("nn3", 1)
	ab := e.f.ConstructInnerJoin(a, b, nil, &memo.JoinPrivate{})
	abc := e.f.ConstructInnerJoin(ab, c, nil, &memo.JoinPrivate{})
	f := e.f.ConstructEq(e.v(ac[0]), e.v(dc[0]))
	before := e.f.ConstructInnerJoin(abc, d, e.filters(f), &memo.JoinPrivate{})
	cd := e.f.ConstructInnerJoin(c, d, nil, &memo.JoinPrivate{})
	want := e.f.ConstructInnerJoin(ab, cd, e.filters(f), &memo.JoinPrivate{})
	e.expectRewrite(rule, before, want)

	wrong := e.f.ConstructInnerJoin(ab, d, e.filters(f), &memo.JoinPrivate{})
	e.expectNoMatch(rule, wrong)
}
