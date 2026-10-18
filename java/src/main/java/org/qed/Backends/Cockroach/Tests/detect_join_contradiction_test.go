package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestDetectJoinContradiction(t *testing.T) {
	rule := loadRule(t, "DetectJoinContradiction")
	e := newEnv(t)
	l, lc := e.scan("u", 1)
	r, rc := e.scan("t", 1)
	rest := e.f.ConstructEq(e.v(lc[0]), e.v(rc[0]))
	before := e.f.ConstructInnerJoin(l, r, e.filters(e.f.ConstructAnd(e.f.ConstructFalse(), rest)), &memo.JoinPrivate{})
	want := e.f.ConstructInnerJoin(l, r, e.filters(e.f.ConstructFalse()), &memo.JoinPrivate{})
	e.expectRewrite(rule, before, want)
	e.expectNoMatch(rule, e.f.ConstructLeftJoin(l, r, e.filters(e.f.ConstructAnd(e.f.ConstructFalse(), rest)), &memo.JoinPrivate{}))
	e.expectNoMatch(rule, e.f.ConstructInnerJoin(l, r, e.filters(e.f.ConstructAnd(e.f.ConstructTrue(), rest)), &memo.JoinPrivate{}))
	e.expectNoMatch(rule, e.f.ConstructInnerJoin(l, r, e.filters(e.f.ConstructOr(e.f.ConstructFalse(), rest)), &memo.JoinPrivate{}))
}
