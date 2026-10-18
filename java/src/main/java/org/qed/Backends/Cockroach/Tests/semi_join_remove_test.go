package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestSemiJoinRemove(t *testing.T) {
	rule := loadRule(t, "SemiJoinRemove")
	e := newEnv(t)
	x, xcols := e.scan("t", 3)
	y, ycols := e.scan("u", 2)
	cond := e.filters(e.f.ConstructEq(e.v(xcols[0]), e.v(ycols[0])))
	semi := e.f.ConstructSemiJoin(x, y, cond, &memo.JoinPrivate{})
	before := e.f.ConstructInnerJoin(semi, y, cond, &memo.JoinPrivate{})
	want := e.f.ConstructInnerJoin(x, y, cond, &memo.JoinPrivate{})
	e.expectRewrite(rule, before, want)

	nearCond := e.filters(e.f.ConstructEq(e.v(xcols[1]), e.v(ycols[0])))
	nearSemi := e.f.ConstructSemiJoin(x, y, cond, &memo.JoinPrivate{})
	nearMiss := e.f.ConstructInnerJoin(nearSemi, y, nearCond, &memo.JoinPrivate{})
	e.expectNoMatch(rule, nearMiss)
}
