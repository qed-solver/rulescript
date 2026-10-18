package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestInjectRuntimeFilter(t *testing.T) {
	rule := loadRule(t, "InjectRuntimeFilter")
	e := newEnv(t)
	l, lc := e.scan("u", 1)
	r, rc := e.scan("t", 1)
	on := e.filters(e.f.ConstructEq(e.v(lc[0]), e.v(rc[0])))
	before := e.f.ConstructInnerJoin(l, r, on, &memo.JoinPrivate{})
	semi := e.f.ConstructSemiJoin(l, r, on, &memo.JoinPrivate{})
	e.expectRewrite(rule, before, e.f.ConstructInnerJoin(semi, r, on, &memo.JoinPrivate{}))
	e.expectNoMatch(rule, e.f.ConstructLeftJoin(l, r, on, &memo.JoinPrivate{}))
	e.expectNoMatch(rule, e.f.ConstructSemiJoin(l, r, on, &memo.JoinPrivate{}))
	lt := e.filters(e.f.ConstructLt(e.v(lc[0]), e.v(rc[0])))
	e.expectNoMatch(rule, e.f.ConstructInnerJoin(l, r, lt, &memo.JoinPrivate{}))
}
