package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestJoinConditionExpandIsNotDistinctFrom(t *testing.T) {
	rule := loadRule(t, "JoinConditionExpandIsNotDistinctFrom")
	e := newEnv(t)
	l, lc := e.scan("u", 1)
	r, rc := e.scan("t", 1)
	x, y := e.v(lc[0]), e.v(rc[0])
	before := e.f.ConstructInnerJoin(l, r, e.filters(e.f.ConstructIs(x, y)), &memo.JoinPrivate{})
	bothNull := e.f.ConstructAnd(e.f.ConstructIs(x, memo.NullSingleton), e.f.ConstructIs(y, memo.NullSingleton))
	equal := e.f.ConstructIs(e.f.ConstructEq(x, y), e.f.ConstructTrue())
	after := e.f.ConstructInnerJoin(l, r, e.filters(e.f.ConstructOr(bothNull, equal)), &memo.JoinPrivate{})
	e.expectRewrite(rule, before, after)
	e.expectNoMatch(rule, e.f.ConstructInnerJoin(l, r, e.filters(e.f.ConstructAnd(e.f.ConstructIs(x, y), e.f.ConstructEq(x, y))), &memo.JoinPrivate{}))
}
