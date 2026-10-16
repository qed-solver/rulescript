package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestSemiJoinRemove(t *testing.T) {
	rule := loadRule(t, "SemiJoinRemove")
	e := newEnv(t)
	x, xc := e.scan("u", 1)
	y1, y1c := e.scan("t", 1)
	y2, y2c := e.scan("t", 1)
	semi := e.f.ConstructSemiJoin(x, y1, e.filters(e.f.ConstructEq(e.v(xc[0]), e.v(y1c[0]))), &memo.JoinPrivate{})
	before := e.f.ConstructInnerJoin(semi, y2, e.filters(e.f.ConstructEq(e.v(xc[0]), e.v(y2c[0]))), &memo.JoinPrivate{})
	want := e.f.ConstructInnerJoin(x, y2, e.filters(e.f.ConstructEq(e.v(xc[0]), e.v(y2c[0]))), &memo.JoinPrivate{})
	e.expectRewrite(rule, before, want)
	inner := e.f.ConstructInnerJoin(x, y1, e.filters(e.f.ConstructEq(e.v(xc[0]), e.v(y1c[0]))), &memo.JoinPrivate{})
	e.expectNoMatch(rule, e.f.ConstructInnerJoin(inner, y2,
		e.filters(e.f.ConstructEq(e.v(xc[0]), e.v(y2c[0]))), &memo.JoinPrivate{}))
	e.expectNoMatch(rule, e.f.ConstructInnerJoin(semi, y2,
		e.filters(e.f.ConstructLt(e.v(xc[0]), e.v(y2c[0]))), &memo.JoinPrivate{}))
}
