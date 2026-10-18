package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestDecorrelateJoin(t *testing.T) {
	rule := loadRule(t, "DecorrelateJoin")
	e := newEnv(t)
	l, lc := e.scan("u", 1)
	r, rc := e.scan("t", 1)
	on := e.filters(e.f.ConstructEq(e.v(lc[0]), e.v(rc[0])))
	e.expectRewrite(rule,
		e.f.ConstructInnerJoinApply(l, r, on, &memo.JoinPrivate{}),
		e.f.ConstructInnerJoin(l, r, on, &memo.JoinPrivate{}))
	e.expectNoMatch(rule, e.f.ConstructInnerJoin(l, r, on, &memo.JoinPrivate{}))
	e.expectNoMatch(rule, e.f.ConstructLeftJoinApply(l, r, on, &memo.JoinPrivate{}))
	e.expectNoMatch(rule, e.f.ConstructSemiJoinApply(l, r, on, &memo.JoinPrivate{}))
	e.expectNoMatch(rule, e.f.ConstructAntiJoinApply(l, r, on, &memo.JoinPrivate{}))
}
