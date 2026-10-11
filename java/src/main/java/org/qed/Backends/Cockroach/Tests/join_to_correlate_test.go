package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestJoinToCorrelate(t *testing.T) {
	rule := loadRule(t, "JoinToCorrelate")
	e := newEnv(t)
	l, lc := e.scan("t", 1)
	r, rc := e.scan("u", 1)
	on := e.filters(e.f.ConstructEq(e.v(lc[0]), e.v(rc[0])))
	before := e.f.ConstructInnerJoin(l, r, on, &memo.JoinPrivate{})
	e.expectRewrite(rule, before, e.f.ConstructInnerJoinApply(l, r, on, &memo.JoinPrivate{}))
	e.expectNoMatch(rule, e.f.ConstructInnerJoinApply(l, r, on, &memo.JoinPrivate{}))
	e.expectNoMatch(rule, e.f.ConstructLeftJoin(l, r, on, &memo.JoinPrivate{}))
	e.expectNoMatch(rule, e.f.ConstructSelect(before, e.filters(e.f.ConstructGt(e.v(lc[0]), e.num(1)))))
}
