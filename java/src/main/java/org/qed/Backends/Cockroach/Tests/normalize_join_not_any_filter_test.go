package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestNormalizeJoinNotAnyFilter(t *testing.T) {
	rule := loadRule(t, "NormalizeJoinNotAnyFilter")
	e := newEnv(t)
	left, lc := e.scan("u", 1)
	right, _ := e.scan("nn", 1)
	sub, sc := e.scan("t", 1)
	membership := e.f.ConstructAny(sub, e.v(lc[0]), &memo.SubqueryPrivate{Cmp: opt.EqOp})
	before := e.f.ConstructInnerJoin(left, right, e.filters(e.f.ConstructNot(membership)), &memo.JoinPrivate{})
	matched := e.f.ConstructEq(e.v(lc[0]), e.v(sc[0]))
	correlated := e.f.ConstructSelect(sub, e.filters(e.f.ConstructOr(matched, e.f.ConstructIs(matched, memo.NullSingleton))))
	want := e.f.ConstructInnerJoin(left, right, e.filters(e.f.ConstructNot(e.exists(correlated))), &memo.JoinPrivate{})
	e.expectRewrite(rule, before, want)
	e.expectNoMatch(rule, e.f.ConstructInnerJoin(left, right, e.filters(membership), &memo.JoinPrivate{}))
	e.expectNoMatch(rule, e.f.ConstructInnerJoin(left, right, nil, &memo.JoinPrivate{}))
}
