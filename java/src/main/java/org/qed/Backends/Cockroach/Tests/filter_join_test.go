package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestFilterJoin(t *testing.T) {
	rule := loadRule(t, "FilterJoin")
	e := newEnv(t)
	l, lc := e.scan("u", 1)
	r, rc := e.scan("t", 1)
	on := e.f.ConstructEq(e.v(lc[0]), e.v(rc[0]))
	above := e.f.ConstructGt(e.v(rc[0]), e.num(1))
	join := e.f.ConstructInnerJoin(l, r, e.filters(on), &memo.JoinPrivate{})
	before := e.f.ConstructSelect(join, e.filters(above))
	want := e.f.ConstructInnerJoin(l, r, e.filters(e.f.ConstructAnd(on, above)), &memo.JoinPrivate{})
	e.expectRewrite(rule, before, want)

	left := e.f.ConstructLeftJoin(l, r, e.filters(on), &memo.JoinPrivate{})
	e.expectNoMatch(rule, e.f.ConstructSelect(left, e.filters(above)))
	e.expectNoMatch(rule, e.f.ConstructSelect(l, e.filters(above)))
	e.expectNoMatch(rule, want)
}
