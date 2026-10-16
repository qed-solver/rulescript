package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestRewriteLateralSubquery(t *testing.T) {
	rule := loadRule(t, "RewriteLateralSubquery")
	e := newEnv(t)
	l, lc := e.scan("t", 2)
	r, rc := e.scan("u", 2)
	on := e.filters(e.f.ConstructEq(e.v(lc[0]), e.v(rc[1])))
	before := e.f.ConstructLeftJoinApply(l, r, on, &memo.JoinPrivate{})
	e.expectRewrite(rule, before, e.f.ConstructLeftJoin(l, r, on, &memo.JoinPrivate{}))
	e.expectNoMatch(rule, e.f.ConstructLeftJoin(l, r, on, &memo.JoinPrivate{}))
	e.expectNoMatch(rule, e.f.ConstructInnerJoinApply(l, r, on, &memo.JoinPrivate{}))
}
